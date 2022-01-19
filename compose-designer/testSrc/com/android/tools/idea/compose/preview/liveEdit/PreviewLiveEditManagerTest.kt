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
package com.android.tools.idea.compose.preview.liveEdit

import com.android.flags.junit.SetFlagRule
import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers.workerThread
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.mock.MockPsiFile
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiManager
import com.intellij.testFramework.PlatformTestUtil
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

private val TEST_VERSION = GradleVersion.parse("0.0.1-test")

private object NopCompilerDaemonClient : CompilerDaemonClient {
  override val isRunning: Boolean = true
  override suspend fun compileRequest(args: List<String>): CompilationResult = CompilationResult.Success
  override fun dispose() {}
}

/*
 * A CompilerDaemonClient that blocks until [complete] is called.
 */
private class BlockingDaemonClient : CompilerDaemonClient {
  override val isRunning: Boolean = true
  private val compilationRequestFuture = CompletableDeferred<Unit>()
  private val _requestReceived = AtomicLong(0)
  val requestReceived: Long
    get() = _requestReceived.get()

  override suspend fun compileRequest(args: List<String>): CompilationResult {
    _requestReceived.incrementAndGet()
    compilationRequestFuture.await()
    return CompilationResult.Success
  }

  fun complete() {
    compilationRequestFuture.complete(Unit)
  }

  override fun dispose() {}
}

fun nopCompileDaemonFactory(onCalled: (String) -> Unit): (String) -> CompilerDaemonClient {
  return {
    onCalled(it)
    NopCompilerDaemonClient
  }
}

internal class PreviewLiveEditManagerTest {
  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()
  private val project: Project
    get() = projectRule.project

  @get:Rule
  val flagRule = SetFlagRule(StudioFlags.COMPOSE_LIVE_EDIT_PREVIEW, true)

  @Test
  fun `pre-start daemon`() {
    val createdVersions = mutableListOf<String>()
    val latch = CountDownLatch(1)
    val manager = PreviewLiveEditManager.getTestInstance(project, nopCompileDaemonFactory {
      createdVersions.add(it)
    }, moduleRuntimeVersionLocator = { TEST_VERSION }).also {
      Disposer.register(projectRule.testRootDisposable, it)
    }
    assertTrue(createdVersions.isEmpty())
    manager.preStartDaemon(projectRule.module)
    latch.await(1, TimeUnit.SECONDS)
    assertEquals("0.0.1-test", createdVersions.single())
  }

  @Test
  fun `request starts daemon`() = runBlocking {
    val file = projectRule.fixture.addFileToProject("test.kt", """
      fun empty() {}
    """.trimIndent())
    val createdVersions = mutableListOf<String>()
    val manager = PreviewLiveEditManager.getTestInstance(project, nopCompileDaemonFactory {
      createdVersions.add(it)
    }, moduleRuntimeVersionLocator = { TEST_VERSION }).also {
      Disposer.register(projectRule.testRootDisposable, it)
    }
    assertTrue(createdVersions.isEmpty())
    // Start 10 requests to ensure only one daemon is started
    coroutineScope {
      repeat(10) {
        launch {
          manager.compileRequest(file, projectRule.module)
        }
      }
    }
    assertEquals("0.0.1-test", createdVersions.single())
  }

  @Test
  fun `identical requests only trigger 1 build`() {
    val file = projectRule.fixture.addFileToProject("test.kt", """
      fun empty() {}
    """.trimIndent())
    val scope = AndroidCoroutineScope(projectRule.testRootDisposable)
    val blockingDaemon = BlockingDaemonClient()
    val manager = PreviewLiveEditManager.getTestInstance(project,
                                                         daemonFactory = { blockingDaemon },
                                                         moduleRuntimeVersionLocator = { TEST_VERSION }).also {
      Disposer.register(projectRule.testRootDisposable, it)
    }

    // Check that the same requests does not trigger more than one compilation
    val latch = CountDownLatch(10)
    repeat(10) {
      scope.launch {
        manager.compileRequest(file, projectRule.module)
        latch.countDown()
      }
    }
    blockingDaemon.complete()

    latch.await() // Wait for the 10 requests to complete
    assertEquals("Only one compilation was expected for the 10 identical requests", 1, blockingDaemon.requestReceived)
  }

  @Test
  fun `disabled request cache creates new compilations for every request`() {
    val file = projectRule.fixture.addFileToProject("test.kt", """
      fun empty() {}
    """.trimIndent())
    val scope = AndroidCoroutineScope(projectRule.testRootDisposable)
    val blockingDaemon = BlockingDaemonClient()
    val manager = PreviewLiveEditManager.getTestInstance(project,
                                                         daemonFactory = { blockingDaemon },
                                                         moduleRuntimeVersionLocator = { TEST_VERSION },
                                                         maxCachedRequests = 0).also {
      Disposer.register(projectRule.testRootDisposable, it)
    }

    // Check that the same requests does not trigger more than one compilation
    val latch = CountDownLatch(10)
    repeat(10) {
      scope.launch {
        manager.compileRequest(file, projectRule.module)
        latch.countDown()
      }
    }
    blockingDaemon.complete()

    latch.await() // Wait for the 10 requests to complete
    assertEquals("10 requests should have triggered 10 compilations", 10, blockingDaemon.requestReceived)
  }

  @Test
  fun `request caches does not trigger repeated builds`() {
    val actualFile = projectRule.fixture.addFileToProject("test.kt", """
      fun empty() {}
    """.trimIndent())
    var modificationCount = 0L

    // Mock file to use so we control when it is signal as "modified".
    val mockFile = object: MockPsiFile(actualFile.virtualFile, actualFile.manager) {
      override fun getModificationStamp(): Long = modificationCount
    }
    val blockingDaemon = BlockingDaemonClient()
    val manager = PreviewLiveEditManager.getTestInstance(project,
                                                         daemonFactory = { blockingDaemon },
                                                         moduleRuntimeVersionLocator = { TEST_VERSION }).also {
      Disposer.register(projectRule.testRootDisposable, it)
    }
    val scope = AndroidCoroutineScope(projectRule.testRootDisposable)

    val latch = CountDownLatch(10)
    scope.launch {
      repeat(10) {
        if (it % 2 == 0) { // Only change the file 5 times
          modificationCount++
        }
        manager.compileRequest(mockFile, projectRule.module)

        latch.countDown()
      }
    }
    blockingDaemon.complete()

    latch.await() // Wait for the 10 requests to complete
    assertEquals("Only 5 requests were expected to be different", 5, blockingDaemon.requestReceived)
  }

  @Test
  fun `verify compiler request`() = runBlocking {
    val file = projectRule.fixture.addFileToProject("test.kt", """
      fun empty() {}
    """.trimIndent())
    val compilationRequests = mutableListOf<List<String>>()
    val manager = PreviewLiveEditManager.getTestInstance(project, {
      object : CompilerDaemonClient by NopCompilerDaemonClient {
        override suspend fun compileRequest(args: List<String>): CompilationResult {
          compilationRequests.add(args)
          return CompilationResult.Success
        }
      }
    }, moduleClassPathLocator = { listOf("A.jar", "b/c/Test.class") }, moduleRuntimeVersionLocator = { TEST_VERSION }).also {
      Disposer.register(projectRule.testRootDisposable, it)
    }
    assertTrue(compilationRequests.isEmpty())
    assertTrue(manager.compileRequest(file, projectRule.module).first == CompilationResult.Success)
    run {
      val requestParameters = compilationRequests.single().joinToString("\n")
        .replace(Regex("/.*/overlay\\d+"), "/tmp/overlay0") // Overlay directories are random
      assertEquals("""
      -verbose
      -version
      -no-stdlib
      -no-reflect
      -Xdisable-default-scripting-plugin
      -jvm-target
      1.8
      -cp
      A.jar:b/c/Test.class
      -d
      /tmp/overlay0
      /src/test.kt
    """.trimIndent(), requestParameters)
    }

    run {
      compilationRequests.clear()
      val file2 = projectRule.fixture.addFileToProject("testB.kt", """
      fun emptyB() {}
    """.trimIndent())
      assertTrue(manager.compileRequest(listOf(file, file2), projectRule.module).first == CompilationResult.Success)
      val requestParameters = compilationRequests.single().joinToString("\n")
        .replace(Regex("/.*/overlay\\d+"), "/tmp/overlay0") // Overlay directories are random
      assertEquals("""
      -verbose
      -version
      -no-stdlib
      -no-reflect
      -Xdisable-default-scripting-plugin
      -jvm-target
      1.8
      -cp
      A.jar:b/c/Test.class
      -d
      /tmp/overlay0
      /src/test.kt
      /src/testB.kt
    """.trimIndent(), requestParameters)
    }
  }

  @Test
  fun `handle daemon start exception`() = runBlocking {
    val file = projectRule.fixture.addFileToProject("test.kt", """
      fun empty() {}
    """.trimIndent())
    val manager = PreviewLiveEditManager.getTestInstance(project, {
      throw IllegalStateException("Unable to start compiler")
    }, moduleClassPathLocator = { listOf("A.jar", "b/c/Test.class") }, moduleRuntimeVersionLocator = { TEST_VERSION }).also {
      Disposer.register(projectRule.testRootDisposable, it)
    }
    val result = manager.compileRequest(file, projectRule.module).first
    assertTrue(result.toString(), result is CompilationResult.DaemonStartFailure)
  }

  @Test
  fun `handle compile request exception`() = runBlocking {
    val file = projectRule.fixture.addFileToProject("test.kt", """
      fun empty() {}
    """.trimIndent())
    val manager = PreviewLiveEditManager.getTestInstance(project, {
      object : CompilerDaemonClient by NopCompilerDaemonClient {
        override suspend fun compileRequest(args: List<String>): CompilationResult {
          throw IllegalStateException("Unable to process request")
        }
      }
    }, moduleClassPathLocator = { listOf("A.jar", "b/c/Test.class") }, moduleRuntimeVersionLocator = { TEST_VERSION }).also {
      Disposer.register(projectRule.testRootDisposable, it)
    }
    val result = manager.compileRequest(file, projectRule.module).first
    assertTrue(result.toString(), result is CompilationResult.RequestException)
  }
}