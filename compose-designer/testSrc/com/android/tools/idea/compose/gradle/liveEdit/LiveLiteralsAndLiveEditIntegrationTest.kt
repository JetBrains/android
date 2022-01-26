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
package com.android.tools.idea.compose.gradle.liveEdit

import com.android.flags.junit.SetFlagRule
import com.android.tools.idea.compose.gradle.ComposeGradleProjectRule
import com.android.tools.idea.compose.preview.SIMPLE_COMPOSE_PROJECT_PATH
import com.android.tools.idea.compose.preview.SimpleComposeAppPaths
import com.android.tools.idea.compose.preview.liveEdit.CompilationResult
import com.android.tools.idea.compose.preview.liveEdit.PreviewLiveEditManager
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.editors.literals.LiveLiteralsApplicationConfiguration
import com.android.tools.idea.editors.literals.LiveLiteralsMonitorHandler
import com.android.tools.idea.editors.literals.LiveLiteralsService
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.moveCaret
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.android.uipreview.ModuleClassLoaderOverlays
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class LiveLiteralsAndLiveEditIntegrationTest {
  @get:Rule
  val projectRule = ComposeGradleProjectRule(SIMPLE_COMPOSE_PROJECT_PATH)

  @get:Rule
  val liveEditFlagRule = SetFlagRule(StudioFlags.COMPOSE_LIVE_EDIT_PREVIEW, true)

  @get:Rule
  val liveLiteralsFlagRule = SetFlagRule(StudioFlags.COMPOSE_LIVE_LITERALS, true)

  lateinit var psiMainFile: PsiFile
  lateinit var liveEditManager: PreviewLiveEditManager

  @Before
  fun setUp() {
    LiveLiteralsApplicationConfiguration.getInstance().isEnabled = true
    val mainFile = projectRule.project.guessProjectDir()!!
      .findFileByRelativePath(SimpleComposeAppPaths.APP_MAIN_ACTIVITY.path)!!
    psiMainFile = runReadAction { PsiManager.getInstance(projectRule.project).findFile(mainFile)!! }
    liveEditManager = PreviewLiveEditManager.getInstance(projectRule.project)
    invokeAndWaitIfNeeded {
      assertTrue(projectRule.build().isBuildSuccessful)
    }
  }

  @After
  fun tearDown() {
    runBlocking {
      liveEditManager.stopAllDaemons().join()
    }
  }

  /**
   * Runs [runnable] and ensures that a document was added to the tracking of the [liveLiteralsService] after the call.
   */
  private fun runAndWaitForDocumentAdded(liveLiteralsService: LiveLiteralsService, runnable: () -> Unit) {
    val documentAdded = CountDownLatch(1)
    val disposable = Disposer.newDisposable(projectRule.fixture.testRootDisposable, "DocumentAddDiposable")
    DumbService.getInstance(projectRule.project).waitForSmartMode()
    try {
      liveLiteralsService.addOnDocumentsUpdatedListener(disposable) {
        documentAdded.countDown()
      }
      runnable()
      // We wait for a maximum of 20 seconds. Finding literals runs in a non-blocking action. During tests
      // indexing can be triggered which can delay the test for a few seconds. Reducing this number will cause
      // the test to become flaky because of some cases where indexing interferes with the test.
      documentAdded.await(20, TimeUnit.SECONDS)
    }
    finally {
      Disposer.dispose(disposable) // Remove listener
    }
  }

  private suspend fun compileAndListOutputFiles() = withContext(AndroidDispatchers.ioThread) {
    val module = ModuleUtilCore.findModuleForPsiElement(psiMainFile)!!
    liveEditManager.compileRequest(psiMainFile, module).let { (result, outputPath) ->
      assertEquals(CompilationResult.Success, result)
      ModuleClassLoaderOverlays.getInstance(module).overlayPath = File(outputPath).toPath()

      val generatedFilesSet = mutableSetOf<String>()
      @Suppress("BlockingMethodInNonBlockingContext")
      Files.walkFileTree(File(outputPath).toPath(), object : SimpleFileVisitor<Path>() {
        override fun visitFile(file: Path?, attrs: BasicFileAttributes?): FileVisitResult {
          file?.let { generatedFilesSet.add(it.fileName.toString()) }
          @Suppress("BlockingMethodInNonBlockingContext")
          return super.visitFile(file, attrs)
        }
      })
      result to generatedFilesSet
    }
  }

  @Test
  fun `verify literals in overlay file`() = runBlocking {
    withContext(uiThread) {
      projectRule.fixture.openFileInEditor(psiMainFile.virtualFile)
    }
    val liveLiteralsService = LiveLiteralsService.getInstance(projectRule.project)
    val (_, outputFiles) = compileAndListOutputFiles()
    assertTrue(outputFiles.any { it.contains("LiveLiterals") })
    assertTrue(liveLiteralsService.allConstants().isEmpty())
    assertFalse(liveLiteralsService.isAvailable)
    runAndWaitForDocumentAdded(liveLiteralsService) {
      liveLiteralsService.liveLiteralsMonitorStarted("TestDevice", LiveLiteralsMonitorHandler.DeviceType.PREVIEW)
    }
    assertTrue(liveLiteralsService.isAvailable)
    assertEquals(6, liveLiteralsService.allConstants().size)

    liveLiteralsService.liveLiteralsMonitorStopped("TestDevice")

    runWriteActionAndWait {
      projectRule.fixture.moveCaret("Text(\"Hello 2\")|")
      // Add a new literal
      projectRule.fixture.type("\nText(\"Hello 3\")|")
    }
    compileAndListOutputFiles()
    runAndWaitForDocumentAdded(liveLiteralsService) {
      liveLiteralsService.liveLiteralsMonitorStarted("TestDevice", LiveLiteralsMonitorHandler.DeviceType.PREVIEW)
    }
    assertEquals(7, liveLiteralsService.allConstants().size)
  }

  @Test
  fun `disabled live literals does not generate LiveLiterals classes`() = runBlocking {
    withContext(uiThread) {
      projectRule.fixture.openFileInEditor(psiMainFile.virtualFile)
    }
    LiveLiteralsApplicationConfiguration.getInstance().isEnabled = false
    val (_, outputFiles) = compileAndListOutputFiles()
    assertTrue(outputFiles.isNotEmpty() && outputFiles.none { it.contains("LiveLiterals") })
  }
}