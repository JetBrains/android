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

import com.android.tools.idea.insights.AppInsightsIssue
import com.android.tools.idea.insights.AppInsightsState
import com.android.tools.idea.insights.DynamicEventGallery
import com.android.tools.idea.insights.Event
import com.android.tools.idea.insights.InsightsProvider
import com.android.tools.idea.insights.LoadingState
import com.android.tools.idea.insights.analytics.AppInsightsTracker
import com.android.tools.idea.insights.events.actions.Action

fun transitionEvent(provider: InsightsProvider, event: Event) =
  if (provider.supportsMultipleEvents) {
    LoadingState.Loading
  } else {
    LoadingState.Ready(DynamicEventGallery(listOf(event), 0, ""))
  }

fun actionsForSelectedIssue(provider: InsightsProvider, issue: AppInsightsIssue) =
  Action.FetchDetails(issue.id) and
    if (provider.supportsMultipleEvents) {
      Action.FetchIssueVariants(issue.id) and
        Action.FetchNotes(issue.id) and
        Action.ListEvents(issue.id, null, null)
    } else {
      Action.FetchInsight(issue.id, null, issue.issueDetails.fatality, issue.sampleEvent)
    }

fun AppInsightsTracker.trackEventView(state: AppInsightsState) {
  val issueId = state.selectedIssue?.id?.value ?: return
  val eventId = state.selectedEvent?.name ?: return
  val appId = state.connections.selected?.appId ?: return

  logEventViewed(appId, state.mode, issueId, eventId)
}
