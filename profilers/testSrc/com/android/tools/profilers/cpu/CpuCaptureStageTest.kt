/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.tools.adtui.model.AspectObserver
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.flags.enums.PowerProfilerDisplayMode
import com.android.tools.idea.protobuf.ByteString
import com.android.tools.idea.transport.TransportService
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.idea.transport.faketransport.TransportServiceTestImpl
import com.android.tools.profiler.proto.Common
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.NullMonitorStage
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.ProfilersTestData
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.cpu.analysis.CpuAnalysisTabModel.Type
import com.android.tools.profilers.cpu.analysis.CpuFullTraceAnalysisModel
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.registerServiceInstance
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.FileReader

class CpuCaptureStageTest {
  private val timer = FakeTimer()
  private val transportService = FakeTransportService(timer, true)

  @get:Rule
  var grpcChannel = FakeGrpcChannel("CpuCaptureStageTestChannel", transportService)

  @get:Rule
  val applicationRule = ApplicationRule()

  private lateinit var profilers: StudioProfilers

  private val services = FakeIdeProfilerServices()

  @Before
  fun setUp() {
    ApplicationManager.getApplication().registerServiceInstance(TransportService::class.java, TransportServiceTestImpl(transportService))
    profilers = StudioProfilers(ProfilerClient(grpcChannel.channel), services, timer)
    // One second must be enough for new devices (and processes) to be picked up
    timer.tick(FakeTimer.ONE_SECOND_IN_NS)
  }

  @Test
  fun savingCaptureHasData() {
    val data = "Some Data"
    val traceId = 1234L
    val file = CpuCaptureStage.saveCapture(traceId, ByteString.copyFromUtf8(data))
    assertThat(file.name).matches("cpu_trace_$traceId.trace")
    val reader = BufferedReader(FileReader(file))
    assertThat(reader.readLine()).isEqualTo(data)
  }

  @Test
  fun defaultStateIsParsing() {
    val stage = CpuCaptureStage.create(profilers, ProfilersTestData.DEFAULT_CONFIG,
                                       CpuProfilerTestUtils.getTraceFile("simpleperf.trace")!!, SESSION_ID)
    assertThat(stage.state).isEqualTo(CpuCaptureStage.State.PARSING)
  }

  @Test
  fun parsingFailureGoesToNullStage() {
    profilers.sessionsManager.importSessionFromFile(CpuProfilerTestUtils.getTraceFile("corrupted_trace.trace"))
    profilers.sessionsManager.update()
    assertThat(services.notification).isNotNull()
    assertThat(profilers.stage).isInstanceOf(NullMonitorStage::class.java)
    assertThat(profilers.session).isEqualTo(Common.Session.getDefaultInstance())
  }

  @Test
  fun parsingSuccessTriggersAspect() {
    val aspect = AspectObserver()
    val stage = CpuCaptureStage.create(profilers, ProfilersTestData.DEFAULT_CONFIG,
                                       CpuProfilerTestUtils.getTraceFile("basic.trace"), SESSION_ID)
    var stateHit = false
    stage.aspect.addDependency(aspect).onChange(CpuCaptureStage.Aspect.STATE) { stateHit = true }
    profilers.stage = stage
    assertThat(profilers.stage).isInstanceOf(CpuCaptureStage::class.java)
    assertThat(stage.state).isEqualTo(CpuCaptureStage.State.ANALYZING)
    assertThat(services.notification).isNull()
    assertThat(stateHit).isTrue()
  }

  @Test
  fun emptyTraceIsAccepted() {
    val stage = CpuCaptureStage.create(profilers, ProfilersTestData.DEFAULT_CONFIG,
                                       CpuProfilerTestUtils.getTraceFile("simpleperf_empty.trace"), SESSION_ID)
    profilers.stage = stage
    assertThat(profilers.stage).isInstanceOf(CpuCaptureStage::class.java)
    assertThat(stage.trackGroupModels).isEmpty()
    assertThat(stage.pinnedAnalysisModels).isEmpty()
    assertThat(services.notification).isNull()
  }

  @Test
  fun configurationNameIsSet() {
    val stage = CpuCaptureStage.create(profilers, ProfilersTestData.DEFAULT_CONFIG,
                                       CpuProfilerTestUtils.getTraceFile("basic.trace"), SESSION_ID)
    assertThat(stage.captureHandler.configurationText).isEqualTo(ProfilersTestData.DEFAULT_CONFIG.name)
  }

  @Test
  fun trackGroupModelsAreSet() {
    val stage = CpuCaptureStage.create(profilers, ProfilersTestData.DEFAULT_CONFIG,
                                       CpuProfilerTestUtils.getTraceFile("basic.trace"), SESSION_ID)
    profilers.stage = stage

    assertThat(stage.trackGroupModels.size).isEqualTo(1)

    val threadsTrackGroup = stage.trackGroupModels[0]
    assertThat(threadsTrackGroup.title).isEqualTo("Threads (1)")
    assertThat(threadsTrackGroup.size).isEqualTo(1)
    assertThat(threadsTrackGroup.boxSelectionModel).isNotNull()
  }

  @Test
  fun trackGroupModelsAreSetForAtrace() {
    services.setListBoxOptionsMatcher { option -> option.contains("system_server") }
    val stage = CpuCaptureStage.create(profilers, ProfilersTestData.DEFAULT_CONFIG,
                                       CpuProfilerTestUtils.getTraceFile("atrace.ctrace"), SESSION_ID)
    profilers.stage = stage

    assertThat(stage.trackGroupModels.size).isEqualTo(4)

    val displayTrackGroup = stage.trackGroupModels[0]
    assertThat(displayTrackGroup.title).isEqualTo("Display")
    assertThat(displayTrackGroup.size).isEqualTo(4)
    assertThat(displayTrackGroup[0].title).isEqualTo("Frames")
    assertThat(displayTrackGroup[1].title).isEqualTo("SurfaceFlinger")
    assertThat(displayTrackGroup[2].title).isEqualTo("VSYNC")
    assertThat(displayTrackGroup[3].title).isEqualTo("BufferQueue")

    val coresTrackGroup = stage.trackGroupModels[1]
    assertThat(coresTrackGroup.title).isEqualTo("CPU cores (4)")
    assertThat(coresTrackGroup.size).isEqualTo(8)
    assertThat(coresTrackGroup[0].title).isEqualTo("CPU 0")
    assertThat(coresTrackGroup[1].title).isEqualTo("CPU 0 Frequency")

    val rssMemoryTrackGroup = stage.trackGroupModels[2]
    assertThat(rssMemoryTrackGroup.title).isEqualTo("Process Memory (RSS)")

    val threadsTrackGroup = stage.trackGroupModels[3]
    assertThat(threadsTrackGroup.title).isEqualTo("Threads (40)")
    assertThat(threadsTrackGroup.size).isEqualTo(40)
  }

  @Test
  fun trackGroupModelsAreSetForPerfetto() {
    services.enableJankDetectionUi(false)
    services.setListBoxOptionsMatcher { option -> option.contains("system_server") }
    val stage = CpuCaptureStage.create(profilers, ProfilersTestData.DEFAULT_CONFIG,
                                       CpuProfilerTestUtils.getTraceFile("perfetto_cpu_usage.trace"), SESSION_ID)
    profilers.stage = stage

    assertThat(stage.trackGroupModels.size).isEqualTo(4)

    val displayTrackGroup = stage.trackGroupModels[0]
    assertThat(displayTrackGroup.title).isEqualTo("Display")
    assertThat(displayTrackGroup.size).isEqualTo(4)
    assertThat(displayTrackGroup[0].title).isEqualTo("Frames")
    assertThat(displayTrackGroup[1].title).isEqualTo("SurfaceFlinger")
    assertThat(displayTrackGroup[2].title).isEqualTo("VSYNC")
    assertThat(displayTrackGroup[3].title).isEqualTo("BufferQueue")

    val coresTrackGroup = stage.trackGroupModels[1]
    assertThat(coresTrackGroup.title).isEqualTo("CPU cores (8)")
    assertThat(coresTrackGroup.size).isEqualTo(16)
    assertThat(coresTrackGroup[0].title).isEqualTo("CPU 0")
    assertThat(coresTrackGroup[1].title).isEqualTo("CPU 0 Frequency")

    val rssMemoryTrackGroup = stage.trackGroupModels[2]
    assertThat(rssMemoryTrackGroup.title).isEqualTo("Process Memory (RSS)")
    assertThat(rssMemoryTrackGroup.size).isEqualTo(5)
    assertThat(rssMemoryTrackGroup[0].title).isEqualTo("Total")
    assertThat(rssMemoryTrackGroup[1].title).isEqualTo("Allocated")
    assertThat(rssMemoryTrackGroup[2].title).isEqualTo("File Mappings")
    assertThat(rssMemoryTrackGroup[3].title).isEqualTo("Shared")
    assertThat(rssMemoryTrackGroup[4].title).isEqualTo("Swapped-Out")

    val threadsTrackGroup = stage.trackGroupModels[3]
    assertThat(threadsTrackGroup.title).isEqualTo("Threads (148)")
    assertThat(threadsTrackGroup.size).isEqualTo(148)
  }

  @Test
  fun trackGroupModelsAreSetForPerfettoWithPower() {
    services.enableJankDetectionUi(false)
    // Enable power rail and battery counter tracks in min-max and zero-based display mode respectively.
    services.setSystemTracePowerProfilerDisplayMode(PowerProfilerDisplayMode.MINMAX)
    val stage = CpuCaptureStage.create(profilers, ProfilersTestData.DEFAULT_CONFIG,
                                       CpuProfilerTestUtils.getTraceFile("perfetto_cpu_usage_with_power.trace"), SESSION_ID)
    profilers.stage = stage

    assertThat(stage.trackGroupModels.size).isEqualTo(6)

    val displayTrackGroup = stage.trackGroupModels[0]
    assertThat(displayTrackGroup.title).isEqualTo("Display")
    assertThat(displayTrackGroup.size).isEqualTo(4)
    assertThat(displayTrackGroup[0].title).isEqualTo("Frames")
    assertThat(displayTrackGroup[1].title).isEqualTo("SurfaceFlinger")
    assertThat(displayTrackGroup[2].title).isEqualTo("VSYNC")
    assertThat(displayTrackGroup[3].title).isEqualTo("BufferQueue")

    val coresTrackGroup = stage.trackGroupModels[1]
    assertThat(coresTrackGroup.title).isEqualTo("CPU cores (8)")
    assertThat(coresTrackGroup.size).isEqualTo(16)
    assertThat(coresTrackGroup[0].title).isEqualTo("CPU 0")
    assertThat(coresTrackGroup[1].title).isEqualTo("CPU 0 Frequency")

    val rssMemoryTrackGroup = stage.trackGroupModels[2]
    assertThat(rssMemoryTrackGroup.title).isEqualTo("Process Memory (RSS)")
    assertThat(rssMemoryTrackGroup.size).isEqualTo(5)
    assertThat(rssMemoryTrackGroup[0].title).isEqualTo("Total")
    assertThat(rssMemoryTrackGroup[1].title).isEqualTo("Allocated")
    assertThat(rssMemoryTrackGroup[2].title).isEqualTo("File Mappings")
    assertThat(rssMemoryTrackGroup[3].title).isEqualTo("Shared")
    assertThat(rssMemoryTrackGroup[4].title).isEqualTo("Swapped-Out")

    val threadsTrackGroup = stage.trackGroupModels[3]
    assertThat(threadsTrackGroup.title).isEqualTo("Threads (105)")
    assertThat(threadsTrackGroup.size).isEqualTo(105)

    val powerRailTrackGroup = stage.trackGroupModels[4]
    assertThat(powerRailTrackGroup.title).isEqualTo("Power Rails")
    assertThat(powerRailTrackGroup.size).isEqualTo(13)

    val batteryDrainTrackGroup = stage.trackGroupModels[5]
    assertThat(batteryDrainTrackGroup.title).isEqualTo("Battery")
    assertThat(batteryDrainTrackGroup.size).isEqualTo(3)
  }

  @Test
  fun trackGroupModelsAreSetForPerfettoWithFrameLifecycle() {
    services.enableJankDetectionUi(false)
    services.setListBoxOptionsMatcher { option -> option.contains("profilertester") }
    val stage = CpuCaptureStage.create(profilers, ProfilersTestData.DEFAULT_CONFIG,
                                       CpuProfilerTestUtils.getTraceFile("perfetto_frame_lifecycle.trace"), SESSION_ID)
    profilers.stage = stage

    assertThat(stage.trackGroupModels.size).isEqualTo(5)

    // The Frames track is hidden when frame lifecycle data is available.
    val displayTrackGroup = stage.trackGroupModels[0]
    assertThat(displayTrackGroup.size).isEqualTo(3)
    assertThat(displayTrackGroup[0].title).isEqualTo("SurfaceFlinger")
    assertThat(displayTrackGroup[1].title).isEqualTo("VSYNC")
    assertThat(displayTrackGroup[2].title).isEqualTo("BufferQueue")

    // The Frame Lifecycle track group is displayed.
    val frameLifecycleTrackGroup = stage.trackGroupModels[1]
    assertThat(frameLifecycleTrackGroup.title).isEqualTo("Frame Lifecycle (android.com.java.profilertester.MainActivity#0)")
    assertThat(frameLifecycleTrackGroup.size).isEqualTo(4)
    assertThat(frameLifecycleTrackGroup.isCollapsedInitially).isFalse()
    assertThat(frameLifecycleTrackGroup[0].title).isEqualTo("Application")
    assertThat(frameLifecycleTrackGroup[1].title).isEqualTo("Wait for GPU")
    assertThat(frameLifecycleTrackGroup[2].title).isEqualTo("Composition")
    assertThat(frameLifecycleTrackGroup[3].title).isEqualTo("Frames on display")
  }

  @Test
  fun timelineSetsCaptureRange() {
    val stage = CpuCaptureStage.create(profilers, ProfilersTestData.DEFAULT_CONFIG,
                                       CpuProfilerTestUtils.getTraceFile("basic.trace"), SESSION_ID)
    profilers.stage = stage
    assertThat(stage.captureTimeline.dataRange.length.toLong()).isEqualTo(303)
    assertThat(stage.minimapModel.captureRange.length.toLong()).isEqualTo(303)
    assertThat(stage.minimapModel.rangeSelectionModel.selectionRange.length.toLong()).isEqualTo(303)
  }

  @Test
  fun minimapRangeSelectionUpdatesTrackGroups() {
    val stage = CpuCaptureStage.create(profilers, ProfilersTestData.DEFAULT_CONFIG,
                                       CpuProfilerTestUtils.getTraceFile("basic.trace"), SESSION_ID)
    profilers.stage = stage
    assertThat(stage.trackGroupModels[0][0].dataModel.javaClass).isAssignableTo(CpuThreadTrackModel::class.java)
    val threadModelRange = (stage.trackGroupModels[0][0].dataModel as CpuThreadTrackModel).callChartModel.range

    // Select a new range
    stage.minimapModel.rangeSelectionModel.selectionRange.set(1.0, 2.0)
    assertThat(threadModelRange.min).isEqualTo(1.0)
    assertThat(threadModelRange.max).isEqualTo(2.0)
  }

  @Test
  fun fullTraceAnalysisAddedByDefault() {
    val stage = CpuCaptureStage.create(profilers, ProfilersTestData.DEFAULT_CONFIG,
                                       CpuProfilerTestUtils.getTraceFile("basic.trace"), SESSION_ID)
    profilers.stage = stage
    assertThat(stage.pinnedAnalysisModels.size).isEqualTo(1)
    assertThat(stage.pinnedAnalysisModels[0].javaClass).isEqualTo(CpuFullTraceAnalysisModel::class.java)
    assertThat(stage.pinnedAnalysisModels[0].tabModels).isNotEmpty()
  }

  @Test
  fun invalidTraceIdReturnsNull() {
    val stage = CpuCaptureStage.create(profilers, ProfilersTestData.DEFAULT_CONFIG, 0)
    assertThat(stage).isNull()
  }

  @Test
  fun validTraceIdReturnsCaptureStage() {
    val trace = CpuProfilerTestUtils.getTraceFile("perfetto.trace")
    val traceBytes = ByteString.readFrom(FileInputStream(trace))
    transportService.addFile("1", traceBytes)
    val stage = CpuCaptureStage.create(profilers, ProfilersTestData.DEFAULT_CONFIG, 1)
    assertThat(stage).isNotNull()
  }

  @Test
  fun captureHintSelectsProperProcessStringName() {
    services.enableJankDetectionUi(false)
    services.setListBoxOptionsIndex(-1) // This makes process selector throws if we didn't selected based on name hint first.
    val stage = CpuCaptureStage(profilers, ProfilersTestData.DEFAULT_CONFIG, CpuProfilerTestUtils.getTraceFile("perfetto.trace"),
                                SESSION_ID, "/system/bin/surfaceflinger", 0)
    profilers.stage = stage
    assertThat(stage.capture).isNotNull()
    val mainThread = stage.capture.threads.find { it.isMainThread }
    assertThat(mainThread!!.name).isEqualTo("surfaceflinger")
  }

  @Test
  fun captureHintSelectsProperProcessPID() {
    services.enableJankDetectionUi(false)
    services.setListBoxOptionsIndex(-1) // This makes process selector throws if we didn't selected based on pid hint first.
    val stage = CpuCaptureStage(profilers, ProfilersTestData.DEFAULT_CONFIG, CpuProfilerTestUtils.getTraceFile("perfetto.trace"),
                                SESSION_ID, null, 709)
    profilers.stage = stage
    assertThat(stage.capture).isNotNull()
    val mainThread = stage.capture.threads.find { it.isMainThread }
    assertThat(mainThread!!.name).isEqualTo("surfaceflinger")
  }

  @Test
  fun nullCaptureHintSelectsCaptureFromDialog() {
    services.enableJankDetectionUi(false)
    services.setListBoxOptionsMatcher { option -> option.contains("system_server") }
    val stage = CpuCaptureStage(profilers, ProfilersTestData.DEFAULT_CONFIG, CpuProfilerTestUtils.getTraceFile("perfetto.trace"),
                                SESSION_ID, null, 0)
    profilers.stage = stage
    assertThat(stage.capture).isNotNull()
    val mainThread = stage.capture.threads.find { it.isMainThread }
    assertThat(mainThread!!.name).isEqualTo("system_server")
  }

  @Test
  fun validateThreadSelectTabsAreDisplayedOnNewCapture() {
    val stage = CpuCaptureStage.create(profilers, ProfilersTestData.DEFAULT_CONFIG,
                                       CpuProfilerTestUtils.getTraceFile("basic.trace"), SESSION_ID)
    profilers.stage = stage
    assertThat(stage.pinnedAnalysisModels.size).isEqualTo(1)
    assertThat(stage.pinnedAnalysisModels[0].javaClass).isEqualTo(CpuFullTraceAnalysisModel::class.java)
    val tabTypes = stage.pinnedAnalysisModels[0].tabModels.map { it.tabType }.toList()
    assertThat(tabTypes).containsExactly(Type.SUMMARY, Type.TOP_DOWN, Type.FLAME_CHART, Type.BOTTOM_UP).inOrder()
  }

  companion object {
    const val SESSION_ID = 123L
  }
}