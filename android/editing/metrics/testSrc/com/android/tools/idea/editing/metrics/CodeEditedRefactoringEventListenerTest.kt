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
import com.android.tools.idea.testing.getEnclosing
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.refactoring.rename.RenameProcessor
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.replaceService
import com.intellij.util.application
import org.jetbrains.kotlin.psi.KtProperty
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class CodeEditedRefactoringEventListenerTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()
  @get:Rule val edtRule = EdtRule()
  private val fixture by lazy { projectRule.fixture }
  private val disposable by lazy { projectRule.testRootDisposable }
  private val fakeCodeEditedMetricsService = FakeCodeEditedMetricsService()

  @Before
  fun setUp() {
    application.replaceService(
      CodeEditedMetricsService::class.java,
      fakeCodeEditedMetricsService,
      disposable,
    )
  }

  @Test
  fun recordsSourcesCorrectly() {
    val contents =
      // language=kotlin
      """
      package com.example

      const val OLD_NAME = 8675309
      fun doSomething() {
        println("Jenny's number is: " + OLD_NAME)
      }
      fun doSomethingElse() {
        PhoneService.getInstance().dial(OLD_NAME)
      }
      """
        .trimIndent()
    val virtualFile =
      fixture.addFileToProject("src/com/example/MyGreatFile.kt", contents).virtualFile
    val otherContents =
      // language=kotlin
      """
      package com.example

      fun doSomethingInAnotherFile() {
        println(1..OLD_NAME)
      }
      fun doSomethingElseInAnotherFile() {
        repeat(OLD_NAME) { playTommyTutoneSongs() }
      }
      """
        .trimIndent()
    val otherVirtualFile =
      fixture.addFileToProject("src/com/example/MyOtherGreatFile.kt", otherContents).virtualFile
    fixture.configureFromExistingVirtualFile(otherVirtualFile)
    fixture.configureFromExistingVirtualFile(virtualFile)

    application.invokeAndWait {
      val property: KtProperty = fixture.getEnclosing("OLD_N|AME =")
      val refactor = RenameProcessor(projectRule.project, property, "NEW_NAME", false, false)
      refactor.run()
    }
    with(fakeCodeEditedMetricsService) {
      assertThat(events.values.distinct()).containsExactly(CodeEditingAction.Refactoring)
      assertThat(events).hasSize(5)
    }
    assertThat(fakeCodeEditedMetricsService.action).isEqualTo(CodeEditingAction.Unknown)
  }

  private class FakeCodeEditedMetricsService : CodeEditedMetricsService {
    val events = mutableMapOf<DocumentEvent, CodeEditingAction>()
    var action: CodeEditingAction = CodeEditingAction.Unknown

    override fun setCodeEditingAction(action: CodeEditingAction) {
      this.action = action
    }

    override fun clearCodeEditingAction() {
      action = CodeEditingAction.Unknown
    }

    override fun recordCodeEdited(event: DocumentEvent) {
      events[event] = action
    }
  }
}
