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
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import com.android.tools.adtui.compose.utils.StudioComposeTestRule
import com.android.tools.idea.compose.preview.BackNavigationEdge
import com.android.tools.idea.compose.preview.InteractivePreviewNavigationController
import com.android.tools.idea.compose.preview.TestComposeViewAdapterViewObj
import com.android.tools.idea.compose.preview.message
import com.android.tools.idea.preview.analytics.InteractivePreviewUsageTracker
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
    var backPressProgressCallCount = 0
    var backPressTrackProgressCallCount = 0
    var edgeDropdownPressCallCount = 0

    composeTestRule.setContent {
      NavigationControlsPanel(
        isEdgeNavigationImplemented = { true },
        canBackPress = { canBackPressMutable.value },
        onBackPress = { backPressCallCount++ },
        onBackPressStart = {},
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
    composeTestRule.mainClock.advanceTimeBy(100L)
    composeTestRule.waitForIdle()
    assertEquals(1, backPressCallCount)

    // Verify Dropdown selection triggers onEdgeDropdownPress
    composeTestRule.onNodeWithTag(NavigationControlsPanelTestTags.edgeDropdown).assertIsDisplayed().assertIsEnabled().performClick()
    composeTestRule.onNodeWithText(BackNavigationEdge.EDGE_RIGHT.visibleName).assertIsDisplayed().performClick()
    assertEquals(1, edgeDropdownPressCallCount)
    composeTestRule.onNodeWithText(BackNavigationEdge.EDGE_RIGHT.visibleName).assertIsDisplayed()

    // Verify divider is displayed
    composeTestRule.onNodeWithTag(NavigationControlsPanelTestTags.divider).assertIsDisplayed()

    // Verify Progress slider triggers start, progress and track callbacks
    composeTestRule.onNodeWithTag(NavigationControlsPanelTestTags.progressSlider).assertIsDisplayed().assertIsEnabled().performTouchInput {
      swipeRight()
    }
    assertTrue("Progress callback should be called", backPressProgressCallCount > 0)
    assertEquals(1, backPressTrackProgressCallCount)

    // Verify behavior when back navigation is unavailable
    canBackPressMutable.value = false
    fpsUpdater.tryEmit(Unit)

    // Verify button and slider are disabled
    composeTestRule.onNodeWithTag(NavigationControlsPanelTestTags.backButton).assertIsDisplayed().assertIsNotEnabled()
    composeTestRule.onNodeWithTag(NavigationControlsPanelTestTags.progressSlider).assertIsDisplayed().assertIsNotEnabled()

    // Verify tooltip appears when back button is disabled
    composeTestRule.onNodeWithText(message("action.navigate.back.button.disabled.tooltip")).assertDoesNotExist()
    composeTestRule.onNodeWithTag(NavigationControlsPanelTestTags.backButton).performMouseInput { moveTo(center) }
    composeTestRule.mainClock.advanceTimeBy(1201L) // org.jetbrains.jewel.ui.component.styling.TooltipMetrics delay is 1200ms
    composeTestRule.onNodeWithText(message("action.navigate.back.button.disabled.tooltip")).assertIsDisplayed()

    // Verify clicking disabled button does not increment counter
    val countBeforeClick = backPressCallCount
    composeTestRule.onNodeWithTag(NavigationControlsPanelTestTags.backButton).performClick()
    assertEquals("Callback should not be triggered when button is disabled", countBeforeClick, backPressCallCount)
  }

  @Test
  fun testBackButtonTriggersStartDelayAndCompleted() {
    var backPressStartCalledWithEdge: BackNavigationEdge? = null
    var backPressCalled = false

    composeTestRule.setContent {
      NavigationControlsPanel(
        isEdgeNavigationImplemented = { true },
        canBackPress = { true },
        onBackPress = { backPressCalled = true },
        onBackPressStart = { backPressStartCalledWithEdge = it },
        onBackPressProgress = { _, _ -> },
        onBackPressTrackProgress = {},
        onEdgeDropdownPress = {},
        fpsUpdater = MutableSharedFlow(),
      )
    }

    composeTestRule.onNodeWithTag(NavigationControlsPanelTestTags.backButton).performClick()

    // Immediately after click, onBackPressStart should be called, but onBackPress should wait for delay
    assertEquals(BackNavigationEdge.EDGE_NONE, backPressStartCalledWithEdge)

    // Advance clock past the delay (100ms)
    composeTestRule.mainClock.advanceTimeBy(100L)
    composeTestRule.waitForIdle()

    assertTrue("onBackPress should be called after delay", backPressCalled)
  }

  @Test
  fun testProgressSliderResetsOnBackPressCompletedFlow() {
    val fpsUpdater = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val backPressCompletedFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    composeTestRule.setContent {
      NavigationControlsPanel(
        isEdgeNavigationImplemented = { true },
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

  @Test
  fun testNavigationControlsContentEdgeDropdownPressCancelsBackPress() {
    var edgeDropdownPressTracked = false
    var cancelledCalled = false

    val tracker =
      object : InteractivePreviewUsageTracker {
        override fun logInteractiveSession(fps: Int, durationMs: Int, userInteractions: Int) {}

        override fun logStartupTime(timeMs: Int, peers: Int) {}

        override fun trackNavigationPanelProgressPress() {}

        override fun trackNavigationPanelBackPress() {}

        override fun trackNavigationPanelVisibilityChange(isShown: Boolean) {}

        override fun trackNavigationPanelEdgeDropdownPress() {
          edgeDropdownPressTracked = true
        }
      }

    val composeViewAdapterObjFake = TestComposeViewAdapterViewObj(onBackPressCancelledCallback = { cancelledCalled = true })

    val fpsUpdater = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    val controller =
      InteractivePreviewNavigationController(usageTrackerProvider = { tracker }, fpsUpdater = fpsUpdater).apply {
        updateObjects(null, composeViewAdapterObjFake, hasNavDisplay = false)
      }

    composeTestRule.setContent {
      NavigationControlsContent(
        interactivePreviewNavigationController = controller,
        fpsUpdater = fpsUpdater,
        isEdgeNavigationImplemented = { true },
      )
    }

    // Verify initially dropdown shows "None"
    composeTestRule.onNodeWithText(BackNavigationEdge.EDGE_NONE.visibleName).assertIsDisplayed()

    // Click on the edge dropdown to select "Right"
    composeTestRule.onNodeWithTag(NavigationControlsPanelTestTags.edgeDropdown).assertIsDisplayed().performClick()
    composeTestRule.onNodeWithText(BackNavigationEdge.EDGE_RIGHT.visibleName).assertIsDisplayed().performClick()

    // Verify dropdown has updated to "Right"
    composeTestRule.onNodeWithText(BackNavigationEdge.EDGE_RIGHT.visibleName).assertIsDisplayed()

    // Assert that both edge tracking and back press cancellation are triggered correctly
    assertTrue("Edge dropdown press tracking should be triggered", edgeDropdownPressTracked)
    assertTrue("Back press cancellation should be triggered", cancelledCalled)
  }

  @Test
  fun testEdgeDropdownDisabledWhenShowEdgeNavigationIsFalse() {
    val fpsUpdater = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    composeTestRule.setContent {
      NavigationControlsPanel(
        isEdgeNavigationImplemented = { false },
        canBackPress = { true },
        onBackPress = {},
        onBackPressStart = {},
        onBackPressProgress = { _, _ -> },
        onBackPressTrackProgress = {},
        onEdgeDropdownPress = {},
        fpsUpdater = fpsUpdater,
      )
    }

    // Verify main panel is displayed
    composeTestRule.onNodeWithTag(NavigationControlsPanelTestTags.panel).assertIsDisplayed()

    // Verify divider is displayed
    composeTestRule.onNodeWithTag(NavigationControlsPanelTestTags.divider).assertIsDisplayed()
    // Verify Back button is displayed
    composeTestRule.onNodeWithTag(NavigationControlsPanelTestTags.backButton).assertIsDisplayed()
    // Verify Dropdown is displayed but disabled
    composeTestRule.onNodeWithTag(NavigationControlsPanelTestTags.edgeDropdown).assertIsDisplayed().assertIsNotEnabled()

    // Verify tooltip appears when edge dropdown is disabled
    composeTestRule.onNodeWithText(message("action.navigate.back.navigation.edge.disabled.tooltip")).assertDoesNotExist()
    composeTestRule.onNodeWithTag(NavigationControlsPanelTestTags.edgeDropdown).performMouseInput { moveTo(center) }
    composeTestRule.mainClock.advanceTimeBy(1201L) // org.jetbrains.jewel.ui.component.styling.TooltipMetrics delay is 1200ms
    composeTestRule.onNodeWithText(message("action.navigate.back.navigation.edge.disabled.tooltip")).assertIsDisplayed()
  }
}
