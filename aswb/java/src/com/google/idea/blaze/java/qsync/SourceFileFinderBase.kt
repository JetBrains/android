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
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot
import com.google.idea.blaze.base.qsync.QuerySyncManager
import com.google.idea.blaze.qsync.deps.JavaArtifactInfo
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiFile
import java.nio.file.Path
import kotlin.io.path.Path

/**
 * A base class used to find source files for a class jar. It provides a basic workflow to fetch
 * source files. And it allows different implementation to provide its own implementation.
 */
abstract class SourceFileFinderBase @VisibleForTesting constructor(
  protected val project: Project?,
  protected val querySyncManager: QuerySyncManager,
  protected val workspaceRoot: Path?,
  protected val projectPath: Path,
  protected val clsFile: PsiFile,
) {

  protected val qualifiedClassNames: Set<String> = getQualifiedClassNames(clsFile)

  constructor(clsFile: PsiFile) : this(
    project = clsFile.project,
    querySyncManager = QuerySyncManager.getInstance(clsFile.project),
    workspaceRoot = WorkspaceRoot.fromProject(clsFile.project).path(),
    projectPath = Path(clsFile.project.basePath ?: ""),
    clsFile = clsFile
  )

  fun findSourceFile(): PsiFile? {
    val sourcePaths = javaArtifactInfos.asSequence()
      .flatMap { filterSourcePaths(it) }
      .toSet()

    if (sourcePaths.isEmpty()) {
      return null
    }

    val matchingPsiFiles = sourcePaths.asSequence()
      .mapNotNull { convertToVirtualFile(it) }
      .flatMap { getMatchingPsiFile(it) }
      .toSet()

    if (matchingPsiFiles.size > 1) {
      logger.warn("Warning: found more than 1 matching source file for $clsFile: $matchingPsiFiles")
    }

    return matchingPsiFiles.firstOrNull()
  }

  /**
   * Returns the path to the source files stored in current JavaArtifactInfo.
   */
  abstract fun filterSourcePaths(artifactInfo: JavaArtifactInfo): Sequence<Path>

  /**
   * Converts the file to VirtualFile.
   */
  abstract fun convertToVirtualFile(path: Path): VirtualFile?

  /**
   * Returns a list of PsiFile's that contains a file with the class we are looking for
   */
  abstract fun getMatchingPsiFile(vf: VirtualFile): Set<PsiFile>

  private val javaArtifactInfos: List<JavaArtifactInfo> by lazy {
    val file = clsFile.containingFile.virtualFile
    val jar = JarFileSystem.getInstance().getLocalVirtualFileFor(file) ?: return@lazy emptyList()

    var jarPath = jar.toNioPath()
    if (!jarPath.startsWith(projectPath)) {
      return@lazy emptyList()
    }
    jarPath = projectPath.relativize(jarPath)

    val snapshot = querySyncManager.currentSnapshot.orElse(null) ?: return@lazy emptyList()
    snapshot.artifactIndex.getInfoForJarArtifact(jarPath)
  }

  protected fun getQualifiedClassNames(psiFile: PsiFile?): Set<String> {
    return (psiFile as? PsiClassOwner)?.classes
             ?.mapNotNull { it.qualifiedName }
             ?.toSet()
           ?: emptySet()
  }

  protected fun containsClass(file: PsiFile?): Boolean {
    return (file as? PsiClassOwner)?.classes?.any {
      it.qualifiedName in qualifiedClassNames
    } == true
  }

  companion object {
    private val logger = Logger.getInstance(SourceFileFinderBase::class.java)
  }
}