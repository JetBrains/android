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

import com.android.annotations.concurrency.WorkerThread
import com.android.tools.idea.dagger.localization.DaggerBundle.message
import com.android.tools.idea.flags.StudioFlags
import com.google.wireless.android.sdk.stats.DaggerEditorEvent
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationUtil
import com.intellij.navigation.GotoRelatedItem
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.progress.ProgressManager
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
import javax.swing.Icon
import org.jetbrains.kotlin.lexer.KtTokens

/**
 * Provides [RelatedItemLineMarkerInfo] for Dagger elements.
 *
 * Adds gutter icon that allows to navigate between Dagger elements.
 */
class DaggerRelatedItemLineMarkerProvider : RelatedItemLineMarkerProvider() {

  override fun getName(): String? {
    return message("dagger.related.items")
  }

  override fun getId(): String {
    // A custom ID is required for isEnabledByDefault to be called
    return "disable.dagger"
  }

  override fun isEnabledByDefault(): Boolean {
    // b/232089770: Dagger line markers are costly, so we provide a way to disable it by default.
    return !System.getProperty("disable.dagger.relateditems.gutter.icons", "false").toBoolean()
  }

  private class GotoItemWithAnalyticsTracking(
    fromElement: PsiElement,
    toElement: PsiElement,
    group: String,
    val customNameToDisplay: String? = null
  ) : GotoRelatedItem(toElement, group) {
    private val fromElementType = getTypeForMetrics(fromElement)
    private val toElementType = getTypeForMetrics(toElement)

    override fun navigate() {
      element
        ?.project
        ?.service<DaggerAnalyticsTracker>()
        ?.trackNavigation(
          DaggerEditorEvent.NavigationMetadata.NavigationContext.CONTEXT_GUTTER,
          fromElementType,
          toElementType
        )
      super.navigate()
    }

    override fun getCustomName() = customNameToDisplay
  }

  @WorkerThread
  override fun collectNavigationMarkers(
    element: PsiElement,
    result: MutableCollection<in RelatedItemLineMarkerInfo<*>>
  ) {
    if (!StudioFlags.DAGGER_SUPPORT_ENABLED.get() ||
        StudioFlags.DAGGER_USING_INDEX_ENABLED.get() ||
        !element.project.service<DaggerDependencyChecker>().isDaggerPresent()
    )
      return

    val startTimeMs = System.currentTimeMillis()

    ProgressManager.checkCanceled()
    if (!element.canBeLineMarkerProvide) return

    // We provide RelatedItemLineMarkerInfo for PsiIdentifier/KtIdentifier (leaf element), not for
    // PsiField/PsiMethod,
    // that's why we check that `element.parent` is Dagger related element, not `element` itself.
    // See [LineMarkerProvider.getLineMarkerInfo]
    val parent = element.parent
    val (icon, gotoTargets) =
      when {
        parent.isDaggerConsumer -> getIconAndGoToItemsForConsumer(parent)
        parent.isDaggerProvider -> getIconAndGoToItemsForProvider(parent)
        parent.isAssistedInjectedConstructor -> getIconAndGotoItemsForAssistedProvider(parent)
        parent.isAssistedFactoryMethod -> getIconAndGotoItemsForAssistedFactoryMethod(parent)
        parent.isDaggerModule -> getIconAndGoToItemsForModule(parent)
        parent.isDaggerComponent -> getIconAndGoToItemsForComponent(parent)
        parent.isDaggerSubcomponent -> getIconAndGoToItemsForSubcomponent(parent)
        parent.isDaggerComponentInstantiationMethod ||
          parent.isDaggerEntryPointInstantiationMethod ->
          getIconAndGoToItemsForComponentMethod(parent)
        else -> return
      }

    if (gotoTargets.isEmpty()) return

    val typeForMetrics = getTypeForMetrics(parent)

    val info =
      RelatedItemLineMarkerInfo<PsiElement>(
        element,
        element.textRange,
        icon,
        getTooltipProvider(parent, gotoTargets),
        { mouseEvent, elt ->
          elt.project.service<DaggerAnalyticsTracker>().trackClickOnGutter(typeForMetrics)
          if (gotoTargets.size == 1) {
            gotoTargets.first().navigate()
          } else {
            NavigationUtil.getRelatedItemsPopup(gotoTargets, "Go to Related Files")
              .show(RelativePoint(mouseEvent))
          }
        },
        GutterIconRenderer.Alignment.RIGHT,
        { gotoTargets }
      )
    val calculationTime = System.currentTimeMillis() - startTimeMs
    element
      .project
      .service<DaggerAnalyticsTracker>()
      .trackGutterWasDisplayed(typeForMetrics, calculationTime)
    result.add(info)
  }

  @Suppress("DialogTitleCapitalization")
  private fun getTooltipProvider(
    targetElement: PsiElement,
    gotoTargets: List<GotoRelatedItem>
  ): (PsiElement) -> String {
    return {
      val fromElementString = SymbolPresentationUtil.getSymbolPresentableText(targetElement)

      if (gotoTargets.size == 1) {
        with(gotoTargets.single()) {
          val toElementString =
            customName ?: SymbolPresentationUtil.getSymbolPresentableText(element!!)

          when (group) {
            message("modules.included") ->
              message("navigate.to.included.module", fromElementString, toElementString)
            message("providers") ->
              if (targetElement.isDaggerConsumer) {
                message("navigate.to.provider", fromElementString, toElementString)
              } else {
                message("navigate.to.provider.from.component", fromElementString, toElementString)
              }
            message("consumers") ->
              message("navigate.to.consumer", fromElementString, toElementString)
            message("exposed.by.components") ->
              message("navigate.to.component.exposes", fromElementString, toElementString)
            message("exposed.by.entry.points") ->
              message("navigate.to.component.exposes", fromElementString, toElementString)
            message("parent.components") ->
              message("navigate.to.parent.component", fromElementString, toElementString)
            message("subcomponents") ->
              message("navigate.to.subcomponent", fromElementString, toElementString)
            message("included.in.components") ->
              message("navigate.to.component.that.include", fromElementString, toElementString)
            message("included.in.modules") ->
              message("navigate.to.module.that.include", fromElementString, toElementString)
            message("assisted.inject") ->
              message("navigate.to.assisted.inject", fromElementString, toElementString)
            message("assisted.factory") ->
              message("navigate.to.assisted.factory", fromElementString, toElementString)
            else -> error("[Dagger tools] Unknown navigation group: $group")
          }
        }
      } else {
        message("dependency.related.files.for", fromElementString)
      }
    }
  }

  private fun getIconAndGoToItemsForSubcomponent(
    subcomponent: PsiElement
  ): Pair<Icon, List<GotoRelatedItem>> {
    // [subcomponent] is always PsiClass or KtClass or KtObjectDeclaration, see
    // [isDaggerSubcomponent].
    val asPsiClass = subcomponent.toPsiClass()!!

    val parents =
      getDaggerParentComponentsForSubcomponent(asPsiClass).map {
        GotoItemWithAnalyticsTracking(subcomponent, it, message("parent.components"))
      }

    ProgressManager.checkCanceled()
    val modules =
      getModulesForComponent(asPsiClass).map {
        GotoItemWithAnalyticsTracking(subcomponent, it, message("modules.included"))
      }

    ProgressManager.checkCanceled()
    val subcomponents =
      getSubcomponents(asPsiClass).map {
        GotoItemWithAnalyticsTracking(subcomponent, it, message("subcomponents"))
      }

    return Pair(StudioIcons.Misc.DEPENDENCY_CONSUMER, parents + modules + subcomponents)
  }

  private fun getIconAndGoToItemsForComponent(
    component: PsiElement
  ): Pair<Icon, List<GotoRelatedItem>> {
    // component is always PsiClass or KtClass, see [isDaggerComponent].
    val componentAsPsiClass = component.toPsiClass()!!

    val components =
      getDependantComponentsForComponent(componentAsPsiClass).map {
        GotoItemWithAnalyticsTracking(component, it, message("parent.components"))
      }

    ProgressManager.checkCanceled()
    val subcomponents =
      getSubcomponents(componentAsPsiClass).map {
        GotoItemWithAnalyticsTracking(component, it, message("subcomponents"))
      }

    ProgressManager.checkCanceled()
    val modules =
      getModulesForComponent(componentAsPsiClass).map {
        GotoItemWithAnalyticsTracking(component, it, message("modules.included"))
      }
    return Pair(StudioIcons.Misc.DEPENDENCY_CONSUMER, components + subcomponents + modules)
  }

  private fun getIconAndGoToItemsForModule(module: PsiElement): Pair<Icon, List<GotoRelatedItem>> {
    // [module] is always PsiClass or KtClass, see [isDaggerModule].
    val gotoTargets =
      getUsagesForDaggerModule(module.toPsiClass()!!).map {
        val group =
          if (it.isDaggerComponent) message("included.in.components")
          else message("included.in.modules")
        GotoItemWithAnalyticsTracking(module, it, group)
      }
    return Pair(StudioIcons.Misc.DEPENDENCY_CONSUMER, gotoTargets)
  }

  private fun getIconAndGoToItemsForProvider(
    provider: PsiElement
  ): Pair<Icon, List<GotoRelatedItem>> {
    val consumers =
      getDaggerConsumersFor(provider).map {
        val nameToDisplay =
          when (it) {
            is PsiField -> it.parentOfType<PsiClass>()?.name
            is PsiParameter -> it.parentOfType<PsiMethod>()?.name
            else -> error("[Dagger editor] invalid consumer type ${it::class}")
          }
        GotoItemWithAnalyticsTracking(provider, it, message("consumers"), nameToDisplay)
      }

    ProgressManager.checkCanceled()
    val components =
      getDaggerComponentMethodsForProvider(provider).map {
        GotoItemWithAnalyticsTracking(
          provider,
          it,
          message("exposed.by.components"),
          it.parentOfType<PsiClass>()?.name
        )
      }

    ProgressManager.checkCanceled()
    val entryPoints =
      getDaggerEntryPointsMethodsForProvider(provider).map {
        GotoItemWithAnalyticsTracking(
          provider,
          it,
          message("exposed.by.entry.points"),
          it.parentOfType<PsiClass>()?.name
        )
      }

    return Pair(StudioIcons.Misc.DEPENDENCY_CONSUMER, consumers + components + entryPoints)
  }

  private fun getIconAndGotoItemsForAssistedProvider(
    provider: PsiElement
  ): Pair<Icon, List<GotoRelatedItem>> {
    val consumers =
      getDaggerAssistedFactoryMethodsForAssistedInjectedConstructor(provider).map {
        GotoItemWithAnalyticsTracking(provider, it, message("assisted.factory"), it.name)
      }
    return Pair(StudioIcons.Misc.DEPENDENCY_CONSUMER, consumers)
  }

  private fun getIconAndGotoItemsForAssistedFactoryMethod(
    provider: PsiElement
  ): Pair<Icon, List<GotoRelatedItem>> {
    val consumers =
      getDaggerAssistedInjectConstructorForAssistedFactoryMethod(provider).map {
        GotoItemWithAnalyticsTracking(provider, it, message("assisted.inject"), it.name)
      }
    return Pair(StudioIcons.Misc.DEPENDENCY_PROVIDER, consumers)
  }

  private fun getProvidersFor(consumer: PsiElement): Pair<Icon, List<GotoRelatedItem>> {
    val gotoTargets =
      getDaggerProvidersFor(consumer).map {
        GotoItemWithAnalyticsTracking(consumer, it, message("providers"))
      }
    return Pair(StudioIcons.Misc.DEPENDENCY_PROVIDER, gotoTargets)
  }

  private fun getIconAndGoToItemsForConsumer(consumer: PsiElement) = getProvidersFor(consumer)

  private fun getIconAndGoToItemsForComponentMethod(method: PsiElement) = getProvidersFor(method)
}

/**
 * Returns true if element is Java/Kotlin identifier or kotlin "constructor" keyword.
 *
 * Only leaf elements are suitable for ItemLineMarkerInfo (see [LineMarkerProvider]), and we return
 * true for those leaf elements that could define Dagger consumer/provider.
 */
private val PsiElement.canBeLineMarkerProvide: Boolean
  get() {
    return when (this) {
      is PsiIdentifier -> true
      is LeafPsiElement ->
        this.elementType == KtTokens.CONSTRUCTOR_KEYWORD || this.elementType == KtTokens.IDENTIFIER
      else -> false
    }
  }
