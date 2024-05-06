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
import com.android.tools.idea.insights.analytics.AppInsightsTracker
import com.android.tools.idea.insights.events.actions.Action
import com.android.tools.idea.insights.persistence.InsightsFilterSettings

class RestoreFilterFromSettings(
  private val settings: InsightsFilterSettings,
  private val delegate: ChangeEvent
) : ChangeEvent {
  override fun transition(
    state: AppInsightsState,
    tracker: AppInsightsTracker,
    key: InsightsProviderKey
  ): StateTransition<Action> {
    val transition = delegate.transition(state, tracker, key)
    val selectConnection =
      transition.newState.connections.items.firstOrNull {
        settings.connection?.equalsConnection(it) ?: false
      } ?: return transition
    return transition.copy(
      newState =
        transition.newState.copy(
          connections = transition.newState.connections.select(selectConnection),
          filters = settings.overwriteFilters(transition.newState.filters)
        )
    )
  }
}
