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

import com.android.testutils.TestUtils
import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.adtui.model.SeriesData
import com.android.tools.profiler.proto.Common
import com.android.tools.profilers.*
import com.android.tools.profilers.cpu.atrace.AtraceFrame
import com.android.tools.profilers.cpu.atrace.AtraceParser
import com.android.tools.profilers.cpu.atrace.CpuFrameTooltip
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit
import javax.swing.JLabel

class CpuFrameTooltipViewTest {

  val ATRACE_TRACE_PATH = "tools/adt/idea/profilers-ui/testData/cputraces/atrace.ctrace"
  private lateinit var stage: CpuProfilerStage
  private lateinit var tooltip: CpuFrameTooltip
  private lateinit var tooltipView: FakeCpuFrameTooltipView
  private val fakeProfilerService = FakeProfilerService()
  @get:Rule
  val grpcChannel = FakeGrpcChannel("CpuFrameTooltipViewTest", FakeCpuService(), fakeProfilerService)

  @Before
  fun setUp() {
    val device = Common.Device.newBuilder().setDeviceId(1).build()
    fakeProfilerService.addDevice(device)
    fakeProfilerService.addProcess(device, Common.Process.newBuilder().setDeviceId(1).setPid(1).build())
    val timer = FakeTimer()
    val profilers = StudioProfilers(grpcChannel.client, FakeIdeProfilerServices(), timer)
    stage = CpuProfilerStage(profilers)
    val capture = AtraceParser(1).parse(TestUtils.getWorkspaceFile(ATRACE_TRACE_PATH), 0)
    stage.capture = capture
    timer.tick(TimeUnit.SECONDS.toNanos(1))
    profilers.stage = stage
    val view = StudioProfilersView(profilers, FakeIdeProfilerComponents())
    val stageView = view.stageView as CpuProfilerStageView
    tooltip = CpuFrameTooltip(stage)
    tooltipView = FakeCpuFrameTooltipView(stageView, tooltip)
    stage.tooltip = tooltip
    stageView.timeline.apply {
      dataRange.set(0.0, TimeUnit.SECONDS.toMicros(5).toDouble())
      tooltipRange.set(1.0, 1.0)
      viewRange.set(0.0, TimeUnit.SECONDS.toMicros(10).toDouble())
    }
  }

  @Test
  fun textUpdateOnRangeChange() {
    val frames = mutableListOf(SeriesData(0, AtraceFrame(0, { _ -> 1L }, 0)),
                               SeriesData(2, AtraceFrame(0, { _ -> 1L }, 0)))
    val series = AtraceDataSeries<AtraceFrame>(stage) { _ -> frames }
    tooltip.setFrameSeries(series)
    val labels = TreeWalker(tooltipView.tooltipPanel).descendants().filterIsInstance<JLabel>()
    assertThat(labels).hasSize(3)
    assertThat(labels[0].text).isEqualTo("00:00.000")
    assertThat(labels[1].text).contains("CPU Time:")
    assertThat(labels[2].text).contains("Total Time:")
  }

  private class FakeCpuFrameTooltipView(
    view: CpuProfilerStageView,
    tooltip: CpuFrameTooltip
  ) : CpuFrameTooltipView(view, tooltip) {
    val tooltipPanel = createComponent()
  }
}
