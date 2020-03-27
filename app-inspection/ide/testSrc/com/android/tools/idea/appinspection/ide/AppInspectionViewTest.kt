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

import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.adtui.stdui.CommonTabbedPane
import com.android.tools.idea.appinspection.api.TestInspectorCommandHandler
import com.android.tools.idea.appinspection.ide.ui.AppInspectionProcessesComboBox
import com.android.tools.idea.appinspection.ide.ui.AppInspectionView
import com.android.tools.idea.appinspection.inspector.ide.AppInspectorTabProvider
import com.android.tools.idea.appinspection.test.AppInspectionServiceRule
import com.android.tools.idea.appinspection.test.AppInspectionTestUtils
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.transport.TransportClient
import com.android.tools.idea.transport.faketransport.FakeGrpcServer
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.idea.transport.faketransport.commands.CommandHandler
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Common
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.testFramework.EdtRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.awt.event.ContainerEvent
import java.awt.event.ContainerListener
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

class TestAppInspectorTabProvider1 : AppInspectorTabProvider by StubTestAppInspectorTabProvider()
class TestAppInspectorTabProvider2 : AppInspectorTabProvider by StubTestAppInspectorTabProvider()

class AppInspectionViewTest {
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

  private val grpcServerRule = FakeGrpcServer.createFakeGrpcServer("AppInspectionViewTest", transportService, transportService)!!
  private val appInspectionServiceRule = AppInspectionServiceRule(timer, transportService, grpcServerRule)
  private val projectRule = AndroidProjectRule.inMemory().initAndroid(false)
  private val api29Device = FakeTransportService.FAKE_DEVICE.toBuilder().setApiLevel(29).build()

  @get:Rule
  val ruleChain = RuleChain.outerRule(grpcServerRule).around(appInspectionServiceRule)!!.around(projectRule).around(EdtRule())!!

  @Before
  fun setup() {
    transportService.setCommandHandler(Commands.Command.CommandType.ATTACH_AGENT, ATTACH_HANDLER)
    transportService.setCommandHandler(Commands.Command.CommandType.APP_INSPECTION, TestInspectorCommandHandler(timer))
  }

  @Test
  fun selectProcessInAppInspectionView_addsTwoTabs() {
    val executor = MoreExecutors.listeningDecorator(Executors.newScheduledThreadPool(1))
    val discoveryHost = AppInspectionTestUtils.createDiscoveryHost(executor, TransportClient(grpcServerRule.name))

    val inspectionView = AppInspectionView(projectRule.project, discoveryHost)
    val newProcessLatch = CountDownLatch(1)
    val tabAddedLatch = CountDownLatch(2)
    discoveryHost.discovery.addTargetListener(appInspectionServiceRule.executorService) {
      newProcessLatch.countDown()
    }
    val tabbedPane = TreeWalker(inspectionView.component).descendants().filterIsInstance<CommonTabbedPane>().first()
    tabbedPane.addContainerListener(object : ContainerListener {
      override fun componentAdded(e: ContainerEvent) {

        tabAddedLatch.countDown()
      }

      override fun componentRemoved(e: ContainerEvent?) {}
    })
    // Attach to a fake process.
    transportService.addDevice(api29Device)
    transportService.addProcess(api29Device, FakeTransportService.FAKE_PROCESS)
    newProcessLatch.await()
    tabAddedLatch.await()
  }

  @Test
  fun disposeInspectorWhenSelectionChanges() {
    val executor = MoreExecutors.listeningDecorator(Executors.newScheduledThreadPool(1))
    val discoveryHost = AppInspectionTestUtils.createDiscoveryHost(executor, TransportClient(grpcServerRule.name))

    val inspectionView = AppInspectionView(projectRule.project, discoveryHost)
    val tabAddedLatch = CountDownLatch(2)
    val tabbedPane = TreeWalker(inspectionView.component).descendants().filterIsInstance<CommonTabbedPane>().first()
    tabbedPane.addContainerListener(object : ContainerListener {
      override fun componentAdded(e: ContainerEvent) {
        tabAddedLatch.countDown()
      }
      override fun componentRemoved(e: ContainerEvent?) {}
    })

    // Launch two processes and wait for them to show up in combobox
    val fakeDevice = api29Device.toBuilder().setDeviceId(1).setModel("fakeModel").setManufacturer("fakeMan").setSerial("1").build()
    val fakeProcess1 = FakeTransportService.FAKE_PROCESS.toBuilder().setPid(1).setDeviceId(1).build()
    val fakeProcess2 = FakeTransportService.FAKE_PROCESS.toBuilder().setPid(2).setDeviceId(1).build()
    transportService.addDevice(fakeDevice)
    transportService.addProcess(fakeDevice, fakeProcess1)
    transportService.addProcess(fakeDevice, fakeProcess2)
    tabAddedLatch.await()

    // Change combobox selection and check to see if a dispose command was sent out.
    val inspectorDisposedLatch = CountDownLatch(1)
    transportService.setCommandHandler(Commands.Command.CommandType.APP_INSPECTION, object : CommandHandler(timer) {
      override fun handleCommand(command: Commands.Command, events: MutableList<Common.Event>) {
        if (command.appInspectionCommand.hasDisposeInspectorCommand()) {
          inspectorDisposedLatch.countDown()
        }
      }
    })
    val comboBox = TreeWalker(inspectionView.component).descendants().filterIsInstance<AppInspectionProcessesComboBox>().first()
    comboBox.selectedIndex = (comboBox.selectedIndex + 1) % comboBox.itemCount

    inspectorDisposedLatch.await()
  }
}