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

import com.android.tools.idea.compose.annotator.check.common.ParameterRule.Companion.create
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
  val shape = create(
    name = DeviceSpec.PARAMETER_SHAPE,
    expectedType = ExpectedShape,
    defaultValue = DeviceSpec.DEFAULT_SHAPE.name
  ) { enumValueOfOrNull<Shape>(it) != null }

  val width = create(
    name = DeviceSpec.PARAMETER_WIDTH,
    expectedType = ExpectedInteger,
    defaultValue = DeviceSpec.DEFAULT_WIDTH_PX.toString()
  ) { it.toIntOrNull() != null }

  val height = create(
    name = DeviceSpec.PARAMETER_HEIGHT,
    expectedType = ExpectedInteger,
    defaultValue = DeviceSpec.DEFAULT_HEIGHT_PX.toString()
  ) { it.toIntOrNull() != null }

  val unit = create(
    name = DeviceSpec.PARAMETER_UNIT,
    expectedType = ExpectedDimUnit,
    defaultValue = DeviceSpec.DEFAULT_UNIT.name
  ) { enumValueOfOrNull<DimUnit>(it) != null }

  val dpi = create(
    name = DeviceSpec.PARAMETER_DPI,
    expectedType = ExpectedInteger,
    defaultValue = DeviceSpec.DEFAULT_DPI.toString()
  ) { it.toIntOrNull() != null }
}

/**
 * Parameter rules for the language based DeviceSpec.
 *
 * For example: `spec:parent=<device_id>,width=1080px,orientation=portrait,height=1920px,isRound=true,chinSize=30dp`
 *
 * @see com.android.tools.idea.compose.preview.util.device.DeviceSpecLanguage
 */
internal object LanguageParameterRule {
  // TODO(b/220006785): Add ParameterRule for parent. Note that such rule requires checking Sdk devices, which depends on the Module class.
  // TODO(b/220006785): Add a type of rule that can read previous parameters. To check that width & height have the same unit suffix.
  // TODO(b/220006785): Make it so that a ParameterRule may attempt a fix from the original input, instead of just defaulting to one value
  //   Eg: width=120 -> width=120dp

  val width = create(
    name = DeviceSpec.PARAMETER_WIDTH,
    expectedType = ExpectedFloatWithUnit,
    defaultValue = DeviceSpec.DEFAULT_WIDTH_PX.toString() + DimUnit.px.name,
    FloatWithUnitCheck
  )

  val height = create(
    name = DeviceSpec.PARAMETER_HEIGHT,
    expectedType = ExpectedFloatWithUnit,
    defaultValue = DeviceSpec.DEFAULT_HEIGHT_PX.toString() + DimUnit.px.name,
    valueCheck = FloatWithUnitCheck
  )

  val round = create(
    name = DeviceSpec.PARAMETER_IS_ROUND,
    expectedType = ExpectedStrictBoolean,
    defaultValue = false.toString()
  ) { it.toBooleanStrictOrNull() != null }

  val orientation = create(
    name = DeviceSpec.PARAMETER_ORIENTATION,
    expectedType = ExpectedOrientation,
    defaultValue = Orientation.portrait.name
  ) { enumValueOfOrNull<Orientation>(it) != null }

  val dpi = create(
    name = DeviceSpec.PARAMETER_DPI,
    expectedType = ExpectedInteger,
    defaultValue = DeviceSpec.DEFAULT_DPI.toString()
  ) { it.toIntOrNull() != null }

  val chinSize = create(
    name = DeviceSpec.PARAMETER_CHIN_SIZE,
    expectedType = ExpectedIntegerWithUnit,
    defaultValue = DeviceSpec.DEFAULT_CHIN_SIZE.toString() + DimUnit.dp.name,
    valueCheck = IntegerWithUnitCheck
  )
}

private val FloatWithUnitCheck: (String) -> Boolean = { value ->
  endsWithDpOrPx(value) && value.dropLast(2).toFloatOrNull() != null
}

private val IntegerWithUnitCheck: (String) -> Boolean = { value ->
  endsWithDpOrPx(value) && value.dropLast(2).toIntOrNull() != null
}

private fun endsWithDpOrPx(value: String): Boolean {
  val suffix = value.takeLast(2)
  return suffix == DimUnit.px.name || suffix == DimUnit.dp.name
}