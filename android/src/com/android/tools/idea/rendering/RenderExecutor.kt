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

import com.android.annotations.concurrency.GuardedBy
import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.annotations.TestOnly
import java.util.LinkedList
import java.util.Queue
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** Max number of tasks that can be waiting to execute  */
private val DEFAULT_MAX_QUEUED_TASKS = Integer.getInteger("layoutlib.thread.max.queued", 50)

private fun singleThreadExecutor(factory: ThreadFactory): ExecutorService = ThreadPoolExecutor(1, 1,
                                                                                               0, TimeUnit.MILLISECONDS,
                                                                                               LinkedBlockingQueue(),
                                                                                               factory)

/**
 * Intended to be used for executing render tasks of layoutlib [RenderSession].
 * Currently, all calls to the layoutlib should be done from the same thread.
 * This executor guarantees that unit of work passed to [runAction] or [runAsyncAction]
 * will be executed sequentially from the same thread.
 *
 * @param maxQueueingTasks max number of tasks that can be queueing waiting for a task to complete.
 * @param executorProvider a provider of the [ExecutorService] using the given [ThreadFactory].
 * @param timeoutExecutorProvider a [ScheduledExecutorService] to keep track of the task timeout.
 */
class RenderExecutor private constructor(private val maxQueueingTasks: Int,
                                         executorProvider: (ThreadFactory) -> ExecutorService,
                                         timeoutExecutorProvider: () -> ScheduledExecutorService) : RenderAsyncActionExecutor {
  private val renderingThread = AtomicReference<Thread?>()

  /**
   * The thread factory allows us controlling when the new thread is created to we can keep track of it. This allows us
   * to capture the stack trace later.
   */
  private val threadFactory = ThreadFactory {
    val newThread = Thread(null, it, "Layoutlib Render Thread")
      .apply { isDaemon = true }
    renderingThread.set(newThread)
    newThread
  }
  private val pendingActionsQueueLock: Lock = ReentrantLock()

  @GuardedBy("pendingActionsQueueLock")
  private val pendingActionsQueue: Queue<CompletableFuture<*>> = LinkedList()
  private val renderingExecutor: ExecutorService = executorProvider(threadFactory)
  private val timeoutExecutor: ScheduledExecutorService = timeoutExecutorProvider()
  private val accumulatedTimeoutExceptions = AtomicInteger(0)
  private val isBusy = AtomicBoolean(false)

  fun shutdown() {
    timeoutExecutor.shutdownNow()
    renderingExecutor.shutdownNow()
    val currentThread = renderingThread.getAndSet(null)
    currentThread?.interrupt()
  }

  private fun createRenderTimeoutException(message: String): TimeoutException {
    val timeoutException = TimeoutException(message)
    renderingThread.get()?.let {
      timeoutException.stackTrace = it.stackTrace
    }

    return timeoutException
  }

  /**
   * Calls the given action in the render thread synchronously.
   */
  @Deprecated("Use the async version runAsyncAction")
  @Throws(Exception::class)
  fun <T> runAction(callable: Callable<T>): T {
    // If the number of timeouts exceeds a certain threshold, stop waiting so the caller doesn't block.
    if (accumulatedTimeoutExceptions.get() > 3) {
      throw createRenderTimeoutException("""
          The rendering thread is not processing requests.
          This typically happens when there is an infinite loop or unbounded recursion in one of the custom views.
          """)
    }

    // All async actions run with a timeout so we do not need to specify another one here
    return runAsyncAction(callable).get()
  }

  private class EvictedException : Exception()

  private fun scheduleTimeoutAction(timeout: Long, unit: TimeUnit, action: () -> Unit): ScheduledFuture<*> =
    timeoutExecutor.schedule(
      action,
      timeout,
      unit)

  override fun <T : Any?> runAsyncActionWithTimeout(queueingTimeout: Long,
                                                    queueingTimeoutUnit: TimeUnit,
                                                    actionTimeout: Long,
                                                    actionTimeoutUnit: TimeUnit,
                                                    callable: Callable<T>): CompletableFuture<T> {
    val future = CompletableFuture<T>()

    val queueTimeoutFuture = if (queueingTimeout > 0) {
      scheduleTimeoutAction(queueingTimeout, queueingTimeoutUnit) {
        val message = """
        Preview timed out (${queueingTimeoutUnit.toMillis(queueingTimeout)}ms).
        This typically happens when there is an infinite loop or unbounded recursion in one of the custom views.
      """.trimIndent()
        future.completeExceptionally(createRenderTimeoutException(message))
        accumulatedTimeoutExceptions.incrementAndGet()
      }
    }
    else {
      // No queue timeout. This will wait indefinitely unless is evicted by other actions being added to the queue.
      null
    }
    pendingActionsQueueLock.withLock {
      pendingActionsQueue.add(future)
      // We have reached the maximum, evict overflow
      if (maxQueueingTasks > 0) {
        while (pendingActionsQueue.size > maxQueueingTasks) {
          pendingActionsQueue.remove().completeExceptionally(EvictedException())
        }
      }
    }
    renderingExecutor.execute {
      isBusy.set(true)
      try {
        queueTimeoutFuture?.cancel(false)
        pendingActionsQueueLock.withLock {
          pendingActionsQueue.remove(future)
        }

        if (future.isDone) return@execute

        val actionTimeoutFuture = scheduleTimeoutAction(actionTimeout, actionTimeoutUnit) {
          future.completeExceptionally(
            createRenderTimeoutException("The render action was too slow to execute (${actionTimeoutUnit.toMillis(actionTimeout)}ms)"))
        }
        future.whenComplete { _, _ -> actionTimeoutFuture.cancel(false) }

        // The request got called, so reset the timeout counter.
        accumulatedTimeoutExceptions.set(0)
        try {
          future.complete(callable.call())
        }
        catch (t: Throwable) {
          future.completeExceptionally(t)
        }
      }
      finally {
        isBusy.set(false)
      }
    }
    return future
      .whenComplete { result, exception ->
        queueTimeoutFuture?.cancel(true)
        if (exception != null) {
          future.completeExceptionally(exception)
        }
        else {
          future.complete(result)
        }
      }
  }

  @TestOnly
  fun shutdown(timeoutSeconds: Long) {
    if (timeoutSeconds > 0) {
      try {
        renderingExecutor.awaitTermination(timeoutSeconds, TimeUnit.SECONDS)
      }
      catch (ignored: InterruptedException) {
        Logger.getInstance(RenderExecutor::class.java).warn("The RenderExecutor does not shutdown after $timeoutSeconds seconds")
      }
    }

    shutdown()
  }

  /**
   * Returns true if the current thread is the render thread managed by this executor.
   */
  fun isCurrentThreadARenderThread() = Thread.currentThread() == renderingThread.get()

  @get:TestOnly
  val accumulatedTimeouts: Int
    get() = accumulatedTimeoutExceptions.get()

  /**
   * Returns true if the render thread is busy running some code, false otherwise.
   */
  fun isBusy() = isBusy.get()

  companion object {
    @JvmStatic
    fun create(): RenderExecutor =
      RenderExecutor(DEFAULT_MAX_QUEUED_TASKS, ::singleThreadExecutor) {
        ScheduledThreadPoolExecutor(1).also {
          it.removeOnCancelPolicy = true
        }
      }

    @TestOnly
    fun createForTests(executorProvider: (ThreadFactory) -> ExecutorService,
                       timeoutExecutorProvider: () -> ScheduledExecutorService) =
      RenderExecutor(DEFAULT_MAX_QUEUED_TASKS, executorProvider, timeoutExecutorProvider)
  }
}