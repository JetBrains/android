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
package com.android.tools.idea.editors.fast

import com.android.tools.compile.fast.CompilationResult
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.rendering.BuildTargetReference
import com.android.tools.idea.rendering.tokens.BuildSystemFilePreviewServices.Companion.getBuildSystemFilePreviewServices
import com.android.tools.idea.rendering.tokens.FakeBuildSystemFilePreviewServices
import com.android.tools.idea.run.deployment.liveedit.tokens.ApplicationLiveEditServices
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.mock.MockPsiFile
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiFile
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.replaceService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

private const val TEST_VERSION_STRING = "0.0.1-test"

private object NopCompilerDaemonClient : CompilerDaemonClient {
  override val isRunning: Boolean = true
  override suspend fun compileRequest(
    applicationLiveEditServices: ApplicationLiveEditServices,
    files: Collection<PsiFile>,
    contextBuildTargetReference: BuildTargetReference,
    outputDirectory: Path,
    indicator: ProgressIndicator
  ): CompilationResult = CompilationResult.Success
  override fun dispose() {}
}

fun nopCompileDaemonFactory(
  onCalled: (String) -> Unit
): (String, Project, Logger, CoroutineScope) -> CompilerDaemonClient {
  return { version, _, _, _ ->
    onCalled(version)
    NopCompilerDaemonClient
  }
}

internal class FastPreviewManagerTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()
  private val project: Project
    get() = projectRule.project

  private val testTracker = TestFastPreviewTrackerManager(showTimes = false)

  @Before
  fun setUp() {
    projectRule.project.replaceService(
      FastPreviewTrackerManager::class.java,
      testTracker,
      projectRule.testRootDisposable
    )
    FakeBuildSystemFilePreviewServices(versionString = TEST_VERSION_STRING)
      .register(projectRule.testRootDisposable)
  }

  @Test
  fun `pre-start daemon`() {
    val createdVersions = mutableListOf<String>()
    val latch = CountDownLatch(1)
    val manager =
      FastPreviewManager.getTestInstance(
          project,
          nopCompileDaemonFactory { createdVersions.add(it) },
        )
        .also { Disposer.register(projectRule.testRootDisposable, it) }
    assertTrue(createdVersions.isEmpty())
    val buildTargets = project.getProjectSystem().getBuildSystemFilePreviewServices().buildTargets
    manager.preStartDaemon(buildTargets.from(projectRule.module, LightVirtualFile()))
    latch.await(1, TimeUnit.SECONDS)
    assertEquals("0.0.1-test", createdVersions.single())
  }

  @Test
  fun `request starts daemon`() = runBlocking {
    val file =
      projectRule.fixture.addFileToProject(
        "test.kt",
        """
      fun empty() {}
    """
          .trimIndent()
      )
    val createdVersions = mutableListOf<String>()
    val manager =
      FastPreviewManager.getTestInstance(
          project,
          nopCompileDaemonFactory { createdVersions.add(it) },
       )
        .also { Disposer.register(projectRule.testRootDisposable, it) }
    assertTrue(createdVersions.isEmpty())
    // Start 10 requests to ensure only one daemon is started
    coroutineScope { repeat(10) { launch { manager.compileRequest(file, BuildTargetReference.from(file)!!) } } }
    assertEquals("0.0.1-test", createdVersions.single())
  }

  @Test
  fun `identical requests only trigger 1 build`() {
    val file =
      projectRule.fixture.addFileToProject(
        "test.kt",
        """
      fun empty() {}
    """
          .trimIndent()
      )
    val scope = AndroidCoroutineScope(projectRule.testRootDisposable)
    val blockingDaemon = BlockingDaemonClient()
    val manager =
      FastPreviewManager.getTestInstance(
          project,
          daemonFactory = { _, _, _, _ -> blockingDaemon },
        )
        .also { Disposer.register(projectRule.testRootDisposable, it) }

    // Check that the same requests does not trigger more than one compilation
    val latch = CountDownLatch(10)
    repeat(10) {
      scope.launch {
        manager.compileRequest(file, BuildTargetReference.from(file)!!)
        latch.countDown()
      }
    }
    blockingDaemon.completeOneRequest()

    latch.await() // Wait for the 10 requests to complete
    assertEquals(
      "Only one compilation was expected for the 10 identical requests",
      1,
      blockingDaemon.requestReceived
    )
    assertEquals(
      """
        compilationSucceeded (compiledFiles=1)
     """
        .trimIndent(),
      testTracker.logOutput()
    )
  }

  @Test
  fun `disabled request cache creates new compilations for every request`() {
    val file =
      projectRule.fixture.addFileToProject(
        "test.kt",
        """
      fun empty() {}
    """
          .trimIndent()
      )
    val scope = AndroidCoroutineScope(projectRule.testRootDisposable)
    val blockingDaemon = BlockingDaemonClient()
    val manager =
      FastPreviewManager.getTestInstance(
          project,
          daemonFactory = { _, _, _, _ -> blockingDaemon },
          maxCachedRequests = 0
        )
        .also { Disposer.register(projectRule.testRootDisposable, it) }

    // Check that the same requests does not trigger more than one compilation
    val latch = CountDownLatch(10)
    repeat(10) {
      scope.launch {
        manager.compileRequest(file, BuildTargetReference.from(file)!!)
        latch.countDown()
      }
    }
    repeat(10) { blockingDaemon.completeOneRequest() }

    latch.await() // Wait for the 10 requests to complete
    assertEquals(
      "10 requests should have triggered 10 compilations",
      10,
      blockingDaemon.requestReceived
    )
  }

  @Test
  fun `request caches does not trigger repeated builds`() {
    val actualFile =
      projectRule.fixture.addFileToProject(
        "test.kt",
        """
      fun empty() {}
    """
          .trimIndent()
      )
    var modificationCount = 0L

    // Mock file to use so we control when it is signal as "modified".
    val mockFile =
      object : MockPsiFile(actualFile.virtualFile, actualFile.manager) {
        override fun getModificationStamp(): Long = modificationCount
      }
    val blockingDaemon = BlockingDaemonClient()
    val manager =
      FastPreviewManager.getTestInstance(
          project,
          daemonFactory = { _, _, _, _ -> blockingDaemon },
        )
        .also { Disposer.register(projectRule.testRootDisposable, it) }
    val scope = AndroidCoroutineScope(projectRule.testRootDisposable)

    val latch = CountDownLatch(10)
    scope.launch {
      repeat(10) {
        if (it % 2 == 0) { // Only change the file 5 times
          modificationCount++
        }
        manager.compileRequest(mockFile, BuildTargetReference.from(mockFile)!!)

        latch.countDown()
      }
    }

    repeat(5) { blockingDaemon.completeOneRequest() }

    latch.await() // Wait for the 10 requests to complete
    assertEquals("Only 5 requests were expected to be different", 5, blockingDaemon.requestReceived)
  }

  @Test
  fun `verify compiler request`() = runBlocking {
    val file =
      projectRule.fixture.addFileToProject(
        "test.kt",
        """
      fun empty() {}
    """
          .trimIndent()
      )
    val compilationRequests = mutableListOf<List<String>>()
    val manager =
      FastPreviewManager.getTestInstance(
          project,
          daemonFactory = { _, _, _, _ ->
            object : CompilerDaemonClient by NopCompilerDaemonClient {
              override suspend fun compileRequest(
                applicationLiveEditServices: ApplicationLiveEditServices,
                files: Collection<PsiFile>,
                contextBuildTargetReference: BuildTargetReference,
                outputDirectory: Path,
                indicator: ProgressIndicator
              ): CompilationResult {
                compilationRequests.add(
                  files.map { it.virtualFile.path }.toList() +
                    contextBuildTargetReference.module.name +
                    listOf(outputDirectory.toString())
                )
                return CompilationResult.Success
              }
            }
          },
        )
        .also { Disposer.register(projectRule.testRootDisposable, it) }
    assertTrue(compilationRequests.isEmpty())
    assertTrue(manager.compileRequest(file, BuildTargetReference.from(file)!!).first == CompilationResult.Success)
    run {
      val requestParameters =
        compilationRequests
          .single()
          .joinToString("\n")
          .replace(Regex("\n.*overlay\\d+"), "\n/tmp/overlay0") // Overlay directories are random
      assertEquals(
        """
      /src/test.kt
      light_idea_test_case
      /tmp/overlay0
    """
          .trimIndent(),
        requestParameters
      )
    }

    // Check, disabling Live Literals disables the compiler flag.
    run {
      compilationRequests.clear()
      FastPreviewConfiguration.getInstance().isEnabled = false
      try {
        val file2 =
          projectRule.fixture.addFileToProject(
            "testB.kt",
            """
          fun emptyB() {}
        """
              .trimIndent()
          )
        assertTrue(
          manager.compileRequest(listOf(file2), BuildTargetReference.from(file2)!!).first ==
            CompilationResult.Success
        )
        val requestParameters =
          compilationRequests
            .single()
            .joinToString("\n")
            .replace(Regex("\n.*overlay\\d+"), "\n/tmp/overlay0") // Overlay directories are random
        assertEquals(
          """
        /src/testB.kt
        light_idea_test_case
        /tmp/overlay0
      """
            .trimIndent(),
          requestParameters
        )
      } finally {
        FastPreviewConfiguration.getInstance().resetDefault()
      }
    }

    run {
      compilationRequests.clear()
      val file2 =
        projectRule.fixture.addFileToProject(
          "testC.kt",
          """
      fun emptyC() {}
    """
            .trimIndent()
        )
      assertTrue(
        manager.compileRequest(listOf(file, file2), BuildTargetReference.from(file /* both from the same module */)!!).first ==
          CompilationResult.Success
      )
      val requestParameters =
        compilationRequests
          .single()
          .joinToString("\n")
          .replace(Regex("\n.*overlay\\d+"), "\n/tmp/overlay0") // Overlay directories are random
      assertEquals(
        """
      /src/test.kt
      /src/testC.kt
      light_idea_test_case
      /tmp/overlay0
    """
          .trimIndent(),
        requestParameters
      )
    }
  }

  @Test
  fun `handle daemon start exception`() = runBlocking {
    val file =
      projectRule.fixture.addFileToProject(
        "test.kt",
        """
      fun empty() {}
    """
          .trimIndent()
      )
    val manager =
      FastPreviewManager.getTestInstance(
          project,
          daemonFactory = { _, _, _, _ -> throw IllegalStateException("Unable to start compiler") },
        )
        .also { Disposer.register(projectRule.testRootDisposable, it) }
    val result = manager.compileRequest(file, BuildTargetReference.from(file)!!).first
    assertTrue(result.toString(), result is CompilationResult.DaemonStartFailure)
  }

  @Test
  fun `handle compile request exception`() = runBlocking {
    val file =
      projectRule.fixture.addFileToProject(
        "test.kt",
        """
      fun empty() {}
    """
          .trimIndent()
      )
    val manager =
      FastPreviewManager.getTestInstance(
          project,
          daemonFactory = { _, _, _, _ ->
            object : CompilerDaemonClient by NopCompilerDaemonClient {
              override suspend fun compileRequest(
                applicationLiveEditServices: ApplicationLiveEditServices,
                files: Collection<PsiFile>,
                contextBuildTargetReference: BuildTargetReference,
                outputDirectory: Path,
                indicator: ProgressIndicator
              ): CompilationResult {
                throw IllegalStateException("Unable to process request")
              }
            }
          },
        )
        .also { Disposer.register(projectRule.testRootDisposable, it) }
    val result = manager.compileRequest(file, BuildTargetReference.from(file)!!).first
    assertTrue(result.toString(), result is CompilationResult.RequestException)
  }

  @Test
  fun `handle compile failure`() = runBlocking {
    val file =
      projectRule.fixture.addFileToProject(
        "test.kt",
        """
      fun empty() {}
    """
          .trimIndent()
      )
    val manager =
      FastPreviewManager.getTestInstance(
          project,
          daemonFactory = { _, _, _, _ ->
            object : CompilerDaemonClient by NopCompilerDaemonClient {
              override suspend fun compileRequest(
                applicationLiveEditServices: ApplicationLiveEditServices,
                files: Collection<PsiFile>,
                contextBuildTargetReference: BuildTargetReference,
                outputDirectory: Path,
                indicator: ProgressIndicator
              ): CompilationResult = CompilationResult.DaemonError(-1)
            }
          },
        )
        .also { Disposer.register(projectRule.testRootDisposable, it) }
    val result = manager.compileRequest(file, BuildTargetReference.from(file)!!).first
    assertTrue(result.toString(), result is CompilationResult.DaemonError)
  }

  // Regression test for http://b/222838793
  @Test
  fun `verify listener parent disposable`() {
    val manager =
      FastPreviewManager.getTestInstance(
          project,
          daemonFactory = { _, _, _, _ -> NopCompilerDaemonClient },
        )
        .also { Disposer.register(projectRule.testRootDisposable, it) }
    val parentDisposable = Disposer.newDisposable()
    manager.addListener(
      parentDisposable,
      object : FastPreviewManager.Companion.FastPreviewManagerListener {
        override fun onCompilationStarted(files: Collection<PsiFile>) {}
        override fun onCompilationComplete(result: CompilationResult, files: Collection<PsiFile>) {}
      }
    )
    Disposer.dispose(parentDisposable)
    assertFalse(manager.isDisposed)
  }

  @Test
  fun `compiling state is true while processing a request`() {
    val file =
      projectRule.fixture.addFileToProject(
        "test.kt",
        """
      fun empty() {}
    """
          .trimIndent()
      )
    val scope = AndroidCoroutineScope(projectRule.testRootDisposable)
    val blockingDaemon = BlockingDaemonClient()
    val manager =
      FastPreviewManager.getTestInstance(
          project,
          daemonFactory = { _, _, _, _ -> blockingDaemon },
          maxCachedRequests = 0
        )
        .also { Disposer.register(projectRule.testRootDisposable, it) }

    val compilationComplete = CompletableDeferred<Unit>()
    assertFalse(manager.isCompiling)
    scope.launch {
      manager.compileRequest(file, BuildTargetReference.from(file)!!)
      compilationComplete.complete(Unit)
    }
    runBlocking {
      blockingDaemon.firstRequestReceived.await()
      assertTrue(manager.isCompiling)
      blockingDaemon.completeOneRequest()
      compilationComplete.await()
    }
    assertFalse(manager.isCompiling)
  }

  @Test
  fun `timeouts must call compilation listener`() {
    val file =
      projectRule.fixture.addFileToProject(
        "test.kt",
        """
      fun empty() {}
    """
          .trimIndent()
      )
    val scope = AndroidCoroutineScope(projectRule.testRootDisposable)
    val timeoutDaemon =
      object : CompilerDaemonClient {
        override val isRunning: Boolean
          get() = false

        override suspend fun compileRequest(
          applicationLiveEditServices: ApplicationLiveEditServices,
          files: Collection<PsiFile>,
          contextBuildTargetReference: BuildTargetReference,
          outputDirectory: Path,
          indicator: ProgressIndicator
        ): CompilationResult {
          throw ProcessCanceledException()
        }

        override fun dispose() {}
      }
    val manager =
      FastPreviewManager.getTestInstance(
          project,
          daemonFactory = { _, _, _, _ -> timeoutDaemon },
          maxCachedRequests = 0
        )
        .also { Disposer.register(projectRule.testRootDisposable, it) }

    val compilationCounter = AtomicInteger(0)
    manager.addListener(
      projectRule.testRootDisposable,
      object : FastPreviewManager.Companion.FastPreviewManagerListener {
        override fun onCompilationStarted(files: Collection<PsiFile>) {
          compilationCounter.incrementAndGet()
        }

        override fun onCompilationComplete(result: CompilationResult, files: Collection<PsiFile>) {
          compilationCounter.decrementAndGet()
        }
      }
    )

    repeat(3) {
      val compilationComplete = CompletableDeferred<Unit>()
      assertFalse(manager.isCompiling)
      scope.launch {
        manager.compileRequest(file, BuildTargetReference.from(file)!!)
        compilationComplete.complete(Unit)
      }
      runBlocking { compilationComplete.await() }
    }
    assertEquals(0, compilationCounter.get())
  }
}
