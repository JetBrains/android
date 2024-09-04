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
package com.android.tools.adtui.compose.sample

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.android.tools.adtui.compose.utils.StudioComposeTestRule.Companion.createStudioComposeTestRule
import org.junit.Rule
import org.junit.Test
import sample.SampleComposeComponent

/**
 * The following test class and test serve as an example of how we can test the content and behavior
 * of Compose Desktop UI.
 *
 * This sample test utilizes the Jewel standalone theme which is for scoped for testing only. NOTE:
 * The Jewel standalone theme should only ever be used for testing and never used in production
 * code.
 */
class SampleComposeComponentTest {

  @get:Rule
  val composeTestRule = createStudioComposeTestRule()

  @Test
  fun sampleComposeComponentTest() {
    composeTestRule.setContent { SampleComposeComponent() }

    // Make sure that the "Displayed Text" text is not in the component tree yet.
    composeTestRule.onNodeWithText("Displayed Text").assertDoesNotExist()
    // Make sure that the "Hello Compose" button is in the component tree, displayed, and has a
    // onClick handler.
    composeTestRule.onNodeWithText("Hello Compose").assertIsDisplayed()
    composeTestRule.onNodeWithText("Hello Compose").assertHasClickAction()
    // Perform a click on the "Hello Compose" button to toggle the "Displayed Text" to be displayed.
    composeTestRule.onNodeWithText("Hello Compose").performClick()
    // Make sure that the "Displayed Text" text is not visible.
    composeTestRule.onNodeWithText("Displayed Text").assertIsDisplayed()
  }
}