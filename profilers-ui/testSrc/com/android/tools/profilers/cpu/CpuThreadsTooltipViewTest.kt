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
package com.android.tools.profilers.cpu

import com.android.tools.adtui.model.FakeTimer
import com.android.tools.profilers.*
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

class CpuThreadsTooltipViewTest {
  private var timer: FakeTimer = FakeTimer()
  private var cpuService = FakeCpuService()
  private lateinit var cpuStage: CpuProfilerStage
  private lateinit var cpuThreadsTooltip: CpuThreadsTooltip
  private lateinit var cpuThreadsTooltipView: FakeCpuThreadsTooltipView
  @get:Rule
  val myGrpcChannel = FakeGrpcChannel("CpuThreadsTooltipViewTest", cpuService)

  @Before
  fun setUp() {
    val profilers = StudioProfilers(myGrpcChannel.client, FakeIdeProfilerServices(), timer)
    cpuStage = CpuProfilerStage(profilers)
    timer.tick(TimeUnit.SECONDS.toNanos(1))
    profilers.stage = cpuStage
    val view = StudioProfilersView(profilers, FakeIdeProfilerComponents())
    val stageView: CpuProfilerStageView = view.stageView as CpuProfilerStageView
    cpuThreadsTooltip = CpuThreadsTooltip(cpuStage)
    cpuThreadsTooltipView = FakeCpuThreadsTooltipView(stageView, cpuThreadsTooltip)
    cpuStage.tooltip = cpuThreadsTooltip
    val tooltipTime = TimeUnit.SECONDS.toMicros(1)
    stageView.timeline.dataRange.set(0.0, TimeUnit.SECONDS.toMicros(5).toDouble())
    stageView.timeline.tooltipRange.set(tooltipTime.toDouble(), tooltipTime.toDouble())
    stageView.timeline.viewRange.set(0.0, TimeUnit.SECONDS.toMicros(10).toDouble())
  }

  @Test
  fun textUpdateOnThreadChange() {
    val threadSeries = ThreadStateDataSeries(cpuStage, ProfilersTestData.SESSION_DATA, 1)

    cpuThreadsTooltip.setThread("myThread", threadSeries)
    assertThat(cpuThreadsTooltipView.headingLabel).contains("myThread")
    assertThat(cpuThreadsTooltipView.content).isEqualTo("Running")

    cpuThreadsTooltip.setThread("newThread", threadSeries)
    assertThat(cpuThreadsTooltipView.headingLabel).contains("newThread")
    assertThat(cpuThreadsTooltipView.content).isEqualTo("Running")
  }

  private class FakeCpuThreadsTooltipView(
      parent: CpuProfilerStageView,
      tooltip: CpuThreadsTooltip)
    : CpuThreadsTooltipView(parent, tooltip) {
    init {
      createComponent()
    }

    val headingLabel: String
      get() = myHeadingLabel.text

    val content: String
      get() = myContent.text
  }
}
