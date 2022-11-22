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
import com.android.tools.profiler.proto.EventProfiler
import com.android.tools.profiler.proto.Interaction
import com.android.tools.profilers.FakeIdeProfilerComponents
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.StageView
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.StudioProfilersView
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ApplicationRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

class UserEventTooltipViewTest {

  private var mySimpleEventTooltipView: FakeUserEventTooltipView? = null
  private var myTimer: FakeTimer = FakeTimer()
  private var myMonitor: EventMonitor? = null
  private var myEventService = FakeEventService()
  private val transportService = FakeTransportService(myTimer)

  @get:Rule
  val myGrpcChannel = FakeGrpcChannel("UserEventTooltipViewTest", myEventService, transportService)

  @get:Rule
  val applicationRule = ApplicationRule()

  @Before
  fun setup() {
    val profilers = StudioProfilers(ProfilerClient(myGrpcChannel.channel), FakeIdeProfilerServices(), myTimer)
    myTimer.tick(TimeUnit.SECONDS.toNanos(1))
    myMonitor = EventMonitor(profilers)
    val view = StudioProfilersView(profilers, FakeIdeProfilerComponents())
    // Need to set the view range, and component bounds as they are used by the view in determining how much time
    // around an event should be considered when determining if the tooltip range overlaps the icon.
    view.stageView.component.setBounds(0, 0, 1024, 1024)
    profilers.timeline.viewRange.min = 0.0
    profilers.timeline.viewRange.max = TimeUnit.SECONDS.toNanos(1) * 1.0
    mySimpleEventTooltipView = FakeUserEventTooltipView(view.stageView, UserEventTooltip(myMonitor!!.timeline, myMonitor!!.userEvents))
    val tooltipTime = TimeUnit.SECONDS.toMicros(1) + TimeUnit.MILLISECONDS.toMicros(1)
    val timelineRange = TimeUnit.SECONDS.toMicros(5)
    myMonitor!!.timeline.dataRange.set(0.0, timelineRange.toDouble())
    myMonitor!!.timeline.tooltipRange.set(tooltipTime.toDouble(), tooltipTime.toDouble())
  }

  @Test
  fun testTouchEventLongPressDuration() {
    validateSimpleEvents(1, "Touch Event - Press", Interaction.InteractionData.Type.TOUCH)
  }

  @Test
  fun testRotationEventNoDuration() {
    validateSimpleEvents(2, "Rotation Event", Interaction.InteractionData.Type.ROTATION)
  }

  @Test
  fun testKeyEventLongPressDuration() {
    validateSimpleEvents(1, "Key Event - Press", Interaction.InteractionData.Type.KEY)
  }

  private fun validateSimpleEvents(durationSeconds: Long, title: String, type: Interaction.InteractionData.Type) {
    myEventService.addSystemEvent(EventProfiler.SystemData.newBuilder()
        .setType(type)
        .setStartTimestamp(TEST_START_TIME_NS)
        .setEndTimestamp(TEST_START_TIME_NS + TimeUnit.SECONDS.toNanos(durationSeconds))
        .build())
    myTimer.tick(TimeUnit.SECONDS.toNanos(1))
    assertThat(mySimpleEventTooltipView!!.headingText).matches("00:01.001")
    assertThat(mySimpleEventTooltipView!!.contentText).matches(title)
    assertThat(mySimpleEventTooltipView!!.durationText).matches(String.format("Duration: %d s", durationSeconds))
    assertThat(mySimpleEventTooltipView!!.startText).matches("Start: 00:01.000")

    myEventService.clearSystemEvents()
    myTimer.tick(TimeUnit.SECONDS.toNanos(1))
    assertThat(mySimpleEventTooltipView!!.headingText).matches("00:01.001")
    assertThat(mySimpleEventTooltipView!!.contentText).isEmpty()
    assertThat(mySimpleEventTooltipView!!.durationText).isEmpty()
    assertThat(mySimpleEventTooltipView!!.startText).isEmpty()
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
