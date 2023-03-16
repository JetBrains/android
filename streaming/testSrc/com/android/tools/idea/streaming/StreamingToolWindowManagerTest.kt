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
package com.android.tools.idea.streaming

import com.android.adblib.DevicePropertyNames
import com.android.emulator.control.KeyboardEvent
import com.android.emulator.control.PaneEntry
import com.android.emulator.control.PaneEntry.PaneIndex
import com.android.sdklib.internal.avd.AvdInfo
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.adtui.actions.ZoomType
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.HeadlessDialogRule
import com.android.tools.adtui.swing.PortableUiFontRule
import com.android.tools.adtui.swing.createModalDialogAndInteractWithIt
import com.android.tools.idea.avdmanager.AvdLaunchListener
import com.android.tools.idea.avdmanager.AvdLaunchListener.RequestType
import com.android.tools.idea.concurrency.AndroidExecutors
import com.android.tools.idea.concurrency.waitForCondition
import com.android.tools.idea.protobuf.TextFormat
import com.android.tools.idea.run.DeviceHeadsUpListener
import com.android.tools.idea.streaming.device.FakeScreenSharingAgentRule
import com.android.tools.idea.streaming.device.isFFmpegAvailableToTest
import com.android.tools.idea.streaming.emulator.EmulatorController
import com.android.tools.idea.streaming.emulator.EmulatorToolWindowPanel
import com.android.tools.idea.streaming.emulator.EmulatorView
import com.android.tools.idea.streaming.emulator.FakeEmulator
import com.android.tools.idea.streaming.emulator.FakeEmulatorRule
import com.android.tools.idea.streaming.emulator.RunningEmulatorCatalog
import com.android.tools.idea.testing.AndroidExecutorsRule
import com.android.tools.idea.testing.DisposerExplorer
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.ide.ui.LafManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ToolWindowType
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.PlatformTestUtil.dispatchAllEventsInIdeEventQueue
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.replaceService
import com.intellij.ui.content.ContentManager
import com.intellij.util.ConcurrencyUtil.awaitQuiescence
import com.intellij.util.ui.UIUtil.dispatchAllInvocationEvents
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.awt.Dimension
import java.awt.Point
import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import javax.swing.JButton
import javax.swing.JViewport
import javax.swing.UIManager

/**
 * Tests for [StreamingToolWindowManager] and [StreamingToolWindowFactory].
 */
@RunsInEdt
class StreamingToolWindowManagerTest {
  private val agentRule = FakeScreenSharingAgentRule()
  private val emulatorRule = FakeEmulatorRule()
  private val androidExecutorsRule = AndroidExecutorsRule(workerThreadExecutor = Executors.newCachedThreadPool())
  @get:Rule
  val ruleChain = RuleChain(agentRule, emulatorRule, androidExecutorsRule, EdtRule(), PortableUiFontRule(), HeadlessDialogRule())

  private val windowFactory: StreamingToolWindowFactory by lazy { StreamingToolWindowFactory() }
  private val toolWindow: ToolWindow by lazy { createToolWindow() }
  private val contentManager: ContentManager by lazy { toolWindow.contentManager }

  private val deviceMirroringSettings: DeviceMirroringSettings by lazy { DeviceMirroringSettings.getInstance() }

  private val project get() = agentRule.project
  private val testRootDisposable get() = agentRule.testRootDisposable
  private val dataContext = DataContext {
    when(it) {
      CommonDataKeys.PROJECT.name -> project
      PlatformDataKeys.TOOL_WINDOW.name -> toolWindow
      else -> null
    }
  }

  @Before
  fun setUp() {
    val mockLafManager = mock<LafManager>()
    whenever(mockLafManager.currentLookAndFeel).thenReturn(UIManager.LookAndFeelInfo("IntelliJ Light", "Ignored className"))
    ApplicationManager.getApplication().replaceService(LafManager::class.java, mockLafManager, testRootDisposable)

    deviceMirroringSettings.deviceMirroringEnabled = true
    deviceMirroringSettings.confirmationDialogShown = true
  }

  @After
  fun tearDown() {
    Disposer.dispose(toolWindow.disposable)
    dispatchAllEventsInIdeEventQueue() // Finish asynchronous processing triggered by hiding the tool window.
    deviceMirroringSettings.loadState(DeviceMirroringSettings()) // Reset device mirroring settings to defaults.
  }

  @Test
  fun testTabManagement() {
    createToolWindowContent()
    assertThat(contentManager.contents).isEmpty()

    val tempFolder = emulatorRule.root
    val emulator1 = emulatorRule.newEmulator(FakeEmulator.createPhoneAvd(tempFolder), standalone = false)
    val emulator2 = emulatorRule.newEmulator(FakeEmulator.createTabletAvd(tempFolder), standalone = true)
    val emulator3 = emulatorRule.newEmulator(FakeEmulator.createWatchAvd(tempFolder), standalone = false)

    // The Emulator tool window is closed.
    assertThat(toolWindow.isVisible).isFalse()

    // Start the first and the second emulators.
    emulator1.start()
    emulator2.start()

    // Send notification that the emulator has been launched.
    val avdInfo = AvdInfo(emulator1.avdId, emulator1.avdFolder.resolve("config.ini"), emulator1.avdFolder, mock(), null)
    val commandLine = GeneralCommandLine("/emulator_home/fake_emulator", "-avd", emulator1.avdId, "-qt-hide-window")
    project.messageBus.syncPublisher(AvdLaunchListener.TOPIC).avdLaunched(avdInfo, commandLine, RequestType.INDIRECT, project)
    dispatchAllInvocationEvents()
    assertThat(toolWindow.isVisible).isFalse() // Indirect AVD launches don't open the Running Devices tool window.

    project.messageBus.syncPublisher(AvdLaunchListener.TOPIC).avdLaunched(avdInfo, commandLine, RequestType.DIRECT, project)
    dispatchAllInvocationEvents()
    // The Running Devices tool is opened when an embedded emulator is launched by a direct request.
    assertThat(toolWindow.isVisible).isTrue()

    waitForCondition(2, TimeUnit.SECONDS) { contentManager.contents.isNotEmpty() }
    assertThat(contentManager.contents).hasLength(1)
    waitForCondition(2, TimeUnit.SECONDS) { RunningEmulatorCatalog.getInstance().emulators.isNotEmpty() }
    emulator1.getNextGrpcCall(2, TimeUnit.SECONDS) { true } // Wait for the initial "getVmState" call.
    waitForCondition(2, TimeUnit.SECONDS) { contentManager.contents[0].displayName != null }
    assertThat(contentManager.contents[0].displayName).isEqualTo(emulator1.avdName)

    // Start the third emulator.
    emulator3.start()

    waitForCondition(3, TimeUnit.SECONDS) { contentManager.contents.size == 2 }

    // The second emulator panel is added but the first one is still selected.
    assertThat(contentManager.contents[0].displayName).isEqualTo(emulator3.avdName)
    assertThat(contentManager.contents[1].displayName).isEqualTo(emulator1.avdName)
    assertThat(contentManager.contents[1].isSelected).isTrue()

    for (emulator in listOf(emulator2, emulator3)) {
      project.messageBus.syncPublisher(DeviceHeadsUpListener.TOPIC).userInvolvementRequired("emulator-${emulator.serialPort}", project)
    }

    // Deploying an app activates the corresponding emulator panel.
    waitForCondition(2, TimeUnit.SECONDS) { contentManager.contents[0].isSelected }

    assertThat(contentManager.contents).hasLength(2)

    // Stop the second emulator.
    emulator3.stop()

    // The panel corresponding to the second emulator goes away.
    waitForCondition(5, TimeUnit.SECONDS) { contentManager.contents.size == 1 }
    assertThat(contentManager.contents[0].displayName).isEqualTo(emulator1.avdName)
    assertThat(contentManager.contents[0].isSelected).isTrue()

    // Close the panel corresponding to emulator1.
    contentManager.removeContent(contentManager.contents[0], true)
    val call = emulator1.getNextGrpcCall(2, TimeUnit.SECONDS,
                                         FakeEmulator.defaultCallFilter.or("android.emulation.control.UiController/closeExtendedControls"))
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/setVmState")
    assertThat(TextFormat.shortDebugString(call.request)).isEqualTo("state: SHUTDOWN")

    // The panel corresponding the first emulator goes away and is replaced by the empty state panel.
    assertThat(contentManager.contents.size).isEqualTo(1)
    assertThat(contentManager.contents[0].component).isInstanceOf(EmptyStatePanel::class.java)
    assertThat(contentManager.contents[0].displayName).isNull()
  }

  @Test
  fun testEmulatorCrash() {
    createToolWindowContent()

    val tempFolder = emulatorRule.root
    val emulator = emulatorRule.newEmulator(FakeEmulator.createPhoneAvd(tempFolder))

    toolWindow.show()

    // Start the emulator.
    emulator.start()

    val controllers = RunningEmulatorCatalog.getInstance().updateNow().get()
    waitForCondition(3, TimeUnit.SECONDS) { contentManager.contents.isNotEmpty() }
    assertThat(contentManager.contents[0].displayName).isEqualTo(emulator.avdName)
    assertThat(controllers).isNotEmpty()
    waitForCondition(5, TimeUnit.SECONDS) { controllers.first().connectionState == EmulatorController.ConnectionState.CONNECTED }

    // Simulate an emulator crash.
    emulator.crash()
    controllers.first().sendKey(KeyboardEvent.newBuilder().setText(" ").build())
    waitForCondition(5, TimeUnit.SECONDS) { contentManager.contents[0].displayName == null }
    assertThat(contentManager.contents[0].component).isInstanceOf(EmptyStatePanel::class.java)
  }

  @Test
  fun testUiStatePreservation() {
    createToolWindowContent()

    val tempFolder = emulatorRule.root
    val emulator = emulatorRule.newEmulator(FakeEmulator.createPhoneAvd(tempFolder))

    toolWindow.show()

    // Start the emulator.
    emulator.start()

    waitForCondition(2, TimeUnit.SECONDS) { contentManager.contents.isNotEmpty() }
    assertThat(contentManager.contents).hasLength(1)
    waitForCondition(2, TimeUnit.SECONDS) { RunningEmulatorCatalog.getInstance().emulators.isNotEmpty() }
    val emulatorController = RunningEmulatorCatalog.getInstance().emulators.first()
    waitForCondition(4, TimeUnit.SECONDS) { emulatorController.connectionState == EmulatorController.ConnectionState.CONNECTED }
    waitForCondition(2, TimeUnit.SECONDS) { contentManager.contents[0].displayName != null }
    assertThat(contentManager.contents[0].displayName).isEqualTo(emulator.avdName)

    assertThat(emulator.extendedControlsVisible).isFalse()
    emulatorController.showExtendedControls(PaneEntry.newBuilder().setIndex(PaneIndex.KEEP_CURRENT).build())
    // Wait for the extended controls to show.
    waitForCondition(2, TimeUnit.SECONDS) { emulator.extendedControlsVisible }

    val panel = contentManager.contents[0].component as EmulatorToolWindowPanel

    toolWindow.hide()

    // Wait for the extended controls to close.
    waitForCondition(4, TimeUnit.SECONDS) { !emulator.extendedControlsVisible }
    // Wait for the prior visibility state of the extended controls to propagate to Studio.
    waitForCondition(2, TimeUnit.SECONDS) { panel.lastUiState?.extendedControlsShown ?: false }

    toolWindow.show()

    // Wait for the extended controls to show.
    waitForCondition(2, TimeUnit.SECONDS) { emulator.extendedControlsVisible }
  }

  @Test
  fun testZoomStatePreservation() {
    createToolWindowContent()

    val tempFolder = emulatorRule.root
    val emulator = emulatorRule.newEmulator(FakeEmulator.createPhoneAvd(tempFolder))

    toolWindow.show()

    // Start the emulator.
    emulator.start()

    waitForCondition(2, TimeUnit.SECONDS) { contentManager.contents.isNotEmpty() }
    assertThat(contentManager.contents).hasLength(1)
    waitForCondition(2, TimeUnit.SECONDS) { RunningEmulatorCatalog.getInstance().emulators.isNotEmpty() }
    val emulatorController = RunningEmulatorCatalog.getInstance().emulators.first()
    waitForCondition(4, TimeUnit.SECONDS) { emulatorController.connectionState == EmulatorController.ConnectionState.CONNECTED }
    waitForCondition(2, TimeUnit.SECONDS) { contentManager.contents[0].displayName != null }
    assertThat(contentManager.contents[0].displayName).isEqualTo(emulator.avdName)

    val panel = contentManager.contents[0].component as EmulatorToolWindowPanel
    panel.setSize(250, 500)
    val ui = FakeUi(panel)
    val emulatorView = ui.getComponent<EmulatorView>()
    waitForCondition(2, TimeUnit.SECONDS) { renderAndGetFrameNumber(ui, emulatorView) > 0 }

    // Zoom in.
    emulatorView.zoom(ZoomType.IN)
    ui.layoutAndDispatchEvents()
    assertThat(emulatorView.scale).isWithin(0.0001).of(0.25)
    var viewport = emulatorView.parent as JViewport
    val viewportSize = viewport.viewSize
    assertThat(viewportSize).isEqualTo(Dimension(396, 811))
    // Scroll to the bottom.
    val scrollPosition = Point(viewport.viewPosition.x, viewport.viewSize.height - viewport.height)
    viewport.viewPosition = scrollPosition

    toolWindow.hide()
    toolWindow.show()

    ui.layoutAndDispatchEvents()
    assertThat(emulatorView.scale).isWithin(0.0001).of(0.25)
    viewport = emulatorView.parent as JViewport
    assertThat(viewport.viewSize).isEqualTo(viewportSize)
    assertThat(viewport.viewPosition).isEqualTo(scrollPosition)
  }

  @Test
  fun testPhysicalDevice() {
    if (!isFFmpegAvailableToTest()) {
      return
    }
    createToolWindowContent()
    assertThat(contentManager.contents).isEmpty()
    assertThat(toolWindow.isVisible).isFalse()

    val device = agentRule.connectDevice("Pixel 4", 30, Dimension(1080, 2280), "arm64-v8a")
    toolWindow.show()

    waitForCondition(15, TimeUnit.SECONDS) { contentManager.contents.size == 1 && contentManager.contents[0].displayName != null }
    assertThat(contentManager.contents[0].displayName).isEqualTo("Pixel 4 API 30")

    agentRule.disconnectDevice(device)
    waitForCondition(2, TimeUnit.SECONDS) { contentManager.contents.size == 1 && contentManager.contents[0].displayName == null }
    waitForCondition(2, TimeUnit.SECONDS) { !device.agent.isRunning }

    // Check that PhysicalDeviceWatcher gets disposed after disabling device mirroring.
    // DisposerExplorer is used because alternative ways of testing this are pretty slow.
    val physicalDeviceWatcherClassName = "com.android.tools.idea.streaming.StreamingToolWindowManager\$PhysicalDeviceWatcher"
    assertThat(DisposerExplorer.findAll { it.javaClass.name == physicalDeviceWatcherClassName }).hasSize(1)
    deviceMirroringSettings.deviceMirroringEnabled = false
    assertThat(DisposerExplorer.findAll { it.javaClass.name == physicalDeviceWatcherClassName }).isEmpty()
  }

  @Test
  fun testPhysicalDeviceActivateOnConnection() {
    if (!isFFmpegAvailableToTest()) {
      return
    }
    createToolWindowContent()
    assertThat(contentManager.contents).isEmpty()
    assertThat(toolWindow.isVisible).isFalse()

    deviceMirroringSettings.activateOnConnection = true
    val device = agentRule.connectDevice("Pixel 4", 30, Dimension(1080, 2280), "arm64-v8a")

    waitForCondition(15, TimeUnit.SECONDS) { contentManager.contents.size == 1 && contentManager.contents[0].displayName != null }
    assertThat(contentManager.contents[0].displayName).isEqualTo("Pixel 4 API 30")

    agentRule.disconnectDevice(device)
    waitForCondition(2, TimeUnit.SECONDS) { contentManager.contents.size == 1 && contentManager.contents[0].displayName == null }
    waitForCondition(2, TimeUnit.SECONDS) { !device.agent.isRunning }
  }

  @Test
  fun testPhysicalDeviceRequestsAttention() {
    if (!isFFmpegAvailableToTest()) {
      return
    }
    createToolWindowContent()
    assertThat(contentManager.contents).isEmpty()
    assertThat(toolWindow.isVisible).isFalse()

    val device1 = agentRule.connectDevice("Pixel 4", 30, Dimension(1080, 2280), "arm64-v8a")
    val device2 = agentRule.connectDevice("Pixel 6", 32, Dimension(1080, 2400), "arm64-v8a")
    project.messageBus.syncPublisher(DeviceHeadsUpListener.TOPIC).launchingApp(device2.serialNumber, project)

    waitForCondition(15, TimeUnit.SECONDS) { contentManager.contents.size == 2 }
    assertThat(contentManager.contents[0].displayName).isEqualTo("Pixel 4 API 30")
    assertThat(contentManager.contents[1].displayName).isEqualTo("Pixel 6 API 32")
    assertThat(contentManager.selectedContent?.displayName).isEqualTo("Pixel 6 API 32")
    assertThat(toolWindow.isVisible).isTrue()

    deviceMirroringSettings.activateOnTestLaunch = true
    project.messageBus.syncPublisher(DeviceHeadsUpListener.TOPIC).launchingTest(device1.serialNumber, project)
    assertThat(contentManager.selectedContent?.displayName).isEqualTo("Pixel 4 API 30")

    deviceMirroringSettings.activateOnAppLaunch = false
    project.messageBus.syncPublisher(DeviceHeadsUpListener.TOPIC).launchingApp(device2.serialNumber, project)
    assertThat(contentManager.selectedContent?.displayName).isEqualTo("Pixel 4 API 30")

    agentRule.disconnectDevice(device1)
    agentRule.disconnectDevice(device2)
    waitForCondition(10, TimeUnit.SECONDS) { contentManager.contents.size == 1 && contentManager.contents[0].displayName == null }
  }

  @Test
  fun testPhysicalDeviceRequestsAttentionMirroringDisabled() {
    if (!isFFmpegAvailableToTest()) {
      return
    }
    deviceMirroringSettings.deviceMirroringEnabled = false
    createToolWindowContent()
    assertThat(contentManager.contents).isEmpty()
    assertThat(toolWindow.isVisible).isFalse()

    val device1 = agentRule.connectDevice("Pixel 4", 30, Dimension(1080, 2280), "arm64-v8a")
    project.messageBus.syncPublisher(DeviceHeadsUpListener.TOPIC).userInvolvementRequired(device1.serialNumber, project)

    dispatchAllEventsInIdeEventQueue()
    awaitQuiescence(AndroidExecutors.getInstance().workerThreadExecutor as ThreadPoolExecutor, 5, TimeUnit.SECONDS)
    dispatchAllEventsInIdeEventQueue()
    assertThat(toolWindow.isVisible).isFalse()
  }

  @Test
  fun testMirroringConfirmationDialogAccept() {
    if (!isFFmpegAvailableToTest()) {
      return
    }
    createToolWindowContent()
    assertThat(toolWindow.isVisible).isFalse()

    deviceMirroringSettings.confirmationDialogShown = false

    val device = agentRule.connectDevice("Pixel 4", 30, Dimension(1080, 2280), "arm64-v8a")

    createModalDialogAndInteractWithIt(toolWindow::show) { dlg ->
      val ui = FakeUi(dlg.rootPane)
      ui.clickOn(ui.getComponent<JButton> { it.text == "Acknowledge" })
    }

    waitForCondition(15, TimeUnit.SECONDS) { contentManager.contents.size == 1 && contentManager.contents[0].displayName != null }
    assertThat(contentManager.contents[0].displayName).isEqualTo("Pixel 4 API 30")

    agentRule.disconnectDevice(device)
    waitForCondition(10, TimeUnit.SECONDS) { contentManager.contents.size == 1 && contentManager.contents[0].displayName == null }

    assertThat(deviceMirroringSettings.confirmationDialogShown).isTrue()
    assertThat(deviceMirroringSettings.deviceMirroringEnabled).isTrue()
  }

  @Test
  fun testMirroringConfirmationDialogReject() {
    if (!isFFmpegAvailableToTest()) {
      return
    }
    createToolWindowContent()
    assertThat(contentManager.contents).isEmpty()
    assertThat(toolWindow.isVisible).isFalse()

    deviceMirroringSettings.confirmationDialogShown = false

    agentRule.connectDevice("Pixel 4", 30, Dimension(1080, 2280), "arm64-v8a")

    createModalDialogAndInteractWithIt(toolWindow::show) { dlg ->
      val ui = FakeUi(dlg.rootPane)
      ui.clickOn(ui.getComponent<JButton> { it.text == "Disable Mirroring" })
    }

    assertThat(deviceMirroringSettings.confirmationDialogShown).isTrue()
    assertThat(deviceMirroringSettings.deviceMirroringEnabled).isFalse()
  }

  @Test
  fun testUnsupportedPhysicalPhone() {
    if (!isFFmpegAvailableToTest()) {
      return
    }
    createToolWindowContent()
    assertThat(contentManager.contents).isEmpty()
    assertThat(toolWindow.isVisible).isFalse()

    agentRule.connectDevice("Pixel", 25, Dimension(1080, 1920), "armeabi-v7a")
    toolWindow.show()

    dispatchAllEventsInIdeEventQueue()
    awaitQuiescence(AndroidExecutors.getInstance().workerThreadExecutor as ThreadPoolExecutor, 5, TimeUnit.SECONDS)
    dispatchAllEventsInIdeEventQueue()
    assertThat(contentManager.contents.size == 1).isTrue()
    assertThat(contentManager.contents[0].displayName).isNull()
  }

  @Test
  fun testUnsupportedPhysicalWatch() {
    if (!isFFmpegAvailableToTest()) {
      return
    }
    createToolWindowContent()
    assertThat(contentManager.contents).isEmpty()
    assertThat(toolWindow.isVisible).isFalse()

    agentRule.connectDevice("LG Watch Sport", 29, Dimension(480, 480), "armeabi-v7a",
                            additionalDeviceProperties = mapOf(DevicePropertyNames.RO_BUILD_CHARACTERISTICS to "nosdcard,watch"))
    toolWindow.show()

    dispatchAllEventsInIdeEventQueue()
    awaitQuiescence(AndroidExecutors.getInstance().workerThreadExecutor as ThreadPoolExecutor, 5, TimeUnit.SECONDS)
    dispatchAllEventsInIdeEventQueue()
    assertThat(contentManager.contents.size == 1).isTrue()
    assertThat(contentManager.contents[0].displayName).isNull()
  }

  @Test
  fun testWindowViewModeActionSetTypeWhenPerformed() {
    windowFactory.init(toolWindow)
    toolWindow.setType(ToolWindowType.DOCKED) {}

    val windowAction = (toolWindow as TestToolWindow).titleActions.find { it.templateText == "Window" }!!
    windowAction.actionPerformed(AnActionEvent.createFromAnAction(windowAction, null, "", dataContext))

    assertThat(toolWindow.type).isEqualTo(ToolWindowType.WINDOWED)
  }

  @Test
  fun testWindowViewModeActionUnavailableWhenTypeIsWindowedOrFloat() {
    windowFactory.init(toolWindow)
    val windowAction = (toolWindow as TestToolWindow).titleActions.find { it.templateText == "Window" }!!

    toolWindow.setType(ToolWindowType.FLOATING) {}
    AnActionEvent.createFromAnAction(windowAction, null, "", dataContext).also(windowAction::update).let {
      assertThat(it.presentation.isVisible).isFalse()
      assertThat(it.presentation.isEnabled).isFalse()
    }

    toolWindow.setType(ToolWindowType.WINDOWED) {}
    AnActionEvent.createFromAnAction(windowAction, null, "", dataContext).also(windowAction::update).let {
      assertThat(it.presentation.isVisible).isFalse()
      assertThat(it.presentation.isEnabled).isFalse()
    }
  }

  private fun createToolWindowContent() {
    assertThat(windowFactory.shouldBeAvailable(project)).isTrue()
    windowFactory.init(toolWindow)
    windowFactory.createToolWindowContent(project, toolWindow)
  }

  private val FakeEmulator.avdName
    get() = avdId.replace('_', ' ')

  private fun createToolWindow(): ToolWindow {
    val windowManager = TestToolWindowManager(project)
    project.replaceService(ToolWindowManager::class.java, windowManager, testRootDisposable)
    return windowManager.toolWindow
  }

  private fun renderAndGetFrameNumber(fakeUi: FakeUi, emulatorView: EmulatorView): Int {
    fakeUi.render() // The frame number may get updated as a result of rendering.
    return emulatorView.frameNumber
  }

  private class TestToolWindowManager(project: Project) : ToolWindowHeadlessManagerImpl(project) {
    var toolWindow = TestToolWindow(project, this)

    override fun getToolWindow(id: String?): ToolWindow? {
      return if (id == RUNNING_DEVICES_TOOL_WINDOW_ID) toolWindow else super.getToolWindow(id)
    }

    override fun invokeLater(runnable: Runnable) {
      ApplicationManager.getApplication().invokeLater(runnable)
    }
  }

  private class TestToolWindow(
    project: Project,
    private val manager: ToolWindowManager
  ) : ToolWindowHeadlessManagerImpl.MockToolWindow(project) {

    private var available = true
    private var visible = false
    var titleActions: List<AnAction> = emptyList()
      private set
    private var type = ToolWindowType.DOCKED

    override fun setAvailable(available: Boolean) {
      this.available = available
    }

    override fun isAvailable(): Boolean {
      return available
    }

    override fun show(runnable: Runnable?) {
      if (!visible) {
        visible = true
        notifyStateChanged()
      }
    }

    override fun hide(runnable: Runnable?) {
      if (visible) {
        visible = false
        notifyStateChanged()
      }
    }

    override fun isVisible() = visible

    override fun setTitleActions(actions: List<AnAction>) {
      this.titleActions = actions
    }

    override fun setType(type: ToolWindowType, runnable: Runnable?) {
      this.type = type
      runnable?.run()
    }

    override fun getType(): ToolWindowType {
      return this.type
    }

    private fun notifyStateChanged() {
      project.messageBus.syncPublisher(ToolWindowManagerListener.TOPIC).stateChanged(manager)
    }
  }
}
