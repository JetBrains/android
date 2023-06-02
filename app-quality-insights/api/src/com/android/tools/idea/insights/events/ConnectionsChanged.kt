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
import com.android.tools.idea.insights.LoadingState
import com.android.tools.idea.insights.Selection
import com.android.tools.idea.insights.analytics.AppInsightsTracker
import com.android.tools.idea.insights.events.actions.Action
import com.android.tools.idea.insights.persistence.ConnectionSetting
import com.google.wireless.android.sdk.stats.AppQualityInsightsUsageEvent

/**
 * Any change to the available connections is propagated here.
 *
 * By default, prefers the [savedConnection] if it is in [connections].
 */
data class ConnectionsChanged(
  val connections: List<Connection>,
  private val defaultFilters: Filters,
  private val savedConnection: ConnectionSetting?
) : ChangeEvent {

  override fun transition(
    state: AppInsightsState,
    tracker: AppInsightsTracker
  ): StateTransition<Action> {
    val activeConnection = findActiveConnection(state)
    val activeConnectionChanged = activeConnection != state.connections.selected

    return if (activeConnectionChanged) {
      StateTransition(
        state.copy(
          connections = Selection(activeConnection, connections),
          issues = LoadingState.Loading,
          currentIssueDetails = LoadingState.Ready(null),
          currentNotes = LoadingState.Ready(null),
          // reset all filters to persisted state, or if not available, default state
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
    // First, try to see if the saved selected connection is part of the current connections, if so,
    // pick it. If not, pick the currently selected connection.
    val currentSelection =
      if (savedConnection == null) {
        state.connections.selected
      } else {
        connections.firstOrNull { savedConnection.equalsConnection(it) }
      }
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
