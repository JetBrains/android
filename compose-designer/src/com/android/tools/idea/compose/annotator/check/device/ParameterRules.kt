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
package com.android.tools.idea.compose.annotator.check.device

import com.android.tools.idea.compose.annotator.check.common.OpenEndedValueType
import com.android.tools.idea.compose.annotator.check.common.ParameterRule.Companion.simpleParameterRule
import com.android.tools.idea.compose.preview.Preview.DeviceSpec
import com.android.tools.idea.compose.preview.pickers.properties.DimUnit
import com.android.tools.idea.compose.preview.pickers.properties.Orientation
import com.android.tools.idea.compose.preview.pickers.properties.Shape
import com.android.tools.idea.kotlin.enumValueOfOrNull

/**
 * Parameter rules for the legacy format of the DeviceSpec.
 *
 * For example: `spec:shape=Normal,width=1080,height=1920,unit=px,dpi=480`
 */
internal object LegacyParameterRule {
  val shape =
    simpleParameterRule(
      name = DeviceSpec.PARAMETER_SHAPE,
      expectedType = ExpectedShape,
      defaultValue = DeviceSpec.DEFAULT_SHAPE.name
    ) { enumValueOfOrNull<Shape>(it) != null }

  val width =
    simpleParameterRule(
      name = DeviceSpec.PARAMETER_WIDTH,
      expectedType = ExpectedInteger,
      defaultValue = DeviceSpec.DEFAULT_WIDTH_DP.toString()
    ) { it.toIntOrNull() != null }

  val height =
    simpleParameterRule(
      name = DeviceSpec.PARAMETER_HEIGHT,
      expectedType = ExpectedInteger,
      defaultValue = DeviceSpec.DEFAULT_HEIGHT_DP.toString()
    ) { it.toIntOrNull() != null }

  val unit =
    simpleParameterRule(
      name = DeviceSpec.PARAMETER_UNIT,
      expectedType = ExpectedDimUnit,
      defaultValue = DeviceSpec.DEFAULT_UNIT.name
    ) { enumValueOfOrNull<DimUnit>(it) != null }

  val dpi =
    simpleParameterRule(
      name = DeviceSpec.PARAMETER_DPI,
      expectedType = ExpectedInteger,
      defaultValue = DeviceSpec.DEFAULT_DPI.toString()
    ) { it.toIntOrNull() != null }

  /**
   * Unused in the picker, so this rule is just to cover the possibility of the parameter,
   * regardless of value, see b/234620152.
   */
  val id =
    simpleParameterRule(
      name = DeviceSpec.PARAMETER_ID,
      expectedType = OpenEndedValueType("String"),
      defaultValue = ""
    ) { true }
}

/**
 * Parameter rules for the language based DeviceSpec.
 *
 * For example:
 * `spec:parent=<device_id>,width=1080px,orientation=portrait,height=1920px,isRound=true,chinSize=30dp`
 *
 * @see com.android.tools.idea.compose.preview.util.device.DeviceSpecLanguage
 */
internal object LanguageParameterRule {

  val width =
    DimensionParameterRule(
      name = DeviceSpec.PARAMETER_WIDTH,
      defaultNumber = DeviceSpec.DEFAULT_WIDTH_DP,
    )

  val height =
    DimensionParameterRule(
      name = DeviceSpec.PARAMETER_HEIGHT,
      defaultNumber = DeviceSpec.DEFAULT_HEIGHT_DP,
    )

  val chinSize =
    DimensionParameterRule(
      name = DeviceSpec.PARAMETER_CHIN_SIZE,
      defaultNumber = DeviceSpec.DEFAULT_CHIN_SIZE_ZERO,
    )

  val round =
    simpleParameterRule(
      name = DeviceSpec.PARAMETER_IS_ROUND,
      expectedType = ExpectedStrictBoolean,
      defaultValue = false.toString()
    ) { it.toBooleanStrictOrNull() != null }

  val orientation =
    simpleParameterRule(
      name = DeviceSpec.PARAMETER_ORIENTATION,
      expectedType = ExpectedOrientation,
      defaultValue = Orientation.portrait.name
    ) { enumValueOfOrNull<Orientation>(it) != null }

  val dpi =
    simpleParameterRule(
      name = DeviceSpec.PARAMETER_DPI,
      expectedType = ExpectedInteger,
      defaultValue = DeviceSpec.DEFAULT_DPI.toString()
    ) { it.toIntOrNull() != null }

  val parent = DeviceIdParameterRule(name = DeviceSpec.PARAMETER_PARENT)
}
