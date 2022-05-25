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
import com.android.testutils.TestUtils
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.concurrency.waitForCondition
import com.android.tools.idea.emulator.DeviceMirroringSettings
import com.android.tools.idea.executeDeviceAction
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.ui.components.JBScrollPane
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.awt.Component
import java.awt.Dimension
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
          MotionEventMessage(arrayListOf(expectedCoordinates[i * 2]), MotionEventMessage.ACTION_DOWN, 0))

      ui.mouse.dragTo(60, 55)
      assertThat(getNextControlMessageAndWaitForFrame(agent, ui, view)).isEqualTo(
          MotionEventMessage(arrayListOf(expectedCoordinates[i * 2 + 1]), MotionEventMessage.ACTION_MOVE, 0))

      ui.mouse.release()
      assertThat(getNextControlMessageAndWaitForFrame(agent, ui, view)).isEqualTo(
          MotionEventMessage(arrayListOf(expectedCoordinates[i * 2 + 1]), MotionEventMessage.ACTION_UP, 0))

      executeDeviceAction("android.device.rotate.left", view, project)
      assertThat(getNextControlMessageAndWaitForFrame(agent, ui, view)).isEqualTo(SetDeviceOrientationMessage((i + 1) % 4))
    }

    // Check dragging over the edge of the device screen.
    ui.mouse.press(40, 50)
    assertThat(agent.getNextControlMessage(2, TimeUnit.SECONDS)).isEqualTo(
        MotionEventMessage(arrayListOf(MotionEventMessage.Pointer(298, 1273, 0)), MotionEventMessage.ACTION_DOWN, 0))
    ui.mouse.dragTo(90, 60)
    assertThat(agent.getNextControlMessage(2, TimeUnit.SECONDS)).isEqualTo(
        MotionEventMessage(arrayListOf(MotionEventMessage.Pointer(1079, 1526, 0)), MotionEventMessage.ACTION_MOVE, 0))
    assertThat(agent.getNextControlMessage(2, TimeUnit.SECONDS)).isEqualTo(
        MotionEventMessage(arrayListOf(MotionEventMessage.Pointer(1079, 1526, 0)), MotionEventMessage.ACTION_OUTSIDE, 0))
  }

  private fun createDeviceView(width: Int, height: Int, screenScale: Double = 2.0) {
    view = DeviceView(testRootDisposable, device.serialNumber, device.abi, null, agentRule.project)
    ui = FakeUi(wrapInScrollPane(view, width, height), screenScale)
    waitForCondition(5, TimeUnit.SECONDS) { agent.started }
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
