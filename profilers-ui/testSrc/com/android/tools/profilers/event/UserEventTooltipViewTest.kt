/*
 * Copyright (C) 2017 The Android Open Source Project
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
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Interaction
import com.android.tools.profilers.FakeIdeProfilerComponents
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.SessionProfilersView
import com.android.tools.profilers.StageView
import com.android.tools.profilers.StudioProfilers
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

class UserEventTooltipViewTest {
  private lateinit var simpleEventTooltipView: FakeUserEventTooltipView
  private val timer: FakeTimer = FakeTimer()
  private lateinit var monitor: EventMonitor
  private val transportService = FakeTransportService(timer)

  @get:Rule
  val grpcChannel = FakeGrpcChannel("UserEventTooltipViewTest", transportService)

  @get:Rule
  val applicationRule = ApplicationRule()

  @get:Rule
  val disposableRule = DisposableRule()

  @Before
  fun setup() {
    val profilers = StudioProfilers(ProfilerClient(grpcChannel.channel), FakeIdeProfilerServices(), timer)
    timer.tick(TimeUnit.SECONDS.toNanos(1))
    monitor = EventMonitor(profilers)
    val view = SessionProfilersView(profilers, FakeIdeProfilerComponents(), disposableRule.disposable)
    // Need to set the view range, and component bounds as they are used by the view in determining how much time
    // around an event should be considered when determining if the tooltip range overlaps the icon.
    view.stageView!!.component.setBounds(0, 0, 1024, 1024)
    profilers.timeline.viewRange.min = 0.0
    profilers.timeline.viewRange.max = TimeUnit.SECONDS.toMicros(10).toDouble()
    simpleEventTooltipView = FakeUserEventTooltipView(view.stageView!!, UserEventTooltip(monitor.timeline, monitor.userEvents))
    val tooltipTime = TimeUnit.SECONDS.toMicros(1) + TimeUnit.MILLISECONDS.toMicros(1)
    val timelineRange = TimeUnit.SECONDS.toMicros(5)
    monitor.timeline.dataRange.set(0.0, timelineRange.toDouble())
    monitor.timeline.tooltipRange.set(tooltipTime.toDouble(), tooltipTime.toDouble())
  }

  @Test
  fun testTouchEventLongPressDuration() {
    transportService.addEventToStream(FakeTransportService.FAKE_DEVICE_ID,
                                      Common.Event.newBuilder()
                                        .setKind(Common.Event.Kind.INTERACTION)
                                        .setTimestamp(TEST_START_TIME_NS)
                                        .setGroupId(1)
                                        .setInteraction(
                                          Interaction.InteractionData.newBuilder().setType(Interaction.InteractionData.Type.TOUCH))
                                        .build())
    transportService.addEventToStream(FakeTransportService.FAKE_DEVICE_ID,
                                      Common.Event.newBuilder()
                                        .setKind(Common.Event.Kind.INTERACTION)
                                        .setTimestamp(TEST_START_TIME_NS + TimeUnit.SECONDS.toNanos(1))
                                        .setGroupId(1)
                                        .setIsEnded(true)
                                        .setInteraction(
                                          Interaction.InteractionData.newBuilder().setType(Interaction.InteractionData.Type.TOUCH))
                                        .build())
    timer.tick(TimeUnit.SECONDS.toNanos(1))
    assertThat(simpleEventTooltipView.headingText).matches("00:01.001")
    assertThat(simpleEventTooltipView.contentText).matches("Touch Event - Press")
    assertThat(simpleEventTooltipView.durationText).matches("Duration: 1 s")
    assertThat(simpleEventTooltipView.startText).matches("Start: 00:01.000")
  }

  @Test
  fun testRotationEventNoDuration() {
    transportService.addEventToStream(FakeTransportService.FAKE_DEVICE_ID,
                                      Common.Event.newBuilder()
                                        .setKind(Common.Event.Kind.INTERACTION)
                                        .setTimestamp(TEST_START_TIME_NS)
                                        .setGroupId(2)
                                        .setIsEnded(true)
                                        .setInteraction(
                                          Interaction.InteractionData.newBuilder().setType(Interaction.InteractionData.Type.ROTATION))
                                        .build())
    timer.tick(TimeUnit.SECONDS.toNanos(1))
    assertThat(simpleEventTooltipView.headingText).matches("00:01.001")
    assertThat(simpleEventTooltipView.contentText).matches("Rotation Event")
    assertThat(simpleEventTooltipView.durationText).matches("Duration: 0 μs")
    assertThat(simpleEventTooltipView.startText).matches("Start: 00:01.000")
  }

  @Test
  fun testKeyEventNoDuration() {
    transportService.addEventToStream(FakeTransportService.FAKE_DEVICE_ID,
                                      Common.Event.newBuilder()
                                        .setKind(Common.Event.Kind.INTERACTION)
                                        .setTimestamp(TEST_START_TIME_NS)
                                        .setGroupId(3)
                                        .setIsEnded(true)
                                        .setInteraction(
                                          Interaction.InteractionData.newBuilder().setType(Interaction.InteractionData.Type.KEY))
                                        .build())
    timer.tick(TimeUnit.SECONDS.toNanos(1))
    assertThat(simpleEventTooltipView.headingText).matches("00:01.001")
    assertThat(simpleEventTooltipView.contentText).matches("Key Event - Press")
    assertThat(simpleEventTooltipView.durationText).matches("Duration: 0 μs")
    assertThat(simpleEventTooltipView.startText).matches("Start: 00:01.000")

    monitor.timeline.tooltipRange.set(TimeUnit.SECONDS.toMicros(2).toDouble(), TimeUnit.SECONDS.toMicros(2).toDouble())
    assertThat(simpleEventTooltipView.headingText).matches("00:02.000")
    assertThat(simpleEventTooltipView.contentText).matches("")
    assertThat(simpleEventTooltipView.durationText).matches("")
    assertThat(simpleEventTooltipView.startText).matches("")
  }

  private class FakeUserEventTooltipView(parent: StageView<*>, tooltip: UserEventTooltip) :
    UserEventTooltipView(parent.component, tooltip) {

    init {
      createComponent()
    }

    val contentText: String
      get() = myContentLabel.text

    val durationText: String
      get() = myDurationLabel.text

    val startText: String
      get() = myStartTimeLabel.text
  }

  companion object {
    private val TEST_START_TIME_NS = TimeUnit.SECONDS.toNanos(1)
  }
}
