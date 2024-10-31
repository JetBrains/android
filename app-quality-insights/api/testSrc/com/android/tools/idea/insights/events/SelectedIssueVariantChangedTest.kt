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
import com.android.tools.idea.insights.CONNECTION1
import com.android.tools.idea.insights.ISSUE1
import com.android.tools.idea.insights.ISSUE2
import com.android.tools.idea.insights.ISSUE_VARIANT
import com.android.tools.idea.insights.ISSUE_VARIANT2
import com.android.tools.idea.insights.LoadingState
import com.android.tools.idea.insights.Selection
import com.android.tools.idea.insights.TEST_FILTERS
import com.android.tools.idea.insights.TEST_KEY
import com.android.tools.idea.insights.Timed
import com.android.tools.idea.insights.analytics.TestAppInsightsTracker
import com.android.tools.idea.insights.events.actions.Action
import com.google.common.truth.Truth.*
import java.time.Instant
import org.junit.Test

class SelectedIssueVariantChangedTest {

  @Test
  fun `variant selection causes state to update`() {
    val currentState =
      AppInsightsState(
        Selection(CONNECTION1, listOf(CONNECTION1)),
        TEST_FILTERS,
        LoadingState.Ready(Timed(Selection(ISSUE1, listOf(ISSUE1, ISSUE2)), Instant.now())),
        LoadingState.Ready(Selection(ISSUE_VARIANT, listOf(ISSUE_VARIANT, ISSUE_VARIANT2))),
      )

    val transition =
      SelectedIssueVariantChanged(ISSUE_VARIANT2)
        .transition(currentState, TestAppInsightsTracker, TEST_KEY)

    with(transition) {
      assertThat(transition.newState.currentIssueVariants)
        .isEqualTo(
          LoadingState.Ready(Selection(ISSUE_VARIANT2, listOf(ISSUE_VARIANT, ISSUE_VARIANT2)))
        )
      assertThat(transition.newState.currentIssueDetails)
        .isInstanceOf(LoadingState.Loading::class.java)

      assertThat(action)
        .isEqualTo(
          Action.FetchDetails(ISSUE1.id, ISSUE_VARIANT2.id) and
            Action.ListEvents(ISSUE1.id, ISSUE_VARIANT2.id, null)
        )
    }
  }

  @Test
  fun `variant deselection causes state to update`() {
    val currentState =
      AppInsightsState(
        Selection(CONNECTION1, listOf(CONNECTION1)),
        TEST_FILTERS,
        LoadingState.Ready(Timed(Selection(ISSUE1, listOf(ISSUE1, ISSUE2)), Instant.now())),
        LoadingState.Ready(Selection(ISSUE_VARIANT, listOf(ISSUE_VARIANT, ISSUE_VARIANT2))),
      )

    val transition =
      SelectedIssueVariantChanged(null).transition(currentState, TestAppInsightsTracker, TEST_KEY)

    with(transition) {
      assertThat(transition.newState.currentIssueVariants)
        .isEqualTo(LoadingState.Ready(Selection(null, listOf(ISSUE_VARIANT, ISSUE_VARIANT2))))
      assertThat(transition.newState.currentIssueDetails)
        .isInstanceOf(LoadingState.Loading::class.java)

      assertThat(action)
        .isEqualTo(
          Action.FetchDetails(ISSUE1.id, null) and Action.ListEvents(ISSUE1.id, null, null)
        )
    }
  }

  @Test
  fun `same variant selected results in noop`() {
    val currentState =
      AppInsightsState(
        Selection(CONNECTION1, listOf(CONNECTION1)),
        TEST_FILTERS,
        LoadingState.Ready(Timed(Selection(ISSUE1, listOf(ISSUE1, ISSUE2)), Instant.now())),
        LoadingState.Ready(Selection(ISSUE_VARIANT, listOf(ISSUE_VARIANT, ISSUE_VARIANT2))),
      )

    val transition =
      SelectedIssueVariantChanged(ISSUE_VARIANT)
        .transition(currentState, TestAppInsightsTracker, TEST_KEY)

    with(transition) {
      assertThat(transition.newState).isEqualTo(currentState)

      assertThat(action).isSameAs(Action.NONE)
    }
  }
}
