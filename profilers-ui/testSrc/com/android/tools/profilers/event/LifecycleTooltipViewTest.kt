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
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit
import javax.swing.JLabel
import javax.swing.JPanel

class LifecycleTooltipViewTest {

  private lateinit var myActivityTooltipView: FakeLifecycleTooltipView
  private lateinit var myIdeProfilerServices: FakeIdeProfilerServices
  private var myTimer: FakeTimer = FakeTimer()
  private lateinit var myMonitor: EventMonitor
  private var myEventService = FakeEventService()
  private val transportService = FakeTransportService(myTimer)

  @get:Rule
  val myGrpcChannel = FakeGrpcChannel("LifecycleTooltipViewTest", myEventService, transportService)

  @get:Rule
  val applicationRule = ApplicationRule()

  @Before
  fun setup() {
    myIdeProfilerServices = FakeIdeProfilerServices()
    val profilers = StudioProfilers(ProfilerClient(myGrpcChannel.channel), myIdeProfilerServices, myTimer)
    myTimer.tick(TimeUnit.SECONDS.toNanos(1))
    myMonitor = EventMonitor(profilers)
    val view = StudioProfilersView(profilers, FakeIdeProfilerComponents())
    myActivityTooltipView = FakeLifecycleTooltipView(view.stageView, LifecycleTooltip(myMonitor.timeline, myMonitor.lifecycleEvents))
    view.stageView.component.setBounds(0, 0, 1024, 256)
    profilers.timeline.viewRange.min = 0.0
    profilers.timeline.viewRange.max = TimeUnit.SECONDS.toMicros(10).toDouble()
    val tooltipTime = TimeUnit.SECONDS.toMicros(1) + TimeUnit.MILLISECONDS.toMicros(1)
    val timelineRange = TimeUnit.SECONDS.toMicros(5)
    myMonitor.timeline.dataRange.set(0.0, timelineRange.toDouble())
    myMonitor.timeline.tooltipRange.set(tooltipTime.toDouble(), tooltipTime.toDouble())
  }

  @Test
  fun tooltipActivityAndFragmentLabelsHaveExpectedText() {
    val activityEndTimeNs = TimeUnit.SECONDS.toNanos(4)
    val fragmentEndTimeNs = TimeUnit.SECONDS.toNanos(5)

    myEventService.addActivityEvent(
      buildActivityEvent(ACTIVITY_NAME, arrayOf(ActivityStateData(Interaction.ViewData.State.CREATED,
                                                                  TEST_START_TIME_NS),
                                                ActivityStateData(Interaction.ViewData.State.RESUMED,
                                                                  TEST_START_TIME_NS),
                                                ActivityStateData(Interaction.ViewData.State.PAUSED,
                                                                  activityEndTimeNs)),
                         0))
    for (fragmentName in FRAGMENT_NAMES) {
      myEventService.addActivityEvent(
        buildActivityEvent(fragmentName,
                           arrayOf(ActivityStateData(Interaction.ViewData.State.ADDED, TEST_START_TIME_NS),
                                   ActivityStateData(Interaction.ViewData.State.REMOVED, fragmentEndTimeNs)),
                           fragmentName.hashCode().toLong()))
    }
    myTimer.tick(TimeUnit.SECONDS.toNanos(2))
    // Check text when hovering over event line for adding fragment.
    assertThat(myActivityTooltipView.headingText).isEqualTo("00:01.001")
    assertThat(myActivityTooltipView.activityNameText).isEqualTo(ACTIVITY_NAME)

    fragmentPanelContainsExpectedText(myActivityTooltipView.fragmentsPanel, arrayOf("TestFragment1 - resumed", "TestFragment2 - resumed"))
    assertThat(myActivityTooltipView.durationText).isEqualTo("00:01.000 - 00:04.000")

    // Check text when hovering over activity bar.
    var tooltipTime = TimeUnit.SECONDS.toMicros(2).toDouble()
    myMonitor.timeline.tooltipRange.set(tooltipTime, tooltipTime)
    assertThat(myActivityTooltipView.headingText).isEqualTo("00:02.000")
    assertThat(myActivityTooltipView.activityNameText).isEqualTo(ACTIVITY_NAME)
    fragmentPanelContainsExpectedText(myActivityTooltipView.fragmentsPanel, arrayOf("TestFragment1", "TestFragment2"))
    assertThat(myActivityTooltipView.durationText).isEqualTo("00:01.000 - 00:04.000")

    // Check text when hovering over event line for removing fragment.
    // All activity related label should be empty since the activity has already been paused.
    tooltipTime = TimeUnit.SECONDS.toMicros(5).toDouble()
    myMonitor.timeline.tooltipRange.set(tooltipTime, tooltipTime)
    assertThat(myActivityTooltipView.headingText).isEqualTo("00:05.000")
    assertThat(myActivityTooltipView.activityNameText).isEqualTo("")
    fragmentPanelContainsExpectedText(myActivityTooltipView.fragmentsPanel, arrayOf("TestFragment1 - paused", "TestFragment2 - paused"))
    assertThat(myActivityTooltipView.durationText).isEqualTo("")
  }

  private fun fragmentPanelContainsExpectedText(fragmentPanel: JPanel, expectedTextArray: Array<String>) {
    assertThat(fragmentPanel.componentCount).isEqualTo(expectedTextArray.size)
    for (i in expectedTextArray.indices) {
      assertThat((fragmentPanel.getComponent(i) as JLabel).text).isEqualTo(expectedTextArray[i])
    }
  }

  @Test
  fun tooltipRangeChangeShouldBeHandled() {
    myEventService.addActivityEvent(
      buildActivityEvent(ACTIVITY_NAME, arrayOf(
        ActivityStateData(Interaction.ViewData.State.ADDED, TimeUnit.SECONDS.toNanos(1)),
        ActivityStateData(Interaction.ViewData.State.PAUSED, TimeUnit.SECONDS.toNanos(5))
      ), 0))

    myEventService.addActivityEvent(
      buildActivityEvent(OTHER_ACTIVITY_NAME, arrayOf(
        ActivityStateData(Interaction.ViewData.State.ADDED, TimeUnit.SECONDS.toNanos(6)),
        ActivityStateData(Interaction.ViewData.State.PAUSED, TimeUnit.SECONDS.toNanos(7))
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
                         arrayOf(ActivityStateData(Interaction.ViewData.State.CREATED,
                                                   TEST_START_TIME_NS),
                                 ActivityStateData(Interaction.ViewData.State.RESUMED,
                                                   TEST_START_TIME_NS),
                                 ActivityStateData(Interaction.ViewData.State.PAUSED,
                                                   TEST_START_TIME_NS + TimeUnit.SECONDS.toNanos(1)),
                                 ActivityStateData(Interaction.ViewData.State.DESTROYED,
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
    builder.setName(name).setHash(name.hashCode().toLong()).setActivityContextHash(contextHash)
    for (state in states) {
      builder.addStateChanges(EventProfiler.ActivityStateData.newBuilder()
                                .setState(state.activityState)
                                .setTimestamp(state.activityStateTime)
                                .build())
    }
    return builder.build()
  }

  private class FakeLifecycleTooltipView(parent: StageView<*>, tooltip: LifecycleTooltip) :
    LifecycleTooltipView(parent.component, tooltip) {

    init {
      createComponent()
    }

    val activityNameText: String
      get() = activityNameLabel.text

    val durationText: String
      get() = durationLabel.text
  }

  private class ActivityStateData constructor(var activityState: Interaction.ViewData.State, var activityStateTime: Long)

  companion object {
    private val TEST_START_TIME_NS = TimeUnit.SECONDS.toNanos(1)
    private val ACTIVITY_NAME = "TestActivity"
    private val FRAGMENT_NAMES = arrayOf("TestFragment1", "TestFragment2")
    private val OTHER_ACTIVITY_NAME = "OtherTestActivity"
  }
}
