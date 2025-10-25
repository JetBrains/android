/*
 * Copyright 2025 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.java.qsync;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.qsync.QuerySyncManager;
import com.google.idea.blaze.qsync.QuerySyncProjectSnapshot;
import com.google.idea.blaze.qsync.deps.JavaArtifactInfo;
import java.nio.file.Path;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.compiled.ClsFileImpl;
import com.intellij.psi.PsiFile;
import java.util.Set;
import com.intellij.psi.PsiClass;

import static java.util.Arrays.stream;

/**
 * A base class for our java source finder util class to inherit. It provides several util functions
 * that can be shared within different finder e.g. find JavaInfoArtifacts of the jar file, check if
 * some PsiFile has expected package name.
 */
abstract class CompiledJavaSourceFinderBase {
  protected final Project project;
  protected final QuerySyncManager querySyncManager;
  protected final Path workspaceRoot;
  protected final Path projectPath;
  protected final ClsFileImpl clsFile;

  public CompiledJavaSourceFinderBase(ClsFileImpl clsFile) {
    this(
        clsFile.getProject(),
        QuerySyncManager.getInstance(clsFile.getProject()),
        WorkspaceRoot.fromProject(clsFile.getProject()).path(),
        Path.of(clsFile.getProject().getBasePath()),
        clsFile);
  }

  @VisibleForTesting
  public CompiledJavaSourceFinderBase(
      Project project,
      QuerySyncManager querySyncManager,
      Path workspaceRoot,
      Path projectPath,
      ClsFileImpl clsFile) {
    this.project = project;
    this.querySyncManager = querySyncManager;
    this.workspaceRoot = workspaceRoot;
    this.projectPath = projectPath;
    this.clsFile = clsFile;
  }

  protected final ImmutableList<JavaArtifactInfo> getJavaArtifactInfos() {
    VirtualFile file = clsFile.getContainingFile().getVirtualFile();
    // We expect a .class file inside a .jar file. Get the outer jar file:
    VirtualFile jar = JarFileSystem.getInstance().getLocalVirtualFileFor(file);
    if (jar == null) {
      // Not a jar file - ignore it
      return ImmutableList.of();
    }
    Path jarPath = jar.toNioPath();
    if (!jarPath.startsWith(projectPath)) {
      return ImmutableList.of();
    }
    jarPath = projectPath.relativize(jarPath);

    QuerySyncProjectSnapshot snapshot = querySyncManager.getCurrentSnapshot().orElse(null);
    if (snapshot == null) {
      return ImmutableList.of();
    }
    return snapshot.getArtifactIndex().getInfoForJarArtifact(jarPath);
  }

  protected static boolean containsClass(PsiFile file, Set<String> qualifiedClassName) {
    return stream(file.getChildren())
        .filter(PsiClass.class::isInstance)
        .map(PsiClass.class::cast)
        .map(PsiClass::getQualifiedName)
        .anyMatch(qualifiedClassName::contains);
  }
}
