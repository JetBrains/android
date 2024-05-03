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
package com.android.tools.idea.adddevicedialog.localavd

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.android.resources.ScreenOrientation
import com.android.sdklib.AndroidVersion
import com.android.sdklib.deviceprovisioner.Resolution
import com.android.sdklib.devices.Abi
import com.android.sdklib.internal.avd.AvdCamera
import com.android.sdklib.internal.avd.EmulatedProperties
import com.android.sdklib.internal.avd.GpuMode
import com.android.testutils.MockitoKt
import com.android.testutils.file.createInMemoryFileSystem
import com.android.tools.idea.adddevicedialog.FormFactors
import com.android.tools.idea.adddevicedialog.LocalFileSystem
import com.android.tools.idea.adddevicedialog.LocalProject
import com.android.tools.idea.avdmanager.skincombobox.DefaultSkin
import com.android.tools.idea.avdmanager.skincombobox.Skin
import com.google.common.collect.Range
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.jewel.bridge.LocalComponent
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mockito

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

  @get:Rule val rule = createComposeRule()

  @Test
  fun radioButtonRowOnClicksChangeDevice() {
    // Arrange
    val fileSystem = createInMemoryFileSystem()
    val home = System.getProperty("user.home")

    val mySdCardFileImg: Path = fileSystem.getPath(home, "mySdCardFile.img")

    Files.createDirectories(mySdCardFileImg.parent)
    Files.createFile(mySdCardFileImg)

    val onDeviceChange = MockitoKt.mock<(VirtualDevice) -> Unit>()

    val device =
      VirtualDevice(
        deviceId = "pixel_6",
        name = "Pixel 6 API 34",
        manufacturer = "Google",
        apiRange = Range.closed(21, 34),
        sdkExtensionLevel = AndroidVersion(34, null, 7, true),
        skin = DefaultSkin(fileSystem.getPath(home, "Android", "Sdk", "skins", "pixel_6")),
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
        abis = listOf(Abi.ARM64_V8A),
        resolution = Resolution(1200, 800),
        displayDensity = 200,
        displayDiagonalLength = 6.2,
        isRound = false,
        formFactor = FormFactors.PHONE,
      )

    rule.setContent {
      IntUiTheme {
        CompositionLocalProvider(
          LocalFileSystem provides fileSystem,
          @OptIn(ExperimentalJewelApi::class) LocalComponent provides MockitoKt.mock(),
          LocalProject provides null,
        ) {
          Column {
            AdditionalSettingsPanel(
              device,
              emptyList<Skin>().toImmutableList(),
              AdditionalSettingsPanelState(device),
              onDeviceChange,
              onImportButtonClick = {},
            )
          }
        }
      }
    }

    // Act
    rule.onNodeWithTag("ExistingImageRadioButton").performClick()
    rule.onNodeWithTag("ExistingImageField").performTextReplacement(mySdCardFileImg.toString())
    rule.onNodeWithTag("CustomRadioButton").performClick()

    // Assert
    val inOrder = Mockito.inOrder(onDeviceChange)

    inOrder
      .verify(onDeviceChange)
      .invoke(device.copy(expandedStorage = ExistingImage(mySdCardFileImg)))

    inOrder.verify(onDeviceChange).invoke(device)
  }
}
