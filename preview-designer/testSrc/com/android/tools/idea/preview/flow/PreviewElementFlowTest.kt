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
package com.android.tools.idea.preview.flow

import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.concurrency.asCollection
import com.android.tools.idea.concurrency.createChildScope
import com.android.tools.idea.preview.PreviewElementProvider
import com.android.tools.idea.preview.PsiTestPreviewElement
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.executeAndSave
import com.android.tools.idea.testing.insertText
import com.android.tools.idea.testing.moveCaretToEnd
import com.android.tools.idea.ui.ApplicationUtils
import com.intellij.openapi.application.ModalityState
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.waitUntil
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock

class PreviewElementFlowTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  val fixture: CodeInsightTestFixture
    get() = projectRule.fixture

  @Test
  fun `test flow updates on file changes`(): Unit = runBlocking {
    val psiFile =
      projectRule.fixture.addFileToProject(
        "src/TestFile.kt",
        // language=kotlin
        """
        @Preview
        fun Preview1() {
        }
      """
          .trimIndent(),
      )

    val firstPreviewElements = listOf(PsiTestPreviewElement())
    val secondPreviewElements = listOf(PsiTestPreviewElement(), PsiTestPreviewElement())

    val filePreviewElementFinder = mock<PreviewElementProvider<PsiTestPreviewElement>>()
    whenever(filePreviewElementFinder.previewElements())
      .thenReturn(firstPreviewElements.asSequence())

    runBlocking {
      val flowScope = createChildScope()
      val flow = previewElementsOnFileChangesFlow(projectRule.project) { filePreviewElementFinder }
      val updates = mutableListOf<List<PsiTestPreviewElement>>()
      flowScope.launch { flow.collect { updates += it.asCollection().toList() } }
      val flowState = flow.stateIn(flowScope)

      assertEquals(firstPreviewElements.toList(), flowState.value.asCollection().toList())
      assertEquals(listOf(firstPreviewElements), updates)

      withContext(AndroidDispatchers.uiThread) {
        projectRule.fixture.openFileInEditor(psiFile.virtualFile)
      }
      // the same preview elements will be returned despite the following file changes to check
      // that the flow is de-duped
      repeat(2) {
        ApplicationUtils.invokeWriteActionAndWait(ModalityState.defaultModalityState()) {
          projectRule.fixture.editor.moveCaretToEnd()
          projectRule.fixture.editor.executeAndSave {
            insertText(
              "\n\nfun method$it() {\n// Some change that will not trigger an update\n}\n\n"
            )
          }
        }
        // Wait for longer than the debouncing timer to ensure we do not remove the changes just by
        // de-bouncing
        delay(500)
      }

      assertEquals(firstPreviewElements, flowState.value.asCollection().toList())
      // there should only be one update up to this point
      assertEquals(listOf(firstPreviewElements), updates)

      // now the following file change should trigger an update as the preview elements found are
      // different
      whenever(filePreviewElementFinder.previewElements())
        .thenReturn(secondPreviewElements.asSequence())

      ApplicationUtils.invokeWriteActionAndWait(ModalityState.defaultModalityState()) {
        projectRule.fixture.editor.moveCaretToEnd()
        projectRule.fixture.editor.executeAndSave {
          insertText("\n\n// Some change that will trigger an update\n\n")
        }
      }

      // the flow should be updated with the second preview element list
      waitUntil { flowState.value.asCollection().toList() == secondPreviewElements }
      assertEquals(listOf(firstPreviewElements, secondPreviewElements), updates)

      // Terminate the flow
      flowScope.cancel()
    }
  }
}
