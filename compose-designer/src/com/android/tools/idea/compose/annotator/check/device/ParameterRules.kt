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
import com.android.tools.idea.compose.preview.Preview
import com.android.tools.idea.compose.preview.pickers.properties.DimUnit
import com.android.tools.idea.compose.preview.pickers.properties.Shape
import com.android.tools.idea.kotlin.enumValueOfOrNull

internal object LegacyParameterRule {
  val shape = create(
    name = Preview.DeviceSpec.PARAMETER_SHAPE,
    expectedType = ExpectedShape,
    defaultValue = Preview.DeviceSpec.DEFAULT_SHAPE.name
  ) { enumValueOfOrNull<Shape>(it) != null }

  val width = create(
    name = Preview.DeviceSpec.PARAMETER_WIDTH,
    expectedType = ExpectedInteger,
    defaultValue = Preview.DeviceSpec.DEFAULT_WIDTH_PX.toString()
  ) { it.toIntOrNull() != null }

  val height = create(
    name = Preview.DeviceSpec.PARAMETER_HEIGHT,
    expectedType = ExpectedInteger,
    defaultValue = Preview.DeviceSpec.DEFAULT_HEIGHT_PX.toString()
  ) { it.toIntOrNull() != null }

  val unit = create(
    name = Preview.DeviceSpec.PARAMETER_UNIT,
    expectedType = ExpectedDimUnit,
    defaultValue = Preview.DeviceSpec.DEFAULT_UNIT.name
  ) { enumValueOfOrNull<DimUnit>(it) != null }

  val dpi = create(
    name = Preview.DeviceSpec.PARAMETER_DPI,
    expectedType = ExpectedInteger,
    defaultValue = Preview.DeviceSpec.DEFAULT_DPI.toString()
  ) { it.toIntOrNull() != null }
}