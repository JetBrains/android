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

import com.android.ide.common.util.enumValueOfOrNull
import com.android.tools.idea.preview.util.device.check.ParameterRule.Companion.simpleParameterRule
import com.android.tools.preview.config.Orientation
import com.android.tools.preview.config.Preview.DeviceSpec

/**
 * Parameter rules for the language based DeviceSpec.
 *
 * For example:
 * * `spec:parent=<device_id>,orientation=landscape`
 * * `spec:width=1080px,orientation=portrait,height=1920px,isRound=true,chinSize=30dp`
 *
 * @see com.android.tools.idea.preview.util.device.DeviceSpecLanguage
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
      defaultValue = false.toString(),
    ) {
      it.toBooleanStrictOrNull() != null
    }

  val orientation =
    simpleParameterRule(
      name = DeviceSpec.PARAMETER_ORIENTATION,
      expectedType = ExpectedOrientation,
      defaultValue = Orientation.portrait.name,
    ) {
      enumValueOfOrNull<Orientation>(it) != null
    }

  val dpi =
    simpleParameterRule(
      name = DeviceSpec.PARAMETER_DPI,
      expectedType = ExpectedInteger,
      defaultValue = DeviceSpec.DEFAULT_DPI.toString(),
    ) {
      it.toIntOrNull() != null
    }

  val parent = DeviceIdParameterRule(name = DeviceSpec.PARAMETER_PARENT)
}
