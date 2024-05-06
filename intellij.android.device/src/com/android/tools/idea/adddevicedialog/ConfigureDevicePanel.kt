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
package com.android.tools.idea.adddevicedialog

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.collections.immutable.ImmutableCollection
import org.jetbrains.jewel.bridge.theme.SwingBridgeTheme
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.ui.component.TabData
import org.jetbrains.jewel.ui.component.TabStrip
import org.jetbrains.jewel.ui.component.Text

@Composable
internal fun ConfigureDevicePanel(
  device: VirtualDevice,
  images: ImmutableCollection<SystemImage>,
  onDeviceChange: (VirtualDevice) -> Unit
) {
  @OptIn(ExperimentalJewelApi::class)
  SwingBridgeTheme {
    Column {
      Text("Configure device")
      Text("Add a device to device manager")
      Tabs(device, images, onDeviceChange)
    }
  }
}

@Composable
private fun Tabs(
  device: VirtualDevice,
  images: ImmutableCollection<SystemImage>,
  onDeviceChange: (VirtualDevice) -> Unit
) {
  var selectedTab by remember { mutableStateOf(Tab.DEVICE_AND_API) }

  TabStrip(
    Tab.values().map { tab ->
      TabData.Default(selectedTab == tab, tab.text, onClick = { selectedTab = tab })
    }
  )

  when (selectedTab) {
    Tab.DEVICE_AND_API -> DeviceAndApiPanel(device, images, onDeviceChange)
    Tab.ADDITIONAL_SETTINGS -> AdditionalSettingsPanel()
  }
}

private enum class Tab(val text: String) {
  DEVICE_AND_API("Device and API"),
  ADDITIONAL_SETTINGS("Additional settings")
}
