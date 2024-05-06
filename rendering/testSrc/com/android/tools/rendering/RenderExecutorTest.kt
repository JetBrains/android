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
package com.android.tools.rendering

import com.android.testutils.VirtualTimeScheduler
import com.android.testutils.concurrency.OnDemandExecutorService
import com.android.tools.rendering.RenderAsyncActionExecutor.RenderingTopic
import com.google.common.truth.Truth
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

private class TestSingleThreadExecutorService(private val delegate: ExecutorService) :
  AbstractExecutorService(), SingleThreadExecutorService {
  override val isBusy: Boolean = false

  override fun hasSpawnedCurrentThread(): Boolean = false

  override fun stackTrace(): Array<StackTraceElement> = emptyArray()

  override fun interrupt() {}

  override fun execute(command: Runnable) = delegate.execute(command)

  override fun shutdown() = delegate.shutdown()

  override fun shutdownNow(): MutableList<Runnable> = delegate.shutdownNow()

  override fun isShutdown(): Boolean = delegate.isShutdown

  override fun isTerminated(): Boolean = delegate.isTerminated

  override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean =
    delegate.awaitTermination(timeout, unit)
}

fun getRandomTopic(): RenderingTopic =
  RenderingTopic.values()[Random.nextInt(0, RenderingTopic.values().size)]

fun getLowPriorityRenderingTopicForTest(): RenderingTopic {
  return RenderingTopic.values().minByOrNull { it.priority }!!
}

fun getHighPriorityRenderingTopicForTest(): RenderingTopic {
  return RenderingTopic.values().maxByOrNull { it.priority }!!
}

// The topic should only affect cancellations, so by default use random topics to
// verify that the tests that are not related with cancellations are not affected by them.
private fun RenderExecutor.runAsyncActionWithTestDefault(
  queueingTimeout: Long = 1,
  queueingTimeoutUnit: TimeUnit = TimeUnit.SECONDS,
  actionTimeout: Long = 1,
  actionTimeoutUnit: TimeUnit = TimeUnit.SECONDS,
  topic: RenderingTopic = getRandomTopic(),
  runnable: () -> Unit
): CompletableFuture<Void> =
  runAsyncActionWithTimeout(
    queueingTimeout,
    queueingTimeoutUnit,
    actionTimeout,
    actionTimeoutUnit,
    topic,
    Callable<Void> {
      runnable()
      null
    }
  )

class RenderExecutorTest {
  @Test
  fun testPriority() {
    // Use the production RenderExecutor to test its priority queue
    val executor = RenderExecutor.create()
    val actionIsRunningLatch = CountDownLatch(4)
    val order = mutableListOf<Int>()
    try {
      executor.runAsyncActionWithTestDefault(topic = getHighPriorityRenderingTopicForTest()) {
        actionIsRunningLatch.countDown()
        Thread.sleep(500)
        order.add(1)
      }
      executor.runAsyncActionWithTestDefault(topic = getLowPriorityRenderingTopicForTest()) {
        order.add(3)
        actionIsRunningLatch.countDown()
      }
      executor.runAsyncActionWithTestDefault(topic = getLowPriorityRenderingTopicForTest()) {
        order.add(4)
        actionIsRunningLatch.countDown()
      }
      executor.runAsyncActionWithTestDefault(topic = getHighPriorityRenderingTopicForTest()) {
        order.add(2)
        actionIsRunningLatch.countDown()
      }
      actionIsRunningLatch.await(5, TimeUnit.SECONDS)
      Truth.assertThat(order).containsExactly(1, 2, 3, 4).inOrder()
    } finally {
      executor.shutdown()
    }
  }

  @Test
  fun testTimeout() {
    val actionExecutor = TestSingleThreadExecutorService(OnDemandExecutorService())
    val timeoutExecutorProvider = VirtualTimeScheduler()
    val executor =
      RenderExecutor.createForTests(
        executorService = actionExecutor,
        scheduledExecutorService = timeoutExecutorProvider
      )
    try {
      val result =
        executor.runAsyncActionWithTestDefault { fail("This should have not been executed.") }
      // Calling getNow should not throw an exception since the task has not timeout yet
      result.getNow(null)
      timeoutExecutorProvider.advanceBy(500, TimeUnit.MILLISECONDS)
      result.getNow(null)
      timeoutExecutorProvider.advanceBy(1500, TimeUnit.MILLISECONDS)
      // Now the TimeoutException should be thrown
      result.getNow(null)
      fail("No timeout triggered")
    } catch (e: CompletionException) {
      // The thrown timeout will have as cause the timeout
      assertTrue(e.cause is TimeoutException)
    }
  }

  @Test
  fun testExecuteAll() {
    val executor =
      RenderExecutor.createForTests(
        executorService = TestSingleThreadExecutorService(MoreExecutors.newDirectExecutorService()),
        scheduledExecutorService =
          ScheduledThreadPoolExecutor(1).also { it.removeOnCancelPolicy = true }
      )
    try {
      val counter = AtomicInteger(0)
      val lastToExecute = AtomicInteger(0)

      repeat(1000) {
        executor.runAsyncActionWithTestDefault {
          counter.incrementAndGet()
          lastToExecute.set(it)
        }
      }
      assertEquals(1000, counter.get())
      assertEquals(999, lastToExecute.get())
    } finally {
      executor.shutdown()
    }
  }

  @Test
  fun testQueueLimit() {
    val actionExecutor = OnDemandExecutorService()
    val timeoutExecutorProvider = VirtualTimeScheduler()
    val executor =
      RenderExecutor.createForTests(
        executorService = TestSingleThreadExecutorService(actionExecutor),
        scheduledExecutorService = timeoutExecutorProvider
      )
    val counterHighPriority = AtomicInteger(0)
    val counterLowPriority = AtomicInteger(0)
    val lastToExecute = AtomicInteger(0)

    repeat(50) {
      executor.runAsyncActionWithTestDefault(topic = getHighPriorityRenderingTopicForTest()) {
        counterHighPriority.incrementAndGet()
        lastToExecute.set(2 * it + 1)
      }
      executor.runAsyncActionWithTestDefault(topic = getLowPriorityRenderingTopicForTest()) {
        counterLowPriority.incrementAndGet()
        lastToExecute.set(2 * it + 2)
      }
    }
    actionExecutor.runAll()
    // Only a maximum of 50 tasks will be queued
    assertEquals(50, counterHighPriority.get())
    assertEquals(0, counterLowPriority.get())
    // But we have evicted the ones that were waiting, so the last one should have executed (99)
    assertEquals(99, lastToExecute.get())
  }

  @Test
  fun testSyncRenderActionTimeout() {
    val actionExecutor = OnDemandExecutorService()
    val timeoutExecutorProvider = VirtualTimeScheduler()
    val executor =
      RenderExecutor.createForTests(
        executorService = TestSingleThreadExecutorService(actionExecutor),
        scheduledExecutorService = timeoutExecutorProvider
      )

    // Force three timeouts to exceed the counter for the sync call
    executor.runAsyncActionWithTestDefault(queueingTimeout = 1) {}
    executor.runAsyncActionWithTestDefault(queueingTimeout = 3) {}
    executor.runAsyncActionWithTestDefault(queueingTimeout = 5) {}

    // This will never complete
    executor.runAsyncActionWithTestDefault(queueingTimeout = 100) {}

    timeoutExecutorProvider.advanceBy(2, TimeUnit.SECONDS)
    assertEquals(1, executor.accumulatedTimeouts)
    timeoutExecutorProvider.advanceBy(2, TimeUnit.SECONDS)
    assertEquals(2, executor.accumulatedTimeouts)
    timeoutExecutorProvider.advanceBy(2, TimeUnit.SECONDS)
    assertEquals(3, executor.accumulatedTimeouts)
    timeoutExecutorProvider.advanceBy(2, TimeUnit.SECONDS)

    val job =
      GlobalScope.launch {
        // Keep advancing the timeout timer
        while (!timeoutExecutorProvider.isShutdown) {
          timeoutExecutorProvider.advanceBy(10, TimeUnit.SECONDS)
          delay(500L)
        }
      }

    try {
      executor.runAction(Callable<Void> { null })
      fail("runAction should not have accepted the request and should have thrown TimeoutException")
    } catch (t: ExecutionException) {
      assertTrue(t.cause is TimeoutException)
    } finally {
      job.cancel()
    }
  }

  @Test
  fun testActionTimeout() {
    val actionExecutor = TestSingleThreadExecutorService(Executors.newSingleThreadExecutor())
    val timeoutExecutorProvider = VirtualTimeScheduler()
    val executor =
      RenderExecutor.createForTests(
        executorService = actionExecutor,
        scheduledExecutorService = timeoutExecutorProvider
      )

    val actionIsRunningLatch = CountDownLatch(1)
    val completeActionLatch = CountDownLatch(1)
    try {
      val future =
        executor.runAsyncActionWithTimeout(
          queueingTimeout = 0,
          queueingTimeoutUnit = TimeUnit.SECONDS,
          actionTimeout = 10,
          actionTimeoutUnit = TimeUnit.SECONDS,
          renderingTopic = getRandomTopic()
        ) {
          actionIsRunningLatch.countDown()
          completeActionLatch.await()
        }

      // Wait until the action has started running
      actionIsRunningLatch.await()
      assertFalse(future.isDone)
      timeoutExecutorProvider.advanceBy(5, TimeUnit.SECONDS)
      assertFalse(future.isDone)
      timeoutExecutorProvider.advanceBy(6, TimeUnit.SECONDS)
      assertTrue(future.isCompletedExceptionally)
    } finally {
      completeActionLatch.countDown()
      actionExecutor.shutdown()
    }
  }

  @Test
  fun testInterrupt() {
    val executor = RenderExecutor.create()

    var forceShutdown = false
    try {
      run {
        // Check manual interruption
        val actionIsRunningLatch = CountDownLatch(1)
        val completeActionLatch = CountDownLatch(1)

        executor.runAsyncAction {
          actionIsRunningLatch.countDown()
          try {
            while (!forceShutdown) {
              if (Thread.interrupted()) break
              Thread.sleep(250)
            }
          } finally {
            completeActionLatch.countDown()
          }
        }

        assertTrue(actionIsRunningLatch.await(5, TimeUnit.SECONDS))
        executor.interrupt()
        assertTrue(completeActionLatch.await(5, TimeUnit.SECONDS))
      }

      run {
        // Check interruption via timeout
        val actionIsRunningLatch = CountDownLatch(1)
        val completeActionLatch = CountDownLatch(1)

        // Run action timing out after 1 second
        executor.runAsyncActionWithTimeout(1, TimeUnit.SECONDS) {
          actionIsRunningLatch.countDown()
          try {
            while (!forceShutdown) {
              Thread.sleep(250)
            }
          } finally {
            completeActionLatch.countDown()
          }
        }

        assertTrue(actionIsRunningLatch.await(5, TimeUnit.SECONDS))
        assertTrue(completeActionLatch.await(5, TimeUnit.SECONDS))
      }

      run {
        val actionIsRunningLatch = CountDownLatch(1)
        val completeActionLatch = CountDownLatch(1)
        // Check the thread is still alive and working
        executor.runAsyncAction {
          actionIsRunningLatch.countDown()
          assertFalse(
            "The interrupted state should clear on every new action",
            Thread.currentThread().isInterrupted
          )
          completeActionLatch.countDown()
        }
        assertTrue(actionIsRunningLatch.await(5, TimeUnit.SECONDS))
        assertTrue(completeActionLatch.await(5, TimeUnit.SECONDS))
      }
    } finally {
      forceShutdown = true
      executor.shutdown()
    }
  }

  @Test
  fun testActionTimeout2() {
    val executor = RenderExecutor.create()

    val future =
      executor.runAsyncActionWithTimeout(
        queueingTimeout = 10,
        queueingTimeoutUnit = TimeUnit.SECONDS,
        actionTimeout = 10,
        actionTimeoutUnit = TimeUnit.SECONDS,
        renderingTopic = getRandomTopic()
      ) {
        runBlocking {
          CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            throw IllegalArgumentException()
          }
        }
        println("Done")
      }

    future.join()
  }

  @Test
  fun testCancelLowerPriority() {
    val actionExecutor = OnDemandExecutorService()
    val timeoutExecutorProvider = VirtualTimeScheduler()
    val executor =
      RenderExecutor.createForTests(
        executorService = TestSingleThreadExecutorService(actionExecutor),
        scheduledExecutorService = timeoutExecutorProvider
      )
    val highPriorityTopic = getHighPriorityRenderingTopicForTest()
    val lowPriorityTopic = getLowPriorityRenderingTopicForTest()
    var counterHighPriority = AtomicInteger(0)
    var counterLowPriority = AtomicInteger(0)

    // Cancelling low priority only
    repeat(10) {
      executor.runAsyncActionWithTestDefault(topic = highPriorityTopic) {
        counterHighPriority.incrementAndGet()
      }
      executor.runAsyncActionWithTestDefault(topic = lowPriorityTopic) {
        counterLowPriority.incrementAndGet()
      }
    }
    var cancellationCount = executor.cancelLowerPriorityActions(lowPriorityTopic.priority, false)
    var numActions = actionExecutor.runAll()
    assertEquals(20, numActions)
    assertEquals(10, cancellationCount)
    assertEquals(10, counterHighPriority.get())
    assertEquals(0, counterLowPriority.get())

    // Cancelling high priority should cancel everything
    counterHighPriority = AtomicInteger(0)
    counterLowPriority = AtomicInteger(0)
    repeat(10) {
      executor.runAsyncActionWithTestDefault(topic = highPriorityTopic) {
        counterHighPriority.incrementAndGet()
      }
      executor.runAsyncActionWithTestDefault(topic = lowPriorityTopic) {
        counterLowPriority.incrementAndGet()
      }
    }
    cancellationCount = executor.cancelLowerPriorityActions(highPriorityTopic.priority, false)
    numActions = actionExecutor.runAll()
    assertEquals(20, numActions)
    assertEquals(20, cancellationCount)
    assertEquals(0, counterHighPriority.get())
    assertEquals(0, counterLowPriority.get())
  }

  @Test
  fun testCancelByTopic() {
    val actionExecutor = OnDemandExecutorService()
    val timeoutExecutorProvider = VirtualTimeScheduler()
    val executor =
      RenderExecutor.createForTests(
        executorService = TestSingleThreadExecutorService(actionExecutor),
        scheduledExecutorService = timeoutExecutorProvider
      )

    val highPriorityTopic = getHighPriorityRenderingTopicForTest()
    val lowPriorityTopic = getLowPriorityRenderingTopicForTest()
    var counterHighPriority = AtomicInteger(0)
    var counterLowPriority = AtomicInteger(0)

    // Cancelling low priority only
    repeat(10) {
      executor.runAsyncActionWithTestDefault(topic = highPriorityTopic) {
        counterHighPriority.incrementAndGet()
      }
      executor.runAsyncActionWithTestDefault(topic = lowPriorityTopic) {
        counterLowPriority.incrementAndGet()
      }
    }
    var cancellationCount = executor.cancelActionsByTopic(listOf(lowPriorityTopic), false)
    var numActions = actionExecutor.runAll()
    assertEquals(20, numActions)
    assertEquals(10, cancellationCount)
    assertEquals(10, counterHighPriority.get())
    assertEquals(0, counterLowPriority.get())

    // Cancelling high priority only
    counterHighPriority = AtomicInteger(0)
    counterLowPriority = AtomicInteger(0)
    repeat(10) {
      executor.runAsyncActionWithTestDefault(topic = highPriorityTopic) {
        counterHighPriority.incrementAndGet()
      }
      executor.runAsyncActionWithTestDefault(topic = lowPriorityTopic) {
        counterLowPriority.incrementAndGet()
      }
    }
    cancellationCount = executor.cancelActionsByTopic(listOf(highPriorityTopic), false)
    numActions = actionExecutor.runAll()
    assertEquals(20, numActions)
    assertEquals(10, cancellationCount)
    assertEquals(0, counterHighPriority.get())
    assertEquals(10, counterLowPriority.get())
  }

  @Test
  fun testCancelAndInterrupt() {
    val executor = RenderExecutor.create()

    lateinit var actionIsRunningLatch: CountDownLatch
    lateinit var completeActionLatch: CountDownLatch
    var completedWithoutInterruption: Boolean = false

    val doRunAsync: (RenderingTopic) -> Unit = { topic ->
      completedWithoutInterruption = false
      actionIsRunningLatch = CountDownLatch(1)
      completeActionLatch = CountDownLatch(1)
      executor.runAsyncActionWithTestDefault(actionTimeout = 3, topic = topic) {
        actionIsRunningLatch.countDown()
        try {
          Thread.sleep(1500)
          completedWithoutInterruption = true
        } finally {
          completeActionLatch.countDown()
        }
      }
    }

    try {
      doRunAsync(getHighPriorityRenderingTopicForTest())
      assertTrue(actionIsRunningLatch.await(5, TimeUnit.SECONDS))
      var cancellationCount =
        executor.cancelLowerPriorityActions(getHighPriorityRenderingTopicForTest().priority, true)
      assertEquals(1, cancellationCount)
      assertTrue(completeActionLatch.await(5, TimeUnit.SECONDS))
      // Action should have been interrupted
      assertFalse(completedWithoutInterruption)

      doRunAsync(getHighPriorityRenderingTopicForTest())
      assertTrue(actionIsRunningLatch.await(5, TimeUnit.SECONDS))
      cancellationCount =
        executor.cancelActionsByTopic(listOf(getHighPriorityRenderingTopicForTest()), true)
      assertEquals(1, cancellationCount)
      assertTrue(completeActionLatch.await(5, TimeUnit.SECONDS))
      // Action should have been interrupted
      assertFalse(completedWithoutInterruption)

      doRunAsync(getHighPriorityRenderingTopicForTest())
      assertTrue(actionIsRunningLatch.await(5, TimeUnit.SECONDS))
      cancellationCount =
        executor.cancelLowerPriorityActions(getLowPriorityRenderingTopicForTest().priority, true)
      assertEquals(0, cancellationCount)
      assertTrue(completeActionLatch.await(5, TimeUnit.SECONDS))
      // Cancellation's priority was lower than the action's priority
      assertTrue(completedWithoutInterruption)

      doRunAsync(getLowPriorityRenderingTopicForTest())
      assertTrue(actionIsRunningLatch.await(5, TimeUnit.SECONDS))
      cancellationCount =
        executor.cancelActionsByTopic(listOf(getHighPriorityRenderingTopicForTest()), true)
      assertEquals(0, cancellationCount)
      assertTrue(completeActionLatch.await(5, TimeUnit.SECONDS))
      // Cancellation's topic was different from the action's topic
      assertTrue(completedWithoutInterruption)
    } finally {
      executor.shutdown()
    }
  }
}
