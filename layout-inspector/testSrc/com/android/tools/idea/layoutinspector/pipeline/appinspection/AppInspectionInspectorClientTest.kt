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
package com.android.tools.idea.layoutinspector.pipeline.appinspection

import com.android.fakeadbserver.DeviceState
import com.android.flags.junit.SetFlagRule
import com.android.repository.Revision
import com.android.repository.api.LocalPackage
import com.android.repository.api.RemotePackage
import com.android.repository.impl.meta.RepositoryPackages
import com.android.repository.impl.meta.TypeDetails
import com.android.repository.testframework.FakePackage
import com.android.repository.testframework.FakeRepoManager
import com.android.resources.Density
import com.android.sdklib.internal.avd.AvdInfo
import com.android.sdklib.internal.avd.AvdManager
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.sdklib.repository.IdDisplay
import com.android.sdklib.repository.targets.SystemImage
import com.android.sdklib.repository.targets.SystemImage.DEFAULT_TAG
import com.android.sdklib.repository.targets.SystemImage.GOOGLE_APIS_TAG
import com.android.sdklib.repository.targets.SystemImage.PLAY_STORE_TAG
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.testutils.file.createInMemoryFileSystemAndFolder
import com.android.testutils.file.someRoot
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.workbench.ToolWindowCallback
import com.android.tools.app.inspection.AppInspection
import com.android.tools.componenttree.treetable.TreeTableHeader
import com.android.tools.idea.appinspection.inspector.api.AppInspectionAppProguardedException
import com.android.tools.idea.appinspection.inspector.api.AppInspectionArtifactNotFoundException
import com.android.tools.idea.appinspection.inspector.api.AppInspectionCannotFindAdbDeviceException
import com.android.tools.idea.appinspection.inspector.api.AppInspectionLibraryMissingException
import com.android.tools.idea.appinspection.inspector.api.AppInspectionProcessNoLongerExistsException
import com.android.tools.idea.appinspection.inspector.api.AppInspectionServiceException
import com.android.tools.idea.appinspection.inspector.api.AppInspectionVersionIncompatibleException
import com.android.tools.idea.appinspection.inspector.api.process.DeviceDescriptor
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.appinspection.test.DEFAULT_TEST_INSPECTION_STREAM
import com.android.tools.idea.avdmanager.AvdManagerConnection
import com.android.tools.idea.concurrency.waitForCondition
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.layoutinspector.LayoutInspectorRule
import com.android.tools.idea.layoutinspector.MODERN_DEVICE
import com.android.tools.idea.layoutinspector.createProcess
import com.android.tools.idea.layoutinspector.metrics.MetricsTrackerRule
import com.android.tools.idea.layoutinspector.model
import com.android.tools.idea.layoutinspector.model.AndroidWindow
import com.android.tools.idea.layoutinspector.model.ComposeViewNode
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient.Capability
import com.android.tools.idea.layoutinspector.pipeline.InspectorClientLaunchMonitor
import com.android.tools.idea.layoutinspector.pipeline.InspectorClientSettings
import com.android.tools.idea.layoutinspector.pipeline.adb.executeShellCommand
import com.android.tools.idea.layoutinspector.pipeline.appinspection.compose.INCOMPATIBLE_LIBRARY_MESSAGE
import com.android.tools.idea.layoutinspector.pipeline.appinspection.compose.PROGUARDED_LIBRARY_MESSAGE
import com.android.tools.idea.layoutinspector.pipeline.appinspection.inspectors.sendEvent
import com.android.tools.idea.layoutinspector.pipeline.appinspection.view.ViewLayoutInspectorClient
import com.android.tools.idea.layoutinspector.resource.DEFAULT_FONT_SCALE
import com.android.tools.idea.layoutinspector.tree.LayoutInspectorTreePanel
import com.android.tools.idea.layoutinspector.ui.InspectorBanner
import com.android.tools.idea.layoutinspector.ui.InspectorBannerService
import com.android.tools.idea.layoutinspector.util.ComponentUtil
import com.android.tools.idea.layoutinspector.util.ReportingCountDownLatch
import com.android.tools.idea.project.AndroidRunConfigurations
import com.android.tools.idea.protobuf.ByteString
import com.android.tools.idea.run.AndroidRunConfiguration
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.addManifest
import com.android.tools.idea.util.ListenerCollection
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorErrorInfo.AttachErrorCode
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorErrorInfo.AttachErrorState
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorEvent.DynamicLayoutInspectorEventType
import com.intellij.execution.RunManager
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.HyperlinkLabel
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.jetbrains.android.facet.AndroidFacet
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.spy
import org.mockito.Mockito.`when`
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import javax.swing.JPanel
import javax.swing.JTable
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol as ViewProtocol
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol as ComposeProtocol

private val MODERN_PROCESS = MODERN_DEVICE.createProcess(streamId = DEFAULT_TEST_INSPECTION_STREAM.streamId)
private val OTHER_MODERN_PROCESS = MODERN_DEVICE.createProcess(name = "com.other", streamId = DEFAULT_TEST_INSPECTION_STREAM.streamId)

/** Timeout used in this test. While debugging, you may want to extend the timeout */
private const val TIMEOUT = 10L
private val TIMEOUT_UNIT = TimeUnit.SECONDS

class AppInspectionInspectorClientTest {
  private val monitor = mock<InspectorClientLaunchMonitor>()
  private var preferredProcess: ProcessDescriptor? = MODERN_PROCESS

  private val disposableRule = DisposableRule()
  private val treeRule = SetFlagRule(StudioFlags.USE_COMPONENT_TREE_TABLE, true)
  private val projectRule: AndroidProjectRule = AndroidProjectRule.onDisk()
  private val inspectionRule = AppInspectionInspectorRule(disposableRule.disposable, projectRule)
  private val inspectorRule = LayoutInspectorRule(listOf(inspectionRule.createInspectorClientProvider { monitor }), projectRule) {
    it == preferredProcess
  }
  private val usageRule = MetricsTrackerRule()

  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule)
    .around(inspectionRule)
    .around(inspectorRule)
    .around(treeRule)
    .around(usageRule)
    .around(disposableRule)!!

  @Before
  fun before() {
    inspectorRule.attachDevice(MODERN_DEVICE)
  }

  @Test
  fun clientCanConnectDisconnectAndReconnect() {
    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    assertThat(inspectorRule.inspectorClient.isConnected).isTrue()

    inspectorRule.processNotifier.fireDisconnected(MODERN_PROCESS)
    assertThat(inspectorRule.inspectorClient.isConnected).isFalse()

    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    assertThat(inspectorRule.inspectorClient.isConnected).isTrue()
  }

  @Test
  fun treeRecompositionVisibilitySetAtConnectTime() {
    val panel = LayoutInspectorTreePanel(disposableRule.disposable)
    var updateActionsCalled = 0
    var enabledActions = 0
    panel.registerCallbacks(object : ToolWindowCallback {
      override fun updateActions() {
        enabledActions = 0
        panel.additionalActions.forEach {
          val event: AnActionEvent = mock()
          val presentation = it.templatePresentation.clone()
          `when`(event.presentation).thenReturn(presentation)
          it.update(event)
          if (event.presentation.isEnabled) {
            enabledActions++
          }
        }
        updateActionsCalled++
      }
    })
    panel.setToolContext(inspectorRule.inspector)
    FakeUi(panel.component, createFakeWindow = true)
    inspectorRule.inspector.treeSettings.showRecompositions = true
    inspectorRule.inspector.treeSettings.hideSystemNodes = false

    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)

    // Wait for client to be connected
    waitForCondition(TIMEOUT, TIMEOUT_UNIT) { inspectorRule.inspectorClient.isConnected }

    // Wait for the tool window to update its actions.
    // There is nothing we directly can rely on to be sure of the table column visibility update.
    // The code below is a hack since the check relies on toolWindowCallback.updateActions is
    // called (delayed) to the UI thread. Because of that we ensure that LayoutInspector.updateConnection
    // will have finished processing after the state is set to CONNECTED.
    waitForCondition(TIMEOUT, TIMEOUT_UNIT) { updateActionsCalled > 0 }

    // Make sure all UI events are done
    runInEdtAndWait { UIUtil.dispatchAllInvocationEvents() }

    // Check that the table header for showing recompositions are shown initially:
    val header = ComponentUtil.flatten(panel.component).filterIsInstance<TreeTableHeader>().single()
    val table = panel.focusComponent as JTable
    assertThat(header.isVisible).isTrue()
    assertThat(table.columnCount).isEqualTo(3)
    assertThat(table.getColumn(table.getColumnName(1)).maxWidth).isGreaterThan(0)
    assertThat(table.getColumn(table.getColumnName(2)).maxWidth).isGreaterThan(0)

    // Check that all 3 actions were enabled initially:
    assertThat(updateActionsCalled).isEqualTo(1)
    assertThat(enabledActions).isEqualTo(3)
  }

  @Test
  fun allStatesReachedDuringConnect() {
    // Validate all the progress events happen in order. Note that those generated on the device side are synthetic, since we're not
    // using the real agent in this test.
    // TODO(b/203712328): Because of a problem with the test framework, this test will pass even without the fix included in the same commit

    val modelUpdatedLatch = ReportingCountDownLatch(1)
    inspectorRule.inspectorModel.modificationListeners.add { _, _, _ ->
      modelUpdatedLatch.countDown()
    }

    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    modelUpdatedLatch.await(TIMEOUT, TIMEOUT_UNIT)

    assertThat(inspectorRule.inspectorClient.isConnected).isTrue()
    val inOrder = inOrder(monitor)
    inOrder.verify(monitor).updateProgress(AttachErrorState.ADB_PING)
    inOrder.verify(monitor).updateProgress(AttachErrorState.ATTACH_SUCCESS)
    inOrder.verify(monitor).updateProgress(AttachErrorState.START_REQUEST_SENT)
    inOrder.verify(monitor).updateProgress(AttachErrorState.START_RECEIVED)
    inOrder.verify(monitor).updateProgress(AttachErrorState.ROOTS_EVENT_SENT)
    inOrder.verify(monitor).updateProgress(AttachErrorState.ROOTS_EVENT_RECEIVED)
    inOrder.verify(monitor).updateProgress(AttachErrorState.VIEW_INVALIDATION_CALLBACK)
    inOrder.verify(monitor).updateProgress(AttachErrorState.SCREENSHOT_CAPTURED)
    inOrder.verify(monitor).updateProgress(AttachErrorState.VIEW_HIERARCHY_CAPTURED)
    inOrder.verify(monitor).updateProgress(AttachErrorState.RESPONSE_SENT)
    inOrder.verify(monitor).updateProgress(AttachErrorState.LAYOUT_EVENT_RECEIVED)
    inOrder.verify(monitor).updateProgress(AttachErrorState.COMPOSE_REQUEST_SENT)
    inOrder.verify(monitor).updateProgress(AttachErrorState.COMPOSE_RESPONSE_RECEIVED)
    inOrder.verify(monitor).updateProgress(AttachErrorState.PARSED_COMPONENT_TREE)
    inOrder.verify(monitor).updateProgress(AttachErrorState.MODEL_UPDATED)
  }

  @Test
  fun inspectorStartsFetchingContinuouslyOnConnectIfLiveMode() = runBlocking {
    InspectorClientSettings.isCapturingModeOn = true

    val startFetchReceived = ReportingCountDownLatch(1)
    inspectionRule.viewInspector.listenWhen({ it.hasStartFetchCommand() }) { command ->
      assertThat(command.startFetchCommand.continuous).isTrue()
      startFetchReceived.countDown()
    }

    // Initial fetch additionally triggers requests for composables
    val composeCommands = ArrayBlockingQueue<ComposeProtocol.Command>(2)
    inspectionRule.composeInspector.listenWhen({ true }) { command ->
      composeCommands.add(command)
    }

    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    startFetchReceived.await(TIMEOUT, TIMEOUT_UNIT) // If here, we already successfully connected (and sent an initial command)
    assertThat(inspectorRule.inspectorClient).isInstanceOf(AppInspectionInspectorClient::class.java)
    assertThat(inspectorRule.inspectorClient.capabilities).contains(Capability.SUPPORTS_COMPOSE_RECOMPOSITION_COUNTS)

    // View Inspector layout event -> Compose Inspector update settings command
    composeCommands.take().let { command ->
      assertThat(command.specializedCase).isEqualTo(ComposeProtocol.Command.SpecializedCase.UPDATE_SETTINGS_COMMAND)
      assertThat(command.updateSettingsCommand.includeRecomposeCounts).isFalse()
      assertThat(command.updateSettingsCommand.delayParameterExtractions).isTrue()
    }
    // View Inspector layout event -> Compose Inspector get composables commands
    composeCommands.take().let { command ->
      assertThat(command.specializedCase).isEqualTo(ComposeProtocol.Command.SpecializedCase.GET_COMPOSABLES_COMMAND)
    }

    inspectorRule.inspector.treeSettings.showRecompositions = true
    (inspectorRule.inspectorClient as AppInspectionInspectorClient).updateRecompositionCountSettings()

    composeCommands.take().let { command ->
      assertThat(command.specializedCase).isEqualTo(ComposeProtocol.Command.SpecializedCase.UPDATE_SETTINGS_COMMAND)
      assertThat(command.updateSettingsCommand.includeRecomposeCounts).isTrue()
      assertThat(command.updateSettingsCommand.delayParameterExtractions).isTrue()
    }
  }

  @Test
  fun statsInitializedWhenConnectedA() {
    inspectorRule.inspector.treeSettings.hideSystemNodes = true
    inspectorRule.inspector.treeSettings.showRecompositions = false

    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    waitForCondition(TIMEOUT, TIMEOUT_UNIT) { inspectorRule.inspectorClient.stats.hideSystemNodes }

    inspectorRule.inspectorClient.stats.selectionMadeFromImage(null)
    inspectorRule.inspectorClient.stats.frameReceived()
    inspectorRule.launcher.disconnectActiveClient()

    val session1 = usageRule.testTracker.usages
      .single { it.studioEvent.dynamicLayoutInspectorEvent.type == DynamicLayoutInspectorEventType.SESSION_DATA }
      .studioEvent.dynamicLayoutInspectorEvent.session
    assertThat(session1.system.clicksWithVisibleSystemViews).isEqualTo(0)
    assertThat(session1.system.clicksWithHiddenSystemViews).isEqualTo(1)
    assertThat(session1.compose.framesWithRecompositionCountsOn).isEqualTo(0)
  }

  @Test
  fun statsInitializedWhenConnectedB() {
    // Make the start settings opposite from statsInitializedWhenConnectedA:
    inspectorRule.inspector.treeSettings.hideSystemNodes = false
    inspectorRule.inspector.treeSettings.showRecompositions = true

    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    waitForCondition(TIMEOUT, TIMEOUT_UNIT) { inspectorRule.inspectorClient.stats.showRecompositions }

    inspectorRule.inspectorClient.stats.selectionMadeFromImage(null)
    inspectorRule.inspectorClient.stats.frameReceived()
    inspectorRule.launcher.disconnectActiveClient()

    val session2 = usageRule.testTracker.usages
      .single { it.studioEvent.dynamicLayoutInspectorEvent.type == DynamicLayoutInspectorEventType.SESSION_DATA }
      .studioEvent.dynamicLayoutInspectorEvent.session
    assertThat(session2.system.clicksWithVisibleSystemViews).isEqualTo(1)
    assertThat(session2.system.clicksWithHiddenSystemViews).isEqualTo(0)
    assertThat(session2.compose.framesWithRecompositionCountsOn).isEqualTo(1)
  }

  @Test
  fun recomposingNotSupported() = runBlocking {
    val inspectorState = FakeInspectorState(inspectionRule.viewInspector, inspectionRule.composeInspector)
    inspectorState.simulateComposeVersionWithoutUpdateSettingsCommand()

    InspectorClientSettings.isCapturingModeOn = true
    inspectorRule.inspector.treeSettings.showRecompositions = true

    val startFetchReceived = ReportingCountDownLatch(1)
    inspectionRule.viewInspector.listenWhen({ it.hasStartFetchCommand() }) { command ->
      assertThat(command.startFetchCommand.continuous).isTrue()
      startFetchReceived.countDown()
    }

    // Initial fetch additionally triggers requests for composables
    val composeCommands = ArrayBlockingQueue<ComposeProtocol.Command>(2)
    inspectionRule.composeInspector.listenWhen({ true }) { command ->
      composeCommands.add(command)
    }

    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    startFetchReceived.await(TIMEOUT, TIMEOUT_UNIT) // If here, we already successfully connected (and sent an initial command)
    assertThat(inspectorRule.inspectorClient).isInstanceOf(AppInspectionInspectorClient::class.java)
    assertThat(inspectorRule.inspectorClient.capabilities).doesNotContain(Capability.SUPPORTS_COMPOSE_RECOMPOSITION_COUNTS)

    // View Inspector layout event -> Compose Inspector update settings command
    composeCommands.take().let { command ->
      assertThat(command.specializedCase).isEqualTo(ComposeProtocol.Command.SpecializedCase.UPDATE_SETTINGS_COMMAND)
      assertThat(command.updateSettingsCommand.includeRecomposeCounts).isTrue()
      assertThat(command.updateSettingsCommand.delayParameterExtractions).isTrue()
    }
    // View Inspector layout event -> Compose Inspector get composables commands
    composeCommands.take().let { command ->
      assertThat(command.specializedCase).isEqualTo(ComposeProtocol.Command.SpecializedCase.GET_COMPOSABLES_COMMAND)
    }
  }

  @Test
  fun inspectorRequestsSingleFetchIfSnapshotMode() = runBlocking {
    InspectorClientSettings.isCapturingModeOn = false

    val startFetchReceived = ReportingCountDownLatch(1)
    inspectionRule.viewInspector.listenWhen({ it.hasStartFetchCommand() }) { command ->
      assertThat(command.startFetchCommand.continuous).isFalse()
      startFetchReceived.countDown()
    }

    // Initial fetch additionally triggers requests for composables
    val composeCommands = ArrayBlockingQueue<ComposeProtocol.Command>(3)
    inspectionRule.composeInspector.listenWhen({ true }) { command ->
      composeCommands.add(command)
    }

    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    startFetchReceived.await(TIMEOUT, TIMEOUT_UNIT) // If here, we already successfully connected (and sent an initial command)
    assertThat(inspectorRule.inspectorClient).isInstanceOf(AppInspectionInspectorClient::class.java)

    // View Inspector layout event -> Compose Inspector get update settings command
    composeCommands.take().let { command ->
      assertThat(command.specializedCase).isEqualTo(ComposeProtocol.Command.SpecializedCase.UPDATE_SETTINGS_COMMAND)
    }
    // View Inspector layout event -> Compose Inspector get composables command
    composeCommands.take().let { command ->
      assertThat(command.specializedCase).isEqualTo(ComposeProtocol.Command.SpecializedCase.GET_COMPOSABLES_COMMAND)
    }
    // View Inspector properties event -> Compose Inspector get all parameters
    composeCommands.take().let { command ->
      assertThat(command.specializedCase).isEqualTo(ComposeProtocol.Command.SpecializedCase.GET_ALL_PARAMETERS_COMMAND)
    }
  }

  @Test
  fun testViewDebugAttributesApplicationPackageSetAndReset() = runWithFlagState(false) {
    inspectorRule.attachDevice(MODERN_DEVICE)
    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    assertThat(inspectorRule.adbProperties.debugViewAttributesApplicationPackage).isEqualTo(MODERN_PROCESS.name)

    // Imitate that the adb server was killed.
    // We expect the ViewDebugAttributes to be cleared anyway since a new adb bridge should be created.
    inspectorRule.adbService.killServer()

    // Disconnect directly instead of calling fireDisconnected - otherwise, we don't have an easy way to wait for the disconnect to
    // happen on a background thread
    inspectorRule.launcher.disconnectActiveClient()
    assertThat(inspectorRule.adbProperties.debugViewAttributesApplicationPackage).isNull()
    // No other attributes were modified
    assertThat(inspectorRule.adbProperties.debugViewAttributesChangesCount).isEqualTo(2)
  }

  @Test
  fun testViewDebugAttributesApplicationUntouchedIfAlreadySet() = runWithFlagState(false) {
    inspectorRule.adbProperties.debugViewAttributesApplicationPackage = MODERN_PROCESS.name

    inspectorRule.attachDevice(MODERN_DEVICE)
    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    assertThat(inspectorRule.adbProperties.debugViewAttributesChangesCount).isEqualTo(0)
    assertThat(inspectorRule.adbProperties.debugViewAttributesApplicationPackage).isEqualTo(MODERN_PROCESS.name)

    // Disconnect directly instead of calling fireDisconnected - otherwise, we don't have an easy way to wait for the disconnect to
    // happen on a background thread
    inspectorRule.launcher.disconnectActiveClient()
    assertThat(inspectorRule.adbProperties.debugViewAttributesChangesCount).isEqualTo(0)
    assertThat(inspectorRule.adbProperties.debugViewAttributesApplicationPackage).isEqualTo(MODERN_PROCESS.name)
  }

  @Test
  fun testViewDebugAttributesApplicationPackageOverriddenAndReset() = runWithFlagState(false) {
    inspectorRule.attachDevice(MODERN_PROCESS.device)
    inspectorRule.adbRule.bridge.executeShellCommand(MODERN_PROCESS.device,
                                                     "settings put global debug_view_attributes_application_package com.example.another-app")

    assertThat(inspectorRule.adbProperties.debugViewAttributesApplicationPackage).isEqualTo("com.example.another-app")

    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    assertThat(inspectorRule.adbProperties.debugViewAttributesApplicationPackage).isEqualTo(MODERN_PROCESS.name)

    inspectorRule.launcher.disconnectActiveClient()
    assertThat(inspectorRule.adbProperties.debugViewAttributesApplicationPackage).isNull()
  }

  @Test
  fun testViewDebugAttributesApplicationPackageNotOverriddenIfMatching() = runWithFlagState(false) {
    inspectorRule.attachDevice(MODERN_PROCESS.device)
    inspectorRule.adbRule.bridge.executeShellCommand(MODERN_PROCESS.device,
                                                     "settings put global debug_view_attributes_application_package ${MODERN_PROCESS.name}")

    assertThat(inspectorRule.adbProperties.debugViewAttributesApplicationPackage).isEqualTo(MODERN_PROCESS.name)
    assertThat(inspectorRule.adbProperties.debugViewAttributesChangesCount).isEqualTo(1)

    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    assertThat(inspectorRule.adbProperties.debugViewAttributesApplicationPackage).isEqualTo(MODERN_PROCESS.name)
    assertThat(inspectorRule.adbProperties.debugViewAttributesChangesCount).isEqualTo(1)

    inspectorRule.launcher.disconnectActiveClient()
    assertThat(inspectorRule.adbProperties.debugViewAttributesApplicationPackage).isEqualTo(MODERN_PROCESS.name)
    assertThat(inspectorRule.adbProperties.debugViewAttributesChangesCount).isEqualTo(1)
  }

  @Test
  fun testPerDeviceViewDebugAttributesSetAndNotReset() = runWithFlagState(true) {
    inspectorRule.attachDevice(MODERN_DEVICE)
    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    assertThat(inspectorRule.adbProperties.debugViewAttributesApplicationPackage).isNull()
    assertThat(inspectorRule.adbProperties.debugViewAttributes).isEqualTo("1")

    // Imitate that the adb server was killed.
    // We expect the ViewDebugAttributes to be cleared anyway since a new adb bridge should be created.
    inspectorRule.adbService.killServer()

    // Disconnect directly instead of calling fireDisconnected - otherwise, we don't have an easy way to wait for the disconnect to
    // happen on a background thread
    inspectorRule.launcher.disconnectActiveClient()
    assertThat(inspectorRule.adbProperties.debugViewAttributesApplicationPackage).isNull()
    assertThat(inspectorRule.adbProperties.debugViewAttributes).isEqualTo("1")
    // No other attributes were modified
    assertThat(inspectorRule.adbProperties.debugViewAttributesChangesCount).isEqualTo(1)
  }

  @Test
  fun testPerDeviceViewDebugAttributesUntouchedIfAlreadySet() = runWithFlagState(true) {
    inspectorRule.adbProperties.debugViewAttributes = "1"

    inspectorRule.attachDevice(MODERN_DEVICE)
    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    assertThat(inspectorRule.adbProperties.debugViewAttributesChangesCount).isEqualTo(0)
    assertThat(inspectorRule.adbProperties.debugViewAttributesApplicationPackage).isNull()
    assertThat(inspectorRule.adbProperties.debugViewAttributes).isEqualTo("1")

    // Disconnect directly instead of calling fireDisconnected - otherwise, we don't have an easy way to wait for the disconnect to
    // happen on a background thread
    inspectorRule.launcher.disconnectActiveClient()
    assertThat(inspectorRule.adbProperties.debugViewAttributesChangesCount).isEqualTo(0)
    assertThat(inspectorRule.adbProperties.debugViewAttributesApplicationPackage).isNull()
    assertThat(inspectorRule.adbProperties.debugViewAttributes).isEqualTo("1")
  }

  @Test
  fun inspectorSendsStopFetchCommand() = runBlocking {
    val stopFetchReceived = CompletableDeferred<Unit>()
    inspectionRule.viewInspector.listenWhen({ it.hasStopFetchCommand() }) {
      stopFetchReceived.complete(Unit)
    }

    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    inspectorRule.inspectorClient.stopFetching()
    stopFetchReceived.await()
  }

  @Test
  fun inspectorFiresErrorOnErrorEvent() = runBlocking {
    val startFetchError = "Failed to start fetching or whatever"

    inspectionRule.viewInspector.listenWhen({ it.hasStartFetchCommand() }) {
      inspectionRule.viewInspector.connection.sendEvent {
        errorEventBuilder.apply {
          message = startFetchError
        }
      }

      ViewProtocol.Response.newBuilder().setStartFetchResponse(ViewProtocol.StartFetchResponse.getDefaultInstance()).build()
    }

    val error = CompletableDeferred<String>()
    inspectorRule.launcher.addClientChangedListener { client ->
      client.registerErrorCallback { error.complete(it) }
    }
    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    assertThat(error.await()).isEqualTo(startFetchError)
    assertThat(InspectorBannerService.getInstance(inspectorRule.project).notification?.message)
      .isEqualTo("Failed to start fetching or whatever")
  }

  @Test
  fun composeClientShowsMessageIfOlderComposeUiLibrary() {
    inspectionRule.composeInspector.createResponseStatus = AppInspection.CreateInspectorResponse.Status.VERSION_INCOMPATIBLE
    val banner = InspectorBanner(inspectorRule.project)

    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    invokeAndWaitIfNeeded { UIUtil.dispatchAllInvocationEvents() }
    assertThat(banner.text.text).isEqualTo(INCOMPATIBLE_LIBRARY_MESSAGE)
  }

  @Test
  fun composeClientShowsMessageIfProguardedComposeUiLibrary() {
    inspectionRule.composeInspector.createResponseStatus = AppInspection.CreateInspectorResponse.Status.APP_PROGUARDED
    val banner = InspectorBanner(inspectorRule.project)

    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    invokeAndWaitIfNeeded { UIUtil.dispatchAllInvocationEvents() }

    assertThat(banner.text.text).isEqualTo(PROGUARDED_LIBRARY_MESSAGE)
  }

  @Test
  fun inspectorTreeEventIncludesUpdateScreenshotTypeCallback() {
    val screenshotTypeUpdated = ReportingCountDownLatch(1)
    inspectionRule.viewInspector.listenWhen({ it.hasUpdateScreenshotTypeCommand() }) { command ->
      assertThat(command.updateScreenshotTypeCommand.type).isEqualTo(ViewProtocol.Screenshot.Type.BITMAP)
      assertThat(command.updateScreenshotTypeCommand.scale).isEqualTo(1.0f)
      screenshotTypeUpdated.countDown()
    }

    inspectorRule.launcher.addClientChangedListener { client ->
      client.registerTreeEventCallback {
        (client as AppInspectionInspectorClient).updateScreenshotType(AndroidWindow.ImageType.BITMAP_AS_REQUESTED)
      }
    }

    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    screenshotTypeUpdated.await(TIMEOUT, TIMEOUT_UNIT)
  }

  @Test
  fun viewClientOnlyHandlesMostRecentLayoutEvent() {
    // This test will send two batches of layout events, to verify that we not only process the last event of a
    // batch, but that once processed, new events can be handled afterwards.
    var handlingFirstBatch = true

    inspectionRule.viewInspector.interceptWhen({ it.hasStartFetchCommand() }) {
      inspectionRule.viewInspector.connection.sendEvent {
        // We must always send roots at least once before the very first layout event
        rootsEventBuilder.apply {
          addIds(1)
        }
      }

      for (i in 0..10) {
        inspectionRule.viewInspector.connection.sendEvent {
          layoutEventBuilder.apply {
            rootViewBuilder.apply {
              id = 1
            }
            screenshotBuilder.apply {
              bytes = ByteString.copyFrom(byteArrayOf(i.toByte()))
            }
          }
        }
      }

      ViewProtocol.Response.newBuilder().setStartFetchResponse(ViewProtocol.StartFetchResponse.getDefaultInstance()).build()
    }

    inspectionRule.viewInspector.interceptWhen({ it.hasStopFetchCommand() }) {
      // Note: We don't normally spam a bunch of layout events when you stop fetching, but we do it
      // here for convenience, as stopFetching is easy to trigger in the test.
      for (i in 11..20) {
        inspectionRule.viewInspector.connection.sendEvent {
          layoutEventBuilder.apply {
            rootViewBuilder.apply {
              id = 1
            }
            screenshotBuilder.apply {
              bytes = ByteString.copyFrom(byteArrayOf(i.toByte()))
            }
          }
        }
      }

      ViewProtocol.Response.newBuilder().setStopFetchResponse(ViewProtocol.StopFetchResponse.getDefaultInstance()).build()
    }

    var sawInitialFirstBatchEvent = false
    var sawInitialSecondBatchEvent = false
    val treeEventsHandled = ReportingCountDownLatch(1)
    inspectorRule.launcher.addClientChangedListener { client ->
      client.registerTreeEventCallback { data ->
        (data as ViewLayoutInspectorClient.Data).viewEvent.let { viewEvent ->
          assertThat(viewEvent.rootView.id).isEqualTo(1)

          if (handlingFirstBatch) {
            if (!sawInitialFirstBatchEvent && viewEvent.screenshot.bytes.byteAt(0) < 10) {
              // This will get called at some point between when we start generating events and after we get to event 10.
              // If we haven't gotten to event 10 yet, wait a little until the rest can be ready for us. After this we should expect the
              // next available event to be 10.
              sawInitialFirstBatchEvent = true
              Thread.sleep(100)
            }
            else {
              handlingFirstBatch = false
              assertThat(viewEvent.screenshot.bytes.byteAt(0)).isEqualTo(10.toByte())
              client.stopFetching() // Triggers second batch of layout events
            }
          }
          else {
            if (!sawInitialSecondBatchEvent && viewEvent.screenshot.bytes.byteAt(0) < 20) {
              // Same as above with the second batch of events, up to event 20.
              sawInitialSecondBatchEvent = true
              Thread.sleep(100)
            }
            else {
              assertThat(viewEvent.screenshot.bytes.byteAt(0)).isEqualTo(20.toByte())
              treeEventsHandled.countDown()
            }
          }
        }
      }
    }

    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS) // Triggers first batch of layout events
    treeEventsHandled.await(TIMEOUT, TIMEOUT_UNIT)
  }

  @Test
  fun testCapabilitiesUpdateWithoutComposeNodes() {
    val inspectorState = FakeInspectorState(inspectionRule.viewInspector, inspectionRule.composeInspector)
    inspectorState.createFakeViewTree()

    val modelUpdatedLatch = ReportingCountDownLatch(2) // We'll get two tree layout events on start fetch
    inspectorRule.inspectorModel.modificationListeners.add { _, _, _ ->
      modelUpdatedLatch.countDown()
    }

    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    modelUpdatedLatch.await(TIMEOUT, TIMEOUT_UNIT)
    assertThat(inspectorRule.inspectorClient.capabilities).containsNoneOf(Capability.SUPPORTS_COMPOSE, Capability.SUPPORTS_SEMANTICS)
  }

  @Test
  fun testCapabilitiesUpdateWithComposeNodes() {
    val inspectorState = FakeInspectorState(inspectionRule.viewInspector, inspectionRule.composeInspector)
    inspectorState.createFakeViewTree()
    inspectorState.createFakeComposeTree(withSemantics = false)

    val modelUpdatedLatch = ReportingCountDownLatch(2) // We'll get two tree layout events on start fetch
    inspectorRule.inspectorModel.modificationListeners.add { _, _, _ ->
      modelUpdatedLatch.countDown()
    }

    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    modelUpdatedLatch.await(TIMEOUT, TIMEOUT_UNIT)
    assertThat(inspectorRule.inspectorClient.capabilities).contains(Capability.SUPPORTS_COMPOSE)
    assertThat(inspectorRule.inspectorClient.capabilities).doesNotContain(Capability.SUPPORTS_SEMANTICS)
  }

  @Test
  fun testCapabilitiesUpdateWithComposeNodesWithSemantics() {
    val inspectorState = FakeInspectorState(inspectionRule.viewInspector, inspectionRule.composeInspector)
    inspectorState.createFakeViewTree()
    inspectorState.createFakeComposeTree(withSemantics = true)

    val modelUpdatedLatch = ReportingCountDownLatch(2) // We'll get two tree layout events on start fetch
    inspectorRule.inspectorModel.modificationListeners.add { _, _, _ ->
      modelUpdatedLatch.countDown()
    }

    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    modelUpdatedLatch.await(TIMEOUT, TIMEOUT_UNIT)
    assertThat(inspectorRule.inspectorClient.capabilities).containsAllOf(Capability.SUPPORTS_COMPOSE, Capability.SUPPORTS_SEMANTICS)
  }

  @Test
  fun testTextViewUnderComposeNode() {
    val inspectorState = FakeInspectorState(inspectionRule.viewInspector, inspectionRule.composeInspector)
    inspectorState.createFakeViewTree()
    inspectorState.createFakeComposeTree()
    val modelUpdatedLatch = ReportingCountDownLatch(2) // We'll get two tree layout events on start fetch
    inspectorRule.inspectorModel.modificationListeners.add { _, _, _ ->
      modelUpdatedLatch.countDown()
    }

    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    modelUpdatedLatch.await(TIMEOUT, TIMEOUT_UNIT)

    // Verify that the MaterialTextView from the views were placed under the ComposeViewNode: "ComposeNode" with id of -7
    val composeNode = inspectorRule.inspectorModel[-7]!!
    ViewNode.readAccess {
      assertThat(composeNode.parent?.qualifiedName).isEqualTo("AndroidView")
      assertThat(composeNode.qualifiedName).isEqualTo("ComposeNode")
      assertThat(composeNode.children.single().qualifiedName).isEqualTo("com.google.android.material.textview.MaterialTextView")

      // Also verify that the ComposeView do not contain the MaterialTextView nor the RippleContainer in its children:
      val composeView = inspectorRule.inspectorModel[6]!!
      assertThat(composeView.qualifiedName).isEqualTo("androidx.compose.ui.platform.ComposeView")
      val surface = composeView.children.single() as ComposeViewNode
      assertThat(surface.qualifiedName).isEqualTo("Surface")
      assertThat(surface.recompositions.count).isEqualTo(7)
      assertThat(surface.recompositions.skips).isEqualTo(14)
    }
  }

  @Test
  fun errorShownOnConnectException() {
    InspectorClientSettings.isCapturingModeOn = true
    val banner = InspectorBanner(inspectorRule.project)
    inspectionRule.viewInspector.interceptWhen({ it.hasStartFetchCommand() }) {
      com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.Response.newBuilder().apply {
        startFetchResponseBuilder.error = "here's my error"
      }.build()
    }
    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    invokeAndWaitIfNeeded { UIUtil.dispatchAllInvocationEvents() }
    assertThat(banner.text.text).isEqualTo("here's my error")
    assertThat(inspectorRule.inspectorClient.isConnected).isFalse()
  }

  @Test
  fun errorShownOnRefreshException() {
    InspectorClientSettings.isCapturingModeOn = false
    val banner = InspectorBanner(inspectorRule.project)
    inspectionRule.viewInspector.interceptWhen({ it.hasStartFetchCommand() }) {
      com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.Response.newBuilder().apply {
        startFetchResponseBuilder.error = "here's my error"
      }.build()
    }

    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    invokeAndWaitIfNeeded { UIUtil.dispatchAllInvocationEvents() }
    assertThat(banner.text.text).isEqualTo("here's my error")
    assertThat(inspectorRule.inspectorClient.isConnected).isFalse()
  }

  @Test
  fun testActivityRestartBannerShown() {
    setUpRunConfiguration()
    preferredProcess = null
    inspectorRule.attachDevice(MODERN_PROCESS.device)
    val banner = InspectorBanner(inspectorRule.project)
    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    inspectorRule.processes.selectedProcess = MODERN_PROCESS
    verifyActivityRestartBanner(banner, runConfigActionExpected = true)
  }

  @Test
  fun testNoActivityRestartBannerShownIfOptedOut() {
    setUpRunConfiguration()
    preferredProcess = null
    inspectorRule.attachDevice(MODERN_PROCESS.device)
    val banner = InspectorBanner(inspectorRule.project)
    PropertiesComponent.getInstance().setValue(KEY_HIDE_ACTIVITY_RESTART_BANNER, true)
    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    inspectorRule.processes.selectedProcess = MODERN_PROCESS
    invokeAndWaitIfNeeded { UIUtil.dispatchAllInvocationEvents() }

    assertThat(banner.isVisible).isFalse()
  }

  @Test
  fun testOptOutOfActivityRestartBanner() {
    setUpRunConfiguration()
    preferredProcess = null
    inspectorRule.attachDevice(MODERN_PROCESS.device)
    val banner = InspectorBanner(inspectorRule.project)
    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    inspectorRule.processes.selectedProcess = MODERN_PROCESS
    invokeAndWaitIfNeeded { UIUtil.dispatchAllInvocationEvents() }

    val actionPanel = banner.components[1] as JPanel
    val doNotShowAction = actionPanel.components[1] as HyperlinkLabel
    doNotShowAction.doClick()
    assertThat(PropertiesComponent.getInstance().getBoolean(KEY_HIDE_ACTIVITY_RESTART_BANNER)).isTrue()
  }

  @Test
  fun testNoActivityRestartBannerShownDuringAutoConnect() {
    setUpRunConfiguration()
    inspectorRule.attachDevice(MODERN_PROCESS.device)
    val banner = InspectorBanner(inspectorRule.project)
    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    invokeAndWaitIfNeeded { UIUtil.dispatchAllInvocationEvents() }
    assertThat(banner.isVisible).isFalse()
  }

  @Test
  fun testNoActivityRestartBannerShownWhenDebugAttributesAreAlreadySet() = runWithFlagState(false) {
    inspectorRule.adbProperties.debugViewAttributesApplicationPackage = MODERN_PROCESS.name
    setUpRunConfiguration()
    preferredProcess = null
    inspectorRule.attachDevice(MODERN_PROCESS.device)
    val banner = InspectorBanner(inspectorRule.project)
    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    inspectorRule.processes.selectedProcess = MODERN_PROCESS
    invokeAndWaitIfNeeded { UIUtil.dispatchAllInvocationEvents() }

    assertThat(banner.isVisible).isFalse()
  }

  @Test
  fun testActivityRestartBannerShownIfRunConfigAreAlreadySetButAttributeIsMissing() {
    setUpRunConfiguration(enableInspectionWithoutRestart = true)
    preferredProcess = null
    inspectorRule.attachDevice(MODERN_PROCESS.device)
    val banner = InspectorBanner(inspectorRule.project)
    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    inspectorRule.processes.selectedProcess = MODERN_PROCESS
    verifyActivityRestartBanner(banner, runConfigActionExpected = false)
  }

  @Test
  fun testActivityRestartBannerShownFromOtherAppProcess() {
    setUpRunConfiguration()
    preferredProcess = null
    inspectorRule.attachDevice(OTHER_MODERN_PROCESS.device)
    val banner = InspectorBanner(inspectorRule.project)
    inspectorRule.processNotifier.fireConnected(OTHER_MODERN_PROCESS)
    inspectorRule.processes.selectedProcess = OTHER_MODERN_PROCESS
    verifyActivityRestartBanner(banner, runConfigActionExpected = false)
  }

  @Test
  fun testConfigurationUpdates() {
    assertThat(inspectorRule.inspectorModel.resourceLookup.dpi).isEqualTo(Density.DEFAULT_DENSITY)
    assertThat(inspectorRule.inspectorModel.resourceLookup.fontScale).isEqualTo(DEFAULT_FONT_SCALE)

    val inspectorState = FakeInspectorState(inspectionRule.viewInspector, inspectionRule.composeInspector)
    inspectorState.createFakeViewTree()

    var modelUpdatedLatch = ReportingCountDownLatch(2) // We'll get two tree layout events on start fetch
    inspectorRule.inspectorModel.modificationListeners.add { _, _, _ ->
      modelUpdatedLatch.countDown()
    }

    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    modelUpdatedLatch.await(TIMEOUT, TIMEOUT_UNIT)

    assertThat(inspectorRule.inspectorModel.resourceLookup.dpi).isEqualTo(Density.HIGH.dpiValue)
    assertThat(inspectorRule.inspectorModel.resourceLookup.fontScale).isEqualTo(1.5f)

    modelUpdatedLatch = ReportingCountDownLatch(1)
    inspectorState.triggerLayoutCapture(rootId = 1L, excludeConfiguration = true)
    modelUpdatedLatch.await(TIMEOUT, TIMEOUT_UNIT)

    assertThat(inspectorRule.inspectorModel.resourceLookup.dpi).isEqualTo(Density.HIGH.dpiValue)
    assertThat(inspectorRule.inspectorModel.resourceLookup.fontScale).isEqualTo(1.5f)
  }

  @Test
  fun testResetOnPendingCommand() {
    val commandLatch = CommandLatch(TIMEOUT, TIMEOUT_UNIT)
    val inspectorState = FakeInspectorState(inspectionRule.viewInspector, inspectionRule.composeInspector)
    inspectorState.createFakeViewTree()
    inspectorState.createFakeComposeTree(withSemantics = true, latch = commandLatch)

    var modelUpdatedLatch = ReportingCountDownLatch(2) // We'll get two tree layout events on start fetch
    inspectorRule.inspectorModel.modificationListeners.add { _, _, _ ->
      modelUpdatedLatch.countDown()
    }

    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    modelUpdatedLatch.await(TIMEOUT, TIMEOUT_UNIT)

    var node = inspectorRule.inspectorModel[-2] as ComposeViewNode
    assertThat(node.recompositions.count).isEqualTo(7)
    assertThat(node.recompositions.skips).isEqualTo(14)

    // Enable the latch such that the GetComposables response is delayed below.
    commandLatch.enabled = true

    // Trigger a GetComposables command to be sent.
    modelUpdatedLatch = ReportingCountDownLatch(1)
    inspectorState.triggerLayoutCapture(rootId = 1)

    // While waiting for the GetComposables response: send a reset recompose counts command
    commandLatch.waitForCommand {
      val client = inspectorRule.inspectorClient as AppInspectionInspectorClient
      client.updateRecompositionCountSettings()
    }

    // Wait to receive the composables from the command sent before the reset
    modelUpdatedLatch.await(TIMEOUT, TIMEOUT_UNIT)

    // The recomposition counts should be 0 even if the counts were read as non-zero.
    node = inspectorRule.inspectorModel[-2] as ComposeViewNode
    assertThat(node.recompositions.count).isEqualTo(0)
    assertThat(node.recompositions.skips).isEqualTo(0)
  }

  private fun setUpRunConfiguration(enableInspectionWithoutRestart: Boolean = false) {
    addManifest(projectRule.fixture)
    AndroidRunConfigurations.getInstance().createRunConfiguration(AndroidFacet.getInstance(projectRule.module)!!)
    if (enableInspectionWithoutRestart) {
      val runManager = RunManager.getInstance(inspectorRule.project)
      val config = runManager.allConfigurationsList.filterIsInstance<AndroidRunConfiguration>().firstOrNull { it.name == "app" }
      config!!.INSPECTION_WITHOUT_ACTIVITY_RESTART = true
    }
  }

  private fun verifyActivityRestartBanner(banner: InspectorBanner, runConfigActionExpected: Boolean) {
    invokeAndWaitIfNeeded { UIUtil.dispatchAllInvocationEvents() }
    assertThat(banner.text.text).isEqualTo("The activity was restarted. This can be avoided by selecting " +
                                           "\"Enable view attribute inspection\" in the developer options on the device or " +
                                           "by enabling \"Connect without restarting activity\" in the run configuration options.")
    val service = InspectorBannerService.getInstance(inspectorRule.project)
    service.DISMISS_ACTION.actionPerformed(mock())
    invokeAndWaitIfNeeded { UIUtil.dispatchAllInvocationEvents() }

    val actionPanel = banner.getComponent(1) as JPanel
    if (runConfigActionExpected) {
      assertThat(actionPanel.componentCount).isEqualTo(3)
      assertThat((actionPanel.components[0] as HyperlinkLabel).text).isEqualTo("Open Run Configuration")
      assertThat((actionPanel.components[1] as HyperlinkLabel).text).isEqualTo("Don't Show Again")
      assertThat((actionPanel.components[2] as HyperlinkLabel).text).isEqualTo("Dismiss")
    }
    else {
      assertThat(actionPanel.componentCount).isEqualTo(2)
      assertThat((actionPanel.components[0] as HyperlinkLabel).text).isEqualTo("Don't Show Again")
      assertThat((actionPanel.components[1] as HyperlinkLabel).text).isEqualTo("Dismiss")
    }
  }
}

class AppInspectionInspectorClientWithUnsupportedApi29 {
  private val disposableRule = DisposableRule()
  private val projectRule: AndroidProjectRule = AndroidProjectRule.onDisk()
  private val inspectionRule = AppInspectionInspectorRule(disposableRule.disposable, projectRule)
  private val inspectorRule = LayoutInspectorRule(listOf(mock()), projectRule) { false }

  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(inspectionRule).around(inspectorRule).around(disposableRule)!!

  @Test
  fun testApi29VersionBanner() = runBlocking {
    val processDescriptor = setUpDevice(29)
    val sdkRoot = createInMemoryFileSystemAndFolder("sdk")

    checkBannerForTag(processDescriptor, sdkRoot, DEFAULT_TAG, MIN_API_29_AOSP_SYSIMG_REV, true)
    checkBannerForTag(processDescriptor, sdkRoot, GOOGLE_APIS_TAG, MIN_API_29_GOOGLE_APIS_SYSIMG_REV, true)
    checkBannerForTag(processDescriptor, sdkRoot, PLAY_STORE_TAG, 999, false)

    // Set up an API 30 device and the inspector should be created successfully
    val processDescriptor2 = setUpDevice(30)

    val sdkPackage = setUpSdkPackage(sdkRoot, 1, 30, null, false) as LocalPackage
    val avdInfo = setUpAvd(sdkPackage, null, 30)
    val packages = RepositoryPackages(listOf(sdkPackage), listOf())
    val sdkHandler = AndroidSdkHandler(sdkRoot, null, FakeRepoManager(sdkRoot, packages))
    val banner = InspectorBanner(inspectorRule.project)
    invokeAndWaitIfNeeded { UIUtil.dispatchAllInvocationEvents() }

    assertThat(banner.isVisible).isFalse()

    setUpAvdManagerAndRun(sdkHandler, avdInfo, suspend {
      val client = AppInspectionInspectorClient(processDescriptor2,
                                                isInstantlyAutoConnected = false, model(inspectorRule.project) {}, mock(), mock(),
                                                disposableRule.disposable, inspectionRule.inspectionService.apiServices,
                                                sdkHandler = sdkHandler)
      // shouldn't get an exception
      client.connect(inspectorRule.project)
    })

  }

  private suspend fun checkBannerForTag(
    processDescriptor: ProcessDescriptor,
    sdkRoot: Path,
    tag: IdDisplay?,
    minRevision: Int,
    checkUpdate: Boolean
  ) {
    // Set up an AOSP api 29 device below the required system image revision, with no update available
    val sdkPackage = setUpSdkPackage(sdkRoot, minRevision - 1, 29, tag, false) as LocalPackage
    val avdInfo = setUpAvd(sdkPackage, tag, 29)
    val packages = RepositoryPackages(listOf(sdkPackage), listOf())
    val sdkHandler = AndroidSdkHandler(sdkRoot, null, FakeRepoManager(sdkRoot, packages))
    val banner = InspectorBanner(inspectorRule.project)
    invokeAndWaitIfNeeded { UIUtil.dispatchAllInvocationEvents() }

    assertThat(banner.isVisible).isFalse()

    setUpAvdManagerAndRun(sdkHandler, avdInfo, suspend {
      val client = AppInspectionInspectorClient(processDescriptor,
                                                isInstantlyAutoConnected = false, model(inspectorRule.project) {}, mock(), mock(),
                                                disposableRule.disposable, inspectionRule.inspectionService.apiServices,
                                                sdkHandler = sdkHandler)
      client.connect(inspectorRule.project)
      waitForCondition(1, TimeUnit.SECONDS) { client.state == InspectorClient.State.DISCONNECTED }
      invokeAndWaitIfNeeded { UIUtil.dispatchAllInvocationEvents() }
      assertThat(banner.isVisible).isTrue()
      assertThat(banner.text.text).isEqualTo(API_29_BUG_MESSAGE)
    })
    banner.isVisible = false

    if (!checkUpdate) {
      return
    }

    // Now there is an update available
    val remotePackage = setUpSdkPackage(sdkRoot, minRevision, 29, tag, true) as RemotePackage
    packages.setRemotePkgInfos(listOf(remotePackage))
    setUpAvdManagerAndRun(sdkHandler, avdInfo, suspend {
      val client = AppInspectionInspectorClient(processDescriptor,
                                                isInstantlyAutoConnected = false, model(inspectorRule.project) {}, mock(), mock(),
                                                disposableRule.disposable, inspectionRule.inspectionService.apiServices,
                                                sdkHandler = sdkHandler)
      client.connect(inspectorRule.project)
      waitForCondition(1, TimeUnit.SECONDS) { client.state == InspectorClient.State.DISCONNECTED }
      invokeAndWaitIfNeeded { UIUtil.dispatchAllInvocationEvents() }
      assertThat(banner.isVisible).isTrue()
      assertThat(banner.text.text).isEqualTo("$API_29_BUG_MESSAGE $API_29_BUG_UPGRADE")
    })
    banner.isVisible = false
  }

  private suspend fun setUpAvdManagerAndRun(sdkHandler: AndroidSdkHandler, avdInfo: AvdInfo, body: suspend () -> Unit) {
    val connection = object : AvdManagerConnection(sdkHandler, sdkHandler.location!!.fileSystem.someRoot.resolve("android/avds"),
                                                   MoreExecutors.newDirectExecutorService()) {
      fun setFactory() {
        setConnectionFactory { _, _ -> this }
      }

      override fun findAvd(avdId: String) = if (avdId == avdInfo.name) avdInfo else null

      fun resetFactory() {
        resetConnectionFactory()
      }
    }
    try {
      connection.setFactory()
      body()
    }
    finally {
      connection.resetFactory()
    }
  }

  private fun setUpAvd(sdkPackage: LocalPackage, tag: IdDisplay?, apiLevel: Int): AvdInfo {
    val systemImage = SystemImage(sdkPackage.location, tag, null, "x86", arrayOf(), sdkPackage)
    val properties = mutableMapOf<String, String>()
    if (tag != null) {
      properties[AvdManager.AVD_INI_TAG_ID] = tag.id
      properties[AvdManager.AVD_INI_TAG_DISPLAY] = tag.display
    }
    return AvdInfo("myAvd-$apiLevel", Paths.get("myIni"), Paths.get("/android/avds/myAvd-$apiLevel"), systemImage, properties)
  }

  private fun setUpSdkPackage(sdkRoot: Path, revision: Int, apiLevel: Int, tag: IdDisplay?, isRemote: Boolean): FakePackage {
    val sdkPackage =
      if (isRemote) FakePackage.FakeRemotePackage("mySysImg-$apiLevel")
      else FakePackage.FakeLocalPackage("mySysImg-$apiLevel", sdkRoot.resolve("mySysImg"))
    sdkPackage.setRevision(Revision(revision))
    val packageDetails = AndroidSdkHandler.getSysImgModule().createLatestFactory().createSysImgDetailsType()
    packageDetails.apiLevel = apiLevel
    tag?.let { packageDetails.tags.add(it) }
    sdkPackage.typeDetails = packageDetails as TypeDetails
    return sdkPackage
  }

  private fun setUpDevice(apiLevel: Int): ProcessDescriptor {
    val processDescriptor = object : ProcessDescriptor {
      override val device = object: DeviceDescriptor {
        override val manufacturer = "mfg"
        override val model = "model"
        override val serial = "emulator-$apiLevel"
        override val isEmulator = true
        override val apiLevel = apiLevel
        override val version = "10.0.0"
        override val codename: String? = null
      }
      override val abiCpuArch = "x86_64"
      override val name = "my name"
      override val packageName = "my package name"
      override val isRunning = true
      override val pid = 1234
      override val streamId = 4321L
    }

    inspectorRule.adbRule.attachDevice(
      processDescriptor.device.serial, processDescriptor.device.manufacturer, processDescriptor.device.model,
      processDescriptor.device.version, processDescriptor.device.apiLevel.toString(), processDescriptor.abiCpuArch, emptyMap(),
      DeviceState.HostConnectionType.LOCAL, "myAvd-$apiLevel", "/android/avds/myAvd-$apiLevel"
    )

    return processDescriptor
  }
}

class AppInspectionInspectorClientWithFailingClientTest {
  private val usageTrackerRule = MetricsTrackerRule()
  private val disposableRule = DisposableRule()
  private val projectRule: AndroidProjectRule = AndroidProjectRule.onDisk()
  private val inspectionRule = AppInspectionInspectorRule(disposableRule.disposable, projectRule)
  private var throwOnState: AttachErrorState = AttachErrorState.UNKNOWN_ATTACH_ERROR_STATE
  private var exceptionToThrow: Exception = RuntimeException("expected")
  private val getMonitor: () -> InspectorClientLaunchMonitor = {
    spy(InspectorClientLaunchMonitor(projectRule.project, ListenerCollection.createWithDirectExecutor())).also {
      doAnswer { invocation ->
        val state = invocation.arguments[0] as AttachErrorState
        if (state == throwOnState) {
          throw exceptionToThrow
        }
        null
      }.whenever(it).updateProgress(any(AttachErrorState::class.java))
    }
  }

  private val inspectorRule = LayoutInspectorRule(listOf(inspectionRule.createInspectorClientProvider(getMonitor)), projectRule) {
    it.name == MODERN_PROCESS.name
  }

  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(inspectionRule).around(inspectorRule).around(usageTrackerRule).around(disposableRule)!!

  @Test
  fun errorShownOnNoAgentWithApi29() {
    throwOnState = AttachErrorState.START_REQUEST_SENT
    val banner = InspectorBanner(inspectorRule.project)
    inspectorRule.attachDevice(MODERN_DEVICE)
    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    invokeAndWaitIfNeeded { UIUtil.dispatchAllInvocationEvents() }
    assertThat(banner.text.text).isEqualTo("Unable to detect a live inspection service. To enable live inspections, restart the device.")
    assertThat(inspectorRule.inspectorClient.isConnected).isFalse()
    val usages = usageTrackerRule.testTracker.usages
      .filter { it.studioEvent.kind == AndroidStudioEvent.EventKind.DYNAMIC_LAYOUT_INSPECTOR_EVENT }
    assertThat(usages).hasSize(4)
    assertThat(usages.map { it.studioEvent.dynamicLayoutInspectorEvent.type }).containsExactly(
      DynamicLayoutInspectorEventType.ATTACH_REQUEST,
      DynamicLayoutInspectorEventType.ATTACH_SUCCESS,
      DynamicLayoutInspectorEventType.ATTACH_ERROR,
      DynamicLayoutInspectorEventType.SESSION_DATA
    ).inOrder()
  }

  @Test
  fun errorLoggedOnException() {
    throwOnState = AttachErrorState.ATTACH_SUCCESS
    val banner = InspectorBanner(inspectorRule.project)
    inspectorRule.attachDevice(MODERN_DEVICE)
    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    invokeAndWaitIfNeeded { UIUtil.dispatchAllInvocationEvents() }
    assertThat(banner.text.text).isEqualTo("Unable to detect a live inspection service. To enable live inspections, restart the device.")
    assertThat(inspectorRule.inspectorClient.isConnected).isFalse()
    val usages = usageTrackerRule.testTracker.usages
      .filter { it.studioEvent.kind == AndroidStudioEvent.EventKind.DYNAMIC_LAYOUT_INSPECTOR_EVENT }
    assertThat(usages).hasSize(3)
    assertThat(usages.map { it.studioEvent.dynamicLayoutInspectorEvent.type }).containsExactly(
      DynamicLayoutInspectorEventType.ATTACH_REQUEST,
      DynamicLayoutInspectorEventType.ATTACH_ERROR,
      DynamicLayoutInspectorEventType.SESSION_DATA
    ).inOrder()
  }

  @Test
  fun noHardwareAcceleration() = runBlocking {
    throwOnState = AttachErrorState.UNKNOWN_ATTACH_ERROR_STATE // do not throw !!!

    val inspectorState = FakeInspectorState(inspectionRule.viewInspector, inspectionRule.composeInspector)
    inspectorState.simulateNoHardwareAccelerationErrorFromStartCapturing()

    val startFetchReceived = ReportingCountDownLatch(1)
    inspectionRule.viewInspector.listenWhen({ it.hasStartFetchCommand() }) { command ->
      assertThat(command.startFetchCommand.continuous).isTrue()
      startFetchReceived.countDown()
    }

    inspectorRule.attachDevice(MODERN_DEVICE)
    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    startFetchReceived.await(TIMEOUT, TIMEOUT_UNIT) // If here, we already successfully connected (and sent an initial command)

    val usages = usageTrackerRule.testTracker.usages
      .filter { it.studioEvent.kind == AndroidStudioEvent.EventKind.DYNAMIC_LAYOUT_INSPECTOR_EVENT }
    assertThat(usages).hasSize(4)
    val event2 = usages[2].studioEvent.dynamicLayoutInspectorEvent
    assertThat(event2.type).isEqualTo(DynamicLayoutInspectorEventType.ATTACH_ERROR)
    assertThat(event2.errorInfo.attachErrorCode).isEqualTo(AttachErrorCode.NO_HARDWARE_ACCELERATION)
  }

  @Test
  fun testErrorsFromAppInspection() {
    checkException(AppInspectionCannotFindAdbDeviceException("expected"), AttachErrorCode.APP_INSPECTION_CANNOT_FIND_DEVICE)
    checkException(AppInspectionProcessNoLongerExistsException("expected"), AttachErrorCode.APP_INSPECTION_PROCESS_NO_LONGER_EXISTS)
    checkException(AppInspectionVersionIncompatibleException("expected"), AttachErrorCode.APP_INSPECTION_INCOMPATIBLE_VERSION)
    checkException(AppInspectionLibraryMissingException("expected"), AttachErrorCode.APP_INSPECTION_MISSING_LIBRARY)
    checkException(AppInspectionAppProguardedException("expected"), AttachErrorCode.APP_INSPECTION_PROGUARDED_APP)
    checkException(AppInspectionArtifactNotFoundException("expected"), AttachErrorCode.APP_INSPECTION_ARTIFACT_NOT_FOUND)
    checkException(object : AppInspectionServiceException("expected") {}, AttachErrorCode.UNKNOWN_APP_INSPECTION_ERROR)
  }

  private fun checkException(exception: Exception, expected: AttachErrorCode) {
    throwOnState = AttachErrorState.ATTACH_SUCCESS
    exceptionToThrow = exception
    inspectorRule.attachDevice(MODERN_DEVICE)
    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    invokeAndWaitIfNeeded { UIUtil.dispatchAllInvocationEvents() }
    assertThat(inspectorRule.inspectorClient.isConnected).isFalse()
    val usages = usageTrackerRule.testTracker.usages
      .filter { it.studioEvent.kind == AndroidStudioEvent.EventKind.DYNAMIC_LAYOUT_INSPECTOR_EVENT }
    assertThat(usages).hasSize(3)
    assertThat(usages.map { it.studioEvent.dynamicLayoutInspectorEvent.type }).containsExactly(
      DynamicLayoutInspectorEventType.ATTACH_REQUEST,
      DynamicLayoutInspectorEventType.ATTACH_ERROR,
      DynamicLayoutInspectorEventType.SESSION_DATA
    ).inOrder()
    assertThat(usages[1].studioEvent.dynamicLayoutInspectorEvent.errorInfo.attachErrorCode).isEqualTo(expected)
    inspectorRule.disconnect()
    usageTrackerRule.testTracker.usages.clear()
  }
}

private fun runWithFlagState(desiredFlagState: Boolean, task: () -> Unit) {
  val flag = StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_AUTO_CONNECT_TO_FOREGROUND_PROCESS_ENABLED
  val flagPreviousState = flag.get()
  flag.override(desiredFlagState)

  task()

  // restore flag state
  flag.override(flagPreviousState)
}