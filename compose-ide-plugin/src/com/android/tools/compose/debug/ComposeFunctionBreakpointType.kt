/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.compose.debug

import com.android.tools.compose.ComposeBundle
import com.android.tools.compose.isComposableFunction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.xdebugger.breakpoints.XBreakpoint
import org.jetbrains.java.debugger.breakpoints.properties.JavaMethodBreakpointProperties
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaClassOrObjectSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaTypeAliasSymbol
import org.jetbrains.kotlin.idea.debugger.core.breakpoints.ApplicabilityResult
import org.jetbrains.kotlin.idea.debugger.core.breakpoints.KotlinFunctionBreakpoint
import org.jetbrains.kotlin.idea.debugger.core.breakpoints.KotlinFunctionBreakpointType
import org.jetbrains.kotlin.idea.debugger.core.breakpoints.isBreakpointApplicable
import org.jetbrains.kotlin.idea.debugger.core.breakpoints.isInlineOnly
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtPsiUtil

/** A [com.intellij.debugger.ui.breakpoints.JavaMethodBreakpointType] for `@Composable` functions */
internal class ComposeFunctionBreakpointType :
  KotlinFunctionBreakpointType(
    "compose-function",
    ComposeBundle.message("compose.breakpoint.description"),
  ) {

  override fun createJavaBreakpoint(
    project: Project,
    breakpoint: XBreakpoint<JavaMethodBreakpointProperties>,
  ): KotlinFunctionBreakpoint = ComposeFunctionBreakpoint(project, breakpoint)

  override fun isFunctionBreakpointApplicable(
    file: VirtualFile,
    line: Int,
    project: Project,
  ): Boolean =
    isBreakpointApplicable(file, line, project) { element ->
      when (element) {
        is KtFunction ->
          ApplicabilityResult.maybe(
            !KtPsiUtil.isLocal(element) && !element.isInlineOnly() && element.isComposableFunction()
          )
        else -> ApplicabilityResult.UNKNOWN
      }
    }
}
// TODO Find a proper commit in Ultimate and apply change
private val COMPOSABLE_CLASS_ID = ClassId.fromString("androidx/compose/runtime/Composable")


/**
 * Don't allow method breakpoints on Composable functions because we can't match their signature.
 *
 * This will be handled by the Compose plugin.
 */
private fun KtFunction.isComposable(): Boolean {
  analyze(this) {
    for (annotationEntry in annotationEntries) {
      val classSymbol = when (val symbol = annotationEntry.typeReference?.mainReference?.resolveToSymbol()) {
        is KaTypeAliasSymbol -> symbol.expandedType.expandedClassSymbol
        is KaClassOrObjectSymbol -> symbol
        else -> null
      }

      if (classSymbol != null && classSymbol.classId == COMPOSABLE_CLASS_ID) {
        return true
      }
    }

    return false
  }
}