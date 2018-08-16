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

  private lateinit var myActivityTooltipView: FakeEventActivityTooltipView
  private lateinit var myIdeProfilerServices: FakeIdeProfilerServices
  private var myTimer: FakeTimer = FakeTimer()
  private lateinit var myMonitor: EventMonitor
  private var myEventService = FakeEventService()
  @get:Rule
  val myGrpcChannel = FakeGrpcChannel("EventActivityTooltipViewTest", myEventService)

  @Before
  fun setup() {
    myIdeProfilerServices = FakeIdeProfilerServices()
    val profilers = StudioProfilers(myGrpcChannel.client, myIdeProfilerServices, myTimer)
    myTimer.tick(TimeUnit.SECONDS.toNanos(1))
    myMonitor = EventMonitor(profilers)
    val view = StudioProfilersView(profilers, FakeIdeProfilerComponents())
    myActivityTooltipView = FakeEventActivityTooltipView(view.stageView, EventActivityTooltip(myMonitor))
    val tooltipTime = TimeUnit.SECONDS.toMicros(1) + TimeUnit.MILLISECONDS.toMicros(1)
    val timelineRange = TimeUnit.SECONDS.toMicros(5)
    myMonitor.timeline.dataRange.set(0.0, timelineRange.toDouble())
    myMonitor.timeline.tooltipRange.set(tooltipTime.toDouble(), tooltipTime.toDouble())
  }

  @Test
  fun tooltipActivityAndFragmentLabelsHaveExpectedText() {
    myIdeProfilerServices.enableFragments(true)
    myEventService.addActivityEvent(
      buildActivityEvent(ACTIVITY_NAME, arrayOf(ActivityStateData(EventProfiler.ActivityStateData.ActivityState.CREATED,
                                                                  TEST_START_TIME_NS),
                                                ActivityStateData(EventProfiler.ActivityStateData.ActivityState.RESUMED,
                                                                  TEST_START_TIME_NS),
                                                ActivityStateData(EventProfiler.ActivityStateData.ActivityState.ADDED, TEST_START_TIME_NS)),
                         0))
    for (fragmentName in FRAGMENT_NAMES) {
      myEventService.addActivityEvent(
        buildActivityEvent(fragmentName,
                           arrayOf(ActivityStateData(EventProfiler.ActivityStateData.ActivityState.ADDED, TEST_START_TIME_NS)),
                           fragmentName.hashCode().toLong()))
    }
    myTimer.tick(TimeUnit.SECONDS.toNanos(2))
    assertThat(myActivityTooltipView.headingText).matches("00:01.001")
    assertThat(myActivityTooltipView.activityNameText).matches(ACTIVITY_NAME)
    assertThat(myActivityTooltipView.fragmentsText).matches("<html>TestFragment1<br>TestFragment2</html>")
    assertThat(myActivityTooltipView.durationText).matches("00:01.000 - 00:03.000")
    myIdeProfilerServices.enableFragments(false)
  }

  @Test
  fun tooltipFragmentLabelShouldBeEmptyWhenDisabled() {
    myIdeProfilerServices.enableFragments(false)
    myEventService.addActivityEvent(
      buildActivityEvent(ACTIVITY_NAME, arrayOf(ActivityStateData(EventProfiler.ActivityStateData.ActivityState.CREATED,
                                                                  TEST_START_TIME_NS),
                                                ActivityStateData(EventProfiler.ActivityStateData.ActivityState.RESUMED,
                                                                  TEST_START_TIME_NS),
                                                ActivityStateData(EventProfiler.ActivityStateData.ActivityState.ADDED, TEST_START_TIME_NS)),
                         0))
    for (fragmentName in FRAGMENT_NAMES) {
      myEventService.addActivityEvent(
        buildActivityEvent(fragmentName,
                           arrayOf(ActivityStateData(EventProfiler.ActivityStateData.ActivityState.ADDED, TEST_START_TIME_NS)),
                           fragmentName.hashCode().toLong()))
    }
    myTimer.tick(TimeUnit.SECONDS.toNanos(2))
    assertThat(myActivityTooltipView.headingText).matches("00:01.001")
    assertThat(myActivityTooltipView.activityNameText).matches(ACTIVITY_NAME)
    assertThat(myActivityTooltipView.fragmentsText).matches("")
    assertThat(myActivityTooltipView.durationText).matches("00:01.000 - 00:03.000")
  }

  @Test
  fun tooltipRangeChangeShouldBeHandled() {
    myEventService.addActivityEvent(
      buildActivityEvent(ACTIVITY_NAME, arrayOf(
        ActivityStateData(EventProfiler.ActivityStateData.ActivityState.ADDED, TimeUnit.SECONDS.toNanos(1)),
        ActivityStateData(EventProfiler.ActivityStateData.ActivityState.PAUSED, TimeUnit.SECONDS.toNanos(5))
      ), 0))

    myEventService.addActivityEvent(
      buildActivityEvent(OTHER_ACTIVITY_NAME, arrayOf(
        ActivityStateData(EventProfiler.ActivityStateData.ActivityState.ADDED, TimeUnit.SECONDS.toNanos(6)),
        ActivityStateData(EventProfiler.ActivityStateData.ActivityState.PAUSED, TimeUnit.SECONDS.toNanos(7))
      ), 0))

    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)

    assertThat(myActivityTooltipView.headingText).matches("00:01.001")
    assertThat(myActivityTooltipView.activityNameText).matches(ACTIVITY_NAME)
    assertThat(myActivityTooltipView.durationText).matches("00:01.000 - 00:05.000")

    val tooltipTime = TimeUnit.SECONDS.toMicros(6).toDouble()
    myMonitor.timeline.tooltipRange.set(tooltipTime, tooltipTime)

    assertThat(myActivityTooltipView.headingText).matches("00:06.000")
    assertThat(myActivityTooltipView.activityNameText).matches(OTHER_ACTIVITY_NAME)
    assertThat(myActivityTooltipView.durationText).matches("00:06.000 - 00:07.000")
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
    assertThat(myActivityTooltipView.headingText).matches("00:01.001")
    assertThat(myActivityTooltipView.activityNameText).matches(String.format("%s - destroyed", ACTIVITY_NAME))
    assertThat(myActivityTooltipView.durationText).matches("00:01.000 - 00:02.000")
  }

  @Test
  fun testGetTitleTextNone() {
    myTimer.tick(1)
    val text = myActivityTooltipView.headingText
    assertEquals("00:01.001", text)
  }

  private fun buildActivityEvent(name: String,
                                 states: Array<ActivityStateData>,
                                 contextHash: Long): EventProfiler.ActivityData {
    val builder = EventProfiler.ActivityData.newBuilder()
    builder.setPid(ProfilersTestData.SESSION_DATA.pid)
        .setName(name)
        .setHash(name.hashCode().toLong())
        .setFragmentData(EventProfiler.FragmentData.newBuilder().setActivityContextHash(contextHash))
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

    val activityNameText: String
      get() = activityNameLabel.text

    val durationText: String
      get() = durationLabel.text

    val fragmentsText: String
      get() = fragmentsLabel.text
  }

  private class ActivityStateData constructor(var activityState: EventProfiler.ActivityStateData.ActivityState, var activityStateTime: Long)

  companion object {
    private val TEST_START_TIME_NS = TimeUnit.SECONDS.toNanos(1)
    private val ACTIVITY_NAME = "TestActivity"
    private val FRAGMENT_NAMES = arrayOf("TestFragment1", "TestFragment2")
    private val OTHER_ACTIVITY_NAME = "OtherTestActivity"
  }
}
