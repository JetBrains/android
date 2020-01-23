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
import com.android.tools.idea.protobuf.ByteString
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profiler.proto.Cpu
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.FakeProfilerService
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.ProfilersTestData
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.cpu.analysis.CpuAnalysisTabModel
import com.android.tools.profilers.cpu.analysis.CpuFullTraceAnalysisModel
import com.google.common.truth.Truth.assertThat
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
  var grpcChannel = FakeGrpcChannel("CpuCaptureStageTestChannel", FakeCpuService(), FakeProfilerService(timer), transportService)
  private val profilerClient = ProfilerClient(grpcChannel.name)

  private lateinit var profilers: StudioProfilers

  private val services = FakeIdeProfilerServices()

  @Before
  fun setUp() {
    profilers = StudioProfilers(profilerClient, services, timer)
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
    val stage = CpuCaptureStage.create(profilers, ProfilersTestData.DEFAULT_CONFIG, CpuProfilerTestUtils.getTraceFile("simpleperf.trace")!!)
    assertThat(stage.state).isEqualTo(CpuCaptureStage.State.PARSING)
  }

  @Test
  fun parsingFailureReturnsToProfilerStage() {
    val stage = CpuCaptureStage.create(profilers, ProfilersTestData.DEFAULT_CONFIG,
                                       CpuProfilerTestUtils.getTraceFile("corrupted_trace.trace"))
    profilers.stage = stage
    assertThat(services.notification).isNotNull()
    assertThat(profilers.stage).isInstanceOf(CpuProfilerStage::class.java)
  }

  @Test
  fun parsingSuccessTriggersAspect() {
    val aspect = AspectObserver()
    val stage = CpuCaptureStage.create(profilers, ProfilersTestData.DEFAULT_CONFIG, CpuProfilerTestUtils.getTraceFile("basic.trace"))
    var stateHit = false
    stage.aspect.addDependency(aspect).onChange(CpuCaptureStage.Aspect.STATE, Runnable { stateHit = true })
    profilers.stage = stage
    assertThat(profilers.stage).isInstanceOf(CpuCaptureStage::class.java)
    assertThat(stage.state).isEqualTo(CpuCaptureStage.State.ANALYZING)
    assertThat(services.notification).isNull()
    assertThat(stateHit).isTrue()
  }

  @Test
  fun configurationNameIsSet() {
    val stage = CpuCaptureStage.create(profilers, ProfilersTestData.DEFAULT_CONFIG, CpuProfilerTestUtils.getTraceFile("basic.trace"))
    assertThat(stage.captureHandler.configurationText).isEqualTo(ProfilersTestData.DEFAULT_CONFIG.name)
  }

  @Test
  fun trackGroupModelsAreSet() {
    val stage = CpuCaptureStage.create(profilers, ProfilersTestData.DEFAULT_CONFIG, CpuProfilerTestUtils.getTraceFile("basic.trace"))
    profilers.stage = stage

    assertThat(stage.trackGroupModels.size).isEqualTo(1)

    val threadsTrackGroup = stage.trackGroupModels[0]
    assertThat(threadsTrackGroup.title).isEqualTo("Threads (1)")
    assertThat(threadsTrackGroup.size).isEqualTo(1)
  }

  @Test
  fun trackGroupModelsAreSetForAtrace() {
    val stage = CpuCaptureStage.create(profilers, ProfilersTestData.DEFAULT_CONFIG, CpuProfilerTestUtils.getTraceFile("atrace.ctrace"))
    profilers.stage = stage

    assertThat(stage.trackGroupModels.size).isEqualTo(3)

    val displayTrackGroup = stage.trackGroupModels[0]
    assertThat(displayTrackGroup.title).isEqualTo("Display")
    assertThat(displayTrackGroup.size).isEqualTo(1)
    assertThat(displayTrackGroup[0].title).isEqualTo("Frames")

    val coresTrackGroup = stage.trackGroupModels[1]
    assertThat(coresTrackGroup.title).isEqualTo("CPU cores (4)")
    assertThat(coresTrackGroup.size).isEqualTo(4)
    assertThat(coresTrackGroup[0].title).isEqualTo("CPU 0")

    val threadsTrackGroup = stage.trackGroupModels[2]
    assertThat(threadsTrackGroup.title).isEqualTo("Threads (40)")
    assertThat(threadsTrackGroup.size).isEqualTo(40)
  }

  @Test
  fun timelineSetsCaptureRange() {
    val stage = CpuCaptureStage.create(profilers, ProfilersTestData.DEFAULT_CONFIG, CpuProfilerTestUtils.getTraceFile("basic.trace"))
    profilers.stage = stage
    assertThat(stage.captureTimeline.dataRange.length.toLong()).isEqualTo(303)
    assertThat(stage.minimapModel.captureRange.length.toLong()).isEqualTo(303)
    assertThat(stage.minimapModel.rangeSelectionModel.selectionRange.length.toLong()).isEqualTo(303)
  }

  @Test
  fun minimapRangeSelectionUpdatesTrackGroups() {
    val stage = CpuCaptureStage.create(profilers, ProfilersTestData.DEFAULT_CONFIG, CpuProfilerTestUtils.getTraceFile("basic.trace"))
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
    val stage = CpuCaptureStage.create(profilers, ProfilersTestData.DEFAULT_CONFIG, CpuProfilerTestUtils.getTraceFile("basic.trace"))
    profilers.stage = stage
    assertThat(stage.analysisModels.size).isEqualTo(1)
    assertThat(stage.analysisModels[0].javaClass).isEqualTo(CpuFullTraceAnalysisModel::class.java)
    assertThat(stage.analysisModels[0].tabModels).isNotEmpty()
  }

  @Test
  fun invalidTraceIdReturnsNull() {
    val stage = CpuCaptureStage.create(profilers, ProfilersTestData.DEFAULT_CONFIG, 0)
    assertThat(stage).isNull()
  }

  @Test
  fun validTraceIdReturnsCaptureStage() {
    services.enableEventsPipeline(true)
    val trace = CpuProfilerTestUtils.getTraceFile("perfetto.trace")
    val traceBytes = ByteString.readFrom(FileInputStream(trace))
    transportService.addFile("1", traceBytes)
    val stage = CpuCaptureStage.create(profilers, ProfilersTestData.DEFAULT_CONFIG, 1)
    assertThat(stage).isNotNull()
  }

  @Test
  fun captureHintSelectsProperProcessStringName() {
    services.setListBoxOptionsIndex(-1)
    services.enablePerfetto(true)
    val stage = CpuCaptureStage(profilers, ProfilersTestData.DEFAULT_CONFIG, CpuProfilerTestUtils.getTraceFile("perfetto.trace"),
                                "surfaceflinger", 0)
    profilers.stage = stage
    assertThat(stage.capture).isNotNull()
    val mainThread = stage.capture.threads.find { it.isMainThread }
    assertThat(mainThread!!.name).isEqualTo("surfaceflinger")
  }

  @Test
  fun captureHintSelectsProperProcessPID() {
    services.setListBoxOptionsIndex(-1)
    services.enablePerfetto(true)
    val stage = CpuCaptureStage(profilers, ProfilersTestData.DEFAULT_CONFIG, CpuProfilerTestUtils.getTraceFile("perfetto.trace"), null, 709)
    profilers.stage = stage
    assertThat(stage.capture).isNotNull()
    val mainThread = stage.capture.threads.find { it.isMainThread }
    assertThat(mainThread!!.name).isEqualTo("surfaceflinger")
  }

  @Test
  fun nullCaptureHintSelectsCaptureFromDialog() {
    services.setListBoxOptionsIndex(1)
    services.enablePerfetto(true)
    val stage = CpuCaptureStage(profilers, ProfilersTestData.DEFAULT_CONFIG, CpuProfilerTestUtils.getTraceFile("perfetto.trace"), null, 0)
    profilers.stage = stage
    assertThat(stage.capture).isNotNull()
    val mainThread = stage.capture.threads.find { it.isMainThread }
    assertThat(mainThread!!.name).isEqualTo("android.traceur")
  }

  @Test
  fun validateThreadSelectTabsAreDisplayedOnNewCapture() {
    val stage = CpuCaptureStage.create(profilers, ProfilersTestData.DEFAULT_CONFIG, CpuProfilerTestUtils.getTraceFile("basic.trace"))
    profilers.stage = stage
    assertThat(stage.analysisModels.size).isEqualTo(1)
    assertThat(stage.analysisModels[0].javaClass).isEqualTo(CpuFullTraceAnalysisModel::class.java)
    assertThat(stage.analysisModels[0].tabSize).isEqualTo(3)
    val tabs = stage.analysisModels[0].tabModels.toList()
    assertThat(tabs[0].tabType).isEqualTo(CpuAnalysisTabModel.Type.TOP_DOWN)
    assertThat(tabs[1].tabType).isEqualTo(CpuAnalysisTabModel.Type.FLAME_CHART)
    assertThat(tabs[2].tabType).isEqualTo(CpuAnalysisTabModel.Type.BOTTOM_UP)
  }

}