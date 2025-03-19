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
package com.android.tools.idea.settingssync.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.buildAnnotatedString
import com.android.tools.adtui.compose.utils.StudioComposeTestRule.Companion.createStudioComposeTestRule
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class RadioButtonWithCommentTest {
  @get:Rule val composeTestRule = createStudioComposeTestRule()

  enum class Selection {
    A,
    B,
  }

  var selection: Selection by mutableStateOf(Selection.A)

  fun initUi() {
    composeTestRule.setContent {
      Column {
        RadioButtonWithComment(
          annotatedText = buildAnnotatedString { append("${Selection.A}") },
          comment = "comment for ${Selection.A}",
          selected = selection == Selection.A,
          onSelect = { selection = Selection.A },
        )

        RadioButtonWithComment(
          annotatedText = buildAnnotatedString { append("${Selection.B}") },
          comment = "comment for ${Selection.B}",
          selected = selection == Selection.B,
          onSelect = { selection = Selection.B },
        )
      }
    }
  }

  @Test
  fun `display correct text and comment`() {
    initUi()

    composeTestRule.onNodeWithText("${Selection.A}").assertExists()
    composeTestRule.onNodeWithText("${Selection.B}").assertExists()

    composeTestRule.onNodeWithText("comment for ${Selection.A}").assertExists()
    composeTestRule.onNodeWithText("comment for ${Selection.B}").assertExists()
  }

  @Test
  fun `toggle selection on click`() {
    initUi()

    assertThat(selection).isEqualTo(Selection.A)
    composeTestRule.onNodeWithText("${Selection.B}").performClick()
    assertThat(selection).isEqualTo(Selection.B)
  }

  @Test
  fun `click box also toggles selection`() {
    initUi()

    assertThat(selection).isEqualTo(Selection.A)
    composeTestRule.onNodeWithText("comment for ${Selection.B}").performClick()
    assertThat(selection).isEqualTo(Selection.B)
  }
}
