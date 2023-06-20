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

package com.android.gmdcodecompletion.completions

import com.android.SdkConstants
import com.android.gmdcodecompletion.CurrentPsiElement
import com.android.gmdcodecompletion.DevicePropertyName
import com.android.gmdcodecompletion.GmdDeviceCatalog
import com.android.gmdcodecompletion.completions.lookupelementprovider.BaseLookupElementProvider
import com.android.gmdcodecompletion.completions.lookupelementprovider.FtlLookupElementProvider
import com.android.gmdcodecompletion.completions.lookupelementprovider.ManagedVirtualLookupElementProvider
import com.android.gmdcodecompletion.completions.GmdDeviceDefinitionPatternMatchingProvider.getMinAndTargetSdk
import com.android.gmdcodecompletion.completions.GmdDeviceDefinitionPatternMatchingProvider.getSiblingPropertyMap
import com.android.gmdcodecompletion.ftl.FtlDeviceCatalog
import com.android.gmdcodecompletion.ftl.FtlDeviceCatalogService
import com.android.gmdcodecompletion.isFtlPluginEnabled
import com.android.gmdcodecompletion.managedvirtual.ManagedVirtualDeviceCatalog
import com.android.gmdcodecompletion.managedvirtual.ManagedVirtualDeviceCatalogService
import com.android.gmdcodecompletion.superParent
import com.android.tools.idea.util.androidFacet
import com.intellij.codeInsight.completion.CompletionConfidence
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionService
import com.intellij.codeInsight.completion.CompletionSorter
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementWeigher
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.patterns.PatternCondition
import com.intellij.patterns.PlatformPatterns
import com.intellij.patterns.PsiElementPattern
import com.intellij.patterns.StandardPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.ProcessingContext
import com.intellij.util.ThreeState
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.plugins.groovy.GroovyLanguage
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression

private val LOGGER: Logger get() = Logger.getInstance(GmdCodeCompletionContributor::class.java)

/**
 * Generates code completion suggestion when caret position is within a GMD definition block and user invokes code completion
 */
class GmdCodeCompletionContributor : CompletionContributor() {

  init {
    val customSorter =
      CompletionService.getCompletionService().emptySorter().weigh(object : LookupElementWeigher(
        "gmdDevicePropertyWeigher") {
        override fun weigh(element: LookupElement): Comparable<LookupElement> {
          return GmdCodeCompletionLookupElement(element)
        }
      })

    // Add code completion for FTl device definition
    extend(CompletionType.BASIC, ftlDevicePropertyPattern, object : CompletionProvider<CompletionParameters>() {
      override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        if (!isFtlPluginEnabled(parameters.position.project)) return
        addGmdCompletions(parameters, customSorter, FtlDeviceCatalogService.getInstance().state.myDeviceCatalog, result)
      }
    })

    // Add code completion for managed virtual device definition
    extend(CompletionType.BASIC, managedVirtualDevicePropertyPattern, object : CompletionProvider<CompletionParameters>() {
      override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) =
        addGmdCompletions(parameters, customSorter, ManagedVirtualDeviceCatalogService.getInstance().state.myDeviceCatalog, result)
    })
  }

  private fun getDevicePropertyName(position: PsiElement): DevicePropertyName? {
    return when (position.language) {
      GroovyLanguage -> {
        val devicePropertyName = (position.superParent() as? GrAssignmentExpression)?.lValue?.text ?: (position.superParent(
          3) as? GrAssignmentExpression)?.lValue?.text ?: return null
        DevicePropertyName.fromOrNull(devicePropertyName)
      }

      KotlinLanguage.INSTANCE -> {
        val devicePropertyName = (position.superParent() as? KtBinaryExpression)?.left?.text ?: (position.superParent(
          3) as? KtBinaryExpression)?.left?.text ?: return null
        DevicePropertyName.fromOrNull(devicePropertyName)
      }

      else -> {
        LOGGER.warn("${position.language} is not supported yet for GMD code completion. Groovy and Kotlin are currently supported")
        null
      }
    }
  }

  // Add code completion for either FTL or managed virtual device based on type of deviceCatalog
  private fun addGmdCompletions(parameters: CompletionParameters, sorter: CompletionSorter,
                                deviceCatalog: GmdDeviceCatalog, result: CompletionResultSet) {
    ProgressManager.checkCanceled()
    // GMD definition is only available if Gradle build file is inside a module
    parameters.position.module ?: return
    if (deviceCatalog.isEmpty()) return

    var currentPosition = parameters.position
    val devicePropertyName: DevicePropertyName? = getDevicePropertyName(currentPosition)

    val deviceType =
      when (deviceCatalog) {
        is FtlDeviceCatalog -> DeviceType.FTL
        is ManagedVirtualDeviceCatalog -> DeviceType.MANAGED_VIRTUAL
        else -> {
          LOGGER.warn("${deviceCatalog.javaClass} is not supported. It should be one of FtlDeviceCatalog or ManagedVirtualDeviceCatalog")
          return
        }
      }

    if (devicePropertyName != null) {
      val lookupElementProvider = if (deviceType == DeviceType.FTL) FtlLookupElementProvider else ManagedVirtualLookupElementProvider
      addDeviceParameterSuggestion(devicePropertyName, currentPosition, deviceCatalog, sorter, lookupElementProvider, result)
    }
    else if (currentPosition.language == GroovyLanguage) {
      // Groovy need code completion for device property name. Kotlin already has this function
      addDevicePropertyNameSuggestion(currentPosition, deviceType, result)
    }
  }

  private fun addDevicePropertyNameSuggestion(position: PsiElement,
                                              deviceType: DeviceType,
                                              result: CompletionResultSet) {
    val existingProperties = getSiblingPropertyMap(position, CurrentPsiElement.DEVICE_PROPERTY_NAME)
    val filterLogic: (DevicePropertyName) -> Boolean = {
      if (deviceType == DeviceType.FTL) {
        !existingProperties.contains(it)
      }
      else {
        !existingProperties.contains(it) &&
        !(it == DevicePropertyName.API_PREVIEW && existingProperties.contains(DevicePropertyName.API_LEVEL)) &&
        !(it == DevicePropertyName.API_LEVEL && existingProperties.contains(DevicePropertyName.API_PREVIEW))
      }
    }
    result.caseInsensitive().addAllElements(deviceType.availableDeviceProperties.mapNotNull {
      if (filterLogic(it)) {
        GmdCodeCompletionLookupElement(myValue = "${it.propertyName} = ")
      }
      else null
    })
  }

  private fun addDeviceParameterSuggestion(devicePropertyName: DevicePropertyName,
                                           position: PsiElement,
                                           deviceCatalog: GmdDeviceCatalog,
                                           sorter: CompletionSorter,
                                           lookupElementProvider: BaseLookupElementProvider,
                                           result: CompletionResultSet) {
    if (getSiblingPropertyMap(position, CurrentPsiElement.DEVICE_PROPERTY_VALUE)[devicePropertyName] != null) return

    val deviceProperties = getSiblingPropertyMap(position, CurrentPsiElement.DEVICE_PROPERTY_VALUE)
    val minAndTargetApiLevel = getMinAndTargetSdk(position.androidFacet)

    if (deviceProperties[devicePropertyName] != null) return
    val suggestions = lookupElementProvider.generateDevicePropertyValueSuggestionList(devicePropertyName,
                                                                                      deviceProperties,
                                                                                      minAndTargetApiLevel,
                                                                                      deviceCatalog)
    result.caseInsensitive().apply {
      if (devicePropertyName.needCustomComparable) this.withRelevanceSorter(sorter).addAllElements(suggestions)
      else this.addAllElements(suggestions)
    }
  }
}

/**
 *  Allow auto-popup when it's in [allowCodeCompletionPattern] context.
 */
class EnableAutoPopupInStringLiteralForGmdCodeCompletion : CompletionConfidence() {
  override fun shouldSkipAutopopup(contextElement: PsiElement, psiFile: PsiFile, offset: Int): ThreeState {
    if (allowCodeCompletionPattern.accepts(contextElement)) return ThreeState.NO
    return ThreeState.UNSURE
  }
}

private val insideDevicePropertyGroovyGradleFilePattern: (DeviceType) -> PsiElementPattern.Capture<GrExpression> = { deviceType ->
  PlatformPatterns.psiElement(GrExpression::class.java).with(object : PatternCondition<GrExpression>(null) {
    override fun accepts(grExpression: GrExpression, context: ProcessingContext?): Boolean =
      GmdDeviceDefinitionPatternMatchingProvider.matchesDevicePropertyGroovyPattern(deviceType, grExpression)
  })
}

private val insideDevicePropertyKotlinGradleFilePattern: (DeviceType) -> PsiElementPattern.Capture<KtExpression> = { deviceType ->
  PlatformPatterns.psiElement(KtExpression::class.java).with(object : PatternCondition<KtExpression>(null) {
    override fun accepts(ktExpression: KtExpression, context: ProcessingContext?): Boolean =
      GmdDeviceDefinitionPatternMatchingProvider.matchesDevicePropertyKotlinPattern(deviceType, ktExpression)
  })
}

private val groovyGmdDevicePropertyPattern: (DeviceType) -> PsiElementPattern.Capture<PsiElement> = { deviceType ->
  PlatformPatterns.psiElement().withLanguage(GroovyLanguage).inFile(
    PlatformPatterns.psiFile().withName(StandardPatterns.string().endsWith(SdkConstants.EXT_GRADLE)))
    .inside(insideDevicePropertyGroovyGradleFilePattern(deviceType))
}

private val kotlinGmdDevicePropertyPattern: (DeviceType) -> PsiElementPattern.Capture<PsiElement> = { deviceType ->
  PlatformPatterns.psiElement().withLanguage(KotlinLanguage.INSTANCE).inFile(
    PlatformPatterns.psiFile().withName(StandardPatterns.string().endsWith(SdkConstants.EXT_GRADLE_KTS)))
    .inside(insideDevicePropertyKotlinGradleFilePattern(deviceType))
}

private val ftlDevicePropertyPattern = PlatformPatterns.psiElement().andOr(
  groovyGmdDevicePropertyPattern(DeviceType.FTL),
  kotlinGmdDevicePropertyPattern(DeviceType.FTL),
)

private val managedVirtualDevicePropertyPattern = PlatformPatterns.psiElement().andOr(
  groovyGmdDevicePropertyPattern(DeviceType.MANAGED_VIRTUAL),
  kotlinGmdDevicePropertyPattern(DeviceType.MANAGED_VIRTUAL),
)

/**
 * Allowed pattern for providing auto-completion to both FTl and managed virtual
 * device definition in Groovy and Kotlin Gradle build file
 */
private val allowCodeCompletionPattern = PlatformPatterns.psiElement().andOr(
  ftlDevicePropertyPattern,
  managedVirtualDevicePropertyPattern,
)