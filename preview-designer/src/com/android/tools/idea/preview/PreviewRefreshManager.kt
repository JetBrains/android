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
import com.android.tools.idea.concurrency.AndroidDispatchers
import java.util.Collections
import java.util.PriorityQueue
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

/**
 * Base interface needed for using a [PreviewRefreshManager].
 *
 * See each val/fun documentation for more details.
 */
interface PreviewRefreshRequest : Comparable<PreviewRefreshRequest> {
  /**
   * Identification used for grouping and cancelling requests.
   *
   * This provides to the tools using a [PreviewRefreshManager] the ability to define their own
   * cancellation granularity. For example, a per-file granularity could be implemented by using as
   * [clientId] the fully qualified name of the file for which a refresh is requested.
   *
   * See [cancel] and [onSkip] for more details.
   */
  val clientId: String

  /**
   * Priority value used by the [PreviewRefreshManager] to:
   * - Sort the pending requests
   * - Cancel or skip requests (see [cancel] and [onSkip])
   */
  val priority: Int

  override fun compareTo(other: PreviewRefreshRequest): Int {
    return priority.compareTo(other.priority)
  }

  /**
   * Method called when it is time for this request to actually be executed.
   *
   * Note that the [PreviewRefreshManager] assumes that it is safe to execute another request after
   * this one, exactly when this method finishes.
   */
  suspend fun doRefresh()

  /**
   * Method called when the [PreviewRefreshManager] detects that a running request needs to be
   * cancelled due to a newer request with the same client id and with higher than or equal priority
   * to this one.
   *
   * Note that the [PreviewRefreshManager] detects the need of cancelling the request, and uses this
   * [cancel] method to do it. Meaning that this method is responsible for properly cancelling this
   * running request.
   */
  fun cancel(cause: String)

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

/**
 * A refresh manager that receives [PreviewRefreshRequest]s and coordinates their execution applying
 * the following rules:
 * - Group the requests by their [PreviewRefreshRequest.clientId], and keep at most 1 request per
 *   client in the queue (see [PreviewRefreshRequest.onSkip]).
 * - Cancel running requests if outdated (see [PreviewRefreshRequest.cancel])
 * - Delegate prioritization to the requests (see [PreviewRefreshRequest.compareTo])
 */
class PreviewRefreshManager(private val scope: CoroutineScope) {
  private val requestsFlow: MutableSharedFlow<Unit> =
    MutableSharedFlow(replay = 1, extraBufferCapacity = 1)

  private val requestsLock: ReentrantLock = ReentrantLock()

  @GuardedBy("requestsLock")
  private val pendingRequestsPerClient: MutableMap<String, PreviewRefreshRequest> = mutableMapOf()

  @GuardedBy("requestsLock")
  private val allPendingRequests: PriorityQueue<PreviewRefreshRequest> =
    PriorityQueue(Collections.reverseOrder()) // higher first

  @GuardedBy("requestsLock") private var runningRequest: PreviewRefreshRequest? = null

  init {
    scope.launch(AndroidDispatchers.workerThread) {
      requestsFlow.collect {
        var requestToRun: PreviewRefreshRequest
        requestsLock.withLock {
          if (allPendingRequests.isEmpty()) return@collect
          requestToRun = allPendingRequests.remove()
          runningRequest = requestToRun
          pendingRequestsPerClient.remove(requestToRun.clientId)
        }

        try {
          requestToRun.doRefresh()
        } finally {
          requestsLock.withLock { runningRequest = null }
          // When a request is found and processed, it is possible that another
          // one is awaiting in the queue, so try to emit to the flow to check.
          requestsFlow.tryEmit(Unit)
        }
      }
    }
  }

  fun requestRefresh(request: PreviewRefreshRequest) {
    var requestToCancel: PreviewRefreshRequest? = null
    requestsLock.withLock {
      // If the running request is of the same client and has lower than
      // or equal priority to the new one, then it should be cancelled.
      runningRequest?.let {
        if (it.clientId == request.clientId && it <= request) {
          requestToCancel = runningRequest
          runningRequest = null
        }
      }

      val currentPendingRequestOfClient = pendingRequestsPerClient[request.clientId]
      // If the pending request of the request's client has higher priority,
      // then skip the new one, otherwise skip the old one.
      if (currentPendingRequestOfClient != null && currentPendingRequestOfClient > request) {
        request.onSkip(currentPendingRequestOfClient)
      } else {
        currentPendingRequestOfClient?.let {
          it.onSkip(request)
          allPendingRequests.remove(it)
        }
        pendingRequestsPerClient[request.clientId] = request
        allPendingRequests.add(request)
      }
    }
    requestsFlow.tryEmit(Unit)
    requestToCancel?.cancel(
      "Outdated, this refresh request was replaced by a newer one (${request.clientId})"
    )
  }
}
