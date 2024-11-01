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
import androidx.compose.runtime.CompositionLocalProvider
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
import com.android.tools.idea.adddevicedialog.LocalProject
import com.android.tools.idea.avdmanager.skincombobox.DefaultSkin
import org.jetbrains.jewel.bridge.LocalComponent
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(JUnit4::class)
class StorageGroupTest {
  private var device = pixel6()
  @get:Rule val rule = createStudioComposeTestRule()

  @Test
  fun internalStorageIsValid() {
    // Arrange
    setContent {
      StorageGroup(
        device,
        StorageGroupState(device),
        hasPlayStore = false,
        isExistingImageValid = true,
        onDeviceChange = { device = it },
      )
    }

    // Act
    rule.onInternalStorageTextField().performTextReplacement("3")

    @OptIn(ExperimentalTestApi::class)
    rule.onInternalStorageTextField().performMouseInput { moveTo(center) }

    // Assert

    // Assert there are no tooltips
    rule.onNode(isPopup()).onChildren().assertCountEquals(0)

    assertEquals(device.copy(internalStorage = StorageCapacity(3, StorageCapacity.Unit.GB)), device)
  }

  @Test
  fun internalStorageIsEmpty() {
    // Arrange
    setContent {
      StorageGroup(
        device,
        StorageGroupState(device),
        hasPlayStore = false,
        isExistingImageValid = true,
        onDeviceChange = { device = it },
      )
    }

    // Act
    rule.onInternalStorageTextField().performTextReplacement("")

    @OptIn(ExperimentalTestApi::class)
    rule.onInternalStorageTextField().performMouseInput { moveTo(center) }

    // Assert
    rule.onNodeWithText("Specify an internal storage value").assertIsDisplayed()
    assertNull(device.internalStorage)
  }

  @Test
  fun internalStorageIsLessThanMinAndHasPlayStore() {
    // Arrange
    setContent {
      StorageGroup(
        device,
        StorageGroupState(device),
        hasPlayStore = true,
        isExistingImageValid = true,
        onDeviceChange = { device = it },
      )
    }

    // Act
    rule.onInternalStorageTextField().performTextReplacement("1")

    @OptIn(ExperimentalTestApi::class)
    rule.onInternalStorageTextField().performMouseInput { moveTo(center) }

    // Assert
    rule
      .onNodeWithText("Internal storage for Play Store devices must be at least 2G")
      .assertIsDisplayed()

    assertNull(device.internalStorage)
  }

  @Test
  fun internalStorageIsLessThanMin() {
    // Arrange
    setContent {
      StorageGroup(
        device,
        StorageGroupState(device),
        hasPlayStore = false,
        isExistingImageValid = true,
        onDeviceChange = { device = it },
      )
    }

    // Act
    rule.onInternalStorageTextField().performTextReplacement("1")

    @OptIn(ExperimentalTestApi::class)
    rule.onInternalStorageTextField().performMouseInput { moveTo(center) }

    // Assert
    rule.onNodeWithText("Internal storage must be at least 2G").assertIsDisplayed()
    assertNull(device.internalStorage)
  }

  @Test
  fun internalStorageIsOverflow() {
    // Arrange
    setContent {
      StorageGroup(
        device,
        StorageGroupState(device),
        hasPlayStore = false,
        isExistingImageValid = true,
        onDeviceChange = { device = it },
      )
    }

    // Act
    rule.onInternalStorageTextField().performTextReplacement("8589934592")

    @OptIn(ExperimentalTestApi::class)
    rule.onInternalStorageTextField().performMouseInput { moveTo(center) }

    // Assert
    rule.onNodeWithText("Internal storage is too large").assertIsDisplayed()
    assertNull(device.internalStorage)
  }

  private fun setContent(composable: @Composable () -> Unit) {
    rule.setContent {
      CompositionLocalProvider(
        @OptIn(ExperimentalJewelApi::class) LocalComponent provides mock(),
        LocalProject provides mock(),
      ) {
        composable()
      }
    }
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
  }
}
