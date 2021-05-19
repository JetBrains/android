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
import com.android.tools.adtui.model.Range
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
import com.android.tools.profilers.cpu.systemtrace.CpuKernelTooltip
import com.android.tools.profilers.cpu.systemtrace.CpuThreadSliceInfo
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit
import javax.swing.JLabel

// Path to trace file. Used in test to build AtraceParser.
private const val TOOLTIP_TRACE_DATA_FILE = "tools/adt/idea/profilers-ui/testData/cputraces/atrace_processid_1.ctrace"

class CpuKernelTooltipViewTest {
  private val timer = FakeTimer()
  private lateinit var myStage: CpuProfilerStage
  private lateinit var myCpuKernelTooltip: CpuKernelTooltip
  private lateinit var myCpuKernelTooltipView: FakeCpuKernelTooltipView
  private lateinit var myCapture: CpuCapture
  private lateinit var myRange: Range
  private val myFakeTransportService = FakeTransportService(timer)
  @get:Rule
  val myGrpcChannel = FakeGrpcChannel("CpuKernelTooltipViewTest", FakeCpuService(), myFakeTransportService, FakeProfilerService(timer))

  @Before
  fun setUp() {
    val device = Common.Device.newBuilder().setDeviceId(1).build()
    myFakeTransportService.addDevice(device)
    myFakeTransportService.addProcess(device, Common.Process.newBuilder().setDeviceId(1).setPid(1).build())
    val profilers = StudioProfilers(ProfilerClient(myGrpcChannel.channel), FakeIdeProfilerServices(), timer)
    myStage = CpuProfilerStage(profilers)
    myCapture = AtraceParser(MainProcessSelector(idHint = 1)).parse(TestUtils.getWorkspaceFile(TOOLTIP_TRACE_DATA_FILE), 0)
    myStage.capture = myCapture
    timer.tick(TimeUnit.SECONDS.toNanos(1))
    profilers.stage = myStage
    val view = StudioProfilersView(profilers, FakeIdeProfilerComponents())
    val stageView: CpuProfilerStageView = view.stageView as CpuProfilerStageView
    myCpuKernelTooltip = CpuKernelTooltip(myStage.timeline, profilers.session.pid)
    myCpuKernelTooltipView = FakeCpuKernelTooltipView(stageView, myCpuKernelTooltip)
    myStage.tooltip = myCpuKernelTooltip
    stageView.stage.timeline.dataRange.set(0.0, TimeUnit.SECONDS.toMicros(5).toDouble())
    myRange = stageView.stage.timeline.tooltipRange
    stageView.stage.timeline.viewRange.set(0.0, TimeUnit.SECONDS.toMicros(10).toDouble())
  }

  @Test
  fun textUpdateOnRangeChange() {
    val testSeriesData = ArrayList<SeriesData<CpuThreadSliceInfo>>()
    testSeriesData.add(SeriesData(0, CpuThreadSliceInfo(0, "SomeThread", 0, "MyProcess", TimeUnit.SECONDS.toMicros(2))))
    testSeriesData.add(SeriesData(5, CpuThreadSliceInfo.NULL_THREAD))
    val series = LazyDataSeries<CpuThreadSliceInfo> { testSeriesData }
    myRange.set(1.0, 1.0)
    myCpuKernelTooltip.setCpuSeries(1, series)
    val labels = TreeWalker(myCpuKernelTooltipView.tooltipPanel).descendants().filterIsInstance<JLabel>()
    assertThat(labels).hasSize(5)
    assertThat(labels[0].text).isEqualTo("00:00.000")
    assertThat(labels[1].text).isEqualTo("Thread: SomeThread")
    assertThat(labels[2].text).isEqualTo("Process: MyProcess")
    assertThat(labels[3].text).isEqualTo("Duration: 2 s")
    assertThat(labels[4].text).isEqualTo("CPU: 1")
  }

  @Test
  fun otherDetailsAppearOnOtherApps() {
    val testSeriesData = ArrayList<SeriesData<CpuThreadSliceInfo>>()
    testSeriesData.add(SeriesData(0, CpuThreadSliceInfo(0, "SomeThread", 22, "MyProcess")))
    val series = LazyDataSeries<CpuThreadSliceInfo> { testSeriesData }
    myRange.set(1.0, 1.0)
    myCpuKernelTooltip.setCpuSeries(1, series)
    val labels = TreeWalker(myCpuKernelTooltipView.tooltipPanel).descendants().filterIsInstance<JLabel>()
    assertThat(labels.stream().anyMatch({ label -> label.text.equals("Other (not selectable)") })).isTrue()
  }

  private class FakeCpuKernelTooltipView(
    view: CpuProfilerStageView,
    tooltip: CpuKernelTooltip
  ) : CpuKernelTooltipView(view.component, tooltip) {
    val tooltipPanel = createComponent()
  }
}
