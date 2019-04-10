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
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.CpuProfiler
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.profiler.proto.Cpu
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.cpu.FakeCpuService
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.*

class StudioLegacyCpuTraceProfilerTest {
  private val myCpuService = FakeCpuService()

  @Rule
  @JvmField
  var myGrpcChannel = FakeGrpcChannel("StudioLegacyCpuTraceProfilerTest", myCpuService)
  val myProfilerClient = ProfilerClient(myGrpcChannel.name)

  @Test
  fun startProfilingNullClientName() {
    val profiler = StudioLegacyCpuTraceProfiler(createMockDevice(null), myProfilerClient.cpuClient)
    val response = profiler.startProfilingApp(CpuProfiler.CpuProfilingAppStartRequest.getDefaultInstance())
    assertThat(response.errorMessage).isNotEmpty()
    assertThat(response.status).isEqualTo(CpuProfiler.CpuProfilingAppStartResponse.Status.FAILURE)
  }

  @Test
  fun startStopProfilingAtrace() {
    val session = Common.Session.newBuilder().setPid(1)
    val profiler = StudioLegacyCpuTraceProfiler(createMockDevice("Test"), myProfilerClient.cpuClient)
    val startRequest = CpuProfiler.CpuProfilingAppStartRequest.newBuilder().setSession(session)
      .setConfiguration(CpuProfiler.CpuProfilerConfiguration.newBuilder().setTraceType(Cpu.CpuTraceType.ATRACE))
      .build()
    val stopRequest = CpuProfiler.CpuProfilingAppStopRequest.newBuilder().setSession(session).build()
    val statusRequest = CpuProfiler.ProfilingStateRequest.newBuilder().setSession(session).build()
    // Validate initial state is not set to recorded
    var checkStatusResponse = profiler.checkAppProfilingState(statusRequest)
    assertThat(checkStatusResponse.beingProfiled).isFalse()
    checkStatusResponse = myProfilerClient.cpuClient.checkAppProfilingState(statusRequest)
    assertThat(checkStatusResponse.beingProfiled).isFalse()

    val startResponse = profiler.startProfilingApp(startRequest)
    assertThat(startResponse.status).isEqualTo(CpuProfiler.CpuProfilingAppStartResponse.Status.SUCCESS)
    // Check the state of the StudioLegacyCpuTraceProfiler
    checkStatusResponse = profiler.checkAppProfilingState(statusRequest)
    assertThat(checkStatusResponse.beingProfiled).isTrue()
    assertThat(checkStatusResponse.configuration.traceType).isEqualTo(Cpu.CpuTraceType.ATRACE)
    // Also check the state of the service for systrace this should be true
    checkStatusResponse = myProfilerClient.cpuClient.checkAppProfilingState(statusRequest)
    assertThat(checkStatusResponse.beingProfiled).isTrue()
    assertThat(checkStatusResponse.configuration.traceType).isEqualTo(Cpu.CpuTraceType.ATRACE)
    val stopResponse = profiler.stopProfilingApp(stopRequest)
    assertThat(stopResponse.status).isEqualTo(CpuProfiler.CpuProfilingAppStopResponse.Status.SUCCESS)
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