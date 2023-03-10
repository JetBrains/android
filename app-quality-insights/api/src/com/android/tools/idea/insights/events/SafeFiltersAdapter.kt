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
import com.android.tools.idea.insights.NoDevicesSelectedException
import com.android.tools.idea.insights.NoOperatingSystemsSelectedException
import com.android.tools.idea.insights.NoTypesSelectedException
import com.android.tools.idea.insights.NoVersionsSelectedException
import com.android.tools.idea.insights.UnconfiguredAppException
import com.android.tools.idea.insights.analytics.AppInsightsTracker
import com.android.tools.idea.insights.events.actions.Action

data class SafeFiltersAdapter(private val delegate: ChangeEvent) : ChangeEvent {

  override fun transition(
    state: AppInsightsState,
    tracker: AppInsightsTracker
  ): StateTransition<Action> {
    var result = delegate.transition(state, tracker)
    if (result.newState.connections.selected?.isConfigured() != true) {
      return StateTransition(
        result.newState.copy(
          issues =
            LoadingState.UnknownFailure(
              "Currently selected app is not linked to a Firebase project.",
              UnconfiguredAppException
            ),
          currentIssueDetails = LoadingState.Ready(null),
          currentNotes = LoadingState.Ready(null),
          filters =
            result.newState.filters.copy(
              versions = MultiSelection.emptySelection(),
              devices = MultiSelection.emptySelection(),
              operatingSystems = MultiSelection.emptySelection()
            )
        ),
        action = Action.CancelFetches
      )
    }
    if (result.newState.filters.failureTypeToggles.selected.isEmpty()) {
      result =
        StateTransition(
          result.newState.copy(
            issues = LoadingState.UnknownFailure(null, NoTypesSelectedException),
            currentIssueDetails = LoadingState.Ready(null),
            currentNotes = LoadingState.Ready(null)
          ),
          action = Action.CancelFetches
        )
    }
    if (
      result.newState.filters.versions.selected.isEmpty() &&
        result.newState.filters.versions.items.isNotEmpty()
    ) {
      result =
        StateTransition(
          result.newState.copy(
            issues = LoadingState.UnknownFailure(null, NoVersionsSelectedException),
            currentIssueDetails = LoadingState.Ready(null),
            currentNotes = LoadingState.Ready(null)
          ),
          action = Action.CancelFetches
        )
    }
    if (
      result.newState.filters.devices.selected.isEmpty() &&
        result.newState.filters.devices.items.isNotEmpty()
    ) {
      result =
        StateTransition(
          result.newState.copy(
            issues = LoadingState.UnknownFailure(null, NoDevicesSelectedException),
            currentIssueDetails = LoadingState.Ready(null),
            currentNotes = LoadingState.Ready(null)
          ),
          action = Action.CancelFetches
        )
    }
    if (
      result.newState.filters.operatingSystems.selected.isEmpty() &&
        result.newState.filters.operatingSystems.items.isNotEmpty()
    ) {
      result =
        StateTransition(
          result.newState.copy(
            issues = LoadingState.UnknownFailure(null, NoOperatingSystemsSelectedException),
            currentIssueDetails = LoadingState.Ready(null),
            currentNotes = LoadingState.Ready(null)
          ),
          action = Action.CancelFetches
        )
    }
    return result
  }
}
