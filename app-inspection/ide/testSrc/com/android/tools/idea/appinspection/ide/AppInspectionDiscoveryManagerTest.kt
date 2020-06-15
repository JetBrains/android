package com.android.tools.idea.appinspection.ide

/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.ddmlib.IDevice
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.appinspection.test.ASYNC_TIMEOUT_MS
import com.android.tools.idea.appinspection.test.AppInspectionServiceRule
import com.android.tools.idea.transport.faketransport.FakeGrpcServer
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.idea.transport.faketransport.FakeTransportService.FAKE_DEVICE
import com.android.tools.idea.transport.faketransport.FakeTransportService.FAKE_PROCESS
import com.android.tools.idea.transport.faketransport.commands.CommandHandler
import com.android.tools.idea.transport.poller.TransportEventListener
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Common
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.util.messages.MessageBus
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.Timeout
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class AppInspectionDiscoveryManagerTest {
  private val timer = FakeTimer()
  private val transportService = FakeTransportService(timer, false)

  @get:Rule
  val timeoutRule = Timeout(ASYNC_TIMEOUT_MS, TimeUnit.MILLISECONDS)

  private val grpcServerRule = FakeGrpcServer.createFakeGrpcServer("AppInspectionDiscoveryTest", transportService, transportService)!!

  private val appInspectionRule = AppInspectionServiceRule(timer, transportService, grpcServerRule)

  @get:Rule
  val ruleChain = RuleChain.outerRule(grpcServerRule).around(appInspectionRule)!!

  private val attachHandler = object : CommandHandler(timer) {
    override fun handleCommand(command: Commands.Command, events: MutableList<Common.Event>) {
      events.add(
        Common.Event.newBuilder()
          .setKind(Common.Event.Kind.AGENT)
          .setPid(FAKE_PROCESS.pid)
          .setAgentData(Common.AgentData.newBuilder().setStatus(Common.AgentData.Status.ATTACHED).build())
          .build()
      )
    }
  }

  private val mockBus = mock(MessageBus::class.java)

  private val mockDevice = mock(IDevice::class.java)

  init {
    transportService.setCommandHandler(Commands.Command.CommandType.ATTACH_AGENT, attachHandler)
    `when`(mockDevice.serialNumber).thenReturn("mock")
  }

  @Test
  fun establishNewConnection() {
    val manager = AppInspectionDiscoveryManager(
      appInspectionRule.client,
      MoreExecutors.directExecutor(),
      appInspectionRule.poller,
      { mockDevice },
      mockBus
    )

    // add listener so we can wait until target is ready before asserting
    val targetReadyLatch = CountDownLatch(1)
    manager.discoveryHost.discovery.addTargetListener(appInspectionRule.executorService) {
      targetReadyLatch.countDown()
    }

    // add fake device and process
    transportService.addDevice(FAKE_DEVICE)
    transportService.addProcess(FAKE_DEVICE, FAKE_PROCESS)

    // wait and assert
    targetReadyLatch.await()
    assertThat(manager.processIdMap).hasSize(1)
    assertThat(manager.streamIdMap).hasSize(1)
  }

  @Test
  fun cleansUpStreamsAndProcesses() {
    // setup
    val manager = AppInspectionDiscoveryManager(
      appInspectionRule.client,
      MoreExecutors.directExecutor(),
      appInspectionRule.poller,
      { mockDevice },
      mockBus
    )

    val targetReadyLatch = CountDownLatch(1)
    manager.discoveryHost.discovery.addTargetListener(appInspectionRule.executorService) {
      targetReadyLatch.countDown()
    }

    transportService.addDevice(FAKE_DEVICE)
    transportService.addProcess(FAKE_DEVICE, FAKE_PROCESS)

    // wait for stream and process to be set up
    targetReadyLatch.await()
    assertThat(manager.streamIdMap).hasSize(1)
    assertThat(manager.processIdMap).hasSize(1)

    // add listener so we can wait for process to end before asserting
    val streamEndedLatch = CountDownLatch(1)
    appInspectionRule.poller.registerListener(
      TransportEventListener(
        Common.Event.Kind.STREAM,
        appInspectionRule.executorService,
        { !it.process.hasProcessStarted() }) {
        streamEndedLatch.countDown()
        true
      })

    // send process ended signal
    transportService.addEventToStream(
      FAKE_DEVICE.deviceId,
      Common.Event.newBuilder().setKind(Common.Event.Kind.PROCESS)
        .setGroupId(FAKE_PROCESS.pid.toLong())
        .setProcess(
          Common.ProcessData.getDefaultInstance()
        ).build()
    )

    // send stream ended signal
    transportService.addEventToStream(
      0, Common.Event.newBuilder().setKind(Common.Event.Kind.STREAM)
        .setGroupId(FAKE_DEVICE.deviceId)
        .setStream(
          Common.StreamData.getDefaultInstance()
        ).build()
    )

    // wait and verify
    streamEndedLatch.await()
    assertThat(manager.streamIdMap).isEmpty()
    assertThat(manager.processIdMap).isEmpty()
  }
}