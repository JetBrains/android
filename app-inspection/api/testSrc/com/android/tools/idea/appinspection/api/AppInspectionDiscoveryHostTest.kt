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
package com.android.tools.idea.appinspection.api

import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.appinspection.test.ASYNC_TIMEOUT_MS
import com.android.tools.idea.appinspection.test.AppInspectionTestUtils
import com.android.tools.idea.transport.TransportClient
import com.android.tools.idea.transport.faketransport.FakeGrpcServer
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.idea.transport.faketransport.commands.CommandHandler
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Common
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit


class AppInspectionDiscoveryHostTest {
  private val timer = FakeTimer()
  private val transportService = FakeTransportService(timer, false)

  private val FAKE_PROCESS_DESCRIPTOR = TransportProcessDescriptor(
    Common.Stream.newBuilder()
      .setType(Common.Stream.Type.DEVICE)
      .setStreamId(FakeTransportService.FAKE_DEVICE_ID)
      .setDevice(FakeTransportService.FAKE_DEVICE)
      .build(),
    FakeTransportService.FAKE_PROCESS
  )

  private val ATTACH_HANDLER = object : CommandHandler(timer) {
    override fun handleCommand(command: Commands.Command, events: MutableList<Common.Event>) {
      events.add(
        Common.Event.newBuilder()
          .setKind(Common.Event.Kind.AGENT)
          .setPid(FakeTransportService.FAKE_PROCESS.pid)
          .setAgentData(Common.AgentData.newBuilder().setStatus(Common.AgentData.Status.ATTACHED).build())
          .build()
      )
    }
  }

  @get:Rule
  val grpcServerRule = FakeGrpcServer.createFakeGrpcServer("AppInspectionDiscoveryTest", transportService, transportService)!!

  @get:Rule
  val timeoutRule = Timeout(ASYNC_TIMEOUT_MS, TimeUnit.MILLISECONDS)

  init {
    transportService.setCommandHandler(Commands.Command.CommandType.ATTACH_AGENT, ATTACH_HANDLER)
  }

  private fun advanceTimer() {
    // Advance timer before each test so the transport pipeline does not consider new events of same timestamp as duplicate events.
    // The consequence of not doing this is transport pipeline will stop supplying process started or stopped events.
    timer.currentTimeNs += 1
  }

  private fun launchFakeProcess(discoveryHost: AppInspectionDiscoveryHost) {
    transportService.addDevice(FakeTransportService.FAKE_DEVICE)
    transportService.addProcess(FakeTransportService.FAKE_DEVICE, FakeTransportService.FAKE_PROCESS)
    discoveryHost.addLaunchedProcess(
      LaunchedProcessDescriptor(
        FakeTransportService.FAKE_DEVICE.manufacturer,
        FakeTransportService.FAKE_DEVICE.model,
        FakeTransportService.FAKE_PROCESS.name
      ),
      AppInspectionTestUtils.TestTransportJarCopier
    )
    advanceTimer()
  }

  private fun removeFakeProcess() {
    // Removes process from FakeTransportService's internal cache.
    transportService.removeProcess(FakeTransportService.FAKE_DEVICE, FakeTransportService.FAKE_PROCESS)
    // Despite the confusing name, this triggers a process end event.
    transportService.addProcess(FakeTransportService.FAKE_DEVICE, FakeTransportService.FAKE_OFFLINE_PROCESS)
    transportService.addDevice(FakeTransportService.FAKE_OFFLINE_DEVICE)
    advanceTimer()
  }

  @Test
  fun makeNewConnectionFiresListener() {
    val executor = MoreExecutors.listeningDecorator(Executors.newScheduledThreadPool(1))
    val discoveryHost = AppInspectionDiscoveryHost(executor, TransportClient(grpcServerRule.name))

    val latch = CountDownLatch(1)
    discoveryHost.addProcessListener(executor, object : AppInspectionDiscoveryHost.AppInspectionProcessListener {
      override fun onProcessConnected(descriptor: TransportProcessDescriptor) {
        latch.countDown()
      }

      override fun onProcessDisconnected(descriptor: TransportProcessDescriptor) {
      }
    })

    launchFakeProcess(discoveryHost)

    latch.await()
  }

  @Test
  fun addListenerReceivesExistingConnections() {
    val executor = MoreExecutors.listeningDecorator(Executors.newScheduledThreadPool(1))
    val discoveryHost = AppInspectionDiscoveryHost(executor, TransportClient(grpcServerRule.name))

    transportService.setCommandHandler(Commands.Command.CommandType.APP_INSPECTION, TestInspectorCommandHandler(timer))

    // Generate a new process.
    launchFakeProcess(discoveryHost)

    val latch = CountDownLatch(1)
    val processesList = mutableListOf<TransportProcessDescriptor>()
    discoveryHost.addProcessListener(executor, object : AppInspectionDiscoveryHost.AppInspectionProcessListener {
      override fun onProcessConnected(descriptor: TransportProcessDescriptor) {
        processesList.add(descriptor)
        latch.countDown()
      }

      override fun onProcessDisconnected(descriptor: TransportProcessDescriptor) {
      }
    })

    // Wait for discovery to notify us of existing connections
    latch.await()

    // Verify
    assertThat(processesList).containsExactly(FAKE_PROCESS_DESCRIPTOR)
  }

  @Test
  fun processDisconnectNotifiesListener() {
    // Setup
    val executor = MoreExecutors.listeningDecorator(Executors.newScheduledThreadPool(1))
    val discoveryHost = AppInspectionDiscoveryHost(executor, TransportClient(grpcServerRule.name))

    val processConnectLatch = CountDownLatch(1)
    val processDisconnectLatch = CountDownLatch(1)
    discoveryHost.addProcessListener(executor, object : AppInspectionDiscoveryHost.AppInspectionProcessListener {
      override fun onProcessConnected(descriptor: TransportProcessDescriptor) {
        processConnectLatch.countDown()
      }

      override fun onProcessDisconnected(descriptor: TransportProcessDescriptor) {
        processDisconnectLatch.countDown()
      }
    })

    // Wait for process to connect.
    launchFakeProcess(discoveryHost)
    processConnectLatch.await()

    // Wait for process to disconnect.
    removeFakeProcess()
    processDisconnectLatch.await()

    assertThat(discoveryHost.processData.processIdMap).doesNotContainKey(FakeTransportService.FAKE_PROCESS.pid)
  }

  @Test
  fun processReconnects() {
    // Setup
    val executor = MoreExecutors.listeningDecorator(Executors.newScheduledThreadPool(1))
    val discoveryHost = AppInspectionDiscoveryHost(executor, TransportClient(grpcServerRule.name))

    val firstProcessLatch = CountDownLatch(1)
    val secondProcessLatch = CountDownLatch(1)
    val processDisconnectLatch = CountDownLatch(1)
    discoveryHost.addProcessListener(executor, object : AppInspectionDiscoveryHost.AppInspectionProcessListener {
      override fun onProcessConnected(descriptor: TransportProcessDescriptor) {
        if (firstProcessLatch.count > 0) {
          firstProcessLatch.countDown()
        } else {
          secondProcessLatch.countDown()
        }
      }

      override fun onProcessDisconnected(descriptor: TransportProcessDescriptor) {
        processDisconnectLatch.countDown()
      }
    })

    // Wait for process to connect.
    launchFakeProcess(discoveryHost)
    firstProcessLatch.await()

    // Wait for process to disconnect.
    removeFakeProcess()
    processDisconnectLatch.await()

    // Wait for it to connect again.
    launchFakeProcess(discoveryHost)
    secondProcessLatch.await()
  }
}