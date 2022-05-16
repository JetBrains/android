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
package com.android.tools.idea.layoutinspector.pipeline

import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.transport.TransportClient
import com.android.tools.idea.transport.faketransport.FakeGrpcServer
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.idea.transport.faketransport.commands.CommandHandler
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Common
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ForegroundProcessDetectionTest {
  private val timer = FakeTimer()
  private val transportService = FakeTransportService(timer, true)

  @get:Rule
  val grpcServerRule = FakeGrpcServer.createFakeGrpcServer("ForegroundProcessDetectionTest", transportService)

  private var transportClient: TransportClient? = null

  @Before
  fun createPoller() {
    transportClient = TransportClient(grpcServerRule.name)
  }

  @Test
  fun testForegroundProcessesAreReceived() {
    val eventsLatch = CountDownLatch(2)

    var receivedStartCommandCount = 0
    var receivedStopCommandCount = 0
    val expectedForegroundProcesses = listOf(ForegroundProcess(1, "process1"), ForegroundProcess(2, "process2"))
    val receivedForegroundProcesses = mutableListOf<ForegroundProcess>()

    val foregroundProcessListener = object : ForegroundProcessListener {
      override fun onNewProcess(foregroundProcess: ForegroundProcess) {
        receivedForegroundProcesses.add(foregroundProcess)
        eventsLatch.countDown()
      }
    }

    setCommandHandler(Commands.Command.CommandType.START_TRACKING_FOREGROUND_PROCESS) {
      receivedStartCommandCount += 1

      // send events
      expectedForegroundProcesses.forEachIndexed { index, foregroundProcess ->
        transportService.addEventToStream(FakeTransportService.FAKE_DEVICE_ID, createNewForegroundProcessEvent(foregroundProcess, index))
      }
    }

    setCommandHandler(Commands.Command.CommandType.STOP_TRACKING_FOREGROUND_PROCESS) {
      receivedStopCommandCount += 1
    }

    val foregroundProcessDetection = ForegroundProcessDetection(transportClient!!, foregroundProcessListener)
    foregroundProcessDetection.start()

    // wait for events to be dispatched
    eventsLatch.await(2, TimeUnit.SECONDS)

    foregroundProcessDetection.stop()

    assertThat(receivedForegroundProcesses).isEqualTo(expectedForegroundProcesses)
    assertThat(receivedStartCommandCount).isEqualTo(1)
    assertThat(receivedStopCommandCount).isEqualTo(1)
  }

  private fun createNewForegroundProcessEvent(foregroundProcess: ForegroundProcess, timestamp: Int): Common.Event {
    val eventBuilder = Common.Event.newBuilder()
    return eventBuilder
      .setKind(Common.Event.Kind.LAYOUT_INSPECTOR_FOREGROUND_PROCESS)
      .setTimestamp(timestamp.toLong())
      .setGroupId(1234)
      .setLayoutInspectorForegroundProcess(
        eventBuilder.layoutInspectorForegroundProcessBuilder
          .setPid(foregroundProcess.pid.toString())
          .setProcessName(foregroundProcess.processName)
          .build()
      ).build()
  }

  private fun setCommandHandler(command: Commands.Command.CommandType, block: () -> Unit) {
    transportService.setCommandHandler(command, object : CommandHandler(timer) {
      override fun handleCommand(command: Commands.Command, events: MutableList<Common.Event>) {
        block.invoke()
      }
    })
  }
}