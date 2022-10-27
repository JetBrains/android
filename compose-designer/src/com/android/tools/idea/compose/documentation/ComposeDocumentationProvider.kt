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
package com.android.tools.idea.compose.documentation

import com.android.annotations.concurrency.AnyThread
import com.android.annotations.concurrency.UiThread
import com.android.annotations.concurrency.WorkerThread
import com.android.tools.compose.COMPOSE_PREVIEW_ANNOTATION_FQN
import com.android.tools.compose.COMPOSE_PREVIEW_ANNOTATION_NAME
import com.android.tools.compose.isComposableFunction
import com.android.tools.idea.compose.preview.renderer.renderPreviewElement
import com.android.tools.idea.compose.preview.util.PreviewConfiguration
import com.android.tools.idea.compose.preview.util.SingleComposePreviewElementInstance
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.kotlin.getQualifiedName
import com.android.tools.idea.preview.PreviewDisplaySettings
import com.android.utils.reflection.qualifiedName
import com.google.common.annotations.VisibleForTesting
import com.intellij.codeInsight.documentation.DocumentationComponent
import com.intellij.codeInsight.documentation.DocumentationManager
import com.intellij.lang.documentation.CompositeDocumentationProvider
import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.lang.documentation.DocumentationProviderEx
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.editor.EditorMouseHoverPopupManager
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType
import com.intellij.ui.ColorUtil
import com.intellij.ui.popup.AbstractPopup
import java.awt.Image
import java.awt.image.BufferedImage
import java.util.concurrent.CompletableFuture
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.kotlin.asJava.findFacadeClass
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.findAnnotation
import org.jetbrains.kotlin.kdoc.psi.impl.KDocName
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction

/** Adds rendered image of sample@ to Compose element's documentation. */
class ComposeDocumentationProvider : DocumentationProviderEx() {
  companion object {
    private val previewImageKey: Key<CachedValue<CompletableFuture<BufferedImage?>>> =
      Key.create(Companion::previewImageKey.qualifiedName)
  }

  @AnyThread
  @VisibleForTesting
  fun generateDocAsync(
    element: PsiElement?,
    originalElement: PsiElement?
  ): CompletableFuture<String?> {
    if (!StudioFlags.COMPOSE_RENDER_SAMPLE_IN_DOCUMENTATION.get()) {
      return CompletableFuture.completedFuture(null)
    }

    val isComposableFunction =
      ReadAction.compute<Boolean, Throwable> {
        return@compute element != null && element.isValid && element.isComposableFunction()
      }
    if (!isComposableFunction) return CompletableFuture.completedFuture(null)

    val previewElement =
      getPreviewElement(element!!) ?: return CompletableFuture.completedFuture(null)

    val future =
      CachedValuesManager.getCachedValue(previewElement, previewImageKey) {
        CachedValueProvider.Result.create(
          renderImage(previewElement),
          PsiModificationTracker.MODIFICATION_COUNT
        )
      }

    return future.thenApply {
      if (!ReadAction.compute<Boolean, Throwable> { element.isValid }) return@thenApply null
      val originalDoc = getOriginalDoc(element, originalElement)
      if (it == null) {
        return@thenApply originalDoc
      }
      val previewElementName = ReadAction.compute<String, Throwable> { previewElement.name!! }
      originalDoc + getImageTag(previewElementName, it)
    }
  }

  /**
   * Updates documentation info for [element] if there is already opened documentation for [element]
   * .
   */
  @UiThread
  private fun updateDocumentation(element: PsiElement) {
    if (!element.isValid) return
    val manager = DocumentationManager.getInstance(element.project)
    val component: DocumentationComponent

    val hint = manager.docInfoHint as? AbstractPopup
    when {
      /**
       * Case when documentation is showed in a popup that was opened intentionally or during code
       * completion.
       */
      hint?.isVisible == true -> component = (hint.component as? DocumentationComponent?) ?: return

      /** Case when documentation is showed in a popup that was opened by a mouse hover action. */
      EditorMouseHoverPopupManager.getInstance().documentationComponent?.isShowing == true -> {
        component = EditorMouseHoverPopupManager.getInstance().documentationComponent ?: return
      }

      /** Case when documentation is showed in a pined tool window. */
      manager.hasActiveDockedDocWindow() -> {
        val toolWindow =
          ToolWindowManager.getInstance(element.project).getToolWindow(ToolWindowId.DOCUMENTATION)
            ?: return
        component =
          toolWindow.contentManager.selectedContent?.component as? DocumentationComponent ?: return
      }
      else -> return
    }

    if (component.isShowing && component.element == element) {
      manager.fetchDocInfo(element, component)
    }
  }

  @WorkerThread
  override fun generateDoc(element: PsiElement?, originalElement: PsiElement?): String? {
    val future = generateDocAsync(element, originalElement)

    if (future.isDone) {
      return future.get()
    }

    /** If we can't return result immediately we try to update documentation when we get it. */
    future.whenComplete { _, _ -> invokeLater { updateDocumentation(element!!) } }

    return null
  }

  private fun renderImage(previewElement: KtNamedFunction): CompletableFuture<BufferedImage?> {
    val facet =
      AndroidFacet.getInstance(previewElement) ?: return CompletableFuture.completedFuture(null)
    val previewElementName = getFullNameForPreview(previewElement)
    return renderPreviewElement(facet, previewFromMethodName(previewElementName)).whenComplete {
      _,
      _ ->
      if (StudioFlags.COMPOSE_RENDER_SAMPLE_IN_DOCUMENTATION_SLOW.get()) {
        Thread.sleep(3000)
      }
    }
  }

  private fun previewFromMethodName(fqName: String): SingleComposePreviewElementInstance {
    val scheme = EditorColorsManager.getInstance().globalScheme
    val background = scheme.getColor(EditorColors.DOCUMENTATION_COLOR) ?: scheme.defaultBackground
    return SingleComposePreviewElementInstance(
      composableMethodFqn = fqName,
      displaySettings =
        PreviewDisplaySettings(
          name = "",
          group = null,
          showBackground = true,
          showDecoration = false,
          backgroundColor = ColorUtil.toHtmlColor(background)
        ),
      previewElementDefinitionPsi = null,
      previewBodyPsi = null,
      configuration = PreviewConfiguration.cleanAndGet()
    )
  }

  /**
   * Returns KtNamedFunction after @sample tag in JavaDoc for given element or null if there is no
   * such tag or function is not valid.
   *
   * Returned function is annotated with `androidx.compose.ui.tooling.preview.Preview` annotation.
   */
  private fun getPreviewElement(element: PsiElement): KtNamedFunction? =
    ReadAction.compute<KtNamedFunction?, Throwable> {
      val docComment = (element as? KtNamedFunction)?.docComment ?: return@compute null
      val sampleTag = docComment.getDefaultSection().findTagByName("sample") ?: return@compute null
      val sample =
        PsiTreeUtil.findChildOfType(sampleTag, KDocName::class.java)?.mainReference?.resolve()
          ?: return@compute null
      return@compute if (sample.isPreview()) sample as KtNamedFunction else null
    }

  // Don't use AndroidKtPsiUtilsKt.getClassName as we need to use original file for a documentation
  // that shows during code completion.
  // During code completion as containingKtFile we have a copy of the original file. That copy
  // always returns null for findFacadeClass.
  private fun KtNamedFunction.getClassName(): String? {
    return if (isTopLevel)
      ((containingKtFile.originalFile as? KtFile)?.findFacadeClass())?.qualifiedName
    else parentOfType<KtClass>()?.getQualifiedName()
  }

  private fun getFullNameForPreview(function: KtNamedFunction): String =
    ReadAction.compute<String, Throwable> { "${function.getClassName()}.${function.name}" }

  /**
   * Returns image tag wrapped in div with DocumentationMarkup style settings that we add into
   * documentation's html.
   *
   * We add "file" protocol in order to make swing go to cache for image (@see
   * AndroidComposeDocumentationProvider.getLocalImageForElement).
   */
  private fun getImageTag(imageKey: String, i: BufferedImage) =
    DocumentationMarkup.CONTENT_START +
      "<img src='file://${imageKey}' alt='preview:${imageKey}' width='${i.width}' height='${i.height}'>" +
      DocumentationMarkup.CONTENT_END

  private fun PsiElement.isPreview() =
    this is KtNamedFunction &&
      annotationEntries.any { it.shortName?.asString() == COMPOSE_PREVIEW_ANNOTATION_NAME } &&
      this.findAnnotation(FqName(COMPOSE_PREVIEW_ANNOTATION_FQN)) != null

  private fun getOriginalDoc(element: PsiElement?, originalElement: PsiElement?): String? =
    ReadAction.compute<String?, Throwable> {
      if (element?.isValid != true) return@compute null
      val docProvider =
        DocumentationManager.getProviderFromElement(element, originalElement) as?
          CompositeDocumentationProvider
          ?: return@compute null
      val providers =
        docProvider.allProviders.asSequence().filter { it !is ComposeDocumentationProvider }
      for (provider in providers) {
        val result = provider.generateDoc(element, originalElement)
        if (result != null) {
          return@compute result
        }
      }
      return@compute null
    }

  /**
   * Provides image for element's documentation. The method will return a [CompletableFuture]. The
   * image might still be loading.
   *
   * Before actual loading image from source swing tries to load image from cache. Intellij provides
   * such a cache for documentation by DocumentationManager.getElementImage that uses
   * getLocalImageForElement.
   */
  @VisibleForTesting
  fun getLocalImageForElementAsync(element: PsiElement): CompletableFuture<BufferedImage?> {
    val previewPsiElement =
      getPreviewElement(element) as? PsiElement ?: return CompletableFuture.completedFuture(null)
    return previewPsiElement.getUserData(previewImageKey)?.value
      ?: return CompletableFuture.completedFuture(null)
  }

  /**
   * Provides image for element's documentation.
   *
   * Before actual loading image from source swing tries to load image from cache. Intellij provides
   * such a cache for documentation by DocumentationManager.getElementImage that uses
   * getLocalImageForElement.
   */
  override fun getLocalImageForElement(element: PsiElement, imageSpec: String): Image? =
    getLocalImageForElementAsync(element).getNow(null)
}
