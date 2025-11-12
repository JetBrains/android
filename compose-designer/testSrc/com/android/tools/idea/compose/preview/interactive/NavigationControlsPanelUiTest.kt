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
package com.android.tools.idea.compose.preview.interactive

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.android.tools.adtui.compose.utils.StudioComposeTestRule
import com.android.tools.idea.compose.preview.BackNavigationEdge
import com.android.tools.idea.compose.preview.InteractivePreviewNavigationController
import com.intellij.testFramework.ProjectRule
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

class NavigationControlsPanelUiTest {
  @get:Rule val composeTestRule = StudioComposeTestRule.createStudioComposeTestRule()
  @get:Rule val projectRule = ProjectRule()

  @Test
  fun testBottomNavigationContentUi() {
    val controller = mock(InteractivePreviewNavigationController::class.java)

    composeTestRule.setContent { NavigationControlsContent(interactivePreviewNavigationController = controller) }

    // Verify main panel is displayed
    composeTestRule.onNodeWithTag(NavigationControlsPanelTestTags.panel).assertIsDisplayed()

    // Verify Back button exists and triggers controller
    composeTestRule.onNodeWithTag(NavigationControlsPanelTestTags.backButton).assertIsDisplayed().performClick()
    verify(controller).backPressCompleted()

    // Verify Dropdown displays selected edge
    composeTestRule.onNodeWithTag(NavigationControlsPanelTestTags.edgeDropdown).assertIsDisplayed()
    composeTestRule.onNodeWithText(BackNavigationEdge.LEFT_EDGE.visibleName).assertIsDisplayed()

    // Verify Progress slider exists
    composeTestRule.onNodeWithTag(NavigationControlsPanelTestTags.progressSlider).assertIsDisplayed()
  }
}
