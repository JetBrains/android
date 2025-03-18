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

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.android.flags.junit.FlagRule
import com.android.sdklib.AndroidVersion
import com.android.sdklib.PathFileWrapper
import com.android.sdklib.SystemImageTags
import com.android.sdklib.devices.Device
import com.android.sdklib.internal.avd.AvdManager
import com.android.sdklib.internal.avd.ConfigKey
import com.android.tools.adtui.compose.TestComposeWizard
import com.android.tools.adtui.compose.utils.StudioComposeTestRule.Companion.createStudioComposeTestRule
import com.android.tools.idea.avdmanager.AccelerationErrorCode
import com.android.tools.idea.flags.StudioFlags
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import java.nio.file.Files
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class AddDeviceWizardTest {
  @get:Rule val edtRule = EdtRule()
  @get:Rule val applicationRule = ApplicationRule()
  @get:Rule val composeTestRule = createStudioComposeTestRule()
  @get:Rule val flagRule = FlagRule(StudioFlags.XR_DEVICE_SUPPORT_ENABLED, true)

  /**
   * Pick a device, advance, and then finish (using default system image and settings). Verify that
   * AVD files are created. Then do it again.
   */
  @OptIn(ExperimentalTestApi::class)
  @Test
  fun addDeviceDefaultPath() {
    with(SdkFixture()) {
      val api34 = createLocalSystemImage("google_apis", listOf(), AndroidVersion(34))
      repoPackages.setLocalPkgInfos(listOf(api34))

      val source = createLocalVirtualDeviceSource()

      fun addPixel8() {
        val wizard = createTestAddDeviceWizard(source)

        composeTestRule.setContentWithSdkLocals { wizard.Content() }

        composeTestRule.waitUntilAtLeastOneExists(hasText("Pixel 8"))
        composeTestRule.onNodeWithText("Pixel 8").performClick()
        composeTestRule.waitForIdle()

        wizard.performAction(wizard.nextAction)
        composeTestRule.waitForIdle()

        composeTestRule.waitUntilDoesNotExist(hasText("Loading system images", substring = true))
        wizard.performAction(wizard.finishAction)
        composeTestRule.waitForIdle()
        wizard.awaitClose()
      }

      addPixel8()

      assertThat(Files.list(avdRoot).map { it.fileName.toString() }.toList())
        .containsExactly("Pixel_8.avd", "Pixel_8.ini")

      addPixel8()

      assertThat(Files.list(avdRoot).map { it.fileName.toString() }.toList())
        .containsExactly("Pixel_8.avd", "Pixel_8.ini", "Pixel_8_2.avd", "Pixel_8_2.ini")
    }
  }

  @Test
  fun addAutomotiveDevice() {
    with(SdkFixture()) {
      val api34Ext9Auto =
        createLocalSystemImage(
          "android-automotive",
          listOf(SystemImageTags.AUTOMOTIVE_TAG),
          AndroidVersion(34, null, 9, false),
        )
      repoPackages.setLocalPkgInfos(listOf(api34Ext9Auto))

      val source = createLocalVirtualDeviceSource()
      val wizard = createTestAddDeviceWizard(source)

      composeTestRule.setContentWithSdkLocals { wizard.Content() }

      composeTestRule.onNodeWithText("Automotive").performClick()
      composeTestRule.onNodeWithText("Automotive Portrait").performClick()
      composeTestRule.waitForIdle()

      wizard.performAction(wizard.nextAction)

      composeTestRule.onNodeWithText(api34Ext9Auto.displayName).assertIsSelected()
      composeTestRule.onNodeWithText("Additional settings").performClick()
      composeTestRule.onNodeWithText("Camera").assertDoesNotExist()
      composeTestRule.waitForIdle()

      wizard.performAction(wizard.finishAction)
      wizard.awaitClose()

      assertThat(Files.list(avdRoot).map { it.fileName.toString() }.toList())
        .containsExactly("Automotive_Portrait.avd", "Automotive_Portrait.ini")
      val properties =
        AvdManager.parseIniFile(
          PathFileWrapper(avdRoot.resolve("Automotive_Portrait.avd").resolve("config.ini")),
          null,
        )
      assertThat(properties[ConfigKey.CAMERA_FRONT]).isEqualTo("none")
    }
  }

  @OptIn(ExperimentalTestApi::class)
  @Test
  fun accelerationErrorCode() {
    with(SdkFixture()) {
      val api34 = createLocalSystemImage("google_apis", listOf(), AndroidVersion(34))
      repoPackages.setLocalPkgInfos(listOf(api34))

      val source = createLocalVirtualDeviceSource()

      val wizard = TestComposeWizard {
        with(AddDeviceWizard(source, null, { AccelerationErrorCode.NO_EMULATOR_INSTALLED })) {
          DeviceGridPage()
        }
      }
      composeTestRule.setContentWithSdkLocals { wizard.Content() }

      composeTestRule.waitUntilDoesNotExist(hasText("Loading system images", substring = true))
      // The fetching of the acceleration status is asynchronous
      composeTestRule.waitUntilAtLeastOneExists(hasText("No emulator installed"))
      composeTestRule.onNodeWithText("No emulator installed").assertIsDisplayed()
      composeTestRule.onNodeWithText("Install Emulator").assertIsEnabled()
    }
  }

  @OptIn(ExperimentalTestApi::class)
  @Test
  fun statePreservation() {
    with(SdkFixture()) {
      val api34 = createLocalSystemImage("google_apis", listOf(), AndroidVersion(34))
      repoPackages.setLocalPkgInfos(listOf(api34))

      val pixel3 = deviceManager.getDevice("pixel_3", "Google")!!
      deviceManager.addUserDevice(
        Device.Builder(pixel3)
          .apply {
            setName("APhone")
            setId("aphone")
          }
          .build()
      )
      deviceManager.addUserDevice(
        Device.Builder(pixel3)
          .apply {
            setName("ZPhone")
            setId("zphone")
          }
          .build()
      )

      val source = createLocalVirtualDeviceSource()
      val wizard = createTestAddDeviceWizard(source)

      composeTestRule.setContentWithSdkLocals { wizard.Content() }

      // Sort by name then arrow down to bring ZPhone into view
      composeTestRule.onNodeWithText("Name").performClick()
      repeat(50) { composeTestRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) } }
      composeTestRule.onNodeWithText("ZPhone").performClick()

      // Show the details, now we see ZPhone twice
      composeTestRule.onNodeWithContentDescription("Details").performClick()
      composeTestRule.onAllNodesWithText("ZPhone").assertCountEquals(2)

      // Go forward and back
      wizard.performAction(wizard.nextAction)
      composeTestRule.waitForIdle()
      wizard.performAction(wizard.prevAction)

      // Sort order is preserved; ZPhone is still selected; details still visible
      composeTestRule.onNodeWithText("APhone").assertDoesNotExist()
      composeTestRule.onNodeWithClickableText("ZPhone").assertIsSelected()
      composeTestRule.onAllNodesWithText("ZPhone").assertCountEquals(2)
      composeTestRule.onNode(hasText("Name") and hasContentDescription("Sorted ascending"))
    }
  }

  @Test
  fun noSystemImage() {
    with(SdkFixture()) {
      val api34 = createLocalSystemImage("google_apis", listOf(), AndroidVersion(34))
      repoPackages.setLocalPkgInfos(listOf(api34))

      val source = createLocalVirtualDeviceSource()

      val wizard = TestComposeWizard {
        with(AddDeviceWizard(source, null, { AccelerationErrorCode.NO_EMULATOR_INSTALLED })) {
          DeviceGridPage()
        }
      }
      composeTestRule.setContentWithSdkLocals { wizard.Content() }

      composeTestRule.onNodeWithText("XR").performClick()
      composeTestRule.waitForIdle()
      assertThat(wizard.nextAction.enabled).isFalse()

      composeTestRule.onNodeWithText("XR Device").performClick()
      composeTestRule.waitForIdle()
      wizard.performAction(wizard.nextAction)

      composeTestRule.waitForIdle()
      assertThat(wizard.nextAction.enabled).isFalse()
      assertThat(wizard.finishAction.enabled).isFalse()

      composeTestRule.onNodeWithText("No system images available.").assertIsDisplayed()
    }
  }

  @Test
  fun noSupportedSystemImage() {
    with(SdkFixture()) {
      val api34 =
        createLocalSystemImage(
          "google_atd",
          listOf(SystemImageTags.GOOGLE_ATD_TAG),
          AndroidVersion(34),
        )
      repoPackages.setLocalPkgInfos(listOf(api34))

      val source = createLocalVirtualDeviceSource()

      val wizard = TestComposeWizard {
        with(AddDeviceWizard(source, null, { AccelerationErrorCode.NO_EMULATOR_INSTALLED })) {
          DeviceGridPage()
        }
      }
      composeTestRule.setContentWithSdkLocals { wizard.Content() }

      composeTestRule.onNodeWithText("Medium Phone").performClick()
      composeTestRule.waitForIdle()
      wizard.performAction(wizard.nextAction)

      composeTestRule.waitForIdle()
      assertThat(wizard.nextAction.enabled).isFalse()
      assertThat(wizard.finishAction.enabled).isFalse()

      composeTestRule
        .onNodeWithText("No system images available matching the current set of filters.")
        .assertIsDisplayed()
    }
  }
}

private fun createTestAddDeviceWizard(source: LocalVirtualDeviceSource) = TestComposeWizard {
  with(
    AddDeviceWizard(source, null, accelerationCheck = { AccelerationErrorCode.ALREADY_INSTALLED })
  ) {
    DeviceGridPage()
  }
}
