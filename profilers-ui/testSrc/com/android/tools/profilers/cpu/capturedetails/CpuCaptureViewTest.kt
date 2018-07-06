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

import com.android.testutils.TestUtils
import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.adtui.stdui.CommonTabbedPane
import com.android.tools.profiler.proto.CpuProfiler
import com.android.tools.profiler.proto.CpuProfiler.CpuProfilerType.ART
import com.android.tools.profiler.proto.CpuProfiler.CpuProfilerType.ATRACE
import com.android.tools.profilers.*
import com.android.tools.profilers.cpu.CpuProfilerStage
import com.android.tools.profilers.cpu.CpuProfilerStageView
import com.android.tools.profilers.cpu.CpuProfilerTestUtils
import com.android.tools.profilers.cpu.FakeCpuService
import com.android.tools.profilers.event.FakeEventService
import com.android.tools.profilers.memory.FakeMemoryService
import com.android.tools.profilers.network.FakeNetworkService
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

class CpuCaptureViewTest {
  private val TRACE_PATH = "tools/adt/idea/profilers-ui/testData/valid_trace.trace"
  private val ATRACE_TRACE_PATH = "tools/adt/idea/profilers-ui/testData/cputraces/atrace_processid_1.ctrace"

  private val myProfilerService = FakeProfilerService()

  private val myCpuService = FakeCpuService()

  private val myTimer = FakeTimer()

  @get:Rule
  val myGrpcChannel = FakeGrpcChannel("CpuCaptureViewTestChannel", myCpuService, myProfilerService,
                                      FakeMemoryService(), FakeEventService(), FakeNetworkService.newBuilder().build())

  private lateinit var myStageView: CpuProfilerStageView

  private lateinit var myStage: CpuProfilerStage

  @Before
  fun setUp() {
    val services = FakeIdeProfilerServices()
    val profilers = StudioProfilers(myGrpcChannel.client, services, myTimer)
    // Add a trace for Atrace captures. This is required to work around a framework design loop see b/77597839.
    val traceInfo = CpuProfiler.TraceInfo.newBuilder()
      .setTraceId(0)
      .setTraceFilePath(ATRACE_TRACE_PATH)
      .setProfilerType(ATRACE)
      .setFromTimestamp(TimeUnit.MICROSECONDS.toNanos(0))
      .setToTimestamp(TimeUnit.MICROSECONDS.toNanos(800))
    myCpuService.addTraceInfo(traceInfo.build())
    myCpuService.addTraceInfo(traceInfo.setTraceId(1).setTraceFilePath(TRACE_PATH).setProfilerType(ART).build())
    myCpuService.setGetTraceResponseStatus(CpuProfiler.GetTraceResponse.Status.SUCCESS)
    // One second must be enough for new devices (and processes) to be picked up
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    profilers.setPreferredProcess(FakeProfilerService.FAKE_DEVICE_NAME, FakeProfilerService.FAKE_PROCESS_NAME, null)

    myStage = CpuProfilerStage(profilers)
    myStage.studioProfilers.stage = myStage

    val profilersView = StudioProfilersView(profilers, FakeIdeProfilerComponents())
    myStageView = CpuProfilerStageView(profilersView, myStage)
  }

  @Test
  fun whenSelectingCallChartThereShouldBeInstanceOfTreeChartView() {
    val stage = myStageView.stage
    myCpuService.profilerType = ART
    myCpuService.setTrace(
      CpuProfilerTestUtils.traceFileToByteString(TestUtils.getWorkspaceFile(TRACE_PATH)))
    stage.setAndSelectCapture(1)
    stage.selectedThread = stage.capture!!.mainThreadId
    stage.setCaptureDetails(CaptureModel.Details.Type.BOTTOM_UP)

    ReferenceWalker(myStageView).assertNotReachable(ChartDetailsView.CallChartDetailsView::class.java)
    stage.setCaptureDetails(CaptureModel.Details.Type.CALL_CHART)
    assertThat(stage.captureDetails?.type).isEqualTo(CaptureModel.Details.Type.CALL_CHART)
    ReferenceWalker(myStageView).assertReachable(ChartDetailsView.CallChartDetailsView::class.java)
    var tabPane = TreeWalker(myStageView.component).descendants().filterIsInstance(CommonTabbedPane::class.java)[0]
    tabPane.selectedIndex = 0
    assertThat(tabPane.getTitleAt(0)).matches("Call Chart")

    //Change to an atrace capture because the tab name changes
    myCpuService.profilerType = ATRACE
    myCpuService.setTrace(
      CpuProfilerTestUtils.traceFileToByteString(TestUtils.getWorkspaceFile(ATRACE_TRACE_PATH)))
    stage.setAndSelectCapture(0)
    stage.selectedThread = stage.capture!!.mainThreadId
    tabPane = TreeWalker(myStageView.component).descendants().filterIsInstance(CommonTabbedPane::class.java)[0]
    tabPane.selectedIndex = 0
    ReferenceWalker(myStageView).assertReachable(ChartDetailsView.CallChartDetailsView::class.java)
    assertThat(tabPane.getTitleAt(0)).matches("Trace Events")
  }
}