/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.tools.idea.flags.StudioFlags
import com.intellij.codeHighlighting.Pass
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationUtil
import com.intellij.navigation.GotoRelatedItem
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.NotNullLazyValue
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiIdentifier
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.ui.awt.RelativePoint
import icons.StudioIcons
import org.jetbrains.kotlin.idea.util.module
import org.jetbrains.kotlin.lexer.KtTokens

internal const val DEPENDENCY_PROVIDERS = "Dependency provider(s)"
internal const val DEPENDENCY_CONSUMERS = "Dependency consumer(s)"

/**
 * Provides [RelatedItemLineMarkerInfo] for Dagger consumers/providers.
 *
 * Adds gutter icon that allows to navigate from Dagger consumers to Dagger providers and vice versa.
 */
class DaggerRelatedItemLineMarkerProvider : RelatedItemLineMarkerProvider() {

  override fun collectNavigationMarkers(element: PsiElement, result: MutableCollection<in RelatedItemLineMarkerInfo<PsiElement>>) {
    if (!StudioFlags.DAGGER_SUPPORT_ENABLED.get() || element.module?.isDaggerPresent() != true) return

    if (!element.canBeLineMarkerProvide) return

    // We provide RelatedItemLineMarkerInfo for PsiIdentifier/KtIdentifier (leaf element), not for PsiField/PsiMethod,
    // that's why we check that `element.parent` isDaggerConsumer, not `element` itself. See [LineMarkerProvider.getLineMarkerInfo]
    val parent = element.parent
    val isListOfProviders = when {
      parent.isDaggerConsumer -> true
      parent.isDaggerProvider -> false
      else -> return
    }

    val relatedItemsName = if (isListOfProviders) DEPENDENCY_PROVIDERS else DEPENDENCY_CONSUMERS

    val gotoTargets = object : NotNullLazyValue<List<GotoRelatedItem>>() {
      override fun compute(): List<GotoRelatedItem> {
        val relatedItems: Collection<PsiElement> = if (isListOfProviders) getDaggerProvidersFor(parent) else getDaggerConsumersFor(parent)
        return relatedItems.map { GotoRelatedItem(it, relatedItemsName) }
      }
    }

    val icon = if (isListOfProviders) StudioIcons.Misc.DEPENDENCY_PROVIDER else StudioIcons.Misc.DEPENDENCY_CONSUMER

    val info = RelatedItemLineMarkerInfo<PsiElement>(
      element,
      element.textRange,
      icon,
      Pass.LINE_MARKERS,
      { relatedItemsName },
      { mouseEvent, _ ->
        when (gotoTargets.value.size) {
          0 -> JBPopupFactory.getInstance()
            .createMessage("No dependency ${if (isListOfProviders) "providers" else "consumers"}")
            .show(RelativePoint(mouseEvent))
          1 -> gotoTargets.value.first().navigate()
          else -> NavigationUtil.getRelatedItemsPopup(gotoTargets.value, "Go to Related Files").show(RelativePoint(mouseEvent))
        }
      },
      GutterIconRenderer.Alignment.RIGHT,
      gotoTargets
    )
    result.add(info)
  }
}

/**
 * Returns true if element is Java/Kotlin identifier or kotlin "constructor" keyword.
 *
 * Only leaf elements are suitable for ItemLineMarkerInfo (see [LineMarkerProvider]),
 * and we return true for those leaf elements that could define Dagger consumer/provider.
 */
private val PsiElement.canBeLineMarkerProvide: Boolean
  get() {
    return when (this) {
      is PsiIdentifier -> true
      is LeafPsiElement -> this.elementType == KtTokens.CONSTRUCTOR_KEYWORD || this.elementType == KtTokens.IDENTIFIER
      else -> false
    }
  }