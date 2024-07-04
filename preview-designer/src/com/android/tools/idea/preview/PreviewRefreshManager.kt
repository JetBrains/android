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
import com.android.tools.idea.concurrency.wrapCompletableDeferredCollection
import com.android.tools.idea.preview.analytics.PreviewRefreshEventBuilder
import com.android.tools.rendering.RenderAsyncActionExecutor.RenderingTopic
import com.android.tools.rendering.RenderService
import com.google.wireless.android.sdk.stats.PreviewRefreshEvent
import com.intellij.ide.ActivityTracker
import com.intellij.openapi.diagnostic.Logger
import java.util.Collections
import java.util.PriorityQueue
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.jetbrains.android.AndroidPluginDisposable
import org.jetbrains.annotations.TestOnly

/**
 * Enum re-used from the metrics package to avoid needing to duplicate code and manually map the
 * enum values.
 */
typealias RefreshResult = PreviewRefreshEvent.RefreshResult

interface RefreshType {
  val priority: Int
}

/**
 * Base interface needed for using a [PreviewRefreshManager].
 *
 * See each val/fun documentation for more details.
 */
interface PreviewRefreshRequest : Comparable<PreviewRefreshRequest> {
  /** Optional [PreviewRefreshEventBuilder] used for tracking refresh metrics */
  val refreshEventBuilder: PreviewRefreshEventBuilder?

  /**
   * Identification used for grouping and cancelling requests.
   *
   * This provides to the tools using a [PreviewRefreshManager] the ability to define their own
   * cancellation granularity. For example, a per-file granularity could be implemented by using as
   * [clientId] the fully qualified name of the file for which a refresh is requested.
   *
   * See [doRefresh] and [onSkip] for more details.
   */
  val clientId: String

  /**
   * Priority value used by the [PreviewRefreshManager] to:
   * - Sort the pending requests
   * - Cancel or skip requests (see [doRefresh] and [onSkip])
   */
  val refreshType: RefreshType

  override fun compareTo(other: PreviewRefreshRequest): Int {
    return refreshType.priority.compareTo(other.refreshType.priority)
  }

  /**
   * Method called when it is time for this request to actually be executed.
   *
   * If the returned [Job] is cancelled, either by the [PreviewRefreshManager] or by the user, then
   * the [PreviewRefreshManager] will cancel all rendering actions for it's
   * [PreviewRefreshManager.topic].
   *
   * Note the [PreviewRefreshManager] will cancel this running refresh request when a newer request
   * comes in with the same client id and with higher than or equal priority to this one.
   */
  fun doRefresh(): Job

  /**
   * Method called when this refresh has completed (i.e. when the [Job] returned by [doRefresh] has
   * completed).
   *
   * The [PreviewRefreshManager] assumes that it is safe to execute another request right after this
   * method returns, so here is where the cleanup of shared resources between requests should
   * probably happen.
   *
   * Note that this will never be used on skipped requests.
   */
  fun onRefreshCompleted(result: RefreshResult, throwable: Throwable?)

  /**
   * Method called when a request is skipped by the [PreviewRefreshManager] due to another request
   * with the same [clientId] that has higher priority, or that has equal priority but is newer.
   *
   * Note that this request hasn't started to execute and will never execute after this [onSkip] is
   * called. This method is used by the [PreviewRefreshManager] to notify this situation to the
   * corresponding request.
   */
  fun onSkip(replacedBy: PreviewRefreshRequest)
}

enum class CommonPreviewRefreshType(override val priority: Int) : RefreshType {
  /** Previews are inflated and rendered. */
  NORMAL(1),

  /** The existing Previews that need a quality change are re-rendered, but no inflation is done. */
  QUALITY(0),
}

/**
 * A common implementation of [PreviewRefreshRequest].
 *
 * @param clientId see [PreviewRefreshRequest.clientId]
 * @param refreshType a [RefreshType] value used for prioritizing the requests and that could
 *   influence the logic in [delegateRefresh].
 * @param delegateRefresh method responsible for performing the refresh
 * @param onRefreshCompleted optional completable that will be completed once the refresh is
 *   completed. If the request is skipped and replaced with another one, then this completable will
 *   be completed when the other one is completed. If the refresh gets cancelled, this completable
 *   will be completed exceptionally.
 * @param refreshEventBuilder see [PreviewRefreshRequest.refreshEventBuilder]
 * @param requestId identifier used for testing and logging/debugging.
 */
class CommonPreviewRefreshRequest(
  override val clientId: String,
  override val refreshType: RefreshType,
  private val delegateRefresh: (PreviewRefreshRequest) -> Job,
  private var onRefreshCompleted: CompletableDeferred<Unit>? = null,
  override val refreshEventBuilder: PreviewRefreshEventBuilder? = null,
  val requestId: String = UUID.randomUUID().toString().substring(0, 5),
) : PreviewRefreshRequest {
  override fun doRefresh(): Job {
    val refreshJob = delegateRefresh(this)
    // If the deferred is cancelled, cancel the refresh Job too
    onRefreshCompleted?.invokeOnCompletion {
      if (it is CancellationException) refreshJob.cancel(it)
    }
    return refreshJob
  }

  override fun onRefreshCompleted(result: RefreshResult, throwable: Throwable?) {
    onRefreshCompleted?.let {
      if (throwable == null) it.complete(Unit) else it.completeExceptionally(throwable)
    }
  }

  override fun onSkip(replacedBy: PreviewRefreshRequest) {
    (replacedBy as? CommonPreviewRefreshRequest)?.let {
      it.onRefreshCompleted =
        wrapCompletableDeferredCollection(
          listOfNotNull(replacedBy.onRefreshCompleted, onRefreshCompleted)
        )
    }
  }
}

/**
 * A refresh manager that receives [PreviewRefreshRequest]s for a given [RenderingTopic] and
 * coordinates their execution applying the following rules:
 * - Group the requests by their [PreviewRefreshRequest.clientId], and keep at most 1 request per
 *   client in the queue (see [PreviewRefreshRequest.onSkip]).
 * - Cancel running requests if outdated (see [PreviewRefreshRequest.doRefresh]). When a request is
 *   cancelled, either because it is outdated or because the user cancelled the
 *   [PreviewRefreshRequest.doRefresh] job, all running and pending renders on the manager's
 *   [RenderingTopic] are cancelled to allow any new refresh request to start straight away.
 * - Delegate prioritization to the requests (see [PreviewRefreshRequest.compareTo])
 */
class PreviewRefreshManager
private constructor(private val scope: CoroutineScope, private val topic: RenderingTopic) {
  private val log = Logger.getInstance(PreviewRefreshManager::class.java)

  private val requestsFlow: MutableSharedFlow<Unit> =
    MutableSharedFlow(replay = 1, extraBufferCapacity = 1)

  private val requestsLock: ReentrantLock = ReentrantLock()

  @GuardedBy("requestsLock")
  private val pendingRequestsPerClient: MutableMap<String, PreviewRefreshRequest> = mutableMapOf()

  @GuardedBy("requestsLock")
  private val allPendingRequests: PriorityQueue<PreviewRefreshRequest> =
    PriorityQueue(Collections.reverseOrder()) // higher first

  @GuardedBy("requestsLock") private var runningRequest: PreviewRefreshRequest? = null
  @GuardedBy("requestsLock") private var runningJob: Job? = null

  private val _refreshingTypeFlow = MutableStateFlow<RefreshType?>(null)

  /**
   * This flow indicates the [RefreshType] of the request that is being processed, or it indicates
   * that no request is being processed when its value is null.
   */
  val refreshingTypeFlow: StateFlow<RefreshType?> = _refreshingTypeFlow

  init {
    scope.launch(AndroidDispatchers.workerThread) {
      requestsFlow.collect {
        val lazyWrapperJob: Job
        var currentRefreshJob: Job? = null
        val currentRequest: PreviewRefreshRequest
        requestsLock.withLock {
          if (allPendingRequests.isEmpty()) {
            _refreshingTypeFlow.value = null
            return@collect
          }
          currentRequest = allPendingRequests.remove()
          pendingRequestsPerClient.remove(currentRequest.clientId)
          // Don't start the refresh inside the requestsLock to avoid
          // a potential deadlock with the UI thread.
          lazyWrapperJob =
            launch(start = CoroutineStart.LAZY) {
              currentRefreshJob = currentRequest.doRefresh()
              currentRefreshJob!!.join()
            }
          currentRequest.refreshEventBuilder?.onRefreshStarted()
          runningRequest = currentRequest
          runningJob = lazyWrapperJob
        }

        try {
          _refreshingTypeFlow.value = currentRequest.refreshType
          lazyWrapperJob.invokeOnCompletion {
            if (it != null) {
              currentRefreshJob?.cancel(if (it is CancellationException) it else null)
            }
            val result =
              when {
                it == null && currentRefreshJob?.isCancelled == false -> RefreshResult.SUCCESS
                it is CancellationException -> RefreshResult.AUTOMATICALLY_CANCELLED
                currentRefreshJob?.isCancelled == true -> RefreshResult.USER_CANCELLED
                else -> RefreshResult.FAILED
              }
            currentRequest.onRefreshCompleted(result, it)
            currentRequest.refreshEventBuilder?.onRefreshCompleted(result)
            if (result != RefreshResult.SUCCESS) {
              // Force stop any running and pending renders when a cancellation or failure happens
              // so that everything is ready for a new refresh that may start right away.
              RenderService.getRenderAsyncActionExecutor().cancelActionsByTopic(listOf(topic), true)
            }
            // Log unexpected failures
            if (result == RefreshResult.FAILED) {
              log.warn("Failed refresh request ($currentRequest)", it)
            }
            // Force actions update, so the Preview Status action UI is redrawn with the new status.
            // This is needed because the status is changed without any user interaction. See the
            // documentation of com.intellij.openapi.actionSystem.AnAction#update() for details.
            ActivityTracker.getInstance().inc()
          }
          lazyWrapperJob.join()
          currentRefreshJob?.join()
        } finally {
          requestsLock.withLock {
            runningRequest = null
            runningJob = null
          }
          // When a request is found and processed, it is possible that another
          // one is awaiting in the queue, so try to emit to the flow to check.
          requestsFlow.tryEmit(Unit)
        }
      }
    }
  }

  /** Add a [request] to the queue asynchronously. */
  fun requestRefresh(request: PreviewRefreshRequest) {
    doRequestRefresh(request)
  }

  /**
   * Add a [request] to the queue asynchronously.
   *
   * Returns the job executing the enqueueing of this request, which is not related with how the
   * refresh manager will later decide to process the request itself.
   *
   * This is only intended to be used for synchronization purposes inside tests.
   */
  @TestOnly
  fun requestRefreshForTest(request: PreviewRefreshRequest): Job = doRequestRefresh(request)

  private fun doRequestRefresh(request: PreviewRefreshRequest): Job {
    val enqueueingJob =
      scope.launch(AndroidDispatchers.workerThread) {
        requestsLock.withLock {
          // If the running request is of the same client and has lower than
          // or equal priority to the new one, then it should be cancelled.
          runningRequest?.let {
            if (it.clientId == request.clientId && it <= request) {
              runningJob!!.cancel(
                CancellationException(
                  "Outdated, a refresh was replaced by a newer one (${request.clientId})"
                )
              )
            }
          }

          val currentPendingRequestOfClient = pendingRequestsPerClient[request.clientId]
          // If the pending request of the request's client has higher priority,
          // then skip the new one, otherwise skip the old one.
          if (currentPendingRequestOfClient != null && currentPendingRequestOfClient > request) {
            request.onSkip(currentPendingRequestOfClient)
            request.refreshEventBuilder?.onRequestSkipped()
          } else {
            currentPendingRequestOfClient?.let {
              it.onSkip(request)
              it.refreshEventBuilder?.onRequestSkipped()
              allPendingRequests.remove(it)
            }
            pendingRequestsPerClient[request.clientId] = request
            allPendingRequests.add(request)
            request.refreshEventBuilder?.onRequestEnqueued()
          }
          requestsFlow.tryEmit(Unit)
        }
      }
    enqueueingJob.invokeOnCompletion {
      if (it != null) {
        log.warn("Failure while trying to enqueue a new refresh request ($request)", it)
      }
    }
    return enqueueingJob
  }

  @TestOnly fun getTotalRequestsInQueueForTest() = requestsLock.withLock { allPendingRequests.size }

  companion object {
    private val coroutineScope =
      AndroidCoroutineScope(AndroidPluginDisposable.getApplicationInstance())
    private val managersByTopicLock = ReentrantLock()
    @GuardedBy("managersByTopicLock")
    private val managersByTopic = mutableMapOf<RenderingTopic, PreviewRefreshManager>()

    fun getInstance(topic: RenderingTopic): PreviewRefreshManager {
      return managersByTopicLock.withLock {
        managersByTopic.computeIfAbsent(topic) { PreviewRefreshManager(coroutineScope, topic) }
        managersByTopic.getValue(topic)
      }
    }

    @TestOnly
    fun getInstanceForTest(scope: CoroutineScope, topic: RenderingTopic) =
      PreviewRefreshManager(scope, topic)
  }
}
