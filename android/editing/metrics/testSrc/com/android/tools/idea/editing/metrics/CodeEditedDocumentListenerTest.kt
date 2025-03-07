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
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.replaceService
import com.intellij.util.application
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

private const val CARET_OFFSET = 13
private const val ADDED_STRING = "woohoo, amazing!"

@RunWith(JUnit4::class)
class CodeEditedDocumentListenerTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()
  private val fixture by lazy { projectRule.fixture }
  private val disposable by lazy { projectRule.testRootDisposable }
  private val codeEditedListener = TestCodeEditedListener()

  private val testDispatcher = StandardTestDispatcher()
  private val testScope = TestScope(testDispatcher)

  @Before
  fun setUp() {
    CodeEditedListener.EP_NAME.point.registerExtension(codeEditedListener, disposable)

    application.replaceService(
      CodeEditedMetricsService::class.java,
      CodeEditedMetricsServiceImpl(testScope, testDispatcher),
      projectRule.testRootDisposable,
    )

    application.invokeAndWait {
      fixture.configureByText("Bar.java", "hey I'm a great java file")
      fixture.editor.caretModel.moveToOffset(CARET_OFFSET)
    }
  }

  @Test
  fun basicFunctionality() {
    fixture.type(ADDED_STRING)

    UIUtil.pump()

    testScope.advanceUntilIdle()
    val expectedEvents = List(ADDED_STRING.length) { CodeEdited(1, 0, Source.TYPING) }
    assertThat(codeEditedListener.events).containsExactlyElementsIn(expectedEvents)
  }

  @Test
  fun onePerDocument() {
    application.invokeAndWait {
      // Open a second Editor and ensure it is released later.
      val factory = EditorFactory.getInstance()
      factory.createEditor(fixture.editor.document).also {
        assertThat(it).isNotNull()
        assertThat(it.document).isEqualTo(fixture.editor.document)
        assertThat(it).isNotSameAs(fixture.editor)
        Disposer.register(disposable) { factory.releaseEditor(it) }
      }
    }

    fixture.type(ADDED_STRING)

    UIUtil.pump()

    // If we're getting events from both Editors, we'll have doubles of the events.
    testScope.advanceUntilIdle()
    val expectedEvents = List(ADDED_STRING.length) { CodeEdited(1, 0, Source.TYPING) }
    assertThat(codeEditedListener.events).containsExactlyElementsIn(expectedEvents)
  }
}
