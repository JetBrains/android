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
package com.android.tools.idea.editing.metrics

import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.moveCaret
import com.android.tools.idea.testing.setSelection
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.testFramework.replaceService
import com.intellij.util.application
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.kotlin.mock

@RunWith(JUnit4::class)
class CodeEditedCopyPastePreProcessorTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  private val fixture by lazy { projectRule.fixture }
  private val fakeCodeEditedMetricsService = FakeCodeEditedMetricsService()

  private val processor = CodeEditedCopyPastePreProcessor()

  @Before
  fun setUp() {
    application.replaceService(
      CodeEditedMetricsService::class.java,
      fakeCodeEditedMetricsService,
      projectRule.testRootDisposable,
    )
  }

  @Test
  fun preprocessOnCopyReturnsNull() {
    val result = processor.preprocessOnCopy(mock(), IntArray(0), IntArray(0), "foo")

    assertThat(result).isNull()
    assertThat(fakeCodeEditedMetricsService.currentCodeEditingAction)
      .isEqualTo(CodeEditingAction.Unknown)
  }

  @Test
  fun preprocessOnPasteSetsAction() {
    val str = "foobar"

    val result = processor.preprocessOnPaste(mock(), mock(), mock(), str, null)

    assertThat(result).isEqualTo(str)
    assertThat(fakeCodeEditedMetricsService.currentCodeEditingAction)
      .isEqualTo(CodeEditingAction.UserPaste)
  }

  @Test
  fun actualPaste() {
    fixture.configureByText(
      "MyGreatFile.kt",
      // language=kotlin
      """
      package com.example

      val foobar = 3
      """
        .trimIndent(),
    )
    application.invokeAndWait {
      fixture.setSelection("|foo|bar")
      fixture.performEditorAction(IdeActions.ACTION_COPY)
    }

    assertThat(fakeCodeEditedMetricsService.eventToAction).isEmpty()

    application.invokeAndWait {
      fixture.moveCaret("foobar|")
      fixture.setSelection("foobar|")
      fixture.performEditorAction(IdeActions.ACTION_PASTE)
    }

    // Make sure the paste actually happened.
    assertThat(fixture.editor.document.text).contains(" foobarfoo ")
    assertThat(fakeCodeEditedMetricsService.eventToAction.values)
      .containsExactly(CodeEditingAction.UserPaste)
  }
}
