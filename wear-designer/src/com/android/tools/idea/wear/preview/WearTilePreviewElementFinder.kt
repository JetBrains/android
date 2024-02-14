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
package com.android.tools.idea.wear.preview

import com.android.SdkConstants
import com.android.ide.common.resources.Locale
import com.android.tools.idea.annotations.findAnnotatedMethodsValues
import com.android.tools.idea.annotations.getContainingUMethodAnnotatedWith
import com.android.tools.idea.annotations.hasAnnotation
import com.android.tools.idea.annotations.isAnnotatedWith
import com.android.tools.idea.preview.FilePreviewElementFinder
import com.android.tools.idea.preview.findPreviewDefaultValues
import com.android.tools.idea.preview.qualifiedName
import com.android.tools.idea.preview.toSmartPsiPointer
import com.android.tools.preview.PreviewDisplaySettings
import com.intellij.codeInspection.findAnnotations
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.text.nullize
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.evaluateString

private const val TILE_PREVIEW_ANNOTATION_NAME = "Preview"
private const val TILE_PREVIEW_ANNOTATION_FQ_NAME =
  "androidx.wear.tiles.tooling.preview.$TILE_PREVIEW_ANNOTATION_NAME"
private const val TILE_PREVIEW_DATA_FQ_NAME = "androidx.wear.tiles.tooling.preview.TilePreviewData"

/** Object that can detect wear tile preview elements in a file. */
internal object WearTilePreviewElementFinder : FilePreviewElementFinder<WearTilePreviewElement> {
  override suspend fun hasPreviewElements(project: Project, vFile: VirtualFile): Boolean {
    return hasAnnotation(
      project = project,
      vFile = vFile,
      annotationFqn = TILE_PREVIEW_ANNOTATION_FQ_NAME,
      shortAnnotationName = TILE_PREVIEW_ANNOTATION_NAME,
      filter = {
        val uMethod = it.getContainingUMethodAnnotatedWith(TILE_PREVIEW_ANNOTATION_FQ_NAME)
        uMethod.isTilePreview()
      },
    )
  }

  override suspend fun findPreviewElements(
    project: Project,
    vFile: VirtualFile,
  ): Collection<WearTilePreviewElement> {
    return findAnnotatedMethodsValues(
      project = project,
      vFile = vFile,
      annotationFqn = TILE_PREVIEW_ANNOTATION_FQ_NAME,
      shortAnnotationName = TILE_PREVIEW_ANNOTATION_NAME,
    ) { methods ->
      val tilePreviewNodes = getTilePreviewNodes(methods)
      val previewElements = tilePreviewNodes.distinct()
      previewElements.asSequence()
    }
  }

  private fun getTilePreviewNodes(methods: List<UMethod>) =
    methods.flatMap {
      ProgressManager.checkCanceled()
      getTilePreviewNodes(it)
    }

  private fun getTilePreviewNodes(uMethod: UMethod): List<WearTilePreviewElement> = runReadAction {
    if (!uMethod.isTilePreview()) return@runReadAction emptyList()

    uMethod.findAnnotations(TILE_PREVIEW_ANNOTATION_FQ_NAME).mapNotNull {
      it.getTilePreviewNode(uMethod)
    }
  }
}

private fun UAnnotation.getTilePreviewNode(uMethod: UMethod): WearTilePreviewElement? {
  if (qualifiedName != TILE_PREVIEW_ANNOTATION_FQ_NAME) return null
  val defaultValues = findPreviewDefaultValues()

  val device = findAttributeValue("device")?.evaluateString()?.nullize() ?: defaultValues["device"]
  val locale =
    (findAttributeValue("locale")?.evaluateString() ?: defaultValues["device"])?.nullize()
  val fontScale =
    findAttributeValue("fontScale")?.evaluate() as? Float
      ?: defaultValues["fontScale"]?.toFloatOrNull()
  val name = findAttributeValue("name")?.evaluateString()?.nullize()
  val group = findAttributeValue("group")?.evaluateString()?.nullize()

  val previewName = name?.let { "${uMethod.name} - $name" } ?: uMethod.name

  val displaySettings =
    PreviewDisplaySettings(
      name = previewName,
      group = group,
      showDecoration = false,
      showBackground = true,
      backgroundColor = DEFAULT_WEAR_TILE_BACKGROUND,
    )

  return WearTilePreviewElement(
    displaySettings = displaySettings,
    previewElementDefinitionPsi = this.toSmartPsiPointer(),
    previewBodyPsi = uMethod.uastBody.toSmartPsiPointer(),
    methodFqn = uMethod.qualifiedName,
    configuration =
      WearTilePreviewConfiguration.forValues(
        device = device,
        locale = locale?.let { Locale.create(it) },
        fontScale = fontScale,
      ),
  )
}

private fun UMethod?.isTilePreview(): Boolean {
  if (this == null) return false
  if (!this.isAnnotatedWith(TILE_PREVIEW_ANNOTATION_FQ_NAME)) return false
  if (this.returnType?.equalsToText(TILE_PREVIEW_DATA_FQ_NAME) != true) return false

  val hasNoParameters = uastParameters.isEmpty()
  val hasContextParameter =
    uastParameters.size == 1 &&
      uastParameters.first().typeReference?.getQualifiedName() == SdkConstants.CLASS_CONTEXT
  return hasNoParameters || hasContextParameter
}
