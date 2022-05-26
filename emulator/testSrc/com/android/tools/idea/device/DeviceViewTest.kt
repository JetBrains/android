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
package com.android.tools.idea.device

import com.android.testutils.ImageDiffUtil
import com.android.testutils.MockitoKt
import com.android.testutils.TestUtils
import com.android.tools.adtui.actions.ZoomType
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.replaceKeyboardFocusManager
import com.android.tools.idea.concurrency.waitForCondition
import com.android.tools.idea.emulator.DeviceMirroringSettings
import com.android.tools.idea.emulator.EmulatorView
import com.android.tools.idea.executeDeviceAction
import com.android.tools.idea.testing.mockStatic
import com.android.utils.FlightRecorder
import com.android.utils.TraceUtils
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.ui.components.JBScrollPane
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import java.awt.Component
import java.awt.Dimension
import java.awt.KeyboardFocusManager
import java.awt.MouseInfo
import java.awt.Point
import java.awt.PointerInfo
import java.awt.event.KeyEvent
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import javax.swing.JScrollPane

/**
 * Tests for [DeviceView] and [DeviceClient].
 */
@RunsInEdt
internal class DeviceViewTest {
  private val agentRule = FakeScreenSharingAgentRule()
  @get:Rule
  val ruleChain: RuleChain = RuleChain.outerRule(agentRule).around(EdtRule())
  private lateinit var device: FakeScreenSharingAgentRule.FakeDevice
  private lateinit var view: DeviceView
  private lateinit var ui: FakeUi
  private var savedClipboardSynchronizationState = false

  private val testRootDisposable
    get() = agentRule.testRootDisposable
  private val project
    get() = agentRule.project
  private val agent
    get() = device.agent

  @Before
  fun setUp() {
    FlightRecorder.initialize(1000)
    savedClipboardSynchronizationState = DeviceMirroringSettings.getInstance().synchronizeClipboard
    DeviceMirroringSettings.getInstance().synchronizeClipboard = false
    device = agentRule.connectDevice("Pixel 4", 30, Dimension(1080, 2280), "arm64-v8a")
  }

  @After
  fun tearDown() {
    DeviceMirroringSettings.getInstance().synchronizeClipboard = savedClipboardSynchronizationState
  }

  @Test
  fun testResizingRotationAndMouseInput() {
    if (SystemInfo.isWindows) {
      return // For some unclear reason the test fails on Windows with java.lang.UnsatisfiedLinkError: no jniavcodec in java.library.path.
    }
    createDeviceView(200, 300, 2.0)
    assertThat(agent.commandLine).isEqualTo("CLASSPATH=/data/local/tmp/screen-sharing-agent.jar app_process" +
                                            " /data/local/tmp com.android.tools.screensharing.Main --log=debug --codec=vp8")
    assertThat(getNextControlMessageAndWaitForFrame(agent, ui, view)).isEqualTo(SetMaxVideoResolutionMessage(400, 600))
    assertThat(view.displayRotationQuadrants).isEqualTo(0)
    assertThat(view.displayRectangle?.width).isEqualTo(284)
    assertThat(view.displayRectangle?.height).isEqualTo(600)

    // Check resizing.
    ui.resizeRoot(100, 90)
    assertThat(getNextControlMessageAndWaitForFrame(agent, ui, view)).isEqualTo(SetMaxVideoResolutionMessage(200, 180))
    assertThat(view.displayRectangle?.width).isEqualTo(85)
    assertThat(view.displayRectangle?.height).isEqualTo(180)

    // Check mouse input in various orientations.
    val expectedCoordinates = listOf(
      MotionEventMessage.Pointer(298, 766, 0),
      MotionEventMessage.Pointer(807, 1399, 0),
      MotionEventMessage.Pointer(890, 917, 0),
      MotionEventMessage.Pointer(315, 1373, 0),
      MotionEventMessage.Pointer(794, 1526, 0),
      MotionEventMessage.Pointer(285, 892, 0),
      MotionEventMessage.Pointer(200, 1373, 0),
      MotionEventMessage.Pointer(775, 917, 0),
    )
    for (i in 0 until 4) {
      assertAppearance(ui, "Rotation${i * 90}")
      // Check mouse input.
      ui.mouse.press(40, 30)
      assertThat(getNextControlMessageAndWaitForFrame(agent, ui, view)).isEqualTo(
          MotionEventMessage(listOf(expectedCoordinates[i * 2]), MotionEventMessage.ACTION_DOWN, 0))

      ui.mouse.dragTo(60, 55)
      assertThat(getNextControlMessageAndWaitForFrame(agent, ui, view)).isEqualTo(
          MotionEventMessage(listOf(expectedCoordinates[i * 2 + 1]), MotionEventMessage.ACTION_MOVE, 0))

      ui.mouse.release()
      assertThat(getNextControlMessageAndWaitForFrame(agent, ui, view)).isEqualTo(
          MotionEventMessage(listOf(expectedCoordinates[i * 2 + 1]), MotionEventMessage.ACTION_UP, 0))

      executeDeviceAction("android.device.rotate.left", view, project)
      assertThat(getNextControlMessageAndWaitForFrame(agent, ui, view)).isEqualTo(SetDeviceOrientationMessage((i + 1) % 4))
    }

    // Check dragging over the edge of the device screen.
    ui.mouse.press(40, 50)
    assertThat(agent.getNextControlMessage(2, TimeUnit.SECONDS)).isEqualTo(
        MotionEventMessage(listOf(MotionEventMessage.Pointer(298, 1273, 0)), MotionEventMessage.ACTION_DOWN, 0))
    ui.mouse.dragTo(90, 60)
    assertThat(agent.getNextControlMessage(2, TimeUnit.SECONDS)).isEqualTo(
        MotionEventMessage(listOf(MotionEventMessage.Pointer(1079, 1526, 0)), MotionEventMessage.ACTION_MOVE, 0))
    assertThat(agent.getNextControlMessage(2, TimeUnit.SECONDS)).isEqualTo(
        MotionEventMessage(listOf(MotionEventMessage.Pointer(1079, 1526, 0)), MotionEventMessage.ACTION_OUTSIDE, 0))
  }

  @Test
  fun testMultiTouch() {
    if (SystemInfo.isWindows) {
      return // For some unclear reason the test fails on Windows with java.lang.UnsatisfiedLinkError: no jniavcodec in java.library.path.
    }
    createDeviceView(50, 100, 2.0)
    assertThat(getNextControlMessageAndWaitForFrame(agent, ui, view)).isEqualTo(SetMaxVideoResolutionMessage(100, 200))

    val mousePosition = Point(30, 30)
    val pointerInfo = MockitoKt.mock<PointerInfo>()
    Mockito.`when`(pointerInfo.location).thenReturn(mousePosition)
    val mouseInfoMock = mockStatic<MouseInfo>(testRootDisposable)
    mouseInfoMock.`when`<Any?> { MouseInfo.getPointerInfo() }.thenReturn(pointerInfo)

    ui.keyboard.setFocus(view)
    ui.mouse.moveTo(mousePosition)
    ui.keyboard.press(KeyEvent.VK_CONTROL)
    ui.layoutAndDispatchEvents()
    assertAppearance(ui, "MultiTouch1")

    ui.mouse.press(mousePosition)
    assertThat(getNextControlMessageAndWaitForFrame(agent, ui, view)).isEqualTo(
        MotionEventMessage(listOf(MotionEventMessage.Pointer(665, 689, 0), MotionEventMessage.Pointer(415, 1591, 1)),
                           MotionEventMessage.ACTION_DOWN, 0))
    assertAppearance(ui, "MultiTouch2")

    mousePosition.x -= 10
    mousePosition.y += 10
    ui.mouse.dragTo(mousePosition)
    assertThat(getNextControlMessageAndWaitForFrame(agent, ui, view)).isEqualTo(
        MotionEventMessage(listOf(MotionEventMessage.Pointer(437, 917, 0), MotionEventMessage.Pointer(643, 1363, 1)),
                           MotionEventMessage.ACTION_MOVE, 0))
    assertAppearance(ui, "MultiTouch3")

    ui.mouse.release()
    assertThat(getNextControlMessageAndWaitForFrame(agent, ui, view)).isEqualTo(
      MotionEventMessage(listOf(MotionEventMessage.Pointer(437, 917, 0), MotionEventMessage.Pointer(643, 1363, 1)),
                         MotionEventMessage.ACTION_UP, 0))

    ui.keyboard.release(KeyEvent.VK_CONTROL)
    assertAppearance(ui, "MultiTouch4")
  }

  @Test
  fun testKeyboardInput() {
    if (SystemInfo.isWindows) {
      return // For some unclear reason the test fails on Windows with java.lang.UnsatisfiedLinkError: no jniavcodec in java.library.path.
    }
    createDeviceView(150, 250, 1.5)
    assertThat(getNextControlMessageAndWaitForFrame(agent, ui, view)).isEqualTo(SetMaxVideoResolutionMessage(225, 375))

    // Check keyboard input.
    ui.keyboard.setFocus(view)
    for (c in ' '..'~') {
      ui.keyboard.type(c.code)
      assertThat(agent.getNextControlMessage(2, TimeUnit.SECONDS)).isEqualTo(TextInputMessage(c.toString()))
    }

    val controlCharacterCases = listOf(
      Pair(KeyEvent.VK_ENTER, AKEYCODE_ENTER),
      Pair(KeyEvent.VK_TAB, AKEYCODE_TAB),
      Pair(KeyEvent.VK_ESCAPE, AKEYCODE_ESCAPE),
      Pair(KeyEvent.VK_BACK_SPACE, AKEYCODE_DEL),
      Pair(KeyEvent.VK_DELETE, if (SystemInfo.isMac) AKEYCODE_DEL else AKEYCODE_FORWARD_DEL),
      Pair(KeyEvent.VK_LEFT, AKEYCODE_DPAD_LEFT),
      Pair(KeyEvent.VK_KP_LEFT, AKEYCODE_DPAD_LEFT),
      Pair(KeyEvent.VK_RIGHT, AKEYCODE_DPAD_RIGHT),
      Pair(KeyEvent.VK_KP_RIGHT, AKEYCODE_DPAD_RIGHT),
      Pair(KeyEvent.VK_UP, AKEYCODE_DPAD_UP),
      Pair(KeyEvent.VK_KP_UP, AKEYCODE_DPAD_UP),
      Pair(KeyEvent.VK_DOWN, AKEYCODE_DPAD_DOWN),
      Pair(KeyEvent.VK_KP_DOWN, AKEYCODE_DPAD_DOWN),
      Pair(KeyEvent.VK_HOME, AKEYCODE_MOVE_HOME),
      Pair(KeyEvent.VK_END, AKEYCODE_MOVE_END),
      Pair(KeyEvent.VK_PAGE_DOWN, AKEYCODE_PAGE_DOWN),
      Pair(KeyEvent.VK_PAGE_UP, AKEYCODE_PAGE_UP),
    )
    for (case in controlCharacterCases) {
      ui.keyboard.pressAndRelease(case.first)
      assertThat(agent.getNextControlMessage(2, TimeUnit.SECONDS)).isEqualTo(
          KeyEventMessage(AndroidKeyEventActionType.ACTION_DOWN_AND_UP, case.second, 0))
    }

    // Ctrl+Tab should be ignored.
    with(ui.keyboard) {
      press(KeyEvent.VK_CONTROL)
      pressAndRelease(KeyEvent.VK_TAB)
      release(KeyEvent.VK_CONTROL)
    }
    assertThat(agent.commandLog).isEmpty()

    val mockFocusManager: KeyboardFocusManager = MockitoKt.mock()
    Mockito.`when`(mockFocusManager.redispatchEvent(MockitoKt.any(Component::class.java), MockitoKt.any(KeyEvent::class.java))).thenCallRealMethod()
    replaceKeyboardFocusManager(mockFocusManager, testRootDisposable)
    // Shift+Tab should trigger a forward local focus traversal.
    with(ui.keyboard) {
      setFocus(view)
      press(KeyEvent.VK_SHIFT)
      pressAndRelease(KeyEvent.VK_TAB)
      release(KeyEvent.VK_SHIFT)
    }
    val arg1 = ArgumentCaptor.forClass(EmulatorView::class.java)
    val arg2 = ArgumentCaptor.forClass(KeyEvent::class.java)
    Mockito.verify(mockFocusManager, Mockito.atLeast(1)).processKeyEvent(arg1.capture(), arg2.capture())
    val tabEvent = arg2.allValues.firstOrNull { it.id == KeyEvent.KEY_PRESSED && it.keyCode == KeyEvent.VK_TAB && it.modifiersEx == 0 }
    assertThat(tabEvent).isNotNull()
  }

  @Ignore("b/233763372")
  @Test
  fun testZoom() {
    if (SystemInfo.isWindows) {
      return // For some unclear reason the test fails on Windows with java.lang.UnsatisfiedLinkError: no jniavcodec in java.library.path.
    }
    createDeviceView(100, 200, 2.0)
    assertThat(getNextControlMessageAndWaitForFrame(agent, ui, view)).isEqualTo(SetMaxVideoResolutionMessage(200, 400))

    // Check zoom.
    assertThat(view.scale).isWithin(1e-4).of(ui.screenScale * ui.root.height / device.displaySize.height)
    assertThat(view.canZoomIn()).isTrue()
    assertThat(view.canZoomOut()).isFalse()
    assertThat(view.canZoomToActual()).isTrue()
    assertThat(view.canZoomToFit()).isFalse()

    view.zoom(ZoomType.IN)
    ui.layoutAndDispatchEvents()
    assertThat(getNextControlMessageAndWaitForFrame(agent, ui, view)).isEqualTo(SetMaxVideoResolutionMessage(270, 570))
    assertThat(view.canZoomIn()).isTrue()
    assertThat(view.canZoomOut()).isTrue()
    assertThat(view.canZoomToActual()).isTrue()
    assertThat(view.canZoomToFit()).isTrue()

    view.zoom(ZoomType.ACTUAL)
    ui.layoutAndDispatchEvents()
    assertThat(getNextControlMessageAndWaitForFrame(agent, ui, view)).isEqualTo(
        SetMaxVideoResolutionMessage(device.displaySize.width, device.displaySize.height))
    assertThat(view.canZoomIn()).isTrue()
    assertThat(view.canZoomOut()).isTrue()
    assertThat(view.canZoomToActual()).isFalse()
    assertThat(view.canZoomToFit()).isTrue()

    view.zoom(ZoomType.OUT)
    ui.layoutAndDispatchEvents()
    assertThat(getNextControlMessageAndWaitForFrame(agent, ui, view)).isEqualTo(
        SetMaxVideoResolutionMessage(device.displaySize.width / 2, device.displaySize.height / 2))
    assertThat(view.canZoomIn()).isTrue()
    assertThat(view.canZoomOut()).isTrue()
    assertThat(view.canZoomToActual()).isTrue()
    assertThat(view.canZoomToFit()).isTrue()

    view.zoom(ZoomType.FIT)
    ui.layoutAndDispatchEvents()
    assertThat(getNextControlMessageAndWaitForFrame(agent, ui, view)).isEqualTo(SetMaxVideoResolutionMessage(200, 400))
    assertThat(view.canZoomIn()).isTrue()
    assertThat(view.canZoomOut()).isFalse()
    assertThat(view.canZoomToActual()).isTrue()
    assertThat(view.canZoomToFit()).isFalse()

    // Check clockwise rotation in zoomed-in state.
    for (i in 0 until 4) {
      view.zoom(ZoomType.IN)
      ui.layoutAndDispatchEvents()
      val expected = if (view.displayRotationQuadrants % 2 == 0)
          SetMaxVideoResolutionMessage(270, 570) else SetMaxVideoResolutionMessage(228, 400)
      assertThat(getNextControlMessageAndWaitForFrame(agent, ui, view)).isEqualTo(expected)
      executeDeviceAction("android.device.rotate.right", view, project)
      assertThat(getNextControlMessageAndWaitForFrame(agent, ui, view)).isEqualTo(SetDeviceOrientationMessage(3 - i))
      ui.layoutAndDispatchEvents()
      assertThat(view.canZoomOut()).isFalse() // zoom-in mode cancelled by the rotation.
      assertThat(view.canZoomToFit()).isFalse()
      assertThat(getNextControlMessageAndWaitForFrame(agent, ui, view)).isEqualTo(SetMaxVideoResolutionMessage(200, 400))
    }
  }

  private fun createDeviceView(width: Int, height: Int, screenScale: Double = 2.0) {
    try {
      view = DeviceView(testRootDisposable, device.serialNumber, device.abi, null, agentRule.project)
      ui = FakeUi(wrapInScrollPane(view, width, height), screenScale)
      FlightRecorder.log { "${TraceUtils.currentTime()} DeviceViewTest.createDeviceView waiting for agent to start" }
      waitForCondition(15, TimeUnit.SECONDS) { agent.started }
    }
    catch (e: Throwable) {
      FlightRecorder.print()
      throw e
    }
  }

  private fun wrapInScrollPane(view: Component, width: Int, height: Int): JScrollPane {
    return JBScrollPane(view).apply {
      border = null
      isFocusable = true
      size = Dimension(width, height)
    }
  }

  private fun assertAppearance(ui: FakeUi, goldenImageName: String) {
    val image = ui.render()
    ImageDiffUtil.assertImageSimilar(getGoldenFile(goldenImageName), image, 0.0)
  }

  private fun getGoldenFile(name: String): Path =
    TestUtils.resolveWorkspacePathUnchecked("${GOLDEN_FILE_PATH}/${name}.png")

  private fun getNextControlMessageAndWaitForFrame(agent: FakeScreenSharingAgent, fakeUi: FakeUi, deviceView: DeviceView): ControlMessage {
    val message = agent.getNextControlMessage(2, TimeUnit.SECONDS)
    // Wait for all video frames to be received.
    waitForCondition(2, TimeUnit.SECONDS) { fakeUi.render(); deviceView.frameNumber == agent.frameNumber }
    return message
  }

  private fun FakeUi.resizeRoot(width: Int, height: Int) {
    root.size = Dimension(width, height)
    layoutAndDispatchEvents()
  }
}

private const val GOLDEN_FILE_PATH = "tools/adt/idea/emulator/testData/DeviceViewTest/golden"
