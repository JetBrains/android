/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.pickers.properties.enumsupport

import com.android.tools.idea.compose.preview.PARAMETER_API_LEVEL
import com.android.tools.idea.compose.preview.PARAMETER_DEVICE
import com.android.tools.idea.compose.preview.PARAMETER_FONT_SCALE
import com.android.tools.idea.compose.preview.PARAMETER_GROUP
import com.android.tools.idea.compose.preview.PARAMETER_LOCALE
import com.android.tools.idea.compose.preview.PARAMETER_UI_MODE
import com.android.tools.idea.compose.preview.pickers.properties.PsiPropertyItem
import com.android.tools.property.panel.api.EnumSupport
import com.android.tools.property.panel.api.EnumSupportProvider
import com.android.tools.property.panel.api.EnumValue
import com.intellij.util.text.nullize

/**
 * Handles how [EnumValue]s are generated for [PsiPropertyItem]s.
 */
class PsiEnumProvider(private val enumSupportValuesProvider: EnumSupportValuesProvider) : EnumSupportProvider<PsiPropertyItem> {

  override fun invoke(property: PsiPropertyItem): EnumSupport? =
    when (property.name) {
      PARAMETER_UI_MODE -> EnumSupportWithConstantData(enumSupportValuesProvider, property.name) uiMode@{ stringValue ->
        val initialResolvedValue = stringValue.toIntOrNull() ?: return@uiMode EnumValue.item(stringValue) // Not an int value
        if (initialResolvedValue.shr(6) != 0) {
          // Goes beyond the supported UiModes
          return@uiMode EnumValue.item(stringValue, "Unknown")
        }

        val uiMode = initialResolvedValue.let { numberValue ->
          // Identify which UiModeType this is, and get the base EnumValue for it
          UiMode.values().firstOrNull { it.resolvedValue.toIntOrNull() == UI_MODE_TYPE_MASK and numberValue }
        } ?: return@uiMode EnumValue.item(stringValue, "Unknown") // Unknown uiMode type

        val supportsNightMode = initialResolvedValue and UI_MODE_NIGHT_MASK != 0
        if (supportsNightMode) {
          return@uiMode UiModeWithNightMaskEnumValue(
            initialResolvedValue and UI_MODE_NIGHT_MASK == 0x20, // Check if it's night mode
            uiMode.classConstant,
            uiMode.display,
            uiMode.resolvedValue
          )
        }
        else {
          return@uiMode uiMode
        }
      }
      PARAMETER_DEVICE -> EnumSupportWithConstantData(enumSupportValuesProvider, property.name) { stringValue ->
        val trimmedValue = stringValue.trim()
        Device.values().firstOrNull { it.resolvedValue == trimmedValue }?.let {
          // First try to parse to a pre-defined device
          return@EnumSupportWithConstantData it
        }

        trimmedValue.nullize()?.let {
          // Show an item with de initial value and make it better to read
          val readableValue = it.substringAfter(':', it).replace('_', ' ')
          return@EnumSupportWithConstantData EnumValue.item(it, readableValue)
        }

        // For the scenario when there's no value or it's empty (Default device is an empty string)
        return@EnumSupportWithConstantData Device.DEFAULT
      }
      PARAMETER_GROUP,
      PARAMETER_LOCALE,
      PARAMETER_API_LEVEL -> EnumSupportWithConstantData(enumSupportValuesProvider, property.name)
      PARAMETER_FONT_SCALE -> object : EnumSupport {
        override val values: List<EnumValue> = FontScale.values().toList()
      }
      else -> null
    }
}