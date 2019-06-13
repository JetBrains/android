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
package com.android.tools.profilers.cpu

import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Cpu
import com.android.tools.profiler.proto.CpuProfiler
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.FakeProfilerService
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.event.FakeEventService
import com.android.tools.profilers.memory.FakeMemoryService
import com.android.tools.profilers.network.FakeNetworkService
import com.android.tools.profilers.sessions.SessionItem
import com.android.tools.profilers.sessions.SessionsManager
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException

class CpuCaptureSessionArtifactTest {

  private val myCpuService = FakeCpuService()
  private val myTimer = FakeTimer()

  @get:Rule
  val myThrown = ExpectedException.none()

  @get:Rule
  var myGrpcChannel = FakeGrpcChannel(
    "CpuCaptureSessionArtifactTestChannel",
    FakeTransportService(myTimer, false),
    FakeProfilerService(myTimer),
    FakeMemoryService(),
    myCpuService,
    FakeEventService(),
    FakeNetworkService.newBuilder().build()
  )

  private lateinit var mySessionsManager: SessionsManager

  private lateinit var mySessionItem: SessionItem

  @Before
  fun setUp() {
    val profilers = StudioProfilers(ProfilerClient(myGrpcChannel.name), FakeIdeProfilerServices(), myTimer)
    mySessionsManager = profilers.sessionsManager
    mySessionsManager.createImportedSession("fake.trace", Common.SessionMetaData.SessionType.CPU_CAPTURE, 0, 0, 0)
    mySessionsManager.update()
    assertThat(mySessionsManager.sessionArtifacts.size).isEqualTo(1)
    mySessionItem = mySessionsManager.sessionArtifacts[0] as SessionItem
    myCpuService.clearTraceInfo()
  }

  @Test
  fun testArtSampledCpuCaptureSessionName() {
    val artSampledTraceInfo = Cpu.CpuTraceInfo.newBuilder()
      .setConfiguration(Cpu.CpuTraceConfiguration.newBuilder()
                          .setUserOptions(Cpu.CpuTraceConfiguration.UserOptions.newBuilder()
                                            .setTraceType(Cpu.CpuTraceType.ART)
                                            .setTraceMode(Cpu.CpuTraceMode.SAMPLED)))
      .build()
    myCpuService.addTraceInfo(artSampledTraceInfo)
    mySessionsManager.update()
    assertThat(mySessionItem.subtitle).isEqualTo(ProfilingTechnology.ART_SAMPLED.getName())
  }

  @Test
  fun testArtInstrumentedCpuCaptureSessionName() {
    val artInstrumentedTraceInfo = Cpu.CpuTraceInfo.newBuilder()
      .setConfiguration(Cpu.CpuTraceConfiguration.newBuilder()
                          .setUserOptions(Cpu.CpuTraceConfiguration.UserOptions.newBuilder()
                                            .setTraceType(Cpu.CpuTraceType.ART)
                                            .setTraceMode(Cpu.CpuTraceMode.INSTRUMENTED)))
      .build()
    myCpuService.clearTraceInfo()
    myCpuService.addTraceInfo(artInstrumentedTraceInfo)
    mySessionsManager.update()
    assertThat(mySessionItem.subtitle).isEqualTo(ProfilingTechnology.ART_INSTRUMENTED.getName())
  }

  @Test
  fun testImportedArtTraceCpuCaptureSessionName() {
    val artImportedTraceInfo = Cpu.CpuTraceInfo.newBuilder()
      .setConfiguration(Cpu.CpuTraceConfiguration.newBuilder()
                          .setUserOptions(Cpu.CpuTraceConfiguration.UserOptions.newBuilder()
                                            .setTraceType(Cpu.CpuTraceType.ART)))
      .build()
    myCpuService.clearTraceInfo()
    myCpuService.addTraceInfo(artImportedTraceInfo)
    mySessionsManager.update()
    assertThat(mySessionItem.subtitle).isEqualTo(ProfilingTechnology.ART_UNSPECIFIED.getName())
  }

  @Test
  fun testSimpleperfCpuCaptureSessionName() {
    val simpleperfTraceInfo = Cpu.CpuTraceInfo.newBuilder()
      .setConfiguration(Cpu.CpuTraceConfiguration.newBuilder()
                          .setUserOptions(Cpu.CpuTraceConfiguration.UserOptions.newBuilder()
                                            .setTraceType(Cpu.CpuTraceType.SIMPLEPERF)))
      .build()
    myCpuService.clearTraceInfo()
    myCpuService.addTraceInfo(simpleperfTraceInfo)
    mySessionsManager.update()
    assertThat(mySessionItem.subtitle).isEqualTo(ProfilingTechnology.SIMPLEPERF.getName())
  }

  @Test
  fun testAtraceCpuCaptureSessionName() {
    val atraceInfo = Cpu.CpuTraceInfo.newBuilder()
      .setConfiguration(Cpu.CpuTraceConfiguration.newBuilder()
                          .setUserOptions(Cpu.CpuTraceConfiguration.UserOptions.newBuilder()
                                            .setTraceType(Cpu.CpuTraceType.ATRACE)))
      .build()
    myCpuService.clearTraceInfo()
    myCpuService.addTraceInfo(atraceInfo)
    mySessionsManager.update()
    assertThat(mySessionItem.subtitle).isEqualTo(ProfilingTechnology.ATRACE.getName())
  }

  @Test
  fun testUnexpectedTraceInfoCpuCaptureSessionName() {
    val unexpectedTraceInfo = Cpu.CpuTraceInfo.getDefaultInstance()
    myCpuService.clearTraceInfo()
    myCpuService.addTraceInfo(unexpectedTraceInfo)
    mySessionsManager.update()
    myThrown.expect(IllegalStateException::class.java)
    assertThat(mySessionItem.subtitle).isEqualTo("Anything")
  }
}