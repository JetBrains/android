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
package com.android.tools.idea.res.completion

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.resources.ResourceResolver
import com.android.resources.ResourceType
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.rendering.GutterIconCache
import com.android.tools.idea.res.StudioResourceRepositoryManager
import com.android.tools.idea.res.getSourceAsVirtualFile
import com.android.tools.idea.util.androidFacet
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResult
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementDecorator
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.lookup.LookupElementRenderer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.android.AndroidAnnotatorUtil
import org.jetbrains.android.augment.ResourceLightField
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.kotlin.idea.base.util.module
import javax.swing.Icon

/**
 * [CompletionContributor] for Kotlin vs. Java-agnostic transformations and filtering of resources.
 *
 * Currently, does the following:
 * * Decorates [LookupElement]s for `Drawable` resources with an [javax.swing.Icon] rendered from
 *   the `Drawable`.
 */
class ResourceCompletionContributor : CompletionContributor() {
  override fun fillCompletionVariants(
    parameters: CompletionParameters,
    resultSet: CompletionResultSet
  ) {
    resultSet.runRemainingContributors(parameters) { completionResult ->
      transformCompletionResult(parameters.position.containingFile, completionResult)
        ?.let(resultSet::passResult)
    }
  }

  /**
   * Transforms [CompletionResult]s that already exist, potentially filtering them out by returning
   * `null`.
   */
  private fun transformCompletionResult(
    psiFile: PsiFile,
    completionResult: CompletionResult
  ): CompletionResult? {
    val lookupElement = completionResult.lookupElement

    // If there's no PsiElement, just leave the result alone.
    val psi = lookupElement.psiElement ?: return completionResult

    return when {
      shouldDecorateDrawable(psi) && StudioFlags.RENDER_DRAWABLES_IN_AUTOCOMPLETE_ENABLED.get() ->
        completionResult.withLookupElement(decorateDrawable(psiFile, lookupElement))

      // No transformation needed.
      else -> completionResult
    }
  }
}

private fun shouldDecorateDrawable(psiElement: PsiElement) =
  psiElement is ResourceLightField && psiElement.resourceType == ResourceType.DRAWABLE

/**
 * Returns [LookupElementDecorator] for decorating this [LookupElement].
 *
 * If the [Icon] for the Drawable is already available, this will be a
 * [FastDrawableResourceLookupElement], otherwise it will be a [SlowDrawableResourceLookupElement]
 * that actually renders the [Icon] on a background thread via [LookupElement.getExpensiveRenderer].
 */
private fun decorateDrawable(psiFile: PsiFile, original: LookupElement): LookupElement {
  val resourceLightField = original.psiElement as? ResourceLightField ?: return original
  val module = resourceLightField.module ?: return original
  val facet = module.androidFacet ?: return original
  val file =
    StudioResourceRepositoryManager.getInstance(module)
      ?.appResources
      ?.getResources(
        ResourceNamespace.RES_AUTO,
        resourceLightField.resourceType,
        resourceLightField.resourceName
      )
      ?.firstNotNullOfOrNull { it.getSourceAsVirtualFile() } ?: return original
  GutterIconCache.getInstance(module.project).getIconIfCached(file)?.let {
    return FastDrawableResourceLookupElement(original, it)
  }

  val resolver =
    AndroidAnnotatorUtil.pickConfiguration(psiFile.originalFile, facet)?.resourceResolver
      ?: return original
  return SlowDrawableResourceLookupElement(original, file, resolver, facet)
}

private class FastDrawableResourceLookupElement(original: LookupElement, private val icon: Icon) :
  LookupElementDecorator<LookupElement>(original) {
  override fun renderElement(presentation: LookupElementPresentation) {
    super.renderElement(presentation)
    presentation.icon = icon
  }
}

private class SlowDrawableResourceLookupElement(
  original: LookupElement,
  private val file: VirtualFile,
  private val resolver: ResourceResolver,
  private val facet: AndroidFacet,
) : LookupElementDecorator<LookupElement>(original) {
  override fun getExpensiveRenderer(): LookupElementRenderer<out LookupElement> =
    object : LookupElementRenderer<SlowDrawableResourceLookupElement>() {
      override fun renderElement(
        element: SlowDrawableResourceLookupElement,
        presentation: LookupElementPresentation
      ) {
        this@SlowDrawableResourceLookupElement.renderElement(presentation)
        GutterIconCache.getInstance(facet.module.project)
          .getIcon(file, resolver, facet)
          ?.let(presentation::setIcon)
      }
    }
}
