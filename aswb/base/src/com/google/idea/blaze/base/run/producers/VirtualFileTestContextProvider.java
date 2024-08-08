/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.run.producers;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.run.ExecutorType;
import com.google.idea.blaze.base.sync.BlazeSyncModificationTracker;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolverProvider;
import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.FakePsiElement;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import javax.annotation.Nullable;
import org.jetbrains.ide.PooledThreadExecutor;

/**
 * For situations where psi elements for the current file can't be efficiently resolved (for
 * example, files outside the current project). Uses rough heuristics to recognize test contexts,
 * then does everything else asynchronously.
 */
class VirtualFileTestContextProvider implements TestContextProvider {

  private static final ListeningExecutorService EXECUTOR =
      MoreExecutors.listeningDecorator(PooledThreadExecutor.INSTANCE);

  @Nullable
  @Override
  public RunConfigurationContext getTestContext(ConfigurationContext context) {
    PsiElement psi = context.getPsiLocation();
    if (!(psi instanceof PsiFileSystemItem) || !(psi instanceof FakePsiElement)) {
      return null;
    }
    VirtualFile vf = ((PsiFileSystemItem) psi).getVirtualFile();
    if (vf == null) {
      return null;
    }
    WorkspacePath path = getWorkspacePath(context.getProject(), vf);
    if (path == null) {
      return null;
    }
    return CachedValuesManager.getCachedValue(
        psi,
        () ->
            CachedValueProvider.Result.create(
                doFindTestContext(context, vf, psi, path),
                PsiModificationTracker.MODIFICATION_COUNT,
                BlazeSyncModificationTracker.getInstance(context.getProject())));
  }

  @Nullable
  private RunConfigurationContext doFindTestContext(
      ConfigurationContext context, VirtualFile vf, PsiElement psi, WorkspacePath path) {
    ImmutableSet<ExecutorType> relevantExecutors =
        Arrays.stream(HeuristicTestIdentifier.EP_NAME.getExtensions())
            .map(h -> h.supportedExecutors(path))
            .flatMap(Collection::stream)
            .collect(toImmutableSet());
    if (relevantExecutors.isEmpty()) {
      return null;
    }

    ListenableFuture<RunConfigurationContext> future =
        EXECUTOR.submit(() -> findContextAsync(resolveContext(context, vf)));
    return TestContext.builder(psi, relevantExecutors)
        .setContextFuture(future)
        .setDescription(vf.getNameWithoutExtension())
        .build();
  }

  @Nullable
  private static RunConfigurationContext findContextAsync(ConfigurationContext context) {
    return Arrays.stream(TestContextProvider.EP_NAME.getExtensions())
        .filter(p -> !(p instanceof VirtualFileTestContextProvider))
        .map(p -> ReadAction.compute(() -> p.getTestContext(context)))
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(null);
  }

  private static ConfigurationContext resolveContext(ConfigurationContext context, VirtualFile vf) {
    PsiFile psi =
        ReadAction.compute(() -> PsiManager.getInstance(context.getProject()).findFile(vf));
    Location<PsiFile> location = PsiLocation.fromPsiElement(psi, context.getModule());
    return location == null
        ? context
        : ConfigurationContext.createEmptyContextForLocation(location);
  }

  @Nullable
  private static WorkspacePath getWorkspacePath(Project project, VirtualFile vf) {
    WorkspacePathResolver resolver =
        WorkspacePathResolverProvider.getInstance(project).getPathResolver();
    if (resolver == null) {
      return null;
    }
    return resolver.getWorkspacePath(new File(vf.getPath()));
  }
}
