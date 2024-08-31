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
package com.android.tools.profilers.taskbased.tabs.task.leakcanary.leakdetails

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.android.tools.adtui.compose.utils.StudioComposeTestRule
import org.junit.Rule
import org.junit.Test

class BulletListTest {
  @get:Rule
  val composeTestRule = StudioComposeTestRule.createStudioComposeTestRule()

  @Test
  fun `test bullet list in more info displays items correctly`() {
    val items = listOf("Item 1", "Item 2", "Item 3")
    composeTestRule.setContent{
      BulletList(items = items)
    }
    items.forEach {
      composeTestRule.onNodeWithText(it).assertIsDisplayed()
    }
    // Verify bullet is displayed for each item
    composeTestRule.onAllNodesWithText("•").assertCountEquals(items.size)
  }

  @Test
  fun `test empty bullet list displays nothing`() {
    composeTestRule.setContent {
      BulletList(items = emptyList())
    }
    // Verify bullet is displayed for each item
    composeTestRule.onAllNodesWithText("•").assertCountEquals(0)
  }
}