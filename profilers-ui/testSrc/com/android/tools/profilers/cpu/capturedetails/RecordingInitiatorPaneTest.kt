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

import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.instructions.InstructionsPanel
import com.android.tools.adtui.instructions.TextInstruction
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.adtui.stdui.CommonTabbedPane
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profiler.proto.Cpu
import com.android.tools.profilers.FakeIdeProfilerComponents
import com.android.tools.profilers.FakeProfilerService
import com.android.tools.profilers.StudioProfilersView
import com.android.tools.profilers.cpu.CpuProfilerStageView
import com.android.tools.profilers.cpu.CpuProfilerToolbar
import com.android.tools.profilers.cpu.FakeCpuProfiler
import com.android.tools.profilers.cpu.FakeCpuService
import com.android.tools.profilers.cpu.ProfilingConfiguration
import com.android.tools.profilers.cpu.ProfilingTechnology
import com.android.tools.profilers.event.FakeEventService
import com.android.tools.profilers.memory.FakeMemoryService
import com.android.tools.profilers.network.FakeNetworkService
import com.google.common.truth.Truth.assertThat
import com.intellij.ui.HyperlinkLabel
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.swing.JButton

class RecordingInitiatorPaneTest {
  @JvmField
  @Rule
  val grpcChannel: FakeGrpcChannel

  @JvmField
  @Rule
  val cpuProfiler: FakeCpuProfiler

  private val timer = FakeTimer()

  init {
    val cpuService = FakeCpuService()
    val transportService = FakeTransportService(timer)
    grpcChannel = FakeGrpcChannel("CpuCaptureViewTestChannel", cpuService,
                                  transportService, FakeProfilerService(timer),
                                  FakeMemoryService(), FakeEventService(), FakeNetworkService.newBuilder().build())

    cpuProfiler = FakeCpuProfiler(grpcChannel = grpcChannel, transportService = transportService, cpuService = cpuService, timer = timer)
  }

  private lateinit var stageView: CpuProfilerStageView

  @Before
  fun setUp() {
    val profilersView = StudioProfilersView(cpuProfiler.stage.studioProfilers, FakeIdeProfilerComponents())
    stageView = CpuProfilerStageView(profilersView, cpuProfiler.stage)
  }

  @Test
  fun interactionIsDisabled() {
    val pane = RecordingInitiatorPane(stageView)

    val toolbar = TreeWalker(pane).descendants().filterIsInstance<CapturePane.Toolbar>().first()
    val tab = TreeWalker(pane).descendants().filterIsInstance<CommonTabbedPane>().first()

    assertThat(toolbar.isEnabled).isFalse()
    assertThat(tab.isEnabled).isFalse()
  }

  @Test
  fun recordButtonIsPresentWhenNewRecordingWorkflowFlagIsEnabled() {
    cpuProfiler.ideServices.enableCpuNewRecordingWorkflow(true)
    val pane = RecordingInitiatorPane(stageView)
    val isPresent = TreeWalker(pane).descendants().filterIsInstance<JButton>().any { it.text == CpuProfilerToolbar.RECORD_TEXT }
    assertThat(isPresent).isTrue()
  }

  @Test
  fun showsHelpTipInstructionsWhenNewRecordingWorkflowFlagIsDisabled() {
    cpuProfiler.ideServices.enableCpuNewRecordingWorkflow(false)

    val titleInstruction = TreeWalker(RecordingInitiatorPane(stageView)).descendants()
      .filterIsInstance<InstructionsPanel>()
      .take(1)
      .flatMap { it.getRenderInstructionsForComponent(0) }
      .filterIsInstance<TextInstruction>()
      .first()

    assertThat(titleInstruction.text).isEqualTo(RecordingInitiatorPane.HELP_TIP_TITLE)
  }

  @Test
  fun testSampledJavaTechnologyDescription() {
    cpuProfiler.ideServices.enableCpuNewRecordingWorkflow(true)

    stageView.stage.profilerConfigModel.profilingConfiguration = ProfilingConfiguration("Sampled Java",
                                                                                        Cpu.CpuTraceType.ART,
                                                                                        Cpu.CpuTraceMode.SAMPLED)
    TreeWalker(RecordingInitiatorPane(stageView))
      .descendants()
      .filterIsInstance<HyperlinkLabel>()
      .any { it.text == ProfilingTechnology.ART_SAMPLED.description }
      .let { assertThat(it).isTrue() }
  }

  @Test
  fun testInstrumentedJavaTechnologyDescription() {
    cpuProfiler.ideServices.enableCpuNewRecordingWorkflow(true)

    stageView.stage.profilerConfigModel.profilingConfiguration = ProfilingConfiguration("Instrumented Java",
                                                                                        Cpu.CpuTraceType.ART,
                                                                                        Cpu.CpuTraceMode.INSTRUMENTED)
    TreeWalker(RecordingInitiatorPane(stageView))
      .descendants()
      .filterIsInstance<HyperlinkLabel>()
      .any { it.text == ProfilingTechnology.ART_INSTRUMENTED.description }
      .let { assertThat(it).isTrue() }
  }

  @Test
  fun testSampledNativeTechnologyDescription() {
    cpuProfiler.ideServices.enableCpuNewRecordingWorkflow(true)

    stageView.stage.profilerConfigModel.profilingConfiguration = ProfilingConfiguration("Sampled Native",
                                                                                        Cpu.CpuTraceType.SIMPLEPERF,
                                                                                        Cpu.CpuTraceMode.SAMPLED)
    TreeWalker(RecordingInitiatorPane(stageView))
      .descendants()
      .filterIsInstance<HyperlinkLabel>()
      .any { it.text == ProfilingTechnology.SIMPLEPERF.description }
      .let { assertThat(it).isTrue() }
  }

  @Test
  fun testATraceTechnologyDescription() {
    cpuProfiler.ideServices.enableCpuNewRecordingWorkflow(true)

    stageView.stage.profilerConfigModel.profilingConfiguration = ProfilingConfiguration("ATrace",
                                                                                        Cpu.CpuTraceType.ATRACE,
                                                                                        Cpu.CpuTraceMode.SAMPLED)
    TreeWalker(RecordingInitiatorPane(stageView))
      .descendants()
      .filterIsInstance<HyperlinkLabel>()
      .any { it.text == ProfilingTechnology.ATRACE.description }
      .let { assertThat(it).isTrue() }
  }

  @Test
  fun learnMoreHyperlinkIsPresent() {
    cpuProfiler.ideServices.enableCpuNewRecordingWorkflow(true)

    TreeWalker(RecordingInitiatorPane(stageView))
      .descendants()
      .filterIsInstance<HyperlinkLabel>()
      .any { it.text == RecordingInitiatorPane.LEARN_MORE_MESSAGE }
      .let { assertThat(it).isTrue() }
  }
}
