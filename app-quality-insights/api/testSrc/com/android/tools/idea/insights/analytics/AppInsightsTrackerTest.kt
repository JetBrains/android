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
package com.android.tools.idea.insights.analytics

import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.argThat
import com.android.testutils.MockitoKt.capture
import com.android.testutils.MockitoKt.eq
import com.android.tools.idea.insights.AppInsightsProjectLevelControllerRule
import com.android.tools.idea.insights.ConnectionMode
import com.android.tools.idea.insights.DEFAULT_FETCHED_DEVICES
import com.android.tools.idea.insights.DEFAULT_FETCHED_OSES
import com.android.tools.idea.insights.DEFAULT_FETCHED_PERMISSIONS
import com.android.tools.idea.insights.DEFAULT_FETCHED_VERSIONS
import com.android.tools.idea.insights.Device
import com.android.tools.idea.insights.Event
import com.android.tools.idea.insights.EventPage
import com.android.tools.idea.insights.FailureType
import com.android.tools.idea.insights.ISSUE1
import com.android.tools.idea.insights.ISSUE2
import com.android.tools.idea.insights.LoadingState
import com.android.tools.idea.insights.NOTE1
import com.android.tools.idea.insights.NOTE1_BODY
import com.android.tools.idea.insights.OperatingSystemInfo
import com.android.tools.idea.insights.Permission
import com.android.tools.idea.insights.SignalType
import com.android.tools.idea.insights.TimeIntervalFilter
import com.android.tools.idea.insights.Version
import com.android.tools.idea.insights.VisibilityType
import com.android.tools.idea.insights.WithCount
import com.android.tools.idea.insights.client.IssueResponse
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.AppQualityInsightsUsageEvent
import com.google.wireless.android.sdk.stats.AppQualityInsightsUsageEvent.AppQualityInsightsNotesDetails
import com.intellij.testFramework.ProjectRule
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.times
import org.mockito.Mockito.verify

private val ISSUE_RESPONSE =
  LoadingState.Ready(
    IssueResponse(
      emptyList(),
      listOf(DEFAULT_FETCHED_VERSIONS, WithCount(42, Version("2", "2.0"))),
      listOf(DEFAULT_FETCHED_DEVICES, WithCount(42, Device("Google", "Pixel 2"))),
      listOf(DEFAULT_FETCHED_OSES, WithCount(43, OperatingSystemInfo("12", "Android (12)"))),
      DEFAULT_FETCHED_PERMISSIONS,
    )
  )

class AppInsightsTrackerTest {
  private val projectRule = ProjectRule()
  private val controllerRule = AppInsightsProjectLevelControllerRule(projectRule)

  @get:Rule val ruleChain = RuleChain.outerRule(projectRule).around(controllerRule)

  @Test
  fun `tracking is triggered by fetch`() = runBlocking {
    controllerRule.consumeInitialState(ISSUE_RESPONSE)
    controllerRule.toggleFatality(FailureType.NON_FATAL)
    consumeAndCompleteIssuesCall()
    controllerRule.toggleFatality(FailureType.ANR)
    consumeAndCompleteIssuesCall()
    controllerRule.selectVisibilityType(VisibilityType.USER_PERCEIVED)
    consumeAndCompleteIssuesCall()
    controllerRule.selectTimeInterval(TimeIntervalFilter.SIXTY_DAYS)
    consumeAndCompleteIssuesCall()
    controllerRule.selectDevices(setOf(DEFAULT_FETCHED_DEVICES.value))
    consumeAndCompleteIssuesCall()
    controllerRule.selectVersions(setOf(DEFAULT_FETCHED_VERSIONS.value))
    consumeAndCompleteIssuesCall()
    controllerRule.selectOsVersion(setOf(DEFAULT_FETCHED_OSES.value))
    consumeAndCompleteIssuesCall()
    controllerRule.selectSignal(SignalType.SIGNAL_REGRESSED)
    consumeAndCompleteIssuesCall()

    verify(controllerRule.tracker)
      .logCrashesFetched(
        any(),
        eq(ConnectionMode.ONLINE),
        argThat {
          it.deviceFilter &&
            it.osFilter &&
            it.versionFilter &&
            it.severityFilter ==
              AppQualityInsightsUsageEvent.AppQualityInsightsFetchDetails.SeverityFilter.FATAL &&
            it.visibilityFilter ==
              AppQualityInsightsUsageEvent.AppQualityInsightsFetchDetails.VisibilityFilter
                .USER_PERCEIVED &&
            it.timeFilter ==
              AppQualityInsightsUsageEvent.AppQualityInsightsFetchDetails.TimeFilter.SIXTY_DAYS &&
            it.signalFilter ==
              AppQualityInsightsUsageEvent.AppQualityInsightsFetchDetails.SignalFilter
                .REGRESSIVE_SIGNAL
        },
      )
  }

  @Test
  fun `issue status changes trigger tracking`() = runBlocking {
    controllerRule.consumeInitialState(
      LoadingState.Ready(
        IssueResponse(
          listOf(ISSUE1, ISSUE2),
          emptyList(),
          emptyList(),
          emptyList(),
          Permission.READ_ONLY,
        )
      )
    )

    controllerRule.controller.closeIssue(ISSUE1)
    controllerRule.consumeNext()
    controllerRule.client.completeUpdateIssueStateCallWith(LoadingState.Ready(Unit))
    controllerRule.consumeNext()

    verify(controllerRule.tracker)
      .logIssueStatusChanged(
        any(),
        eq(ConnectionMode.ONLINE),
        argThat {
          it.statusChange ==
            AppQualityInsightsUsageEvent.AppQualityInsightsIssueChangedDetails.StatusChange.CLOSED
        },
      )

    controllerRule.controller.openIssue(ISSUE1)
    controllerRule.consumeNext()
    controllerRule.client.completeUpdateIssueStateCallWith(LoadingState.Ready(Unit))
    controllerRule.consumeNext()

    verify(controllerRule.tracker)
      .logIssueStatusChanged(
        any(),
        eq(ConnectionMode.ONLINE),
        argThat {
          it.statusChange ==
            AppQualityInsightsUsageEvent.AppQualityInsightsIssueChangedDetails.StatusChange.OPENED
        },
      )
  }

  @Test
  fun `adding and deleting notes causes tracking`() = runBlocking {
    controllerRule.consumeInitialState(
      LoadingState.Ready(
        IssueResponse(
          listOf(ISSUE1, ISSUE2),
          emptyList(),
          emptyList(),
          emptyList(),
          Permission.FULL,
        )
      )
    )

    controllerRule.controller.addNote(ISSUE1, NOTE1_BODY)
    controllerRule.consumeNext()
    controllerRule.client.completeCreateNoteCallWith(LoadingState.Ready(NOTE1))
    controllerRule.consumeNext()

    verify(controllerRule.tracker)
      .logNotesAction(
        any(),
        eq(ConnectionMode.ONLINE),
        argThat { it.noteEvent == AppQualityInsightsNotesDetails.NoteEvent.ADDED },
      )

    controllerRule.controller.deleteNote(NOTE1)
    controllerRule.consumeNext()
    controllerRule.client.completeDeleteNoteCallWith(LoadingState.Ready(Unit))
    controllerRule.consumeNext()

    verify(controllerRule.tracker)
      .logNotesAction(
        any(),
        eq(ConnectionMode.ONLINE),
        argThat { it.noteEvent == AppQualityInsightsNotesDetails.NoteEvent.REMOVED },
      )
  }

  @Test
  fun `entering offline or online mode triggers tracking`() = runBlocking {
    controllerRule.consumeInitialState(
      LoadingState.Ready(
        IssueResponse(listOf(ISSUE1), emptyList(), emptyList(), emptyList(), Permission.READ_ONLY)
      )
    )
    controllerRule.enterOfflineMode()
    controllerRule.consumeNext()
    controllerRule.consumeFetchState(
      LoadingState.Ready(
        IssueResponse(listOf(ISSUE1), emptyList(), emptyList(), emptyList(), Permission.READ_ONLY)
      )
    )

    verify(controllerRule.tracker)
      .logOfflineTransitionAction(
        any(),
        eq(ConnectionMode.ONLINE),
        eq(AppQualityInsightsUsageEvent.AppQualityInsightsModeTransitionDetails.ONLINE_TO_OFFLINE),
      )

    controllerRule.refreshAndConsumeLoadingState()
    controllerRule.consumeFetchState(
      LoadingState.Ready(
        IssueResponse(listOf(ISSUE1), emptyList(), emptyList(), emptyList(), Permission.READ_ONLY)
      ),
      isTransitionToOnlineMode = true,
    )
    verify(controllerRule.tracker)
      .logOfflineTransitionAction(
        any(),
        eq(ConnectionMode.OFFLINE),
        eq(AppQualityInsightsUsageEvent.AppQualityInsightsModeTransitionDetails.OFFLINE_TO_ONLINE),
      )
  }

  @Test
  fun `track event views`() = runBlocking {
    controllerRule.consumeInitialState(
      LoadingState.Ready(
        IssueResponse(listOf(ISSUE1), emptyList(), emptyList(), emptyList(), Permission.READ_ONLY)
      ),
      eventsState = LoadingState.Ready(EventPage(listOf(Event("1"), Event("2"), Event("3")), "abc")),
    )

    controllerRule.controller.nextEvent()
    controllerRule.consumeNext()
    controllerRule.controller.nextEvent()
    controllerRule.consumeNext()

    controllerRule.controller.nextEvent()
    controllerRule.client.completeListEvents(LoadingState.Ready(EventPage(listOf(Event("4")), "")))
    controllerRule.consumeNext()

    val eventIdCaptor: ArgumentCaptor<String> = ArgumentCaptor.forClass(String::class.java)
    val isFetchedCaptor: ArgumentCaptor<Boolean> = ArgumentCaptor.forClass(Boolean::class.java)

    // verify total number of tracking calls
    verify(controllerRule.tracker, times(4))
      .logEventViewed(
        any(),
        eq(ConnectionMode.ONLINE),
        eq(ISSUE1.id.value),
        capture(eventIdCaptor),
        capture(isFetchedCaptor),
      )

    assertThat(eventIdCaptor.allValues).containsExactly("1", "2", "3", "4").inOrder()
    assertThat(isFetchedCaptor.allValues).containsExactly(true, false, false, true).inOrder()
  }

  private suspend fun consumeAndCompleteIssuesCall() {
    controllerRule.consumeNext()
    controllerRule.consumeFetchState(ISSUE_RESPONSE)
  }
}
