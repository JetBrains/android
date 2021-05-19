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
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profiler.proto.Common
import com.android.tools.profilers.FakeIdeProfilerComponents
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.FakeProfilerService
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.StudioProfilersView
import com.android.tools.profilers.cpu.systemtrace.AtraceParser
import com.android.tools.profilers.cpu.systemtrace.CpuFrameTooltip
import com.android.tools.profilers.cpu.systemtrace.SystemTraceFrame
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit
import javax.swing.JLabel
import javax.swing.JPanel

class CpuFrameTooltipViewTest {

  val ATRACE_TRACE_PATH = "tools/adt/idea/profilers-ui/testData/cputraces/atrace.ctrace"
  private val timer = FakeTimer()
  private lateinit var stage: CpuProfilerStage
  private lateinit var tooltip: CpuFrameTooltip
  private lateinit var tooltipView: FakeCpuFrameTooltipView
  private lateinit var capture: CpuCapture
  private val fakeTransportService = FakeTransportService(timer)
  @get:Rule
  val grpcChannel = FakeGrpcChannel("CpuFrameTooltipViewTest", FakeCpuService(), fakeTransportService, FakeProfilerService(timer))

  @Before
  fun setUp() {
    val device = Common.Device.newBuilder().setDeviceId(1).build()
    fakeTransportService.addDevice(device)
    fakeTransportService.addProcess(device, Common.Process.newBuilder().setDeviceId(1).setPid(1).build())
    val profilers = StudioProfilers(ProfilerClient(grpcChannel.channel), FakeIdeProfilerServices(), timer)
    stage = CpuProfilerStage(profilers)
    capture = AtraceParser(MainProcessSelector(idHint = 1)).parse(TestUtils.getWorkspaceFile(ATRACE_TRACE_PATH), 0)
    stage.capture = capture
    timer.tick(TimeUnit.SECONDS.toNanos(1))
    profilers.stage = stage
    val view = StudioProfilersView(profilers, FakeIdeProfilerComponents())
    val stageView = view.stageView as CpuProfilerStageView
    tooltip = CpuFrameTooltip(stage.timeline)
    tooltipView = FakeCpuFrameTooltipView(stageView, tooltip)
    stage.tooltip = tooltip
    stageView.stage.timeline.apply {
      dataRange.set(0.0, TimeUnit.SECONDS.toMicros(5).toDouble())
      tooltipRange.set(1.0, 1.0)
      viewRange.set(0.0, TimeUnit.SECONDS.toMicros(10).toDouble())
    }
  }

  @Test
  fun textUpdateOnRangeChange() {
    val mainFrame = SystemTraceFrame(1, 1, 0.0, 0, SystemTraceFrame.FrameThread.MAIN)
    val renderFrame = SystemTraceFrame(1, 1, 0.0, 0, SystemTraceFrame.FrameThread.RENDER)
    mainFrame.associatedFrame = renderFrame
    renderFrame.associatedFrame = mainFrame

    val frames = mutableListOf(SeriesData(0, mainFrame), SeriesData(2, renderFrame))
    val series = LazyDataSeries<SystemTraceFrame> { frames }
    tooltip.setFrameSeries(series)
    val labels = TreeWalker(tooltipView.tooltipPanel).descendants().filterIsInstance<JLabel>()
    assertThat(labels).hasSize(8)
    assertThat(labels[0].text).isEqualTo("00:00.000")
    assertThat(labels[1].text).contains("Total Time:")
    assertThat(labels[2].text).contains("Main Thread")
    assertThat(labels[3].text).contains("CPU Time:")
    assertThat(labels[4].text).contains("Wall Time:")
    assertThat(labels[5].text).contains("RenderThread")
    assertThat(labels[6].text).contains("CPU Time:")
    assertThat(labels[7].text).contains("Wall Time:")

    val panels = TreeWalker(tooltipView.tooltipPanel).descendants().filterIsInstance<JPanel>()
    assertThat(panels).hasSize(4)

    assertThat(panels[1].isVisible).isTrue()
    assertThat(panels[2].isVisible).isTrue()
    assertThat(panels[3].isVisible).isTrue()
  }

  @Test
  fun renderFramePanelAndSeparatorShouldBeHidden() {
    val frames = mutableListOf(
      SeriesData(0, SystemTraceFrame(1L, 1L, 0.0, 0, SystemTraceFrame.FrameThread.MAIN)))
    val series = LazyDataSeries<SystemTraceFrame> { frames }
    tooltip.setFrameSeries(series)
    val panels = TreeWalker(tooltipView.tooltipPanel).descendants().filterIsInstance<JPanel>()
    assertThat(panels).hasSize(4)

    assertThat(panels[1].isVisible).isTrue()
    assertThat(panels[2].isVisible).isTrue()
    assertThat(panels[3].isVisible).isFalse()
  }

  @Test
  fun mainFramePanelAndSeparatorShouldBeHidden() {
    val frames = mutableListOf(
      SeriesData(0, SystemTraceFrame(1L, 1L, 0.0, 0, SystemTraceFrame.FrameThread.RENDER)))
    val series = LazyDataSeries<SystemTraceFrame> { frames }
    tooltip.setFrameSeries(series)
    val panels = TreeWalker(tooltipView.tooltipPanel).descendants().filterIsInstance<JPanel>()
    assertThat(panels).hasSize(4)

    assertThat(panels[1].isVisible).isTrue()
    assertThat(panels[2].isVisible).isFalse()
    assertThat(panels[3].isVisible).isTrue()
  }

  @Test
  fun allPanelsShouldBeHidden() {
    val frames = mutableListOf<SeriesData<SystemTraceFrame>>()
    val series = LazyDataSeries<SystemTraceFrame> { frames }
    tooltip.setFrameSeries(series)
    val panels = TreeWalker(tooltipView.tooltipPanel).descendants().filterIsInstance<JPanel>()
    assertThat(panels).hasSize(4)

    assertThat(panels[1].isVisible).isFalse()
    assertThat(panels[2].isVisible).isFalse()
    assertThat(panels[3].isVisible).isFalse()
  }

  private class FakeCpuFrameTooltipView(
    view: CpuProfilerStageView,
    tooltip: CpuFrameTooltip
  ) : CpuFrameTooltipView(view.component, tooltip) {
    val tooltipPanel = createComponent()
  }
}
