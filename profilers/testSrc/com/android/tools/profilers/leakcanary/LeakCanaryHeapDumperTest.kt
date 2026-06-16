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
package com.android.tools.profilers.leakcanary

import com.android.testutils.TestUtils
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.idea.transport.faketransport.commands.CommandHandler
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Memory
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.WithFakeTimer
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LeakCanaryHeapDumperTest : WithFakeTimer {
  override val timer = FakeTimer()
  private val transportService = FakeTransportService(timer)
  @get:Rule val grpcChannel = FakeGrpcChannel("LeakCanaryHeapDumperTest", transportService)
  @get:Rule val tempFolder = TemporaryFolder()

  private lateinit var profilers: StudioProfilers
  private lateinit var ideProfilerServices: FakeIdeProfilerServices
  private lateinit var dumper: LeakCanaryHeapDumper

  @Before
  fun setUp() {
    ideProfilerServices = FakeIdeProfilerServices()
    profilers = StudioProfilers(ProfilerClient(grpcChannel.channel), ideProfilerServices, timer)
    dumper = LeakCanaryHeapDumper(profilers)

    // Register empty handlers for commands we expect to be sent
    transportService.setCommandHandler(
      Commands.Command.CommandType.SEND_LEAKCANARY_ANALYSIS,
      object : CommandHandler(timer) {
        override fun handleCommand(command: Commands.Command, events: MutableList<Common.Event>) {}
      },
    )
    transportService.setCommandHandler(
      Commands.Command.CommandType.SIGNAL_HEAP_DUMP_COMPLETE,
      object : CommandHandler(timer) {
        override fun handleCommand(command: Commands.Command, events: MutableList<Common.Event>) {}
      },
    )
  }

  private fun runWithPolling(block: () -> Unit) {
    val executor = Executors.newSingleThreadExecutor()
    try {
      val future = executor.submit { block() }
      while (!future.isDone) {
        timer.tick(FakeTimer.ONE_SECOND_IN_NS)
        profilers.transportPoller.poll()
        Thread.sleep(10)
      }
      future.get()
    } finally {
      executor.shutdown()
    }
  }

  /**
   * Tests the successful state machine execution of the LeakCanaryHeapDumper. Ensures that it sends the correct HEAP_DUMP command, waits
   * for status and completion, retrieves the file correctly from the transport, invokes the analyzer, and calls the successful callbacks.
   */
  @Test
  fun `test successful state machine`() {
    val heapDumpStartTime = 12345L
    transportService.setCommandHandler(
      Commands.Command.CommandType.HEAP_DUMP,
      object : CommandHandler(timer) {
        override fun handleCommand(command: Commands.Command, events: MutableList<Common.Event>) {
          events.add(
            Common.Event.newBuilder()
              .setPid(command.pid)
              .setGroupId(command.pid.toLong())
              .setKind(Common.Event.Kind.MEMORY_HEAP_DUMP_STATUS)
              .setCommandId(command.commandId)
              .setMemoryHeapdumpStatus(
                Memory.MemoryHeapDumpStatusData.newBuilder()
                  .setStatus(
                    Memory.HeapDumpStatus.newBuilder()
                      .setStatus(Memory.HeapDumpStatus.Status.SUCCESS)
                      .setStartTime(heapDumpStartTime)
                      .build()
                  )
              )
              .build()
          )
          events.add(
            Common.Event.newBuilder()
              .setPid(command.pid)
              .setGroupId(command.pid.toLong())
              .setKind(Common.Event.Kind.MEMORY_HEAP_DUMP)
              .setIsEnded(true)
              .setTimestamp(123456L)
              .setMemoryHeapdump(
                Memory.MemoryHeapDumpData.newBuilder()
                  .setInfo(Memory.HeapDumpInfo.newBuilder().setStartTime(heapDumpStartTime).setEndTime(123456L).setSuccess(true).build())
              )
              .build()
          )
        }
      },
    )

    // Provide the actual HPROF file for the download request
    val hprofFile = TestUtils.resolveWorkspacePath("tools/adt/idea/profilers/testData/hprofs/single_leak.hprof").toFile()
    transportService.addFile(heapDumpStartTime.toString(), hprofFile.absolutePath)

    var hostAnalysisFinishedCalled = false
    var onFatalErrorCalled = false
    var resetRetainedObjectCountCalled = false

    dumper.onHostAnalysisFinished = { _, _, _, _ -> hostAnalysisFinishedCalled = true }
    dumper.onFatalError = { _, _ -> onFatalErrorCalled = true }
    dumper.onResetRetainedObjectCount = { resetRetainedObjectCountCalled = true }
    dumper.onAnalysisProgress = {}

    runWithPolling {
      val triggered = dumper.triggerAndAnalyze()
      assertThat(triggered).isTrue()
    }

    assertThat(hostAnalysisFinishedCalled).isTrue()
    assertThat(resetRetainedObjectCountCalled).isTrue()
    assertThat(onFatalErrorCalled).isFalse()
  }

  /** Tests that triggering the analysis while another analysis is already in progress correctly returns false and ignores the request. */
  @Test
  fun `test concurrent trigger returns false`() {
    val field = LeakCanaryHeapDumper::class.java.getDeclaredField("isHeapDumpInProgress")
    field.isAccessible = true
    val atomicBoolean = field.get(dumper) as AtomicBoolean
    atomicBoolean.set(true)

    val triggered = dumper.triggerAndAnalyze()
    assertThat(triggered).isFalse()
  }
}
