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
package com.android.tools.idea.compose.pickers.common.enumsupport

import com.android.tools.idea.compose.pickers.base.enumsupport.EnumSupportValuesProvider
import com.android.tools.idea.compose.pickers.base.property.PsiPropertyItem
import com.android.tools.idea.compose.pickers.preview.enumsupport.FontScale
import com.android.tools.idea.compose.pickers.preview.enumsupport.UI_MODE_NIGHT_MASK
import com.android.tools.idea.compose.pickers.preview.enumsupport.UI_MODE_TYPE_MASK
import com.android.tools.idea.compose.pickers.preview.enumsupport.UiMode
import com.android.tools.idea.compose.pickers.preview.enumsupport.UiModeWithNightMaskEnumValue
import com.android.tools.idea.compose.pickers.preview.enumsupport.Wallpaper
import com.android.tools.idea.compose.pickers.preview.enumsupport.devices.DensityEnumSupport
import com.android.tools.idea.compose.pickers.preview.enumsupport.devices.DimensionUnitEnumSupport
import com.android.tools.idea.compose.pickers.preview.enumsupport.devices.OrientationEnumSupport
import com.android.tools.idea.compose.pickers.preview.enumsupport.devices.createDeviceEnumSupport
import com.android.tools.idea.compose.preview.PARAMETER_API_LEVEL
import com.android.tools.idea.compose.preview.PARAMETER_DEVICE
import com.android.tools.idea.compose.preview.PARAMETER_FONT_SCALE
import com.android.tools.idea.compose.preview.PARAMETER_GROUP
import com.android.tools.idea.compose.preview.PARAMETER_HARDWARE_DENSITY
import com.android.tools.idea.compose.preview.PARAMETER_HARDWARE_DEVICE
import com.android.tools.idea.compose.preview.PARAMETER_HARDWARE_DIM_UNIT
import com.android.tools.idea.compose.preview.PARAMETER_HARDWARE_ORIENTATION
import com.android.tools.idea.compose.preview.PARAMETER_LOCALE
import com.android.tools.idea.compose.preview.PARAMETER_UI_MODE
import com.android.tools.idea.compose.preview.PARAMETER_WALLPAPER
import com.android.tools.property.panel.api.EnumSupport
import com.android.tools.property.panel.api.EnumSupportProvider
import com.android.tools.property.panel.api.EnumValue

/** Handles how [EnumValue]s are generated for [PsiPropertyItem]s. */
class PsiEnumProvider(private val enumSupportValuesProvider: EnumSupportValuesProvider) :
  EnumSupportProvider<PsiPropertyItem> {

  // TODO: Refactor out Preview specific implementation

  override fun invoke(property: PsiPropertyItem): EnumSupport? =
    when (property.name) {
      PARAMETER_UI_MODE ->
        EnumSupportWithConstantData(enumSupportValuesProvider, property.name) uiMode@{ stringValue
          ->
          val initialResolvedValue =
            stringValue.toIntOrNull()
              ?: return@uiMode EnumValue.item(stringValue) // Not an int value
          if (initialResolvedValue.shr(6) != 0) {
            // Goes beyond the supported UiModes
            return@uiMode EnumValue.item(stringValue, "Unknown")
          }

          val uiMode =
            initialResolvedValue.let { numberValue ->
              // Identify which UiModeType this is, and get the base EnumValue for it
              UiMode.values().firstOrNull {
                it.resolvedValue.toIntOrNull() == UI_MODE_TYPE_MASK and numberValue
              }
            }
              ?: return@uiMode EnumValue.item(stringValue, "Unknown") // Unknown uiMode type

          val supportsNightMode = initialResolvedValue and UI_MODE_NIGHT_MASK != 0
          if (supportsNightMode) {
            return@uiMode UiModeWithNightMaskEnumValue(
              initialResolvedValue and UI_MODE_NIGHT_MASK == 0x20, // Check if it's night mode
              uiMode.classConstant,
              uiMode.display,
              uiMode.resolvedValue
            )
          } else {
            return@uiMode uiMode
          }
        }
      PARAMETER_GROUP,
      PARAMETER_LOCALE,
      PARAMETER_API_LEVEL -> EnumSupportWithConstantData(enumSupportValuesProvider, property.name)
      PARAMETER_FONT_SCALE ->
        object : EnumSupport {
          override val values: List<EnumValue> = FontScale.values().toList()
        }
      PARAMETER_DEVICE,
      PARAMETER_HARDWARE_DEVICE -> createDeviceEnumSupport(enumSupportValuesProvider, property)
      PARAMETER_HARDWARE_DIM_UNIT -> DimensionUnitEnumSupport
      PARAMETER_HARDWARE_DENSITY -> DensityEnumSupport
      PARAMETER_HARDWARE_ORIENTATION -> OrientationEnumSupport
      PARAMETER_WALLPAPER ->
        EnumSupportWithConstantData(enumSupportValuesProvider, property.name) {
          Wallpaper.values()[Integer.parseInt(it) + 1]
        }
      else -> EnumSupport.simple(emptyList())
    }
}
