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
import com.android.tools.idea.dagger.concepts.AssistedFactoryMethodDaggerElement
import com.android.tools.idea.dagger.concepts.ConsumerDaggerElementBase
import com.android.tools.idea.dagger.concepts.DaggerElement
import com.android.tools.idea.dagger.concepts.getDaggerElement
import com.android.tools.idea.dagger.localization.DaggerBundle
import com.google.wireless.android.sdk.stats.DaggerEditorEvent
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.codeInsight.navigation.getRelatedItemsPopup
import com.intellij.navigation.GotoRelatedItem
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiIdentifier
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.presentation.java.SymbolPresentationUtil
import com.intellij.ui.awt.RelativePoint
import icons.StudioIcons
import org.jetbrains.annotations.PropertyKey
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.kotlin.idea.base.plugin.suppressAndroidPlugin
import org.jetbrains.kotlin.lexer.KtTokens
import java.awt.event.MouseEvent
import javax.swing.Icon
import kotlin.system.measureTimeMillis

/**
 * Provides [RelatedItemLineMarkerInfo] for Dagger elements.
 *
 * Adds gutter icon that allows to navigate between Dagger elements.
 */
class DaggerRelatedItemLineMarkerProvider : RelatedItemLineMarkerProvider() {

  override fun getName(): String = DaggerBundle.message("dagger.related.items")

  override fun getId(): String =
    // A custom ID is required for isEnabledByDefault to be called
    "com.android.tools.idea.dagger.DaggerRelatedItemLineMarkerProvider"

  override fun getIcon(): Icon = StudioIcons.GutterIcons.DEPENDENCY_CONSUMER

  override fun isEnabledByDefault(): Boolean = true

  @WorkerThread
  override fun collectNavigationMarkers(
    element: PsiElement,
    result: MutableCollection<in RelatedItemLineMarkerInfo<*>>,
  ) {
    if (suppressAndroidPlugin()) return

    val metricsType: DaggerEditorEvent.ElementType
    val lineMarkerInfo: RelatedItemLineMarkerInfo<PsiElement>

    val elapsedTimeMillis = measureTimeMillis {
      ProgressManager.checkCanceled()
      if (!element.project.service<DaggerDependencyChecker>().isDaggerPresent()) return

      // Only leaf elements should be given markers; see `LineMarkerProvider.getLineMarkerInfo` for
      // details.
      if (!element.canReceiveLineMarker()) return

      // Since element is either an identifier or the `constructor` keyword, its parent is the
      // potential Dagger element.
      val daggerElement = element.parent.getDaggerElement() ?: return
      metricsType = daggerElement.metricsElementType

      val gotoItems = daggerElement.getGotoItems()
      if (gotoItems.isEmpty()) return

      lineMarkerInfo =
        RelatedItemLineMarkerInfo(
          element,
          element.textRange,
          daggerElement.getIcon(),
          { getTooltip(daggerElement.psiElement, gotoItems) },
          NavigationHandler(gotoItems, metricsType),
          GutterIconRenderer.Alignment.RIGHT,
          { gotoItems },
        )
    }

    element.project
      .service<DaggerAnalyticsTracker>()
      .trackGutterWasDisplayed(metricsType, elapsedTimeMillis)
    result.add(lineMarkerInfo)
  }

  companion object {
    private fun getTooltip(
      fromElement: PsiElement,
      gotoItems: List<GotoItemWithAnalytics>,
    ): String {
      val fromElementString = SymbolPresentationUtil.getSymbolPresentableText(fromElement)

      if (gotoItems.size == 1) {
        val gotoItem = gotoItems[0]
        val toElementString =
          gotoItem.customName
            ?: gotoItem.element?.let { SymbolPresentationUtil.getSymbolPresentableText(it) }
        if (toElementString != null) {
          return DaggerBundle.message(
            gotoItem.relationDescriptionKey,
            fromElementString,
            toElementString,
          )
        }
      }

      return DaggerBundle.message("dependency.related.files.for", fromElementString)
    }

    /**
     * Returns [GotoRelatedItem]s representing related [DaggerElement]s for a given source element.
     */
    @VisibleForTesting
    internal fun DaggerElement.getGotoItems(): List<GotoItemWithAnalytics> =
      getRelatedDaggerElements()
        .map { (relatedItem, relationName, relationDescriptionKey, customDisplayName) ->
          GotoItemWithAnalytics(
            this,
            relatedItem,
            relationName,
            relationDescriptionKey,
            customDisplayName,
          )
        }
        .sortedWith(
          // Goto items in the dropdown should be displayed in a predictable manner. Sort by groups
          // first, then within the groups sort by the displayed text.
          compareBy(
            GotoItemWithAnalytics::getGroup,
            { it.customName ?: it.element?.let(SymbolPresentationUtil::getSymbolPresentableText) },
          )
        )

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
        is AssistedFactoryMethodDaggerElement,
        is ConsumerDaggerElementBase -> StudioIcons.GutterIcons.DEPENDENCY_PROVIDER
        else -> StudioIcons.GutterIcons.DEPENDENCY_CONSUMER
      }
  }

  /**
   * Navigation handler for when the gutter icon is clicked. Note that this is a bit of a misnomer
   * for our case, in that when [navigate] is called we actually pop open a menu rather than
   * navigating immediately.
   */
  private class NavigationHandler(
    private val gotoItems: List<GotoRelatedItem>,
    private val metricsType: DaggerEditorEvent.ElementType,
  ) : GutterIconNavigationHandler<PsiElement> {
    override fun navigate(mouseEvent: MouseEvent, psiElement: PsiElement) {
      psiElement.project.service<DaggerAnalyticsTracker>().trackClickOnGutter(metricsType)

      if (gotoItems.size == 1) {
        gotoItems.first().navigate()
      } else {
        getRelatedItemsPopup(gotoItems, DaggerBundle.message("dagger.related.items.popup.title"))
          .show(RelativePoint(mouseEvent))
      }
    }
  }

  internal class GotoItemWithAnalytics(
    fromElement: DaggerElement,
    toElement: DaggerElement,
    group: String,
    @PropertyKey(resourceBundle = DaggerBundle.BUNDLE_NAME) val relationDescriptionKey: String,
    private val customDisplayName: String?,
  ) : GotoRelatedItem(toElement.psiElement, group) {
    private val fromElementType = fromElement.metricsElementType
    private val toElementType = toElement.metricsElementType

    override fun navigate() {
      element
        ?.project
        ?.service<DaggerAnalyticsTracker>()
        ?.trackNavigation(
          DaggerEditorEvent.NavigationMetadata.NavigationContext.CONTEXT_GUTTER,
          fromElementType,
          toElementType,
        )
      super.navigate()
    }

    override fun getCustomName() = customDisplayName
  }
}
