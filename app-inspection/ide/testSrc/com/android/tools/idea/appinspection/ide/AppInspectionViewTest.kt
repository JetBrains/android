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
import com.android.tools.adtui.stdui.CommonTabbedPane
import com.android.tools.adtui.stdui.EmptyStatePanel
import com.android.tools.app.inspection.AppInspection
import com.android.tools.idea.appinspection.api.AppInspectionApiServices
import com.android.tools.idea.appinspection.ide.model.AppInspectionBundle
import com.android.tools.idea.appinspection.ide.ui.AppInspectionView
import com.android.tools.idea.appinspection.ide.ui.AppInspectorTabShell
import com.android.tools.idea.appinspection.ide.ui.TAB_KEY
import com.android.tools.idea.appinspection.inspector.api.AppInspectionArtifactNotFoundException
import com.android.tools.idea.appinspection.inspector.api.AppInspectionIdeServices
import com.android.tools.idea.appinspection.inspector.api.AppInspectionIdeServicesAdapter
import com.android.tools.idea.appinspection.inspector.api.AppInspectionProcessNoLongerExistsException
import com.android.tools.idea.appinspection.inspector.api.AppInspectorMessenger
import com.android.tools.idea.appinspection.inspector.api.launch.ArtifactCoordinate
import com.android.tools.idea.appinspection.inspector.api.launch.LaunchParameters
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.appinspection.inspector.api.test.StubTestAppInspectorMessenger
import com.android.tools.idea.appinspection.inspector.ide.AppInspectorMessengerTarget
import com.android.tools.idea.appinspection.inspector.ide.AppInspectorTab
import com.android.tools.idea.appinspection.inspector.ide.AppInspectorTabProvider
import com.android.tools.idea.appinspection.inspector.ide.LibraryInspectorLaunchParams
import com.android.tools.idea.appinspection.internal.AppInspectionTarget
import com.android.tools.idea.appinspection.test.AppInspectionServiceRule
import com.android.tools.idea.appinspection.test.INSPECTOR_ID
import com.android.tools.idea.appinspection.test.INSPECTOR_ID_2
import com.android.tools.idea.appinspection.test.INSPECTOR_ID_3
import com.android.tools.idea.appinspection.test.TEST_ARTIFACT
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
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.EdtExecutorService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ArrayBlockingQueue
import javax.swing.JPanel

class TestAppInspectorTabProvider1 : AppInspectorTabProvider by StubTestAppInspectorTabProvider(INSPECTOR_ID)
class TestAppInspectorTabProvider2 : AppInspectorTabProvider by StubTestAppInspectorTabProvider(
  INSPECTOR_ID_2,
  LibraryInspectorLaunchParams(TEST_JAR,
                               ArtifactCoordinate("groupId", "artifactId", "0.0.0", ArtifactCoordinate.Type.JAR)))

@ExperimentalCoroutinesApi
class AppInspectionViewTest {
  private val timer = FakeTimer()
  private val transportService = FakeTransportService(timer, false)

  private val grpcServerRule = FakeGrpcServer.createFakeGrpcServer("AppInspectionViewTest", transportService)
  private val appInspectionServiceRule = AppInspectionServiceRule(timer, transportService, grpcServerRule)
  private val projectRule = AndroidProjectRule.inMemory().initAndroid(false)

  private class TestIdeServices : AppInspectionIdeServicesAdapter() {
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
        it.name == FakeTransportService.FAKE_PROCESS_NAME
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
                                             appInspectionServiceRule.scope, uiDispatcher, TestInspectorArtifactService()) {
        it.name == FakeTransportService.FAKE_PROCESS_NAME
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

    lateinit var tabs: List<AppInspectorTab>
    launch(uiDispatcher) {
      val inspectionView = AppInspectionView(projectRule.project, appInspectionServiceRule.apiServices, ideServices,
                                             appInspectionServiceRule.scope, uiDispatcher) {
        it.name == FakeTransportService.FAKE_PROCESS_NAME
      }
      Disposer.register(projectRule.fixture.testRootDisposable, inspectionView)

      inspectionView.tabsChangedFlow.take(2)
        .collectIndexed { i, _ ->
          if (i == 0) {
            assertThat(inspectionView.inspectorTabs.size).isEqualTo(2)
            inspectionView.inspectorTabs.forEach { it.waitForContent() }
            tabs = inspectionView.inspectorTabs
              .mapNotNull { it.getUserData(TAB_KEY) }
              .filter { it.messengers.iterator().hasNext() } // If a tab is "dead", it won't have any messengers

            assertThat(tabs).hasSize(1)
            inspectionView.processesModel.selectedProcess = inspectionView.processesModel.processes.first { process ->
              process != inspectionView.processesModel.selectedProcess
            }
          } else if (i == 1) {
            tabs.forEach { tab -> assertThat(tab.messengers.single().scope.isActive).isFalse() }
          }
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
  }

  @Test
  fun receivesInspectorDisposedEvent() = runBlocking<Unit> {
    val uiDispatcher = EdtExecutorService.getInstance().asCoroutineDispatcher()
    val fakeDevice = FakeTransportService.FAKE_DEVICE.toBuilder().apply {
      deviceId = 1
      model = "fakeModel"
      serial = "1"
    }.build()

    val fakeProcess = FakeTransportService.FAKE_PROCESS.toBuilder().apply {
      pid = 1
      deviceId = 1
    }.build()

    lateinit var inspectionView: AppInspectionView
    launch(uiDispatcher) {
      inspectionView = AppInspectionView(projectRule.project, appInspectionServiceRule.apiServices, ideServices,
                                         { listOf(StubTestAppInspectorTabProvider(INSPECTOR_ID)) },
                                         appInspectionServiceRule.scope, uiDispatcher, TestInspectorArtifactService()) {
        it.name == FakeTransportService.FAKE_PROCESS_NAME
      }
      Disposer.register(projectRule.fixture.testRootDisposable, inspectionView)

      // Test initial tabs added.
      inspectionView.tabsChangedFlow
        .first {
          assertThat(inspectionView.inspectorTabs.size).isEqualTo(1)
          val tabShell = inspectionView.inspectorTabs[0]
          val component = tabShell.waitForContent()
          assertThat(component).isNotInstanceOf(EmptyStatePanel::class.java)
          launch(start = CoroutineStart.UNDISPATCHED) {
            assertThat(tabShell.componentUpdates.first()).isInstanceOf(EmptyStatePanel::class.java)
          }
          // Generate fake dispose event
          transportService.addEventToStream(
            fakeDevice.deviceId,
            Common.Event.newBuilder()
              .setPid(fakeProcess.pid)
              .setKind(Common.Event.Kind.APP_INSPECTION_EVENT)
              .setTimestamp(timer.currentTimeNs)
              .setIsEnded(true)
              .setAppInspectionEvent(AppInspection.AppInspectionEvent.newBuilder()
                                       .setInspectorId(INSPECTOR_ID)
                                       .setDisposedEvent(AppInspection.DisposedEvent.getDefaultInstance())
                                       .build())
              .build()
          )
          true
        }
    }

    // Launch a processes and wait for its tab to be created
    transportService.addDevice(fakeDevice)
    transportService.addProcess(fakeDevice, fakeProcess)
  }

  @Test
  fun inspectorTabsAreDisposed_whenUiIsRefreshed() = runBlocking<Unit> {
    val uiDispatcher = EdtExecutorService.getInstance().asCoroutineDispatcher()
    val tabDisposedDeferred = CompletableDeferred<Unit>()
    val offlineTabDisposedDeferred = CompletableDeferred<Unit>()
    val tabProvider = object : AppInspectorTabProvider by StubTestAppInspectorTabProvider(INSPECTOR_ID) {
      override fun createTab(project: Project, ideServices: AppInspectionIdeServices, processDescriptor: ProcessDescriptor,
                             messengerTargets: List<AppInspectorMessengerTarget>, parentDisposable: Disposable): AppInspectorTab {
        return object : AppInspectorTab, Disposable {
          override val messengers: Iterable<AppInspectorMessenger> = listOf(StubTestAppInspectorMessenger())
          override val component = JPanel()
          override fun dispose() {
            tabDisposedDeferred.complete(Unit)
          }

          init {
            Disposer.register(parentDisposable, this)
          }
        }
      }
    }
    val offlineTabProvider = object : AppInspectorTabProvider by StubTestAppInspectorTabProvider(INSPECTOR_ID_2) {
      override fun supportsOffline() = true
      override fun createTab(project: Project, ideServices: AppInspectionIdeServices, processDescriptor: ProcessDescriptor,
                             messengerTargets: List<AppInspectorMessengerTarget>, parentDisposable: Disposable): AppInspectorTab {
        return object : AppInspectorTab, Disposable {
          override val messengers = listOf(StubTestAppInspectorMessenger())
          override val component = JPanel()
          override fun dispose() {
            offlineTabDisposedDeferred.complete(Unit)
          }

          init {
            Disposer.register(parentDisposable, this)
          }
        }
      }
    }

    launch(uiDispatcher) {
      val inspectionView = AppInspectionView(projectRule.project, appInspectionServiceRule.apiServices, ideServices,
                                             { listOf(tabProvider, offlineTabProvider) },
                                             appInspectionServiceRule.scope, uiDispatcher, TestInspectorArtifactService()) {
        it.name == FakeTransportService.FAKE_PROCESS_NAME
      }
      Disposer.register(projectRule.fixture.testRootDisposable, inspectionView)
      var previousTabs = mutableListOf<AppInspectorTabShell>()
      inspectionView.tabsChangedFlow
        .take(3)
        .collectIndexed { i, _ ->
          when (i) {
            0 -> {
              // Stopping the process should cause the first tab to be disposed, but keep the offline tab.
              assertThat(inspectionView.inspectorTabs).hasSize(2)
              inspectionView.inspectorTabs.forEach { it.waitForContent() }
              transportService.stopProcess(FakeTransportService.FAKE_DEVICE, FakeTransportService.FAKE_PROCESS)
              timer.currentTimeNs += 1
              previousTabs.addAll(inspectionView.inspectorTabs)
            }
            1 -> {
              // Making the process go back online should cause the tool window to refresh and dispose of the offline tab.
              assertThat(inspectionView.inspectorTabs).hasSize(1)
              // Verify regardless of tab's offline capability, all messengers are disposed.
              previousTabs.forEach { tab ->
                assertThat(tab.getUserData(TAB_KEY)!!.messengers.first().scope.coroutineContext[Job]!!.isCancelled).isTrue()
              }
              transportService.addProcess(FakeTransportService.FAKE_DEVICE, FakeTransportService.FAKE_PROCESS)
              timer.currentTimeNs += 1
            }
            2 -> {
              // 2 tabs are added back in the tool window.
              assertThat(inspectionView.inspectorTabs).hasSize(2)
            }
          }
        }
    }

    // Launch a processes to start the test
    transportService.addDevice(FakeTransportService.FAKE_DEVICE)
    transportService.addProcess(FakeTransportService.FAKE_DEVICE, FakeTransportService.FAKE_PROCESS)
    timer.currentTimeNs += 1

    tabDisposedDeferred.await()
    offlineTabDisposedDeferred.await()
  }

  @Test
  fun inspectorCrashNotification() = runBlocking<Unit> {
    val uiDispatcher = EdtExecutorService.getInstance().asCoroutineDispatcher()
    val fakeDevice = FakeTransportService.FAKE_DEVICE.toBuilder().setDeviceId(1).setModel("fakeModel").setManufacturer("fakeMan").setSerial(
      "1").build()
    val fakeProcess = FakeTransportService.FAKE_PROCESS.toBuilder().setPid(1).setDeviceId(1).build()

    lateinit var inspectionView: AppInspectionView
    launch(uiDispatcher) {
      inspectionView = AppInspectionView(projectRule.project, appInspectionServiceRule.apiServices, ideServices,
                                         appInspectionServiceRule.scope, uiDispatcher) {
        it.name == FakeTransportService.FAKE_PROCESS_NAME
      }
      Disposer.register(projectRule.fixture.testRootDisposable, inspectionView)

      // Test initial tabs added.
      inspectionView.tabsChangedFlow
        .first {
          assertThat(inspectionView.inspectorTabs.size).isEqualTo(2)
          inspectionView.inspectorTabs.forEach { it.waitForContent() }

          // The tab shell that will be restarted.
          val crashedTabShell = inspectionView.inspectorTabs.first()
          launch(start = CoroutineStart.UNDISPATCHED) {
            assertThat(crashedTabShell.componentUpdates.first()).isInstanceOf(TestAppInspectorTabComponent::class.java)
          }

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
                                       .setDisposedEvent(
                                         AppInspection.DisposedEvent.newBuilder()
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
          true
        }
    }
    // Launch a processes and wait for its tab to be created
    transportService.addDevice(fakeDevice)
    transportService.addProcess(fakeDevice, fakeProcess)
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
    val tabsAdded = CompletableDeferred<Unit>()
    launch(uiDispatcher) {
      inspectionView = AppInspectionView(projectRule.project, appInspectionServiceRule.apiServices, ideServices,
                                         { listOf(StubTestAppInspectorTabProvider(INSPECTOR_ID)) },
                                         appInspectionServiceRule.scope, uiDispatcher, TestInspectorArtifactService()) {
        it.name == FakeTransportService.FAKE_PROCESS_NAME
      }
      Disposer.register(projectRule.fixture.testRootDisposable, inspectionView)

      inspectionView.tabsChangedFlow
        .first {
          assertThat(inspectionView.inspectorTabs.size).isEqualTo(1)
          val tab = inspectionView.inspectorTabs[0]
          val initialComponent = tab.waitForContent()
          assertThat((initialComponent as EmptyStatePanel).reasonText).isEqualTo(
            AppInspectionBundle.message("inspector.launch.error", tab.provider.displayName))

          val restartedComponent = tab.componentUpdates.first()
          assertThat(restartedComponent).isNotSameAs(initialComponent)
          assertThat(restartedComponent).isInstanceOf(TestAppInspectorTabComponent::class.java)
          tabsAdded.complete(Unit)
        }
    }

    // Verify we crashed on launch, failing to open the UI and triggering the toast.
    val notificationData = notificationDataDeferred.await()
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
        appInspectionServiceRule.scope, uiDispatcher, TestInspectorArtifactService()) {
        it.name == FakeTransportService.FAKE_PROCESS_NAME
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

    timer.currentTimeNs += 1
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
        appInspectionServiceRule.scope, uiDispatcher, TestInspectorArtifactService()) {
        it.name == FakeTransportService.FAKE_PROCESS_NAME
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

    timer.currentTimeNs += 1
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
                                             appInspectionServiceRule.scope, uiDispatcher, TestInspectorArtifactService()) {
        it.name == FakeTransportService.FAKE_PROCESS_NAME
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
              (provider.launchConfigs.single().params as LibraryInspectorLaunchParams).minVersionLibraryCoordinate.toString()))

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
  fun launchInspectorFailsDueToAppProguarded_emptyMessageAdded() = runBlocking<Unit> {
    val uiDispatcher = EdtExecutorService.getInstance().asCoroutineDispatcher()
    val tabsAdded = CompletableDeferred<Unit>()
    val provider = TestAppInspectorTabProvider2()
    launch(uiDispatcher) {
      val inspectionView = AppInspectionView(projectRule.project, appInspectionServiceRule.apiServices,
                                             ideServices,
                                             { listOf(provider) },
                                             appInspectionServiceRule.scope, uiDispatcher, TestInspectorArtifactService()) {
        it.name == FakeTransportService.FAKE_PROCESS_NAME
      }
      Disposer.register(projectRule.fixture.testRootDisposable, inspectionView)
      inspectionView.tabsChangedFlow.first {
        assertThat(inspectionView.inspectorTabs.size).isEqualTo(1)
        val tab = inspectionView.inspectorTabs[0]
        tab.waitForContent()
        val emptyPanel = tab.containerPanel.getComponent(0) as EmptyStatePanel
        assertThat(emptyPanel.reasonText).isEqualTo(AppInspectionBundle.message("app.proguarded"))
        tabsAdded.complete(Unit)
      }
    }

    transportService.setCommandHandler(Commands.Command.CommandType.APP_INSPECTION,
                                       TestAppInspectorCommandHandler(timer, createInspectorResponse = createCreateInspectorResponse(
                                         AppInspection.AppInspectionResponse.Status.ERROR,
                                         AppInspection.CreateInspectorResponse.Status.APP_PROGUARDED, "error"))
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
                                             appInspectionServiceRule.scope, uiDispatcher, TestInspectorArtifactService()) {
        it.name == FakeTransportService.FAKE_PROCESS_NAME
      }
      Disposer.register(projectRule.fixture.testRootDisposable, inspectionView)
      inspectionView.tabsChangedFlow.first {
        assertThat(inspectionView.inspectorTabs.size).isEqualTo(1)
        val tab = inspectionView.inspectorTabs[0]
        tab.waitForContent()
        val statePanel = tab.containerPanel.getComponent(0)
        assertThat(statePanel).isInstanceOf(EmptyStatePanel::class.java)
        assertThat((statePanel as EmptyStatePanel).reasonText).isEqualTo(
          AppInspectionBundle.message(
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
  fun launchInspectorFailsBecauseProcessNoLongerExists() = runBlocking<Unit> {
    val uiDispatcher = EdtExecutorService.getInstance().asCoroutineDispatcher()
    val tabsAdded = CompletableDeferred<Unit>()

    val apiServices = object : AppInspectionApiServices by appInspectionServiceRule.apiServices {
      override suspend fun attachToProcess(process: ProcessDescriptor, projectName: String): AppInspectionTarget {
        throw AppInspectionProcessNoLongerExistsException("process no longer exists!")
      }
    }
    launch(uiDispatcher) {
      val inspectionView = AppInspectionView(projectRule.project, apiServices,
                                             ideServices,
                                             { listOf(TestAppInspectorTabProvider2()) },
                                             appInspectionServiceRule.scope, uiDispatcher, TestInspectorArtifactService()) {
        it.name == FakeTransportService.FAKE_PROCESS_NAME
      }
      Disposer.register(projectRule.fixture.testRootDisposable, inspectionView)
      inspectionView.tabsChangedFlow.first {
        assertThat(inspectionView.inspectorTabs).isEmpty()
        val statePanel = inspectionView.inspectorPanel.getComponent(0)
        assertThat(statePanel).isInstanceOf(EmptyStatePanel::class.java)
        assertThat((statePanel as EmptyStatePanel).reasonText).isEqualTo(AppInspectionBundle.message("select.process"))
        tabsAdded.complete(Unit)
      }
    }

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
                                             appInspectionServiceRule.scope, uiDispatcher, TestInspectorArtifactService()) {
        it.name == FakeTransportService.FAKE_PROCESS_NAME
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
            (provider.launchConfigs.single().params as LibraryInspectorLaunchParams).minVersionLibraryCoordinate.toString()))

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
  fun stopInspectionPressed_onlyOfflineInspectorsRemain() = runBlocking {
    val uiDispatcher = EdtExecutorService.getInstance().asCoroutineDispatcher()

    val inspectionView = withContext(uiDispatcher) {
      AppInspectionView(projectRule.project, appInspectionServiceRule.apiServices, ideServices,
                        appInspectionServiceRule.scope, uiDispatcher) {
        it.name == FakeTransportService.FAKE_PROCESS_NAME
      }
    }
    Disposer.register(projectRule.fixture.testRootDisposable, inspectionView)

    val firstProcessReadyDeferred = CompletableDeferred<Unit>()
    val deadProcessAddedDeferred = CompletableDeferred<Unit>()
    launch {
      launch {
        // Add a process.
        transportService.addDevice(FakeTransportService.FAKE_DEVICE)
        transportService.addProcess(FakeTransportService.FAKE_DEVICE, FakeTransportService.FAKE_PROCESS)
      }
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

    // Wait for inspector to be added.
    firstProcessReadyDeferred.await()

    // Stop inspection.
    inspectionView.processesModel.stop()

    // Wait for the offline inspector tabs to be added.
    deadProcessAddedDeferred.await()
  }

  @Test
  fun launchLibraryInspectors() = runBlocking<Unit> {
    val uiDispatcher = EdtExecutorService.getInstance().asCoroutineDispatcher()
    val resolvedInspector = object : StubTestAppInspectorTabProvider(INSPECTOR_ID) {
      override val inspectorLaunchParams = LibraryInspectorLaunchParams(
        TEST_JAR, TEST_ARTIFACT
      )
    }
    val unresolvableLibrary = ArtifactCoordinate("unresolvable", "artifact", "1.0.0", ArtifactCoordinate.Type.JAR)
    val unresolvableInspector = object : StubTestAppInspectorTabProvider(INSPECTOR_ID_2) {
      override val inspectorLaunchParams = LibraryInspectorLaunchParams(
        TEST_JAR, unresolvableLibrary
      )
    }
    val incompatibleLibrary = ArtifactCoordinate("incompatible", "artifact", "INCOMPATIBLE", ArtifactCoordinate.Type.JAR)
    val incompatibleInspector = object : StubTestAppInspectorTabProvider(INSPECTOR_ID_3) {
      override val inspectorLaunchParams = LibraryInspectorLaunchParams(
        TEST_JAR, incompatibleLibrary
      )
    }

    val launchParamsVerifiedDeferred = CompletableDeferred<Unit>()
    val apiServices = object : AppInspectionApiServices by appInspectionServiceRule.apiServices {
      override suspend fun launchInspector(params: LaunchParameters): AppInspectorMessenger {
        // Verify the jar being launched is the one returned by resolver.
        assertThat(params.inspectorJar.releaseDirectory).isEqualTo(Paths.get("path", "to").toString())
        assertThat(params.inspectorJar.name).isEqualTo("inspector.jar")
        launchParamsVerifiedDeferred.complete(Unit)
        return appInspectionServiceRule.apiServices.launchInspector(params)
      }
    }
    launch(uiDispatcher) {
      val inspectionView = AppInspectionView(
        projectRule.project, apiServices,
        ideServices,
        { listOf(resolvedInspector, unresolvableInspector, incompatibleInspector) },
        appInspectionServiceRule.scope,
        uiDispatcher,
        object : InspectorArtifactService {
          override suspend fun getOrResolveInspectorArtifact(artifactCoordinate: ArtifactCoordinate, project: Project): Path {
            return if (artifactCoordinate.groupId == "unresolvable") {
              throw AppInspectionArtifactNotFoundException("not resolved")
            }
            else {
              Paths.get("path/to/inspector.jar")
            }
          }
        }
      ) {
        it.name == FakeTransportService.FAKE_PROCESS_NAME
      }
      Disposer.register(projectRule.fixture.testRootDisposable, inspectionView)
      inspectionView.tabsChangedFlow.first {
        assertThat(inspectionView.inspectorTabs.size).isEqualTo(3)
        inspectionView.inspectorTabs.forEach { inspectorTab ->
          inspectorTab.waitForContent()
          when (inspectorTab.provider) {
            incompatibleInspector -> {
              val emptyPanel = inspectorTab.containerPanel.getComponent(0) as EmptyStatePanel
              assertThat(emptyPanel.reasonText)
                .isEqualTo(
                  AppInspectionBundle.message(
                  "incompatible.version",
                  (inspectorTab.provider.launchConfigs.single().params as LibraryInspectorLaunchParams)
                    .minVersionLibraryCoordinate.toString()))
            }
            unresolvableInspector -> {
              val emptyPanel = inspectorTab.containerPanel.getComponent(0) as EmptyStatePanel
              assertThat(emptyPanel.reasonText)
                .isEqualTo(
                  AppInspectionBundle.message(
                  "unresolved.inspector",
                  (inspectorTab.provider.launchConfigs.single().params as LibraryInspectorLaunchParams)
                    .minVersionLibraryCoordinate.toString()))
            }
            else -> {
              // Verify it's not an info tab - it's an actual inspector tab.
              assertThat(inspectorTab.containerPanel.getComponent(0)).isNotInstanceOf(EmptyStatePanel::class.java)
            }
          }
        }
        true
      }
    }

    transportService.setCommandHandler(
      Commands.Command.CommandType.APP_INSPECTION,
      TestAppInspectorCommandHandler(
        timer,
        getLibraryVersionsResponse = { command ->
          AppInspection.GetLibraryCompatibilityInfoResponse.newBuilder().addAllResponses(
            command.targetLibrariesList.map {
              val builder = AppInspection.LibraryCompatibilityInfo.newBuilder()
                .setTargetLibrary(it.coordinate)
                .setVersion(it.coordinate.version)
              if (it.coordinate.version == "INCOMPATIBLE") {
                builder.status = AppInspection.LibraryCompatibilityInfo.Status.INCOMPATIBLE
              }
              else {
                builder.status = AppInspection.LibraryCompatibilityInfo.Status.COMPATIBLE
              }
              builder.build()
            }
          ).build()
        }
      )
    )

    // Attach to a fake process.
    transportService.addDevice(FakeTransportService.FAKE_DEVICE)
    transportService.addProcess(FakeTransportService.FAKE_DEVICE, FakeTransportService.FAKE_PROCESS)

    launchParamsVerifiedDeferred.await()
  }

  @Test
  fun appInspectionView_canToggleAutoConnectedState() = runBlocking {
    val uiDispatcher = EdtExecutorService.getInstance().asCoroutineDispatcher()

    val inspectionView = withContext(uiDispatcher) {
      AppInspectionView(projectRule.project, appInspectionServiceRule.apiServices, ideServices,
                        appInspectionServiceRule.scope, uiDispatcher) {
        it.name == FakeTransportService.FAKE_PROCESS_NAME
      }
    }
    Disposer.register(projectRule.fixture.testRootDisposable, inspectionView)

    assertThat(inspectionView.autoConnects).isTrue()

    val fakeDevice = FakeTransportService.FAKE_DEVICE
    val fakeProcesses = (1..3).map { i ->
      FakeTransportService.FAKE_PROCESS.toBuilder()
        .setPid(i)
        .setDeviceId(fakeDevice.deviceId)
        .build()
    }.toList()

    // Add a process.
    val selectedProcessChangedQueue = ArrayBlockingQueue<Unit>(1)
    inspectionView.processesModel.addSelectedProcessListeners {
      // This listener is triggered on the test thread
      selectedProcessChangedQueue.add(Unit)
    }

    transportService.addDevice(fakeDevice)
    transportService.addProcess(fakeDevice, fakeProcesses[0])

    // Verify auto connected to initial process
    withContext(Dispatchers.Default) {
      // Note: We need to wait for process changes here (and below) on a non-test thread that's *not* the UI thread, so we
      // don't block the test thread *and* we give the UI thread a chance to respond to the change
      selectedProcessChangedQueue.take()
    }

    withContext(uiDispatcher) {
      assertThat(inspectionView.currentProcess!!.pid).isEqualTo(fakeProcesses[0].pid)
      assertThat(inspectionView.currentProcess!!.isRunning).isTrue()
    }

    // Verify auto connect to new process
    withContext(Dispatchers.Default) {
      inspectionView.stopInspectors()
      selectedProcessChangedQueue.take()
      timer.currentTimeNs += 1
      transportService.addProcess(fakeDevice, fakeProcesses[1])
      selectedProcessChangedQueue.take()
    }

    withContext(uiDispatcher) {
      assertThat(inspectionView.currentProcess!!.pid).isEqualTo(fakeProcesses[1].pid)
      assertThat(inspectionView.currentProcess!!.isRunning).isTrue()
    }

    // Process stop still handled, even if auto connect enabled is set to false
    withContext(uiDispatcher) {
      inspectionView.autoConnects = false
    }

    withContext(Dispatchers.Default) {
      inspectionView.stopInspectors()
      selectedProcessChangedQueue.take()
    }

    withContext(uiDispatcher) {
      assertThat(inspectionView.currentProcess!!.pid).isEqualTo(fakeProcesses[1].pid)
      assertThat(inspectionView.currentProcess!!.isRunning).isFalse()

      // Stopped process is still there even if toggling back to true
      inspectionView.autoConnects = true
      assertThat(inspectionView.currentProcess!!.pid).isEqualTo(fakeProcesses[1].pid)
      assertThat(inspectionView.currentProcess!!.isRunning).isFalse()
    }

    // New process is ignored (as expected) if autoconnection isn't enabled
    withContext(uiDispatcher) {
      inspectionView.autoConnects = false
    }

    withContext(Dispatchers.Default) {
      timer.currentTimeNs += 1
      transportService.addProcess(fakeDevice, fakeProcesses[2])
      selectedProcessChangedQueue.take()
    }

    withContext(uiDispatcher) {
      assertThat(inspectionView.currentProcess!!.pid).isEqualTo(fakeProcesses[1].pid)
      assertThat(inspectionView.currentProcess!!.isRunning).isFalse()
    }

    // New process attaches if the state changes, however
    withContext(uiDispatcher) {
      inspectionView.autoConnects = true

      assertThat(inspectionView.currentProcess!!.pid).isEqualTo(fakeProcesses[2].pid)
      assertThat(inspectionView.currentProcess!!.isRunning).isTrue()
    }
  }

  @Test
  fun remembersLastActiveTab() = runBlocking {
    val uiDispatcher = EdtExecutorService.getInstance().asCoroutineDispatcher()

    val inspectionView = withContext(uiDispatcher) {
      AppInspectionView(projectRule.project, appInspectionServiceRule.apiServices, ideServices,
                        { listOf(TestAppInspectorTabProvider1(), TestAppInspectorTabProvider2()) },
                        appInspectionServiceRule.scope, uiDispatcher, TestInspectorArtifactService()) {
        it.name == FakeTransportService.FAKE_PROCESS_NAME
      }
    }
    Disposer.register(projectRule.fixture.testRootDisposable, inspectionView)

    launch(uiDispatcher) {
      inspectionView.tabsChangedFlow
        .take(3)
        .collectIndexed { i, _ ->
        when (i) {
          0 -> {
            val inspectorTabsPane = inspectionView.inspectorPanel.getComponent(0) as CommonTabbedPane
            assertThat(inspectorTabsPane.selectedIndex).isEqualTo(0)
            inspectionView.inspectorTabs.forEach { it.waitForContent() }
            inspectorTabsPane.selectedIndex = 1
            assertThat(inspectorTabsPane.selectedIndex).isEqualTo(1)
            transportService.stopProcess(FakeTransportService.FAKE_DEVICE, FakeTransportService.FAKE_PROCESS)
            timer.currentTimeNs += 1
          }
          1 -> {
            transportService.addProcess(FakeTransportService.FAKE_DEVICE, FakeTransportService.FAKE_PROCESS)
          }
          2 -> assertThat((inspectionView.inspectorPanel.getComponent(0) as CommonTabbedPane).selectedIndex).isEqualTo(1)
        }
      }
    }

    transportService.addDevice(FakeTransportService.FAKE_DEVICE)
    transportService.addProcess(FakeTransportService.FAKE_DEVICE, FakeTransportService.FAKE_PROCESS)
    timer.currentTimeNs += 1
  }

  @Test
  fun activeTabIsSelected() = runBlocking {
    val uiDispatcher = EdtExecutorService.getInstance().asCoroutineDispatcher()

    val inspectionView = withContext(uiDispatcher) {
      AppInspectionView(projectRule.project, appInspectionServiceRule.apiServices, ideServices,
                        { listOf(TestAppInspectorTabProvider1(), TestAppInspectorTabProvider2()) },
                        appInspectionServiceRule.scope, uiDispatcher, TestInspectorArtifactService()) {
        it.name == FakeTransportService.FAKE_PROCESS_NAME
      }
    }
    Disposer.register(projectRule.fixture.testRootDisposable, inspectionView)

    launch(uiDispatcher) {
      inspectionView.tabsChangedFlow.first()
      val inspectorTabsPane = inspectionView.inspectorPanel.getComponent(0) as CommonTabbedPane
      assertThat(inspectorTabsPane.selectedIndex).isEqualTo(0)
      assertThat(inspectionView.isTabSelected(INSPECTOR_ID)).isTrue()
      assertThat(inspectionView.isTabSelected(INSPECTOR_ID_2)).isFalse()
      inspectorTabsPane.selectedIndex = 1
      assertThat(inspectorTabsPane.selectedIndex).isEqualTo(1)
      assertThat(inspectionView.isTabSelected(INSPECTOR_ID)).isFalse()
      assertThat(inspectionView.isTabSelected(INSPECTOR_ID_2)).isTrue()
    }

    transportService.addDevice(FakeTransportService.FAKE_DEVICE)
    transportService.addProcess(FakeTransportService.FAKE_DEVICE, FakeTransportService.FAKE_PROCESS)
    timer.currentTimeNs += 1
  }
}