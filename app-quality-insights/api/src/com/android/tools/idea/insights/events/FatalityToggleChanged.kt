/*
 * Copyright (C) 2022 The Android Open Source Project
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
import com.android.tools.idea.insights.FailureType
import com.android.tools.idea.insights.InsightsProvider
import com.android.tools.idea.insights.LoadingState
import com.android.tools.idea.insights.analytics.AppInsightsTracker
import com.android.tools.idea.insights.client.AppInsightsCache
import com.android.tools.idea.insights.events.actions.Action
import com.google.wireless.android.sdk.stats.AppQualityInsightsUsageEvent

/** Fatality Toggle changed. */
data class FatalityToggleChanged(val fatality: FailureType) : ChangeEvent {
  override fun transition(
    state: AppInsightsState,
    tracker: AppInsightsTracker,
    provider: InsightsProvider,
    cache: AppInsightsCache,
  ): StateTransition<Action> {
    val newState = state.toggleFatality(fatality)

    if (newState == state) {
      return StateTransition(newState, Action.NONE)
    }
    return StateTransition(
      newState.copy(
        issues = LoadingState.Loading,
        currentIssueVariants = LoadingState.Ready(null),
        currentIssueDetails = LoadingState.Ready(null),
        currentNotes = LoadingState.Ready(null),
      ),
      action =
        Action.Fetch(AppQualityInsightsUsageEvent.AppQualityInsightsFetchDetails.FetchSource.FILTER),
    )
  }
}
