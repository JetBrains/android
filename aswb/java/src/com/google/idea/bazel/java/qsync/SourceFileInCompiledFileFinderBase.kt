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
package com.google.idea.bazel.java.qsync

import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import java.nio.file.Path

/** A base class used to find source files existed in source jar. */
abstract class SourceFileInCompiledFileFinderBase(clsFile: PsiFile) : SourceFileFinderBase(clsFile) {

  override fun convertToVirtualFile(path: Path): VirtualFile? {
    val localFile = LocalFileSystem.getInstance().findFileByNioFile(path) ?: return null
    return JarFileSystem.getInstance().getJarRootForLocalFile(localFile)
  }

  override fun getMatchingPsiFile(vf: VirtualFile): Set<PsiFile> {
    val project = project ?: return emptySet()
    val matchingPsiFiles = mutableSetOf<PsiFile>()
    val psiManager = PsiManager.getInstance(project)

    // Guess candidate file path according to qualifiedName,
    // iterate over all files can take a long time if src jar is large
    for (qualifiedName in qualifiedClassNames) {
      val jvmClassPath = qualifiedName.substringBefore('$').replace('.', '/')

      val candidateFilePaths = buildSet {
        add(jvmClassPath)

        if (jvmClassPath.endsWith("Kt")) {
          val pathWithoutKt = jvmClassPath.removeSuffix("Kt")

          // MyUtilsKt -> MyUtils.kt
          add(pathWithoutKt)

          // MyUtilsKt -> myUtils.kt
          val packagePath = pathWithoutKt.substringBeforeLast('/', missingDelimiterValue = "")
          val className = pathWithoutKt.substringAfterLast('/')

          if (className.isNotEmpty()) {
            val camelCaseFileName = className.replaceFirstChar { it.lowercase() }
            val camelCasePath =
              if (packagePath.isEmpty()) {
                camelCaseFileName
              } else {
                "$packagePath/$camelCaseFileName"
              }
            add(camelCasePath)
          }
        }
      }

      for (path in candidateFilePaths) {
        for (ext in SOURCE_EXTENSIONS) {
          val psiFile = vf.findFileByRelativePath("$path$ext")?.let { psiManager.findFile(it) }

          if (psiFile != null && containsClass(psiFile)) {
            matchingPsiFiles.add(psiFile)
          }
        }
      }
    }

    if (matchingPsiFiles.isNotEmpty()) {
      return matchingPsiFiles
    }

    // Fallback to recursive VFS iteration if direct lookup failed.
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

  companion object {
    private val SOURCE_EXTENSIONS = listOf(".java", ".kt", ".groovy", ".scala")
  }
}
