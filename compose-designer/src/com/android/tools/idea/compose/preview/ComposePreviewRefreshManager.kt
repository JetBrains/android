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
import com.android.tools.idea.preview.RefreshResult
import com.android.tools.idea.preview.RefreshType
import com.android.tools.rendering.RenderAsyncActionExecutor
import com.android.tools.rendering.RenderService
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

enum class ComposePreviewRefreshType(override val priority: Int) : RefreshType {
  /** Previews are inflated and rendered. */
  NORMAL(3),

  /**
   * Previews from the same Composable are not re-inflated (i.e. only new Previews are inflated).
   * See [ComposePreviewRepresentation.requestRefresh].
   */
  QUICK(2),

  /** The existing Previews that need a quality change are re-rendered, but no inflation is done. */
  QUALITY(1),

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
 * @param refreshType a [ComposePreviewRefreshType] value used for prioritizing the requests and
 *   that could influence the logic in [delegateRefresh].
 * @param requestId identifier used for testing and logging/debugging.
 */
class ComposePreviewRefreshRequest(
  override val clientId: String,
  private val delegateRefresh: (ComposePreviewRefreshRequest) -> Job,
  private var completableDeferred: CompletableDeferred<Unit>?,
  override val refreshType: ComposePreviewRefreshType,
  val requestId: String = UUID.randomUUID().toString().substring(0, 5)
) : PreviewRefreshRequest {

  var requestSources: List<Throwable> = listOf(Throwable())
    private set

  override fun doRefresh(): Job {
    val refreshJob = delegateRefresh(this)
    // If the deferred is cancelled, cancel the refresh Job too
    completableDeferred?.invokeOnCompletion {
      if (it is CancellationException) refreshJob.cancel(it)
    }
    return refreshJob
  }

  override fun onRefreshCompleted(result: RefreshResult, throwable: Throwable?) {
    completableDeferred?.let {
      if (throwable == null) it.complete(Unit) else it.completeExceptionally(throwable)
    }

    if (result == RefreshResult.CANCELLED) {
      // Force stop any running and pending renders so that everything is ready
      // for a new refresh that may start right away.
      RenderService.getRenderAsyncActionExecutor()
        .cancelActionsByTopic(
          listOf(RenderAsyncActionExecutor.RenderingTopic.COMPOSE_PREVIEW),
          true
        )
    }
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

  val refreshingTypeFlow: StateFlow<RefreshType?> = refreshManager.refreshingTypeFlow

  fun requestRefresh(request: ComposePreviewRefreshRequest) {
    refreshManager.requestRefresh(request)
  }

  override fun dispose() {
    scope.cancel()
  }
}
