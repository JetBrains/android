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
import com.android.tools.idea.insights.DEFAULT_FETCHED_PERMISSIONS
import com.android.tools.idea.insights.DynamicEventGallery
import com.android.tools.idea.insights.ISSUE1
import com.android.tools.idea.insights.ISSUE2
import com.android.tools.idea.insights.LoadingState
import com.android.tools.idea.insights.Selection
import com.android.tools.idea.insights.TEST_FILTERS
import com.android.tools.idea.insights.TEST_KEY
import com.android.tools.idea.insights.Timed
import com.android.tools.idea.insights.analytics.IssueSelectionSource
import com.android.tools.idea.insights.analytics.TestAppInsightsTracker
import com.android.tools.idea.insights.client.IssueResponse
import com.android.tools.idea.insights.events.actions.Action
import com.google.common.truth.Truth.*
import java.time.Instant
import org.junit.Rule
import org.junit.Test

class PreJIssueTransitionTest {
  @get:Rule val flagRule = FlagRule(StudioFlags.CRASHLYTICS_J_UI, false)

  @Test
  fun `issues changed transitions using event from issue, without calling listEvents`() {
    val currentState =
      AppInsightsState(
        Selection(CONNECTION1, listOf(CONNECTION1)),
        TEST_FILTERS,
        LoadingState.Ready(Timed(Selection(null, emptyList()), Instant.now())),
      )

    val event =
      IssuesChanged(
        LoadingState.Ready(
          IssueResponse(
            listOf(ISSUE1),
            emptyList(),
            emptyList(),
            emptyList(),
            DEFAULT_FETCHED_PERMISSIONS,
          )
        ),
        FakeClock(),
        currentState,
      )

    with(event.transition(currentState, TestAppInsightsTracker, TEST_KEY)) {
      assertThat(newState.currentEvents)
        .isEqualTo(LoadingState.Ready(DynamicEventGallery(listOf(ISSUE1.sampleEvent), 0, "")))
      assertThat(newState.currentIssueVariants).isEqualTo(LoadingState.Loading)
      assertThat(newState.currentIssueDetails).isEqualTo(LoadingState.Loading)
      assertThat(newState.currentNotes).isEqualTo(LoadingState.Loading)
      assertThat(action)
        .isEqualTo(
          Action.FetchDetails(ISSUE1.id, null) and
            Action.FetchIssueVariants(ISSUE1.id) and
            Action.FetchNotes(ISSUE1.id)
        )
    }
  }

  @Test
  fun `selected issue changed transitions using event from issue, without calling listEvents`() {
    val currentState =
      AppInsightsState(
        Selection(CONNECTION1, listOf(CONNECTION1)),
        TEST_FILTERS,
        LoadingState.Ready(Timed(Selection(ISSUE2, listOf(ISSUE1, ISSUE2)), Instant.now())),
      )

    val event = SelectedIssueChanged(ISSUE1, IssueSelectionSource.LIST)

    with(event.transition(currentState, TestAppInsightsTracker, TEST_KEY)) {
      assertThat(newState.currentEvents)
        .isEqualTo(LoadingState.Ready(DynamicEventGallery(listOf(ISSUE1.sampleEvent), 0, "")))
      assertThat(newState.currentIssueVariants).isEqualTo(LoadingState.Loading)
      assertThat(newState.currentIssueDetails).isEqualTo(LoadingState.Loading)
      assertThat(newState.currentNotes).isEqualTo(LoadingState.Loading)
      assertThat(action)
        .isEqualTo(
          Action.FetchDetails(ISSUE1.id, null) and
            Action.FetchIssueVariants(ISSUE1.id) and
            Action.FetchNotes(ISSUE1.id)
        )
    }
  }
}
