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
import com.android.tools.app.inspection.AppInspection
import com.android.tools.idea.appinspection.api.TestInspectorCommandHandler
import com.android.tools.idea.appinspection.ide.model.AppInspectionProcessModel
import com.android.tools.idea.appinspection.ide.ui.AppInspectionView
import com.android.tools.idea.appinspection.inspector.api.AppInspectionIdeServices
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
import com.google.common.truth.Truth.assertThat
import com.intellij.util.concurrency.EdtExecutorService
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
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

  private class TestIdeServices : AppInspectionIdeServices {
    class NotificationData(val content: String, val severity: AppInspectionIdeServices.Severity, val hyperlinkClicked: () -> Unit)

    val notificationListeners = mutableListOf<(NotificationData) -> Unit>()

    override fun showToolWindow(callback: () -> Unit) {}
    override fun showNotification(content: String,
                                  title: String,
                                  severity: AppInspectionIdeServices.Severity,
                                  hyperlinkClicked: () -> Unit) {
      val data = NotificationData(content, severity, hyperlinkClicked)
      notificationListeners.forEach { listener -> listener(data) }
    }
  }

  private val ideServices = TestIdeServices()

  @get:Rule
  val ruleChain = RuleChain.outerRule(grpcServerRule).around(appInspectionServiceRule)!!.around(projectRule)!!

  @Before
  fun setup() {
    transportService.setCommandHandler(Commands.Command.CommandType.ATTACH_AGENT, ATTACH_HANDLER)
    transportService.setCommandHandler(Commands.Command.CommandType.APP_INSPECTION, TestInspectorCommandHandler(timer))
  }

  @Test
  fun selectProcessInAppInspectionView_twoTabProvidersAddTwoTabs() {
    val backgroundExecutor = Executors.newSingleThreadExecutor()
    val uiExecutor = EdtExecutorService.getInstance()
    val discoveryHost = AppInspectionTestUtils.createDiscoveryHost(backgroundExecutor, TransportClient(grpcServerRule.name))

    val tabsAddedLatch = CountDownLatch(2)
    uiExecutor.submit {
      val inspectionView = AppInspectionView(projectRule.project, discoveryHost, ideServices) {
        listOf(FakeTransportService.FAKE_PROCESS_NAME)
      }

      inspectionView.inspectorTabs.addContainerListener(object : ContainerAdapter() {
        override fun componentAdded(e: ContainerEvent) = tabsAddedLatch.countDown()
      })
    }

    // Attach to a fake process.
    transportService.addDevice(FakeTransportService.FAKE_DEVICE)
    transportService.addProcess(FakeTransportService.FAKE_DEVICE, FakeTransportService.FAKE_PROCESS)
    tabsAddedLatch.await()
  }

  @Test
  fun selectProcessInAppInspectionView_tabNotAddedForDisabledTabProvider() {
    // Disable Inspector2 and only one tab should be added.
    val backgroundExecutor = Executors.newSingleThreadExecutor()
    val uiExecutor = EdtExecutorService.getInstance()
    val discoveryHost = AppInspectionTestUtils.createDiscoveryHost(backgroundExecutor, TransportClient(grpcServerRule.name))

    val tabsAddedLatch = CountDownLatch(1)
    uiExecutor.submit {
      val inspectionView = AppInspectionView(projectRule.project, discoveryHost, ideServices, { listOf(TestAppInspectorTabProvider1()) }) {
        listOf(FakeTransportService.FAKE_PROCESS_NAME)
      }

      inspectionView.inspectorTabs.addContainerListener(object : ContainerAdapter() {
        override fun componentAdded(e: ContainerEvent) = tabsAddedLatch.countDown()
      })
    }

    // Attach to a fake process.
    transportService.addDevice(FakeTransportService.FAKE_DEVICE)
    transportService.addProcess(FakeTransportService.FAKE_DEVICE, FakeTransportService.FAKE_PROCESS)
    tabsAddedLatch.await()
  }

  @Test
  fun disposeInspectorWhenSelectionChanges() {
    val backgroundExecutor = Executors.newSingleThreadExecutor()
    val uiExecutor = EdtExecutorService.getInstance()
    val discoveryHost = AppInspectionTestUtils.createDiscoveryHost(backgroundExecutor, TransportClient(grpcServerRule.name))

    val tabsAddedLatch = CountDownLatch(2)
    lateinit var processModel: AppInspectionProcessModel
    uiExecutor.submit {
      val inspectionView = AppInspectionView(projectRule.project, discoveryHost, ideServices) {
        listOf(FakeTransportService.FAKE_PROCESS_NAME)
      }
      processModel = inspectionView.processModel

      inspectionView.inspectorTabs.addContainerListener(object : ContainerAdapter() {
        override fun componentAdded(e: ContainerEvent) {
          tabsAddedLatch.countDown()
        }
      })
    }

    // Launch two processes and wait for them to show up in combobox
    val fakeDevice =
      FakeTransportService.FAKE_DEVICE.toBuilder().setDeviceId(1).setModel("fakeModel").setManufacturer("fakeMan").setSerial("1").build()
    val fakeProcess1 = FakeTransportService.FAKE_PROCESS.toBuilder().setPid(1).setDeviceId(1).build()
    val fakeProcess2 = FakeTransportService.FAKE_PROCESS.toBuilder().setPid(2).setDeviceId(1).build()
    transportService.addDevice(fakeDevice)
    transportService.addProcess(fakeDevice, fakeProcess1)
    transportService.addProcess(fakeDevice, fakeProcess2)
    tabsAddedLatch.await()

    // Change process selection and check to see if a dispose command was sent out.
    val inspectorDisposedLatch = CountDownLatch(1)
    transportService.setCommandHandler(Commands.Command.CommandType.APP_INSPECTION, object : CommandHandler(timer) {
      override fun handleCommand(command: Commands.Command, events: MutableList<Common.Event>) {
        if (command.appInspectionCommand.hasDisposeInspectorCommand()) {
          inspectorDisposedLatch.countDown()
        }
      }
    })

    processModel.selectedProcess = processModel.processes.first { it != processModel.selectedProcess }
    inspectorDisposedLatch.await()
  }

  @Test
  fun inspectorCrashNotification() {
    val backgroundExecutor = Executors.newSingleThreadExecutor()
    val uiExecutor = EdtExecutorService.getInstance()
    val discoveryHost = AppInspectionTestUtils.createDiscoveryHost(backgroundExecutor, TransportClient(grpcServerRule.name))

    val notificationLatch = CountDownLatch(1)
    lateinit var notificationData: TestIdeServices.NotificationData
    ideServices.notificationListeners += { data ->
      notificationData = data
      notificationLatch.countDown()
    }

    // Set up the tool window.
    val initialTabsAddedLatch = CountDownLatch(2) // Initial tabs created by the two test providers
    val retryTabAddedLatch = CountDownLatch(1) // A later tab will be opened by pressing the "retry" notification hyperlink
    uiExecutor.submit {
      val inspectionView = AppInspectionView(projectRule.project, discoveryHost, ideServices) {
        listOf(FakeTransportService.FAKE_PROCESS_NAME)
      }

      inspectionView.inspectorTabs.addContainerListener(object : ContainerAdapter() {
        override fun componentAdded(e: ContainerEvent) {
          if (initialTabsAddedLatch.count > 0) {
            initialTabsAddedLatch.countDown()
          }
          else {
            retryTabAddedLatch.countDown()
          }
        }
      })
    }

    // Launch a processes and wait for its tab to be created
    val fakeDevice = FakeTransportService.FAKE_DEVICE.toBuilder().setDeviceId(1).setModel("fakeModel").setManufacturer("fakeMan").setSerial(
      "1").build()
    val fakeProcess = FakeTransportService.FAKE_PROCESS.toBuilder().setPid(1).setDeviceId(1).build()
    transportService.addDevice(fakeDevice)
    transportService.addProcess(fakeDevice, fakeProcess)
    initialTabsAddedLatch.await()

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
    notificationLatch.await()
    assertThat(notificationData.content).contains("$INSPECTOR_ID has crashed")
    assertThat(notificationData.severity).isEqualTo(AppInspectionIdeServices.Severity.ERROR)

    // Make sure clicking the notification causes a new tab to get created
    assertThat(retryTabAddedLatch.count).isGreaterThan(0)
    uiExecutor.submit { notificationData.hyperlinkClicked() }
    retryTabAddedLatch.await()
  }
}
