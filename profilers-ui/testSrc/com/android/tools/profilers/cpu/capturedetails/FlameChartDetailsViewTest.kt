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
import com.android.tools.profilers.FakeIdeProfilerComponents
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.SessionProfilersView
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.StudioProfilersView
import com.android.tools.profilers.Utils
import com.android.tools.profilers.cpu.CaptureNode
import com.android.tools.profilers.cpu.CpuCaptureParser
import com.android.tools.profilers.cpu.CpuProfilerTestUtils
import com.android.tools.profilers.cpu.CpuProfilerUITestUtils
import com.android.tools.profilers.cpu.config.ProfilingConfiguration.TraceType
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class FlameChartDetailsViewTest {
  private val timer = FakeTimer()

  @JvmField
  @Rule
  val grpcChannel = FakeGrpcChannel("FlameChartDetailsViewTest", FakeTransportService(timer))

  @get:Rule
  val applicationRule = ApplicationRule()

  @get:Rule
  val disposableRule = DisposableRule()

  private lateinit var profilersView: StudioProfilersView
  private val capture = CpuProfilerUITestUtils.validCapture()

  @Before
  fun setUp() {
    val profilers = StudioProfilers(ProfilerClient(grpcChannel.channel), FakeIdeProfilerServices(), timer)
    profilersView = SessionProfilersView(profilers, FakeIdeProfilerComponents(), disposableRule.disposable)
  }

  @Test
  fun showsNoDataForThreadMessageWhenNodeIsEmpty() {
    val flameChart = CaptureDetails.Type.FLAME_CHART.build(ClockType.GLOBAL, Range(), emptyList(), capture, Utils::runOnUi)
      as CaptureDetails.FlameChart
    val flameChartView = ChartDetailsView.FlameChartDetailsView(profilersView, flameChart)

    val noDataInstructions = TreeWalker(flameChartView.component).descendants().filterIsInstance<InstructionsPanel>().first {
      val textInstruction = it.getRenderInstructionsForComponent(0)[0] as TextInstruction

      textInstruction.text == CaptureDetailsView.NO_DATA_FOR_THREAD_MESSAGE
    }
    assertThat(noDataInstructions.isVisible).isTrue()
  }

  @Test
  fun flameChartHasCpuTraceEventTooltipView() {
    val parser = CpuCaptureParser(FakeIdeProfilerServices())

    val traceFile = resolveWorkspacePath(CpuProfilerUITestUtils.ATRACE_PID1_PATH).toFile()
    val atraceCapture = parser.parse(traceFile, CpuProfilerTestUtils.FAKE_TRACE_ID, TraceType.ATRACE, 1, null).get()

    val flameChart = CaptureDetails.Type.FLAME_CHART.build(ClockType.GLOBAL, Range(Double.MIN_VALUE, Double.MAX_VALUE),
                                                           listOf(atraceCapture.getCaptureNode(atraceCapture.mainThreadId)!!),
                                                           atraceCapture, Utils::runOnUi) as CaptureDetails.FlameChart
    val flameChartView = ChartDetailsView.FlameChartDetailsView(profilersView, flameChart)
    val treeChart = TreeWalker(flameChartView.component).descendants().filterIsInstance<HTreeChart<CaptureNode>>().first()
    assertThat(treeChart.mouseMotionListeners[2]).isInstanceOf(CpuTraceEventTooltipView::class.java)
  }

  @Test
  fun showsContentWhenNodeIsNotNull() {
    val flameChart = CaptureDetails.Type.FLAME_CHART.build(ClockType.GLOBAL, Range(Double.MIN_VALUE, Double.MAX_VALUE),
                                                           listOf(capture.getCaptureNode(capture.mainThreadId)!!),
                                                           capture, Utils::runOnUi) as CaptureDetails.FlameChart
    val flameChartView = ChartDetailsView.FlameChartDetailsView(profilersView, flameChart)

    val noDataInstructionsList = TreeWalker(flameChartView.component).descendants().filterIsInstance<InstructionsPanel>().filter {
      val textInstruction = it.getRenderInstructionsForComponent(0)[0] as TextInstruction

      textInstruction.text == CaptureDetailsView.NO_DATA_FOR_THREAD_MESSAGE
    }
    assertThat(noDataInstructionsList).isEmpty()

    val chart = TreeWalker(flameChartView.component).descendants().filterIsInstance<HTreeChart<CaptureNode>>().first()
    assertThat(chart.isVisible).isTrue()
  }
}