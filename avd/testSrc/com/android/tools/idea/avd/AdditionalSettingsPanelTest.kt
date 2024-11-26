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

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.onChild
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextReplacement
import com.android.resources.ScreenOrientation
import com.android.sdklib.AndroidVersion
import com.android.sdklib.ISystemImage
import com.android.sdklib.internal.avd.AvdNetworkSpeed
import com.android.testutils.file.createInMemoryFileSystem
import com.android.tools.adtui.compose.utils.StudioComposeTestRule.Companion.createStudioComposeTestRule
import com.android.tools.idea.avdmanager.skincombobox.NoSkin
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
  fun deviceSkinDropdownOnSelectedItemChange() {
    // Arrange
    val device = TestDevices.pixel9Pro(fileSystem)

    val state =
      ConfigureDevicePanelState(
        device,
        listOf(NoSkin.INSTANCE, device.skin).toImmutableList(),
        null,
        fileSystem,
      )

    rule.setContent { provideCompositionLocals { AdditionalSettingsPanel(state) } }

    // Act
    rule.onNodeWithTag("DeviceSkinDropdown").performClick()
    rule.onNodeWithText("[None]").performClick()

    // Assert
    assertThat(state.device).isEqualTo(device.copy(skin = NoSkin.INSTANCE))
  }

  @Test
  fun deviceSkinDropdownIsEnabledIsFoldable() {
    // Arrange
    val state =
      ConfigureDevicePanelState(
        TestDevices.pixel9ProFold(fileSystem),
        emptyList<Skin>().toImmutableList(),
        null,
        fileSystem,
      )

    // Act
    rule.setContent { provideCompositionLocals { AdditionalSettingsPanel(state) } }

    // Assert
    rule.onNodeWithTag("DeviceSkinDropdown").assertIsNotEnabled()
  }

  @Test
  fun speedDropdownOnSelectedItemChange() {
    // Arrange
    val device = TestDevices.pixel9Pro(fileSystem)

    val state =
      ConfigureDevicePanelState(device, emptyList<Skin>().toImmutableList(), null, fileSystem)

    rule.setContent { provideCompositionLocals { AdditionalSettingsPanel(state) } }

    // Act
    rule.onNodeWithText("Full").performClick()
    rule.onNodeWithText("LTE").performClick()

    // Assert
    assertThat(state.device).isEqualTo(device.copy(speed = AvdNetworkSpeed.LTE))
  }

  @Test
  fun orientationDropdownOnClick() {
    // Arrange
    val device = TestDevices.pixel9Pro(fileSystem)

    val state =
      ConfigureDevicePanelState(device, emptyList<Skin>().toImmutableList(), null, fileSystem)

    rule.setContent { provideCompositionLocals { AdditionalSettingsPanel(state) } }

    // Act
    rule.onNodeWithText("Portrait").performClick()
    rule.onNodeWithText("Landscape").performClick()

    // Assert
    assertThat(state.device).isEqualTo(device.copy(orientation = ScreenOrientation.LANDSCAPE))
  }

  @Test
  fun radioButtonRowOnClicksChangeDevice() {
    // Arrange
    val device = TestDevices.pixel6(fileSystem)

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

  @Test
  fun cpuCoresDropdownOnClick() {
    // Arrange
    val device = TestDevices.pixel9Pro(fileSystem)

    val state =
      ConfigureDevicePanelState(device, emptyList<Skin>().toImmutableList(), null, fileSystem, 4)

    rule.setContent { provideCompositionLocals { AdditionalSettingsPanel(state) } }

    // Act
    rule.onNodeWithText("4").performClick()
    rule.onNodeWithText("3").performClick()

    // Assert
    assertThat(state.device).isEqualTo(device.copy(cpuCoreCount = 3))
  }

  @Test
  fun graphicsAccelerationDropdownOnSelectedItemChange() {
    // Arrange
    val device = TestDevices.pixel6(fileSystem)

    val state =
      ConfigureDevicePanelState(device, emptyList<Skin>().toImmutableList(), null, fileSystem)

    rule.setContent { provideCompositionLocals { AdditionalSettingsPanel(state) } }

    // Act
    rule.onNodeWithText("Automatic").performClick()
    rule.onNodeWithText("Hardware").performClick()

    // Assert
    assertThat(state.device).isEqualTo(device.copy(graphicsMode = GraphicsMode.HARDWARE))
  }

  @Test
  fun ramIsValid() {
    // Arrange
    val device = TestDevices.pixel6(fileSystem)

    val state =
      ConfigureDevicePanelState(device, emptyList<Skin>().toImmutableList(), null, fileSystem)

    rule.setContent { provideCompositionLocals { AdditionalSettingsPanel(state) } }

    // Act
    rule.onRamTextField().performTextReplacement("3")
    @OptIn(ExperimentalTestApi::class) rule.onRamTextField().performMouseInput { moveTo(center) }

    // Assert
    rule.onTooltips().assertCountEquals(0)

    assertThat(state.device)
      .isEqualTo(device.copy(ram = StorageCapacity(3, StorageCapacity.Unit.GB)))
  }

  @Test
  fun ramIsEmpty() {
    // Arrange
    val device = TestDevices.pixel6(fileSystem)

    val state =
      ConfigureDevicePanelState(device, emptyList<Skin>().toImmutableList(), null, fileSystem)

    rule.setContent { provideCompositionLocals { AdditionalSettingsPanel(state) } }

    // Act
    rule.onRamTextField().performTextReplacement("")
    @OptIn(ExperimentalTestApi::class) rule.onRamTextField().performMouseInput { moveTo(center) }

    // Assert
    rule.onNodeWithText("Specify a RAM value").assertIsDisplayed()
    assertThat(state.device).isEqualTo(device.copy(ram = null))
  }

  @Test
  fun ramIsLessThanMin() {
    // Arrange
    val device = TestDevices.pixel6(fileSystem)

    val state =
      ConfigureDevicePanelState(device, emptyList<Skin>().toImmutableList(), null, fileSystem)

    rule.setContent { provideCompositionLocals { AdditionalSettingsPanel(state) } }

    // Act
    rule.onRamDropdown().performClick()
    rule.onRamDropdownPopupChildren().filterToOne(hasText("MB")).performClick()
    @OptIn(ExperimentalTestApi::class) rule.onRamTextField().performMouseInput { moveTo(center) }

    // Assert
    rule.onNodeWithText("RAM must be at least 128M. Recommendation is 1G.").assertIsDisplayed()
    assertThat(state.device).isEqualTo(device.copy(ram = null))
  }

  @Test
  fun ramIsOverflow() {
    // Arrange
    val device = TestDevices.pixel6(fileSystem)

    val state =
      ConfigureDevicePanelState(device, emptyList<Skin>().toImmutableList(), null, fileSystem)

    rule.setContent { provideCompositionLocals { AdditionalSettingsPanel(state) } }

    // Act
    rule.onRamTextField().performTextReplacement("8589934592")
    @OptIn(ExperimentalTestApi::class) rule.onRamTextField().performMouseInput { moveTo(center) }

    // Assert
    rule.onNodeWithText("RAM value is too large").assertIsDisplayed()
    assertThat(state.device).isEqualTo(device.copy(ram = null))
  }

  @Test
  fun vmHeapSizeIsValid() {
    // Arrange
    val device = TestDevices.pixel6(fileSystem)

    val state =
      ConfigureDevicePanelState(device, emptyList<Skin>().toImmutableList(), null, fileSystem)

    rule.setContent { provideCompositionLocals { AdditionalSettingsPanel(state) } }

    // Act
    rule.onVMHeapSizeTextField().performTextReplacement("229")

    @OptIn(ExperimentalTestApi::class)
    rule.onVMHeapSizeTextField().performMouseInput { moveTo(center) }

    // Assert
    rule.onTooltips().assertCountEquals(0)

    assertThat(state.device)
      .isEqualTo(device.copy(vmHeapSize = StorageCapacity(229, StorageCapacity.Unit.MB)))
  }

  @Test
  fun vmHeapSizeIsEmpty() {
    // Arrange
    val device = TestDevices.pixel6(fileSystem)

    val state =
      ConfigureDevicePanelState(device, emptyList<Skin>().toImmutableList(), null, fileSystem)

    rule.setContent { provideCompositionLocals { AdditionalSettingsPanel(state) } }

    // Act
    rule.onVMHeapSizeTextField().performTextReplacement("")

    @OptIn(ExperimentalTestApi::class)
    rule.onVMHeapSizeTextField().performMouseInput { moveTo(center) }

    // Assert
    rule.onNodeWithText("Specify a VM heap size").assertIsDisplayed()
    assertThat(state.device).isEqualTo(device.copy(vmHeapSize = null))
  }

  @Test
  fun vmHeapSizeIsLessThanMin() {
    // Arrange
    val device = TestDevices.pixel6(fileSystem)

    val state =
      ConfigureDevicePanelState(device, emptyList<Skin>().toImmutableList(), null, fileSystem)

    rule.setContent { provideCompositionLocals { AdditionalSettingsPanel(state) } }

    // Act
    rule.onVMHeapSizeTextField().performTextReplacement("15")

    @OptIn(ExperimentalTestApi::class)
    rule.onVMHeapSizeTextField().performMouseInput { moveTo(center) }

    // Assert
    rule.onNodeWithText("VM heap must be at least 16M").assertIsDisplayed()
    assertThat(state.device).isEqualTo(device.copy(vmHeapSize = null))
  }

  @Test
  fun vmHeapSizeIsOverflow() {
    // Arrange
    val device = TestDevices.pixel6(fileSystem)

    val state =
      ConfigureDevicePanelState(device, emptyList<Skin>().toImmutableList(), null, fileSystem)

    rule.setContent { provideCompositionLocals { AdditionalSettingsPanel(state) } }

    // Act
    rule.onVMHeapSizeTextField().performTextReplacement("8796093022208")

    @OptIn(ExperimentalTestApi::class)
    rule.onVMHeapSizeTextField().performMouseInput { moveTo(center) }

    // Assert
    rule.onNodeWithText("VM heap size is too large").assertIsDisplayed()
    assertThat(state.device).isEqualTo(device.copy(vmHeapSize = null))
  }

  private companion object {
    private fun SemanticsNodeInteractionsProvider.onRamTextField() =
      onNodeWithTag("RamRow").onChildren().filterToOne(hasSetTextAction())

    private fun SemanticsNodeInteractionsProvider.onRamDropdown() =
      onNodeWithTag("RamRow")
        .onChildren()
        .filterToOne(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))

    private fun SemanticsNodeInteractionsProvider.onRamDropdownPopupChildren() =
      onNode(isPopup()).onChild().onChildren()

    private fun SemanticsNodeInteractionsProvider.onVMHeapSizeTextField() =
      onNodeWithTag("VMHeapSizeRow").onChildren().filterToOne(hasSetTextAction())
  }
}
