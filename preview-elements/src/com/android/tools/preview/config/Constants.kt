/*
 * Copyright (C) 2020 The Android Open Source Project
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

import kotlin.math.roundToInt

// region Preview
const val PARAMETER_NAME = "name"
const val PARAMETER_GROUP = "group"
const val PARAMETER_API_LEVEL = "apiLevel"
const val PARAMETER_WIDTH = "width"
const val PARAMETER_WIDTH_DP = "widthDp"
const val PARAMETER_HEIGHT = "height"
const val PARAMETER_HEIGHT_DP = "heightDp"
const val PARAMETER_LOCALE = "locale"
const val PARAMETER_THEME = "theme"
const val PARAMETER_UI_MODE = "uiMode"
const val PARAMETER_DEVICE = "device"
const val PARAMETER_SHOW_DECORATION = "showDecoration"
const val PARAMETER_SHOW_SYSTEM_UI = "showSystemUi"
const val PARAMETER_SHOW_BACKGROUND = "showBackground"
const val PARAMETER_BACKGROUND_COLOR = "backgroundColor"
const val PARAMETER_FONT_SCALE = "fontScale"
const val PARAMETER_HARDWARE_DEVICE = "Device"
const val PARAMETER_HARDWARE_DIMENSIONS = "Dimensions"
const val PARAMETER_HARDWARE_WIDTH = "Width"
const val PARAMETER_HARDWARE_HEIGHT = "Height"
const val PARAMETER_HARDWARE_DIM_UNIT = "DimensionUnit"
const val PARAMETER_HARDWARE_DENSITY = "Density"
const val PARAMETER_HARDWARE_ORIENTATION = "Orientation"
const val PARAMETER_HARDWARE_CHIN_SIZE = "ChinSize"
const val PARAMETER_HARDWARE_IS_ROUND = "IsRound"
const val PARAMETER_WALLPAPER = "wallpaper"
// endregion

object Preview {
  object DeviceSpec {
    const val PREFIX = "spec:"
    const val SEPARATOR = ','
    const val OPERATOR = '='

    const val PARAMETER_WIDTH = "width"
    const val PARAMETER_HEIGHT = "height"
    const val PARAMETER_DPI = "dpi"

    /**
     * Unused, may be used to define an `id` for user defined custom devices.
     *
     * E.g: "spec:id=my_device,width=900px,height=1900px"
     *
     * See b/234620152 for more context.
     */
    const val PARAMETER_ID = "id"

    // region DeviceSpec Language only
    const val PARAMETER_IS_ROUND = "isRound"
    const val PARAMETER_CHIN_SIZE = "chinSize"
    const val PARAMETER_ORIENTATION = "orientation"
    const val PARAMETER_PARENT = "parent"
    // endregion

    val DEFAULT_WIDTH_DP: Int = ReferencePhoneConfig.width.roundToInt()
    val DEFAULT_HEIGHT_DP: Int = ReferencePhoneConfig.height.roundToInt()
    val DEFAULT_SHAPE: Shape = ReferencePhoneConfig.shape
    val DEFAULT_UNIT: DimUnit = ReferencePhoneConfig.dimUnit
    const val DEFAULT_DPI: Int = 420
    val DEFAULT_IS_ROUND: Boolean = ReferencePhoneConfig.isRound
    const val DEFAULT_CHIN_SIZE_ZERO: Int = 0
    val DEFAULT_ORIENTATION = Orientation.portrait

    /**
     * Returns whether the given [parameterName] matches to a known DeviceSpec parameter that takes
     * an Android dimension value (with a dp/px suffix).
     */
    fun isDimensionParameter(parameterName: String): Boolean =
      when (parameterName) {
        PARAMETER_WIDTH,
        PARAMETER_HEIGHT,
        PARAMETER_CHIN_SIZE -> true
        else -> false
      }
  }
}
