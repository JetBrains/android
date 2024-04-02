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
package com.android.tools.profilers.taskbased.tabs.home.processlist.deviceselection

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.android.tools.idea.IdeInfo
import com.android.tools.profiler.proto.Common
import com.android.tools.profilers.taskbased.common.constants.TaskBasedUxDimensions.DROPDOWN_HORIZONTAL_PADDING_DP
import com.android.tools.profilers.taskbased.common.constants.TaskBasedUxDimensions.DEVICE_SELECTION_DROPDOWN_VERTICAL_PADDING_DP
import com.android.tools.profilers.taskbased.common.constants.TaskBasedUxDimensions.DEVICE_SELECTION_VERTICAL_PADDING_DP
import com.android.tools.profilers.taskbased.common.constants.TaskBasedUxStrings
import com.android.tools.profilers.taskbased.common.constants.TaskBasedUxStrings.NO_SUPPORTED_DEVICES_TITLE
import com.android.tools.profilers.taskbased.home.selections.deviceprocesses.ProcessListModel.ProfilerDeviceSelection
import com.android.tools.profilers.taskbased.tabs.home.processlist.deviceselection.common.DeviceText
import com.android.tools.profilers.taskbased.tabs.home.processlist.deviceselection.common.SingleDeviceSelectionContent
import org.jetbrains.jewel.ui.component.Dropdown
import org.jetbrains.jewel.ui.component.Text

@Composable
fun DeviceSelectionDropdown(deviceList: List<Common.Device>,
                            selectedDevice: ProfilerDeviceSelection?,
                            onDeviceSelection: (Common.Device) -> Unit) {
  // Only the standalone profiler should be using this dropdown component.
  assert(IdeInfo.isGameTool())
  Dropdown(
    modifier = Modifier.fillMaxWidth().padding(horizontal = DROPDOWN_HORIZONTAL_PADDING_DP,
                                               vertical = DEVICE_SELECTION_DROPDOWN_VERTICAL_PADDING_DP).testTag("DeviceSelectionDropdown"),
    menuContent = {
      if (deviceList.isEmpty()) {
        passiveItem {
          Text(NO_SUPPORTED_DEVICES_TITLE,
               modifier = Modifier.padding(horizontal = DROPDOWN_HORIZONTAL_PADDING_DP).testTag(
                 "DefaultDeviceSelectionDropdownItem"))
        }
      }
      else {
        deviceList.forEach {
          selectableItem(
            selected = selectedDevice?.device == it,
            onClick = { onDeviceSelection(it) }
          ) {
            Text(text = it.model,
                 modifier = Modifier.padding(horizontal = DROPDOWN_HORIZONTAL_PADDING_DP).testTag("DeviceSelectionDropdownItem"))
          }
        }
      }
    },
  ) {
    Box(modifier = Modifier.padding(vertical = DEVICE_SELECTION_VERTICAL_PADDING_DP)) {
      if (selectedDevice == null) {
        DeviceText(TaskBasedUxStrings.NO_DEVICE_SELECTED_TITLE)
      }
      else {
        SingleDeviceSelectionContent(selectedDevice)
      }
    }
  }
}
