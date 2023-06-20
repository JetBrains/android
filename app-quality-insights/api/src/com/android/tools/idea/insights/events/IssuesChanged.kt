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
import com.android.tools.idea.insights.Device
import com.android.tools.idea.insights.LoadingState
import com.android.tools.idea.insights.MultiSelection
import com.android.tools.idea.insights.OperatingSystemInfo
import com.android.tools.idea.insights.Selection
import com.android.tools.idea.insights.Timed
import com.android.tools.idea.insights.Version
import com.android.tools.idea.insights.analytics.AppInsightsTracker
import com.android.tools.idea.insights.client.IssueResponse
import com.android.tools.idea.insights.convertSeverityList
import com.android.tools.idea.insights.events.actions.Action
import com.android.tools.idea.insights.toIssueRequest
import com.google.wireless.android.sdk.stats.AppQualityInsightsUsageEvent
import java.time.Clock

/** List of issues changed(fetched). */
data class IssuesChanged(
  val issues: LoadingState.Done<IssueResponse>,
  private val clock: Clock,
  private val previousGoodState: AppInsightsState?
) : ChangeEvent {
  override fun transition(
    state: AppInsightsState,
    tracker: AppInsightsTracker
  ): StateTransition<Action> {
    if (issues is LoadingState.Failure) {
      return StateTransition(
        state.copy(
          issues = issues,
          currentIssueDetails = LoadingState.Ready(null),
          currentNotes = LoadingState.Ready(null)
        ),
        Action.NONE
      )
    }

    state.toIssueRequest()?.let { request ->
      tracker.logCrashesFetched(
        state.connections.selected?.connection!!.appId,
        state.mode,
        AppQualityInsightsUsageEvent.AppQualityInsightsFetchDetails.newBuilder()
          .apply {
            timeFilter = request.filters.interval.toTimeFilter()
            versionFilter = !request.filters.versions.contains(Version.ALL)
            severityFilter = convertSeverityList(request.filters.eventTypes)
            deviceFilter = !request.filters.devices.contains(Device.ALL)
            osFilter = !request.filters.operatingSystems.contains(OperatingSystemInfo.ALL)
            signalFilter = request.filters.signal.toLogProto()
            defaultProject = false
            fetchSource?.let { this.fetchSource = it }
            numRetries = 0
            cache = false
          }
          .build()
      )
    }

    val currentVersions = state.filters.versions
    val currentDevices = state.filters.devices
    val currentOses = state.filters.operatingSystems
    val currentlySelectedIssue =
      (previousGoodState?.issues as? LoadingState.Ready)?.value?.value?.selected
    val newSelectedIssue =
      if (issues is LoadingState.Ready)
        issues.value.issues.firstOrNull { it.id == currentlySelectedIssue?.id }
          ?: issues.value.issues.firstOrNull()
      else null
    return StateTransition(
      state.copy(
        issues = issues.map { Timed(Selection(newSelectedIssue, it.issues), clock.instant()) },
        filters =
          state.filters.copy(
            versions =
              if (issues is LoadingState.Ready)
                MultiSelection(emptySet(), issues.value.versions).selectMatching {
                  currentVersions.allSelected() ||
                    currentVersions.selected.any { current -> it.value == current.value }
                }
              else state.filters.versions,
            devices =
              if (issues is LoadingState.Ready)
                MultiSelection(emptySet(), issues.value.devices).selectMatching {
                  currentDevices.allSelected() ||
                    currentDevices.selected.any { current -> it.value == current.value }
                }
              else state.filters.devices,
            operatingSystems =
              if (issues is LoadingState.Ready)
                MultiSelection(emptySet(), issues.value.operatingSystems).selectMatching {
                  currentOses.allSelected() ||
                    currentOses.selected.any { current -> it.value == current.value }
                }
              else state.filters.operatingSystems
          ),
        currentIssueDetails =
          if (issues is LoadingState.Ready && newSelectedIssue != null) LoadingState.Loading
          else LoadingState.Ready(null),
        currentNotes =
          if (issues is LoadingState.Ready && newSelectedIssue != null) LoadingState.Loading
          else LoadingState.Ready(null),
        permission = (issues as? LoadingState.Ready)?.value?.permission ?: state.permission
      ),
      action =
        if (issues is LoadingState.Ready && newSelectedIssue != null)
          newSelectedIssue.id.let { Action.FetchDetails(it) and Action.FetchNotes(it) }
        else Action.NONE
    )
  }
}
