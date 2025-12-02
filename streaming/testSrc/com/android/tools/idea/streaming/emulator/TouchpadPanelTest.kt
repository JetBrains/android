/*
 * Copyright (C) 2025 The Android Open Source Project
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
import com.android.testutils.TestUtils
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.PortableUiFontRule
import com.android.tools.idea.protobuf.TextFormat.shortDebugString
import com.android.tools.idea.streaming.ClipboardSynchronizationDisablementRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.ui.components.JBBox
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Dimension
import java.awt.event.KeyEvent.VK_SHIFT
import java.nio.file.Path
import javax.swing.Box.createHorizontalGlue
import javax.swing.BoxLayout
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

/** Tests for [TouchpadPanel]. */
@RunsInEdt
class TouchpadPanelTest {

  private val applicationRule = ApplicationRule()
  private val emulatorRule = FakeEmulatorRule()

  @get:Rule
  val ruleChain = RuleChain(applicationRule, emulatorRule, ClipboardSynchronizationDisablementRule(), EdtRule(), PortableUiFontRule())

  private val glasses by lazy { createGlassesAvd() }
  private val touchpadPanel by lazy { createTouchpadPanel() }
  private val ui: FakeUi by lazy { createFakeUi(touchpadPanel) }

  @Test
  fun testSingleTouch() {
    assertAppearance("SingleTouch1")

    ui.mouse.press(touchpadPanel.x + 10, touchpadPanel.y + 20)
    val call = glasses.getNextGrpcCall(2.seconds)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/streamInputEvent")
    assertThat(shortDebugString(call.getNextRequest(2.seconds)))
        .isEqualTo("touchpad_event { touches { x: 70 y: 144 pressure: 1024 expiration: NEVER_EXPIRE } }")
    assertAppearance("SingleTouch2")

    ui.mouse.dragTo(touchpadPanel.x + 200, touchpadPanel.y + 25)
    assertThat(shortDebugString(call.getNextRequest(2.seconds)))
        .isEqualTo("touchpad_event { touches { x: 1479 y: 181 pressure: 1024 expiration: NEVER_EXPIRE } }")
    ui.mouse.release()
    assertThat(shortDebugString(call.getNextRequest(2.seconds)))
        .isEqualTo("touchpad_event { touches { x: 1479 y: 181 expiration: NEVER_EXPIRE } }")
    assertAppearance("SingleTouch3")

    ui.mouse.press(touchpadPanel.x + 200, touchpadPanel.y + 25)
    assertThat(shortDebugString(call.getNextRequest(2.seconds)))
        .isEqualTo("touchpad_event { touches { x: 1479 y: 181 pressure: 1024 expiration: NEVER_EXPIRE } }")

    // Drag over the edge of the touchpad.
    ui.mouse.dragTo(touchpadPanel.x + 210, touchpadPanel.y + 25)
    assertThat(shortDebugString(call.getNextRequest(2.seconds)))
        .isEqualTo("touchpad_event { touches { x: 1542 y: 181 expiration: NEVER_EXPIRE } }")

    // Drag into the touchpad.
    ui.mouse.dragTo(touchpadPanel.x + 205, touchpadPanel.y + 20)
    assertThat(shortDebugString(call.getNextRequest(2.seconds)))
        .isEqualTo("touchpad_event { touches { x: 1517 y: 144 pressure: 1024 expiration: NEVER_EXPIRE } }")
  }

  @Test
  fun testMultiTouch() {
    ui.mouse.press(touchpadPanel.x + 5, touchpadPanel.y + 20)
    val call = glasses.getNextGrpcCall(2.seconds)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/streamInputEvent")
    assertThat(shortDebugString(call.getNextRequest(2.seconds)))
        .isEqualTo("touchpad_event { touches { x: 33 y: 144 pressure: 1024 expiration: NEVER_EXPIRE } }")

    // Press Shift to trigger multi-touch mode.
    ui.keyboard.press(VK_SHIFT)
    ui.mouse.dragTo(touchpadPanel.x + 15, touchpadPanel.y + 20)
    assertThat(shortDebugString(call.getNextRequest(2.seconds)))
        .isEqualTo("touchpad_event { touches { x: 33 y: 144 expiration: NEVER_EXPIRE } }")
    // The mode is multi-touch but only one of the fingers is inside the touchpad.
    assertThat(shortDebugString(call.getNextRequest(2.seconds)))
      .isEqualTo("touchpad_event { touches { x: 367 y: 144 identifier: 1 pressure: 1024 expiration: NEVER_EXPIRE } }")
    assertAppearance("MultiTouch1")

    // Drag so that both fingers are inside the touchpad.
    ui.mouse.dragTo(touchpadPanel.x + 100, touchpadPanel.y + 20)
    assertThat(shortDebugString(call.getNextRequest(2.seconds)))
        .isEqualTo("touchpad_event { touches { x: 478 y: 144 pressure: 1024 expiration: NEVER_EXPIRE }" +
                   " touches { x: 998 y: 144 identifier: 1 pressure: 1024 expiration: NEVER_EXPIRE } }")

    // Terminate dragging.
    ui.mouse.release()
    assertThat(shortDebugString(call.getNextRequest(2.seconds)))
        .isEqualTo("touchpad_event { touches { x: 478 y: 144 expiration: NEVER_EXPIRE }" +
                   " touches { x: 998 y: 144 identifier: 1 expiration: NEVER_EXPIRE } }")
    assertAppearance("MultiTouch2")

    // Start dragging again.
    ui.mouse.press(touchpadPanel.x + 100, touchpadPanel.y + 20)
    assertThat(shortDebugString(call.getNextRequest(2.seconds)))
        .isEqualTo("touchpad_event { touches { x: 478 y: 144 pressure: 1024 expiration: NEVER_EXPIRE }" +
                   " touches { x: 998 y: 144 identifier: 1 pressure: 1024 expiration: NEVER_EXPIRE } }")

    // Drag closer to the right edge of the touchpad so that one of the fingers leaves the touchpad.
    ui.mouse.dragTo(touchpadPanel.x + 190, touchpadPanel.y + 20)
    assertThat(shortDebugString(call.getNextRequest(2.seconds)))
        .isEqualTo("touchpad_event { touches { x: 1145 y: 144 pressure: 1024 expiration: NEVER_EXPIRE }" +
                   " touches { x: 1542 y: 144 identifier: 1 expiration: NEVER_EXPIRE } }")

    // Terminate multi-touch mode.
    ui.keyboard.release(VK_SHIFT)
    ui.mouse.dragTo(touchpadPanel.x + 200, touchpadPanel.y + 20)
    assertThat(shortDebugString(call.getNextRequest(2.seconds)))
        .isEqualTo("touchpad_event { touches { x: 1145 y: 144 expiration: NEVER_EXPIRE } }")
    assertThat(shortDebugString(call.getNextRequest(2.seconds)))
        .isEqualTo("touchpad_event { touches { x: 1479 y: 144 pressure: 1024 expiration: NEVER_EXPIRE } }")

    // Terminate dragging.
    ui.mouse.release()
    assertThat(shortDebugString(call.getNextRequest(2.seconds)))
        .isEqualTo("touchpad_event { touches { x: 1479 y: 144 expiration: NEVER_EXPIRE } }")
  }

  private fun createTouchpadPanel(): TouchpadPanel {
    val glassesPort = glasses.grpcPort
    val emulators = runBlocking { RunningEmulatorCatalog.getInstance().updateNow().await() }
    val emulatorController = emulators.find { it.emulatorId.grpcPort == glassesPort }!!
    return TouchpadPanel(emulatorController, emulatorController.emulatorConfig.touchpadSize!!)
  }

  private fun createFakeUi(touchpadPanel: TouchpadPanel): FakeUi {
    val container = JBBox(BoxLayout.X_AXIS).apply {
      isOpaque = true
      background = Color(0xF2F2F2)
      border = JBUI.Borders.empty(5, 10)
      add(touchpadPanel)
      add(createHorizontalGlue())
    }
    val ui = FakeUi(container)
    ui.root.size = Dimension(300, 80)
    ui.layoutAndDispatchEvents()
    return ui
  }

  private fun createGlassesAvd(): FakeEmulator =
      emulatorRule.newEmulator(FakeEmulator.createAiGlassesAvd(emulatorRule.avdRoot)).apply { start(standalone = false) }

  private fun assertAppearance(goldenImageName: String) {
    val image = ui.render()
    val maxPercentDifferent = when {
      SystemInfo.isMac -> 0.5
      SystemInfo.isWindows -> 0.5
      else -> 0.0
    }
    ImageDiffUtil.assertImageSimilar(getGoldenFile(goldenImageName), image, maxPercentDifferent)
  }

  private fun getGoldenFile(name: String): Path =
      TestUtils.resolveWorkspacePathUnchecked("$GOLDEN_FILE_PATH/$name.png")
}

private const val GOLDEN_FILE_PATH = "tools/adt/idea/streaming/testData/TouchpadPanelTest/golden"
