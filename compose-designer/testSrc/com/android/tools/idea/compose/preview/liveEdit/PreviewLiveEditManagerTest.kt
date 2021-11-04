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
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private val TEST_VERSION = GradleVersion.parse("0.0.1-test")

private object NopCompilerDaemonClient: CompilerDaemonClient {
  override val isRunning: Boolean = true
  override suspend fun compileRequest(args: List<String>): CompilationResult = CompilationResult.Success
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
  fun `verify compiler request`() = runBlocking {
    val file = projectRule.fixture.addFileToProject("test.kt", """
      fun empty() {}
    """.trimIndent())
    val compilationRequests = mutableListOf<List<String>>()
    val manager = PreviewLiveEditManager.getTestInstance(project, {
      object: CompilerDaemonClient by NopCompilerDaemonClient {
        override suspend fun compileRequest(args: List<String>): CompilationResult {
          compilationRequests.add(args)
          return CompilationResult.Success
        }
      }
    }, moduleRuntimeVersionLocator = { TEST_VERSION }).also {
      Disposer.register(projectRule.testRootDisposable, it)
    }
    assertTrue(compilationRequests.isEmpty())
    assertTrue(manager.compileRequest(file, projectRule.module).first == CompilationResult.Success)
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
      null/build/tmp/kotlin-classes/debug:null/build/intermediates/compile_and_runtime_not_namespaced_r_class_jar/debug/R.jar
      -d
      /tmp/overlay0
      /src/test.kt
    """.trimIndent(), requestParameters)
  }
}