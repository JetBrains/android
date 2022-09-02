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
import com.android.tools.idea.compose.preview.pickers.properties.Orientation
import com.android.tools.idea.compose.preview.pickers.properties.Shape
import com.android.tools.idea.compose.preview.pickers.properties.enumsupport.devices.ReferencePhoneConfig
import kotlin.math.roundToInt

// region Preview
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
internal const val PARAMETER_HARDWARE_CHIN_SIZE = "ChinSize"
internal const val PARAMETER_HARDWARE_IS_ROUND = "IsRound"
// endregion
// region SpringSpec
internal const val DECLARATION_SPRING_SPEC = "SpringSpec"
internal const val DECLARATION_FLOAT_SPEC = "FloatSpringSpec"
internal const val DECLARATION_SPRING = "spring"

internal const val PARAMETER_RATIO = "dampingRatio"
internal const val PARAMETER_STIFFNESS = "stiffness"
internal const val PARAMETER_THRESHOLD = "visibilityThreshold"
// endregion

object Preview {
  object DeviceSpec {
    // TODO(205051960): Namespace other Preview parameters, and make a clear distinction of
    // PropertyItem name and parameter name,
    //  alternatively restructure properties so that they are not a flat list, so that the 'device'
    // PropertyItem has its own PropertyItems
    internal const val PREFIX = "spec:"
    internal const val SEPARATOR = ','
    internal const val OPERATOR = '='

    internal const val PARAMETER_WIDTH = "width"
    internal const val PARAMETER_HEIGHT = "height"
    internal const val PARAMETER_SHAPE = "shape"
    internal const val PARAMETER_UNIT = "unit"
    internal const val PARAMETER_DPI = "dpi"

    /**
     * Unused, may be used to define an `id` for user defined custom devices.
     *
     * E.g: "spec:id=my_device,width=900px,height=1900px"
     *
     * See b/234620152 for more context.
     */
    internal const val PARAMETER_ID = "id"

    // region DeviceSpec Language only
    internal const val PARAMETER_IS_ROUND = "isRound"
    internal const val PARAMETER_CHIN_SIZE = "chinSize"
    internal const val PARAMETER_ORIENTATION = "orientation"
    internal const val PARAMETER_PARENT = "parent"
    // endregion

    internal val DEFAULT_WIDTH_DP: Int = ReferencePhoneConfig.width.roundToInt()
    internal val DEFAULT_HEIGHT_DP: Int = ReferencePhoneConfig.height.roundToInt()
    internal val DEFAULT_SHAPE: Shape = ReferencePhoneConfig.shape
    internal val DEFAULT_UNIT: DimUnit = ReferencePhoneConfig.dimUnit
    internal val DEFAULT_DPI: Int = ReferencePhoneConfig.dpi
    internal val DEFAULT_IS_ROUND: Boolean = ReferencePhoneConfig.isRound
    internal const val DEFAULT_CHIN_SIZE_ZERO: Int = 0
    internal val DEFAULT_ORIENTATION = Orientation.portrait

    /**
     * Returns whether the given [parameterName] matches to a known DeviceSpec parameter that takes
     * an Android dimension value (with a dp/px suffix).
     */
    internal fun isDimensionParameter(parameterName: String): Boolean =
      when (parameterName) {
        PARAMETER_WIDTH, PARAMETER_HEIGHT, PARAMETER_CHIN_SIZE -> true
        else -> false
      }
  }
}
