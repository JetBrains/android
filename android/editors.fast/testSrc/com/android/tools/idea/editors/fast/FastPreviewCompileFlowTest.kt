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
package com.android.tools.idea.editors.fast

import com.android.ide.common.gradle.Version
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

private val TEST_VERSION = Version.parse("0.0.1-test")

class FastPreviewCompileFlowTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  private val project: Project
    get() = projectRule.project

  @Test
  fun testFlowUpdates() {
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
          moduleRuntimeVersionLocator = { TEST_VERSION }
        )
        .also { Disposer.register(projectRule.testRootDisposable, it) }
    val results = mutableListOf<Boolean>()
    val flowIsReady = CompletableDeferred<Unit>()
    val flow = scope.launch {
      // We take 9 elements. 2 per compilation + the initial false (not compiling) state that is triggered when we subscribe to the flow.
      fastPreviewCompileFlow(project, projectRule.testRootDisposable, manager) { flowIsReady.complete(Unit) }.take(9).collect {
        results.add(it)
      }
    }

    runBlocking {
      flowIsReady.await() // Wait for the flow to be listening
      repeat(4) {
        manager.invalidateRequestsCache()
        val request = scope.launch { manager.compileRequest(file, projectRule.module) }
        blockingDaemon.completeOneRequest()
        request.join()
      }

      flow.join()
      assertThat(results).containsExactly(false, true, false, true, false, true, false, true, false).inOrder()
    }
  }
}
