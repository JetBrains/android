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

import com.android.emulator.control.ImageFormat
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.mock
import com.android.testutils.TestUtils
import com.android.tools.adtui.ZOOMABLE_KEY
import com.android.tools.adtui.actions.ZoomType
import com.android.tools.adtui.imagediff.ImageDiffUtil
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.concurrency.waitForCondition
import com.android.tools.idea.emulator.FakeEmulator.GrpcCallRecord
import com.android.tools.idea.emulator.RuntimeConfigurationOverrider.getRuntimeConfiguration
import com.android.tools.idea.protobuf.TextFormat.shortDebugString
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.ClipboardSynchronizer
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.registerComponentInstance
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
import java.awt.Dimension
import java.awt.KeyboardFocusManager
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.event.FocusEvent
import java.awt.event.KeyEvent
import java.awt.event.KeyEvent.KEY_PRESSED
import java.io.File
import java.nio.file.Files
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
  private val projectRule = AndroidProjectRule.inMemory()
  private val emulatorRule = FakeEmulatorRule()
  private var nullableEmulator: FakeEmulator? = null
  private val filesOpened = mutableListOf<VirtualFile>()
  @get:Rule
  val ruleChain: RuleChain = RuleChain.outerRule(projectRule).around(emulatorRule).around(EdtRule())

  private var emulator: FakeEmulator
    get() = nullableEmulator ?: throw IllegalStateException()
    set(value) { nullableEmulator = value }

  private val testRootDisposable
    get() = projectRule.fixture.testRootDisposable

  @Before
  fun setUp() {
    val fileEditorManager = mock<FileEditorManagerEx>()
    `when`(fileEditorManager.openFile(any(), anyBoolean())).thenAnswer { invocation ->
      filesOpened.add(invocation.getArgument(0))
      return@thenAnswer emptyArray<FileEditor>()
    }
    `when`(fileEditorManager.openFiles).thenReturn(VirtualFile.EMPTY_ARRAY)
    projectRule.project.registerComponentInstance(FileEditorManager::class.java, fileEditorManager, testRootDisposable)
  }

  @Test
  fun testEmulatorView() {
    val view = createEmulatorView()
    @Suppress("UndesirableClassUsage")
    val container = JScrollPane(view).apply { border = null }
    container.isFocusable = true
    val ui = FakeUi(container, 2.0)

    // Check initial appearance.
    var frameNumber = view.frameNumber
    assertThat(frameNumber).isEqualTo(0)
    container.size = Dimension(200, 300)
    ui.layoutAndDispatchEvents()
    var call = getStreamScreenshotCallAndWaitForFrame(view, ++frameNumber)
    if (call.completion.isCancelled) {
      // Due to timing of connection and resizing events there could be two streamScreenshot calls
      // instead of one. The second call cancels the first one.
      call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
      assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/streamScreenshot")
      dispatchAllInvocationEvents()
      frameNumber = view.frameNumber
    }
    assertThat(shortDebugString(call.request)).isEqualTo("format: RGB888 width: 363 height: 547")
    assertAppearance(ui, "image1")
    assertThat(call.completion.isCancelled).isFalse() // The call has not been cancelled.
    assertThat(call.completion.isDone).isFalse() // The call is still ongoing.

    // Check resizing.
    val previousCall = call
    container.size = Dimension(250, 200)
    ui.layoutAndDispatchEvents()
    call = getStreamScreenshotCallAndWaitForFrame(view, ++frameNumber)
    assertThat(shortDebugString(call.request)).isEqualTo("format: RGB888 width: 454 height: 364")
    assertAppearance(ui, "image2")
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
    executeAction("android.emulator.rotate.left", view)
    call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/setPhysicalModel")
    assertThat(shortDebugString(call.request)).isEqualTo("target: ROTATION value { data: 0.0 data: 0.0 data: 90.0 }")
    call = getStreamScreenshotCallAndWaitForFrame(view, ++frameNumber)
    assertThat(shortDebugString(call.request)).isEqualTo("format: RGB888 width: 456 height: 363")
    assertAppearance(ui, "image3")

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
    ui.keyboard.type(KeyEvent.VK_A)
    call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendKey")
    assertThat(shortDebugString(call.request)).isEqualTo("""text: "A"""")

    ui.keyboard.pressAndRelease(KeyEvent.VK_BACK_SPACE)
    call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendKey")
    assertThat(shortDebugString(call.request)).isEqualTo("""eventType: keypress key: "Backspace"""")

    ui.keyboard.pressAndRelease(KeyEvent.VK_ENTER)
    call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendKey")
    assertThat(shortDebugString(call.request)).isEqualTo("""eventType: keypress key: "Enter"""")

    ui.keyboard.pressAndRelease(KeyEvent.VK_TAB)
    call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendKey")
    assertThat(shortDebugString(call.request)).isEqualTo("""eventType: keypress key: "Tab"""")

    // Ctrl+Tab should be ignored.
    with(ui.keyboard) {
      press(KeyEvent.VK_CONTROL)
      pressAndRelease(KeyEvent.VK_TAB)
      release(KeyEvent.VK_CONTROL)
    }

    ui.keyboard.pressAndRelease(KeyEvent.VK_PAGE_DOWN)
    call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendKey")
    assertThat(shortDebugString(call.request)).isEqualTo("""eventType: keypress key: "PageDown"""")

    val mockFocusManager: KeyboardFocusManager = mock()
    KeyboardFocusManager.setCurrentKeyboardFocusManager(mockFocusManager)
    // Shift+Tab should trigger a forward local focus traversal.
    with(ui.keyboard) {
      setFocus(view)
      press(KeyEvent.VK_SHIFT)
      pressAndRelease(KeyEvent.VK_TAB)
      release(KeyEvent.VK_SHIFT)
    }
    val arg1 = ArgumentCaptor.forClass(EmulatorView::class.java)
    val arg2 = ArgumentCaptor.forClass(KeyEvent::class.java)
    verify(mockFocusManager, atLeast(1)).processKeyEvent(arg1.capture(), arg2.capture())
    val tabEvent = arg2.allValues.firstOrNull { it.id == KEY_PRESSED && it.keyCode == KeyEvent.VK_TAB && it.modifiersEx == 0 }
    assertThat(tabEvent).isNotNull()

    // Check clockwise rotation.
    executeAction("android.emulator.rotate.right", view)
    call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/setPhysicalModel")
    assertThat(shortDebugString(call.request)).isEqualTo("target: ROTATION value { data: 0.0 data: 0.0 data: 0.0 }")
    call = getStreamScreenshotCallAndWaitForFrame(view, ++frameNumber)
    assertThat(shortDebugString(call.request)).isEqualTo("format: RGB888 width: 454 height: 364")
    assertAppearance(ui, "image2")

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
    assertAppearance(ui, "image4")

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

  @Test
  fun testActions() {
    val view = createEmulatorView()

    // Check EmulatorBackButtonAction.
    executeAction("android.emulator.back.button", view)
    var call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendKey")
    assertThat(shortDebugString(call.request)).isEqualTo("""eventType: keypress key: "GoBack"""")

    // Check EmulatorHomeButtonAction.
    executeAction("android.emulator.home.button", view)
    call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendKey")
    assertThat(shortDebugString(call.request)).isEqualTo("""eventType: keypress key: "Home"""")

    // Check EmulatorOverviewButtonAction.
    executeAction("android.emulator.overview.button", view)
    call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendKey")
    assertThat(shortDebugString(call.request)).isEqualTo("""eventType: keypress key: "AppSwitch"""")

    // Check EmulatorScreenshotAction.
    executeAction("android.emulator.screenshot", view)
    call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/getScreenshot")
    assertThat((call.request as ImageFormat).format).isEqualTo(ImageFormat.ImgFormat.PNG)
    call.waitForCompletion(5, TimeUnit.SECONDS) // Use longer timeout for PNG creation.
    waitForCondition(2, TimeUnit.SECONDS) {
      dispatchAllInvocationEvents()
      Files.list(getRuntimeConfiguration().getDesktopOrUserHomeDirectory()).use {
        it.filter { Pattern.matches("Screenshot_.*\\.png", it.fileName.toString()) }.toList()
      }.isNotEmpty()
    }
    waitForCondition(2, TimeUnit.SECONDS) { filesOpened.isNotEmpty() }
    assertThat(Pattern.matches("Screenshot_.*\\.png", filesOpened[0].name)).isTrue()
  }

  private fun getStreamScreenshotCallAndWaitForFrame(view: EmulatorView, frameNumber: Int): GrpcCallRecord {
    val call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/streamScreenshot")
    view.waitForFrame(frameNumber, 2, TimeUnit.SECONDS)
    return call
  }

  private fun executeAction(actionId: String, emulatorView: EmulatorView) {
    val actionManager = ActionManager.getInstance()
    val action = actionManager.getAction(actionId)
    val event = AnActionEvent(null, TestContext(emulatorView), ActionPlaces.UNKNOWN, Presentation(), actionManager, 0)
    action.actionPerformed(event)
  }

  private fun createEmulatorView(): EmulatorView {
    val catalog = RunningEmulatorCatalog.getInstance()
    val tempFolder = emulatorRule.root.toPath()
    emulator = emulatorRule.newEmulator(FakeEmulator.createPhoneAvd(tempFolder), 8554)
    emulator.start()
    val emulators = catalog.updateNow().get()
    assertThat(emulators).hasSize(1)
    val emulatorController = emulators.first()
    val view = EmulatorView(emulatorController, testRootDisposable, true)
    waitForCondition(5, TimeUnit.SECONDS) { emulatorController.connectionState == EmulatorController.ConnectionState.CONNECTED }
    emulator.getNextGrpcCall(2, TimeUnit.SECONDS) // Skip the initial "getVmState" call.
    return view
  }

  @Throws(TimeoutException::class)
  private fun EmulatorView.waitForFrame(frame: Int, timeout: Long, unit: TimeUnit) {
    waitForCondition(timeout, unit) { frameNumber >= frame }
  }

  private fun assertAppearance(ui: FakeUi, goldenImageName: String) {
    val image = ui.render()
    ImageDiffUtil.assertImageSimilar(getGoldenFile(goldenImageName), image, 0.0)
  }

  private fun getGoldenFile(name: String): File {
    return TestUtils.getWorkspaceRoot().toPath().resolve("${GOLDEN_FILE_PATH}/${name}.png").toFile()
  }

  private inner class TestContext(private val emulatorView: EmulatorView) : DataContext {

    override fun getData(dataId: String): Any? {
      return when (dataId) {
        EMULATOR_CONTROLLER_KEY.name -> emulatorView.emulator
        EMULATOR_VIEW_KEY.name, ZOOMABLE_KEY.name -> emulatorView
        CommonDataKeys.PROJECT.name -> projectRule.project
        else -> null
      }
    }
  }
}

private const val GOLDEN_FILE_PATH = "tools/adt/idea/emulator/testData/EmulatorViewTest/golden"
