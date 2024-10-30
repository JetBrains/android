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
import androidx.compose.ui.platform.testTag
import com.android.tools.idea.adddevicedialog.LocalProject
import com.android.tools.idea.avd.StorageCapacityFieldState.Empty
import com.android.tools.idea.avd.StorageCapacityFieldState.Overflow
import com.android.tools.idea.avd.StorageCapacityFieldState.Result
import com.android.tools.idea.avd.StorageCapacityFieldState.Valid
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import icons.StudioIconsCompose
import java.awt.Component
import java.nio.file.Files
import java.nio.file.Path
import org.jetbrains.jewel.bridge.LocalComponent
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.ui.Outline
import org.jetbrains.jewel.ui.component.GroupHeader
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.RadioButtonRow
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.icons.AllIconsKeys

@Composable
internal fun StorageGroup(
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
        validateInternalStorage(state.internalStorage.result(), hasPlayStore),
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
        ExpandedStorageRadioButton.CUSTOM,
        state.selectedRadioButton,
        onClick = { state.selectedRadioButton = ExpandedStorageRadioButton.CUSTOM },
        Modifier.alignByBaseline().padding(end = Padding.SMALL).testTag("CustomRadioButton"),
        !hasPlayStore,
      )

      val enabled = state.selectedRadioButton == ExpandedStorageRadioButton.CUSTOM
      val errorMessage = validateCustomExpandedStorage(state.custom.result(), hasPlayStore, enabled)
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
        ExpandedStorageRadioButton.EXISTING_IMAGE,
        state.selectedRadioButton,
        onClick = { state.selectedRadioButton = ExpandedStorageRadioButton.EXISTING_IMAGE },
        Modifier.alignByBaseline().padding(end = Padding.SMALL).testTag("ExistingImageRadioButton"),
        !hasPlayStore,
      )

      ExistingImageField(
        state.existingImage,
        state.selectedRadioButton == ExpandedStorageRadioButton.EXISTING_IMAGE && !hasPlayStore,
        isExistingImageValid,
        Modifier.alignByBaseline().padding(end = Padding.MEDIUM),
      )
    }

    RadioButtonRow(
      ExpandedStorageRadioButton.NONE,
      state.selectedRadioButton,
      onClick = { state.selectedRadioButton = ExpandedStorageRadioButton.NONE },
      enabled = !hasPlayStore,
    )
  }
}

private fun validateInternalStorage(storage: Result, hasPlayStore: Boolean) =
  when (storage) {
    is Empty -> "Specify an internal storage value"
    is Overflow -> "Internal storage is too large"
    is Valid ->
      when {
        storage.storageCapacity < VirtualDevice.MIN_INTERNAL_STORAGE && hasPlayStore ->
          "Internal storage for Play Store devices must be at least ${VirtualDevice.MIN_INTERNAL_STORAGE}"
        storage.storageCapacity < VirtualDevice.MIN_INTERNAL_STORAGE ->
          "Internal storage must be at least ${VirtualDevice.MIN_INTERNAL_STORAGE}"
        else -> null
      }
  }

private fun validateCustomExpandedStorage(
  storage: Result,
  hasPlayStore: Boolean,
  customRadioButtonEnabled: Boolean,
) =
  if (!customRadioButtonEnabled) {
    null
  } else {
    when (storage) {
      is Empty -> "Specify an SD card size"
      is Overflow -> "SD card size is too large"
      is Valid ->
        when {
          storage.storageCapacity < VirtualDevice.MIN_CUSTOM_EXPANDED_STORAGE_FOR_PLAY_STORE &&
            hasPlayStore ->
            "The SD card for Play Store devices must be at least ${VirtualDevice.MIN_CUSTOM_EXPANDED_STORAGE_FOR_PLAY_STORE}"
          storage.storageCapacity < VirtualDevice.MIN_CUSTOM_EXPANDED_STORAGE ->
            "The SD card must be at least ${VirtualDevice.MIN_CUSTOM_EXPANDED_STORAGE}"
          else -> null
        }
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

  val virtualFile = FileChooser.chooseFile(descriptor, parent, project, null) ?: return null

  val path = virtualFile.toNioPath()
  assert(Files.isRegularFile(path))

  return path
}

internal class StorageGroupState internal constructor(private val device: VirtualDevice) {
  internal val internalStorage = StorageCapacityFieldState(requireNotNull(device.internalStorage))

  internal var selectedRadioButton by
    mutableStateOf(ExpandedStorageRadioButton.valueOf(device.expandedStorage))

  internal val custom = StorageCapacityFieldState(customValue(device))
  internal val existingImage = TextFieldState(device.expandedStorage.toTextFieldValue())

  val expandedStorageFlow = snapshotFlow {
    when (selectedRadioButton) {
      ExpandedStorageRadioButton.CUSTOM -> {
        val value = custom.result().storageCapacity
        if (value == null) null else Custom(value.withMaxUnit())
      }
      ExpandedStorageRadioButton.EXISTING_IMAGE -> ExistingImage(existingImage.text.toString())
      ExpandedStorageRadioButton.NONE -> None
    }
  }

  internal fun isCustomChangedWarningVisible(isValid: Boolean) =
    when {
      selectedRadioButton != ExpandedStorageRadioButton.CUSTOM -> false
      !isValid -> false
      device.existingCustomExpandedStorage == null -> false
      else ->
        device.existingCustomExpandedStorage != Custom(custom.valid().storageCapacity).withMaxUnit()
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

internal enum class ExpandedStorageRadioButton {
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
