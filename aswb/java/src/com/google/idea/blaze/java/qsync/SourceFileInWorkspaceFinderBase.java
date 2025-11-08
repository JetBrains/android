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
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.qsync.QuerySyncManager;
import com.google.idea.blaze.qsync.deps.JavaArtifactInfo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Stream;

/** A base class used to find source files existed in workspace. */
abstract class SourceFileInWorkspaceFinderBase extends SourceFileFinderBase {
  public SourceFileInWorkspaceFinderBase(PsiFile clsFile) {
    super(clsFile);
  }
  @VisibleForTesting
  public SourceFileInWorkspaceFinderBase(
    Project project,
    QuerySyncManager querySyncManager,
    Path workspaceRoot,
    Path projectPath,
    PsiFile clsFile) {
    super(project, querySyncManager, workspaceRoot, projectPath, clsFile);
  }

  @Override
  public VirtualFile convertToVirtualFile(Path path) {
    return LocalFileSystem.getInstance().findFileByNioFile(path);
  }

  @Override
  public Stream<Path> filterSourcePaths(JavaArtifactInfo artifactInfo) {
    final var pathResolver = querySyncManager.assertProjectLoaded().getProjectPathResolver();
    return artifactInfo.sources().stream()
        .map(pathResolver::resolve)
        .filter(p -> getSourceFileNamesFromClasses().contains(p.getFileName().toString()))
        .map(workspaceRoot::resolve);
  }

  abstract ImmutableSet<String> getSourceFileNamesFromClasses();

  @Override
  public Set<PsiFile> getMatchingPsiFile(VirtualFile vf) {
    PsiFile psiFile = PsiManager.getInstance(project).findFile(vf);
    if(psiFile != null && containsClass(psiFile)) {
      return ImmutableSet.of(psiFile);
    }
    return ImmutableSet.of();
  }
}
