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
package com.android.tools.idea.profilers

import com.android.ddmlib.Client
import com.android.ddmlib.ClientData
import com.android.ddmlib.IDevice
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Cpu
import com.android.tools.profiler.proto.CpuProfiler
import com.android.tools.profiler.protobuf3jarjar.ByteString
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.cpu.FakeCpuService
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import org.mockito.Mockito.`when`
import org.mockito.Mockito.any
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.mock

class StudioLegacyCpuTraceProfilerTest {
  private val myTimer = FakeTimer()
  private val myTransportService = FakeTransportService(myTimer)
  private val myCpuService = FakeCpuService()

  @Rule
  @JvmField
  var myGrpcChannel = FakeGrpcChannel("StudioLegacyCpuTraceProfilerTest", myTransportService, myCpuService)
  val myProfilerClient = ProfilerClient(myGrpcChannel.name)

  @get:Rule
  val thrown = ExpectedException.none()

  @Test
  fun simplePerfThrowsAssertion() {
    val profiler = StudioLegacyCpuTraceProfiler(createMockDevice(null),
                                                myProfilerClient.cpuClient,
                                                myProfilerClient.transportClient,
                                                HashMap<String, ByteString>())
    thrown.expect(AssertionError::class.java)
    profiler.startProfilingApp(CpuProfiler.CpuProfilingAppStartRequest.newBuilder()
                                 .setConfiguration(Cpu.CpuTraceConfiguration.newBuilder()
                                                     .setUserOptions(Cpu.CpuTraceConfiguration.UserOptions.newBuilder()
                                                                       .setTraceType(Cpu.CpuTraceType.SIMPLEPERF)))
                                 .build())
  }

  @Test
  fun startProfilingNullClientName() {
    val profiler = StudioLegacyCpuTraceProfiler(createMockDevice(null),
                                                myProfilerClient.cpuClient,
                                                myProfilerClient.transportClient,
                                                HashMap<String, ByteString>())
    val response = profiler.startProfilingApp(CpuProfiler.CpuProfilingAppStartRequest.newBuilder()
                                                .setConfiguration(Cpu.CpuTraceConfiguration.newBuilder()
                                                                    .setUserOptions(Cpu.CpuTraceConfiguration.UserOptions.newBuilder()
                                                                                      .setTraceType(Cpu.CpuTraceType.ART)))
                                                .build())
    assertThat(response.status.errorMessage).isNotEmpty()
    assertThat(response.status.status).isEqualTo(Cpu.TraceStartStatus.Status.FAILURE)
  }

  @Test
  fun getTraceInfoGoesThroughDaemon() {
    val byteCache = HashMap<String, ByteString>()
    val session = Common.Session.newBuilder().setPid(1)
    val profiler = StudioLegacyCpuTraceProfiler(createMockDevice("Test"),
                                                myProfilerClient.cpuClient,
                                                myProfilerClient.transportClient,
                                                byteCache)
    val traceInfoRequest = CpuProfiler.GetTraceInfoRequest.newBuilder()
      .setSession(session)
      .setFromTimestamp(Long.MIN_VALUE)
      .setToTimestamp(Long.MAX_VALUE)
      .build()

    assertThat(profiler.getTraceInfo(traceInfoRequest)).isEmpty()

    val traceInfo = Cpu.CpuTraceInfo.newBuilder()
      .setTraceId(1)
      .setFromTimestamp(10)
      .setToTimestamp(20)
      .build()
    myCpuService.addTraceInfo(traceInfo)
    assertThat(profiler.getTraceInfo(traceInfoRequest)).containsExactly(traceInfo)
  }

  @Test
  fun atraceGoesThroughDaemon() {
    myTimer.currentTimeNs = 100L
    val byteCache = HashMap<String, ByteString>()
    val session = Common.Session.newBuilder().setPid(1)
    val profiler = StudioLegacyCpuTraceProfiler(createMockDevice("Test"),
                                                myProfilerClient.cpuClient,
                                                myProfilerClient.transportClient,
                                                byteCache)
    val configuration = Cpu.CpuTraceConfiguration.newBuilder().setUserOptions(
      Cpu.CpuTraceConfiguration.UserOptions.newBuilder().setTraceType(Cpu.CpuTraceType.ATRACE))
      .build()
    val startRequest = CpuProfiler.CpuProfilingAppStartRequest.newBuilder().setSession(session).setConfiguration(configuration).build()
    val stopRequest = CpuProfiler.CpuProfilingAppStopRequest.newBuilder().setSession(session).setTraceType(Cpu.CpuTraceType.ATRACE).build()
    val traceInfoRequest = CpuProfiler.GetTraceInfoRequest.newBuilder()
      .setSession(session)
      .setFromTimestamp(Long.MIN_VALUE)
      .setToTimestamp(Long.MAX_VALUE)
      .build()
    assertThat(profiler.getTraceInfo(traceInfoRequest)).isEmpty()

    myCpuService.setStartProfilingStatus(Cpu.TraceStartStatus.Status.SUCCESS)
    val startResponse = profiler.startProfilingApp(startRequest)
    assertThat(startResponse.status.status).isEqualTo(Cpu.TraceStartStatus.Status.SUCCESS)
    assertThat(myCpuService.traceType).isEqualTo(Cpu.CpuTraceType.ATRACE)
    assertThat(myCpuService.startStopCapturingSession).isEqualTo(session.build())

    // Check the state of the StudioLegacyCpuTraceProfiler
    myTimer.currentTimeNs = 150L
    myCpuService.setStopProfilingStatus(Cpu.TraceStopStatus.Status.SUCCESS)
    val stopResponse = profiler.stopProfilingApp(stopRequest)
    assertThat(stopResponse.traceId).isEqualTo(FakeCpuService.FAKE_TRACE_ID)
    assertThat(stopResponse.status.status).isEqualTo(Cpu.TraceStopStatus.Status.SUCCESS)
  }

  private fun createMockDevice(deviceName: String?): IDevice {
    val device = mock<IDevice>(IDevice::class.java)
    val client = mock<Client>(Client::class.java)
    val clientData = mock<ClientData>(ClientData::class.java)
    val profilingStatus = ClientData.MethodProfilingStatus.OFF
    // Configure Device
    `when`(device.getClientName(anyInt())).thenReturn(deviceName)
    `when`(device.getClient(any())).thenReturn(client)
    // Configure Client
    `when`(client.clientData).thenReturn(clientData)
    // Configure ClientData
    `when`(clientData.methodProfilingStatus).thenReturn(profilingStatus)
    return device
  }
}