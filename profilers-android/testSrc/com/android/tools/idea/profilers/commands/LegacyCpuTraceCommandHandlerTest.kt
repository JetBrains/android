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
package com.android.tools.idea.profilers.commands

import com.android.ddmlib.Client
import com.android.ddmlib.ClientData
import com.android.ddmlib.IDevice
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.protobuf.ByteString
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Cpu
import com.android.tools.profiler.proto.TransportServiceGrpc
import com.google.common.truth.Truth.assertThat
import io.grpc.inprocess.InProcessChannelBuilder
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.util.concurrent.LinkedBlockingDeque

class LegacyCpuTraceCommandHandlerTest {

  private val myTimer = FakeTimer()
  // Needed for GetCurrentTime
  private val myService = FakeTransportService(myTimer)
  @get:Rule
  var myGrpcChannel = FakeGrpcChannel("LegacyCpuTraceCommandHandlerTest", myService)
  val channel = InProcessChannelBuilder.forName(myGrpcChannel.name).usePlaintext().directExecutor().build()

  @Test
  fun testStartStopWorkflow() {
    val testPid = 1
    val startTimestamp = 10L
    val endTimestamp = 20L

    val mockDevice = mock(IDevice::class.java)
    val mockClient = mock(Client::class.java)
    val mockClientData = mock(ClientData::class.java)
    `when`(mockClientData.pid).thenReturn(testPid)
    `when`(mockClient.clientData).thenReturn(mockClientData)
    `when`(mockDevice.getClientName(ArgumentMatchers.anyInt())).thenReturn("TestClient")
    `when`(mockDevice.getClient(ArgumentMatchers.anyString())).thenReturn(mockClient)

    val eventQueue = LinkedBlockingDeque<Common.Event>()
    val byteCache = HashMap<String, ByteString>()
    val commandHandler = LegacyCpuTraceCommandHandler(mockDevice,
                                                      TransportServiceGrpc.newBlockingStub(channel),
                                                      eventQueue,
                                                      byteCache)

    val traceConfig = Cpu.CpuTraceConfiguration.newBuilder().apply {
      userOptions = Cpu.CpuTraceConfiguration.UserOptions.newBuilder().apply {
        traceMode = Cpu.CpuTraceMode.INSTRUMENTED
        traceType = Cpu.CpuTraceType.ART
      }.build()
    }.build()

    myTimer.currentTimeNs = startTimestamp
    val startTrackCommand = Commands.Command.newBuilder().apply {
      pid = testPid
      type = Commands.Command.CommandType.START_CPU_TRACE
      commandId = 1
      startCpuTrace = Cpu.StartCpuTrace.newBuilder().setConfiguration(traceConfig).build()
    }.build()
    commandHandler.execute(startTrackCommand)

    assertThat(eventQueue).hasSize(2)
    val expectedStartStatus = Cpu.TraceStartStatus.newBuilder().setStatus(Cpu.TraceStartStatus.Status.SUCCESS).build()
    var expectedTraceInfo = Cpu.CpuTraceInfo.newBuilder().apply {
      traceId = startTimestamp
      configuration = traceConfig
      fromTimestamp = startTimestamp
      toTimestamp = -1
      startStatus = expectedStartStatus
    }
    val startStatusEvent = Common.Event.newBuilder().apply {
      pid = testPid
      kind = Common.Event.Kind.CPU_TRACE_STATUS
      commandId = 1
      cpuTraceStatus = Cpu.CpuTraceStatusData.newBuilder().apply {
        traceStartStatus = expectedStartStatus
      }.build()
    }.build()
    val startTrackingEvent = Common.Event.newBuilder().apply {
      pid = testPid
      kind = Common.Event.Kind.CPU_TRACE
      groupId = startTimestamp
      timestamp = startTimestamp
      cpuTrace = Cpu.CpuTraceData.newBuilder().apply {
        traceStarted = Cpu.CpuTraceData.TraceStarted.newBuilder().setTraceInfo(expectedTraceInfo).build()
      }.build()
    }.build()
    assertThat(eventQueue).containsExactly(startStatusEvent, startTrackingEvent)

    eventQueue.clear()
    // We don't need to mock the callback to the LegacyCpuProfilerHandler for start tracing because no callback assumes success.
    // But we have to mock onSuccess(...) for the stop tracing workflow
    val FAKE_DATA = byteArrayOf('a'.toByte())
    `when`(mockClient.stopMethodTracer()).thenAnswer { commandHandler.profilingHandler.onSuccess(FAKE_DATA, mockClient) }
    `when`(mockClientData.methodProfilingStatus).thenReturn(ClientData.MethodProfilingStatus.TRACER_ON)

    myTimer.currentTimeNs = endTimestamp
    val stopTrackCommand = Commands.Command.newBuilder().apply {
      pid = testPid
      type = Commands.Command.CommandType.STOP_CPU_TRACE
      commandId = 2
      stopCpuTrace = Cpu.StopCpuTrace.newBuilder().setConfiguration(traceConfig).build()
    }.build()
    commandHandler.execute(stopTrackCommand)

    assertThat(eventQueue).hasSize(2)
    val expectedEndStatus = Cpu.TraceStopStatus.newBuilder().setStatus(Cpu.TraceStopStatus.Status.SUCCESS).build()
    expectedTraceInfo.apply {
      toTimestamp = endTimestamp
      stopStatus = expectedEndStatus
    }
    val stopStatusEvent = Common.Event.newBuilder().apply {
      pid = testPid
      kind = Common.Event.Kind.CPU_TRACE_STATUS
      commandId = 2
      cpuTraceStatus = Cpu.CpuTraceStatusData.newBuilder().apply {
        traceStopStatus = expectedEndStatus
      }.build()
    }.build()
    val stopTrackingEvent = Common.Event.newBuilder().apply {
      pid = testPid
      kind = Common.Event.Kind.CPU_TRACE
      groupId = startTimestamp
      timestamp = endTimestamp
      cpuTrace = Cpu.CpuTraceData.newBuilder().apply {
        traceEnded = Cpu.CpuTraceData.TraceEnded.newBuilder().setTraceInfo(expectedTraceInfo).build()
      }.build()
    }.build()
    assertThat(eventQueue).containsExactly(stopStatusEvent, stopTrackingEvent)
    // Also assert that the bytes are stored in the cache.
    assertThat(byteCache[startTimestamp.toString()]).isEqualTo(ByteString.copyFrom(FAKE_DATA))

  }

}