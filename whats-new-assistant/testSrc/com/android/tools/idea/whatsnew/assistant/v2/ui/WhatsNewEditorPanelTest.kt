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
package com.android.tools.idea.whatsnew.assistant.v2.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.android.repository.Revision
import com.android.tools.adtui.compose.StudioTestTheme
import com.android.tools.idea.whatsnew.assistant.v2.model.WhatsNewAssets
import com.android.tools.idea.whatsnew.assistant.v2.model.WhatsNewMarkdownDocument
import com.android.tools.idea.whatsnew.assistant.v2.ui.composeutils.DefaultImagePainterLoader
import java.nio.file.Path
import kotlin.io.path.readText
import org.jetbrains.android.AndroidTestBase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

class WhatsNewEditorPanelTest {

  @get:Rule val composeTestRule = createComposeRule()

  private lateinit var markdownDocuments: List<WhatsNewMarkdownDocument>

  @Before
  fun setUp() {
    val testFile = getTestDataPath().resolve("whatsnewassistant/2026.1.1.md")
    markdownDocuments =
      listOf(
        WhatsNewMarkdownDocument(
          productVersion = Revision.parseRevision("2026.1.1"),
          shortName = "Quail 1",
          fullMarkdownContents = testFile.readText(),
        )
      )
  }

  @After
  fun tearDown() {
    composeTestRule.setContent {}
  }

  @Ignore("b/520107807")
  @Test
  fun tableOfContents() {
    composeTestRule.setContent {
      StudioTestTheme {
        WhatsNewEditorPanel(
          markdownDocuments = markdownDocuments,
          whatsNewAssets = WhatsNewAssets(),
          imageLoader = DefaultImagePainterLoader(),
        )
      }
    }

    // Expand the table of contents
    composeTestRule.onNodeWithContentDescription("Menu").performClick()

    composeTestRule.onNodeWithText("Quail 1 | 2026.1.1").assertExists()
  }

  @Ignore("b/520107807")
  @Test
  fun oneColumnWithNarrowPanel() {
    composeTestRule.setContent {
      StudioTestTheme {
        Box(modifier = Modifier.width(800.dp).height(6000.dp)) {
          WhatsNewEditorPanel(
            markdownDocuments = markdownDocuments,
            whatsNewAssets = WhatsNewAssets(),
            imageLoader = DefaultImagePainterLoader(),
          )
        }
      }
    }

    val firstBlockLeft = composeTestRule.onNodeWithText("Rules in Gemini").getBoundsInRoot().left
    val secondBlockLeft = composeTestRule.onNodeWithText("Gemini in Android Studio's Agent mode").getBoundsInRoot().left
    assertEquals(firstBlockLeft, secondBlockLeft)
  }

  @Ignore("b/520107807")
  @Test
  fun twoColumnsWithWidePanel() {
    composeTestRule.setContent {
      StudioTestTheme {
        Box(modifier = Modifier.width(2000.dp).height(6000.dp)) {
          WhatsNewEditorPanel(
            markdownDocuments = markdownDocuments,
            whatsNewAssets = WhatsNewAssets(),
            imageLoader = DefaultImagePainterLoader(),
          )
        }
      }
    }

    val firstBlockLeft = composeTestRule.onNodeWithText("Rules in Gemini").getBoundsInRoot().left
    val secondBlockLeft = composeTestRule.onNodeWithText("Gemini in Android Studio's Agent mode").getBoundsInRoot().left
    assertTrue(firstBlockLeft < secondBlockLeft)
  }

  private fun getTestDataPath(): Path {
    return Path.of(AndroidTestBase.getTestDataPath())
  }
}
