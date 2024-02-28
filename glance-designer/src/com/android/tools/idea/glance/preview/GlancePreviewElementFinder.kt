/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.glance.preview

import com.android.tools.idea.AndroidPsiUtils
import com.android.tools.idea.preview.FilePreviewElementFinder
import com.android.tools.idea.preview.annotations.findAnnotatedMethodsValues
import com.android.tools.idea.preview.annotations.hasAnnotation
import com.android.tools.idea.preview.findPreviewDefaultValues
import com.android.tools.idea.preview.toSmartPsiPointer
import com.android.tools.preview.PreviewConfiguration
import com.android.tools.preview.PreviewDisplaySettings
import com.android.tools.preview.config.PARAMETER_HEIGHT_DP
import com.android.tools.preview.config.PARAMETER_WIDTH_DP
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UMethod

private const val GLANCE_PREVIEW_ANNOTATION_NAME = "Preview"
private const val GLANCE_PREVIEW_ANNOTATION_FQN =
  "androidx.glance.preview.$GLANCE_PREVIEW_ANNOTATION_NAME"

/** Returns true if this [UAnnotation] is a Glance @Preview annotation. */
private fun isGlancePreview(annotation: UAnnotation) =
  ReadAction.compute<Boolean, Throwable> {
    GLANCE_PREVIEW_ANNOTATION_FQN == annotation.qualifiedName
  }

/** Returns the sequence of Glance preview elements for the given [methods]. */
private fun toGlancePreviewElements(methods: List<UMethod>): Sequence<PsiGlancePreviewElement> =
  methods
    .flatMap { method ->
      val uClass = method.uastParent as UClass
      val methodFqn = "${uClass.qualifiedName}.${method.name}"
      method.uAnnotations
        .filter { isGlancePreview(it) }
        .map {
          val displaySettings = PreviewDisplaySettings(method.name, null, false, false, null)
          val defaultValues = runReadAction { it.findPreviewDefaultValues() }
          val widthDp =
            it.findAttributeValue(PARAMETER_WIDTH_DP)?.evaluate() as? Int
              ?: defaultValues[PARAMETER_WIDTH_DP]?.toIntOrNull()
          val heightDp =
            it.findAttributeValue(PARAMETER_HEIGHT_DP)?.evaluate() as? Int
              ?: defaultValues[PARAMETER_HEIGHT_DP]?.toIntOrNull()
          GlancePreviewElement(
            displaySettings,
            it.toSmartPsiPointer(),
            method.uastBody.toSmartPsiPointer(),
            methodFqn,
            PreviewConfiguration.cleanAndGet(width = widthDp, height = heightDp),
          )
        }
    }
    .asSequence()

/** Common class to find Glance preview elements. */
open class GlancePreviewElementFinder : FilePreviewElementFinder<PsiGlancePreviewElement> {
  private val methodsToElements: (List<UMethod>) -> Sequence<PsiGlancePreviewElement> = {
    toGlancePreviewElements(it)
  }

  override suspend fun findPreviewElements(project: Project, vFile: VirtualFile) =
    findAnnotatedMethodsValues(
      project,
      vFile,
      GLANCE_PREVIEW_ANNOTATION_FQN,
      GLANCE_PREVIEW_ANNOTATION_NAME,
      ::isGlancePreview,
      methodsToElements,
    )

  override suspend fun hasPreviewElements(project: Project, vFile: VirtualFile): Boolean {
    val psiFile = AndroidPsiUtils.getPsiFileSafely(project, vFile) ?: return false
    if (
      readAction {
        psiFile.viewProvider.document?.charsSequence?.contains(GLANCE_PREVIEW_ANNOTATION_NAME)
      } == false
    )
      return false
    return hasAnnotation(
      project,
      vFile,
      GLANCE_PREVIEW_ANNOTATION_FQN,
      GLANCE_PREVIEW_ANNOTATION_NAME,
      ::isGlancePreview,
    )
  }
}

/** Object that finds Glance App Widget preview elements in the (Kotlin) file. */
object AppWidgetPreviewElementFinder : GlancePreviewElementFinder()
