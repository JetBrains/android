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

import com.android.tools.idea.compose.preview.pickers.properties.utils.getSdkDevices
import com.android.tools.idea.compose.preview.util.device.parser.DeviceSpecTypes
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
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
  }
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