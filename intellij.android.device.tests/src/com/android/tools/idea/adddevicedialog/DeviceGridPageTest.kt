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

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsToggleable
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.android.tools.adtui.compose.TestComposeWizard
import com.android.tools.adtui.compose.WizardAction
import com.android.tools.adtui.compose.WizardPageScope
import com.android.tools.adtui.compose.utils.StudioComposeTestRule.Companion.createStudioComposeTestRule
import com.google.common.truth.Truth.assertThat
import org.jetbrains.jewel.ui.component.Text
import org.junit.Rule
import org.junit.Test

class DeviceGridPageTest {
  @get:Rule val composeTestRule = createStudioComposeTestRule()

  @Test
  fun tableSelectionStateIsPreserved() {
    val source =
      object : TestDeviceSource() {
        override fun WizardPageScope.selectionUpdated(profile: TestDevice) {
          nextAction = WizardAction { pushPage { Text("Configuring ${profile.name}") } }
        }
      }
    TestDevices.allTestDevices.forEach(source::add)
    val wizard = TestComposeWizard { TestDeviceGridPage(source) }
    composeTestRule.setContent { wizard.Content() }

    composeTestRule.onNodeWithText("Pixel Fold").performClick()
    composeTestRule.waitForIdle()
    wizard.performAction(wizard.nextAction)
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithText("Configuring Pixel Fold").assertIsDisplayed()

    wizard.performAction(wizard.prevAction)
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithText("Pixel Fold").assertIsSelected()

    wizard.performAction(wizard.nextAction)
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithText("Configuring Pixel Fold").assertIsDisplayed()
  }

  @Test
  fun canAdvanceOnlyWhenDeviceSelected() {
    val source =
      object : TestDeviceSource() {
        override fun WizardPageScope.selectionUpdated(profile: TestDevice) {
          nextAction = WizardAction { pushPage { Text("Config page") } }
        }
      }

    TestDevices.allTestDevices.forEach(source::add)
    val wizard = TestComposeWizard { TestDeviceGridPage(source) }
    composeTestRule.setContent { wizard.Content() }

    assertThat(wizard.nextAction.action).isNull()

    composeTestRule.onNodeWithText("Pixel Fold").performClick()
    composeTestRule.waitForIdle()

    // Google device selected, can advance to config page
    composeTestRule.onNodeWithText("Pixel Fold").assertIsSelected()
    val googleFilter = hasText("Google") and hasAnyAncestor(hasTestTag("DeviceFilters"))
    composeTestRule.onNode(googleFilter).assertIsToggleable()
    composeTestRule.onNode(googleFilter).assertIsOn()
    assertThat(wizard.nextAction.action).isNotNull()

    // Disable Google devices
    composeTestRule.onNode(googleFilter).performClick()
    composeTestRule.waitForIdle()

    // Can't advance; selected device is filtered out
    composeTestRule.onNode(googleFilter).assertIsOff()
    composeTestRule.onNodeWithText("Pixel Fold").assertDoesNotExist()
    assertThat(wizard.nextAction.action).isNull()

    // Re-enable Google devices
    composeTestRule.onNode(googleFilter).performClick()
    composeTestRule.waitForIdle()

    // Can advance again
    composeTestRule.onNodeWithText("Pixel Fold").assertIsSelected()
    assertThat(wizard.nextAction.action).isNotNull()
  }
}

@Composable
private fun WizardPageScope.TestDeviceGridPage(source: TestDeviceSource) {
  val filterState = TestDeviceFilterState()
  DeviceLoadingPage(source.profiles) { profiles ->
    DefaultDeviceGridPage(
      profiles,
      testDeviceTableColumns,
      filterContent = { TestDeviceFilters(profiles, filterState) },
      filterState = filterState,
      onSelectionUpdated = { with(source) { selectionUpdated(it) } },
    )
  }
}
