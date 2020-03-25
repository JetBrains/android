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

import com.android.testutils.MockitoKt.any
import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.app.inspection.AppInspection
import com.android.tools.idea.appinspection.api.TestInspectorCommandHandler
import com.android.tools.idea.appinspection.ide.ui.AppInspectionNotificationFactory
import com.android.tools.idea.appinspection.ide.ui.AppInspectionProcessesComboBox
import com.android.tools.idea.appinspection.ide.ui.AppInspectionView
import com.android.tools.idea.appinspection.inspector.ide.AppInspectionCallbacks
import com.android.tools.idea.appinspection.inspector.ide.AppInspectorTabProvider
import com.android.tools.idea.appinspection.test.AppInspectionServiceRule
import com.android.tools.idea.appinspection.test.AppInspectionTestUtils
import com.android.tools.idea.appinspection.test.INSPECTOR_ID
import com.android.tools.idea.appinspection.test.INSPECTOR_ID_2
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.transport.TransportClient
import com.android.tools.idea.transport.faketransport.FakeGrpcServer
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.idea.transport.faketransport.commands.CommandHandler
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Common
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.notification.Notification
import com.intellij.notification.NotificationListener
import com.intellij.notification.NotificationType
import com.intellij.testFramework.EdtRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.timeout
import org.mockito.Mockito.verify
import java.awt.event.ContainerAdapter
import java.awt.event.ContainerEvent
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

class TestAppInspectorTabProvider1 : AppInspectorTabProvider by StubTestAppInspectorTabProvider(INSPECTOR_ID)
class TestAppInspectorTabProvider2 : AppInspectorTabProvider by StubTestAppInspectorTabProvider(INSPECTOR_ID_2)

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

  private val appInspectionCallbacks = object : AppInspectionCallbacks {
    override fun showToolWindow(callback: () -> Unit) { }
  }

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

    val inspectionView = AppInspectionView(projectRule.project, discoveryHost, appInspectionCallbacks,
                                           { listOf(FakeTransportService.FAKE_PROCESS_NAME) },
                                           mock(AppInspectionNotificationFactory::class.java))
    val newProcessLatch = CountDownLatch(1)
    val tabAddedLatch = CountDownLatch(2)
    discoveryHost.discovery.addTargetListener(appInspectionServiceRule.executorService) {
      newProcessLatch.countDown()
    }
    inspectionView.inspectorTabs.addContainerListener(object : ContainerAdapter() {
      override fun componentAdded(e: ContainerEvent) = tabAddedLatch.countDown()
    })
    // Attach to a fake process.
    transportService.addDevice(FakeTransportService.FAKE_DEVICE)
    transportService.addProcess(FakeTransportService.FAKE_DEVICE, FakeTransportService.FAKE_PROCESS)
    newProcessLatch.await()
    tabAddedLatch.await()
  }

  @Test
  fun disposeInspectorWhenSelectionChanges() {
    val executor = MoreExecutors.listeningDecorator(Executors.newScheduledThreadPool(1))
    val discoveryHost = AppInspectionTestUtils.createDiscoveryHost(executor, TransportClient(grpcServerRule.name))

    val inspectionView = AppInspectionView(projectRule.project, discoveryHost, appInspectionCallbacks,
                                           { listOf(FakeTransportService.FAKE_PROCESS_NAME) },
                                           mock(AppInspectionNotificationFactory::class.java))
    val tabAddedLatch = CountDownLatch(2)
    inspectionView.inspectorTabs.addContainerListener(object : ContainerAdapter() {
      override fun componentAdded(e: ContainerEvent) {
        tabAddedLatch.countDown()
      }
    })

    // Launch two processes and wait for them to show up in combobox
    val fakeDevice =
      FakeTransportService.FAKE_DEVICE.toBuilder().setDeviceId(1).setModel("fakeModel").setManufacturer("fakeMan").setSerial("1").build()
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

  @Test
  fun inspectorCrashNotification() {
    val executor = MoreExecutors.listeningDecorator(Executors.newScheduledThreadPool(1))
    val discoveryHost = AppInspectionTestUtils.createDiscoveryHost(executor, TransportClient(grpcServerRule.name))

    // Mock the notification handles that will be used to trigger toast.
    val notificationFactory = mock(AppInspectionNotificationFactory::class.java)
    val notification = mock(Notification::class.java)
    `when`(
      notificationFactory.createNotification(
        ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), any(NotificationType::class.java),
        any(NotificationListener::class.java))).thenReturn(notification)

    // Set up the tool window.
    val inspectionView = AppInspectionView(projectRule.project, discoveryHost, appInspectionCallbacks,
                                           { listOf(FakeTransportService.FAKE_PROCESS_NAME) }, notificationFactory)
    val tabAddedLatch = CountDownLatch(1)
    inspectionView.inspectorTabs.addContainerListener(object : ContainerAdapter() {
      override fun componentAdded(e: ContainerEvent) {
        tabAddedLatch.countDown()
      }
    })

    // Launch a processes and wait for its tab to be created
    val fakeDevice = FakeTransportService.FAKE_DEVICE.toBuilder().setDeviceId(1).setModel("fakeModel").setManufacturer("fakeMan").setSerial(
      "1").build()
    val fakeProcess = FakeTransportService.FAKE_PROCESS.toBuilder().setPid(1).setDeviceId(1).build()
    transportService.addDevice(fakeDevice)
    transportService.addProcess(fakeDevice, fakeProcess)
    tabAddedLatch.await()

    // Generate fake crash event
    transportService.addEventToStream(
      1,
      Common.Event.newBuilder()
        .setPid(1)
        .setKind(Common.Event.Kind.APP_INSPECTION_EVENT)
        .setTimestamp(timer.currentTimeNs)
        .setIsEnded(true)
        .setAppInspectionEvent(AppInspection.AppInspectionEvent.newBuilder()
                                 .setInspectorId(INSPECTOR_ID)
                                 .setCrashEvent(
                                   AppInspection.CrashEvent.newBuilder()
                                     .setErrorMessage("error")
                                     .build()
                                 )
                                 .build())
        .build()
    )

    // Verify crash triggers toast.
    verify(notification, timeout(5000).times(1)).notify(projectRule.project)
  }
}