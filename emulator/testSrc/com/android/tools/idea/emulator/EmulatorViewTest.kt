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

import com.android.emulator.control.FoldedDisplay
import com.android.emulator.control.ImageFormat
import com.android.testutils.ImageDiffUtil
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.mock
import com.android.testutils.TestUtils.getWorkspaceRoot
import com.android.tools.adtui.actions.ZoomType
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.replaceKeyboardFocusManager
import com.android.tools.idea.concurrency.waitForCondition
import com.android.tools.idea.emulator.FakeEmulator.GrpcCallRecord
import com.android.tools.idea.io.IdeFileUtils
import com.android.tools.idea.protobuf.TextFormat.shortDebugString
import com.android.tools.idea.testing.mockStatic
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.ClipboardSynchronizer
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.registerComponentInstance
import com.intellij.util.SystemProperties
import com.intellij.util.ui.UIUtil.dispatchAllInvocationEvents
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.Mockito.`when`
import org.mockito.Mockito.atLeast
import org.mockito.Mockito.verify
import java.awt.Component
import java.awt.Dimension
import java.awt.KeyboardFocusManager
import java.awt.MouseInfo
import java.awt.Point
import java.awt.PointerInfo
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.event.FocusEvent
import java.awt.event.KeyEvent
import java.awt.event.KeyEvent.KEY_PRESSED
import java.awt.event.KeyEvent.VK_A
import java.awt.event.KeyEvent.VK_BACK_SPACE
import java.awt.event.KeyEvent.VK_CONTROL
import java.awt.event.KeyEvent.VK_ENTER
import java.awt.event.KeyEvent.VK_PAGE_DOWN
import java.awt.event.KeyEvent.VK_SHIFT
import java.awt.event.KeyEvent.VK_TAB
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.regex.Pattern
import javax.swing.JScrollPane
import kotlin.streams.toList

/**
 * Tests for [EmulatorView] and some of the emulator toolbar actions.
 */
@RunsInEdt
class EmulatorViewTest {
  private val emulatorViewRule = EmulatorViewRule()
  @get:Rule
  val ruleChain: RuleChain = RuleChain.outerRule(emulatorViewRule).around(EdtRule())
  private val filesOpened = mutableListOf<VirtualFile>()

  private val testRootDisposable
    get() = emulatorViewRule.testRootDisposable

  @Before
  fun setUp() {
    val fileEditorManager = mock<FileEditorManagerEx>()
    `when`(fileEditorManager.openFile(any(), anyBoolean())).thenAnswer { invocation ->
      filesOpened.add(invocation.getArgument(0))
      return@thenAnswer emptyArray<FileEditor>()
    }
    `when`(fileEditorManager.selectedEditors).thenReturn(FileEditor.EMPTY_ARRAY)
    `when`(fileEditorManager.openFiles).thenReturn(VirtualFile.EMPTY_ARRAY)
    `when`(fileEditorManager.allEditors).thenReturn(FileEditor.EMPTY_ARRAY)
    emulatorViewRule.project.registerComponentInstance(FileEditorManager::class.java, fileEditorManager, testRootDisposable)
  }

  @Test
  fun testEmulatorView() {
    val view = emulatorViewRule.newEmulatorView()
    val emulator = emulatorViewRule.getFakeEmulator(view)

    val container = createScrollPane(view)
    val ui = FakeUi(container, 2.0)

    // Check initial appearance.
    var frameNumber = view.frameNumber
    assertThat(frameNumber).isEqualTo(0)
    container.size = Dimension(200, 300)
    ui.layoutAndDispatchEvents()
    var call = getStreamScreenshotCallAndWaitForFrame(view, ++frameNumber)
    assertThat(shortDebugString(call.request)).isEqualTo("format: RGB888 width: 363 height: 547")
    assertAppearance(ui, "EmulatorView1")
    assertThat(call.completion.isCancelled).isFalse() // The call has not been cancelled.
    assertThat(call.completion.isDone).isFalse() // The call is still ongoing.

    // Check resizing.
    val previousCall = call
    container.size = Dimension(250, 200)
    ui.layoutAndDispatchEvents()
    call = getStreamScreenshotCallAndWaitForFrame(view, ++frameNumber)
    assertThat(shortDebugString(call.request)).isEqualTo("format: RGB888 width: 454 height: 364")
    assertAppearance(ui, "EmulatorView2")
    assertThat(previousCall.completion.isCancelled).isTrue() // The previous call is cancelled.
    assertThat(call.completion.isCancelled).isFalse() // The latest call has not been cancelled.
    assertThat(call.completion.isDone).isFalse() // The latest call is still ongoing.

    // Check zoom.
    val skinHeight = 3245
    assertThat(view.scale).isWithin(1e-4).of(200 * ui.screenScale / skinHeight)
    assertThat(view.canZoomIn()).isTrue()
    assertThat(view.canZoomOut()).isFalse()
    assertThat(view.canZoomToActual()).isTrue()
    assertThat(view.canZoomToFit()).isFalse()

    view.zoom(ZoomType.IN)
    ui.layoutAndDispatchEvents()
    call = getStreamScreenshotCallAndWaitForFrame(view, ++frameNumber)
    assertThat(shortDebugString(call.request)).isEqualTo("format: RGB888 width: 423 height: 740")
    assertThat(view.canZoomIn()).isTrue()
    assertThat(view.canZoomOut()).isTrue()
    assertThat(view.canZoomToActual()).isTrue()
    assertThat(view.canZoomToFit()).isTrue()

    view.zoom(ZoomType.ACTUAL)
    ui.layoutAndDispatchEvents()
    call = getStreamScreenshotCallAndWaitForFrame(view, ++frameNumber)
    assertThat(shortDebugString(call.request)).isEqualTo("format: RGB888 width: 1440 height: 2960")
    assertThat(view.canZoomIn()).isTrue()
    assertThat(view.canZoomOut()).isTrue()
    assertThat(view.canZoomToActual()).isFalse()
    assertThat(view.canZoomToFit()).isTrue()

    view.zoom(ZoomType.OUT)
    ui.layoutAndDispatchEvents()
    call = getStreamScreenshotCallAndWaitForFrame(view, ++frameNumber)
    assertThat(shortDebugString(call.request)).isEqualTo("format: RGB888 width: 720 height: 1481")
    assertThat(view.canZoomIn()).isTrue()
    assertThat(view.canZoomOut()).isTrue()
    assertThat(view.canZoomToActual()).isTrue()
    assertThat(view.canZoomToFit()).isTrue()

    view.zoom(ZoomType.FIT)
    ui.layoutAndDispatchEvents()
    call = getStreamScreenshotCallAndWaitForFrame(view, ++frameNumber)
    assertThat(shortDebugString(call.request)).isEqualTo("format: RGB888 width: 454 height: 364")
    assertThat(view.canZoomIn()).isTrue()
    assertThat(view.canZoomOut()).isFalse()
    assertThat(view.canZoomToActual()).isTrue()
    assertThat(view.canZoomToFit()).isFalse()

    // Check rotation.
    emulatorViewRule.executeAction("android.emulator.rotate.left", view)
    call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/setPhysicalModel")
    assertThat(shortDebugString(call.request)).isEqualTo("target: ROTATION value { data: 0.0 data: 0.0 data: 90.0 }")
    call = getStreamScreenshotCallAndWaitForFrame(view, ++frameNumber)
    assertThat(shortDebugString(call.request)).isEqualTo("format: RGB888 width: 456 height: 363")
    assertAppearance(ui, "EmulatorView3")

    // Check mouse input in landscape orientation.
    ui.mouse.press(10, 153)
    call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendMouse")
    assertThat(shortDebugString(call.request)).isEqualTo("x: 33 y: 58 buttons: 1")

    ui.mouse.dragTo(215, 48)
    call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendMouse")
    assertThat(shortDebugString(call.request)).isEqualTo("x: 1401 y: 2720 buttons: 1")

    ui.mouse.release()
    call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendMouse")
    assertThat(shortDebugString(call.request)).isEqualTo("x: 1401 y: 2720")

    // Check keyboard input.
    ui.keyboard.setFocus(view)
    ui.keyboard.type(VK_A)
    call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendKey")
    assertThat(shortDebugString(call.request)).isEqualTo("""text: "A"""")

    ui.keyboard.pressAndRelease(VK_BACK_SPACE)
    call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendKey")
    assertThat(shortDebugString(call.request)).isEqualTo("""eventType: keypress key: "Backspace"""")

    ui.keyboard.pressAndRelease(VK_ENTER)
    call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendKey")
    assertThat(shortDebugString(call.request)).isEqualTo("""eventType: keypress key: "Enter"""")

    ui.keyboard.pressAndRelease(VK_TAB)
    call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendKey")
    assertThat(shortDebugString(call.request)).isEqualTo("""eventType: keypress key: "Tab"""")

    // Ctrl+Tab should be ignored.
    with(ui.keyboard) {
      press(VK_CONTROL)
      pressAndRelease(VK_TAB)
      release(VK_CONTROL)
    }

    ui.keyboard.pressAndRelease(VK_PAGE_DOWN)
    call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendKey")
    assertThat(shortDebugString(call.request)).isEqualTo("""eventType: keypress key: "PageDown"""")

    val mockFocusManager: KeyboardFocusManager = mock()
    `when`(mockFocusManager.redispatchEvent(any(Component::class.java), any(KeyEvent::class.java))).thenCallRealMethod()
    replaceKeyboardFocusManager(mockFocusManager, testRootDisposable)
    // Shift+Tab should trigger a forward local focus traversal.
    with(ui.keyboard) {
      setFocus(view)
      press(VK_SHIFT)
      pressAndRelease(VK_TAB)
      release(VK_SHIFT)
    }
    val arg1 = ArgumentCaptor.forClass(EmulatorView::class.java)
    val arg2 = ArgumentCaptor.forClass(KeyEvent::class.java)
    verify(mockFocusManager, atLeast(1)).processKeyEvent(arg1.capture(), arg2.capture())
    val tabEvent = arg2.allValues.firstOrNull { it.id == KEY_PRESSED && it.keyCode == VK_TAB && it.modifiersEx == 0 }
    assertThat(tabEvent).isNotNull()

    // Check clockwise rotation.
    emulatorViewRule.executeAction("android.emulator.rotate.right", view)
    call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/setPhysicalModel")
    assertThat(shortDebugString(call.request)).isEqualTo("target: ROTATION value { data: 0.0 data: 0.0 data: 0.0 }")
    call = getStreamScreenshotCallAndWaitForFrame(view, ++frameNumber)
    assertThat(shortDebugString(call.request)).isEqualTo("format: RGB888 width: 454 height: 364")
    assertAppearance(ui, "EmulatorView2")

    // Check mouse input in portrait orientation.
    ui.mouse.press(82, 7)
    call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendMouse")
    assertThat(shortDebugString(call.request)).isEqualTo("x: 33 y: 41 buttons: 1")
    ui.mouse.release()
    call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendMouse")
    assertThat(shortDebugString(call.request)).isEqualTo("x: 33 y: 41")

    // Mouse events outside of the display image should be ignored.
    ui.mouse.press(50, 7)
    ui.mouse.release()

    // Check hiding the device frame.
    view.deviceFrameVisible = false
    call = getStreamScreenshotCallAndWaitForFrame(view, ++frameNumber)
    assertThat(shortDebugString(call.request)).isEqualTo("format: RGB888 width: 500 height: 400")
    assertAppearance(ui, "EmulatorView4")

    // Check clipboard synchronization.
    val content = StringSelection("host clipboard")
    ClipboardSynchronizer.getInstance().setContent(content, content)
    val event = FocusEvent(view, FocusEvent.FOCUS_GAINED, false, null)
    for (listener in view.focusListeners) {
      listener.focusGained(event)
    }
    call = emulator.getNextGrpcCall(3, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/setClipboard")
    assertThat(shortDebugString(call.request)).isEqualTo("""text: "host clipboard"""")
    call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/streamClipboard")
    call.waitForResponse(2, TimeUnit.SECONDS)
    emulator.clipboard = "device clipboard"
    call.waitForResponse(2, TimeUnit.SECONDS)
    waitForCondition(2, TimeUnit.SECONDS) { ClipboardSynchronizer.getInstance().getData(DataFlavor.stringFlavor) == "device clipboard" }
  }

  /** Checks a large container size resulting in a scale greater than 1:1. */
  @Test
  fun testLargeScale() {
    val view = emulatorViewRule.newEmulatorView { path -> FakeEmulator.createWatchAvd(path) }

    val container = createScrollPane(view)
    val ui = FakeUi(container, 2.0)

    var frameNumber = view.frameNumber
    assertThat(frameNumber).isEqualTo(0)
    container.size = Dimension(250, 250)
    ui.layoutAndDispatchEvents()
    val call = getStreamScreenshotCallAndWaitForFrame(view, ++frameNumber)
    assertThat(shortDebugString(call.request)).isEqualTo("format: RGB888 width: 320 height: 320")
    assertAppearance(ui, "LargeScale")
    assertThat(call.completion.isCancelled).isFalse() // The latest call has not been cancelled.
    assertThat(call.completion.isDone).isFalse() // The latest call is still ongoing.
  }

  @Test
  fun testFolding() {
    val view = emulatorViewRule.newEmulatorView { path -> FakeEmulator.createFoldableAvd(path) }
    val emulator = emulatorViewRule.getFakeEmulator(view)

    val container = createScrollPane(view)
    val ui = FakeUi(container, 2.0)

    var frameNumber = view.frameNumber
    assertThat(frameNumber).isEqualTo(0)
    container.size = Dimension(200, 200)
    ui.layoutAndDispatchEvents()
    getStreamScreenshotCallAndWaitForFrame(view, ++frameNumber)
    assertAppearance(ui, "Unfolded")

    ui.mouse.press(135, 190)
    var call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendMouse")
    assertThat(shortDebugString(call.request)).isEqualTo("x: 1271 y: 2098 buttons: 1")
    ui.mouse.release()
    call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendMouse")
    assertThat(shortDebugString(call.request)).isEqualTo("x: 1271 y: 2098")

    val config = view.emulator.emulatorConfig
    emulator.setFoldedDisplay(FoldedDisplay.newBuilder().setWidth(config.displayWidth / 2).setHeight(config.displayHeight).build())
    view.waitForFrame(++frameNumber, 2, TimeUnit.SECONDS)
    assertAppearance(ui, "Folded")

    // Check that in a folded state mouse coordinates are interpreted differently.
    ui.mouse.press(135, 190)
    call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendMouse")
    assertThat(shortDebugString(call.request)).isEqualTo("x: 829 y: 2098 buttons: 1")
    ui.mouse.release()
    call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendMouse")
    assertThat(shortDebugString(call.request)).isEqualTo("x: 829 y: 2098")

    // Check EmulatorShowFoldingControlsAction.
    emulatorViewRule.executeAction("android.emulator.folding.controls", view)
    call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.UiController/setUiTheme")
    call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.UiController/showExtendedControls")
    assertThat(shortDebugString(call.request)).isEqualTo("index: VIRT_SENSORS")
  }

  @Test
  fun testMultiTouch() {
    val view = emulatorViewRule.newEmulatorView()
    val emulator = emulatorViewRule.getFakeEmulator(view)

    val container = createScrollPane(view)
    val ui = FakeUi(container, 2.0)

    var frameNumber = view.frameNumber
    assertThat(frameNumber).isEqualTo(0)
    container.size = Dimension(200, 300)
    ui.layoutAndDispatchEvents()
    getStreamScreenshotCallAndWaitForFrame(view, ++frameNumber)

    val mousePosition = Point(150, 75)
    val pointerInfo = mock<PointerInfo>()
    `when`(pointerInfo.location).thenReturn(mousePosition)
    val mouseInfoMock = mockStatic<MouseInfo>(testRootDisposable)
    mouseInfoMock.`when`<Any?> { MouseInfo.getPointerInfo() }.thenReturn(pointerInfo)

    ui.keyboard.setFocus(view)
    ui.mouse.moveTo(mousePosition)
    ui.keyboard.press(VK_CONTROL)
    ui.layoutAndDispatchEvents()
    assertAppearance(ui, "MultiTouch1")

    ui.mouse.press(mousePosition)
    assertAppearance(ui, "MultiTouch2")
    var call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendTouch")
    assertThat(shortDebugString(call.request)).isEqualTo(
        "touches { x: 1272 y: 741 pressure: 1 expiration: NEVER_EXPIRE }" +
        " touches { x: 168 y: 2219 identifier: 1 pressure: 1 expiration: NEVER_EXPIRE }")

    mousePosition.x -= 20
    mousePosition.y += 20
    ui.mouse.dragTo(mousePosition)
    assertAppearance(ui, "MultiTouch3")
    call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendTouch")
    assertThat(shortDebugString(call.request)).isEqualTo(
        "touches { x: 1056 y: 958 pressure: 1 expiration: NEVER_EXPIRE }" +
        " touches { x: 384 y: 2002 identifier: 1 pressure: 1 expiration: NEVER_EXPIRE }")

    ui.mouse.release()
    call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendTouch")
    assertThat(shortDebugString(call.request)).isEqualTo(
        "touches { x: 1056 y: 958 expiration: NEVER_EXPIRE } touches { x: 384 y: 2002 identifier: 1 expiration: NEVER_EXPIRE }")

    ui.keyboard.release(VK_CONTROL)
    assertAppearance(ui, "MultiTouch4")
  }

  @Test
  fun testActions() {
    val view = emulatorViewRule.newEmulatorView()
    val emulator = emulatorViewRule.getFakeEmulator(view)

    // Check EmulatorBackButtonAction.
    emulatorViewRule.executeAction("android.emulator.back.button", view)
    var call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendKey")
    assertThat(shortDebugString(call.request)).isEqualTo("""eventType: keypress key: "GoBack"""")

    // Check EmulatorHomeButtonAction.
    emulatorViewRule.executeAction("android.emulator.home.button", view)
    call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendKey")
    assertThat(shortDebugString(call.request)).isEqualTo("""eventType: keypress key: "Home"""")

    // Check EmulatorOverviewButtonAction.
    emulatorViewRule.executeAction("android.emulator.overview.button", view)
    call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendKey")
    assertThat(shortDebugString(call.request)).isEqualTo("""eventType: keypress key: "AppSwitch"""")

    // Check EmulatorScreenshotAction.
    emulatorViewRule.executeAction("android.emulator.screenshot", view)
    call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/getScreenshot")
    assertThat((call.request as ImageFormat).format).isEqualTo(ImageFormat.ImgFormat.PNG)
    call.waitForCompletion(5, TimeUnit.SECONDS) // Use longer timeout for PNG creation.
    waitForCondition(2, TimeUnit.SECONDS) {
      dispatchAllInvocationEvents()
      val dir = IdeFileUtils.getDesktopDirectory() ?: Paths.get(SystemProperties.getUserHome())
      Files.list(dir).use { stream ->
        stream.filter { Pattern.matches("Screenshot_.*\\.png", it.fileName.toString()) }.toList()
      }.isNotEmpty()
    }
    waitForCondition(2, TimeUnit.SECONDS) { filesOpened.isNotEmpty() }
    assertThat(Pattern.matches("Screenshot_.*\\.png", filesOpened[0].name)).isTrue()
  }

  private fun createScrollPane(view: EmulatorView): JScrollPane {
    @Suppress("UndesirableClassUsage")
    return JScrollPane(view).apply {
      border = null
      isFocusable = true
    }
  }

  private fun getStreamScreenshotCallAndWaitForFrame(view: EmulatorView, frameNumber: Int): GrpcCallRecord {
    val emulator = emulatorViewRule.getFakeEmulator(view)
    val call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/streamScreenshot")
    view.waitForFrame(frameNumber, 2, TimeUnit.SECONDS)
    return call
  }

  @Throws(TimeoutException::class)
  private fun EmulatorView.waitForFrame(frame: Int, timeout: Long, unit: TimeUnit) {
    waitForCondition(timeout, unit) { frameNumber >= frame }
  }

  private fun assertAppearance(ui: FakeUi, goldenImageName: String) {
    val image = ui.render()
    ImageDiffUtil.assertImageSimilar(getGoldenFile(goldenImageName), image, 0.0)
  }

  private fun getGoldenFile(name: String): Path {
    return getWorkspaceRoot().resolve("${GOLDEN_FILE_PATH}/${name}.png")
  }
}

private const val GOLDEN_FILE_PATH = "tools/adt/idea/emulator/testData/EmulatorViewTest/golden"
