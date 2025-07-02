/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.testartifacts.instrumented.testsuite.view

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.android.tools.adtui.compose.utils.StudioComposeTestRule.Companion.createStudioComposeTestRule
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.JourneyActionArtifacts
import org.junit.Rule
import org.junit.Test

class JourneysResultsViewTest {

  @get:Rule
  val composeTestRule = createStudioComposeTestRule()

  @Test
  fun journeyTextArtifactsAreDisplayed() {
    // TODO(414800489): Fix on Windows
    if (System.getProperty("os.name").startsWith("Windows")) {
      return
    }

    val artifact = JourneyActionArtifacts("Performed an action", "This is a test", "")
    composeTestRule.setContent {
      JourneysResultsView(
        modifier = Modifier,
        artifact = artifact,
        index = 0,
        numEntries = 3,
        onImageDoubleClicked = {}
      )
    }

    composeTestRule
      .onNodeWithText("Performed an action")
      .assertIsDisplayed()
    composeTestRule
      .onNodeWithText("This is a test")
      .assertIsDisplayed()
  }

  @Test
  fun errorIconIsDisplayedWhenScreenshotArtifactDoesntExist() {
    // TODO(414800489): Fix on Windows
    if (System.getProperty("os.name").startsWith("Windows")) {
      return
    }

    val artifact = JourneyActionArtifacts("Performed an action", "This is a test", "path_to_image_that_doesnt_exist.png")
    composeTestRule.setContent {
      JourneysResultsView(
        modifier = Modifier,
        artifact = artifact,
        index = 0,
        numEntries = 3,
        onImageDoubleClicked = {}
      )
    }

    composeTestRule
      .onNodeWithTag("ScreenshotError", useUnmergedTree = true)
      .assertIsDisplayed()
  }

  // TODO(414753403): Write more tests

}