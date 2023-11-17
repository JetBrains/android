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
import com.android.tools.idea.insights.Connection
import com.android.tools.idea.insights.Filters
import com.android.tools.idea.insights.InsightsProviderKey
import com.android.tools.idea.insights.LoadingState
import com.android.tools.idea.insights.Selection
import com.android.tools.idea.insights.analytics.AppInsightsTracker
import com.android.tools.idea.insights.events.actions.Action
import com.google.wireless.android.sdk.stats.AppQualityInsightsUsageEvent

/** Any change to the available connections is propagated here. */
data class ConnectionsChanged(
  val connections: List<Connection>,
  private val defaultFilters: Filters
) : ChangeEvent {

  override fun transition(
    state: AppInsightsState,
    tracker: AppInsightsTracker,
    key: InsightsProviderKey
  ): StateTransition<Action> {
    val activeConnection = findActiveConnection(state)
    val activeConnectionChanged = activeConnection != state.connections.selected

    return if (activeConnectionChanged) {
      StateTransition(
        state.copy(
          connections = Selection(activeConnection, connections),
          issues = LoadingState.Loading,
          currentIssueVariants = LoadingState.Ready(null),
          currentIssueDetails = LoadingState.Ready(null),
          currentNotes = LoadingState.Ready(null),
          filters = defaultFilters
        ),
        action =
          Action.Fetch(
            AppQualityInsightsUsageEvent.AppQualityInsightsFetchDetails.FetchSource
              .PROJECT_SELECTION
          )
      )
    } else {
      StateTransition(
        state.copy(connections = Selection(activeConnection, connections)),
        action = Action.NONE
      )
    }
  }

  private fun findActiveConnection(state: AppInsightsState): Connection? {
    val currentSelection = state.connections.selected
    if (currentSelection in connections) return currentSelection

    // Next, try to see if there's a matching connection with the currently selected build variant,
    // if so, pick it.
    connections
      .firstOrNull { it.isPreferredConnection() }
      ?.let {
        return it
      }

    // Then, pick the first available connection if there is.
    return connections.firstOrNull { it.isConfigured } ?: connections.firstOrNull()
  }
}
