/*
 * Copyright (C) 2026 The Android Open Source Project
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

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.android.tools.adtui.compose.utils.StudioComposeTestRule.Companion.createStudioComposeTestRule
import org.jetbrains.jewel.ui.component.Text
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DropdownIconButtonTest {
  @get:Rule val composeTestRule = createStudioComposeTestRule()

  @Test
  fun testClickableAndContentDisplayed() {
    var clicked = false
    composeTestRule.setContent { DropdownIconButton(onClick = { clicked = true }) { Text("My Icon Content") } }

    val node = composeTestRule.onNodeWithText("My Icon Content")
    node.assertIsDisplayed()
    node.assertIsEnabled()
    node.assertHasClickAction()
    node.performClick()

    assertTrue(clicked)
  }

  @Test
  fun testDisabled() {
    var clicked = false
    composeTestRule.setContent { DropdownIconButton(onClick = { clicked = true }, enabled = false) { Text("Disabled Icon Content") } }

    val node = composeTestRule.onNodeWithText("Disabled Icon Content")
    node.assertIsDisplayed()
    node.assertIsNotEnabled()
    assertFalse(clicked)
  }
}
