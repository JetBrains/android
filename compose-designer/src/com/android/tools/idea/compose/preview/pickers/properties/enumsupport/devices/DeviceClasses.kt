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
package com.android.tools.idea.compose.preview.pickers.properties.enumsupport.devices

import com.android.tools.idea.avdmanager.AvdScreenData
import com.android.tools.idea.compose.preview.pickers.properties.DeviceConfig
import com.android.tools.idea.compose.preview.pickers.properties.DimUnit
import com.android.tools.idea.compose.preview.pickers.properties.Shape
import com.android.tools.property.panel.api.EnumValue
import icons.StudioIcons
import javax.swing.Icon
import kotlin.math.round
import kotlin.math.sqrt

internal enum class DeviceClass(val display: String, val icon: Icon? = null) {
  Canonical("Canonical Devices", StudioIcons.LayoutEditor.Toolbar.DEVICE_PHONE),
  PhoneTablet("Phones & Tablets", StudioIcons.LayoutEditor.Toolbar.DEVICE_PHONE),
  Wear("Wear", StudioIcons.LayoutEditor.Toolbar.DEVICE_WEAR),
  Tv("Tv", StudioIcons.LayoutEditor.Toolbar.DEVICE_TV),
  Auto("Auto", StudioIcons.LayoutEditor.Toolbar.DEVICE_AUTOMOTIVE),
  Generic("Generic Devices", StudioIcons.LayoutEditor.Toolbar.DEVICE_PHONE)
}

/**
 * Helper class to assist on building a List of [EnumValue].
 *
 * Typically, each method will take device parameters (eg: width, height) and use [DeviceConfig] to encode it to a common format.
 *
 * [build] returns the List of [EnumValue] ordered by [DeviceClass] and including a header for each of them.
 */
internal class DeviceEnumValueBuilder {
  private val deviceEnumValues = mapOf<DeviceClass, MutableList<EnumValue>>(
    Pair(DeviceClass.Canonical, mutableListOf()),
    Pair(DeviceClass.PhoneTablet, mutableListOf()),
    Pair(DeviceClass.Wear, mutableListOf()),
    Pair(DeviceClass.Tv, mutableListOf()),
    Pair(DeviceClass.Auto, mutableListOf()),
    Pair(DeviceClass.Generic, mutableListOf())
  )

  fun addDeviceDp(
    name: String,
    type: DeviceClass,
    widthDp: Int,
    heightDp: Int
  ): DeviceEnumValueBuilder = apply {
    val deviceSpec = DeviceConfig(widthDp, heightDp, DimUnit.dp).deviceSpec()
    deviceEnumValues[type]?.add(EnumValue.indented(deviceSpec, name))
  }

  private fun addDevicePx(
    overrideDisplayName: String? = null,
    type: DeviceClass,
    widthPx: Int,
    heightPx: Int,
    diagonalIn: Double
  ): DeviceEnumValueBuilder = apply {
    val dpi = kotlin.run {
      val dpiCalc = sqrt((1.0 * widthPx * widthPx) + (1.0 * heightPx * heightPx)) / diagonalIn
      round(dpiCalc * 100) / 100.0
    }
    val density = AvdScreenData.getScreenDensity(null, true, dpi, heightPx)
    val deviceSpec = DeviceConfig(width = widthPx, height = heightPx, dimUnit = DimUnit.px, density = density.dpiValue).deviceSpec()
    val display = overrideDisplayName ?: "${round(diagonalIn * 100) / 100}\" ${type.name} ${heightPx}p"
    val enumValue = EnumValue.indented(deviceSpec, display)
    deviceEnumValues[type]?.add(enumValue)
  }

  fun addWearDevice(
    shape: Shape
  ): DeviceEnumValueBuilder = apply {
    val density = AvdScreenData.getScreenDensity(null, false, 224.0, 300)
    val deviceSpec = DeviceConfig(width = 300, height = 300, dimUnit = DimUnit.px, density = density.dpiValue).deviceSpec()
    deviceEnumValues[DeviceClass.Wear]?.add(EnumValue.indented(deviceSpec, shape.display))
  }

  fun addTvDevice(
    widthPx: Int,
    heightPx: Int,
    diagonalIn: Double
  ): DeviceEnumValueBuilder =
    addDevicePx(type = DeviceClass.Tv, widthPx = widthPx, heightPx = heightPx, diagonalIn = diagonalIn)

  fun addAutoDevice(
    widthPx: Int,
    heightPx: Int,
    diagonalIn: Double
  ): DeviceEnumValueBuilder =
    addDevicePx(type = DeviceClass.Auto, widthPx = widthPx, heightPx = heightPx, diagonalIn = diagonalIn)

  fun addPhoneOrTablet(
    displayName: String,
    widthPx: Int,
    heightPx: Int,
    diagonalIn: Double
  ): DeviceEnumValueBuilder =
    addDevicePx(
      overrideDisplayName = displayName,
      type = DeviceClass.PhoneTablet,
      widthPx = widthPx,
      heightPx = heightPx,
      diagonalIn = diagonalIn
    )

  fun addGeneric(
    displayName: String,
    widthPx: Int,
    heightPx: Int,
    diagonalIn: Double
  ): DeviceEnumValueBuilder =
    addDevicePx(
      overrideDisplayName = displayName,
      type = DeviceClass.Generic,
      widthPx = widthPx,
      heightPx = heightPx,
      diagonalIn = diagonalIn
    )

  fun build(): List<EnumValue> {
    val enumValues = mutableListOf<EnumValue>()
    deviceEnumValues.keys.forEach { type ->
      val values = deviceEnumValues[type]
      if (values?.isNotEmpty() == true) {
        enumValues.add(EnumValue.header(type.display, type.icon))
        values.forEach(enumValues::add)
      }
    }
    return enumValues
  }
}