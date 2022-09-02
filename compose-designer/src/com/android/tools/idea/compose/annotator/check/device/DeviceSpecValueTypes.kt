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

import com.android.tools.idea.compose.annotator.check.common.MultipleChoiceValueType
import com.android.tools.idea.compose.annotator.check.common.OpenEndedValueType
import com.android.tools.idea.compose.preview.pickers.properties.DimUnit
import com.android.tools.idea.compose.preview.pickers.properties.Orientation
import com.android.tools.idea.compose.preview.pickers.properties.Shape

/** Expected the value to be an Integer. */
internal val ExpectedInteger = OpenEndedValueType("Integer")

/** Value should be one of the supported Shapes. */
internal val ExpectedShape = MultipleChoiceValueType(Shape.values().map { it.name })

/** Value should be a unit used in dimension. Ie: "px", "dp". */
internal val ExpectedDimUnit = MultipleChoiceValueType(DimUnit.values().map { it.name })

/** Value should be a boolean, either 'true' or 'false'. */
internal val ExpectedStrictBoolean =
  MultipleChoiceValueType(listOf(true.toString(), false.toString()))

/** Value should be either 'landscape' or 'portrait'. */
internal val ExpectedOrientation = MultipleChoiceValueType(Orientation.values().map { it.name })

/** Value should be an Integer with a unit suffix. Eg: 120dp */
internal val ExpectedIntegerWithUnit = OpenEndedValueType("Integer(dp/px)")

/** Value should be a Float with a unit suffix. Eg: 120.1dp */
internal val ExpectedFloatWithUnit = OpenEndedValueType("Float(dp/px)")
