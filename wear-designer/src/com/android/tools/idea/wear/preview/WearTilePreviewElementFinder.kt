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
import com.android.tools.idea.preview.FilePreviewElementFinder
import com.android.tools.idea.preview.annotations.NodeInfo
import com.android.tools.idea.preview.annotations.UAnnotationSubtreeInfo
import com.android.tools.idea.preview.annotations.findAllAnnotationsInGraph
import com.android.tools.idea.preview.buildPreviewName
import com.android.tools.idea.preview.findPreviewDefaultValues
import com.android.tools.idea.preview.qualifiedName
import com.android.tools.idea.preview.toSmartPsiPointer
import com.android.tools.preview.PreviewConfiguration
import com.android.tools.preview.PreviewDisplaySettings
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.text.nullize
import org.jetbrains.kotlin.asJava.LightClassUtil
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.evaluateString
import org.jetbrains.uast.toUElement

private const val TILE_PREVIEW_ANNOTATION_NAME = "Preview"
const val TILE_PREVIEW_ANNOTATION_FQ_NAME =
  "androidx.wear.tiles.tooling.preview.$TILE_PREVIEW_ANNOTATION_NAME"
const val TILE_PREVIEW_DATA_FQ_NAME = "androidx.wear.tiles.tooling.preview.TilePreviewData"

/** Object that can detect wear tile preview elements in a file. */
internal object WearTilePreviewElementFinder : FilePreviewElementFinder<PsiWearTilePreviewElement> {
  override suspend fun hasPreviewElements(project: Project, vFile: VirtualFile): Boolean {
    return findUMethodsWithTilePreviewSignature(project, vFile).any {
      it.findAllTilePreviewAnnotations().any()
    }
  }

  override suspend fun findPreviewElements(
    project: Project,
    vFile: VirtualFile,
  ): Collection<PsiWearTilePreviewElement> {
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

/**
 * Returns true if a [UMethod] is not null is annotated with a Tile Preview annotation, either
 * directly or through a Multi-Preview annotation.
 */
fun UMethod?.hasTilePreviewAnnotation() =
  this?.findAllAnnotationsInGraph { it.isTilePreviewAnnotation() }?.any() ?: false

internal fun UAnnotation.isTilePreviewAnnotation() = runReadAction {
  this.qualifiedName == TILE_PREVIEW_ANNOTATION_FQ_NAME
}

/** Returns true if the [UElement] is a `@Preview` annotation */
private fun UElement?.isWearTilePreviewAnnotation() =
  (this as? UAnnotation)?.let { it.isTilePreviewAnnotation() } == true

@Slow
private fun NodeInfo<UAnnotationSubtreeInfo>.asTilePreviewNode(
  uMethod: UMethod
): PsiWearTilePreviewElement? {
  val annotation = element as UAnnotation
  if (!annotation.isTilePreviewAnnotation()) return null
  val defaultValues = runReadAction { annotation.findPreviewDefaultValues() }

  val displaySettings = runReadAction {
    val name = annotation.findAttributeValue("name")?.evaluateString()?.nullize()
    val group = annotation.findAttributeValue("group")?.evaluateString()?.nullize()
    PreviewDisplaySettings(
      buildPreviewName(
        methodName = uMethod.name,
        nameParameter = name,
        isPreviewAnnotation = UElement?::isWearTilePreviewAnnotation,
      ),
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

    PreviewConfiguration.cleanAndGet(device = device, locale = locale, fontScale = fontScale)
  }

  return PsiWearTilePreviewElement(
    displaySettings = displaySettings,
    previewElementDefinition =
      runReadAction { (subtreeInfo?.topLevelAnnotation ?: annotation).toSmartPsiPointer() },
    previewBody = runReadAction { uMethod.uastBody.toSmartPsiPointer() },
    methodFqn = runReadAction { uMethod.qualifiedName },
    configuration = configuration,
  )
}

@Slow
private suspend fun findUMethodsWithTilePreviewSignature(
  project: Project,
  vFile: VirtualFile,
): List<UMethod> {
  val pointerManager = SmartPointerManager.getInstance(project)
  return smartReadAction(project) {
      PsiTreeUtil.findChildrenOfAnyType(
          vFile.toPsiFile(project),
          PsiMethod::class.java,
          KtNamedFunction::class.java,
        )
        .map { pointerManager.createSmartPsiElementPointer(it) }
    }
    .filter { smartReadAction(project) { it.element?.isMethodWithTilePreviewSignature() } ?: false }
    .mapNotNull { smartReadAction(project) { it.element.toUElement(UMethod::class.java) } }
}

private fun UMethod.findAllTilePreviewAnnotations() = findAllAnnotationsInGraph {
  it.isTilePreviewAnnotation()
}

/**
 * Checks if a [PsiElement] is a method with the signature required for a Tile Preview. The expected
 * signature of a Tile Preview method is to have the return type [TILE_PREVIEW_DATA_FQ_NAME] and to
 * have either no parameters or single parameter of type [SdkConstants.CLASS_CONTEXT].
 *
 * To be considered a method, the [PsiElement] should be either a [PsiMethod] or a
 * [KtNamedFunction].
 */
@RequiresReadLock
internal fun PsiElement?.isMethodWithTilePreviewSignature(): Boolean {
  return when (this) {
    is PsiMethod -> hasTilePreviewSignature()
    is KtNamedFunction ->
      LightClassUtil.getLightClassMethod(this)?.hasTilePreviewSignature() ?: false
    else -> false
  }
}

@RequiresReadLock
private fun PsiMethod.hasTilePreviewSignature(): Boolean {
  if (returnType?.equalsToText(TILE_PREVIEW_DATA_FQ_NAME) != true) return false

  val hasNoParameters = !hasParameters()
  val hasSingleContextParameter =
    parameterList.parametersCount == 1 &&
      parameterList.getParameter(0)?.type?.equalsToText(SdkConstants.CLASS_CONTEXT) == true

  return hasNoParameters || hasSingleContextParameter
}
