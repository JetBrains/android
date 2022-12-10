/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.streaming.device

import com.android.testutils.ImageDiffUtil
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.testutils.TestUtils
import com.android.tools.adtui.ImageUtils
import com.android.tools.adtui.actions.ZoomType
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.replaceKeyboardFocusManager
import com.android.tools.idea.concurrency.AndroidExecutors
import com.android.tools.idea.concurrency.waitForCondition
import com.android.tools.idea.executeCapturingLoggedErrors
import com.android.tools.idea.streaming.AbstractDisplayView
import com.android.tools.idea.streaming.DeviceMirroringSettings
import com.android.tools.idea.streaming.device.AndroidKeyEventActionType.ACTION_DOWN
import com.android.tools.idea.streaming.device.AndroidKeyEventActionType.ACTION_DOWN_AND_UP
import com.android.tools.idea.streaming.device.AndroidKeyEventActionType.ACTION_UP
import com.android.tools.idea.streaming.device.StartClipboardSyncMessage
import com.android.tools.idea.streaming.emulator.EmulatorView
import com.android.tools.idea.streaming.executeDeviceAction
import com.android.tools.idea.testing.AndroidExecutorsRule
import com.android.tools.idea.testing.mockStatic
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.ClipboardSynchronizer
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
import com.intellij.openapi.actionSystem.IdeActions.ACTION_SELECT_ALL
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ConcurrencyUtil
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import java.awt.Component
import java.awt.Dimension
import java.awt.KeyboardFocusManager
import java.awt.MouseInfo
import java.awt.Point
import java.awt.PointerInfo
import java.awt.Rectangle
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.event.KeyEvent
import java.awt.event.KeyEvent.KEY_PRESSED
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
import java.awt.event.KeyEvent.VK_PAGE_DOWN
import java.awt.event.KeyEvent.VK_PAGE_UP
import java.awt.event.KeyEvent.VK_RIGHT
import java.awt.event.KeyEvent.VK_SHIFT
import java.awt.event.KeyEvent.VK_TAB
import java.awt.event.KeyEvent.VK_UP
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JScrollPane
import kotlin.time.Duration.Companion.seconds

/**
 * Tests for [DeviceView] and [DeviceClient].
 */
@RunsInEdt
internal class DeviceViewTest {
  private val agentRule = FakeScreenSharingAgentRule()
  private val androidExecutorsRule = AndroidExecutorsRule(workerThreadExecutor = Executors.newCachedThreadPool())
  @get:Rule
  val ruleChain = RuleChain(agentRule, androidExecutorsRule, EdtRule())
  private lateinit var device: FakeScreenSharingAgentRule.FakeDevice
  private lateinit var view: DeviceView
  private lateinit var fakeUi: FakeUi
  private var savedClipboardSynchronizationState = false

  private val testRootDisposable
    get() = agentRule.testRootDisposable
  private val project
    get() = agentRule.project
  private val agent
    get() = device.agent

  @Before
  fun setUp() {
    savedClipboardSynchronizationState = DeviceMirroringSettings.getInstance().synchronizeClipboard
    DeviceMirroringSettings.getInstance().synchronizeClipboard = false
    device = agentRule.connectDevice("Pixel 5", 30, Dimension(1080, 2340), "arm64-v8a")
  }

  @After
  fun tearDown() {
    DeviceMirroringSettings.getInstance().synchronizeClipboard = savedClipboardSynchronizationState
  }

  @Test
  fun testFrameListener() {
    if (!isFFmpegAvailableToTest()) {
      return
    }
    createDeviceView(200, 300, 2.0)
    var frameListenerCalls = 0

    val frameListener = AbstractDisplayView.FrameListener { _, _, _, _ -> ++frameListenerCalls }

    view.addFrameListener(frameListener)
    waitForCondition(2, TimeUnit.SECONDS) { fakeUi.render(); view.frameNumber == agent.frameNumber }

    assertThat(frameListenerCalls).isGreaterThan(0)
    assertThat(frameListenerCalls).isEqualTo(view.frameNumber)
    val framesBeforeRemoving = view.frameNumber
    view.removeFrameListener(frameListener)

    runBlocking { agent.renderDisplay(1) }
    waitForCondition(2, TimeUnit.SECONDS) { fakeUi.render(); view.frameNumber == agent.frameNumber }

    // If removal didn't work, the frame number part would fail here.
    assertThat(view.frameNumber).isGreaterThan(framesBeforeRemoving)
    assertThat(frameListenerCalls).isEqualTo(framesBeforeRemoving)
  }

  @Test
  fun testResizingRotationAndMouseInput() {
    if (!isFFmpegAvailableToTest()) {
      return
    }
    createDeviceView(200, 300, 2.0)
    assertThat(agent.commandLine).matches("CLASSPATH=$DEVICE_PATH_BASE/$SCREEN_SHARING_AGENT_JAR_NAME app_process" +
                                          " $DEVICE_PATH_BASE com.android.tools.screensharing.Main" +
                                          " --socket=screen-sharing-agent-\\d+ --max_size=400,600 --flags=1 --codec=vp8")
    waitForFrame()
    assertThat(view.displayRectangle).isEqualTo(Rectangle(61, 0, 277, 600))
    assertThat(view.displayOrientationQuadrants).isEqualTo(0)

    // Check resizing.
    fakeUi.resizeRoot(100, 90)
    assertThat(getNextControlMessageAndWaitForFrame()).isEqualTo(SetMaxVideoResolutionMessage(200, 180))
    assertThat(view.displayRectangle).isEqualTo(Rectangle(58, 0, 83, 180))

    // Check mouse input in various orientations.
    val expectedCoordinates = listOf(
      MotionEventMessage.Pointer(292, 786, 0),
      MotionEventMessage.Pointer(813, 1436, 0),
      MotionEventMessage.Pointer(898, 941, 0),
      MotionEventMessage.Pointer(311, 1409, 0),
      MotionEventMessage.Pointer(800, 1566, 0),
      MotionEventMessage.Pointer(279, 916, 0),
      MotionEventMessage.Pointer(193, 1409, 0),
      MotionEventMessage.Pointer(780, 941, 0),
    )
    for (i in 0 until 4) {
      assertAppearance("Rotation${i * 90}")
      // Check mouse input.
      fakeUi.mouse.press(40, 30)
      assertThat(getNextControlMessageAndWaitForFrame()).isEqualTo(
          MotionEventMessage(listOf(expectedCoordinates[i * 2]), MotionEventMessage.ACTION_DOWN, 0))

      fakeUi.mouse.dragTo(60, 55)
      assertThat(getNextControlMessageAndWaitForFrame()).isEqualTo(
          MotionEventMessage(listOf(expectedCoordinates[i * 2 + 1]), MotionEventMessage.ACTION_MOVE, 0))

      fakeUi.mouse.release()
      assertThat(getNextControlMessageAndWaitForFrame()).isEqualTo(
          MotionEventMessage(listOf(expectedCoordinates[i * 2 + 1]), MotionEventMessage.ACTION_UP, 0))

      executeDeviceAction("android.device.rotate.left", view, project)
      assertThat(getNextControlMessageAndWaitForFrame()).isEqualTo(SetDeviceOrientationMessage((i + 1) % 4))
    }

    // Check dragging over the edge of the device screen.
    fakeUi.mouse.press(40, 50)
    assertThat(agent.getNextControlMessage(2, TimeUnit.SECONDS)).isEqualTo(
        MotionEventMessage(listOf(MotionEventMessage.Pointer(292, 1306, 0)), MotionEventMessage.ACTION_DOWN, 0))
    fakeUi.mouse.dragTo(90, 60)
    assertThat(agent.getNextControlMessage(2, TimeUnit.SECONDS)).isEqualTo(
        MotionEventMessage(listOf(MotionEventMessage.Pointer(1079, 1566, 0)), MotionEventMessage.ACTION_MOVE, 0))
    assertThat(agent.getNextControlMessage(2, TimeUnit.SECONDS)).isEqualTo(
        MotionEventMessage(listOf(MotionEventMessage.Pointer(1079, 1566, 0)), MotionEventMessage.ACTION_UP, 0))
    fakeUi.mouse.release()

    // Check mouse leaving the device view while dragging.
    fakeUi.mouse.press(50, 40)
    assertThat(agent.getNextControlMessage(2, TimeUnit.SECONDS)).isEqualTo(
        MotionEventMessage(listOf(MotionEventMessage.Pointer(553, 1046, 0)), MotionEventMessage.ACTION_DOWN, 0))
    fakeUi.mouse.dragTo(55, 10)
    assertThat(agent.getNextControlMessage(2, TimeUnit.SECONDS)).isEqualTo(
        MotionEventMessage(listOf(MotionEventMessage.Pointer(683, 266, 0)), MotionEventMessage.ACTION_MOVE, 0))
    fakeUi.mouse.dragTo(60, -10)
    assertThat(agent.getNextControlMessage(2, TimeUnit.SECONDS)).isEqualTo(
        MotionEventMessage(listOf(MotionEventMessage.Pointer(813, 0, 0)), MotionEventMessage.ACTION_MOVE, 0))
    assertThat(agent.getNextControlMessage(2, TimeUnit.SECONDS)).isEqualTo(
        MotionEventMessage(listOf(MotionEventMessage.Pointer(813, 0, 0)), MotionEventMessage.ACTION_UP, 0))
    fakeUi.mouse.release()
  }

  @Test
  fun testMultiTouch() {
    if (!isFFmpegAvailableToTest()) {
      return
    }
    createDeviceView(50, 100, 2.0)
    waitForFrame()
    assertThat(view.displayRectangle).isEqualTo(Rectangle(4, 0, 92, 200))

    val mousePosition = Point(30, 30)
    val pointerInfo = mock<PointerInfo>()
    whenever(pointerInfo.location).thenReturn(mousePosition)
    val mouseInfoMock = mockStatic<MouseInfo>(testRootDisposable)
    mouseInfoMock.whenever<Any?> { MouseInfo.getPointerInfo() }.thenReturn(pointerInfo)

    fakeUi.keyboard.setFocus(view)
    fakeUi.mouse.moveTo(mousePosition)
    fakeUi.keyboard.press(VK_CONTROL)
    fakeUi.layoutAndDispatchEvents()
    assertAppearance("MultiTouch1")

    fakeUi.mouse.press(mousePosition)
    assertThat(getNextControlMessageAndWaitForFrame()).isEqualTo(
        MotionEventMessage(listOf(MotionEventMessage.Pointer(663, 707, 0), MotionEventMessage.Pointer(417, 1633, 1)),
                           MotionEventMessage.ACTION_DOWN, 0))
    assertAppearance("MultiTouch2")

    mousePosition.x -= 10
    mousePosition.y += 10
    fakeUi.mouse.dragTo(mousePosition)
    assertThat(getNextControlMessageAndWaitForFrame()).isEqualTo(
        MotionEventMessage(listOf(MotionEventMessage.Pointer(428, 941, 0), MotionEventMessage.Pointer(652, 1399, 1)),
                           MotionEventMessage.ACTION_MOVE, 0))
    assertAppearance("MultiTouch3")

    fakeUi.mouse.release()
    assertThat(getNextControlMessageAndWaitForFrame()).isEqualTo(
        MotionEventMessage(listOf(MotionEventMessage.Pointer(428, 941, 0), MotionEventMessage.Pointer(652, 1399, 1)),
                           MotionEventMessage.ACTION_UP, 0))

    fakeUi.keyboard.release(VK_CONTROL)
    assertAppearance("MultiTouch4")
  }

  @Test
  fun testKeyboardInput() {
    if (!isFFmpegAvailableToTest()) {
      return
    }
    createDeviceView(150, 250, 1.5)
    waitForFrame()

    // Check keyboard input.
    fakeUi.keyboard.setFocus(view)
    for (c in ' '..'~') {
      fakeUi.keyboard.type(c.code)
      assertThat(agent.getNextControlMessage(2, TimeUnit.SECONDS)).isEqualTo(TextInputMessage(c.toString()))
    }

    val controlCharacterCases = mapOf(
      VK_ENTER to AKEYCODE_ENTER,
      VK_TAB to AKEYCODE_TAB,
      VK_ESCAPE to AKEYCODE_ESCAPE,
      VK_BACK_SPACE to AKEYCODE_DEL,
      VK_DELETE to if (SystemInfo.isMac) AKEYCODE_DEL else AKEYCODE_FORWARD_DEL,
    )
    for ((hostKeyStroke, androidKeyCode) in controlCharacterCases) {
      fakeUi.keyboard.pressAndRelease(hostKeyStroke)
      assertThat(agent.getNextControlMessage(2, TimeUnit.SECONDS)).isEqualTo(KeyEventMessage(ACTION_DOWN, androidKeyCode, 0))
      assertThat(agent.getNextControlMessage(2, TimeUnit.SECONDS)).isEqualTo(KeyEventMessage(ACTION_UP, androidKeyCode, 0))
    }

    val trivialKeyStrokeCases = mapOf(
      VK_LEFT to AKEYCODE_DPAD_LEFT,
      VK_KP_LEFT to AKEYCODE_DPAD_LEFT,
      VK_RIGHT to AKEYCODE_DPAD_RIGHT,
      VK_KP_RIGHT to AKEYCODE_DPAD_RIGHT,
      VK_UP to AKEYCODE_DPAD_UP,
      VK_KP_UP to AKEYCODE_DPAD_UP,
      VK_DOWN to AKEYCODE_DPAD_DOWN,
      VK_KP_DOWN to AKEYCODE_DPAD_DOWN,
      VK_HOME to AKEYCODE_MOVE_HOME,
      VK_END to AKEYCODE_MOVE_END,
      VK_PAGE_DOWN to AKEYCODE_PAGE_DOWN,
      VK_PAGE_UP to AKEYCODE_PAGE_UP,
    )
    for ((hostKeyStroke, androidKeyCode) in trivialKeyStrokeCases) {
      fakeUi.keyboard.pressAndRelease(hostKeyStroke)
      assertThat(agent.getNextControlMessage(2, TimeUnit.SECONDS)).isEqualTo(KeyEventMessage(ACTION_DOWN_AND_UP, androidKeyCode, 0))
    }

    val action = ACTION_CUT
    val keyStrokeCases = listOf(
      Triple(getKeyStroke(action), AKEYCODE_CUT, 0),
      Triple(getKeyStroke(ACTION_COPY), AKEYCODE_COPY, 0),
      Triple(getKeyStroke(ACTION_PASTE), AKEYCODE_PASTE, 0),
      Triple(getKeyStroke(ACTION_SELECT_ALL), AKEYCODE_A, AMETA_CTRL_ON),
      Triple(getKeyStroke(ACTION_EDITOR_MOVE_CARET_LEFT_WITH_SELECTION), AKEYCODE_DPAD_LEFT, AMETA_SHIFT_ON),
      Triple(getKeyStroke(ACTION_EDITOR_MOVE_CARET_RIGHT_WITH_SELECTION), AKEYCODE_DPAD_RIGHT, AMETA_SHIFT_ON),
      Triple(getKeyStroke(ACTION_EDITOR_MOVE_CARET_UP_WITH_SELECTION), AKEYCODE_DPAD_UP, AMETA_SHIFT_ON),
      Triple(getKeyStroke(ACTION_EDITOR_MOVE_CARET_DOWN_WITH_SELECTION), AKEYCODE_DPAD_DOWN, AMETA_SHIFT_ON),
      Triple(getKeyStroke(ACTION_EDITOR_PREVIOUS_WORD), AKEYCODE_DPAD_LEFT, AMETA_CTRL_ON),
      Triple(getKeyStroke(ACTION_EDITOR_NEXT_WORD), AKEYCODE_DPAD_RIGHT, AMETA_CTRL_ON),
      Triple(getKeyStroke(ACTION_EDITOR_PREVIOUS_WORD_WITH_SELECTION), AKEYCODE_DPAD_LEFT, AMETA_CTRL_SHIFT_ON),
      Triple(getKeyStroke(ACTION_EDITOR_NEXT_WORD_WITH_SELECTION), AKEYCODE_DPAD_RIGHT, AMETA_CTRL_SHIFT_ON),
      Triple(getKeyStroke(ACTION_EDITOR_MOVE_LINE_START_WITH_SELECTION), AKEYCODE_MOVE_HOME, AMETA_SHIFT_ON),
      Triple(getKeyStroke(ACTION_EDITOR_MOVE_LINE_END_WITH_SELECTION), AKEYCODE_MOVE_END, AMETA_SHIFT_ON),
      Triple(getKeyStroke(ACTION_EDITOR_MOVE_CARET_PAGE_UP_WITH_SELECTION), AKEYCODE_PAGE_UP, AMETA_SHIFT_ON),
      Triple(getKeyStroke(ACTION_EDITOR_MOVE_CARET_PAGE_DOWN_WITH_SELECTION), AKEYCODE_PAGE_DOWN, AMETA_SHIFT_ON),
      Triple(getKeyStroke(ACTION_EDITOR_TEXT_START), AKEYCODE_MOVE_HOME, AMETA_CTRL_ON),
      Triple(getKeyStroke(ACTION_EDITOR_TEXT_END), AKEYCODE_MOVE_END, AMETA_CTRL_ON),
      Triple(getKeyStroke(ACTION_EDITOR_TEXT_START_WITH_SELECTION), AKEYCODE_MOVE_HOME, AMETA_CTRL_SHIFT_ON),
      Triple(getKeyStroke(ACTION_EDITOR_TEXT_END_WITH_SELECTION), AKEYCODE_MOVE_END, AMETA_CTRL_SHIFT_ON),
    )
    for ((hostKeyStroke, androidKeyCode, androidMetaState) in keyStrokeCases) {
      fakeUi.keyboard.pressForModifiers(hostKeyStroke.modifiers)
      fakeUi.keyboard.pressAndRelease(hostKeyStroke.keyCode)
      fakeUi.keyboard.releaseForModifiers(hostKeyStroke.modifiers)
      when (androidMetaState) {
        AMETA_SHIFT_ON -> {
          assertThat(agent.getNextControlMessage(2, TimeUnit.SECONDS)).isEqualTo(
              KeyEventMessage(ACTION_DOWN, AKEYCODE_SHIFT_LEFT, AMETA_SHIFT_ON))
        }
        AMETA_CTRL_ON -> {
          assertThat(agent.getNextControlMessage(2, TimeUnit.SECONDS)).isEqualTo(
              KeyEventMessage(ACTION_DOWN, AKEYCODE_CTRL_LEFT, AMETA_CTRL_ON))
        }
        AMETA_CTRL_SHIFT_ON -> {
          assertThat(agent.getNextControlMessage(2, TimeUnit.SECONDS)).isEqualTo(
              KeyEventMessage(ACTION_DOWN, AKEYCODE_SHIFT_LEFT, AMETA_SHIFT_ON))
          assertThat(agent.getNextControlMessage(2, TimeUnit.SECONDS)).isEqualTo(
              KeyEventMessage(ACTION_DOWN, AKEYCODE_CTRL_LEFT, AMETA_CTRL_SHIFT_ON))
        }
        else -> {}
      }

      assertThat(agent.getNextControlMessage(2, TimeUnit.SECONDS)).isEqualTo(
          KeyEventMessage(ACTION_DOWN_AND_UP, androidKeyCode, androidMetaState))

      when (androidMetaState) {
        AMETA_SHIFT_ON -> {
          assertThat(agent.getNextControlMessage(2, TimeUnit.SECONDS)).isEqualTo(
              KeyEventMessage(ACTION_UP, AKEYCODE_SHIFT_LEFT, 0))
        }
        AMETA_CTRL_ON -> {
          assertThat(agent.getNextControlMessage(2, TimeUnit.SECONDS)).isEqualTo(
              KeyEventMessage(ACTION_UP, AKEYCODE_CTRL_LEFT, 0))
        }
        AMETA_CTRL_SHIFT_ON -> {
          assertThat(agent.getNextControlMessage(2, TimeUnit.SECONDS)).isEqualTo(
              KeyEventMessage(ACTION_UP, AKEYCODE_CTRL_LEFT, AMETA_SHIFT_ON))
          assertThat(agent.getNextControlMessage(2, TimeUnit.SECONDS)).isEqualTo(
              KeyEventMessage(ACTION_UP, AKEYCODE_SHIFT_LEFT, 0))
        }
        else -> {}
      }
    }

    val mockFocusManager: KeyboardFocusManager = mock()
    whenever(mockFocusManager.redispatchEvent(any(Component::class.java), any(KeyEvent::class.java))).thenCallRealMethod()
    replaceKeyboardFocusManager(mockFocusManager, testRootDisposable)
    // Shift+Tab should trigger a forward local focus traversal.
    with(fakeUi.keyboard) {
      setFocus(view)
      press(VK_SHIFT)
      pressAndRelease(VK_TAB)
      release(VK_SHIFT)
    }
    val arg1 = ArgumentCaptor.forClass(EmulatorView::class.java)
    val arg2 = ArgumentCaptor.forClass(KeyEvent::class.java)
    Mockito.verify(mockFocusManager, Mockito.atLeast(1)).processKeyEvent(arg1.capture(), arg2.capture())
    val tabEvent = arg2.allValues.firstOrNull { it.id == KEY_PRESSED && it.keyCode == VK_TAB && it.modifiersEx == 0 }
    assertThat(tabEvent).isNotNull()
  }

  @Test
  fun testZoom() {
    if (!isFFmpegAvailableToTest()) {
      return
    }
    createDeviceView(100, 200, 2.0)
    waitForFrame()

    // Check zoom.
    assertThat(view.scale).isWithin(1e-4).of(fakeUi.screenScale * fakeUi.root.height / device.displaySize.height)
    assertThat(view.canZoomIn()).isTrue()
    assertThat(view.canZoomOut()).isFalse()
    assertThat(view.canZoomToActual()).isTrue()
    assertThat(view.canZoomToFit()).isFalse()

    view.zoom(ZoomType.IN)
    fakeUi.layoutAndDispatchEvents()
    assertThat(getNextControlMessageAndWaitForFrame()).isEqualTo(SetMaxVideoResolutionMessage(270, 586))
    assertThat(view.canZoomIn()).isTrue()
    assertThat(view.canZoomOut()).isTrue()
    assertThat(view.canZoomToActual()).isTrue()
    assertThat(view.canZoomToFit()).isTrue()

    view.zoom(ZoomType.ACTUAL)
    fakeUi.layoutAndDispatchEvents()
    assertThat(getNextControlMessageAndWaitForFrame()).isEqualTo(
        SetMaxVideoResolutionMessage(device.displaySize.width, device.displaySize.height))
    assertThat(view.canZoomIn()).isTrue()
    assertThat(view.canZoomOut()).isTrue()
    assertThat(view.canZoomToActual()).isFalse()
    assertThat(view.canZoomToFit()).isTrue()
    val image = ImageUtils.scale(fakeUi.render(view), 0.125)
    ImageDiffUtil.assertImageSimilar(getGoldenFile("Zoom1"), image, 0.0)

    view.zoom(ZoomType.OUT)
    fakeUi.layoutAndDispatchEvents()
    assertThat(getNextControlMessageAndWaitForFrame()).isEqualTo(
        SetMaxVideoResolutionMessage(device.displaySize.width / 2, device.displaySize.height / 2))
    assertThat(view.canZoomIn()).isTrue()
    assertThat(view.canZoomOut()).isTrue()
    assertThat(view.canZoomToActual()).isTrue()
    assertThat(view.canZoomToFit()).isTrue()

    view.zoom(ZoomType.FIT)
    fakeUi.layoutAndDispatchEvents()
    assertThat(getNextControlMessageAndWaitForFrame()).isEqualTo(SetMaxVideoResolutionMessage(200, 400))
    assertThat(view.canZoomIn()).isTrue()
    assertThat(view.canZoomOut()).isFalse()
    assertThat(view.canZoomToActual()).isTrue()
    assertThat(view.canZoomToFit()).isFalse()

    // Check clockwise rotation in zoomed-in state.
    for (i in 0 until 4) {
      view.zoom(ZoomType.IN)
      fakeUi.layoutAndDispatchEvents()
      val expected = when {
        view.displayOrientationQuadrants % 2 == 0 -> SetMaxVideoResolutionMessage(270, 586)
        SystemInfo.isMac -> SetMaxVideoResolutionMessage(234, 372)
        else -> SetMaxVideoResolutionMessage(234, 400)
      }
      assertThat(getNextControlMessageAndWaitForFrame()).isEqualTo(expected)
      executeDeviceAction("android.device.rotate.right", view, project)
      assertThat(getNextControlMessageAndWaitForFrame()).isEqualTo(SetDeviceOrientationMessage(3 - i))
      fakeUi.layoutAndDispatchEvents()
      assertThat(view.canZoomOut()).isFalse() // zoom-in mode cancelled by the rotation.
      assertThat(view.canZoomToFit()).isFalse()
      assertThat(getNextControlMessageAndWaitForFrame()).isEqualTo(SetMaxVideoResolutionMessage(200, 400))
    }
  }

  @Test
  fun testClipboardSynchronization() {
    if (!isFFmpegAvailableToTest()) {
      return
    }
    createDeviceView(100, 200, 1.5)
    waitForFrame()

    val settings = DeviceMirroringSettings.getInstance()
    settings.synchronizeClipboard = true
    assertThat(agent.getNextControlMessage(2, TimeUnit.SECONDS)).isInstanceOf(StartClipboardSyncMessage::class.java)
    CopyPasteManager.getInstance().setContents(StringSelection("host clipboard"))
    assertThat(agent.getNextControlMessage(2, TimeUnit.SECONDS)).isEqualTo(
        StartClipboardSyncMessage(settings.maxSyncedClipboardLength, "host clipboard"))
    agent.clipboard = "device clipboard"
    waitForCondition(2, TimeUnit.SECONDS) { ClipboardSynchronizer.getInstance().getData(DataFlavor.stringFlavor) == "device clipboard" }
    settings.synchronizeClipboard = false
    assertThat(agent.getNextControlMessage(2, TimeUnit.SECONDS)).isEqualTo(StopClipboardSyncMessage())
  }

  @Test
  fun testAgentCrashAndReconnect() {
    if (!isFFmpegAvailableToTest()) {
      return
    }
    createDeviceView(500, 1000, screenScale = 1.0)
    waitForFrame()
    assertThat(view.displayRectangle).isEqualTo(Rectangle(19, 0, 462, 1000))
    assertThat(view.displayOrientationQuadrants).isEqualTo(0)

    // Simulate crash of the screen sharing agent.
    runBlocking { agent.crash() }
    val errorMessage = fakeUi.getComponent<JLabel>()
    waitForCondition(2, TimeUnit.SECONDS) { fakeUi.isShowing(errorMessage) }
    assertThat(errorMessage.text).isEqualTo("Lost connection to the device. See the error log.")

    fakeUi.layoutAndDispatchEvents()
    val button = fakeUi.getComponent<JButton>()
    assertThat(fakeUi.isShowing(button)).isTrue()
    assertThat(button.text).isEqualTo("Reconnect")
    // Check handling of the agent crash on startup.
    agent.crashOnStart = true
    errorMessage.text = ""
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue() // Let all ongoing activity finish before attempting to reconnect.
    val loggedErrors = executeCapturingLoggedErrors {
      fakeUi.clickOn(button)
      waitForCondition(5, TimeUnit.SECONDS) { errorMessage.text.isNotEmpty() }
      for (i in 1 until 3) {
        ConcurrencyUtil.awaitQuiescence(AndroidExecutors.getInstance().workerThreadExecutor as ThreadPoolExecutor, 5, TimeUnit.SECONDS)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
      }
    }
    assertThat(errorMessage.text).isEqualTo("Failed to initialize the device agent. See the error log.")
    assertThat(button.text).isEqualTo("Retry")
    assertThat(loggedErrors).containsExactly("Failed to initialize the screen sharing agent")

    // Check reconnection.
    agent.crashOnStart = false
    fakeUi.clickOn(button)
    waitForFrame()
    assertThat(view.displayRectangle).isEqualTo(Rectangle(19, 0, 462, 1000))
    assertThat(view.displayOrientationQuadrants).isEqualTo(0)
  }

  @Test
  fun testDeviceDisconnection() {
    if (!isFFmpegAvailableToTest()) {
      return
    }
    createDeviceView(500, 1000, screenScale = 1.0)
    waitForFrame()

    agentRule.disconnectDevice(device)

    waitForCondition(15.seconds) { !view.isConnected }
  }

  private fun createDeviceView(width: Int, height: Int, screenScale: Double = 2.0) {
    val deviceState = device.deviceState
    val deviceName = "${deviceState.manufacturer} ${deviceState.model} API ${deviceState.buildVersionSdk}"
    view = DeviceView(testRootDisposable, device.serialNumber, deviceState.cpuAbi, deviceName, UNKNOWN_ORIENTATION, agentRule.project)
    fakeUi = FakeUi(wrapInScrollPane(view, width, height), screenScale)
    waitForCondition(15, TimeUnit.SECONDS) { agent.isRunning }
  }

  private fun wrapInScrollPane(view: Component, width: Int, height: Int): JScrollPane {
    return JBScrollPane(view).apply {
      border = null
      isFocusable = true
      size = Dimension(width, height)
    }
  }

  private fun assertAppearance(goldenImageName: String) {
    val image = fakeUi.render()
    ImageDiffUtil.assertImageSimilar(getGoldenFile(goldenImageName), image, 0.0)
  }

  private fun getGoldenFile(name: String): Path =
    TestUtils.resolveWorkspacePathUnchecked("$GOLDEN_FILE_PATH/${name}.png")

  private fun getNextControlMessageAndWaitForFrame(): ControlMessage {
    val message = agent.getNextControlMessage(5, TimeUnit.SECONDS)
    waitForFrame()
    return message
  }

  /** Waits for all video frames to be received. */
  private fun waitForFrame() {
    waitForCondition(2, TimeUnit.SECONDS) { view.isConnected && agent.frameNumber > 0 && renderAndGetFrameNumber() == agent.frameNumber }
  }

  private fun renderAndGetFrameNumber(): Int {
    fakeUi.render() // The frame number may get updated as a result of rendering.
    return view.frameNumber
  }

  private fun FakeUi.resizeRoot(width: Int, height: Int) {
    root.size = Dimension(width, height)
    layoutAndDispatchEvents()
  }
}

private fun getKeyStroke(action: String) =
  KeymapUtil.getKeyStroke(KeymapUtil.getActiveKeymapShortcuts(action))!!

private const val GOLDEN_FILE_PATH = "tools/adt/idea/streaming/testData/DeviceViewTest/golden"
