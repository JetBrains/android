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
import com.android.tools.idea.appinspection.test.INSPECTOR_ID
import com.android.tools.idea.appinspection.test.INSPECTOR_ID_2
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.transport.faketransport.FakeGrpcServer
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.idea.transport.faketransport.commands.CommandHandler
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Common
import com.google.common.truth.Truth.assertThat
import com.intellij.util.concurrency.EdtExecutorService
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.awt.event.ContainerAdapter
import java.awt.event.ContainerEvent
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

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
  fun selectProcessInAppInspectionView_twoTabProvidersAddTwoTabs() = runBlocking<Unit> {
    val uiDispatcher = EdtExecutorService.getInstance().asCoroutineDispatcher()

    launch(uiDispatcher) {
      val inspectionView = AppInspectionView(projectRule.project, appInspectionServiceRule.apiServices, ideServices,
                                             appInspectionServiceRule.scope, uiDispatcher) {
        listOf(FakeTransportService.FAKE_PROCESS_NAME)
      }

      var count = 0
      suspendCoroutine<Unit> { cont ->
        inspectionView.inspectorTabs.addContainerListener(object : ContainerAdapter() {
          override fun componentAdded(e: ContainerEvent) {
            count++
            if (count == 2) {
              cont.resume(Unit)
            }
          }
        })
      }
    }

    // Attach to a fake process.
    transportService.addDevice(FakeTransportService.FAKE_DEVICE)
    transportService.addProcess(FakeTransportService.FAKE_DEVICE, FakeTransportService.FAKE_PROCESS)
  }

  @Test
  fun selectProcessInAppInspectionView_tabNotAddedForDisabledTabProvider() = runBlocking<Unit> {
    // Disable Inspector2 and only one tab should be added.
    val uiDispatcher = EdtExecutorService.getInstance().asCoroutineDispatcher()
    launch(uiDispatcher) {
      val inspectionView = AppInspectionView(projectRule.project, appInspectionServiceRule.apiServices,
                                             ideServices,
                                             { listOf(TestAppInspectorTabProvider1()) },
                                             appInspectionServiceRule.scope, uiDispatcher) {
        listOf(FakeTransportService.FAKE_PROCESS_NAME)
      }
      suspendCoroutine<Unit> { cont ->
        inspectionView.inspectorTabs.addContainerListener(object : ContainerAdapter() {
          override fun componentAdded(e: ContainerEvent) = cont.resume(Unit)
        })
      }
    }

    // Attach to a fake process.
    transportService.addDevice(FakeTransportService.FAKE_DEVICE)
    transportService.addProcess(FakeTransportService.FAKE_DEVICE, FakeTransportService.FAKE_PROCESS)
  }

  @Test
  fun disposeInspectorWhenSelectionChanges() = runBlocking<Unit> {
    val uiDispatcher = EdtExecutorService.getInstance().asCoroutineDispatcher()

    lateinit var processModel: AppInspectionProcessModel
    launch(uiDispatcher) {
      val inspectionView = AppInspectionView(projectRule.project, appInspectionServiceRule.apiServices, ideServices,
                                             appInspectionServiceRule.scope, uiDispatcher) {
        listOf(FakeTransportService.FAKE_PROCESS_NAME)
      }

      processModel = inspectionView.processModel

      suspendCoroutine<Unit> { cont ->
        inspectionView.inspectorTabs.addContainerListener(object : ContainerAdapter() {
          var count = 0
          override fun componentAdded(e: ContainerEvent) {
            count++
            if (count == 2) {
              cont.resume(Unit)
            }
          }
        })

        // Launch two processes and wait for them to show up in combobox
        val fakeDevice =
          FakeTransportService.FAKE_DEVICE.toBuilder().setDeviceId(1).setModel("fakeModel").setManufacturer("fakeMan").setSerial(
            "1").build()
        val fakeProcess1 = FakeTransportService.FAKE_PROCESS.toBuilder().setPid(1).setDeviceId(1).build()
        val fakeProcess2 = FakeTransportService.FAKE_PROCESS.toBuilder().setPid(2).setDeviceId(1).build()
        transportService.addDevice(fakeDevice)
        transportService.addProcess(fakeDevice, fakeProcess1)
        transportService.addProcess(fakeDevice, fakeProcess2)
      }
    }.join()


    // Change process selection and check to see if a dispose command was sent out.
    launch {
      suspendCoroutine<Unit> { cont ->
        transportService.setCommandHandler(Commands.Command.CommandType.APP_INSPECTION, object : CommandHandler(timer) {
          override fun handleCommand(command: Commands.Command, events: MutableList<Common.Event>) {
            if (command.appInspectionCommand.hasDisposeInspectorCommand()) {
              cont.resume(Unit)
            }
          }
        })

        processModel.selectedProcess = processModel.processes.first { it != processModel.selectedProcess }
      }
    }
  }

  @Test
  fun inspectorCrashNotification() = runBlocking<Unit> {
    val uiDispatcher = EdtExecutorService.getInstance().asCoroutineDispatcher()
    val fakeDevice = FakeTransportService.FAKE_DEVICE.toBuilder().setDeviceId(1).setModel("fakeModel").setManufacturer("fakeMan").setSerial(
      "1").build()
    val fakeProcess = FakeTransportService.FAKE_PROCESS.toBuilder().setPid(1).setDeviceId(1).build()
    launch(uiDispatcher) {
      val inspectionView = AppInspectionView(projectRule.project, appInspectionServiceRule.apiServices, ideServices,
                                             appInspectionServiceRule.scope, uiDispatcher) {
        listOf(FakeTransportService.FAKE_PROCESS_NAME)
      }

      // Test initial tabs added.
      launch {
        suspendCoroutine<Unit> { cont ->
          inspectionView.inspectorTabs.addContainerListener(object : ContainerAdapter() {
            var tabsAdded = 0
            override fun componentAdded(e: ContainerEvent) {
              tabsAdded++
              if (tabsAdded == 2) {
                cont.resume(Unit)
              }
            }
          })

          // Launch a processes and wait for its tab to be created
          transportService.addDevice(fakeDevice)
          transportService.addProcess(fakeDevice, fakeProcess)
        }
      }.join()

      // Test crash notification shown.
      lateinit var notificationData: TestIdeServices.NotificationData
      launch {
        suspendCoroutine<Unit> { cont ->
          ideServices.notificationListeners += { data ->
            notificationData = data
            cont.resume(Unit)
          }

          // Generate fake crash event
          transportService.addEventToStream(
            fakeDevice.deviceId,
            Common.Event.newBuilder()
              .setPid(fakeProcess.pid)
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

          // increment timer manually here because otherwise the new inspector connection created below will poll the crash event above.
          timer.currentTimeNs += 1
        }

        assertThat(notificationData.content).contains("$INSPECTOR_ID has crashed")
        assertThat(notificationData.severity).isEqualTo(AppInspectionIdeServices.Severity.ERROR)
      }.join()

      // Test initial tabs added.
      launch {
        suspendCoroutine<Unit> { cont ->
          inspectionView.inspectorTabs.addContainerListener(object : ContainerAdapter() {
            override fun componentAdded(e: ContainerEvent) {
              cont.resume(Unit)
            }
          })

          // Make sure clicking the notification causes a new tab to get created
          notificationData.hyperlinkClicked()
        }
      }.join()
    }
  }

  @Test
  fun inspectorRestartNotificationShownOnLaunchError() = runBlocking<Unit> {
    val uiDispatcher = EdtExecutorService.getInstance().asCoroutineDispatcher()

    val fakeDevice = FakeTransportService.FAKE_DEVICE.toBuilder()
      .setDeviceId(1)
      .setModel("fakeModel")
      .setManufacturer("fakeMan")
      .setSerial("1")
      .build()

    val fakeProcess = FakeTransportService.FAKE_PROCESS.toBuilder()
      .setPid(1)
      .setDeviceId(1)
      .build()

    transportService.addDevice(fakeDevice)
    transportService.addProcess(fakeDevice, fakeProcess)

    // Overwrite the handler to simulate a launch error, e.g. an inspector was left over from a previous crash
    transportService.setCommandHandler(Commands.Command.CommandType.APP_INSPECTION, TestInspectorCommandHandler(timer, false, "error"))

    lateinit var notificationData: TestIdeServices.NotificationData
    val notificationWatcher = launch {
      suspendCoroutine<Unit> { cont ->
        ideServices.notificationListeners += { data ->
          notificationData = data
          cont.resume(Unit)
        }
      }
    }

    val tabWatcher = launch(uiDispatcher) {
      val inspectionView = AppInspectionView(projectRule.project, appInspectionServiceRule.apiServices, ideServices,
                                             { listOf(TestAppInspectorTabProvider1()) },
                                             appInspectionServiceRule.scope, uiDispatcher) {
        listOf(FakeTransportService.FAKE_PROCESS_NAME)
      }

      suspendCoroutine<Unit> { cont ->
        inspectionView.inspectorTabs.addContainerListener(object : ContainerAdapter() {
          override fun componentAdded(e: ContainerEvent) {
            cont.resume(Unit)
          }
        })
      }
    }

    // Verify we crashed on launch, failing to open the UI and triggering the toast.
    notificationWatcher.join()
    assertThat(tabWatcher.isCompleted).isFalse()
    assertThat(notificationData.content).startsWith("Could not launch inspector")
    assertThat(notificationData.severity).isEqualTo(AppInspectionIdeServices.Severity.ERROR)

    // Restore the working command handler, which emulates relaunching with force == true
    transportService.setCommandHandler(Commands.Command.CommandType.APP_INSPECTION, TestInspectorCommandHandler(timer))

    launch(uiDispatcher) {
      notificationData.hyperlinkClicked()
    }
  }
}