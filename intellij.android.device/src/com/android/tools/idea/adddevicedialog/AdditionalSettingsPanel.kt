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

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import com.android.resources.ScreenOrientation
import com.android.sdklib.internal.avd.AvdCamera
import com.android.sdklib.internal.avd.AvdNetworkLatency
import com.android.sdklib.internal.avd.AvdNetworkSpeed
import com.android.tools.idea.avdmanager.skincombobox.Skin
import kotlinx.collections.immutable.ImmutableCollection
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.jewel.ui.component.Dropdown
import org.jetbrains.jewel.ui.component.GroupHeader
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text

@Composable
internal fun AdditionalSettingsPanel(
  device: VirtualDevice,
  skins: ImmutableCollection<Skin>,
  onDeviceChange: (VirtualDevice) -> Unit,
  onImportButtonClick: () -> Unit
) {
  Row {
    Text("Device skin")
    Dropdown(device.skin, skins) { onDeviceChange(device.copy(skin = it)) }
    OutlinedButton(onImportButtonClick) { Text("Import") }
  }

  CameraGroup(device, onDeviceChange)
  NetworkGroup(device, onDeviceChange)
  StartupGroup(device.orientation) { onDeviceChange(device.copy(orientation = it)) }
}

@Composable
private fun CameraGroup(device: VirtualDevice, onDeviceChange: (VirtualDevice) -> Unit) {
  GroupHeader("Camera")

  Row {
    Text("Front")
    Dropdown(device.frontCamera, FRONT_CAMERAS) { onDeviceChange(device.copy(frontCamera = it)) }
  }

  Row {
    Text("Rear")
    Dropdown(device.rearCamera, REAR_CAMERAS) { onDeviceChange(device.copy(rearCamera = it)) }
  }
}

private val FRONT_CAMERAS =
  listOf(AvdCamera.NONE, AvdCamera.EMULATED, AvdCamera.WEBCAM).toImmutableList()

private val REAR_CAMERAS = AvdCamera.values().asIterable().toImmutableList()

@Composable
private fun NetworkGroup(device: VirtualDevice, onDeviceChange: (VirtualDevice) -> Unit) {
  GroupHeader("Network")

  Row {
    Text("Speed")
    Dropdown(device.speed, SPEEDS) { onDeviceChange(device.copy(speed = it)) }
  }

  Row {
    Text("Latency")
    Dropdown(device.latency, LATENCIES) { onDeviceChange(device.copy(latency = it)) }
  }
}

private val SPEEDS = AvdNetworkSpeed.values().asIterable().toImmutableList()
private val LATENCIES = AvdNetworkLatency.values().asIterable().toImmutableList()

@Composable
private fun StartupGroup(
  selectedOrientation: ScreenOrientation,
  onSelectedOrientationChange: (ScreenOrientation) -> Unit
) {
  GroupHeader("Startup")

  Row {
    Text("Orientation")

    Dropdown(
      menuContent = {
        ORIENTATIONS.forEach {
          selectableItem(selectedOrientation == it, onClick = { onSelectedOrientationChange(it) }) {
            Text(it.shortDisplayValue)
          }
        }
      }
    ) {
      Text(selectedOrientation.shortDisplayValue)
    }
  }
}

private val ORIENTATIONS =
  listOf(ScreenOrientation.PORTRAIT, ScreenOrientation.LANDSCAPE).toImmutableList()

@Composable
private fun <I> Dropdown(
  selectedItem: I,
  items: ImmutableCollection<I>,
  onSelectedItemChange: (I) -> Unit
) {
  Dropdown(
    menuContent = {
      items.forEach {
        selectableItem(selectedItem == it, onClick = { onSelectedItemChange(it) }) {
          Text(it.toString())
        }
      }
    }
  ) {
    Text(selectedItem.toString())
  }
}
