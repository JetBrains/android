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
package com.android.tools.idea.dagger

import com.android.annotations.concurrency.WorkerThread
import com.android.tools.idea.dagger.concepts.ComponentDaggerElement
import com.android.tools.idea.dagger.concepts.ConsumerDaggerElementBase
import com.android.tools.idea.dagger.concepts.DaggerElement
import com.android.tools.idea.dagger.concepts.ModuleDaggerElement
import com.android.tools.idea.dagger.concepts.ProviderDaggerElement
import com.android.tools.idea.dagger.concepts.SubcomponentDaggerElement
import com.android.tools.idea.dagger.concepts.getDaggerElement
import com.android.tools.idea.dagger.localization.DaggerBundle
import com.google.common.base.Supplier
import com.google.common.base.Suppliers
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationUtil
import com.intellij.navigation.GotoRelatedItem
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiIdentifier
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.presentation.java.SymbolPresentationUtil
import com.intellij.ui.awt.RelativePoint
import icons.StudioIcons
import java.awt.event.MouseEvent
import javax.swing.Icon
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.kotlin.lexer.KtTokens

/**
 * Provides [RelatedItemLineMarkerInfo] for Dagger elements.
 *
 * Adds gutter icon that allows to navigate between Dagger elements.
 */
class DaggerRelatedItemLineMarkerProviderV2 : RelatedItemLineMarkerProvider() {

  @WorkerThread
  override fun collectNavigationMarkers(
    element: PsiElement,
    result: MutableCollection<in RelatedItemLineMarkerInfo<*>>
  ) {
    if (!element.isDaggerWithIndexEnabled()) return

    ProgressManager.checkCanceled()

    // Only leaf elements should be given markers; see `LineMarkerProvider.getLineMarkerInfo` for
    // details.
    if (!element.canReceiveLineMarker()) return

    // Since element is either an identifier or the `constructor` keyword, its parent is the
    // potential Dagger element.
    val daggerElement = element.parent.getDaggerElement() ?: return

    val gotoTargetsSupplier = Suppliers.memoize { daggerElement.getGotoItems() }
    val lineMarkerInfo =
      RelatedItemLineMarkerInfo(
        element,
        element.textRange,
        daggerElement.getIcon(),
        ::tooltipProvider,
        NavigationHandler(gotoTargetsSupplier),
        GutterIconRenderer.Alignment.RIGHT,
        gotoTargetsSupplier::get,
      )

    result.add(lineMarkerInfo)
  }

  companion object {
    /** Tooltip provider for related link marker. */
    @VisibleForTesting
    internal fun tooltipProvider(element: PsiElement) =
      DaggerBundle.message(
        "dependency.related.files.for",
        SymbolPresentationUtil.getSymbolPresentableText(element)
      )

    /** Given a [DaggerElement], find its related items. */
    private fun DaggerElement.getGotoItems(): List<GotoRelatedItem> =
      getRelatedDaggerElements().map { (relatedItem, relationName) ->
        GotoRelatedItem(relatedItem.psiElement, relationName)
      }

    /**
     * Returns true if element is Java/Kotlin identifier or kotlin "constructor" keyword.
     *
     * Only leaf elements can have a ItemLineMarkerInfo (see
     * [com.intellij.codeInsight.daemon.LineMarkerProvider.getLineMarkerInfo]). For Dagger, the leaf
     * elements we're interested in are either identifiers or the `constructor` keyword.
     */
    @VisibleForTesting
    internal fun PsiElement.canReceiveLineMarker() =
      when (this) {
        is PsiIdentifier -> true
        is LeafPsiElement ->
          this.elementType == KtTokens.CONSTRUCTOR_KEYWORD ||
            this.elementType == KtTokens.IDENTIFIER
        else -> false
      }

    /** Returns the gutter icon to use for a given Dagger element type. */
    private fun DaggerElement.getIcon(): Icon =
      when (this) {
        is ConsumerDaggerElementBase -> StudioIcons.Misc.DEPENDENCY_PROVIDER
        is ProviderDaggerElement,
        is ComponentDaggerElement,
        is SubcomponentDaggerElement,
        is ModuleDaggerElement -> StudioIcons.Misc.DEPENDENCY_CONSUMER
      }
  }

  /**
   * Navigation handler for when the gutter icon is clicked. Note that this is a bit of a misnomer
   * for our case, in that when [navigate] is called we actually pop open a menu rather than
   * navigating immediately.
   */
  private class NavigationHandler(private val targetsSupplier: Supplier<List<GotoRelatedItem>>) :
    GutterIconNavigationHandler<PsiElement> {
    override fun navigate(mouseEvent: MouseEvent, psiElement: PsiElement) {
      val gotoTargets = targetsSupplier.get()
      val displayLocation = RelativePoint(mouseEvent)
      if (gotoTargets.isNotEmpty()) {
        NavigationUtil.getRelatedItemsPopup(
            gotoTargets,
            DaggerBundle.message("dagger.related.items.popup.title")
          )
          .show(displayLocation)
      } else {
        JBPopupFactory.getInstance()
          .createMessage(DaggerBundle.message("dagger.related.items.none.found"))
          .show(displayLocation)
      }
    }
  }
}
