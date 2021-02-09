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
package com.android.tools.idea.emulator

import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.IDevice
import com.android.sdklib.AndroidVersion
import com.android.testutils.ImageDiffUtil
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.eq
import com.android.testutils.MockitoKt.mock
import com.android.testutils.TestUtils.getWorkspaceRoot
import com.android.tools.adtui.actions.ZoomType
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.HeadlessRootPaneContainer
import com.android.tools.adtui.swing.IconLoaderRule
import com.android.tools.adtui.swing.replaceKeyboardFocusManager
import com.android.tools.adtui.swing.setPortableUiFont
import com.android.tools.idea.adb.AdbService
import com.android.tools.idea.concurrency.waitForCondition
import com.android.tools.idea.emulator.FakeEmulator.GrpcCallRecord
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.protobuf.TextFormat.shortDebugString
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.flags.override
import com.android.utils.FlightRecorder
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.Futures.immediateFuture
import com.intellij.ide.dnd.DnDEvent
import com.intellij.ide.dnd.DnDManager
import com.intellij.ide.dnd.DnDTarget
import com.intellij.ide.dnd.TransferableWrapper
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.registerServiceInstance
import com.intellij.testFramework.replaceService
import com.intellij.ui.EditorNotificationPanel
import org.jetbrains.android.sdk.AndroidSdkUtils.ADB_PATH_PROPERTY
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.ArgumentCaptor
import org.mockito.MockedStatic
import org.mockito.Mockito.`when`
import org.mockito.Mockito.anyLong
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.verify
import java.awt.Component
import java.awt.Dimension
import java.awt.KeyboardFocusManager
import java.awt.MouseInfo
import java.awt.Point
import java.awt.PointerInfo
import java.awt.datatransfer.DataFlavor
import java.awt.event.FocusEvent
import java.awt.event.KeyEvent.VK_DOWN
import java.awt.event.KeyEvent.VK_LEFT
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
  val ruleChain: RuleChain = RuleChain.outerRule(projectRule).around(emulatorRule).around(EdtRule())

  private var nullableEmulator: FakeEmulator? = null

  private var emulator: FakeEmulator
    get() = nullableEmulator ?: throw IllegalStateException()
    set(value) { nullableEmulator = value }

  private val testRootDisposable
    get() = projectRule.fixture.testRootDisposable

  @Before
  fun setUp() {
    setPortableUiFont()
    // Necessary to properly update button states.
    installHeadlessTestDataManager(projectRule.project, testRootDisposable)
  }

  @Test
  fun testEmulatorToolWindowPanel() {
    val panel = createWindowPanel()
    val ui = FakeUi(panel)

    assertThat(panel.emulatorView).isNull()

    panel.createContent(true)
    val emulatorView = panel.emulatorView ?: throw AssertionError()

    // Check appearance.
    panel.zoomToolbarVisible = true
    var frameNumber = emulatorView.frameNumber
    assertThat(frameNumber).isEqualTo(0)
    panel.size = Dimension(400, 600)
    ui.layoutAndDispatchEvents()
    val streamScreenshotCall = getStreamScreenshotCallAndWaitForFrame(panel, ++frameNumber)
    assertThat(shortDebugString(streamScreenshotCall.request)).isEqualTo("format: RGB888 width: 363 height: 520")
    ui.updateToolbars()
    assertAppearance(ui, "image1")

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

    assertThat(streamScreenshotCall.completion.isCancelled).isFalse()

    panel.destroyContent()
    assertThat(panel.emulatorView).isNull()
    waitForCondition(2, TimeUnit.SECONDS) { streamScreenshotCall.completion.isCancelled }
  }

  @Test
  fun testZoom() {
    val panel = createWindowPanel()
    val ui = FakeUi(panel)

    assertThat(panel.emulatorView).isNull()

    panel.createContent(true)
    var emulatorView = panel.emulatorView ?: throw AssertionError()

    panel.zoomToolbarVisible = true
    var frameNumber = emulatorView.frameNumber
    assertThat(frameNumber).isEqualTo(0)
    panel.size = Dimension(400, 600)
    ui.layoutAndDispatchEvents()
    val streamScreenshotCall = getStreamScreenshotCallAndWaitForFrame(panel, ++frameNumber)
    assertThat(shortDebugString(streamScreenshotCall.request)).isEqualTo("format: RGB888 width: 363 height: 520")
    ui.updateToolbars()

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
    panel.destroyContent()
    panel.createContent(true)
    emulatorView = panel.emulatorView ?: throw AssertionError()
    ui.layoutAndDispatchEvents()

    // Check that zoom level and scroll position are restored.
    assertThat(emulatorView.scale).isWithin(0.0001).of(0.25)
    assertThat(viewport.viewSize).isEqualTo(Dimension(400, 811))
    assertThat(viewport.viewPosition).isEqualTo(scrollPosition)

    panel.destroyContent()
  }

  @Test
  fun testVirtualSceneCamera() {
    StudioFlags.EMBEDDED_EMULATOR_VIRTUAL_SCENE_CAMERA.override(true, testRootDisposable)
    val panel = createWindowPanel()
    val ui = FakeUi(panel)

    assertThat(panel.emulatorView).isNull()

    panel.createContent(true)
    val emulatorView = panel.emulatorView ?: throw AssertionError()
    panel.size = Dimension(400, 600)
    ui.layoutAndDispatchEvents()

    val container = HeadlessRootPaneContainer(panel)
    val glassPane = container.glassPane

    val initialMousePosition = Point(glassPane.x + glassPane.width / 2, glassPane.y + glassPane.height / 2)
    val pointerInfo = mock<PointerInfo>()
    `when`(pointerInfo.location).thenReturn(initialMousePosition)
    val mouseInfoMock = mockStatic<MouseInfo>(testRootDisposable)
    mouseInfoMock.`when`<Any?> { MouseInfo.getPointerInfo() }.thenReturn(pointerInfo)

    ui.keyboard.setFocus(emulatorView)
    val mockFocusManager = mock<KeyboardFocusManager>()
    `when`(mockFocusManager.focusOwner).thenReturn(emulatorView)
    `when`(mockFocusManager.redispatchEvent(any(), any())).thenCallRealMethod()
    replaceKeyboardFocusManager(mockFocusManager, testRootDisposable)

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

    // Drain the gRPC call log.
    for (i in 1..5) {
      if (emulator.getNextGrpcCall(2, TimeUnit.SECONDS).methodName.endsWith("streamClipboard")) {
        break
      }
    }

    // Check camera movement.
    for (c in "WASQDE") {
      ui.keyboard.press(c.toInt())
      var call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
      assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendKey")
      assertThat(shortDebugString(call.request)).isEqualTo("""key: "Key$c"""")
      ui.keyboard.release(c.toInt())
      call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
      assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendKey")
      assertThat(shortDebugString(call.request)).isEqualTo("""eventType: keyup key: "Key$c"""")
    }

    // Check camera rotation.
    val x = initialMousePosition.x + 5
    val y = initialMousePosition.y + 10
    val event = MouseEvent(glassPane, MOUSE_MOVED, System.currentTimeMillis(), ui.keyboard.toModifiersCode(), x, y, x, y, 0, false, 0)
    glassPane.dispatch(event)
    var call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendMouse")
    assertThat(shortDebugString(call.request)).isEqualTo("x: 23 y: 45")

    ui.keyboard.pressAndRelease(VK_LEFT)
    call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendMouse")
    assertThat(shortDebugString(call.request)).isEqualTo("x: -2 y: 45")

    ui.keyboard.pressAndRelease(VK_RIGHT)
    call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendMouse")
    assertThat(shortDebugString(call.request)).isEqualTo("x: 23 y: 45")

    ui.keyboard.pressAndRelease(VK_UP)
    call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendMouse")
    assertThat(shortDebugString(call.request)).isEqualTo("x: 23 y: 20")

    ui.keyboard.pressAndRelease(VK_DOWN)
    call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendMouse")
    assertThat(shortDebugString(call.request)).isEqualTo("x: 23 y: 45")

    // Check that the notification changes when Shift is released.
    ui.keyboard.release(VK_SHIFT)
    assertThat(ui.findComponent<EditorNotificationPanel>()?.text).isEqualTo("Hold Shift to control camera")

    // Deactivate the camera and check that the notification is removed.
    emulator.virtualSceneCameraActive = false
    waitForCondition(2, TimeUnit.SECONDS) { ui.findComponent<EditorNotificationPanel>() == null }

    panel.destroyContent()
  }

  @Test
  fun testDnD() {
    FlightRecorder.initialize(100)
    val adb = getWorkspaceRoot().resolve("$TEST_DATA_PATH/fake-adb")
    val savedAdbPath = System.getProperty(ADB_PATH_PROPERTY)
    if (savedAdbPath != null) {
      Disposer.register(testRootDisposable) { System.setProperty(ADB_PATH_PROPERTY, savedAdbPath) }
    }
    System.setProperty(ADB_PATH_PROPERTY, adb.toString())

    var nullableTarget: DnDTarget? = null
    val mockDnDManager = mock<DnDManager>()
    `when`(mockDnDManager.registerTarget(any(), any())).then {
      it.apply { nullableTarget = getArgument<DnDTarget>(0) }
    }
    ApplicationManager.getApplication().replaceService(DnDManager::class.java, mockDnDManager, testRootDisposable)

    val panel = createWindowPanel()
    panel.createContent(false)

    val target = nullableTarget as DnDTarget
    val device = mock<IDevice>()
    FlightRecorder.log("EmulatorToolWindowPanelTest.testDnD: device: $device")
    `when`(device.isEmulator).thenReturn(true)
    `when`(device.serialNumber).thenReturn("emulator-${emulator.serialPort}")
    `when`(device.version).thenReturn(AndroidVersion(AndroidVersion.MIN_RECOMMENDED_API))
    val mockAdb = mock<AndroidDebugBridge>()
    `when`(mockAdb.devices).thenReturn(arrayOf(device))
    val mockAdbService = mock<AdbService>()
    `when`(mockAdbService.getDebugBridge(any())).thenReturn(immediateFuture(mockAdb))
    ApplicationManager.getApplication().registerServiceInstance(AdbService::class.java, mockAdbService)

    // Check APK installation.
    val apkFileList = listOf(File("/some_folder/myapp.apk"))
    val dnDEvent1 = createDnDEvent(apkFileList)

    // Simulate drag.
    target.update(dnDEvent1)

    verify(dnDEvent1).isDropPossible = true

    val installPackagesCalled = CountDownLatch(1)
    val installOptions = listOf("-t", "--user", "current", "--full", "--dont-kill")
    val fileListCaptor = ArgumentCaptor.forClass(apkFileList.javaClass)
    `when`(device.installPackages(fileListCaptor.capture(), eq(true), eq(installOptions), anyLong(), any())).then {
      FlightRecorder.log("EmulatorToolWindowPanelTest.testDnD: app installed")
      installPackagesCalled.countDown()
    }

    // Simulate drop.
    target.drop(dnDEvent1)

    assertThat(installPackagesCalled.await(2, TimeUnit.SECONDS)).isTrue()
    assertThat(fileListCaptor.value).isEqualTo(apkFileList)

    // Check file copying.
    val fileList = listOf(File("/some_folder/file1.txt"), File("/some_folder/file2.jpg"))
    val dnDEvent2 = createDnDEvent(fileList)

    // Simulate drag.
    target.update(dnDEvent2)

    verify(dnDEvent2).isDropPossible = true

    val allFilesPushed = CountDownLatch(fileList.size)
    val firstArgCaptor = ArgumentCaptor.forClass(String::class.java)
    val secondArgCaptor = ArgumentCaptor.forClass(String::class.java)
    `when`(device.pushFile(firstArgCaptor.capture(), secondArgCaptor.capture())).then {
      FlightRecorder.log("EmulatorToolWindowPanelTest.testDnD: file pushed")
      allFilesPushed.countDown()
    }

    // Simulate drop.
    target.drop(dnDEvent2)

    try {
      assertThat(allFilesPushed.await(5, TimeUnit.SECONDS)).isTrue()
      assertThat(firstArgCaptor.allValues).isEqualTo(fileList.map(File::getAbsolutePath).toList())
      assertThat(secondArgCaptor.allValues).isEqualTo(fileList.map { "/sdcard/Download/${it.name}" }.toList())
      FlightRecorder.print()
    }
    catch (t: Throwable) {
      FlightRecorder.print()
      throw t
    }

    panel.destroyContent()
  }

  private fun createDnDEvent(files: List<File>): DnDEvent {
    val transferableWrapper = mock<TransferableWrapper>()
    `when`(transferableWrapper.asFileList()).thenReturn(files)
    val dnDEvent = mock<DnDEvent>()
    `when`(dnDEvent.isDataFlavorSupported(DataFlavor.javaFileListFlavor)).thenReturn(true)
    `when`(dnDEvent.attachedObject).thenReturn(transferableWrapper)
    return dnDEvent
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

  private fun getStreamScreenshotCallAndWaitForFrame(panel: EmulatorToolWindowPanel, frameNumber: Int): GrpcCallRecord {
    val call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/streamScreenshot")
    panel.waitForFrame(frameNumber, 2, TimeUnit.SECONDS)
    return call
  }

  private fun createWindowPanel(): EmulatorToolWindowPanel {
    val catalog = RunningEmulatorCatalog.getInstance()
    val tempFolder = emulatorRule.root
    emulator = emulatorRule.newEmulator(FakeEmulator.createPhoneAvd(tempFolder), 8554)
    emulator.start()
    val emulators = catalog.updateNow().get()
    assertThat(emulators).hasSize(1)
    val emulatorController = emulators.first()
    val panel = EmulatorToolWindowPanel(projectRule.project, emulatorController, EmulatorUiState())
    waitForCondition(2, TimeUnit.SECONDS) { emulatorController.connectionState == EmulatorController.ConnectionState.CONNECTED }
    emulator.getNextGrpcCall(2, TimeUnit.SECONDS) // Skip the initial "getStatus" call.
    return panel
  }

  @Throws(TimeoutException::class)
  private fun EmulatorToolWindowPanel.waitForFrame(frame: Int, timeout: Long, unit: TimeUnit) {
    waitForCondition(timeout, unit) { emulatorView!!.frameNumber >= frame }
  }

  private val EmulatorToolWindowPanel.emulatorView
    get() = getData(EMULATOR_VIEW_KEY.name) as EmulatorView?

  private fun assertAppearance(ui: FakeUi, @Suppress("SameParameterValue") goldenImageName: String) {
    val image = ui.render()
    ImageDiffUtil.assertImageSimilar(getGoldenFile(goldenImageName), image, 0.04)
  }

  private fun getGoldenFile(name: String): Path {
    return getWorkspaceRoot().resolve("$TEST_DATA_PATH/golden/${name}.png")
  }
}

private const val TEST_DATA_PATH = "tools/adt/idea/emulator/testData/EmulatorToolWindowPanelTest"

private inline fun <reified T> mockStatic(disposable: Disposable): MockedStatic<T> {
  return mockStatic(T::class.java).also { mock -> Disposer.register(disposable) { mock.close() } }
}
