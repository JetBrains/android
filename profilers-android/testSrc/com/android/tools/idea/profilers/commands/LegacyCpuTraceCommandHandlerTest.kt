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
import com.android.testutils.MockitoKt.whenever
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.profilers.LegacyCpuProfilingHandler
import com.android.tools.idea.protobuf.ByteString
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Cpu
import com.android.tools.profiler.proto.TransportServiceGrpc
import com.google.common.truth.Truth.assertThat
import com.android.tools.idea.io.grpc.ManagedChannel
import com.android.tools.idea.io.grpc.inprocess.InProcessChannelBuilder
import com.android.tools.profiler.proto.Trace
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.mock
import java.util.concurrent.LinkedBlockingDeque

class LegacyCpuTraceCommandHandlerTest {
  private val timer = FakeTimer()

  // Needed for GetCurrentTime
  private val service = FakeTransportService(timer)

  @get:Rule
  var grpcChannel = FakeGrpcChannel("LegacyCpuTraceCommandHandlerTest", service)
  private val channel: ManagedChannel = InProcessChannelBuilder.forName(grpcChannel.name).usePlaintext().directExecutor().build()

  @Test
  fun testStartStopWorkflow() {
    val testPid = 1
    val startTimestamp = 10L
    val endTimestamp = 20L

    val mockClient = createMockClient(testPid)
    val eventQueue = LinkedBlockingDeque<Common.Event>()
    val byteCache = HashMap<String, ByteString>()
    val commandHandler = LegacyCpuTraceCommandHandler(mockClient.device,
                                                      TransportServiceGrpc.newBlockingStub(channel),
                                                      eventQueue,
                                                      byteCache)

    timer.currentTimeNs = startTimestamp
    commandHandler.execute(buildStartCommand(testPid, 1))

    val expectedStartStatus = Trace.TraceStartStatus.newBuilder().setStatus(Trace.TraceStartStatus.Status.SUCCESS).build()
    val expectedTraceInfo = Cpu.CpuTraceInfo.newBuilder().apply {
      traceId = startTimestamp
      configuration = TRACE_CONFIG
      fromTimestamp = startTimestamp
      toTimestamp = -1
      startStatus = expectedStartStatus
    }
    val startStatusEvent = Common.Event.newBuilder().apply {
      pid = testPid
      kind = Common.Event.Kind.TRACE_STATUS
      commandId = 1
      traceStatus = Trace.TraceStatusData.newBuilder().apply {
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
    timer.currentTimeNs = endTimestamp
    commandHandler.execute(buildStopCommand(testPid, 2))

    val expectedEndStatus = Trace.TraceStopStatus.newBuilder().setStatus(Trace.TraceStopStatus.Status.SUCCESS).build()
    expectedTraceInfo.apply {
      toTimestamp = endTimestamp
      stopStatus = expectedEndStatus
    }
    val stopStatusEvent = Common.Event.newBuilder().apply {
      pid = testPid
      kind = Common.Event.Kind.TRACE_STATUS
      commandId = 2
      traceStatus = Trace.TraceStatusData.newBuilder().apply {
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
    assertThat(byteCache[startTimestamp.toString()]).isEqualTo(ByteString.copyFrom(FAKE_TRACE_BYTES))
  }

  @Test
  fun testMultiDeviceWorkflow() {
    // Start and stop recording on Device 1.
    val testPid1 = 1
    val startTimestamp = 10L
    val endTimestamp = 20L

    val mockClient1 = createMockClient(testPid1)
    val eventQueue1 = LinkedBlockingDeque<Common.Event>()
    val byteCache1 = HashMap<String, ByteString>()
    val commandHandler1 = LegacyCpuTraceCommandHandler(mockClient1.device,
                                                       TransportServiceGrpc.newBlockingStub(channel),
                                                       eventQueue1,
                                                       byteCache1)
    timer.currentTimeNs = startTimestamp
    commandHandler1.execute(buildStartCommand(testPid1, 1))
    eventQueue1.clear()
    timer.currentTimeNs = endTimestamp
    commandHandler1.execute(buildStopCommand(testPid1, 2))
    assertThat(byteCache1[startTimestamp.toString()]).isEqualTo(ByteString.copyFrom(FAKE_TRACE_BYTES))

    // Start and stop recording on Device 2.
    val testPid2 = 2
    val startTimestamp2 = 30L
    val traceBytes = byteArrayOf('b'.code.toByte())
    // Return different trace bytes.
    val mockClient2 = createMockClient(testPid2, traceBytes)
    val eventQueue2 = LinkedBlockingDeque<Common.Event>()
    val byteCache2 = HashMap<String, ByteString>()
    val commandHandler2 = LegacyCpuTraceCommandHandler(mockClient2.device,
                                                       TransportServiceGrpc.newBlockingStub(channel),
                                                       eventQueue2,
                                                       byteCache2)
    timer.currentTimeNs = startTimestamp2
    commandHandler2.execute(buildStartCommand(testPid2, 3))
    eventQueue2.clear()
    timer.currentTimeNs = 40L
    commandHandler2.execute(buildStopCommand(testPid2, 4))
    assertThat(byteCache2[startTimestamp2.toString()]).isEqualTo(ByteString.copyFrom(traceBytes))
  }

  companion object {
    private val FAKE_TRACE_BYTES = byteArrayOf('a'.code.toByte())
    private val TRACE_CONFIG = Trace.TraceConfiguration.newBuilder().apply {
      userOptions = Trace.UserOptions.newBuilder().apply {
        traceMode = Trace.TraceMode.INSTRUMENTED
        traceType = Trace.TraceType.ART
      }.build()
    }.build()

    fun createMockClient(testPid: Int, traceBytes: ByteArray = FAKE_TRACE_BYTES): Client = mock(Client::class.java).also { thisClient ->
      val mockClientData = mock(ClientData::class.java).apply {
        whenever(pid).thenReturn(testPid)
        whenever(methodProfilingStatus).thenReturn(ClientData.MethodProfilingStatus.TRACER_ON)
      }
      val mockDevice = mock(IDevice::class.java).apply {
        whenever(serialNumber).thenReturn("")
        whenever(getClientName(ArgumentMatchers.anyInt())).thenReturn("TestClient")
        whenever(getClient(ArgumentMatchers.anyString())).thenReturn(thisClient)
      }
      whenever(thisClient.clientData).thenReturn(mockClientData)
      whenever(thisClient.device).thenReturn(mockDevice)
      // We only have to mock onSuccess(...) for the stop tracing workflow for the command handler to work.
      whenever(thisClient.stopMethodTracer()).thenAnswer { LegacyCpuProfilingHandler.onSuccess(traceBytes, thisClient) }
    }

    fun buildStartCommand(testPid: Int, testCommandId: Int): Commands.Command = Commands.Command.newBuilder().apply {
      pid = testPid
      type = Commands.Command.CommandType.START_CPU_TRACE
      commandId = testCommandId
      startCpuTrace = Cpu.StartCpuTrace.newBuilder().setConfiguration(TRACE_CONFIG).build()
    }.build()

    fun buildStopCommand(testPid: Int, testCommandId: Int): Commands.Command = Commands.Command.newBuilder().apply {
      pid = testPid
      type = Commands.Command.CommandType.STOP_CPU_TRACE
      commandId = testCommandId
      stopCpuTrace = Cpu.StopCpuTrace.newBuilder().setConfiguration(TRACE_CONFIG).build()
    }.build()
  }
}