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
package com.android.tools.profilers.taskbased.tabs.home.processlist.deviceselection.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import com.android.tools.profiler.proto.Common
import com.android.tools.profilers.taskbased.common.constants.TaskBasedUxDimensions
import com.android.tools.profilers.taskbased.common.constants.TaskBasedUxStrings
import com.android.tools.profilers.taskbased.common.icons.DeviceIconUtils
import com.android.tools.profilers.taskbased.home.selections.deviceprocesses.ProcessListModel.ProfilerDeviceSelection
import icons.StudioIcons
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import javax.swing.Icon

@Composable
fun DeviceSelectionContent(selectedDevice: ProfilerDeviceSelection?, selectedDevicesCount: Int) {
  Box(modifier = Modifier.height(TaskBasedUxDimensions.TOP_BAR_HEIGHT_DP).padding(
    vertical = TaskBasedUxDimensions.DEVICE_SELECTION_VERTICAL_PADDING_DP,
    horizontal = TaskBasedUxDimensions.DEVICE_SELECTION_HORIZONTAL_PADDING_DP), contentAlignment = Alignment.BottomStart) {
    when (selectedDevicesCount) {
      0 -> DeviceText(TaskBasedUxStrings.NO_DEVICE_SELECTED_TITLE)
      1 -> {
        if (selectedDevice != null) SingleDeviceSelectionContent(selectedDevice)
        else throw IllegalStateException("If there count of devices selected is one, then the selected device must be non-null.")
      }
      in 2..Int.MAX_VALUE -> MultipleDeviceSelectionContent(selectedDevicesCount)

      else -> throw IllegalStateException("Negative toolbar selection count is not possible")
    }
  }
}

@Composable
fun DeviceText(text: String) {
  Text(text, fontSize = TextUnit(14f, TextUnitType.Sp), lineHeight = TextUnit(18f, TextUnitType.Sp), maxLines = 1,
       overflow = TextOverflow.Ellipsis)
}

@Composable
private fun DeviceIcon(icon: Icon?, description: String) {
  DeviceIconUtils.getDeviceIconPainter(icon)?.let {
    Icon(painter = it, contentDescription = description)
  }
}

@Composable
private fun DeviceStatusText(text: String) {
  Text(text = text, fontSize = TextUnit(12f, TextUnitType.Sp), lineHeight = TextUnit(16f, TextUnitType.Sp), color = Color.Gray,
       maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun DeviceSelectionContentContainer(content: @Composable () -> Unit) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(TaskBasedUxDimensions.DEVICE_SELECTION_TITLE_HORIZONTAL_SPACE_DP),
    verticalAlignment = Alignment.CenterVertically
  ) {
    content()
  }
}

@Composable
private fun DeviceSelectionTextContainer(content: @Composable () -> Unit) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(TaskBasedUxDimensions.DEVICE_SELECTION_TITLE_HORIZONTAL_SPACE_DP),
    verticalAlignment = Alignment.Bottom
  ) {
    content()
  }
}

@Composable
fun SingleDeviceSelectionContent(selectedDevice: ProfilerDeviceSelection) {
  DeviceSelectionContentContainer {
    DeviceIcon(selectedDevice.icon, selectedDevice.name)
    DeviceSelectionTextContainer {
      DeviceText(selectedDevice.name)
      val deviceSelectionInfo = if (selectedDevice.isRunning) {
        // If the 'device' field of a running device still has a default instance of Common.Device, this is indicative that the selected
        // device is actually running, but the profiler-side device data has not found the running device yet. This brief state of waiting
        // for the device information to arrive to the profiler will be communicated via a "Loading" subtext next to the selected device name.
        if (selectedDevice.device == Common.Device.getDefaultInstance()) {
          TaskBasedUxStrings.LOADING_SELECTED_DEVICE_INFO
        }
        else {
          "Android ${selectedDevice.device.version}, API ${selectedDevice.device.apiLevel}"
        }
      }
      else {
        TaskBasedUxStrings.SELECTED_DEVICE_OFFLINE
      }
      DeviceStatusText(deviceSelectionInfo)
    }
  }
}

@Composable
private fun MultipleDeviceSelectionContent(numDevices: Int) {
  DeviceSelectionContentContainer {
    val selectionText = "${TaskBasedUxStrings.MULTIPLE_DEVICES_SELECTED_TITLE} ($numDevices)"
    DeviceIcon(StudioIcons.DeviceExplorer.MULTIPLE_DEVICES, selectionText)
    DeviceText(selectionText)
  }
}