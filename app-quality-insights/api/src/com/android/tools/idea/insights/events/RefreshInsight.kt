/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.insights.events

import com.android.tools.idea.insights.AppInsightsState
import com.android.tools.idea.insights.InsightsProviderKey
import com.android.tools.idea.insights.LoadingState
import com.android.tools.idea.insights.analytics.AppInsightsTracker
import com.android.tools.idea.insights.client.AppInsightsCache
import com.android.tools.idea.insights.events.actions.Action

private const val REGENERATING_INSIGHT = "Regenerating insight..."

class RefreshInsight(private val regenerateWithContext: Boolean) : ChangeEvent {
  override fun transition(
    state: AppInsightsState,
    tracker: AppInsightsTracker,
    key: InsightsProviderKey,
    cache: AppInsightsCache,
  ): StateTransition<Action> {
    val issue = state.selectedIssue

    return if (issue == null) {
      StateTransition(state, Action.NONE)
    } else {
      StateTransition(
        state.copy(
          currentInsight =
            LoadingState.Loading(if (regenerateWithContext) REGENERATING_INSIGHT else "")
        ),
        Action.FetchInsight(
          issue.id,
          state.selectedVariant?.id,
          issue.issueDetails.fatality,
          issue.sampleEvent,
        ),
      )
    }
  }
}
