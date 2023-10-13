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
package com.android.tools.preview.config

import com.android.resources.Density
import com.android.resources.ScreenOrientation
import com.android.tools.configurations.DEVICE_CLASS_DESKTOP_ID
import com.android.tools.configurations.DEVICE_CLASS_FOLDABLE_ID
import com.android.tools.configurations.DEVICE_CLASS_PHONE_ID
import com.android.tools.configurations.DEVICE_CLASS_TABLET_ID
import kotlin.math.roundToInt

data class WindowSizeData(val id: String, val name: String, val widthDp: Double, val heightDp: Double, val density: Density,
                          val defaultOrientation: ScreenOrientation) {
  val widthPx: Int = widthDp.toPx(density)
  val heightPx: Int = heightDp.toPx(density)
}

/**
 * Convert dp to px.
 * The formula is "px = dp * (dpi / 160)"
 */
internal fun Double.toPx(density: Density): Int = (this * (density.dpiValue / 160.0)).roundToInt()

val DeviceWindowsNames = mapOf(
  DEVICE_CLASS_PHONE_ID to "Medium Phone",
  DEVICE_CLASS_FOLDABLE_ID to "Foldable",
  DEVICE_CLASS_TABLET_ID to "Medium Tablet",
  DEVICE_CLASS_DESKTOP_ID to "Desktop"
)

/**
 * The device definitions used by Android Studio only
 */
val PREDEFINED_WINDOW_SIZES_DEFINITIONS = referenceDeviceIds.entries.map { (_, deviceClassName) ->
  val config = getDeviceConfigFor(deviceClassName)
  WindowSizeData(
    config.deviceId ?: "",
    DeviceWindowsNames[deviceClassName] ?: "Custom",
    config.width.toDouble(),
    config.height.toDouble(),
    Density.create(config.dpi),
    when (config.orientation) {
      Orientation.portrait -> ScreenOrientation.PORTRAIT
      Orientation.landscape -> ScreenOrientation.LANDSCAPE
    }
  )
}
