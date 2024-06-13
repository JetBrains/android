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

import com.android.emulator.control.Posture.PostureValue
import com.android.testutils.ImageDiffUtil
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.eq
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.test.testutils.TestUtils
import com.android.testutils.waitForCondition
import com.android.tools.adtui.actions.ZoomType
import com.android.tools.adtui.swing.FakeKeyboardFocusManager
import com.android.tools.adtui.swing.FakeMouse
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.HeadlessRootPaneContainer
import com.android.tools.adtui.swing.IconLoaderRule
import com.android.tools.adtui.swing.replaceKeyboardFocusManager
import com.android.tools.adtui.ui.NotificationHolderPanel
import com.android.tools.analytics.UsageTrackerRule
import com.android.tools.idea.protobuf.TextFormat.shortDebugString
import com.android.tools.idea.streaming.emulator.EmulatorController.ConnectionState
import com.android.tools.idea.streaming.emulator.FakeEmulator.GrpcCallRecord
import com.android.tools.idea.streaming.executeStreamingAction
import com.android.tools.idea.testing.mockStatic
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
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
import com.intellij.openapi.util.Disposer
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
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeoutException
import javax.swing.JScrollPane
import kotlin.time.Duration.Companion.seconds

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
  @get:Rule
  val usageTrackerRule = UsageTrackerRule()
  private lateinit var view: EmulatorView
  private val fakeEmulator: FakeEmulator by lazy { emulatorViewRule.getFakeEmulator(view) }
  private lateinit var fakeUi: FakeUi

  private val testRootDisposable
    get() = emulatorViewRule.disposable

  private lateinit var mouseInfo: MockedStatic<MouseInfo>
  private lateinit var focusManager: FakeKeyboardFocusManager

  @Before
  fun setUp() {
    mouseInfo = mockStatic(testRootDisposable)
    mouseInfo.whenever<PointerInfo> { MouseInfo.getPointerInfo() }.thenReturn(mock<PointerInfo>())
    focusManager = FakeKeyboardFocusManager(testRootDisposable)
  }

  @Test
  fun testEmulatorView() {
    view = emulatorViewRule.newEmulatorView()
    fakeUi = FakeUi(createScrollPane(view), 2.0)

    // Check initial appearance.
    fakeUi.root.size = Dimension(200, 300)
    fakeUi.layoutAndDispatchEvents()
    var call = getStreamScreenshotCallAndWaitForFrame()
    assertThat(shortDebugString(call.request)).isEqualTo("format: RGB888 width: 363 height: 547")
    assertAppearance("EmulatorView1")
    assertThat(call.completion.isCancelled).isFalse() // The call has not been cancelled.
    assertThat(call.completion.isDone).isFalse() // The call is still ongoing.

    // Check resizing.
    val previousCall = call
    fakeUi.root.size = Dimension(250, 200)
    fakeUi.layoutAndDispatchEvents()
    call = getStreamScreenshotCallAndWaitForFrame()
    assertThat(shortDebugString(call.request)).isEqualTo("format: RGB888 width: 454 height: 364")
    assertAppearance("EmulatorView2")
    assertThat(previousCall.completion.isCancelled).isTrue() // The previous call is cancelled.
    assertThat(call.completion.isCancelled).isFalse() // The latest call has not been cancelled.
    assertThat(call.completion.isDone).isFalse() // The latest call is still ongoing.

    // Check zoom.
    val skinHeight = 3245
    assertThat(view.scale).isWithin(1e-4).of(200 * fakeUi.screenScale / skinHeight)
    assertThat(view.canZoomIn()).isTrue()
    assertThat(view.canZoomOut()).isFalse()
    assertThat(view.canZoomToActual()).isTrue()
    assertThat(view.canZoomToFit()).isFalse()

    view.zoom(ZoomType.IN)
    fakeUi.layoutAndDispatchEvents()
    call = getStreamScreenshotCallAndWaitForFrame()
    assertThat(shortDebugString(call.request)).isEqualTo(
      // Available space is slightly wider on Mac due to a narrower scrollbar.
      if (UIUtil.isRetina()) "format: RGB888 width: 427 height: 740" else "format: RGB888 width: 423 height: 740")
    assertThat(view.canZoomIn()).isTrue()
    assertThat(view.canZoomOut()).isTrue()
    assertThat(view.canZoomToActual()).isTrue()
    assertThat(view.canZoomToFit()).isTrue()

    view.zoom(ZoomType.ACTUAL)
    fakeUi.layoutAndDispatchEvents()
    call = getStreamScreenshotCallAndWaitForFrame()
    assertThat(shortDebugString(call.request)).isEqualTo("format: RGB888 width: 1440 height: 2960")
    assertThat(view.canZoomIn()).isTrue()
    assertThat(view.canZoomOut()).isTrue()
    assertThat(view.canZoomToActual()).isFalse()
    assertThat(view.canZoomToFit()).isTrue()

    view.zoom(ZoomType.OUT)
    fakeUi.layoutAndDispatchEvents()
    call = getStreamScreenshotCallAndWaitForFrame()
    assertThat(shortDebugString(call.request)).isEqualTo("format: RGB888 width: 720 height: 1481")
    assertThat(view.canZoomIn()).isTrue()
    assertThat(view.canZoomOut()).isTrue()
    assertThat(view.canZoomToActual()).isTrue()
    assertThat(view.canZoomToFit()).isTrue()

    view.zoom(ZoomType.FIT)
    fakeUi.layoutAndDispatchEvents()
    call = getStreamScreenshotCallAndWaitForFrame()
    assertThat(shortDebugString(call.request)).isEqualTo("format: RGB888 width: 454 height: 364")
    assertThat(view.canZoomIn()).isTrue()
    assertThat(view.canZoomOut()).isFalse()
    assertThat(view.canZoomToActual()).isTrue()
    assertThat(view.canZoomToFit()).isFalse()

    // Check rotation.
    emulatorViewRule.executeAction("android.device.rotate.left", view)
    call = fakeEmulator.getNextGrpcCall(2.seconds)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/setPhysicalModel")
    assertThat(shortDebugString(call.request)).isEqualTo("target: ROTATION value { data: 0.0 data: 0.0 data: 90.0 }")
    call = getStreamScreenshotCallAndWaitForFrame()
    assertThat(shortDebugString(call.request)).isEqualTo("format: RGB888 width: 456 height: 363")
    waitForCondition(2.seconds) { view.displayOrientationQuadrants == 1 }
    assertAppearance("EmulatorView3")

    // Check mouse input in landscape orientation.
    fakeUi.mouse.press(10, 153)
    call = fakeEmulator.getNextGrpcCall(2.seconds)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendMouse")
    assertThat(shortDebugString(call.request)).isEqualTo("x: 35 y: 61 buttons: 1")

    fakeUi.mouse.dragTo(215, 48)
    call = fakeEmulator.getNextGrpcCall(2.seconds)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendMouse")
    assertThat(shortDebugString(call.request)).isEqualTo("x: 1404 y: 2723 buttons: 1")

    fakeUi.mouse.release()
    call = fakeEmulator.getNextGrpcCall(2.seconds)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendMouse")
    assertThat(shortDebugString(call.request)).isEqualTo("x: 1404 y: 2723")

    // Check clockwise rotation in a zoomed-in state.
    view.zoom(ZoomType.IN)
    fakeUi.layoutAndDispatchEvents()
    call = getStreamScreenshotCallAndWaitForFrame()
    assertThat(shortDebugString(call.request)).isEqualTo(
      // Available space is slightly wider on Mac due to a narrower scrollbar.
      if (SystemInfo.isMac) "format: RGB888 width: 740 height: 360" else "format: RGB888 width: 740 height: 360")
    assertThat(view.canZoomOut()).isTrue()
    assertThat(view.canZoomToFit()).isTrue()
    emulatorViewRule.executeAction("android.device.rotate.right", view)
    call = fakeEmulator.getNextGrpcCall(2.seconds)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/setPhysicalModel")
    assertThat(shortDebugString(call.request)).isEqualTo("target: ROTATION value { data: 0.0 data: 0.0 data: 0.0 }")
    getStreamScreenshotCallAndWaitForFrame()
    fakeUi.layoutAndDispatchEvents()
    call = getStreamScreenshotCallAndWaitForFrame()
    assertThat(shortDebugString(call.request)).isEqualTo("format: RGB888 width: 454 height: 364")
    assertThat(view.canZoomOut()).isFalse() // zoom-in mode cancelled by the rotation.
    assertThat(view.canZoomToFit()).isFalse()
    assertAppearance("EmulatorView2")

    // Check mouse input in portrait orientation.
    fakeUi.mouse.press(82, 7)
    call = fakeEmulator.getNextGrpcCall(2.seconds)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendMouse")
    assertThat(shortDebugString(call.request)).isEqualTo("x: 36 y: 44 buttons: 1")
    fakeUi.mouse.release()
    call = fakeEmulator.getNextGrpcCall(2.seconds)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendMouse")
    assertThat(shortDebugString(call.request)).isEqualTo("x: 36 y: 44")

    // Mouse events outside the display image should be ignored.
    fakeUi.mouse.press(50, 7)
    fakeUi.mouse.release()

    // Check hiding the device frame.
    view.deviceFrameVisible = false
    call = getStreamScreenshotCallAndWaitForFrame()
    assertThat(shortDebugString(call.request)).isEqualTo("format: RGB888 width: 500 height: 400")
    assertAppearance("EmulatorView4")
  }

  @Test
  fun testKeyboardInput() {
    view = emulatorViewRule.newEmulatorView()
    fakeUi = FakeUi(createScrollPane(view), 2.0)

    fakeUi.keyboard.setFocus(view)
    // Printable ASCII characters.
    for (c in ' '..'~') {
      fakeUi.keyboard.type(c.code)
      val call = fakeEmulator.getNextGrpcCall(2.seconds)
      assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendKey")
      val expectedText = when (c) { '\"', '\'', '\\' -> "\\$c" else -> c.toString() }
      assertThat(shortDebugString(call.request)).isEqualTo("""text: "$expectedText"""")
    }

    val trivialKeyStrokeCases = mapOf(
      VK_ENTER to "Enter",
      VK_TAB to "Tab",
      VK_ESCAPE to "Escape",
      VK_BACK_SPACE to "Backspace",
      VK_DELETE to if (SystemInfo.isMac) "Backspace" else "Delete",
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
      fakeUi.keyboard.pressAndRelease(hostKeyStroke)
      val call = fakeEmulator.getNextGrpcCall(2.seconds)
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
      fakeUi.keyboard.pressForModifiers(hostKeyStroke.modifiers)
      fakeUi.keyboard.pressAndRelease(hostKeyStroke.keyCode)
      fakeUi.keyboard.releaseForModifiers(hostKeyStroke.modifiers)
      for (message in keyboardEventMessages) {
        val call = fakeEmulator.getNextGrpcCall(2.seconds)
        assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendKey")
        assertThat(shortDebugString(call.request)).isEqualTo(message)
      }
    }

    // Ctrl+Tab should be ignored.
    with(fakeUi.keyboard) {
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
    view = emulatorViewRule.newEmulatorView { path -> FakeEmulator.createFoldableAvd(path) }
    fakeUi = FakeUi(createScrollPane(view), 2.0)

    fakeUi.root.size = Dimension(200, 200)
    fakeUi.layoutAndDispatchEvents()
    getStreamScreenshotCallAndWaitForFrame()
    assertAppearance("FoldingOpen")

    fakeUi.mouse.press(135, 160)
    var call = fakeEmulator.getNextGrpcCall(2.seconds)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendMouse")
    assertThat(shortDebugString(call.request)).isEqualTo("x: 1528 y: 1635 buttons: 1")
    fakeUi.mouse.release()
    call = fakeEmulator.getNextGrpcCall(2.seconds)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendMouse")
    assertThat(shortDebugString(call.request)).isEqualTo("x: 1528 y: 1635")

    fakeEmulator.setPosture(PostureValue.POSTURE_CLOSED)
    waitForCondition(1.seconds) { view.currentPosture?.posture == PostureValue.POSTURE_CLOSED}
    getStreamScreenshotCallAndWaitForFrame()
    assertAppearance("FoldingClosed")

    // Check that in a folded state mouse coordinates are interpreted differently.
    fakeUi.mouse.press(135, 160)
    call = fakeEmulator.getNextGrpcCall(2.seconds)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendMouse")
    assertThat(shortDebugString(call.request)).isEqualTo("x: 914 y: 1720 buttons: 1")
    fakeUi.mouse.release()
    call = fakeEmulator.getNextGrpcCall(2.seconds)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendMouse")
    assertThat(shortDebugString(call.request)).isEqualTo("x: 914 y: 1720")
  }

  /** Checks that the mouse button release event is sent when the mouse leaves the device display. */
  @Test
  fun testSwipe() {
    view = emulatorViewRule.newEmulatorView()
    fakeUi = FakeUi(createScrollPane(view), 1.5)

    fakeUi.root.size = Dimension(200, 300)
    fakeUi.layoutAndDispatchEvents()
    getStreamScreenshotCallAndWaitForFrame()
    fakeUi.render()

    fakeUi.mouse.press(100, 100)
    var call = fakeEmulator.getNextGrpcCall(2.seconds)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendMouse")
    assertThat(shortDebugString(call.request)).isEqualTo("x: 734 y: 1014 buttons: 1")
    fakeUi.mouse.dragTo(140, 100)
    call = fakeEmulator.getNextGrpcCall(2.seconds)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendMouse")
    assertThat(shortDebugString(call.request)).isEqualTo("x: 1168 y: 1014 buttons: 1")
    fakeUi.mouse.dragTo(180, 100)
    call = fakeEmulator.getNextGrpcCall(2.seconds)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendMouse")
    assertThat(shortDebugString(call.request)).isEqualTo("x: 1439 y: 1014")
  }

  @Test
  fun testMultiTouch() {
    view = emulatorViewRule.newEmulatorView()
    fakeUi = FakeUi(createScrollPane(view), 2.0)

    fakeUi.root.size = Dimension(200, 300)
    fakeUi.layoutAndDispatchEvents()
    getStreamScreenshotCallAndWaitForFrame()

    val mousePosition = Point(150, 75)
    val pointerInfo = mock<PointerInfo>()
    whenever(pointerInfo.location).thenReturn(mousePosition)
    mouseInfo.whenever<Any?> { MouseInfo.getPointerInfo() }.thenReturn(pointerInfo)

    fakeUi.keyboard.setFocus(view)
    fakeUi.mouse.moveTo(mousePosition)
    fakeUi.keyboard.press(VK_CONTROL)
    fakeUi.layoutAndDispatchEvents()
    assertAppearance("MultiTouch1")
    var call = fakeEmulator.getNextGrpcCall(2.seconds)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendMouse")
    assertThat(shortDebugString(call.request)).isEqualTo("x: 1274 y: 744")

    fakeUi.mouse.press(mousePosition)
    assertAppearance("MultiTouch2")
    call = fakeEmulator.getNextGrpcCall(2.seconds)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendTouch")
    assertThat(shortDebugString(call.request)).isEqualTo(
        "touches { x: 1274 y: 744 pressure: 1024 expiration: NEVER_EXPIRE }" +
        " touches { x: 165 y: 2215 identifier: 1 pressure: 1024 expiration: NEVER_EXPIRE }")

    mousePosition.x -= 20
    mousePosition.y += 20
    fakeUi.mouse.dragTo(mousePosition)
    assertAppearance("MultiTouch3")
    call = fakeEmulator.getNextGrpcCall(2.seconds)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendTouch")
    assertThat(shortDebugString(call.request)).isEqualTo(
        "touches { x: 1058 y: 960 pressure: 1024 expiration: NEVER_EXPIRE }" +
        " touches { x: 381 y: 1999 identifier: 1 pressure: 1024 expiration: NEVER_EXPIRE }")

    fakeUi.mouse.release()
    call = fakeEmulator.getNextGrpcCall(2.seconds)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendTouch")
    assertThat(shortDebugString(call.request)).isEqualTo(
        "touches { x: 1058 y: 960 expiration: NEVER_EXPIRE } touches { x: 381 y: 1999 identifier: 1 expiration: NEVER_EXPIRE }")

    fakeUi.keyboard.release(VK_CONTROL)
    assertAppearance("MultiTouch4")
  }

  @Test
  fun testDeviceButtonActions() {
    view = emulatorViewRule.newEmulatorView()

    // Check EmulatorBackButtonAction.
    emulatorViewRule.executeAction("android.device.back.button", view, place = ActionPlaces.KEYBOARD_SHORTCUT)
    var call = fakeEmulator.getNextGrpcCall(2.seconds)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendKey")
    assertThat(shortDebugString(call.request)).isEqualTo("""eventType: keypress key: "GoBack"""")

    // Check EmulatorHomeButtonAction.
    emulatorViewRule.executeAction("android.device.home.button", view, place = ActionPlaces.KEYBOARD_SHORTCUT)
    call = fakeEmulator.getNextGrpcCall(2.seconds)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendKey")
    assertThat(shortDebugString(call.request)).isEqualTo("""eventType: keypress key: "GoHome"""")

    // Check EmulatorOverviewButtonAction.
    emulatorViewRule.executeAction("android.device.overview.button", view, place = ActionPlaces.MOUSE_SHORTCUT)
    call = fakeEmulator.getNextGrpcCall(2.seconds)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendKey")
    assertThat(shortDebugString(call.request)).isEqualTo("""eventType: keypress key: "AppSwitch"""")
  }

  @Test
  fun testMouseMoveSendGrpc() {
    view = emulatorViewRule.newEmulatorView()
    fakeUi = FakeUi(createScrollPane(view), 1.0)

    fakeUi.root.size = Dimension(200, 300)
    fakeUi.layoutAndDispatchEvents()
    getStreamScreenshotCallAndWaitForFrame()
    fakeUi.render()

    fakeUi.mouse.moveTo(135, 190)

    val call = fakeEmulator.getNextGrpcCall(2.seconds)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendMouse")
    assertThat(shortDebugString(call.request)).doesNotContain("button") // No button should be pressed
  }

  @Test
  fun testMouseMoveNotSendWhenMultiTouch() {
    view = emulatorViewRule.newEmulatorView()
    fakeUi = FakeUi(createScrollPane(view), 1.0)

    fakeUi.root.size = Dimension(200, 300)
    fakeUi.layoutAndDispatchEvents()
    getStreamScreenshotCallAndWaitForFrame()
    fakeUi.render()

    fakeUi.keyboard.setFocus(view)
    fakeUi.keyboard.press(VK_CONTROL)

    fakeUi.mouse.moveTo(135, 190)
    fakeUi.mouse.press(135, 190)

    // Here we expect the GRPC call from `press()`, as `moveTo()` should not send any GRPC call.
    val call = fakeEmulator.getNextGrpcCall(2.seconds)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendTouch")
    assertThat(shortDebugString(call.request)).contains("pressure") // Should have non-zero pressure.

    fakeUi.keyboard.release(VK_CONTROL)
  }

  @Test
  fun testMouseMoveNotSendWhenCameraOperating() {
    view = emulatorViewRule.newEmulatorView()
    val panel = NotificationHolderPanel(view)
    val container = HeadlessRootPaneContainer(panel)
    container.rootPane.size = Dimension(200, 300)
    fakeUi = FakeUi(container.rootPane)

    fakeUi.layoutAndDispatchEvents()
    getStreamScreenshotCallAndWaitForFrame()
    fakeUi.render()

    // Activate the virtual scene camera
    focusManager.focusOwner = view
    fakeEmulator.virtualSceneCameraActive = true
    waitForCondition(200, MILLISECONDS) { fakeUi.findComponent<EditorNotificationPanel>() != null }

    // Start operating camera
    fakeUi.keyboard.press(VK_SHIFT)

    // Move mouse
    fakeUi.mouse.moveTo(135, 190)
    fakeUi.mouse.press(135, 190)

    // Stop operating camera
    fakeUi.keyboard.release(VK_SHIFT)

    // Here we expect the GRPC call from `press()`, as `moveTo()` should not send any GRPC call.
    val call = fakeEmulator.getNextGrpcCall(2.seconds)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendMouse")
    assertThat(shortDebugString(call.request)).contains("button") // Some button should be pressed
  }

  @Test
  fun testMouseButtonFilledInGrpc() {
    view = emulatorViewRule.newEmulatorView()
    fakeUi = FakeUi(createScrollPane(view))

    fakeUi.root.size = Dimension(200, 300)
    fakeUi.layoutAndDispatchEvents()
    getStreamScreenshotCallAndWaitForFrame()
    fakeUi.render()

    val params = listOf(Pair(FakeMouse.Button.RIGHT, "buttons: 2"), Pair(FakeMouse.Button.MIDDLE, "buttons: 4"))
    for ((button, expected) in params) {
      fakeUi.mouse.press(135, 190, button)

      fakeEmulator.getNextGrpcCall(2.seconds).apply {
        assertWithMessage(button.name).that(methodName).isEqualTo("android.emulation.control.EmulatorController/sendMouse")
        assertWithMessage(button.name).that(shortDebugString(request)).contains(expected)
      }

      fakeUi.mouse.release()

      fakeEmulator.getNextGrpcCall(2.seconds).apply {
        assertWithMessage(button.name).that(methodName).isEqualTo("android.emulation.control.EmulatorController/sendMouse")
        assertWithMessage(button.name).that(shortDebugString(request)).doesNotContain("button") // No button should be pressed
      }
    }
  }

  @Test
  fun testMouseDragHasPressedButton() {
    view = emulatorViewRule.newEmulatorView()
    fakeUi = FakeUi(createScrollPane(view))

    fakeUi.root.size = Dimension(200, 300)
    fakeUi.layoutAndDispatchEvents()
    getStreamScreenshotCallAndWaitForFrame()
    fakeUi.render()

    fakeUi.mouse.press(135, 190, FakeMouse.Button.RIGHT)

    fakeEmulator.getNextGrpcCall(2.seconds).apply {
      assertThat(methodName).isEqualTo("android.emulation.control.EmulatorController/sendMouse")
    }

    fakeUi.mouse.dragDelta(5, 0)

    fakeEmulator.getNextGrpcCall(2.seconds).apply {
      assertThat(methodName).isEqualTo("android.emulation.control.EmulatorController/sendMouse")
      assertThat(shortDebugString(request)).contains("buttons: 2")
    }

    fakeUi.mouse.release()

    fakeEmulator.getNextGrpcCall(2.seconds).apply {
      assertThat(methodName).isEqualTo("android.emulation.control.EmulatorController/sendMouse")
      assertThat(shortDebugString(request)).doesNotContain("button") // No button should be pressed
    }
  }

  @Test
  fun testMouseWheel() {
    view = emulatorViewRule.newEmulatorView()
    fakeUi = FakeUi(createScrollPane(view))

    fakeUi.root.size = Dimension(200, 300)
    fakeUi.layoutAndDispatchEvents()
    getStreamScreenshotCallAndWaitForFrame()
    fakeUi.render()

    var call: GrpcCallRecord? = null
    for (rotation in listOf(1, 1, -1, -1)) {
      fakeUi.mouse.wheel(100, 100, rotation)
      if (call == null) {
        call = fakeEmulator.getNextGrpcCall(2.seconds)
        assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/injectWheel")
      }
      assertThat(shortDebugString(call.getNextRequest(2.seconds))).isEqualTo("dy: ${-rotation * 120}")
    }
  }

  @Test
  fun testKeysForMnemonicsShouldNotBeConsumed() {
    view = emulatorViewRule.newEmulatorView()

    val altMPressedEvent = KeyEvent(view, KEY_PRESSED, System.nanoTime(), KeyEvent.ALT_DOWN_MASK, VK_M, VK_M.toChar())
    KeyboardFocusManager.getCurrentKeyboardFocusManager().redispatchEvent(view, altMPressedEvent)
    assertThat(altMPressedEvent.isConsumed).isFalse()

    val altMReleasedEvent = KeyEvent(view, KEY_RELEASED, System.nanoTime(), KeyEvent.ALT_DOWN_MASK, VK_M, VK_M.toChar())
    KeyboardFocusManager.getCurrentKeyboardFocusManager().redispatchEvent(view, altMReleasedEvent)
    assertThat(altMReleasedEvent.isConsumed).isFalse()
  }

  @Test
  fun testKeyPreprocessingSkippedWhenHardwareInputEnabled() {
    view = emulatorViewRule.newEmulatorView()
    emulatorViewRule.executeAction("android.streaming.hardware.input", view)
    assertThat(view.skipKeyEventDispatcher(KeyEvent(view, KEY_PRESSED, System.nanoTime(), 0, VK_M, VK_M.toChar()))).isTrue()
  }

  @Test
  fun testKeyPreprocessingNotSkippedForActionTogglingHardwareInput() {
    view = emulatorViewRule.newEmulatorView()
    emulatorViewRule.executeAction("android.streaming.hardware.input", view)
    val keymapManager = KeymapManager.getInstance()
    keymapManager.activeKeymap.addShortcut("android.streaming.hardware.input", KeyboardShortcut.fromString("control shift J"))

    assertThat(view.skipKeyEventDispatcher(KeyEvent(view, KEY_PRESSED, System.nanoTime(),
                                                    KeyEvent.SHIFT_DOWN_MASK or KeyEvent.CTRL_DOWN_MASK, KeyEvent.VK_J,
                                                    KeyEvent.CHAR_UNDEFINED))).isFalse()
  }

  @Test
  fun testCtrlAndAlphabeticalKeysSentWhenHardwareInputEnabled() {
    view = emulatorViewRule.newEmulatorView()
    fakeUi = FakeUi(createScrollPane(view))

    emulatorViewRule.executeAction("android.streaming.hardware.input", view)
    fakeUi.keyboard.setFocus(view)

    fakeUi.keyboard.press(VK_CONTROL)
    fakeEmulator.getNextGrpcCall(2.seconds).apply {
      assertThat(methodName).isEqualTo("android.emulation.control.EmulatorController/sendKey")
      assertThat(shortDebugString(request)).isEqualTo("key: \"Control\"")
    }

    fakeUi.keyboard.press(KeyEvent.VK_S)
    fakeEmulator.getNextGrpcCall(2.seconds).apply {
      assertThat(methodName).isEqualTo("android.emulation.control.EmulatorController/sendKey")
      assertThat(shortDebugString(request)).isEqualTo("key: \"s\"")
    }

    fakeUi.keyboard.release(KeyEvent.VK_S)
    fakeEmulator.getNextGrpcCall(2.seconds).apply {
      assertThat(methodName).isEqualTo("android.emulation.control.EmulatorController/sendKey")
      assertThat(shortDebugString(request)).isEqualTo("eventType: keyup key: \"s\"")
    }

    fakeUi.keyboard.release(VK_CONTROL)
    fakeEmulator.getNextGrpcCall(2.seconds).apply {
      assertThat(methodName).isEqualTo("android.emulation.control.EmulatorController/sendKey")
      assertThat(shortDebugString(request)).isEqualTo("eventType: keyup key: \"Control\"")
    }
  }

  @Test
  fun testHideCameraNotificationDuringHardwareInput() {
    view = emulatorViewRule.newEmulatorView()
    val panel = NotificationHolderPanel(view)
    val container = HeadlessRootPaneContainer(panel)
    container.rootPane.size = Dimension(200, 300)
    fakeUi = FakeUi(container.rootPane, 1.0)

    // Activate the virtual scene camera
    focusManager.focusOwner = view
    fakeEmulator.virtualSceneCameraActive = true
    waitForCondition(200, MILLISECONDS) {
      fakeUi.findComponent<EditorNotificationPanel>() != null
    }

    // Enable hardware input
    emulatorViewRule.executeAction("android.streaming.hardware.input", view)

    // Check if notification panel is disappeared
    waitForCondition(200, MILLISECONDS) {
      fakeUi.findComponent<EditorNotificationPanel>() == null
    }

    // Disable hardware input
    emulatorViewRule.executeAction("android.streaming.hardware.input", view)

    // Check if notification panel is re-appeared
    waitForCondition(200, MILLISECONDS) {
      fakeUi.findComponent<EditorNotificationPanel>() != null
    }
  }

  @Test
  fun testCameraNotificationHasOperatingMessageWhenHardwareInputDisabledWithShift() {
    view = emulatorViewRule.newEmulatorView()
    val panel = NotificationHolderPanel(view)
    val container = HeadlessRootPaneContainer(panel)
    container.rootPane.size = Dimension(200, 300)
    fakeUi = FakeUi(container.rootPane, 1.0)

    // Enable hardware input
    emulatorViewRule.executeAction("android.streaming.hardware.input", view)

    // Activate the virtual scene camera
    focusManager.focusOwner = view
    fakeEmulator.virtualSceneCameraActive = true

    // Disable hardware input with shift key
    executeStreamingAction("android.streaming.hardware.input", view, emulatorViewRule.project, modifiers=SHIFT_DOWN_MASK)

    // Check if notification panel is disappeared
    waitForCondition(200, MILLISECONDS) {
      fakeUi.findComponent<EditorNotificationPanel>() != null
    }

    assertThat(fakeUi.findComponent<EditorNotificationPanel>()?.text).isEqualTo(
        "Move camera with WASDQE keys, rotate with mouse or arrow keys")
  }

  @Test
  fun testDisableMultiTouchDuringHardwareInput() {
    view = emulatorViewRule.newEmulatorView()
    fakeUi = FakeUi(createScrollPane(view), 2.0)

    fakeUi.root.size = Dimension(200, 300)
    fakeUi.layoutAndDispatchEvents()
    getStreamScreenshotCallAndWaitForFrame()

    val mousePosition = Point(150, 75)
    val pointerInfo = mock<PointerInfo>()
    whenever(pointerInfo.location).thenReturn(mousePosition)
    mouseInfo.whenever<Any?> { MouseInfo.getPointerInfo() }.thenReturn(pointerInfo)

    // Start multitouch
    fakeUi.keyboard.setFocus(view)
    fakeUi.mouse.moveTo(mousePosition)
    fakeUi.keyboard.press(VK_CONTROL)
    fakeUi.layoutAndDispatchEvents()
    assertAppearance("MultiTouch1")
    var call = fakeEmulator.getNextGrpcCall(2.seconds)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendMouse")
    assertThat(shortDebugString(call.request)).isEqualTo("x: 1274 y: 744")

    // Enable hardware input
    emulatorViewRule.executeAction("android.streaming.hardware.input", view)

    // Check if multitouch indicator is hidden
    fakeUi.layoutAndDispatchEvents()
    assertAppearance("MultiTouch4")

    // Check if mouse down event is generated instead of touch
    fakeUi.mouse.press(mousePosition)
    call = fakeEmulator.getNextGrpcCall(2.seconds)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendMouse")
    assertThat(shortDebugString(call.request)).isEqualTo("x: 1274 y: 744 buttons: 1")

    // Disable hardware input
    executeStreamingAction("android.streaming.hardware.input", view, emulatorViewRule.project, modifiers=CTRL_DOWN_MASK)

    // Check if multitouch indicator is shown
    fakeUi.layoutAndDispatchEvents()
    assertAppearance("MultiTouch2")

    // Drag mouse
    mousePosition.x -= 20
    mousePosition.y += 20
    fakeUi.mouse.dragTo(mousePosition)

    // Check if touch event is generated
    call = fakeEmulator.getNextGrpcCall(2.seconds)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendTouch")
    assertThat(shortDebugString(call.request)).isEqualTo(
        "touches { x: 1058 y: 960 pressure: 1024 expiration: NEVER_EXPIRE }" +
        " touches { x: 381 y: 1999 identifier: 1 pressure: 1024 expiration: NEVER_EXPIRE }")
  }

  @Test
  fun testMetaKeysReleasedWhenHardwareInputDisabled() {
    view = emulatorViewRule.newEmulatorView()
    fakeUi = FakeUi(createScrollPane(view), 2.0)

    // Enable hardware input
    emulatorViewRule.executeAction("android.streaming.hardware.input", view)

    // Press Ctrl
    focusManager.focusOwner = view
    fakeUi.keyboard.press(VK_CONTROL)

    fakeEmulator.getNextGrpcCall(2.seconds).apply {
      assertThat(methodName).isEqualTo("android.emulation.control.EmulatorController/sendKey")
      assertThat(shortDebugString(request)).isEqualTo("key: \"Control\"")
    }

    // Disable hardware input
    emulatorViewRule.executeAction("android.streaming.hardware.input", view)

    fakeEmulator.getNextGrpcCall(2.seconds).apply {
      assertThat(methodName).isEqualTo("android.emulation.control.EmulatorController/sendKey")
      assertThat(shortDebugString(request)).isEqualTo("eventType: keyup key: \"Control\"")
    }
  }

  @Test
  fun testMetaKeysReleasedWhenLostFocusDuringHardwareInput() {
    view = emulatorViewRule.newEmulatorView()
    fakeUi = FakeUi(createScrollPane(view), 2.0)

    // Enable hardware input
    emulatorViewRule.executeAction("android.streaming.hardware.input", view)

    // Press Ctrl
    focusManager.focusOwner = view
    fakeUi.keyboard.press(VK_CONTROL)

    fakeEmulator.getNextGrpcCall(2.seconds).apply {
      assertThat(methodName).isEqualTo("android.emulation.control.EmulatorController/sendKey")
      assertThat(shortDebugString(request)).isEqualTo("key: \"Control\"")
    }

    // Lose focus
    focusManager.focusOwner = null

    fakeEmulator.getNextGrpcCall(2.seconds).apply {
      assertThat(methodName).isEqualTo("android.emulation.control.EmulatorController/sendKey")
      assertThat(shortDebugString(request)).isEqualTo("eventType: keyup key: \"Control\"")
    }
  }

  @Test
  fun testMetricsCollection() {
    view = emulatorViewRule.newEmulatorView()
    fakeUi = FakeUi(createScrollPane(view), 2.0)

    fakeUi.root.size = Dimension(200, 300)
    fakeUi.layoutAndDispatchEvents()
    getStreamScreenshotCallAndWaitForFrame()

    Disposer.dispose(view)
    val mirroringSessions = usageTrackerRule.deviceMirroringSessions()
    assertThat(mirroringSessions.size).isEqualTo(1)
    val mirroringSessionPattern = Regex(
      "kind: DEVICE_MIRRORING_SESSION\n" +
      "studio_session_id: \".+\"\n" +
      "product_details \\{\n" +
      "\\s*version: \".*\"\n" +
      "}\n" +
      "device_info \\{\n" +
      "\\s*device_type: LOCAL_EMULATOR\n" +
      "}\n" +
      "ide_brand: ANDROID_STUDIO\n" +
      "idea_is_internal: \\w+\n" +
      "device_mirroring_session \\{\n" +
      "\\s*device_kind: VIRTUAL\n" +
      "\\s*duration_sec: \\d+\n" +
      "\\s*first_frame_delay_millis: \\d+\n" +
      "}\n"
    )
    assertThat(mirroringSessionPattern.matches(mirroringSessions[0].toString())).isTrue()
  }

  private fun createScrollPane(view: Component): JScrollPane {
    return JScrollPane(view).apply {
      border = null
      isFocusable = true
    }
  }

  @Throws(TimeoutException::class)
  private fun getStreamScreenshotCallAndWaitForFrame(): GrpcCallRecord {
    val call = fakeEmulator.getNextGrpcCall(2.seconds)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/streamScreenshot")
    call.waitForResponse(2.seconds)
    waitForFrame()
    return call
  }

  @Throws(TimeoutException::class)
  private fun waitForFrame() {
    waitForCondition(2.seconds) {
      view.emulator.connectionState == ConnectionState.CONNECTED &&
      view.displayOrientationQuadrants == fakeEmulator.displayRotation.number &&
      view.currentPosture?.posture == fakeEmulator.devicePosture
      fakeEmulator.frameNumber > 0u && renderAndGetFrameNumber() == fakeEmulator.frameNumber
    }
  }

  private fun renderAndGetFrameNumber(): UInt {
    fakeUi.render() // The frame number may get updated as a result of rendering.
    return view.frameNumber
  }

  private fun assertAppearance(goldenImageName: String) {
    val image = fakeUi.render()
    ImageDiffUtil.assertImageSimilar(getGoldenFile(goldenImageName), image, 0.0)
  }

  private fun getGoldenFile(name: String): Path =
      TestUtils.resolveWorkspacePathUnchecked("${GOLDEN_FILE_PATH}/${name}.png")
}

private fun UsageTrackerRule.deviceMirroringSessions(): List<AndroidStudioEvent> =
    usages.filter { it.studioEvent.kind == AndroidStudioEvent.EventKind.DEVICE_MIRRORING_SESSION }.map { it.studioEvent }

private fun getKeyStroke(action: String) =
    KeymapUtil.getKeyStroke(KeymapUtil.getActiveKeymapShortcuts(action))!!

private const val GOLDEN_FILE_PATH = "tools/adt/idea/streaming/testData/EmulatorViewTest/golden"
