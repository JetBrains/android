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
package com.android.tools.profilers.cpu

import com.android.tools.adtui.model.FakeTimer
import com.android.tools.profilers.*
import com.android.tools.profilers.event.FakeEventService
import com.android.tools.profilers.memory.FakeMemoryService
import com.android.tools.profilers.network.FakeNetworkService
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class CpuCaptureViewTest {
  private val TRACE_PATH = "tools/adt/idea/profilers-ui/testData/valid_trace.trace"

  private val myProfilerService = FakeProfilerService()

  private val myCpuService = FakeCpuService()

  private val myTimer = FakeTimer()

  @get:Rule
  val myGrpcChannel = FakeGrpcChannel("CpuCaptureViewTestChannel", myCpuService, myProfilerService,
      FakeMemoryService(), FakeEventService(), FakeNetworkService.newBuilder().build())

  private lateinit var myStageView: CpuProfilerStageView

  @Before
  fun setUp() {
    val services = FakeIdeProfilerServices()
    val profilers = StudioProfilers(myGrpcChannel.client, services, myTimer)
    // One second must be enough for new devices (and processes) to be picked up
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)

    val myStage = CpuProfilerStage(profilers)
    myStage.studioProfilers.stage = myStage

    val profilersView = StudioProfilersView(profilers, FakeIdeProfilerComponents())
    myStageView = CpuProfilerStageView(profilersView, myStage)
  }

  @Test
  fun whenSelectingCallChartThereShouldBeInstanceOfTreeChartView() {
    val stage = myStageView.stage
    val capture = CpuProfilerTestUtils.getCapture(TRACE_PATH)
    stage.capture = capture
    stage.selectedThread = capture.mainThreadId
    stage.setCaptureDetails(CaptureModel.Details.Type.BOTTOM_UP)

    ReferenceWalker(myStageView).assertNotReachable(CpuCaptureView.CallChartView::class.java)
    stage.setCaptureDetails(CaptureModel.Details.Type.CALL_CHART)
    assertThat(stage.captureDetails?.type).isEqualTo(CaptureModel.Details.Type.CALL_CHART)
    ReferenceWalker(myStageView).assertReachable(CpuCaptureView.CallChartView::class.java)
  }
}