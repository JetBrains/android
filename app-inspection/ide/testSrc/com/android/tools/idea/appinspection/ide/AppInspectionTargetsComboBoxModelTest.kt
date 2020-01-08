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
import com.android.tools.idea.appinspection.api.AppInspectionDiscoveryHost
import com.android.tools.idea.appinspection.api.ProcessDescriptor
import com.android.tools.idea.appinspection.api.TestInspectorCommandHandler
import com.android.tools.idea.appinspection.ide.model.AppInspectionTargetsComboBoxModel
import com.android.tools.idea.appinspection.test.ASYNC_TIMEOUT_MS
import com.android.tools.idea.transport.TransportClient
import com.android.tools.idea.transport.faketransport.FakeGrpcServer
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.idea.transport.faketransport.commands.CommandHandler
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Common
import com.google.common.truth.Truth
import com.google.common.util.concurrent.MoreExecutors
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener

class AppInspectionTargetsComboBoxModelTest {

  companion object {
    private val DEAD_FAKE_PROCESS = FakeTransportService.FAKE_PROCESS.toBuilder().setState(Common.Process.State.DEAD).build()
  }

  private val timer = FakeTimer()
  private val transportService = FakeTransportService(timer)

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
  val grpcServerRule = FakeGrpcServer.createFakeGrpcServer("AppInspectionTargetsComboBoxModelTest", transportService, transportService)!!

  @get:Rule
  val timeoutRule = Timeout(ASYNC_TIMEOUT_MS, TimeUnit.MILLISECONDS)

  init {
    transportService.setCommandHandler(Commands.Command.CommandType.ATTACH_AGENT, ATTACH_HANDLER)
  }

  @Test
  fun contentUpdatedProperlyAfterAppInspectionTargetAddedAndRemoved() {
    val executor = MoreExecutors.listeningDecorator(Executors.newScheduledThreadPool(1))
    val discoveryHost = AppInspectionDiscoveryHost(executor, TransportClient(grpcServerRule.name))

    transportService.setCommandHandler(Commands.Command.CommandType.APP_INSPECTION, TestInspectorCommandHandler(timer))
    val addedLatch = CountDownLatch(1)
    val removedLatch = CountDownLatch(1)
    val model = AppInspectionTargetsComboBoxModel.newInstance(discoveryHost.discovery)
    model.addListDataListener(object : ListDataListener {
      override fun contentsChanged(e: ListDataEvent?) {}

      override fun intervalRemoved(e: ListDataEvent?) {
        removedLatch.countDown()
      }

      override fun intervalAdded(e: ListDataEvent?) {
        addedLatch.countDown()
      }
    })

    // Attach to a fake process.
    transportService.addProcess(FakeTransportService.FAKE_DEVICE, FakeTransportService.FAKE_PROCESS)
    addedLatch.await()

    // Verify the added target.
    Truth.assertThat(model.size).isEqualTo(1)
    Truth.assertThat(model.getElementAt(0)).isEqualTo(
      ProcessDescriptor(
        Common.Stream.newBuilder().setDevice(FakeTransportService.FAKE_DEVICE)
          .setType(Common.Stream.Type.DEVICE).setStreamId(FakeTransportService.FAKE_DEVICE.deviceId).build(),
        FakeTransportService.FAKE_PROCESS
      )
    )

    // Remove the fake process.
    transportService.addProcess(FakeTransportService.FAKE_DEVICE, DEAD_FAKE_PROCESS)
    removedLatch.await()

    // Verify the empty model list.
    Truth.assertThat(model.size).isEqualTo(0)
  }
}