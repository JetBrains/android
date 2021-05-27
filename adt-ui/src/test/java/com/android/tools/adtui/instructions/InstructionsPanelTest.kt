/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.adtui.instructions

import com.android.tools.adtui.TabularLayout
import com.android.tools.adtui.common.AdtUiUtils
import com.android.tools.adtui.model.EaseOutModel
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.adtui.model.updater.Updater
import com.android.tools.adtui.swing.FakeUi
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.util.ui.UIUtilities
import org.junit.Rule
import org.junit.Test
import java.awt.BorderLayout
import java.awt.Cursor
import javax.swing.JPanel
import kotlin.math.roundToInt

@RunsInEdt
class InstructionsPanelTest {
  @get:Rule
  val edtRule = EdtRule()

  @Test
  fun testPanelRemovedFromParentWhenFadedOut() {
    val timer = FakeTimer()
    val updater = Updater(timer)
    val easeOut = EaseOutModel(updater, FakeTimer.ONE_SECOND_IN_NS)
    val panel = JPanel(BorderLayout())

    val instructions = InstructionsPanel.Builder(
      TextInstruction(UIUtilities.getFontMetrics(panel, AdtUiUtils.DEFAULT_FONT), "InstructionsPanelTest"))
      .setEaseOut(easeOut, { child -> panel.remove(child) })
      .build()
    panel.add(instructions, BorderLayout.CENTER)

    // After 1 second, fade out should start the next update.
    timer.tick(FakeTimer.ONE_SECOND_IN_NS)
    assertThat(panel.components).asList().contains(instructions)

    // 1st update would start lerping the fade ratio, but the instructions should still be in the hierarchy
    timer.tick(FakeTimer.ONE_SECOND_IN_NS)
    assertThat(panel.components).asList().contains(instructions)

    // 2nd update would fade out the instructions completely, at which point the panel will be auto-removed.
    timer.tick(FakeTimer.ONE_SECOND_IN_NS)
    assertThat(panel.components).asList().doesNotContain(instructions)
  }

  @Test
  fun instructionsPanelCursorChangesWhenMouseOverUrl() {
    // Create two rows, so we can assert that moving the mouse out of the instructions panel clears
    // the cursor
    val panel = JPanel(TabularLayout("Fit-"))
    val metrics = UIUtilities.getFontMetrics(panel, AdtUiUtils.DEFAULT_FONT)

    val instructions = InstructionsPanel.Builder(
      TextInstruction(metrics, "Line 1"),
      NewRowInstruction(0),
      HyperlinkInstruction(metrics.font, "Line 2", "www.google.com"),
      NewRowInstruction(0),
      TextInstruction(metrics, "Line 3"))
      .setPaddings(0, 0)
      .build()
    panel.add(instructions, TabularLayout.Constraint(0, 0))

    val fakeUi = FakeUi(panel)
    panel.size = panel.minimumSize // Force size just to make the test work
    val rowHeight = instructions.renderer.rowHeight
    val yLine1Text = (rowHeight * 0.5f).roundToInt()
    val yLine2Url = (rowHeight * 1.5f).roundToInt()
    val yLine3Text = (rowHeight * 2.5f).roundToInt()

    assertThat(fakeUi.mouse.focus).isNull()

    fakeUi.mouse.moveTo(20, yLine1Text)
    val instructionsComponent = fakeUi.mouse.focus!!
    assertThat(instructionsComponent.cursor).isEqualTo(Cursor.getDefaultCursor())

    fakeUi.mouse.moveTo(20, yLine2Url)
    assertThat(instructionsComponent.cursor).isEqualTo(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR))

    fakeUi.mouse.moveTo(20, yLine3Text)
    assertThat(instructionsComponent.cursor).isEqualTo(Cursor.getDefaultCursor())

    fakeUi.mouse.moveTo(20, yLine2Url)
    assertThat(instructionsComponent.cursor).isEqualTo(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR))

    assertThat(fakeUi.mouse.focus).isNotNull()
    fakeUi.mouse.moveTo(Int.MAX_VALUE, Int.MAX_VALUE) // Force mouseExited event
    assertThat(fakeUi.mouse.focus).isNull()
    assertThat(instructionsComponent.cursor).isEqualTo(Cursor.getDefaultCursor())

    fakeUi.mouse.moveTo(20, yLine2Url)
    assertThat(instructionsComponent.cursor).isEqualTo(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR))
  }

  @Test
  fun instructionsPanelCLickOnUrlRunsAction() {
    val panel = JPanel(TabularLayout("Fit-"))
    val metrics = UIUtilities.getFontMetrics(panel, AdtUiUtils.DEFAULT_FONT)

    var actionPerformed = false
    val action = Runnable { actionPerformed = true }

    val instructions = InstructionsPanel.Builder(HyperlinkInstruction(metrics.font, "Hyperlink", action))
      .setPaddings(0, 0)
      .build()
    panel.add(instructions, TabularLayout.Constraint(0, 0))

    val fakeUi = FakeUi(panel)
    panel.size = panel.minimumSize // Force size just to make the test work
    val rowHeight = instructions.renderer.rowHeight
    val yHyperlink = (rowHeight * 0.5f).roundToInt()

    assertThat(fakeUi.mouse.focus).isNull()

    fakeUi.mouse.moveTo(20, yHyperlink)
    val instructionsComponent = fakeUi.mouse.focus!!
    assertThat(instructionsComponent.cursor).isEqualTo(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR))

    fakeUi.mouse.click(20, yHyperlink)
    assertThat(actionPerformed).isTrue()
  }

  @Test
  fun instructionPanelSetsCursor() {
    val panel = JPanel(TabularLayout("Fit-"))
    val metrics = UIUtilities.getFontMetrics(panel, AdtUiUtils.DEFAULT_FONT)

    val action = Runnable { }

    val instructions = InstructionsPanel.Builder(HyperlinkInstruction(metrics.font, "Hyperlink", action))
      .setPaddings(0, 0)
      .setCursorSetter { _, cursor -> panel.cursor = cursor; panel }
      .build()
    panel.add(instructions, TabularLayout.Constraint(0, 0))

    val fakeUi = FakeUi(panel)
    panel.size = panel.minimumSize // Force size just to make the test work
    val rowHeight = instructions.renderer.rowHeight
    val yHyperlink = (rowHeight * 0.5f).roundToInt()

    assertThat(fakeUi.mouse.focus).isNull()

    fakeUi.mouse.moveTo(20, yHyperlink)
    assertThat(panel.cursor).isEqualTo(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR))
  }
}