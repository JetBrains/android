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
import com.android.tools.idea.insights.Version
import com.android.tools.idea.insights.analytics.AppInsightsTracker
import com.android.tools.idea.insights.events.actions.Action
import com.google.wireless.android.sdk.stats.AppQualityInsightsUsageEvent

/** Version changed. */
data class VersionsChanged(val versions: Set<Version>) : ChangeEvent {
  override fun transition(
    state: AppInsightsState,
    tracker: AppInsightsTracker
  ): StateTransition<Action> {
    val newState = state.selectVersions(versions)
    if (newState == state) {
      return StateTransition(newState, Action.NONE)
    }
    return StateTransition(
      newState.copy(
        issues = LoadingState.Loading,
        currentIssueDetails = LoadingState.Ready(null),
        currentNotes = LoadingState.Ready(null)
      ),
      action =
        Action.Fetch(AppQualityInsightsUsageEvent.AppQualityInsightsFetchDetails.FetchSource.FILTER)
    )
  }
}
