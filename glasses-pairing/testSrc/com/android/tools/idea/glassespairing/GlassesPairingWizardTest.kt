/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.glassespairing

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.android.sdklib.AndroidVersion
import com.android.sdklib.deviceprovisioner.DeviceHandle
import com.android.sdklib.deviceprovisioner.DeviceProperties
import com.android.sdklib.deviceprovisioner.DeviceState
import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.sdklib.deviceprovisioner.EmptyIcon
import com.android.sdklib.deviceprovisioner.testing.FakeDeviceProvisionerPlugin
import com.android.tools.adtui.compose.TestComposeWizard
import com.android.tools.adtui.compose.utils.StudioComposeTestRule.Companion.createStudioComposeTestRule
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class GlassesPairingWizardTest {
  @get:Rule val edtRule = EdtRule()
  @get:Rule val composeTestRule = createStudioComposeTestRule()

  @Test
  fun testGlassesPairingWizard() {
    val coroutineScope = CoroutineScope(UnconfinedTestDispatcher())

    try {
      val phone =
        FakeDeviceProvisionerPlugin.FakeDeviceHandle(
          "p1",
          coroutineScope,
          DeviceState.Disconnected(
            DeviceProperties.buildForTest {
              icon = EmptyIcon.DEFAULT
              manufacturer = "Google"
              model = "Pixel 9"
              deviceType = DeviceType.HANDHELD
              androidVersion = AndroidVersion(36, 1)
            }
          ),
        )
      val glasses =
        FakeDeviceProvisionerPlugin.FakeDeviceHandle(
          "g1",
          coroutineScope,
          DeviceState.Disconnected(
            DeviceProperties.buildForTest {
              icon = EmptyIcon.DEFAULT
              manufacturer = "Google"
              model = "AI Glasses"
              deviceType = DeviceType.AI_GLASSES
              androidVersion = AndroidVersion(36, 1)
            }
          ),
        )
      val devicesFlow = MutableStateFlow(listOf(phone, glasses))

      val pairingFlow = MutableStateFlow<PairingState>(PairingState.NotStarted)
      fun pair(g: DeviceHandle, p: DeviceHandle): Flow<PairingState> {
        assertThat(g).isSameAs(glasses)
        assertThat(p).isSameAs(phone)
        return pairingFlow
      }

      val glassesWizard =
        GlassesPairingWizard(null, coroutineScope, devicesFlow, glasses, ::pair, { true })
      val wizard = TestComposeWizard { with(glassesWizard) { SelectDevicePage() } }

      composeTestRule.setContent { wizard.Content() }

      composeTestRule.onNodeWithText("Select a device", substring = true).assertIsDisplayed()
      composeTestRule.onNodeWithText("Google Pixel 9").performClick()
      composeTestRule.onNodeWithText("Next").performClick()

      pairingFlow.value =
        PairingState.Launching("Pixel 9", LaunchState.Booting, "AI Glasses", LaunchState.Booting)

      composeTestRule.onNodeWithText("Starting devices...").assertIsDisplayed()
      composeTestRule.onNodeWithText("Waiting for Pixel 9 to boot").assertIsDisplayed()

      pairingFlow.value = PairingState.Pairing("Pairing in progress...")

      composeTestRule.waitForIdle()
      composeTestRule.onNodeWithText("Pairing in progress...").assertIsDisplayed()

      pairingFlow.value = PairingState.Complete

      composeTestRule.waitForIdle()
      composeTestRule.onNodeWithText("Pairing complete.").assertIsDisplayed()

      composeTestRule.onNodeWithText("Previous").assertIsNotEnabled()
      composeTestRule.onNodeWithText("Next").assertIsNotEnabled()
      composeTestRule.onNodeWithText("Cancel").assertIsNotEnabled()
      composeTestRule.onNodeWithText("Finish").assertIsEnabled().performClick()

      wizard.awaitClose()
    } finally {
      coroutineScope.cancel()
    }
  }
}
