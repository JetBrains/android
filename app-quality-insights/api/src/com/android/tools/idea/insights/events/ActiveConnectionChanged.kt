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
import com.android.tools.idea.insights.LoadingState
import com.android.tools.idea.insights.MultiSelection
import com.android.tools.idea.insights.VariantConnection
import com.android.tools.idea.insights.analytics.AppInsightsTracker
import com.android.tools.idea.insights.events.actions.Action
import com.google.wireless.android.sdk.stats.AppQualityInsightsUsageEvent

/** Any change to the active connection is propagated here. */
data class ActiveConnectionChanged(val connection: VariantConnection) : ChangeEvent {

  override fun transition(
    state: AppInsightsState,
    tracker: AppInsightsTracker
  ): StateTransition<Action> {
    val newState = state.selectConnection(connection)
    if (newState == state) {
      return StateTransition(state, Action.NONE)
    }
    return StateTransition(
      newState.copy(
        issues = LoadingState.Loading,
        currentIssueDetails = LoadingState.Ready(null),
        currentNotes = LoadingState.Ready(null),
        // reset the version filter (implying Version.ALL) upon connection change.
        filters = state.filters.copy(versions = MultiSelection.emptySelection())
      ),
      action =
        Action.Fetch(
          AppQualityInsightsUsageEvent.AppQualityInsightsFetchDetails.FetchSource.PROJECT_SELECTION
        )
    )
  }
}
