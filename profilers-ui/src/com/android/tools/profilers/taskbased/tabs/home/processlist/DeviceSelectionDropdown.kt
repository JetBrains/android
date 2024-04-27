/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.profilers.taskbased.tabs.home.processlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import com.android.tools.profiler.proto.Common
import com.android.tools.profilers.taskbased.common.constants.TaskBasedUxDimensions.DEVICE_SELECTION_DROPDOWN_CONTENT_PADDING_DP
import org.jetbrains.jewel.ui.component.Dropdown
import org.jetbrains.jewel.ui.component.Text

@Composable
fun DeviceSelectionDropdown(deviceList: List<Common.Device>, selectedDevice: Common.Device, onDeviceSelection: (Common.Device) -> Unit) {
  Dropdown(
    modifier = Modifier.fillMaxWidth().padding(DEVICE_SELECTION_DROPDOWN_CONTENT_PADDING_DP).testTag("DeviceSelectionDropdown"),
    menuContent = {
      deviceList.forEach {
        selectableItem(selectedDevice == it, {
          onDeviceSelection(it)
        }) {
          Text(text = it.model, modifier = Modifier.testTag("DeviceSelectionDropdownItem"))
        }
      }
    },
  ) {
    if (selectedDevice == Common.Device.getDefaultInstance()) {
      Text("Please select a device", modifier = Modifier.padding(DEVICE_SELECTION_DROPDOWN_CONTENT_PADDING_DP))
    }
    else {
      Row(
        modifier = Modifier.fillMaxWidth().padding(DEVICE_SELECTION_DROPDOWN_CONTENT_PADDING_DP),
        horizontalArrangement = Arrangement.spacedBy(DEVICE_SELECTION_DROPDOWN_CONTENT_PADDING_DP),
        verticalAlignment = Alignment.Bottom
      ) {
        // TODO (b/309866927): Add Icon for respective device type here.
        Text(text = selectedDevice.model, fontSize = TextUnit(14f, TextUnitType.Sp), lineHeight = TextUnit(18f, TextUnitType.Sp))
        Text(text = "Android ${selectedDevice.version}, API ${selectedDevice.apiLevel}", fontSize = TextUnit(12f, TextUnitType.Sp),
             lineHeight = TextUnit(16f, TextUnitType.Sp), color = Color.Gray)
      }
    }
  }
}