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

import com.android.tools.idea.compose.preview.pickers.properties.enumsupport.devices.ReferenceDesktopConfig
import com.android.tools.idea.compose.preview.pickers.properties.enumsupport.devices.ReferenceFoldableConfig
import com.android.tools.idea.compose.preview.pickers.properties.enumsupport.devices.ReferencePhoneConfig
import com.android.tools.idea.compose.preview.pickers.properties.enumsupport.devices.ReferenceTabletConfig
import com.android.tools.idea.compose.preview.pickers.properties.utils.DEVICE_BY_ID_PREFIX
import com.android.tools.idea.compose.preview.pickers.properties.utils.getDefaultPreviewDevice
import com.android.tools.idea.compose.preview.pickers.properties.utils.getSdkDevices
import com.android.tools.idea.compose.preview.util.device.parser.DeviceSpecPsiFile
import com.android.tools.idea.compose.preview.util.device.parser.DeviceSpecTypes
import com.android.tools.idea.configurations.ConfigurationManager
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
import org.jetbrains.kotlin.idea.util.module

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
      result.addElement(LookupElementBuilder.create(DEVICE_BY_ID_PREFIX + defaultDeviceId).appendTailText(" Default Device", true))
    }

    result.addElement(LookupElementBuilder.create(ReferencePhoneConfig.deviceSpec()).appendTailText(" Reference Phone", true))
    result.addElement(LookupElementBuilder.create(ReferenceTabletConfig.deviceSpec()).appendTailText(" Reference Tablet", true))
    result.addElement(LookupElementBuilder.create(ReferenceDesktopConfig.deviceSpec()).appendTailText(" Reference Desktop", true))
    result.addElement(LookupElementBuilder.create(ReferenceFoldableConfig.deviceSpec()).appendTailText(" Reference Foldable", true))
  }
}