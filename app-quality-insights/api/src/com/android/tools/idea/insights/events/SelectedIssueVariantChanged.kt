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
package com.android.tools.idea.insights.events

import com.android.tools.idea.insights.AppInsightsState
import com.android.tools.idea.insights.InsightsProviderKey
import com.android.tools.idea.insights.IssueVariant
import com.android.tools.idea.insights.LoadingState
import com.android.tools.idea.insights.analytics.AppInsightsTracker
import com.android.tools.idea.insights.events.actions.Action

data class SelectedIssueVariantChanged(private val variant: IssueVariant?) : ChangeEvent {
  override fun transition(
    state: AppInsightsState,
    tracker: AppInsightsTracker,
    key: InsightsProviderKey,
  ): StateTransition<Action> {
    if (variant == state.selectedVariant) {
      return StateTransition(state, Action.NONE)
    }
    val selectedIssueId = state.selectedIssue?.id
    val shouldFetchDetails =
      state.currentIssueVariants is LoadingState.Ready && selectedIssueId != null
    return StateTransition(
      state.copy(
        currentIssueVariants = state.currentIssueVariants.map { it?.select(variant) },
        currentIssueDetails =
          if (shouldFetchDetails) LoadingState.Loading else LoadingState.Ready(null),
        currentEvents = if (shouldFetchDetails) LoadingState.Loading else LoadingState.Ready(null),
        currentInsight = if (shouldFetchDetails) LoadingState.Loading else LoadingState.Ready(null),
      ),
      if (shouldFetchDetails)
        (Action.FetchDetails(selectedIssueId!!, variant?.id) and
          Action.ListEvents(selectedIssueId, variant?.id, null) and
          Action.FetchInsight(
            selectedIssueId,
            state.selectedEvent?.eventId ?: state.selectedIssue!!.sampleEvent.eventId,
            variant?.id,
          ))
      else Action.NONE,
    )
  }
}
