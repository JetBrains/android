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
import com.android.tools.adtui.stdui.EmptyStatePanel
import com.android.tools.app.inspection.AppInspection
import com.android.tools.idea.appinspection.ide.model.AppInspectionBundle
import com.android.tools.idea.appinspection.ide.model.AppInspectionProcessModel
import com.android.tools.idea.appinspection.ide.ui.AppInspectionView
import com.android.tools.idea.appinspection.inspector.api.AppInspectionIdeServices
import com.android.tools.idea.appinspection.inspector.api.launch.ArtifactCoordinate
import com.android.tools.idea.appinspection.inspector.api.service.TestAppInspectionIdeServices
import com.android.tools.idea.appinspection.inspector.ide.AppInspectorTabProvider
import com.android.tools.idea.appinspection.inspector.ide.LibraryInspectorLaunchParams
import com.android.tools.idea.appinspection.test.AppInspectionServiceRule
import com.android.tools.idea.appinspection.test.INSPECTOR_ID
import com.android.tools.idea.appinspection.test.INSPECTOR_ID_2
import com.android.tools.idea.appinspection.test.INSPECTOR_ID_3
import com.android.tools.idea.appinspection.test.TEST_JAR
import com.android.tools.idea.appinspection.test.TestAppInspectorCommandHandler
import com.android.tools.idea.appinspection.test.createCreateInspectorResponse
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.transport.faketransport.FakeGrpcServer
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.idea.transport.faketransport.commands.CommandHandler
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Common
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.EdtExecutorService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

class TestAppInspectorTabProvider1 : AppInspectorTabProvider by StubTestAppInspectorTabProvider(INSPECTOR_ID)
class TestAppInspectorTabProvider2 : AppInspectorTabProvider by StubTestAppInspectorTabProvider(
  INSPECTOR_ID_2,
  LibraryInspectorLaunchParams(TEST_JAR,
                               ArtifactCoordinate("groupId", "artifactId", "0.0.0", ArtifactCoordinate.Type.JAR)))

@ExperimentalCoroutinesApi
class AppInspectionViewTest {
  private val timer = FakeTimer()
  private val transportService = FakeTransportService(timer, false)

  private val grpcServerRule = FakeGrpcServer.createFakeGrpcServer("AppInspectionViewTest", transportService, transportService)!!
  private val appInspectionServiceRule = AppInspectionServiceRule(timer, transportService, grpcServerRule)
  private val projectRule = AndroidProjectRule.inMemory().initAndroid(false)

  private class TestIdeServices : TestAppInspectionIdeServices() {
    class NotificationData(val content: String, val severity: AppInspectionIdeServices.Severity, val hyperlinkClicked: () -> Unit)

    val notificationListeners = mutableListOf<(NotificationData) -> Unit>()

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
    transportService.setCommandHandler(Commands.Command.CommandType.APP_INSPECTION, TestAppInspectorCommandHandler(timer))
  }

  @Test
  fun selectProcessInAppInspectionView_twoTabProvidersAddTwoTabs() = runBlocking<Unit> {
    val uiDispatcher = EdtExecutorService.getInstance().asCoroutineDispatcher()

    val tabsAdded = CompletableDeferred<Unit>()
    launch(uiDispatcher) {
      val inspectionView = AppInspectionView(projectRule.project, appInspectionServiceRule.apiServices, ideServices,
                                             appInspectionServiceRule.scope, uiDispatcher) {
        listOf(FakeTransportService.FAKE_PROCESS_NAME)
      }
      Disposer.register(projectRule.fixture.testRootDisposable, inspectionView)

      inspectionView.tabsChangedFlow.first {
        assertThat(inspectionView.inspectorTabs.size).isEqualTo(2)
        tabsAdded.complete(Unit)
      }
    }

    // Attach to a fake process.
    transportService.addDevice(FakeTransportService.FAKE_DEVICE)
    transportService.addProcess(FakeTransportService.FAKE_DEVICE, FakeTransportService.FAKE_PROCESS)

    tabsAdded.join()
  }

  @Test
  fun selectProcessInAppInspectionView_tabNotAddedForDisabledTabProvider() = runBlocking<Unit> {
    // Disable Inspector2 and only one tab should be added.
    val uiDispatcher = EdtExecutorService.getInstance().asCoroutineDispatcher()
    val tabsAdded = CompletableDeferred<Unit>()
    launch(uiDispatcher) {
      val inspectionView = AppInspectionView(projectRule.project, appInspectionServiceRule.apiServices,
                                             ideServices,
                                             { listOf(TestAppInspectorTabProvider1()) },
                                             appInspectionServiceRule.scope, uiDispatcher) {
        listOf(FakeTransportService.FAKE_PROCESS_NAME)
      }
      Disposer.register(projectRule.fixture.testRootDisposable, inspectionView)
      inspectionView.tabsChangedFlow.first {
        assertThat(inspectionView.inspectorTabs.size).isEqualTo(1)
        tabsAdded.complete(Unit)
      }
    }

    // Attach to a fake process.
    transportService.addDevice(FakeTransportService.FAKE_DEVICE)
    transportService.addProcess(FakeTransportService.FAKE_DEVICE, FakeTransportService.FAKE_PROCESS)

    tabsAdded.join()
  }

  @Test
  fun disposeInspectorWhenSelectionChanges() = runBlocking<Unit> {
    val uiDispatcher = EdtExecutorService.getInstance().asCoroutineDispatcher()

    lateinit var processModel: AppInspectionProcessModel
    val tabsAdded = CompletableDeferred<Unit>()
    launch(uiDispatcher) {
      val inspectionView = AppInspectionView(projectRule.project, appInspectionServiceRule.apiServices, ideServices,
                                             appInspectionServiceRule.scope, uiDispatcher) {
        listOf(FakeTransportService.FAKE_PROCESS_NAME)
      }
      Disposer.register(projectRule.fixture.testRootDisposable, inspectionView)

      processModel = inspectionView.processModel
      inspectionView.tabsChangedFlow.first {
        assertThat(inspectionView.inspectorTabs.size).isEqualTo(2)
        inspectionView.inspectorTabs.forEach { it.waitForContent() }
        tabsAdded.complete(Unit)
      }
    }

    // Launch two processes and wait for them to show up in combobox
    val fakeDevice =
      FakeTransportService.FAKE_DEVICE.toBuilder().setDeviceId(1).setModel("fakeModel").setManufacturer("fakeMan").setSerial(
        "1").build()
    val fakeProcess1 = FakeTransportService.FAKE_PROCESS.toBuilder().setPid(1).setDeviceId(1).build()
    val fakeProcess2 = FakeTransportService.FAKE_PROCESS.toBuilder().setPid(2).setDeviceId(1).build()
    transportService.addDevice(fakeDevice)
    transportService.addProcess(fakeDevice, fakeProcess1)
    transportService.addProcess(fakeDevice, fakeProcess2)

    tabsAdded.join()


    // Change process selection and check to see if a dispose command was sent out.
    val disposed = CompletableDeferred<Unit>()
    transportService.setCommandHandler(Commands.Command.CommandType.APP_INSPECTION, object : CommandHandler(timer) {
      override fun handleCommand(command: Commands.Command, events: MutableList<Common.Event>) {
        if (command.appInspectionCommand.hasDisposeInspectorCommand()) {
          disposed.complete(Unit)
        }
      }
    })

    processModel.setSelectedProcess(processModel.processes.first { it != processModel.selectedProcess })

    disposed.join()
  }

  @Test
  fun inspectorCrashNotification() = runBlocking<Unit> {
    val uiDispatcher = EdtExecutorService.getInstance().asCoroutineDispatcher()
    val fakeDevice = FakeTransportService.FAKE_DEVICE.toBuilder().setDeviceId(1).setModel("fakeModel").setManufacturer("fakeMan").setSerial(
      "1").build()
    val fakeProcess = FakeTransportService.FAKE_PROCESS.toBuilder().setPid(1).setDeviceId(1).build()

    lateinit var inspectionView: AppInspectionView
    val tabsAdded = CompletableDeferred<Unit>()
    val restartedTabAdded = CompletableDeferred<Unit>()
    launch(uiDispatcher) {
      inspectionView = AppInspectionView(projectRule.project, appInspectionServiceRule.apiServices, ideServices,
                                         appInspectionServiceRule.scope, uiDispatcher) {
        listOf(FakeTransportService.FAKE_PROCESS_NAME)
      }
      Disposer.register(projectRule.fixture.testRootDisposable, inspectionView)

      // Test initial tabs added.
      inspectionView.tabsChangedFlow
        .take(2)
        .collectIndexed { i, _ ->
          if (i == 0) {
            assertThat(inspectionView.inspectorTabs.size).isEqualTo(2)
            inspectionView.inspectorTabs.forEach { it.waitForContent() }
            tabsAdded.complete(Unit)
          }
          else if (i == 1) {
            restartedTabAdded.complete(Unit)
          }
        }
    }

    // Launch a processes and wait for its tab to be created
    transportService.addDevice(fakeDevice)
    transportService.addProcess(fakeDevice, fakeProcess)

    tabsAdded.await()

    // Test crash notification shown.
    val notificationDataDeferred = CompletableDeferred<TestIdeServices.NotificationData>()
    ideServices.notificationListeners += { data -> notificationDataDeferred.complete(data) }

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

    val notificationData = notificationDataDeferred.await()
    assertThat(notificationData.content).contains("$INSPECTOR_ID has crashed")
    assertThat(notificationData.severity).isEqualTo(AppInspectionIdeServices.Severity.ERROR)

    launch(uiDispatcher) {
      // Make sure clicking the notification causes a new tab to get created
      notificationData.hyperlinkClicked()
    }
    restartedTabAdded.await()
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
    transportService.setCommandHandler(Commands.Command.CommandType.APP_INSPECTION,
                                       TestAppInspectorCommandHandler(timer, createInspectorResponse = createCreateInspectorResponse(
                                         AppInspection.AppInspectionResponse.Status.ERROR,
                                         AppInspection.CreateInspectorResponse.Status.GENERIC_SERVICE_ERROR, "error"))
    )

    val notificationDataDeferred = CompletableDeferred<TestIdeServices.NotificationData>()
    ideServices.notificationListeners += { data -> notificationDataDeferred.complete(data) }


    lateinit var inspectionView: AppInspectionView
    val launchFailed = CompletableDeferred<Unit>()
    val tabsAdded = CompletableDeferred<Unit>()
    launch(uiDispatcher) {
      inspectionView = AppInspectionView(projectRule.project, appInspectionServiceRule.apiServices, ideServices,
                                         { listOf(TestAppInspectorTabProvider1()) },
                                         appInspectionServiceRule.scope, uiDispatcher) {
        listOf(FakeTransportService.FAKE_PROCESS_NAME)
      }
      Disposer.register(projectRule.fixture.testRootDisposable, inspectionView)

      inspectionView.tabsChangedFlow
        .take(2)
        .collectIndexed { i, _ ->
          assertThat(inspectionView.inspectorTabs.size).isEqualTo(1)
          val tab = inspectionView.inspectorTabs[0]
          tab.waitForContent()
          if (i == 0) {
            assertThat((tab.containerPanel.getComponent(0) as EmptyStatePanel).reasonText).isEqualTo(
              AppInspectionBundle.message("inspector.launch.error", tab.provider.displayName))
            launchFailed.complete(Unit)
          }
          else if (i == 1) {
            assertThat(tab).isNotInstanceOf(EmptyStatePanel::class.java)
            tabsAdded.complete(Unit)
          }
        }
    }

    // Verify we crashed on launch, failing to open the UI and triggering the toast.
    val notificationData = notificationDataDeferred.await()
    launchFailed.join()
    assertThat(notificationData.content).startsWith("Could not launch inspector")
    assertThat(notificationData.severity).isEqualTo(AppInspectionIdeServices.Severity.ERROR)

    // Restore the working command handler, which emulates relaunching with force == true
    transportService.setCommandHandler(Commands.Command.CommandType.APP_INSPECTION, TestAppInspectorCommandHandler(timer))

    launch(uiDispatcher) {
      notificationData.hyperlinkClicked()
    }
    tabsAdded.join()
  }

  @Test
  fun ifTabSupportsOfflineModeTabStaysOpenAfterProcessIsTerminated() = runBlocking {
    val uiDispatcher = EdtExecutorService.getInstance().asCoroutineDispatcher()

    lateinit var inspectionView: AppInspectionView
    val tabsAdded = CompletableDeferred<Unit>()
    val tabsUpdated = CompletableDeferred<Unit>()
    launch(uiDispatcher) {
      val supportsOfflineInspector = object : AppInspectorTabProvider by StubTestAppInspectorTabProvider(INSPECTOR_ID_3) {
        override fun supportsOffline() = true
      }

      inspectionView = AppInspectionView(
        projectRule.project, appInspectionServiceRule.apiServices, ideServices,
        { listOf(TestAppInspectorTabProvider1(), TestAppInspectorTabProvider2(), supportsOfflineInspector) },
        appInspectionServiceRule.scope, uiDispatcher) {
        listOf(FakeTransportService.FAKE_PROCESS_NAME)
      }
      Disposer.register(projectRule.fixture.testRootDisposable, inspectionView)
      inspectionView.tabsChangedFlow
        .take(2)
        .collectIndexed { i, _ ->
          if (i == 0) {
            assertThat(inspectionView.inspectorTabs.size).isEqualTo(3)
            inspectionView.inspectorTabs.forEach { it.waitForContent() }
            tabsAdded.complete(Unit)
          }
          else if (i == 1) {
            assertThat(inspectionView.inspectorTabs.size).isEqualTo(1) // Only the offline tab remains
            tabsUpdated.complete(Unit)
          }
        }
    }
    transportService.addDevice(FakeTransportService.FAKE_DEVICE)
    transportService.addProcess(FakeTransportService.FAKE_DEVICE, FakeTransportService.FAKE_PROCESS)

    tabsAdded.join()

    transportService.stopProcess(FakeTransportService.FAKE_DEVICE, FakeTransportService.FAKE_PROCESS)
    tabsUpdated.join()
  }

  @Test
  fun offlineTabsAreRemovedIfInspectorIsStillLoading() = runBlocking {
    val uiDispatcher = EdtExecutorService.getInstance().asCoroutineDispatcher()

    lateinit var inspectionView: AppInspectionView
    val tabsAdded = CompletableDeferred<Unit>()
    val tabsUpdated = CompletableDeferred<Unit>()
    launch(uiDispatcher) {
      val supportsOfflineInspector = object : AppInspectorTabProvider by StubTestAppInspectorTabProvider(INSPECTOR_ID_3) {
        override fun supportsOffline() = true
      }

      inspectionView = AppInspectionView(
        projectRule.project, appInspectionServiceRule.apiServices, ideServices,
        { listOf(TestAppInspectorTabProvider1(), TestAppInspectorTabProvider2(), supportsOfflineInspector) },
        appInspectionServiceRule.scope, uiDispatcher) {
        listOf(FakeTransportService.FAKE_PROCESS_NAME)
      }
      Disposer.register(projectRule.fixture.testRootDisposable, inspectionView)
      inspectionView.tabsChangedFlow
        .take(2)
        .collectIndexed { i, _ ->
          if (i == 0) {
            assertThat(inspectionView.inspectorTabs).hasSize(3)
            tabsAdded.complete(Unit)
          }
          else if (i == 1) {
            assertThat(inspectionView.inspectorTabs).isEmpty()
            tabsUpdated.complete(Unit)
          }
        }
    }

    // Suppress the response to createInspectorCommand to simulate the tab is loading.
    transportService.setCommandHandler(Commands.Command.CommandType.APP_INSPECTION, object : CommandHandler(timer) {
      val handler = TestAppInspectorCommandHandler(timer)
      override fun handleCommand(command: Commands.Command, events: MutableList<Common.Event>) {
        if (!command.appInspectionCommand.hasCreateInspectorCommand()) {
          handler.handleCommand(command, events)
        }
      }
    })
    transportService.addDevice(FakeTransportService.FAKE_DEVICE)
    transportService.addProcess(FakeTransportService.FAKE_DEVICE, FakeTransportService.FAKE_PROCESS)

    tabsAdded.join()

    transportService.stopProcess(FakeTransportService.FAKE_DEVICE, FakeTransportService.FAKE_PROCESS)
    tabsUpdated.join()
  }


  @Test
  fun launchInspectorFailsDueToIncompatibleVersion_emptyMessageAdded() = runBlocking<Unit> {
    val uiDispatcher = EdtExecutorService.getInstance().asCoroutineDispatcher()
    val tabsAdded = CompletableDeferred<Unit>()
    val provider = TestAppInspectorTabProvider2()
    launch(uiDispatcher) {
      val inspectionView = AppInspectionView(projectRule.project, appInspectionServiceRule.apiServices,
                                             ideServices,
                                             { listOf(provider) },
                                             appInspectionServiceRule.scope, uiDispatcher) {
        listOf(FakeTransportService.FAKE_PROCESS_NAME)
      }
      Disposer.register(projectRule.fixture.testRootDisposable, inspectionView)
      inspectionView.tabsChangedFlow.first {
        assertThat(inspectionView.inspectorTabs.size).isEqualTo(1)
        val tab = inspectionView.inspectorTabs[0]
        tab.waitForContent()
        val emptyPanel = tab.containerPanel.getComponent(0) as EmptyStatePanel

        assertThat(emptyPanel.reasonText)
          .isEqualTo(
            AppInspectionBundle.message(
              "incompatible.version",
              (provider.inspectorLaunchParams as LibraryInspectorLaunchParams).minVersionLibraryCoordinate.toString()))

        tabsAdded.complete(Unit)
      }
    }

    transportService.setCommandHandler(Commands.Command.CommandType.APP_INSPECTION,
                                       TestAppInspectorCommandHandler(timer, createInspectorResponse = createCreateInspectorResponse(
                                         AppInspection.AppInspectionResponse.Status.ERROR,
                                         AppInspection.CreateInspectorResponse.Status.VERSION_INCOMPATIBLE, "error"))
    )

    // Attach to a fake process.
    transportService.addDevice(FakeTransportService.FAKE_DEVICE)
    transportService.addProcess(FakeTransportService.FAKE_DEVICE, FakeTransportService.FAKE_PROCESS)

    tabsAdded.join()
  }

  @Test
  fun launchInspectorFailsDueToServiceError() = runBlocking<Unit> {
    val uiDispatcher = EdtExecutorService.getInstance().asCoroutineDispatcher()
    val tabsAdded = CompletableDeferred<Unit>()
    launch(uiDispatcher) {
      val inspectionView = AppInspectionView(projectRule.project, appInspectionServiceRule.apiServices,
                                             ideServices,
                                             { listOf(TestAppInspectorTabProvider1()) },
                                             appInspectionServiceRule.scope, uiDispatcher) {
        listOf(FakeTransportService.FAKE_PROCESS_NAME)
      }
      Disposer.register(projectRule.fixture.testRootDisposable, inspectionView)
      inspectionView.tabsChangedFlow.first {
        assertThat(inspectionView.inspectorTabs.size).isEqualTo(1)
        val tab = inspectionView.inspectorTabs[0]
        tab.waitForContent()
        val statePanel = tab.containerPanel.getComponent(0)
        assertThat(statePanel).isInstanceOf(EmptyStatePanel::class.java)
        assertThat((statePanel as EmptyStatePanel).reasonText).isEqualTo(AppInspectionBundle.message(
          "inspector.launch.error", tab.provider.displayName))
        tabsAdded.complete(Unit)
      }
    }

    transportService.setCommandHandler(Commands.Command.CommandType.APP_INSPECTION,
                                       TestAppInspectorCommandHandler(timer, createInspectorResponse = createCreateInspectorResponse(
                                         AppInspection.AppInspectionResponse.Status.ERROR,
                                         AppInspection.CreateInspectorResponse.Status.GENERIC_SERVICE_ERROR, "error")))

    // Attach to a fake process.
    transportService.addDevice(FakeTransportService.FAKE_DEVICE)
    transportService.addProcess(FakeTransportService.FAKE_DEVICE, FakeTransportService.FAKE_PROCESS)

    tabsAdded.join()
  }

  @Test
  fun launchInspectorFailsDueToMissingLibrary_emptyMessageAdded() = runBlocking<Unit> {
    val uiDispatcher = EdtExecutorService.getInstance().asCoroutineDispatcher()
    val tabsAdded = CompletableDeferred<Unit>()
    val provider = TestAppInspectorTabProvider2()
    launch(uiDispatcher) {
      val inspectionView = AppInspectionView(projectRule.project, appInspectionServiceRule.apiServices,
                                             ideServices,
                                             { listOf(provider) },
                                             appInspectionServiceRule.scope, uiDispatcher) {
        listOf(FakeTransportService.FAKE_PROCESS_NAME)
      }
      Disposer.register(projectRule.fixture.testRootDisposable, inspectionView)
      inspectionView.tabsChangedFlow.first {
        assertThat(inspectionView.inspectorTabs.size).isEqualTo(1)
        val tab = inspectionView.inspectorTabs[0]
        tab.waitForContent()
        val emptyPanel = tab.containerPanel.getComponent(0) as EmptyStatePanel
        assertThat(emptyPanel.reasonText)
          .isEqualTo(AppInspectionBundle.message(
            "incompatible.version",
            (provider.inspectorLaunchParams as LibraryInspectorLaunchParams).minVersionLibraryCoordinate.toString()))

        tabsAdded.complete(Unit)
      }
    }

    transportService.setCommandHandler(Commands.Command.CommandType.APP_INSPECTION,
                                       TestAppInspectorCommandHandler(timer, createInspectorResponse = createCreateInspectorResponse(
                                         AppInspection.AppInspectionResponse.Status.ERROR,
                                         AppInspection.CreateInspectorResponse.Status.LIBRARY_MISSING, "error")))

    // Attach to a fake process.
    transportService.addDevice(FakeTransportService.FAKE_DEVICE)
    transportService.addProcess(FakeTransportService.FAKE_DEVICE, FakeTransportService.FAKE_PROCESS)

    tabsAdded.join()
  }

  @Test
  fun stopInspectionPressed_noMoreLaunchingOfInspectors() = runBlocking {
    val uiDispatcher = EdtExecutorService.getInstance().asCoroutineDispatcher()

    val inspectionView = withContext(uiDispatcher) {
      AppInspectionView(projectRule.project, appInspectionServiceRule.apiServices, ideServices,
                        appInspectionServiceRule.scope, uiDispatcher) {
        listOf(FakeTransportService.FAKE_PROCESS_NAME)
      }
    }
    Disposer.register(projectRule.fixture.testRootDisposable, inspectionView)

    val firstProcessReadyDeferred = CompletableDeferred<Unit>()
    val deadProcessAddedDeferred = CompletableDeferred<Unit>()
    launch {
      inspectionView.tabsChangedFlow
        .take(2)
        .collectIndexed { index, _ ->
          if (index == 0) {
            firstProcessReadyDeferred.complete(Unit)
          }
          else if (index == 1) {
            deadProcessAddedDeferred.complete(Unit)
          }
        }
    }

    // Add a process.
    transportService.addDevice(FakeTransportService.FAKE_DEVICE)
    transportService.addProcess(FakeTransportService.FAKE_DEVICE, FakeTransportService.FAKE_PROCESS)

    // Wait for inspector to be added.
    firstProcessReadyDeferred.await()

    // Stop inspection.
    inspectionView.processModel.stopInspection(inspectionView.processModel.selectedProcess!!)

    // Wait for the offline inspector tabs to be added.
    deadProcessAddedDeferred.await()
  }
}