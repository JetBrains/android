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
import androidx.compose.ui.test.performTextReplacement
import com.android.resources.ScreenOrientation
import com.android.sdklib.AndroidVersion
import com.android.sdklib.ISystemImage
import com.android.sdklib.devices.VendorDevices
import com.android.sdklib.internal.avd.AvdNetworkSpeed
import com.android.testutils.file.createInMemoryFileSystem
import com.android.tools.adtui.compose.utils.StudioComposeTestRule.Companion.createStudioComposeTestRule
import com.android.tools.adtui.compose.utils.lingerMouseHover
import com.android.tools.idea.avdmanager.skincombobox.NoSkin
import com.android.tools.idea.avdmanager.skincombobox.Skin
import com.android.utils.NullLogger
import com.google.common.truth.Truth.assertThat
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Files
import kotlin.math.max
import kotlinx.collections.immutable.ImmutableCollection
import kotlinx.collections.immutable.toImmutableList
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
  fun deviceSkinDropdownOnSelectedItemChange() {
    // Arrange
    val device = TestDevices.pixel9Pro()

    val state =
      configureDevicePanelState(device, listOf(NoSkin.INSTANCE, device.skin).toImmutableList())

    rule.setContent { provideCompositionLocals { AdditionalSettingsPanel(state) } }

    // Act
    rule.onNodeWithTag("DeviceSkinDropdown").performClick()
    rule.onNodeWithText("[None]").performClick()

    // Assert
    assertThat(state.device.skin).isEqualTo(NoSkin.INSTANCE)
  }

  @Test
  fun deviceSkinDropdownIsEnabledIsFoldable() {
    // Arrange
    val state = configureDevicePanelState(TestDevices.pixel9ProFold())

    // Act
    rule.setContent { provideCompositionLocals { AdditionalSettingsPanel(state) } }

    // Assert
    rule.onNodeWithTag("DeviceSkinDropdown").assertIsNotEnabled()
  }

  @Test
  fun speedDropdownOnSelectedItemChange() {
    // Arrange
    val device = TestDevices.pixel9Pro()
    val state = configureDevicePanelState(device)

    rule.setContent { provideCompositionLocals { AdditionalSettingsPanel(state) } }

    // Act
    rule.onNodeWithText("Full").performClick()
    rule.onNodeWithText("LTE").performClick()

    // Assert
    assertThat(state.device.speed).isEqualTo(AvdNetworkSpeed.LTE)
  }

  @Test
  fun orientationDropdownOnClick() {
    // Arrange
    val deviceProfiles = VendorDevices(NullLogger()).apply { init { true } }
    val pixel8 = deviceProfiles.getDevice("pixel_8", "Google")!!
    val device = VirtualDevice(pixel8).apply { initializeFromProfile() }
    val state = configureDevicePanelState(device)

    rule.setContent { provideCompositionLocals { AdditionalSettingsPanel(state) } }

    // Act
    rule.onNodeWithText("Portrait").performClick()
    rule.onNodeWithText("Landscape").performClick()

    // Assert
    assertThat(state.device.orientation).isEqualTo(ScreenOrientation.LANDSCAPE)
  }

  @Test
  fun orientationNotPresentWithoutMultipleStates() {
    val devices = VendorDevices(NullLogger()).apply { init { true } }
    val xrHeadset = devices.getDevice("xr_headset_device", "Google")!!
    assertThat(xrHeadset.allStates).hasSize(1)

    val device = VirtualDevice(xrHeadset).apply { initializeFromProfile() }
    val state = configureDevicePanelState(device)

    rule.setContent { provideCompositionLocals { AdditionalSettingsPanel(state) } }

    rule.onNodeWithText("Orientation").assertDoesNotExist()
  }

  @Test
  fun radioButtonRowOnClicksChangeDevice() {
    // Arrange
    // This uses the default file system
    val device = TestDevices.pixel6()

    device.image =
      mock<ISystemImage>().apply {
        whenever(androidVersion).thenReturn(AndroidVersion(34, null, 7, true))
      }

    val fileSystem = createInMemoryFileSystem()

    val state = configureDevicePanelState(device, fileSystem = fileSystem)
    val initialExpandedStorage = device.expandedStorage

    val mySdCardFileImg = fileSystem.getPath(System.getProperty("user.home"), "mySdCardFile.img")
    Files.createDirectories(mySdCardFileImg.parent)
    Files.createFile(mySdCardFileImg)

    rule.setContent { provideCompositionLocals { AdditionalSettingsPanel(state) } }

    // Act
    rule.onNodeWithTag("ExistingImageRadioButton").performClick()
    rule.onNodeWithTag("ExistingImageField").performTextReplacement(mySdCardFileImg.toString())
    rule.waitForIdle()

    // Assert
    assertThat(state.device.expandedStorage).isEqualTo(ExistingImage(mySdCardFileImg))

    // Act
    rule.onNodeWithTag("CustomRadioButton").performClick()
    rule.waitForIdle()

    // Assert
    assertThat(state.device.expandedStorage).isEqualTo(initialExpandedStorage)
  }

  @Test
  fun cpuCoresDropdownOnClick() {
    // Arrange
    val device = TestDevices.pixel9Pro()

    val state = configureDevicePanelState(device, maxCpuCoreCount = 4)

    rule.setContent { provideCompositionLocals { AdditionalSettingsPanel(state) } }

    // Act
    rule.onNodeWithText("4").performClick()
    rule.onNodeWithText("3").performClick()

    // Assert
    assertThat(state.device.cpuCoreCount).isEqualTo(3)
  }

  @Test
  fun graphicsAccelerationDropdownOnSelectedItemChange() {
    // Arrange
    val device = TestDevices.pixel6()
    val state = configureDevicePanelState(device)

    rule.setContent { provideCompositionLocals { AdditionalSettingsPanel(state) } }

    // Act
    rule.onNodeWithText("Automatic").performClick()
    rule.onNodeWithText("Hardware").performClick()

    // Assert
    assertThat(state.device.graphicsMode).isEqualTo(GraphicsMode.HARDWARE)
  }

  @Test
  fun ramIsValid() {
    // Arrange
    val device = TestDevices.pixel6()
    val state = configureDevicePanelState(device)

    rule.setContent { provideCompositionLocals { AdditionalSettingsPanel(state) } }

    // Act
    rule.onRamTextField().performTextReplacement("3")
    rule.onRamTextField().lingerMouseHover(rule)

    // Assert
    rule.onTooltips().assertCountEquals(0)
    assertThat(state.device.ram).isEqualTo(StorageCapacity(3, StorageCapacity.Unit.GB))
  }

  @Test
  fun ramIsEmpty() {
    // Arrange
    val device = TestDevices.pixel6()
    val state = configureDevicePanelState(device)

    rule.setContent { provideCompositionLocals { AdditionalSettingsPanel(state) } }

    // Act
    rule.onRamTextField().performTextReplacement("")
    rule.onRamTextField().lingerMouseHover(rule)

    // Assert
    rule.onNodeWithText("Specify a RAM value").assertIsDisplayed()
    assertThat(state.device.ram).isNull()
  }

  @Test
  fun ramIsLessThanMin() {
    // Arrange
    val device = TestDevices.pixel6()
    val state = configureDevicePanelState(device)

    rule.setContent { provideCompositionLocals { AdditionalSettingsPanel(state) } }

    // Act
    rule.onRamDropdown().performClick()
    rule.onRamDropdownPopupChildren().filterToOne(hasText("MB")).performClick()
    rule.onRamTextField().lingerMouseHover(rule)

    // Assert
    rule.onNodeWithText("RAM must be at least 128 MB. Recommendation is 1 GB.").assertIsDisplayed()
    assertThat(state.device.ram).isNull()
  }

  @Test
  fun ramIsOverflow() {
    // Arrange
    val device = TestDevices.pixel6()
    val state = configureDevicePanelState(device)

    rule.setContent { provideCompositionLocals { AdditionalSettingsPanel(state) } }

    // Act
    rule.onRamTextField().performTextReplacement("8589934592")
    rule.onRamTextField().lingerMouseHover(rule)

    // Assert
    rule.onNodeWithText("RAM value is too large").assertIsDisplayed()
    assertThat(state.device.ram).isNull()
  }

  @Test
  fun vmHeapSizeIsValid() {
    // Arrange
    val device = TestDevices.pixel6()
    val state = configureDevicePanelState(device)

    rule.setContent { provideCompositionLocals { AdditionalSettingsPanel(state) } }

    // Act
    rule.onVMHeapSizeTextField().performTextReplacement("229")

    rule.onVMHeapSizeTextField().lingerMouseHover(rule)

    // Assert
    rule.onTooltips().assertCountEquals(0)

    assertThat(state.device.vmHeapSize).isEqualTo(StorageCapacity(229, StorageCapacity.Unit.MB))
  }

  @Test
  fun vmHeapSizeIsEmpty() {
    // Arrange
    val device = TestDevices.pixel6()
    val state = configureDevicePanelState(device)

    rule.setContent { provideCompositionLocals { AdditionalSettingsPanel(state) } }

    // Act
    rule.onVMHeapSizeTextField().performTextReplacement("")

    rule.onVMHeapSizeTextField().lingerMouseHover(rule)

    // Assert
    rule.onNodeWithText("Specify a VM heap size").assertIsDisplayed()
    assertThat(state.device.vmHeapSize).isNull()
  }

  @Test
  fun vmHeapSizeIsLessThanMin() {
    // Arrange
    val device = TestDevices.pixel6()
    val state = configureDevicePanelState(device)

    rule.setContent { provideCompositionLocals { AdditionalSettingsPanel(state) } }

    // Act
    rule.onVMHeapSizeTextField().performTextReplacement("15")

    rule.onVMHeapSizeTextField().lingerMouseHover(rule)

    // Assert
    rule.onNodeWithText("VM heap must be at least 16 MB").assertIsDisplayed()
    assertThat(state.device.vmHeapSize).isNull()
  }

  @Test
  fun vmHeapSizeIsOverflow() {
    // Arrange
    val device = TestDevices.pixel6()
    val state = configureDevicePanelState(device)

    rule.setContent { provideCompositionLocals { AdditionalSettingsPanel(state) } }

    // Act
    rule.onVMHeapSizeTextField().performTextReplacement("8796093022208")

    rule.onVMHeapSizeTextField().lingerMouseHover(rule)

    // Assert
    rule.onNodeWithText("VM heap size is too large").assertIsDisplayed()
    assertThat(state.device.vmHeapSize).isNull()
  }
}

private fun configureDevicePanelState(
  device: VirtualDevice,
  skins: ImmutableCollection<Skin> = emptyList<Skin>().toImmutableList(),
  deviceNameValidator: DeviceNameValidator = DeviceNameValidator(emptySet()),
  fileSystem: FileSystem = FileSystems.getDefault(),
  maxCpuCoreCount: Int = max(1, Runtime.getRuntime().availableProcessors() / 2),
) = ConfigureDevicePanelState(device, skins, deviceNameValidator, fileSystem, maxCpuCoreCount)

private fun SemanticsNodeInteractionsProvider.onRamTextField() =
  onNodeWithTag("RamRow").onChildren().filterToOne(hasSetTextAction())

private fun SemanticsNodeInteractionsProvider.onRamDropdown() =
  onNodeWithTag("RamRow")
    .onChildren()
    .filterToOne(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.DropdownList))

private fun SemanticsNodeInteractionsProvider.onRamDropdownPopupChildren() =
  onNode(isPopup()).onChild().onChildren()

private fun SemanticsNodeInteractionsProvider.onVMHeapSizeTextField() =
  onNodeWithTag("VMHeapSizeRow").onChildren().filterToOne(hasSetTextAction())
