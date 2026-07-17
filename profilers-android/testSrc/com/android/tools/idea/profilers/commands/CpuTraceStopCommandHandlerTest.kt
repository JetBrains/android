/*
 * Copyright (C) 2026 The Android Open Source Project
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

import com.android.ddmlib.IShellOutputReceiver
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.io.grpc.ManagedChannel
import com.android.tools.idea.io.grpc.inprocess.InProcessChannelBuilder
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Trace
import com.android.tools.profiler.proto.TransportServiceGrpc
import com.android.tools.profilers.TraceConfigOptionsUtils
import com.android.tools.profilers.cpu.TraceMerger
import com.android.tools.profilers.cpu.config.ProfilingConfiguration.TraceType
import com.google.common.truth.Truth
import com.intellij.testFramework.ApplicationRule
import java.io.File
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

class CpuTraceStopCommandHandlerTest {
  @get:Rule val applicationRule = ApplicationRule()
  private val timer = FakeTimer()
  private val service = FakeTransportService(timer)

  @get:Rule var grpcChannel = FakeGrpcChannel("CpuTraceStopCommandHandlerTest", service)
  private val channel: ManagedChannel = InProcessChannelBuilder.forName(grpcChannel.name).usePlaintext().directExecutor().build()

  @After
  fun tearDown() {
    TraceMerger.activeV2Sessions.clear()
    TraceMerger.appTracePathCache.clear()
  }

  /**
   * Tests the baseline behavior of the STOP_TRACE command interceptor when NO Tracing 2.0 session is active.
   *
   * It validates that:
   * 1. The interceptor immediately forwards the stop command to the transport daemon without side effects.
   * 2. No `executeShellCommand` calls are made to the device (the Tracing 1.0 logic operates purely at the daemon level).
   */
  @Test
  fun `STOP_TRACE forwards to daemon without Tracing 2_0 active`() {
    val testPid = 1
    val mockClient = LegacyCpuTraceCommandHandlerTest.createMockClient(testPid)
    val commandHandler = CpuTraceStopCommandHandler(mockClient.device, TransportServiceGrpc.newBlockingStub(channel))

    val stopTrackCommand = buildCommand(1, TraceType.PERFETTO, testPid)
    val returnValue = commandHandler.execute(stopTrackCommand)

    // Command is forwarded, no executeShellCommand is called because Tracing 2.0 isn't active
    Truth.assertThat(returnValue.commandId).isEqualTo(1)
    verify(mockClient.device, times(0)).executeShellCommand(any(), any(), any(), any())
  }

  /**
   * Tests the behavior of the STOP_TRACE command interceptor when a Tracing 2.0 session is active.
   *
   * It validates that:
   * 1. The interceptor securely triggers the FLUSH_TRACES_GET_PATH broadcast to save memory trace buffers to disk.
   * 2. The interceptor queries the directory using `ls -1tp` to identify trace files correctly.
   * 3. The interceptor pulls all identified trace files from the device via a direct pull.
   */
  @Test
  fun `STOP_TRACE with Tracing 2_0 active pulls traces directly`() {
    val testPid = 1
    val mockClient = LegacyCpuTraceCommandHandlerTest.createMockClient(testPid)
    TraceMerger.markAsTracingV2(testPid.toLong(), "com.example.app")

    val commandCaptor = ArgumentCaptor.forClass(String::class.java)
    val shellCaptor = ArgumentCaptor.forClass(IShellOutputReceiver::class.java)
    whenever(mockClient.device.executeShellCommand(commandCaptor.capture(), shellCaptor.capture(), any(), any())).then {
      val cmd = commandCaptor.value ?: ""
      val data =
        if (cmd.contains("FLUSH_TRACES_GET_PATH")) {
          "Broadcast completed: result=2, data=\"/data/local/tmp/trace_dir\"\n".toByteArray(Charset.defaultCharset())
        } else if (cmd.contains("ls -1tp")) {
          "trace1.perfetto-trace\ntrace2.perfetto-trace\n".toByteArray(Charset.defaultCharset())
        } else {
          ByteArray(0)
        }
      shellCaptor.value.addOutput(data, 0, data.size)
    }

    // Mock pullFile to actually create the local file so `localFile.exists()` returns true
    whenever(mockClient.device.pullFile(any(), any())).thenAnswer { invocation ->
      val localPath = invocation.arguments[1] as String
      val file = File(localPath)
      file.parentFile?.mkdirs()
      file.writeText("fake content")
      null
    }

    val commandHandler = CpuTraceStopCommandHandler(mockClient.device, TransportServiceGrpc.newBlockingStub(channel))
    val stopTrackCommand = buildCommand(2, TraceType.PERFETTO, testPid)
    commandHandler.execute(stopTrackCommand)

    // Wait for the background thread to finish pulling traces
    try {
      TraceMerger.appTracePathCache[testPid.toLong()]?.get(5, TimeUnit.SECONDS)
    } catch (e: Exception) {}

    // Verify explicit shell commands
    val executedCommands = commandCaptor.allValues
    Truth.assertThat(executedCommands.any { it.contains("FLUSH_TRACES_GET_PATH") }).isTrue()
    Truth.assertThat(executedCommands.any { it.contains("ls -1tp") }).isTrue()
    Truth.assertThat(executedCommands.any { it.contains("action.STOP") }).isTrue()
    // Verify run-as was NOT called because direct pull succeeded
    Truth.assertThat(executedCommands.any { it.contains("run-as com.example.app cat") }).isFalse()

    // Verify pullFile was called with correct remote paths
    val pullLocalCaptor = ArgumentCaptor.forClass(String::class.java)
    val pullRemoteCaptor = ArgumentCaptor.forClass(String::class.java)
    verify(mockClient.device, times(2)).pullFile(pullRemoteCaptor.capture(), pullLocalCaptor.capture())
    Truth.assertThat(pullRemoteCaptor.allValues).contains("/data/local/tmp/trace_dir/trace1.perfetto-trace")
    Truth.assertThat(pullRemoteCaptor.allValues).contains("/data/local/tmp/trace_dir/trace2.perfetto-trace")
  }

  /**
   * Tests the fallback behavior of the STOP_TRACE command interceptor when a Tracing 2.0 session is active, but the direct pull fails due
   * to permissions (common on non-Profileable apps or strict OEMs).
   *
   * It validates that:
   * 1. The direct `ls` command fails (e.g. Permission Denied), falling back to `run-as ls`.
   * 2. The direct `pullFile` throws an exception.
   * 3. The interceptor successfully falls back to using `run-as appName cat ...` to copy the trace.
   * 4. The trace is securely extracted and the temporary device file is cleanly deleted.
   */
  @Test
  fun `STOP_TRACE with Tracing 2_0 active falls back to run-as`() {
    val testPid = 1
    val mockClient = LegacyCpuTraceCommandHandlerTest.createMockClient(testPid)
    TraceMerger.markAsTracingV2(testPid.toLong(), "com.example.app")

    val commandCaptor = ArgumentCaptor.forClass(String::class.java)
    val shellCaptor = ArgumentCaptor.forClass(IShellOutputReceiver::class.java)
    whenever(mockClient.device.executeShellCommand(commandCaptor.capture(), shellCaptor.capture(), any(), any())).then {
      val cmd = commandCaptor.value ?: ""
      val data =
        if (cmd.contains("FLUSH_TRACES_GET_PATH")) {
          "Broadcast completed: result=2, data=\"/data/local/tmp/trace_dir\"\n".toByteArray(Charset.defaultCharset())
        } else if (cmd.contains("ls -1tp") && !cmd.contains("run-as")) {
          // Direct ls fails with permission denied
          "ls: /data/local/tmp/trace_dir: Permission denied\n".toByteArray(Charset.defaultCharset())
        } else if (cmd.contains("run-as com.example.app ls -1tp")) {
          // run-as ls succeeds
          "trace1.perfetto-trace\n".toByteArray(Charset.defaultCharset())
        } else {
          ByteArray(0)
        }
      shellCaptor.value.addOutput(data, 0, data.size)
    }

    // Direct pull fails, run-as pull succeeds
    whenever(mockClient.device.pullFile(any(), any())).thenAnswer { invocation ->
      val remotePath = invocation.arguments[0] as String
      if (!remotePath.startsWith("/data/local/tmp/1_")) {
        throw Exception("Permission denied")
      }
      val localPath = invocation.arguments[1] as String
      val file = File(localPath)
      file.parentFile?.mkdirs()
      file.writeText("fake content")
      null
    }

    val commandHandler = CpuTraceStopCommandHandler(mockClient.device, TransportServiceGrpc.newBlockingStub(channel))
    val stopTrackCommand = buildCommand(3, TraceType.PERFETTO, testPid)
    commandHandler.execute(stopTrackCommand)

    try {
      TraceMerger.appTracePathCache[testPid.toLong()]?.get(5, TimeUnit.SECONDS)
    } catch (e: Exception) {}

    // Verify explicit shell commands for fallback strategy
    val executedCommands = commandCaptor.allValues
    Truth.assertThat(executedCommands.any { it.contains("run-as com.example.app ls -1tp") }).isTrue()
    Truth.assertThat(executedCommands.any { it.contains("run-as com.example.app cat") }).isTrue()
    Truth.assertThat(executedCommands.any { it.contains("rm \"/data/local/tmp/1_trace1.perfetto-trace\"") }).isTrue()

    val pullLocalCaptor = ArgumentCaptor.forClass(String::class.java)
    val pullRemoteCaptor = ArgumentCaptor.forClass(String::class.java)
    verify(mockClient.device, times(2)).pullFile(pullRemoteCaptor.capture(), pullLocalCaptor.capture())
    Truth.assertThat(pullRemoteCaptor.allValues).contains("/data/local/tmp/trace_dir/trace1.perfetto-trace")
    Truth.assertThat(pullRemoteCaptor.allValues).contains("/data/local/tmp/1_trace1.perfetto-trace")
  }

  /**
   * Tests the behavior of the STOP_TRACE command interceptor when a Tracing 2.0 session is active, but the device trace directory is empty
   * (e.g. tracing failed silently on device, or permissions issues).
   *
   * It validates that:
   * 1. The interceptor securely triggers the FLUSH_TRACES_GET_PATH broadcast.
   * 2. The interceptor queries the directory using `ls -1tp` and correctly handles empty output.
   * 3. The interceptor safely skips `pullFile` without throwing exceptions or crashing.
   */
  @Test
  fun `STOP_TRACE with Tracing 2_0 empty directory skips pull safely`() {
    val testPid = 1
    val mockClient = LegacyCpuTraceCommandHandlerTest.createMockClient(testPid)
    TraceMerger.markAsTracingV2(testPid.toLong(), "com.example.app")

    val commandCaptor = ArgumentCaptor.forClass(String::class.java)
    val shellCaptor = ArgumentCaptor.forClass(IShellOutputReceiver::class.java)
    whenever(mockClient.device.executeShellCommand(commandCaptor.capture(), shellCaptor.capture(), any(), any())).then {
      val cmd = commandCaptor.value ?: ""
      val data =
        if (cmd.contains("FLUSH_TRACES_GET_PATH")) {
          "Broadcast completed: result=2, data=\"/data/local/tmp/trace_dir\"\n".toByteArray(Charset.defaultCharset())
        } else if (cmd.contains("ls -1tp")) {
          ByteArray(0)
        } else {
          ByteArray(0)
        }
      shellCaptor.value.addOutput(data, 0, data.size)
    }

    val commandHandler = CpuTraceStopCommandHandler(mockClient.device, TransportServiceGrpc.newBlockingStub(channel))
    val stopTrackCommand = buildCommand(4, TraceType.PERFETTO, testPid)
    commandHandler.execute(stopTrackCommand)

    try {
      TraceMerger.appTracePathCache[testPid.toLong()]?.get(5, TimeUnit.SECONDS)
    } catch (e: Exception) {}

    verify(mockClient.device, times(0)).pullFile(any(), any())

    val executedCommands = commandCaptor.allValues
    Truth.assertThat(executedCommands.any { it.contains("FLUSH_TRACES_GET_PATH") }).isTrue()
    Truth.assertThat(executedCommands.any { it.contains("ls -1tp") }).isTrue()
    Truth.assertThat(executedCommands.any { it.contains("action.STOP") }).isTrue()
  }

  /**
   * Tests the migration behavior for Startup Tracing.
   *
   * During Startup Tracing, the PID is initially unknown (0). Once STOP_TRACE is issued, the real PID is known. This validates that the
   * session state correctly migrates from PID 0 to the actual PID before processing.
   */
  @Test
  fun `STOP_TRACE handles Startup Tracing migration`() {
    val testPid = 1234
    val mockClient = LegacyCpuTraceCommandHandlerTest.createMockClient(testPid)
    whenever(mockClient.device.getClientName(testPid)).thenReturn("com.example.app")
    TraceMerger.activeV2Sessions[0L] = "com.example.app"

    val commandCaptor = ArgumentCaptor.forClass(String::class.java)
    val shellCaptor = ArgumentCaptor.forClass(IShellOutputReceiver::class.java)
    whenever(mockClient.device.executeShellCommand(commandCaptor.capture(), shellCaptor.capture(), any(), any())).then {
      shellCaptor.value.addOutput(ByteArray(0), 0, 0)
    }

    val commandHandler = CpuTraceStopCommandHandler(mockClient.device, TransportServiceGrpc.newBlockingStub(channel))
    val stopTrackCommand = buildCommand(5, TraceType.UNSPECIFIED, testPid)
    commandHandler.execute(stopTrackCommand)

    try {
      TraceMerger.appTracePathCache[testPid.toLong()]?.get(5, TimeUnit.SECONDS)
    } catch (e: Exception) {}

    // Verify migration
    Truth.assertThat(TraceMerger.activeV2Sessions.containsKey(0L)).isFalse()
    Truth.assertThat(TraceMerger.activeV2Sessions[testPid.toLong()]).isEqualTo("com.example.app")
    Truth.assertThat(TraceMerger.appTracePathCache.containsKey(testPid.toLong())).isTrue()
  }

  fun buildCommand(cmdId: Int, traceType: TraceType, testPid: Int) =
    Commands.Command.newBuilder()
      .apply {
        type = Commands.Command.CommandType.STOP_TRACE
        commandId = cmdId
        pid = testPid
        stopTrace =
          Trace.StopTrace.newBuilder()
            .apply {
              profilerType = Trace.ProfilerType.CPU
              val configuration =
                Trace.TraceConfiguration.newBuilder().apply {
                  abiCpuArch = "FakeAbi"
                  appName = "com.example.app"
                }
              TraceConfigOptionsUtils.addDefaultTraceOptions(configuration, traceType)
              this.configuration = configuration.build()
            }
            .build()
      }
      .build()
}
