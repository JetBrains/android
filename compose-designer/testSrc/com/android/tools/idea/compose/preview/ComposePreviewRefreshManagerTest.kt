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
package com.android.tools.idea.compose.preview

import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ProjectRule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ComposePreviewRefreshManagerTest {
  @JvmField @Rule val projectRule = ProjectRule()

  private lateinit var refreshManager: ComposePreviewRefreshManager
  private lateinit var myDisposable: Disposable
  private lateinit var myScope: CoroutineScope
  private lateinit var log: StringBuilder
  private lateinit var startLatch: CountDownLatch

  private fun testRefresh(request: ComposePreviewRefreshRequest): Job {
    return myScope.launch {
      try {
        log.appendLine("start ${request.requestId}")
        startLatch.countDown()
        delay(1000)
        log.appendLine("finish ${request.requestId}")
      } catch (e: CancellationException) {
        log.appendLine("cancel ${request.requestId}")
      }
    }
  }

  @Before
  fun setUp() {
    refreshManager = ComposePreviewRefreshManager.getInstance(projectRule.project)
    myDisposable = Disposer.newDisposable()
    myScope = AndroidCoroutineScope(myDisposable)
    log = StringBuilder()
    startLatch = CountDownLatch(1)
  }

  @After
  fun tearDown() {
    Disposer.dispose(myDisposable)
    myScope.cancel()
  }

  @Test
  fun testCancel() = runBlocking {
    val completable1 = CompletableDeferred<Unit>()
    val completable2 = CompletableDeferred<Unit>()
    refreshManager.requestRefresh(
      ComposePreviewRefreshRequest(
        "test_id",
        ::testRefresh,
        completable1,
        ComposePreviewRefreshType.NORMAL,
        requestId = "req1"
      )
    )
    startLatch.await()
    refreshManager.requestRefresh(
      ComposePreviewRefreshRequest(
        "test_id",
        ::testRefresh,
        completable2,
        ComposePreviewRefreshType.NORMAL,
        requestId = "req2"
      )
    )

    // 1st request should have been cancelled
    assertFailsWith<CancellationException> { completable1.await() }
    completable2.await()

    assertEquals(
      """
      start req1
      cancel req1
      start req2
      finish req2
    """
        .trimIndent(),
      log.toString().trimIndent()
    )
  }

  @Test
  fun testSkip() = runBlocking {
    val completable1 = CompletableDeferred<Unit>()
    val completable2 = CompletableDeferred<Unit>()
    val completable3 = CompletableDeferred<Unit>()
    refreshManager.requestRefresh(
      ComposePreviewRefreshRequest(
        "test_id",
        ::testRefresh,
        completable1,
        ComposePreviewRefreshType.NORMAL,
        requestId = "req1"
      )
    )
    startLatch.await()
    // Use lower priority for 2 and 3 to ensure that the first request is not cancelled.
    refreshManager.requestRefresh(
      ComposePreviewRefreshRequest(
        "test_id",
        ::testRefresh,
        completable2,
        ComposePreviewRefreshType.QUICK,
        requestId = "req2"
      )
    )
    refreshManager.requestRefresh(
      ComposePreviewRefreshRequest(
        "test_id",
        ::testRefresh,
        completable3,
        ComposePreviewRefreshType.QUICK,
        requestId = "req3"
      )
    )

    // All should get completed, even the ones from skipped requests.
    completable1.await()
    completable2.await()
    completable3.await()

    // Given that enqueueing of refresh requests is asynchronous we cannot know which of
    // req2 or req3 will be enqueued first, and then skipped by the one enqueued second.
    // But we know that one of them should be skipped (no logs) and the other executed.
    assertTrue(
      listOf(
          """
      start req1
      finish req1
      start req3
      finish req3
    """
            .trimIndent(),
          """
      start req1
      finish req1
      start req2
      finish req2
    """
            .trimIndent()
        )
        .contains(log.toString().trimIndent())
    )
  }
}
