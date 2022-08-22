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
package com.android.tools.idea.compose.pickers.preview.enumsupport.devices

import com.android.resources.Density
import com.android.tools.idea.avdmanager.AvdScreenData
import com.android.tools.idea.compose.pickers.base.enumsupport.EnumSupportValuesProvider
import com.android.tools.idea.compose.pickers.base.property.PsiPropertyItem
import com.android.tools.idea.compose.pickers.common.enumsupport.EnumSupportWithConstantData
import com.android.tools.idea.compose.pickers.preview.enumsupport.Device
import com.android.tools.idea.compose.pickers.preview.property.DimUnit
import com.android.tools.idea.compose.pickers.preview.property.Orientation
import com.android.tools.idea.compose.preview.PARAMETER_HARDWARE_DEVICE
import com.android.tools.idea.kotlin.enumValueOfOrDefault
import com.android.tools.property.panel.api.EnumSupport
import com.android.tools.property.panel.api.EnumValue
import com.intellij.util.text.nullize

internal fun createDeviceEnumSupport(
  enumSupportValuesProvider: EnumSupportValuesProvider,
  property: PsiPropertyItem
) =
  EnumSupportWithConstantData(enumSupportValuesProvider, property.name) { stringValue ->
    val trimmedValue = stringValue.trim()
    Device.values().firstOrNull { it.resolvedValue == trimmedValue }?.let {
      // First try to parse to a pre-defined device
      return@EnumSupportWithConstantData it
    }

    trimmedValue.nullize()?.let {
      val isDeviceSpec =
        it.startsWith("spec:") // TODO(b/197021783): Reuse constant from PreviewElement.kt
      if (isDeviceSpec) {
        val knownSpec =
          DeviceEnumValueBuilder()
            .includeDefaultsAndBuild()
            .filter { enumValue -> enumValue.value?.isNotBlank() == true }
            .firstOrNull { enumValue -> enumValue.value == stringValue }
        if (knownSpec != null) {
          return@EnumSupportWithConstantData knownSpec
        }
        // If the Device definition is a hardware spec. Just show as 'Custom'
        return@EnumSupportWithConstantData EnumValue.item(stringValue, "Custom")
      } else {
        val displayName =
          enumSupportValuesProvider
            .getValuesProvider(PARAMETER_HARDWARE_DEVICE)
            ?.invoke()
            ?.firstOrNull { enum -> enum.value.equals(it) }
            ?.display
        // Show an item with the initial value and make it better to read
        val readableValue = displayName ?: it.substringAfter(':', it).replace('_', ' ')
        return@EnumSupportWithConstantData EnumValue.item(it, readableValue)
      }
    }

    // For the scenario when there's no value or it's empty (Default device is an empty string)
    return@EnumSupportWithConstantData Device.DEFAULT
  }

internal object DensityEnumSupport : EnumSupport {
  override val values: List<EnumValue> =
    listOf(
        Density.LOW,
        Density.MEDIUM,
        Density.HIGH,
        Density.XHIGH,
        Density.XXHIGH,
        Density.XXXHIGH
      )
      .map { it.toEnumValue() }

  override fun createValue(stringValue: String): EnumValue =
    AvdScreenData.getScreenDensity(
        null,
        false,
        stringValue.toDoubleOrNull() ?: Density.XXHIGH.dpiValue.toDouble(),
        0
      )
      .toEnumValue()

  private fun Density.toEnumValue() =
    EnumValue.item(dpiValue.toString(), "$resourceValue (${dpiValue} dpi)")
}

internal object OrientationEnumSupport : EnumSupport {
  override val values: List<EnumValue> =
    Orientation.values().map { orientation -> EnumValue.item(orientation.name) }

  override fun createValue(stringValue: String): EnumValue {
    val orientation = enumValueOfOrDefault(stringValue, Orientation.portrait)
    return EnumValue.item(orientation.name)
  }
}

internal object DimensionUnitEnumSupport : EnumSupport {
  override val values: List<EnumValue> =
    DimUnit.values().map { dimUnit -> EnumValue.item(dimUnit.name) }

  override fun createValue(stringValue: String): EnumValue {
    val dimUnit = enumValueOfOrDefault(stringValue, DimUnit.px)
    return EnumValue.item(dimUnit.name)
  }
}
