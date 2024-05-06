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
package com.android.tools.idea.preview

import com.android.annotations.concurrency.GuardedBy
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.concurrency.awaitStatus
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ProjectRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

private class TestPreviewRefreshRequest(
  private val scope: CoroutineScope,
  override val clientId: String,
  val priority: Int,
  val name: String,
  val doBeforeLaunchingRefresh: () -> Unit = {},
  val doInsideRefreshJob: suspend () -> Unit = {}
) : PreviewRefreshRequest {
  companion object {
    // A lock is needed because these properties are shared between all requests
    val testLock = ReentrantLock()
    @GuardedBy("testLock") lateinit var log: StringBuilder
    @GuardedBy("testLock") lateinit var expectedLogPrintCount: CountDownLatch
  }

  override val refreshType: RefreshType
    get() =
      object : RefreshType {
        override val priority: Int
          get() = this@TestPreviewRefreshRequest.priority
      }

  var runningRefreshJob: Job? = null

  override fun doRefresh(): Job {
    doBeforeLaunchingRefresh()
    runningRefreshJob =
      scope.launch(AndroidDispatchers.uiThread) {
        testLock.withLock {
          log.appendLine("start $name")
          expectedLogPrintCount.countDown()
        }
        doInsideRefreshJob()
        delay(1000)
      }
    return runningRefreshJob!!
  }

  override fun onRefreshCompleted(result: RefreshResult, throwable: Throwable?) {
    testLock.withLock {
      when (result) {
        RefreshResult.SUCCESS -> log.appendLine("finish $name")
        RefreshResult.CANCELLED -> log.appendLine("cancel $name")
        // This should never happen, and if it does the test will fail when doing assertions about
        // the content of 'log'
        else -> log.appendLine("unexpected result")
      }
      expectedLogPrintCount.countDown()
    }
  }

  override fun onSkip(replacedBy: PreviewRefreshRequest) {
    testLock.withLock {
      log.appendLine("skip $name")
      expectedLogPrintCount.countDown()
    }
  }
}

class PreviewRefreshManagerTest {
  @JvmField @Rule val projectRule = ProjectRule()

  private lateinit var myDisposable: Disposable
  private lateinit var myScope: CoroutineScope
  private lateinit var refreshManager: PreviewRefreshManager

  @Before
  fun setUp() {
    myDisposable = Disposer.newDisposable()
    myScope = AndroidCoroutineScope(myDisposable)
    refreshManager = PreviewRefreshManager(myScope)
    TestPreviewRefreshRequest.log = StringBuilder()
  }

  @After
  fun tearDown() {
    Disposer.dispose(myDisposable)
    myScope.cancel()
  }

  @Test
  fun testRequestPriority() = runBlocking {
    TestPreviewRefreshRequest.expectedLogPrintCount = CountDownLatch(10)
    refreshManager.requestRefreshSync(TestPreviewRefreshRequest(myScope, "client1", 5, "req5"))
    val priorities = listOf(1, 2, 3, 4).shuffled()
    refreshManager.requestRefreshSync(
      TestPreviewRefreshRequest(myScope, "client2", priorities[0], "req${priorities[0]}")
    )
    refreshManager.requestRefreshSync(
      TestPreviewRefreshRequest(myScope, "client3", priorities[1], "req${priorities[1]}")
    )
    refreshManager.requestRefreshSync(
      TestPreviewRefreshRequest(myScope, "client4", priorities[2], "req${priorities[2]}")
    )
    refreshManager.requestRefreshSync(
      TestPreviewRefreshRequest(myScope, "client5", priorities[3], "req${priorities[3]}")
    )
    TestPreviewRefreshRequest.expectedLogPrintCount.await()
    assertEquals(
      """
      start req5
      finish req5
      start req4
      finish req4
      start req3
      finish req3
      start req2
      finish req2
      start req1
      finish req1
    """
        .trimIndent(),
      TestPreviewRefreshRequest.log.toString().trimIndent()
    )
  }

  @Test
  fun testSkipRequest() = runBlocking {
    TestPreviewRefreshRequest.expectedLogPrintCount = CountDownLatch(10)
    refreshManager.requestRefreshSync(TestPreviewRefreshRequest(myScope, "client1", 100, "req1"))
    val priorities2 = listOf(11, 22, 33)
    val priorities3 = listOf(1, 3, 2)
    for (i in 0 until 3) {
      refreshManager.requestRefreshSync(
        TestPreviewRefreshRequest(myScope, "client2", priorities2[i], "req2-${priorities2[i]}")
      )
      refreshManager.requestRefreshSync(
        TestPreviewRefreshRequest(myScope, "client3", priorities3[i], "req3-${priorities3[i]}")
      )
    }
    TestPreviewRefreshRequest.expectedLogPrintCount.await()
    val lines = TestPreviewRefreshRequest.log.toString().trimIndent().lines()
    assertEquals(10, lines.size) // 4 skip, 3 start and 3 finish
    // First 5 actions should be start of req1 and all skips,
    // but we cannot know in which of those positions the start will be
    assertTrue(lines.subList(0, 5).contains("start req1"))
    assertTrue(lines.subList(0, 5).contains("skip req2-11"))
    assertTrue(lines.subList(0, 5).contains("skip req2-22"))
    assertTrue(lines.subList(0, 5).contains("skip req3-1"))
    assertTrue(lines.subList(0, 5).contains("skip req3-2"))
    // We know the order of the last 5 actions
    assertEquals("finish req1", lines[5])
    assertEquals("start req2-33", lines[6])
    assertEquals("finish req2-33", lines[7])
    assertEquals("start req3-3", lines[8])
    assertEquals("finish req3-3", lines[9])
  }

  @Test
  fun testCancelRequest_newHigherPriority() = runBlocking {
    TestPreviewRefreshRequest.expectedLogPrintCount = CountDownLatch(1)
    refreshManager.requestRefreshSync(TestPreviewRefreshRequest(myScope, "client1", 1, "req1"))
    // wait for start of previous request and create a new one with higher priority
    TestPreviewRefreshRequest.expectedLogPrintCount.await()
    TestPreviewRefreshRequest.expectedLogPrintCount = CountDownLatch(3)
    refreshManager.requestRefreshSync(TestPreviewRefreshRequest(myScope, "client1", 2, "req2"))
    TestPreviewRefreshRequest.expectedLogPrintCount.await()
    assertEquals(
      """
      start req1
      cancel req1
      start req2
      finish req2
    """
        .trimIndent(),
      TestPreviewRefreshRequest.log.toString().trimIndent()
    )
  }

  @Test
  fun testCancelRequest_newSamePriority() = runBlocking {
    TestPreviewRefreshRequest.expectedLogPrintCount = CountDownLatch(1)
    refreshManager.requestRefreshSync(TestPreviewRefreshRequest(myScope, "client1", 1, "req1"))
    // wait for start of previous request and create a new one with same priority
    TestPreviewRefreshRequest.expectedLogPrintCount.await()
    TestPreviewRefreshRequest.expectedLogPrintCount = CountDownLatch(3)
    refreshManager.requestRefreshSync(TestPreviewRefreshRequest(myScope, "client1", 1, "req2"))
    TestPreviewRefreshRequest.expectedLogPrintCount.await()
    assertEquals(
      """
      start req1
      cancel req1
      start req2
      finish req2
    """
        .trimIndent(),
      TestPreviewRefreshRequest.log.toString().trimIndent()
    )
  }

  @Test
  fun testNotCancelRequest_newLowerPriority() = runBlocking {
    TestPreviewRefreshRequest.expectedLogPrintCount = CountDownLatch(1)
    refreshManager.requestRefreshSync(TestPreviewRefreshRequest(myScope, "client1", 1, "req1"))
    // wait for start of previous request and create a new one with lower priority
    TestPreviewRefreshRequest.expectedLogPrintCount.await()
    TestPreviewRefreshRequest.expectedLogPrintCount = CountDownLatch(3)
    refreshManager.requestRefreshSync(TestPreviewRefreshRequest(myScope, "client1", 0, "req2"))
    TestPreviewRefreshRequest.expectedLogPrintCount.await()
    assertEquals(
      """
      start req1
      finish req1
      start req2
      finish req2
    """
        .trimIndent(),
      TestPreviewRefreshRequest.log.toString().trimIndent()
    )
  }

  @Test
  fun testRefreshingTypeFlow_isCorrect(): Unit = runBlocking {
    TestPreviewRefreshRequest.expectedLogPrintCount = CountDownLatch(1)
    val refreshWaitJob = launch {
      refreshManager.refreshingTypeFlow
        // Wait for the flow to go to not-null and back to null
        .take(2)
        .collect {}
    }

    refreshManager.requestRefreshSync(TestPreviewRefreshRequest(myScope, "client1", 0, "req0"))
    refreshWaitJob.join()
  }

  @Test
  fun testRefreshingTypeFlow_eventuallyMovesToNull(): Unit = runBlocking {
    TestPreviewRefreshRequest.expectedLogPrintCount = CountDownLatch(150)
    val waitForARefreshJob = launch {
      refreshManager.refreshingTypeFlow.awaitStatus(
        "Failed waiting for the first refresh",
        1.seconds
      ) {
        it != null
      }
    }
    repeat(150) {
      refreshManager.requestRefreshSync(TestPreviewRefreshRequest(myScope, "client1", 0, "req$it"))
    }

    // Wait for refreshingType to change to not-null
    waitForARefreshJob.join()
    TestPreviewRefreshRequest.expectedLogPrintCount.await()
    refreshManager.refreshingTypeFlow.awaitStatus(
      "Failed waiting for refreshingTypeFlow to become null",
      5.seconds
    ) {
      it == null
    }
  }

  // Regression test for b/291792172
  @Test
  fun testRequestFromUiThread_noDeadlock() = runBlocking {
    TestPreviewRefreshRequest.expectedLogPrintCount = CountDownLatch(4)
    val doRefreshCalledLatch = CountDownLatch(1)

    refreshManager.requestRefreshSync(
      TestPreviewRefreshRequest(
        myScope,
        "client1",
        2,
        "req2",
        doBeforeLaunchingRefresh = {
          doRefreshCalledLatch.countDown()
          // Wait a little bit and try to get the UI-thread
          // Note that a countDownLatch cannot be used here as the wait is for the second request
          // to happen, which would start the deadlock "on the other side (uiThread)" if we regress
          runBlocking { delay(1000) }
          // Here is one of the sides of the deadlock seen in b/291792172,
          // this would hang if we regress
          runWriteActionAndWait { /*do nothing, just try to get the UI thread*/}
        }
      )
    )

    doRefreshCalledLatch.await()
    // Request another refresh before the previous one tries to get the UI-thread
    // Here is one of the sides of the deadlock seen in b/291792172, this would hang if we regress
    withContext(AndroidDispatchers.uiThread) {
      refreshManager.requestRefreshSync(TestPreviewRefreshRequest(myScope, "client1", 1, "req1"))
    }
    TestPreviewRefreshRequest.expectedLogPrintCount.await()
    assertEquals(
      """
      start req2
      finish req2
      start req1
      finish req1
    """
        .trimIndent(),
      TestPreviewRefreshRequest.log.toString().trimIndent()
    )
  }

  // Regression test for b/304569719
  @Test
  fun testInternalJobCancellationIsDetected() = runBlocking {
    TestPreviewRefreshRequest.expectedLogPrintCount = CountDownLatch(1)
    val refreshRequest =
      TestPreviewRefreshRequest(
        myScope,
        "client1",
        1,
        "req1",
        doInsideRefreshJob = {
          while (true) {
            delay(500)
          }
        }
      )
    refreshManager.requestRefreshSync(refreshRequest)
    // wait for refresh to start and then cancel its "internal" job
    TestPreviewRefreshRequest.expectedLogPrintCount.await()
    TestPreviewRefreshRequest.expectedLogPrintCount = CountDownLatch(1)
    refreshRequest.runningRefreshJob!!.cancel()
    TestPreviewRefreshRequest.expectedLogPrintCount.await()
    assertEquals(
      """
      start req1
      cancel req1
    """
        .trimIndent(),
      TestPreviewRefreshRequest.log.toString().trimIndent()
    )
  }

  /**
   * Send the [request] to the refresh manager and wait for it to be actually enqueued.
   *
   * Note that it doesn't wait for the request to be actually processed.
   */
  private suspend fun PreviewRefreshManager.requestRefreshSync(request: PreviewRefreshRequest) {
    this.requestRefreshForTest(request).join()
  }
}
