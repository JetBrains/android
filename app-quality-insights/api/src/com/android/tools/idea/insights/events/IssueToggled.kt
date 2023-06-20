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
import com.android.tools.idea.insights.IssueId
import com.android.tools.idea.insights.IssueState
import com.android.tools.idea.insights.Selection
import com.android.tools.idea.insights.Timed
import com.android.tools.idea.insights.analytics.AppInsightsTracker
import com.android.tools.idea.insights.events.actions.Action
import com.google.wireless.android.sdk.stats.AppQualityInsightsUsageEvent

data class IssueToggled(
  val issue: IssueId,
  val issueState: IssueState,
  private val isUndo: Boolean = false
) : ChangeEvent {
  override fun transition(
    state: AppInsightsState,
    tracker: AppInsightsTracker
  ): StateTransition<Action> {
    if ((issueState == IssueState.OPEN || issueState == IssueState.CLOSED) && !isUndo) {
      state.connections.selected?.connection?.appId?.let { appId ->
        tracker.logIssueStatusChanged(
          appId,
          state.mode,
          AppQualityInsightsUsageEvent.AppQualityInsightsIssueChangedDetails.newBuilder()
            .apply {
              statusChange =
                if (issueState == IssueState.OPEN)
                  AppQualityInsightsUsageEvent.AppQualityInsightsIssueChangedDetails.StatusChange
                    .OPENED
                else
                  AppQualityInsightsUsageEvent.AppQualityInsightsIssueChangedDetails.StatusChange
                    .CLOSED
            }
            .build()
        )
      }
    }
    return StateTransition(
      state.copy(
        issues =
          state.issues.map { (issues, time) ->
            Timed(
              Selection(
                issues.selected?.let { selected ->
                  if (selected.id == issue) {
                    selected.copy(state = issueState)
                  } else {
                    selected
                  }
                },
                issues.items.map { if (it.id == issue) it.copy(state = issueState) else it }
              ),
              time
            )
          }
      ),
      action =
        when (issueState) {
          IssueState.OPENING -> Action.OpenIssue(issue)
          IssueState.CLOSING -> Action.CloseIssue(issue)
          else -> Action.NONE
        }
    )
  }
}
