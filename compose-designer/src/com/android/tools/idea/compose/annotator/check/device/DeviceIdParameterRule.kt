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

import com.android.tools.idea.compose.annotator.check.common.ExpectedValueType
import com.android.tools.idea.compose.annotator.check.common.OpenEndedValueType
import com.android.tools.idea.compose.annotator.check.common.ParameterRule
import com.android.tools.idea.compose.preview.pickers.properties.AvailableDevicesKey
import com.android.tools.idea.compose.preview.pickers.properties.utils.DEFAULT_DEVICE_ID
import com.intellij.openapi.actionSystem.DataProvider

/** [ParameterRule] that checks that the parameter's value corresponds to an existing Device ID. */
internal class DeviceIdParameterRule(override val name: String) : ParameterRule() {

  override val expectedType: ExpectedValueType = OpenEndedValueType("Device ID")

  override val defaultValue: String = DEFAULT_DEVICE_ID

  override fun checkValue(value: String, dataProvider: DataProvider): Boolean {
    // Check that the value corresponds to the ID of an existing Device
    val availableDevices = AvailableDevicesKey.getData(dataProvider) ?: return false
    return availableDevices.associateBy { it.id }.containsKey(value)
  }

  override fun attemptFix(value: String, dataProvider: DataProvider): String? =
    null // can't provide any fix
}
