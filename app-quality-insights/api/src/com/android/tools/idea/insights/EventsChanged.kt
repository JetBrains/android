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
package com.android.tools.idea.insights

import com.android.tools.idea.insights.analytics.AppInsightsTracker
import com.android.tools.idea.insights.events.ChangeEvent
import com.android.tools.idea.insights.events.StateTransition
import com.android.tools.idea.insights.events.actions.Action
import com.intellij.openapi.diagnostic.Logger

class EventsChanged(private val eventPage: LoadingState.Done<EventPage>) : ChangeEvent {
  override fun transition(
    state: AppInsightsState,
    tracker: AppInsightsTracker,
    key: InsightsProviderKey
  ): StateTransition<Action> {
    if (eventPage is LoadingState.Failure) {
      Logger.getInstance(this::class.java).warn("Failed to load events: $eventPage")
      return StateTransition(state, Action.NONE)
    }
    val newEvents = (eventPage as LoadingState.Ready).value
    return StateTransition(
      newState =
        state.copy(
          currentEvents =
            if (state.currentEvents is LoadingState.Ready) {
              state.currentEvents.map { currentEvents ->
                if (currentEvents == null) {
                  Logger.getInstance(this::class.java)
                    .warn(
                      "currentEvents is null when it's expected to be LoadingState.Loading or LoadingState.Ready"
                    )
                  DynamicEventGallery(newEvents.events, 0, newEvents.token)
                } else {
                  currentEvents.appendEventPage(newEvents).next()
                }
              }
            } else {
              eventPage.map {
                if (it == EventPage.EMPTY) null
                else DynamicEventGallery(newEvents.events, 0, newEvents.token)
              }
            }
        ),
      action = Action.NONE
    )
  }
}
