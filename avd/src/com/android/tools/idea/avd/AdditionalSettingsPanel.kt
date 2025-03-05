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
package com.android.tools.idea.avd

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.android.resources.ScreenOrientation
import com.android.sdklib.ISystemImage
import com.android.sdklib.devices.CameraLocation
import com.android.sdklib.internal.avd.AvdCamera
import com.android.sdklib.internal.avd.AvdNetworkLatency
import com.android.sdklib.internal.avd.AvdNetworkSpeed
import com.android.tools.idea.adddevicedialog.FormFactors
import com.android.tools.idea.avd.StorageCapacityFieldState.Empty
import com.android.tools.idea.avd.StorageCapacityFieldState.LessThanMin
import com.android.tools.idea.avd.StorageCapacityFieldState.Overflow
import com.android.tools.idea.avd.StorageCapacityFieldState.Result
import com.android.tools.idea.avd.StorageCapacityFieldState.Valid
import com.android.tools.idea.flags.StudioFlags
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.plus
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.jewel.ui.Outline
import org.jetbrains.jewel.ui.component.Dropdown
import org.jetbrains.jewel.ui.component.GroupHeader
import org.jetbrains.jewel.ui.component.Text

@Composable
internal fun AdditionalSettingsPanel(
  state: ConfigureDevicePanelState,
  modifier: Modifier = Modifier,
) {
  val hasPlayStore = state.hasPlayStore()
  val device = state.device
  Column(modifier, verticalArrangement = Arrangement.spacedBy(Padding.EXTRA_LARGE)) {
    Row {
      Text("Device skin", Modifier.padding(end = Padding.SMALL).alignByBaseline())

      Dropdown(
        device.skin,
        state.skins().toImmutableList(),
        onSelectedItemChange = { device.skin = it },
        Modifier.alignByBaseline().testTag("DeviceSkinDropdown"),
        !device.isFoldable,
      )
    }

    CameraGroup(device)
    NetworkGroup(device)
    StartupGroup(device)

    StorageGroup(
      device,
      state.storageGroupState,
      hasPlayStore,
      StudioFlags.POST_MVP_VIRTUAL_DEVICE_DIALOG_FEATURES_ENABLED.get(),
    )

    EmulatedPerformanceGroup(
      device,
      state.emulatedPerformanceGroupState,
      hasPlayStore,
      state.maxCpuCoreCount,
    )

    PreferredAbiGroup(
      device.preferredAbi,
      state.systemImageTableSelectionState.selection,
      onPreferredAbiChange = state::setPreferredAbi,
    )
  }
}

@Composable
private fun CameraGroup(device: VirtualDevice) {
  if (device.cameraLocations.isEmpty()) {
    return
  }
  Column(verticalArrangement = Arrangement.spacedBy(Padding.MEDIUM)) {
    GroupHeader("Camera")

    if (CameraLocation.FRONT in device.cameraLocations) {
      Row {
        Text("Front", Modifier.alignByBaseline().padding(end = Padding.SMALL))

        Dropdown(
          device.frontCamera,
          FRONT_CAMERAS,
          onSelectedItemChange = { device.frontCamera = it },
          Modifier.alignByBaseline().padding(end = Padding.MEDIUM),
        )

        InfoOutlineIcon(
          """
        None: no camera installed for AVD
        Emulated: use a simulated camera
        Webcam0: use host computer webcam or built-in camera
        """
            .trimIndent(),
          Modifier.align(Alignment.CenterVertically),
        )
      }
    }

    if (CameraLocation.BACK in device.cameraLocations) {
      Row {
        Text("Rear", Modifier.alignByBaseline().padding(end = Padding.SMALL))

        Dropdown(
          device.rearCamera,
          REAR_CAMERAS,
          onSelectedItemChange = { device.rearCamera = it },
          Modifier.alignByBaseline().padding(end = Padding.MEDIUM),
        )

        InfoOutlineIcon(
          """
        None: no camera installed for AVD
        VirtualScene: use a virtual camera in a simulated environment
        Emulated: use a simulated camera
        Webcam0: use host computer webcam or built-in camera
        """
            .trimIndent(),
          Modifier.align(Alignment.CenterVertically),
        )
      }
    }
  }
}

private val FRONT_CAMERAS =
  listOf(AvdCamera.NONE, AvdCamera.EMULATED, AvdCamera.WEBCAM).toImmutableList()

private val REAR_CAMERAS = AvdCamera.values().asIterable().toImmutableList()

@Composable
private fun NetworkGroup(device: VirtualDevice) {
  Column(verticalArrangement = Arrangement.spacedBy(Padding.MEDIUM)) {
    GroupHeader("Network")

    Row {
      Text("Speed", Modifier.alignByBaseline().padding(end = Padding.SMALL))

      Dropdown(
        device.speed,
        SPEEDS,
        onSelectedItemChange = { device.speed = it },
        Modifier.alignByBaseline().padding(end = Padding.MEDIUM),
      )

      InfoOutlineIcon(
        "Sets the initial state of the simulated network transfer rate used by the AVD. The network speed can also be adjusted in the " +
          "emulator.",
        Modifier.align(Alignment.CenterVertically),
      )
    }

    Row {
      Text("Latency", Modifier.alignByBaseline().padding(end = Padding.SMALL))

      Dropdown(
        device.latency,
        LATENCIES,
        onSelectedItemChange = { device.latency = it },
        Modifier.alignByBaseline().padding(end = Padding.MEDIUM),
      )

      InfoOutlineIcon(
        "Sets the initial state of the simulated network transfer latency used by the AVD. Latency is the delay in processing data " +
          "across the network. The network latency can also be adjusted in the emulator.",
        Modifier.align(Alignment.CenterVertically),
      )
    }
  }
}

private val SPEEDS = AvdNetworkSpeed.values().asIterable().toImmutableList()
private val LATENCIES = AvdNetworkLatency.values().asIterable().toImmutableList()

@Composable
private fun StartupGroup(device: VirtualDevice) {
  Column(verticalArrangement = Arrangement.spacedBy(Padding.MEDIUM)) {
    GroupHeader("Startup")

    Row {
      Text("Orientation", Modifier.alignByBaseline().padding(end = Padding.SMALL))

      Dropdown(
        Modifier.alignByBaseline(),
        menuContent = {
          ORIENTATIONS.forEach {
            selectableItem(device.orientation == it, onClick = { device.orientation = it }) {
              Text(it.shortDisplayValue)
            }
          }
        },
      ) {
        Text(device.orientation.shortDisplayValue)
      }
    }

    Row {
      Text("Default boot", Modifier.alignByBaseline().padding(end = Padding.SMALL))

      Dropdown(
        device.defaultBoot,
        BOOTS,
        onSelectedItemChange = { device.defaultBoot = it },
        Modifier.alignByBaseline().padding(end = Padding.MEDIUM),
      )

      InfoOutlineIcon(
        """
        Choose how the AVD should start

        Cold: start as from a power-up
        Quick: start from the state that was saved when the AVD last exited
        """
          .trimIndent(),
        Modifier.align(Alignment.CenterVertically),
      )
    }
  }
}

private val ORIENTATIONS =
  listOf(ScreenOrientation.PORTRAIT, ScreenOrientation.LANDSCAPE).toImmutableList()

private val BOOTS = enumValues<Boot>().asIterable().toImmutableList()

@Composable
private fun EmulatedPerformanceGroup(
  device: VirtualDevice,
  state: EmulatedPerformanceGroupState,
  hasGooglePlayStore: Boolean,
  maxCpuCoreCount: Int,
) {
  Column(verticalArrangement = Arrangement.spacedBy(Padding.MEDIUM)) {
    GroupHeader("Emulated Performance")

    Row {
      Text("CPU cores", Modifier.alignByBaseline().padding(end = Padding.SMALL))

      Dropdown(
        Modifier.alignByBaseline(),
        !hasGooglePlayStore,
        menuContent = {
          for (count in 1..maxCpuCoreCount) {
            selectableItem(
              device.cpuCoreCount == count,
              onClick = { device.cpuCoreCount = count },
            ) {
              Text(count.toString())
            }
          }
        },
      ) {
        Text(device.cpuCoreCount.toString())
      }
    }

    Row {
      Text("Graphics acceleration", Modifier.alignByBaseline().padding(end = Padding.SMALL))

      Dropdown(
        device.graphicsMode,
        listOf(GraphicsMode.AUTO, GraphicsMode.HARDWARE, GraphicsMode.SOFTWARE).toImmutableList(),
        onSelectedItemChange = { device.graphicsMode = it },
        Modifier.alignByBaseline(),
        !hasGooglePlayStore,
      )
    }

    @Suppress("NAME_SHADOWING") val device by rememberUpdatedState(device)

    Row(Modifier.testTag("RamRow")) {
      Text("RAM", Modifier.alignByBaseline().padding(end = Padding.SMALL))

      StorageCapacityField(
        state.ram,
        state.ram.result().ramErrorMessage(),
        Modifier.alignByBaseline().padding(end = Padding.MEDIUM),
        !hasGooglePlayStore || device.formFactor == FormFactors.AUTO,
      )

      LaunchedEffect(Unit) { state.ram.storageCapacity.collect { device.ram = it } }

      InfoOutlineIcon(
        "The amount of RAM on the AVD. This RAM is allocated from the host system while the AVD is running. Larger amounts of RAM will " +
          "allow the AVD to run more applications, but have a greater impact on the host system.",
        Modifier.align(Alignment.CenterVertically),
      )
    }

    Row(Modifier.testTag("VMHeapSizeRow")) {
      Text("VM heap size", Modifier.alignByBaseline().padding(end = Padding.SMALL))

      StorageCapacityField(
        state.vmHeapSize,
        state.vmHeapSize.result().vmHeapSizeErrorMessage(),
        Modifier.alignByBaseline().padding(end = Padding.MEDIUM),
        !hasGooglePlayStore,
      )

      LaunchedEffect(Unit) { state.vmHeapSize.storageCapacity.collect { device.vmHeapSize = it } }

      InfoOutlineIcon(
        "The amount of RAM available to the Java virtual machine (VM) to allocate to running apps on the AVD. A larger VM heap allows " +
          "applications to run longer between garbage collection events.",
        Modifier.align(Alignment.CenterVertically),
      )
    }
  }
}

internal class EmulatedPerformanceGroupState(device: VirtualDevice) {
  val ram = StorageCapacityFieldState(requireNotNull(device.ram), VirtualDevice.MIN_RAM, UNITS)

  val vmHeapSize =
    StorageCapacityFieldState(
      requireNotNull(device.vmHeapSize),
      VirtualDevice.MIN_VM_HEAP_SIZE,
      UNITS,
    )

  companion object {
    private val UNITS = listOf(StorageCapacity.Unit.MB, StorageCapacity.Unit.GB).toImmutableList()
  }
}

private fun Result.ramErrorMessage() =
  when (this) {
    is Valid -> null
    is Empty -> "Specify a RAM value"
    is LessThanMin ->
      "RAM must be at least ${VirtualDevice.MIN_RAM}. Recommendation is ${StorageCapacity(1, StorageCapacity.Unit.GB)}."
    is Overflow -> "RAM value is too large"
  }

private fun Result.vmHeapSizeErrorMessage() =
  when (this) {
    is Valid -> null
    is Empty -> "Specify a VM heap size"
    is LessThanMin -> "VM heap must be at least ${VirtualDevice.MIN_VM_HEAP_SIZE}"
    is Overflow -> "VM heap size is too large"
  }

@Composable
private fun PreferredAbiGroup(
  preferredAbi: String?,
  systemImage: ISystemImage?,
  onPreferredAbiChange: (String?) -> Unit,
) {
  val availableAbis = persistentListOf("Optimal").plus(systemImage.allAbiTypes())
  Row {
    Text("Preferred ABI", Modifier.alignByBaseline().padding(end = Padding.SMALL))

    // "Optimal" is our null value; it means we don't set a preferred ABI and use the default.
    Dropdown(
      preferredAbi ?: "Optimal",
      availableAbis,
      onSelectedItemChange = { onPreferredAbiChange(it.takeUnless { it == "Optimal" }) },
      Modifier.alignByBaseline(),
      enabled = availableAbis.isNotEmpty(),
      outline =
        if (preferredAbi == null || preferredAbi in availableAbis) Outline.None else Outline.Error,
    )
  }
}
