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

import com.android.annotations.concurrency.GuardedBy
import com.android.tools.rendering.RenderAsyncActionExecutor.RenderingTopic
import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.annotations.TestOnly
import java.util.EnumMap
import java.util.PriorityQueue
import java.util.Queue
import java.util.concurrent.Callable
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.LongAdder
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** Max number of tasks that can be waiting to execute */
private val DEFAULT_MAX_QUEUED_TASKS = Integer.getInteger("layoutlib.thread.max.queued", 50)

/**
 * Intended to be used for executing render tasks of layoutlib [RenderSession]. Currently, all calls
 * to the layoutlib should be done from the same thread. This executor guarantees that unit of work
 * passed to [runAction] or [runAsyncAction] will be executed sequentially from the same thread.
 *
 * @param maxQueueingTasks max number of tasks that can be queueing waiting for a task to complete.
 * @param renderingExecutorService a provider of the [ExecutorService] using the given
 *   [ThreadFactory].
 * @param scheduledExecutorService a [ScheduledExecutorService] to keep track of the task timeout.
 */
class RenderExecutor
private constructor(
  private val maxQueueingTasks: Int,
  private val renderingExecutorService: SingleThreadExecutorService,
  private val scheduledExecutorService: ScheduledExecutorService,
) : RenderAsyncActionExecutor {
  private val pendingActionsQueueLock: Lock = ReentrantLock()
  private val runningRenderLock: Lock = ReentrantLock()

  @GuardedBy("pendingActionsQueueLock")
  private val allPendingActionsQueue: Queue<PriorityCompletableFuture<*>> = PriorityQueue()
  @GuardedBy("pendingActionsQueueLock")
  private val pendingActionsQueueByTopic:
    MutableMap<RenderingTopic, Queue<PriorityCompletableFuture<*>>> =
    EnumMap(RenderingTopic::class.java)
  @GuardedBy("runningRenderLock") private var runningRender: PriorityCompletableFuture<*>? = null
  private val accumulatedTimeoutExceptions = AtomicInteger(0)
  private val executedRenderActions = LongAdder()

  fun interrupt() = renderingExecutorService.interrupt()

  fun shutdown() {
    scheduledExecutorService.shutdownNow()
    renderingExecutorService.shutdownNow()
  }

  fun currentStackTrace() = renderingExecutorService.stackTrace()

  private fun createRenderTimeoutException(message: String): TimeoutException =
    TimeoutException(message).apply { stackTrace = renderingExecutorService.stackTrace() }

  /** Calls the given action in the render thread synchronously. */
  @Deprecated("Use the async version runAsyncAction")
  @Throws(Exception::class)
  fun <T> runAction(callable: Callable<T>): T {
    // If the number of timeouts exceeds a certain threshold, stop waiting so the caller doesn't
    // block.
    if (accumulatedTimeoutExceptions.get() > 3) {
      throw createRenderTimeoutException(
        """
          The rendering thread is not processing requests.
          This typically happens when there is an infinite loop or unbounded recursion in one of the custom views.
          """
      )
    }

    // All async actions run with a timeout so we do not need to specify another one here
    return runAsyncAction(RenderingTopic.NOT_SPECIFIED, callable).get()
  }

  private class EvictedException(message: String?) : CancellationException(message)

  private fun scheduleTimeoutAction(
    timeout: Long,
    unit: TimeUnit,
    action: () -> Unit,
  ): ScheduledFuture<*> = scheduledExecutorService.schedule(action, timeout, unit)

  override val executedRenderActionCount
    get() = executedRenderActions.toLong()

  override fun <T : Any?> runAsyncActionWithTimeout(
    queueingTimeout: Long,
    queueingTimeoutUnit: TimeUnit,
    actionTimeout: Long,
    actionTimeoutUnit: TimeUnit,
    renderingTopic: RenderingTopic,
    callable: Callable<T>,
  ): CompletableFuture<T> {
    val future =
      object : PriorityCompletableFuture<T>(renderingTopic) {
        override fun cancel(mayInterruptIfRunning: Boolean): Boolean =
          super.cancel(mayInterruptIfRunning).also {
            if (mayInterruptIfRunning && it) {
              interrupt()
            }
          }
      }

    val queueTimeoutFuture =
      if (queueingTimeout > 0) {
        scheduleTimeoutAction(queueingTimeout, queueingTimeoutUnit) {
          val message =
            """
        Preview timed out (${queueingTimeoutUnit.toMillis(queueingTimeout)}ms).
        This typically happens when there is an infinite loop or unbounded recursion in one of the custom views.
      """
              .trimIndent()
          future.completeExceptionally(createRenderTimeoutException(message))
          accumulatedTimeoutExceptions.incrementAndGet()
        }
      } else {
        // No queue timeout. This will wait indefinitely unless is evicted by other actions being
        // added to the queue.
        null
      }
    pendingActionsQueueLock
      .withLock {
        allPendingActionsQueue.add(future)
        pendingActionsQueueByTopic.getOrPut(renderingTopic) { PriorityQueue() }.add(future)
        // We have reached the maximum, evict overflow
        return@withLock if (maxQueueingTasks > 0) {
          val evictedTasks = mutableListOf<CompletableFuture<*>>()
          while (allPendingActionsQueue.size > maxQueueingTasks) {
            val evicted = allPendingActionsQueue.remove()
            pendingActionsQueueByTopic[evicted.renderingTopic]?.remove(evicted)
            evictedTasks.add(evicted)
          }
          evictedTasks
        } else emptyList()
      }
      .forEach {
        // Complete all the evicted tasks
        it.completeExceptionally(
          EvictedException("Max number ($maxQueueingTasks) of render actions reached")
        )
      }
    renderingExecutorService.execute(
      PriorityRunnable(renderingTopic) {
        runningRenderLock.withLock { runningRender = future }
        try {
          executedRenderActions.increment()
          // Clear the interrupted state
          Thread.interrupted()
          queueTimeoutFuture?.cancel(false)
          val isPending =
            pendingActionsQueueLock.withLock {
              pendingActionsQueueByTopic[renderingTopic]?.remove(future)
              allPendingActionsQueue.remove(future)
            }

          if (!isPending || future.isDone) return@PriorityRunnable

          val actionTimeoutFuture =
            scheduleTimeoutAction(actionTimeout, actionTimeoutUnit) {
              if (!future.isDone) {
                interrupt()
              }
              future.completeExceptionally(
                createRenderTimeoutException(
                  "The render action was too slow to execute (${actionTimeoutUnit.toMillis(actionTimeout)}ms)"
                )
              )
            }
          future.whenComplete { _, _ -> actionTimeoutFuture.cancel(false) }

          // The request got called, so reset the timeout counter.
          accumulatedTimeoutExceptions.set(0)
          try {
            future.complete(callable.call())
          } catch (t: Throwable) {
            future.completeExceptionally(t)
          }
        } finally {
          runningRenderLock.withLock { runningRender = null }
        }
      }
    )
    return future.whenComplete { result, exception ->
      queueTimeoutFuture?.cancel(true)
      if (exception != null) {
        future.completeExceptionally(exception)
      } else {
        future.complete(result)
      }
    }
  }

  override fun cancelActionsByTopic(
    topicsToCancel: List<RenderingTopic>,
    mayInterruptIfRunning: Boolean,
  ): Int {
    var numberOfCancelledActions = 0
    pendingActionsQueueLock.withLock {
      for (topic in topicsToCancel) {
        pendingActionsQueueByTopic[topic]?.let { queue ->
          while (queue.isNotEmpty()) {
            val removed = queue.remove()
            allPendingActionsQueue.remove(removed)
            removed.cancel(false)
            numberOfCancelledActions++
          }
        }
      }
    }
    runningRenderLock.withLock {
      runningRender?.let {
        if (it.renderingTopic in topicsToCancel) {
          it.cancel(mayInterruptIfRunning)
          numberOfCancelledActions++
        }
      }
    }
    return numberOfCancelledActions
  }

  @TestOnly
  fun shutdown(timeoutSeconds: Long) {
    shutdown()

    if (timeoutSeconds > 0) {
      try {
        renderingExecutorService.awaitTermination(timeoutSeconds, TimeUnit.SECONDS)
      } catch (ignored: InterruptedException) {
        Logger.getInstance(RenderExecutor::class.java)
          .warn("The RenderExecutor does not shutdown after $timeoutSeconds seconds")
      }
    }
  }

  @get:TestOnly
  val accumulatedTimeouts: Int
    get() = accumulatedTimeoutExceptions.get()

  @get:TestOnly
  val numPendingActions: Int
    get() = allPendingActionsQueue.size

  /** Returns true if the render thread is busy running some code, false otherwise. */
  fun isBusy() = renderingExecutorService.isBusy

  /** Returns true if called from the render thread. */
  fun isRenderThread(): Boolean = renderingExecutorService.hasSpawnedCurrentThread()

  companion object {
    @JvmStatic
    fun create(): RenderExecutor {
      val scheduledExecutorService =
        ScheduledThreadPoolExecutor(1).also { it.removeOnCancelPolicy = true }
      return RenderExecutor(
        DEFAULT_MAX_QUEUED_TASKS,
        renderingExecutorService =
          SingleThreadExecutorService.create(
            "Layoutlib Render Thread",
            ThreadProfileSettings(
              scheduledExecutorService = scheduledExecutorService,
              onSlowThread = { Logger.getInstance(RenderExecutor::class.java).warn(it) },
            ),
          ),
        scheduledExecutorService = scheduledExecutorService,
      )
    }

    @TestOnly
    fun createForTests(
      executorService: SingleThreadExecutorService,
      scheduledExecutorService: ScheduledExecutorService,
    ) = RenderExecutor(DEFAULT_MAX_QUEUED_TASKS, executorService, scheduledExecutorService)
  }

  /**
   * [Runnable] with a priority, which is [Comparable]. Ordering for those runnables puts first the
   * ones with highest priority. In case of equal priority, this puts first the one created first.
   *
   * This is to be used in the priority queue of the [RenderExecutor], so that the executor will
   * execute first the runnable with the highest priority.
   */
  private class PriorityRunnable(val renderingTopic: RenderingTopic, val runnable: Runnable) :
    Runnable, Comparable<PriorityRunnable> {

    private val creationTime = System.currentTimeMillis()

    override fun run() {
      runnable.run()
    }

    override fun compareTo(other: PriorityRunnable): Int {
      // Minus sign as we want the highest priority first
      val priorityComparison = -renderingTopic.priority.compareTo(other.renderingTopic.priority)
      if (priorityComparison != 0) {
        return priorityComparison
      }
      return creationTime.compareTo(other.creationTime)
    }
  }

  /**
   * [CompletableFuture] with a priority, which is [Comparable]. Ordering for those futures puts
   * first the ones with the lowest priority. In case of equal priority, this puts first the one
   * created first.
   *
   * This is to be used in the pending action priority queue of the [RenderExecutor], so that, if
   * the queue reaches capacity, the lowest priority actions are removed first.
   *
   * The [renderingTopic] associates a completable with the tool or context in which the render is
   * happening, and it is used for grouping and cancelling purposes.
   */
  private open class PriorityCompletableFuture<T : Any?>(val renderingTopic: RenderingTopic) :
    Comparable<PriorityCompletableFuture<Any?>>, CompletableFuture<T>() {

    private val creationTime = System.currentTimeMillis()

    override fun compareTo(other: PriorityCompletableFuture<Any?>): Int {
      // Plus sign as we want the lowest priority first to be removed from the wait list when
      // reaching max
      val priorityComparison = renderingTopic.priority.compareTo(other.renderingTopic.priority)
      if (priorityComparison != 0) {
        return priorityComparison
      }
      return creationTime.compareTo(other.creationTime)
    }
  }
}
