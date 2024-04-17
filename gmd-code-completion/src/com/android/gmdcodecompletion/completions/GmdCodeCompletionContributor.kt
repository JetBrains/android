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
import com.android.gmdcodecompletion.ConfigurationParameterName
import com.android.gmdcodecompletion.ConfigurationParameterName.DIRECTORIES_TO_PULL
import com.android.gmdcodecompletion.ConfigurationParameterName.EXTRA_DEVICE_FILES
import com.android.gmdcodecompletion.GmdDeviceCatalog
import com.android.gmdcodecompletion.MIN_SUPPORTED_GMD_API_LEVEL
import com.android.gmdcodecompletion.PsiElementLevel
import com.android.gmdcodecompletion.completions.GmdDeviceDefinitionPatternMatchingProvider.getMinAndTargetSdk
import com.android.gmdcodecompletion.completions.GmdDeviceDefinitionPatternMatchingProvider.getSiblingPropertyMap
import com.android.gmdcodecompletion.completions.lookupelementprovider.BaseLookupElementProvider
import com.android.gmdcodecompletion.completions.lookupelementprovider.FtlLookupElementProvider
import com.android.gmdcodecompletion.completions.lookupelementprovider.FtlTestOptionsLookupElementProvider
import com.android.gmdcodecompletion.completions.lookupelementprovider.ManagedVirtualLookupElementProvider
import com.android.gmdcodecompletion.ftl.FtlDeviceCatalog
import com.android.gmdcodecompletion.ftl.FtlDeviceCatalogService
import com.android.gmdcodecompletion.getGradlePropertyValue
import com.android.gmdcodecompletion.getQualifiedNameList
import com.android.gmdcodecompletion.isFtlPluginEnabled
import com.android.gmdcodecompletion.managedvirtual.ManagedVirtualDeviceCatalog
import com.android.gmdcodecompletion.managedvirtual.ManagedVirtualDeviceCatalogService
import com.android.gmdcodecompletion.superParent
import com.android.gmdcodecompletion.superParentAsGrMethodCall
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
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
        if (!isFtlPluginEnabled(parameters.position.project, arrayOf(parameters.position.module ?: return))) return
        addDeviceDefinitionCompletions(parameters, customSorter, FtlDeviceCatalogService.getInstance().state.myDeviceCatalog, result)
      }
    })

    // Add code completion for managed virtual device definition
    extend(CompletionType.BASIC, managedVirtualDevicePropertyPattern, object : CompletionProvider<CompletionParameters>() {
      override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) =
        addDeviceDefinitionCompletions(parameters, customSorter, ManagedVirtualDeviceCatalogService.getInstance().state.myDeviceCatalog,
                                       result)
    })

    // Add code completion for FTl test options
    extend(CompletionType.BASIC, ftlTestOptionsPattern, object : CompletionProvider<CompletionParameters>() {
      override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        if (!isFtlPluginEnabled(parameters.position.project, arrayOf(parameters.position.module ?: return))) return
        ProgressManager.checkCanceled()
        val currentPosition = parameters.position
        val configurationParameterName: ConfigurationParameterName? = getCompletionParameterName(currentPosition)
        if (configurationParameterName != null) {
          addSimpleValueSuggestion(configurationParameterName, currentPosition, FtlTestOptionsLookupElementProvider, result)
        }
        else if (currentPosition.language == GroovyLanguage) {
          val leafContainer = currentPosition.superParentAsGrMethodCall()
                              ?: currentPosition.superParentAsGrMethodCall(3) ?: return
          val qualifiedReferenceName = leafContainer.getQualifiedNameList()?.get(0) ?: return
          val interfaceIndex = ConfigurationType.FTL_TEST_OPTIONS.getMatchingInterfaceIndex(qualifiedReferenceName)
          if (interfaceIndex == -1) return
          addCompletionParameterNameSuggestion(currentPosition, ConfigurationType.FTL_TEST_OPTIONS, result, interfaceIndex)
        }
      }
    })
  }

  // Provides custom and default GmdDevicePropertyInsertHandler for completion parameter names
  private val myParameterNameHandlers: Map<ConfigurationParameterName, GmdDevicePropertyInsertHandler> = mapOf(
    EXTRA_DEVICE_FILES to GmdDevicePropertyInsertHandler(InsertType.CUSTOM_SUFFIX, "[] = ", 1),
    DIRECTORIES_TO_PULL to GmdDevicePropertyInsertHandler(InsertType.CUSTOM_SUFFIX, ".addAll()", 8),
  ).withDefault { GmdDevicePropertyInsertHandler(InsertType.CUSTOM_SUFFIX, " = ", 3) }

  private fun getCompletionParameterName(position: PsiElement): ConfigurationParameterName? {
    return when (position.language) {
      GroovyLanguage -> {
        val parameterName = (position.superParent() as? GrAssignmentExpression)?.lValue?.text ?: (position.superParent(
          3) as? GrAssignmentExpression)?.lValue?.text ?: return null
        ConfigurationParameterName.fromOrNull(parameterName)
      }

      KotlinLanguage.INSTANCE -> {
        val devicePropertyName = (position.superParent() as? KtBinaryExpression)?.left?.text ?: (position.superParent(
          3) as? KtBinaryExpression)?.left?.text ?: return null
        ConfigurationParameterName.fromOrNull(devicePropertyName)
      }

      else -> {
        LOGGER.warn("${position.language} is not supported yet for GMD code completion. Groovy and Kotlin are currently supported")
        null
      }
    }
  }

  // Add code completion for either FTL or managed virtual device based on type of deviceCatalog
  private fun addDeviceDefinitionCompletions(parameters: CompletionParameters, sorter: CompletionSorter,
                                             deviceCatalog: GmdDeviceCatalog, result: CompletionResultSet) {
    ProgressManager.checkCanceled()
    // GMD definition is only available if Gradle build file is inside a module
    parameters.position.module ?: return
    if (deviceCatalog.isEmptyCatalog) return

    val currentPosition = parameters.position
    val configurationParameterName: ConfigurationParameterName? = getCompletionParameterName(currentPosition)

    val configurationType =
      when (deviceCatalog) {
        is FtlDeviceCatalog -> ConfigurationType.FTL
        is ManagedVirtualDeviceCatalog -> ConfigurationType.MANAGED_VIRTUAL
        else -> {
          LOGGER.warn("${deviceCatalog.javaClass} is not supported. It should be one of FtlDeviceCatalog or ManagedVirtualDeviceCatalog")
          return
        }
      }

    if (configurationParameterName != null) {
      val lookupElementProvider = if (configurationType == ConfigurationType.FTL) FtlLookupElementProvider else ManagedVirtualLookupElementProvider
      addDeviceParameterSuggestion(configurationParameterName, currentPosition, deviceCatalog, sorter, lookupElementProvider, result)
    }
    else if (currentPosition.language == GroovyLanguage) {
      // Groovy need code completion for device property name. Kotlin already has this function
      addCompletionParameterNameSuggestion(currentPosition, configurationType, result)
    }
  }

  private fun addCompletionParameterNameSuggestion(position: PsiElement,
                                                   configurationType: ConfigurationType,
                                                   result: CompletionResultSet,
                                                   containerIndex: Int = 0) {
    val existingProperties = getSiblingPropertyMap(position, PsiElementLevel.DEVICE_PROPERTY_NAME)
    result.caseInsensitive().addAllElements(configurationType.availableContainers[containerIndex].availableConfigurations.mapNotNull {
      if (!existingProperties.contains(it) && (configurationType != ConfigurationType.MANAGED_VIRTUAL ||
                                               (!(it == ConfigurationParameterName.API_PREVIEW && existingProperties.contains(
                                                 ConfigurationParameterName.API_LEVEL)) &&
                                                !(it == ConfigurationParameterName.API_LEVEL && existingProperties.contains(
                                                  ConfigurationParameterName.API_PREVIEW))))) {
        GmdCodeCompletionLookupElement(myValue = it.propertyName,
                                       myInsertHandler = myParameterNameHandlers.getValue(it))
      }
      else null
    })
  }

  private fun addDeviceParameterSuggestion(configurationParameterName: ConfigurationParameterName,
                                           position: PsiElement,
                                           deviceCatalog: GmdDeviceCatalog,
                                           sorter: CompletionSorter,
                                           lookupElementProvider: BaseLookupElementProvider,
                                           result: CompletionResultSet) {

    val deviceProperties = getSiblingPropertyMap(position, PsiElementLevel.COMPLETION_PROPERTY_VALUE)
    val minAndTargetApiLevel = getMinAndTargetSdk(position.androidFacet)
    // check if project has the support old API flag for local GMD. This affects minimum supported API level
    if (deviceCatalog is ManagedVirtualDeviceCatalog &&
        !getGradlePropertyValue(ProjectBuildModel.get(position.project),
                                "android.experimental.testOptions.managedDevices.allowOldApiLevelDevices")) {
      minAndTargetApiLevel.minSdk = maxOf(MIN_SUPPORTED_GMD_API_LEVEL, minAndTargetApiLevel.minSdk)
    }
    if (deviceProperties[configurationParameterName] != null) return
    val suggestions = lookupElementProvider.generateDevicePropertyValueSuggestionList(configurationParameterName,
                                                                                      deviceProperties,
                                                                                      minAndTargetApiLevel,
                                                                                      deviceCatalog)
    result.caseInsensitive().apply {
      if (configurationParameterName.needCustomComparable) this.withRelevanceSorter(sorter).addAllElements(suggestions)
      else this.addAllElements(suggestions)
    }
  }

  private fun addSimpleValueSuggestion(configurationParameterName: ConfigurationParameterName,
                                       position: PsiElement,
                                       lookupElementProvider: BaseLookupElementProvider,
                                       result: CompletionResultSet) {
    val deviceProperties = getSiblingPropertyMap(position, PsiElementLevel.COMPLETION_PROPERTY_VALUE)
    if (deviceProperties[configurationParameterName] != null) return

    val suggestions = lookupElementProvider.generateSimpleValueSuggestionList(configurationParameterName, deviceProperties)
    result.caseInsensitive().addAllElements(suggestions)
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

private val insideDevicePropertyGroovyGradleFilePattern: (ConfigurationType) -> PsiElementPattern.Capture<GrExpression> = { propertyType ->
  PlatformPatterns.psiElement(GrExpression::class.java).with(object : PatternCondition<GrExpression>(null) {
    override fun accepts(grExpression: GrExpression, context: ProcessingContext?): Boolean =
      GmdDeviceDefinitionPatternMatchingProvider.matchesDevicePropertyGroovyPattern(propertyType, grExpression)
  })
}

private val insideDevicePropertyKotlinGradleFilePattern: (ConfigurationType) -> PsiElementPattern.Capture<KtExpression> = { propertyType ->
  PlatformPatterns.psiElement(KtExpression::class.java).with(object : PatternCondition<KtExpression>(null) {
    override fun accepts(ktExpression: KtExpression, context: ProcessingContext?): Boolean =
      GmdDeviceDefinitionPatternMatchingProvider.matchesDevicePropertyKotlinPattern(propertyType, ktExpression)
  })
}

private val groovyGmdDevicePropertyPattern: (ConfigurationType) -> PsiElementPattern.Capture<PsiElement> = { propertyType ->
  PlatformPatterns.psiElement().withLanguage(GroovyLanguage).inFile(
    PlatformPatterns.psiFile().withName(StandardPatterns.string().endsWith(SdkConstants.EXT_GRADLE)))
    .inside(insideDevicePropertyGroovyGradleFilePattern(propertyType))
}

private val kotlinGmdDevicePropertyPattern: (ConfigurationType) -> PsiElementPattern.Capture<PsiElement> = { deviceType ->
  PlatformPatterns.psiElement().withLanguage(KotlinLanguage.INSTANCE).inFile(
    PlatformPatterns.psiFile().withName(StandardPatterns.string().endsWith(SdkConstants.EXT_GRADLE_KTS)))
    .inside(insideDevicePropertyKotlinGradleFilePattern(deviceType))
}

private val ftlDevicePropertyPattern = PlatformPatterns.psiElement().andOr(
  groovyGmdDevicePropertyPattern(ConfigurationType.FTL),
  kotlinGmdDevicePropertyPattern(ConfigurationType.FTL),
)

private val ftlTestOptionsPattern = PlatformPatterns.psiElement().andOr(
  groovyGmdDevicePropertyPattern(ConfigurationType.FTL_TEST_OPTIONS),
)

private val managedVirtualDevicePropertyPattern = PlatformPatterns.psiElement().andOr(
  groovyGmdDevicePropertyPattern(ConfigurationType.MANAGED_VIRTUAL),
  kotlinGmdDevicePropertyPattern(ConfigurationType.MANAGED_VIRTUAL),
)

/**
 * Allowed pattern for providing auto-completion to both FTl and managed virtual
 * device definition in Groovy and Kotlin Gradle build file
 */
private val allowCodeCompletionPattern = PlatformPatterns.psiElement().andOr(
  ftlDevicePropertyPattern,
  managedVirtualDevicePropertyPattern,
)