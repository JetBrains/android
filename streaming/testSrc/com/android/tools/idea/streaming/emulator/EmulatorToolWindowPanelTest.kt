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
package com.android.tools.idea.streaming.emulator

import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.IDevice
import com.android.emulator.control.DisplayConfiguration
import com.android.sdklib.AndroidVersion
import com.android.testutils.ImageDiffUtil
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.eq
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.testutils.TestUtils
import com.android.tools.adtui.ImageUtils
import com.android.tools.adtui.actions.ZoomType
import com.android.tools.adtui.swing.FakeKeyboardFocusManager
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.HeadlessRootPaneContainer
import com.android.tools.adtui.swing.IconLoaderRule
import com.android.tools.adtui.swing.SetPortableUiFontRule
import com.android.tools.idea.adb.AdbService
import com.android.tools.idea.concurrency.waitForCondition
import com.android.tools.idea.editors.liveedit.ui.LiveEditAction
import com.android.tools.idea.protobuf.TextFormat.shortDebugString
import com.android.tools.idea.streaming.emulator.EmulatorToolWindowPanel.MultiDisplayStateStorage
import com.android.tools.idea.streaming.emulator.FakeEmulator.GrpcCallRecord
import com.android.tools.idea.streaming.executeDeviceAction
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.mockStatic
import com.android.tools.idea.testing.registerServiceInstance
import com.android.tools.idea.ui.screenrecording.ScreenRecordingSupportedCache
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.Futures.immediateFuture
import com.intellij.configurationStore.deserialize
import com.intellij.configurationStore.serialize
import com.intellij.ide.ClipboardSynchronizer
import com.intellij.ide.dnd.DnDEvent
import com.intellij.ide.dnd.DnDManager
import com.intellij.ide.dnd.DnDTarget
import com.intellij.ide.dnd.TransferableWrapper
import com.intellij.ide.impl.HeadlessDataManager
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.replaceService
import com.intellij.ui.EditorNotificationPanel
import org.jetbrains.android.sdk.AndroidSdkUtils.ADB_PATH_PROPERTY
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.mockito.Mockito.anyLong
import org.mockito.Mockito.verify
import java.awt.Component
import java.awt.Dimension
import java.awt.MouseInfo
import java.awt.Point
import java.awt.PointerInfo
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.event.FocusEvent
import java.awt.event.KeyEvent.VK_DOWN
import java.awt.event.KeyEvent.VK_END
import java.awt.event.KeyEvent.VK_HOME
import java.awt.event.KeyEvent.VK_LEFT
import java.awt.event.KeyEvent.VK_PAGE_DOWN
import java.awt.event.KeyEvent.VK_PAGE_UP
import java.awt.event.KeyEvent.VK_RIGHT
import java.awt.event.KeyEvent.VK_SHIFT
import java.awt.event.KeyEvent.VK_UP
import java.awt.event.MouseEvent
import java.awt.event.MouseEvent.MOUSE_MOVED
import java.io.File
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.swing.JViewport

/**
 * Tests for [EmulatorToolWindowPanel] and some of the toolbar actions.
 */
@RunsInEdt
class EmulatorToolWindowPanelTest {
  companion object {
    @JvmField
    @ClassRule
    val iconRule = IconLoaderRule()
  }

  private val projectRule = AndroidProjectRule.inMemory()
  private val emulatorRule = FakeEmulatorRule()
  @get:Rule
  val ruleChain: RuleChain = RuleChain(projectRule, emulatorRule, EdtRule())

  @get:Rule
  val portableUiFontRule = SetPortableUiFontRule()

  private var nullableEmulator: FakeEmulator? = null

  private var emulator: FakeEmulator
    get() = nullableEmulator ?: throw IllegalStateException()
    set(value) { nullableEmulator = value }

  private val testRootDisposable
    get() = projectRule.testRootDisposable

  @Before
  fun setUp() {
    // Necessary to properly update toolbar button states.
    HeadlessDataManager.fallbackToProductionDataManager(testRootDisposable)
    val mockScreenRecordingCache = mock<ScreenRecordingSupportedCache>()
    whenever(mockScreenRecordingCache.isScreenRecordingSupported(any(), Mockito.anyInt())).thenReturn(true)
    projectRule.project.registerServiceInstance(ScreenRecordingSupportedCache::class.java, mockScreenRecordingCache, testRootDisposable)
  }

  @Test
  fun testAppearanceAndToolbarActions() {
    val focusManager = FakeKeyboardFocusManager(testRootDisposable)

    val panel = createWindowPanelForPhone()
    val ui = FakeUi(panel, createFakeWindow = true) // Fake window is necessary for the toolbars to be rendered.

    assertThat(panel.primaryEmulatorView).isNull()

    panel.createContent(true)
    val emulatorView = panel.primaryEmulatorView ?: throw AssertionError()

    // Check appearance.
    var frameNumber = emulatorView.frameNumber
    assertThat(frameNumber).isEqualTo(0)
    panel.size = Dimension(400, 600)
    ui.updateToolbars()
    ui.layoutAndDispatchEvents()
    val streamScreenshotCall = getStreamScreenshotCallAndWaitForFrame(ui, panel, ++frameNumber)
    assertThat(shortDebugString(streamScreenshotCall.request)).isEqualTo("format: RGB888 width: 363 height: 520")
    assertAppearance(ui, "AppearanceAndToolbarActions1", maxPercentDifferentWindows = 0.03, maxPercentDifferentMac = 0.03)

    // Check EmulatorPowerButtonAction.
    var button = ui.getComponent<ActionButton> { it.action.templateText == "Power" }
    ui.mousePressOn(button)
    var call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendKey")
    assertThat(shortDebugString(call.request)).isEqualTo("""key: "Power"""")
    ui.mouseRelease()
    call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendKey")
    assertThat(shortDebugString(call.request)).isEqualTo("""eventType: keyup key: "Power"""")

    // Check EmulatorVolumeUpButtonAction.
    button = ui.getComponent { it.action.templateText == "Volume Up" }
    ui.mousePressOn(button)
    call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendKey")
    assertThat(shortDebugString(call.request)).isEqualTo("""key: "AudioVolumeUp"""")
    ui.mouseRelease()
    call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendKey")
    assertThat(shortDebugString(call.request)).isEqualTo("""eventType: keyup key: "AudioVolumeUp"""")

    // Check EmulatorVolumeDownButtonAction.
    button = ui.getComponent { it.action.templateText == "Volume Down" }
    ui.mousePressOn(button)
    call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendKey")
    assertThat(shortDebugString(call.request)).isEqualTo("""key: "AudioVolumeDown"""")
    ui.mouseRelease()
    call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendKey")
    assertThat(shortDebugString(call.request)).isEqualTo("""eventType: keyup key: "AudioVolumeDown"""")

    // Ensures that LiveEditAction starts off hidden since it's disconnected.
    assertThat(ui.findComponent<LiveEditAction>()).isNull()

    assertThat(streamScreenshotCall.completion.isCancelled).isFalse()

    // Check clipboard synchronization.
    val content = StringSelection("host clipboard")
    ClipboardSynchronizer.getInstance().setContent(content, content)
    focusManager.focusOwner = emulatorView
    call = emulator.getNextGrpcCall(3, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/setClipboard")
    assertThat(shortDebugString(call.request)).isEqualTo("""text: "host clipboard"""")
    call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/streamClipboard")
    call.waitForResponse(2, TimeUnit.SECONDS)
    emulator.clipboard = "device clipboard"
    call.waitForResponse(2, TimeUnit.SECONDS)
    waitForCondition(2, TimeUnit.SECONDS) { ClipboardSynchronizer.getInstance().getData(DataFlavor.stringFlavor) == "device clipboard" }
    assertThat(call.completion.isCancelled).isFalse()
    focusManager.focusOwner = null
    call.waitForCancellation(2, TimeUnit.SECONDS)

    panel.destroyContent()
    assertThat(panel.primaryEmulatorView).isNull()
    streamScreenshotCall.waitForCancellation(2, TimeUnit.SECONDS)
  }

  @Test
  fun testWearToolbarActionsApi30() {
    val avdFolder = FakeEmulator.createWatchAvd(emulatorRule.root, api = 30)
    val panel = createWindowPanel(avdFolder)
    val ui = FakeUi(panel, createFakeWindow = true) // Fake window is necessary for the toolbars to be rendered.

    assertThat(panel.primaryEmulatorView).isNull()

    panel.createContent(true)
    val emulatorView = panel.primaryEmulatorView ?: throw AssertionError()

    // Check appearance.
    var frameNumber = emulatorView.frameNumber
    assertThat(frameNumber).isEqualTo(0)
    panel.size = Dimension(430, 450)
    ui.updateToolbars()
    ui.layoutAndDispatchEvents()
    val streamScreenshotCall = getStreamScreenshotCallAndWaitForFrame(ui, panel, ++frameNumber)
    assertThat(shortDebugString(streamScreenshotCall.request)).isEqualTo("format: RGB888 width: 320 height: 320")
    assertAppearance(ui, "WearToolbarActions1", maxPercentDifferentWindows = 0.04, maxPercentDifferentMac = 0.04)

    // Check Wear1ButtonAction.
    var button = ui.getComponent<ActionButton> { it.action.templateText == "Button 1" }
    ui.mouseClickOn(button)
    var call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendKey")
    assertThat(shortDebugString(call.request)).isEqualTo("""eventType: keypress key: "Home"""")

    // Check Wear2ButtonAction.
    button = ui.getComponent { it.action.templateText == "Button 2" }
    ui.mousePressOn(button)
    call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendKey")
    assertThat(shortDebugString(call.request)).isEqualTo("""key: "Power"""")
    ui.mouseRelease()
    call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendKey")
    assertThat(shortDebugString(call.request)).isEqualTo("""eventType: keyup key: "Power"""")

    // Check PalmAction.
    button = ui.getComponent { it.action.templateText == "Palm" }
    ui.mouseClickOn(button)
    call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendKey")
    assertThat(shortDebugString(call.request)).isEqualTo("""eventType: keypress key: "Standby"""")

    // Check TiltAction.
    button = ui.getComponent { it.action.templateText == "Tilt" }
    ui.mouseClickOn(button)
    call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/setPhysicalModel")
    assertThat(shortDebugString(call.request)).isEqualTo("target: WRIST_TILT value { data: 1.0 }")

    // Check that the buttons not applicable to Wear OS 3 are hidden.
    assertThat(ui.findComponent<ActionButton> { it.action.templateText == "Power" }).isNull()
    assertThat(ui.findComponent<ActionButton> { it.action.templateText == "Volume Up" }).isNull()
    assertThat(ui.findComponent<ActionButton> { it.action.templateText == "Volume Down" }).isNull()
    assertThat(ui.findComponent<ActionButton> { it.action.templateText == "Rotate Left" }).isNull()
    assertThat(ui.findComponent<ActionButton> { it.action.templateText == "Rotate Right" }).isNull()
    assertThat(ui.findComponent<ActionButton> { it.action.templateText == "Home" }).isNull()
    assertThat(ui.findComponent<ActionButton> { it.action.templateText == "Overview" }).isNull()

    panel.destroyContent()
    assertThat(panel.primaryEmulatorView).isNull()
    streamScreenshotCall.waitForCancellation(2, TimeUnit.SECONDS)
  }

  @Test
  fun testWearToolbarActionsApi28() {
    val avdFolder = FakeEmulator.createWatchAvd(emulatorRule.root, api = 28)
    val panel = createWindowPanel(avdFolder)
    val ui = FakeUi(panel, createFakeWindow = true) // Fake window is necessary for the toolbars to be rendered.

    assertThat(panel.primaryEmulatorView).isNull()

    panel.createContent(true)
    panel.size = Dimension(430, 450)
    ui.updateToolbars()
    ui.layoutAndDispatchEvents()

    // Check toolbar buttons.
    assertThat(ui.findComponent<ActionButton> { it.action.templateText == "Button 1" }).isNotNull()
    assertThat(ui.findComponent<ActionButton> { it.action.templateText == "Button 2" }).isNull()
    assertThat(ui.findComponent<ActionButton> { it.action.templateText == "Palm" }).isNotNull()
    assertThat(ui.findComponent<ActionButton> { it.action.templateText == "Tilt" }).isNotNull()

    // Check that the buttons not applicable to Wear OS are hidden.
    assertThat(ui.findComponent<ActionButton> { it.action.templateText == "Power" }).isNull()
    assertThat(ui.findComponent<ActionButton> { it.action.templateText == "Volume Up" }).isNull()
    assertThat(ui.findComponent<ActionButton> { it.action.templateText == "Volume Down" }).isNull()
    assertThat(ui.findComponent<ActionButton> { it.action.templateText == "Rotate Left" }).isNull()
    assertThat(ui.findComponent<ActionButton> { it.action.templateText == "Rotate Right" }).isNull()
    assertThat(ui.findComponent<ActionButton> { it.action.templateText == "Home" }).isNull()
    assertThat(ui.findComponent<ActionButton> { it.action.templateText == "Overview" }).isNull()

    val streamScreenshotCall = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    panel.destroyContent()
    assertThat(panel.primaryEmulatorView).isNull()
    streamScreenshotCall.waitForCancellation(2, TimeUnit.SECONDS)
  }

  @Test
  fun testWearToolbarActionsApi26() {
    val avdFolder = FakeEmulator.createWatchAvd(emulatorRule.root, api = 26)
    val panel = createWindowPanel(avdFolder)
    val ui = FakeUi(panel, createFakeWindow = true) // Fake window is necessary for the toolbars to be rendered.

    assertThat(panel.primaryEmulatorView).isNull()

    panel.createContent(true)
    panel.size = Dimension(430, 450)
    ui.updateToolbars()
    ui.layoutAndDispatchEvents()

    // Check that the buttons specific to API 30 and API 28 are hidden.
    assertThat(ui.findComponent<ActionButton> { it.action.templateText == "Button 1" }).isNull()
    assertThat(ui.findComponent<ActionButton> { it.action.templateText == "Button 2" }).isNull()
    assertThat(ui.findComponent<ActionButton> { it.action.templateText == "Palm" }).isNull()
    assertThat(ui.findComponent<ActionButton> { it.action.templateText == "Tilt" }).isNull()

    // Check that the generic Android buttons are visible on API 26.
    assertThat(ui.findComponent<ActionButton> { it.action.templateText == "Power" }).isNotNull()
    assertThat(ui.findComponent<ActionButton> { it.action.templateText == "Home" }).isNotNull()
    assertThat(ui.findComponent<ActionButton> { it.action.templateText == "Overview" }).isNotNull()

    // Check that the buttons not applicable to Wear OS are hidden.
    assertThat(ui.findComponent<ActionButton> { it.action.templateText == "Volume Up" }).isNull()
    assertThat(ui.findComponent<ActionButton> { it.action.templateText == "Volume Down" }).isNull()
    assertThat(ui.findComponent<ActionButton> { it.action.templateText == "Rotate Left" }).isNull()
    assertThat(ui.findComponent<ActionButton> { it.action.templateText == "Rotate Right" }).isNull()

    val streamScreenshotCall = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    panel.destroyContent()
    assertThat(panel.primaryEmulatorView).isNull()
    streamScreenshotCall.waitForCancellation(2, TimeUnit.SECONDS)
  }

  @Test
  fun testChangeDisplayMode() {
    val avdFolder = FakeEmulator.createResizableAvd(emulatorRule.root)
    val panel = createWindowPanel(avdFolder)
    panel.zoomToolbarVisible = false
    val ui = FakeUi(panel, createFakeWindow = true) // Fake window is necessary for the toolbars to be rendered.
    val project = projectRule.project

    assertThat(panel.primaryEmulatorView).isNull()

    panel.createContent(true)
    val emulatorView = panel.primaryEmulatorView ?: throw AssertionError()

    var frameNumber = emulatorView.frameNumber
    assertThat(frameNumber).isEqualTo(0)
    panel.size = Dimension(1200, 1200)
    ui.updateToolbars()
    ui.layoutAndDispatchEvents()
    var streamScreenshotCall = getStreamScreenshotCallAndWaitForFrame(ui, panel, ++frameNumber)
    assertThat(shortDebugString(streamScreenshotCall.request)).isEqualTo("format: RGB888 width: 1080 height: 1171")
    assertAppearance(ui, "ChangeDisplayMode1", maxPercentDifferentWindows = 0.002, maxPercentDifferentMac = 0.002)

    // Set the desktop display mode.
    executeDeviceAction("android.emulator.display.mode.tablet", emulatorView, project)
    val setDisplayModeCall = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(setDisplayModeCall.methodName).isEqualTo("android.emulation.control.EmulatorController/setDisplayMode")
    assertThat(shortDebugString(setDisplayModeCall.request)).isEqualTo("value: TABLET")

    streamScreenshotCall = getStreamScreenshotCallAndWaitForFrame(ui, panel, ++frameNumber)
    assertThat(shortDebugString(streamScreenshotCall.request)).isEqualTo("format: RGB888 width: 1200 height: 1171")
    assertAppearance(ui, "ChangeDisplayMode2", maxPercentDifferentWindows = 0.002, maxPercentDifferentMac = 0.002)
  }

  @Test
  fun testZoom() {
    val panel = createWindowPanelForPhone()
    val ui = FakeUi(panel, createFakeWindow = true) // Fake window is necessary for the toolbars to be rendered.

    assertThat(panel.primaryEmulatorView).isNull()

    panel.createContent(true)
    var emulatorView = panel.primaryEmulatorView ?: throw AssertionError()

    var frameNumber = emulatorView.frameNumber
    assertThat(frameNumber).isEqualTo(0)
    panel.size = Dimension(400, 600)
    ui.updateToolbars()
    ui.layoutAndDispatchEvents()
    val streamScreenshotCall = getStreamScreenshotCallAndWaitForFrame(ui, panel, ++frameNumber)
    assertThat(shortDebugString(streamScreenshotCall.request)).isEqualTo("format: RGB888 width: 363 height: 520")

    // Zoom in.
    emulatorView.zoom(ZoomType.IN)
    ui.layoutAndDispatchEvents()
    assertThat(emulatorView.scale).isWithin(0.0001).of(0.25)
    assertThat(emulatorView.preferredSize).isEqualTo(Dimension(396, 811))
    val viewport = emulatorView.parent as JViewport
    assertThat(viewport.viewSize).isEqualTo(Dimension(400, 811))
    // Scroll to the bottom.
    val scrollPosition = Point(viewport.viewPosition.x, viewport.viewSize.height - viewport.height)
    viewport.viewPosition = scrollPosition

    // Recreate panel content.
    val uiState = panel.destroyContent()
    panel.createContent(true, uiState)
    emulatorView = panel.primaryEmulatorView ?: throw AssertionError()
    ui.layoutAndDispatchEvents()

    // Check that zoom level and scroll position are restored.
    assertThat(emulatorView.scale).isWithin(0.0001).of(0.25)
    assertThat(viewport.viewSize).isEqualTo(Dimension(400, 811))
    assertThat(viewport.viewPosition).isEqualTo(scrollPosition)

    panel.destroyContent()
  }

  @Test
  fun testMultipleDisplays() {
    val panel = createWindowPanelForPhone()
    val ui = FakeUi(panel, createFakeWindow = true) // Fake window is necessary for the toolbars to be rendered.

    assertThat(panel.primaryEmulatorView).isNull()

    panel.createContent(true)
    val emulatorView = panel.primaryEmulatorView ?: throw AssertionError()

    // Check appearance.
    val frameNumbers = intArrayOf(emulatorView.frameNumber, 0, 0)
    assertThat(frameNumbers[PRIMARY_DISPLAY_ID]).isEqualTo(0)
    panel.size = Dimension(400, 600)
    ui.updateToolbars()
    ui.layoutAndDispatchEvents()
    val streamScreenshotCall = getStreamScreenshotCallAndWaitForFrame(ui, panel, ++frameNumbers[PRIMARY_DISPLAY_ID])
    assertThat(shortDebugString(streamScreenshotCall.request)).isEqualTo("format: RGB888 width: 363 height: 520")

    emulator.changeSecondaryDisplays(listOf(DisplayConfiguration.newBuilder().setDisplay(1).setWidth(1080).setHeight(2340).build(),
                                            DisplayConfiguration.newBuilder().setDisplay(2).setWidth(2048).setHeight(1536).build()))

    waitForCondition(2, TimeUnit.SECONDS) { ui.findAllComponents<EmulatorView>().size == 3 }
    ui.layoutAndDispatchEvents()
    waitForNextFrameInAllDisplays(ui, frameNumbers)
    assertAppearance(ui, "MultipleDisplays1", maxPercentDifferentWindows = 0.09, maxPercentDifferentMac = 0.09)

    // Resize emulator display panels.
    ui.findAllComponents<EmulatorSplitPanel>().forEach { it.proportion /= 2 }
    ui.layoutAndDispatchEvents()
    val displayViewSizes = ui.findAllComponents<EmulatorView>().map { it.size }

    val uiState = panel.destroyContent()
    assertThat(panel.primaryEmulatorView).isNull()
    streamScreenshotCall.waitForCancellation(2, TimeUnit.SECONDS)

    // Check serialization and deserialization of MultiDisplayStateStorage.
    val state = MultiDisplayStateStorage.getInstance(projectRule.project)
    val deserializedState = serialize(state)?.deserialize(MultiDisplayStateStorage::class.java)
    assertThat(deserializedState?.displayStateByAvdFolder).isEqualTo(state.displayStateByAvdFolder)

    // Check that the panel layout is recreated when the panel content is created again.
    panel.createContent(true, uiState)
    ui.layoutAndDispatchEvents()
    waitForCondition(2, TimeUnit.SECONDS) { ui.findAllComponents<EmulatorView>().size == 3 }
    assertThat(ui.findAllComponents<EmulatorView>().map { it.size }).isEqualTo(displayViewSizes)
  }

  @Test
  fun testVirtualSceneCamera() {
    val panel = createWindowPanelForPhone()
    val ui = FakeUi(panel)

    assertThat(panel.primaryEmulatorView).isNull()

    panel.createContent(true)
    val emulatorView = panel.primaryEmulatorView ?: throw AssertionError()
    panel.size = Dimension(400, 600)
    ui.layoutAndDispatchEvents()

    val container = HeadlessRootPaneContainer(panel)
    val glassPane = container.glassPane

    val initialMousePosition = Point(glassPane.x + glassPane.width / 2, glassPane.y + glassPane.height / 2)
    val pointerInfo = mock<PointerInfo>()
    whenever(pointerInfo.location).thenReturn(initialMousePosition)
    val mouseInfoMock = mockStatic<MouseInfo>(testRootDisposable)
    mouseInfoMock.whenever<Any?> { MouseInfo.getPointerInfo() }.thenReturn(pointerInfo)

    val focusManager = FakeKeyboardFocusManager(testRootDisposable)
    focusManager.focusOwner = emulatorView

    assertThat(ui.findComponent<EditorNotificationPanel>()).isNull()

    // Activate the camera and check that a notification is displayed.
    emulator.virtualSceneCameraActive = true
    waitForCondition(2, TimeUnit.SECONDS) { ui.findComponent<EditorNotificationPanel>() != null }
    assertThat(ui.findComponent<EditorNotificationPanel>()?.text).isEqualTo("Hold Shift to control camera")

    // Check that the notification is removed when the emulator view loses focus.
    val focusLostEvent = FocusEvent(emulatorView, FocusEvent.FOCUS_LOST, false, null)
    for (listener in emulatorView.focusListeners) {
      listener.focusLost(focusLostEvent)
    }
    assertThat(ui.findComponent<EditorNotificationPanel>()).isNull()

    // Check that the notification is displayed again when the emulator view gains focus.
    val focusGainedEvent = FocusEvent(emulatorView, FocusEvent.FOCUS_GAINED, false, null)
    for (listener in emulatorView.focusListeners) {
      listener.focusGained(focusGainedEvent)
    }
    assertThat(ui.findComponent<EditorNotificationPanel>()?.text).isEqualTo("Hold Shift to control camera")

    // Check that the notification changes when Shift is pressed.
    ui.keyboard.press(VK_SHIFT)
    assertThat(ui.findComponent<EditorNotificationPanel>()?.text)
        .isEqualTo("Move camera with WASDQE keys, rotate with mouse or arrow keys")

    // Check camera movement.
    val velocityExpectations =
        mapOf('W' to "z: -1.0", 'A' to "x: -1.0", 'S' to "z: 1.0", 'D' to "x: 1.0", 'Q' to "y: -1.0", 'E' to "y: 1.0")
    val callFilter = FakeEmulator.defaultCallFilter.or("android.emulation.control.EmulatorController/streamClipboard",
                                                       "android.emulation.control.EmulatorController/streamScreenshot")
    for ((key, expected) in velocityExpectations) {
      ui.keyboard.press(key.code)

      var call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS, callFilter)
      assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/setVirtualSceneCameraVelocity")
      assertThat(shortDebugString(call.request)).isEqualTo(expected)
      ui.keyboard.release(key.code)
      call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS, callFilter)
      assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/setVirtualSceneCameraVelocity")
      assertThat(shortDebugString(call.request)).isEqualTo("")
    }

    // Check camera rotation.
    val x = initialMousePosition.x + 5
    val y = initialMousePosition.y + 10
    val event = MouseEvent(glassPane, MOUSE_MOVED, System.currentTimeMillis(), ui.keyboard.toModifiersCode(), x, y, x, y, 0, false, 0)
    glassPane.dispatch(event)
    var call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS, callFilter)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/rotateVirtualSceneCamera")
    assertThat(shortDebugString(call.request)).isEqualTo("x: 2.3561945 y: 1.5707964")

    val rotationExpectations = mapOf(VK_LEFT to "y: 0.08726646", VK_RIGHT to "y: -0.08726646",
                                     VK_UP to "x: 0.08726646", VK_DOWN to "x: -0.08726646",
                                     VK_HOME to "x: 0.08726646 y: 0.08726646", VK_END to "x: -0.08726646 y: 0.08726646",
                                     VK_PAGE_UP to "x: 0.08726646 y: -0.08726646", VK_PAGE_DOWN to "x: -0.08726646 y: -0.08726646")
    for ((key, expected) in rotationExpectations) {
      ui.keyboard.pressAndRelease(key)
      call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS, callFilter)
      assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/rotateVirtualSceneCamera")
      assertThat(shortDebugString(call.request)).isEqualTo(expected)
    }

    // Check that the notification changes when Shift is released.
    ui.keyboard.release(VK_SHIFT)
    assertThat(ui.findComponent<EditorNotificationPanel>()?.text).isEqualTo("Hold Shift to control camera")

    // Deactivate the camera and check that the notification is removed.
    emulator.virtualSceneCameraActive = false
    waitForCondition(2, TimeUnit.SECONDS) { ui.findComponent<EditorNotificationPanel>() == null }

    panel.destroyContent()
  }

  @Test
  fun testDragToInstallApp() {
    val target = createDropTarget()
    val device = createMockDevice()

    // Check APK installation.
    val apkFileList = listOf(File("/some_folder/myapp.apk"))
    val event = createDragEvent(apkFileList)

    // Simulate drag.
    target.update(event)

    verify(event).isDropPossible = true

    val installPackagesCalled = CountDownLatch(1)
    val installOptions = listOf("-t", "--user", "current", "--full")
    val fileListCaptor = ArgumentCaptor.forClass(apkFileList.javaClass)
    whenever(device.installPackages(fileListCaptor.capture(), eq(true), eq(installOptions), anyLong(), any())).then {
      installPackagesCalled.countDown()
    }

    // Simulate drop.
    target.drop(event)

    assertThat(installPackagesCalled.await(2, TimeUnit.SECONDS)).isTrue()
    assertThat(fileListCaptor.value).isEqualTo(apkFileList)
  }

  @Test
  fun testDragToPushFiles() {
    val target = createDropTarget()
    val device = createMockDevice()

    // Check file copying.
    val fileList = listOf(File("/some_folder/file1.txt"), File("/some_folder/file2.jpg"))
    val event = createDragEvent(fileList)

    // Simulate drag.
    target.update(event)

    verify(event).isDropPossible = true

    val pushCalled = CountDownLatch(1)
    val firstArgCaptor = ArgumentCaptor.forClass(Array<String>::class.java)
    val secondArgCaptor = ArgumentCaptor.forClass(String::class.java)
    whenever(device.push(firstArgCaptor.capture(), secondArgCaptor.capture())).then { pushCalled.countDown() }

    // Simulate drop.
    target.drop(event)

    assertThat(pushCalled.await(2, TimeUnit.SECONDS)).isTrue()
    assertThat(firstArgCaptor.value).isEqualTo(fileList.map(File::getAbsolutePath).toTypedArray())
    assertThat(secondArgCaptor.value).isEqualTo("/sdcard/Download")
  }

  private fun createDropTarget(): DnDTarget {
    val adb = TestUtils.resolveWorkspacePathUnchecked("$TEST_DATA_PATH/fake-adb")
    val savedAdbPath = System.getProperty(ADB_PATH_PROPERTY)
    if (savedAdbPath != null) {
      Disposer.register(testRootDisposable) { System.setProperty(ADB_PATH_PROPERTY, savedAdbPath) }
    }
    System.setProperty(ADB_PATH_PROPERTY, adb.toString())

    var nullableTarget: DnDTarget? = null
    val mockDndManager = mock<DnDManager>()
    whenever(mockDndManager.registerTarget(any(), any())).then {
      it.apply { nullableTarget = getArgument<DnDTarget>(0) }
    }
    ApplicationManager.getApplication().replaceService(DnDManager::class.java, mockDndManager, testRootDisposable)

    val panel = createWindowPanelForPhone()
    panel.createContent(false)
    Disposer.register(testRootDisposable) { panel.destroyContent() }

    return nullableTarget as DnDTarget
  }

  private fun createMockDevice(): IDevice {
    val device = mock<IDevice>()
    whenever(device.isEmulator).thenReturn(true)
    whenever(device.serialNumber).thenReturn("emulator-${emulator.serialPort}")
    whenever(device.version).thenReturn(AndroidVersion(AndroidVersion.MIN_RECOMMENDED_API))
    val mockAdb = mock<AndroidDebugBridge>()
    whenever(mockAdb.devices).thenReturn(arrayOf(device))
    val mockAdbService = mock<AdbService>()
    whenever(mockAdbService.getDebugBridge(any(File::class.java))).thenReturn(immediateFuture(mockAdb))
    ApplicationManager.getApplication().registerServiceInstance(AdbService::class.java, mockAdbService, testRootDisposable)
    return device
  }

  private fun createDragEvent(files: List<File>): DnDEvent {
    val transferableWrapper = mock<TransferableWrapper>()
    whenever(transferableWrapper.asFileList()).thenReturn(files)
    val event = mock<DnDEvent>()
    whenever(event.isDataFlavorSupported(DataFlavor.javaFileListFlavor)).thenReturn(true)
    whenever(event.attachedObject).thenReturn(transferableWrapper)
    return event
  }

  private fun FakeUi.mousePressOn(component: Component) {
    val location: Point = getPosition(component)
    mouse.press(location.x, location.y)
    // Allow events to propagate.
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
  }

  private fun FakeUi.mouseRelease() {
    mouse.release()
    // Allow events to propagate.
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
  }

  private fun FakeUi.mouseClickOn(component: Component) {
    val location: Point = getPosition(component)
    mouse.click(location.x, location.y)
    // Allow events to propagate.
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
  }

  private fun getStreamScreenshotCallAndWaitForFrame(fakeUi: FakeUi, panel: EmulatorToolWindowPanel, frameNumber: Int): GrpcCallRecord {
    val call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/streamScreenshot")
    panel.waitForFrame(fakeUi, frameNumber, 2, TimeUnit.SECONDS)
    return call
  }

  private fun createWindowPanelForPhone(): EmulatorToolWindowPanel {
    val avdFolder = FakeEmulator.createPhoneAvd(emulatorRule.root)
    return createWindowPanel(avdFolder)
  }

  private fun createWindowPanel(avdFolder: Path): EmulatorToolWindowPanel {
    emulator = emulatorRule.newEmulator(avdFolder)
    emulator.start()
    val catalog = RunningEmulatorCatalog.getInstance()
    val emulators = catalog.updateNow().get()
    assertThat(emulators).hasSize(1)
    val emulatorController = emulators.first()
    val panel = EmulatorToolWindowPanel(projectRule.project, emulatorController)
    Disposer.register(testRootDisposable) {
      if (panel.primaryEmulatorView != null) {
        panel.destroyContent()
      }
      emulator.stop()
    }
    panel.zoomToolbarVisible = true
    waitForCondition(5, TimeUnit.SECONDS) { emulatorController.connectionState == EmulatorController.ConnectionState.CONNECTED }
    return panel
  }

  @Throws(TimeoutException::class)
  private fun EmulatorToolWindowPanel.waitForFrame(fakeUi: FakeUi, frame: Int, timeout: Long, unit: TimeUnit) {
    waitForCondition(timeout, unit) { renderAndGetFrameNumber(fakeUi, primaryEmulatorView!!) >= frame }
  }

  @Throws(TimeoutException::class)
  private fun waitForNextFrameInAllDisplays(fakeUi: FakeUi, frameNumbers: IntArray) {
    val displayViews = fakeUi.findAllComponents<EmulatorView>()
    assertThat(displayViews.size).isEqualTo(frameNumbers.size)
    waitForCondition(2, TimeUnit.SECONDS) {
      fakeUi.render()
      for (view in displayViews) {
        if (view.frameNumber <= frameNumbers[view.displayId]) {
          return@waitForCondition false
        }
      }
      for (view in displayViews) {
        frameNumbers[view.displayId] = view.frameNumber
      }
      return@waitForCondition true
    }
  }

  private fun renderAndGetFrameNumber(fakeUi: FakeUi, emulatorView: EmulatorView): Int {
    fakeUi.render() // The frame number may get updated as a result of rendering.
    return emulatorView.frameNumber
  }

  private val EmulatorToolWindowPanel.primaryEmulatorView
    get() = getData(EMULATOR_VIEW_KEY.name) as EmulatorView?

  private fun assertAppearance(ui: FakeUi,
                               goldenImageName: String,
                               maxPercentDifferentLinux: Double = 0.0,
                               maxPercentDifferentMac: Double = 0.0,
                               maxPercentDifferentWindows: Double = 0.0) {
    ui.layoutAndDispatchEvents()
    ui.updateToolbars()
    val image = ui.render()
    val scaledImage = ImageUtils.scale(image, 0.5)
    val maxPercentDifferent = when {
      SystemInfo.isMac -> maxPercentDifferentMac
      SystemInfo.isWindows -> maxPercentDifferentWindows
      else -> maxPercentDifferentLinux
    }
    ImageDiffUtil.assertImageSimilar(getGoldenFile(goldenImageName), scaledImage, maxPercentDifferent)
  }

  private fun getGoldenFile(name: String): Path {
    return TestUtils.resolveWorkspacePathUnchecked("$TEST_DATA_PATH/golden/${name}.png")
  }
}

private const val TEST_DATA_PATH = "tools/adt/idea/streaming/testData/EmulatorToolWindowPanelTest"
