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
import com.android.tools.idea.preview.buildParameterName
import com.android.tools.idea.preview.buildPreviewName
import com.android.tools.idea.preview.findPreviewDefaultValues
import com.android.tools.idea.preview.qualifiedName
import com.android.tools.idea.preview.toSmartPsiPointer
import com.android.tools.preview.PreviewConfiguration
import com.android.tools.preview.PreviewDisplaySettings
import com.android.tools.preview.config.PARAMETER_DEVICE
import com.android.tools.preview.config.PARAMETER_FONT_SCALE
import com.android.tools.preview.config.PARAMETER_GROUP
import com.android.tools.preview.config.PARAMETER_LOCALE
import com.android.tools.preview.config.PARAMETER_NAME
import com.android.utils.cache.ChangeTracker
import com.android.utils.cache.ChangeTrackerCachedValue
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.text.nullize
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.idea.KotlinLanguage
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

private val hasPreviewElementsCacheKey =
  Key<ChangeTrackerCachedValue<Boolean>>("hasPreviewElements")
private val previewElementsCacheKey =
  Key<ChangeTrackerCachedValue<Collection<PsiWearTilePreviewElement>>>("previewElements")
private val uMethodsWithTilePreviewSignatureCacheKey =
  Key<ChangeTrackerCachedValue<List<UMethod>>>("uMethodsWithTilePreviewSignature")

/**
 * Object that can detect wear tile preview elements in a file.
 *
 * @param findMethods a function that returns all [PsiMethod]s and [KtNamedFunction]s for a given
 *   [PsiFile]. The method will be invoked under a read lock.
 */
internal class WearTilePreviewElementFinder(
  @RequiresReadLock
  private val findMethods: (PsiFile?) -> Collection<PsiElement> = { psiFile ->
    PsiTreeUtil.findChildrenOfAnyType(psiFile, PsiMethod::class.java, KtNamedFunction::class.java)
  }
) : FilePreviewElementFinder<PsiWearTilePreviewElement> {

  /**
   * Checks if a given [vFile] contains any [PsiWearTilePreviewElement]s. Results of this method
   * will be cached until there are changes to any java or kotlin files in the given [project] or
   * when there are smart mode changes to the [project]. It's also possible for the cached value to
   * be garbage collected, in which case the results will be recomputed.
   */
  override suspend fun hasPreviewElements(project: Project, vFile: VirtualFile): Boolean {
    val cachedValue =
      vFile.getOrCreateCachedValue(hasPreviewElementsCacheKey) {
        ChangeTrackerCachedValue.softReference()
      }
    return ChangeTrackerCachedValue.get(
      cachedValue,
      {
        findUMethodsWithTilePreviewSignature(project, vFile, findMethods).any {
          it.findAllTilePreviewAnnotations().any()
        }
      },
      project.javaKotlinAndDumbChangeTrackers(),
    )
  }

  /**
   * Retrieves all [PsiWearTilePreviewElement] in a given [vFile]. Results of this method will be
   * cached until there are changes to any java or kotlin files in the given [project] or when there
   * are smart mode changes to the [project]. It's also possible for the cached value to be garbage
   * collected, in which case the results will be recomputed.
   */
  override suspend fun findPreviewElements(
    project: Project,
    vFile: VirtualFile,
  ): Collection<PsiWearTilePreviewElement> {
    val cachedValue =
      vFile.getOrCreateCachedValue(previewElementsCacheKey) {
        ChangeTrackerCachedValue.weakReference()
      }
    return ChangeTrackerCachedValue.get(
      cachedValue,
      {
        findUMethodsWithTilePreviewSignature(project, vFile, findMethods)
          .flatMap { method ->
            ProgressManager.checkCanceled()
            method
              .findAllAnnotationsInGraph { it.isTilePreviewAnnotation() }
              .mapNotNull { it.asTilePreviewNode(method) }
          }
          .distinct()
      },
      project.javaKotlinAndDumbChangeTrackers(),
    )
  }
}

/**
 * Returns true if a [UMethod] or [UAnnotation] is not null is annotated with a Tile Preview
 * annotation, either directly or through a Multi-Preview annotation.
 */
fun UElement?.hasTilePreviewAnnotation(): Boolean {
  assert(this is UMethod? || this is UAnnotation?) {
    "The UElement should be either a UMethod or a UAnnotation"
  }
  return this?.findAllAnnotationsInGraph { it.isTilePreviewAnnotation() }?.any() ?: false
}

internal fun UAnnotation.isTilePreviewAnnotation() = runReadAction {
  this.qualifiedName == TILE_PREVIEW_ANNOTATION_FQ_NAME
}

/** Returns true if the [UElement] is a `@Preview` annotation */
private fun UElement?.isWearTilePreviewAnnotation() =
  (this as? UAnnotation)?.isTilePreviewAnnotation() == true

@Slow
private fun NodeInfo<UAnnotationSubtreeInfo>.asTilePreviewNode(
  uMethod: UMethod
): PsiWearTilePreviewElement? {
  val annotation = element as UAnnotation
  if (!annotation.isTilePreviewAnnotation()) return null
  val defaultValues = runReadAction { annotation.findPreviewDefaultValues() }

  val displaySettings = runReadAction {
    val name = annotation.findAttributeValue(PARAMETER_NAME)?.evaluateString()?.nullize()
    val group = annotation.findAttributeValue(PARAMETER_GROUP)?.evaluateString()?.nullize()
    PreviewDisplaySettings(
      buildPreviewName(
        methodName = uMethod.name,
        nameParameter = name,
        isPreviewAnnotation = UElement?::isWearTilePreviewAnnotation,
      ),
      baseName = uMethod.name,
      parameterName =
        buildParameterName(
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
      annotation.findAttributeValue(PARAMETER_DEVICE)?.evaluateString()?.nullize()
        ?: defaultValues[PARAMETER_DEVICE]
    val locale =
      (annotation.findAttributeValue(PARAMETER_LOCALE)?.evaluateString()?.nullize()
        ?: defaultValues[PARAMETER_LOCALE])
    val fontScale =
      annotation.findAttributeValue(PARAMETER_FONT_SCALE)?.evaluate() as? Float
        ?: defaultValues[PARAMETER_FONT_SCALE]?.toFloatOrNull()

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

/**
 * Retrieves all [UMethod]s in a given [virtualFile] that have a Tile Preview signature. Results of
 * this method will be cached until there are changes to any java or kotlin files in the given
 * [project] or when there are smart mode changes to the [project]. It's also possible for the
 * cached value to be garbage collected, in which case the results will be recomputed.
 *
 * @see isMethodWithTilePreviewSignature for details on what a tile preview signature should be
 */
@Slow
private suspend fun findUMethodsWithTilePreviewSignature(
  project: Project,
  virtualFile: VirtualFile,
  @RequiresReadLock findMethods: (PsiFile?) -> Collection<PsiElement>,
): List<UMethod> {
  val cachedValue =
    virtualFile.getOrCreateCachedValue(uMethodsWithTilePreviewSignatureCacheKey) {
      ChangeTrackerCachedValue.weakReference()
    }
  return ChangeTrackerCachedValue.get(
    cachedValue,
    { findUMethodsWithTilePreviewSignatureNonCached(project, virtualFile, findMethods) },
    project.javaKotlinAndDumbChangeTrackers(),
  )
}

@Slow
private suspend fun findUMethodsWithTilePreviewSignatureNonCached(
  project: Project,
  virtualFile: VirtualFile,
  @RequiresReadLock findMethods: (PsiFile?) -> Collection<PsiElement>,
): List<UMethod> {
  val pointerManager = SmartPointerManager.getInstance(project)
  return smartReadAction(project) {
      if (!virtualFile.isValid) {
        return@smartReadAction emptyList()
      }
      findMethods(virtualFile.toPsiFile(project))
        .filter { it.isValid }
        .map {
          ProgressManager.checkCanceled()
          pointerManager.createSmartPsiElementPointer(it)
        }
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
  ProgressManager.checkCanceled()
  val hasValidReturnType =
    when (val sourcePsi = this) {
      is PsiMethod -> sourcePsi.returnType?.equalsToText(TILE_PREVIEW_DATA_FQ_NAME) == true
      is KtNamedFunction -> {
        analyze(sourcePsi) {
          val symbol = sourcePsi.symbol
          val returnType = symbol.returnType as? KaClassType
          returnType?.classId?.asSingleFqName()?.asString() == TILE_PREVIEW_DATA_FQ_NAME
        }
      }
      else -> false
    }

  if (!hasValidReturnType) {
    return false
  }

  ProgressManager.checkCanceled()
  val hasNoParameters =
    when (this) {
      is PsiMethod -> !hasParameters()
      is KtNamedFunction -> getValueParameters().isEmpty()
      else -> false
    }
  if (hasNoParameters) {
    return true
  }

  ProgressManager.checkCanceled()
  val hasSingleContextParameter =
    when (this) {
      is PsiMethod ->
        parameterList.parametersCount == 1 &&
          parameterList.getParameter(0)?.type?.equalsToText(SdkConstants.CLASS_CONTEXT) == true
      is KtNamedFunction -> {
        val typeReference = valueParameters.singleOrNull()?.typeReference
        if (typeReference != null) {
          analyze(typeReference) {
            val ktType = typeReference.type as? KaClassType
            ktType?.classId?.asSingleFqName()?.asString() == SdkConstants.CLASS_CONTEXT
          }
        } else false
      }
      else -> false
    }
  return hasSingleContextParameter
}

private fun <T> VirtualFile.getOrCreateCachedValue(
  key: Key<ChangeTrackerCachedValue<T>>,
  create: () -> ChangeTrackerCachedValue<T>,
) = getUserData(key) ?: create().also { putUserData(key, it) }

private fun Project.javaKotlinAndDumbChangeTrackers() =
  ChangeTracker(
    ChangeTracker {
      PsiModificationTracker.getInstance(this)
        .forLanguages { lang ->
          lang.`is`(KotlinLanguage.INSTANCE) || lang.`is`(JavaLanguage.INSTANCE)
        }
        .modificationCount
    },
    ChangeTracker { DumbService.getInstance(this).modificationTracker.modificationCount },
  )
