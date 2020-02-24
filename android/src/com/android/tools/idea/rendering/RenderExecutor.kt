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

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Supplier

/** Number of ms that we will wait for the rendering thread to return before timing out  */
private val DEFAULT_RENDER_THREAD_TIMEOUT_MS = java.lang.Long.getLong("layoutlib.thread.timeout",
                                                                      TimeUnit.SECONDS.toMillis(
                                                                        if (ApplicationManager.getApplication().isUnitTestMode) 60 else 6.toLong()))

/**
 * Intended to be used for executing render tasks of layoutlib [RenderSession].
 * Currently, all calls to the layoutlib should be done from the same thread.
 * This executor guarantees that unit of work passed to [runAction] or [runAsyncAction]
 * will be executed sequentially from the same thread.
 */
class RenderExecutor {
  private val renderingThread = AtomicReference<Thread?>()
  private val renderingExecutor: ExecutorService
  private val timeoutExceptionCounter = AtomicInteger(0)

  private var isFirstCall = true

  init {
    renderingExecutor = ThreadPoolExecutor(1, 1,
                                           0, TimeUnit.MILLISECONDS,
                                           LinkedBlockingQueue(),
                                           ThreadFactory {
                                             val renderingThread =
                                               Thread(null, it, "Layoutlib Render Thread")
                                                 .apply { isDaemon = true }
                                             this.renderingThread.set(renderingThread)
                                             renderingThread
                                           })
  }

  fun shutdown() {
    renderingExecutor.shutdownNow()
    val currentThread = renderingThread.getAndSet(null)
    currentThread?.interrupt()
  }

  @Throws(Exception::class)
  fun <T> runAction(callable: Callable<T>): T {
    return try { // If the number of timeouts exceeds a certain threshold, stop waiting so the caller doesn't block. We try to submit a task that
      // clean-up the timeout counter instead. If it goes through, it means the queue is free.
      if (timeoutExceptionCounter.get() > 3) {
        renderingExecutor.submit { timeoutExceptionCounter.set(0) }.get(50, TimeUnit.MILLISECONDS)
      }
      var timeout = DEFAULT_RENDER_THREAD_TIMEOUT_MS
      if (isFirstCall) { // The initial call might be significantly slower since there is a lot of initialization done on the resource management side.
        // This covers that case.
        isFirstCall = false
        timeout *= 2
      }
      val result = renderingExecutor.submit(callable).get(timeout, TimeUnit.MILLISECONDS)
      // The executor seems to be taking tasks so reset the counter
      timeoutExceptionCounter.set(0)
      result
    }
    catch (e: TimeoutException) {
      timeoutExceptionCounter.incrementAndGet()
      val renderingThread = renderingThread.get()
      val timeoutException = TimeoutException(
"""
Preview timed out while rendering the layout.
This typically happens when there is an infinite loop or unbounded recursion in one of the custom views.
""")
      if (renderingThread != null) {
        timeoutException.stackTrace = renderingThread.stackTrace
      }
      throw timeoutException
    }
  }

  fun <T> runAsyncAction(callable: Supplier<T>): CompletableFuture<T> =
    CompletableFuture.supplyAsync(callable, renderingExecutor)

  fun runAsyncAction(runnable: Runnable) {
    renderingExecutor.submit(runnable)
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
}