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
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.profilers.FakeIdeProfilerComponents
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.FakeProfilerService
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.idea.transport.faketransport.FakeTransportService.FAKE_DEVICE_NAME
import com.android.tools.idea.transport.faketransport.FakeTransportService.FAKE_PROCESS_NAME
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.StudioProfilersView
import com.android.tools.profilers.cpu.CpuProfilerStage
import com.android.tools.profilers.cpu.CpuProfilerStageView
import com.android.tools.profilers.cpu.CpuProfilerUITestUtils
import com.android.tools.profilers.cpu.FakeCpuService
import com.android.tools.profilers.event.FakeEventService
import com.android.tools.profilers.memory.FakeMemoryService
import com.android.tools.profilers.network.FakeNetworkService
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.swing.JTree

class TopDownDetailsViewTest {

  private val cpuService = FakeCpuService()
  private val timer = FakeTimer()

  @JvmField
  @Rule
  val grpcChannel = FakeGrpcChannel("TopDownDetailsViewTest", cpuService,
                                    FakeTransportService(timer), FakeProfilerService(timer),
                                    FakeMemoryService(), FakeEventService(), FakeNetworkService.newBuilder().build())

  private lateinit var stageView: CpuProfilerStageView
  private lateinit var stage: CpuProfilerStage

  @Before
  fun setUp() {
    val profilers = StudioProfilers(ProfilerClient(grpcChannel.name), FakeIdeProfilerServices(), timer)
    timer.tick(FakeTimer.ONE_SECOND_IN_NS)
    profilers.setPreferredProcess(FAKE_DEVICE_NAME, FAKE_PROCESS_NAME, null)

    stage = CpuProfilerStage(profilers)
    stage.studioProfilers.stage = stage
    stage.capture = CpuProfilerUITestUtils.validCapture()
    stage.enter()

    val profilersView = StudioProfilersView(profilers, FakeIdeProfilerComponents())
    stageView = CpuProfilerStageView(profilersView, stage)
  }


  @Test
  fun showsNoDataForThreadMessageWhenNodeIsNull() {
    stage.setCaptureDetails(CaptureDetails.Type.TOP_DOWN)
    stage.selectedThread = 1

    val topDown = stage.captureDetails as CaptureDetails.TopDown
    assertThat(topDown.model).isNull()

    val topDownView = TreeDetailsView.TopDownDetailsView(stageView, topDown)

    val noDataInstructions = TreeWalker(topDownView.component).descendants().filterIsInstance<InstructionsPanel>().first {
      val textInstruction = it.getRenderInstructionsForComponent(0)[0] as TextInstruction
      textInstruction.text == CaptureDetailsView.NO_DATA_FOR_THREAD_MESSAGE
    }
    assertThat(noDataInstructions.isVisible).isTrue()
  }

  @Test
  fun showsContentWhenNodeIsNotNull() {
    stage.apply {
      val capture = CpuProfilerUITestUtils.validCapture()
      setAndSelectCapture(capture)
      selectedThread = capture.mainThreadId
      setCaptureDetails(CaptureDetails.Type.TOP_DOWN)
    }

    val topDown = stage.captureDetails as CaptureDetails.TopDown
    val topDownView = TreeDetailsView.TopDownDetailsView(stageView, topDown)

    val noDataInstructionsList = TreeWalker(topDownView.component).descendants().filterIsInstance<InstructionsPanel>().filter {
      val textInstruction = it.getRenderInstructionsForComponent(0)[0] as TextInstruction
      textInstruction.text == CaptureDetailsView.NO_DATA_FOR_THREAD_MESSAGE
    }
    assertThat(noDataInstructionsList).isEmpty()

    val tree = TreeWalker(topDownView.component).descendants().filterIsInstance<JTree>().first()
    assertThat(tree.isVisible).isTrue()
  }

  @Test
  fun showsNoDataForRangeMessage() {
    stage.apply {
      val capture = CpuProfilerUITestUtils.validCapture()
      setAndSelectCapture(capture)
      selectedThread = capture.mainThreadId
      setCaptureDetails(CaptureDetails.Type.TOP_DOWN)
    }
    // Select a range where we don't have trace data
    stage.studioProfilers.timeline.selectionRange.set(Double.MAX_VALUE - 10, Double.MAX_VALUE - 5)

    val topDown = stage.captureDetails as CaptureDetails.TopDown
    val topDownView = TreeDetailsView.TopDownDetailsView(stageView, topDown)

    val noDataInstructions = TreeWalker(topDownView.component).descendants().filterIsInstance<InstructionsPanel>().first {
      val textInstruction = it.getRenderInstructionsForComponent(0)[0] as TextInstruction
      textInstruction.text == CaptureDetailsView.NO_DATA_FOR_RANGE_MESSAGE
    }
    assertThat(noDataInstructions.isVisible).isTrue()
  }
}