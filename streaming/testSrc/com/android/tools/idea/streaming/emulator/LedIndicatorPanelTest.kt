/*
 * Copyright (C) 2026 The Android Open Source Project
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

import com.android.emulator.control.LedIndicator
import com.android.testutils.waitForCondition
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.streaming.emulator.EmulatorController.ConnectionState.CONNECTED
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.ui.scale.JBUIScale
import java.awt.Color
import java.awt.Dimension
import java.awt.event.MouseEvent
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Tests for [LedIndicatorPanel]. */
@RunsInEdt
class LedIndicatorPanelTest {

  private val applicationRule = ApplicationRule()
  private val emulatorRule = FakeEmulatorRule()
  private val disposableRule = DisposableRule()

  @get:Rule val ruleChain = RuleChain(applicationRule, emulatorRule, disposableRule, EdtRule())

  private lateinit var glasses: FakeEmulator
  private lateinit var emulatorController: EmulatorController
  private lateinit var ledPanel: LedIndicatorPanel
  private lateinit var ui: FakeUi

  @Before
  fun setUp() {
    glasses = emulatorRule.newEmulator(FakeEmulator.createDisplayGlassesAvd(emulatorRule.avdRoot)).apply { start() }

    val glassesPort = glasses.grpcPort
    val emulators = runBlocking { RunningEmulatorCatalog.getInstance().updateNow().await() }
    emulatorController = emulators.find { it.emulatorId.grpcPort == glassesPort }!!
    waitForCondition(2.seconds) { emulatorController.connectionState == CONNECTED }

    ledPanel = LedIndicatorPanel(emulatorController, disposableRule.disposable)
    ledPanel.size = Dimension(30, 40)

    ui = FakeUi(ledPanel)
    ui.layoutAndDispatchEvents()
  }

  @Test
  fun testLedIndicatorTooltipsAndStateChanges() {
    val defaultBackground = Color(ui.render(ledPanel).getRGB(0, 0), true)

    // Both LEDs should be OFF initially.
    assertThat(getTooltipText(4)).isEqualTo("Outside LED")
    assertThat(getTooltipText(24)).isEqualTo("Inside LED")
    assertThat(getLedColors()).containsExactly(defaultBackground, defaultBackground).inOrder()

    // Turn LED 0 on.
    glasses.setLedState(LedIndicator.Facing.INSIDE, Color.RED)
    waitForCondition(2.seconds) { getLedColors()[1] == Color.RED }
    assertThat(getLedColors()).containsExactly(defaultBackground, Color.RED).inOrder()

    // Turn LED 1 on
    glasses.setLedState(LedIndicator.Facing.OUTSIDE, Color.GREEN)
    waitForCondition(2.seconds) { getLedColors()[0] == Color.GREEN }
    assertThat(getLedColors()).containsExactly(Color.GREEN, Color.RED).inOrder()

    // Turn LED 0 off
    glasses.setLedState(LedIndicator.Facing.INSIDE, null)
    waitForCondition(2.seconds) { getLedColors()[1] == defaultBackground }
    assertThat(getLedColors()).containsExactly(Color.GREEN, defaultBackground).inOrder()
  }

  private fun getTooltipText(y: Int): String? {
    val x = ledPanel.width / 2
    val event = MouseEvent(ledPanel, MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), 0, x, y, 0, false)
    return ledPanel.getToolTipText(event)
  }

  private fun getLedColors(): List<Color> {
    val image = ui.render(ledPanel)
    val size = JBUIScale.scale(LedIndicatorPanel.INDICATOR_SIZE)
    val spacing = JBUIScale.scale(LedIndicatorPanel.INDICATOR_SPACING)
    val x = (ledPanel.width - size) / 2 + size / 2
    return (0..1).map { index ->
      val y = index * (size + spacing) + size / 2
      Color(image.getRGB(x, y), true)
    }
  }
}
