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

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import com.android.tools.adtui.compose.utils.StudioComposeTestRule.Companion.createStudioComposeTestRule
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.JourneyActionArtifacts
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import org.junit.Assume
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.kotlin.inOrder

class JourneysResultsViewTest {

  @get:Rule
  val composeTestRule = createStudioComposeTestRule()

  @Test
  fun journeyTextArtifactsAreDisplayed() {
    // TODO(414800489): Fix on Windows
    Assume.assumeFalse(System.getProperty("os.name").startsWith("Windows"))

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
    Assume.assumeFalse(System.getProperty("os.name").startsWith("Windows"))

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

  @Test
  fun onOpenScreenshotClickedUsesMostRecentCallback() {
    // TODO(414800489): Fix on Windows
    Assume.assumeFalse(System.getProperty("os.name").startsWith("Windows"))

    val initialCallback: () -> Unit = mock()
    val updatedCallback: () -> Unit = mock()
    val currentCallbackState = mutableStateOf(initialCallback)
    val screenshotPath = createTempScreenshotFile()

    composeTestRule.setContent {
      JourneysResultsView(
        artifact = JourneyActionArtifacts("Performed an action", "This is a test", screenshotImage = screenshotPath),
        index = 0,
        numEntries = 1,
        onImageDoubleClicked = currentCallbackState.value
      )
    }

    val screenshotNode = composeTestRule.onNodeWithTag("Screenshot", useUnmergedTree = true)

    // Hover over the screenshot
    screenshotNode.performMouseInput { moveTo(center) }
    composeTestRule.waitForIdle()

    // Find and click the "Open Screenshot" button
    val openScreenshotButton = composeTestRule.onNodeWithText("Open Screenshot")
    openScreenshotButton.performClick()
    composeTestRule.waitForIdle()

    // Update the callback
    currentCallbackState.value = updatedCallback
    composeTestRule.waitForIdle()

    // Hover again to make the button visible
    screenshotNode.performMouseInput { moveTo(center) }
    composeTestRule.waitForIdle()

    // Open the screenshot again
    openScreenshotButton.performClick()
    composeTestRule.waitForIdle()

    // Verify the both callbacks were invoked
    inOrder(initialCallback, updatedCallback) {
      verify(initialCallback, times(1)).invoke()
      verify(updatedCallback, times(1)).invoke()
    }
  }

  internal fun createTempScreenshotFile(): String {
    val bufferedImage = BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB).apply {
      with(createGraphics()) {
        color = Color(0x012345)
        fillRect(0, 0, 100, 100)
      }
    }
    return File.createTempFile("screenshot", ".png").apply {
      ImageIO.write(bufferedImage, "png", this)
    }.absolutePath
  }

  // TODO(414753403): Write more tests

}