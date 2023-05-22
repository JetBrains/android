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
package com.android.tools.idea.compose.preview

import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.wrapCompletableDeferredCollection
import com.android.tools.idea.preview.PreviewRefreshManager
import com.android.tools.idea.preview.PreviewRefreshRequest
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel

enum class RefreshType(internal val priority: Int) {
  /** Previews are inflated and rendered. */
  NORMAL(2),

  /**
   * Previews from the same Composable are not re-inflated. See
   * [ComposePreviewRepresentation.requestRefresh].
   */
  QUICK(1),

  /**
   * Previews are not rendered or inflated. This mode is just used to trace a request to, for
   * example, ensure there are no pending requests.
   */
  TRACE(0)
}

/**
 * A [PreviewRefreshRequest] specific for Compose Preview.
 *
 * @param clientId see [PreviewRefreshRequest.clientId]
 * @param delegateRefresh method responsible for performing the refresh
 * @param completableDeferred optional completable that will be completed once the refresh is
 *   completed. If the request is skipped and replaced with another one, then this completable will
 *   be completed when the other one is completed. If the refresh gets cancelled, this completable
 *   will be completed exceptionally.
 * @param type a [RefreshType] value used for prioritizing the requests and that could influence the
 *   logic in [delegateRefresh].
 * @param requestId identifier used for testing and logging/debugging.
 */
class ComposePreviewRefreshRequest(
  override val clientId: String,
  private val delegateRefresh: suspend (ComposePreviewRefreshRequest) -> Job,
  private var completableDeferred: CompletableDeferred<Unit>?,
  val type: RefreshType,
  val requestId: String = UUID.randomUUID().toString().substring(0, 5)
) : PreviewRefreshRequest {

  override val priority: Int = type.priority
  private var refreshJob: Job? = null
  var requestSources: List<Throwable> = listOf(Throwable())
    private set

  override suspend fun doRefresh() {
    refreshJob = delegateRefresh(this)
    // Link refreshJob and the completableDeferred so when one is cancelled the other one
    // is too.
    completableDeferred?.let { completableDeferred ->
      refreshJob!!.invokeOnCompletion { throwable ->
        if (throwable != null) {
          completableDeferred.completeExceptionally(throwable)
        } else completableDeferred.complete(Unit)
      }

      completableDeferred.invokeOnCompletion {
        // If the deferred is cancelled, cancel the refresh Job too
        if (it is CancellationException) refreshJob!!.cancel(it)
      }
    }
    refreshJob!!.join()
  }

  override fun cancel(cause: String) {
    refreshJob?.cancel(CancellationException(cause))
  }

  override fun onSkip(replacedBy: PreviewRefreshRequest) {
    (replacedBy as ComposePreviewRefreshRequest).completableDeferred =
      wrapCompletableDeferredCollection(
        listOfNotNull(replacedBy.completableDeferred, completableDeferred)
      )
    replacedBy.requestSources += requestSources
  }
}

/**
 * Service that receives all [ComposePreviewRefreshRequest]s from a project and delegates their
 * coordination and execution to a [PreviewRefreshManager].
 */
@Service(Service.Level.PROJECT)
class ComposePreviewRefreshManager private constructor() : Disposable {
  companion object {
    fun getInstance(project: Project): ComposePreviewRefreshManager {
      return project.getService(ComposePreviewRefreshManager::class.java)
    }
  }

  private val scope = AndroidCoroutineScope(this)

  private val refreshManager = PreviewRefreshManager(scope)

  fun requestRefresh(request: ComposePreviewRefreshRequest) {
    refreshManager.requestRefresh(request)
  }

  override fun dispose() {
    scope.cancel()
  }
}
