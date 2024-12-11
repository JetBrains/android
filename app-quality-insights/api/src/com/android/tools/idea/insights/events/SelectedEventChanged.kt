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
import com.android.tools.idea.insights.EventMovement
import com.android.tools.idea.insights.InsightsProvider
import com.android.tools.idea.insights.LoadingState
import com.android.tools.idea.insights.analytics.AppInsightsTracker
import com.android.tools.idea.insights.client.AppInsightsCache
import com.android.tools.idea.insights.events.actions.Action
import com.intellij.openapi.diagnostic.Logger

class SelectedEventChanged(private val movement: EventMovement) : ChangeEvent {

  override fun transition(
    state: AppInsightsState,
    tracker: AppInsightsTracker,
    provider: InsightsProvider,
    cache: AppInsightsCache,
  ): StateTransition<Action> {
    val selection =
      (state.currentEvents as? LoadingState.Ready)?.value
        ?: return StateTransition(state, Action.NONE)
    return if (movement == EventMovement.NEXT && selection.hasNext()) {
      val newSelection = selection.next()
      StateTransition(
          newState = state.copy(currentEvents = LoadingState.Ready(newSelection)),
          action =
            if (newSelection.isLastIndexSelected() && newSelection.canRequestMoreEvents()) {
              Action.ListEvents(
                state.selectedIssue!!.id,
                state.selectedVariant?.id,
                newSelection.token,
              )
            } else {
              Action.NONE
            },
        )
        .also { trackEventView(tracker, it) }
    } else if (movement == EventMovement.PREVIOUS && selection.hasPrevious()) {
      StateTransition(
          newState = state.copy(currentEvents = LoadingState.Ready(selection.previous())),
          action = Action.NONE,
        )
        .also { trackEventView(tracker, it) }
    } else {
      Logger.getInstance(this::class.java)
        .warn(
          "Invalid state: attempting to select ${movement.name} item when there is none available."
        )
      StateTransition(state, Action.NONE)
    }
  }

  private fun trackEventView(tracker: AppInsightsTracker, transition: StateTransition<Action>) =
    tracker.trackEventView(transition.newState)
}
