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
package com.android.tools.profilers.taskbased.tabs.task.leakcanary.banner

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.android.tools.adtui.compose.utils.StudioComposeTestRule
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.taskbased.common.constants.strings.TaskBasedUxStrings
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class LeakCanaryBannerTest {

  private lateinit var ideProfilerServices: FakeIdeProfilerServices

  @get:Rule val composeTestRule = StudioComposeTestRule.createStudioComposeTestRule()

  @Before
  fun setup() {
    ideProfilerServices = FakeIdeProfilerServices()
  }

  @Test
  fun testBannerDisplaysAllElementsWhenFlagEnabled() {
    var onEditConfigClicked = false
    var onCloseClicked = false
    var onDoNotShowAgainClicked = false

    composeTestRule.setContent {
      LeakCanaryBanner(
        onBannerClose = { onCloseClicked = true },
        onBannerDoNotAskAgainClick = { onDoNotShowAgainClicked = true },
        onEditConfigurationClick = { onEditConfigClicked = true },
      )
    }

    composeTestRule.onNodeWithText(TaskBasedUxStrings.LEAKCANARY_BANNER_MESSAGE).assertIsDisplayed()
    composeTestRule.onNodeWithText(TaskBasedUxStrings.LEAKCANARY_EDIT_CONFIGURATION).assertIsDisplayed()
    composeTestRule.onNodeWithText(TaskBasedUxStrings.DONT_SHOW_AGAIN_TITLE).assertIsDisplayed()

    composeTestRule.onNodeWithText(TaskBasedUxStrings.LEAKCANARY_EDIT_CONFIGURATION).performClick()
    assert(onEditConfigClicked)

    composeTestRule.onNodeWithText(TaskBasedUxStrings.DONT_SHOW_AGAIN_TITLE).performClick()
    assert(onDoNotShowAgainClicked)

    composeTestRule.onNodeWithContentDescription("Close").performClick()
    assert(onCloseClicked)
  }
}
