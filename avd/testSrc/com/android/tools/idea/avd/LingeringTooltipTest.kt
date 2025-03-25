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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.printToLog
import androidx.compose.ui.unit.dp
import com.android.tools.adtui.compose.utils.StudioComposeTestRule.Companion.createStudioComposeTestRule
import org.jetbrains.jewel.ui.component.Text
import org.junit.Rule
import org.junit.Test

class LingeringTooltipTest {
  @get:Rule val composeTestRule = createStudioComposeTestRule()

  @OptIn(ExperimentalFoundationApi::class, ExperimentalTestApi::class)
  @Test
  fun lingeringTooltip() {
    val delayMillis = 800
    val lingerMillis = 1200
    composeTestRule.setContent {
      Column {
        LingeringTooltip(
          tooltip = { Text("Tooltip", Modifier.size(200.dp)) },
          delayMillis = delayMillis,
          lingerMillis = lingerMillis,
        ) {
          Text("Target", Modifier.size(50.dp))
        }
        Spacer(Modifier.size(200.dp))
        Text("Other Text", Modifier.size(200.dp))
      }
    }
    composeTestRule.mainClock.autoAdvance = false

    composeTestRule.onNodeWithText("Tooltip").assertDoesNotExist()

    // Move to target; tooltip is shown
    composeTestRule.onNodeWithText("Target").performMouseInput { moveTo(center) }
    composeTestRule.mainClock.advanceTimeBy(delayMillis + 1L)
    composeTestRule.onAllNodes(isRoot()).printToLog("foo", maxDepth = 100)
    composeTestRule.onNodeWithText("Tooltip").assertIsDisplayed()

    // Move to tooltip; tooltip remains shown
    composeTestRule.onNodeWithText("Tooltip").performMouseInput { moveTo(center) }
    composeTestRule.mainClock.advanceTimeBy(lingerMillis + 1L)
    composeTestRule.onNodeWithText("Tooltip").assertIsDisplayed()

    // Move to other text; tooltip is gone
    composeTestRule.onNodeWithText("Other Text").performMouseInput { moveTo(center) }
    composeTestRule.mainClock.advanceTimeBy(delayMillis + 1L)
    composeTestRule.onNodeWithText("Tooltip").assertIsDisplayed()
    composeTestRule.mainClock.advanceTimeBy(lingerMillis - delayMillis.toLong())
    composeTestRule.onNodeWithText("Tooltip").assertDoesNotExist()
  }
}
