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

import com.android.tools.idea.dagger.localization.DaggerBundle.message
import com.android.tools.idea.flags.StudioFlags
import com.google.wireless.android.sdk.stats.DaggerEditorEvent
import com.intellij.codeHighlighting.Pass
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationUtil
import com.intellij.navigation.GotoRelatedItem
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.NotNullLazyValue
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiIdentifier
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.presentation.java.SymbolPresentationUtil
import com.intellij.psi.util.parentOfType
import com.intellij.ui.awt.RelativePoint
import icons.StudioIcons
import org.jetbrains.kotlin.lexer.KtTokens
import javax.swing.Icon

/**
 * Provides [RelatedItemLineMarkerInfo] for Dagger consumers/providers.
 *
 * Adds gutter icon that allows to navigate from Dagger consumers to Dagger providers and vice versa.
 */
class DaggerRelatedItemLineMarkerProvider : RelatedItemLineMarkerProvider() {

  private class GotoItemWithAnalyticsTracking(
    fromElement: PsiElement,
    toElement: PsiElement,
    group: String,
    val customNameToDisplay: String? = null
  ) : GotoRelatedItem(toElement, group) {
    private val fromElementType = getTypeForMetrics(fromElement)
    private val toElementType = getTypeForMetrics(toElement)

    override fun navigate() {
      element?.project?.service<DaggerAnalyticsTracker>()
        ?.trackNavigation(DaggerEditorEvent.NavigationMetadata.NavigationContext.CONTEXT_GUTTER, fromElementType, toElementType)
      super.navigate()
    }

    override fun getCustomName() = customNameToDisplay
  }

  override fun collectNavigationMarkers(element: PsiElement, result: MutableCollection<in RelatedItemLineMarkerInfo<PsiElement>>) {
    if (!StudioFlags.DAGGER_SUPPORT_ENABLED.get() || !element.project.service<DaggerDependencyChecker>().isDaggerPresent()) return

    if (!element.canBeLineMarkerProvide) return

    // We provide RelatedItemLineMarkerInfo for PsiIdentifier/KtIdentifier (leaf element), not for PsiField/PsiMethod,
    // that's why we check that `element.parent` is Dagger related element, not `element` itself. See [LineMarkerProvider.getLineMarkerInfo]
    val parent = element.parent
    val (icon, gotoTargets) = when {
      parent.isDaggerConsumer -> getIconAndGoToItemsForConsumer(parent)
      parent.isDaggerProvider -> getIconAndGoToItemsForProvider(parent)
      parent.isDaggerModule -> getIconAndGoToItemsForModule(parent)
      parent.isDaggerComponent -> getIconAndGoToItemsForComponent(parent)
      parent.isDaggerSubcomponent -> getIconAndGoToItemsForSubcomponent(parent)
      parent.isDaggerComponentInstantiationMethod || parent.isDaggerEntryPointInstantiationMethod -> getIconAndGoToItemsForComponentMethod(
        parent)
      else -> return
    }

    val typeForMetrics = getTypeForMetrics(parent)

    val info = RelatedItemLineMarkerInfo<PsiElement>(
      element,
      element.textRange,
      icon,
      Pass.LINE_MARKERS,
      getTooltipProvider(parent, gotoTargets),
      { mouseEvent, elt ->
        elt.project.service<DaggerAnalyticsTracker>().trackClickOnGutter(typeForMetrics)
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

  private fun getTooltipProvider(
    targetElement: PsiElement,
    gotoTargets: NotNullLazyValue<List<GotoRelatedItem>>
  ): (PsiElement) -> String {
    return {
      val fromElementString = SymbolPresentationUtil.getSymbolPresentableText(targetElement)

      if (gotoTargets.value.size == 1) {
        with(gotoTargets.value.single()) {
          val toElementString = customName ?: SymbolPresentationUtil.getSymbolPresentableText(element!!)

          when (group) {
            message("modules.included") -> message("navigate.to.included.module", fromElementString, toElementString)
            message("providers") -> if (targetElement.isDaggerConsumer) {
              message("navigate.to.provider", fromElementString, toElementString)
            }
            else {
              message("navigate.to.provider.from.component", fromElementString, toElementString)
            }
            message("consumers") -> message("navigate.to.consumer", fromElementString, toElementString)
            message("exposed.by.components") -> message("navigate.to.component.exposes", fromElementString, toElementString)
            message("exposed.by.entry.points") -> message("navigate.to.component.exposes", fromElementString, toElementString)
            message("parent.components") -> message("navigate.to.parent.component", fromElementString, toElementString)
            message("subcomponents") -> message("navigate.to.subcomponent", fromElementString, toElementString)
            message("included.in.components") -> message("navigate.to.component.that.include", fromElementString, toElementString)
            message("included.in.modules") -> message("navigate.to.module.that.include", fromElementString, toElementString)
            else -> error("[Dagger tools] Unknown navigation group: $group")
          }
        }
      }
      else {
        message("dependency.related.files.for", fromElementString)
      }
    }
  }

  private fun getIconAndGoToItemsForSubcomponent(subcomponent: PsiElement): Pair<Icon, NotNullLazyValue<List<GotoRelatedItem>>> {
    val gotoTargets = object : NotNullLazyValue<List<GotoRelatedItem>>() {
      override fun compute(): List<GotoRelatedItem> {
        // [subcomponent] is always PsiClass or KtClass or KtObjectDeclaration, see [isDaggerSubcomponent].
        val asPsiClass = subcomponent.toPsiClass()!!
        return getDaggerParentComponentsForSubcomponent(asPsiClass)
                 .map { GotoItemWithAnalyticsTracking(subcomponent, it, message("parent.components")) } +
               getModulesForComponent(asPsiClass).map {
                 GotoItemWithAnalyticsTracking(subcomponent, it, message("modules.included"))
               } +
               getSubcomponents(asPsiClass).map {
                 GotoItemWithAnalyticsTracking(subcomponent, it, message("subcomponents"))
               }
      }
    }
    return Pair(StudioIcons.Misc.DEPENDENCY_CONSUMER, gotoTargets)
  }

  private fun getIconAndGoToItemsForComponent(component: PsiElement): Pair<Icon, NotNullLazyValue<List<GotoRelatedItem>>> {
    val gotoTargets = object : NotNullLazyValue<List<GotoRelatedItem>>() {
      override fun compute(): List<GotoRelatedItem> {
        // component is always PsiClass or KtClass, see [isDaggerComponent].
        val componentAsPsiClass = component.toPsiClass()!!
        return getDependantComponentsForComponent(componentAsPsiClass)
                 .map { GotoItemWithAnalyticsTracking(component, it, message("parent.components")) } +
               getSubcomponents(componentAsPsiClass).map {
                 GotoItemWithAnalyticsTracking(component, it, message("subcomponents"))
               } +
               getModulesForComponent(componentAsPsiClass).map {
                 GotoItemWithAnalyticsTracking(component, it, message("modules.included"))
               }
      }
    }
    return Pair(StudioIcons.Misc.DEPENDENCY_CONSUMER, gotoTargets)
  }

  private fun getIconAndGoToItemsForModule(module: PsiElement): Pair<Icon, NotNullLazyValue<List<GotoRelatedItem>>> {
    val gotoTargets = object : NotNullLazyValue<List<GotoRelatedItem>>() {
      override fun compute(): List<GotoRelatedItem> {
        // [module] is always PsiClass or KtClass, see [isDaggerModule].
        return getUsagesForDaggerModule(module.toPsiClass()!!).map {
          val group = if (it.isDaggerComponent) message("included.in.components") else message("included.in.modules")
          GotoItemWithAnalyticsTracking(module, it, group)
        }
      }
    }
    return Pair(StudioIcons.Misc.DEPENDENCY_CONSUMER, gotoTargets)
  }

  private fun getIconAndGoToItemsForProvider(provider: PsiElement): Pair<Icon, NotNullLazyValue<List<GotoRelatedItem>>> {
    val gotoTargets = object : NotNullLazyValue<List<GotoRelatedItem>>() {
      override fun compute(): List<GotoRelatedItem> {
        val consumers = getDaggerConsumersFor(provider).map {
          val nameToDisplay = when (it) {
            is PsiField -> it.parentOfType<PsiClass>()?.name
            is PsiParameter -> it.parentOfType<PsiMethod>()?.name
            else -> error("[Dagger editor] invalid consumer type ${it::class}")
          }
          GotoItemWithAnalyticsTracking(provider, it, message("consumers"), nameToDisplay)
        }
        val components = getDaggerComponentMethodsForProvider(provider).map {
          GotoItemWithAnalyticsTracking(provider, it, message("exposed.by.components"), it.parentOfType<PsiClass>()?.name)
        }
        val entryPoints = getDaggerEntryPointsMethodsForProvider(provider).map {
          GotoItemWithAnalyticsTracking(provider, it, message("exposed.by.entry.points"), it.parentOfType<PsiClass>()?.name)
        }
        return consumers + components + entryPoints
      }
    }
    return Pair(StudioIcons.Misc.DEPENDENCY_CONSUMER, gotoTargets)
  }

  private fun getProvidersFor(consumer: PsiElement): Pair<Icon, NotNullLazyValue<List<GotoRelatedItem>>> {
    val gotoTargets = object : NotNullLazyValue<List<GotoRelatedItem>>() {
      override fun compute() = getDaggerProvidersFor(consumer).map {
        GotoItemWithAnalyticsTracking(consumer, it, message("providers"))
      }
    }
    return Pair(StudioIcons.Misc.DEPENDENCY_PROVIDER, gotoTargets)
  }

  private fun getIconAndGoToItemsForConsumer(consumer: PsiElement) = getProvidersFor(consumer)

  private fun getIconAndGoToItemsForComponentMethod(method: PsiElement) = getProvidersFor(method)
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