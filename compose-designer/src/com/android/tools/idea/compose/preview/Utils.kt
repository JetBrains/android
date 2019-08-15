/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.compose.preview

import com.android.tools.idea.configurations.Configuration
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker
import com.android.tools.idea.gradle.project.build.invoker.TestCompileType
import com.android.tools.idea.rendering.multi.CompatibilityRenderTarget
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.toUElement

const val UNDEFINED_API_LEVEL = -1
const val UNDEFINED_DIMENSION = -1

data class PreviewConfiguration(val apiLevel: Int,
                                val theme: String?,
                                val width: Int,
                                val height: Int) {
  fun applyTo(renderConfiguration: Configuration) {
    if (apiLevel != UNDEFINED_API_LEVEL) {
      val highestTarget = renderConfiguration.configurationManager.highestApiTarget!!

      renderConfiguration.target = CompatibilityRenderTarget(highestTarget, apiLevel, null)
    }

    if (theme != null) {
      renderConfiguration.setTheme(theme)
    }
  }
}
data class PreviewElement(val name: String, val method: String, val textRange: TextRange, val configuration: PreviewConfiguration)

interface PreviewElementFinder {
  /**
   * Returns whether this Preview element finder might apply to the given Kotlin file.
   * The main difference with [findPreviewMethods] is that method might be called on Dumb mode so it must not use any indexes.
   */
  fun hasPreviewMethods(project: Project, vFile: VirtualFile): Boolean

  /**
   * Returns all the [PreviewElement]s present in the passed Kotlin [VirtualFile].
   *
   * This method always runs on smart mode.
   */
  fun findPreviewMethods(project: Project, vFile: VirtualFile): Set<PreviewElement> {
    assert(!DumbService.getInstance(project).isDumb) { "findPreviewMethods can not be called on dumb mode" }

    val uFile: UFile = PsiManager.getInstance(project).findFile(vFile)?.toUElement() as? UFile ?: return emptySet<PreviewElement>()

    return findPreviewMethods(uFile)
  }

  /**
   * Returns all the [PreviewElement]s present in the passed [UFile].
   *
   * This method always runs on smart mode.
   */
  fun findPreviewMethods(uFile: UFile): Set<PreviewElement>

  /**
   * Returns whether the given [PsiElement] belongs to a PreviewElement handled by this [PreviewElementFinder]. Implementations must return
   * true if they can not determine if the element belongs to a [PreviewElement] or not.
   * This method will be called to detect changes into [PreviewElement]s and issue a refresh.
   *
   * This method can not use UAST for performance reasons since it might be called very frequently.
   *
   * This method always runs on smart mode.
   */
  fun elementBelongsToPreviewElement(element: PsiElement): Boolean = true
}

internal fun requestBuild(project: Project, module: Module) {
  GradleBuildInvoker.getInstance(project).compileJava(arrayOf(module), TestCompileType.NONE)
}

fun UMethod?.bodyTextRange(): TextRange =
  this?.uastBody?.sourcePsi?.textRange ?: TextRange.EMPTY_RANGE