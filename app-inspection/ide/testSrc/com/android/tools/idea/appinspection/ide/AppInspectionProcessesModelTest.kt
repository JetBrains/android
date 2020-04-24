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
package com.android.tools.idea.appinspection.ide

import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.appinspection.api.process.ProcessDescriptor
import com.android.tools.idea.appinspection.api.process.ProcessListener
import com.android.tools.idea.appinspection.ide.model.AppInspectionProcessModel
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
import com.intellij.util.concurrency.EdtExecutorService
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class AppInspectionProcessesModelTest {
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
  val grpcServerRule = FakeGrpcServer.createFakeGrpcServer("AppInspectionProcessesModelTest", transportService, transportService)!!

  @get:Rule
  val timeoutRule = Timeout(ASYNC_TIMEOUT_MS, TimeUnit.MILLISECONDS)

  init {
    transportService.setCommandHandler(Commands.Command.CommandType.ATTACH_AGENT, ATTACH_HANDLER)
  }

  @Test
  fun addsAndRemovesProcess_modelUpdatesProperly() {
    val executor = MoreExecutors.listeningDecorator(Executors.newScheduledThreadPool(1))
    val discoveryHost = AppInspectionTestUtils.createDiscoveryHost(executor, TransportClient(grpcServerRule.name))

    val addedLatch = CountDownLatch(1)
    val removedLatch = CountDownLatch(1)

    val model = AppInspectionProcessModel(discoveryHost) { listOf(FakeTransportService.FAKE_PROCESS.name) }
    model.addSelectedProcessListeners {
      if (model.processes.isEmpty()) {
        removedLatch.countDown()
      }
      else {
        addedLatch.countDown()
      }
    }

    // Attach to a fake process.
    transportService.addDevice(FakeTransportService.FAKE_DEVICE)
    transportService.addProcess(FakeTransportService.FAKE_DEVICE, FakeTransportService.FAKE_PROCESS)
    addedLatch.await()

    // Verify the added target.
    assertThat(model.processes.size).isEqualTo(1)
    with(model.processes.first()) {
      assertThat(this.model).isEqualTo(FakeTransportService.FAKE_DEVICE.model)
      assertThat(this.processName).isEqualTo(FakeTransportService.FAKE_PROCESS.name)
    }

    // Remove the fake process.
    transportService.addProcess(FakeTransportService.FAKE_DEVICE, FakeTransportService.FAKE_OFFLINE_PROCESS)
    removedLatch.await()

    // Verify the empty model list.
    assertThat(model.processes.size).isEqualTo(0)
  }

  @Test
  fun prioritizePreferredProcesses() {
    val executor = MoreExecutors.listeningDecorator(Executors.newScheduledThreadPool(1))
    val discoveryHost = AppInspectionTestUtils.createDiscoveryHost(executor, TransportClient(grpcServerRule.name))

    val addedLatch = CountDownLatch(2)

    val model = AppInspectionProcessModel(discoveryHost) { listOf("preferred") }

    discoveryHost.addProcessListener(EdtExecutorService.getInstance(), object : ProcessListener {
      override fun onProcessConnected(descriptor: ProcessDescriptor) {
        addedLatch.countDown()
      }

      override fun onProcessDisconnected(descriptor: ProcessDescriptor) {}
    })
    val nonPreferredProcess = FakeTransportService.FAKE_PROCESS.toBuilder().setName("non-preferred").setPid(100).build()
    val preferredProcess = FakeTransportService.FAKE_PROCESS.toBuilder().setName("preferred").setPid(101).build()

    // Launch non-preferred process
    transportService.addDevice(FakeTransportService.FAKE_DEVICE)
    transportService.addProcess(FakeTransportService.FAKE_DEVICE, nonPreferredProcess)

    // Launch preferred process
    transportService.addProcess(FakeTransportService.FAKE_DEVICE, preferredProcess)

    addedLatch.await()

    // Verify the preferred process is listed before the non-preferred process.
    assertThat(model.processes.size).isEqualTo(2)
    assertThat(model.selectedProcess!!.processName).isEqualTo("preferred")
  }

  @Test
  fun newProcessDoesNotCauseSelectionToChange() {
    val executor = MoreExecutors.listeningDecorator(Executors.newScheduledThreadPool(1))
    val discoveryHost = AppInspectionTestUtils.createDiscoveryHost(executor, TransportClient(grpcServerRule.name))

    val processReadyLatch = CountDownLatch(2)

    val model = AppInspectionProcessModel(discoveryHost) { listOf("A", "B") }
    discoveryHost.addProcessListener(EdtExecutorService.getInstance(), object : ProcessListener {
      override fun onProcessConnected(descriptor: ProcessDescriptor) {
        processReadyLatch.countDown()
      }

      override fun onProcessDisconnected(descriptor: ProcessDescriptor) {}
    })


    val processA = FakeTransportService.FAKE_PROCESS.toBuilder().setName("A").setPid(100).build()
    val processB = FakeTransportService.FAKE_PROCESS.toBuilder().setName("B").setPid(101).build()

    // Launch process A
    transportService.addDevice(FakeTransportService.FAKE_DEVICE)
    transportService.addProcess(FakeTransportService.FAKE_DEVICE, processA)

    // Launch process B
    transportService.addProcess(FakeTransportService.FAKE_DEVICE, processB)
    processReadyLatch.await()

    // Verify model's selection is set to the first process (A)
    assertThat(model.selectedProcess!!.processName).isEqualTo("A")
  }

  @Test
  fun noPreferredProcesses_noSelection() {
    val executor = MoreExecutors.listeningDecorator(Executors.newScheduledThreadPool(1))
    val discoveryHost = AppInspectionTestUtils.createDiscoveryHost(executor, TransportClient(grpcServerRule.name))

    val processReadyLatch = CountDownLatch(1)

    val model = AppInspectionProcessModel(discoveryHost) { emptyList() }
    discoveryHost.addProcessListener(EdtExecutorService.getInstance(), object : ProcessListener {
      override fun onProcessConnected(descriptor: ProcessDescriptor) {
        processReadyLatch.countDown()
      }

      override fun onProcessDisconnected(descriptor: ProcessDescriptor) {}
    })

    val process = FakeTransportService.FAKE_PROCESS.toBuilder().setName("A").setPid(100).build()

    // Launch process
    transportService.addDevice(FakeTransportService.FAKE_DEVICE)
    transportService.addProcess(FakeTransportService.FAKE_DEVICE, process)

    processReadyLatch.await()

    // Verify model's selection is set to null
    assertThat(model.selectedProcess).isNull()
    assertThat(model.processes).isNotEmpty()
  }

  @Test
  fun noInspectionTargetAvailable() {
    val executor = MoreExecutors.listeningDecorator(Executors.newScheduledThreadPool(1))
    val discoveryHost = AppInspectionTestUtils.createDiscoveryHost(executor, TransportClient(grpcServerRule.name))

    val processReadyLatch = CountDownLatch(1)
    val processRemovedLatch = CountDownLatch(1)

    val model = AppInspectionProcessModel(discoveryHost) { emptyList() }
    discoveryHost.addProcessListener(EdtExecutorService.getInstance(), object : ProcessListener {
      override fun onProcessConnected(descriptor: ProcessDescriptor) {
        processReadyLatch.countDown()
      }

      override fun onProcessDisconnected(descriptor: ProcessDescriptor) {
        processRemovedLatch.countDown()
      }
    })

    // Verify model's selection is null
    assertThat(model.selectedProcess).isNull()
    assertThat(model.processes).isEmpty()

    val process = FakeTransportService.FAKE_PROCESS.toBuilder().setName("A").setPid(100).build()
    val deadProcess = process.toBuilder().setState(Common.Process.State.DEAD).build()

    // Launch process
    transportService.addDevice(FakeTransportService.FAKE_DEVICE)
    transportService.addProcess(FakeTransportService.FAKE_DEVICE, process)
    processReadyLatch.await()

    // Remove the process
    transportService.addProcess(FakeTransportService.FAKE_DEVICE, deadProcess)
    processRemovedLatch.await()

    // Verify model's selection is null
    assertThat(model.selectedProcess).isNull()
    assertThat(model.processes).isEmpty()
  }
}