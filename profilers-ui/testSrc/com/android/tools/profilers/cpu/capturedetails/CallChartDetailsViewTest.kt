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
package com.android.tools.profilers.cpu.capturedetails

import com.android.testutils.TestUtils.resolveWorkspacePath
import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.chart.hchart.HTreeChart
import com.android.tools.adtui.instructions.InstructionsPanel
import com.android.tools.adtui.instructions.TextInstruction
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.adtui.model.Range
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.perflib.vmtrace.ClockType
import com.android.tools.profiler.proto.Cpu
import com.android.tools.profilers.FakeIdeProfilerComponents
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.StudioProfilersView
import com.android.tools.profilers.Utils
import com.android.tools.profilers.cpu.CaptureNode
import com.android.tools.profilers.cpu.CpuCaptureParser
import com.android.tools.profilers.cpu.CpuProfilerUITestUtils
import com.android.tools.profilers.cpu.FakeCpuService
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ApplicationRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class CallChartDetailsViewTest {
  private val timer = FakeTimer()

  @JvmField
  @Rule
  val grpcChannel = FakeGrpcChannel("CallChartDetailsViewTest", FakeTransportService(timer))

  @get:Rule
  val applicationRule = ApplicationRule()

  private lateinit var profilersView: StudioProfilersView
  private val capture = CpuProfilerUITestUtils.validCapture()

  @Before
  fun setUp() {
    val profilers = StudioProfilers(ProfilerClient(grpcChannel.channel), FakeIdeProfilerServices(), timer)
    profilersView = StudioProfilersView(profilers, FakeIdeProfilerComponents())
  }

  @Test
  fun showsContentWhenNodeIsNotNull() {
    val callChart = CaptureDetails.Type.CALL_CHART.build(ClockType.GLOBAL, Range(),
                                                         listOf(capture.getCaptureNode(capture.mainThreadId)!!),
                                                         capture, Utils::runOnUi) as CaptureDetails.CallChart
    val callChartView = ChartDetailsView.CallChartDetailsView(profilersView, callChart)

    val noDataInstructionsList = TreeWalker(callChartView.component).descendants().filterIsInstance<InstructionsPanel>().filter {
      val textInstruction = it.getRenderInstructionsForComponent(0)[0] as TextInstruction

      textInstruction.text == CaptureDetailsView.NO_DATA_FOR_THREAD_MESSAGE
    }
    assertThat(noDataInstructionsList).isEmpty()

    val chart = TreeWalker(callChartView.component).descendants().filterIsInstance<HTreeChart<CaptureNode>>().first()
    assertThat(chart.isVisible).isTrue()
  }

  @Test
  fun callChartHasCpuTraceEventTooltipView() {
    val parser = CpuCaptureParser(FakeIdeProfilerServices())

    val traceFile = resolveWorkspacePath(CpuProfilerUITestUtils.ATRACE_PID1_PATH).toFile()
    val aTraceCapture = parser.parse(traceFile, FakeCpuService.FAKE_TRACE_ID, Cpu.CpuTraceType.ATRACE, 1, null).get()

    val callChart = CaptureDetails.Type.CALL_CHART.build(ClockType.GLOBAL, Range(Double.MIN_VALUE, Double.MAX_VALUE),
                                                         listOf(aTraceCapture.getCaptureNode(aTraceCapture.mainThreadId)!!),
                                                         aTraceCapture, Utils::runOnUi) as CaptureDetails.CallChart
    val callChartView = ChartDetailsView.CallChartDetailsView(profilersView, callChart)
    val treeChart = TreeWalker(callChartView.component).descendants().filterIsInstance<HTreeChart<CaptureNode>>().first()
    assertThat(treeChart.mouseMotionListeners[2]).isInstanceOf(CpuTraceEventTooltipView::class.java)
  }

  @Test
  fun showsNoDataForRangeMessage() {
    // Select a range where we don't have trace data
    val range = Range(Double.MAX_VALUE - 10, Double.MAX_VALUE - 5)
    val callChart = CaptureDetails.Type.CALL_CHART.build(ClockType.GLOBAL, range,
                                                         listOf(capture.getCaptureNode(capture.mainThreadId)!!),
                                                         capture, Utils::runOnUi) as CaptureDetails.CallChart
    val callChartView = ChartDetailsView.CallChartDetailsView(profilersView, callChart)

    val noDataInstructions = TreeWalker(callChartView.component).descendants().filterIsInstance<InstructionsPanel>().first {
      val textInstruction = it.getRenderInstructionsForComponent(0)[0] as TextInstruction

      textInstruction.text == CaptureDetailsView.NO_DATA_FOR_RANGE_MESSAGE
    }
    assertThat(noDataInstructions.isVisible).isTrue()
  }
}