/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.preview.util.device.check

import com.android.tools.preview.config.Cutout
import com.android.tools.preview.config.Navigation
import com.android.tools.preview.config.Orientation

/** Expected the value to be an Integer. */
internal val ExpectedInteger = OpenEndedValueType("Integer")

/** Value should be a boolean, either 'true' or 'false'. */
internal val ExpectedStrictBoolean =
  MultipleChoiceValueType(listOf(true.toString(), false.toString()))

/** Value should be either 'landscape' or 'portrait'. */
internal val ExpectedOrientation = MultipleChoiceValueType(Orientation.values().map { it.name })

/** Value should be a Float with a unit suffix. Eg: 120.1dp */
internal val ExpectedFloatWithUnit = OpenEndedValueType("Float(dp/px)")

/** Value should be one of the supported Cutouts. */
internal val ExpectedCutout = MultipleChoiceValueType(Cutout.entries.map { it.name })

/** Value should be either 'buttons' or 'gesture'. */
internal val ExpectedNavigation = MultipleChoiceValueType(Navigation.entries.map { it.name })
