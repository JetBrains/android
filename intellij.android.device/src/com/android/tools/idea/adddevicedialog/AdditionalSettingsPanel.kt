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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.resources.ScreenOrientation
import com.android.sdklib.internal.avd.AvdCamera
import com.android.sdklib.internal.avd.AvdNetworkLatency
import com.android.sdklib.internal.avd.AvdNetworkSpeed
import com.android.sdklib.internal.avd.EmulatedProperties
import com.android.sdklib.internal.avd.GpuMode
import com.android.tools.idea.avdmanager.skincombobox.Skin
import kotlinx.collections.immutable.ImmutableCollection
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.Dropdown
import org.jetbrains.jewel.ui.component.GroupHeader
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import kotlin.math.max

@Composable
internal fun AdditionalSettingsPanel(
  device: VirtualDevice,
  skins: ImmutableCollection<Skin>,
  state: AdditionalSettingsPanelState,
  onDeviceChange: (VirtualDevice) -> Unit,
  onImportButtonClick: () -> Unit,
) {
  Row {
    Text("Device skin")
    Dropdown(device.skin, skins) { onDeviceChange(device.copy(skin = it)) }
    OutlinedButton(onImportButtonClick) { Text("Import") }
  }

  CameraGroup(device, onDeviceChange)
  NetworkGroup(device, onDeviceChange)
  StartupGroup(device, onDeviceChange)
  StorageGroup(device, state, onDeviceChange)
  EmulatedPerformanceGroup(device, onDeviceChange)
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
private fun StartupGroup(device: VirtualDevice, onDeviceChange: (VirtualDevice) -> Unit) {
  GroupHeader("Startup")

  Row {
    Text("Orientation")

    Dropdown(
      menuContent = {
        ORIENTATIONS.forEach {
          selectableItem(
            device.orientation == it,
            onClick = { onDeviceChange(device.copy(orientation = it)) },
          ) {
            Text(it.shortDisplayValue)
          }
        }
      }
    ) {
      Text(device.orientation.shortDisplayValue)
    }
  }

  Row {
    Text("Default boot")
    Dropdown(device.defaultBoot, BOOTS) { onDeviceChange(device.copy(defaultBoot = it)) }
  }
}

private val ORIENTATIONS =
  listOf(ScreenOrientation.PORTRAIT, ScreenOrientation.LANDSCAPE).toImmutableList()

private val BOOTS = enumValues<Boot>().asIterable().toImmutableList()

@Composable
private fun StorageGroup(
  device: VirtualDevice,
  state: AdditionalSettingsPanelState,
  onDeviceChange: (VirtualDevice) -> Unit,
) {
  GroupHeader("Storage")

  Row {
    Text("Internal storage")

    StorageCapacityField(state.internalStorage, onValueChange = {
      state.internalStorage = it
      onDeviceChange(device.copy(internalStorage = it))
    })
  }
}

@Composable
private fun EmulatedPerformanceGroup(
  device: VirtualDevice,
  onDeviceChange: (VirtualDevice) -> Unit,
) {
  GroupHeader("Emulated Performance")

  CheckboxRow(
    "Enable multithreading",
    device.cpuCoreCount != null,
    onCheckedChange = {
      val count = if (it) EmulatedProperties.RECOMMENDED_NUMBER_OF_CORES else null
      onDeviceChange(device.copy(cpuCoreCount = count))
    },
  )

  Row {
    Text("CPU cores")
    val cpuCoreCount = device.cpuCoreCount ?: 1

    Dropdown(
      enabled = device.cpuCoreCount != null,
      menuContent = {
        for (count in 1..max(1, Runtime.getRuntime().availableProcessors() / 2)) {
          selectableItem(
            cpuCoreCount == count,
            onClick = { onDeviceChange(device.copy(cpuCoreCount = count)) },
          ) {
            Text(count.toString())
          }
        }
      },
    ) {
      Text(cpuCoreCount.toString())
    }
  }

  Row {
    Text("Graphic acceleration")

    Dropdown(
      device.graphicAcceleration,
      GRAPHIC_ACCELERATION_ITEMS,
      onSelectedItemChange = { onDeviceChange(device.copy(graphicAcceleration = it)) },
    )
  }
}

// TODO The third item depends on the system image
private val GRAPHIC_ACCELERATION_ITEMS =
  GpuMode.values().filterNot { it == GpuMode.OFF }.toImmutableList()

internal class AdditionalSettingsPanelState internal constructor(device: VirtualDevice) {
  internal var internalStorage by mutableStateOf(device.internalStorage)
}

@Composable
private fun StorageCapacityField(value: StorageCapacity, onValueChange: (StorageCapacity) -> Unit) {
  TextField(value.value.toString(), onValueChange = {
    try {
      onValueChange(StorageCapacity(if (it.isEmpty()) 0 else it.toLong(), value.unit))
    }
    catch (exception: NumberFormatException) {
      // Use the old storage
    }
  })

  Dropdown(value.unit, UNITS, onSelectedItemChange = { onValueChange(StorageCapacity(value.value, it)) })
}

private val UNITS = enumValues<StorageCapacity.Unit>().asIterable().toImmutableList()

@Composable
private fun <I> Dropdown(
  selectedItem: I,
  items: ImmutableCollection<I>,
  onSelectedItemChange: (I) -> Unit,
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
