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
package com.android.tools.idea.adddevicedialog.localavd

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.testTag
import com.android.resources.ScreenOrientation
import com.android.sdklib.internal.avd.AvdCamera
import com.android.sdklib.internal.avd.AvdNetworkLatency
import com.android.sdklib.internal.avd.AvdNetworkSpeed
import com.android.sdklib.internal.avd.EmulatedProperties
import com.android.sdklib.internal.avd.GpuMode
import com.android.tools.idea.adddevicedialog.LocalFileSystem
import com.android.tools.idea.adddevicedialog.LocalProject
import com.intellij.icons.AllIcons
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import java.awt.Component
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.max
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.jewel.bridge.LocalComponent
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.Dropdown
import org.jetbrains.jewel.ui.component.GroupHeader
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.RadioButtonRow
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField

@Composable
internal fun AdditionalSettingsPanel(
  configureDevicePanelState: ConfigureDevicePanelState,
  additionalSettingsPanelState: AdditionalSettingsPanelState,
  onImportButtonClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val scrollState = rememberScrollState()
  Box(modifier) {
    Column(
      Modifier.verticalScroll(scrollState),
      verticalArrangement = Arrangement.spacedBy(Padding.EXTRA_LARGE),
    ) {
      Row {
        Text("Device skin", Modifier.padding(end = Padding.SMALL))

        Dropdown(
          configureDevicePanelState.device.skin,
          configureDevicePanelState.skins,
          onSelectedItemChange = {
            configureDevicePanelState.device = configureDevicePanelState.device.copy(skin = it)
          },
          Modifier.padding(end = Padding.MEDIUM),
        )

        OutlinedButton(onImportButtonClick) { Text("Import") }
      }

      CameraGroup(configureDevicePanelState.device, configureDevicePanelState::device::set)
      NetworkGroup(configureDevicePanelState.device, configureDevicePanelState::device::set)
      StartupGroup(configureDevicePanelState.device, configureDevicePanelState::device::set)

      StorageGroup(
        configureDevicePanelState.device,
        additionalSettingsPanelState.storageGroupState,
        configureDevicePanelState::device::set,
      )

      EmulatedPerformanceGroup(
        configureDevicePanelState.device,
        configureDevicePanelState::device::set,
      )
    }
    VerticalScrollbar(
      rememberScrollbarAdapter(scrollState),
      modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
    )
  }
}

@Composable
private fun CameraGroup(device: VirtualDevice, onDeviceChange: (VirtualDevice) -> Unit) {
  GroupLayout {
    GroupHeader("Camera")
    Text("Front")

    Dropdown(
      device.frontCamera,
      FRONT_CAMERAS,
      onSelectedItemChange = { onDeviceChange(device.copy(frontCamera = it)) },
    )

    InfoOutlineIcon(Modifier.layoutId(Icon))
    Text("Rear")

    Dropdown(
      device.rearCamera,
      REAR_CAMERAS,
      onSelectedItemChange = { onDeviceChange(device.copy(rearCamera = it)) },
    )

    InfoOutlineIcon(Modifier.layoutId(Icon))
  }
}

private val FRONT_CAMERAS =
  listOf(AvdCamera.NONE, AvdCamera.EMULATED, AvdCamera.WEBCAM).toImmutableList()

private val REAR_CAMERAS = AvdCamera.values().asIterable().toImmutableList()

@Composable
private fun NetworkGroup(device: VirtualDevice, onDeviceChange: (VirtualDevice) -> Unit) {
  GroupLayout {
    GroupHeader("Network")
    Text("Speed")

    Dropdown(
      device.speed,
      SPEEDS,
      onSelectedItemChange = { onDeviceChange(device.copy(speed = it)) },
    )

    InfoOutlineIcon(Modifier.layoutId(Icon))
    Text("Latency")

    Dropdown(
      device.latency,
      LATENCIES,
      onSelectedItemChange = { onDeviceChange(device.copy(latency = it)) },
    )

    InfoOutlineIcon(Modifier.layoutId(Icon))
  }
}

private val SPEEDS = AvdNetworkSpeed.values().asIterable().toImmutableList()
private val LATENCIES = AvdNetworkLatency.values().asIterable().toImmutableList()

@Composable
private fun StartupGroup(device: VirtualDevice, onDeviceChange: (VirtualDevice) -> Unit) {
  GroupLayout {
    GroupHeader("Startup")
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

    Text("Default boot")

    Dropdown(
      device.defaultBoot,
      BOOTS,
      onSelectedItemChange = { onDeviceChange(device.copy(defaultBoot = it)) },
    )

    InfoOutlineIcon(Modifier.layoutId(Icon))
  }
}

private val ORIENTATIONS =
  listOf(ScreenOrientation.PORTRAIT, ScreenOrientation.LANDSCAPE).toImmutableList()

private val BOOTS = enumValues<Boot>().asIterable().toImmutableList()

@Composable
private fun StorageGroup(
  device: VirtualDevice,
  storageGroupState: StorageGroupState,
  onDeviceChange: (VirtualDevice) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(Padding.MEDIUM)) {
    GroupHeader("Storage")

    Row {
      Text("Internal storage", Modifier.alignByBaseline().padding(end = Padding.SMALL))

      StorageCapacityField(
        device.internalStorage,
        onValueChange = { onDeviceChange(device.copy(internalStorage = it)) },
        Modifier.alignByBaseline().padding(end = Padding.MEDIUM),
      )

      InfoOutlineIcon(Modifier.align(Alignment.CenterVertically))
    }

    Row {
      Text("Expanded storage", Modifier.padding(end = Padding.MEDIUM))
      InfoOutlineIcon()
    }

    Row {
      RadioButtonRow(
        RadioButton.CUSTOM,
        storageGroupState.selectedRadioButton,
        onClick = {
          storageGroupState.selectedRadioButton = RadioButton.CUSTOM

          val custom = storageGroupState.custom.withMaxUnit()
          onDeviceChange(device.copy(expandedStorage = Custom(custom)))
        },
        Modifier.alignByBaseline().padding(end = Padding.SMALL).testTag("CustomRadioButton"),
      )

      StorageCapacityField(
        storageGroupState.custom,
        onValueChange = {
          storageGroupState.custom = it
          onDeviceChange(device.copy(expandedStorage = Custom(it.withMaxUnit())))
        },
        Modifier.alignByBaseline(),
        storageGroupState.selectedRadioButton == RadioButton.CUSTOM,
      )
    }

    Row {
      val existingImageFieldState = storageGroupState.existingImageFieldState
      val fileSystem = LocalFileSystem.current

      RadioButtonRow(
        RadioButton.EXISTING_IMAGE,
        storageGroupState.selectedRadioButton,
        onClick = {
          storageGroupState.selectedRadioButton = RadioButton.EXISTING_IMAGE

          if (existingImageFieldState.valid) {
            val image = fileSystem.getPath(existingImageFieldState.value)
            onDeviceChange(device.copy(expandedStorage = ExistingImage(image)))
          }
        },
        Modifier.alignByBaseline().padding(end = Padding.SMALL).testTag("ExistingImageRadioButton"),
      )

      ExistingImageField(
        existingImageFieldState,
        storageGroupState.selectedRadioButton == RadioButton.EXISTING_IMAGE,
        onStateChange = {
          storageGroupState.existingImageFieldState = it

          if (it.valid) {
            val image = fileSystem.getPath(it.value)
            onDeviceChange(device.copy(expandedStorage = ExistingImage(image)))
          }

          // TODO Else image is not valid. Disable the Add button.
        },
        Modifier.alignByBaseline(),
      )
    }

    RadioButtonRow(
      RadioButton.NONE,
      storageGroupState.selectedRadioButton,
      onClick = {
        storageGroupState.selectedRadioButton = RadioButton.NONE
        onDeviceChange(device.copy(expandedStorage = None))
      },
    )
  }
}

@Composable
private fun <E : Enum<E>> RadioButtonRow(
  value: Enum<E>,
  selectedValue: Enum<E>,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  RadioButtonRow(value.toString(), selectedValue == value, onClick, modifier)
}

@Composable
private fun ExistingImageField(
  state: ExistingImageFieldState,
  enabled: Boolean,
  onStateChange: (ExistingImageFieldState) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(modifier) {
    if (enabled && !state.valid) {
      Text("The specified image must be a valid file")
    }

    val fileSystem = LocalFileSystem.current
    @OptIn(ExperimentalJewelApi::class) val component = LocalComponent.current
    val project = LocalProject.current

    TextField(
      state.value,
      onValueChange = {
        onStateChange(ExistingImageFieldState(it, Files.isRegularFile(fileSystem.getPath(it))))
      },
      Modifier.testTag("ExistingImageField"),
      enabled,
      trailingIcon = {
        Icon(
          "general/openDisk.svg",
          null,
          AllIcons::class.java,
          Modifier.clickable(
              enabled,
              onClick = {
                val image = chooseFile(component, project)

                if (image != null) {
                  onStateChange(ExistingImageFieldState(image.toString(), true))
                }
              },
            )
            .pointerHoverIcon(PointerIcon.Default),
        )
      },
    )
  }
}

private fun chooseFile(parent: Component, project: Project?): Path? {
  // TODO chooseFile logs an error because it does slow things on the EDT
  val virtualFile =
    FileChooser.chooseFile(
      FileChooserDescriptorFactory.createSingleFileDescriptor(),
      parent,
      project,
      null,
    )

  if (virtualFile == null) {
    return null
  }

  val path = virtualFile.toNioPath()
  assert(Files.isRegularFile(path))

  return path
}

@Composable
private fun EmulatedPerformanceGroup(
  device: VirtualDevice,
  onDeviceChange: (VirtualDevice) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(Padding.MEDIUM)) {
    GroupHeader("Emulated Performance")

    Row {
      CheckboxRow(
        "Enable multithreading",
        device.cpuCoreCount != null,
        onCheckedChange = {
          val count = if (it) EmulatedProperties.RECOMMENDED_NUMBER_OF_CORES else null
          onDeviceChange(device.copy(cpuCoreCount = count))
        },
        Modifier.padding(end = Padding.MEDIUM),
      )

      InfoOutlineIcon(Modifier.align(Alignment.CenterVertically))
    }

    Row {
      Text("CPU cores", Modifier.alignByBaseline().padding(end = Padding.SMALL))
      val cpuCoreCount = device.cpuCoreCount ?: 1

      Dropdown(
        Modifier.alignByBaseline(),
        device.cpuCoreCount != null,
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
      Text("Graphic acceleration", Modifier.alignByBaseline().padding(end = Padding.SMALL))

      Dropdown(
        device.graphicAcceleration,
        GRAPHIC_ACCELERATION_ITEMS,
        onSelectedItemChange = { onDeviceChange(device.copy(graphicAcceleration = it)) },
        Modifier.alignByBaseline(),
      )
    }

    Row {
      Text("Simulated RAM", Modifier.alignByBaseline().padding(end = Padding.SMALL))

      StorageCapacityField(
        device.simulatedRam,
        onValueChange = { onDeviceChange(device.copy(simulatedRam = it)) },
        Modifier.alignByBaseline().padding(end = Padding.MEDIUM),
      )

      InfoOutlineIcon(Modifier.align(Alignment.CenterVertically))
    }

    Row {
      Text("VM heap size", Modifier.alignByBaseline().padding(end = Padding.SMALL))

      StorageCapacityField(
        device.vmHeapSize,
        onValueChange = { onDeviceChange(device.copy(vmHeapSize = it)) },
        Modifier.alignByBaseline().padding(end = Padding.MEDIUM),
      )

      InfoOutlineIcon(Modifier.align(Alignment.CenterVertically))
    }
  }
}

// TODO The third item depends on the system image
private val GRAPHIC_ACCELERATION_ITEMS =
  GpuMode.values().filterNot { it == GpuMode.OFF }.toImmutableList()

internal class AdditionalSettingsPanelState internal constructor(device: VirtualDevice) {
  internal val storageGroupState = StorageGroupState(device)
}

internal class StorageGroupState internal constructor(device: VirtualDevice) {
  internal var selectedRadioButton by mutableStateOf(RadioButton.valueOf(device.expandedStorage))
  internal var custom by mutableStateOf(customValue(device))

  internal var existingImageFieldState by
    mutableStateOf(ExistingImageFieldState.from(device.expandedStorage))

  private companion object {
    private fun customValue(device: VirtualDevice) =
      if (device.expandedStorage is Custom) {
        device.expandedStorage.value
      } else {
        StorageCapacity(512, StorageCapacity.Unit.MB)
      }
  }
}

internal enum class RadioButton {
  CUSTOM {
    override fun toString() = "Custom"
  },
  EXISTING_IMAGE {
    override fun toString() = "Existing image"
  },
  NONE {
    override fun toString() = "None"
  };

  internal companion object {
    internal fun valueOf(storage: ExpandedStorage) =
      when (storage) {
        is Custom -> CUSTOM
        is ExistingImage -> EXISTING_IMAGE
        is None -> NONE
      }
  }
}

/**
 * @property value the value of the Existing image text field
 * @property valid if Files.isRegularFile(Path.of(value)) is true
 */
internal data class ExistingImageFieldState
internal constructor(internal val value: String, internal val valid: Boolean) {
  internal companion object {
    internal fun from(storage: ExpandedStorage) =
      if (storage is ExistingImage) {
        // If storage is an ExistingImage the Existing image radio button is selected.
        // storage.toString() returns storage.value.toString() which must be a valid path. Set valid
        // to true.
        ExistingImageFieldState(storage.toString(), true)
      } else {
        // The Existing image radio button is not selected. The Existing image text field is still
        // displayed, and it still needs a string value. Use the empty string and set valid to false
        // because the empty string is not a path to a real file.
        ExistingImageFieldState("", false)
      }
  }
}
