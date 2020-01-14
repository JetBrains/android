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
package com.android.tools.idea.appinspection.api

import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.appinspection.test.ASYNC_TIMEOUT_MS
import com.android.tools.idea.appinspection.test.AppInspectionServiceRule
import com.android.tools.idea.transport.TransportClient
import com.android.tools.idea.transport.faketransport.FakeGrpcServer
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.idea.transport.faketransport.commands.CommandHandler
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Common
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class AppInspectionDiscoveryTest {
  private val timer = FakeTimer()
  private val transportService = FakeTransportService(timer)

  private val FAKE_PROCESS = ProcessDescriptor(
    Common.Stream.newBuilder().setType(Common.Stream.Type.DEVICE).setStreamId(0).setDevice(FakeTransportService.FAKE_DEVICE).build(),
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

  @Test
  fun makeNewConnectionFiresListener() {
    val executor = MoreExecutors.listeningDecorator(Executors.newScheduledThreadPool(1))
    val discoveryHost = AppInspectionDiscoveryHost(executor, TransportClient(grpcServerRule.name))

    val latch = CountDownLatch(1)
    discoveryHost.discovery.addProcessListener(object : AppInspectionProcessListener {
      override fun onProcessConnect(processDescriptor: ProcessDescriptor) {
        latch.countDown()
      }

      override fun onProcessDisconnect(processDescriptor: ProcessDescriptor) {
      }
    }, executor)

    discoveryHost.discovery.addProcess(FAKE_PROCESS)

    latch.await()
  }

  @Test
  fun connectionIsCached() {
    val executor = MoreExecutors.listeningDecorator(Executors.newScheduledThreadPool(1))
    val discoveryHost = AppInspectionDiscoveryHost(executor, TransportClient(grpcServerRule.name))

    transportService.setCommandHandler(Commands.Command.CommandType.APP_INSPECTION, TestInspectorCommandHandler(timer))

    // Attach to the process.
    val target1 = discoveryHost.discovery.addProcess(FAKE_PROCESS)

    // Attach to the same process again.
    val target2 = discoveryHost.discovery.addProcess(FAKE_PROCESS)

    assertThat(target1).isSameAs(target2)
  }

  @Test
  fun addListenerReceivesExistingConnections() {
    val executor = MoreExecutors.listeningDecorator(Executors.newScheduledThreadPool(1))
    val discoveryHost = AppInspectionDiscoveryHost(executor, TransportClient(grpcServerRule.name))

    transportService.setCommandHandler(Commands.Command.CommandType.APP_INSPECTION, TestInspectorCommandHandler(timer))

    // Attach to a new process.
    discoveryHost.discovery.addProcess(FAKE_PROCESS)

    val latch = CountDownLatch(1)
    val processesList = mutableListOf<ProcessDescriptor>()
    discoveryHost.discovery.addProcessListener(object : AppInspectionProcessListener {
      override fun onProcessConnect(processDescriptor: ProcessDescriptor) {
        processesList.add(processDescriptor)
        latch.countDown()
      }

      override fun onProcessDisconnect(processDescriptor: ProcessDescriptor) {
      }
    }, executor)

    // Wait for discovery to notify us of existing connections
    latch.await()

    // Verify
    assertThat(processesList).containsExactly(FAKE_PROCESS)
  }

  @Test
  fun removeProcessUpdatesDiscoveryInternalCache() {
    val executor = MoreExecutors.listeningDecorator(Executors.newScheduledThreadPool(1))
    val discoveryHost = AppInspectionDiscoveryHost(executor, TransportClient(grpcServerRule.name))

    transportService.setCommandHandler(Commands.Command.CommandType.APP_INSPECTION, TestInspectorCommandHandler(timer))

    discoveryHost.discovery.addProcess(FAKE_PROCESS)

    assertThat(discoveryHost.discovery.processesForTesting).hasSize(1)

    discoveryHost.discovery.removeProcess(FAKE_PROCESS)

    assertThat(discoveryHost.discovery.processesForTesting).isEmpty()
  }

  @Ignore
  @Test
  fun attachMultipleTimesToSameProcess() {
    // Setup
    val executor = MoreExecutors.listeningDecorator(Executors.newScheduledThreadPool(1))
    val discoveryHost = AppInspectionDiscoveryHost(executor, TransportClient(grpcServerRule.name))

    transportService.setCommandHandler(Commands.Command.CommandType.APP_INSPECTION, TestInspectorCommandHandler(timer))

    // Add a fake process to transport and wait for discovery to be aware of it.
    discoveryHost.discovery.addProcess(FAKE_PROCESS)

    val processReadyLatch = CountDownLatch(1)
    var descriptor: ProcessDescriptor? = null
    discoveryHost.discovery.addProcessListener(object : AppInspectionProcessListener {
      override fun onProcessConnect(processDescriptor: ProcessDescriptor) {
        descriptor = processDescriptor
        processReadyLatch.countDown()
      }

      override fun onProcessDisconnect(processDescriptor: ProcessDescriptor) {
      }
    }, executor)

    processReadyLatch.await()

    // Attach to the fake process on 2 times, and check that they yield the same result due to caching.
    val targetFuture1 = discoveryHost.discovery.attachToProcess(
      descriptor!!, AppInspectionServiceRule.TestTransportFileCopier()
    )

    val targetFuture2 = discoveryHost.discovery.attachToProcess(
      descriptor!!, AppInspectionServiceRule.TestTransportFileCopier()
    )

    assertThat(targetFuture1).isSameAs(targetFuture2)
  }

  @Test
  fun processDisconnectNotifies() {
    // Setup
    val executor = MoreExecutors.listeningDecorator(Executors.newScheduledThreadPool(1))
    val discoveryHost = AppInspectionDiscoveryHost(executor, TransportClient(grpcServerRule.name))

    val processConnectLatch = CountDownLatch(1)
    val processDisconnectLatch = CountDownLatch(1)
    discoveryHost.discovery.addProcessListener(object : AppInspectionProcessListener {
      override fun onProcessConnect(processDescriptor: ProcessDescriptor) {
        processConnectLatch.countDown()
      }

      override fun onProcessDisconnect(processDescriptor: ProcessDescriptor) {
        processDisconnectLatch.countDown()
      }
    }, executor)

    // Wait for process to connect.
    discoveryHost.discovery.addProcess(FAKE_PROCESS)
    processConnectLatch.await()

    // Wait for process to disconnect.
    discoveryHost.discovery.removeProcess(FAKE_PROCESS)
    processDisconnectLatch.await()
  }
}