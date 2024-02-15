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
import androidx.compose.ui.test.junit4.createComposeRule
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
import com.android.tools.idea.avdmanager.skincombobox.DefaultSkin
import com.android.tools.idea.avdmanager.skincombobox.Skin
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.collections.immutable.toImmutableList
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
        "Pixel 6 API 34",
        AndroidVersion(34, null, 7, true),
        DefaultSkin(fileSystem.getPath(home, "Android", "Sdk", "skins", "pixel_6")),
        frontCamera = AvdCamera.EMULATED,
        rearCamera = AvdCamera.VIRTUAL_SCENE,
        EmulatedProperties.DEFAULT_NETWORK_SPEED,
        EmulatedProperties.DEFAULT_NETWORK_LATENCY,
        ScreenOrientation.PORTRAIT,
        Boot.QUICK,
        internalStorage = StorageCapacity(2_048, StorageCapacity.Unit.MB),
        expandedStorage = Custom(StorageCapacity(512, StorageCapacity.Unit.MB)),
        EmulatedProperties.RECOMMENDED_NUMBER_OF_CORES,
        GpuMode.AUTO,
      )

    rule.setContent {
      IntUiTheme {
        Column {
          AdditionalSettingsPanel(
            device,
            emptyList<Skin>().toImmutableList(),
            AdditionalSettingsPanelState(device),
            onDeviceChange,
            onImportButtonClick = {},
            fileSystem,
          )
        }
      }
    }

    // Act
    rule.onNodeWithTag("ExistingImageRadioButton").performClick()
    rule.onNodeWithTag("ExistingImageTextField").performTextReplacement(mySdCardFileImg.toString())
    rule.onNodeWithTag("CustomRadioButton").performClick()

    // Assert
    val inOrder = Mockito.inOrder(onDeviceChange)

    inOrder
      .verify(onDeviceChange)
      .invoke(device.copy(expandedStorage = ExistingImage(mySdCardFileImg)))

    inOrder.verify(onDeviceChange).invoke(device)
  }
}
