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

import com.android.tools.idea.insights.AppInsightsIssue
import com.android.tools.idea.insights.AppInsightsState
import com.android.tools.idea.insights.InsightsProviderKey
import com.android.tools.idea.insights.LoadingState
import com.android.tools.idea.insights.Timed
import com.android.tools.idea.insights.analytics.AppInsightsTracker
import com.android.tools.idea.insights.analytics.IssueSelectionSource
import com.android.tools.idea.insights.events.actions.Action
import com.google.wireless.android.sdk.stats.AppQualityInsightsUsageEvent
import com.intellij.openapi.diagnostic.Logger

/** Issue selection changed. */
data class SelectedIssueChanged(
  val issue: AppInsightsIssue?,
  val selectionSource: IssueSelectionSource,
) : ChangeEvent {
  override fun transition(
    state: AppInsightsState,
    tracker: AppInsightsTracker,
    key: InsightsProviderKey,
  ): StateTransition<Action> {
    if (issue == state.selectedIssue) {
      return StateTransition(state, Action.NONE)
    }
    if (issue != null) {
      tracker.logCrashListDetailView(
        AppQualityInsightsUsageEvent.AppQualityInsightsCrashOpenDetails.newBuilder()
          .apply {
            source = selectionSource.toCrashOpenSource()
            crashType = issue.issueDetails.fatality.toCrashType()
          }
          .build()
      )
    }
    Logger.getInstance(SelectedIssueChanged::class.java)
      .info(
        "Changing selection from ${(state.issues as? LoadingState.Ready)?.value?.value?.selected} to $issue"
      )
    return StateTransition(
      state.copy(
        issues = state.issues.map { Timed(value = it.value.select(issue), time = it.time) },
        currentIssueVariants =
          if (issue != null && state.issues is LoadingState.Ready) {
            LoadingState.Loading
          } else {
            LoadingState.Ready(null)
          },
        currentIssueDetails =
          if (issue != null && state.issues is LoadingState.Ready) {
            LoadingState.Loading
          } else {
            LoadingState.Ready(null)
          },
        currentEvents =
          if (issue != null && state.issues is LoadingState.Ready) {
            transitionEventForKey(key, issue.sampleEvent)
          } else {
            LoadingState.Ready(null)
          },
        currentNotes =
          if (issue != null && state.issues is LoadingState.Ready) {
            LoadingState.Loading
          } else {
            LoadingState.Ready(null)
          },
        currentInsight =
          if (issue != null && state.issues is LoadingState.Ready) {
            LoadingState.Loading
          } else {
            LoadingState.Ready(null)
          },
      ),
      action =
        if (issue != null && state.issues is LoadingState.Ready)
          actionsForSelectedIssue(key, issue.id)
        else Action.NONE,
    )
  }
}
