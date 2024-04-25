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

import com.android.testutils.waitForCondition
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.concurrency.awaitStatus
import com.android.tools.idea.preview.analytics.PreviewRefreshEventBuilder
import com.android.tools.idea.preview.analytics.PreviewRefreshTracker
import com.android.tools.rendering.RenderAsyncActionExecutor.RenderingTopic
import com.android.tools.rendering.RenderService
import com.android.tools.rendering.getRandomTopic
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.PreviewRefreshEvent
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ProjectRule
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
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

private class TestPreviewRefreshTracker : PreviewRefreshTracker {
  override fun logEvent(event: PreviewRefreshEvent): AndroidStudioEvent.Builder {
    logList.add(event)
    return AndroidStudioEvent.newBuilder()
  }

  companion object {
    lateinit var logList: MutableList<PreviewRefreshEvent>
  }
}

private val testPreviewType = PreviewRefreshEvent.PreviewType.UNKNOWN_TYPE

class PreviewRefreshManagerTest {
  @JvmField @Rule val projectRule = ProjectRule()

  private lateinit var myDisposable: Disposable
  private lateinit var myScope: CoroutineScope
  private lateinit var refreshManager: PreviewRefreshManager
  private lateinit var refreshTracker: TestPreviewRefreshTracker
  private lateinit var myTopic: RenderingTopic

  @Before
  fun setUp() {
    myDisposable = Disposer.newDisposable()
    myScope = AndroidCoroutineScope(myDisposable)
    myTopic = getRandomTopic()
    refreshManager = PreviewRefreshManager.getInstanceForTest(myScope, myTopic)
    TestPreviewRefreshRequest.log = StringBuilder()
    refreshTracker = TestPreviewRefreshTracker()
    TestPreviewRefreshTracker.logList = mutableListOf()
  }

  @After
  fun tearDown() {
    Disposer.dispose(myDisposable)
    myScope.cancel()
  }

  @Test
  fun testRequestPriority() = runBlocking {
    TestPreviewRefreshRequest.expectedLogPrintCount = CountDownLatch(10)
    refreshManager.requestRefreshSync(
      TestPreviewRefreshRequest(
        myScope,
        "client1",
        5,
        "req5",
        PreviewRefreshEventBuilder(testPreviewType, refreshTracker),
      )
    )
    val priorities = listOf(1, 2, 3, 4).shuffled()
    refreshManager.requestRefreshSync(
      TestPreviewRefreshRequest(
        myScope,
        "client2",
        priorities[0],
        "req${priorities[0]}",
        PreviewRefreshEventBuilder(testPreviewType, refreshTracker),
      )
    )
    refreshManager.requestRefreshSync(
      TestPreviewRefreshRequest(
        myScope,
        "client3",
        priorities[1],
        "req${priorities[1]}",
        PreviewRefreshEventBuilder(testPreviewType, refreshTracker),
      )
    )
    refreshManager.requestRefreshSync(
      TestPreviewRefreshRequest(
        myScope,
        "client4",
        priorities[2],
        "req${priorities[2]}",
        PreviewRefreshEventBuilder(testPreviewType, refreshTracker),
      )
    )
    refreshManager.requestRefreshSync(
      TestPreviewRefreshRequest(
        myScope,
        "client5",
        priorities[3],
        "req${priorities[3]}",
        PreviewRefreshEventBuilder(testPreviewType, refreshTracker),
      )
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
      TestPreviewRefreshRequest.log.toString().trimIndent(),
    )
    waitForCondition(5.seconds) { TestPreviewRefreshTracker.logList.size == 5 }
    assertTrue(TestPreviewRefreshTracker.logList.all { it.result == RefreshResult.SUCCESS })
    assertTrue(TestPreviewRefreshTracker.logList.all { it.hasRefreshTimeMillis() })
    assertTrue(TestPreviewRefreshTracker.logList.all { it.hasInQueueTimeMillis() })
    assertTrue(TestPreviewRefreshTracker.logList.all { it.hasPreviewsCount() })
    assertTrue(TestPreviewRefreshTracker.logList.all { it.hasPreviewsToRefresh() })
    assertTrue(TestPreviewRefreshTracker.logList.all { it.previewRendersCount > 0 })
  }

  @Test
  fun testSkipRequest() = runBlocking {
    TestPreviewRefreshRequest.expectedLogPrintCount = CountDownLatch(10)
    refreshManager.requestRefreshSync(
      TestPreviewRefreshRequest(
        myScope,
        "client1",
        100,
        "req1",
        PreviewRefreshEventBuilder(testPreviewType, refreshTracker),
      )
    )
    val priorities2 = listOf(11, 22, 33)
    val priorities3 = listOf(1, 3, 2)
    for (i in 0 until 3) {
      refreshManager.requestRefreshSync(
        TestPreviewRefreshRequest(
          myScope,
          "client2",
          priorities2[i],
          "req2-${priorities2[i]}",
          PreviewRefreshEventBuilder(testPreviewType, refreshTracker),
        )
      )
      refreshManager.requestRefreshSync(
        TestPreviewRefreshRequest(
          myScope,
          "client3",
          priorities3[i],
          "req3-${priorities3[i]}",
          PreviewRefreshEventBuilder(testPreviewType, refreshTracker),
        )
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

    waitForCondition(5.seconds) { TestPreviewRefreshTracker.logList.size == 7 }

    val skipped = TestPreviewRefreshTracker.logList.filter { it.result == RefreshResult.SKIPPED }
    assertEquals(4, skipped.size)
    assertTrue(skipped.all { it.hasInQueueTimeMillis() })

    val success = TestPreviewRefreshTracker.logList.filter { it.result == RefreshResult.SUCCESS }
    assertEquals(3, success.size)
    assertTrue(success.all { it.hasInQueueTimeMillis() })
    assertTrue(success.all { it.hasRefreshTimeMillis() })
    assertTrue(success.all { it.hasPreviewsCount() })
    assertTrue(success.all { it.hasPreviewsToRefresh() })
    assertTrue(success.all { it.previewRendersCount > 0 })
  }

  @Test
  fun testCancelRequest_newHigherPriority() = runBlocking {
    TestPreviewRefreshRequest.expectedLogPrintCount = CountDownLatch(1)
    refreshManager.requestRefreshSync(
      TestPreviewRefreshRequest(
        myScope,
        "client1",
        1,
        "req1",
        PreviewRefreshEventBuilder(testPreviewType, refreshTracker),
      )
    )
    // wait for start of previous request and create a new one with higher priority
    TestPreviewRefreshRequest.expectedLogPrintCount.await()
    TestPreviewRefreshRequest.expectedLogPrintCount = CountDownLatch(3)
    refreshManager.requestRefreshSync(
      TestPreviewRefreshRequest(
        myScope,
        "client1",
        2,
        "req2",
        PreviewRefreshEventBuilder(testPreviewType, refreshTracker),
      )
    )
    TestPreviewRefreshRequest.expectedLogPrintCount.await()
    assertEquals(
      """
      start req1
      auto-cancel req1
      start req2
      finish req2
    """
        .trimIndent(),
      TestPreviewRefreshRequest.log.toString().trimIndent(),
    )

    waitForCondition(5.seconds) { TestPreviewRefreshTracker.logList.size == 2 }

    val cancelled =
      TestPreviewRefreshTracker.logList.filter {
        it.result == RefreshResult.AUTOMATICALLY_CANCELLED
      }
    assertEquals(1, cancelled.size)
    assertTrue(cancelled.all { it.hasInQueueTimeMillis() })
    assertTrue(cancelled.all { it.hasRefreshTimeMillis() })

    val success = TestPreviewRefreshTracker.logList.filter { it.result == RefreshResult.SUCCESS }
    assertEquals(1, success.size)
    assertTrue(success.all { it.hasInQueueTimeMillis() })
    assertTrue(success.all { it.hasRefreshTimeMillis() })
    assertTrue(success.all { it.hasPreviewsCount() })
    assertTrue(success.all { it.hasPreviewsToRefresh() })
    assertTrue(success.all { it.previewRendersCount > 0 })
  }

  @Test
  fun testCancelRequest_newSamePriority() = runBlocking {
    TestPreviewRefreshRequest.expectedLogPrintCount = CountDownLatch(1)
    refreshManager.requestRefreshSync(
      TestPreviewRefreshRequest(
        myScope,
        "client1",
        1,
        "req1",
        PreviewRefreshEventBuilder(testPreviewType, refreshTracker),
      )
    )
    // wait for start of previous request and create a new one with same priority
    TestPreviewRefreshRequest.expectedLogPrintCount.await()
    TestPreviewRefreshRequest.expectedLogPrintCount = CountDownLatch(3)
    refreshManager.requestRefreshSync(
      TestPreviewRefreshRequest(
        myScope,
        "client1",
        1,
        "req2",
        PreviewRefreshEventBuilder(testPreviewType, refreshTracker),
      )
    )
    TestPreviewRefreshRequest.expectedLogPrintCount.await()
    assertEquals(
      """
      start req1
      auto-cancel req1
      start req2
      finish req2
    """
        .trimIndent(),
      TestPreviewRefreshRequest.log.toString().trimIndent(),
    )

    waitForCondition(5.seconds) { TestPreviewRefreshTracker.logList.size == 2 }

    val cancelled =
      TestPreviewRefreshTracker.logList.filter {
        it.result == RefreshResult.AUTOMATICALLY_CANCELLED
      }
    assertEquals(1, cancelled.size)
    assertTrue(cancelled.all { it.hasInQueueTimeMillis() })
    assertTrue(cancelled.all { it.hasRefreshTimeMillis() })

    val success = TestPreviewRefreshTracker.logList.filter { it.result == RefreshResult.SUCCESS }
    assertEquals(1, success.size)
    assertTrue(success.all { it.hasInQueueTimeMillis() })
    assertTrue(success.all { it.hasRefreshTimeMillis() })
    assertTrue(success.all { it.hasPreviewsCount() })
    assertTrue(success.all { it.hasPreviewsToRefresh() })
    assertTrue(success.all { it.previewRendersCount > 0 })
  }

  @Test
  fun testNotCancelRequest_newLowerPriority() = runBlocking {
    TestPreviewRefreshRequest.expectedLogPrintCount = CountDownLatch(1)
    refreshManager.requestRefreshSync(
      TestPreviewRefreshRequest(
        myScope,
        "client1",
        1,
        "req1",
        PreviewRefreshEventBuilder(testPreviewType, refreshTracker),
      )
    )
    // wait for start of previous request and create a new one with lower priority
    TestPreviewRefreshRequest.expectedLogPrintCount.await()
    TestPreviewRefreshRequest.expectedLogPrintCount = CountDownLatch(3)
    refreshManager.requestRefreshSync(
      TestPreviewRefreshRequest(
        myScope,
        "client1",
        0,
        "req2",
        PreviewRefreshEventBuilder(testPreviewType, refreshTracker),
      )
    )
    TestPreviewRefreshRequest.expectedLogPrintCount.await()
    assertEquals(
      """
      start req1
      finish req1
      start req2
      finish req2
    """
        .trimIndent(),
      TestPreviewRefreshRequest.log.toString().trimIndent(),
    )

    waitForCondition(5.seconds) { TestPreviewRefreshTracker.logList.size == 2 }

    val success = TestPreviewRefreshTracker.logList.filter { it.result == RefreshResult.SUCCESS }
    assertEquals(2, success.size)
    assertTrue(success.all { it.hasInQueueTimeMillis() })
    assertTrue(success.all { it.hasRefreshTimeMillis() })
    assertTrue(success.all { it.hasPreviewsCount() })
    assertTrue(success.all { it.hasPreviewsToRefresh() })
    assertTrue(success.all { it.previewRendersCount > 0 })
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

    refreshManager.requestRefreshSync(
      TestPreviewRefreshRequest(
        myScope,
        "client1",
        0,
        "req0",
        PreviewRefreshEventBuilder(testPreviewType, refreshTracker),
      )
    )
    refreshWaitJob.join()
  }

  @Test
  fun testRefreshingTypeFlow_eventuallyMovesToNull(): Unit = runBlocking {
    TestPreviewRefreshRequest.expectedLogPrintCount = CountDownLatch(150)
    val waitForARefreshJob = launch {
      refreshManager.refreshingTypeFlow.awaitStatus(
        "Failed waiting for the first refresh",
        1.seconds,
      ) {
        it != null
      }
    }
    repeat(150) {
      refreshManager.requestRefreshSync(
        TestPreviewRefreshRequest(
          myScope,
          "client1",
          0,
          "req$it",
          PreviewRefreshEventBuilder(testPreviewType, refreshTracker),
        )
      )
    }

    // Wait for refreshingType to change to not-null
    waitForARefreshJob.join()
    TestPreviewRefreshRequest.expectedLogPrintCount.await()
    refreshManager.refreshingTypeFlow.awaitStatus(
      "Failed waiting for refreshingTypeFlow to become null",
      5.seconds,
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
        PreviewRefreshEventBuilder(testPreviewType, refreshTracker),
        doBeforeLaunchingRefresh = {
          doRefreshCalledLatch.countDown()
          // Wait a little bit and try to get the UI-thread
          // Note that a countDownLatch cannot be used here as the wait is for the second request
          // to happen, which would start the deadlock "on the other side (uiThread)" if we regress
          runBlocking { delay(1000) }
          // Here is one of the sides of the deadlock seen in b/291792172,
          // this would hang if we regress
          runWriteActionAndWait { /*do nothing, just try to get the UI thread*/ }
        },
      )
    )

    doRefreshCalledLatch.await()
    // Request another refresh before the previous one tries to get the UI-thread
    // Here is one of the sides of the deadlock seen in b/291792172, this would hang if we regress
    withContext(AndroidDispatchers.uiThread) {
      refreshManager.requestRefreshSync(
        TestPreviewRefreshRequest(
          myScope,
          "client1",
          1,
          "req1",
          PreviewRefreshEventBuilder(testPreviewType, refreshTracker),
        )
      )
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
      TestPreviewRefreshRequest.log.toString().trimIndent(),
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
        PreviewRefreshEventBuilder(testPreviewType, refreshTracker),
        doInsideRefreshJob = {
          while (true) {
            delay(500)
          }
        },
      )
    refreshManager.requestRefreshSync(refreshRequest)
    // wait for refresh to start and then cancel its "internal" job
    TestPreviewRefreshRequest.expectedLogPrintCount.await()
    TestPreviewRefreshRequest.expectedLogPrintCount = CountDownLatch(1)
    refreshRequest.waitUntilRefreshStarts()
    refreshRequest.runningRefreshJob!!.cancel()
    TestPreviewRefreshRequest.expectedLogPrintCount.await()
    assertEquals(
      """
      start req1
      user-cancel req1
    """
        .trimIndent(),
      TestPreviewRefreshRequest.log.toString().trimIndent(),
    )

    waitForCondition(5.seconds) { TestPreviewRefreshTracker.logList.size == 1 }

    val cancelled =
      TestPreviewRefreshTracker.logList.filter { it.result == RefreshResult.USER_CANCELLED }
    assertEquals(1, cancelled.size)
    assertTrue(cancelled.all { it.hasInQueueTimeMillis() })
    assertTrue(cancelled.all { it.hasRefreshTimeMillis() })
  }

  @Test
  fun testRenderingTopicIsCancelled_AfterUserCancellation() = runBlocking {
    val waitingLatch = CountDownLatch(1)
    val renderTopicCancelledLatch = CountDownLatch(1)
    RenderService.getRenderAsyncActionExecutor().runAsyncAction(myTopic) {
      try {
        waitingLatch.await()
      } catch (e: InterruptedException) {
        renderTopicCancelledLatch.countDown()
      }
    }

    TestPreviewRefreshRequest.expectedLogPrintCount = CountDownLatch(1)
    val refreshRequest =
      TestPreviewRefreshRequest(
        myScope,
        "client1",
        1,
        "req1",
        PreviewRefreshEventBuilder(testPreviewType, refreshTracker),
        doInsideRefreshJob = {
          while (true) {
            delay(500)
          }
        },
      )
    refreshManager.requestRefreshSync(refreshRequest)
    // wait for refresh to start and then cancel its "internal" job
    TestPreviewRefreshRequest.expectedLogPrintCount.await()
    TestPreviewRefreshRequest.expectedLogPrintCount = CountDownLatch(1)
    refreshRequest.waitUntilRefreshStarts()
    refreshRequest.runningRefreshJob!!.cancel()

    // wait for the cancel to complete
    TestPreviewRefreshRequest.expectedLogPrintCount.await()
    waitForCondition(5.seconds) { TestPreviewRefreshTracker.logList.size == 1 }

    val renderTopicWasCancelled = renderTopicCancelledLatch.await(5, TimeUnit.SECONDS)
    assertTrue(renderTopicWasCancelled)
  }

  @Test
  fun testRenderingTopicIsCancelled_AfterAutomaticCancellation() = runBlocking {
    val waitingLatch = CountDownLatch(1)
    val renderTopicCancelledLatch = CountDownLatch(1)
    RenderService.getRenderAsyncActionExecutor().runAsyncAction(myTopic) {
      try {
        waitingLatch.await()
      } catch (e: InterruptedException) {
        renderTopicCancelledLatch.countDown()
      }
    }

    TestPreviewRefreshRequest.expectedLogPrintCount = CountDownLatch(1)
    refreshManager.requestRefreshSync(
      TestPreviewRefreshRequest(
        myScope,
        "client1",
        1,
        "req1",
        PreviewRefreshEventBuilder(testPreviewType, refreshTracker),
      )
    )
    // wait for start of previous request and create a new one with higher priority which will
    // automatically cancel the previous request
    TestPreviewRefreshRequest.expectedLogPrintCount.await()
    TestPreviewRefreshRequest.expectedLogPrintCount = CountDownLatch(3)
    refreshManager.requestRefreshSync(
      TestPreviewRefreshRequest(
        myScope,
        "client1",
        2,
        "req2",
        PreviewRefreshEventBuilder(testPreviewType, refreshTracker),
      )
    )
    TestPreviewRefreshRequest.expectedLogPrintCount.await()
    waitForCondition(5.seconds) { TestPreviewRefreshTracker.logList.size == 2 }

    val renderTopicWasCancelled = renderTopicCancelledLatch.await(5, TimeUnit.SECONDS)
    assertTrue(renderTopicWasCancelled)
  }
}
