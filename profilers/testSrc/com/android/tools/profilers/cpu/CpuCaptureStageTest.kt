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
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.FakeProfilerService
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.StudioProfilers
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.BufferedReader
import java.io.FileReader

class CpuCaptureStageTest {
  private val timer = FakeTimer()

  @get:Rule
  var grpcChannel = FakeGrpcChannel("CpuCaptureStageTestChannel", FakeCpuService(), FakeProfilerService(timer))
  private val profilerClient = ProfilerClient(grpcChannel.getName())

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
}