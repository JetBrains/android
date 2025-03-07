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
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

@RunWith(JUnit4::class)
class CodeEditedCopyPastePreProcessorTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  private val fixture by lazy { projectRule.fixture }
  private val codeEditedListener = TestCodeEditedListener()

  private val processor = CodeEditedCopyPastePreProcessor()

  private val testDispatcher = StandardTestDispatcher()
  private val testScope = TestScope(testDispatcher)

  @Before
  fun setUp() {
    CodeEditedListener.EP_NAME.point.registerExtension(
      codeEditedListener,
      projectRule.testRootDisposable,
    )

    application.replaceService(
      CodeEditedMetricsService::class.java,
      CodeEditedMetricsServiceImpl(testScope, testDispatcher),
      projectRule.testRootDisposable,
    )
  }

  @Test
  fun preprocessOnCopyReturnsNull() {
    val mockCodeEditedMetricsService: CodeEditedMetricsService = mock()
    application.replaceService(
      CodeEditedMetricsService::class.java,
      mockCodeEditedMetricsService,
      projectRule.testRootDisposable,
    )

    val result = processor.preprocessOnCopy(mock(), IntArray(0), IntArray(0), "foo")

    assertThat(result).isNull()
    verify(mockCodeEditedMetricsService, never()).setCodeEditingAction(any())
  }

  @Test
  fun preprocessOnPasteSetsAction() {
    val mockCodeEditedMetricsService: CodeEditedMetricsService = mock()
    application.replaceService(
      CodeEditedMetricsService::class.java,
      mockCodeEditedMetricsService,
      projectRule.testRootDisposable,
    )

    val str = "foobar"

    val result = processor.preprocessOnPaste(mock(), mock(), mock(), str, null)

    assertThat(result).isEqualTo(str)
    verify(mockCodeEditedMetricsService).setCodeEditingAction(CodeEditingAction.UserPaste)
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

    testScope.advanceUntilIdle()
    assertThat(codeEditedListener.events).isEmpty()

    application.invokeAndWait {
      fixture.moveCaret("foobar|")
      fixture.setSelection("foobar|")
      fixture.performEditorAction(IdeActions.ACTION_PASTE)
    }

    // Make sure the paste actually happened.
    testScope.advanceUntilIdle()
    assertThat(fixture.editor.document.text).contains(" foobarfoo ")
    assertThat(codeEditedListener.events).containsExactly(CodeEdited(3, 0, Source.USER_PASTE))
  }
}
