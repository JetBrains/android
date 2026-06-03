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

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import com.android.tools.adtui.compose.utils.StudioComposeTestRule
import com.android.tools.idea.compose.preview.BackNavigationEdge
import com.android.tools.idea.compose.preview.message
import com.intellij.testFramework.ProjectRule
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class NavigationControlsPanelUiTest {
  @get:Rule val composeTestRule = StudioComposeTestRule.createStudioComposeTestRule()
  @get:Rule val projectRule = ProjectRule()

  @Test
  fun testBottomNavigationContentUi() {
    val canBackPressMutable = mutableStateOf(true)
    val fpsUpdater = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    var backPressCallCount = 0
    var backPressStartCalledWithEdge: BackNavigationEdge? = null
    var backPressProgressCallCount = 0
    var backPressTrackProgressCallCount = 0
    var edgeDropdownPressCallCount = 0

    composeTestRule.setContent {
      NavigationControlsPanel(
        canBackPress = { canBackPressMutable.value },
        onBackPress = { backPressCallCount++ },
        onBackPressStart = { backPressStartCalledWithEdge = it },
        onBackPressProgress = { _, _ -> backPressProgressCallCount++ },
        onBackPressTrackProgress = { backPressTrackProgressCallCount++ },
        onEdgeDropdownPress = { edgeDropdownPressCallCount++ },
        fpsUpdater = fpsUpdater,
      )
    }

    // Verify main panel is displayed
    composeTestRule.onNodeWithTag(NavigationControlsPanelTestTags.panel).assertIsDisplayed()

    // Emulate the update from the fps counter
    fpsUpdater.tryEmit(Unit)

    // Verify Back button triggers onBackPress
    composeTestRule.onNodeWithTag(NavigationControlsPanelTestTags.backButton).assertIsDisplayed().performClick()
    assertEquals(1, backPressCallCount)

    // Verify Dropdown selection triggers onEdgeDropdownPress
    composeTestRule.onNodeWithTag(NavigationControlsPanelTestTags.edgeDropdown).assertIsDisplayed().assertIsEnabled().performClick()
    composeTestRule.onNodeWithText(BackNavigationEdge.RIGHT_EDGE.visibleName).assertIsDisplayed().performClick()
    assertEquals(1, edgeDropdownPressCallCount)
    composeTestRule.onNodeWithText(BackNavigationEdge.RIGHT_EDGE.visibleName).assertIsDisplayed()

    // Verify Progress slider triggers start, progress and track callbacks
    composeTestRule.onNodeWithTag(NavigationControlsPanelTestTags.progressSlider).assertIsDisplayed().assertIsEnabled().performTouchInput {
      swipeRight()
    }
    assertEquals(BackNavigationEdge.RIGHT_EDGE, backPressStartCalledWithEdge)
    assertTrue("Progress callback should be called", backPressProgressCallCount > 0)
    assertEquals(1, backPressTrackProgressCallCount)

    // Verify behavior when back navigation is unavailable
    canBackPressMutable.value = false
    fpsUpdater.tryEmit(Unit)

    // Verify button and slider are disabled
    composeTestRule.onNodeWithTag(NavigationControlsPanelTestTags.backButton).assertIsDisplayed().assertIsNotEnabled()
    composeTestRule.onNodeWithTag(NavigationControlsPanelTestTags.progressSlider).assertIsDisplayed().assertIsNotEnabled()

    // Verify clicking disabled button does not increment counter
    val countBeforeClick = backPressCallCount
    composeTestRule.onNodeWithTag(NavigationControlsPanelTestTags.backButton).performClick()
    assertEquals("Callback should not be triggered when button is disabled", countBeforeClick, backPressCallCount)
  }

  @Test
  fun testProgressSliderResetsOnBackPressCompletedFlow() {
    val fpsUpdater = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val backPressCompletedFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    composeTestRule.setContent {
      NavigationControlsPanel(
        canBackPress = { true },
        onBackPress = {},
        onBackPressStart = {},
        onBackPressProgress = { _, _ -> },
        onBackPressTrackProgress = {},
        onEdgeDropdownPress = {},
        fpsUpdater = fpsUpdater,
        backPressCompletedFlow = backPressCompletedFlow,
      )
    }

    // Verify initially the slider is at 0f (label should display 0.0)
    composeTestRule.onNodeWithText(message("action.navigate.back.predictive.back.progress", 0.0f)).assertIsDisplayed()

    // Drag the slider to update the value
    composeTestRule.onNodeWithTag(NavigationControlsPanelTestTags.progressSlider).assertIsDisplayed().performTouchInput { swipeRight() }

    // Since swipeRight drags the slider, the label should be updated to a positive float value (e.g., 1.0f or something similar)
    // We can verify that it is NOT at 0.0f anymore
    composeTestRule.onNodeWithText(message("action.navigate.back.predictive.back.progress", 0.0f)).assertDoesNotExist()

    // Trigger back press completion from the flow
    backPressCompletedFlow.tryEmit(Unit)
    composeTestRule.waitForIdle()

    // Verify the slider resets back to 0.0f
    composeTestRule.onNodeWithText(message("action.navigate.back.predictive.back.progress", 0.0f)).assertIsDisplayed()
  }
}
