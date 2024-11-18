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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextReplacement
import com.android.resources.ScreenOrientation
import com.android.sdklib.devices.Device
import com.android.sdklib.devices.Hardware
import com.android.sdklib.internal.avd.AvdCamera
import com.android.sdklib.internal.avd.AvdNetworkLatency
import com.android.sdklib.internal.avd.AvdNetworkSpeed
import com.android.testutils.file.createInMemoryFileSystem
import com.android.tools.adtui.compose.utils.StudioComposeTestRule.Companion.createStudioComposeTestRule
import com.android.tools.idea.avdmanager.skincombobox.DefaultSkin
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunsInEdt
@RunWith(JUnit4::class)
class StorageGroupTest {
  private var device by mutableStateOf(pixel6())
  private val state = StorageGroupState(device, createInMemoryFileSystem())

  @get:Rule val composeRule = createStudioComposeTestRule()
  @get:Rule val edtRule = EdtRule()

  @Test
  fun internalStorageIsValid() {
    // Arrange
    setContent { StorageGroup(device, state, false, onDeviceChange = { device = it }) }

    // Act
    composeRule.onInternalStorageTextField().performTextReplacement("3")

    @OptIn(ExperimentalTestApi::class)
    composeRule.onInternalStorageTextField().performMouseInput { moveTo(center) }

    // Assert
    composeRule.onTooltips().assertCountEquals(0)
    assertEquals(device.copy(internalStorage = StorageCapacity(3, StorageCapacity.Unit.GB)), device)
  }

  @Test
  fun internalStorageIsEmpty() {
    // Arrange
    setContent { StorageGroup(device, state, false, onDeviceChange = { device = it }) }

    // Act
    composeRule.onInternalStorageTextField().performTextReplacement("")

    @OptIn(ExperimentalTestApi::class)
    composeRule.onInternalStorageTextField().performMouseInput { moveTo(center) }

    // Assert
    composeRule.onNodeWithText("Specify an internal storage value").assertIsDisplayed()
    assertEquals(device.copy(internalStorage = null), device)
  }

  @Test
  fun internalStorageIsLessThanMinAndHasPlayStore() {
    // Arrange
    setContent { StorageGroup(device, state, true, onDeviceChange = { device = it }) }

    // Act
    composeRule.onInternalStorageTextField().performTextReplacement("1")

    @OptIn(ExperimentalTestApi::class)
    composeRule.onInternalStorageTextField().performMouseInput { moveTo(center) }

    // Assert
    composeRule
      .onNodeWithText("Internal storage for Play Store devices must be at least 2G")
      .assertIsDisplayed()

    assertEquals(device.copy(internalStorage = null), device)
  }

  @Test
  fun internalStorageIsLessThanMin() {
    // Arrange
    setContent { StorageGroup(device, state, false, onDeviceChange = { device = it }) }

    // Act
    composeRule.onInternalStorageTextField().performTextReplacement("1")

    @OptIn(ExperimentalTestApi::class)
    composeRule.onInternalStorageTextField().performMouseInput { moveTo(center) }

    // Assert
    composeRule.onNodeWithText("Internal storage must be at least 2G").assertIsDisplayed()
    assertEquals(device.copy(internalStorage = null), device)
  }

  @Test
  fun internalStorageIsOverflow() {
    // Arrange
    setContent { StorageGroup(device, state, false, onDeviceChange = { device = it }) }

    // Act
    composeRule.onInternalStorageTextField().performTextReplacement("8589934592")

    @OptIn(ExperimentalTestApi::class)
    composeRule.onInternalStorageTextField().performMouseInput { moveTo(center) }

    // Assert
    composeRule.onNodeWithText("Internal storage is too large").assertIsDisplayed()
    assertEquals(device.copy(internalStorage = null), device)
  }

  @Test
  fun onCustomRadioButtonClick() {
    // Arrange
    setContent { StorageGroup(device, state, false, onDeviceChange = { device = it }) }

    // Act
    composeRule.onNodeWithText("Existing image").performClick()
    composeRule.onNodeWithText("Custom").performClick()
    composeRule.waitForIdle()

    // Assert
    assertEquals(ExpandedStorageRadioButton.CUSTOM, state.selectedRadioButton)

    assertEquals(
      device.copy(expandedStorage = Custom(StorageCapacity(512, StorageCapacity.Unit.MB))),
      device,
    )
  }

  @Test
  fun onExistingImageRadioButtonClick() {
    // Arrange
    setContent { StorageGroup(device, state, false, onDeviceChange = { device = it }) }

    // Act
    composeRule.onNodeWithText("Existing image").performClick()

    @OptIn(ExperimentalTestApi::class)
    composeRule.onNodeWithTag("ExistingImageField").performMouseInput { moveTo(center) }

    // Assert
    assertEquals(ExpandedStorageRadioButton.EXISTING_IMAGE, state.selectedRadioButton)
    composeRule.onNodeWithText("The specified image must be a valid file").assertIsDisplayed()
    assertEquals(device.copy(expandedStorage = null), device)
  }

  @Test
  fun onNoneRadioButtonClick() {
    // Arrange
    setContent { StorageGroup(device, state, false, onDeviceChange = { device = it }) }

    // Act
    composeRule.onNodeWithText("None").performClick()
    composeRule.waitForIdle()

    // Assert
    assertEquals(ExpandedStorageRadioButton.NONE, state.selectedRadioButton)
    assertEquals(device.copy(expandedStorage = None), device)
  }

  @Test
  fun customIsValid() {
    // Arrange
    setContent { StorageGroup(device, state, false, onDeviceChange = { device = it }) }

    // Act
    composeRule.onCustomTextField().performTextReplacement("513")

    @OptIn(ExperimentalTestApi::class)
    composeRule.onCustomTextField().performMouseInput { moveTo(center) }

    // Assert
    composeRule.onTooltips().assertCountEquals(0)

    assertEquals(
      device.copy(expandedStorage = Custom(StorageCapacity(513, StorageCapacity.Unit.MB))),
      device,
    )
  }

  @Test
  fun customIsEmpty() {
    // Arrange
    setContent { StorageGroup(device, state, false, onDeviceChange = { device = it }) }

    // Act
    composeRule.onCustomTextField().performTextReplacement("")

    @OptIn(ExperimentalTestApi::class)
    composeRule.onCustomTextField().performMouseInput { moveTo(center) }

    // Assert
    composeRule.onNodeWithText("Specify an SD card size").assertIsDisplayed()
    assertEquals(device.copy(expandedStorage = null), device)
  }

  @Test
  fun customIsLessThanMinAndHasPlayStore() {
    // Arrange
    setContent { StorageGroup(device, state, true, onDeviceChange = { device = it }) }

    // Act
    composeRule.onCustomTextField().performTextReplacement("99")

    @OptIn(ExperimentalTestApi::class)
    composeRule.onCustomTextField().performMouseInput { moveTo(center) }

    // Assert
    composeRule
      .onNodeWithText("The SD card for Play Store devices must be at least 100M")
      .assertIsDisplayed()

    assertEquals(device.copy(expandedStorage = null), device)
  }

  @Test
  fun customIsLessThanMin() {
    // Arrange
    setContent { StorageGroup(device, state, false, onDeviceChange = { device = it }) }

    // Act
    composeRule.onCustomTextField().performTextReplacement("9")

    @OptIn(ExperimentalTestApi::class)
    composeRule.onCustomTextField().performMouseInput { moveTo(center) }

    // Assert
    composeRule.onNodeWithText("The SD card must be at least 10M").assertIsDisplayed()
    assertEquals(device.copy(expandedStorage = null), device)
  }

  @Test
  fun customIsOverflow() {
    // Arrange
    setContent { StorageGroup(device, state, false, onDeviceChange = { device = it }) }

    // Act
    composeRule.onCustomTextField().performTextReplacement("8796093022208")

    @OptIn(ExperimentalTestApi::class)
    composeRule.onCustomTextField().performMouseInput { moveTo(center) }

    // Assert
    composeRule.onNodeWithText("SD card size is too large").assertIsDisplayed()
    assertEquals(device.copy(expandedStorage = null), device)
  }

  @Test
  fun existingCustomExpandedStorageDoesntEqualState() {
    // Arrange
    device =
      device.copy(
        existingCustomExpandedStorage = Custom(StorageCapacity(512, StorageCapacity.Unit.MB))
      )

    setContent {
      StorageGroup(
        device,
        StorageGroupState(device, createInMemoryFileSystem()),
        false,
        onDeviceChange = { device = it },
      )
    }

    // Act
    composeRule.onCustomTextField().performTextReplacement("513")
    composeRule.waitForIdle()

    // Assert
    composeRule.onNodeWithText("Modifying storage size erases existing content").assertIsDisplayed()
  }

  private fun setContent(composable: @Composable () -> Unit) {
    composeRule.setContent { provideCompositionLocals { composable() } }
  }

  private companion object {
    private fun pixel6(): VirtualDevice {
      val hardware = mock<Hardware>()
      whenever(hardware.screen).thenReturn(mock())

      val device = mock<Device>()
      whenever(device.defaultHardware).thenReturn(hardware)

      return VirtualDevice(
        name = "Pixel 6",
        device = device,
        skin =
          DefaultSkin(
            createInMemoryFileSystem()
              .getPath(System.getProperty("user.home"), "Android", "Sdk", "skins", "pixel_6")
          ),
        frontCamera = AvdCamera.EMULATED,
        rearCamera = AvdCamera.VIRTUAL_SCENE,
        speed = AvdNetworkSpeed.FULL,
        latency = AvdNetworkLatency.NONE,
        orientation = ScreenOrientation.PORTRAIT,
        defaultBoot = Boot.QUICK,
        internalStorage = StorageCapacity(2, StorageCapacity.Unit.GB),
        expandedStorage = Custom(StorageCapacity(512, StorageCapacity.Unit.MB)),
        cpuCoreCount = 4,
        graphicsMode = GraphicsMode.AUTO,
        ram = StorageCapacity(2, StorageCapacity.Unit.GB),
        vmHeapSize = StorageCapacity(228, StorageCapacity.Unit.MB),
        preferredAbi = null,
      )
    }

    private fun SemanticsNodeInteractionsProvider.onInternalStorageTextField() =
      onNodeWithTag("InternalStorageRow").onChildren().filterToOne(hasSetTextAction())

    private fun SemanticsNodeInteractionsProvider.onCustomTextField() =
      onNodeWithTag("CustomRow").onChildren().filterToOne(hasSetTextAction())

    private fun SemanticsNodeInteractionsProvider.onTooltips() = onNode(isPopup()).onChildren()
  }
}
