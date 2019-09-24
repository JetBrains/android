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
import com.android.tools.adtui.model.event.EventModel
import com.android.tools.idea.protobuf.ByteString
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.FakeProfilerService
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.StudioProfilers
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
    val stage = CpuCaptureStage.create(profilers, "Test", CpuProfilerTestUtils.getTraceFile("simpleperf.trace"))
    assertThat(stage.state).isEqualTo(CpuCaptureStage.State.PARSING)
  }

  @Test
  fun parsingFailureReturnsToProfilerStage() {
    val stage = CpuCaptureStage.create(profilers, "Test", CpuProfilerTestUtils.getTraceFile("corrupted_trace.trace"))
    profilers.stage = stage
    assertThat(services.notification).isNotNull()
    assertThat(profilers.stage).isInstanceOf(CpuProfilerStage::class.java)
  }

  @Test
  fun parsingSuccessTriggersAspect() {
    val aspect = AspectObserver()
    val stage = CpuCaptureStage.create(profilers, "Test", CpuProfilerTestUtils.getTraceFile("basic.trace"))
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
    val name = "Test"
    val stage = CpuCaptureStage.create(profilers, name, CpuProfilerTestUtils.getTraceFile("basic.trace"))
    assertThat(stage.captureHandler.configurationText).isEqualTo(name)
  }

  @Test
  fun trackGroupModelsAreSet() {
    val stage = CpuCaptureStage.create(profilers, "Test", CpuProfilerTestUtils.getTraceFile("basic.trace"))
    profilers.stage = stage

    assertThat(stage.trackGroupListModel.size).isEqualTo(2)

    val interactionTrackGroup = stage.trackGroupListModel[0]
    assertThat(interactionTrackGroup.title).isEqualTo("Interaction")
    assertThat(interactionTrackGroup.size).isEqualTo(2)
    assertThat(interactionTrackGroup[0].title).isEqualTo("User")
    assertThat(interactionTrackGroup[1].title).isEqualTo("Lifecycle")

    val threadsTrackGroup = stage.trackGroupListModel[1]
    assertThat(threadsTrackGroup.title).isEqualTo("Threads (1)")
    assertThat(threadsTrackGroup.size).isEqualTo(1)
  }

  @Test
  fun trackGroupModelsAreSetForAtrace() {
    services.enableAtrace(true)
    val stage = CpuCaptureStage.create(profilers, "Test", CpuProfilerTestUtils.getTraceFile("atrace_processid_1.ctrace"))
    profilers.stage = stage

    assertThat(stage.trackGroupListModel.size).isEqualTo(3)

    val interactionTrackGroup = stage.trackGroupListModel[0]
    assertThat(interactionTrackGroup.title).isEqualTo("Interaction")
    assertThat(interactionTrackGroup.size).isEqualTo(2)
    assertThat(interactionTrackGroup[0].title).isEqualTo("User")
    assertThat(interactionTrackGroup[1].title).isEqualTo("Lifecycle")

    val displayTrackGroup = stage.trackGroupListModel[1]
    assertThat(displayTrackGroup.title).isEqualTo("Display")
    assertThat(displayTrackGroup.size).isEqualTo(3)
    assertThat(displayTrackGroup[0].title).isEqualTo("Frames")
    assertThat(displayTrackGroup[1].title).isEqualTo("Surfaceflinger")
    assertThat(displayTrackGroup[2].title).isEqualTo("Vsync")

    val threadsTrackGroup = stage.trackGroupListModel[2]
    assertThat(threadsTrackGroup.title).isEqualTo("Threads (1)")
    assertThat(threadsTrackGroup.size).isEqualTo(1)
    assertThat(threadsTrackGroup[0].title).isEqualTo("atrace")
  }

  @Test
  fun minimapSetsCaptureRange() {
    val stage = CpuCaptureStage.create(profilers, "Test", CpuProfilerTestUtils.getTraceFile("basic.trace"))
    profilers.stage = stage
    assertThat(stage.minimapModel.maxRange.length.toLong()).isEqualTo(303)
  }

  @Test
  fun minimapRangeSelectionUpdatesTrackGroups() {
    val stage = CpuCaptureStage.create(profilers, "Test", CpuProfilerTestUtils.getTraceFile("basic.trace"))
    profilers.stage = stage
    assertThat(stage.trackGroupListModel[0][0].dataModel.javaClass).isAssignableTo(EventModel::class.java)
    val userEventModelRange = (stage.trackGroupListModel[0][0].dataModel as EventModel<*>).rangedSeries.xRange

    // Select a new range
    stage.minimapModel.rangeSelectionModel.selectionRange.set(1.0, 2.0)
    assertThat(userEventModelRange.min).isEqualTo(1.0)
    assertThat(userEventModelRange.max).isEqualTo(2.0)
  }

  @Test
  fun fullTraceAnalysisAddedByDefault() {
    val stage = CpuCaptureStage.create(profilers, "Test", CpuProfilerTestUtils.getTraceFile("basic.trace"))
    profilers.stage = stage
    assertThat(stage.analysisModels.size).isEqualTo(1)
    assertThat(stage.analysisModels[0].name).isEqualTo(CpuCaptureStage.DEFAULT_ANALYSIS_NAME)
    assertThat(stage.analysisModels[0].tabs).isNotEmpty()
  }

  @Test
  fun invalidTraceIdReturnsNull() {
    val stage = CpuCaptureStage.create(profilers, "Test", 0)
    assertThat(stage).isNull()
  }

  @Test
  fun validTraceIdReturnsCaptureStage() {
    services.enableEventsPipeline(true)
    val trace = CpuProfilerTestUtils.getTraceFile("perfetto.trace")
    val traceBytes = ByteString.readFrom(FileInputStream(trace))
    transportService.addFile("1", traceBytes)
    val stage = CpuCaptureStage.create(profilers, "Test", 1)
    assertThat(stage).isNotNull()
  }

  @Test
  fun captureHintSelectsProperProcessStringName() {
    services.setListBoxOptionsIndex(-1)
    services.enableAtrace(true)
    services.enablePerfetto(true)
    val stage = CpuCaptureStage(profilers, "Test", CpuProfilerTestUtils.getTraceFile("perfetto.trace"), "surfaceflinger", 0)
    profilers.stage = stage
    assertThat(stage.capture).isNotNull()
    val mainThread = stage.capture.threads.find { it.isMainThread }
    assertThat(mainThread!!.name).isEqualTo("surfaceflinger")
  }

  @Test
  fun captureHintSelectsProperProcessPID() {
    services.setListBoxOptionsIndex(-1)
    services.enableAtrace(true)
    services.enablePerfetto(true)
    val stage = CpuCaptureStage(profilers, "Test", CpuProfilerTestUtils.getTraceFile("perfetto.trace"), null, 709)
    profilers.stage = stage
    assertThat(stage.capture).isNotNull()
    val mainThread = stage.capture.threads.find { it.isMainThread }
    assertThat(mainThread!!.name).isEqualTo("surfaceflinger")
  }

  @Test
  fun nullCaptureHintSelectsCaptureFromDialog() {
    services.setListBoxOptionsIndex(1)
    services.enableAtrace(true)
    services.enablePerfetto(true)
    val stage = CpuCaptureStage(profilers, "Test", CpuProfilerTestUtils.getTraceFile("perfetto.trace"), null, 0)
    profilers.stage = stage
    assertThat(stage.capture).isNotNull()
    val mainThread = stage.capture.threads.find { it.isMainThread }
    assertThat(mainThread!!.name).isEqualTo("android.traceur")
  }
}