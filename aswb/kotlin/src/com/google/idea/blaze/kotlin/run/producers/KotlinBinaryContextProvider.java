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
package com.google.idea.blaze.kotlin.run.producers;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.RuleType;
import com.google.idea.blaze.base.run.producers.BinaryContextProvider;
import com.google.idea.blaze.base.run.testmap.FilteredTargetMap;
import com.google.idea.blaze.base.sync.SyncCache;
import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import java.util.Collection;
import javax.annotation.Nullable;
import org.jetbrains.kotlin.idea.run.KotlinRunConfigurationProducer;
import org.jetbrains.kotlin.psi.KtDeclarationContainer;

/** Creates run configurations for Kotlin main methods. */
class KotlinBinaryContextProvider implements BinaryContextProvider {

  private static final String KOTLIN_BINARY_MAP_KEY = "BlazeKotlinBinaryMap";

  @Nullable
  @Override
  public BinaryRunContext getRunContext(ConfigurationContext context) {
    Location<?> location = context.getLocation();
    if (location == null) {
      return null;
    }
    TargetIdeInfo target = getTargetIdeInfo(context);
    if (target == null) {
      return null;
    }
    return BinaryRunContext.create(location.getPsiElement(), target.toTargetInfo());
  }

  @Nullable
  private static TargetIdeInfo getTargetIdeInfo(ConfigurationContext context) {
    Location<?> location = context.getLocation();
    if (location == null) {
      return null;
    }
    VirtualFile virtualFile = location.getVirtualFile();
    if (virtualFile == null) {
      return null;
    }
    // #api213. Switch to EntryPointContainerFinder.find.
    KtDeclarationContainer entryPointContainer =
        KotlinRunConfigurationProducer.Companion.getEntryPointContainer(location.getPsiElement());
    if (entryPointContainer == null) {
      return null;
    }
    String startClassFqName =
        KotlinRunConfigurationProducer.Companion.getStartClassFqName(entryPointContainer);
    if (startClassFqName == null) {
      return null;
    }
    Collection<TargetIdeInfo> kotlinBinaryTargets =
        findKotlinBinaryTargets(context.getProject(), VfsUtil.virtualToIoFile(virtualFile));

    // first look for a matching main_class
    TargetIdeInfo match =
        kotlinBinaryTargets.stream()
            .filter(
                target ->
                    target.getJavaIdeInfo() != null
                        && startClassFqName.equals(
                            target.getJavaIdeInfo().getJavaBinaryMainClass()))
            .findFirst()
            .orElse(null);
    if (match != null) {
      return match;
    }

    match =
        kotlinBinaryTargets.stream()
            .filter(
                target ->
                    startClassFqName.equals(target.getKey().getLabel().targetName().toString()))
            .findFirst()
            .orElse(null);
    if (match != null) {
      return match;
    }

    return Iterables.getFirst(kotlinBinaryTargets, null);
  }

  /** Returns all kt_jvm_binary targets reachable from the given source file. */
  private static Collection<TargetIdeInfo> findKotlinBinaryTargets(
      Project project, File mainClassFile) {
    FilteredTargetMap map =
        SyncCache.getInstance(project)
            .get(KOTLIN_BINARY_MAP_KEY, KotlinBinaryContextProvider::computeTargetMap);
    return map != null ? map.targetsForSourceFile(mainClassFile) : ImmutableList.of();
  }

  private static FilteredTargetMap computeTargetMap(Project project, BlazeProjectData projectData) {
    return new FilteredTargetMap(
        project,
        projectData.getArtifactLocationDecoder(),
        projectData.getTargetMap(),
        KotlinBinaryContextProvider::possiblyRelevantTarget);
  }

  private static boolean possiblyRelevantTarget(TargetIdeInfo target) {
    if (!target.isPlainTarget()) {
      return false;
    }
    Kind kind = target.getKind();
    if (kind.getRuleType() != RuleType.BINARY) {
      return false;
    }
    return kind.hasAnyLanguageIn(LanguageClass.KOTLIN, LanguageClass.JAVA);
  }
}
