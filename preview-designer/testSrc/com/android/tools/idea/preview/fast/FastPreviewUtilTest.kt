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
package com.android.tools.idea.preview.fast

import com.android.tools.compile.fast.CompilationResult
import com.android.tools.idea.concurrency.AndroidDispatchers.workerThread
import com.android.tools.idea.editors.fast.BlockingDaemonClient
import com.android.tools.idea.editors.fast.FastPreviewManager
import com.android.tools.idea.run.deployment.liveedit.setUpComposeInProjectFixture
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiFile
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.idea.util.projectStructure.module
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

class FastPreviewUtilTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  private lateinit var testFile: PsiFile

  @Before
  fun setUp() {
    testFile =
      projectRule.fixture.addFileToProject(
        "src/Test.kt",
        """
      fun testA() {
      }

      fun testB() {
        testA()
      }
    """
          .trimIndent(),
      )
  }

  @Test
  fun `fast compile call`() {
    setUpComposeInProjectFixture(projectRule)
    runBlocking(workerThread) {
      val (result, _) =
        fastCompile(
          projectRule.testRootDisposable,
          runReadAction { testFile.module }!!,
          setOf(testFile),
        )
      assertEquals(CompilationResult.Success, result)
    }
  }

  @Test
  fun `fast compile call cancellation`() {
    val blockingDaemon = BlockingDaemonClient()
    val testPreviewManager =
      FastPreviewManager.getTestInstance(projectRule.project, { _, _, _, _ -> blockingDaemon })
        .also { Disposer.register(projectRule.fixture.testRootDisposable, it) }

    val launchedCompileRequests = AtomicInteger(0)
    runBlocking {
      // Launch and cancel the 50 calls. Verify that they are cancelled correctly.
      repeat(50) {
        val job =
          launch(workerThread) {
            try {
              val (result, _) =
                fastCompile(
                  projectRule.testRootDisposable,
                  runReadAction { testFile.module }!!,
                  setOf(testFile),
                  testPreviewManager,
                )
              assertTrue(result is CompilationResult.CompilationAborted)
            } catch (_: CancellationException) {}
            launchedCompileRequests.incrementAndGet()
          }
        launch(workerThread) {
          delay(Random.nextLong(100, 1200))
          job.cancel()
        }
      }
    }
    assertEquals(50, launchedCompileRequests.get())
  }
}
