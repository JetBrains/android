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
import com.android.tools.adtui.model.Range
import com.android.tools.profiler.proto.EventProfiler
import com.android.tools.profilers.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

import java.util.concurrent.TimeUnit

import com.google.common.truth.Truth.assertThat

class EventSimpleEventTooltipViewTest {

  private var mySimpleEventTooltipView: FakeEventSimpleEventTooltipView? = null
  private var myTimer: FakeTimer = FakeTimer()
  private var myMonitor: EventMonitor? = null
  private var myEventService = FakeEventService()
  @get:Rule
  val myGrpcChannel = FakeGrpcChannel("EventSimpleEventTooltipViewTest", myEventService)

  @Before
  fun setup() {
    val profilers = StudioProfilers(myGrpcChannel.client, FakeIdeProfilerServices(), myTimer)
    myTimer.tick(TimeUnit.SECONDS.toNanos(1))
    myMonitor = EventMonitor(profilers)
    val view = StudioProfilersView(profilers, FakeIdeProfilerComponents())
    // Need to set the view range, and component bounds as they are used by the view in determining how much time
    // around an event should be considered when determining if the tooltip range overlaps the icon.
    view.stageView.component.setBounds(0,0,1024,1024)
    profilers.timeline.viewRange.min = 0.0
    profilers.timeline.viewRange.max = TimeUnit.SECONDS.toNanos(1) * 1.0
    mySimpleEventTooltipView = FakeEventSimpleEventTooltipView(view.stageView, EventSimpleEventTooltip(myMonitor!!))
    val tooltipTime = TimeUnit.SECONDS.toMicros(1) + TimeUnit.MILLISECONDS.toMicros(1)
    val timelineRange = TimeUnit.SECONDS.toMicros(5)
    myMonitor!!.timeline.dataRange.set(0.0, timelineRange.toDouble())
    myMonitor!!.timeline.tooltipRange.set(tooltipTime.toDouble(), tooltipTime.toDouble())
  }

  @Test
  fun testTouchEventLongPressDuration() {
    validateSimpleEvents(1, "Touch Event - Press", EventProfiler.SystemData.SystemEventType.TOUCH)
  }

  @Test
  fun testRotationEventNoDuration() {
    validateSimpleEvents(0, "Rotation Event", EventProfiler.SystemData.SystemEventType.ROTATION)
  }

  @Test
  fun testKeyEventLongPressDuration() {
    validateSimpleEvents(1, "Key Event - Press", EventProfiler.SystemData.SystemEventType.KEY)
  }

  private fun validateSimpleEvents(durationSeconds: Long, title: String, type: EventProfiler.SystemData.SystemEventType) {
    myEventService.addSystemEvent(EventProfiler.SystemData.newBuilder()
        .setType(type)
        .setStartTimestamp(TEST_START_TIME_NS)
        .setEndTimestamp(TEST_START_TIME_NS + TimeUnit.SECONDS.toNanos(durationSeconds))
        .build())
    myTimer.tick(TimeUnit.SECONDS.toNanos(1))
    assertThat(mySimpleEventTooltipView!!.titleText).matches(title)
    assertThat(mySimpleEventTooltipView!!.durationText).matches(String.format("Duration: %ds", durationSeconds))
    assertThat(mySimpleEventTooltipView!!.startText).matches("Start: 1s")
  }


  private class FakeEventSimpleEventTooltipView(parent: StageView<*>, tooltip: EventSimpleEventTooltip) :
      EventSimpleEventTooltipView(parent, tooltip) {

    init {
      createComponent()
    }

    val titleText: String
      get() = myHeadingLabel.text

    val durationText: String
      get() = myDurationLabel.text

    val startText: String
      get() = myStartTimeLabel.text
  }

  companion object {
    private val TEST_START_TIME_NS = TimeUnit.SECONDS.toNanos(1)
  }
}
