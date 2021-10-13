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
package com.android.tools.idea.compose.preview

import com.android.tools.idea.compose.preview.pickers.properties.DimUnit
import com.android.tools.idea.compose.preview.pickers.properties.Shape

internal const val PARAMETER_NAME = "name"
internal const val PARAMETER_GROUP = "group"
internal const val PARAMETER_API_LEVEL = "apiLevel"
internal const val PARAMETER_WIDTH = "width"
internal const val PARAMETER_WIDTH_DP = "widthDp"
internal const val PARAMETER_HEIGHT = "height"
internal const val PARAMETER_HEIGHT_DP = "heightDp"
internal const val PARAMETER_LOCALE = "locale"
internal const val PARAMETER_THEME = "theme"
internal const val PARAMETER_UI_MODE = "uiMode"
internal const val PARAMETER_DEVICE = "device"
internal const val PARAMETER_SHOW_DECORATION = "showDecoration"
internal const val PARAMETER_SHOW_SYSTEM_UI = "showSystemUi"
internal const val PARAMETER_SHOW_BACKGROUND = "showBackground"
internal const val PARAMETER_BACKGROUND_COLOR = "backgroundColor"
internal const val PARAMETER_FONT_SCALE = "fontScale"
internal const val PARAMETER_HARDWARE_DEVICE = "Device"
internal const val PARAMETER_HARDWARE_DIMENSIONS = "Dimensions"
internal const val PARAMETER_HARDWARE_WIDTH = "Width"
internal const val PARAMETER_HARDWARE_HEIGHT = "Height"
internal const val PARAMETER_HARDWARE_DIM_UNIT = "DimensionUnit"
internal const val PARAMETER_HARDWARE_DENSITY = "Density"
internal const val PARAMETER_HARDWARE_ORIENTATION = "Orientation"

object Preview {
  object DeviceSpec {
    // TODO(205051960): Namespace other Preview parameters, and make a clear distinction of PropertyItem name and parameter name,
    //  alternatively restructure properties so that they are not a flat list, so that the 'device' PropertyItem has its own PropertyItems
    internal const val PREFIX = "spec:"
    internal const val SEPARATOR = ','
    internal const val OPERATOR = '='

    internal const val PARAMETER_WIDTH = "width"
    internal const val PARAMETER_HEIGHT = "height"
    internal const val PARAMETER_SHAPE = "shape"
    internal const val PARAMETER_UNIT = "unit"
    internal const val PARAMETER_DPI = "dpi"

    internal const val DEFAULT_WIDTH_PX = 1080
    internal const val DEFAULT_HEIGHT_PX = 1920
    internal val DEFAULT_SHAPE = Shape.Normal
    internal val DEFAULT_UNIT = DimUnit.px
    internal const val DEFAULT_DPI = 480
  }
}
