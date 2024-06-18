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
package com.android.tools.idea.adddevicedialog

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsToggleable
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.android.sdklib.AndroidVersion
import com.android.tools.adtui.compose.StudioTestTheme
import com.google.common.truth.Truth.assertThat
import org.jetbrains.jewel.ui.component.Text
import org.junit.Rule
import org.junit.Test

class AddDeviceWizardTest {
  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun apiLevel() {
    val source = TestDeviceSource()
    TestDevices.allTestDevices.forEach(source::add)
    val wizard = TestComposeWizard { DeviceGridPage(listOf(source)) }
    composeTestRule.setContent { StudioTestTheme { wizard.Content() } }

    composeTestRule.onNodeWithText("Latest").performClick()
    composeTestRule
      .onNode(hasText("API 28", substring = true) and hasAnyAncestor(isPopup()))
      .performClick()
    composeTestRule.onNodeWithText("Medium Phone").performClick()
    composeTestRule.waitForIdle()

    assertThat(source.selectedProfile.value?.apiLevels).containsExactly(AndroidVersion(28))
  }

  @Test
  fun tableSelectionStateIsPreserved() {
    val source =
      object : TestDeviceSource() {
        override fun WizardPageScope.selectionUpdated(profile: DeviceProfile) {
          nextAction = WizardAction { pushPage { Text("Configuring ${profile.name}") } }
        }
      }
    TestDevices.allTestDevices.forEach(source::add)
    val wizard = TestComposeWizard { DeviceGridPage(listOf(source)) }
    composeTestRule.setContent { StudioTestTheme { wizard.Content() } }

    composeTestRule.onNodeWithText("Television (4K)").performClick()
    composeTestRule.waitForIdle()
    wizard.performAction(wizard.nextAction)
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithText("Configuring Television (4K)").assertIsDisplayed()

    wizard.performAction(wizard.prevAction)
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithText("Television (4K)").assertIsSelected()

    wizard.performAction(wizard.nextAction)
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithText("Configuring Television (4K)").assertIsDisplayed()
  }

  @Test
  fun canAdvanceOnlyWhenDeviceSelected() {
    val source =
      object : TestDeviceSource() {
        override fun WizardPageScope.selectionUpdated(profile: DeviceProfile) {
          nextAction = WizardAction { pushPage { Text("Config page") } }
        }
      }

    TestDevices.allTestDevices.forEach(source::add)
    val wizard = TestComposeWizard { DeviceGridPage(listOf(source)) }
    composeTestRule.setContent { StudioTestTheme { wizard.Content() } }

    assertThat(wizard.nextAction.action).isNull()

    composeTestRule.onNodeWithText("Television (4K)").performClick()
    composeTestRule.waitForIdle()

    // TV device selected, can advance to config page
    composeTestRule.onNodeWithText("Television (4K)").assertIsSelected()
    composeTestRule.onNodeWithText("TV").assertIsToggleable()
    composeTestRule.onNodeWithText("TV").assertIsOn()
    assertThat(wizard.nextAction.action).isNotNull()

    // Disable TV devices
    composeTestRule.onNodeWithText("TV").performClick()
    composeTestRule.waitForIdle()

    // Can't advance; selected device is filtered out
    composeTestRule.onNodeWithText("TV").assertIsOff()
    composeTestRule.onNodeWithText("Television (4K)").assertDoesNotExist()
    assertThat(wizard.nextAction.action).isNull()

    // Re-enable TV devices
    composeTestRule.onNodeWithText("TV").performClick()
    composeTestRule.waitForIdle()

    // Can advance again
    composeTestRule.onNodeWithText("Television (4K)").assertIsSelected()
    assertThat(wizard.nextAction.action).isNotNull()
  }
}
