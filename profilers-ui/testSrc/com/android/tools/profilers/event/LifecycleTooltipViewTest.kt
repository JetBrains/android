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
import com.android.tools.profilers.StageView
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.StudioProfilersView
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit
import javax.swing.JLabel
import javax.swing.JPanel

class LifecycleTooltipViewTest {
  private lateinit var activityTooltipView: FakeLifecycleTooltipView
  private var timer: FakeTimer = FakeTimer()
  private lateinit var monitor: EventMonitor
  private val transportService = FakeTransportService(timer)

  @get:Rule
  val grpcChannel = FakeGrpcChannel("LifecycleTooltipViewTest", transportService)

  @get:Rule
  val applicationRule = ApplicationRule()

  @get:Rule
  val disposableRule = DisposableRule()

  @Before
  fun setup() {
    val profilers = StudioProfilers(ProfilerClient(grpcChannel.channel), FakeIdeProfilerServices(), timer)
    timer.tick(TimeUnit.SECONDS.toNanos(1))
    monitor = EventMonitor(profilers)
    val view = StudioProfilersView(profilers, FakeIdeProfilerComponents(), disposableRule.disposable)
    activityTooltipView = FakeLifecycleTooltipView(view.stageView!!, LifecycleTooltip(monitor.timeline, monitor.lifecycleEvents))
    view.stageView!!.component.setBounds(0, 0, 1024, 256)
    profilers.timeline.viewRange.min = 0.0
    profilers.timeline.viewRange.max = TimeUnit.SECONDS.toMicros(10).toDouble()
    val tooltipTime = TimeUnit.SECONDS.toMicros(1) + TimeUnit.MILLISECONDS.toMicros(1)
    val timelineRange = TimeUnit.SECONDS.toMicros(10)
    monitor.timeline.dataRange.set(0.0, timelineRange.toDouble())
    monitor.timeline.tooltipRange.set(tooltipTime.toDouble(), tooltipTime.toDouble())
  }

  @Test
  fun tooltipActivityAndFragmentLabelsHaveExpectedText() {
    val activityEndTimeNs = TimeUnit.SECONDS.toNanos(4)
    val fragmentEndTimeNs = TimeUnit.SECONDS.toNanos(5)

    buildActivityEvent(ACTIVITY_NAME, arrayOf(ActivityStateData(Interaction.ViewData.State.CREATED,
                                                                TEST_START_TIME_NS),
                                              ActivityStateData(Interaction.ViewData.State.RESUMED,
                                                                TEST_START_TIME_NS),
                                              ActivityStateData(Interaction.ViewData.State.PAUSED,
                                                                activityEndTimeNs)),
                       0)
    for (fragmentName in FRAGMENT_NAMES) {
      buildActivityEvent(fragmentName,
                         arrayOf(ActivityStateData(Interaction.ViewData.State.ADDED, TEST_START_TIME_NS),
                                 ActivityStateData(Interaction.ViewData.State.REMOVED, fragmentEndTimeNs)),
                         fragmentName.hashCode().toLong())
    }
    timer.tick(TimeUnit.SECONDS.toNanos(2))
    // Check text when hovering over event line for adding fragment.
    assertThat(activityTooltipView.headingText).isEqualTo("00:01.001")
    assertThat(activityTooltipView.activityNameText).isEqualTo(ACTIVITY_NAME)

    fragmentPanelContainsExpectedText(activityTooltipView.fragmentsPanel, arrayOf("TestFragment1 - resumed", "TestFragment2 - resumed"))
    assertThat(activityTooltipView.durationText).isEqualTo("00:01.000 - 00:04.000")

    // Check text when hovering over activity bar.
    var tooltipTime = TimeUnit.SECONDS.toMicros(2).toDouble()
    monitor.timeline.tooltipRange.set(tooltipTime, tooltipTime)
    assertThat(activityTooltipView.headingText).isEqualTo("00:02.000")
    assertThat(activityTooltipView.activityNameText).isEqualTo(ACTIVITY_NAME)
    fragmentPanelContainsExpectedText(activityTooltipView.fragmentsPanel, arrayOf("TestFragment1", "TestFragment2"))
    assertThat(activityTooltipView.durationText).isEqualTo("00:01.000 - 00:04.000")

    // Check text when hovering over event line for removing fragment.
    // All activity related label should be empty since the activity has already been paused.
    tooltipTime = TimeUnit.SECONDS.toMicros(5).toDouble()
    monitor.timeline.tooltipRange.set(tooltipTime, tooltipTime)
    assertThat(activityTooltipView.headingText).isEqualTo("00:05.000")
    assertThat(activityTooltipView.activityNameText).isEqualTo("")
    fragmentPanelContainsExpectedText(activityTooltipView.fragmentsPanel, arrayOf("TestFragment1 - paused", "TestFragment2 - paused"))
    assertThat(activityTooltipView.durationText).isEqualTo("")
  }

  private fun fragmentPanelContainsExpectedText(fragmentPanel: JPanel, expectedTextArray: Array<String>) {
    assertThat(fragmentPanel.componentCount).isEqualTo(expectedTextArray.size)
    for (i in expectedTextArray.indices) {
      assertThat((fragmentPanel.getComponent(i) as JLabel).text).isEqualTo(expectedTextArray[i])
    }
  }

  @Test
  fun tooltipRangeChangeShouldBeHandled() {
    buildActivityEvent(ACTIVITY_NAME, arrayOf(
      ActivityStateData(Interaction.ViewData.State.ADDED, TimeUnit.SECONDS.toNanos(1)),
      ActivityStateData(Interaction.ViewData.State.PAUSED, TimeUnit.SECONDS.toNanos(2))
    ), 0)

    buildActivityEvent(OTHER_ACTIVITY_NAME, arrayOf(
      ActivityStateData(Interaction.ViewData.State.ADDED, TimeUnit.SECONDS.toNanos(2)),
      ActivityStateData(Interaction.ViewData.State.PAUSED, TimeUnit.SECONDS.toNanos(3))
    ), 0)

    timer.tick(FakeTimer.ONE_SECOND_IN_NS)

    assertThat(activityTooltipView.headingText).matches("00:01.001")
    assertThat(activityTooltipView.activityNameText).matches(ACTIVITY_NAME)
    assertThat(activityTooltipView.durationText).matches("00:01.000 - 00:02.000")

    val tooltipTime = TimeUnit.SECONDS.toMicros(2).toDouble()
    monitor.timeline.tooltipRange.set(tooltipTime, tooltipTime)

    assertThat(activityTooltipView.headingText).matches("00:02.000")
    assertThat(activityTooltipView.activityNameText).matches(OTHER_ACTIVITY_NAME)
    assertThat(activityTooltipView.durationText).matches("00:02.000 - 00:03.000")
  }

  @Test
  fun testGetActivityTitleTextCompleted() {
    buildActivityEvent(ACTIVITY_NAME,
                       arrayOf(ActivityStateData(Interaction.ViewData.State.CREATED,
                                                 TEST_START_TIME_NS),
                               ActivityStateData(Interaction.ViewData.State.RESUMED,
                                                 TEST_START_TIME_NS),
                               ActivityStateData(Interaction.ViewData.State.PAUSED,
                                                 TEST_START_TIME_NS + TimeUnit.SECONDS.toNanos(1)),
                               ActivityStateData(Interaction.ViewData.State.DESTROYED,
                                                 TEST_START_TIME_NS + TimeUnit.SECONDS.toNanos(1))),
                       0)
    timer.tick(TimeUnit.SECONDS.toNanos(2))
    assertThat(activityTooltipView.headingText).matches("00:01.001")
    assertThat(activityTooltipView.activityNameText).matches(String.format("%s - destroyed", ACTIVITY_NAME))
    assertThat(activityTooltipView.durationText).matches("00:01.000 - 00:02.000")
  }

  @Test
  fun testGetTitleTextNone() {
    timer.tick(1)
    val text = activityTooltipView.headingText
    assertEquals("00:01.001", text)
  }

  private fun buildActivityEvent(name: String,
                                 states: Array<ActivityStateData>,
                                 contextHash: Long) {
    for (state in states) {
      transportService.addEventToStream(FakeTransportService.FAKE_DEVICE_ID,
                                        Common.Event.newBuilder()
                                          .setKind(Common.Event.Kind.VIEW)
                                          .setTimestamp(state.activityStateTime)
                                          .setGroupId(name.hashCode().toLong())
                                          .setIsEnded(state.isEndState())
                                          .setView(
                                            Interaction.ViewData.newBuilder()
                                              .setName(name)
                                              .setState(state.activityState)
                                              .setParentActivityId(contextHash))
                                          .build())
    }
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

  private data class ActivityStateData(var activityState: Interaction.ViewData.State, var activityStateTime: Long) {
    fun isEndState(): Boolean {
      return when (activityState) {
        Interaction.ViewData.State.PAUSED, Interaction.ViewData.State.STOPPED, Interaction.ViewData.State.DESTROYED -> true
        Interaction.ViewData.State.SAVED, Interaction.ViewData.State.REMOVED -> true
        else -> false
      }
    }
  }

  companion object {
    private val TEST_START_TIME_NS = TimeUnit.SECONDS.toNanos(1)
    private const val ACTIVITY_NAME = "TestActivity"
    private val FRAGMENT_NAMES = arrayOf("TestFragment1", "TestFragment2")
    private const val OTHER_ACTIVITY_NAME = "OtherTestActivity"
  }
}
