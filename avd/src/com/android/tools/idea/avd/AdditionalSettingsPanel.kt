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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.resources.ScreenOrientation
import com.android.sdklib.ISystemImage
import com.android.sdklib.devices.CameraLocation
import com.android.sdklib.internal.avd.AvdCamera
import com.android.sdklib.internal.avd.AvdNetworkLatency
import com.android.sdklib.internal.avd.AvdNetworkSpeed
import com.android.tools.idea.adddevicedialog.FormFactors
import com.android.tools.idea.adddevicedialog.LocalProject
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import icons.StudioIconsCompose
import java.awt.Component
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.max
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.plus
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
  state: ConfigureDevicePanelState,
  onImportButtonClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val hasPlayStore = state.hasPlayStore()
  if (hasPlayStore) {
    WarningBanner(
      "Some device settings cannot be configured when using a Google Play Store image",
      Modifier.expandWidth(24.dp),
    )
  }

  VerticallyScrollableContainer(modifier) {
    Column(
      Modifier.padding(vertical = Padding.SMALL),
      verticalArrangement = Arrangement.spacedBy(Padding.EXTRA_LARGE),
    ) {
      Row {
        Text("Device skin", Modifier.padding(end = Padding.SMALL).alignByBaseline())

        Dropdown(
          state.device.skin,
          state.skins,
          onSelectedItemChange = { state.device = state.device.copy(skin = it) },
          Modifier.padding(end = Padding.MEDIUM).alignByBaseline(),
          !hasPlayStore && !state.device.isFoldable,
        )
      }

      CameraGroup(state.device, state::device::set)
      NetworkGroup(state.device, state::device::set)
      StartupGroup(state.device, state::device::set)

      StorageGroup(
        state.device,
        state.storageGroupState,
        hasPlayStore,
        state.validity.isExpandedStorageValid,
        state::device::set,
      )

      LaunchedEffect(Unit) {
        state.storageGroupState.expandedStorageFlow.collect(state::setExpandedStorage)
      }

      EmulatedPerformanceGroup(
        state.device,
        state.emulatedPerformanceGroupState,
        hasPlayStore,
        state::device::set,
      )

      PreferredAbiGroup(
        state.device.preferredAbi,
        state.systemImageTableSelectionState.selection,
        onPreferredAbiChange = state::setPreferredAbi,
      )
    }
  }
}

@Composable
private fun CameraGroup(device: VirtualDevice, onDeviceChange: (VirtualDevice) -> Unit) {
  val cameraLocations = device.device.defaultHardware.cameras.map { it.location }
  if (cameraLocations.isEmpty()) {
    return
  }
  Column(verticalArrangement = Arrangement.spacedBy(Padding.MEDIUM)) {
    GroupHeader("Camera")

    if (CameraLocation.FRONT in cameraLocations) {
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
    }

    if (CameraLocation.BACK in cameraLocations) {
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
  state: StorageGroupState,
  hasPlayStore: Boolean,
  isExistingImageValid: Boolean,
  onDeviceChange: (VirtualDevice) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(Padding.MEDIUM)) {
    GroupHeader("Storage")

    Row {
      Text("Internal storage", Modifier.alignByBaseline().padding(end = Padding.SMALL))

      StorageCapacityField(
        state.internalStorage,
        validateInternalStorage(state.internalStorage, hasPlayStore),
        Modifier.alignByBaseline().padding(end = Padding.MEDIUM),
      )

      @Suppress("NAME_SHADOWING") val device by rememberUpdatedState(device)

      LaunchedEffect(Unit) {
        state.internalStorage.storageCapacity.collect {
          onDeviceChange(device.copy(internalStorage = it))
        }
      }

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
        state.selectedRadioButton,
        onClick = { state.selectedRadioButton = RadioButton.CUSTOM },
        Modifier.alignByBaseline().padding(end = Padding.SMALL).testTag("CustomRadioButton"),
        !hasPlayStore,
      )

      val enabled = state.selectedRadioButton == RadioButton.CUSTOM
      val errorMessage = validateCustomExpandedStorage(state.custom, hasPlayStore, enabled)
      val isWarningVisible = state.isCustomChangedWarningVisible(errorMessage == null)

      StorageCapacityField(
        state.custom,
        errorMessage,
        Modifier.alignByBaseline(),
        enabled,
        when {
          errorMessage != null -> Outline.Error
          isWarningVisible -> Outline.Warning
          else -> Outline.None
        },
      )

      if (isWarningVisible) {
        Icon(
          StudioIconsCompose.Common.Warning,
          "Warning",
          Modifier.align(Alignment.CenterVertically)
            .padding(start = Padding.MEDIUM, end = Padding.SMALL_MEDIUM),
        )

        Text("Modifying storage size erases existing content", Modifier.alignByBaseline())
      }
    }

    Row {
      RadioButtonRow(
        RadioButton.EXISTING_IMAGE,
        state.selectedRadioButton,
        onClick = { state.selectedRadioButton = RadioButton.EXISTING_IMAGE },
        Modifier.alignByBaseline().padding(end = Padding.SMALL).testTag("ExistingImageRadioButton"),
        !hasPlayStore,
      )

      ExistingImageField(
        state.existingImage,
        state.selectedRadioButton == RadioButton.EXISTING_IMAGE && !hasPlayStore,
        isExistingImageValid,
        Modifier.alignByBaseline().padding(end = Padding.MEDIUM),
      )
    }

    RadioButtonRow(
      RadioButton.NONE,
      state.selectedRadioButton,
      onClick = { state.selectedRadioButton = RadioButton.NONE },
      enabled = !hasPlayStore,
    )
  }
}

private fun validateInternalStorage(
  storage: StorageCapacityFieldState,
  hasPlayStore: Boolean,
): String? {
  val capacity = storage.toStorageCapacity()

  return when {
    storage.valueIsEmpty() -> "Specify an internal storage value"
    storage.willOverflow() -> "Internal storage is too large"
    requireNotNull(capacity) < VirtualDevice.MIN_INTERNAL_STORAGE && hasPlayStore ->
      "Internal storage for Play Store devices must be at least ${VirtualDevice.MIN_INTERNAL_STORAGE}"
    capacity < VirtualDevice.MIN_INTERNAL_STORAGE ->
      "Internal storage must be at least ${VirtualDevice.MIN_INTERNAL_STORAGE}"
    else -> null
  }
}

private fun validateCustomExpandedStorage(
  storage: StorageCapacityFieldState,
  hasPlayStore: Boolean,
  customRadioButtonEnabled: Boolean,
): String? {
  val capacity = storage.toStorageCapacity()

  return when {
    !customRadioButtonEnabled -> null
    storage.valueIsEmpty() -> "Specify an SD card size"
    storage.willOverflow() -> "SD card size is too large"
    requireNotNull(capacity) < VirtualDevice.MIN_CUSTOM_EXPANDED_STORAGE_FOR_PLAY_STORE &&
      hasPlayStore ->
      "The SD card for Play Store devices must be at least ${VirtualDevice.MIN_CUSTOM_EXPANDED_STORAGE_FOR_PLAY_STORE}"
    capacity < VirtualDevice.MIN_CUSTOM_EXPANDED_STORAGE ->
      "The SD card must be at least ${VirtualDevice.MIN_CUSTOM_EXPANDED_STORAGE}"
    else -> null
  }
}

@Composable
private fun <E : Enum<E>> RadioButtonRow(
  value: Enum<E>,
  selectedValue: Enum<E>,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean,
) {
  RadioButtonRow(value.toString(), selectedValue == value, onClick, modifier, enabled)
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
  val descriptor =
    FileChooserDescriptor(
        /* chooseFiles= */ true,
        /* chooseFolders= */ false,
        /* chooseJars= */ true,
        /* chooseJarsAsFiles= */ true,
        /* chooseJarContents= */ false,
        /* chooseMultiple= */ false,
      )
      .withFileFilter { it.name.endsWith(".img", ignoreCase = true) }

  // TODO chooseFile logs an error because it does slow things on the EDT
  val virtualFile = FileChooser.chooseFile(descriptor, parent, project, null) ?: return null

  val path = virtualFile.toNioPath()
  assert(Files.isRegularFile(path))

  return path
}

@Composable
private fun EmulatedPerformanceGroup(
  device: VirtualDevice,
  state: EmulatedPerformanceGroupState,
  hasGooglePlayStore: Boolean,
  onDeviceChange: (VirtualDevice) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(Padding.MEDIUM)) {
    GroupHeader("Emulated Performance")

    Row {
      Text("CPU cores", Modifier.alignByBaseline().padding(end = Padding.SMALL))
      val cpuCoreCount = device.cpuCoreCount ?: 1

      Dropdown(
        Modifier.alignByBaseline(),
        device.cpuCoreCount != null && !hasGooglePlayStore,
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
        !hasGooglePlayStore,
      )
    }

    @Suppress("NAME_SHADOWING") val device by rememberUpdatedState(device)

    Row {
      Text("RAM", Modifier.alignByBaseline().padding(end = Padding.SMALL))

      StorageCapacityField(
        state.ram,
        validateRam(state.ram),
        Modifier.alignByBaseline().padding(end = Padding.MEDIUM),
        !hasGooglePlayStore || device.formFactor == FormFactors.AUTO,
      )

      LaunchedEffect(Unit) {
        state.ram.storageCapacity.collect { onDeviceChange(device.copy(ram = it)) }
      }

      InfoOutlineIcon(
        "The amount of RAM on the AVD. This RAM is allocated from the host system while the AVD is running. Larger amounts of RAM will " +
          "allow the AVD to run more applications, but have a greater impact on the host system.",
        Modifier.align(Alignment.CenterVertically),
      )
    }

    Row {
      Text("VM heap size", Modifier.alignByBaseline().padding(end = Padding.SMALL))

      StorageCapacityField(
        state.vmHeapSize,
        validateVmHeapSize(state.vmHeapSize),
        Modifier.alignByBaseline().padding(end = Padding.MEDIUM),
        !hasGooglePlayStore,
      )

      LaunchedEffect(Unit) {
        state.vmHeapSize.storageCapacity.collect { onDeviceChange(device.copy(vmHeapSize = it)) }
      }

      InfoOutlineIcon(
        "The amount of RAM available to the Java virtual machine (VM) to allocate to running apps on the AVD. A larger VM heap allows " +
          "applications to run longer between garbage collection events.",
        Modifier.align(Alignment.CenterVertically),
      )
    }
  }
}

internal class EmulatedPerformanceGroupState internal constructor(device: VirtualDevice) {
  internal val ram = StorageCapacityFieldState(requireNotNull(device.ram))
  internal val vmHeapSize = StorageCapacityFieldState(requireNotNull(device.vmHeapSize))
}

private fun validateRam(ram: StorageCapacityFieldState) =
  when {
    ram.valueIsEmpty() -> "Specify a RAM value"
    ram.willOverflow() -> "RAM value is too large"
    requireNotNull(ram.toStorageCapacity()) < VirtualDevice.MIN_RAM ->
      "RAM must be at least ${VirtualDevice.MIN_RAM}. Recommendation is ${StorageCapacity(1, StorageCapacity.Unit.GB)}."
    else -> null
  }

private fun validateVmHeapSize(size: StorageCapacityFieldState) =
  when {
    size.valueIsEmpty() -> "Specify a VM heap size"
    size.willOverflow() -> "VM heap size is too large"
    requireNotNull(size.toStorageCapacity()) < VirtualDevice.MIN_VM_HEAP_SIZE ->
      "VM heap must be at least ${VirtualDevice.MIN_VM_HEAP_SIZE}"
    else -> null
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

internal class StorageGroupState internal constructor(private val device: VirtualDevice) {
  internal val internalStorage = StorageCapacityFieldState(requireNotNull(device.internalStorage))
  internal var selectedRadioButton by mutableStateOf(RadioButton.valueOf(device.expandedStorage))
  internal val custom = StorageCapacityFieldState(customValue(device))
  internal val existingImage = TextFieldState(device.expandedStorage.toTextFieldValue())

  val expandedStorageFlow = snapshotFlow {
    when (selectedRadioButton) {
      RadioButton.CUSTOM -> {
        val value = custom.toStorageCapacity()
        if (value == null) null else Custom(value.withMaxUnit())
      }
      RadioButton.EXISTING_IMAGE -> ExistingImage(existingImage.text.toString())
      RadioButton.NONE -> None
    }
  }

  internal fun isCustomChangedWarningVisible(isValid: Boolean) =
    when {
      selectedRadioButton != RadioButton.CUSTOM -> false
      !isValid -> false
      device.existingCustomExpandedStorage == null -> false
      else ->
        device.existingCustomExpandedStorage !=
          Custom(checkNotNull(custom.toStorageCapacity()).withMaxUnit())
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

/** Extends the maxWidth of the incoming constraints by the given [extraWidth]. */
internal fun Modifier.expandWidth(extraWidth: Dp) = layout { measurable, constraints ->
  val placeable =
    measurable.measure(constraints.copy(maxWidth = constraints.maxWidth + extraWidth.roundToPx()))
  layout(placeable.width, placeable.height) { placeable.place(0, 0) }
}
