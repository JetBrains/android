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
import com.android.tools.idea.insights.client.AppInsightsCacheImpl
import com.android.tools.idea.insights.events.EventsChanged
import com.android.tools.idea.insights.events.actions.Action
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import org.junit.Test

class EventsChangedTest {

  @Test
  fun `loading events for the first time`() {
    val eventList = listOf(Event("event1"))
    val currentState =
      AppInsightsState(
        Selection(CONNECTION1, listOf(CONNECTION1)),
        TEST_FILTERS,
        LoadingState.Ready(Timed(Selection(ISSUE1, listOf(ISSUE1)), Instant.now())),
        currentEvents = LoadingState.Loading,
        currentInsight = LoadingState.Loading,
      )
    val event = EventsChanged(LoadingState.Ready(EventPage(eventList, "")))
    val transition =
      event.transition(
        currentState,
        TestAppInsightsTracker,
        FakeInsightsProvider(),
        AppInsightsCacheImpl(),
      )
    assertThat(transition.newState.currentEvents)
      .isEqualTo(LoadingState.Ready(DynamicEventGallery(eventList, 0, "")))
    assertThat(transition.action)
      .isEqualTo(
        Action.FetchInsight(ISSUE1.id, null, ISSUE1.issueDetails.fatality, eventList.first())
      )
  }

  @Test
  fun `loading new page of events appends to previous list of events`() {
    val currentState =
      AppInsightsState(
        Selection(CONNECTION1, listOf(CONNECTION1)),
        TEST_FILTERS,
        LoadingState.Loading,
        currentEvents = LoadingState.Ready(DynamicEventGallery(listOf(Event("event1")), 0, "")),
        currentInsight = LoadingState.Ready(DEFAULT_AI_INSIGHT),
      )
    val event = EventsChanged(LoadingState.Ready(EventPage(listOf(Event("event2")), "")))
    val transition =
      event.transition(
        currentState,
        TestAppInsightsTracker,
        FakeInsightsProvider(),
        AppInsightsCacheImpl(),
      )
    assertThat(transition.newState.currentEvents)
      .isEqualTo(
        LoadingState.Ready(DynamicEventGallery(listOf(Event("event1"), Event("event2")), 0, ""))
      )
    assertThat(transition.action).isEqualTo(Action.NONE)
  }

  @Test
  fun `propagate failure to get event to state`() {
    val currentState =
      AppInsightsState(
        Selection(CONNECTION1, listOf(CONNECTION1)),
        TEST_FILTERS,
        issues = LoadingState.Ready(Timed(Selection(ISSUE1, listOf(ISSUE1)), Instant.now())),
        currentEvents = LoadingState.Loading,
      )
    val failure = LoadingState.NetworkFailure("failed")
    val event = EventsChanged(failure)
    val transition =
      event.transition(
        currentState,
        TestAppInsightsTracker,
        FakeInsightsProvider(),
        AppInsightsCacheImpl(),
      )
    assertThat(transition.newState.currentEvents).isEqualTo(failure)
    assertThat(transition.action)
      .isEqualTo(Action.FetchInsight(ISSUE1.id, null, ISSUE1.issueDetails.fatality, Event.EMPTY))
  }
}
