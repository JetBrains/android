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

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiUtil
import org.jetbrains.kotlin.idea.debugger.SourceLineKind
import org.jetbrains.kotlin.idea.debugger.isInlineFunctionLineNumber
import org.jetbrains.kotlin.idea.debugger.mapStacktraceLineToSource
import org.jetbrains.kotlin.idea.debugger.readBytecodeInfo
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.jvm.JvmClassName

/**
 * Mapping function used to correct the inline code references. Given a [SourceLocation] it returns a new one that points to the correct
 * place in the source file.
 */
internal fun remapInlineLocation(project: Project, ktFile: KtFile, className: String, line: Int): Pair<KtFile, Int> {
  val searchScope = GlobalSearchScope.projectScope(project)
  val virtualFile = PsiUtil.getVirtualFile(ktFile) ?: return Pair(ktFile, line)
  val internalClassName = JvmClassName.byInternalName(className.replace(".", "/"))
  val bytecodeInfo = readBytecodeInfo(project,
                                      internalClassName,
                                      virtualFile) ?: return ktFile to line
  if (bytecodeInfo.smapData == null) {
    return return Pair(ktFile, line)
  }

  return mapStacktraceLineToSource(bytecodeInfo.smapData!!,
                                   line,
                                   project,
                                   SourceLineKind.CALL_LINE,
                                   searchScope) ?: return ktFile to line
}

/**
 * [SourceLocation] associated to the [VirtualFile] that the location points to. This class is merely used as a cache to avoid the complex
 * task of mapping the string location to a VirtualFile.
 */
internal class SourceLocationWithVirtualFile(internal val virtualFile: VirtualFile,
                                             override val className: String,
                                             override val methodName: String,
                                             override val lineNumber: Int): SourceLocation {
  override val fileName: String
    get() = virtualFile.name

  override fun toString(): String =
    "SourceLocationWithVirtualFile(className=$className, methodName=$methodName, fileName=${virtualFile.name} lineNumber=$lineNumber)"
}

/**
 * Returns a [SourceLocationWithVirtualFile] from a given [SourceLocation] if the mapping can be done. If there is no mapping, for example
 * the reference file does not exist in the project, then the method returns null.
 *
 * @param project the project to use for the file resolution
 * @param scope the resolution [GlobalSearchScope]. By default, files will be found in the whole project but you can limit the search scope
 * by passing a different scope.
 */
internal fun SourceLocation.asSourceLocationWithVirtualFile(project: Project,
                                                            scope: GlobalSearchScope = GlobalSearchScope.projectScope(project)): SourceLocationWithVirtualFile? {
  val files = FilenameIndex.getFilesByName(project, fileName, scope)
  val originalPsiFile = if (files.size == 1) files[0] else return null
  val remappedLocation = if (isInlineFunctionLineNumber(originalPsiFile.virtualFile, lineNumber, project)) {
    // re-map inline
    remapInlineLocation(project, originalPsiFile as KtFile, className, lineNumber)
  }
  else {
    Pair(originalPsiFile as KtFile, lineNumber)
  }

  val remappedFile = remappedLocation.first as PsiFile
  val remappedVirtualFile = PsiUtil.getVirtualFile(remappedFile) ?: return null
  return SourceLocationWithVirtualFile(
    remappedVirtualFile,
    className,
    methodName,
    remappedLocation.second + 1 // Remapped lines are 0 based so add 1 here
  )
}

/**
 * Returns a [SourceLocation] that maps any inlined references. If the [SourceLocation] does not belong to an inline call, the same
 * [SourceLocation] is returned.
 */
fun remapInline(project: Project): (SourceLocation) -> SourceLocation = { sourceLocation ->
  sourceLocation.asSourceLocationWithVirtualFile(project) ?: sourceLocation
}
