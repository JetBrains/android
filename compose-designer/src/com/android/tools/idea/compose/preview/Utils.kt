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
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UFile
import org.jetbrains.uast.toUElement

const val UNDEFINED_API_LEVEL = -1
const val UNDEFINED_DIMENSION = -1

// Max allowed API
@VisibleForTesting const val MAX_WIDTH = 2000
@VisibleForTesting const val MAX_HEIGHT = 2000

/**
 * Truncates the given dimension value to fit between the [min] and [max] values. If the receiver is null,
 * this will return null.
 */
private fun Int?.truncate(min: Int, max: Int): Int? {
  if (this == null) {
    return null
  }

  if (this == UNDEFINED_DIMENSION) {
    return UNDEFINED_DIMENSION
  }

  return minOf(maxOf(this, min), max)
}

data class PreviewConfiguration private constructor(val apiLevel: Int,
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

  companion object {
    /**
     * Cleans the given values and creates a PreviewConfiguration. The cleaning ensures that the user inputted value are within
     * reasonable values before the PreviewConfiguration is created
     */
    @JvmStatic
    fun cleanAndGet(apiLevel: Int?,
                    theme: String?,
                    width: Int?,
                    height: Int?): PreviewConfiguration =
      // We only limit the sizes. We do not limit the API because using an incorrect API level will throw an exception that
      // we will handle and any other error.
      PreviewConfiguration(apiLevel = apiLevel ?: UNDEFINED_API_LEVEL,
                           theme = theme,
                           width = width.truncate(1, MAX_WIDTH) ?: UNDEFINED_DIMENSION,
                           height = height.truncate(1, MAX_HEIGHT) ?: UNDEFINED_DIMENSION)
  }
}

/**
 * @param displayName display name of this preview element
 * @param composableMethodFqn Fully Qualified Name of the composable method
 * @param previewElementDefinitionPsi [SmartPsiElementPointer] to the preview element definition
 * @param previewBodyPsi [SmartPsiElementPointer] to the preview body. This is the code that will be ran during preview
 * @param configuration the preview element configuration
 */
data class PreviewElement(val displayName: String,
                          val composableMethodFqn: String,
                          val previewElementDefinitionPsi: SmartPsiElementPointer<PsiElement>?,
                          val previewBodyPsi: SmartPsiElementPointer<PsiElement>?,
                          val configuration: PreviewConfiguration)

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
  fun findPreviewMethods(project: Project, vFile: VirtualFile): List<PreviewElement> {
    assert(!DumbService.getInstance(project).isDumb) { "findPreviewMethods can not be called on dumb mode" }

    val uFile: UFile = PsiManager.getInstance(project).findFile(vFile)?.toUElement() as? UFile ?: return emptyList<PreviewElement>()

    return findPreviewMethods(uFile)
  }

  /**
   * Returns all the [PreviewElement]s present in the passed [UFile].
   *
   * This method always runs on smart mode.
   */
  fun findPreviewMethods(uFile: UFile): List<PreviewElement>

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

fun UElement?.toSmartPsiPointer(): SmartPsiElementPointer<PsiElement>? {
  val bodyPsiElement = this?.sourcePsi ?: return null
  return SmartPointerManager.createPointer(bodyPsiElement)
}