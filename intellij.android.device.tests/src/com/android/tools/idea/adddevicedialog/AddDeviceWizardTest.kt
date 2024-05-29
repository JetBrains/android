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

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.android.tools.adtui.compose.JewelTestTheme
import com.google.common.collect.Range
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

class AddDeviceWizardTest {
  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun apiLevel() {
    val source = TestDeviceSource()
    TestDevices.allTestDevices.forEach(source::add)
    val wizard = TestComposeWizard { DeviceGridPage(listOf(source)) }
    composeTestRule.setContent { JewelTestTheme { wizard.Content() } }

    composeTestRule.onNodeWithText("Newest on device").performClick()
    composeTestRule
      .onNode(hasText("API 28", substring = true) and hasAnyAncestor(isPopup()))
      .performClick()
    composeTestRule.onNodeWithText("Medium Phone").performClick()
    composeTestRule.waitForIdle()

    assertThat(source.selectedProfile.value?.apiRange).isEqualTo(Range.singleton(28))
  }
}
