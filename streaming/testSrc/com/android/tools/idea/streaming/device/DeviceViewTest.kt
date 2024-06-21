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

import com.android.adblib.DevicePropertyNames
import com.android.testutils.ImageDiffUtil
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.eq
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.testutils.TestUtils
import com.android.testutils.truth.PathSubject.assertThat
import com.android.testutils.waitForCondition
import com.android.tools.adtui.ImageUtils
import com.android.tools.adtui.actions.ZoomType
import com.android.tools.adtui.swing.FakeKeyboardFocusManager
import com.android.tools.adtui.swing.FakeMouse
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.replaceKeyboardFocusManager
import com.android.tools.analytics.UsageTrackerRule
import com.android.tools.analytics.crash.CrashReport
import com.android.tools.idea.concurrency.AndroidExecutors
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.streaming.DeviceMirroringSettings
import com.android.tools.idea.streaming.core.AbstractDisplayView
import com.android.tools.idea.streaming.core.PRIMARY_DISPLAY_ID
import com.android.tools.idea.streaming.device.AndroidKeyEventActionType.ACTION_DOWN
import com.android.tools.idea.streaming.device.AndroidKeyEventActionType.ACTION_DOWN_AND_UP
import com.android.tools.idea.streaming.device.AndroidKeyEventActionType.ACTION_UP
import com.android.tools.idea.streaming.device.DeviceView.Companion.ANDROID_SCROLL_ADJUSTMENT_FACTOR
import com.android.tools.idea.streaming.executeStreamingAction
import com.android.tools.idea.streaming.extractText
import com.android.tools.idea.testing.AndroidExecutorsRule
import com.android.tools.idea.testing.CrashReporterRule
import com.android.tools.idea.testing.executeCapturingLoggedErrors
import com.android.tools.idea.testing.executeCapturingLoggedWarnings
import com.android.tools.idea.testing.flags.override
import com.android.tools.idea.testing.mockStatic
import com.android.tools.idea.testing.override
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.DEVICE_MIRRORING_ABNORMAL_AGENT_TERMINATION
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.DEVICE_MIRRORING_SESSION
import com.intellij.ide.ClipboardSynchronizer
import com.intellij.ide.DataManager
import com.intellij.ide.impl.HeadlessDataManager
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
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.TestDataProvider
import com.intellij.testFramework.assertInstanceOf
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ConcurrencyUtil
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap
import kotlinx.coroutines.runBlocking
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.atLeast
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify
import java.awt.Component
import java.awt.Dimension
import java.awt.MouseInfo
import java.awt.Point
import java.awt.PointerInfo
import java.awt.Rectangle
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.event.KeyEvent
import java.awt.event.KeyEvent.ALT_DOWN_MASK
import java.awt.event.KeyEvent.CHAR_UNDEFINED
import java.awt.event.KeyEvent.CTRL_DOWN_MASK
import java.awt.event.KeyEvent.KEY_PRESSED
import java.awt.event.KeyEvent.SHIFT_DOWN_MASK
import java.awt.event.KeyEvent.VK_BACK_SPACE
import java.awt.event.KeyEvent.VK_CONTROL
import java.awt.event.KeyEvent.VK_DELETE
import java.awt.event.KeyEvent.VK_DOWN
import java.awt.event.KeyEvent.VK_END
import java.awt.event.KeyEvent.VK_ENTER
import java.awt.event.KeyEvent.VK_ESCAPE
import java.awt.event.KeyEvent.VK_HOME
import java.awt.event.KeyEvent.VK_J
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
import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit.SECONDS
import javax.swing.JButton
import javax.swing.JEditorPane
import javax.swing.JScrollPane
import kotlin.time.Duration.Companion.seconds

/**
 * Tests for [DeviceView] and [DeviceClient].
 */
@RunsInEdt
internal class DeviceViewTest {

  private val agentRule = FakeScreenSharingAgentRule()
  private val androidExecutorsRule = AndroidExecutorsRule(workerThreadExecutor = Executors.newCachedThreadPool())
  private val crashReporterRule = CrashReporterRule()
  @get:Rule
  val ruleChain = RuleChain(agentRule, crashReporterRule, androidExecutorsRule, ClipboardSynchronizationDisablementRule(), EdtRule())
  @get:Rule
  val usageTrackerRule = UsageTrackerRule()
  private lateinit var device: FakeScreenSharingAgentRule.FakeDevice
  private lateinit var view: DeviceView
  private lateinit var fakeUi: FakeUi
  private lateinit var focusManager: FakeKeyboardFocusManager

  private val testRootDisposable
    get() = agentRule.disposable
  private val project
    get() = agentRule.project
  private val agent
    get() = device.agent

  @Before
  fun setUp() {
    BitRateManager.getInstance().clear()
    device = agentRule.connectDevice("Pixel 5", 32, Dimension(1080, 2340))
    (DataManager.getInstance() as HeadlessDataManager).setTestDataProvider(TestDataProvider(project), testRootDisposable)
    focusManager = FakeKeyboardFocusManager(testRootDisposable)
  }

  fun tearDown() {
    BitRateManager.getInstance().clear()
  }

  @Test
  fun testFrameListener() {
    createDeviceView(200, 300, 2.0)
    var frameListenerCalls = 0u

    val frameListener = AbstractDisplayView.FrameListener { _, _, _, _ -> ++frameListenerCalls }

    view.addFrameListener(frameListener)
    waitForCondition(2.seconds) { fakeUi.render(); view.frameNumber == agent.getFrameNumber(PRIMARY_DISPLAY_ID) }

    assertThat(frameListenerCalls).isGreaterThan(0u)
    assertThat(frameListenerCalls).isEqualTo(view.frameNumber)
    val framesBeforeRemoving = view.frameNumber
    view.removeFrameListener(frameListener)

    runBlocking { agent.renderDisplay(PRIMARY_DISPLAY_ID, 1) }
    waitForCondition(2.seconds) { fakeUi.render(); view.frameNumber == agent.getFrameNumber(PRIMARY_DISPLAY_ID) }

    // If removal didn't work, the frame number part would fail here.
    assertThat(view.frameNumber).isGreaterThan(framesBeforeRemoving)
    assertThat(frameListenerCalls).isEqualTo(framesBeforeRemoving)
  }

  @Test
  fun testResizingRotationAndMouseInput() {
    createDeviceView(200, 300, 2.0)
    assertThat(agent.commandLine).matches("CLASSPATH=$DEVICE_PATH_BASE/$SCREEN_SHARING_AGENT_JAR_NAME app_process" +
                                          " $DEVICE_PATH_BASE com.android.tools.screensharing.Main" +
                                          " --socket=screen-sharing-agent-\\d+ --max_size=400,600 --flags=\\d+")
    waitForFrame()
    assertThat(view.displayRectangle).isEqualTo(Rectangle(61, 0, 277, 600))
    assertThat(view.displayOrientationQuadrants).isEqualTo(0)

    // Check resizing.
    fakeUi.resizeRoot(100, 90)
    assertThat(getNextControlMessageAndWaitForFrame()).isEqualTo(
        SetMaxVideoResolutionMessage(view.displayId, Dimension(200, 180)))
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
      fakeUi.mouse.moveTo(40, 30)
      assertThat(getNextControlMessageAndWaitForFrame()).isEqualTo(
          MotionEventMessage(listOf(expectedCoordinates[i * 2]), MotionEventMessage.ACTION_HOVER_MOVE, 0, 0, 0))

      fakeUi.mouse.press(40, 30)
      assertThat(getNextControlMessageAndWaitForFrame()).isEqualTo(
          MotionEventMessage(listOf(expectedCoordinates[i * 2]), MotionEventMessage.ACTION_HOVER_EXIT, 0, 0, 0))
      assertThat(getNextControlMessageAndWaitForFrame()).isEqualTo(
          MotionEventMessage(listOf(expectedCoordinates[i * 2]), MotionEventMessage.ACTION_DOWN, 0, 0, 0))

      fakeUi.mouse.dragTo(60, 55)
      assertThat(getNextControlMessageAndWaitForFrame()).isEqualTo(
          MotionEventMessage(listOf(expectedCoordinates[i * 2 + 1]), MotionEventMessage.ACTION_MOVE, 0, 0, 0))

      fakeUi.mouse.release()
      assertThat(getNextControlMessageAndWaitForFrame()).isEqualTo(
          MotionEventMessage(listOf(expectedCoordinates[i * 2 + 1]), MotionEventMessage.ACTION_UP, 0, 0, 0))

      fakeUi.mouse.wheel(60, 55, -1)  // Vertical scrolling is backward on Android
      val verticalAxisValues = Int2FloatOpenHashMap(1).apply {
        put(MotionEventMessage.AXIS_VSCROLL, ANDROID_SCROLL_ADJUSTMENT_FACTOR)
      }
      val verticalScrollPointer = expectedCoordinates[i * 2 + 1].copy(axisValues = verticalAxisValues)
      assertThat(getNextControlMessageAndWaitForFrame()).isEqualTo(
          MotionEventMessage(listOf(verticalScrollPointer), MotionEventMessage.ACTION_SCROLL, 0, 0, 0))

      // Java fakes horizontal scrolling by pretending shift was held down during the scroll.
      fakeUi.keyboard.press(VK_SHIFT)
      fakeUi.mouse.wheel(60, 55, 1)
      fakeUi.keyboard.release(VK_SHIFT)
      val horizontalAxisValues = Int2FloatOpenHashMap(1).apply {
        put(MotionEventMessage.AXIS_HSCROLL, ANDROID_SCROLL_ADJUSTMENT_FACTOR)
      }
      val horizontalScrollPointer = expectedCoordinates[i * 2 + 1].copy(axisValues = horizontalAxisValues)
      assertThat(getNextControlMessageAndWaitForFrame()).isEqualTo(
          MotionEventMessage(listOf(horizontalScrollPointer), MotionEventMessage.ACTION_SCROLL, 0, 0, 0))

      executeStreamingAction("android.device.rotate.left", view, project)
      assertThat(getNextControlMessageAndWaitForFrame()).isEqualTo(SetDeviceOrientationMessage((i + 1) % 4))
    }

    // Check dragging over the edge of the device screen.
    fakeUi.mouse.press(40, 50)
    assertThat(agent.getNextControlMessage(2.seconds)).isEqualTo(
        MotionEventMessage(listOf(MotionEventMessage.Pointer(292, 1306, 0)), MotionEventMessage.ACTION_DOWN, 0, 0, 0))
    fakeUi.mouse.dragTo(90, 60)
    assertThat(agent.getNextControlMessage(2.seconds)).isEqualTo(
        MotionEventMessage(listOf(MotionEventMessage.Pointer(1079, 1566, 0)), MotionEventMessage.ACTION_MOVE, 0, 0, 0))
    assertThat(agent.getNextControlMessage(2.seconds)).isEqualTo(
        MotionEventMessage(listOf(MotionEventMessage.Pointer(1079, 1566, 0)), MotionEventMessage.ACTION_UP, 0, 0, 0))
    fakeUi.mouse.release()

    // Check mouse leaving the device view while dragging.
    fakeUi.mouse.press(50, 40)
    assertThat(agent.getNextControlMessage(2.seconds)).isEqualTo(
        MotionEventMessage(listOf(MotionEventMessage.Pointer(553, 1046, 0)), MotionEventMessage.ACTION_DOWN, 0, 0, 0))
    fakeUi.mouse.dragTo(55, 10)
    assertThat(agent.getNextControlMessage(2.seconds)).isEqualTo(
        MotionEventMessage(listOf(MotionEventMessage.Pointer(683, 266, 0)), MotionEventMessage.ACTION_MOVE, 0, 0, 0))
    fakeUi.mouse.dragTo(60, -10)
    assertThat(agent.getNextControlMessage(2.seconds)).isEqualTo(
        MotionEventMessage(listOf(MotionEventMessage.Pointer(813, 0, 0)), MotionEventMessage.ACTION_MOVE, 0, 0, 0))
    assertThat(agent.getNextControlMessage(2.seconds)).isEqualTo(
        MotionEventMessage(listOf(MotionEventMessage.Pointer(813, 0, 0)), MotionEventMessage.ACTION_UP, 0, 0, 0))
    fakeUi.mouse.release()
  }

  @Test
  fun testUpsideDownMouseInput() {
    createDeviceView(200, 300, 2.0)
    waitForFrame()
    assertThat(view.displayRectangle).isEqualTo(Rectangle(61, 0, 277, 600))
    assertThat(view.displayOrientationQuadrants).isEqualTo(0)

    executeStreamingAction("android.device.rotate.right", view, project)
    assertThat(getNextControlMessageAndWaitForFrame()).isEqualTo(SetDeviceOrientationMessage(3))
    executeStreamingAction("android.device.rotate.right", view, project)
    assertThat(getNextControlMessageAndWaitForFrame()).isEqualTo(SetDeviceOrientationMessage(2))
    assertThat(view.displayOrientationQuadrants).isEqualTo(2)
    assertThat(view.displayOrientationCorrectionQuadrants).isEqualTo(0)

    fakeUi.mouse.press(40, 30)
    assertThat(getNextControlMessageAndWaitForFrame()).isEqualTo(
        MotionEventMessage(listOf(MotionEventMessage.Pointer(1007, 2107, 0)), MotionEventMessage.ACTION_DOWN, 0, 0, 0))
    fakeUi.mouse.release()
    assertThat(getNextControlMessageAndWaitForFrame()).isEqualTo(
        MotionEventMessage(listOf(MotionEventMessage.Pointer(1007, 2107, 0)), MotionEventMessage.ACTION_UP, 0, 0, 0))

    runBlocking { agent.setDisplayOrientationCorrection(PRIMARY_DISPLAY_ID, 2) }
    waitForFrame()
    assertThat(view.displayOrientationQuadrants).isEqualTo(2)
    assertThat(view.displayOrientationCorrectionQuadrants).isEqualTo(2)
    fakeUi.mouse.press(40, 30)
    assertThat(getNextControlMessageAndWaitForFrame()).isEqualTo(
        MotionEventMessage(listOf(MotionEventMessage.Pointer(235, 1008, 0)), MotionEventMessage.ACTION_DOWN, 0, 0, 0))
    fakeUi.mouse.release()
    assertThat(getNextControlMessageAndWaitForFrame()).isEqualTo(
        MotionEventMessage(listOf(MotionEventMessage.Pointer(235, 1008, 0)), MotionEventMessage.ACTION_UP, 0, 0, 0))
  }

  @Test
  fun testRoundWatch() {
    device = agentRule.connectDevice("Pixel Watch", 30, Dimension(384, 384), roundDisplay = true, abi = "armeabi-v7a",
                                     additionalDeviceProperties = mapOf(DevicePropertyNames.RO_BUILD_CHARACTERISTICS to "nosdcard,watch"))

    createDeviceView(100, 150, 2.0)
    assertThat(agent.commandLine).matches("CLASSPATH=$DEVICE_PATH_BASE/$SCREEN_SHARING_AGENT_JAR_NAME app_process" +
                                          " $DEVICE_PATH_BASE com.android.tools.screensharing.Main" +
                                          " --socket=screen-sharing-agent-\\d+ --max_size=200,300 --flags=\\d+")
    waitForFrame()
    assertThat(view.displayRectangle).isEqualTo(Rectangle(0, 50, 200, 200))
    assertThat(view.displayOrientationQuadrants).isEqualTo(0)
    assertAppearance("RoundWatch1")
  }

  @Test
  fun testMultiTouch() {
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
    assertThat(getNextControlMessageAndWaitForFrame()).isEqualTo(
        MotionEventMessage(listOf(MotionEventMessage.Pointer(663, 707, 0)), MotionEventMessage.ACTION_HOVER_MOVE, 0, 0, 0))
    fakeUi.keyboard.press(VK_CONTROL)
    fakeUi.layoutAndDispatchEvents()
    assertAppearance("MultiTouch1")

    fakeUi.mouse.press(mousePosition)
    assertThat(getNextControlMessageAndWaitForFrame()).isEqualTo(
        MotionEventMessage(listOf(MotionEventMessage.Pointer(663, 707, 0)), MotionEventMessage.ACTION_HOVER_EXIT, 0, 0, 0))
    assertThat(getNextControlMessageAndWaitForFrame()).isEqualTo(
        MotionEventMessage(listOf(MotionEventMessage.Pointer(663, 707, 0), MotionEventMessage.Pointer(417, 1633, 1)),
                           MotionEventMessage.ACTION_DOWN, 0, 0, 0))
    assertAppearance("MultiTouch2")

    mousePosition.x -= 10
    mousePosition.y += 10
    fakeUi.mouse.dragTo(mousePosition)
    assertThat(getNextControlMessageAndWaitForFrame()).isEqualTo(
        MotionEventMessage(listOf(MotionEventMessage.Pointer(428, 941, 0), MotionEventMessage.Pointer(652, 1399, 1)),
                           MotionEventMessage.ACTION_MOVE, 0, 0, 0))
    assertAppearance("MultiTouch3")

    fakeUi.mouse.release()
    assertThat(getNextControlMessageAndWaitForFrame()).isEqualTo(
        MotionEventMessage(listOf(MotionEventMessage.Pointer(428, 941, 0), MotionEventMessage.Pointer(652, 1399, 1)),
                           MotionEventMessage.ACTION_UP, 0, 0, 0))

    fakeUi.keyboard.release(VK_CONTROL)
    assertAppearance("MultiTouch4")
  }

  @Test
  fun testKeyboardInput() {
    createDeviceView(150, 250, 1.5)
    waitForFrame()

    // Check keyboard input.
    fakeUi.keyboard.setFocus(view)
    for (c in ' '..'~') {
      fakeUi.keyboard.type(c.code)
      assertThat(agent.getNextControlMessage(2.seconds)).isEqualTo(TextInputMessage(c.toString()))
    }

    val trivialKeyStrokeCases = mapOf(
      VK_ENTER to AKEYCODE_ENTER,
      VK_TAB to AKEYCODE_TAB,
      VK_ESCAPE to AKEYCODE_ESCAPE,
      VK_BACK_SPACE to AKEYCODE_DEL,
      VK_DELETE to if (SystemInfo.isMac) AKEYCODE_DEL else AKEYCODE_FORWARD_DEL,
      VK_LEFT to AKEYCODE_DPAD_LEFT,
      VK_KP_LEFT to AKEYCODE_DPAD_LEFT,
      VK_RIGHT to AKEYCODE_DPAD_RIGHT,
      VK_KP_RIGHT to AKEYCODE_DPAD_RIGHT,
      VK_DOWN to AKEYCODE_DPAD_DOWN,
      VK_KP_DOWN to AKEYCODE_DPAD_DOWN,
      VK_UP to AKEYCODE_DPAD_UP,
      VK_KP_UP to AKEYCODE_DPAD_UP,
      VK_HOME to AKEYCODE_MOVE_HOME,
      VK_END to AKEYCODE_MOVE_END,
      VK_PAGE_DOWN to AKEYCODE_PAGE_DOWN,
      VK_PAGE_UP to AKEYCODE_PAGE_UP,
    )
    for ((hostKeyStroke, androidKeyCode) in trivialKeyStrokeCases) {
      fakeUi.keyboard.pressAndRelease(hostKeyStroke)
      assertThat(agent.getNextControlMessage(2.seconds)).isEqualTo(KeyEventMessage(ACTION_DOWN_AND_UP, androidKeyCode, 0))
    }

    val action = ACTION_CUT
    val keyStrokeCases = listOf(
      Triple(getKeyStroke(action), AKEYCODE_CUT, 0),
      Triple(getKeyStroke(ACTION_COPY), AKEYCODE_COPY, 0),
      Triple(getKeyStroke(ACTION_PASTE), AKEYCODE_PASTE, 0),
      Triple(getKeyStroke(ACTION_SELECT_ALL), AKEYCODE_A, AMETA_CTRL_ON),
      Triple(getKeyStroke(ACTION_EDITOR_MOVE_CARET_LEFT_WITH_SELECTION), AKEYCODE_DPAD_LEFT, AMETA_SHIFT_ON),
      Triple(getKeyStroke(ACTION_EDITOR_MOVE_CARET_RIGHT_WITH_SELECTION), AKEYCODE_DPAD_RIGHT, AMETA_SHIFT_ON),
      Triple(getKeyStroke(ACTION_EDITOR_MOVE_CARET_DOWN_WITH_SELECTION), AKEYCODE_DPAD_DOWN, AMETA_SHIFT_ON),
      Triple(getKeyStroke(ACTION_EDITOR_MOVE_CARET_UP_WITH_SELECTION), AKEYCODE_DPAD_UP, AMETA_SHIFT_ON),
      Triple(getKeyStroke(ACTION_EDITOR_PREVIOUS_WORD), AKEYCODE_DPAD_LEFT, AMETA_CTRL_ON),
      Triple(getKeyStroke(ACTION_EDITOR_NEXT_WORD), AKEYCODE_DPAD_RIGHT, AMETA_CTRL_ON),
      Triple(getKeyStroke(ACTION_EDITOR_PREVIOUS_WORD_WITH_SELECTION), AKEYCODE_DPAD_LEFT, AMETA_CTRL_SHIFT_ON),
      Triple(getKeyStroke(ACTION_EDITOR_NEXT_WORD_WITH_SELECTION), AKEYCODE_DPAD_RIGHT, AMETA_CTRL_SHIFT_ON),
      Triple(getKeyStroke(ACTION_EDITOR_MOVE_LINE_START_WITH_SELECTION), AKEYCODE_MOVE_HOME, AMETA_SHIFT_ON),
      Triple(getKeyStroke(ACTION_EDITOR_MOVE_LINE_END_WITH_SELECTION), AKEYCODE_MOVE_END, AMETA_SHIFT_ON),
      Triple(getKeyStroke(ACTION_EDITOR_MOVE_CARET_PAGE_DOWN_WITH_SELECTION), AKEYCODE_PAGE_DOWN, AMETA_SHIFT_ON),
      Triple(getKeyStroke(ACTION_EDITOR_MOVE_CARET_PAGE_UP_WITH_SELECTION), AKEYCODE_PAGE_UP, AMETA_SHIFT_ON),
      Triple(getKeyStroke(ACTION_EDITOR_TEXT_START), AKEYCODE_MOVE_HOME, AMETA_CTRL_ON),
      Triple(getKeyStroke(ACTION_EDITOR_TEXT_END), AKEYCODE_MOVE_END, AMETA_CTRL_ON),
      Triple(getKeyStroke(ACTION_EDITOR_TEXT_START_WITH_SELECTION), AKEYCODE_MOVE_HOME, AMETA_CTRL_SHIFT_ON),
      Triple(getKeyStroke(ACTION_EDITOR_TEXT_END_WITH_SELECTION), AKEYCODE_MOVE_END, AMETA_CTRL_SHIFT_ON),
      Triple(getKeyStroke(ACTION_UNDO), AKEYCODE_Z, AMETA_CTRL_ON),
      Triple(getKeyStroke(ACTION_REDO), AKEYCODE_Z, AMETA_CTRL_SHIFT_ON),
    )
    for ((hostKeyStroke, androidKeyCode, androidMetaState) in keyStrokeCases) {
      fakeUi.keyboard.pressForModifiers(hostKeyStroke.modifiers)
      fakeUi.keyboard.pressAndRelease(hostKeyStroke.keyCode)
      fakeUi.keyboard.releaseForModifiers(hostKeyStroke.modifiers)
      when (androidMetaState) {
        AMETA_SHIFT_ON -> {
          assertThat(agent.getNextControlMessage(2.seconds)).isEqualTo(KeyEventMessage(ACTION_DOWN, AKEYCODE_SHIFT_LEFT, AMETA_SHIFT_ON))
        }
        AMETA_CTRL_ON -> {
          assertThat(agent.getNextControlMessage(2.seconds)).isEqualTo(KeyEventMessage(ACTION_DOWN, AKEYCODE_CTRL_LEFT, AMETA_CTRL_ON))
        }
        AMETA_CTRL_SHIFT_ON -> {
          assertThat(agent.getNextControlMessage(2.seconds)).isEqualTo(KeyEventMessage(ACTION_DOWN, AKEYCODE_SHIFT_LEFT, AMETA_SHIFT_ON))
          assertThat(agent.getNextControlMessage(2.seconds)).isEqualTo(KeyEventMessage(ACTION_DOWN, AKEYCODE_CTRL_LEFT, AMETA_CTRL_SHIFT_ON))
        }
        else -> {}
      }

      assertThat(agent.getNextControlMessage(2.seconds)).isEqualTo(KeyEventMessage(ACTION_DOWN_AND_UP, androidKeyCode, androidMetaState))

      when (androidMetaState) {
        AMETA_SHIFT_ON -> {
          assertThat(agent.getNextControlMessage(2.seconds)).isEqualTo(KeyEventMessage(ACTION_UP, AKEYCODE_SHIFT_LEFT, 0))
        }
        AMETA_CTRL_ON -> {
          assertThat(agent.getNextControlMessage(2.seconds)).isEqualTo(KeyEventMessage(ACTION_UP, AKEYCODE_CTRL_LEFT, 0))
        }
        AMETA_CTRL_SHIFT_ON -> {
          assertThat(agent.getNextControlMessage(2.seconds)).isEqualTo(KeyEventMessage(ACTION_UP, AKEYCODE_CTRL_LEFT, AMETA_SHIFT_ON))
          assertThat(agent.getNextControlMessage(2.seconds)).isEqualTo(KeyEventMessage(ACTION_UP, AKEYCODE_SHIFT_LEFT, 0))
        }
        else -> {}
      }
    }

    focusManager = spy(focusManager)
    replaceKeyboardFocusManager(focusManager, testRootDisposable)
    focusManager.focusOwner = view
    focusManager.processKeyEvent(view, KeyEvent(view, KEY_PRESSED, System.currentTimeMillis(), SHIFT_DOWN_MASK, VK_TAB, VK_TAB.toChar()))
    verify(focusManager, atLeast(1)).focusNextComponent(eq(view))
  }

  @Test
  fun testZoom() {
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
    assertThat(getNextControlMessageAndWaitForFrame()).isEqualTo(
        SetMaxVideoResolutionMessage(view.displayId, Dimension(270, 586)))
    assertThat(view.canZoomIn()).isTrue()
    assertThat(view.canZoomOut()).isTrue()
    assertThat(view.canZoomToActual()).isTrue()
    assertThat(view.canZoomToFit()).isTrue()

    view.zoom(ZoomType.ACTUAL)
    fakeUi.layoutAndDispatchEvents()
    assertThat(getNextControlMessageAndWaitForFrame()).isEqualTo(
        SetMaxVideoResolutionMessage(view.displayId, device.displaySize))
    assertThat(view.canZoomIn()).isTrue()
    assertThat(view.canZoomOut()).isTrue()
    assertThat(view.canZoomToActual()).isFalse()
    assertThat(view.canZoomToFit()).isTrue()
    val image = ImageUtils.scale(fakeUi.render(view), 0.125)
    ImageDiffUtil.assertImageSimilar(getGoldenFile("Zoom1"), image, 0.0)

    view.zoom(ZoomType.OUT)
    fakeUi.layoutAndDispatchEvents()
    assertThat(getNextControlMessageAndWaitForFrame()).isEqualTo(
        SetMaxVideoResolutionMessage(view.displayId, Dimension(device.displaySize.width / 2, device.displaySize.height / 2)))
    assertThat(view.canZoomIn()).isTrue()
    assertThat(view.canZoomOut()).isTrue()
    assertThat(view.canZoomToActual()).isTrue()
    assertThat(view.canZoomToFit()).isTrue()

    view.zoom(ZoomType.FIT)
    fakeUi.layoutAndDispatchEvents()
    assertThat(getNextControlMessageAndWaitForFrame()).isEqualTo(
        SetMaxVideoResolutionMessage(view.displayId, Dimension(200, 400)))
    assertThat(view.canZoomIn()).isTrue()
    assertThat(view.canZoomOut()).isFalse()
    assertThat(view.canZoomToActual()).isTrue()
    assertThat(view.canZoomToFit()).isFalse()

    // Check clockwise rotation in zoomed-in state.
    for (i in 0 until 4) {
      view.zoom(ZoomType.IN)
      fakeUi.layoutAndDispatchEvents()
      val expected = when {
        view.displayOrientationQuadrants % 2 == 0 -> SetMaxVideoResolutionMessage(view.displayId, Dimension(270, 586))
        SystemInfo.isMac && !isRunningInBazelTest() -> SetMaxVideoResolutionMessage(view.displayId, Dimension(294, 372))
        else -> SetMaxVideoResolutionMessage(view.displayId, Dimension(294, 400))
      }
      assertThat(getNextControlMessageAndWaitForFrame()).isEqualTo(expected)
      executeStreamingAction("android.device.rotate.right", view, project)
      assertThat(getNextControlMessageAndWaitForFrame()).isEqualTo(SetDeviceOrientationMessage(3 - i))
      fakeUi.layoutAndDispatchEvents()
      assertThat(view.canZoomOut()).isFalse() // zoom-in mode cancelled by the rotation.
      assertThat(view.canZoomToFit()).isFalse()
      assertThat(getNextControlMessageAndWaitForFrame()).isEqualTo(
          SetMaxVideoResolutionMessage(view.displayId, Dimension(200, 400)))
    }
  }

  @Test
  fun testClipboardSynchronization() {
    createDeviceView(100, 200, 1.5)
    waitForFrame()

    val settings = DeviceMirroringSettings.getInstance()
    settings.synchronizeClipboard = true
    assertThat(agent.getNextControlMessage(2.seconds)).isInstanceOf(StartClipboardSyncMessage::class.java)
    CopyPasteManager.getInstance().setContents(StringSelection("host clipboard"))
    assertThat(agent.getNextControlMessage(2.seconds)).isEqualTo(
        StartClipboardSyncMessage(settings.maxSyncedClipboardLength, "host clipboard"))
    agent.clipboard = "device clipboard"
    waitForCondition(2.seconds) { ClipboardSynchronizer.getInstance().getData(DataFlavor.stringFlavor) == "device clipboard" }
    settings.synchronizeClipboard = false
    assertThat(agent.getNextControlMessage(2.seconds)).isEqualTo(StopClipboardSyncMessage.instance)
  }

  @Test
  fun testBitRateReduction() {
    createDeviceView(500, 1000, screenScale = 1.0)
    waitForFrame()

    agent.bitRate = 2000000
    runBlocking { agent.renderDisplay(PRIMARY_DISPLAY_ID, 1) }
    waitForFrame()
    assertThat(BitRateManager.getInstance().toXmlString()).isEqualTo(
        "<BitRateManager>\n" +
        "  <option name=\"bitRateTrackers\">\n" +
        "    <map>\n" +
        "      <entry key=\"Google|Pixel 5|arm64-v8a|32\">\n" +
        "        <value>\n" +
        "          <BitRateTracker>\n" +
        "            <candidates>\n" +
        "              <CandidateBitRate>\n" +
        "                <option name=\"bitRate\" value=\"2000000\" />\n" +
        "                <option name=\"score\" value=\"334\" />\n" +
        "              </CandidateBitRate>\n" +
        "            </candidates>\n" +
        "          </BitRateTracker>\n" +
        "        </value>\n" +
        "      </entry>\n" +
        "    </map>\n" +
        "  </option>\n" +
        "</BitRateManager>")

    ::BIT_RATE_STABILITY_FRAME_COUNT.override(3, testRootDisposable) // Replace with a smaller value to speed up test.
    agent.bitRate = 5000000
    for (i in 0 until BIT_RATE_STABILITY_FRAME_COUNT) {
      runBlocking { agent.renderDisplay(PRIMARY_DISPLAY_ID, 1) }
      waitForFrame()
    }
    assertThat(BitRateManager.getInstance().toXmlString()).isEqualTo(
        "<BitRateManager>\n" +
        "  <option name=\"bitRateTrackers\">\n" +
        "    <map>\n" +
        "      <entry key=\"Google|Pixel 5|arm64-v8a|32\">\n" +
        "        <value>\n" +
        "          <BitRateTracker>\n" +
        "            <candidates>\n" +
        "              <CandidateBitRate>\n" +
        "                <option name=\"bitRate\" value=\"2000000\" />\n" +
        "                <option name=\"score\" value=\"318\" />\n" +
        "              </CandidateBitRate>\n" +
        "            </candidates>\n" +
        "          </BitRateTracker>\n" +
        "        </value>\n" +
        "      </entry>\n" +
        "    </map>\n" +
        "  </option>\n" +
        "</BitRateManager>")
  }

  @Test
  fun testAgentCrashAndReconnect() {
    createDeviceView(500, 1000, screenScale = 1.0)
    waitForFrame()
    assertThat(view.displayRectangle).isEqualTo(Rectangle(19, 0, 462, 1000))
    assertThat(view.displayOrientationQuadrants).isEqualTo(0)

    // Simulate crash of the screen sharing agent.
    runBlocking {
      agent.writeToStderr("Crash is near\n")
      agent.writeToStderr("Kaput\n")
      agent.crash()
    }
    val errorMessage = fakeUi.getComponent<JEditorPane>()
    waitForCondition(2.seconds) { fakeUi.isShowing(errorMessage) }
    assertThat(extractText(errorMessage.text)).isEqualTo("Lost connection to the device. See log for details.")
    var mirroringSessions = usageTrackerRule.deviceMirroringSessions()
    assertThat(mirroringSessions.size).isEqualTo(1)
    var mirroringSessionPattern = Regex(
      "kind: DEVICE_MIRRORING_SESSION\n" +
      "studio_session_id: \".+\"\n" +
      "product_details \\{\n" +
      "\\s*version: \".*\"\n" +
      "}\n" +
      "device_info \\{\n" +
      "\\s*anonymized_serial_number: \"\\w+\"\n" +
      "\\s*build_tags: \"\"\n" +
      "\\s*build_type: \"\"\n" +
      "\\s*build_version_release: \"Sweet dessert\"\n" +
      "\\s*cpu_abi: ARM64_V8A_ABI\n" +
      "\\s*manufacturer: \"Google\"\n" +
      "\\s*model: \"Pixel 5\"\n" +
      "\\s*device_type: LOCAL_PHYSICAL\n" +
      "\\s*build_api_level_full: \"32\"\n" +
      "\\s*mdns_connection_type: MDNS_NONE\n" +
      "\\s*device_provisioner_id: \"FakeDevicePlugin\"\n" +
      "\\s*connection_id: \"fakeConnectionId\"\n" +
      "}\n" +
      "ide_brand: ANDROID_STUDIO\n" +
      "idea_is_internal: \\w+\n" +
      "device_mirroring_session \\{\n" +
      "\\s*device_kind: PHYSICAL\n" +
      "\\s*duration_sec: \\d+\n" +
      "\\s*agent_push_time_millis: \\d+\n" +
      "\\s*first_frame_delay_millis: \\d+\n" +
      "}\n"
    )
    assertThat(mirroringSessionPattern.matches(mirroringSessions[0].toString())).isTrue()

    var agentTerminations = usageTrackerRule.agentTerminationEvents()
    assertThat(agentTerminations.size).isEqualTo(1)
    val agentTerminationPattern = Regex(
      "kind: DEVICE_MIRRORING_ABNORMAL_AGENT_TERMINATION\n" +
      "studio_session_id: \".+\"\n" +
      "product_details \\{\n" +
      "\\s*version: \".*\"\n" +
      "}\n" +
      "device_info \\{\n" +
      "\\s*anonymized_serial_number: \"\\w+\"\n" +
      "\\s*build_tags: \"\"\n" +
      "\\s*build_type: \"\"\n" +
      "\\s*build_version_release: \"Sweet dessert\"\n" +
      "\\s*cpu_abi: ARM64_V8A_ABI\n" +
      "\\s*manufacturer: \"Google\"\n" +
      "\\s*model: \"Pixel 5\"\n" +
      "\\s*device_type: LOCAL_PHYSICAL\n" +
      "\\s*build_api_level_full: \"32\"\n" +
      "\\s*mdns_connection_type: MDNS_NONE\n" +
      "\\s*device_provisioner_id: \"FakeDevicePlugin\"\n" +
      "\\s*connection_id: \"fakeConnectionId\"\n" +
      "}\n" +
      "ide_brand: ANDROID_STUDIO\n" +
      "idea_is_internal: \\w+\n" +
      "device_mirroring_abnormal_agent_termination \\{\n" +
      "\\s*exit_code: 139\n" +
      "\\s*run_duration_millis: \\d+\n" +
      "}\n"
    )
    assertThat(agentTerminationPattern.matches(agentTerminations[0].toString())).isTrue()
    var crashReports = crashReporterRule.reports
    assertThat(crashReports.size).isEqualTo(1)
    val crashReportPattern1 =
        Regex("\\{exitCode=\"139\", runDurationMillis=\"\\d+\", agentMessages=\"Crash is near\nKaput\", device=\"Pixel 5 API 32\"}")
    assertThat(crashReportPattern1.matches(crashReports[0].toPartMap().toString())).isTrue()
    assertThat(AgentLogSaver.logFile).hasContents(
        "--------- beginning of crash",
        "06-20 17:54:11.642 14782 14782 F libc: Fatal signal 11 (SIGSEGV), code 1 (SEGV_MAPERR)",
        "")

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
      waitForCondition(5, SECONDS) { extractText(errorMessage.text).isNotEmpty() }
      for (i in 1 until 3) {
        ConcurrencyUtil.awaitQuiescence(AndroidExecutors.getInstance().workerThreadExecutor as ThreadPoolExecutor, 5, SECONDS)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
      }
    }
    assertThat(extractText(errorMessage.text)).isEqualTo("Failed to initialize the device agent. See log for details.")
    assertThat(button.text).isEqualTo("Retry")
    assertThat(loggedErrors).containsExactly("Failed to initialize the screen sharing agent")

    mirroringSessions = usageTrackerRule.deviceMirroringSessions()
    assertThat(mirroringSessions.size).isEqualTo(2)
    mirroringSessionPattern = Regex(
      "kind: DEVICE_MIRRORING_SESSION\n" +
      "studio_session_id: \".+\"\n" +
      "product_details \\{\n" +
      "\\s*version: \".*\"\n" +
      "}\n" +
      "device_info \\{\n" +
      "\\s*anonymized_serial_number: \"\\w+\"\n" +
      "\\s*build_tags: \"\"\n" +
      "\\s*build_type: \"\"\n" +
      "\\s*build_version_release: \"Sweet dessert\"\n" +
      "\\s*cpu_abi: ARM64_V8A_ABI\n" +
      "\\s*manufacturer: \"Google\"\n" +
      "\\s*model: \"Pixel 5\"\n" +
      "\\s*device_type: LOCAL_PHYSICAL\n" +
      "\\s*build_api_level_full: \"32\"\n" +
      "\\s*mdns_connection_type: MDNS_NONE\n" +
      "\\s*device_provisioner_id: \"FakeDevicePlugin\"\n" +
      "\\s*connection_id: \"fakeConnectionId\"\n" +
      "}\n" +
      "ide_brand: ANDROID_STUDIO\n" +
      "idea_is_internal: \\w+\n" +
      "device_mirroring_session \\{\n" +
      "\\s*device_kind: PHYSICAL\n" +
      "\\s*duration_sec: \\d+\n" +
      "\\s*agent_push_time_millis: \\d+\n" +
      "}\n"
    )
    assertThat(mirroringSessionPattern.matches(mirroringSessions[1].toString())).isTrue()

    agentTerminations = usageTrackerRule.agentTerminationEvents()
    assertThat(agentTerminations.size).isEqualTo(2)
    assertThat(agentTerminationPattern.matches(agentTerminations[1].toString())).isTrue()

    crashReports = crashReporterRule.reports
    assertThat(crashReports.size).isEqualTo(2)
    val crashReportPattern2 = Regex("\\{exitCode=\"139\", runDurationMillis=\"\\d+\", agentMessages=\"\", device=\"Pixel 5 API 32\"}")
    assertThat(crashReportPattern2.matches(crashReports[1].toPartMap().toString())).isTrue()

    // Check reconnection.
    agent.crashOnStart = false
    fakeUi.clickOn(button)
    waitForFrame()
    assertThat(view.displayRectangle).isEqualTo(Rectangle(19, 0, 462, 1000))
    assertThat(view.displayOrientationQuadrants).isEqualTo(0)
  }

  @Test
  fun testInvalidFrameRecovery() {
    createDeviceView(200, 300)
    waitForFrame()

    val loggedErrors = executeCapturingLoggedWarnings() {
      runBlocking { agent.produceInvalidVideoFrame(PRIMARY_DISPLAY_ID) }
      assertThat(agent.getNextControlMessage(2.seconds)).isEqualTo(StopVideoStreamMessage(PRIMARY_DISPLAY_ID))
      assertThat(getNextControlMessageAndWaitForFrame(PRIMARY_DISPLAY_ID)).isEqualTo(
          StartVideoStreamMessage(PRIMARY_DISPLAY_ID, Dimension(400, 600)))
    }
    assertThat(loggedErrors).hasSize(1)
    assertThat(loggedErrors.first()).startsWith("Display 0: video packet was rejected by the decoder: ")
  }

  @Test
  fun testMetricsCollection() {
    createDeviceView(200, 300, 2.0)
    waitForFrame()
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
      "\\s*anonymized_serial_number: \"\\w+\"\n" +
      "\\s*build_tags: \"\"\n" +
      "\\s*build_type: \"\"\n" +
      "\\s*build_version_release: \"Sweet dessert\"\n" +
      "\\s*cpu_abi: ARM64_V8A_ABI\n" +
      "\\s*manufacturer: \"Google\"\n" +
      "\\s*model: \"Pixel 5\"\n" +
      "\\s*device_type: LOCAL_PHYSICAL\n" +
      "\\s*build_api_level_full: \"32\"\n" +
      "\\s*mdns_connection_type: MDNS_NONE\n" +
      "\\s*device_provisioner_id: \"FakeDevicePlugin\"\n" +
      "\\s*connection_id: \"fakeConnectionId\"\n" +
      "}\n" +
      "ide_brand: ANDROID_STUDIO\n" +
      "idea_is_internal: \\w+\n" +
      "device_mirroring_session \\{\n" +
      "\\s*device_kind: PHYSICAL\n" +
      "\\s*duration_sec: \\d+\n" +
      "\\s*agent_push_time_millis: \\d+\n" +
      "\\s*first_frame_delay_millis: \\d+\n" +
      "}\n"
    )
    assertThat(mirroringSessionPattern.matches(mirroringSessions[0].toString())).isTrue()
  }

  @Test
  fun testConnectionTimeout() {
    StudioFlags.DEVICE_MIRRORING_CONNECTION_TIMEOUT_MILLIS.override(200, testRootDisposable)
    agent.startDelayMillis = 500
    val loggedErrors = executeCapturingLoggedErrors {
      createDeviceViewWithoutWaitingForAgent(500, 1000, screenScale = 1.0)
      val errorMessage = fakeUi.getComponent<JEditorPane>()
      waitForCondition(2.seconds) { fakeUi.isShowing(errorMessage) }
      assertThat(extractText(errorMessage.text)).isEqualTo("Device agent is not responding")
    }
    assertThat(loggedErrors).containsExactly("Failed to initialize the screen sharing agent")
  }

  @Test
  fun testDeviceDisconnection() {
    createDeviceView(500, 1000)
    waitForFrame()

    agentRule.disconnectDevice(device)
    waitForCondition(15, SECONDS) { !view.isConnected }
  }

  @Test
  fun testKeysForMnemonicsShouldNotBeConsumed() {
    createDeviceView(500, 1000)
    waitForFrame()

    val altMPressedEvent = KeyEvent(view, KEY_PRESSED, System.currentTimeMillis(), ALT_DOWN_MASK, VK_M, VK_M.toChar())
    focusManager.redispatchEvent(view, altMPressedEvent)
    assertThat(altMPressedEvent.isConsumed).isFalse()

    val altMReleasedEvent = KeyEvent(view, KeyEvent.KEY_RELEASED, System.currentTimeMillis(), ALT_DOWN_MASK, VK_M, VK_M.toChar())
    focusManager.redispatchEvent(view, altMReleasedEvent)
    assertThat(altMReleasedEvent.isConsumed).isFalse()
  }

  @Test
  fun testKeyPreprocessingSkippedWhenHardwareInputEnabled() {
    createDeviceView(250, 500)
    waitForFrame()

    executeStreamingAction("android.streaming.hardware.input", view, agentRule.project)

    assertThat(view.skipKeyEventDispatcher(KeyEvent(view, KEY_PRESSED, System.currentTimeMillis(), 0, VK_M, VK_M.toChar()))).isTrue()
  }

  @Test
  fun testKeyPreprocessingNotSkippedForActionTogglingHardwareInput() {
    createDeviceView(250, 500)
    waitForFrame()

    executeStreamingAction("android.streaming.hardware.input", view, project)
    val keymapManager = KeymapManager.getInstance()
    keymapManager.activeKeymap.addShortcut("android.streaming.hardware.input", KeyboardShortcut.fromString("control shift J"))

    val event = KeyEvent(view, KEY_PRESSED, System.currentTimeMillis(), SHIFT_DOWN_MASK or CTRL_DOWN_MASK, VK_J, CHAR_UNDEFINED)
    assertThat(view.skipKeyEventDispatcher(event)).isFalse()
  }

  @Test
  fun testCtrlAndAlphabeticalKeysSentWhenHardwareInputEnabled() {
    createDeviceView(250, 500)
    waitForFrame()

    executeStreamingAction("android.streaming.hardware.input", view, agentRule.project)
    fakeUi.keyboard.setFocus(view)

    fakeUi.keyboard.press(VK_CONTROL)
    assertThat(agent.getNextControlMessage(2.seconds)).isEqualTo(KeyEventMessage(ACTION_DOWN, AKEYCODE_CTRL_LEFT, AMETA_CTRL_ON))

    fakeUi.keyboard.press(KeyEvent.VK_S)
    assertThat(agent.getNextControlMessage(2.seconds)).isEqualTo(KeyEventMessage(ACTION_DOWN, AKEYCODE_S, AMETA_CTRL_ON))

    fakeUi.keyboard.release(KeyEvent.VK_S)
    assertThat(agent.getNextControlMessage(2.seconds)).isEqualTo(KeyEventMessage(ACTION_UP, AKEYCODE_S, AMETA_CTRL_ON))

    fakeUi.keyboard.release(VK_CONTROL)
    assertThat(agent.getNextControlMessage(2.seconds)).isEqualTo(KeyEventMessage(ACTION_UP, AKEYCODE_CTRL_LEFT, 0))
  }

  @Test
  fun testButtonsDuringHardwareInput() {
    createDeviceView(250, 500)
    waitForFrame()

    executeStreamingAction("android.streaming.hardware.input", view, agentRule.project)

    val mousePosition = Point(97, 141)
    fakeUi.mouse.press(mousePosition)
    fakeUi.mouse.release()
    fakeUi.mouse.press(mousePosition.x, mousePosition.y, FakeMouse.Button.RIGHT)
    fakeUi.mouse.release()
    fakeUi.mouse.press(mousePosition.x, mousePosition.y, FakeMouse.Button.MIDDLE)
    fakeUi.mouse.release()

    assertInstanceOf<MotionEventMessage>(agent.getNextControlMessage(2.seconds)).apply {
      assertThat(action).isEqualTo(MotionEventMessage.ACTION_DOWN)
      assertThat(buttonState).isEqualTo(MotionEventMessage.BUTTON_PRIMARY)
      assertThat(actionButton).isEqualTo(MotionEventMessage.BUTTON_PRIMARY)
    }
    assertInstanceOf<MotionEventMessage>(agent.getNextControlMessage(2.seconds)).apply {
      assertThat(action).isEqualTo(MotionEventMessage.ACTION_UP)
      assertThat(buttonState).isEqualTo(0)
      assertThat(actionButton).isEqualTo(MotionEventMessage.BUTTON_PRIMARY)
    }
    assertInstanceOf<MotionEventMessage>(agent.getNextControlMessage(2.seconds)).apply {
      assertThat(action).isEqualTo(MotionEventMessage.ACTION_DOWN)
      assertThat(buttonState).isEqualTo(MotionEventMessage.BUTTON_SECONDARY)
      assertThat(actionButton).isEqualTo(MotionEventMessage.BUTTON_SECONDARY)
    }
    assertInstanceOf<MotionEventMessage>(agent.getNextControlMessage(2.seconds)).apply {
      assertThat(action).isEqualTo(MotionEventMessage.ACTION_UP)
      assertThat(buttonState).isEqualTo(0)
      assertThat(actionButton).isEqualTo(MotionEventMessage.BUTTON_SECONDARY)
    }
    assertInstanceOf<MotionEventMessage>(agent.getNextControlMessage(2.seconds)).apply {
      assertThat(action).isEqualTo(MotionEventMessage.ACTION_DOWN)
      assertThat(buttonState).isEqualTo(MotionEventMessage.BUTTON_TERTIARY)
      assertThat(actionButton).isEqualTo(MotionEventMessage.BUTTON_TERTIARY)
    }
    assertInstanceOf<MotionEventMessage>(agent.getNextControlMessage(2.seconds)).apply {
      assertThat(action).isEqualTo(MotionEventMessage.ACTION_UP)
      assertThat(buttonState).isEqualTo(0)
      assertThat(actionButton).isEqualTo(MotionEventMessage.BUTTON_TERTIARY)
    }
  }

  @Test
  fun testDisableMultiTouchDuringHardwareInput() {
    createDeviceView(50, 100)
    waitForFrame()
    assertThat(view.displayRectangle).isEqualTo(Rectangle(4, 0, 92, 200))

    val mousePosition = Point(30, 30)
    val pointerInfo = mock<PointerInfo>()
    whenever(pointerInfo.location).thenReturn(mousePosition)
    val mouseInfoMock = mockStatic<MouseInfo>(testRootDisposable)
    mouseInfoMock.whenever<Any?> { MouseInfo.getPointerInfo() }.thenReturn(pointerInfo)

    // Start multi-touch
    fakeUi.keyboard.setFocus(view)
    fakeUi.mouse.moveTo(mousePosition)
    assertThat(getNextControlMessageAndWaitForFrame()).isEqualTo(
      MotionEventMessage(listOf(MotionEventMessage.Pointer(663, 707, 0)), MotionEventMessage.ACTION_HOVER_MOVE, 0, 0,
                         0))
    fakeUi.keyboard.press(VK_CONTROL)
    fakeUi.layoutAndDispatchEvents()
    assertAppearance("MultiTouch1")

    // Enable hardware input
    executeStreamingAction("android.streaming.hardware.input", view, agentRule.project)

    // Check if multitouch indicator is hidden
    fakeUi.layoutAndDispatchEvents()
    assertAppearance("MultiTouch4")

    // Pressing mouse should generate mouse events instead of touch
    fakeUi.mouse.press(mousePosition)
    assertThat(getNextControlMessageAndWaitForFrame()).isEqualTo(
        MotionEventMessage(listOf(MotionEventMessage.Pointer(663, 707, 0)), MotionEventMessage.ACTION_HOVER_EXIT, 0, 0, 0))
    assertThat(getNextControlMessageAndWaitForFrame()).isEqualTo(
        MotionEventMessage(listOf(MotionEventMessage.Pointer(663, 707, 0)), MotionEventMessage.ACTION_DOWN, 1, 1, 0))

    // Disable hardware input
    executeStreamingAction("android.streaming.hardware.input", view, agentRule.project, modifiers = CTRL_DOWN_MASK)

    // Check if multitouch indicator is shown again
    fakeUi.layoutAndDispatchEvents()
    assertAppearance("MultiTouch2")
  }

  @Test
  fun testMetaKeysReleasedWhenHardwareInputDisabled() {
    createDeviceView(50, 100)
    waitForFrame()

    // Enable hardware input
    executeStreamingAction("android.streaming.hardware.input", view, agentRule.project)

    // Press Ctrl
    focusManager.focusOwner = view
    fakeUi.keyboard.press(VK_CONTROL)

    assertThat(getNextControlMessageAndWaitForFrame()).isEqualTo(
      KeyEventMessage(ACTION_DOWN, AKEYCODE_CTRL_LEFT, AMETA_CTRL_ON))

    // Disable hardware input
    executeStreamingAction("android.streaming.hardware.input", view, agentRule.project)

    assertThat(getNextControlMessageAndWaitForFrame()).isEqualTo(
      KeyEventMessage(ACTION_UP, AKEYCODE_CTRL_LEFT, 0))
  }

  @Test
  fun testMetaKeysReleasedWhenLostFocusDuringHardwareInput() {
    createDeviceView(50, 100)
    waitForFrame()

    // Enable hardware input
    executeStreamingAction("android.streaming.hardware.input", view, agentRule.project)

    // Press Ctrl
    focusManager.focusOwner = view
    fakeUi.keyboard.press(VK_CONTROL)

    assertThat(getNextControlMessageAndWaitForFrame()).isEqualTo(
        KeyEventMessage(ACTION_DOWN, AKEYCODE_CTRL_LEFT, AMETA_CTRL_ON))

    // Lose focus
    focusManager.focusOwner = null

    assertThat(getNextControlMessageAndWaitForFrame()).isEqualTo(
      KeyEventMessage(ACTION_UP, AKEYCODE_CTRL_LEFT, 0))
  }

  private fun createDeviceView(width: Int, height: Int, screenScale: Double = 2.0) {
    createDeviceViewWithoutWaitingForAgent(width, height, screenScale)
    waitForCondition(15, SECONDS) { agent.isRunning }
  }

  private fun createDeviceViewWithoutWaitingForAgent(width: Int, height: Int, screenScale: Double) {
    val deviceClient = DeviceClient(device.serialNumber, device.configuration, device.deviceState.cpuAbi)
    Disposer.register(testRootDisposable, deviceClient)
    // DeviceView has to be disposed before DeviceClient.
    val disposable = Disposer.newDisposable()
    Disposer.register(testRootDisposable, disposable)
    view = DeviceView(disposable, deviceClient, PRIMARY_DISPLAY_ID, UNKNOWN_ORIENTATION, agentRule.project)
    fakeUi = FakeUi(wrapInScrollPane(view, width, height), screenScale)
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

  private fun getNextControlMessageAndWaitForFrame(displayId: Int = PRIMARY_DISPLAY_ID): ControlMessage {
    val message = agent.getNextControlMessage(5.seconds)
    waitForFrame(displayId)
    return message
  }

  /** Waits for all video frames to be received. */
  private fun waitForFrame(displayId: Int = PRIMARY_DISPLAY_ID) {
    waitForCondition(2.seconds) {
      view.isConnected && agent.getFrameNumber(displayId) > 0u && renderAndGetFrameNumber() == agent.getFrameNumber(displayId)
    }
  }

  private fun renderAndGetFrameNumber(): UInt {
    fakeUi.render() // The frame number may get updated as a result of rendering.
    return view.frameNumber
  }

  private fun FakeUi.resizeRoot(width: Int, height: Int) {
    root.size = Dimension(width, height)
    layoutAndDispatchEvents()
  }

  private fun isRunningInBazelTest(): Boolean {
    return System.getenv().containsKey("TEST_WORKSPACE")
  }
}

private fun getKeyStroke(action: String) =
  KeymapUtil.getKeyStroke(KeymapUtil.getActiveKeymapShortcuts(action))!!

private fun UsageTrackerRule.agentTerminationEvents(): List<AndroidStudioEvent> {
  return usages.filter { it.studioEvent.kind == DEVICE_MIRRORING_ABNORMAL_AGENT_TERMINATION }.map { it.studioEvent }
}

private fun UsageTrackerRule.deviceMirroringSessions(): List<AndroidStudioEvent> {
  return usages.filter { it.studioEvent.kind == DEVICE_MIRRORING_SESSION }.map { it.studioEvent }
}

private fun CrashReport.toPartMap(): Map<String, String> {
  val parts = linkedMapOf<String, String>()
  val mockBuilder: MultipartEntityBuilder = mock()
  serialize(mockBuilder)
  val keyCaptor = ArgumentCaptor.forClass(String::class.java)
  val valueCaptor = ArgumentCaptor.forClass(String::class.java)
  verify(mockBuilder, atLeast(1)).addTextBody(keyCaptor.capture(), valueCaptor.capture(), any())
  val keys = keyCaptor.allValues
  val values = valueCaptor.allValues
  for ((i, key) in keys.withIndex()) {
    parts[key] = "\"${values[i]}\""
  }
  return parts
}

private const val GOLDEN_FILE_PATH = "tools/adt/idea/streaming/testData/DeviceViewTest/golden"
