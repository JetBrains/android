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
package com.android.tools.idea.streaming.core

import com.android.adblib.DevicePropertyNames
import com.android.emulator.control.KeyboardEvent
import com.android.emulator.control.PaneEntry
import com.android.emulator.control.PaneEntry.PaneIndex
import com.android.sdklib.internal.avd.AvdInfo
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.testutils.override
import com.android.testutils.waitForCondition
import com.android.tools.adtui.actions.ZoomType
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.HeadlessDialogRule
import com.android.tools.adtui.swing.PortableUiFontRule
import com.android.tools.adtui.swing.createModalDialogAndInteractWithIt
import com.android.tools.adtui.swing.popup.FakeListPopup
import com.android.tools.adtui.swing.popup.JBPopupRule
import com.android.tools.idea.avdmanager.AvdLaunchListener
import com.android.tools.idea.avdmanager.AvdLaunchListener.RequestType
import com.android.tools.idea.concurrency.AndroidExecutors
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.protobuf.TextFormat
import com.android.tools.idea.run.DeviceHeadsUpListener
import com.android.tools.idea.streaming.DeviceMirroringSettings
import com.android.tools.idea.streaming.EmulatorSettings
import com.android.tools.idea.streaming.RUNNING_DEVICES_TOOL_WINDOW_ID
import com.android.tools.idea.streaming.ToolWindowHeadlessManagerImpl
import com.android.tools.idea.streaming.createTestEvent
import com.android.tools.idea.streaming.device.ClipboardSynchronizationDisablementRule
import com.android.tools.idea.streaming.device.DeviceToolWindowPanel
import com.android.tools.idea.streaming.device.FakeScreenSharingAgentRule
import com.android.tools.idea.streaming.device.isFFmpegAvailableToTest
import com.android.tools.idea.streaming.emulator.EmulatorController
import com.android.tools.idea.streaming.emulator.EmulatorToolWindowPanel
import com.android.tools.idea.streaming.emulator.EmulatorView
import com.android.tools.idea.streaming.emulator.FakeEmulator
import com.android.tools.idea.streaming.emulator.FakeEmulatorRule
import com.android.tools.idea.streaming.emulator.RunningEmulatorCatalog
import com.android.tools.idea.streaming.executeStreamingAction
import com.android.tools.idea.testing.AndroidExecutorsRule
import com.android.tools.idea.testing.DisposerExplorer
import com.android.tools.idea.testing.flags.override
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.icons.AllIcons
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
import icons.StudioIcons
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
  private val popupRule = JBPopupRule()
  @get:Rule
  val ruleChain = RuleChain(agentRule, emulatorRule, ClipboardSynchronizationDisablementRule(), androidExecutorsRule, EdtRule(),
                            PortableUiFontRule(), HeadlessDialogRule(), popupRule)

  private val windowFactory: StreamingToolWindowFactory by lazy { StreamingToolWindowFactory() }
  private var nullableToolWindow: TestToolWindow? = null
  private val toolWindow: TestToolWindow
    get() {
      return nullableToolWindow ?: createToolWindow().also { nullableToolWindow = it }
    }
  private val contentManager: ContentManager by lazy { toolWindow.contentManager }

  private val deviceMirroringSettings: DeviceMirroringSettings by lazy { DeviceMirroringSettings.getInstance() }

  private val project get() = agentRule.project
  private val testRootDisposable get() = agentRule.disposable
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

    val tempFolder = emulatorRule.avdRoot
    val emulator1 = emulatorRule.newEmulator(FakeEmulator.createPhoneAvd(tempFolder))
    val emulator2 = emulatorRule.newEmulator(FakeEmulator.createTabletAvd(tempFolder))
    val emulator3 = emulatorRule.newEmulator(FakeEmulator.createWatchAvd(tempFolder))

    // The Emulator tool window is closed.
    assertThat(toolWindow.isVisible).isFalse()

    // Start the first and the second emulators.
    emulator1.start(standalone = false)
    emulator2.start(standalone = true)

    // Send notification that the emulator has been launched.
    val avdInfo = AvdInfo(emulator1.avdId, emulator1.avdFolder.resolve("config.ini"), emulator1.avdFolder, mock(), null)
    val commandLine = GeneralCommandLine("/emulator_home/fake_emulator", "-avd", emulator1.avdId, "-qt-hide-window")
    project.messageBus.syncPublisher(AvdLaunchListener.TOPIC).avdLaunched(avdInfo, commandLine, RequestType.INDIRECT, project)
    dispatchAllInvocationEvents()
    assertThat(toolWindow.isVisible).isFalse() // Indirect AVD launches don't open the Running Devices tool window.

    project.messageBus.syncPublisher(AvdLaunchListener.TOPIC).avdLaunched(avdInfo, commandLine, RequestType.DIRECT_DEVICE_MANAGER, project)
    dispatchAllInvocationEvents()
    // The Running Devices tool is opened and activated when an embedded emulator is launched by a direct request.
    waitForCondition(2, TimeUnit.SECONDS) { toolWindow.isVisible }
    assertThat(toolWindow.isActive).isTrue()

    waitForCondition(2, TimeUnit.SECONDS) { contentManager.contents.isNotEmpty() }
    assertThat(contentManager.contents).hasLength(1)
    waitForCondition(2, TimeUnit.SECONDS) { RunningEmulatorCatalog.getInstance().emulators.isNotEmpty() }
    emulator1.getNextGrpcCall(2, TimeUnit.SECONDS) { true } // Wait for the initial "getVmState" call.
    waitForCondition(2, TimeUnit.SECONDS) { contentManager.contents[0].displayName != null }
    assertThat(contentManager.contents[0].displayName).isEqualTo(emulator1.avdName)

    // Start the third emulator.
    emulator3.start(standalone = false)

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

    val tempFolder = emulatorRule.avdRoot
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

    val tempFolder = emulatorRule.avdRoot
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

    val tempFolder = emulatorRule.avdRoot
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
    deviceMirroringSettings::activateOnConnection.override(true, testRootDisposable)
    createToolWindowContent()
    assertThat(contentManager.contents).isEmpty()
    assertThat(toolWindow.isVisible).isFalse()

    val device = agentRule.connectDevice("Pixel 4", 30, Dimension(1080, 2280))
    toolWindow.show()

    waitForCondition(15, TimeUnit.SECONDS) { contentManager.contents.size == 1 && contentManager.contents[0].displayName != null }
    assertThat(contentManager.contents[0].displayName).isEqualTo("Pixel 4 API 30")
    if (StudioFlags.DEVICE_MIRRORING_ADVANCED_TAB_CONTROL.get()) {
      assertThat(contentManager.contents[0].isCloseable).isTrue()
    }
    else {
      assertThat(contentManager.contents[0].isCloseable).isFalse()
    }

    agentRule.disconnectDevice(device)
    waitForCondition(2, TimeUnit.SECONDS) { contentManager.contents.size == 1 && contentManager.contents[0].displayName == null }
    waitForCondition(2, TimeUnit.SECONDS) { !device.agent.isRunning }

    // Check that PhysicalDeviceWatcher gets disposed after disabling device mirroring.
    // DisposerExplorer is used because alternative ways of testing this are pretty slow.
    val physicalDeviceWatcherClassName = "com.android.tools.idea.streaming.core.StreamingToolWindowManager\$PhysicalDeviceWatcher"
    assertThat(DisposerExplorer.findAll { it.javaClass.name == physicalDeviceWatcherClassName }).hasSize(1)
    if (!StudioFlags.DEVICE_MIRRORING_ADVANCED_TAB_CONTROL.get()) {
      deviceMirroringSettings.deviceMirroringEnabled = false
      assertThat(DisposerExplorer.findAll { it.javaClass.name == physicalDeviceWatcherClassName }).isEmpty()
    }
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
    val device = agentRule.connectDevice("Pixel 4", 30, Dimension(1080, 2280))

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
    StudioFlags.DEVICE_MIRRORING_ADVANCED_TAB_CONTROL.override(true, testRootDisposable)
    deviceMirroringSettings::activateOnAppLaunch.override(true, testRootDisposable)
    deviceMirroringSettings::activateOnTestLaunch.override(true, testRootDisposable)
    createToolWindowContent()
    assertThat(contentManager.contents).isEmpty()
    assertThat(toolWindow.isVisible).isFalse()
    assertThat(toolWindow.isActive).isFalse()

    val device1 = agentRule.connectDevice("Pixel 4", 30, Dimension(1080, 2280))
    val device2 = agentRule.connectDevice("Pixel 6", 32, Dimension(1080, 2400))
    project.messageBus.syncPublisher(DeviceHeadsUpListener.TOPIC).launchingApp(device2.serialNumber, project)

    waitForCondition(15, TimeUnit.SECONDS) { contentManager.contents.size == 1 && contentManager.contents[0].displayName != null }
    assertThat(contentManager.contents[0].displayName).isEqualTo("Pixel 6 API 32")
    assertThat(contentManager.selectedContent?.displayName).isEqualTo("Pixel 6 API 32")
    assertThat(toolWindow.isVisible).isTrue()
    assertThat(toolWindow.isActive).isFalse()

    project.messageBus.syncPublisher(DeviceHeadsUpListener.TOPIC).launchingTest(device1.serialNumber, project)
    waitForCondition(15, TimeUnit.SECONDS) { contentManager.contents.size == 2 }
    assertThat(contentManager.contents[0].displayName).isEqualTo("Pixel 4 API 30")
    assertThat(contentManager.selectedContent?.displayName).isEqualTo("Pixel 4 API 30")

    deviceMirroringSettings.activateOnAppLaunch = false
    project.messageBus.syncPublisher(DeviceHeadsUpListener.TOPIC).launchingApp(device2.serialNumber, project)
    assertThat(contentManager.selectedContent?.displayName).isEqualTo("Pixel 4 API 30")

    agentRule.disconnectDevice(device1)
    agentRule.disconnectDevice(device2)
    waitForCondition(10, TimeUnit.SECONDS) { contentManager.contents.size == 1 && contentManager.contents[0].displayName == null }
  }

  @Test
  fun testPhysicalDeviceRequestsAttentionWithoutAdvancedTabControl() {
    if (!isFFmpegAvailableToTest()) {
      return
    }
    StudioFlags.DEVICE_MIRRORING_ADVANCED_TAB_CONTROL.override(false, testRootDisposable)
    deviceMirroringSettings::activateOnAppLaunch.override(true, testRootDisposable)
    createToolWindowContent()
    assertThat(contentManager.contents).isEmpty()
    assertThat(toolWindow.isVisible).isFalse()
    assertThat(toolWindow.isActive).isFalse()

    val device1 = agentRule.connectDevice("Pixel 4", 30, Dimension(1080, 2280))
    val device2 = agentRule.connectDevice("Pixel 6", 32, Dimension(1080, 2400))
    project.messageBus.syncPublisher(DeviceHeadsUpListener.TOPIC).launchingApp(device2.serialNumber, project)

    waitForCondition(15, TimeUnit.SECONDS) { contentManager.contents.size == 2 }
    assertThat(contentManager.contents[0].displayName).isEqualTo("Pixel 4 API 30")
    assertThat(contentManager.contents[1].displayName).isEqualTo("Pixel 6 API 32")
    assertThat(contentManager.selectedContent?.displayName).isEqualTo("Pixel 6 API 32")
    assertThat(toolWindow.isVisible).isTrue()
    assertThat(toolWindow.isActive).isFalse()

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

    val device1 = agentRule.connectDevice("Pixel 4", 30, Dimension(1080, 2280))
    project.messageBus.syncPublisher(DeviceHeadsUpListener.TOPIC).userInvolvementRequired(device1.serialNumber, project)

    dispatchAllEventsInIdeEventQueue()
    awaitQuiescence(AndroidExecutors.getInstance().workerThreadExecutor as ThreadPoolExecutor, 5, TimeUnit.SECONDS)
    dispatchAllEventsInIdeEventQueue()
    assertThat(toolWindow.isVisible).isFalse()
  }

  @Test
  fun testMirroringDisablementEnablement() {
    if (!isFFmpegAvailableToTest()) {
      return
    }
    StudioFlags.DEVICE_MIRRORING_ADVANCED_TAB_CONTROL.override(false, testRootDisposable)
    createToolWindowContent()
    assertThat(contentManager.contents).isEmpty()
    assertThat(toolWindow.isVisible).isFalse()
    toolWindow.show()

    val device = agentRule.connectDevice("Pixel 4", 30, Dimension(1080, 2280))

    waitForCondition(15, TimeUnit.SECONDS) { contentManager.contents.size == 1 && contentManager.contents[0].displayName != null }
    assertThat(contentManager.contents[0].displayName).isEqualTo("Pixel 4 API 30")

    // Wait until the first frame is rendered before disabling mirroring.
    val panel = contentManager.contents[0].component as DeviceToolWindowPanel
    panel.setSize(250, 500)
    val ui = FakeUi(panel)
    val displayView = ui.getComponent<AbstractDisplayView>()
    waitForCondition(2, TimeUnit.SECONDS) { renderAndGetFrameNumber(ui, displayView) > 0 }

    deviceMirroringSettings.deviceMirroringEnabled = false
    waitForCondition(2, TimeUnit.SECONDS) { contentManager.contents.size == 1 && contentManager.contents[0].displayName == null }
    waitForCondition(2, TimeUnit.SECONDS) { !device.agent.isRunning }

    deviceMirroringSettings.deviceMirroringEnabled = true
    waitForCondition(15, TimeUnit.SECONDS) { contentManager.contents.size == 1 && contentManager.contents[0].displayName != null }
    assertThat(contentManager.contents[0].displayName).isEqualTo("Pixel 4 API 30")

    agentRule.disconnectDevice(device)
    waitForCondition(2, TimeUnit.SECONDS) { contentManager.contents.size == 1 && contentManager.contents[0].displayName == null }
    waitForCondition(2, TimeUnit.SECONDS) { !device.agent.isRunning }
  }

  @Test
  fun testMirroringStoppingStarting() {
    if (!isFFmpegAvailableToTest()) {
      return
    }
    StudioFlags.DEVICE_MIRRORING_ADVANCED_TAB_CONTROL.override(true, testRootDisposable)
    deviceMirroringSettings::activateOnConnection.override(true, testRootDisposable)
    createToolWindowContent()
    assertThat(contentManager.contents).isEmpty()
    assertThat(toolWindow.isVisible).isFalse()
    toolWindow.show()

    agentRule.connectDevice("Pixel 4", 30, Dimension(1080, 2280))
    waitForCondition(15, TimeUnit.SECONDS) { contentManager.contents.size == 1 && contentManager.contents[0].displayName != null }
    assertThat(contentManager.contents[0].displayName).isEqualTo("Pixel 4 API 30")
    assertThat(contentManager.contents[0].isCloseable).isTrue()

    agentRule.connectDevice("Pixel 7", 33, Dimension(1080, 2400))
    waitForCondition(15, TimeUnit.SECONDS) { contentManager.contents.size == 2 }
    assertThat(contentManager.contents[1].displayName).isEqualTo("Pixel 7 API 33")
    assertThat(contentManager.contents[1].isCloseable).isTrue()

    assertThat(toolWindow.tabActions).isNotEmpty()
    val newTabAction = toolWindow.tabActions[0]
    assertThat(newTabAction.templateText).isEqualTo("New Tab")
    assertThat(newTabAction.templatePresentation.icon).isEqualTo(AllIcons.General.Add)

    newTabAction.actionPerformed(createTestEvent(toolWindow.component, project))
    var popup: FakeListPopup<Any> = popupRule.fakePopupFactory.getNextPopup(2, TimeUnit.SECONDS)
    assertThat(popup.actions.toString()).isEqualTo(
        "[Pair Devices Using Wi-Fi (Open the Device Pairing dialog which allows connecting devices over Wi-Fi)]")

    contentManager.removeContent(contentManager.contents[0], true)
    contentManager.removeContent(contentManager.contents[0], true)

    executeStreamingAction(newTabAction, toolWindow.component, project)
    popup = popupRule.fakePopupFactory.getNextPopup(2, TimeUnit.SECONDS)
    assertThat(popup.actions.toString()).isEqualTo(
        "[Separator (Connected Devices), Pixel 4 API 30 (null), Pixel 7 API 33 (null), " +
        "Separator (null), Pair Devices Using Wi-Fi (Open the Device Pairing dialog which allows connecting devices over Wi-Fi)]")
    assertThat(popup.actions[1].templatePresentation.icon).isEqualTo(StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_PHONE)
    assertThat(popup.actions[2].templatePresentation.icon).isEqualTo(StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_PHONE)

    executeStreamingAction(popup.actions[2], toolWindow.component, project)
    waitForCondition(2, TimeUnit.SECONDS) { contentManager.contents.size == 1 && contentManager.contents[0].displayName != null }
    assertThat(contentManager.contents[0].displayName).isEqualTo("Pixel 7 API 33")
    assertThat(contentManager.selectedContent?.displayName).isEqualTo("Pixel 7 API 33")

    executeStreamingAction(newTabAction, toolWindow.component, project)
    popup = popupRule.fakePopupFactory.getNextPopup(2, TimeUnit.SECONDS)
    assertThat(popup.actions.toString()).isEqualTo(
        "[Separator (Connected Devices), Pixel 4 API 30 (null), " +
                "Separator (null), Pair Devices Using Wi-Fi (Open the Device Pairing dialog which allows connecting devices over Wi-Fi)]")

    executeStreamingAction(popup.actions[1], toolWindow.component, project)
    waitForCondition(2, TimeUnit.SECONDS) { contentManager.contents.size == 2 }
    assertThat(contentManager.contents[0].displayName).isEqualTo("Pixel 4 API 30")
    assertThat(contentManager.selectedContent?.displayName).isEqualTo("Pixel 4 API 30")

    executeStreamingAction(newTabAction, toolWindow.component, project)
    popup = popupRule.fakePopupFactory.getNextPopup(2, TimeUnit.SECONDS)
    assertThat(popup.actions.toString()).isEqualTo(
        "[Pair Devices Using Wi-Fi (Open the Device Pairing dialog which allows connecting devices over Wi-Fi)]")
  }

  @Test
  fun testAvdStarting() {
    if (!isFFmpegAvailableToTest()) {
      return
    }
    StudioFlags.DEVICE_MIRRORING_ADVANCED_TAB_CONTROL.override(true, testRootDisposable)
    EmulatorSettings.getInstance()::launchInToolWindow.override(false, testRootDisposable)
    createToolWindowContent()
    assertThat(contentManager.contents).isEmpty()
    assertThat(toolWindow.isVisible).isFalse()
    toolWindow.show()

    val avdRoot = emulatorRule.avdRoot
    val phone = emulatorRule.newEmulator(FakeEmulator.createPhoneAvd(avdRoot))
    val tablet = emulatorRule.newEmulator(FakeEmulator.createTabletAvd(avdRoot))
    tablet.start(standalone = true)
    RunningEmulatorCatalog.getInstance().updateNow().get()

    assertThat(toolWindow.tabActions).isNotEmpty()
    val newTabAction = toolWindow.tabActions[0]
    val testEvent = createTestEvent(toolWindow.component, project)
    newTabAction.actionPerformed(testEvent)
    lateinit var popup: FakeListPopup<Any>
    waitForCondition(4, TimeUnit.SECONDS) {
      newTabAction.actionPerformed(testEvent)
      popup = popupRule.fakePopupFactory.getNextPopup(2, TimeUnit.SECONDS)
      popup.items.size >= 2
    }
    assertThat(popup.actions.toString()).isEqualTo(
        "[Separator (Virtual Devices), ${phone.avdName} (null), " +
        "Separator (null), Pair Devices Using Wi-Fi (Open the Device Pairing dialog which allows connecting devices over Wi-Fi)]")
    assertThat(popup.actions[1].templatePresentation.icon).isEqualTo(StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_PHONE)

    executeStreamingAction(popup.actions[1], toolWindow.component, project)
    waitForCondition(2, TimeUnit.SECONDS) { contentManager.contents.size == 1 && contentManager.contents[0].displayName != null }
    assertThat(contentManager.contents[0].displayName).isEqualTo(phone.avdName)
    assertThat(contentManager.selectedContent?.displayName).isEqualTo(phone.avdName)
  }

  @Test
  fun testMirroringUserInvolvementRequired() {
    if (!isFFmpegAvailableToTest()) {
      return
    }
    createToolWindowContent()
    assertThat(contentManager.contents).isEmpty()
    assertThat(toolWindow.isVisible).isFalse()
    if (StudioFlags.DEVICE_MIRRORING_ADVANCED_TAB_CONTROL.get()) {
      deviceMirroringSettings::activateOnConnection.override(true, testRootDisposable)
    }
    else {
      toolWindow.show()
    }

    agentRule.connectDevice("Pixel 4", 30, Dimension(1080, 2280))
    waitForCondition(15, TimeUnit.SECONDS) { contentManager.contents.size == 1 && contentManager.contents[0].displayName != null }
    assertThat(contentManager.contents[0].displayName).isEqualTo("Pixel 4 API 30")

    // Calling userInvolvementRequired should trigger device selection even if it is done before the device is connected.
    val nextSerialNumber = "2"
    project.messageBus.syncPublisher(DeviceHeadsUpListener.TOPIC).userInvolvementRequired(nextSerialNumber, project)
    val device = agentRule.connectDevice("Pixel 7", 33, Dimension(1080, 2400))
    assertThat(device.serialNumber).isEqualTo(nextSerialNumber)
    waitForCondition(15, TimeUnit.SECONDS) { contentManager.contents.size == 2 }
    assertThat(contentManager.contents[1].displayName).isEqualTo("Pixel 7 API 33")
    assertThat(contentManager.selectedContent?.displayName).isEqualTo("Pixel 7 API 33")
  }

  @Test
  fun testMirroringConfirmationDialogAccept() {
    if (!isFFmpegAvailableToTest()) {
      return
    }
    deviceMirroringSettings::activateOnConnection.override(true, testRootDisposable)
    createToolWindowContent()
    assertThat(toolWindow.isVisible).isFalse()

    deviceMirroringSettings.confirmationDialogShown = false

    val device = agentRule.connectDevice("Pixel 4", 30, Dimension(1080, 2280))

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
    StudioFlags.DEVICE_MIRRORING_ADVANCED_TAB_CONTROL.override(true, testRootDisposable)
    deviceMirroringSettings::activateOnConnection.override(true, testRootDisposable)
    createToolWindowContent()
    assertThat(contentManager.contents).isEmpty()
    assertThat(toolWindow.isVisible).isFalse()

    deviceMirroringSettings.confirmationDialogShown = false

    createModalDialogAndInteractWithIt({ agentRule.connectDevice("Pixel 4", 30, Dimension(1080, 2280)) }) { dlg ->
      val ui = FakeUi(dlg.rootPane)
      ui.clickOn(ui.getComponent<JButton> { it.text == "Cancel" })
    }

    assertThat(deviceMirroringSettings.confirmationDialogShown).isTrue()
  }

  @Test
  fun testMirroringConfirmationDialogRejectWithoutAdvancedTabControl() {
    if (!isFFmpegAvailableToTest()) {
      return
    }
    StudioFlags.DEVICE_MIRRORING_ADVANCED_TAB_CONTROL.override(false, testRootDisposable)
    deviceMirroringSettings::activateOnConnection.override(true, testRootDisposable)
    createToolWindowContent()
    assertThat(contentManager.contents).isEmpty()
    assertThat(toolWindow.isVisible).isFalse()

    deviceMirroringSettings.confirmationDialogShown = false

    agentRule.connectDevice("Pixel 4", 30, Dimension(1080, 2280))

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

    agentRule.connectDevice("Pixel", 25, Dimension(1080, 1920), abi = "armeabi-v7a")
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

    agentRule.connectDevice("LG Watch Sport", 29, Dimension(480, 480), abi = "armeabi-v7a",
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

    val windowAction = toolWindow.titleActions.find { it.templateText == "Window" }!!
    windowAction.actionPerformed(AnActionEvent.createFromAnAction(windowAction, null, "", dataContext))

    assertThat(toolWindow.type).isEqualTo(ToolWindowType.WINDOWED)
  }

  @Test
  fun testWindowViewModeActionUnavailableWhenTypeIsWindowedOrFloat() {
    windowFactory.init(toolWindow)
    val windowAction = toolWindow.titleActions.find { it.templateText == "Window" }!!

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

  private fun createToolWindow(): TestToolWindow {
    val windowManager = TestToolWindowManager(project)
    project.replaceService(ToolWindowManager::class.java, windowManager, testRootDisposable)
    return windowManager.toolWindow
  }

  private fun renderAndGetFrameNumber(fakeUi: FakeUi, displayView: AbstractDisplayView): Int {
    fakeUi.render() // The frame number may get updated as a result of rendering.
    return displayView.frameNumber
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

    var tabActions: List<AnAction> = emptyList()
      private set
    var titleActions: List<AnAction> = emptyList()
      private set
    private var available = true
    private var visible = false
    private var active = false
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
        runnable?.run()
      }
    }

    override fun hide(runnable: Runnable?) {
      if (visible) {
        visible = false
        notifyStateChanged()
        runnable?.run()
      }
    }

    override fun activate(runnable: Runnable?) {
      active = true
      super.activate(runnable)
    }

    override fun isVisible() = visible

    override fun isActive() = active

    override fun setTabActions(vararg actions: AnAction) {
      tabActions = listOf(*actions)
    }

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
