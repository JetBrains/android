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

import com.android.SdkConstants
import com.android.adblib.DeviceSelector
import com.android.ddmlib.testing.FakeAdbRule
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.resources.ResourceType
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.testutils.TestUtils
import com.android.testutils.VirtualTimeScheduler
import com.android.testutils.waitForCondition
import com.android.tools.adtui.actions.ZoomType
import com.android.tools.adtui.common.AdtUiCursorType
import com.android.tools.adtui.common.AdtUiCursorsProvider
import com.android.tools.adtui.common.TestAdtUiCursorsProvider
import com.android.tools.adtui.common.replaceAdtUiCursorWithPredefinedCursor
import com.android.tools.adtui.swing.FakeKeyboardFocusManager
import com.android.tools.adtui.swing.FakeMouse.Button
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.IconLoaderRule
import com.android.tools.idea.appinspection.api.process.ProcessesModel
import com.android.tools.idea.appinspection.ide.ui.ICON_PHONE
import com.android.tools.idea.appinspection.ide.ui.SelectProcessAction
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.appinspection.internal.process.TransportProcessDescriptor
import com.android.tools.idea.appinspection.test.DEFAULT_TEST_INSPECTION_STREAM
import com.android.tools.idea.appinspection.test.TestProcessDiscovery
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.layoutinspector.InspectorClientProvider
import com.android.tools.idea.layoutinspector.LAYOUT_INSPECTOR_DATA_KEY
import com.android.tools.idea.layoutinspector.LEGACY_DEVICE
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.LayoutInspectorBundle
import com.android.tools.idea.layoutinspector.LayoutInspectorRule
import com.android.tools.idea.layoutinspector.MODERN_DEVICE
import com.android.tools.idea.layoutinspector.OLDER_LEGACY_DEVICE
import com.android.tools.idea.layoutinspector.createProcess
import com.android.tools.idea.layoutinspector.model
import com.android.tools.idea.layoutinspector.model.AndroidWindow
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.NotificationModel
import com.android.tools.idea.layoutinspector.model.ROOT
import com.android.tools.idea.layoutinspector.model.ROOT2
import com.android.tools.idea.layoutinspector.model.SelectionOrigin
import com.android.tools.idea.layoutinspector.model.VIEW1
import com.android.tools.idea.layoutinspector.model.VIEW2
import com.android.tools.idea.layoutinspector.model.VIEW3
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient
import com.android.tools.idea.layoutinspector.pipeline.InspectorClientLauncher
import com.android.tools.idea.layoutinspector.pipeline.InspectorClientSettings
import com.android.tools.idea.layoutinspector.pipeline.appinspection.AppInspectionInspectorRule
import com.android.tools.idea.layoutinspector.pipeline.foregroundprocessdetection.DeviceModel
import com.android.tools.idea.layoutinspector.tree.GotoDeclarationAction
import com.android.tools.idea.layoutinspector.ui.toolbar.actions.ICON_LEGACY_PHONE
import com.android.tools.idea.layoutinspector.ui.toolbar.actions.INITIAL_LAYER_SPACING
import com.android.tools.idea.layoutinspector.ui.toolbar.actions.SelectDeviceAction
import com.android.tools.idea.layoutinspector.ui.toolbar.actions.Toggle3dAction
import com.android.tools.idea.layoutinspector.util.FakeTreeSettings
import com.android.tools.idea.layoutinspector.util.ReportingCountDownLatch
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.Command.SpecializedCase.UPDATE_SCREENSHOT_TYPE_COMMAND
import com.android.tools.idea.layoutinspector.window
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.runDispatching
import com.android.tools.idea.testing.ui.FileOpenCaptureRule
import com.android.tools.idea.testing.ui.flatten
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profiler.proto.Common
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.ide.DataManager
import com.intellij.ide.impl.HeadlessDataManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.keymap.impl.IdeKeyEventDispatcher
import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.registerServiceInstance
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.UIUtil
import icons.StudioIcons
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Point
import java.awt.event.KeyEvent
import java.awt.event.KeyEvent.VK_B
import java.awt.event.KeyEvent.VK_SPACE
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JViewport
import javax.swing.plaf.basic.BasicScrollBarUI
import junit.framework.TestCase
import kotlin.time.Duration.Companion.seconds
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

private val MODERN_PROCESS =
  MODERN_DEVICE.createProcess(streamId = DEFAULT_TEST_INSPECTION_STREAM.streamId)

@RunsInEdt
class DeviceViewPanelWithFullInspectorTest {
  private val scheduler = VirtualTimeScheduler()
  private val projectRule: AndroidProjectRule = AndroidProjectRule.onDisk()
  private val process = MODERN_PROCESS
  private val appInspectorRule =
    AppInspectionInspectorRule(projectRule, withDefaultResponse = false)
  private val inspectorRule =
    LayoutInspectorRule(
      clientProviders = listOf(appInspectorRule.createInspectorClientProvider()),
      projectRule = projectRule,
      isPreferredProcess = { it.name == process.name },
    )
  private val fileOpenCaptureRule = FileOpenCaptureRule(projectRule)

  @get:Rule
  val ruleChain =
    RuleChain.outerRule(projectRule)
      .around(appInspectorRule)
      .around(inspectorRule)
      .around(fileOpenCaptureRule)
      .around(IconLoaderRule())
      .around(EdtRule())

  // Used by all tests that install command handlers
  private var latch: CountDownLatch? = null
  private val commands = mutableListOf<LayoutInspectorViewProtocol.Command>()

  private lateinit var deviceModel: DeviceModel
  private val appNamespace = ResourceNamespace.fromPackageName("com.example")
  private val demoLayout = ResourceReference(appNamespace, ResourceType.LAYOUT, "demo")
  private val view1Id = ResourceReference(appNamespace, ResourceType.ID, "v1")
  private val view2Id = ResourceReference(appNamespace, ResourceType.ID, "v2")

  @Before
  fun before() {
    deviceModel = DeviceModel(projectRule.testRootDisposable, inspectorRule.processes)
    inspectorRule.attachDevice(MODERN_DEVICE)
    projectRule.fixture.testDataPath =
      TestUtils.resolveWorkspacePath("tools/adt/idea/layout-inspector/testData/resource").toString()

    val deviceSelector = DeviceSelector.fromSerialNumber(process.device.serial)
    appInspectorRule.adbSession.deviceServices.configureShellCommand(
      deviceSelector,
      "settings get global debug_view_attributes",
      stdout = "0",
    )
    appInspectorRule.adbSession.deviceServices.configureShellCommand(
      deviceSelector,
      "settings put global debug_view_attributes 1",
      stdout = "",
      stderr = "",
    )
  }

  @Test
  fun testShowAndClearPerformanceWarnings() {
    val clientSettings = InspectorClientSettings(projectRule.project)
    clientSettings.inLiveMode = true

    installCommandHandlers()
    latch = CountDownLatch(1)
    connect(process)
    assertThat(latch?.await(1L, TimeUnit.SECONDS)).isTrue()

    val panel = DeviceViewPanel(inspectorRule.inspector, projectRule.fixture.testRootDisposable)
    val notificationModel = inspectorRule.notificationModel
    val renderModel =
      panel.flatten(false).filterIsInstance<DeviceViewContentPanel>().first().renderModel
    delegateDataProvider(panel)
    panel.flatten(false).filterIsInstance<ActionToolbar>().forEach { toolbar ->
      PlatformTestUtil.waitForFuture(toolbar.updateActionsAsync())
    }
    val toggle =
      panel.flatten(false).filterIsInstance<ActionButton>().single { it.action is Toggle3dAction }
    (toggle.action as Toggle3dAction).executorFactory = { scheduler }
    (toggle.action as Toggle3dAction).getCurrentTimeMillis = { scheduler.currentTimeMillis }
    assertThat(toggle.isEnabled).isTrue()
    assertThat(toggle.isSelected).isFalse()

    // Toggling to 3D mode will cause an UpdateScreenShotTypeCommand to execute on the device. Be
    // ready to wait for the response.
    latch = CountDownLatch(1)

    // Turn on 3D mode:
    toggle.click()

    // Wait for the UpdateScreenShotTypeCommand to finish
    assertThat(latch?.await(1L, TimeUnit.SECONDS)).isTrue()
    assertThat(lastImageType).isEqualTo(AndroidWindow.ImageType.SKP)

    // Advance past the timeout of the animation
    scheduler.advanceBy(15, TimeUnit.SECONDS)

    // Verify we are rotated
    assertThat(scheduler.isShutdown).isTrue()
    assertThat(renderModel.isRotated).isTrue()
    UIUtil.dispatchAllInvocationEvents()
    assertThat(notificationModel.notifications).hasSize(2)
    val notification1 = notificationModel.notifications[1]
    assertThat(notification1.message)
      .isEqualTo(LayoutInspectorBundle.message(PERFORMANCE_WARNING_3D))

    // Turn 3D mode off:
    toggle.click()
    UIUtil.dispatchAllInvocationEvents()
    assertThat(notificationModel.notifications).hasSize(1)

    // Hide VIEW2:
    val view2 = inspectorRule.inspectorModel[VIEW2]!!
    inspectorRule.inspectorModel.hideSubtree(view2)
    val expectedMessage = LayoutInspectorBundle.message(PERFORMANCE_WARNING_HIDDEN)
    waitForCondition(10.seconds) {
      notificationModel.notifications.any { it.message == expectedMessage }
    }

    // Show all:
    inspectorRule.inspectorModel.showAll()
    waitForCondition(10.seconds) { notificationModel.notifications.size == 1 }
  }

  private fun delegateDataProvider(panel: DeviceViewPanel) {
    (DataManager.getInstance() as HeadlessDataManager).setTestDataProvider { dataId ->
      when {
        LAYOUT_INSPECTOR_DATA_KEY.`is`(dataId) -> inspectorRule.inspector
        else -> panel.getData(dataId)
      }
    }
  }

  @Test
  fun testLoadingPane() {
    val latch = ReportingCountDownLatch(1)
    inspectorRule.launchSynchronously = false
    appInspectorRule.viewInspector.listenWhen({ true }) {
      latch.await(20, TimeUnit.SECONDS)
      inspectorRule.inspectorModel.update(window("w1", 1L), listOf("w1"), 1)
    }
    val panel = DeviceViewPanel(inspectorRule.inspector, projectRule.fixture.testRootDisposable)
    val loadingPane = panel.flatten(false).filterIsInstance<JBLoadingPanel>().first()
    val contentPanel = panel.flatten(false).filterIsInstance<DeviceViewContentPanel>().first()

    assertThat(loadingPane.isLoading).isFalse()
    assertThat(contentPanel.showEmptyText).isTrue()

    // Start connecting, loading should show and empty text should not be visible
    inspectorRule.startLaunch(2)
    inspectorRule.processes.selectedProcess = process

    waitForCondition(1, TimeUnit.SECONDS) { loadingPane.isLoading && !contentPanel.showEmptyText }

    // Release the response from the agent and wait for connection.
    // The loading should stop and the empty text should not be visible, because now we are
    // connected and showing views on screen
    latch.countDown()
    inspectorRule.awaitLaunch()

    waitForCondition(1, TimeUnit.SECONDS) { !loadingPane.isLoading && !contentPanel.showEmptyText }
  }

  @Test
  fun testLoadingPanelWithStartAndStop() {
    val latch = ReportingCountDownLatch(1)
    inspectorRule.launchSynchronously = false
    appInspectorRule.viewInspector.listenWhen({ true }) {
      latch.await(5, TimeUnit.HOURS)
      inspectorRule.inspectorModel.update(window("w1", 1L), listOf("w1"), 1)
    }
    val panel = DeviceViewPanel(inspectorRule.inspector, projectRule.fixture.testRootDisposable)

    val loadingPane = panel.flatten(false).filterIsInstance<JBLoadingPanel>().first()
    val contentPanel = panel.flatten(false).filterIsInstance<DeviceViewContentPanel>().first()
    assertThat(loadingPane.isLoading).isFalse()
    assertThat(contentPanel.showEmptyText).isTrue()

    // Start connecting, loading should show
    inspectorRule.startLaunch(6)
    inspectorRule.processes.selectedProcess = process

    waitForCondition(1, TimeUnit.SECONDS) { loadingPane.isLoading }
    waitForCondition(1, TimeUnit.SECONDS) { !contentPanel.showEmptyText }

    // Stop connecting, loading should stop
    val dropdownAction = contentPanel.selectTargetAction?.dropDownAction
    if (dropdownAction is SelectProcessAction) {
      dropdownAction.updateActions(mock())
    } else if (dropdownAction is SelectDeviceAction) {
      dropdownAction.updateActions(mock())
    }
    val actionEvent = mock<AnActionEvent>()
    whenever(actionEvent.actionManager).thenReturn(mock())
    val stopAction =
      dropdownAction?.getChildren(actionEvent)?.first { it.templateText == "Stop Inspector" }
    stopAction?.actionPerformed(mock())

    waitForCondition(10, TimeUnit.SECONDS) { !loadingPane.isLoading }
    assertThat(contentPanel.showEmptyText).isTrue()

    // Release the response from the agent such that all waiting threads can complete (cleanup).
    latch.countDown()
    inspectorRule.awaitLaunch()
  }

  @Test
  fun testSelectProcessDropDown() {
    val panel = DeviceViewPanel(inspectorRule.inspector, projectRule.fixture.testRootDisposable)

    val selectTargetAction =
      panel.flatten(false).filterIsInstance<DeviceViewContentPanel>().first().selectTargetAction!!
    val dropDownAction = selectTargetAction.dropDownAction
    installCommandHandlers()
    connect(process)
    inspectorRule.processNotifier.addDevice(LEGACY_DEVICE)
    inspectorRule.processNotifier.addDevice(OLDER_LEGACY_DEVICE)
    if (dropDownAction is SelectProcessAction) {
      dropDownAction.updateActions(DataContext.EMPTY_CONTEXT)

      val children = dropDownAction.getChildren(null)
      assertThat(children).hasLength(4)
      // not alphabetically sorted in SelectProcessAction
      checkDeviceAction(children[0], enabled = true, ICON_PHONE, "Google Modern Model")
      checkDeviceAction(
        children[1],
        enabled = true,
        ICON_LEGACY_PHONE,
        "Google Legacy Model (Live inspection disabled for API < 29)",
      )
      checkDeviceAction(
        children[2],
        enabled = false,
        ICON_PHONE,
        "Google Older Legacy Model (Unsupported for API < 23)",
      )
      checkDeviceAction(
        children[3],
        enabled = true,
        StudioIcons.Shell.Toolbar.STOP,
        "Stop Inspector",
      )
    } else if (dropDownAction is SelectDeviceAction) {
      dropDownAction.updateActions(DataContext.EMPTY_CONTEXT)

      val children = dropDownAction.getChildren(null)
      assertThat(children).hasLength(4)
      // alphabetically sorted in SelectDeviceAction
      checkDeviceAction(
        children[0],
        enabled = true,
        ICON_LEGACY_PHONE,
        "Google Legacy Model (Live inspection disabled for API < 29)",
      )
      checkDeviceAction(
        children[1],
        enabled = true,
        ICON_PHONE,
        "Google Modern Model ${LayoutInspectorBundle.message("cant.detect.foreground.process")}",
      )
      checkDeviceAction(
        children[2],
        enabled = false,
        ICON_PHONE,
        "Google Older Legacy Model (Unsupported for API < 23)",
      )
      checkDeviceAction(
        children[3],
        enabled = true,
        StudioIcons.Shell.Toolbar.STOP,
        "Stop Inspector",
      )
    }
  }

  @RunsInEdt
  @Test
  fun testGotoDeclaration() {
    gotoDeclaration(VIEW2)
    fileOpenCaptureRule.checkEditor("demo.xml", lineNumber = 4, "<v2 android:id=\"@+id/v2\"/>")
  }

  @RunsInEdt
  @Test
  fun testGotoDeclarationOfViewWithoutAnId() {
    gotoDeclaration(VIEW3)
    fileOpenCaptureRule.checkNoNavigation()
    val notification1 = inspectorRule.notificationModel.notifications[1]
    assertThat(notification1.message)
      .isEqualTo("Cannot navigate to source because v3 in the layout demo.xml doesn't have an id.")
  }

  private fun gotoDeclaration(selectedView: Long) {
    installCommandHandlers()
    latch = CountDownLatch(1)
    connect(process)
    assertThat(latch?.await(1L, TimeUnit.SECONDS)).isTrue()
    projectRule.fixture.copyFileToProject(SdkConstants.FN_ANDROID_MANIFEST_XML)
    projectRule.fixture.addFileToProject(
      "res/layout/demo.xml",
      """
      <?xml version="1.0" encoding="utf-8"?>
      <v1 xmlns:android="http://schemas.android.com/apk/res/android"
          android:id="@+id/v1">
        <v2 android:id="@+id/v2"/>
        <v3/>
      </v1>
    """
        .trimIndent(),
    )

    val model = inspectorRule.inspectorModel
    val theme = ResourceReference.style(appNamespace, "AppTheme")
    model.resourceLookup.updateConfiguration(
      FolderConfiguration(),
      theme,
      process,
      fontScaleFromConfig = 1.0f,
      mainDisplayOrientation = 90,
      screenSize = Dimension(600, 800),
    )
    inspectorRule.inspector.treeSettings.hideSystemNodes = false
    val panel = DeviceViewPanel(inspectorRule.inspector, projectRule.fixture.testRootDisposable)
    delegateDataProvider(panel)
    val focusManager = FakeKeyboardFocusManager(projectRule.testRootDisposable)
    focusManager.focusOwner =
      panel.flatten(false).filterIsInstance<DeviceViewContentPanel>().single()
    val dispatcher = IdeKeyEventDispatcher(null)
    val modifier = if (SystemInfo.isMac) KeyEvent.META_DOWN_MASK else KeyEvent.CTRL_DOWN_MASK

    // Press ctrl-B / command-B when the selectedView is selected:
    model.setSelection(model[selectedView], SelectionOrigin.INTERNAL)
    dispatcher.dispatchKeyEvent(KeyEvent(panel, KeyEvent.KEY_PRESSED, 0, modifier, VK_B, 'B'))
    runDispatching { GotoDeclarationAction.lastAction?.join() }
    UIUtil.dispatchAllInvocationEvents()
  }

  private fun checkDeviceAction(action: AnAction, enabled: Boolean, icon: Icon?, text: String) {
    val presentation = action.templatePresentation.clone()
    val event: AnActionEvent = mock()
    whenever(event.presentation).thenReturn(presentation)
    action.update(event)
    assertThat(presentation.text).isEqualTo(text)
    assertThat(presentation.icon).isSameAs(icon)
    assertThat(presentation.isEnabled).isEqualTo(enabled)
  }

  private var lastImageType = AndroidWindow.ImageType.BITMAP_AS_REQUESTED

  private fun installCommandHandlers() {
    appInspectorRule.viewInspector.listenWhen({ true }) { command ->
      commands.add(command)

      when (command.specializedCase) {
        UPDATE_SCREENSHOT_TYPE_COMMAND ->
          lastImageType = command.updateScreenshotTypeCommand.type.toImageType()
        else -> {}
      }
      val window =
        window("w1", 1L, imageType = lastImageType) {
          view(VIEW1, 0, 0, 10, 10, qualifiedName = "v1", layout = demoLayout, viewId = view1Id) {
            view(VIEW2, 0, 0, 10, 10, qualifiedName = "v2", layout = demoLayout, viewId = view2Id)
            view(VIEW3, 0, 5, 10, 5, qualifiedName = "v3", layout = demoLayout)
          }
        }
      inspectorRule.inspectorModel.update(window, listOf("w1"), 1)
      latch?.countDown()
    }
  }

  private fun LayoutInspectorViewProtocol.Screenshot.Type.toImageType(): AndroidWindow.ImageType {
    return when (this) {
      LayoutInspectorViewProtocol.Screenshot.Type.SKP -> AndroidWindow.ImageType.SKP
      LayoutInspectorViewProtocol.Screenshot.Type.BITMAP ->
        AndroidWindow.ImageType.BITMAP_AS_REQUESTED
      else -> AndroidWindow.ImageType.UNKNOWN
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

  @get:Rule val disposableRule = DisposableRule()

  @get:Rule val edtRule = EdtRule()

  @get:Rule val projectRule = ProjectRule()

  @get:Rule val adbRule = FakeAdbRule()

  @Before
  fun setup() {
    ApplicationManager.getApplication()
      .registerServiceInstance(AdtUiCursorsProvider::class.java, TestAdtUiCursorsProvider())
    replaceAdtUiCursorWithPredefinedCursor(
      AdtUiCursorType.GRAB,
      Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR),
    )
    replaceAdtUiCursorWithPredefinedCursor(
      AdtUiCursorType.GRABBING,
      Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR),
    )
  }

  @Test
  fun testZoomOnConnect() {
    val coroutineScope = AndroidCoroutineScope(disposableRule.disposable)
    val model = InspectorModel(projectRule.project, coroutineScope)
    val processModel = ProcessesModel(TestProcessDiscovery())
    val deviceModel = DeviceModel(disposableRule.disposable, processModel)
    val launcher =
      InspectorClientLauncher(
        processModel,
        listOf(),
        projectRule.project,
        mock(),
        coroutineScope,
        disposableRule.disposable,
        executor = MoreExecutors.directExecutor(),
        metrics = mock(),
      )
    val clientSettings = InspectorClientSettings(projectRule.project)
    val treeSettings = FakeTreeSettings()
    val inspector =
      LayoutInspector(
        coroutineScope,
        processModel,
        deviceModel,
        null,
        clientSettings,
        launcher,
        model,
        NotificationModel(projectRule.project),
        treeSettings,
        MoreExecutors.directExecutor(),
      )
    treeSettings.hideSystemNodes = false
    val panel = DeviceViewPanel(inspector, disposableRule.disposable)

    val scrollPane = panel.flatten(false).filterIsInstance<JBScrollPane>().first()
    scrollPane.setSize(200, 300)

    assertThat(inspector.renderLogic.renderSettings.scalePercent).isEqualTo(100)

    val newWindow = window(ROOT, ROOT, 0, 0, 100, 200) { view(VIEW1, 25, 30, 50, 50) { image() } }

    model.update(newWindow, listOf(ROOT), 0)

    // now we should be zoomed to fit
    assertThat(inspector.renderLogic.renderSettings.scalePercent).isEqualTo(135)

    inspector.renderLogic.renderSettings.scalePercent = 200

    // Update the model
    val newWindow2 = window(ROOT, ROOT, 0, 0, 100, 200) { view(VIEW2, 50, 20, 30, 40) { image() } }
    model.update(newWindow2, listOf(ROOT), 0)

    // Should still have the manually set zoom
    assertThat(inspector.renderLogic.renderSettings.scalePercent).isEqualTo(200)
  }

  @Test
  fun testZoomOnConnectWithFiltering() {
    val coroutineScope = AndroidCoroutineScope(disposableRule.disposable)
    val model = InspectorModel(projectRule.project, coroutineScope)
    val notificationModel = NotificationModel(projectRule.project)
    val processModel = ProcessesModel(TestProcessDiscovery())
    val deviceModel = DeviceModel(disposableRule.disposable, processModel)
    val launcher =
      InspectorClientLauncher(
        processModel,
        listOf(),
        projectRule.project,
        notificationModel,
        coroutineScope,
        disposableRule.disposable,
        executor = MoreExecutors.directExecutor(),
        metrics = mock(),
      )
    val clientSettings = InspectorClientSettings(projectRule.project)
    val treeSettings = FakeTreeSettings()
    val inspector =
      LayoutInspector(
        coroutineScope,
        processModel,
        deviceModel,
        null,
        clientSettings,
        launcher,
        model,
        notificationModel,
        treeSettings,
        MoreExecutors.directExecutor(),
      )
    treeSettings.hideSystemNodes = true
    val panel = DeviceViewPanel(inspector, disposableRule.disposable)

    val scrollPane = panel.flatten(false).filterIsInstance<JBScrollPane>().first()
    scrollPane.setSize(200, 300)

    assertThat(inspector.renderLogic.renderSettings.scalePercent).isEqualTo(100)

    val newWindow = window(ROOT, ROOT, 0, 0, 100, 200) { view(VIEW1, 25, 30, 50, 50) { image() } }

    model.update(newWindow, listOf(ROOT), 0)

    // now we should be zoomed to fit
    assertThat(inspector.renderLogic.renderSettings.scalePercent).isEqualTo(135)
  }

  @Test
  fun testZoomOnConnectWithFilteringAndScreenSizeFromAppContext() {
    val coroutineScope = AndroidCoroutineScope(disposableRule.disposable)
    val model = InspectorModel(projectRule.project, coroutineScope)
    val notificationModel = NotificationModel(projectRule.project)
    val processModel = ProcessesModel(TestProcessDiscovery())
    val deviceModel = DeviceModel(disposableRule.disposable, processModel)
    val launcher =
      InspectorClientLauncher(
        processModel,
        listOf(),
        projectRule.project,
        notificationModel,
        coroutineScope,
        disposableRule.disposable,
        executor = MoreExecutors.directExecutor(),
        metrics = mock(),
      )
    val clientSettings = InspectorClientSettings(projectRule.project)
    val treeSettings = FakeTreeSettings()
    val inspector =
      LayoutInspector(
        coroutineScope,
        processModel,
        deviceModel,
        null,
        clientSettings,
        launcher,
        model,
        notificationModel,
        treeSettings,
        MoreExecutors.directExecutor(),
      )
    treeSettings.hideSystemNodes = true
    val panel = DeviceViewPanel(inspector, disposableRule.disposable)

    val scrollPane = panel.flatten(false).filterIsInstance<JBScrollPane>().first()
    scrollPane.setSize(200, 300)
    model.resourceLookup.screenDimension = Dimension(200, 300)

    assertThat(inspector.renderLogic.renderSettings.scalePercent).isEqualTo(100)

    val newWindow = window(ROOT, ROOT, 0, 0, 100, 200) { view(VIEW1, 25, 30, 50, 50) { image() } }

    model.update(newWindow, listOf(ROOT), 0)

    // now we should be zoomed to fit
    assertThat(inspector.renderLogic.renderSettings.scalePercent).isEqualTo(90)
  }

  @Test
  fun testDrawNewWindow() {
    val coroutineScope = AndroidCoroutineScope(disposableRule.disposable)
    val model = InspectorModel(projectRule.project, coroutineScope)
    val notificationModel = NotificationModel(projectRule.project)
    val processModel = ProcessesModel(TestProcessDiscovery())
    val deviceModel = DeviceModel(disposableRule.disposable, processModel)
    val launcher =
      InspectorClientLauncher(
        processModel,
        listOf(),
        projectRule.project,
        notificationModel,
        coroutineScope,
        disposableRule.disposable,
        executor = MoreExecutors.directExecutor(),
        metrics = mock(),
      )
    val clientSettings = InspectorClientSettings(projectRule.project)
    val treeSettings = FakeTreeSettings()
    val inspector =
      LayoutInspector(
        coroutineScope,
        processModel,
        deviceModel,
        null,
        clientSettings,
        launcher,
        model,
        notificationModel,
        treeSettings,
        MoreExecutors.directExecutor(),
      )
    treeSettings.hideSystemNodes = false
    val panel = DeviceViewPanel(inspector, disposableRule.disposable)

    val scrollPane = panel.flatten(false).filterIsInstance<JBScrollPane>().first()
    scrollPane.setSize(200, 300)

    val window1 = window(ROOT, ROOT, 0, 0, 100, 200) { view(VIEW1, 25, 30, 50, 50) { image() } }

    model.update(window1, listOf(ROOT), 0)
    waitForCondition(10.seconds) { ViewNode.readAccess { window1.root.drawChildren.size } == 1 }

    // Add another window
    val window2 = window(100, 100, 0, 0, 100, 200) { view(VIEW2, 50, 20, 30, 40) { image() } }
    // clear drawChildren for window2 so we can ensure they're regenerated
    ViewNode.writeAccess { window2.root.flatten().forEach { it.drawChildren.clear() } }

    model.update(window2, listOf(ROOT, 100), 1)

    // drawChildren for the new window should be populated
    waitForCondition(10.seconds) { ViewNode.readAccess { window2.root.drawChildren.size } > 0 }
  }

  @Test
  fun testNewWindowDoesntResetZoom() {
    val coroutineScope = AndroidCoroutineScope(disposableRule.disposable)
    val model = InspectorModel(projectRule.project, coroutineScope)
    val notificationModel = NotificationModel(projectRule.project)
    val processModel = ProcessesModel(TestProcessDiscovery())
    val deviceModel = DeviceModel(disposableRule.disposable, processModel)
    val launcher: InspectorClientLauncher = mock()
    val client: InspectorClient = mock()
    whenever(client.isConnected).thenReturn(true)
    whenever(client.stats).thenReturn(mock())
    whenever(launcher.activeClient).thenReturn(client)

    val clientSettings = InspectorClientSettings(projectRule.project)
    val treeSettings = FakeTreeSettings()
    val inspector =
      LayoutInspector(
        coroutineScope,
        processModel,
        deviceModel,
        null,
        clientSettings,
        launcher,
        model,
        notificationModel,
        treeSettings,
        MoreExecutors.directExecutor(),
      )
    treeSettings.hideSystemNodes = false
    val panel = DeviceViewPanel(inspector, disposableRule.disposable)

    val scrollPane = panel.flatten(false).filterIsInstance<JBScrollPane>().first()
    val contentPanelModel =
      panel.flatten(false).filterIsInstance<DeviceViewContentPanel>().first().renderModel
    scrollPane.setSize(200, 300)

    val window1 = window(ROOT, ROOT, 0, 0, 100, 200) { view(VIEW1, 25, 30, 50, 50) { image() } }

    model.update(window1, listOf(ROOT), 0)
    waitForCondition(10.seconds) { contentPanelModel.hitRects.size == 2 }

    inspector.renderLogic.renderSettings.scalePercent = 33

    // Add another window
    val window2 = window(ROOT2, ROOT2, 0, 0, 100, 200) { view(VIEW2, 50, 20, 30, 40) { image() } }

    val latch = CountDownLatch(1)
    model.update(window2, listOf(ROOT, ROOT2), 1) { latch.countDown() }
    latch.await()

    // Make sure model has been refreshed.
    contentPanelModel.refresh()
    waitForCondition(10.seconds) { contentPanelModel.hitRects.size == 4 }

    // we should still have the manually set zoom
    assertThat(inspector.renderLogic.renderSettings.scalePercent).isEqualTo(33)
  }

  @Test
  fun testDragWithSpace() {
    testPan({ ui, _ -> ui.keyboard.press(VK_SPACE) }, { ui, _ -> ui.keyboard.release(VK_SPACE) })
  }

  @Test
  fun testDragInPanMode() {
    testPan({ _, panel -> panel.isPanning = true }, { _, panel -> panel.isPanning = false })
  }

  @Test
  fun testDragWithMiddleButton() {
    testPan({ _, _ -> }, { _, _ -> }, Button.MIDDLE)
  }

  @Test
  fun testDragWithSpaceFromSnapshot() {
    testPan(
      { ui, _ -> ui.keyboard.press(VK_SPACE) },
      { ui, _ -> ui.keyboard.release(VK_SPACE) },
      fromSnapshot = true,
    )
  }

  @Test
  fun testDragInPanModeFromSnapShot() {
    testPan(
      { _, panel -> panel.isPanning = true },
      { _, panel -> panel.isPanning = false },
      fromSnapshot = true,
    )
  }

  @Test
  fun testDragWithMiddleButtonFromSnapshot() {
    testPan({ _, _ -> }, { _, _ -> }, Button.MIDDLE, fromSnapshot = true)
  }

  private fun testPan(
    startPan: (FakeUi, DeviceViewPanel) -> Unit,
    endPan: (FakeUi, DeviceViewPanel) -> Unit,
    panButton: Button = Button.LEFT,
    fromSnapshot: Boolean = false,
  ) {
    val model =
      model(disposableRule.disposable) {
        view(ROOT, 0, 0, 100, 200) { view(VIEW1, 25, 30, 50, 50) }
      }

    val notificationModel = NotificationModel(projectRule.project)
    val launcher: InspectorClientLauncher = mock()
    val coroutineScope = AndroidCoroutineScope(disposableRule.disposable)
    val client: InspectorClient = mock()
    whenever(client.capabilities).thenReturn(setOf(InspectorClient.Capability.SUPPORTS_SKP))
    whenever(client.stats).thenReturn(mock())
    whenever(launcher.activeClient).thenReturn(client)
    val clientSettings = InspectorClientSettings(projectRule.project)
    val treeSettings = FakeTreeSettings()
    treeSettings.hideSystemNodes = false

    val inspector: LayoutInspector
    val processes: ProcessesModel?
    val deviceModel: DeviceModel?
    if (fromSnapshot) {
      deviceModel = null
      processes = null
      inspector =
        LayoutInspector(
          coroutineScope,
          clientSettings,
          client,
          model,
          notificationModel,
          treeSettings,
        )
    } else {
      val fakeProcess = createFakeStream().createFakeProcess()
      val latch = CountDownLatch(1)
      processes = ProcessesModel(TestProcessDiscovery())
      processes.addSelectedProcessListeners { latch.countDown() }

      processes.selectedProcess = fakeProcess
      latch.await()

      deviceModel = DeviceModel(disposableRule.disposable, processes)
      inspector =
        LayoutInspector(
          coroutineScope,
          processes,
          deviceModel,
          null,
          clientSettings,
          launcher,
          model,
          notificationModel,
          treeSettings,
          MoreExecutors.directExecutor(),
        )
    }
    val panel = DeviceViewPanel(inspector, disposableRule.disposable)

    val contentPanel = panel.flatten(false).filterIsInstance<DeviceViewContentPanel>().first()
    val viewport = panel.flatten(false).filterIsInstance<JViewport>().first()

    (DataManager.getInstance() as HeadlessDataManager).setTestDataProvider { id ->
      if (id == LAYOUT_INSPECTOR_DATA_KEY.name) inspector else null
    }

    if (!fromSnapshot) {
      assertThat(processes?.selectedProcess).isNotNull()
    }

    contentPanel.setSize(200, 300)
    viewport.extentSize = Dimension(100, 100)

    assertThat(viewport.viewPosition).isEqualTo(Point(0, 0))

    val fakeUi = FakeUi(contentPanel)
    fakeUi.keyboard.setFocus(contentPanel)

    // Rotate the model so that dragging would normally rotate
    contentPanel.renderModel.xOff = 0.02

    assertThat(panel.isPanning).isFalse()
    startPan(fakeUi, panel)
    fakeUi.mouse.press(20, 20, panButton)
    assertThat(panel.isPanning).isTrue()
    fakeUi.mouse.dragTo(10, 10)
    assertThat(panel.isPanning).isTrue()
    fakeUi.mouse.release()

    // Unchanged--we panned instead
    TestCase.assertEquals(0.02, contentPanel.renderModel.xOff)
    TestCase.assertEquals(0.0, contentPanel.renderModel.yOff)
    assertThat(viewport.viewPosition).isEqualTo(Point(10, 10))

    endPan(fakeUi, panel)
    // Now we'll actually rotate
    fakeUi.mouse.drag(20, 20, -10, -10)
    assertThat(panel.isPanning).isFalse()
    TestCase.assertEquals(0.01, contentPanel.renderModel.xOff)
    TestCase.assertEquals(-0.01, contentPanel.renderModel.yOff)

    startPan(fakeUi, panel)
    fakeUi.mouse.press(20, 20, panButton)
    assertThat(panel.isPanning).isTrue()

    // make sure that disconnecting the process disables panning
    if (!fromSnapshot) {
      processes?.selectedProcess = null
      assertThat(panel.isPanning).isFalse()
    }
  }
}

@RunsInEdt
class MyViewportLayoutManagerTest {
  private lateinit var scrollPane: JScrollPane
  private lateinit var contentPanel: JComponent
  private lateinit var layoutManager: MyViewportLayoutManager

  private var layerSpacing = INITIAL_LAYER_SPACING

  private var rootPosition: Point? = Point(400, 500)
  private var rootLocationCompute: () -> Point? = { rootPosition }
  private var rootLocation: () -> Point? = { rootLocationCompute() }

  @get:Rule val edtRule = EdtRule()

  @Before
  fun setUp() {
    contentPanel = JPanel()
    scrollPane = JBScrollPane(contentPanel)

    // Avoid MacScrollBarUI setting making the scrollbars opaque.
    // That would make these test fail if they are run multiple times on Mac.
    scrollPane.horizontalScrollBar.setUI(BasicScrollBarUI())
    scrollPane.verticalScrollBar.setUI(BasicScrollBarUI())
    scrollPane.horizontalScrollBar.isOpaque = false
    scrollPane.verticalScrollBar.isOpaque = false

    scrollPane.size = Dimension(502, 202)
    scrollPane.preferredSize = Dimension(502, 202)
    contentPanel.preferredSize = Dimension(1000, 1000)
    layoutManager = MyViewportLayoutManager(scrollPane.viewport, { layerSpacing }, rootLocation)
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
  @Ignore("b/353455979")
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

  @Test
  fun testScrollPaneShouldNotOscillateInPlace() {
    // This is a regression test for a bug that happened frequently on Mac (see b/240289276)
    // First make the scrollbars opaque (that affect the viewport size when scrollbars are needed)
    // This will emulate how scrollbars act on a Mac
    scrollPane.horizontalScrollBar.isOpaque = true
    scrollPane.verticalScrollBar.isOpaque = true

    // Mimic the real location computation which involves the size of the view
    // (DeviceViewContentPanel)
    val modelLocation = Point(-500, 0)
    rootLocationCompute = {
      Point(modelLocation).apply { translate(contentPanel.width / 2, contentPanel.height / 2) }
    }

    // Set the size such that the scrollpane can hold the preferred size of the
    // DeviceViewContentPanel if there
    // are no horizontal scrollbar, but is too small if the horizontal scrollbar is present:
    scrollPane.size = Dimension(1500, 1010)
    scrollPane.preferredSize = scrollPane.size
    scrollPane.layout.layoutContainer(scrollPane)
    layoutManager.layoutContainer(scrollPane.viewport)
    scrollPane.viewport.viewPosition = Point(7, 0)

    // Init the cached data:
    scrollPane.layout.layoutContainer(scrollPane)
    layoutManager.layoutContainer(scrollPane.viewport)
    scrollPane.layout.layoutContainer(scrollPane)
    layoutManager.layoutContainer(scrollPane.viewport)
    val pos = scrollPane.viewport.viewPosition
    val positions = mutableListOf<Point>()
    val expected = mutableListOf<Point>()

    // If the bug is still present the viewPosition will oscillate between 2 points:
    for (i in 1..20) {
      scrollPane.layout.layoutContainer(scrollPane)
      layoutManager.layoutContainer(scrollPane.viewport)
      positions.add(scrollPane.viewport.viewPosition)
      expected.add(pos)
    }
    assertThat(positions).isEqualTo(expected)
  }
}

@RunsInEdt
class DeviceViewPanelWithNoClientsTest {
  private val disposableRule = DisposableRule()
  private val projectRule = AndroidProjectRule.onDisk()
  private val process = MODERN_PROCESS
  private val appInspectorRule =
    AppInspectionInspectorRule(projectRule, withDefaultResponse = false)
  private val postCreateLatch = CountDownLatch(1)
  private val inspectorRule =
    LayoutInspectorRule(
      clientProviders =
        listOf(
          InspectorClientProvider { _, _ ->
            postCreateLatch.await()
            null
          }
        ),
      projectRule = projectRule,
      isPreferredProcess = { it.name == process.name },
    )

  @get:Rule
  val ruleChain =
    RuleChain.outerRule(projectRule)
      .around(appInspectorRule)
      .around(inspectorRule)
      .around(IconLoaderRule())
      .around(EdtRule())
      .around(disposableRule)!!

  @Test
  fun testLoadingPane() {
    inspectorRule.startLaunch(4)
    inspectorRule.launchSynchronously = false
    val panel = DeviceViewPanel(inspectorRule.inspector, projectRule.fixture.testRootDisposable)
    val loadingPane = panel.flatten(false).filterIsInstance<JBLoadingPanel>().first()
    val contentPanel = panel.flatten(false).filterIsInstance<DeviceViewContentPanel>().first()
    assertThat(loadingPane.isLoading).isFalse()
    assertThat(contentPanel.showEmptyText).isTrue()

    // Start connecting, loading should show
    inspectorRule.processes.selectedProcess = process

    waitForCondition(1, TimeUnit.SECONDS) { loadingPane.isLoading }
    waitForCondition(1, TimeUnit.SECONDS) { !contentPanel.showEmptyText }
    postCreateLatch.countDown()
    inspectorRule.awaitLaunch()

    waitForCondition(1, TimeUnit.SECONDS) { !loadingPane.isLoading }
    waitForCondition(1, TimeUnit.SECONDS) { contentPanel.showEmptyText }
  }

  @Test
  fun testNotDebuggablePane() {
    inspectorRule.startLaunch(4)
    inspectorRule.launchSynchronously = false
    val panel = DeviceViewPanel(inspectorRule.inspector, projectRule.fixture.testRootDisposable)

    val deviceViewContentPanel =
      panel.flatten(false).filterIsInstance<DeviceViewContentPanel>().first()

    // false by default
    assertThat(deviceViewContentPanel.showProcessNotDebuggableText).isFalse()
    assertThat(deviceViewContentPanel.showNavigateToDebuggableProcess).isFalse()

    // connect device
    inspectorRule.inspector.deviceModel?.setSelectedDevice(MODERN_DEVICE)

    // remains false, because the device is connected but no foreground process showed up yet
    assertThat(deviceViewContentPanel.showProcessNotDebuggableText).isFalse()

    // becomes true, because the device is connected but no foreground process showed up yet
    assertThat(deviceViewContentPanel.showNavigateToDebuggableProcess).isTrue()

    // send a non-debuggable process
    panel.onNewForegroundProcess(false)

    // remains true because the foreground process is not in the process model
    assertThat(deviceViewContentPanel.showProcessNotDebuggableText).isTrue()
    assertThat(deviceViewContentPanel.showNavigateToDebuggableProcess).isFalse()

    inspectorRule.processNotifier.addDevice(process.device)
    inspectorRule.processNotifier.fireConnected(process)

    postCreateLatch.countDown()
    inspectorRule.awaitLaunch()

    // send a debuggable process
    panel.onNewForegroundProcess(true)

    // goes back to false because MODERN_PROCESS is in the process model
    assertThat(deviceViewContentPanel.showProcessNotDebuggableText).isFalse()
    assertThat(deviceViewContentPanel.showNavigateToDebuggableProcess).isFalse()
  }
}

private fun Common.Stream.createFakeProcess(name: String? = null, pid: Int = 0): ProcessDescriptor {
  return TransportProcessDescriptor(
    this,
    FakeTransportService.FAKE_PROCESS.toBuilder()
      .setName(name ?: FakeTransportService.FAKE_PROCESS_NAME)
      .setPid(pid)
      .build(),
  )
}

private fun createFakeStream(): Common.Stream {
  return Common.Stream.newBuilder().setDevice(FakeTransportService.FAKE_DEVICE).build()
}
