/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.flow

import com.android.tools.idea.compose.ComposeProjectRule
import com.android.tools.idea.compose.preview.defaultFilePreviewElementFinder
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.concurrency.asCollection
import com.android.tools.idea.concurrency.awaitStatus
import com.android.tools.idea.concurrency.createChildScope
import com.android.tools.idea.preview.FilePreviewElementProvider
import com.android.tools.idea.preview.flow.previewElementsOnFileChangesFlow
import com.android.tools.idea.testing.executeAndSave
import com.android.tools.idea.testing.insertText
import com.android.tools.idea.testing.moveCaretToEnd
import com.android.tools.idea.testing.replaceText
import com.android.tools.idea.ui.ApplicationUtils
import com.android.tools.preview.ComposePreviewElement
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.SmartPointerManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.seconds

class ComposePreviewElementFlowTest {
  @get:Rule val projectRule = ComposeProjectRule()

  @Test
  fun `test flow updates`(): Unit = runBlocking {
    val psiFile =
      projectRule.fixture.addFileToProject(
        "src/OtherFile.kt",
        // language=kotlin
        """
        import androidx.compose.ui.tooling.preview.Devices
        import androidx.compose.ui.tooling.preview.Preview
        import androidx.compose.runtime.Composable

        @Composable
        @Preview
        fun Preview1() {
        }
      """
          .trimIndent(),
      )
    val psiFilePointer = runReadAction { SmartPointerManager.createPointer(psiFile) }

    val completed = CompletableDeferred<Unit>()
    val listenersReady = CompletableDeferred<Unit>()
    val previousElement = AtomicReference<Collection<ComposePreviewElement<*>>>(emptySet())
    val previewElementProvider =
      FilePreviewElementProvider(psiFilePointer, defaultFilePreviewElementFinder)
    val testJob = launch {
      val flowScope = createChildScope()
      val flow =
        previewElementsOnFileChangesFlow(projectRule.project) { previewElementProvider }
          .map { it.asCollection() }
          .onEach { newValue ->
            val previousValue = previousElement.getAndSet(newValue)

            // Assert that we do not receive duplicated updates
            assertFalse("Duplicated update received", previousValue == newValue)
          }
          .stateIn(flowScope)
      flow.awaitStatus(timeout = 5.seconds) { elements ->
        elements.singleOrNull()?.methodFqn == "OtherFileKt.Preview1"
      }

      listenersReady.complete(Unit)

      // Wait for the final state we care about
      flow.awaitStatus(timeout = 5.seconds) { elements ->
        elements.map { it.methodFqn }.sorted().joinToString("\n") ==
          """
            OtherFileKt.Preview1
            OtherFileKt.Preview2
          """
            .trimIndent()
      }

      completed.complete(Unit)

      flowScope.cancel()
    }

    listenersReady.await()

    withContext(uiThread) { projectRule.fixture.openFileInEditor(psiFile.virtualFile) }

    // Make irrelevant change that should not trigger any updates
    repeat(2) {
      ApplicationUtils.invokeWriteActionAndWait(ModalityState.defaultModalityState()) {
        projectRule.fixture.editor.moveCaretToEnd()
        projectRule.fixture.editor.executeAndSave {
          insertText("\n\n// Irrelevant change should not trigger an update\n\n")
        }
        projectRule.fixture.editor.executeAndSave {
          insertText(
            "\n\nfun method$it() {\n// Irrelevant change should not trigger an update\n}\n\n"
          )
        }
      }
      // Wait for longer than the debouncing timer to ensure we do not remove the changes just by
      // de-bouncing
      delay(500)
    }

    // Make the change that will trigger an update
    ApplicationUtils.invokeWriteActionAndWait(ModalityState.defaultModalityState()) {
      projectRule.fixture.editor.executeAndSave {
        insertText(
          """

            @Composable
            @Preview
            fun Preview2() {
            }

          """
            .trimIndent()
        )
      }
    }

    completed.await()

    // Ensure the flow listener is terminated
    testJob.cancel()
  }

  @Test
  fun `test multi preview flow updates`(): Unit {
    val multiPreviewPsiFile =
      projectRule.fixture.addFileToProject(
        "src/Multipreview.kt",
        // language=kotlin
        """
          import androidx.compose.ui.tooling.preview.Preview

          @Preview(name = "A")
          @Preview(name = "B")
          annotation class MultiPreview
        """
          .trimIndent(),
      )
    val psiFile =
      projectRule.fixture.addFileToProject(
        "src/OtherFile.kt",
        // language=kotlin
        """
        import androidx.compose.ui.tooling.preview.Preview
        import androidx.compose.runtime.Composable

        @Composable
        @MultiPreview
        fun Preview1() {
        }
      """
          .trimIndent(),
      )
    val psiFilePointer = runReadAction { SmartPointerManager.createPointer(psiFile) }
    val previewElementProvider =
      FilePreviewElementProvider(psiFilePointer, defaultFilePreviewElementFinder)

    runBlocking {
      val flowScope = createChildScope()
      val flow =
        previewElementsOnFileChangesFlow(projectRule.project) { previewElementProvider }
          .stateIn(flowScope)
      assertEquals(
        "Preview1 - A,Preview1 - B",
        flow
          .map { it.asCollection() }
          .filter { it.size == 2 }
          .first()
          .joinToString(",") { it.displaySettings.name },
      )

      // Make change
      ApplicationUtils.invokeWriteActionAndWait(ModalityState.defaultModalityState()) {
        projectRule.fixture.openFileInEditor(multiPreviewPsiFile.virtualFile)

        // Make 3 changes that should trigger *at least* 3 flow elements
        projectRule.fixture.editor.moveCaretToEnd()
        projectRule.fixture.editor.executeAndSave {
          replaceText("@Preview(name = \"B\")", "@Preview(name = \"B\")\n@Preview(name = \"C\")")
        }
      }

      assertEquals(
        "Preview1 - A,Preview1 - B,Preview1 - C",
        flow
          .map { it.asCollection() }
          .filter { it.size == 3 }
          .first()
          .joinToString(",") { it.displaySettings.name },
      )

      // Terminate the flow
      flowScope.cancel()
    }
  }
}
