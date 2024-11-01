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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import com.android.resources.ScreenOrientation
import com.android.sdklib.internal.avd.AvdCamera
import com.android.sdklib.internal.avd.AvdNetworkLatency
import com.android.sdklib.internal.avd.AvdNetworkSpeed
import com.android.tools.idea.adddevicedialog.LocalProject
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
import org.jetbrains.jewel.ui.Outline
import org.jetbrains.jewel.ui.component.Dropdown
import org.jetbrains.jewel.ui.component.GroupHeader
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.RadioButtonRow
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import org.jetbrains.jewel.ui.icons.AllIconsKeys

@Composable
internal fun AdditionalSettingsPanel(
  configureDevicePanelState: ConfigureDevicePanelState,
  additionalSettingsPanelState: AdditionalSettingsPanelState,
  onImportButtonClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  VerticallyScrollableContainer(modifier) {
    Column(verticalArrangement = Arrangement.spacedBy(Padding.EXTRA_LARGE)) {
      Row {
        Text("Device skin", Modifier.padding(end = Padding.SMALL).alignByBaseline())

        Dropdown(
          configureDevicePanelState.device.skin,
          configureDevicePanelState.skins,
          onSelectedItemChange = {
            configureDevicePanelState.device = configureDevicePanelState.device.copy(skin = it)
          },
          Modifier.padding(end = Padding.MEDIUM).alignByBaseline(),
        )
      }

      CameraGroup(configureDevicePanelState.device, configureDevicePanelState::device::set)
      NetworkGroup(configureDevicePanelState.device, configureDevicePanelState::device::set)
      StartupGroup(configureDevicePanelState.device, configureDevicePanelState::device::set)

      StorageGroup(
        configureDevicePanelState.device,
        additionalSettingsPanelState.storageGroupState,
        configureDevicePanelState.validity.isExpandedStorageValid,
        configureDevicePanelState::device::set,
      )
      LaunchedEffect(Unit) {
        additionalSettingsPanelState.storageGroupState.expandedStorageFlow.collect(
          configureDevicePanelState::setExpandedStorage
        )
      }

      EmulatedPerformanceGroup(
        configureDevicePanelState.device,
        configureDevicePanelState::device::set,
      )
    }
  }
}

@Composable
private fun CameraGroup(device: VirtualDevice, onDeviceChange: (VirtualDevice) -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(Padding.MEDIUM)) {
    GroupHeader("Camera")

    Row {
      Text("Front", Modifier.alignByBaseline().padding(end = Padding.SMALL))

      Dropdown(
        device.frontCamera,
        FRONT_CAMERAS,
        onSelectedItemChange = { onDeviceChange(device.copy(frontCamera = it)) },
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

    Row {
      Text("Rear", Modifier.alignByBaseline().padding(end = Padding.SMALL))

      Dropdown(
        device.rearCamera,
        REAR_CAMERAS,
        onSelectedItemChange = { onDeviceChange(device.copy(rearCamera = it)) },
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

private val FRONT_CAMERAS =
  listOf(AvdCamera.NONE, AvdCamera.EMULATED, AvdCamera.WEBCAM).toImmutableList()

private val REAR_CAMERAS = AvdCamera.values().asIterable().toImmutableList()

@Composable
private fun NetworkGroup(device: VirtualDevice, onDeviceChange: (VirtualDevice) -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(Padding.MEDIUM)) {
    GroupHeader("Network")

    Row {
      Text("Speed", Modifier.alignByBaseline().padding(end = Padding.SMALL))

      Dropdown(
        device.speed,
        SPEEDS,
        onSelectedItemChange = { onDeviceChange(device.copy(speed = it)) },
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
        onSelectedItemChange = { onDeviceChange(device.copy(latency = it)) },
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
private fun StartupGroup(device: VirtualDevice, onDeviceChange: (VirtualDevice) -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(Padding.MEDIUM)) {
    GroupHeader("Startup")

    Row {
      Text("Orientation", Modifier.alignByBaseline().padding(end = Padding.SMALL))

      Dropdown(
        Modifier.alignByBaseline(),
        menuContent = {
          ORIENTATIONS.forEach {
            selectableItem(
              device.orientation == it,
              onClick = { onDeviceChange(device.copy(orientation = it)) },
            ) {
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
        onSelectedItemChange = { onDeviceChange(device.copy(defaultBoot = it)) },
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
private fun StorageGroup(
  device: VirtualDevice,
  storageGroupState: StorageGroupState,
  isExistingImageValid: Boolean,
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

      InfoOutlineIcon(
        "The amount of non-removable space available to store data on the AVD",
        Modifier.align(Alignment.CenterVertically),
      )
    }

    Row {
      Text("Expanded storage", Modifier.padding(end = Padding.MEDIUM))

      InfoOutlineIcon(
        """
        Custom: The amount of expanded storage available to store data on the AVD. We recommend at least 100 MB in order to use the camera in the emulator.
        Existing image: Choose a file path to an existing expanded storage image. Using an existing image is useful when sharing data (pictures, media, files, etc.) between AVDs. 
        None: No expanded storage on this AVD
        """
          .trimIndent()
      )
    }

    Row {
      RadioButtonRow(
        RadioButton.CUSTOM,
        storageGroupState.selectedRadioButton,
        onClick = { storageGroupState.selectedRadioButton = RadioButton.CUSTOM },
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
      RadioButtonRow(
        RadioButton.EXISTING_IMAGE,
        storageGroupState.selectedRadioButton,
        onClick = { storageGroupState.selectedRadioButton = RadioButton.EXISTING_IMAGE },
        Modifier.alignByBaseline().padding(end = Padding.SMALL).testTag("ExistingImageRadioButton"),
      )

      ExistingImageField(
        storageGroupState.existingImage,
        storageGroupState.selectedRadioButton == RadioButton.EXISTING_IMAGE,
        isExistingImageValid,
        Modifier.alignByBaseline().padding(end = Padding.MEDIUM),
      )
    }

    RadioButtonRow(
      RadioButton.NONE,
      storageGroupState.selectedRadioButton,
      onClick = { storageGroupState.selectedRadioButton = RadioButton.NONE },
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExistingImageField(
  existingImage: TextFieldState,
  enabled: Boolean,
  isExistingImageValid: Boolean,
  modifier: Modifier = Modifier,
) {
  Row(modifier) {
    @OptIn(ExperimentalJewelApi::class) val component = LocalComponent.current
    val project = LocalProject.current

    val errorText =
      "The specified image must be a valid file".takeIf { enabled && !isExistingImageValid }
    ErrorTooltip(errorText) {
      TextField(
        existingImage,
        Modifier.testTag("ExistingImageField"),
        enabled,
        outline = if (enabled && !isExistingImageValid) Outline.Error else Outline.None,
        trailingIcon = {
          Icon(
            AllIconsKeys.General.OpenDisk,
            null,
            Modifier.padding(start = Padding.MEDIUM_LARGE)
              .clickable(
                enabled,
                onClick = {
                  val image = chooseFile(component, project)
                  if (image != null) existingImage.setTextAndPlaceCursorAtEnd(image.toString())
                },
              )
              .pointerHoverIcon(PointerIcon.Default),
          )
        },
      )
    }
  }
}

private fun chooseFile(parent: Component, project: Project?): Path? {
  // TODO chooseFile logs an error because it does slow things on the EDT
  val virtualFile =
    FileChooser.chooseFile(
      FileChooserDescriptorFactory.createSingleFileDescriptor().withFileFilter {
        it.name.endsWith(".img", ignoreCase = true)
      },
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
      Text("Graphics acceleration", Modifier.alignByBaseline().padding(end = Padding.SMALL))

      Dropdown(
        device.graphicsMode,
        listOf(GraphicsMode.AUTO, GraphicsMode.HARDWARE, GraphicsMode.SOFTWARE).toImmutableList(),
        onSelectedItemChange = { onDeviceChange(device.copy(graphicsMode = it)) },
        Modifier.alignByBaseline(),
      )
    }

    Row {
      Text("RAM", Modifier.alignByBaseline().padding(end = Padding.SMALL))

      StorageCapacityField(
        device.ram,
        onValueChange = { onDeviceChange(device.copy(ram = it)) },
        Modifier.alignByBaseline().padding(end = Padding.MEDIUM),
      )

      InfoOutlineIcon(
        "The amount of RAM on the AVD. This RAM is allocated from the host system while the AVD is running. Larger amounts of RAM will " +
          "allow the AVD to run more applications, but have a greater impact on the host system.",
        Modifier.align(Alignment.CenterVertically),
      )
    }

    Row {
      Text("VM heap size", Modifier.alignByBaseline().padding(end = Padding.SMALL))

      StorageCapacityField(
        device.vmHeapSize,
        onValueChange = { onDeviceChange(device.copy(vmHeapSize = it)) },
        Modifier.alignByBaseline().padding(end = Padding.MEDIUM, bottom = Padding.SMALL),
      )

      InfoOutlineIcon(
        "The amount of RAM available to the Java virtual machine (VM) to allocate to running apps on the AVD. A larger VM heap allows " +
          "applications to run longer between garbage collection events.",
        Modifier.align(Alignment.CenterVertically),
      )
    }
  }
}

internal class AdditionalSettingsPanelState internal constructor(device: VirtualDevice) {
  internal val storageGroupState = StorageGroupState(device)
}

internal class StorageGroupState internal constructor(device: VirtualDevice) {
  internal var selectedRadioButton by mutableStateOf(RadioButton.valueOf(device.expandedStorage))
  internal var custom by mutableStateOf(customValue(device))
  internal val existingImage = TextFieldState(device.expandedStorage.toTextFieldValue())

  val expandedStorageFlow = snapshotFlow {
    when (selectedRadioButton) {
      RadioButton.CUSTOM -> Custom(custom.withMaxUnit())
      RadioButton.EXISTING_IMAGE -> ExistingImage(existingImage.text.toString())
      RadioButton.NONE -> None
    }
  }

  private companion object {
    private fun customValue(device: VirtualDevice) =
      if (device.expandedStorage is Custom) {
        device.expandedStorage.value
      } else {
        StorageCapacity(512, StorageCapacity.Unit.MB)
      }

    private fun ExpandedStorage.toTextFieldValue() = if (this is ExistingImage) toString() else ""
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
