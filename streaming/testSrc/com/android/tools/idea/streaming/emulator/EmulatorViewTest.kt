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

import com.android.testutils.ImageDiffUtil
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.eq
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.testutils.TestUtils
import com.android.testutils.waitForCondition
import com.android.tools.adtui.actions.ZoomType
import com.android.tools.adtui.swing.FakeKeyboardFocusManager
import com.android.tools.adtui.swing.FakeMouse
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.HeadlessRootPaneContainer
import com.android.tools.adtui.swing.IconLoaderRule
import com.android.tools.adtui.swing.replaceKeyboardFocusManager
import com.android.tools.adtui.ui.NotificationHolderPanel
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.protobuf.TextFormat.shortDebugString
import com.android.tools.idea.streaming.emulator.FakeEmulator.GrpcCallRecord
import com.android.tools.idea.streaming.executeStreamingAction
import com.android.tools.idea.testing.flags.override
import com.android.tools.idea.testing.mockStatic
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.IdeActions.ACTION_COPY
import com.intellij.openapi.actionSystem.IdeActions.ACTION_CUT
import com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN_WITH_SELECTION
import com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT_WITH_SELECTION
import com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_MOVE_CARET_PAGE_DOWN_WITH_SELECTION
import com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_MOVE_CARET_PAGE_UP_WITH_SELECTION
import com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT_WITH_SELECTION
import com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_MOVE_CARET_UP_WITH_SELECTION
import com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_MOVE_LINE_END_WITH_SELECTION
import com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_MOVE_LINE_START_WITH_SELECTION
import com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_NEXT_WORD
import com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_NEXT_WORD_WITH_SELECTION
import com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_PREVIOUS_WORD
import com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_PREVIOUS_WORD_WITH_SELECTION
import com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_TEXT_END
import com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_TEXT_END_WITH_SELECTION
import com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_TEXT_START
import com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_TEXT_START_WITH_SELECTION
import com.intellij.openapi.actionSystem.IdeActions.ACTION_PASTE
import com.intellij.openapi.actionSystem.IdeActions.ACTION_REDO
import com.intellij.openapi.actionSystem.IdeActions.ACTION_SELECT_ALL
import com.intellij.openapi.actionSystem.IdeActions.ACTION_UNDO
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.ui.EditorNotificationPanel
import com.intellij.util.ui.UIUtil
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.mockito.MockedStatic
import org.mockito.Mockito.atLeast
import org.mockito.Mockito.verify
import java.awt.Component
import java.awt.DefaultKeyboardFocusManager
import java.awt.Dimension
import java.awt.KeyboardFocusManager
import java.awt.MouseInfo
import java.awt.Point
import java.awt.PointerInfo
import java.awt.event.InputEvent.CTRL_DOWN_MASK
import java.awt.event.InputEvent.SHIFT_DOWN_MASK
import java.awt.event.KeyEvent
import java.awt.event.KeyEvent.KEY_PRESSED
import java.awt.event.KeyEvent.KEY_RELEASED
import java.awt.event.KeyEvent.VK_BACK_SPACE
import java.awt.event.KeyEvent.VK_CONTROL
import java.awt.event.KeyEvent.VK_DELETE
import java.awt.event.KeyEvent.VK_DOWN
import java.awt.event.KeyEvent.VK_END
import java.awt.event.KeyEvent.VK_ENTER
import java.awt.event.KeyEvent.VK_ESCAPE
import java.awt.event.KeyEvent.VK_HOME
import java.awt.event.KeyEvent.VK_KP_DOWN
import java.awt.event.KeyEvent.VK_KP_LEFT
import java.awt.event.KeyEvent.VK_KP_RIGHT
import java.awt.event.KeyEvent.VK_KP_UP
import java.awt.event.KeyEvent.VK_LEFT
import java.awt.event.KeyEvent.VK_M
import java.awt.event.KeyEvent.VK_PAGE_DOWN
import java.awt.event.KeyEvent.VK_PAGE_UP
import java.awt.event.KeyEvent.VK_RIGHT
import java.awt.event.KeyEvent.VK_SHIFT
import java.awt.event.KeyEvent.VK_TAB
import java.awt.event.KeyEvent.VK_UP
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.TimeoutException
import javax.swing.JScrollPane

/**
 * Tests for [EmulatorView] and some emulator toolbar actions.
 */
@RunsInEdt
class EmulatorViewTest {

  companion object {
    @JvmField
    @ClassRule
    val iconLoaderRule = IconLoaderRule()
  }

  private val emulatorViewRule = EmulatorViewRule()
  @get:Rule
  val ruleChain = RuleChain(emulatorViewRule, EdtRule())

  private val testRootDisposable
    get() = emulatorViewRule.disposable

  private lateinit var mouseInfo: MockedStatic<MouseInfo>
  private lateinit var focusManager: FakeKeyboardFocusManager

  @Before
  fun setUp() {
    StudioFlags.STREAMING_HARDWARE_INPUT_BUTTON.override(true, testRootDisposable)
    mouseInfo = mockStatic(testRootDisposable)
    mouseInfo.whenever<PointerInfo> { MouseInfo.getPointerInfo() }.thenReturn(mock<PointerInfo>())
    focusManager = FakeKeyboardFocusManager(testRootDisposable)
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
    var call = getStreamScreenshotCallAndWaitForFrame(ui, view, ++frameNumber)
    assertThat(shortDebugString(call.request)).isEqualTo("format: RGB888 width: 363 height: 547")
    assertAppearance(ui, "EmulatorView1")
    assertThat(call.completion.isCancelled).isFalse() // The call has not been cancelled.
    assertThat(call.completion.isDone).isFalse() // The call is still ongoing.

    // Check resizing.
    val previousCall = call
    container.size = Dimension(250, 200)
    ui.layoutAndDispatchEvents()
    call = getStreamScreenshotCallAndWaitForFrame(ui, view, ++frameNumber)
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
    call = getStreamScreenshotCallAndWaitForFrame(ui, view, ++frameNumber)
    assertThat(shortDebugString(call.request)).isEqualTo(
      // Available space is slightly wider on Mac due to a narrower scrollbar.
      if (UIUtil.isRetina()) "format: RGB888 width: 427 height: 740" else "format: RGB888 width: 423 height: 740")
    assertThat(view.canZoomIn()).isTrue()
    assertThat(view.canZoomOut()).isTrue()
    assertThat(view.canZoomToActual()).isTrue()
    assertThat(view.canZoomToFit()).isTrue()

    view.zoom(ZoomType.ACTUAL)
    ui.layoutAndDispatchEvents()
    call = getStreamScreenshotCallAndWaitForFrame(ui, view, ++frameNumber)
    assertThat(shortDebugString(call.request)).isEqualTo("format: RGB888 width: 1440 height: 2960")
    assertThat(view.canZoomIn()).isTrue()
    assertThat(view.canZoomOut()).isTrue()
    assertThat(view.canZoomToActual()).isFalse()
    assertThat(view.canZoomToFit()).isTrue()

    view.zoom(ZoomType.OUT)
    ui.layoutAndDispatchEvents()
    call = getStreamScreenshotCallAndWaitForFrame(ui, view, ++frameNumber)
    assertThat(shortDebugString(call.request)).isEqualTo("format: RGB888 width: 720 height: 1481")
    assertThat(view.canZoomIn()).isTrue()
    assertThat(view.canZoomOut()).isTrue()
    assertThat(view.canZoomToActual()).isTrue()
    assertThat(view.canZoomToFit()).isTrue()

    view.zoom(ZoomType.FIT)
    ui.layoutAndDispatchEvents()
    call = getStreamScreenshotCallAndWaitForFrame(ui, view, ++frameNumber)
    assertThat(shortDebugString(call.request)).isEqualTo("format: RGB888 width: 454 height: 364")
    assertThat(view.canZoomIn()).isTrue()
    assertThat(view.canZoomOut()).isFalse()
    assertThat(view.canZoomToActual()).isTrue()
    assertThat(view.canZoomToFit()).isFalse()

    // Check rotation.
    emulatorViewRule.executeAction("android.device.rotate.left", view)
    call = emulator.getNextGrpcCall(2, SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/setPhysicalModel")
    assertThat(shortDebugString(call.request)).isEqualTo("target: ROTATION value { data: 0.0 data: 0.0 data: 90.0 }")
    call = getStreamScreenshotCallAndWaitForFrame(ui, view, ++frameNumber)
    assertThat(shortDebugString(call.request)).isEqualTo("format: RGB888 width: 456 height: 363")
    assertAppearance(ui, "EmulatorView3")

    // Check mouse input in landscape orientation.
    ui.mouse.press(10, 153)
    call = emulator.getNextGrpcCall(2, SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendMouse")
    assertThat(shortDebugString(call.request)).isEqualTo("x: 35 y: 61 buttons: 1")

    ui.mouse.dragTo(215, 48)
    call = emulator.getNextGrpcCall(2, SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendMouse")
    assertThat(shortDebugString(call.request)).isEqualTo("x: 1404 y: 2723 buttons: 1")

    ui.mouse.release()
    call = emulator.getNextGrpcCall(2, SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendMouse")
    assertThat(shortDebugString(call.request)).isEqualTo("x: 1404 y: 2723")

    // Check clockwise rotation in a zoomed-in state.
    view.zoom(ZoomType.IN)
    ui.layoutAndDispatchEvents()
    call = getStreamScreenshotCallAndWaitForFrame(ui, view, ++frameNumber)
    assertThat(shortDebugString(call.request)).isEqualTo(
      // Available space is slightly wider on Mac due to a narrower scrollbar.
      if (SystemInfo.isMac) "format: RGB888 width: 740 height: 360" else "format: RGB888 width: 740 height: 360")
    assertThat(view.canZoomOut()).isTrue()
    assertThat(view.canZoomToFit()).isTrue()
    emulatorViewRule.executeAction("android.device.rotate.right", view)
    call = emulator.getNextGrpcCall(2, SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/setPhysicalModel")
    assertThat(shortDebugString(call.request)).isEqualTo("target: ROTATION value { data: 0.0 data: 0.0 data: 0.0 }")
    getStreamScreenshotCallAndWaitForFrame(ui, view, ++frameNumber)
    ui.layoutAndDispatchEvents()
    call = getStreamScreenshotCallAndWaitForFrame(ui, view, ++frameNumber)
    assertThat(shortDebugString(call.request)).isEqualTo("format: RGB888 width: 454 height: 364")
    assertThat(view.canZoomOut()).isFalse() // zoom-in mode cancelled by the rotation.
    assertThat(view.canZoomToFit()).isFalse()
    assertAppearance(ui, "EmulatorView2")

    // Check mouse input in portrait orientation.
    ui.mouse.press(82, 7)
    call = emulator.getNextGrpcCall(2, SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendMouse")
    assertThat(shortDebugString(call.request)).isEqualTo("x: 36 y: 44 buttons: 1")
    ui.mouse.release()
    call = emulator.getNextGrpcCall(2, SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendMouse")
    assertThat(shortDebugString(call.request)).isEqualTo("x: 36 y: 44")

    // Mouse events outside the display image should be ignored.
    ui.mouse.press(50, 7)
    ui.mouse.release()

    // Check hiding the device frame.
    view.deviceFrameVisible = false
    call = getStreamScreenshotCallAndWaitForFrame(ui, view, ++frameNumber)
    assertThat(shortDebugString(call.request)).isEqualTo("format: RGB888 width: 500 height: 400")
    assertAppearance(ui, "EmulatorView4")
  }

  @Test
  fun testKeyboardInput() {
    val view = emulatorViewRule.newEmulatorView()
    val emulator = emulatorViewRule.getFakeEmulator(view)

    val container = createScrollPane(view)
    val ui = FakeUi(container, 2.0)

    ui.keyboard.setFocus(view)
    // Printable ASCII characters.
    for (c in ' '..'~') {
      ui.keyboard.type(c.code)
      val call = emulator.getNextGrpcCall(2, SECONDS)
      assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendKey")
      val expectedText = when (c) { '\"', '\'', '\\' -> "\\$c" else -> c.toString() }
      assertThat(shortDebugString(call.request)).isEqualTo("""text: "$expectedText"""")
    }

    val controlCharacterCases = mapOf(
      VK_ENTER to "Enter",
      VK_TAB to "Tab",
      VK_ESCAPE to "Escape",
      VK_BACK_SPACE to "Backspace",
      VK_DELETE to if (SystemInfo.isMac) "Backspace" else "Delete",
    )
    for ((hostKeyStroke, emulatorKeyName) in controlCharacterCases) {
      ui.keyboard.pressAndRelease(hostKeyStroke)
      val pressCall = emulator.getNextGrpcCall(2, SECONDS)
      assertThat(pressCall.methodName).isEqualTo("android.emulation.control.EmulatorController/sendKey")
      assertThat(shortDebugString(pressCall.request)).isEqualTo("""key: "$emulatorKeyName"""")
      val releaseCall = emulator.getNextGrpcCall(2, SECONDS)
      assertThat(releaseCall.methodName).isEqualTo("android.emulation.control.EmulatorController/sendKey")
      assertThat(shortDebugString(releaseCall.request)).isEqualTo("""eventType: keyup key: "$emulatorKeyName"""")
    }

    val trivialKeyStrokeCases = mapOf(
      VK_LEFT to "ArrowLeft",
      VK_KP_LEFT to "ArrowLeft",
      VK_RIGHT to "ArrowRight",
      VK_KP_RIGHT to "ArrowRight",
      VK_DOWN to "ArrowDown",
      VK_KP_DOWN to "ArrowDown",
      VK_UP to "ArrowUp",
      VK_KP_UP to "ArrowUp",
      VK_HOME to "Home",
      VK_END to "End",
      VK_PAGE_DOWN to "PageDown",
      VK_PAGE_UP to "PageUp",
    )
    for ((hostKeyStroke, emulatorKeyName) in trivialKeyStrokeCases) {
      ui.keyboard.pressAndRelease(hostKeyStroke)
      val call = emulator.getNextGrpcCall(2, SECONDS)
      assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendKey")
      assertThat(shortDebugString(call.request)).isEqualTo("""eventType: keypress key: "$emulatorKeyName"""")
    }

    val keyStrokeCases = mapOf(
      getKeyStroke(ACTION_CUT) to listOf("eventType: keypress key: \"Cut\""),
      getKeyStroke(ACTION_COPY) to listOf("eventType: keypress key: \"Copy\""),
      getKeyStroke(ACTION_PASTE) to listOf("eventType: keypress key: \"Paste\""),
      getKeyStroke(ACTION_SELECT_ALL) to
          listOf("key: \"Control\"", "eventType: keypress key: \"a\"", "eventType: keyup key: \"Control\""),
      getKeyStroke(ACTION_EDITOR_MOVE_CARET_LEFT_WITH_SELECTION) to
          listOf("key: \"Shift\"", "eventType: keypress key: \"ArrowLeft\"", "eventType: keyup key: \"Shift\""),
      getKeyStroke(ACTION_EDITOR_MOVE_CARET_RIGHT_WITH_SELECTION) to
          listOf("key: \"Shift\"", "eventType: keypress key: \"ArrowRight\"", "eventType: keyup key: \"Shift\""),
      getKeyStroke(ACTION_EDITOR_MOVE_CARET_DOWN_WITH_SELECTION) to
          listOf("key: \"Shift\"", "eventType: keypress key: \"ArrowDown\"", "eventType: keyup key: \"Shift\""),
      getKeyStroke(ACTION_EDITOR_MOVE_CARET_UP_WITH_SELECTION) to
          listOf("key: \"Shift\"", "eventType: keypress key: \"ArrowUp\"", "eventType: keyup key: \"Shift\""),
      getKeyStroke(ACTION_EDITOR_PREVIOUS_WORD) to
          listOf("key: \"Control\"", "eventType: keypress key: \"ArrowLeft\"", "eventType: keyup key: \"Control\""),
      getKeyStroke(ACTION_EDITOR_NEXT_WORD) to
          listOf("key: \"Control\"", "eventType: keypress key: \"ArrowRight\"", "eventType: keyup key: \"Control\""),
      getKeyStroke(ACTION_EDITOR_PREVIOUS_WORD_WITH_SELECTION) to
          listOf("key: \"Shift\"", "key: \"Control\"", "eventType: keypress key: \"ArrowLeft\"",
                 "eventType: keyup key: \"Control\"", "eventType: keyup key: \"Shift\""),
      getKeyStroke(ACTION_EDITOR_NEXT_WORD_WITH_SELECTION) to
          listOf("key: \"Shift\"", "key: \"Control\"", "eventType: keypress key: \"ArrowRight\"",
                 "eventType: keyup key: \"Control\"", "eventType: keyup key: \"Shift\""),
      getKeyStroke(ACTION_EDITOR_MOVE_LINE_START_WITH_SELECTION) to
          listOf("key: \"Shift\"", "eventType: keypress key: \"Home\"", "eventType: keyup key: \"Shift\""),
      getKeyStroke(ACTION_EDITOR_MOVE_LINE_END_WITH_SELECTION) to
          listOf("key: \"Shift\"", "eventType: keypress key: \"End\"", "eventType: keyup key: \"Shift\""),
      getKeyStroke(ACTION_EDITOR_MOVE_CARET_PAGE_DOWN_WITH_SELECTION) to
          listOf("key: \"Shift\"", "eventType: keypress key: \"PageDown\"", "eventType: keyup key: \"Shift\""),
      getKeyStroke(ACTION_EDITOR_MOVE_CARET_PAGE_UP_WITH_SELECTION) to
          listOf("key: \"Shift\"", "eventType: keypress key: \"PageUp\"", "eventType: keyup key: \"Shift\""),
      getKeyStroke(ACTION_EDITOR_TEXT_START) to
          listOf("key: \"Control\"", "eventType: keypress key: \"Home\"", "eventType: keyup key: \"Control\""),
      getKeyStroke(ACTION_EDITOR_TEXT_END) to
          listOf("key: \"Control\"", "eventType: keypress key: \"End\"", "eventType: keyup key: \"Control\""),
      getKeyStroke(ACTION_EDITOR_TEXT_START_WITH_SELECTION) to
          listOf("key: \"Shift\"", "key: \"Control\"", "eventType: keypress key: \"Home\"",
                 "eventType: keyup key: \"Control\"", "eventType: keyup key: \"Shift\""),
      getKeyStroke(ACTION_EDITOR_TEXT_END_WITH_SELECTION) to
          listOf("key: \"Shift\"", "key: \"Control\"", "eventType: keypress key: \"End\"",
                 "eventType: keyup key: \"Control\"", "eventType: keyup key: \"Shift\""),
      getKeyStroke(ACTION_UNDO) to
          listOf("key: \"Control\"", "eventType: keypress key: \"z\"", "eventType: keyup key: \"Control\""),
      getKeyStroke(ACTION_REDO) to
          listOf("key: \"Shift\"", "key: \"Control\"", "eventType: keypress key: \"z\"",
                 "eventType: keyup key: \"Control\"", "eventType: keyup key: \"Shift\"")
    )
    for ((hostKeyStroke, keyboardEventMessages) in keyStrokeCases) {
      ui.keyboard.pressForModifiers(hostKeyStroke.modifiers)
      ui.keyboard.pressAndRelease(hostKeyStroke.keyCode)
      ui.keyboard.releaseForModifiers(hostKeyStroke.modifiers)
      for (message in keyboardEventMessages) {
        val call = emulator.getNextGrpcCall(2, SECONDS)
        assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendKey")
        assertThat(shortDebugString(call.request)).isEqualTo(message)
      }
    }

    // Ctrl+Tab should be ignored.
    with(ui.keyboard) {
      press(VK_CONTROL)
      pressAndRelease(VK_TAB)
      release(VK_CONTROL)
    }

    val mockFocusManager: DefaultKeyboardFocusManager = mock()
    whenever(mockFocusManager.processKeyEvent(any(Component::class.java), any(KeyEvent::class.java))).thenCallRealMethod()
    replaceKeyboardFocusManager(mockFocusManager, testRootDisposable)

    mockFocusManager.processKeyEvent(
        view, KeyEvent(view, KEY_PRESSED, System.nanoTime(), KeyEvent.SHIFT_DOWN_MASK, VK_TAB, VK_TAB.toChar()))

    verify(mockFocusManager, atLeast(1)).focusNextComponent(eq(view))
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
    getStreamScreenshotCallAndWaitForFrame(ui, view, ++frameNumber)
    assertAppearance(ui, "Unfolded")

    ui.mouse.press(135, 160)
    var call = emulator.getNextGrpcCall(2, SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendMouse")
    assertThat(shortDebugString(call.request)).isEqualTo("x: 1493 y: 1588 buttons: 1")
    ui.mouse.release()
    call = emulator.getNextGrpcCall(2, SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendMouse")
    assertThat(shortDebugString(call.request)).isEqualTo("x: 1493 y: 1588")

    emulator.setFolded(true)
    view.waitForFrame(ui, ++frameNumber, 2, SECONDS)
    assertAppearance(ui, "Folded")

    // Check that in a folded state mouse coordinates are interpreted differently.
    ui.mouse.press(135, 160)
    call = emulator.getNextGrpcCall(2, SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendMouse")
    assertThat(shortDebugString(call.request)).isEqualTo("x: 909 y: 1676 buttons: 1")
    ui.mouse.release()
    call = emulator.getNextGrpcCall(2, SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendMouse")
    assertThat(shortDebugString(call.request)).isEqualTo("x: 909 y: 1676")
  }

  /** Checks that the mouse button release event is sent when the mouse leaves the device display. */
  @Test
  fun testSwipe() {
    val view = emulatorViewRule.newEmulatorView()
    val emulator = emulatorViewRule.getFakeEmulator(view)

    val container = createScrollPane(view)
    val ui = FakeUi(container, 1.5)

    var frameNumber = view.frameNumber
    assertThat(frameNumber).isEqualTo(0)
    container.size = Dimension(200, 300)
    ui.layoutAndDispatchEvents()
    getStreamScreenshotCallAndWaitForFrame(ui, view, ++frameNumber)
    ui.render()

    ui.mouse.press(100, 100)
    var call = emulator.getNextGrpcCall(2, SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendMouse")
    assertThat(shortDebugString(call.request)).isEqualTo("x: 734 y: 1014 buttons: 1")
    ui.mouse.dragTo(140, 100)
    call = emulator.getNextGrpcCall(2, SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendMouse")
    assertThat(shortDebugString(call.request)).isEqualTo("x: 1168 y: 1014 buttons: 1")
    ui.mouse.dragTo(180, 100)
    call = emulator.getNextGrpcCall(2, SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendMouse")
    assertThat(shortDebugString(call.request)).isEqualTo("x: 1439 y: 1014")
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
    getStreamScreenshotCallAndWaitForFrame(ui, view, ++frameNumber)

    val mousePosition = Point(150, 75)
    val pointerInfo = mock<PointerInfo>()
    whenever(pointerInfo.location).thenReturn(mousePosition)
    mouseInfo.whenever<Any?> { MouseInfo.getPointerInfo() }.thenReturn(pointerInfo)

    ui.keyboard.setFocus(view)
    ui.mouse.moveTo(mousePosition)
    ui.keyboard.press(VK_CONTROL)
    ui.layoutAndDispatchEvents()
    assertAppearance(ui, "MultiTouch1")
    var call = emulator.getNextGrpcCall(2, SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendMouse")
    assertThat(shortDebugString(call.request)).isEqualTo("x: 1274 y: 744")

    ui.mouse.press(mousePosition)
    assertAppearance(ui, "MultiTouch2")
    call = emulator.getNextGrpcCall(2, SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendTouch")
    assertThat(shortDebugString(call.request)).isEqualTo(
        "touches { x: 1274 y: 744 pressure: 1024 expiration: NEVER_EXPIRE }" +
        " touches { x: 165 y: 2215 identifier: 1 pressure: 1024 expiration: NEVER_EXPIRE }")

    mousePosition.x -= 20
    mousePosition.y += 20
    ui.mouse.dragTo(mousePosition)
    assertAppearance(ui, "MultiTouch3")
    call = emulator.getNextGrpcCall(2, SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendTouch")
    assertThat(shortDebugString(call.request)).isEqualTo(
        "touches { x: 1058 y: 960 pressure: 1024 expiration: NEVER_EXPIRE }" +
        " touches { x: 381 y: 1999 identifier: 1 pressure: 1024 expiration: NEVER_EXPIRE }")

    ui.mouse.release()
    call = emulator.getNextGrpcCall(2, SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendTouch")
    assertThat(shortDebugString(call.request)).isEqualTo(
        "touches { x: 1058 y: 960 expiration: NEVER_EXPIRE } touches { x: 381 y: 1999 identifier: 1 expiration: NEVER_EXPIRE }")

    ui.keyboard.release(VK_CONTROL)
    assertAppearance(ui, "MultiTouch4")
  }

  @Test
  fun testDeviceButtonActions() {
    val view = emulatorViewRule.newEmulatorView()
    val emulator = emulatorViewRule.getFakeEmulator(view)

    // Check EmulatorBackButtonAction.
    emulatorViewRule.executeAction("android.device.back.button", view, place = ActionPlaces.KEYBOARD_SHORTCUT)
    var call = emulator.getNextGrpcCall(2, SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendKey")
    assertThat(shortDebugString(call.request)).isEqualTo("""eventType: keypress key: "GoBack"""")

    // Check EmulatorHomeButtonAction.
    emulatorViewRule.executeAction("android.device.home.button", view, place = ActionPlaces.KEYBOARD_SHORTCUT)
    call = emulator.getNextGrpcCall(2, SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendKey")
    assertThat(shortDebugString(call.request)).isEqualTo("""eventType: keypress key: "GoHome"""")

    // Check EmulatorOverviewButtonAction.
    emulatorViewRule.executeAction("android.device.overview.button", view, place = ActionPlaces.MOUSE_SHORTCUT)
    call = emulator.getNextGrpcCall(2, SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendKey")
    assertThat(shortDebugString(call.request)).isEqualTo("""eventType: keypress key: "AppSwitch"""")
  }

  @Test
  fun testMouseMoveSendGrpc() {
    val view = emulatorViewRule.newEmulatorView()
    val emulator = emulatorViewRule.getFakeEmulator(view)
    val container = createScrollPane(view)
    val ui = FakeUi(container, 1.0)

    container.size = Dimension(200, 300)
    ui.layoutAndDispatchEvents()
    getStreamScreenshotCallAndWaitForFrame(ui, view, 1)
    ui.render()

    ui.mouse.moveTo(135, 190)

    val call = emulator.getNextGrpcCall(2, SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendMouse")
    assertThat(shortDebugString(call.request)).doesNotContain("button") // No button should be pressed
  }

  @Test
  fun testMouseMoveNotSendWhenMultiTouch() {
    val view = emulatorViewRule.newEmulatorView()
    val emulator = emulatorViewRule.getFakeEmulator(view)
    val container = createScrollPane(view)
    val ui = FakeUi(container, 1.0)

    container.size = Dimension(200, 300)
    ui.layoutAndDispatchEvents()
    getStreamScreenshotCallAndWaitForFrame(ui, view, 1)
    ui.render()

    ui.keyboard.setFocus(view)
    ui.keyboard.press(VK_CONTROL)

    ui.mouse.moveTo(135, 190)
    ui.mouse.press(135, 190)

    // Here we expect the GRPC call from `press()`, as `moveTo()` should not send any GRPC call.
    val call = emulator.getNextGrpcCall(2, SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendTouch")
    assertThat(shortDebugString(call.request)).contains("pressure") // Should have non-zero pressure.

    ui.keyboard.release(VK_CONTROL)
  }

  @Test
  fun testMouseMoveNotSendWhenCameraOperating() {
    val view = emulatorViewRule.newEmulatorView()
    val emulator = emulatorViewRule.getFakeEmulator(view)
    val panel = NotificationHolderPanel(view)
    val container = HeadlessRootPaneContainer(panel)
    container.rootPane.size = Dimension(200, 300)
    val ui = FakeUi(container.rootPane)

    ui.layoutAndDispatchEvents()
    getStreamScreenshotCallAndWaitForFrame(ui, view, 1)
    ui.render()

    // Activate the virtual scene camera
    focusManager.focusOwner = view
    emulator.virtualSceneCameraActive = true
    waitForCondition(200, MILLISECONDS) { ui.findComponent<EditorNotificationPanel>() != null }

    // Start operating camera
    ui.keyboard.press(VK_SHIFT)

    // Move mouse
    ui.mouse.moveTo(135, 190)
    ui.mouse.press(135, 190)

    // Stop operating camera
    ui.keyboard.release(VK_SHIFT)

    // Here we expect the GRPC call from `press()`, as `moveTo()` should not send any GRPC call.
    val call = emulator.getNextGrpcCall(2, SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendMouse")
    assertThat(shortDebugString(call.request)).contains("button") // Some button should be pressed
  }

  @Test
  fun testMouseButtonFilledInGrpc() {
    val view = emulatorViewRule.newEmulatorView()
    val emulator = emulatorViewRule.getFakeEmulator(view)
    val container = createScrollPane(view)
    val ui = FakeUi(container)

    container.size = Dimension(200, 300)
    ui.layoutAndDispatchEvents()
    getStreamScreenshotCallAndWaitForFrame(ui, view, 1)
    ui.render()

    val params = listOf(Pair(FakeMouse.Button.RIGHT, "buttons: 2"), Pair(FakeMouse.Button.MIDDLE, "buttons: 4"))
    for ((button, expected) in params) {
      ui.mouse.press(135, 190, button)

      emulator.getNextGrpcCall(2, SECONDS).apply {
        assertWithMessage(button.name).that(methodName).isEqualTo("android.emulation.control.EmulatorController/sendMouse")
        assertWithMessage(button.name).that(shortDebugString(request)).contains(expected)
      }

      ui.mouse.release()

      emulator.getNextGrpcCall(2, SECONDS).apply {
        assertWithMessage(button.name).that(methodName).isEqualTo("android.emulation.control.EmulatorController/sendMouse")
        assertWithMessage(button.name).that(shortDebugString(request)).doesNotContain("button") // No button should be pressed
      }
    }
  }

  @Test
  fun testMouseDragHasPressedButton() {
    val view = emulatorViewRule.newEmulatorView()
    val emulator = emulatorViewRule.getFakeEmulator(view)
    val container = createScrollPane(view)
    val ui = FakeUi(container)

    container.size = Dimension(200, 300)
    ui.layoutAndDispatchEvents()
    getStreamScreenshotCallAndWaitForFrame(ui, view, 1)
    ui.render()

    ui.mouse.press(135, 190, FakeMouse.Button.RIGHT)

    emulator.getNextGrpcCall(2, SECONDS).apply {
      assertThat(methodName).isEqualTo("android.emulation.control.EmulatorController/sendMouse")
    }

    ui.mouse.dragDelta(5, 0)

    emulator.getNextGrpcCall(2, SECONDS).apply {
      assertThat(methodName).isEqualTo("android.emulation.control.EmulatorController/sendMouse")
      assertThat(shortDebugString(request)).contains("buttons: 2")
    }

    ui.mouse.release()

    emulator.getNextGrpcCall(2, SECONDS).apply {
      assertThat(methodName).isEqualTo("android.emulation.control.EmulatorController/sendMouse")
      assertThat(shortDebugString(request)).doesNotContain("button") // No button should be pressed
    }
  }

  @Test
  fun testMouseWheel() {
    val view = emulatorViewRule.newEmulatorView()
    val emulator = emulatorViewRule.getFakeEmulator(view)
    val container = createScrollPane(view)
    val ui = FakeUi(container)

    container.size = Dimension(200, 300)
    ui.layoutAndDispatchEvents()
    getStreamScreenshotCallAndWaitForFrame(ui, view, 1)
    ui.render()

    var call: GrpcCallRecord? = null
    for (rotation in listOf(1, 1, -1, -1)) {
      ui.mouse.wheel(100, 100, rotation)
      if (call == null) {
        call = emulator.getNextGrpcCall(2, SECONDS)
        assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/injectWheel")
      }
      assertThat(shortDebugString(call.getNextRequest(2, SECONDS))).isEqualTo("dy: ${-rotation * 120}")
    }
  }

  @Test
  fun testKeysForMnemonicsShouldNotBeConsumed() {
    val view = emulatorViewRule.newEmulatorView()

    val altMPressedEvent = KeyEvent(view, KEY_PRESSED, System.nanoTime(), KeyEvent.ALT_DOWN_MASK, VK_M, VK_M.toChar())
    KeyboardFocusManager.getCurrentKeyboardFocusManager().redispatchEvent(view, altMPressedEvent)
    assertThat(altMPressedEvent.isConsumed).isFalse()

    val altMReleasedEvent = KeyEvent(view, KEY_RELEASED, System.nanoTime(), KeyEvent.ALT_DOWN_MASK, VK_M, VK_M.toChar())
    KeyboardFocusManager.getCurrentKeyboardFocusManager().redispatchEvent(view, altMReleasedEvent)
    assertThat(altMReleasedEvent.isConsumed).isFalse()
  }

  @Test
  fun testKeyPreprocessingSkippedWhenHardwareInputEnabled() {
    val view = emulatorViewRule.newEmulatorView()
    emulatorViewRule.executeAction("android.streaming.hardware.input", view)
    assertThat(view.skipKeyEventDispatcher(KeyEvent(view, KEY_PRESSED, System.nanoTime(), 0, VK_M, VK_M.toChar()))).isTrue()
  }

  @Test
  fun testKeyPreprocessingNotSkippedForActionTogglingHardwareInput() {
    val view = emulatorViewRule.newEmulatorView()
    emulatorViewRule.executeAction("android.streaming.hardware.input", view)
    val keymapManager = KeymapManager.getInstance()
    keymapManager.activeKeymap.addShortcut("android.streaming.hardware.input", KeyboardShortcut.fromString("control shift J"))

    assertThat(view.skipKeyEventDispatcher(KeyEvent(view, KEY_PRESSED, System.nanoTime(),
                                                    KeyEvent.SHIFT_DOWN_MASK or KeyEvent.CTRL_DOWN_MASK, KeyEvent.VK_J,
                                                    KeyEvent.CHAR_UNDEFINED))).isFalse()
  }

  @Test
  fun testCtrlAndAlphabeticalKeysSentWhenHardwareInputEnabled() {
    val view = emulatorViewRule.newEmulatorView()
    val emulator = emulatorViewRule.getFakeEmulator(view)
    val container = createScrollPane(view)
    val ui = FakeUi(container)

    emulatorViewRule.executeAction("android.streaming.hardware.input", view)
    ui.keyboard.setFocus(view)

    ui.keyboard.press(VK_CONTROL)
    emulator.getNextGrpcCall(2, SECONDS).apply {
      assertThat(methodName).isEqualTo("android.emulation.control.EmulatorController/sendKey")
      assertThat(shortDebugString(request)).isEqualTo("key: \"Control\"")
    }

    ui.keyboard.press(KeyEvent.VK_S)
    emulator.getNextGrpcCall(2, SECONDS).apply {
      assertThat(methodName).isEqualTo("android.emulation.control.EmulatorController/sendKey")
      assertThat(shortDebugString(request)).isEqualTo("key: \"s\"")
    }

    ui.keyboard.release(KeyEvent.VK_S)
    emulator.getNextGrpcCall(2, SECONDS).apply {
      assertThat(methodName).isEqualTo("android.emulation.control.EmulatorController/sendKey")
      assertThat(shortDebugString(request)).isEqualTo("eventType: keyup key: \"s\"")
    }

    ui.keyboard.release(VK_CONTROL)
    emulator.getNextGrpcCall(2, SECONDS).apply {
      assertThat(methodName).isEqualTo("android.emulation.control.EmulatorController/sendKey")
      assertThat(shortDebugString(request)).isEqualTo("eventType: keyup key: \"Control\"")
    }
  }

  @Test
  fun testHideCameraNotificationDuringHardwareInput() {
    val view = emulatorViewRule.newEmulatorView()
    val emulator = emulatorViewRule.getFakeEmulator(view)
    val panel = NotificationHolderPanel(view)
    val container = HeadlessRootPaneContainer(panel)
    container.rootPane.size = Dimension(200, 300)
    val ui = FakeUi(container.rootPane, 1.0)

    // Activate the virtual scene camera
    focusManager.focusOwner = view
    emulator.virtualSceneCameraActive = true
    waitForCondition(200, MILLISECONDS) {
      ui.findComponent<EditorNotificationPanel>() != null
    }

    // Enable hardware input
    emulatorViewRule.executeAction("android.streaming.hardware.input", view)

    // Check if notification panel is disappeared
    waitForCondition(200, MILLISECONDS) {
      ui.findComponent<EditorNotificationPanel>() == null
    }

    // Disable hardware input
    emulatorViewRule.executeAction("android.streaming.hardware.input", view)

    // Check if notification panel is re-appeared
    waitForCondition(200, MILLISECONDS) {
      ui.findComponent<EditorNotificationPanel>() != null
    }
  }

  @Test
  fun testCameraNotificationHasOperatingMessageWhenHardwareInputDisabledWithShift() {
    val view = emulatorViewRule.newEmulatorView()
    val emulator = emulatorViewRule.getFakeEmulator(view)
    val panel = NotificationHolderPanel(view)
    val container = HeadlessRootPaneContainer(panel)
    container.rootPane.size = Dimension(200, 300)
    val ui = FakeUi(container.rootPane, 1.0)

    // Enable hardware input
    emulatorViewRule.executeAction("android.streaming.hardware.input", view)

    // Activate the virtual scene camera
    focusManager.focusOwner = view
    emulator.virtualSceneCameraActive = true

    // Disable hardware input with shift key
    executeStreamingAction("android.streaming.hardware.input", view, emulatorViewRule.project, modifiers=SHIFT_DOWN_MASK)

    // Check if notification panel is disappeared
    waitForCondition(200, MILLISECONDS) {
      ui.findComponent<EditorNotificationPanel>() != null
    }

    assertThat(ui.findComponent<EditorNotificationPanel>()?.text).isEqualTo(
        "Move camera with WASDQE keys, rotate with mouse or arrow keys")
  }

  @Test
  fun testDisableMultiTouchDuringHardwareInput() {
    val view = emulatorViewRule.newEmulatorView()
    val emulator = emulatorViewRule.getFakeEmulator(view)

    val container = createScrollPane(view)
    val ui = FakeUi(container, 2.0)

    var frameNumber = view.frameNumber
    assertThat(frameNumber).isEqualTo(0)
    container.size = Dimension(200, 300)
    ui.layoutAndDispatchEvents()
    getStreamScreenshotCallAndWaitForFrame(ui, view, ++frameNumber)

    val mousePosition = Point(150, 75)
    val pointerInfo = mock<PointerInfo>()
    whenever(pointerInfo.location).thenReturn(mousePosition)
    mouseInfo.whenever<Any?> { MouseInfo.getPointerInfo() }.thenReturn(pointerInfo)

    // Start multitouch
    ui.keyboard.setFocus(view)
    ui.mouse.moveTo(mousePosition)
    ui.keyboard.press(VK_CONTROL)
    ui.layoutAndDispatchEvents()
    assertAppearance(ui, "MultiTouch1")
    var call = emulator.getNextGrpcCall(2, SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendMouse")
    assertThat(shortDebugString(call.request)).isEqualTo("x: 1274 y: 744")

    // Enable hardware input
    emulatorViewRule.executeAction("android.streaming.hardware.input", view)

    // Check if multitouch indicator is hidden
    ui.layoutAndDispatchEvents()
    assertAppearance(ui, "MultiTouch4")

    // Check if mouse down event is generated instead of touch
    ui.mouse.press(mousePosition)
    call = emulator.getNextGrpcCall(2, SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendMouse")
    assertThat(shortDebugString(call.request)).isEqualTo("x: 1274 y: 744 buttons: 1")

    // Disable hardware input
    executeStreamingAction("android.streaming.hardware.input", view, emulatorViewRule.project, modifiers=CTRL_DOWN_MASK)

    // Check if multitouch indicator is shown
    ui.layoutAndDispatchEvents()
    assertAppearance(ui, "MultiTouch2")

    // Drag mouse
    mousePosition.x -= 20
    mousePosition.y += 20
    ui.mouse.dragTo(mousePosition)

    // Check if touch event is generated
    call = emulator.getNextGrpcCall(2, SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendTouch")
    assertThat(shortDebugString(call.request)).isEqualTo(
        "touches { x: 1058 y: 960 pressure: 1024 expiration: NEVER_EXPIRE }" +
        " touches { x: 381 y: 1999 identifier: 1 pressure: 1024 expiration: NEVER_EXPIRE }")
  }

  @Test
  fun testMetaKeysReleasedWhenHardwareInputDisabled() {
    val view = emulatorViewRule.newEmulatorView()
    val emulator = emulatorViewRule.getFakeEmulator(view)
    val container = createScrollPane(view)
    val ui = FakeUi(container, 2.0)

    // Enable hardware input
    emulatorViewRule.executeAction("android.streaming.hardware.input", view)

    // Press Ctrl
    focusManager.focusOwner = view
    ui.keyboard.press(VK_CONTROL)

    emulator.getNextGrpcCall(2, SECONDS).apply {
      assertThat(methodName).isEqualTo("android.emulation.control.EmulatorController/sendKey")
      assertThat(shortDebugString(request)).isEqualTo("key: \"Control\"")
    }

    // Disable hardware input
    emulatorViewRule.executeAction("android.streaming.hardware.input", view)

    emulator.getNextGrpcCall(2, SECONDS).apply {
      assertThat(methodName).isEqualTo("android.emulation.control.EmulatorController/sendKey")
      assertThat(shortDebugString(request)).isEqualTo("eventType: keyup key: \"Control\"")
    }
  }

  @Test
  fun testMetaKeysReleasedWhenLostFocusDuringHardwareInput() {
    val view = emulatorViewRule.newEmulatorView()
    val emulator = emulatorViewRule.getFakeEmulator(view)
    val container = createScrollPane(view)
    val ui = FakeUi(container, 2.0)

    // Enable hardware input
    emulatorViewRule.executeAction("android.streaming.hardware.input", view)

    // Press Ctrl
    focusManager.focusOwner = view
    ui.keyboard.press(VK_CONTROL)

    emulator.getNextGrpcCall(2, SECONDS).apply {
      assertThat(methodName).isEqualTo("android.emulation.control.EmulatorController/sendKey")
      assertThat(shortDebugString(request)).isEqualTo("key: \"Control\"")
    }

    // Lose focus
    focusManager.focusOwner = null

    emulator.getNextGrpcCall(2, SECONDS).apply {
      assertThat(methodName).isEqualTo("android.emulation.control.EmulatorController/sendKey")
      assertThat(shortDebugString(request)).isEqualTo("eventType: keyup key: \"Control\"")
    }
  }

  private fun createScrollPane(view: Component): JScrollPane {
    return JScrollPane(view).apply {
      border = null
      isFocusable = true
    }
  }

  private fun getStreamScreenshotCallAndWaitForFrame(fakeUi: FakeUi, view: EmulatorView, frameNumber: Int): GrpcCallRecord {
    val emulator = emulatorViewRule.getFakeEmulator(view)
    val call = emulator.getNextGrpcCall(2, SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/streamScreenshot")
    view.waitForFrame(fakeUi, frameNumber, 2, SECONDS)
    return call
  }

  @Throws(TimeoutException::class)
  private fun EmulatorView.waitForFrame(fakeUi: FakeUi, frame: Int, timeout: Long, unit: TimeUnit) {
    waitForCondition(timeout, unit) { renderAndGetFrameNumber(fakeUi) >= frame }
  }

  private fun EmulatorView.renderAndGetFrameNumber(fakeUi: FakeUi): Int {
    fakeUi.render() // The frame number may get updated as a result of rendering.
    return frameNumber
  }

  private fun assertAppearance(ui: FakeUi, goldenImageName: String) {
    val image = ui.render()
    ImageDiffUtil.assertImageSimilar(getGoldenFile(goldenImageName), image, 0.0)
  }

  private fun getGoldenFile(name: String): Path =
      TestUtils.resolveWorkspacePathUnchecked("${GOLDEN_FILE_PATH}/${name}.png")
}

private fun getKeyStroke(action: String) =
  KeymapUtil.getKeyStroke(KeymapUtil.getActiveKeymapShortcuts(action))!!

private const val GOLDEN_FILE_PATH = "tools/adt/idea/streaming/testData/EmulatorViewTest/golden"
