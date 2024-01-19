/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.compose.preview.navigation

import com.android.tools.idea.compose.preview.SourceLocation
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import kotlin.math.absoluteValue

/**
 * [SourceLocation] associated to the [VirtualFile] that the location points to. This class is
 * merely used as a cache to avoid the complex task of mapping the string location to a VirtualFile.
 */
internal class SourceLocationWithVirtualFile(
  internal val virtualFile: VirtualFile,
  override val lineNumber: Int,
  override val packageHash: Int,
) : SourceLocation {
  override val fileName: String
    get() = virtualFile.name

  override fun toString(): String =
    "SourceLocationWithVirtualFile(fileName=${virtualFile.name}, lineNumber=$lineNumber, packageHash=$packageHash)"
}

/**
 * Calculates the hash of the given [packageName]. This calculation must match the one done in the
 * Compose runtime so we can match the package names.
 */
private fun packageNameHash(packageName: String): Int =
  packageName.fold(0) { hash, char -> hash * 31 + char.code }.absoluteValue

/** Returns true if the given [file] package matches the [packageHash]. */
private fun matchesPackage(file: PsiClassOwner, packageHash: Int): Boolean =
  packageHash != -1 && packageNameHash(file.packageName) == packageHash

/**
 * Returns a [SourceLocationWithVirtualFile] from a given [SourceLocation] if the mapping can be
 * done. If there is no mapping, for example the reference file does not exist in the project, then
 * the method returns null.
 *
 * @param module the module to use for the file resolution
 * @param scope the resolution [GlobalSearchScope]. By default, files will be found in the whole
 *   project but you can limit the search scope by passing a different scope.
 */
internal fun SourceLocation.asSourceLocationWithVirtualFile(
  module: Module,
  scope: GlobalSearchScope =
    GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, false),
): SourceLocationWithVirtualFile? {
  if (isEmpty()) return null

  // Lookup in the filename index for matches of the filename. Multiple matches are possible.
  val filesWithName =
    runReadAction {
        FilenameIndex.getVirtualFilesByName(fileName, scope).mapNotNull {
          PsiManager.getInstance(module.project).findFile(it)
        }
      }
      .filterIsInstance<PsiClassOwner>()
      .toList()

  val originalPsiFile =
    runReadAction {
      when {
        packageHash != -1 ->
          filesWithName.find {
            // File names are not unique. If the class name is available, disambiguate by class
            // name.
            matchesPackage(it, packageHash)
          }
        filesWithName.size == 1 -> filesWithName.single()
        else -> null
      }
    } ?: return null

  return SourceLocationWithVirtualFile(originalPsiFile.virtualFile, lineNumber, this.packageHash)
}
