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

import com.intellij.openapi.application.ApplicationManager
import java.util.Arrays
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors

/** Interface to be implemented by executors of rendered async actions. */
interface RenderAsyncActionExecutor {
  /** Returns the number of render actions that this executor has executed. */
  val executedRenderActionCount: Long

  /**
   * Runs an action that requires the rendering lock. Layoutlib is not thread safe so any rendering
   * actions should be called using this method. This method will run the passed action
   * asynchronously and return a [CompletableFuture].
   *
   * @param queueingTimeout maximum timeout for this action to wait to be executed.
   * @param queueingTimeoutUnit [TimeUnit] for queueingTimeout.
   * @param actionTimeout maximum timeout for this action to executed once it has started running.
   * @param actionTimeoutUnit [TimeUnit] for actionTimeout.
   * @param renderingTopic enum representing context in which the render is happening and its
   *   priority.
   * @param callable [Callable] to be executed with the render action.
   * @param <T> return type of the given callable.
   */
  fun <T> runAsyncActionWithTimeout(
    queueingTimeout: Long,
    queueingTimeoutUnit: TimeUnit,
    actionTimeout: Long,
    actionTimeoutUnit: TimeUnit,
    renderingTopic: RenderingTopic,
    callable: Callable<T>,
  ): CompletableFuture<T>

  /**
   * Runs an action that requires the rendering lock. Layoutlib is not thread safe so any rendering
   * actions should be called using this method. This method will run the passed action
   * asynchronously and return a [CompletableFuture].
   *
   * @param actionTimeout maximum timeout for this action to executed once it has started running.
   * @param actionTimeoutUnit [TimeUnit] for actionTimeout.
   * @param renderingTopic enum representing context in which the render is happening and its
   *   priority.
   * @param callable [Callable] to be executed with the render action.
   * @param <T> return type of the given callable.
   */
  fun <T> runAsyncActionWithTimeout(
    actionTimeout: Long,
    actionTimeoutUnit: TimeUnit,
    renderingTopic: RenderingTopic,
    callable: Callable<T>,
  ): CompletableFuture<T> {
    return runAsyncActionWithTimeout(
      DEFAULT_RENDER_THREAD_QUEUE_TIMEOUT_MS,
      TimeUnit.MILLISECONDS,
      actionTimeout,
      actionTimeoutUnit,
      renderingTopic,
      callable,
    )
  }

  /**
   * Runs an action that requires the rendering lock. Layoutlib is not thread safe so any rendering
   * actions should be called using this method. This method will run the passed action
   * asynchronously and return a [CompletableFuture].
   */
  fun <T> runAsyncAction(callable: Callable<T>): CompletableFuture<T> {
    return runAsyncActionWithTimeout(
      DEFAULT_RENDER_THREAD_QUEUE_TIMEOUT_MS,
      TimeUnit.MILLISECONDS,
      DEFAULT_RENDER_THREAD_TIMEOUT_MS,
      TimeUnit.MILLISECONDS,
      RenderingTopic.NOT_SPECIFIED,
      callable,
    )
  }

  /**
   * Runs an action that requires the rendering lock. Layoutlib is not thread safe so any rendering
   * actions should be called using this method. This method will run the passed action
   * asynchronously and return a [CompletableFuture].
   */
  fun <T> runAsyncAction(
    renderingTopic: RenderingTopic,
    callable: Callable<T>,
  ): CompletableFuture<T> {
    return runAsyncActionWithTimeout(
      DEFAULT_RENDER_THREAD_QUEUE_TIMEOUT_MS,
      TimeUnit.MILLISECONDS,
      DEFAULT_RENDER_THREAD_TIMEOUT_MS,
      TimeUnit.MILLISECONDS,
      renderingTopic,
      callable,
    )
  }

  /**
   * Runs an action that requires the rendering lock. Layoutlib is not thread safe so any rendering
   * actions should be called using this method. This method will run the passed action
   * asynchronously.
   */
  fun runAsyncAction(runnable: Runnable): CompletableFuture<Void?> {
    return runAsyncAction<Void?>(RenderingTopic.NOT_SPECIFIED) {
      runnable.run()
      null
    }
  }

  /**
   * Runs an action that requires the rendering lock. Layoutlib is not thread safe so any rendering
   * actions should be called using this method/ This method will run the passed action
   * asynchronously.
   */
  fun runAsyncAction(renderingTopic: RenderingTopic, runnable: Runnable): CompletableFuture<Void?> {
    return runAsyncAction(
      renderingTopic,
      Callable {
        runnable.run()
        return@Callable null
      },
    )
  }

  /** Cancels all pending actions of the given topics. Returns the number of cancelled actions. */
  fun cancelActionsByTopic(
    topicsToCancel: List<RenderingTopic>,
    mayInterruptIfRunning: Boolean,
  ): Int

  /**
   * Cancels all pending actions with rendering priority lower than or equal to minPriority,
   * regardless of their topic. Returns the number of cancelled actions.
   */
  fun cancelLowerPriorityActions(minPriority: Int, mayInterruptIfRunning: Boolean): Int {
    return cancelActionsByTopic(
      Arrays.stream(RenderingTopic.values())
        .filter { topic: RenderingTopic -> topic.priority <= minPriority }
        .collect(Collectors.toList()),
      mayInterruptIfRunning,
    )
  }

  /**
   * Enum representing the context or tool in which a render is happening and the priority that the
   * RenderExecutor should apply to run the action.
   */
  enum class RenderingTopic(val value: String, val priority: Int) {
    // Topic used for actions related with disposing or freeing resources.
    CLEAN("Clean", 200),

    // Topic used by default when the tool/context doesn't specify one.
    NOT_SPECIFIED("Not specified", 100),
    COMPOSE_PREVIEW("Compose preview", 100),
    WEAR_TILE_PREVIEW("Wear tile preview", 100),
    GLANCE_PREVIEW("Glance preview", 100),
    VISUAL_LINT("Visual lint", 1),
  }

  companion object {
    /** Number of ms that we will wait for the rendering thread to return before timing out */
    @JvmField
    val DEFAULT_RENDER_THREAD_TIMEOUT_MS: Long =
      java.lang.Long.getLong("layoutlib.thread.timeout", TimeUnit.SECONDS.toMillis(10))

    @JvmField
    val DEFAULT_RENDER_THREAD_QUEUE_TIMEOUT_MS: Long =
      java.lang.Long.getLong(
        "layoutlib.thread.queue.timeout",
        TimeUnit.SECONDS.toMillis(
          (if (
              (ApplicationManager.getApplication() == null ||
                ApplicationManager.getApplication().isUnitTestMode)
            )
              50
            else 60)
            .toLong()
        ),
      )
  }
}
