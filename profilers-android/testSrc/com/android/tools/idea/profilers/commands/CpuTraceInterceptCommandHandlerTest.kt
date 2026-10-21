/*
 * Copyright (C) 2022 The Android Open Source Project
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

import androidx.tracing.perfetto.handshake.protocol.ResponseResultCodes
import com.android.ddmlib.IShellOutputReceiver
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.io.grpc.ManagedChannel
import com.android.tools.idea.io.grpc.inprocess.InProcessChannelBuilder
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Trace
import com.android.tools.profiler.proto.TransportServiceGrpc
import com.android.tools.profilers.TraceConfigOptionsUtils
import com.android.tools.profilers.cpu.TraceMerger
import com.android.tools.profilers.cpu.config.ProfilingConfiguration.TraceType
import com.google.common.truth.Truth
import com.google.wireless.android.sdk.stats.PerfettoSdkHandshakeMetadata.HandshakeResult
import com.intellij.notification.Notification
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.testFramework.ApplicationRule
import java.nio.charset.Charset
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever

class CpuTraceInterceptCommandHandlerTest {
  @get:Rule val applicationRule = ApplicationRule()
  private val timer = FakeTimer()
  private val service = FakeTransportService(timer)

  @get:Rule var grpcChannel = FakeGrpcChannel("CpuTraceInterceptCommandHandlerTest", service)
  private val channel: ManagedChannel = InProcessChannelBuilder.forName(grpcChannel.name).usePlaintext().directExecutor().build()

  @After
  fun tearDown() {
    TraceMerger.activeV2Sessions.clear()
    TraceMerger.appTracePathCache.clear()
  }

  @Test
  fun `ShouldHandle filters request non perfetto`() {
    val testPid = 1
    val cmdId = 1
    val commandHandler = setupInterceptForTest(testPid)

    var command = buildCommand(cmdId, TraceType.PERFETTO)
    Truth.assertThat(commandHandler.shouldHandle(command)).isTrue()
    command = buildCommand(cmdId, TraceType.ART)
    Truth.assertThat(commandHandler.shouldHandle(command)).isFalse()
    command = buildCommand(cmdId, TraceType.SIMPLEPERF)
    Truth.assertThat(commandHandler.shouldHandle(command)).isFalse()
  }

  @Test
  fun `Trace command is forwarded`() {
    val testPid = 1
    val commandHandler = setupInterceptForTest(testPid)

    var command = buildCommand(1, TraceType.ART)
    var returnValue = commandHandler.execute(command)
    Truth.assertThat(returnValue.commandId).isEqualTo(1)

    var eventStream = service.getListForStream(0L)
    Truth.assertThat(eventStream).hasSize(1)
    Truth.assertThat(eventStream.first { it.kind == Common.Event.Kind.TRACE_STATUS }.commandId).isEqualTo(1)

    command = buildCommand(2, TraceType.PERFETTO)
    returnValue = commandHandler.execute(command)
    Truth.assertThat(returnValue.commandId).isEqualTo(2)

    eventStream = service.getListForStream(0L)
    Truth.assertThat(eventStream).hasSize(2)
    Truth.assertThat(eventStream.last { it.kind == Common.Event.Kind.TRACE_STATUS }.commandId).isEqualTo(2)
  }

  @Test
  fun `Trace command triggers handler`() {
    val testPid = 1
    val cmdId = 1
    val commandHandler = setupInterceptForTest(testPid)
    val startTrackCommand = buildCommand(cmdId, TraceType.PERFETTO)
    val returnValue = commandHandler.execute(startTrackCommand)
    Truth.assertThat(returnValue.commandId).isEqualTo(cmdId)
    val captor = ArgumentCaptor.forClass(String::class.java)
    verify(commandHandler.device, times(2)).executeShellCommand(captor.capture(), any(), any(), any())
    Truth.assertThat(captor.value).contains("ENABLE_TRACING")
  }

  @Test
  fun `Trace failed request log to logger`() {
    val testPid = 1
    val cmdId = 1
    // Exit code 2 == SDK already enabled.
    val broadcastFailed =
      ("Broadcasting: Intent { act=androidx.tracing.perfetto.action.ENABLE_TRACING flg=0x400000" +
        "cmp=androidx.compose.samples.crane/androidx.tracing.perfetto.TracingReceiver }\n" +
        "Broadcast completed: result=2, data=\"{\"exitCode\":2,\"requiredVersion\":\"1.0.0-alpha01\"" +
        "}\"\n")
    val commandHandler = setupInterceptForTest(testPid, broadcastFailed)
    val startTrackCommand = buildCommand(cmdId, TraceType.PERFETTO)
    val returnValue = commandHandler.execute(startTrackCommand)
    Truth.assertThat(returnValue.commandId).isEqualTo(cmdId)
    val captor = ArgumentCaptor.forClass(String::class.java)
    verify(commandHandler.device, times(2)).executeShellCommand(captor.capture(), any(), any(), any())
    Truth.assertThat(captor.value).contains("ENABLE_TRACING")
    Truth.assertThat(commandHandler.lastResponseCode).isEqualTo(ResponseResultCodes.RESULT_CODE_ALREADY_ENABLED)
    with(commandHandler.lastMetricsEvent!!) {
      Truth.assertThat(hasAndroidProfilerEvent()).isTrue()
      Truth.assertThat(androidProfilerEvent.hasPerfettoSdkHandshakeMetadata()).isTrue()
      Truth.assertThat(androidProfilerEvent.perfettoSdkHandshakeMetadata.handshakeResult).isEqualTo(HandshakeResult.ALREADY_ENABLED)
    }
  }

  @Test
  fun `Trace gracefully rejects invalid SDK version`() {
    val testPid = 1
    val cmdId = 1
    // Exit code 11 == SDK binary missing, combined with an invalid payload
    val payload = "1.0.0/tracing-perfetto-binary-1.0.0.aar#/../../../../../../../../../../../../../../../../../../tmp/aswb_044_pwned"
    val broadcastFailed =
      ("Broadcasting: Intent { act=androidx.tracing.perfetto.action.ENABLE_TRACING flg=0x400000" +
        "cmp=androidx.compose.samples.crane/androidx.tracing.perfetto.TracingReceiver }\n" +
        "Broadcast completed: result=11, data=\"{\"exitCode\":11,\"requiredVersion\":\"$payload\"" +
        "}\"\n")
    val commandHandler = setupInterceptForTest(testPid, broadcastFailed)
    val startTrackCommand = buildCommand(cmdId, TraceType.PERFETTO)
    val returnValue = commandHandler.execute(startTrackCommand)
    Truth.assertThat(returnValue.commandId).isEqualTo(cmdId)
    val captor = ArgumentCaptor.forClass(String::class.java)
    verify(commandHandler.device, times(2)).executeShellCommand(captor.capture(), any(), any(), any())
    Truth.assertThat(captor.value).contains("ENABLE_TRACING")
    Truth.assertThat(commandHandler.lastResponseCode).isEqualTo(ResponseResultCodes.RESULT_CODE_ERROR_BINARY_MISSING)
    with(commandHandler.lastMetricsEvent!!) {
      Truth.assertThat(hasAndroidProfilerEvent()).isTrue()
      Truth.assertThat(androidProfilerEvent.hasPerfettoSdkHandshakeMetadata()).isTrue()
      Truth.assertThat(androidProfilerEvent.perfettoSdkHandshakeMetadata.handshakeResult)
        .isEqualTo(HandshakeResult.ERROR_BINARY_UNAVAILABLE)
    }
  }

  @Test
  fun `Tracing 2_0 START success skips Tracing 1_0`() {
    val testPid = 1
    val cmdId = 1
    val commandHandler = setupInterceptForTracing2Test(testPid, 1)
    val startTrackCommand = buildCommand(cmdId, TraceType.PERFETTO)
    val returnValue = commandHandler.execute(startTrackCommand)
    Truth.assertThat(returnValue.commandId).isEqualTo(cmdId)

    val captor = ArgumentCaptor.forClass(String::class.java)
    verify(commandHandler.device, times(1)).executeShellCommand(captor.capture(), any(), any(), any())
    Truth.assertThat(captor.value).contains("action.START")
    Truth.assertThat(captor.value).doesNotContain("ENABLE_TRACING")
    Truth.assertThat(TraceMerger.getAppName(testPid.toLong())).isNotNull()
    verify(commandHandler.device, never()).pushFile(any(), any())
  }

  @Test
  fun `Tracing 2_0 START returns error shows UI notification and skips Tracing 1_0`() {
    val testPid = 1
    val cmdId = 1
    val commandHandler = setupInterceptForTracing2Test(testPid, -1)
    val startTrackCommand = buildCommand(cmdId, TraceType.PERFETTO)
    mockStatic(NotificationGroupManager::class.java).use { mockedManager ->
      val mockNotificationGroupManager = mock(NotificationGroupManager::class.java)
      val mockNotificationGroup = mock(NotificationGroup::class.java)
      val mockNotification = mock(Notification::class.java)

      mockedManager.`when`<Any> { NotificationGroupManager.getInstance() }.thenReturn(mockNotificationGroupManager)
      whenever(mockNotificationGroupManager.getNotificationGroup("Android Notification Group")).thenReturn(mockNotificationGroup)
      whenever(mockNotificationGroup.createNotification(any<String>(), any<String>(), any<NotificationType>())).thenReturn(mockNotification)

      val returnValue = commandHandler.execute(startTrackCommand)
      Truth.assertThat(returnValue.commandId).isEqualTo(cmdId)

      val captor = ArgumentCaptor.forClass(String::class.java)
      verify(commandHandler.device, times(1)).executeShellCommand(captor.capture(), any(), any(), any())
      Truth.assertThat(captor.value).contains("action.START")
      Truth.assertThat(captor.value).doesNotContain("ENABLE_TRACING")
      Truth.assertThat(TraceMerger.getAppName(testPid.toLong())).isNotNull()

      verify(mockNotificationGroup, times(1))
        .createNotification(
          eq("Compose Tracing Error"),
          eq("Tracing initialization returned -1. Final merged trace might contain partial or no Compose data."),
          eq(NotificationType.WARNING),
        )
      verify(mockNotification, times(1)).notify(null)
    }
  }

  private fun setupInterceptForTest(testPid: Int): CpuTraceInterceptCommandHandler {
    val broadcast =
      ("Broadcasting: Intent { act=androidx.tracing.perfetto.action.ENABLE_TRACING flg=0x400000" +
        "cmp=androidx.compose.samples.crane/androidx.tracing.perfetto.TracingReceiver }\n" +
        "Broadcast completed: result=1, data=\"{\"exitCode\":1,\"requiredVersion\":\"1.0.0-alpha01\"" +
        "}\"\n")
    return setupInterceptForTest(testPid, broadcast)
  }

  private fun setupInterceptForTest(testPid: Int, broadcast: String): CpuTraceInterceptCommandHandler {
    val mockClient = LegacyCpuTraceCommandHandlerTest.createMockClient(testPid)
    val commandCaptor = ArgumentCaptor.forClass(String::class.java)
    val shellCaptor = ArgumentCaptor.forClass(IShellOutputReceiver::class.java)
    whenever(mockClient.device.executeShellCommand(commandCaptor.capture(), shellCaptor.capture(), any(), any())).then {
      val cmd = commandCaptor.value ?: ""
      val data =
        if (cmd.contains("action.START")) {
          "Broadcast completed: result=0\n".toByteArray(Charset.defaultCharset())
        } else {
          broadcast.toByteArray(Charset.defaultCharset())
        }
      shellCaptor.value.addOutput(data, 0, data.size)
    }
    val commandHandler = CpuTraceInterceptCommandHandler(mockClient.device, TransportServiceGrpc.newBlockingStub(channel))
    return commandHandler
  }

  private fun setupInterceptForTracing2Test(testPid: Int, startResult: Int): CpuTraceInterceptCommandHandler {
    val mockClient = LegacyCpuTraceCommandHandlerTest.createMockClient(testPid)
    val commandCaptor = ArgumentCaptor.forClass(String::class.java)
    val shellCaptor = ArgumentCaptor.forClass(IShellOutputReceiver::class.java)
    whenever(mockClient.device.executeShellCommand(commandCaptor.capture(), shellCaptor.capture(), any(), any())).then {
      val cmd = commandCaptor.value ?: ""
      val data =
        if (cmd.contains("action.START")) {
          "Broadcast completed: result=$startResult\n".toByteArray(Charset.defaultCharset())
        } else {
          "Broadcast completed: result=0\n".toByteArray(Charset.defaultCharset())
        }
      shellCaptor.value.addOutput(data, 0, data.size)
    }
    return CpuTraceInterceptCommandHandler(mockClient.device, TransportServiceGrpc.newBlockingStub(channel))
  }

  fun buildCommand(cmdId: Int, traceType: TraceType, testPid: Int = 1) =
    Commands.Command.newBuilder()
      .apply {
        type = Commands.Command.CommandType.START_TRACE
        commandId = cmdId
        pid = testPid
        startTrace =
          Trace.StartTrace.newBuilder()
            .apply {
              profilerType = Trace.ProfilerType.CPU
              val configuration =
                Trace.TraceConfiguration.newBuilder().apply {
                  abiCpuArch = "FakeAbi"
                  appName = "com.example.app"
                }
              // Add the technology-specific options.
              TraceConfigOptionsUtils.addDefaultTraceOptions(configuration, traceType)
              this.configuration = configuration.build()
            }
            .build()
      }
      .build()
}
