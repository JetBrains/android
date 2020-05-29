/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.rendering

import com.android.testutils.VirtualTimeScheduler
import com.android.testutils.concurrency.OnDemandExecutorService
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.Callable
import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutionException
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger

class RenderExecutorTest {
  @Test
  fun testTimeout() {
    val actionExecutor = OnDemandExecutorService()
    val timeoutExecutorProvider = VirtualTimeScheduler()
    val executor = RenderExecutor.createForTests(executorProvider = { actionExecutor },
                                                 timeoutExecutorProvider = { timeoutExecutorProvider })
    try {
      val result = executor.runAsyncAction(1, TimeUnit.SECONDS) {
        fail("This should have not been executed.")
      }
      // Calling getNow should not throw an exception since the task has not timeout yet
      result.getNow(null)
      timeoutExecutorProvider.advanceBy(500, TimeUnit.MILLISECONDS)
      result.getNow(null)
      timeoutExecutorProvider.advanceBy(1500, TimeUnit.MILLISECONDS)
      // Now the TimeoutException should be thrown
      result.getNow(null)
      fail("No timeout triggered")
    }
    catch (e: CompletionException) {
      // The thrown timeout will have as cause the timeout
      assertTrue(e.cause is TimeoutException)
    }
  }

  @Test
  fun testExecuteAll() {
    val executor = RenderExecutor.createForTests(executorProvider = { MoreExecutors.newDirectExecutorService() },
                                                 timeoutExecutorProvider = {
                                                   ScheduledThreadPoolExecutor(1).also {
                                                     it.removeOnCancelPolicy = true
                                                   }
                                                 })
    try {
      val counter = AtomicInteger(0)
      val lastToExecute = AtomicInteger(0)

      repeat(1000) {
        executor.runAsyncAction(1, TimeUnit.SECONDS) {
          counter.incrementAndGet()
          lastToExecute.set(it)
        }
      }
      assertEquals(1000, counter.get())
      assertEquals(999, lastToExecute.get())
    }
    finally {
      executor.shutdown()
    }
  }

  @Test
  fun testQueueLimit() {
    val actionExecutor = OnDemandExecutorService()
    val timeoutExecutorProvider = VirtualTimeScheduler()
    val executor = RenderExecutor.createForTests(executorProvider = { actionExecutor },
                                                 timeoutExecutorProvider = { timeoutExecutorProvider })
    val counter = AtomicInteger(0)
    val lastToExecute = AtomicInteger(0)

    repeat(100) {
      executor.runAsyncAction(1, TimeUnit.SECONDS) {
        counter.incrementAndGet()
        lastToExecute.set(it)
      }
    }
    actionExecutor.runAll()
    // Only a maximum of 50 tasks will be queued
    assertEquals(50, counter.get())
    // But we have evicted the ones that were waiting, so the last one should have executed (99)
    assertEquals(99, lastToExecute.get())
  }

  @Test
  fun testSyncRenderActionTimeout() {
    val actionExecutor = OnDemandExecutorService()
    val timeoutExecutorProvider = VirtualTimeScheduler()
    val executor = RenderExecutor.createForTests(executorProvider = { actionExecutor },
                                                 timeoutExecutorProvider = { timeoutExecutorProvider })

    // Force three timeouts to exceed the counter for the sync call
    executor.runAsyncAction(1, TimeUnit.SECONDS) {
    }
    executor.runAsyncAction(3, TimeUnit.SECONDS) {
    }
    executor.runAsyncAction(5, TimeUnit.SECONDS) {
    }

    // This will never complete
    executor.runAsyncAction(100, TimeUnit.SECONDS) {
    }

    timeoutExecutorProvider.advanceBy(2, TimeUnit.SECONDS)
    assertEquals(1, executor.accumulatedTimeouts)
    timeoutExecutorProvider.advanceBy(2, TimeUnit.SECONDS)
    assertEquals(2, executor.accumulatedTimeouts)
    timeoutExecutorProvider.advanceBy(2, TimeUnit.SECONDS)
    assertEquals(3, executor.accumulatedTimeouts)
    timeoutExecutorProvider.advanceBy(2, TimeUnit.SECONDS)

    val job = GlobalScope.launch {
      // Keep advancing the timeout timer
      while (!timeoutExecutorProvider.isShutdown) {
        timeoutExecutorProvider.advanceBy(10, TimeUnit.SECONDS)
        delay(500L)
      }
    }

    try {
      executor.runAction(Callable<Void> {
        null
      })
      fail("runAction should not have accepted the request and should have thrown TimeoutException")
    }
    catch (t: ExecutionException) {
      assertTrue(t.cause is TimeoutException)
    }
    finally {
      job.cancel()
    }
  }
}