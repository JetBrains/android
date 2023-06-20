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

import com.android.tools.idea.annotations.findAnnotatedMethodsValues
import com.android.tools.idea.annotations.hasAnnotations
import com.android.tools.idea.preview.FilePreviewElementFinder
import com.android.tools.idea.preview.PreviewDisplaySettings
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.toUElementOfType

private const val GLANCE_PREVIEW_ANNOTATION_NAME = "GlancePreview"
private const val GLANCE_PREVIEW_ANNOTATION_FQN =
  "androidx.glance.preview.$GLANCE_PREVIEW_ANNOTATION_NAME"

private const val SURFACE_PARAM_NAME = "surface"
private const val APP_WIDGET_SURFACE = "AppWidget"
private const val TILE_SURFACE = "Tile"

private fun UElement?.toSmartPsiPointer(): SmartPsiElementPointer<PsiElement>? {
  val bodyPsiElement = this?.sourcePsi ?: return null
  return SmartPointerManager.createPointer(bodyPsiElement)
}

/**
 * Returns true if the [annotationEntry] annotation has [SURFACE_PARAM_NAME] parameter of
 * [surfaceName] value, false otherwise.
 */
private fun surfaceFilter(annotationEntry: UAnnotation?, surfaceName: String) =
  ReadAction.compute<Boolean, Throwable> {
    annotationEntry?.findAttributeValue(SURFACE_PARAM_NAME)?.evaluate() == surfaceName
  }

/**
 * Returns true if this [UAnnotation] is a @GlancePreview annotation for the [surfaceName] surface.
 */
private fun UAnnotation.isGlancePreview(surfaceName: String) =
  ReadAction.compute<Boolean, Throwable> {
    GLANCE_PREVIEW_ANNOTATION_FQN == qualifiedName && surfaceFilter(this, surfaceName)
  }

/**
 * Returns the sequence of the Glance preview elements for [surfaceName] surface from the [methods].
 */
private fun toGlancePreviewElements(
  methods: List<UMethod>,
  surfaceName: String
): Sequence<GlancePreviewElement> =
  methods
    .flatMap { method ->
      val uClass = method.uastParent as UClass
      val methodFqn = "${uClass.qualifiedName}.${method.name}"
      method.uAnnotations
        .filter { it.isGlancePreview(surfaceName) }
        .map {
          val displaySettings = PreviewDisplaySettings(method.name, null, false, false, null)
          GlancePreviewElement(
            displaySettings,
            it.toSmartPsiPointer(),
            method.uastBody.toSmartPsiPointer(),
            methodFqn
          )
        }
    }
    .asSequence()

/** Common class to find Glance preview elements for [surfaceName] surface. */
open class GlancePreviewElementFinder(private val surfaceName: String) :
  FilePreviewElementFinder<GlancePreviewElement> {
  private val glanceSurfaceUAnnotationFilter: (UAnnotation?) -> Boolean = {
    surfaceFilter(it, surfaceName)
  }

  private val glanceSurfaceKtAnnotationFilter: (KtAnnotationEntry) -> Boolean = {
    ReadAction.compute<Boolean, Throwable> {
      glanceSurfaceUAnnotationFilter(it.psiOrParent.toUElementOfType())
    }
  }

  private val methodsToElements: (List<UMethod>) -> Sequence<GlancePreviewElement> = {
    toGlancePreviewElements(it, surfaceName)
  }

  override suspend fun findPreviewElements(project: Project, vFile: VirtualFile) =
    findAnnotatedMethodsValues(
      project,
      vFile,
      setOf(GLANCE_PREVIEW_ANNOTATION_FQN),
      GLANCE_PREVIEW_ANNOTATION_NAME,
      glanceSurfaceUAnnotationFilter,
      methodsToElements
    )

  override suspend fun hasPreviewElements(project: Project, vFile: VirtualFile): Boolean {
    return hasAnnotations(
      project,
      vFile,
      setOf(GLANCE_PREVIEW_ANNOTATION_FQN),
      GLANCE_PREVIEW_ANNOTATION_NAME,
      glanceSurfaceKtAnnotationFilter
    )
  }
}

/** Object that finds Glance App Widget preview elements in the (Kotlin) file. */
object AppWidgetPreviewElementFinder : GlancePreviewElementFinder(APP_WIDGET_SURFACE)

/** Object that finds Glance Tile preview elements in the (Kotlin) file. */
object TilePreviewElementFinder : GlancePreviewElementFinder(TILE_SURFACE)
