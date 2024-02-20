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
import com.android.annotations.concurrency.Slow
import com.android.ide.common.resources.Locale
import com.android.tools.idea.AndroidPsiUtils
import com.android.tools.idea.preview.annotations.NodeInfo
import com.android.tools.idea.preview.annotations.UAnnotationSubtreeInfo
import com.android.tools.idea.preview.annotations.findAllAnnotationsInGraph
import com.android.tools.idea.preview.FilePreviewElementFinder
import com.android.tools.idea.preview.findPreviewDefaultValues
import com.android.tools.idea.preview.qualifiedName
import com.android.tools.idea.preview.toSmartPsiPointer
import com.android.tools.preview.PreviewDisplaySettings
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.text.nullize
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.evaluateString
import org.jetbrains.uast.toUElement

private const val TILE_PREVIEW_ANNOTATION_NAME = "Preview"
private const val TILE_PREVIEW_ANNOTATION_FQ_NAME =
  "androidx.wear.tiles.tooling.preview.$TILE_PREVIEW_ANNOTATION_NAME"
private const val TILE_PREVIEW_DATA_FQ_NAME = "androidx.wear.tiles.tooling.preview.TilePreviewData"

/** Object that can detect wear tile preview elements in a file. */
internal object WearTilePreviewElementFinder : FilePreviewElementFinder<WearTilePreviewElement> {
  override suspend fun hasPreviewElements(project: Project, vFile: VirtualFile): Boolean {
    return findUMethodsWithTilePreviewSignature(project, vFile).any {
      it.findAllTilePreviewAnnotations().any()
    }
  }

  override suspend fun findPreviewElements(
    project: Project,
    vFile: VirtualFile,
  ): Collection<WearTilePreviewElement> {
    return findUMethodsWithTilePreviewSignature(project, vFile)
      .flatMap { method ->
        ProgressManager.checkCanceled()
        method
          .findAllAnnotationsInGraph { it.isTilePreviewAnnotation() }
          .mapNotNull { it.asTilePreviewNode(method) }
      }
      .distinct()
  }
}

private fun UAnnotation.isTilePreviewAnnotation() = runReadAction {
  this.qualifiedName == TILE_PREVIEW_ANNOTATION_FQ_NAME
}

private fun NodeInfo<UAnnotationSubtreeInfo>.asTilePreviewNode(
  uMethod: UMethod
): WearTilePreviewElement? {
  val annotation = element as UAnnotation
  if (!annotation.isTilePreviewAnnotation()) return null
  val defaultValues = runReadAction { annotation.findPreviewDefaultValues() }

  val displaySettings = runReadAction {
    val name = annotation.findAttributeValue("name")?.evaluateString()?.nullize()
    val group = annotation.findAttributeValue("group")?.evaluateString()?.nullize()
    val previewName = name?.let { "${uMethod.name} - $name" } ?: uMethod.name
    PreviewDisplaySettings(
      name = previewName,
      group = group,
      showDecoration = false,
      showBackground = true,
      backgroundColor = DEFAULT_WEAR_TILE_BACKGROUND,
    )
  }

  val configuration = runReadAction {
    val device =
      annotation.findAttributeValue("device")?.evaluateString()?.nullize()
        ?: defaultValues["device"]
    val locale =
      (annotation.findAttributeValue("locale")?.evaluateString() ?: defaultValues["device"])
        ?.nullize()
    val fontScale =
      annotation.findAttributeValue("fontScale")?.evaluate() as? Float
        ?: defaultValues["fontScale"]?.toFloatOrNull()

    WearTilePreviewConfiguration.forValues(
      device = device,
      locale = locale?.let { Locale.create(it) },
      fontScale = fontScale,
    )
  }

  return WearTilePreviewElement(
    displaySettings = displaySettings,
    previewElementDefinitionPsi =
      runReadAction { (subtreeInfo?.topLevelAnnotation ?: annotation).toSmartPsiPointer() },
    previewBodyPsi = runReadAction { uMethod.uastBody.toSmartPsiPointer() },
    methodFqn = runReadAction { uMethod.qualifiedName },
    configuration = configuration,
  )
}

@Slow
private suspend fun findUMethodsWithTilePreviewSignature(
  project: Project,
  vFile: VirtualFile,
): List<UMethod> {
  val psiFile = AndroidPsiUtils.getPsiFileSafely(project, vFile) ?: return emptyList()
  return smartReadAction(project) {
    PsiTreeUtil.findChildrenOfAnyType(psiFile, PsiMethod::class.java, KtNamedFunction::class.java)
      .mapNotNull { it.toUElement(UMethod::class.java) }
      .filter { it.hasTilePreviewSignature() }
  }
}

private fun UMethod.findAllTilePreviewAnnotations() = findAllAnnotationsInGraph {
  it.isTilePreviewAnnotation()
}

@RequiresReadLock
private fun UMethod?.hasTilePreviewSignature(): Boolean {
  if (this == null) return false
  if (this.returnType?.equalsToText(TILE_PREVIEW_DATA_FQ_NAME) != true) return false

  val hasNoParameters = uastParameters.isEmpty()
  val hasContextParameter =
    uastParameters.size == 1 &&
      uastParameters.first().typeReference?.getQualifiedName() == SdkConstants.CLASS_CONTEXT
  return hasNoParameters || hasContextParameter
}
