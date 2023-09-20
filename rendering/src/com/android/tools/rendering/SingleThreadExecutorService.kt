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
package com.android.tools.rendering

import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

private val PROFILE_SLOW_TASKS_TIMEOUT_MS =
  Integer.getInteger("layoutlib.thread.profile.timeoutms", 4000)
private val PROFILE_INTERVAL_MS = Integer.getInteger("layoutlib.thread.profile.intervalms", 2500)

/** Settings for profiling a thread. */
data class ThreadProfileSettings(
  /** Ms to wait before considering a running task as slow and start profiling. */
  val profileSlowTasksTimeoutMs: Long = PROFILE_SLOW_TASKS_TIMEOUT_MS.toLong(),
  /** Ms in between profiling. */
  val profileIntervalMs: Long = PROFILE_INTERVAL_MS.toLong(),
  /** Number of samples to take. If 0, the profiling will not run. */
  val maxSamples: Int = 3,
  /** An [ScheduledExecutorService] used to execute the profiling actions. */
  val scheduledExecutorService: ScheduledExecutorService,
  /** Callback called with every profiled sample. */
  val onSlowThread: (Throwable) -> Unit
) {
  companion object {
    val disabled =
      ThreadProfileSettings(
        maxSamples = 0,
        scheduledExecutorService = ScheduledThreadPoolExecutor(0),
        onSlowThread = {}
      )
  }
}

/**
 * Interface for a single threaded executor service with additional functionality to allow for
 * interrupting the thread and checking whether is active or not.
 */
interface SingleThreadExecutorService : ExecutorService {
  /** True if busy and a new command will not execute immediately. */
  val isBusy: Boolean

  /** Returns true if called from the thread that is handled by this [ExecutorService]. */
  fun hasSpawnedCurrentThread(): Boolean

  /**
   * Returns the current stack thread for the thread executing commands. The stack trace might be
   * empty if the thread is not currently running.
   */
  fun stackTrace(): Array<StackTraceElement>

  /** Sends an interrupt to the thread and returns immediately. */
  fun interrupt()

  companion object {
    /**
     * Creates a new single thread [ExecutorService] with optional profiling. The [threadName] will
     * name the thread used by this executor.
     */
    fun create(
      threadName: String,
      threadProfileSettings: ThreadProfileSettings
    ): SingleThreadExecutorService =
      ProfilingSingleThreadExecutorImpl(threadName, threadProfileSettings)
  }
}

private class ProfilingSingleThreadExecutorImpl(
  threadName: String,
  private val threadProfileSettings: ThreadProfileSettings
) : SingleThreadExecutorService, AbstractExecutorService() {
  private val theThread = AtomicReference<Thread?>()
  private val _isBusy = AtomicBoolean(false)

  override val isBusy: Boolean
    get() = _isBusy.get()

  private val renderThreadGroup = ThreadGroup("Render Thread group")

  /**
   * The thread factory allows us controlling when the new thread is created to we can keep track of
   * it. This allows us to capture the stack trace later.
   */
  private val threadFactory = ThreadFactory {
    val newThread = Thread(renderThreadGroup, it, threadName).apply { isDaemon = true }
    theThread.set(newThread)
    newThread
  }

  private fun profileThread(remainingSamples: Int, profileThreadFuture: CompletableFuture<Unit>) {
    if (remainingSamples <= 0) return
    if (!isBusy) return
    threadProfileSettings.onSlowThread(
      TimeoutException(
          "Slow render action ${threadProfileSettings.maxSamples-remainingSamples}/${threadProfileSettings.maxSamples}"
        )
        .also { slowException ->
          theThread.get()?.also { slowException.stackTrace = it.stackTrace }
        }
    )
    threadProfileSettings.scheduledExecutorService
      .schedule(
        { profileThread(remainingSamples - 1, profileThreadFuture) },
        threadProfileSettings.profileIntervalMs,
        TimeUnit.MILLISECONDS
      )
      .also { scheduledFuture ->
        profileThreadFuture.whenComplete { _, _ -> scheduledFuture.cancel(true) }
      }
  }

  private var profileThreadFuture: CompletableFuture<Unit>? = null
  private val threadPoolExecutor =
    object :
      ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, PriorityBlockingQueue(), threadFactory) {
      override fun beforeExecute(t: Thread?, r: Runnable?) {
        _isBusy.set(true)
        profileThreadFuture = CompletableFuture()
        threadProfileSettings.scheduledExecutorService
          .schedule(
            { profileThread(threadProfileSettings.maxSamples, profileThreadFuture!!) },
            threadProfileSettings.profileSlowTasksTimeoutMs,
            TimeUnit.MILLISECONDS
          )
          .also { scheduledFuture ->
            profileThreadFuture!!.whenComplete { _, _ -> scheduledFuture.cancel(true) }
          }
        super.beforeExecute(t, r)
      }

      override fun afterExecute(r: Runnable?, t: Throwable?) {
        val pendingProfiling = profileThreadFuture
        profileThreadFuture = null
        pendingProfiling?.cancel(true)
        _isBusy.set(false)
        super.afterExecute(r, t)
      }
    }

  override fun hasSpawnedCurrentThread(): Boolean =
    renderThreadGroup.parentOf(Thread.currentThread().threadGroup)

  override fun stackTrace(): Array<StackTraceElement> = theThread.get()?.stackTrace ?: emptyArray()

  override fun interrupt() {
    theThread.get()?.interrupt()
  }

  override fun execute(command: Runnable) {
    threadPoolExecutor.execute(command)
  }

  override fun shutdown() {
    threadPoolExecutor.shutdown()
    val currentThread = theThread.getAndSet(null)
    currentThread?.interrupt()
  }

  override fun shutdownNow(): MutableList<Runnable> = threadPoolExecutor.shutdownNow()

  override fun isShutdown(): Boolean = threadPoolExecutor.isShutdown

  override fun isTerminated(): Boolean = threadPoolExecutor.isTerminated

  override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean =
    threadPoolExecutor.awaitTermination(timeout, unit)
}
