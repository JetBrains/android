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

import com.android.testutils.TestUtils
import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.adtui.stdui.CommonTabbedPane
import com.android.tools.profiler.proto.CpuProfiler
import com.android.tools.profiler.proto.CpuProfiler.CpuProfilerConfiguration.Mode.SAMPLED
import com.android.tools.profiler.proto.CpuProfiler.CpuProfilerType.*
import com.android.tools.profiler.proto.CpuProfiler.TraceInitiationType.INITIATED_BY_API
import com.android.tools.profilers.*
import com.android.tools.profilers.cpu.CpuProfilerStage.CaptureState.CAPTURING
import com.android.tools.profilers.cpu.CpuProfilerStage.CaptureState.IDLE
import com.android.tools.profilers.cpu.CpuProfilingConfigurationView.EDIT_CONFIGURATIONS_ENTRY
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

    myStage = CpuProfilerStage(profilers)
    myStage.studioProfilers.stage = myStage

    val profilersView = StudioProfilersView(profilers, FakeIdeProfilerComponents())
    myStageView = CpuProfilerStageView(profilersView, myStage)
  }

  @Test
  fun whenSelectingCallChartThereShouldBeInstanceOfTreeChartView() {
    val stage = myStageView.stage
    myCpuService.profilerType = ART
    myCpuService.setTrace(CpuProfilerTestUtils.traceFileToByteString(TestUtils.getWorkspaceFile(TRACE_PATH)))
    stage.setAndSelectCapture(1)
    stage.selectedThread = stage.capture!!.mainThreadId
    stage.setCaptureDetails(CaptureModel.Details.Type.BOTTOM_UP)

    ReferenceWalker(myStageView).assertNotReachable(CpuCaptureView.CallChartView::class.java)
    stage.setCaptureDetails(CaptureModel.Details.Type.CALL_CHART)
    assertThat(stage.captureDetails?.type).isEqualTo(CaptureModel.Details.Type.CALL_CHART)
    ReferenceWalker(myStageView).assertReachable(CpuCaptureView.CallChartView::class.java)
    var tabPane = TreeWalker(myStageView.component).descendants().filterIsInstance(CommonTabbedPane::class.java)[0]
    tabPane.selectedIndex = 0
    assertThat(tabPane.getTitleAt(0)).matches("Call Chart")

    //Change to an atrace capture because the tab name changes
    myCpuService.profilerType = ATRACE
    myCpuService.setTrace(CpuProfilerTestUtils.traceFileToByteString(TestUtils.getWorkspaceFile(ATRACE_TRACE_PATH)))
    stage.setAndSelectCapture(0)
    stage.selectedThread = stage.capture!!.mainThreadId
    tabPane = TreeWalker(myStageView.component).descendants().filterIsInstance(CommonTabbedPane::class.java)[0]
    tabPane.selectedIndex = 0
    ReferenceWalker(myStageView).assertReachable(CpuCaptureView.CallChartView::class.java)
    assertThat(tabPane.getTitleAt(0)).matches("Trace Events")
  }

  @Test
  fun apiInitiatedCaptureShouldShowSpecialConfig() {
    val stage = myStageView.stage

    assertThat(myCpuService.profilerType).isEqualTo(ART)
    val config = ProfilingConfiguration("My Config", SIMPLEPERF, SAMPLED)
    stage.profilerConfigModel.profilingConfiguration = config

    // Verify non-API-initiated config before the API tracing starts.
    assertThat(myStageView.profilingConfigurationView.profilingConfiguration).isEqualTo(config)
    assertThat(myStageView.profilingConfigurationView.profilingConfigurations.size).isGreaterThan(1)

    // API-initiated tracing starts.
    val apiTracingconfig = CpuProfiler.CpuProfilerConfiguration.newBuilder().setProfilerType(ART).build()
    val startTimestamp: Long = 100
    myCpuService.setOngoingCaptureConfiguration(apiTracingconfig, startTimestamp, INITIATED_BY_API)
    stage.updateProfilingState()

    // Verify the configuration is set to the special config properly.
    assertThat(myStageView.profilingConfigurationView.profilingConfiguration.name).isEqualTo("Debug API (Java)")
    assertThat(myStageView.profilingConfigurationView.profilingConfiguration.profilerType).isEqualTo(ART)
    assertThat(myStageView.profilingConfigurationView.profilingConfigurations.size).isEqualTo(1)
    assertThat(stage.captureState).isEqualTo(CAPTURING)

    // API-initiated tracing ends.
    myCpuService.setAppBeingProfiled(false)
    stage.updateProfilingState()

    // Verify the configuration is set back to the config before API-initiated tracing.
    assertThat(myStageView.profilingConfigurationView.profilingConfiguration).isEqualTo(config)
    assertThat(myStageView.profilingConfigurationView.profilingConfigurations.size).isGreaterThan(1)
    assertThat(stage.captureState).isEqualTo(IDLE)
  }

  @Test
  fun editConfigurationsEntryCantBeSetAsProfilingConfiguration() {
    assertThat(myStageView.profilingConfigurationView.profilingConfiguration).isNotNull()
    // ART Sampled should be the default configuration when starting the stage,
    // as it's the first configuration on the list.
    assertThat(myStageView.profilingConfigurationView.profilingConfiguration.name).isEqualTo(ProfilingConfiguration.ART_SAMPLED)

    // Set a new configuration and check it's actually set as stage's profiling configuration
    val instrumented = ProfilingConfiguration(ProfilingConfiguration.ART_INSTRUMENTED,
                                              CpuProfiler.CpuProfilerType.ART,
                                              CpuProfiler.CpuProfilerConfiguration.Mode.INSTRUMENTED)
    myStageView.profilingConfigurationView.profilingConfiguration = instrumented
    assertThat(myStageView.profilingConfigurationView.profilingConfiguration.name).isEqualTo(ProfilingConfiguration.ART_INSTRUMENTED)

    // Set CpuProfilerStage.EDIT_CONFIGURATIONS_ENTRY as profiling configuration
    // and check it doesn't actually replace the current configuration
    myStageView.profilingConfigurationView.profilingConfiguration = EDIT_CONFIGURATIONS_ENTRY
    assertThat(myStageView.profilingConfigurationView.profilingConfiguration.name).isEqualTo(ProfilingConfiguration.ART_INSTRUMENTED)

    // Just sanity check "Instrumented" is not the name of CpuProfilerStage.EDIT_CONFIGURATIONS_ENTRY
    assertThat(EDIT_CONFIGURATIONS_ENTRY.name).isNotEqualTo(ProfilingConfiguration.ART_INSTRUMENTED)
  }

  @Test
  fun nullProcessShouldNotThrowException() {
    // Set a device to null (e.g. when stop profiling) should not crash the CpuProfilerStage
    myStage.studioProfilers.device = null
    assertThat(myStage.studioProfilers.device as kotlin.Any?).isNull()

    // Open the profiling configurations dialog with null device shouldn't crash CpuProfilerStage.
    // Dialog is expected to be open so the user can edit configurations to be used by other devices later.
    myStageView.profilingConfigurationView.openProfilingConfigurationsDialog()
  }

}