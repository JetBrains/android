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
package com.android.tools.studio.labs

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.android.tools.adtui.compose.utils.StudioComposeTestRule.Companion.createStudioComposeTestRule
import com.android.tools.idea.flags.StudioFlags
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ApplicationRule
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class StudioLabsFeaturePanelUiTest {
  @get:Rule val composeTestRule = createStudioComposeTestRule()
  @get:Rule val applicationRule = ApplicationRule()
  private val testFlag = StudioFlags.STUDIOBOT_PROMPT_LIBRARY_ENABLED

  @Before
  fun setUp() {
    // initial value of the flag set to true
    testFlag.override(true)
  }

  @Test
  fun featurePanel_onButtonClick_modifiesState() {
    val featurePanel = createFeaturePanel()
    assertThat(featurePanel.isModified()).isFalse()

    composeTestRule.onNodeWithTag("enable_disable_button").performClick()

    assertThat(featurePanel.isModified()).isTrue()
  }

  @Test
  fun featurePanel_onButtonClick_updatesButtonText() {
    val unused = createFeaturePanel()
    composeTestRule.onNodeWithTag("enable_disable_button").assertTextEquals("Disable")

    composeTestRule.onNodeWithTag("enable_disable_button").performClick()

    composeTestRule.onNodeWithTag("enable_disable_button").assertTextEquals("Enable")
  }

  @Test
  fun featurePanel_onApply_overridesFlag() = runBlocking {
    val featurePanel = createFeaturePanel()
    assertThat(testFlag.get()).isTrue()

    composeTestRule.onNodeWithTag("enable_disable_button").performClick()
    featurePanel.apply()
    composeTestRule.awaitIdle()

    assertThat(testFlag.get()).isFalse()
  }

  @Test
  fun featurePanel_onReset_doesNotOverrideFlag() {
    val featurePanel = createFeaturePanel()
    assertThat(testFlag.get()).isTrue()

    composeTestRule.onNodeWithTag("enable_disable_button").performClick()
    featurePanel.reset()

    assertThat(testFlag.get()).isTrue()
  }

  @Test
  fun featurePanel_onReset_resetsButtonText() {
    val featurePanel = createFeaturePanel()
    composeTestRule.onNodeWithTag("enable_disable_button").assertTextEquals("Disable")
    composeTestRule.onNodeWithTag("enable_disable_button").performClick()
    composeTestRule.onNodeWithTag("enable_disable_button").assertTextEquals("Enable")

    featurePanel.reset()

    composeTestRule.onNodeWithTag("enable_disable_button").assertTextEquals("Disable")
  }

  private fun createFeaturePanel(): StudioLabsFeaturePanelUi {
    val featurePanel =
      StudioLabsFeaturePanelUi(
        flag = testFlag,
        heading = "Enable Prompt Library",
        description =
          "Allows to store frequently used prompts for quick access." +
            " Optionally share prompts with other people working on a same project.",
        imageSourceDefault = "images/studio_labs/prompt-library-settings.png",
        imageSourceDark = "images/studio_labs/prompt-library-settings_dark.png",
        imageDescription = "Prompt Library settings",
      )
    composeTestRule.setContent { featurePanel.PanelContent() }
    composeTestRule.waitForIdle()
    return featurePanel
  }
}
