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
package com.android.tools.idea.preview

import com.android.annotations.concurrency.GuardedBy
import com.android.testutils.waitForCondition
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.preview.analytics.PreviewRefreshEventBuilder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class TestPreviewRefreshRequest(
  private val scope: CoroutineScope,
  override val clientId: String,
  val priority: Int,
  val name: String,
  override val refreshEventBuilder: PreviewRefreshEventBuilder? = null,
  val doBeforeLaunchingRefresh: () -> Unit = {},
  val doInsideRefreshJob: suspend () -> Unit = {},
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
        // Some parts of the metrics to be collected are responsibility of each preview tool
        refreshEventBuilder?.withPreviewsCount(1)
        refreshEventBuilder?.withPreviewsToRefresh(1)
        refreshEventBuilder?.addPreviewRenderDetails(false, true, 1f, 1)
      }
    return runningRefreshJob!!
  }

  override fun onRefreshCompleted(result: RefreshResult, throwable: Throwable?) {
    testLock.withLock {
      when (result) {
        RefreshResult.SUCCESS -> log.appendLine("finish $name")
        RefreshResult.AUTOMATICALLY_CANCELLED -> log.appendLine("auto-cancel $name")
        RefreshResult.USER_CANCELLED -> log.appendLine("user-cancel $name")
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

internal fun TestPreviewRefreshRequest.waitUntilRefreshStarts() {
  waitForCondition(5.seconds) { this.runningRefreshJob != null }
}
