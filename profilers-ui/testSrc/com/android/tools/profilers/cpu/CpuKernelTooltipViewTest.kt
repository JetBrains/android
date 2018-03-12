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
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.adtui.model.Range
import com.android.tools.adtui.model.SeriesData
import com.android.tools.profiler.proto.Common
import com.android.tools.profilers.*
import com.android.tools.profilers.cpu.atrace.AtraceParser
import com.android.tools.profilers.cpu.atrace.CpuKernelTooltip
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit
import javax.swing.JLabel

// Path to trace file. Used in test to build AtraceParser.
private const val TOOLTIP_TRACE_DATA_FILE = "tools/adt/idea/profilers-ui/testData/cputraces/atrace_processid_1.ctrace"

class CpuKernelTooltipViewTest {
  private lateinit var myStage: CpuProfilerStage
  private lateinit var myCpuKernelTooltip: CpuKernelTooltip
  private lateinit var myCpuKernelTooltipView: FakeCpuKernelTooltipView
  private lateinit var myRange: Range
  private val myFakeProfilerService = FakeProfilerService()
  @get:Rule
  val myGrpcChannel = FakeGrpcChannel("CpuKernelTooltipViewTest", FakeCpuService(), myFakeProfilerService)

  @Before
  fun setUp() {
    val device = Common.Device.newBuilder().setDeviceId(1).build()
    myFakeProfilerService.addDevice(device)
    myFakeProfilerService.addProcess(device, Common.Process.newBuilder().setDeviceId(1).setPid(1).build())
    val timer = FakeTimer()
    val profilers = StudioProfilers(myGrpcChannel.client, FakeIdeProfilerServices(), timer)
    myStage = CpuProfilerStage(profilers)
    val capture = AtraceParser(1).parse(TestUtils.getWorkspaceFile(TOOLTIP_TRACE_DATA_FILE), 0)
    myStage.capture = capture
    timer.tick(TimeUnit.SECONDS.toNanos(1))
    profilers.stage = myStage
    val view = StudioProfilersView(profilers, FakeIdeProfilerComponents())
    val stageView: CpuProfilerStageView = view.stageView as CpuProfilerStageView
    myCpuKernelTooltip = CpuKernelTooltip(myStage)
    myCpuKernelTooltipView = FakeCpuKernelTooltipView(stageView, myCpuKernelTooltip)
    myStage.tooltip = myCpuKernelTooltip
    stageView.timeline.dataRange.set(0.0, TimeUnit.SECONDS.toMicros(5).toDouble())
    myRange = stageView.timeline.tooltipRange
    stageView.timeline.viewRange.set(0.0, TimeUnit.SECONDS.toMicros(10).toDouble())
  }

  @Test
  fun textUpdateOnRangeChange() {
    val testSeriesData = ArrayList<SeriesData<CpuThreadInfo>>()
    testSeriesData.add(SeriesData(0, CpuThreadInfo(0, "Cpu", 0, "Thread")))
    testSeriesData.add(SeriesData(5, CpuThreadInfo.NULL_THREAD))
    val series = AtraceDataSeries<CpuThreadInfo>(myStage, { _ -> testSeriesData })
    myRange.set(1.0, 1.0)
    myCpuKernelTooltip.setCpuSeries(series)
    assertThat(myCpuKernelTooltipView.headingLabel).contains("Thread")
    assertThat(myCpuKernelTooltipView.text).isEqualTo("Cpu")
  }

  private class FakeCpuKernelTooltipView(
    view: CpuProfilerStageView,
    tooltip: CpuKernelTooltip
  ) : CpuKernelTooltipView(view, tooltip) {
    val myTooltipContent = createTooltip() as JLabel
    val headingLabel: String
      get() = myHeadingLabel.text

    val text: String
      get() = myTooltipContent.text
  }
}
