import com.android.tools.idea.insights.AppInsightsState
import com.android.tools.idea.insights.CONNECTION1
import com.android.tools.idea.insights.DynamicEventGallery
import com.android.tools.idea.insights.Event
import com.android.tools.idea.insights.EventMovement
import com.android.tools.idea.insights.ISSUE1
import com.android.tools.idea.insights.LoadingState
import com.android.tools.idea.insights.Selection
import com.android.tools.idea.insights.TEST_FILTERS
import com.android.tools.idea.insights.TEST_KEY
import com.android.tools.idea.insights.Timed
import com.android.tools.idea.insights.analytics.TestAppInsightsTracker
import com.android.tools.idea.insights.events.SelectedEventChanged
import com.android.tools.idea.insights.events.actions.Action
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import org.junit.Test

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
class SelectedEventChangedTest {

  @Test
  fun `get next event`() {
    val state =
      AppInsightsState(
        Selection(CONNECTION1, listOf(CONNECTION1)),
        TEST_FILTERS,
        LoadingState.Loading,
        currentEvents =
          LoadingState.Ready(DynamicEventGallery(listOf(Event("1"), Event("2")), 0, ""))
      )
    val transition =
      SelectedEventChanged(EventMovement.NEXT).transition(state, TestAppInsightsTracker, TEST_KEY)
    assertThat(transition.newState.currentEvents)
      .isEqualTo(LoadingState.Ready(DynamicEventGallery(listOf(Event("1"), Event("2")), 1, "")))
    assertThat(transition.action).isEqualTo(Action.NONE)
  }

  @Test
  fun `get previous event`() {
    val state =
      AppInsightsState(
        Selection(CONNECTION1, listOf(CONNECTION1)),
        TEST_FILTERS,
        LoadingState.Loading,
        currentEvents =
          LoadingState.Ready(DynamicEventGallery(listOf(Event("1"), Event("2")), 1, ""))
      )
    val transition =
      SelectedEventChanged(EventMovement.PREVIOUS)
        .transition(state, TestAppInsightsTracker, TEST_KEY)
    assertThat(transition.newState.currentEvents)
      .isEqualTo(LoadingState.Ready(DynamicEventGallery(listOf(Event("1"), Event("2")), 0, "")))
    assertThat(transition.action).isEqualTo(Action.NONE)
  }

  @Test
  fun `get next or previous event when none are available results in noop`() {
    val state =
      AppInsightsState(
        Selection(CONNECTION1, listOf(CONNECTION1)),
        TEST_FILTERS,
        LoadingState.Loading,
        currentEvents = LoadingState.Ready(DynamicEventGallery(listOf(Event("1")), 0, ""))
      )
    var transition =
      SelectedEventChanged(EventMovement.PREVIOUS)
        .transition(state, TestAppInsightsTracker, TEST_KEY)
    assertThat(transition.newState.currentEvents)
      .isEqualTo(LoadingState.Ready(DynamicEventGallery(listOf(Event("1")), 0, "")))
    assertThat(transition.action).isEqualTo(Action.NONE)

    transition =
      SelectedEventChanged(EventMovement.NEXT).transition(state, TestAppInsightsTracker, TEST_KEY)
    assertThat(transition.newState.currentEvents)
      .isEqualTo(LoadingState.Ready(DynamicEventGallery(listOf(Event("1")), 0, "")))
    assertThat(transition.action).isEqualTo(Action.NONE)
  }

  @Test
  fun `next event triggers ListEvents action when token exists`() {
    val state =
      AppInsightsState(
        Selection(CONNECTION1, listOf(CONNECTION1)),
        TEST_FILTERS,
        LoadingState.Ready(Timed(Selection(ISSUE1, listOf(ISSUE1)), Instant.now())),
        currentEvents = LoadingState.Ready(DynamicEventGallery(listOf(Event("1")), 0, "abc"))
      )

    val transition =
      SelectedEventChanged(EventMovement.NEXT).transition(state, TestAppInsightsTracker, TEST_KEY)
    assertThat(transition.newState.currentEvents)
      .isEqualTo(LoadingState.Ready(DynamicEventGallery(listOf(Event("1")), 0, "abc")))
    assertThat(transition.action).isEqualTo(Action.ListEvents(ISSUE1.id, null, "abc"))
  }
}
