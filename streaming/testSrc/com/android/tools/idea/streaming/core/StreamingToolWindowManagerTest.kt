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
import com.android.flags.junit.FlagRule
import com.android.sdklib.deviceprovisioner.DeviceAction
import com.android.sdklib.deviceprovisioner.DeviceId
import com.android.sdklib.deviceprovisioner.DeviceProperties
import com.android.sdklib.deviceprovisioner.DeviceState
import com.android.sdklib.deviceprovisioner.DeviceTemplate
import com.android.sdklib.deviceprovisioner.EditTemplateAction
import com.android.sdklib.deviceprovisioner.Reservation
import com.android.sdklib.deviceprovisioner.ReservationState
import com.android.sdklib.deviceprovisioner.TemplateActivationAction
import com.android.sdklib.deviceprovisioner.testing.DeviceProvisionerRule
import com.android.sdklib.internal.avd.AvdInfo
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
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
import com.android.tools.idea.deviceprovisioner.DeviceProvisionerService
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.protobuf.TextFormat
import com.android.tools.idea.run.DeviceHeadsUpListener
import com.android.tools.idea.streaming.DeviceMirroringSettings
import com.android.tools.idea.streaming.EmulatorSettings
import com.android.tools.idea.streaming.MirroringManager
import com.android.tools.idea.streaming.MirroringState
import com.android.tools.idea.streaming.RUNNING_DEVICES_TOOL_WINDOW_ID
import com.android.tools.idea.streaming.ToolWindowHeadlessManagerImpl
import com.android.tools.idea.streaming.createTestEvent
import com.android.tools.idea.streaming.device.ClipboardSynchronizationDisablementRule
import com.android.tools.idea.streaming.device.FakeScreenSharingAgentRule
import com.android.tools.idea.streaming.emulator.EmulatorController
import com.android.tools.idea.streaming.emulator.EmulatorToolWindowPanel
import com.android.tools.idea.streaming.emulator.EmulatorView
import com.android.tools.idea.streaming.emulator.FakeEmulator
import com.android.tools.idea.streaming.emulator.FakeEmulatorRule
import com.android.tools.idea.streaming.emulator.RunningEmulatorCatalog
import com.android.tools.idea.streaming.executeStreamingAction
import com.android.tools.idea.testing.AndroidExecutorsRule
import com.android.tools.idea.testing.DisposerExplorer
import com.android.tools.idea.testing.override
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.runners.IndicatorIcon
import com.intellij.icons.AllIcons
import com.intellij.ide.ui.LafManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ToolWindowType
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.openapi.wm.ex.ToolWindowManagerListener.ToolWindowManagerEventType
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.PlatformTestUtil.dispatchAllEventsInIdeEventQueue
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.replaceService
import com.intellij.ui.LayeredIcon
import com.intellij.ui.content.ContentManager
import com.intellij.util.ConcurrencyUtil.awaitQuiescence
import com.intellij.util.ui.UIUtil.dispatchAllInvocationEvents
import icons.StudioIcons
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.awt.Dimension
import java.awt.Point
import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit.SECONDS
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JViewport
import javax.swing.SwingConstants
import javax.swing.UIManager
import kotlin.time.Duration.Companion.seconds

/**
 * Tests for [StreamingToolWindowManager] and [StreamingToolWindowFactory].
 */
@RunsInEdt
class StreamingToolWindowManagerTest {

  private val agentRule = FakeScreenSharingAgentRule()
  private val emulatorRule = FakeEmulatorRule()
  private val androidExecutorsRule = AndroidExecutorsRule(workerThreadExecutor = Executors.newCachedThreadPool())
  private val popupRule = JBPopupRule()
  private val provisionerRule = DeviceProvisionerRule()
  @get:Rule
  val ruleChain = RuleChain(agentRule, provisionerRule, emulatorRule, ClipboardSynchronizationDisablementRule(), androidExecutorsRule,
                            EdtRule(), PortableUiFontRule(), HeadlessDialogRule(), popupRule, FlagRule(StudioFlags.DEVICE_MIRRORING_REMOTE_TEMPLATES_IN_PLUS, true))

  private val windowFactory: StreamingToolWindowFactory by lazy { StreamingToolWindowFactory() }
  private val toolWindow: TestToolWindow by lazy { createToolWindow() }
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
    deviceMirroringSettings.confirmationDialogShown = true
  }

  @After
  fun tearDown() {
    Disposer.dispose(toolWindow.disposable)
    dispatchAllEventsInIdeEventQueue() // Finish asynchronous processing triggered by hiding the tool window.
    deviceMirroringSettings.loadState(DeviceMirroringSettings()) // Reset device mirroring settings to defaults.
    service<DeviceClientRegistry>().clear()
  }

  @Test
  fun testTabManagement() {
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
    val avdInfo = AvdInfo(emulator1.avdId, emulator1.avdFolder.resolve("config.ini"), emulator1.avdFolder, mock(), null, null)
    val commandLine = GeneralCommandLine("/emulator_home/fake_emulator", "-avd", emulator1.avdId, "-qt-hide-window")
    project.messageBus.syncPublisher(AvdLaunchListener.TOPIC).avdLaunched(avdInfo, commandLine, RequestType.INDIRECT, project)
    dispatchAllInvocationEvents()
    assertThat(toolWindow.isVisible).isFalse() // Indirect AVD launches don't open the Running Devices tool window.

    project.messageBus.syncPublisher(AvdLaunchListener.TOPIC).avdLaunched(avdInfo, commandLine, RequestType.DIRECT_DEVICE_MANAGER, project)
    dispatchAllInvocationEvents()
    // The Running Devices tool is opened and activated when an embedded emulator is launched by a direct request.
    waitForCondition(2.seconds) { toolWindow.isVisible }
    assertThat(toolWindow.isActive).isTrue()

    waitForCondition(2.seconds) { contentManager.contents.isNotEmpty() }
    assertThat(contentManager.contents).hasLength(1)
    waitForCondition(2.seconds) { RunningEmulatorCatalog.getInstance().emulators.isNotEmpty() }
    emulator1.getNextGrpcCall(2.seconds) { true } // Wait for the initial "getVmState" call.
    waitForCondition(2.seconds) { contentManager.contents[0].displayName != null }
    assertThat(contentManager.contents[0].displayName).isEqualTo(emulator1.avdName)
    assertThat(contentManager.contents[0].description).isEqualTo(
      "${emulator1.avdName} <font color=808080>(${emulator1.serialNumber})</font>")

    // Start the third emulator.
    emulator3.start(standalone = false)

    waitForCondition(3.seconds) { contentManager.contents.size == 2 }

    assertThat(contentManager.contents[0].displayName).isEqualTo(emulator1.avdName)
    assertThat(contentManager.contents[0].description).isEqualTo(
        "${emulator1.avdName} <font color=808080>(${emulator1.serialNumber})</font>")
    // The panel for emulator3 is added but the emulator1 is still selected.
    assertThat(contentManager.contents[0].isSelected).isTrue()
    assertThat(contentManager.contents[1].displayName).isEqualTo(emulator3.avdName)
    assertThat(contentManager.contents[1].description).isEqualTo(
        "${emulator3.avdName} <font color=808080>(${emulator3.serialNumber})</font>")
    // Deploying an app activates the corresponding emulator panel.
    for (emulator in listOf(emulator2, emulator3)) {
      project.messageBus.syncPublisher(DeviceHeadsUpListener.TOPIC).userInvolvementRequired(emulator.serialNumber, project)
    }
    waitForCondition(2.seconds) { contentManager.contents[1].isSelected }

    assertThat(contentManager.contents).hasLength(2)

    // Stop the second embedded emulator.
    emulator3.stop()

    // The panel corresponding to the second emulator goes away.
    waitForCondition(5.seconds) { contentManager.contents.size == 1 }
    assertThat(contentManager.contents[0].displayName).isEqualTo(emulator1.avdName)
    assertThat(contentManager.contents[0].isSelected).isTrue()

    // Close the panel corresponding to emulator1.
    contentManager.removeContent(contentManager.contents[0], true)
    val call = emulator1.getNextGrpcCall(2.seconds,
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
    val tempFolder = emulatorRule.avdRoot
    val emulator = emulatorRule.newEmulator(FakeEmulator.createPhoneAvd(tempFolder))

    toolWindow.show()

    // Start the emulator.
    emulator.start()

    val controllers = RunningEmulatorCatalog.getInstance().updateNow().get()
    waitForCondition(3.seconds) { contentManager.contents.isNotEmpty() }
    assertThat(contentManager.contents[0].displayName).isEqualTo(emulator.avdName)
    assertThat(controllers).isNotEmpty()
    waitForCondition(5.seconds) { controllers.first().connectionState == EmulatorController.ConnectionState.CONNECTED }

    // Simulate an emulator crash.
    emulator.crash()
    controllers.first().sendKey(KeyboardEvent.newBuilder().setText(" ").build())
    waitForCondition(5.seconds) { contentManager.contents[0].displayName == null }
    assertThat(contentManager.contents[0].component).isInstanceOf(EmptyStatePanel::class.java)
  }

  @Test
  fun testUiStatePreservation() {
    val tempFolder = emulatorRule.avdRoot
    val emulator = emulatorRule.newEmulator(FakeEmulator.createPhoneAvd(tempFolder))

    toolWindow.show()

    // Start the emulator.
    emulator.start()

    waitForCondition(2.seconds) { contentManager.contents.isNotEmpty() }
    assertThat(contentManager.contents).hasLength(1)
    waitForCondition(2.seconds) { RunningEmulatorCatalog.getInstance().emulators.isNotEmpty() }
    val emulatorController = RunningEmulatorCatalog.getInstance().emulators.first()
    waitForCondition(4.seconds) { emulatorController.connectionState == EmulatorController.ConnectionState.CONNECTED }
    waitForCondition(2.seconds) { contentManager.contents[0].displayName != null }
    assertThat(contentManager.contents[0].displayName).isEqualTo(emulator.avdName)

    assertThat(emulator.extendedControlsVisible).isFalse()
    emulatorController.showExtendedControls(PaneEntry.newBuilder().setIndex(PaneIndex.KEEP_CURRENT).build())
    // Wait for the extended controls to show.
    waitForCondition(2.seconds) { emulator.extendedControlsVisible }

    val panel = contentManager.contents[0].component as EmulatorToolWindowPanel

    toolWindow.hide()
    // Wait for the extended controls to close.
    waitForCondition(4.seconds) { !emulator.extendedControlsVisible }
    // Wait for the prior visibility state of the extended controls to propagate to Studio.
    waitForCondition(2.seconds) { panel.lastUiState?.extendedControlsShown ?: false }

    toolWindow.show()
    // Wait for the extended controls to show.
    waitForCondition(2.seconds) { emulator.extendedControlsVisible }
  }

  @Test
  fun testZoomStatePreservation() {
    val tempFolder = emulatorRule.avdRoot
    val emulator = emulatorRule.newEmulator(FakeEmulator.createPhoneAvd(tempFolder))

    toolWindow.show()

    // Start the emulator.
    emulator.start()

    waitForCondition(2.seconds) { contentManager.contents.isNotEmpty() }
    assertThat(contentManager.contents).hasLength(1)
    waitForCondition(2.seconds) { RunningEmulatorCatalog.getInstance().emulators.isNotEmpty() }
    val emulatorController = RunningEmulatorCatalog.getInstance().emulators.first()
    waitForCondition(4.seconds) { emulatorController.connectionState == EmulatorController.ConnectionState.CONNECTED }
    waitForCondition(2.seconds) { contentManager.contents[0].displayName != null }
    assertThat(contentManager.contents[0].displayName).isEqualTo(emulator.avdName)

    val panel = contentManager.contents[0].component as EmulatorToolWindowPanel
    panel.setSize(250, 500)
    val ui = FakeUi(panel)
    val emulatorView = ui.getComponent<EmulatorView>()
    waitForCondition(2.seconds) { renderAndGetFrameNumber(ui, emulatorView) > 0u }

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
    deviceMirroringSettings::activateOnConnection.override(true, testRootDisposable)
    assertThat(contentManager.contents).isEmpty()
    assertThat(toolWindow.isVisible).isFalse()

    val device = agentRule.connectDevice("Pixel 4", 30, Dimension(1080, 2280))
    toolWindow.show()

    waitForCondition(15.seconds) { contentManager.contents.size == 1 && contentManager.contents[0].displayName != null }
    assertThat(contentManager.contents[0].displayName).isEqualTo("Pixel 4 API 30")
    assertThat(contentManager.contents[0].description).isEqualTo(
        "Google Pixel 4 API 30 <font color=808080>(${device.serialNumber})</font>")
    assertThat(contentManager.contents[0].isCloseable).isTrue()

    agentRule.disconnectDevice(device)
    waitForCondition(2.seconds) { contentManager.contents.size == 1 && contentManager.contents[0].displayName == null }
    waitForCondition(2.seconds) { !device.agent.isRunning }

    // Check that PhysicalDeviceWatcher gets disposed after disabling device mirroring.
    // DisposerExplorer is used because alternative ways of testing this are pretty slow.
    assertThat(DisposerExplorer.findAll { it.javaClass.name.endsWith("\$PhysicalDeviceWatcher") }).hasSize(1)
  }

  @Test
  fun testSplitWindow() {
    val tempFolder = emulatorRule.avdRoot
    val emulator1 = emulatorRule.newEmulator(FakeEmulator.createPhoneAvd(tempFolder))
    val emulator2 = emulatorRule.newEmulator(FakeEmulator.createFoldableAvd(tempFolder))
    val device1 = agentRule.connectDevice("Pixel 8 Pro", 34, Dimension(1080, 2400))
    val device2 = agentRule.connectDevice("Pixel Watch 2", 33, Dimension(450, 450))

    toolWindow.show()

    // Start the emulator.
    emulator1.start()
    waitForCondition(15.seconds) { contentManager.contents[0].displayName != null }
    project.messageBus.syncPublisher(DeviceHeadsUpListener.TOPIC).userInvolvementRequired(device1.serialNumber, project)
    waitForCondition(15.seconds) { contentManager.contents.size == 2 }

    val topContent = contentManager.contents[0]
    val bottomContent = contentManager.contents[1]
    ToolWindowHeadlessManagerImpl.split(bottomContent, SwingConstants.BOTTOM)
    val topContentManager = topContent.manager!!
    assertThat(topContentManager.contents).hasLength(1)
    val bottomContentManager = bottomContent.manager!!
    assertThat(bottomContentManager.contents).hasLength(1)

    var action = triggerAddDevicePopup().actions.find { it.templateText == emulator2.avdName }!!
    executeStreamingAction(action, toolWindow.component, project,
                           extraData = mapOf(PlatformDataKeys.CONTENT_MANAGER.name to bottomContentManager))
    waitForCondition(15.seconds) { bottomContentManager.contents.size == 2 }
    assertThat(bottomContentManager.contents[1].displayName).isEqualTo(emulator2.avdName)

    val device2Name = "${device2.deviceState.model} API ${device2.deviceState.buildVersionSdk}"
    action = triggerAddDevicePopup().actions.find { it.templateText == device2Name }!!
    executeStreamingAction(action, toolWindow.component, project,
                           extraData = mapOf(PlatformDataKeys.CONTENT_MANAGER.name to topContentManager))
    waitForCondition(15.seconds) { topContentManager.contents.size == 2 }
    assertThat(topContentManager.contents[1].displayName).isEqualTo(device2Name)
  }

  @Test
  fun testRemoteDevice() {
    val properties = DeviceProperties.buildForTest {
      icon = StudioIcons.DeviceExplorer.FIREBASE_DEVICE_CAR
      model = "Pixel 9000"
    }
    val device = provisionerRule.deviceProvisionerPlugin.newDevice(properties = properties)
    device.sourceTemplate = object: DeviceTemplate {
      override val id = DeviceId("TEST", true, "")
      override val properties = properties
      override val activationAction: TemplateActivationAction = mock()
      override val editAction = null
    }
    device.stateFlow.value = DeviceState.Disconnected(
      properties, false, "offline", Reservation(ReservationState.ACTIVE, "active", null, null, null))
    provisionerRule.deviceProvisionerPlugin.addDevice(device)

    val provisionerService: DeviceProvisionerService = mock()
    whenever(provisionerService.deviceProvisioner).thenReturn(provisionerRule.deviceProvisioner)
    project.replaceService(DeviceProvisionerService::class.java, provisionerService, agentRule.disposable)

    toolWindow.show()
    waitForCondition(2.seconds) { toolWindow.tabActions.isNotEmpty() }
    val newTabAction = toolWindow.tabActions[0]
    newTabAction.actionPerformed(createTestEvent(toolWindow.component, project))
    val popup: FakeListPopup<Any> = popupRule.fakePopupFactory.getNextPopup(2.seconds)

    assertThat(popup.actions.toString()).isEqualTo(
      "[Separator (Reserved Remote Devices), Pixel 9000 (null), Separator (null), " +
        "Pair Devices Using Wi-Fi (Open the Device Pairing dialog which allows connecting devices over Wi-Fi)]")
    assertThat(popup.actions[1].templatePresentation.icon).isEqualTo(StudioIcons.DeviceExplorer.FIREBASE_DEVICE_CAR)
  }

  @Test
  fun testReservableRemoteDevice() {
    val templateProperties = DeviceProperties.buildForTest {
      icon = StudioIcons.DeviceExplorer.FIREBASE_DEVICE_PHONE
      model = "Pixel Reservable"
      isRemote = true
    }
    val deviceProperties = DeviceProperties.buildForTest {
      icon = StudioIcons.DeviceExplorer.FIREBASE_DEVICE_WEAR
      model = "Pixel Reserved"
    }
    val template = object : DeviceTemplate {
      override val id = DeviceId("TEST", true, "")
      override val properties = templateProperties
      override val activationAction: TemplateActivationAction = mock<TemplateActivationAction>().apply {
        val stateFlow = MutableStateFlow(DeviceAction.Presentation("", templateProperties.icon, true)).asStateFlow()
        whenever(this.presentation).thenReturn(stateFlow)
      }
      override val editAction: EditTemplateAction? = null
    }
    val device = provisionerRule.deviceProvisionerPlugin.newDevice(properties = deviceProperties)
    device.sourceTemplate = object : DeviceTemplate {
      override val id = DeviceId("TEST2", true, "")
      override val properties = deviceProperties
      override val activationAction: TemplateActivationAction = mock()
      override val editAction: EditTemplateAction? = null
    }
    device.stateFlow.value = DeviceState.Disconnected(
      deviceProperties, false, "offline", Reservation(ReservationState.ACTIVE, "active", null, null, null))
    provisionerRule.deviceProvisionerPlugin.addTemplate(template)
    provisionerRule.deviceProvisionerPlugin.addDevice(device)

    val provisionerService: DeviceProvisionerService = mock()
    whenever(provisionerService.deviceProvisioner).thenReturn(provisionerRule.deviceProvisioner)
    project.replaceService(DeviceProvisionerService::class.java, provisionerService, agentRule.disposable)

    toolWindow.show()
    waitForCondition(2.seconds) { toolWindow.tabActions.isNotEmpty() }
    val newTabAction = toolWindow.tabActions[0]
    newTabAction.actionPerformed(createTestEvent(toolWindow.component, project))
    val popup: FakeListPopup<Any> = popupRule.fakePopupFactory.getNextPopup(2.seconds)

    assertThat(popup.actions.toString()).isEqualTo("[Separator (Reserved Remote Devices), Pixel Reserved (null), Separator (Remote Devices), Pixel Reservable (null), Separator (null), Pair Devices Using Wi-Fi (Open the Device Pairing dialog which allows connecting devices over Wi-Fi)]")
    assertThat(popup.actions[1].templatePresentation.icon).isEqualTo(StudioIcons.DeviceExplorer.FIREBASE_DEVICE_WEAR)
    assertThat(popup.actions[3].templatePresentation.icon).isEqualTo(StudioIcons.DeviceExplorer.FIREBASE_DEVICE_PHONE)
  }

  @Test
  fun testPhysicalDeviceActivateOnConnection() {
    assertThat(contentManager.contents).isEmpty()
    assertThat(toolWindow.isVisible).isFalse()

    deviceMirroringSettings.activateOnConnection = true
    val device = agentRule.connectDevice("Pixel 4", 30, Dimension(1080, 2280))

    waitForCondition(15.seconds) { contentManager.contents.size == 1 && contentManager.contents[0].displayName != null }
    assertThat(contentManager.contents[0].displayName).isEqualTo("Pixel 4 API 30")

    agentRule.disconnectDevice(device)
    waitForCondition(2.seconds) { contentManager.contents.size == 1 && contentManager.contents[0].displayName == null }
    waitForCondition(2.seconds) { !device.agent.isRunning }
  }

  @Test
  fun testPhysicalDeviceRequestsAttention() {
    deviceMirroringSettings::activateOnAppLaunch.override(true, testRootDisposable)
    deviceMirroringSettings::activateOnTestLaunch.override(true, testRootDisposable)
    assertThat(contentManager.contents).isEmpty()
    assertThat(toolWindow.isVisible).isFalse()
    assertThat(toolWindow.isActive).isFalse()

    val device1 = agentRule.connectDevice("Pixel 4", 30, Dimension(1080, 2280))
    val device2 = agentRule.connectDevice("Pixel 6", 32, Dimension(1080, 2400))
    project.messageBus.syncPublisher(DeviceHeadsUpListener.TOPIC).launchingApp(device2.serialNumber, project)

    waitForCondition(15.seconds) { contentManager.contents.size == 1 && contentManager.contents[0].displayName != null }
    assertThat(contentManager.contents[0].displayName).isEqualTo("Pixel 6 API 32")
    assertThat(contentManager.selectedContent?.displayName).isEqualTo("Pixel 6 API 32")
    assertThat(toolWindow.isVisible).isTrue()
    assertThat(toolWindow.isActive).isFalse()

    project.messageBus.syncPublisher(DeviceHeadsUpListener.TOPIC).launchingTest(device1.serialNumber, project)
    waitForCondition(15.seconds) { contentManager.contents.size == 2 }
    assertThat(contentManager.contents[1].displayName).isEqualTo("Pixel 4 API 30")
    assertThat(contentManager.selectedContent?.displayName).isEqualTo("Pixel 4 API 30")

    deviceMirroringSettings.activateOnAppLaunch = false
    project.messageBus.syncPublisher(DeviceHeadsUpListener.TOPIC).launchingApp(device2.serialNumber, project)
    assertThat(contentManager.selectedContent?.displayName).isEqualTo("Pixel 4 API 30")

    agentRule.disconnectDevice(device1)
    agentRule.disconnectDevice(device2)
    waitForCondition(10.seconds) { contentManager.contents.size == 1 && contentManager.contents[0].displayName == null }
  }

  @Test
  fun testMirroringStoppingStarting() {
    deviceMirroringSettings::activateOnConnection.override(true, testRootDisposable)
    assertThat(contentManager.contents).isEmpty()
    assertThat(toolWindow.isVisible).isFalse()

    agentRule.connectDevice("Pixel 4", 30, Dimension(1080, 2280))
    waitForCondition(15.seconds) { contentManager.contents.size == 1 && contentManager.contents[0].displayName != null }
    assertThat(contentManager.contents[0].displayName).isEqualTo("Pixel 4 API 30")
    assertThat(contentManager.contents[0].isCloseable).isTrue()

    agentRule.connectDevice("Pixel 7", 33, Dimension(1080, 2400))
    waitForCondition(15.seconds) { contentManager.contents.size == 2 }
    assertThat(contentManager.contents[1].displayName).isEqualTo("Pixel 7 API 33")
    assertThat(contentManager.contents[1].isCloseable).isTrue()

    assertThat(toolWindow.tabActions).isNotEmpty()
    val newTabAction = toolWindow.tabActions[0]
    assertThat(newTabAction.templateText).isEqualTo("Add Device")
    assertThat(newTabAction.templatePresentation.icon).isEqualTo(AllIcons.General.Add)

    newTabAction.actionPerformed(createTestEvent(toolWindow.component, project))
    var popup: FakeListPopup<Any> = popupRule.fakePopupFactory.getNextPopup(2.seconds)
    assertThat(popup.actions.toString()).isEqualTo(
        "[Pair Devices Using Wi-Fi (Open the Device Pairing dialog which allows connecting devices over Wi-Fi)]")

    contentManager.removeContent(contentManager.contents[0], true)
    contentManager.removeContent(contentManager.contents[0], true)

    executeStreamingAction(newTabAction, toolWindow.component, project)
    popup = popupRule.fakePopupFactory.getNextPopup(2.seconds)
    assertThat(popup.actions.toString()).isEqualTo(
        "[Separator (Connected Devices), Pixel 4 API 30 (null), Pixel 7 API 33 (null), " +
        "Separator (null), Pair Devices Using Wi-Fi (Open the Device Pairing dialog which allows connecting devices over Wi-Fi)]")
    assertThat(popup.actions[1].templatePresentation.icon).isEqualTo(StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_PHONE)
    assertThat(popup.actions[2].templatePresentation.icon).isEqualTo(StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_PHONE)

    // Activate mirroring of Pixel 7 API 33.
    executeStreamingAction(popup.actions[2], toolWindow.component, project)
    waitForCondition(2.seconds) { contentManager.contents.size == 1 && contentManager.contents[0].displayName != null }
    assertThat(contentManager.contents[0].displayName).isEqualTo("Pixel 7 API 33")
    assertThat(contentManager.selectedContent?.displayName).isEqualTo("Pixel 7 API 33")

    executeStreamingAction(newTabAction, toolWindow.component, project)
    popup = popupRule.fakePopupFactory.getNextPopup(2.seconds)
    assertThat(popup.actions.toString()).isEqualTo(
        "[Separator (Connected Devices), Pixel 4 API 30 (null), " +
                "Separator (null), Pair Devices Using Wi-Fi (Open the Device Pairing dialog which allows connecting devices over Wi-Fi)]")

    // Activate mirroring of Pixel 4 API 30.
    executeStreamingAction(popup.actions[1], toolWindow.component, project)
    waitForCondition(2.seconds) { contentManager.contents.size == 2 }
    assertThat(contentManager.contents[1].displayName).isEqualTo("Pixel 4 API 30")
    assertThat(contentManager.selectedContent?.displayName).isEqualTo("Pixel 4 API 30")

    executeStreamingAction(newTabAction, toolWindow.component, project)
    popup = popupRule.fakePopupFactory.getNextPopup(2.seconds)
    assertThat(popup.actions.toString()).isEqualTo(
        "[Pair Devices Using Wi-Fi (Open the Device Pairing dialog which allows connecting devices over Wi-Fi)]")
  }

  @Test
  fun testMirroringManager() {
    assertThat(contentManager.contents).isEmpty()
    assertThat(toolWindow.isVisible).isFalse()

    val mirroringManager = project.service<MirroringManager>()
    val deviceProvisioner = project.service<DeviceProvisionerService>().deviceProvisioner
    assertThat(mirroringManager.mirroringHandles.value).isEmpty()

    agentRule.connectDevice("Pixel 2", 25, Dimension(1080, 1920)) // API too low for mirroring.
    val pixel4 = agentRule.connectDevice("Pixel 4", 30, Dimension(1080, 2280))
    val pixel7 = agentRule.connectDevice("Pixel 7", 33, Dimension(1080, 2400))
    dispatchAllEventsInIdeEventQueue()
    waitForCondition(2.seconds) { deviceProvisioner.devices.value.size == 3 }
    val pixel4Handle = deviceProvisioner.devices.value[1]
    assertThat(pixel4Handle.state.properties.model).isEqualTo("Pixel 4")
    val pixel7Handle = deviceProvisioner.devices.value[2]
    assertThat(pixel7Handle.state.properties.model).isEqualTo("Pixel 7")
    waitForCondition(2.seconds) { mirroringManager.mirroringHandles.value.size == 2 }
    assertThat(mirroringManager.mirroringHandles.value[pixel4Handle]?.mirroringState).isEqualTo(MirroringState.INACTIVE)
    assertThat(mirroringManager.mirroringHandles.value[pixel7Handle]?.mirroringState).isEqualTo(MirroringState.INACTIVE)

    runBlocking { mirroringManager.mirroringHandles.value[pixel4Handle]?.toggleMirroring() }
    waitForCondition(2.seconds) { contentManager.contents.size == 1 && contentManager.contents[0].displayName != null }
    assertThat(contentManager.contents[0].displayName).contains(pixel4Handle.state.properties.model)
    assertThat(contentManager.selectedContent?.displayName).contains(pixel4Handle.state.properties.model)
    assertThat(mirroringManager.mirroringHandles.value[pixel4Handle]?.mirroringState).isEqualTo(MirroringState.ACTIVE)
    assertThat(mirroringManager.mirroringHandles.value[pixel7Handle]?.mirroringState).isEqualTo(MirroringState.INACTIVE)

    runBlocking { mirroringManager.mirroringHandles.value[pixel4Handle]?.toggleMirroring() }
    waitForCondition(1.seconds) { contentManager.contents.size == 1 && contentManager.contents[0].displayName == null }
    assertThat(mirroringManager.mirroringHandles.value[pixel4Handle]?.mirroringState).isEqualTo(MirroringState.INACTIVE)
    assertThat(mirroringManager.mirroringHandles.value[pixel7Handle]?.mirroringState).isEqualTo(MirroringState.INACTIVE)

    agentRule.disconnectDevice(pixel7)
    waitForCondition(1.seconds) { mirroringManager.mirroringHandles.value[pixel7Handle] == null }
    // Check that mirroring handles are updated when the tool window is hidden.
    toolWindow.hide()
    waitForCondition(1.seconds) { !toolWindow.isVisible }
    agentRule.disconnectDevice(pixel4)
    waitForCondition(1.seconds) { mirroringManager.mirroringHandles.value[pixel4Handle] == null }
  }

  @Test
  fun testLivenessIndicator() {
    assertThat(contentManager.contents).isEmpty()
    assertThat(toolWindow.isVisible).isFalse()

    val mirroringManager = project.service<MirroringManager>()
    val deviceProvisioner = project.service<DeviceProvisionerService>().deviceProvisioner
    assertThat(mirroringManager.mirroringHandles.value).isEmpty()

    agentRule.connectDevice("Pixel 4", 30, Dimension(1080, 2280))
    waitForCondition(2.seconds) { deviceProvisioner.devices.value.size == 1 }
    val device = deviceProvisioner.devices.value[0]
    waitForCondition(2.seconds) { mirroringManager.mirroringHandles.value.size == 1 }
    assertThat(toolWindow.icon).isNotInstanceOf(LayeredIcon::class.java) // Liveness indicator is off.

    runBlocking { mirroringManager.mirroringHandles.value[device]?.toggleMirroring() }
    waitForCondition(2.seconds) { contentManager.contents.size == 1 && contentManager.contents[0].displayName != null }
    assertThat((toolWindow.icon as? LayeredIcon)?.getIcon(1)).isInstanceOf(IndicatorIcon::class.java) // Liveness indicator is on.

    toolWindow.hide()
    runBlocking { mirroringManager.mirroringHandles.value[device]?.toggleMirroring() }
    waitForCondition(2.seconds) { toolWindow.icon !is LayeredIcon } // Liveness indicator is off.
    assertThat(mirroringManager.mirroringHandles.value[device]?.mirroringState).isEqualTo(MirroringState.INACTIVE)
  }

  @Test
  fun testAvdStarting() {
    EmulatorSettings.getInstance()::launchInToolWindow.override(false, testRootDisposable)
    assertThat(contentManager.contents).isEmpty()
    assertThat(toolWindow.isVisible).isFalse()
    toolWindow.show()

    val avdRoot = emulatorRule.avdRoot
    val phone = emulatorRule.newEmulator(FakeEmulator.createPhoneAvd(avdRoot))
    val tablet = emulatorRule.newEmulator(FakeEmulator.createTabletAvd(avdRoot))
    tablet.start(standalone = true)
    RunningEmulatorCatalog.getInstance().updateNow().get()

    val popup = triggerAddDevicePopup()
    assertThat(popup.actions.toString()).isEqualTo(
        "[Separator (Virtual Devices), ${phone.avdName} (null), " +
        "Separator (null), Pair Devices Using Wi-Fi (Open the Device Pairing dialog which allows connecting devices over Wi-Fi)]")
    assertThat(popup.actions[1].templatePresentation.icon).isEqualTo(StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_PHONE)

    executeStreamingAction(popup.actions[1], toolWindow.component, project)
    waitForCondition(2.seconds) { contentManager.contents.size == 1 && contentManager.contents[0].displayName != null }
    assertThat(contentManager.contents[0].displayName).isEqualTo(phone.avdName)
    assertThat(contentManager.selectedContent?.displayName).isEqualTo(phone.avdName)
  }

  @Test
  fun testMirroringUserInvolvementRequired() {
    assertThat(contentManager.contents).isEmpty()
    assertThat(toolWindow.isVisible).isFalse()
    deviceMirroringSettings::activateOnConnection.override(true, testRootDisposable)

    agentRule.connectDevice("Pixel 4", 30, Dimension(1080, 2280))
    waitForCondition(15.seconds) { contentManager.contents.size == 1 && contentManager.contents[0].displayName != null }
    assertThat(contentManager.contents[0].displayName).isEqualTo("Pixel 4 API 30")

    // Calling userInvolvementRequired should trigger device selection even if it is done before the device is connected.
    val nextSerialNumber = "2"
    project.messageBus.syncPublisher(DeviceHeadsUpListener.TOPIC).userInvolvementRequired(nextSerialNumber, project)
    val device = agentRule.connectDevice("Pixel 7", 33, Dimension(1080, 2400))
    assertThat(device.serialNumber).isEqualTo(nextSerialNumber)
    waitForCondition(15.seconds) { contentManager.contents.size == 2 }
    assertThat(contentManager.contents[1].displayName).isEqualTo("Pixel 7 API 33")
    assertThat(contentManager.selectedContent?.displayName).isEqualTo("Pixel 7 API 33")
  }

  @Test
  fun testMirroringConfirmationDialogAccept() {
    deviceMirroringSettings::activateOnConnection.override(true, testRootDisposable)
    assertThat(toolWindow.isVisible).isFalse()

    deviceMirroringSettings.confirmationDialogShown = false

    val device = agentRule.connectDevice("Pixel 4", 30, Dimension(1080, 2280))

    createModalDialogAndInteractWithIt(toolWindow::show) { dlg ->
      val ui = FakeUi(dlg.rootPane)
      ui.clickOn(ui.getComponent<JButton> { it.text == "Acknowledge" })
    }

    waitForCondition(15.seconds) { contentManager.contents.size == 1 && contentManager.contents[0].displayName != null }
    assertThat(contentManager.contents[0].displayName).isEqualTo("Pixel 4 API 30")

    agentRule.disconnectDevice(device)
    waitForCondition(10.seconds) { contentManager.contents.size == 1 && contentManager.contents[0].displayName == null }

    assertThat(deviceMirroringSettings.confirmationDialogShown).isTrue()
  }

  @Test
  fun testMirroringConfirmationDialogReject() {
    deviceMirroringSettings::activateOnConnection.override(true, testRootDisposable)
    assertThat(contentManager.contents).isEmpty()
    assertThat(toolWindow.isVisible).isFalse()

    deviceMirroringSettings.confirmationDialogShown = false

    createModalDialogAndInteractWithIt({ agentRule.connectDevice("Pixel 4", 30, Dimension(1080, 2280)) }) { dlg ->
      val ui = FakeUi(dlg.rootPane)
      ui.clickOn(ui.getComponent<JButton> { it.text == "Cancel" })
    }

    assertThat(deviceMirroringSettings.confirmationDialogShown).isFalse()
  }

  @Test
  fun testUnsupportedPhysicalPhone() {
    assertThat(contentManager.contents).isEmpty()
    assertThat(toolWindow.isVisible).isFalse()

    agentRule.connectDevice("Pixel", 25, Dimension(1080, 1920), abi = "armeabi-v7a")
    toolWindow.show()

    dispatchAllEventsInIdeEventQueue()
    awaitQuiescence(AndroidExecutors.getInstance().workerThreadExecutor as ThreadPoolExecutor, 5, SECONDS)
    dispatchAllEventsInIdeEventQueue()
    assertThat(contentManager.contents.size == 1).isTrue()
    assertThat(contentManager.contents[0].displayName).isNull()
  }

  @Test
  fun testUnsupportedPhysicalWatch() {
    assertThat(contentManager.contents).isEmpty()
    assertThat(toolWindow.isVisible).isFalse()

    agentRule.connectDevice("LG Watch Sport", 29, Dimension(480, 480), abi = "armeabi-v7a",
                            additionalDeviceProperties = mapOf(DevicePropertyNames.RO_BUILD_CHARACTERISTICS to "nosdcard,watch"))
    toolWindow.show()

    dispatchAllEventsInIdeEventQueue()
    awaitQuiescence(AndroidExecutors.getInstance().workerThreadExecutor as ThreadPoolExecutor, 5, SECONDS)
    dispatchAllEventsInIdeEventQueue()
    assertThat(contentManager.contents.size == 1).isTrue()
    assertThat(contentManager.contents[0].displayName).isNull()
  }

  @Test
  fun testWindowViewModeActionSetTypeWhenPerformed() {
    toolWindow.setType(ToolWindowType.DOCKED) {}

    windowFactory.createToolWindowContent(project, toolWindow)
    val windowAction = toolWindow.titleActions.find { it.templateText == "Window" }!!
    windowAction.actionPerformed(AnActionEvent.createFromAnAction(windowAction, null, "", dataContext))

    assertThat(toolWindow.type).isEqualTo(ToolWindowType.WINDOWED)
  }

  @Test
  fun testWindowViewModeActionUnavailableWhenTypeIsWindowedOrFloat() {
    windowFactory.createToolWindowContent(project, toolWindow)
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

  private fun createToolWindow(): TestToolWindow {
    val windowManager = TestToolWindowManager()
    project.replaceService(ToolWindowManager::class.java, windowManager, testRootDisposable)
    val toolWindow = windowManager.toolWindow
    assertThat(windowFactory.shouldBeAvailable(project)).isTrue()
    windowFactory.init(toolWindow)
    return toolWindow
  }

  private fun renderAndGetFrameNumber(fakeUi: FakeUi, displayView: AbstractDisplayView): UInt {
    fakeUi.render() // The frame number may get updated as a result of rendering.
    return displayView.frameNumber
  }

  private fun triggerAddDevicePopup(): FakeListPopup<Any> {
    waitForCondition(2.seconds) { toolWindow.tabActions.isNotEmpty() }
    val newTabAction = toolWindow.tabActions[0]
    val testEvent = createTestEvent(toolWindow.component, project)
    newTabAction.actionPerformed(testEvent)
    lateinit var popup: FakeListPopup<Any>
    waitForCondition(4.seconds) {
      newTabAction.actionPerformed(testEvent)
      popup = popupRule.fakePopupFactory.getNextPopup(2.seconds)
      popup.items.size >= 2
    }
    return popup
  }

  private inner class TestToolWindowManager : ToolWindowHeadlessManagerImpl(project) {
    var toolWindow = TestToolWindow(this)

    override fun getToolWindow(id: String?): ToolWindow? {
      return if (id == RUNNING_DEVICES_TOOL_WINDOW_ID) toolWindow else super.getToolWindow(id)
    }

    override fun invokeLater(runnable: Runnable) {
      ApplicationManager.getApplication().invokeLater(runnable)
    }
  }

  private inner class TestToolWindow(private val manager: ToolWindowManager) : ToolWindowHeadlessManagerImpl.MockToolWindow(project) {

    var tabActions: List<AnAction> = emptyList()
      private set
    var titleActions: List<AnAction> = emptyList()
      private set
    private var available = true
    private var visible = false
    private var active = false
    private var type = ToolWindowType.DOCKED
    private var icon = StudioIcons.Shell.ToolWindows.EMULATOR

    override fun setAvailable(available: Boolean) {
      this.available = available
    }

    override fun isAvailable(): Boolean {
      return available
    }

    override fun show(runnable: Runnable?) {
      if (!visible) {
        windowFactory.createToolWindowContent(project, this)
        visible = true
        notifyStateChanged(ToolWindowManagerEventType.ShowToolWindow)
        runnable?.run()
      }
    }

    override fun hide(runnable: Runnable?) {
      if (visible) {
        visible = false
        notifyStateChanged(ToolWindowManagerEventType.HideToolWindow)
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

    override fun getType(): ToolWindowType {
      return type
    }

    override fun setType(type: ToolWindowType, runnable: Runnable?) {
      this.type = type
      runnable?.run()
    }

    override fun getIcon(): Icon {
      return icon
    }

    override fun setIcon(icon: Icon) {
      this.icon = icon
    }

    private fun notifyStateChanged(changeType: ToolWindowManagerEventType) {
      project.messageBus.syncPublisher(ToolWindowManagerListener.TOPIC).stateChanged(manager, changeType)
    }
  }
}
