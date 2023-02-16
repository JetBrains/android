/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.util

import com.android.tools.idea.compose.ComposeProjectRule
import com.android.tools.idea.testing.executeAndSave
import com.android.tools.idea.testing.insertText
import com.android.tools.idea.testing.moveCaretToEnd
import com.android.tools.idea.ui.ApplicationUtils
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.util.Disposer
import com.intellij.psi.SmartPointerManager
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.Test

class ComposePreviewElementFlowTest {
  @get:Rule
  val projectRule =
    ComposeProjectRule(
      previewAnnotationPackage = "androidx.compose.ui.tooling.preview",
      composableAnnotationPackage = "androidx.compose.runtime"
    )

  @OptIn(ExperimentalTime::class)
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
          .trimIndent()
      )
    val psiFilePointer = runReadAction { SmartPointerManager.createPointer(psiFile) }

    val completed = CompletableDeferred<Unit>()
    val listenersReady = CompletableDeferred<Unit>()
    val testJob = launch {
      val flowForFile =
        Disposer.newDisposable(projectRule.fixture.testRootDisposable, "flowForFile")
      val flow = previewElementFlowForFile(flowForFile, psiFilePointer)
      flow.value.single().let { assertEquals("OtherFileKt.Preview1", it.composableMethodFqn) }

      listenersReady.complete(Unit)

      // We take 2 elements to ensure a change (the first one is the original, the second one the
      // change
      withTimeout(5000) {
        flow.take(2).collect()

        assertEquals(
          """
            OtherFileKt.Preview1
            OtherFileKt.Preview2
          """
            .trimIndent(),
          flow.value.map { it.composableMethodFqn }.sorted().joinToString("\n")
        )

        completed.complete(Unit)
      }
    }

    listenersReady.await()

    // Make change
    ApplicationUtils.invokeWriteActionAndWait(ModalityState.defaultModalityState()) {
      projectRule.fixture.openFileInEditor(psiFile.virtualFile)

      // Make 3 changes that should trigger *at least* 3 flow elements
      projectRule.fixture.editor.moveCaretToEnd()
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
}
