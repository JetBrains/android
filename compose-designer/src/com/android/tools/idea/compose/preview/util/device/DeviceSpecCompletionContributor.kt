/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.util.device

import com.android.tools.compose.completion.addLookupElement
import com.android.tools.compose.completion.inserthandler.InsertionFormat
import com.android.tools.compose.completion.inserthandler.LiveTemplateFormat
import com.android.tools.idea.compose.preview.Preview.DeviceSpec
import com.android.tools.idea.compose.preview.pickers.properties.DimUnit
import com.android.tools.idea.compose.preview.pickers.properties.enumsupport.devices.ReferenceDesktopConfig
import com.android.tools.idea.compose.preview.pickers.properties.enumsupport.devices.ReferenceFoldableConfig
import com.android.tools.idea.compose.preview.pickers.properties.enumsupport.devices.ReferencePhoneConfig
import com.android.tools.idea.compose.preview.pickers.properties.enumsupport.devices.ReferenceTabletConfig
import com.android.tools.idea.compose.preview.pickers.properties.utils.DEVICE_BY_ID_PREFIX
import com.android.tools.idea.compose.preview.pickers.properties.utils.DEVICE_BY_SPEC_PREFIX
import com.android.tools.idea.compose.preview.pickers.properties.utils.getDefaultPreviewDevice
import com.android.tools.idea.compose.preview.pickers.properties.utils.getSdkDevices
import com.android.tools.idea.compose.preview.util.device.parser.DeviceSpecParam
import com.android.tools.idea.compose.preview.util.device.parser.DeviceSpecPsiFile
import com.android.tools.idea.compose.preview.util.device.parser.DeviceSpecTypes
import com.android.tools.idea.compose.preview.util.device.parser.impl.DeviceSpecSpecImpl
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.kotlin.enumValueOfOrNull
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PatternCondition
import com.intellij.patterns.PlatformPatterns
import com.intellij.patterns.PsiElementPattern
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.util.ProcessingContext
import org.jetbrains.kotlin.idea.completion.or
import org.jetbrains.kotlin.idea.util.module
import org.jetbrains.kotlin.psi.psiUtil.getChildOfType
import org.jetbrains.kotlin.psi.psiUtil.getChildrenOfType
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

/**
 * [CompletionContributor] for the [DeviceSpecLanguage] parameters.
 */
class DeviceSpecCompletionContributor : CompletionContributor() {
  init {
    extend(
      CompletionType.BASIC,
      // Completion for `id:<device_id>`
      PlatformPatterns.psiElement(DeviceSpecTypes.STRING_T)
        .afterLeafSkipping(
          PlatformPatterns.psiElement(DeviceSpecTypes.COLON),
          PlatformPatterns.psiElement(DeviceSpecTypes.ID_KEYWORD)
        ),
      SdkDeviceIdProvider
    )
    extend(
      CompletionType.BASIC,
      // Completion for `parent=<device_id>`
      PlatformPatterns.psiElement(DeviceSpecTypes.STRING_T)
        .afterLeafSkipping(
          PlatformPatterns.psiElement(DeviceSpecTypes.EQUALS),
          PlatformPatterns.psiElement(DeviceSpecTypes.PARENT_KEYWORD)
        ),
      SdkDeviceIdProvider
    )
    extend(
      CompletionType.BASIC,
      // Completion for spec/id prefix.
      PlatformPatterns.psiElement(DeviceSpecTypes.STRING_T) // String token
        // The initial prefix for the device definition is always the first element of the file, and it's always within PsiErrorElement
        // since incomplete statements cannot be parsed into DeviceSpec element, such as: "", "s", "i", "spec", "id". The first valid prefix
        // statement has to end with a ':'. Ie: "spec:", "id:". At that point the completion results would no longer show.
        .withParent(PlatformPatterns.psiElement(PsiErrorElement::class.java).isFirstChild())
        .withSuperParent(2, DeviceSpecPsiFile::class.java),
      DeviceReferenceProvider
    )
    extend(
      CompletionType.BASIC,
      // Completion for DeviceSpec parameters, a parameter definition may start after a colon character ':' or after a comma ',' following
      // the value of another parameter.
      PlatformPatterns.psiElement(DeviceSpecTypes.STRING_T) // String token
        // Unresolved/incomplete statements are wrapped in a PsiErrorElement.
        .withParent(PlatformPatterns.psiElement(PsiErrorElement::class.java).afterLeaf(
          PlatformPatterns.psiElement(DeviceSpecTypes.COLON)
            .or(PlatformPatterns.psiElement(DeviceSpecTypes.COMMA)))
        )
        .withSuperParent(2, DeviceSpecPsiFile::class.java),
      MissingParameterProvider
    )
  }
}

/**
 * Pattern that is successful when the currently captured [PsiElement] is the first element within its parent.
 */
private fun <T : PsiElement> PsiElementPattern<T, PsiElementPattern.Capture<T>>.isFirstChild() =
  with(object : PatternCondition<T>("isFirstChild") {
    override fun accepts(element: T, context: ProcessingContext?): Boolean {
      val parent = element.context ?: return false
      return parent.children.indexOf(element) == 0
    }
  })

/**
 * Supported parameters to autocomplete for DeviceSpec Language.
 */
private val parametersToDefaultValues: Map<String, String> by lazy {
  mapOf(
    DeviceSpec.PARAMETER_WIDTH to DeviceSpec.DEFAULT_WIDTH_PX.toString(),
    DeviceSpec.PARAMETER_HEIGHT to DeviceSpec.DEFAULT_HEIGHT_PX.toString(),
    DeviceSpec.PARAMETER_DPI to DeviceSpec.DEFAULT_DPI.toString(),
    DeviceSpec.PARAMETER_IS_ROUND to DeviceSpec.DEFAULT_IS_ROUND.toString(),
    DeviceSpec.PARAMETER_CHIN_SIZE to DeviceSpec.DEFAULT_CHIN_SIZE_PX.toString(),
  )
}

/**
 * A [LiveTemplateFormat] that includes all supported DeviceSpec parameters with their default values.
 */
private val baseDeviceSpecTemplate: LiveTemplateFormat by lazy {
  val template = parametersToDefaultValues.map { entry ->
    val suffix = if (DeviceSpec.isDimensionParameter(entry.key)) DeviceSpec.DEFAULT_UNIT.name else ""

    // param=<default_value>suffix
    entry.key + DeviceSpec.OPERATOR + "<${entry.value}>" + suffix
  }.joinToString(DeviceSpec.SEPARATOR.toString())
  LiveTemplateFormat(template)
}

/**
 * Provides completions for the Id of the Devices present in the Sdk.
 */
private object SdkDeviceIdProvider : CompletionProvider<CompletionParameters>() {
  override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
    val module = parameters.position.module ?: return
    val devices = getSdkDevices(module)

    devices.forEach {
      result.addElement(LookupElementBuilder.create(it.id))
    }
  }
}

/**
 * [CompletionProvider] for the device parameter as a whole.
 *
 * This is meant to provide usage examples of the `device` parameter, so it should only provide completions on the first segment of the
 * device definition.
 * Ie: when the user is typing 'id:' or 'spec:'
 */
private object DeviceReferenceProvider : CompletionProvider<CompletionParameters>() {
  override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
    val module = parameters.position.module
    val defaultDeviceId = module?.let(ConfigurationManager::getOrCreateInstance)?.getDefaultPreviewDevice()?.id
    defaultDeviceId?.let {
      result.addLookupElement(lookupString = DEVICE_BY_ID_PREFIX + defaultDeviceId, tailText = " Default Device")
    }

    result.addLookupElement(
      lookupString = DEVICE_BY_SPEC_PREFIX,
      tailText = "width=px,height=px,dpi=int,isRound=boolean,chinSize=px",
      format = baseDeviceSpecTemplate
    )
    result.addLookupElement(lookupString = ReferencePhoneConfig.deviceSpec(), tailText = " Reference Phone")
    result.addLookupElement(lookupString = ReferenceTabletConfig.deviceSpec(), tailText = " Reference Tablet")
    result.addLookupElement(lookupString = ReferenceDesktopConfig.deviceSpec(), tailText = " Reference Desktop")
    result.addLookupElement(lookupString = ReferenceFoldableConfig.deviceSpec(), tailText = " Reference Foldable")
  }
}

private object MissingParameterProvider : CompletionProvider<CompletionParameters>() {
  override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
    val remainingParameters = parametersToDefaultValues.toMutableMap()

    val deviceSpecImplElement = parameters.position.getParentOfType<DeviceSpecPsiFile>(false)?.getChildOfType<DeviceSpecSpecImpl>()
    val expectedUnit = deviceSpecImplElement?.getFirstValidUnit() ?: DeviceSpec.DEFAULT_UNIT

    deviceSpecImplElement?.getChildrenOfType<DeviceSpecParam>()?.forEach {
      // Remove parameters already used.
      remainingParameters.remove(it.firstChild.text)
    }
    val createDimensionFormat: (String) -> InsertionFormat = { defaultValue ->
      LiveTemplateFormat("=<$defaultValue>${expectedUnit.name}")
    }
    val createCommonFormat: (String) -> InsertionFormat = { defaultValue ->
      LiveTemplateFormat("=<$defaultValue>")
    }

    remainingParameters.forEach {
      if (DeviceSpec.isDimensionParameter(it.key)) {
        // For parameters that take a dimension, use an appropriate InsertionFormat that includes the expected dimension unit.
        result.addLookupElement(lookupString = it.key, format = createDimensionFormat(it.value))
      }
      else {
        result.addLookupElement(lookupString = it.key, format = createCommonFormat(it.value))
      }
    }
  }
}

/**
 * Return the first valid dimension unit [DimUnit] used within a [DeviceSpecSpecImpl] element. That same unit is expected to be used in
 * other dimension values.
 */
private fun DeviceSpecSpecImpl.getFirstValidUnit(): DimUnit? {
  getChildrenOfType<DeviceSpecParam>().forEach loop@{ paramElement ->
    when (paramElement.firstChild.text) {
      DeviceSpec.PARAMETER_WIDTH,
      DeviceSpec.PARAMETER_HEIGHT,
      DeviceSpec.PARAMETER_CHIN_SIZE -> {
        enumValueOfOrNull<DimUnit>(paramElement.text.takeLast(2))?.let {
          return it
        }
      }
      else -> {
        return@loop
      }
    }
  }
  return null
}