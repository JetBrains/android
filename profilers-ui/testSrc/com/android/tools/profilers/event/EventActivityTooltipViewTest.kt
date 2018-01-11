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
import com.android.tools.profiler.proto.EventProfiler
import com.android.tools.profilers.*
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

class EventActivityTooltipViewTest {

  private var myActivityTooltipView: FakeEventActivityTooltipView? = null
  private var myTimer: FakeTimer = FakeTimer()
  private var myMonitor: EventMonitor? = null
  private var myEventService = FakeEventService()
  @get:Rule
  val myGrpcChannel = FakeGrpcChannel("EventActivityTooltipViewTest", myEventService)

  @Before
  fun setup() {
    val profilers = StudioProfilers(myGrpcChannel.client, FakeIdeProfilerServices(), myTimer)
    myTimer.tick(TimeUnit.SECONDS.toNanos(1))
    myMonitor = EventMonitor(profilers)
    val view = StudioProfilersView(profilers, FakeIdeProfilerComponents())
    myActivityTooltipView = FakeEventActivityTooltipView(view.stageView, EventActivityTooltip(myMonitor!!))
    val tooltipTime = TimeUnit.SECONDS.toMicros(1) + TimeUnit.MILLISECONDS.toMicros(1)
    val timelineRange = TimeUnit.SECONDS.toMicros(5)
    myMonitor!!.timeline.dataRange.set(0.0, timelineRange.toDouble())
    myMonitor!!.timeline.tooltipRange.set(tooltipTime.toDouble(), tooltipTime.toDouble())
  }

  @Test
  fun testGetActivityTitleText() {
    myEventService.addActivityEvent(
        buildActivityEvent(ACTIVITY_NAME,
            arrayOf(ActivityStateData(EventProfiler.ActivityStateData.ActivityState.CREATED,
                TEST_START_TIME_NS), ActivityStateData(EventProfiler.ActivityStateData.ActivityState.RESUMED,
                TEST_START_TIME_NS)),
            0
        ))
    myTimer.tick(TimeUnit.SECONDS.toNanos(2))
    assertThat(myActivityTooltipView!!.titleText).matches(ACTIVITY_NAME)
    assertThat(myActivityTooltipView!!.durationText).matches("1s - 3s")
  }

  @Test
  fun testGetActivityTitleTextCompleted() {
    myEventService.addActivityEvent(
        buildActivityEvent(ACTIVITY_NAME,
            arrayOf(ActivityStateData(EventProfiler.ActivityStateData.ActivityState.CREATED,
                TEST_START_TIME_NS), ActivityStateData(EventProfiler.ActivityStateData.ActivityState.RESUMED,
                TEST_START_TIME_NS), ActivityStateData(EventProfiler.ActivityStateData.ActivityState.PAUSED,
                TEST_START_TIME_NS + TimeUnit.SECONDS.toNanos(1)),
                ActivityStateData(EventProfiler.ActivityStateData.ActivityState.DESTROYED,
                TEST_START_TIME_NS + TimeUnit.SECONDS.toNanos(1))),
            0
        ))
    myTimer.tick(TimeUnit.SECONDS.toNanos(2))
    assertThat(myActivityTooltipView!!.titleText).matches(String.format("%s - destroyed", ACTIVITY_NAME))
    assertThat(myActivityTooltipView!!.durationText).matches("1s - 2s")
  }

  @Test
  fun testGetTitleTextNone() {
    myTimer.tick(1)
    val text = myActivityTooltipView!!.titleText
    assertEquals("EVENTS at 1s1.00ms", text)
  }

  private fun buildActivityEvent(name: String,
                                 states: Array<ActivityStateData>,
                                 contextHash: Long): com.android.tools.profiler.proto.EventProfiler.ActivityData {
    val builder = com.android.tools.profiler.proto.EventProfiler.ActivityData.newBuilder()
    builder.setPid(ProfilersTestData.SESSION_DATA.getPid())
        .setName(name)
        .setHash(name.hashCode().toLong())
        .setFragmentData(com.android.tools.profiler.proto.EventProfiler.FragmentData.newBuilder().setActivityContextHash(contextHash))
    for (state in states) {
      builder.addStateChanges(EventProfiler.ActivityStateData.newBuilder()
          .setState(state.activityState)
          .setTimestamp(state.activityStateTime)
          .build())
    }
    return builder.build()
  }

  private class FakeEventActivityTooltipView(parent: StageView<*>, tooltip: EventActivityTooltip) :
      EventActivityTooltipView(parent, tooltip) {

    init {
      createComponent()
    }

    val titleText: String
      get() = myHeadingLabel.text

    val durationText: String
      get() = myDurationLabel.text
  }

  private class ActivityStateData constructor(var activityState: EventProfiler.ActivityStateData.ActivityState, var activityStateTime: Long)

  companion object {
    private val TEST_START_TIME_NS = TimeUnit.SECONDS.toNanos(1)
    private val ACTIVITY_NAME = "TestActivity"
  }
}
