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

import com.android.annotations.concurrency.Slow
import com.android.tools.compose.COMPOSABLE_ANNOTATION_FQ_NAME
import com.android.tools.compose.COMPOSABLE_ANNOTATION_NAME
import com.android.tools.idea.preview.AnnotationPreviewNameHelper
import com.android.tools.idea.preview.FilePreviewElementFinder
import com.android.tools.idea.preview.find.NodeInfo
import com.android.tools.idea.preview.find.UAnnotationSubtreeInfo
import com.android.tools.idea.preview.find.findAllAnnotationsInGraph
import com.android.tools.idea.preview.find.findAnnotatedMethodsValues
import com.android.tools.idea.preview.find.getContainingUMethodAnnotatedWith
import com.android.tools.idea.preview.findPreviewDefaultValues
import com.android.tools.idea.preview.toSmartPsiPointer
import com.android.tools.preview.PreviewConfiguration
import com.android.tools.preview.PreviewDisplaySettings
import com.android.tools.preview.config.PARAMETER_HEIGHT_DP
import com.android.tools.preview.config.PARAMETER_WIDTH_DP
import com.intellij.openapi.application.readAction
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.mapNotNull
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod

private const val GLANCE_PREVIEW_ANNOTATION_NAME = "Preview"
internal const val GLANCE_PREVIEW_ANNOTATION_FQN =
  "androidx.glance.preview.$GLANCE_PREVIEW_ANNOTATION_NAME"

/**
 * Returns true if this [UAnnotation] is a Glance @Preview annotation.
 *
 * This method must be called under a read lock.
 */
@RequiresReadLock
private fun isGlancePreview(annotation: UAnnotation) =
  GLANCE_PREVIEW_ANNOTATION_FQN == annotation.qualifiedName

/**
 * Returns true if the [UElement] is a `@Preview` annotation.
 *
 * This method must be called under a read lock.
 */
@RequiresReadLock
private fun UElement?.isGlancePreviewAnnotation() =
  (this as? UAnnotation)?.let { isGlancePreview(it) } == true

@Slow
private suspend fun NodeInfo<UAnnotationSubtreeInfo>.asGlancePreviewNode(
  uMethod: UMethod
): PsiGlancePreviewElement? {
  val annotation = element as UAnnotation
  if (readAction { !isGlancePreview(annotation) }) return null

  val uClass = uMethod.uastParent as UClass
  val methodFqn = "${uClass.qualifiedName}.${uMethod.name}"
  val nameHelper =
    AnnotationPreviewNameHelper.create(this, uMethod.name) {
      readAction { isGlancePreviewAnnotation() }
    }
  val displaySettings =
    PreviewDisplaySettings(
      nameHelper.buildPreviewName(),
      uMethod.name,
      nameHelper.buildParameterName(),
      null,
      false,
      false,
      null,
    )
  val defaultValues = readAction { annotation.findPreviewDefaultValues() }
  val widthDp =
    annotation.findAttributeValue(PARAMETER_WIDTH_DP)?.evaluate() as? Int
      ?: defaultValues[PARAMETER_WIDTH_DP]?.toIntOrNull()
  val heightDp =
    annotation.findAttributeValue(PARAMETER_HEIGHT_DP)?.evaluate() as? Int
      ?: defaultValues[PARAMETER_HEIGHT_DP]?.toIntOrNull()
  return GlancePreviewElement(
    displaySettings,
    (subtreeInfo?.topLevelAnnotation ?: annotation).toSmartPsiPointer(),
    uMethod.uastBody.toSmartPsiPointer(),
    methodFqn,
    PreviewConfiguration.cleanAndGet(width = widthDp, height = heightDp),
  )
}

/** Common class to find Glance preview elements. */
open class GlancePreviewElementFinder : FilePreviewElementFinder<PsiGlancePreviewElement> {
  /**
   * Returns a [Collection] of all the Glance Preview elements in the [vFile]. Glance Preview
   * elements are `@Composable` functions that are also tagged with `@Preview` or a MultiPreview. A
   * `@Composable` function tagged with many `@Preview` or with a MultiPreview annotation can return
   * multiple preview elements.
   */
  override suspend fun findPreviewElements(project: Project, vFile: VirtualFile) =
    findAnnotatedMethodsValues(
        project,
        vFile,
        COMPOSABLE_ANNOTATION_FQ_NAME,
        COMPOSABLE_ANNOTATION_NAME,
      ) { methods ->
        methods.asFlow().flatMapConcat { method ->
          method
            .findAllAnnotationsInGraph(filter = { readAction { isGlancePreview(it) } })
            .mapNotNull { it.asGlancePreviewNode(method) }
        }
      }
      .distinct()

  override suspend fun hasPreviewElements(project: Project, vFile: VirtualFile) =
    findPreviewElements(project, vFile).any()
}

/** Object that finds Glance App Widget preview elements in the (Kotlin) file. */
object AppWidgetPreviewElementFinder : GlancePreviewElementFinder()

/**
 * Returns true if this is not a Preview annotation, but a MultiPreview annotation, i.e. an
 * annotation that is annotated with @Preview or with other MultiPreview.
 */
@RequiresReadLock
@RequiresBackgroundThread
fun isMultiPreviewAnnotation(annotation: UAnnotation) =
  !isGlancePreview(annotation) &&
    annotation.getContainingUMethodAnnotatedWith(COMPOSABLE_ANNOTATION_FQ_NAME) != null &&
    // TODO(b/381827960): avoid using runBlockingCancellable
    runBlockingCancellable {
      annotation.findAllAnnotationsInGraph(filter = ::isGlancePreview).firstOrNull() != null
    }
