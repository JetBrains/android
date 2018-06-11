/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.profilers.event

import com.android.tools.adtui.model.FakeTimer
import com.android.tools.adtui.model.event.ActivityAction
import com.android.tools.adtui.model.event.EventAction
import com.android.tools.adtui.model.event.KeyboardAction
import com.android.tools.adtui.model.event.SimpleEventType
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.EventProfiler
import com.android.tools.profilers.*
import com.android.tools.profilers.ProfilersTestData.DEFAULT_AGENT_ATTACHED_RESPONSE
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class EventMonitorTest {
  private val eventService = FakeEventService()

  private val profilerService = FakeProfilerService(false)

  @get:Rule
  val grpcChannel = FakeGrpcChannel("EventMonitorTest", profilerService, eventService)

  private lateinit var monitor: EventMonitor
  private lateinit var timer: FakeTimer
  private lateinit var profilers: StudioProfilers

  @Before
  fun setUp() {
    timer = FakeTimer()
    val services = FakeIdeProfilerServices()
    profilers = StudioProfilers(grpcChannel.client, services, timer)
    monitor = EventMonitor(profilers)
  }

  @Test
  fun monitorEnabledOnAgentAttached() {
    assertThat(monitor.isEnabled).isFalse() // Monitor is not enabled on start.

    val session = Common.Session.newBuilder()
      .setSessionId(2).setStartTimestamp(FakeTimer.ONE_SECOND_IN_NS).setEndTimestamp(FakeTimer.ONE_SECOND_IN_NS * 2).build()
    val sessionOMetadata = Common.SessionMetaData.newBuilder()
      .setSessionId(2).setType(Common.SessionMetaData.SessionType.FULL).setJvmtiEnabled(true).setStartTimestampEpochMs(1).build()
    profilerService.addSession(session, sessionOMetadata)
    timer.tick(FakeTimer.ONE_SECOND_IN_NS)

    // Set the session without attaching the agent. The monitor should still be disabled.
    profilers.sessionsManager.setSession(session)
    assertThat(monitor.isEnabled).isFalse()

    // Set the session to something else, to make sure we won't return early when setting the session again
    profilers.sessionsManager.setSession(Common.Session.getDefaultInstance())
    timer.tick(FakeTimer.ONE_SECOND_IN_NS)

    // Make sure the agent is attached and set the session afterwards. Monitor should be enabled.
    profilerService.setAgentStatus(DEFAULT_AGENT_ATTACHED_RESPONSE)
    profilers.sessionsManager.setSession(session)

    assertThat(monitor.isEnabled).isTrue()
  }

  @Test
  fun testName() {
    assertThat(monitor.name).isEqualTo("EVENTS")
  }

  @Test
  fun tooltipBuilderShouldGenerateTooltip() {
    val tooltip = EventActivityTooltip(monitor)
    monitor.setTooltipBuilder{ tooltip }
    assertThat(monitor.buildTooltip()).isEqualTo(tooltip)
  }

  @Test
  fun buildTooltipWithNullTooltipBuilderGeneratesNewTooltip() {
    val tooltip1 = monitor.buildTooltip()
    val tooltip2 = monitor.buildTooltip()

    // Each call to buildTooltip should return a different tooltip.
    assertThat(tooltip1).isNotEqualTo(tooltip2)
  }

  @Test
  fun monitorCantBeExpanded() {
    assertThat(monitor.canExpand()).isFalse()

    assertThat(monitor.profilers.stage).isInstanceOf(NullMonitorStage::class.java)
    // One second is enough for new devices (and processes) to be picked up
    timer.tick(FakeTimer.ONE_SECOND_IN_NS)

    monitor.expand()
    assertThat(monitor.profilers.stage).isInstanceOf(NullMonitorStage::class.java)
  }

  @Test
  fun simpleEvents() {
    // Populate the service with some events
    val rotation = EventProfiler.SystemData.newBuilder().setEventId(1).setType(EventProfiler.SystemData.SystemEventType.ROTATION).build()
    eventService.addSystemEvent(rotation)
    val touch = EventProfiler.SystemData.newBuilder().setEventId(2).setType(EventProfiler.SystemData.SystemEventType.TOUCH).build()
    eventService.addSystemEvent(touch)
    val key = EventProfiler.SystemData.newBuilder()
      .setEventId(3)
      .setType(EventProfiler.SystemData.SystemEventType.KEY)
      .setEventData("Some Text")
      .build()
    eventService.addSystemEvent(key)
    val unspecified = EventProfiler.SystemData.newBuilder().setEventId(4).setType(EventProfiler.SystemData.SystemEventType.UNSPECIFIED).build()
    eventService.addSystemEvent(unspecified)

    val series = monitor.simpleEvents.rangedSeries.series
    assertThat(series).hasSize(3) // unspecified shouldn't be returned.

    assertThat(series[0].value.type).isEqualTo(SimpleEventType.ROTATION)
    assertThat(series[0].value).isInstanceOf(EventAction::class.java)

    assertThat(series[1].value.type).isEqualTo(SimpleEventType.TOUCH)
    assertThat(series[1].value).isInstanceOf(EventAction::class.java)

    assertThat(series[2].value.type).isEqualTo(SimpleEventType.KEYBOARD)
    assertThat(series[2].value).isInstanceOf(KeyboardAction::class.java)
    assertThat((series[2].value as KeyboardAction).data.toString()).isEqualTo("Some Text")
  }

  @Test
  fun activityEvents() {
    // Populate the service with some events
    val activity1 = EventProfiler.ActivityData.newBuilder()
      .setName("activity 1")
      .setHash(1)
      .addStateChanges(EventProfiler.ActivityStateData.newBuilder().setState(EventProfiler.ActivityStateData.ActivityState.ADDED))
      .build()
    eventService.addActivityEvent(activity1)
    val fragment = EventProfiler.ActivityData.newBuilder()
      .setName("fragment 1")
      .setHash(2)
      .addStateChanges(EventProfiler.ActivityStateData.newBuilder().setState(EventProfiler.ActivityStateData.ActivityState.ADDED))
      .setFragmentData(EventProfiler.FragmentData.newBuilder().setActivityContextHash(1).build())
      .build()
    eventService.addActivityEvent(fragment)
    val activity2 = EventProfiler.ActivityData.newBuilder()
      .setName("activity 2")
      .setHash(3)
      .addStateChanges(EventProfiler.ActivityStateData.newBuilder().setState(EventProfiler.ActivityStateData.ActivityState.ADDED))
      .build()
    eventService.addActivityEvent(activity2)

    val series = monitor.activityEvents.rangedSeries.series
    assertThat(series).hasSize(2) // fragment shouldn't be returned

    assertThat(series[0].value).isInstanceOf(ActivityAction::class.java)
    assertThat((series[0].value as ActivityAction).data).isEqualTo("activity 1")

    assertThat(series[1].value).isInstanceOf(ActivityAction::class.java)
    assertThat((series[1].value as ActivityAction).data).isEqualTo("activity 2")
  }

  @Test
  fun fragmentEvents() {
    // Populate the service with some events
    val activity1 = EventProfiler.ActivityData.newBuilder()
      .setName("activity 1")
      .setHash(1)
      .addStateChanges(EventProfiler.ActivityStateData.newBuilder().setState(EventProfiler.ActivityStateData.ActivityState.ADDED))
      .build()
    eventService.addActivityEvent(activity1)
    val fragment = EventProfiler.ActivityData.newBuilder()
      .setName("fragment 1")
      .setHash(2)
      .addStateChanges(EventProfiler.ActivityStateData.newBuilder().setState(EventProfiler.ActivityStateData.ActivityState.ADDED))
      .setFragmentData(EventProfiler.FragmentData.newBuilder().setActivityContextHash(1).build())
      .build()
    eventService.addActivityEvent(fragment)
    val activity2 = EventProfiler.ActivityData.newBuilder()
      .setName("activity 2")
      .setHash(3)
      .addStateChanges(EventProfiler.ActivityStateData.newBuilder().setState(EventProfiler.ActivityStateData.ActivityState.ADDED))
      .build()
    eventService.addActivityEvent(activity2)

    val series = monitor.fragmentEvents.rangedSeries.series
    assertThat(series).hasSize(1) // activities shouldn't be returned

    assertThat(series[0].value).isInstanceOf(ActivityAction::class.java)
    assertThat((series[0].value as ActivityAction).data).isEqualTo("fragment 1")
  }
}