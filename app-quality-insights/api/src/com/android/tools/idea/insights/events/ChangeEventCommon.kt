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

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.insights.AppInsightsState
import com.android.tools.idea.insights.DynamicEventGallery
import com.android.tools.idea.insights.Event
import com.android.tools.idea.insights.InsightsProviderKey
import com.android.tools.idea.insights.IssueId
import com.android.tools.idea.insights.LoadingState
import com.android.tools.idea.insights.VITALS_KEY
import com.android.tools.idea.insights.analytics.AppInsightsTracker
import com.android.tools.idea.insights.events.actions.Action

fun transitionEventForKey(key: InsightsProviderKey, event: Event) =
  if (useIssueSampleEvent(key)) {
    LoadingState.Ready(DynamicEventGallery(listOf(event), 0, ""))
  } else {
    LoadingState.Loading
  }

fun actionsForSelectedIssue(key: InsightsProviderKey, id: IssueId) =
  Action.FetchDetails(id) and
    if (key == VITALS_KEY) {
      Action.NONE
    } else {
      Action.FetchIssueVariants(id) and
        Action.FetchNotes(id) and
        if (useIssueSampleEvent(key)) Action.NONE else Action.ListEvents(id, null, null)
    }

private fun useIssueSampleEvent(key: InsightsProviderKey) =
  key == VITALS_KEY || !StudioFlags.CRASHLYTICS_J_UI.get()

fun AppInsightsTracker.trackEventView(state: AppInsightsState, isFetched: Boolean) {
  val issueId = state.selectedIssue?.id?.value ?: return
  val eventId = state.selectedEvent?.name ?: return
  val appId = state.connections.selected?.appId ?: return

  logEventViewed(appId, state.mode, issueId, eventId, isFetched)
}
