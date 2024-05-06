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
import com.android.tools.idea.appinspection.api.process.ProcessListener
import com.android.tools.idea.appinspection.api.process.SimpleProcessListener
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.appinspection.internal.process.toDeviceDescriptor
import com.android.tools.idea.appinspection.test.AppInspectionServiceRule
import com.android.tools.idea.appinspection.test.TestAppInspectorCommandHandler
import com.android.tools.idea.transport.faketransport.FakeGrpcServer
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.idea.transport.faketransport.commands.CommandHandler
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Common
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

class AppInspectionProcessDiscoveryTest {
  private val timer = FakeTimer()
  private val transportService = FakeTransportService(timer, false)

  private val ATTACH_HANDLER =
    object : CommandHandler(timer) {
      override fun handleCommand(command: Commands.Command, events: MutableList<Common.Event>) {
        events.add(
          Common.Event.newBuilder()
            .setKind(Common.Event.Kind.AGENT)
            .setPid(FakeTransportService.FAKE_PROCESS.pid)
            .setAgentData(
              Common.AgentData.newBuilder().setStatus(Common.AgentData.Status.ATTACHED).build()
            )
            .build()
        )
      }
    }

  private val grpcServerRule =
    FakeGrpcServer.createFakeGrpcServer("AppInspectionDiscoveryTest", transportService)
  private val appInspectionRule = AppInspectionServiceRule(timer, transportService, grpcServerRule)

  @get:Rule val ruleChain = RuleChain.outerRule(grpcServerRule).around(appInspectionRule)!!

  init {
    transportService.setCommandHandler(Commands.Command.CommandType.ATTACH_AGENT, ATTACH_HANDLER)
  }

  private fun advanceTimer() {
    // Advance timer before each test so the transport pipeline does not consider new events of same
    // timestamp as duplicate events.
    // The consequence of not doing this is transport pipeline will stop supplying process started
    // or stopped events.
    timer.currentTimeNs += 1
  }

  private fun launchFakeProcess(
    device: Common.Device = FakeTransportService.FAKE_DEVICE,
    process: Common.Process = FakeTransportService.FAKE_PROCESS
  ) {
    transportService.addProcess(device, process)
    advanceTimer()
  }

  private fun removeFakeProcess() {
    // Removes process from FakeTransportService's internal cache.
    transportService.stopProcess(
      FakeTransportService.FAKE_DEVICE,
      FakeTransportService.FAKE_PROCESS
    )
    advanceTimer()
  }

  private fun launchFakeDevice(device: Common.Device = FakeTransportService.FAKE_DEVICE) {
    transportService.addDevice(device)
    advanceTimer()
  }

  private fun removeFakeDevice() {
    transportService.addDevice(FakeTransportService.FAKE_OFFLINE_DEVICE)
    advanceTimer()
  }

  @Test
  fun makeNewConnectionFiresListener() {
    val latch = CountDownLatch(1)
    appInspectionRule.addProcessListener(
      object : SimpleProcessListener() {
        override fun onProcessConnected(process: ProcessDescriptor) {
          latch.countDown()
        }

        override fun onProcessDisconnected(process: ProcessDescriptor) {}
      }
    )

    launchFakeDevice()
    launchFakeProcess()

    latch.await()
  }

  @Test
  fun addListenerReceivesExistingConnections() {
    transportService.setCommandHandler(
      Commands.Command.CommandType.APP_INSPECTION,
      TestAppInspectorCommandHandler(timer)
    )

    // Generate a new process.
    launchFakeDevice()
    launchFakeProcess()

    val latch = CountDownLatch(1)
    val processesList = mutableListOf<ProcessDescriptor>()
    appInspectionRule.addProcessListener(
      object : SimpleProcessListener() {
        override fun onProcessConnected(process: ProcessDescriptor) {
          processesList.add(process)
          latch.countDown()
        }

        override fun onProcessDisconnected(process: ProcessDescriptor) {}
      }
    )

    // Wait for discovery to notify us of existing connections
    latch.await()

    // Verify
    assertThat(processesList).hasSize(1)
    assertThat(processesList[0].device.manufacturer)
      .isEqualTo(FakeTransportService.FAKE_DEVICE.manufacturer)
    assertThat(processesList[0].device.model).isEqualTo(FakeTransportService.FAKE_DEVICE.model)
    assertThat(processesList[0].name).isEqualTo(FakeTransportService.FAKE_PROCESS.name)
  }

  @Test
  fun processDisconnectNotifiesListener() {
    val processConnectLatch = CountDownLatch(1)
    val processDisconnectLatch = CountDownLatch(1)
    appInspectionRule.addProcessListener(
      object : SimpleProcessListener() {
        override fun onProcessConnected(process: ProcessDescriptor) {
          processConnectLatch.countDown()
        }

        override fun onProcessDisconnected(process: ProcessDescriptor) {
          processDisconnectLatch.countDown()
        }
      }
    )

    // Wait for process to connect.
    launchFakeDevice()
    launchFakeProcess()
    processConnectLatch.await()

    // Wait for process to disconnect.
    removeFakeProcess()
    processDisconnectLatch.await()
  }

  @Test
  fun deviceDisconnectNotifiesListener() {
    val processConnectLatch = CountDownLatch(1)
    val processDisconnectLatch = CountDownLatch(1)
    appInspectionRule.addProcessListener(
      object : SimpleProcessListener() {
        override fun onProcessConnected(process: ProcessDescriptor) {
          processConnectLatch.countDown()
        }

        override fun onProcessDisconnected(process: ProcessDescriptor) {
          processDisconnectLatch.countDown()
        }
      }
    )

    // Wait for process to connect.
    launchFakeDevice()
    launchFakeProcess()
    processConnectLatch.await()

    // Device disconnecting takes the process down with it.
    removeFakeDevice()
    processDisconnectLatch.await()
  }

  @Test
  fun processReconnects() {
    val firstProcessLatch = CountDownLatch(1)
    val secondProcessLatch = CountDownLatch(1)
    val processDisconnectLatch = CountDownLatch(1)
    appInspectionRule.addProcessListener(
      object : SimpleProcessListener() {
        override fun onProcessConnected(process: ProcessDescriptor) {
          if (firstProcessLatch.count > 0) {
            firstProcessLatch.countDown()
          } else {
            secondProcessLatch.countDown()
          }
        }

        override fun onProcessDisconnected(process: ProcessDescriptor) {
          processDisconnectLatch.countDown()
        }
      }
    )

    // Wait for process to connect.
    launchFakeDevice()
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
    val latch = CountDownLatch(2)
    appInspectionRule.addProcessListener(
      object : SimpleProcessListener() {
        override fun onProcessConnected(process: ProcessDescriptor) {
          latch.countDown()
        }

        override fun onProcessDisconnected(process: ProcessDescriptor) {}
      }
    )

    // Launch process in stream 1
    val fakeDevice1 =
      FakeTransportService.FAKE_DEVICE.toBuilder()
        .setDeviceId(1)
        .setModel("fakeModel1")
        .setManufacturer("fakeMan2")
        .build()
    val fakeProcess1 = FakeTransportService.FAKE_PROCESS.toBuilder().setDeviceId(1).build()
    launchFakeDevice(fakeDevice1)
    launchFakeProcess(fakeDevice1, fakeProcess1)

    // Launch process with same pid in stream 2
    val fakeDevice2 =
      FakeTransportService.FAKE_DEVICE.toBuilder()
        .setDeviceId(2)
        .setModel("fakeModel2")
        .setManufacturer("fakeMan2")
        .build()
    val fakeProcess2 = FakeTransportService.FAKE_PROCESS.toBuilder().setDeviceId(2).build()
    launchFakeDevice(fakeDevice2)
    launchFakeProcess(fakeDevice2, fakeProcess2)

    latch.await()
  }

  @Test
  fun processesRunningOnTwoIdenticalDeviceModels() {
    val latch = CountDownLatch(2)
    appInspectionRule.addProcessListener(
      object : SimpleProcessListener() {
        override fun onProcessConnected(process: ProcessDescriptor) {
          latch.countDown()
        }

        override fun onProcessDisconnected(process: ProcessDescriptor) {}
      }
    )

    // Launch process in stream 1
    val fakeDevice1 =
      FakeTransportService.FAKE_DEVICE.toBuilder()
        .setDeviceId(1)
        .setModel("fakeModel")
        .setManufacturer("fakeMan")
        .setSerial("1")
        .build()
    val fakeProcess1 = FakeTransportService.FAKE_PROCESS.toBuilder().setDeviceId(1).build()
    launchFakeDevice(fakeDevice1)
    launchFakeProcess(fakeDevice1, fakeProcess1)

    // Launch process with same pid in stream 2
    val fakeDevice2 =
      FakeTransportService.FAKE_DEVICE.toBuilder()
        .setDeviceId(2)
        .setModel("fakeModel")
        .setManufacturer("fakeMan")
        .setSerial("2")
        .build()
    val fakeProcess2 = FakeTransportService.FAKE_PROCESS.toBuilder().setDeviceId(2).build()
    launchFakeDevice(fakeDevice2)
    launchFakeProcess(fakeDevice2, fakeProcess2)

    latch.await()
  }

  @Test
  fun discoveryFiltersProcessByDeviceApiLevel() {
    val latch = CountDownLatch(1)
    lateinit var processDescriptor: ProcessDescriptor
    appInspectionRule.addProcessListener(
      object : SimpleProcessListener() {
        override fun onProcessConnected(process: ProcessDescriptor) {
          processDescriptor = process
          latch.countDown()
        }

        override fun onProcessDisconnected(process: ProcessDescriptor) {}
      }
    )

    // Launch process on a device with Api Level < O
    val oldDevice =
      FakeTransportService.FAKE_DEVICE.toBuilder()
        .setDeviceId(1)
        .setModel("fakeModel")
        .setManufacturer("fakeMan")
        .setSerial("1")
        .setApiLevel(AndroidVersion.VersionCodes.N)
        .build()
    val process = FakeTransportService.FAKE_PROCESS.toBuilder().setDeviceId(1).build()
    launchFakeDevice(oldDevice)
    launchFakeProcess(oldDevice, process)

    // Launch process on another device with Api level >= O
    val newDevice =
      FakeTransportService.FAKE_DEVICE.toBuilder()
        .setDeviceId(2)
        .setModel("fakeModel")
        .setManufacturer("fakeMan")
        .setSerial("1")
        .setApiLevel(AndroidVersion.VersionCodes.O)
        .build()
    val newProcess = FakeTransportService.FAKE_PROCESS.toBuilder().setDeviceId(2).build()
    launchFakeDevice(newDevice)
    launchFakeProcess(newDevice, newProcess)

    // Verify discovery host has only notified about the process that ran on >= O device.
    latch.await()
    assertThat(processDescriptor.device.apiLevel >= AndroidVersion.VersionCodes.O)
  }

  // Test the scenario where discovery encounters a device it has discovered before.
  @Test
  fun discoveryIgnoresPastEventsFromReconnectedDevice() {
    val firstProcessReadyLatch = CountDownLatch(1)
    val secondProcessReadyLatch = CountDownLatch(1)
    val processDisconnectLatch = CountDownLatch(1)
    var firstProcessTimestamp: Long? = null
    var secondProcessTimestamp: Long? = null
    appInspectionRule.addProcessListener(
      object : SimpleProcessListener() {
        override fun onProcessConnected(process: ProcessDescriptor) {
          if (firstProcessReadyLatch.count > 0) {
            firstProcessTimestamp = timer.currentTimeNs
            firstProcessReadyLatch.countDown()
          } else {
            secondProcessTimestamp = timer.currentTimeNs
            secondProcessReadyLatch.countDown()
          }
        }

        override fun onProcessDisconnected(process: ProcessDescriptor) {
          processDisconnectLatch.countDown()
        }
      }
    )

    // Fake device connection.
    launchFakeDevice()

    // Fake process connection on the device.
    launchFakeProcess()
    firstProcessReadyLatch.await()

    // Fake process disconnect.
    removeFakeProcess()
    processDisconnectLatch.await()

    // Fake device disconnect.
    removeFakeDevice()

    // This test should not discover anything that happened prior to this timestamp.
    val deviceDisconnectTimestamp = timer.currentTimeNs

    // Fake device and process connection again.
    launchFakeDevice()
    launchFakeProcess()

    secondProcessReadyLatch.await()
    assertThat(firstProcessTimestamp!!).isLessThan(deviceDisconnectTimestamp)
    assertThat(deviceDisconnectTimestamp).isLessThan(secondProcessTimestamp!!)
  }

  // Test the scenario where discovery encounters a profileable-but-not-debuggable process.
  @Test
  fun discoveryIgnoresProfileableProcesses() {
    val latch = CountDownLatch(1)
    var reportedProcessCount = 0
    lateinit var processDescriptor: ProcessDescriptor
    appInspectionRule.addProcessListener(
      object : SimpleProcessListener() {
        override fun onProcessConnected(process: ProcessDescriptor) {
          reportedProcessCount++
          processDescriptor = process
          latch.countDown()
        }

        override fun onProcessDisconnected(process: ProcessDescriptor) {}
      }
    )

    launchFakeDevice()
    // FAKE_PROCESS is of Common.Process.ExposureLevel.DEBUGGABLE by default.
    val debuggableProcess = FakeTransportService.FAKE_PROCESS.toBuilder().setPid(100).build()
    launchFakeProcess(FakeTransportService.FAKE_DEVICE, debuggableProcess)
    val profileableProcess =
      FakeTransportService.FAKE_PROCESS.toBuilder()
        .setPid(200)
        .setExposureLevel(Common.Process.ExposureLevel.PROFILEABLE)
        .build()
    launchFakeProcess(FakeTransportService.FAKE_DEVICE, profileableProcess)

    // Verify discovery host has only notified about the process that's debuggable.
    latch.await()
    assertThat(reportedProcessCount).isEqualTo(1)
    assertThat(processDescriptor.pid).isEqualTo(100)
  }

  @Test
  fun discoverDevices() {
    val processConnectLatch = CountDownLatch(1)

    appInspectionRule.addProcessListener(
      object : SimpleProcessListener() {
        override fun onProcessConnected(process: ProcessDescriptor) {
          processConnectLatch.countDown()
        }

        override fun onProcessDisconnected(process: ProcessDescriptor) {}
      }
    )

    // Launch stream 1
    val fakeDevice1 =
      FakeTransportService.FAKE_DEVICE.toBuilder()
        .setDeviceId(1)
        .setModel("fakeModel1")
        .setManufacturer("fakeMan2")
        .build()
    launchFakeDevice(fakeDevice1)

    // Launch process with same pid in stream 2
    val fakeDevice2 =
      FakeTransportService.FAKE_DEVICE.toBuilder()
        .setDeviceId(2)
        .setModel("fakeModel2")
        .setManufacturer("fakeMan2")
        .build()
    val fakeProcess2 = FakeTransportService.FAKE_PROCESS.toBuilder().setDeviceId(2).build()
    launchFakeDevice(fakeDevice2)
    launchFakeProcess(fakeDevice2, fakeProcess2)

    processConnectLatch.await()

    assertThat(appInspectionRule.processDiscovery.devices.map { it.toString() })
      .containsExactly(
        fakeDevice1.toDeviceDescriptor().toString(),
        fakeDevice2.toDeviceDescriptor().toString()
      )
  }

  @Test
  fun addListenerWithFilter() =
    runBlocking<Unit> {
      val processConnectedDeferred = CompletableDeferred<String>()

      appInspectionRule.addProcessListener(
        object : ProcessListener {
          override val filter: (ProcessDescriptor) -> Boolean = { process ->
            process.name == "name2"
          }

          override fun onProcessConnected(process: ProcessDescriptor) {
            processConnectedDeferred.complete(process.name)
          }

          override fun onProcessDisconnected(process: ProcessDescriptor) {}
        }
      )

      // Launch stream 1
      val fakeDevice1 =
        FakeTransportService.FAKE_DEVICE.toBuilder()
          .setDeviceId(1)
          .setModel("fakeModel1")
          .setManufacturer("fakeMan2")
          .build()
      launchFakeDevice(fakeDevice1)
      launchFakeProcess(
        fakeDevice1,
        FakeTransportService.FAKE_PROCESS.toBuilder()
          .apply {
            deviceId = 1
            name = "name1"
          }
          .build()
      )

      // Launch process with same pid in stream 2
      val fakeDevice2 =
        FakeTransportService.FAKE_DEVICE.toBuilder()
          .setDeviceId(2)
          .setModel("fakeModel2")
          .setManufacturer("fakeMan2")
          .build()
      launchFakeDevice(fakeDevice2)
      launchFakeProcess(
        fakeDevice2,
        FakeTransportService.FAKE_PROCESS.toBuilder()
          .apply {
            name = "name2"
            deviceId = 2
          }
          .build()
      )

      assertThat(processConnectedDeferred.await()).isEqualTo("name2")
    }
}
