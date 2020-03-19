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

import com.android.sdklib.AndroidVersion
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

  private fun launchFakeProcess(
    device: Common.Device = FakeTransportService.FAKE_DEVICE,
    process: Common.Process = FakeTransportService.FAKE_PROCESS
  ) {
    transportService.addDevice(device)
    transportService.addProcess(device, process)
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
    val discoveryHost = AppInspectionTestUtils.createDiscoveryHost(executor, TransportClient(grpcServerRule.name))

    val latch = CountDownLatch(1)
    discoveryHost.addProcessListener(executor, object : AppInspectionDiscoveryHost.ProcessListener {
      override fun onProcessConnected(descriptor: ProcessDescriptor) {
        latch.countDown()
      }

      override fun onProcessDisconnected(descriptor: ProcessDescriptor) {
      }
    })

    launchFakeProcess()

    latch.await()
  }

  @Test
  fun addListenerReceivesExistingConnections() {
    val executor = MoreExecutors.listeningDecorator(Executors.newScheduledThreadPool(1))
    val discoveryHost = AppInspectionTestUtils.createDiscoveryHost(executor, TransportClient(grpcServerRule.name))

    transportService.setCommandHandler(Commands.Command.CommandType.APP_INSPECTION, TestInspectorCommandHandler(timer))

    // Generate a new process.
    launchFakeProcess()

    val latch = CountDownLatch(1)
    val processesList = mutableListOf<ProcessDescriptor>()
    discoveryHost.addProcessListener(executor, object : AppInspectionDiscoveryHost.ProcessListener {
      override fun onProcessConnected(descriptor: ProcessDescriptor) {
        processesList.add(descriptor)
        latch.countDown()
      }

      override fun onProcessDisconnected(descriptor: ProcessDescriptor) {
      }
    })

    // Wait for discovery to notify us of existing connections
    latch.await()

    // Verify
    assertThat(processesList).hasSize(1)
    assertThat(processesList[0].manufacturer).isEqualTo(FakeTransportService.FAKE_DEVICE.manufacturer)
    assertThat(processesList[0].model).isEqualTo(FakeTransportService.FAKE_DEVICE.model)
    assertThat(processesList[0].processName).isEqualTo(FakeTransportService.FAKE_PROCESS.name)
  }

  @Test
  fun processDisconnectNotifiesListener() {
    // Setup
    val executor = MoreExecutors.listeningDecorator(Executors.newScheduledThreadPool(1))
    val discoveryHost = AppInspectionTestUtils.createDiscoveryHost(executor, TransportClient(grpcServerRule.name))

    val processConnectLatch = CountDownLatch(1)
    val processDisconnectLatch = CountDownLatch(1)
    discoveryHost.addProcessListener(executor, object : AppInspectionDiscoveryHost.ProcessListener {
      override fun onProcessConnected(descriptor: ProcessDescriptor) {
        processConnectLatch.countDown()
      }

      override fun onProcessDisconnected(descriptor: ProcessDescriptor) {
        processDisconnectLatch.countDown()
      }
    })

    // Wait for process to connect.
    launchFakeProcess()
    processConnectLatch.await()

    // Wait for process to disconnect.
    removeFakeProcess()
    processDisconnectLatch.await()
  }

  @Test
  fun processReconnects() {
    // Setup
    val executor = MoreExecutors.listeningDecorator(Executors.newScheduledThreadPool(1))
    val discoveryHost = AppInspectionTestUtils.createDiscoveryHost(executor, TransportClient(grpcServerRule.name))

    val firstProcessLatch = CountDownLatch(1)
    val secondProcessLatch = CountDownLatch(1)
    val processDisconnectLatch = CountDownLatch(1)
    discoveryHost.addProcessListener(executor, object : AppInspectionDiscoveryHost.ProcessListener {
      override fun onProcessConnected(descriptor: ProcessDescriptor) {
        if (firstProcessLatch.count > 0) {
          firstProcessLatch.countDown()
        } else {
          secondProcessLatch.countDown()
        }
      }

      override fun onProcessDisconnected(descriptor: ProcessDescriptor) {
        processDisconnectLatch.countDown()
      }
    })

    // Wait for process to connect.
    launchFakeProcess()
    firstProcessLatch.await()

    // Wait for process to disconnect.
    removeFakeProcess()
    processDisconnectLatch.await()

    // Wait for it to connect again.
    launchFakeProcess()
    secondProcessLatch.await()
  }

  @Test
  fun twoProcessWithSamePidFromDifferentStream() {
    // Setup
    val executor = MoreExecutors.listeningDecorator(Executors.newScheduledThreadPool(1))
    val discoveryHost = AppInspectionTestUtils.createDiscoveryHost(executor, TransportClient(grpcServerRule.name))

    val latch = CountDownLatch(2)
    discoveryHost.addProcessListener(executor, object : AppInspectionDiscoveryHost.ProcessListener {
      override fun onProcessConnected(descriptor: ProcessDescriptor) {
        latch.countDown()
      }

      override fun onProcessDisconnected(descriptor: ProcessDescriptor) {
      }
    })

    // Launch process in stream 1
    val fakeDevice1 = FakeTransportService.FAKE_DEVICE.toBuilder().setDeviceId(1).setModel("fakeModel1").setManufacturer("fakeMan2").build()
    val fakeProcess1 = FakeTransportService.FAKE_PROCESS.toBuilder().setDeviceId(1).build()
    launchFakeProcess(fakeDevice1, fakeProcess1)

    // Launch process with same pid in stream 2
    val fakeDevice2 = FakeTransportService.FAKE_DEVICE.toBuilder().setDeviceId(2).setModel("fakeModel2").setManufacturer("fakeMan2").build()
    val fakeProcess2 = FakeTransportService.FAKE_PROCESS.toBuilder().setDeviceId(2).build()
    launchFakeProcess(fakeDevice2, fakeProcess2)

    latch.await()
  }

  @Test
  fun processesRunningOnTwoIdenticalDeviceModels() {
    // Setup
    val executor = MoreExecutors.listeningDecorator(Executors.newScheduledThreadPool(1))
    val discoveryHost = AppInspectionTestUtils.createDiscoveryHost(executor, TransportClient(grpcServerRule.name))

    val latch = CountDownLatch(2)
    discoveryHost.addProcessListener(executor, object : AppInspectionDiscoveryHost.ProcessListener {
      override fun onProcessConnected(descriptor: ProcessDescriptor) {
        latch.countDown()
      }

      override fun onProcessDisconnected(descriptor: ProcessDescriptor) {
      }
    })

    // Launch process in stream 1
    val fakeDevice1 =
      FakeTransportService.FAKE_DEVICE.toBuilder().setDeviceId(1).setModel("fakeModel").setManufacturer("fakeMan").setSerial("1").build()
    val fakeProcess1 = FakeTransportService.FAKE_PROCESS.toBuilder().setDeviceId(1).build()
    launchFakeProcess(fakeDevice1, fakeProcess1)

    // Launch process with same pid in stream 2
    val fakeDevice2 =
      FakeTransportService.FAKE_DEVICE.toBuilder().setDeviceId(2).setModel("fakeModel").setManufacturer("fakeMan").setSerial("2").build()
    val fakeProcess2 = FakeTransportService.FAKE_PROCESS.toBuilder().setDeviceId(2).build()
    launchFakeProcess(fakeDevice2, fakeProcess2)

    latch.await()
  }


  @Test
  fun discoveryFiltersProcessByDeviceApiLevel() {
    // Setup
    val executor = MoreExecutors.listeningDecorator(Executors.newScheduledThreadPool(1))
    val discoveryHost = AppInspectionTestUtils.createDiscoveryHost(executor, TransportClient(grpcServerRule.name))

    val latch = CountDownLatch(1)
    lateinit var processDescriptor: ProcessDescriptor
    discoveryHost.addProcessListener(executor, object : AppInspectionDiscoveryHost.ProcessListener {
      override fun onProcessConnected(descriptor: ProcessDescriptor) {
        processDescriptor = descriptor
        latch.countDown()
      }

      override fun onProcessDisconnected(descriptor: ProcessDescriptor) {
      }
    })

    // Launch process on a device with Api Level < O
    val oldDevice =
      FakeTransportService.FAKE_DEVICE.toBuilder()
        .setDeviceId(1)
        .setModel("fakeModel")
        .setManufacturer("fakeMan")
        .setSerial("1")
        .setApiLevel(AndroidVersion.VersionCodes.N)
        .build()
    val process = FakeTransportService.FAKE_PROCESS.toBuilder()
      .setDeviceId(1)
      .build()
    launchFakeProcess(oldDevice, process)


    // Launch process on another device with Api level >= O
    val newDevice = FakeTransportService.FAKE_DEVICE.toBuilder()
      .setDeviceId(2)
      .setModel("fakeModel")
      .setManufacturer("fakeMan")
      .setSerial("1")
      .setApiLevel(AndroidVersion.VersionCodes.O)
      .build()
    val newProcess = FakeTransportService.FAKE_PROCESS.toBuilder()
      .setDeviceId(2)
      .build()
    launchFakeProcess(newDevice, newProcess)

    // Verify discovery host has only notified about the process that ran on >= O device.
    latch.await()
    assertThat(processDescriptor.stream.device.apiLevel >= AndroidVersion.VersionCodes.O)
  }
}