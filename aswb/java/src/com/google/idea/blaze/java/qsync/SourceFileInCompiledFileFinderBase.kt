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

import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import java.nio.file.Path

/**
 * A base class used to find source files existed in source jar.
 */
abstract class SourceFileInCompiledFileFinderBase(clsFile: PsiFile) : SourceFileFinderBase(clsFile) {

  override fun convertToVirtualFile(path: Path): VirtualFile? {
    val localFile = JarFileSystem.getInstance().findLocalVirtualFileByPath(path.toString())
                    ?: return null
    return JarFileSystem.getInstance().getJarRootForLocalFile(localFile) ?: localFile
  }

  override fun getMatchingPsiFile(vf: VirtualFile): Set<PsiFile> {
    val project = project ?: return emptySet()
    val matchingPsiFiles = mutableSetOf<PsiFile>()
    val psiManager = PsiManager.getInstance(project)

    VfsUtilCore.iterateChildrenRecursively(vf, null) { fileOrDir ->
      if (!fileOrDir.isDirectory) {
        val psiFile = psiManager.findFile(fileOrDir)
        if (psiFile != null && containsClass(psiFile)) {
          matchingPsiFiles.add(psiFile)
        }
      }
      true
    }
    return matchingPsiFiles
  }
}