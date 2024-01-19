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

import com.android.flags.junit.FlagRule
import com.android.testutils.time.FakeClock
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.insights.AppInsightsState
import com.android.tools.idea.insights.CONNECTION1
import com.android.tools.idea.insights.DEFAULT_FETCHED_DEVICES
import com.android.tools.idea.insights.DEFAULT_FETCHED_OSES
import com.android.tools.idea.insights.DEFAULT_FETCHED_PERMISSIONS
import com.android.tools.idea.insights.DEFAULT_FETCHED_VERSIONS
import com.android.tools.idea.insights.Device
import com.android.tools.idea.insights.DynamicEventGallery
import com.android.tools.idea.insights.FailureType
import com.android.tools.idea.insights.Filters
import com.android.tools.idea.insights.ISSUE1
import com.android.tools.idea.insights.ISSUE2
import com.android.tools.idea.insights.LoadingState
import com.android.tools.idea.insights.MultiSelection
import com.android.tools.idea.insights.OperatingSystemInfo
import com.android.tools.idea.insights.Selection
import com.android.tools.idea.insights.SignalType
import com.android.tools.idea.insights.TEST_FILTERS
import com.android.tools.idea.insights.TEST_KEY
import com.android.tools.idea.insights.TimeIntervalFilter
import com.android.tools.idea.insights.Timed
import com.android.tools.idea.insights.VITALS_KEY
import com.android.tools.idea.insights.Version
import com.android.tools.idea.insights.VisibilityType
import com.android.tools.idea.insights.WithCount
import com.android.tools.idea.insights.analytics.TestAppInsightsTracker
import com.android.tools.idea.insights.client.IssueResponse
import com.android.tools.idea.insights.events.actions.Action
import com.android.tools.idea.insights.selectionOf
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

private val fetchedVersion =
  listOf(
    DEFAULT_FETCHED_VERSIONS,
    WithCount(42, Version("2", "2.0")),
    WithCount(33, Version("2.1", "2.1")),
  )
private val fetchedDevice =
  listOf(
    DEFAULT_FETCHED_DEVICES,
    WithCount(10, Device("Google", "Pixel 2")),
    WithCount(22, Device("Apple", "iPhone 14")),
  )
private val fetchedOs =
  listOf(
    DEFAULT_FETCHED_OSES,
    WithCount(10, OperatingSystemInfo("11", "Android (11)")),
    WithCount(41, OperatingSystemInfo("13", "Android (13)")),
  )

class IssuesChangedTest {

  @get:Rule val flagRule = FlagRule(StudioFlags.CRASHLYTICS_J_UI, true)

  @Test
  fun `empty issues result in no action`() {
    val currentState =
      AppInsightsState(
        Selection(CONNECTION1, listOf(CONNECTION1)),
        TEST_FILTERS,
        LoadingState.Loading,
      )
    val event =
      IssuesChanged(
        LoadingState.Ready(
          IssueResponse(
            emptyList(),
            fetchedVersion,
            fetchedDevice,
            fetchedOs,
            DEFAULT_FETCHED_PERMISSIONS,
          )
        ),
        FakeClock(),
        currentState,
      )

    with(event.transition(currentState, TestAppInsightsTracker, TEST_KEY)) {
      assertThat(newState.currentIssueDetails).isEqualTo(LoadingState.Ready(null))
      assertThat(newState.currentNotes).isEqualTo(LoadingState.Ready(null))
      assertThat(action).isEqualTo(Action.NONE)
    }
  }

  @Test
  fun `transition selects previously selected issue and triggers action`() {
    val clock = FakeClock()
    val currentState =
      AppInsightsState(
        Selection(CONNECTION1, listOf(CONNECTION1)),
        TEST_FILTERS,
        LoadingState.Ready(Timed(Selection(ISSUE1, listOf(ISSUE1)), clock.instant())),
      )

    val event =
      IssuesChanged(
        LoadingState.Ready(
          IssueResponse(
            listOf(ISSUE2, ISSUE1),
            fetchedVersion,
            fetchedDevice,
            fetchedOs,
            DEFAULT_FETCHED_PERMISSIONS,
          )
        ),
        clock,
        currentState,
      )

    with(event.transition(currentState, TestAppInsightsTracker, TEST_KEY)) {
      assertThat((newState.issues as LoadingState.Ready).value.value.selected).isEqualTo(ISSUE1)
      assertThat(newState.currentIssueVariants).isEqualTo(LoadingState.Loading)
      assertThat(newState.currentIssueDetails).isEqualTo(LoadingState.Loading)
      assertThat(newState.currentNotes).isEqualTo(LoadingState.Loading)
      assertThat(action)
        .isEqualTo(
          Action.FetchDetails(ISSUE1.id) and
            Action.FetchIssueVariants(ISSUE1.id) and
            Action.FetchNotes(ISSUE1.id) and
            Action.ListEvents(ISSUE1.id, null, null)
        )
    }
  }

  @Test
  fun `transition selects first issue in response when none matches the currently selected issue`() {
    val clock = FakeClock()
    val currentState =
      AppInsightsState(
        Selection(CONNECTION1, listOf(CONNECTION1)),
        TEST_FILTERS,
        LoadingState.Ready(Timed(Selection(ISSUE1, listOf(ISSUE1)), clock.instant())),
      )

    val event =
      IssuesChanged(
        LoadingState.Ready(
          IssueResponse(
            listOf(ISSUE2),
            fetchedVersion,
            fetchedDevice,
            fetchedOs,
            DEFAULT_FETCHED_PERMISSIONS,
          )
        ),
        clock,
        currentState,
      )

    with(event.transition(currentState, TestAppInsightsTracker, TEST_KEY)) {
      assertThat((newState.issues as LoadingState.Ready).value.value.selected).isEqualTo(ISSUE2)
      assertThat(newState.currentIssueVariants).isEqualTo(LoadingState.Loading)
      assertThat(newState.currentIssueDetails).isEqualTo(LoadingState.Loading)
      assertThat(newState.currentNotes).isEqualTo(LoadingState.Loading)
      assertThat(newState.currentEvents).isEqualTo(LoadingState.Loading)
      assertThat(action)
        .isEqualTo(
          Action.FetchDetails(ISSUE2.id) and
            Action.FetchIssueVariants(ISSUE2.id) and
            Action.FetchNotes(ISSUE2.id) and
            Action.ListEvents(ISSUE2.id, null, null)
        )
    }
  }

  @Test
  fun `issues changed maintains the ALL state of filters if they are currently ALL`() {
    val currentState =
      AppInsightsState(
        Selection(CONNECTION1, listOf(CONNECTION1)),
        TEST_FILTERS,
        LoadingState.Loading,
      )
    val event =
      IssuesChanged(
        LoadingState.Ready(
          IssueResponse(
            listOf(ISSUE1),
            fetchedVersion,
            fetchedDevice,
            fetchedOs,
            DEFAULT_FETCHED_PERMISSIONS,
          )
        ),
        FakeClock(),
        AppInsightsState(
          Selection(CONNECTION1, listOf(CONNECTION1)),
          TEST_FILTERS,
          LoadingState.Loading,
        ),
      )

    val resultState = event.transition(currentState, TestAppInsightsTracker, TEST_KEY)

    // These filters should remain untouched
    assertThat(resultState.newState.filters.timeInterval).isEqualTo(TEST_FILTERS.timeInterval)
    assertThat(resultState.newState.filters.signal).isEqualTo(TEST_FILTERS.signal)
    assertThat(resultState.newState.filters.failureTypeToggles)
      .isEqualTo(TEST_FILTERS.failureTypeToggles)
    assertThat(resultState.newState.filters.visibilityType).isEqualTo(TEST_FILTERS.visibilityType)

    // These should be ALL
    assertThat(resultState.newState.filters.versions)
      .isEqualTo(MultiSelection(fetchedVersion.toSet(), fetchedVersion))
    assertThat(resultState.newState.filters.devices)
      .isEqualTo(MultiSelection(fetchedDevice.toSet(), fetchedDevice))
    assertThat(resultState.newState.filters.operatingSystems)
      .isEqualTo(MultiSelection(fetchedOs.toSet(), fetchedOs))
  }

  @Test
  fun `issues changed selects currently selected filters`() {
    val currentFilters =
      Filters(
        MultiSelection(setOf(DEFAULT_FETCHED_VERSIONS), fetchedVersion),
        selectionOf(TimeIntervalFilter.NINETY_DAYS),
        MultiSelection(setOf(FailureType.FATAL), listOf(FailureType.NON_FATAL, FailureType.FATAL)),
        MultiSelection(setOf(DEFAULT_FETCHED_DEVICES), fetchedDevice),
        MultiSelection(setOf(DEFAULT_FETCHED_OSES), fetchedOs),
        selectionOf(SignalType.SIGNAL_REGRESSED),
        selectionOf(VisibilityType.USER_PERCEIVED),
      )
    val currentState =
      AppInsightsState(
        Selection(CONNECTION1, listOf(CONNECTION1)),
        currentFilters,
        LoadingState.Loading,
      )
    val event =
      IssuesChanged(
        LoadingState.Ready(
          IssueResponse(
            listOf(ISSUE1),
            fetchedVersion,
            fetchedDevice,
            fetchedOs,
            DEFAULT_FETCHED_PERMISSIONS,
          )
        ),
        FakeClock(),
        AppInsightsState(
          Selection(CONNECTION1, listOf(CONNECTION1)),
          TEST_FILTERS,
          LoadingState.Loading,
        ),
      )

    val result = event.transition(currentState, TestAppInsightsTracker, TEST_KEY)

    // These filters are untouched
    assertThat(result.newState.filters.timeInterval).isEqualTo(currentFilters.timeInterval)
    assertThat(result.newState.filters.signal).isEqualTo(currentFilters.signal)
    assertThat(result.newState.filters.failureTypeToggles)
      .isEqualTo(currentFilters.failureTypeToggles)
    assertThat(result.newState.filters.visibilityType).isEqualTo(currentFilters.visibilityType)

    // Previously selected items should be selected in the new filter
    assertThat(result.newState.filters.versions)
      .isEqualTo(MultiSelection(setOf(DEFAULT_FETCHED_VERSIONS), fetchedVersion))
    assertThat(result.newState.filters.devices)
      .isEqualTo(MultiSelection(setOf(DEFAULT_FETCHED_DEVICES), fetchedDevice))
    assertThat(result.newState.filters.operatingSystems)
      .isEqualTo(MultiSelection(setOf(DEFAULT_FETCHED_OSES), fetchedOs))
  }

  @Test
  fun `vitals transition updates event immediately, and does not include variants and notes actions`() {
    val clock = FakeClock()
    val currentState =
      AppInsightsState(
        Selection(CONNECTION1, listOf(CONNECTION1)),
        TEST_FILTERS,
        LoadingState.Ready(Timed(Selection(ISSUE1, listOf(ISSUE1)), clock.instant())),
      )

    val event =
      IssuesChanged(
        LoadingState.Ready(
          IssueResponse(
            listOf(ISSUE2, ISSUE1),
            fetchedVersion,
            fetchedDevice,
            fetchedOs,
            DEFAULT_FETCHED_PERMISSIONS,
          )
        ),
        clock,
        currentState,
      )

    with(event.transition(currentState, TestAppInsightsTracker, VITALS_KEY)) {
      assertThat((newState.issues as LoadingState.Ready).value.value.selected).isEqualTo(ISSUE1)
      assertThat(newState.currentIssueVariants).isEqualTo(LoadingState.Loading)
      assertThat(newState.currentIssueDetails).isEqualTo(LoadingState.Loading)
      assertThat(newState.currentNotes).isEqualTo(LoadingState.Loading)
      assertThat(newState.currentEvents)
        .isEqualTo(LoadingState.Ready(DynamicEventGallery(listOf(ISSUE1.sampleEvent), 0, "")))
      assertThat(action).isEqualTo(Action.FetchDetails(ISSUE1.id))
    }
  }
}
