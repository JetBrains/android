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
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.NotNullLazyValue
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiIdentifier
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.ui.awt.RelativePoint
import icons.StudioIcons
import org.jetbrains.kotlin.lexer.KtTokens
import javax.swing.Icon

internal const val DEPENDENCY_PROVIDERS = "Dependency provider(s)"
internal const val DEPENDENCY_CONSUMERS = "Dependency consumer(s)"
internal const val DEPENDENCY_COMPONENT_METHODS = "Dependency components method(s)"
internal const val DEPENDENCY_COMPONENTS = "Dependency component(s)"
internal const val SUBCOMPONENTS = "Subcomponent(s)"
internal const val DEPENDENCY_MODULES = "Dependency module(s)"

/**
 * Provides [RelatedItemLineMarkerInfo] for Dagger consumers/providers.
 *
 * Adds gutter icon that allows to navigate from Dagger consumers to Dagger providers and vice versa.
 */
class DaggerRelatedItemLineMarkerProvider : RelatedItemLineMarkerProvider() {

  override fun collectNavigationMarkers(element: PsiElement, result: MutableCollection<in RelatedItemLineMarkerInfo<PsiElement>>) {
    if (!StudioFlags.DAGGER_SUPPORT_ENABLED.get() || !element.project.service<DaggerDependencyChecker>().isDaggerPresent()) return

    if (!element.canBeLineMarkerProvide) return

    // We provide RelatedItemLineMarkerInfo for PsiIdentifier/KtIdentifier (leaf element), not for PsiField/PsiMethod,
    // that's why we check that `element.parent` isDaggerConsumer, not `element` itself. See [LineMarkerProvider.getLineMarkerInfo]
    val parent = element.parent
    val (icon, gotoTargets) = when {
      parent.isDaggerConsumer -> getIconAndGoToItemsForConsumer(parent)
      parent.isDaggerProvider -> getIconAndGoToItemsForProvider(parent)
      parent.isDaggerModule -> getIconAndGoToItemsForModule(parent)
      parent.isDaggerComponent -> getIconAndGoToItemsForComponent(parent)
      parent.isDaggerSubcomponent -> getIconAndGoToItemsForSubcomponent(parent)
      else -> return
    }

    val info = RelatedItemLineMarkerInfo<PsiElement>(
      element,
      element.textRange,
      icon,
      Pass.LINE_MARKERS,
      { "Dependency Related Files" },
      { mouseEvent, _ ->
        when (gotoTargets.value.size) {
          0 -> JBPopupFactory.getInstance()
            .createMessage("No related items")
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

  private fun getIconAndGoToItemsForSubcomponent(subcomponent: PsiElement): Pair<Icon, NotNullLazyValue<List<GotoRelatedItem>>> {
    val gotoTargets = object : NotNullLazyValue<List<GotoRelatedItem>>() {
      // subcomponent is always PsiClass or KtClass, see [isDaggerSubcomponent].
      override fun compute() = getDaggerParentComponentsForSubcomponent(subcomponent.toPsiClass()!!).map {
        GotoRelatedItem(it, DEPENDENCY_COMPONENTS)
      }
    }
    return Pair(StudioIcons.Misc.DEPENDENCY_CONSUMER, gotoTargets)
  }

  private fun getIconAndGoToItemsForComponent(component: PsiElement): Pair<Icon, NotNullLazyValue<List<GotoRelatedItem>>> {
    val gotoTargets = object : NotNullLazyValue<List<GotoRelatedItem>>() {
      // component is always PsiClass or KtClass, see [isDaggerComponent].
      override fun compute(): List<GotoRelatedItem> {
        return getDependantComponentsForComponent(component.toPsiClass()!!).map { GotoRelatedItem(it, DEPENDENCY_COMPONENTS) } +
               getSubcomponents(component.toPsiClass()!!).map { GotoRelatedItem(it, SUBCOMPONENTS) }
      }
    }
    return Pair(StudioIcons.Misc.DEPENDENCY_CONSUMER, gotoTargets)
  }

  private fun getIconAndGoToItemsForModule(module: PsiElement): Pair<Icon, NotNullLazyValue<List<GotoRelatedItem>>> {
    val gotoTargets = object : NotNullLazyValue<List<GotoRelatedItem>>() {
      override fun compute(): List<GotoRelatedItem> {
        // [module] is always PsiClass or KtClass, see [isDaggerModule].
        return getUsagesForDaggerModule(module.toPsiClass()!!).map {
          val group = if (it.isDaggerComponent) DEPENDENCY_COMPONENTS else DEPENDENCY_MODULES
          GotoRelatedItem(it, group)
        }
      }
    }
    return Pair(StudioIcons.Misc.DEPENDENCY_CONSUMER, gotoTargets)
  }

  private fun getIconAndGoToItemsForProvider(provider: PsiElement): Pair<Icon, NotNullLazyValue<List<GotoRelatedItem>>> {
    val gotoTargets = object : NotNullLazyValue<List<GotoRelatedItem>>() {
      override fun compute(): List<GotoRelatedItem> {
        return getDaggerConsumersFor(provider).map { GotoRelatedItem(it, DEPENDENCY_CONSUMERS) } +
               getDaggerComponentMethodsForProvider(provider).map { GotoRelatedItem(it, DEPENDENCY_COMPONENT_METHODS) }
      }
    }
    return Pair(StudioIcons.Misc.DEPENDENCY_CONSUMER, gotoTargets)
  }

  private fun getIconAndGoToItemsForConsumer(consumer: PsiElement): Pair<Icon, NotNullLazyValue<List<GotoRelatedItem>>> {
    val gotoTargets = object : NotNullLazyValue<List<GotoRelatedItem>>() {
      override fun compute() = getDaggerProvidersFor(consumer).map { GotoRelatedItem(it, DEPENDENCY_PROVIDERS) }
    }
    return Pair(StudioIcons.Misc.DEPENDENCY_PROVIDER, gotoTargets)
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