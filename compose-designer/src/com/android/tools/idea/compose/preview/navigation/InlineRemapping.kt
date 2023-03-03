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
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiUtil
import kotlin.math.absoluteValue
import org.jetbrains.kotlin.idea.debugger.base.util.KotlinSourceMapCache
import org.jetbrains.kotlin.idea.debugger.base.util.isInlineFrameLineNumber
import org.jetbrains.kotlin.idea.debugger.core.SourceLineKind
import org.jetbrains.kotlin.idea.debugger.core.mapStacktraceLineToSource
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.jvm.JvmClassName

/**
 * Mapping function used to correct the inline code references. Given a [SourceLocation] it returns
 * a new one that points to the correct place in the source file. The returned [Pair] maps the
 * source [KtFile] to the 1-indexed line information.
 */
internal fun remapInlineLocation(
  module: Module,
  ktFile: KtFile,
  className: String,
  line: Int,
  searchScope: GlobalSearchScope =
    GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, false)
): Pair<KtFile, Int> {
  val virtualFile = PsiUtil.getVirtualFile(ktFile) ?: return Pair(ktFile, line)
  val internalClassName = JvmClassName.byInternalName(className.replace(".", "/"))
  val smapData =
    KotlinSourceMapCache.getInstance(module.project).getSourceMap(virtualFile, internalClassName)
      ?: return ktFile to line

  val inlineRemapped =
    mapStacktraceLineToSource(smapData, line, module.project, SourceLineKind.CALL_LINE, searchScope)
      ?: return ktFile to line

  return inlineRemapped.first to
    inlineRemapped.second + 1 // Remapped lines are 0 based so add 1 here
}

/**
 * [SourceLocation] associated to the [VirtualFile] that the location points to. This class is
 * merely used as a cache to avoid the complex task of mapping the string location to a VirtualFile.
 */
internal class SourceLocationWithVirtualFile(
  internal val virtualFile: VirtualFile,
  override val className: String,
  override val methodName: String,
  override val lineNumber: Int,
  override val packageHash: Int
) : SourceLocation {
  override val fileName: String
    get() = virtualFile.name

  override fun toString(): String {
    val classNameInformation =
      if (className.isNotEmpty() || methodName.isNotEmpty()) {
        "className=$className, methodName=$methodName, "
      } else ""

    return "SourceLocationWithVirtualFile(${classNameInformation}fileName=${virtualFile.name}, lineNumber=$lineNumber, packageHash=$packageHash)"
  }
}

/** Returns true if any of the classes contained in the [file] match the given [rootClassName]. */
private fun matchesFile(file: PsiClassOwner, rootClassName: String): Boolean =
  rootClassName.isNotEmpty() && file.classes?.any { it.qualifiedName == rootClassName } ?: false

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
    GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, false)
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
    when {
      packageHash != -1 ->
        filesWithName.find {
          // File names are not unique. If the class name is available, disambiguate by class name.
          matchesPackage(it, packageHash)
        }
      filesWithName.size == 1 -> filesWithName.single()
      else -> null
    }
      ?: return null

  val remappedLocation =
    if (isInlineFrameLineNumber(originalPsiFile.virtualFile, lineNumber, module.project)) {
      // re-map inline
      remapInlineLocation(module, originalPsiFile as KtFile, className, lineNumber, scope)
    } else {
      Pair(originalPsiFile as KtFile, lineNumber)
    }

  val remappedFile = remappedLocation.first as PsiFile
  val remappedVirtualFile = PsiUtil.getVirtualFile(remappedFile) ?: return null
  return SourceLocationWithVirtualFile(
    remappedVirtualFile,
    className,
    methodName,
    remappedLocation.second,
    this.packageHash
  )
}

/**
 * Returns a [SourceLocation] that maps any inlined references. If the [SourceLocation] does not
 * belong to an inline call, the same [SourceLocation] is returned.
 */
fun remapInline(module: Module): (SourceLocation) -> SourceLocation = { sourceLocation ->
  runReadAction { sourceLocation.asSourceLocationWithVirtualFile(module) } ?: sourceLocation
}
