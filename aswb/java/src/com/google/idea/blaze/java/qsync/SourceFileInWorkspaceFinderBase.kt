/*
 * Copyright 2025 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.java.qsync

import com.google.common.annotations.VisibleForTesting
import com.google.idea.blaze.base.qsync.QuerySyncManager
import com.google.idea.blaze.qsync.deps.JavaArtifactInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import java.nio.file.Path

/** A base class used to find source files existed in workspace. */
abstract class SourceFileInWorkspaceFinderBase : SourceFileFinderBase {

  constructor(clsFile: PsiFile) : super(clsFile)

  @VisibleForTesting
  constructor(
    project: Project,
    querySyncManager: QuerySyncManager,
    workspaceRoot: Path,
    projectPath: Path,
    clsFile: PsiFile
  ) : super(project, querySyncManager, workspaceRoot, projectPath, clsFile)

  override fun convertToVirtualFile(path: Path): VirtualFile? {
    return LocalFileSystem.getInstance().findFileByNioFile(path)
  }

  override fun filterSourcePaths(artifactInfo: JavaArtifactInfo): Sequence<Path> {
    val pathResolver = querySyncManager.assertProjectLoaded().projectPathResolver
    val sourceFileNames = getSourceFileNamesFromClasses()
    val root = workspaceRoot ?: return emptySequence()

    return artifactInfo.sources().asSequence()
      .map { pathResolver.resolve(it) }
      .filter { it.fileName.toString() in sourceFileNames }
      .map { root.resolve(it) }
  }

  abstract fun getSourceFileNamesFromClasses(): Set<String>

  override fun getMatchingPsiFile(vf: VirtualFile): Set<PsiFile> {
    val currentProject = project ?: return emptySet()

    val psiFile = PsiManager.getInstance(currentProject).findFile(vf)

    return if (psiFile != null && containsClass(psiFile)) {
      setOf(psiFile)
    } else {
      emptySet()
    }
  }
}