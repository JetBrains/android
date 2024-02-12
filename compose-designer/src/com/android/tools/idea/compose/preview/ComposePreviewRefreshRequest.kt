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

import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.preview.CommonPreviewRefreshRequest
import com.android.tools.idea.preview.PreviewRefreshRequest
import com.android.tools.idea.preview.RefreshResult
import com.android.tools.idea.preview.RefreshType
import com.android.tools.idea.preview.analytics.PreviewRefreshEventBuilder
import com.android.tools.idea.preview.analytics.PreviewRefreshTracker
import com.google.wireless.android.sdk.stats.PreviewRefreshEvent
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job

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
}

/**
 * A [PreviewRefreshRequest] specific for Compose Preview.
 *
 * @param surface the surface where the previews are located. Actually used for finding the
 *   application id when tracking refresh metrics.
 * @param clientId see [PreviewRefreshRequest.clientId]
 * @param delegateRefresh method responsible for performing the refresh
 * @param onRefreshCompleted optional completable that will be completed once the refresh is
 *   completed. If the request is skipped and replaced with another one, then this completable will
 *   be completed when the other one is completed. If the refresh gets cancelled, this completable
 *   will be completed exceptionally.
 * @param refreshType a [ComposePreviewRefreshType] value used for prioritizing the requests and
 *   that could influence the logic in [delegateRefresh].
 * @param requestId identifier used for testing and logging/debugging.
 */
class ComposePreviewRefreshRequest(
  surface: DesignSurface<*>?,
  override val clientId: String,
  private val delegateRefresh: (ComposePreviewRefreshRequest) -> Job,
  private var onRefreshCompleted: CompletableDeferred<Unit>?,
  override val refreshType: ComposePreviewRefreshType,
  requestId: String = UUID.randomUUID().toString().substring(0, 5),
) : PreviewRefreshRequest {

  private val delegate =
    CommonPreviewRefreshRequest(
      clientId = clientId,
      delegateRefresh = { delegateRefresh(this) },
      onRefreshCompleted = onRefreshCompleted,
      refreshType = refreshType,
      refreshEventBuilder =
        PreviewRefreshEventBuilder(
          PreviewRefreshEvent.PreviewType.COMPOSE,
          PreviewRefreshTracker.getInstance(surface),
        ),
      requestId = requestId,
    )

  val requestId = delegate.requestId

  override val refreshEventBuilder = delegate.refreshEventBuilder

  var requestSources: List<Throwable> = listOf(Throwable())
    private set

  override fun doRefresh() = delegate.doRefresh()

  override fun onRefreshCompleted(result: RefreshResult, throwable: Throwable?) =
    delegate.onRefreshCompleted(result, throwable)

  override fun onSkip(replacedBy: PreviewRefreshRequest) {
    delegate.onSkip((replacedBy as ComposePreviewRefreshRequest).delegate)
    replacedBy.requestSources += requestSources
  }
}
