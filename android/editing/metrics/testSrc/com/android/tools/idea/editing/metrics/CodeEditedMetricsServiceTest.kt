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
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ExtensionTestUtil
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(JUnit4::class)
class CodeEditedMetricsServiceTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()
  private val fixture by lazy { projectRule.fixture }

  private val testScheduler = TestCoroutineScheduler()
  private val testDispatcher = StandardTestDispatcher(testScheduler)
  private val testScope = TestScope(testDispatcher)
  private val service: CodeEditedMetricsServiceImpl by lazy {
    CodeEditedMetricsServiceImpl(testScope, testDispatcher).also {
      Disposer.register(projectRule.testRootDisposable, it)
    }
  }
  private val fakeCodeEditedListener = FakeCodeEditedListener()

  @Before
  fun setUp() {
    ExtensionTestUtil.maskExtensions(
      CodeEditedListener.EP_NAME,
      listOf(fakeCodeEditedListener),
      projectRule.testRootDisposable,
    )
    fixture.configureByText("empty.kt", "")
  }

  @Test
  fun noFragmentsNoEvents() {
    service.recordCodeEdited(FakeDocumentEvent("" to ""))

    testScope.runCurrent()

    assertThat(fakeCodeEditedListener.receivedEvents).isEmpty()
  }

  @Test
  fun allEventsSent() {
    service.setCodeEditingAction(CodeEditingAction.NewLine)
    service.recordCodeEdited(FakeDocumentEvent("" to "\n12345"))

    testScope.runCurrent()

    assertThat(fakeCodeEditedListener.receivedEvents)
      .containsExactly(CodeEdited(1, 0, Source.TYPING), CodeEdited(5, 0, Source.IDE_ACTION))
      .inOrder()
  }

  @Test
  fun setAndClearCodeEditingAction() {
    service.recordCodeEdited(FakeDocumentEvent("foo" to "bar"))
    service.setCodeEditingAction(CodeEditingAction.Typing)
    service.recordCodeEdited(FakeDocumentEvent("bar" to "bazquux"))
    service.clearCodeEditingAction()
    service.recordCodeEdited(FakeDocumentEvent("bazquux" to ""))

    testScope.runCurrent()

    assertThat(fakeCodeEditedListener.receivedEvents)
      .containsExactly(
        CodeEdited(3, 3, Source.UNKNOWN),
        CodeEdited(7, 3, Source.TYPING),
        CodeEdited(0, 7, Source.UNKNOWN),
      )
      .inOrder()
  }

  inner class FakeDocumentEvent(
    private val oldFrag: CharSequence,
    private val newFrag: CharSequence,
  ) : DocumentEvent(fixture.editor.document) {
    constructor(oldToNew: Pair<CharSequence, CharSequence>) : this(oldToNew.first, oldToNew.second)

    override fun getOffset() = 42

    override fun getOldLength() = oldFrag.length

    override fun getNewLength() = newFrag.length

    override fun getOldFragment() = oldFrag

    override fun getNewFragment() = newFrag

    override fun getOldTimeStamp() = 0L
  }
}

/** A fake [CodeEditedListener] that we can use in tests. */
private class FakeCodeEditedListener : CodeEditedListener {
  val receivedEvents = mutableListOf<CodeEdited>()

  override fun onCodeEdited(event: CodeEdited) {
    receivedEvents.add(event)
  }
}
