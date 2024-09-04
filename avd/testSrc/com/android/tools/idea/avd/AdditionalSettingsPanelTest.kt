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
import com.android.sdklib.internal.avd.AvdCamera
import com.android.sdklib.internal.avd.EmulatedProperties
import com.android.sdklib.internal.avd.GpuMode
import com.android.testutils.MockitoKt
import com.android.testutils.file.createInMemoryFileSystem
import com.android.tools.adtui.compose.utils.StudioComposeTestRule.Companion.createStudioComposeTestRule
import com.android.tools.idea.adddevicedialog.LocalFileSystem
import com.android.tools.idea.adddevicedialog.LocalProject
import com.android.tools.idea.avdmanager.skincombobox.DefaultSkin
import com.android.tools.idea.avdmanager.skincombobox.Skin
import java.nio.file.Files
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.jewel.bridge.LocalComponent
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class AdditionalSettingsPanelTest {
  // TODO: http://b/325127223
  private companion object {
    private val oldHome: String = System.getProperty("user.home")

    @BeforeClass
    @JvmStatic
    fun overrideUserHome() {
      System.setProperty("user.home", System.getProperty("java.io.tmpdir"))
    }

    @AfterClass
    @JvmStatic
    fun restoreUserHome() {
      System.setProperty("user.home", oldHome)
    }
  }

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
        androidVersion = AndroidVersion(34, null, 7, true),
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
        graphicAcceleration = GpuMode.AUTO,
        simulatedRam = StorageCapacity(2_048, StorageCapacity.Unit.MB),
        vmHeapSize = StorageCapacity(256, StorageCapacity.Unit.MB),
      )

    val state =
      ConfigureDevicePanelState(device, emptyList<Skin>().toImmutableList(), MockitoKt.mock())

    rule.setContent {
      CompositionLocalProvider(
        LocalFileSystem provides fileSystem,
        @OptIn(ExperimentalJewelApi::class) LocalComponent provides MockitoKt.mock(),
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

    // Assert
    assertEquals(device.copy(expandedStorage = ExistingImage(mySdCardFileImg)), state.device)

    // Act
    rule.onNodeWithTag("CustomRadioButton").performClick()

    // Assert
    assertEquals(device, state.device)
  }
}
