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

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.android.sdklib.AndroidVersion
import com.android.sdklib.ISystemImage
import com.android.testutils.file.createInMemoryFileSystem
import com.android.tools.adtui.compose.utils.StudioComposeTestRule.Companion.createStudioComposeTestRule
import com.android.tools.idea.avdmanager.skincombobox.Skin
import com.google.common.truth.Truth.assertThat
import java.nio.file.Files
import kotlinx.collections.immutable.toImmutableList
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(JUnit4::class)
class AdditionalSettingsPanelTest {
  private val fileSystem = createInMemoryFileSystem()
  @get:Rule val rule = createStudioComposeTestRule()

  @Test
  fun deviceSkinDropdownIsEnabledHasPlayStoreAndIsFoldable() {
    // Arrange
    val image = mock<ISystemImage>()
    whenever(image.hasPlayStore()).thenReturn(true)

    val state =
      ConfigureDevicePanelState(
        TestDevices.pixel9ProFold(),
        emptyList<Skin>().toImmutableList(),
        image,
        fileSystem,
      )

    // Act
    rule.setContent { provideCompositionLocals { AdditionalSettingsPanel(state) } }

    // Assert
    rule.onNodeWithTag("DeviceSkinDropdown").assertIsNotEnabled()
  }

  @Test
  fun deviceSkinDropdownIsEnabledHasPlayStoreAndIsntFoldable() {
    // Arrange
    val image = mock<ISystemImage>()
    whenever(image.hasPlayStore()).thenReturn(true)

    val state =
      ConfigureDevicePanelState(
        TestDevices.pixel9Pro(),
        emptyList<Skin>().toImmutableList(),
        image,
        fileSystem,
      )

    // Act
    rule.setContent { provideCompositionLocals { AdditionalSettingsPanel(state) } }

    // Assert
    rule.onNodeWithTag("DeviceSkinDropdown").assertIsNotEnabled()
  }

  @Test
  fun deviceSkinDropdownIsEnabledDoesntHavePlayStoreAndIsFoldable() {
    // Arrange
    val state =
      ConfigureDevicePanelState(
        TestDevices.pixel9ProFold(),
        emptyList<Skin>().toImmutableList(),
        mock(),
        fileSystem,
      )

    // Act
    rule.setContent { provideCompositionLocals { AdditionalSettingsPanel(state) } }

    // Assert
    rule.onNodeWithTag("DeviceSkinDropdown").assertIsNotEnabled()
  }

  @Test
  fun deviceSkinDropdownIsEnabledDoesntHavePlayStoreAndIsntFoldable() {
    // Arrange
    val state =
      ConfigureDevicePanelState(
        TestDevices.pixel9Pro(),
        emptyList<Skin>().toImmutableList(),
        mock(),
        fileSystem,
      )

    // Act
    rule.setContent { provideCompositionLocals { AdditionalSettingsPanel(state) } }

    // Assert
    rule.onNodeWithTag("DeviceSkinDropdown").assertIsEnabled()
  }

  @Test
  fun radioButtonRowOnClicksChangeDevice() {
    // Arrange
    val device = TestDevices.pixel6()

    val image = mock<ISystemImage>()
    whenever(image.androidVersion).thenReturn(AndroidVersion(34, null, 7, true))

    val state =
      ConfigureDevicePanelState(device, emptyList<Skin>().toImmutableList(), image, fileSystem)

    rule.setContent { provideCompositionLocals { AdditionalSettingsPanel(state) } }

    val mySdCardFileImg = fileSystem.getPath(System.getProperty("user.home"), "mySdCardFile.img")
    Files.createDirectories(mySdCardFileImg.parent)
    Files.createFile(mySdCardFileImg)

    // Act
    rule.onNodeWithTag("ExistingImageRadioButton").performClick()
    rule.onNodeWithTag("ExistingImageField").performTextReplacement(mySdCardFileImg.toString())
    rule.waitForIdle()

    // Assert
    assertThat(state.device)
      .isEqualTo(device.copy(expandedStorage = ExistingImage(mySdCardFileImg)))

    // Act
    rule.onNodeWithTag("CustomRadioButton").performClick()
    rule.waitForIdle()

    // Assert
    assertThat(state.device).isEqualTo(device)
  }
}
