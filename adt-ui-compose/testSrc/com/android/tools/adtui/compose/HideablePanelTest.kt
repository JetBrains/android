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
package com.android.tools.adtui.compose

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.jetbrains.jewel.ui.component.Text
import org.junit.Rule
import org.junit.Test
import com.android.tools.adtui.compose.utils.StudioComposeTestRule.Companion.createStudioComposeTestRule

class HideablePanelTest {
  @get:Rule val composeTestRule = createStudioComposeTestRule()

  @Test
  fun toggleDisplay() {
    composeTestRule.setContent {
      HideablePanel("Header", initiallyOpen = false) { Text("Content") }
    }
    composeTestRule.onNodeWithText("Content").assertDoesNotExist()
    composeTestRule.onNodeWithText("Header").performClick()
    composeTestRule.onNodeWithText("Content").assertIsDisplayed()
    composeTestRule.onNodeWithText("Header").performClick()
    composeTestRule.onNodeWithText("Content").assertDoesNotExist()
  }
}