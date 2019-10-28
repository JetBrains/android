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

import com.android.annotations.concurrency.WorkerThread
import com.android.tools.idea.compose.preview.renderer.renderPreviewElement
import com.android.tools.idea.flags.StudioFlags
import com.android.utils.reflection.qualifiedName
import com.intellij.codeInsight.documentation.DocumentationManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.codeInsight.lookup.impl.LookupManagerImpl
import com.intellij.lang.documentation.CompositeDocumentationProvider
import com.intellij.lang.documentation.DocumentationProviderEx
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.android.compose.isComposableFunction
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.findAnnotation
import org.jetbrains.kotlin.kdoc.psi.impl.KDocName
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.uast.UClass
import org.jetbrains.uast.toUElement
import java.awt.Image
import java.awt.image.BufferedImage
import java.util.concurrent.CompletableFuture

/**
 * Adds rendered image of sample@ to Compose element's documentation.
 */
class AndroidComposeDocumentationProvider : DocumentationProviderEx() {
  companion object {
    private val previewImageKey: Key<CachedValue<CompletableFuture<BufferedImage?>>> = Key.create(::previewImageKey.qualifiedName)
  }

  @WorkerThread
  override fun generateDoc(element: PsiElement?, originalElement: PsiElement?): String? {
    if (!StudioFlags.COMPOSE_RENDER_SAMPLE_IN_DOCUMENTATION.get()) return null

    if (element == null || !element.isComposableFunction()) return null

    val previewElement = getPreviewElement(element) ?: return null

    val future = CachedValuesManager.getCachedValue(previewElement, previewImageKey) {
      CachedValueProvider.Result.create(renderImage(previewElement), PsiModificationTracker.MODIFICATION_COUNT)
    }

    if (future.isDone) {
      val image = future.get() ?: return null
      val originalDoc = getOriginalDoc(element, originalElement)
      return originalDoc + getImageTag(previewElement.name!!, image)
    }
    future.whenComplete { image, _ ->
      if (image == null) return@whenComplete
      ApplicationManager.getApplication().executeOnPooledThread {
        runReadAction {
          if (StudioFlags.COMPOSE_RENDER_SAMPLE_IN_DOCUMENTATION_SLOW.get()) {
            Thread.sleep(3000)
          }
          if ((LookupManagerImpl.getInstance(element.project).activeLookup as? LookupImpl)?.currentItem?.psiElement == element) {
            DocumentationManager.getInstance(element.project).showJavaDocInfo(element, originalElement)
          }
        }
      }
    }

    return null
  }

  private fun renderImage(previewElement: KtNamedFunction): CompletableFuture<BufferedImage?> {
    val facet = AndroidFacet.getInstance(previewElement) ?: return CompletableFuture.completedFuture(null)
    val previewElementName = getFullNameForPreview(previewElement) ?: return CompletableFuture.completedFuture(null)
    return renderPreviewElement(facet, previewFromMethodName(previewElementName))
  }

  private val nullConfiguration = PreviewConfiguration.cleanAndGet(null, null, null, null, null)

  private fun previewFromMethodName(fqName: String) = PreviewElement("", fqName, null, null, nullConfiguration)

  /**
   * Returns KtNamedFunction after @sample tag in JavaDoc for given element or null if there is no such tag or function is not valid.
   *
   * Returned function is annotated with [androidx.ui.tooling.preview.Preview] annotation.
   */
  private fun getPreviewElement(element: PsiElement): KtNamedFunction? {
    val docComment = (element as? KtNamedFunction)?.docComment ?: return null
    val sampleTag = docComment.getDefaultSection().findTagByName("sample") ?: return null
    val sample = PsiTreeUtil.findChildOfType<KDocName>(sampleTag, KDocName::class.java)?.mainReference?.resolve() ?: return null
    return if (sample.isPreview()) sample as KtNamedFunction else null
  }

  private fun getFullNameForPreview(function: KtNamedFunction): String? = "${function.getClassName()}.${function.name}"

  private fun KtNamedFunction.getClassName(): String? = (toUElement()?.uastParent as? UClass)?.qualifiedName

  /**
   * Returns image tag that we add into documentation's html.
   *
   * We add "file" protocol in order to make swing go to cache for image (@see AndroidComposeDocumentationProvider.getLocalImageForElement).
   */
  private fun getImageTag(imageKey: String, i: BufferedImage) =
    "<img src='file://${imageKey}' alt='preview:${imageKey}' width='${i.width}' height='${i.height}'>"

  private fun PsiElement.isPreview() = this is KtNamedFunction &&
                                       annotationEntries.any { it.shortName?.asString() == PREVIEW_NAME } &&
                                       this.findAnnotation(FqName(PREVIEW_ANNOTATION_FQN)) != null

  private fun getOriginalDoc(element: PsiElement?, originalElement: PsiElement?): String? {
    val docProvider = DocumentationManager.getProviderFromElement(element, originalElement) as? CompositeDocumentationProvider
                      ?: return null
    val providers = docProvider.allProviders.asSequence().filter { it !is AndroidComposeDocumentationProvider }
    for (provider in providers) {
      val result = provider.generateDoc(element, originalElement)
      if (result != null) {
        return result
      }
    }
    return null
  }

  /**
   * Provides image for element's documentation.
   *
   * Before actual loading image from source swing tries to load image from cache.
   * Intellij provides such a cache for documentation by DocumentationManager.getElementImage that uses getLocalImageForElement.
   */
  override fun getLocalImageForElement(element: PsiElement, imageSpec: String): Image? {
    val previewPsiElement = getPreviewElement(element) as? PsiElement ?: return null
    val future: CompletableFuture<BufferedImage?> = previewPsiElement.getUserData(previewImageKey)?.value ?: return null
    return future.getNow(null)
  }
}
