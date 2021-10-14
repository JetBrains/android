/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.ui

import com.android.ddmlib.testing.FakeAdbRule
import com.android.testutils.MockitoKt.mock
import com.android.tools.adtui.actions.ZoomType
import com.android.tools.adtui.common.AdtUiCursorType
import com.android.tools.adtui.common.AdtUiCursorsProvider
import com.android.tools.adtui.common.TestAdtUiCursorsProvider
import com.android.tools.adtui.common.replaceAdtUiCursorWithPredefinedCursor
import com.android.tools.adtui.swing.FakeKeyboard
import com.android.tools.adtui.swing.FakeMouse.Button
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.IconLoaderRule
import com.android.tools.idea.appinspection.api.process.ProcessesModel
import com.android.tools.idea.appinspection.ide.ui.ICON_PHONE
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.appinspection.test.DEFAULT_TEST_INSPECTION_STREAM
import com.android.tools.idea.appinspection.test.TestProcessDiscovery
import com.android.tools.idea.concurrency.waitForCondition
import com.android.tools.idea.layoutinspector.InspectorClientProvider
import com.android.tools.idea.layoutinspector.LAYOUT_INSPECTOR_DATA_KEY
import com.android.tools.idea.layoutinspector.LEGACY_DEVICE
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.LayoutInspectorRule
import com.android.tools.idea.layoutinspector.LegacyClientProvider
import com.android.tools.idea.layoutinspector.MODERN_DEVICE
import com.android.tools.idea.layoutinspector.OLDER_LEGACY_DEVICE
import com.android.tools.idea.layoutinspector.createProcess
import com.android.tools.idea.layoutinspector.metrics.statistics.SessionStatistics
import com.android.tools.idea.layoutinspector.model
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.REBOOT_FOR_LIVE_INSPECTOR_MESSAGE_KEY
import com.android.tools.idea.layoutinspector.model.ROOT
import com.android.tools.idea.layoutinspector.model.ROOT2
import com.android.tools.idea.layoutinspector.model.VIEW1
import com.android.tools.idea.layoutinspector.model.VIEW2
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient
import com.android.tools.idea.layoutinspector.pipeline.InspectorClientLauncher
import com.android.tools.idea.layoutinspector.pipeline.InspectorClientSettings
import com.android.tools.idea.layoutinspector.pipeline.appinspection.AppInspectionInspectorRule
import com.android.tools.idea.layoutinspector.util.ComponentUtil.flatten
import com.android.tools.idea.layoutinspector.util.FakeTreeSettings
import com.android.tools.idea.layoutinspector.util.ReportingCountDownLatch
import com.android.tools.idea.layoutinspector.window
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.ide.DataManager
import com.intellij.ide.impl.HeadlessDataManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.registerServiceInstance
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.ui.components.JBScrollPane
import icons.StudioIcons
import junit.framework.TestCase
import layoutinspector.view.inspection.LayoutInspectorViewProtocol
import org.jetbrains.android.util.AndroidBundle
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.Mockito.`when`
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Point
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JViewport

private val MODERN_PROCESS = MODERN_DEVICE.createProcess(streamId = DEFAULT_TEST_INSPECTION_STREAM.streamId)

@RunsInEdt
class DeviceViewPanelWithFullInspectorTest {
  private val disposableRule = DisposableRule()
  private val appInspectorRule = AppInspectionInspectorRule(disposableRule.disposable, withDefaultResponse = false)
  private val inspectorRule = LayoutInspectorRule(
    clientProvider = appInspectorRule.createInspectorClientProvider(),
    isPreferredProcess =  { it.name == MODERN_PROCESS.name }
  )

  @get:Rule
  val ruleChain =
    RuleChain.outerRule(appInspectorRule).around(inspectorRule).around(IconLoaderRule()).around(EdtRule()).around(disposableRule)!!

  // Used by all tests that install command handlers
  private var latch: CountDownLatch? = null
  private val commands = mutableListOf<LayoutInspectorViewProtocol.Command>()

  @Test
  fun testLiveControlEnabledAndSetByDefaultWhenDisconnected() {
    val settings = EditorDeviceViewSettings()
    val toolbar = getToolbar(
      DeviceViewPanel(inspectorRule.processes, inspectorRule.inspector, settings, inspectorRule.projectRule.fixture.testRootDisposable))

    val toggle = toolbar.components.find { it is ActionButton && it.action is DeviceViewPanel.PauseLayoutInspectorAction } as ActionButton
    assertThat(toggle.isEnabled).isTrue()
    assertThat(toggle.isSelected).isTrue()
    assertThat(getPresentation(toggle).description).isEqualTo(
      "Stream updates to your app's layout from your device in realtime. Enabling live updates consumes more device resources and might " +
      "impact runtime performance.")
  }

  @Test
  fun testLiveControlEnabledAndNotSetInSnapshotModeWhenDisconnected() {
    InspectorClientSettings.isCapturingModeOn = false

    val settings = EditorDeviceViewSettings()
    val toolbar = getToolbar(
      DeviceViewPanel(inspectorRule.processes, inspectorRule.inspector, settings, inspectorRule.projectRule.fixture.testRootDisposable))

    val toggle = toolbar.components.find { it is ActionButton && it.action is DeviceViewPanel.PauseLayoutInspectorAction } as ActionButton
    assertThat(toggle.isEnabled).isTrue()
    assertThat(toggle.isSelected).isFalse()
    assertThat(getPresentation(toggle).description).isEqualTo(
      "Stream updates to your app's layout from your device in realtime. Enabling live updates consumes more device resources and might " +
      "impact runtime performance.")
  }

  @Test
  fun testLiveControlEnabledAndSetByDefaultWhenConnected() {
    installCommandHandlers()
    latch = CountDownLatch(1)
    connect(MODERN_PROCESS)
    assertThat(latch?.await(1L, TimeUnit.SECONDS)).isTrue()

    val settings = EditorDeviceViewSettings()
    val toolbar = getToolbar(
      DeviceViewPanel(inspectorRule.processes, inspectorRule.inspector, settings, inspectorRule.projectRule.fixture.testRootDisposable))

    val toggle = toolbar.components.find { it is ActionButton && it.action is DeviceViewPanel.PauseLayoutInspectorAction } as ActionButton
    assertThat(toggle.isEnabled).isTrue()
    assertThat(toggle.isSelected).isTrue()
    assertThat(getPresentation(toggle).description).isEqualTo(
      "Stream updates to your app's layout from your device in realtime. Enabling live updates consumes more device resources and might " +
      "impact runtime performance.")
    assertThat(commands).hasSize(1)
    assertThat(commands[0].hasStartFetchCommand()).isTrue()
  }

  @Test
  fun testLiveControlEnabledAndNotSetInSnapshotModeWhenConnected() {
    InspectorClientSettings.isCapturingModeOn = false
    installCommandHandlers()
    latch = CountDownLatch(1)
    connect(MODERN_PROCESS)
    assertThat(latch?.await(1L, TimeUnit.SECONDS)).isTrue()
    val settings = EditorDeviceViewSettings()
    val toolbar = getToolbar(
      DeviceViewPanel(inspectorRule.processes, inspectorRule.inspector, settings, inspectorRule.projectRule.fixture.testRootDisposable))

    val toggle = toolbar.components.find { it is ActionButton && it.action is DeviceViewPanel.PauseLayoutInspectorAction } as ActionButton
    assertThat(toggle.isEnabled).isTrue()
    assertThat(toggle.isSelected).isFalse()
    assertThat(getPresentation(toggle).description).isEqualTo(
      "Stream updates to your app's layout from your device in realtime. Enabling live updates consumes more device resources and might " +
      "impact runtime performance.")
    assertThat(commands).hasSize(1)
    assertThat(commands[0].startFetchCommand.continuous).isFalse()
  }

  @Test
  fun testTurnOnSnapshotModeWhenDisconnected() {
    installCommandHandlers()

    val stats = inspectorRule.inspector.stats.live
    stats.toggledToLive()
    val settings = EditorDeviceViewSettings()
    val toolbar = getToolbar(
      DeviceViewPanel(inspectorRule.processes, inspectorRule.inspector, settings, inspectorRule.projectRule.fixture.testRootDisposable))

    val toggle = toolbar.components.find { it is ActionButton && it.action is DeviceViewPanel.PauseLayoutInspectorAction } as ActionButton
    val fakeUi = FakeUi(toggle)
    fakeUi.mouse.click(10, 10)
    assertThat(toggle.isEnabled).isTrue()
    assertThat(toggle.isSelected).isFalse()
    assertThat(getPresentation(toggle).description).isEqualTo(
      "Stream updates to your app's layout from your device in realtime. Enabling live updates consumes more device resources and might " +
      "impact runtime performance.")

    assertThat(commands).isEmpty()
    assertThat(InspectorClientSettings.isCapturingModeOn).isFalse()
    assertThat(stats.currentModeIsLive).isTrue() // unchanged
  }

  @Test
  fun testTurnOnLiveModeWhenDisconnected() {
    installCommandHandlers()
    InspectorClientSettings.isCapturingModeOn = false

    val stats = inspectorRule.inspector.stats.live
    stats.toggledToRefresh()
    val settings = EditorDeviceViewSettings()
    val toolbar = getToolbar(
      DeviceViewPanel(inspectorRule.processes, inspectorRule.inspector, settings, inspectorRule.projectRule.fixture.testRootDisposable))

    val toggle = toolbar.components.find { it is ActionButton && it.action is DeviceViewPanel.PauseLayoutInspectorAction } as ActionButton
    toolbar.size = Dimension(800, 200)
    toolbar.doLayout()
    val fakeUi = FakeUi(toggle)
    fakeUi.mouse.click(10, 10)
    assertThat(toggle.isEnabled).isTrue()
    assertThat(toggle.isSelected).isTrue()
    assertThat(getPresentation(toggle).description).isEqualTo(
      "Stream updates to your app's layout from your device in realtime. Enabling live updates consumes more device resources and might " +
      "impact runtime performance.")

    assertThat(commands).isEmpty()
    assertThat(InspectorClientSettings.isCapturingModeOn).isTrue()
    assertThat(stats.currentModeIsLive).isFalse() // unchanged
  }

  @Test
  fun testTurnOnSnapshotMode() {
    val stats = inspectorRule.inspector.stats.live
    stats.toggledToLive()
    latch = CountDownLatch(1)

    installCommandHandlers()
    connect(MODERN_PROCESS)
    assertThat(latch?.await(1, TimeUnit.SECONDS)).isTrue()
    latch = CountDownLatch(2)
    val settings = EditorDeviceViewSettings()
    val toolbar = getToolbar(
      DeviceViewPanel(inspectorRule.processes, inspectorRule.inspector, settings, inspectorRule.projectRule.fixture.testRootDisposable))
    val toggle = toolbar.components.find { it is ActionButton && it.action is DeviceViewPanel.PauseLayoutInspectorAction } as ActionButton

    val fakeUi = FakeUi(toggle)
    fakeUi.mouse.click(10, 10)
    assertThat(toggle.isEnabled).isTrue()
    assertThat(toggle.isSelected).isFalse()
    assertThat(getPresentation(toggle).description).isEqualTo(
      "Stream updates to your app's layout from your device in realtime. Enabling live updates consumes more device resources and might " +
      "impact runtime performance.")

    assertThat(latch?.await(1, TimeUnit.SECONDS)).isTrue()
    assertThat(commands).hasSize(3)
    assertThat(commands[0].hasStartFetchCommand()).isTrue()
    assertThat(commands[1].hasStopFetchCommand()).isTrue()
    assertThat(commands[2].updateScreenshotTypeCommand.type).isEqualTo(LayoutInspectorViewProtocol.Screenshot.Type.SKP)
    assertThat(stats.currentModeIsLive).isFalse()
  }

  @Test
  fun testTurnOnLiveMode() {
    val stats = inspectorRule.inspector.stats.live
    stats.toggledToRefresh()
    latch = CountDownLatch(1)

    installCommandHandlers()
    InspectorClientSettings.isCapturingModeOn = false
    connect(MODERN_PROCESS)
    assertThat(latch?.await(1, TimeUnit.SECONDS)).isTrue()
    latch = CountDownLatch(1)

    val settings = EditorDeviceViewSettings()
    val toolbar = getToolbar(
      DeviceViewPanel(inspectorRule.processes, inspectorRule.inspector, settings, inspectorRule.projectRule.fixture.testRootDisposable))
    val toggle = toolbar.components.find { it is ActionButton && it.action is DeviceViewPanel.PauseLayoutInspectorAction } as ActionButton
    toolbar.size = Dimension(800, 200)
    toolbar.doLayout()

    val fakeUi = FakeUi(toggle)
    fakeUi.mouse.click(10, 10)
    assertThat(toggle.isEnabled).isTrue()
    assertThat(toggle.isSelected).isTrue()
    assertThat(getPresentation(toggle).description).isEqualTo(
      "Stream updates to your app's layout from your device in realtime. Enabling live updates consumes more device resources and might" +
      " impact runtime performance.")

    assertThat(latch?.await(1, TimeUnit.SECONDS)).isTrue()
    assertThat(commands).hasSize(2)
    assertThat(commands[0].startFetchCommand.continuous).isFalse()
    assertThat(commands[1].startFetchCommand.continuous).isTrue()

    assertThat(stats.currentModeIsLive).isTrue()
  }

  @Test
  fun testLoadingPane() {
    val latch = ReportingCountDownLatch(1)
    inspectorRule.launchSynchronously = false
    appInspectorRule.viewInspector.listenWhen({ true }) {
      latch.await(20, TimeUnit.SECONDS)
      inspectorRule.inspectorModel.update(window("w1", 1L), listOf("w1"), 1)
    }
    val settings = EditorDeviceViewSettings()
    val panel = DeviceViewPanel(inspectorRule.processes, inspectorRule.inspector, settings,
                                inspectorRule.projectRule.fixture.testRootDisposable)
    val loadingPane = flatten(panel).filterIsInstance<JBLoadingPanel>().first()
    val contentPanel = flatten(panel).filterIsInstance<DeviceViewContentPanel>().first()
    assertThat(loadingPane.isLoading).isFalse()
    assertThat(contentPanel.showEmptyText).isTrue()

    // Start connecting, loading should show
    inspectorRule.asyncLaunchLatch = ReportingCountDownLatch(1)
    inspectorRule.processes.selectedProcess = MODERN_PROCESS

    waitForCondition(1, TimeUnit.SECONDS) { loadingPane.isLoading }
    waitForCondition(1, TimeUnit.SECONDS) { !contentPanel.showEmptyText }

    // Release the response from the agent and wait for connection. The loading should stop.
    latch.countDown()
    inspectorRule.asyncLaunchLatch.await(1, TimeUnit.SECONDS)

    waitForCondition(1, TimeUnit.SECONDS) { !loadingPane.isLoading }
    assertThat(contentPanel.showEmptyText).isTrue()
  }

  @Test
  fun testLoadingPanelWithStartAndStop() {
    val latch = ReportingCountDownLatch(1)
    inspectorRule.launchSynchronously = false
    appInspectorRule.viewInspector.listenWhen({ true }) {
      latch.await(5, TimeUnit.HOURS)
      inspectorRule.inspectorModel.update(window("w1", 1L), listOf("w1"), 1)
    }
    val settings = EditorDeviceViewSettings()
    val panel = DeviceViewPanel(inspectorRule.processes, inspectorRule.inspector, settings,
                                inspectorRule.projectRule.fixture.testRootDisposable)
    val loadingPane = flatten(panel).filterIsInstance<JBLoadingPanel>().first()
    val contentPanel = flatten(panel).filterIsInstance<DeviceViewContentPanel>().first()
    assertThat(loadingPane.isLoading).isFalse()
    assertThat(contentPanel.showEmptyText).isTrue()

    // Start connecting, loading should show
    inspectorRule.asyncLaunchLatch = ReportingCountDownLatch(3)
    inspectorRule.processes.selectedProcess = MODERN_PROCESS

    waitForCondition(1, TimeUnit.SECONDS) { loadingPane.isLoading }
    waitForCondition(1, TimeUnit.SECONDS) { !contentPanel.showEmptyText }

    // Stop connecting, loading should stop
    contentPanel.selectProcessAction?.updateActions(mock())
    val actionEvent = mock<AnActionEvent>()
    `when`(actionEvent.actionManager).thenReturn(mock())
    val stopAction = contentPanel.selectProcessAction?.getChildren(actionEvent)?.first { it.templateText == "Stop inspector" }
    stopAction?.actionPerformed(mock())

    waitForCondition(1, TimeUnit.SECONDS) { !loadingPane.isLoading }
    assertThat(contentPanel.showEmptyText).isTrue()

    // Release the response from the agent such that all waiting threads can complete (cleanup).
    latch.countDown()
    inspectorRule.asyncLaunchLatch.await(3, TimeUnit.MINUTES)
  }

  @Test
  fun testSelectProcessDropDown() {
    val settings = EditorDeviceViewSettings()
    val panel = DeviceViewPanel(inspectorRule.processes, inspectorRule.inspector, settings,
                                inspectorRule.projectRule.fixture.testRootDisposable)
    val selectProcessAction = flatten(panel).filterIsInstance<DeviceViewContentPanel>().first().selectProcessAction!!
    installCommandHandlers()
    connect(MODERN_PROCESS)
    inspectorRule.processNotifier.addDevice(LEGACY_DEVICE)
    inspectorRule.processNotifier.addDevice(OLDER_LEGACY_DEVICE)
    selectProcessAction.updateActions(DataContext.EMPTY_CONTEXT)
    val children = selectProcessAction.getChildren(null)
    assertThat(children).hasLength(4)
    checkDeviceAction(children[0], enabled = true, ICON_PHONE, "Google Modern Model")
    checkDeviceAction(children[1], enabled = true, ICON_LEGACY_PHONE, "Google Legacy Model (Live inspection disabled for API < 29)")
    checkDeviceAction(children[2], enabled = false, ICON_PHONE, "Google Older Legacy Model (Unsupported for API < 23)")
    checkDeviceAction(children[3], enabled = true, StudioIcons.Shell.Toolbar.STOP, "Stop inspector")
  }

  private fun checkDeviceAction(action: AnAction, enabled: Boolean, icon: Icon?, text: String) {
    val presentation = action.templatePresentation.clone()
    val event: AnActionEvent = mock()
    `when`(event.presentation).thenReturn(presentation)
    action.update(event)
    assertThat(presentation.text).isEqualTo(text)
    assertThat(presentation.icon).isSameAs(icon)
    assertThat(presentation.isEnabled).isEqualTo(enabled)
  }

  private fun installCommandHandlers() {
    appInspectorRule.viewInspector.listenWhen({ true }) { command ->
      commands.add(command)
      inspectorRule.inspectorModel.update(window("w1", 1L), listOf("w1"), 1)
      latch?.countDown()
    }
  }

  @Suppress("SameParameterValue")
  private fun connect(process: ProcessDescriptor) {
    inspectorRule.processNotifier.addDevice(process.device)
    inspectorRule.processNotifier.fireConnected(process)
  }
}

@RunsInEdt
class DeviceViewPanelTest {

  @get:Rule
  val disposableRule = DisposableRule()

  @get:Rule
  val edtRule = EdtRule()

  @get:Rule
  val projectRule = ProjectRule()

  @get:Rule
  val adbRule = FakeAdbRule()

  @Before
  fun setup() {
    ApplicationManager.getApplication().registerServiceInstance(AdtUiCursorsProvider::class.java, TestAdtUiCursorsProvider())
    replaceAdtUiCursorWithPredefinedCursor(AdtUiCursorType.GRAB, Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR))
    replaceAdtUiCursorWithPredefinedCursor(AdtUiCursorType.GRABBING, Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR))
  }

  @Test
  fun testZoomOnConnect() {
    val viewSettings = EditorDeviceViewSettings()
    val model = InspectorModel(projectRule.project)
    val processes = ProcessesModel(TestProcessDiscovery())
    val launcher = InspectorClientLauncher(processes, listOf(), projectRule.project, disposableRule.disposable,
                                           MoreExecutors.directExecutor())
    val treeSettings = FakeTreeSettings()
    val stats: SessionStatistics = mock()
    `when`(stats.rotation).thenReturn(mock())
    val inspector = LayoutInspector(launcher, model, stats, treeSettings, MoreExecutors.directExecutor())
    treeSettings.hideSystemNodes = false
    val panel = DeviceViewPanel(processes, inspector, viewSettings, disposableRule.disposable)

    val scrollPane = flatten(panel).filterIsInstance<JBScrollPane>().first()
    scrollPane.setSize(200, 300)

    assertThat(viewSettings.scalePercent).isEqualTo(100)

    val newWindow = window(ROOT, ROOT, 0, 0, 100, 200) {
      view(VIEW1, 25, 30, 50, 50) {
        image()
      }
    }

    model.update(newWindow, listOf(ROOT), 0)

    // now we should be zoomed to fit
    assertThat(viewSettings.scalePercent).isEqualTo(135)

    viewSettings.scalePercent = 200

    // Update the model
    val newWindow2 = window(ROOT, ROOT, 0, 0, 100, 200) {
      view(VIEW2, 50, 20, 30, 40) {
        image()
      }
    }
    model.update(newWindow2, listOf(ROOT), 0)

    // Should still have the manually set zoom
    assertThat(viewSettings.scalePercent).isEqualTo(200)
  }

  @Test
  fun testZoomOnConnectWithFiltering() {
    val viewSettings = EditorDeviceViewSettings()
    val model = InspectorModel(projectRule.project)
    val processes = ProcessesModel(TestProcessDiscovery())
    val launcher = InspectorClientLauncher(processes, listOf(), projectRule.project, disposableRule.disposable,
                                           MoreExecutors.directExecutor())
    val treeSettings = FakeTreeSettings()
    val stats: SessionStatistics = mock()
    `when`(stats.rotation).thenReturn(mock())
    val inspector = LayoutInspector(launcher, model, stats, treeSettings, MoreExecutors.directExecutor())
    treeSettings.hideSystemNodes = true
    val panel = DeviceViewPanel(processes, inspector, viewSettings, disposableRule.disposable)

    val scrollPane = flatten(panel).filterIsInstance<JBScrollPane>().first()
    scrollPane.setSize(200, 300)

    assertThat(viewSettings.scalePercent).isEqualTo(100)

    val newWindow = window(ROOT, ROOT, 0, 0, 100, 200) {
      view(VIEW1, 25, 30, 50, 50) {
        image()
      }
    }

    model.update(newWindow, listOf(ROOT), 0)

    // now we should be zoomed to fit
    assertThat(viewSettings.scalePercent).isEqualTo(135)
  }

  @Test
  fun testDrawNewWindow() {
    val viewSettings = EditorDeviceViewSettings()
    val model = InspectorModel(projectRule.project)
    val processes = ProcessesModel(TestProcessDiscovery())
    val launcher = InspectorClientLauncher(processes, listOf(), projectRule.project, disposableRule.disposable,
                                           MoreExecutors.directExecutor())
    val treeSettings = FakeTreeSettings()
    val inspector = LayoutInspector(launcher, model, SessionStatistics(model, treeSettings), treeSettings, MoreExecutors.directExecutor())
    treeSettings.hideSystemNodes = false
    val panel = DeviceViewPanel(processes, inspector, viewSettings, disposableRule.disposable, MoreExecutors.directExecutor())

    val scrollPane = flatten(panel).filterIsInstance<JBScrollPane>().first()
    scrollPane.setSize(200, 300)

    val window1 = window(ROOT, ROOT, 0, 0, 100, 200) {
      view(VIEW1, 25, 30, 50, 50) {
        image()
      }
    }

    model.update(window1, listOf(ROOT), 0)

    // Add another window
    val window2 = window(100, 100, 0, 0, 100, 200) {
      view(VIEW2, 50, 20, 30, 40) {
        image()
      }
    }
    //clear drawChildren for window2 so we can ensure they're regenerated
    ViewNode.writeAccess { window2.root.flatten().forEach { it.drawChildren.clear() } }

    model.update(window2, listOf(ROOT, 100), 1)

    // drawChildren for the new window should be populated
    assertThat(ViewNode.readAccess { window2.root.drawChildren }).isNotEmpty()
  }

  @Test
  fun testNewWindowDoesntResetZoom() {
    val viewSettings = EditorDeviceViewSettings()
    val model = InspectorModel(projectRule.project)
    val processes = ProcessesModel(TestProcessDiscovery())
    val launcher = InspectorClientLauncher(processes, listOf(), projectRule.project, disposableRule.disposable,
                                           MoreExecutors.directExecutor())
    val treeSettings = FakeTreeSettings()
    val inspector = LayoutInspector(launcher, model, SessionStatistics(model, treeSettings), treeSettings, MoreExecutors.directExecutor())
    treeSettings.hideSystemNodes = false
    val panel = DeviceViewPanel(processes, inspector, viewSettings, disposableRule.disposable, MoreExecutors.directExecutor())

    val scrollPane = flatten(panel).filterIsInstance<JBScrollPane>().first()
    val contentPanelModel = flatten(panel).filterIsInstance<DeviceViewContentPanel>().first().model
    scrollPane.setSize(200, 300)

    val window1 = window(ROOT, ROOT, 0, 0, 100, 200) {
      view(VIEW1, 25, 30, 50, 50) {
        image()
      }
    }

    model.update(window1, listOf(ROOT), 0)
    assertThat(contentPanelModel.hitRects.size).isEqualTo(2)

    viewSettings.scalePercent = 33

    // Add another window
    val window2 = window(ROOT2, ROOT2, 0, 0, 100, 200) {
      view(VIEW2, 50, 20, 30, 40) {
        image()
      }
    }

    model.update(window2, listOf(ROOT, ROOT2), 1)
    assertThat(contentPanelModel.hitRects.size).isEqualTo(4)

    // we should still have the manually set zoom
    assertThat(viewSettings.scalePercent).isEqualTo(33)
  }

  @Test
  fun testFocusableActionButtons() {
    val model = model { view(1, 0, 0, 1200, 1600, qualifiedName = "RelativeLayout") }
    val processes = ProcessesModel(TestProcessDiscovery())
    val launcher = InspectorClientLauncher(processes, listOf(), projectRule.project, disposableRule.disposable,
                                           MoreExecutors.directExecutor())
    val treeSettings = FakeTreeSettings()
    val inspector = LayoutInspector(launcher, model, SessionStatistics(model, treeSettings), treeSettings, MoreExecutors.directExecutor())
    treeSettings.hideSystemNodes = false
    val settings = EditorDeviceViewSettings()
    val panel = DeviceViewPanel(processes, inspector, settings, disposableRule.disposable)
    val toolbar = getToolbar(panel)

    toolbar.components.forEach { assertThat(it.isFocusable).isTrue() }
  }

  @Test
  fun testDragWithSpace() {
    testPan({ ui, _ -> ui.keyboard.press(FakeKeyboard.Key.SPACE) },
            { ui, _ -> ui.keyboard.release(FakeKeyboard.Key.SPACE) })
  }

  @Test
  fun testDragInPanMode() {
    testPan({ _, panel -> panel.isPanning = true },
            { _, panel -> panel.isPanning = false })
  }

  @Test
  fun testDragWithMiddleButton() {
    testPan({ _, _ -> }, { _, _ -> }, Button.MIDDLE)
  }

  private fun testPan(startPan: (FakeUi, DeviceViewPanel) -> Unit,
                      endPan: (FakeUi, DeviceViewPanel) -> Unit,
                      panButton: Button = Button.LEFT) {
    val model = model {
      view(ROOT, 0, 0, 100, 200) {
        view(VIEW1, 25, 30, 50, 50)
      }
    }

    val processes = ProcessesModel(TestProcessDiscovery())
    val launcher: InspectorClientLauncher = mock()
    val client: InspectorClient = mock()
    `when`(client.capabilities).thenReturn(setOf(InspectorClient.Capability.SUPPORTS_SKP))
    `when`(launcher.activeClient).thenReturn(client)
    val treeSettings = FakeTreeSettings()
    val inspector = LayoutInspector(launcher, model, SessionStatistics(model, treeSettings), treeSettings, MoreExecutors.directExecutor())
    treeSettings.hideSystemNodes = false
    val settings = EditorDeviceViewSettings()
    val panel = DeviceViewPanel(processes, inspector, settings, disposableRule.disposable)

    val contentPanel = flatten(panel).filterIsInstance<DeviceViewContentPanel>().first()
    val viewport = flatten(panel).filterIsInstance<JViewport>().first()

    (DataManager.getInstance() as HeadlessDataManager).setTestDataProvider {
      id -> if (id == LAYOUT_INSPECTOR_DATA_KEY.name) inspector else null
    }


    contentPanel.setSize(200, 300)
    viewport.extentSize = Dimension(100, 100)

    assertThat(viewport.viewPosition).isEqualTo(Point(0, 0))

    val fakeUi = FakeUi(contentPanel)
    fakeUi.keyboard.setFocus(contentPanel)

    // Rotate the model so that dragging would normally rotate
    contentPanel.model.xOff = 0.02

    assertThat(panel.isPanning).isFalse()
    startPan(fakeUi, panel)
    fakeUi.mouse.press(20, 20, panButton)
    assertThat(panel.isPanning).isTrue()
    fakeUi.mouse.dragTo(10, 10)
    assertThat(panel.isPanning).isTrue()
    fakeUi.mouse.release()

    // Unchanged--we panned instead
    TestCase.assertEquals(0.02, contentPanel.model.xOff)
    TestCase.assertEquals(0.0, contentPanel.model.yOff)
    assertThat(viewport.viewPosition).isEqualTo(Point(10, 10))

    endPan(fakeUi, panel)
    // Now we'll actually rotate
    fakeUi.mouse.drag(20, 20, -10, -10)
    assertThat(panel.isPanning).isFalse()
    TestCase.assertEquals(0.01, contentPanel.model.xOff)
    TestCase.assertEquals(-0.01, contentPanel.model.yOff)
  }
}

@RunsInEdt
class DeviceViewPanelLegacyClientOnLegacyDeviceTest {
  @get:Rule
  val edtRule = EdtRule()

  private val disposableRule = DisposableRule()
  private val inspectorRule = LayoutInspectorRule(LegacyClientProvider(disposableRule.disposable))

  @get:Rule
  val ruleChain = RuleChain.outerRule(inspectorRule).around(disposableRule)!!

  @Test
  fun testLiveControlDisabledWithProcessFromLegacyDevice() {
    inspectorRule.processes.selectedProcess = LEGACY_DEVICE.createProcess()
    waitForCondition(5, TimeUnit.SECONDS) { inspectorRule.inspectorClient.isConnected }

    val settings = EditorDeviceViewSettings()
    val toolbar = getToolbar(
      DeviceViewPanel(inspectorRule.processes, inspectorRule.inspector, settings, inspectorRule.projectRule.fixture.testRootDisposable))

    val toggle = toolbar.components.find { it is ActionButton && it.action is DeviceViewPanel.PauseLayoutInspectorAction } as ActionButton
    assertThat(toggle.isEnabled).isFalse()
    assertThat(getPresentation(toggle).description).isEqualTo("Live updates not available for devices below API 29")
  }

  @Test
  fun testLiveControlDisabledWithProcessFromModernDevice() {
    inspectorRule.launchSynchronously = false
    inspectorRule.asyncLaunchLatch = ReportingCountDownLatch(1)
    inspectorRule.processes.selectedProcess = MODERN_PROCESS
    waitForCondition(5, TimeUnit.SECONDS) { inspectorRule.inspectorClient.isConnected }

    val settings = EditorDeviceViewSettings()
    val toolbar = getToolbar(
      DeviceViewPanel(inspectorRule.processes, inspectorRule.inspector, settings, inspectorRule.projectRule.fixture.testRootDisposable))

    val toggle = toolbar.components.find { it is ActionButton && it.action is DeviceViewPanel.PauseLayoutInspectorAction } as ActionButton
    assertThat(toggle.isEnabled).isFalse()
    assertThat(getPresentation(toggle).description).isEqualTo(AndroidBundle.message(REBOOT_FOR_LIVE_INSPECTOR_MESSAGE_KEY))
  }
}

@RunsInEdt
class MyViewportLayoutManagerTest {
  private lateinit var scrollPane: JScrollPane
  private lateinit var contentPanel: JComponent
  private lateinit var layoutManager: MyViewportLayoutManager

  private var layerSpacing = INITIAL_LAYER_SPACING

  private var rootPosition: Point? = Point(400, 500)

  @get:Rule
  val edtRule = EdtRule()

  @Before
  fun setUp() {
    contentPanel = JPanel()
    scrollPane = JBScrollPane(contentPanel)
    scrollPane.size = Dimension(502, 202)
    scrollPane.preferredSize = Dimension(502, 202)
    contentPanel.preferredSize = Dimension(1000, 1000)
    layoutManager = MyViewportLayoutManager(scrollPane.viewport, { layerSpacing }, { rootPosition })
    layoutManager.layoutContainer(scrollPane.viewport)
    scrollPane.layout.layoutContainer(scrollPane)
  }

  @Test
  fun testAdjustLayerSpacing() {
    // Start view as centered
    scrollPane.viewport.viewPosition = Point(250, 400)
    // expand spacing
    layerSpacing = 200
    contentPanel.preferredSize = Dimension(1200, 1200)
    layoutManager.layoutContainer(scrollPane.viewport)
    // Still centered
    assertThat(scrollPane.viewport.viewPosition).isEqualTo(Point(350, 500))

    // offset view (-100, -50) from center
    scrollPane.viewport.viewPosition = Point(250, 450)
    // put spacing and size back
    layerSpacing = INITIAL_LAYER_SPACING
    contentPanel.preferredSize = Dimension(1000, 1000)
    layoutManager.layoutContainer(scrollPane.viewport)

    // view still offset (-100, -50) from center
    assertThat(scrollPane.viewport.viewPosition).isEqualTo(Point(150, 350))
  }

  @Test
  fun testZoomToFit() {
    assertThat(scrollPane.viewport.viewPosition).isEqualTo(Point(0, 0))
    layoutManager.currentZoomOperation = ZoomType.FIT
    layoutManager.layoutContainer(scrollPane.viewport)
    // view is centered after fit
    assertThat(scrollPane.viewport.viewPosition).isEqualTo(Point(250, 400))
  }

  @Test
  fun testZoom() {
    // Start view as centered
    scrollPane.viewport.viewPosition = Point(250, 400)
    // zoom in
    layoutManager.currentZoomOperation = ZoomType.IN
    contentPanel.preferredSize = Dimension(1200, 1200)

    layoutManager.layoutContainer(scrollPane.viewport)
    // Still centered
    assertThat(scrollPane.viewport.viewPosition).isEqualTo(Point(350, 500))

    // offset view (-100, -50) from center
    scrollPane.viewport.viewPosition = Point(250, 450)
    // zoom out
    layoutManager.currentZoomOperation = ZoomType.OUT
    contentPanel.preferredSize = Dimension(1000, 1000)

    layoutManager.layoutContainer(scrollPane.viewport)

    // view proportionally offset from center
    assertThat(scrollPane.viewport.viewPosition).isEqualTo(Point(166, 358))
  }

  @Test
  fun testChangeSize() {
    // Start view as centered
    scrollPane.viewport.viewPosition = Point(250, 400)
    layoutManager.layoutContainer(scrollPane.viewport)

    // view grows
    contentPanel.preferredSize = Dimension(1200, 1200)
    layoutManager.layoutContainer(scrollPane.viewport)

    // view should still be in the same place
    assertThat(scrollPane.viewport.viewPosition).isEqualTo(Point(250, 400))

    // view grows, root location moves
    contentPanel.preferredSize = Dimension(1300, 1300)
    rootPosition = Point(500, 600)
    layoutManager.layoutContainer(scrollPane.viewport)

    // scroll changes to keep view in the same place
    assertThat(scrollPane.viewport.viewPosition).isEqualTo(Point(350, 500))

    // disconnect, no root location
    contentPanel.preferredSize = Dimension(0, 0)
    rootPosition = null
    layoutManager.layoutContainer(scrollPane.viewport)

    // scroll goes back to origin
    assertThat(scrollPane.viewport.viewPosition).isEqualTo(Point(0, 0))
  }
}

@RunsInEdt
class DeviceViewPanelWithNoClientsTest {
  private val disposableRule = DisposableRule()
  private val appInspectorRule = AppInspectionInspectorRule(disposableRule.disposable, withDefaultResponse = false)
  private val postCreateLatch = CountDownLatch(1)
  private val inspectorRule = LayoutInspectorRule(
    clientProvider = object: InspectorClientProvider {
      override fun create(params: InspectorClientLauncher.Params, inspector: LayoutInspector): InspectorClient? {
        postCreateLatch.await()
        return null
      }
    },
    isPreferredProcess = { it.name == MODERN_PROCESS.name }
  )

  @get:Rule
  val ruleChain =
    RuleChain.outerRule(appInspectorRule).around(inspectorRule).around(IconLoaderRule()).around(EdtRule()).around(disposableRule)!!

  @Test
  fun testLoadingPane() {
    inspectorRule.asyncLaunchLatch = ReportingCountDownLatch(1)
    inspectorRule.launchSynchronously = false
    val settings = EditorDeviceViewSettings()
    val panel = DeviceViewPanel(inspectorRule.processes, inspectorRule.inspector, settings,
                                inspectorRule.projectRule.fixture.testRootDisposable)
    val loadingPane = flatten(panel).filterIsInstance<JBLoadingPanel>().first()
    val contentPanel = flatten(panel).filterIsInstance<DeviceViewContentPanel>().first()
    assertThat(loadingPane.isLoading).isFalse()
    assertThat(contentPanel.showEmptyText).isTrue()

    // Start connecting, loading should show
    inspectorRule.processes.selectedProcess = MODERN_PROCESS

    waitForCondition(1, TimeUnit.SECONDS) { loadingPane.isLoading }
    waitForCondition(1, TimeUnit.SECONDS) { !contentPanel.showEmptyText }
    postCreateLatch.countDown()
    inspectorRule.asyncLaunchLatch.await(1, TimeUnit.SECONDS)

    waitForCondition(1, TimeUnit.SECONDS) { !loadingPane.isLoading }
    waitForCondition(1, TimeUnit.SECONDS) { contentPanel.showEmptyText }
  }
}

private fun getToolbar(panel: DeviceViewPanel): JComponent =
  (flatten(panel).find { it.name == DEVICE_VIEW_ACTION_TOOLBAR_NAME } as JComponent).run {
    size = Dimension(800, 200)
    doLayout()
    return this
  }

private fun getPresentation(button: ActionButton): Presentation {
  val presentation = Presentation()
  val event = AnActionEvent(null, DataManager.getInstance().getDataContext(button), "DynamicLayoutInspectorLeft", presentation,
                            ActionManager.getInstance(), 0)
  button.action.update(event)
  return presentation
}
