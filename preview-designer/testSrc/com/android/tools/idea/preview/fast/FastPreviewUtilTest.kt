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
import com.android.tools.idea.concurrency.UniqueTaskCoroutineLauncher
import com.android.tools.idea.editors.fast.BlockingDaemonClient
import com.android.tools.idea.editors.fast.FastPreviewManager
import com.android.tools.idea.preview.mvvm.PreviewViewModelStatus
import com.android.tools.idea.rendering.BuildTargetReference
import com.android.tools.idea.rendering.tokens.FakeBuildSystemFilePreviewServices
import com.android.tools.idea.run.deployment.liveedit.setUpComposeInProjectFixture
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.moveCaret
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.ide.DataManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiFile
import com.intellij.refactoring.actions.RenameElementAction
import com.intellij.testFramework.TestActionEvent
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Files
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.Path
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.kotlin.idea.util.projectStructure.module
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.util.TraceClassVisitor

class FastPreviewUtilTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory().withKotlin()

  private val fixture
    get() = projectRule.fixture

  private lateinit var testFile: PsiFile

  @Before
  fun setUp() {
    testFile =
      fixture.addFileToProject(
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
    FakeBuildSystemFilePreviewServices().register(projectRule.testRootDisposable)
    setUpComposeInProjectFixture(projectRule)
    runBlocking(workerThread) {
      val (result, _) =
        fastCompile(
          projectRule.testRootDisposable,
          runReadAction { BuildTargetReference.gradleOnly(testFile.module!!) },
          setOf(testFile),
        )
      assertEquals(CompilationResult.Success, result)
    }
  }

  @Test
  fun `fast compile call cancellation`() {
    val buildTargetReference = runReadAction { BuildTargetReference.from(testFile)!! }
    val blockingDaemon = BlockingDaemonClient()
    val testPreviewManager =
      FastPreviewManager.getTestInstance(projectRule.project, { _, _, _, _ -> blockingDaemon })
        .also { Disposer.register(fixture.testRootDisposable, it) }

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
                  buildTargetReference,
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

  @Test
  fun `in-place rename blocks compilation`() = runBlocking {
    FakeBuildSystemFilePreviewServices().register(projectRule.testRootDisposable)
    setUpComposeInProjectFixture(projectRule)
    TemplateManagerImpl.setTemplateTesting(projectRule.testRootDisposable)

    withContext(Dispatchers.EDT) {
      fixture.configureFromExistingVirtualFile(testFile.virtualFile)
      fixture.moveCaret("test|A()")
      val fakeEvent =
        TestActionEvent.createTestEvent(
          DataManager.getInstance().getDataContext(fixture.editor.component)
        )
      RenameElementAction().actionPerformed(fakeEvent)
    }

    val uniqueCoroutineLauncher = UniqueTaskCoroutineLauncher(this, "FastPreviewLauncher")
    val testPreviewViewModelStatus =
      object : PreviewViewModelStatus {
        override var isRefreshing: Boolean = false
        override var hasRenderErrors: Boolean = false
        override var hasSyntaxErrors: Boolean = false
        override var isOutOfDate: Boolean = false
        override val areResourcesOutOfDate: Boolean = false
        override var previewedFile: PsiFile? = null
      }
    val result =
      requestFastPreviewRefreshAndTrack(
        projectRule.testRootDisposable,
        runReadAction { BuildTargetReference.gradleOnly(testFile.module!!) },
        setOf(testFile),
        testPreviewViewModelStatus,
        uniqueCoroutineLauncher,
        {},
      )
    assertEquals(
      "Renaming in progress",
      (result as CompilationResult.CompilationAborted).e!!.message,
    )
  }

  @Test
  fun `source information is generated by the compose compiler`() {
    val composeFile =
      fixture.addFileToProject(
        "src/Composables.kt",
        """
          import androidx.compose.runtime.Composable

          @Composable
          fun ComposableA() {
          }

          @Composable
          fun ComposableB() {
            ComposableA()
          }
        """
          .trimIndent(),
      )
    FakeBuildSystemFilePreviewServices().register(projectRule.testRootDisposable)
    setUpComposeInProjectFixture(projectRule)
    runBlocking {
      val (result, output) =
        fastCompile(
          projectRule.testRootDisposable,
          runReadAction { BuildTargetReference.gradleOnly(composeFile.module!!) },
          setOf(composeFile),
        )
      assertEquals(CompilationResult.Success, result)

      val outputTrace = StringWriter()
      ClassReader(Files.readAllBytes(Path(output).resolve("ComposablesKt.class")))
        .accept(
          TraceClassVisitor(ClassWriter(ClassWriter.COMPUTE_MAXS), PrintWriter(outputTrace)),
          ClassReader.EXPAND_FRAMES,
        )
      assertTrue(
        "Source information not generated by the Compose compiler:\n$outputTrace",
        outputTrace.toString().lines().any {
          it.contains("androidx/compose/runtime/ComposerKt.sourceInformation")
        },
      )
    }
  }
}
