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
package com.android.tools.idea.insights

import com.android.tools.idea.insights.analytics.TestAppInsightsTracker
import com.android.tools.idea.insights.events.actions.Action
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EventsChangedTest {

  @Test
  fun `loading events for the first time`() {
    LoadingState.Ready(EventPage(listOf(Event("event1")), ""))
    val currentState =
      AppInsightsState(
        Selection(CONNECTION1, listOf(CONNECTION1)),
        TEST_FILTERS,
        LoadingState.Loading
      )
    val event = EventsChanged(LoadingState.Ready(EventPage(listOf(Event("event1")), "")))
    val transition = event.transition(currentState, TestAppInsightsTracker, TEST_KEY)
    assertThat(transition.newState.currentEvents)
      .isEqualTo(LoadingState.Ready(DynamicEventGallery(listOf(Event("event1")), 0, "")))
    assertThat(transition.action).isEqualTo(Action.NONE)
  }

  @Test
  fun `loading new page of events appends to previous list of events and advanced index`() {
    LoadingState.Ready(EventPage(listOf(Event("event1")), ""))
    val currentState =
      AppInsightsState(
        Selection(CONNECTION1, listOf(CONNECTION1)),
        TEST_FILTERS,
        LoadingState.Loading,
        currentEvents = LoadingState.Ready(DynamicEventGallery(listOf(Event("event1")), 0, ""))
      )
    val event = EventsChanged(LoadingState.Ready(EventPage(listOf(Event("event2")), "")))
    val transition = event.transition(currentState, TestAppInsightsTracker, TEST_KEY)
    assertThat(transition.newState.currentEvents)
      .isEqualTo(
        LoadingState.Ready(DynamicEventGallery(listOf(Event("event1"), Event("event2")), 1, ""))
      )
    assertThat(transition.action).isEqualTo(Action.NONE)
  }
}
