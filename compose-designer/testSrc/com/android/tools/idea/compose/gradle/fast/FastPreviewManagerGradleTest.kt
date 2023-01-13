/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.compose.gradle.fast

import com.android.flags.junit.FlagRule
import com.android.tools.idea.compose.gradle.ComposeGradleProjectRule
import com.android.tools.idea.compose.preview.SIMPLE_COMPOSE_PROJECT_PATH
import com.android.tools.idea.compose.preview.SimpleComposeAppPaths
import com.android.tools.idea.compose.preview.fast.OutOfProcessCompilerDaemonClientImpl
import com.android.tools.idea.compose.preview.renderer.renderPreviewElement
import com.android.tools.idea.compose.preview.util.SingleComposePreviewElementInstance
import com.android.tools.idea.concurrency.AndroidDispatchers.diskIoThread
import com.android.tools.idea.editors.fast.CompilationResult
import com.android.tools.idea.editors.fast.CompilerDaemonClient
import com.android.tools.idea.editors.fast.FastPreviewConfiguration
import com.android.tools.idea.editors.fast.FastPreviewManager
import com.android.tools.idea.editors.fast.toFileNameSet
import com.android.tools.idea.editors.liveedit.LiveEditAdvancedConfiguration
import com.android.tools.idea.editors.liveedit.LiveEditApplicationConfiguration
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.run.deployment.liveedit.LiveEditCompiler
import com.android.tools.idea.run.deployment.liveedit.LiveEditCompilerInput
import com.android.tools.idea.run.deployment.liveedit.LiveEditCompilerOutput
import com.android.tools.idea.testing.moveCaret
import com.android.tools.idea.testing.replaceText
import com.android.tools.idea.util.toIoFile
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.runInEdtAndWait
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.android.uipreview.ModuleClassLoaderOverlays
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.org.objectweb.asm.ClassReader
import org.jetbrains.org.objectweb.asm.ClassWriter
import org.jetbrains.org.objectweb.asm.util.TraceClassVisitor
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * This factory will instantiate [OutOfProcessCompilerDaemonClientImpl] for the given version. This
 * factory will try to find a daemon for the given specific version or fallback to a stable one if
 * the specific one is not found. For example, for `1.1.0-alpha02`, this factory will try to locate
 * the jar for the daemon `kotlin-compiler-daemon-1.1.0-alpha02.jar`. If not found, it will
 * alternatively try `kotlin-compiler-daemon-1.1.0.jar` since it should be compatible.
 */
private fun defaultDaemonFactory(
  version: String,
  project: Project,
  log: Logger,
  scope: CoroutineScope
): CompilerDaemonClient {
  return OutOfProcessCompilerDaemonClientImpl(version, scope, log)
}

@RunWith(Parameterized::class)
class FastPreviewManagerGradleTest(private val useEmbeddedCompiler: Boolean) {
  companion object {
    @Suppress("unused") // Used by JUnit via reflection
    @JvmStatic
    @get:Parameterized.Parameters(name = "useEmbeddedCompiler = {0}")
    val useEmbeddedCompilerValues = listOf(true) // TODO: add "false" back after compose 1.3 release
  }

  @get:Rule val projectRule = ComposeGradleProjectRule(SIMPLE_COMPOSE_PROJECT_PATH)

  @get:Rule val fastPreviewFlagRule = FlagRule(StudioFlags.COMPOSE_FAST_PREVIEW, true)

  lateinit var psiMainFile: PsiFile
  lateinit var fastPreviewManager: FastPreviewManager

  @Before
  fun setUp() {
    FastPreviewConfiguration.getInstance().isEnabled = true
    val mainFile =
      projectRule.project.guessProjectDir()!!.findFileByRelativePath(
        SimpleComposeAppPaths.APP_MAIN_ACTIVITY.path
      )!!
    psiMainFile = runReadAction { PsiManager.getInstance(projectRule.project).findFile(mainFile)!! }
    fastPreviewManager =
      if (useEmbeddedCompiler) FastPreviewManager.getInstance(projectRule.project)
      else
        FastPreviewManager.getTestInstance(projectRule.project, ::defaultDaemonFactory).also {
          Disposer.register(projectRule.fixture.testRootDisposable, it)
        }
    invokeAndWaitIfNeeded { projectRule.buildAndAssertIsSuccessful() }
    runWriteActionAndWait {
      projectRule.fixture.openFileInEditor(mainFile)
      WriteCommandAction.runWriteCommandAction(projectRule.project) {
        // Delete the reference to PreviewInOtherFile since it's a top level function not supported
        // by the embedded compiler (b/201728545) and it's not used by the tests.
        projectRule.fixture.editor.replaceText("PreviewInOtherFile()", "")
      }
      projectRule.fixture.moveCaret("Text(\"Hello 2\")|")
      projectRule.fixture.type("\n")
    }
    runInEdtAndWait {
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue() // Consume editor events
    }
  }

  @After
  fun tearDown() {
    runBlocking { fastPreviewManager.stopAllDaemons().join() }
    LiveEditApplicationConfiguration.getInstance().resetDefault()
  }

  @Test
  fun testSingleFileCompileSuccessfully() {
    val module = ModuleUtilCore.findModuleForPsiElement(psiMainFile)!!
    typeAndSaveDocument("Text(\"Hello 3\")\n")
    runBlocking {
      val (result, _) = fastPreviewManager.compileRequest(psiMainFile, module)
      assertTrue("Compilation must pass, failed with $result", result == CompilationResult.Success)
    }
  }

  @Test
  fun testFastPreviewDoesNotInlineRIds() {
    // Force the use of final resource ids
    File(projectRule.project.guessProjectDir()!!.toIoFile(), "gradle.properties")
      .appendText("android.nonFinalResIds=false")
    projectRule.requestSyncAndWait()

    val module = ModuleUtilCore.findModuleForPsiElement(psiMainFile)!!
    typeAndSaveDocument("Text(stringResource(R.string.greeting))\n")
    runBlocking {
      val (result, outputPath) = fastPreviewManager.compileRequest(psiMainFile, module)
      assertTrue("Compilation must pass, failed with $result", result == CompilationResult.Success)

      // Decompile the generated Preview code
      val decompiledOutput =
        withContext(diskIoThread) {
          File(outputPath).toPath().toFileNameSet().joinToString("\n") {
            try {
              val reader =
                ClassReader(
                  Files.readAllBytes(Paths.get(outputPath, "google/simpleapplication/$it"))
                )
              val outputTrace = StringWriter()
              val classOutputWriter =
                TraceClassVisitor(ClassWriter(ClassWriter.COMPUTE_MAXS), PrintWriter(outputTrace))
              reader.accept(classOutputWriter, 0)
              outputTrace.toString()
            } catch (t: Throwable) {
              ""
            }
          }
        }

      val containsStaticFinalAccess =
        decompiledOutput.lines().any {
          it.contains("GETSTATIC google/simpleapplication/R\$string.greeting : I")
        }

      assertTrue(
        "Fast Preview should not inline R values when compiling",
        containsStaticFinalAccess
      )
    }
  }

  @Test
  fun testDaemonIsRestartedAutomatically() {
    val module = ModuleUtilCore.findModuleForPsiElement(psiMainFile)!!
    typeAndSaveDocument("Text(\"Hello 3\")\n")
    runBlocking {
      val (result, _) = fastPreviewManager.compileRequest(psiMainFile, module)
      assertTrue("Compilation must pass, failed with $result", result == CompilationResult.Success)
      fastPreviewManager.stopAllDaemons().join()
    }
    runBlocking {
      val (result, _) = fastPreviewManager.compileRequest(psiMainFile, module)
      assertTrue("Compilation must pass, failed with $result", result == CompilationResult.Success)
    }
  }

  @Test
  fun testFastPreviewEditChangeRender() {
    val previewElement =
      SingleComposePreviewElementInstance.forTesting(
        "google.simpleapplication.MainActivityKt.TwoElementsPreview"
      )
    val initialState =
      renderPreviewElement(projectRule.androidFacet(":app"), previewElement).get()!!

    val module = ModuleUtilCore.findModuleForPsiElement(psiMainFile)!!
    typeAndSaveDocument("Text(\"Hello 3\")\n")
    runBlocking {
      val (result, outputPath) = fastPreviewManager.compileRequest(psiMainFile, module)
      assertTrue("Compilation must pass, failed with $result", result == CompilationResult.Success)
      ModuleClassLoaderOverlays.getInstance(module).pushOverlayPath(File(outputPath).toPath())
    }
    val finalState = renderPreviewElement(projectRule.androidFacet(":app"), previewElement).get()!!
    assertTrue(
      "Resulting image is expected to be at least 20% higher since a new text line was added",
      finalState.height > initialState.height * 1.20
    )
  }

  @Test
  fun testMultipleFilesCompileSuccessfully() {
    val module = ModuleUtilCore.findModuleForPsiElement(psiMainFile)!!
    val psiSecondFile = runReadAction {
      val vFile =
        projectRule.project.guessProjectDir()!!.findFileByRelativePath(
          "app/src/main/java/google/simpleapplication/OtherPreviews.kt"
        )!!
      PsiManager.getInstance(projectRule.project).findFile(vFile)!!
    }
    runBlocking {
      val (result, outputPath) =
        fastPreviewManager.compileRequest(listOf(psiMainFile, psiSecondFile), module)
      assertTrue("Compilation must pass, failed with $result", result == CompilationResult.Success)
      val generatedFilesSet =
        withContext(diskIoThread) { File(outputPath).toPath().toFileNameSet() }
      assertTrue(generatedFilesSet.contains("OtherPreviewsKt.class"))
    }
  }

  // Regression test for b/228168101
  @Test
  fun `test parallel compilations`() {
    // This tests is only to verify the interaction of the internal compiler with the Live Edit
    // on device compilation.
    if (!useEmbeddedCompiler) return

    var compile = true
    val startCountDownLatch = CountDownLatch(1)

    val previewCompilations = AtomicLong(0)
    val previewThread = thread {
      startCountDownLatch.await()
      while (compile) {
        val module = ModuleUtilCore.findModuleForPsiElement(psiMainFile)!!
        typeAndSaveDocument("Text(\"Hello 3\")\n")
        runBlocking { fastPreviewManager.compileRequest(psiMainFile, module) }
        previewCompilations.incrementAndGet()
      }
    }

    val deviceCompilations = AtomicLong(0)
    val deviceThread = thread {
      val output = mutableListOf<LiveEditCompilerOutput>()
      val function = runReadAction {
        psiMainFile.collectDescendantsOfType<KtNamedFunction>().first {
          it.name?.contains("TwoElementsPreview") ?: false
        }
      }

      startCountDownLatch.await()
      while (compile) {
        LiveEditCompiler(projectRule.project)
          .compile(listOf(LiveEditCompilerInput(psiMainFile, function)), output)
        deviceCompilations.incrementAndGet()
      }
    }

    val iterations = 20L

    // Start both threads.
    startCountDownLatch.countDown()

    // Wait for both threads to run the iterations.
    runBlocking {
      while (deviceCompilations.get() < iterations || previewCompilations.get() < iterations) delay(
        200
      )
      compile = false
    }

    previewThread.join()
    deviceThread.join()
  }

  @Test
  fun testInlineMethodsCompileSuccessfully() {
    val originalUseInlineAnalysis = LiveEditAdvancedConfiguration.getInstance().useInlineAnalysis
    LiveEditAdvancedConfiguration.getInstance().useInlineAnalysis = true
    try {
      val module = ModuleUtilCore.findModuleForPsiElement(psiMainFile)!!
      typeAndSaveDocument("inlineCall()\n")
      runWriteActionAndWait { projectRule.fixture.moveCaret("|@Preview") }
      runInEdtAndWait {
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue() // Consume editor events
      }
      typeAndSaveDocument("inline fun inlineCall() {}\n\n")

      runBlocking {
        val (result, _) = fastPreviewManager.compileRequest(psiMainFile, module)
        assertTrue(
          "Compilation must pass, failed with $result",
          result == CompilationResult.Success
        )
      }
    } finally {
      LiveEditAdvancedConfiguration.getInstance().useInlineAnalysis = originalUseInlineAnalysis
    }
  }

  private fun typeAndSaveDocument(typedString: String) {
    runWriteActionAndWait {
      projectRule.fixture.type(typedString)
      PsiDocumentManager.getInstance(projectRule.project).commitAllDocuments()
      FileDocumentManager.getInstance().saveAllDocuments()
    }
    runInEdtAndWait {
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue() // Consume editor events
    }
  }
}
