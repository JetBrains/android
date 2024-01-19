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
import com.android.tools.idea.insights.persistence.AppInsightsSettings
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

/** A pass-thru event who persists connection and filter settings when they change. */
data class PersistSettingsAdapter(
  private val delegate: ChangeEvent,
  private val project: Project,
  private val tabId: InsightsProviderKey,
) : ChangeEvent {
  override fun transition(
    state: AppInsightsState,
    tracker: AppInsightsTracker,
    key: InsightsProviderKey,
  ): StateTransition<Action> {
    val transition = delegate.transition(state, tracker, key)
    if (
      state.filters != transition.newState.filters ||
        state.connections.selected != transition.newState.connections.selected
    ) {
      project.service<AppInsightsSettings>().setTabSetting(tabId, transition.newState)
    }
    return transition
  }
}
