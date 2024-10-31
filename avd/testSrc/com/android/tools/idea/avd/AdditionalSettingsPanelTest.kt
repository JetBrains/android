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

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.android.resources.ScreenOrientation
import com.android.sdklib.AndroidVersion
import com.android.sdklib.ISystemImage
import com.android.sdklib.internal.avd.AvdCamera
import com.android.sdklib.internal.avd.EmulatedProperties
import com.android.testutils.file.createInMemoryFileSystem
import com.android.tools.adtui.compose.utils.StudioComposeTestRule.Companion.createStudioComposeTestRule
import com.android.tools.idea.adddevicedialog.LocalFileSystem
import com.android.tools.idea.adddevicedialog.LocalProject
import com.android.tools.idea.avdmanager.skincombobox.DefaultSkin
import com.android.tools.idea.avdmanager.skincombobox.Skin
import com.google.common.truth.Truth.assertThat
import java.nio.file.Files
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.jewel.bridge.LocalComponent
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(JUnit4::class)
class AdditionalSettingsPanelTest {
  @get:Rule val rule = createStudioComposeTestRule()

  @Test
  fun radioButtonRowOnClicksChangeDevice() {
    // Arrange
    val fileSystem = createInMemoryFileSystem()
    val home = System.getProperty("user.home")

    val device =
      VirtualDevice(
        device = readTestDevices().first { it.id == "pixel_8" },
        name = "Pixel 8 API 34",
        skin = DefaultSkin(fileSystem.getPath(home, "Android", "Sdk", "skins", "pixel_8")),
        frontCamera = AvdCamera.EMULATED,
        rearCamera = AvdCamera.VIRTUAL_SCENE,
        speed = EmulatedProperties.DEFAULT_NETWORK_SPEED,
        latency = EmulatedProperties.DEFAULT_NETWORK_LATENCY,
        orientation = ScreenOrientation.PORTRAIT,
        defaultBoot = Boot.QUICK,
        internalStorage = StorageCapacity(2_048, StorageCapacity.Unit.MB),
        expandedStorage = Custom(StorageCapacity(512, StorageCapacity.Unit.MB)),
        cpuCoreCount = EmulatedProperties.RECOMMENDED_NUMBER_OF_CORES,
        graphicsMode = GraphicsMode.AUTO,
        ram = StorageCapacity(2_048, StorageCapacity.Unit.MB),
        vmHeapSize = StorageCapacity(256, StorageCapacity.Unit.MB),
      )

    val image = mock<ISystemImage>()
    whenever(image.androidVersion).thenReturn(AndroidVersion(34, null, 7, true))

    val state = ConfigureDevicePanelState(device, emptyList<Skin>().toImmutableList(), image)

    rule.setContent {
      CompositionLocalProvider(
        LocalFileSystem provides fileSystem,
        @OptIn(ExperimentalJewelApi::class) LocalComponent provides mock(),
        LocalProject provides null,
      ) {
        Column {
          AdditionalSettingsPanel(
            state,
            AdditionalSettingsPanelState(device),
            onImportButtonClick = {},
          )
        }
      }
    }

    val mySdCardFileImg = fileSystem.getPath(home, "mySdCardFile.img")

    Files.createDirectories(mySdCardFileImg.parent)
    Files.createFile(mySdCardFileImg)

    // Act
    rule.onNodeWithTag("ExistingImageRadioButton").performClick()
    rule.onNodeWithTag("ExistingImageField").performTextReplacement(mySdCardFileImg.toString())
    rule.waitForIdle()

    // Assert
    assertThat(state.device)
      .isEqualTo(device.copy(expandedStorage = ExistingImage(mySdCardFileImg.toString())))

    // Act
    rule.onNodeWithTag("CustomRadioButton").performClick()
    rule.waitForIdle()

    // Assert
    assertThat(state.device).isEqualTo(device)
  }
}
