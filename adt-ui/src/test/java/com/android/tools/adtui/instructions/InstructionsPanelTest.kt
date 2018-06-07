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

import com.android.tools.adtui.common.AdtUiUtils
import com.android.tools.adtui.model.EaseOutModel
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.adtui.model.updater.Updater
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.awt.BorderLayout
import javax.swing.JPanel

class InstructionsPanelTest {
  @Test
  fun testPanelRemovedFromParentWhenFadedOut() {
    val timer = FakeTimer()
    val updater = Updater(timer)
    val easeOut = EaseOutModel(updater, FakeTimer.ONE_SECOND_IN_NS)
    val panel = JPanel(BorderLayout())

    val instructions = InstructionsPanel.Builder(TextInstruction(AdtUiUtils.DEFAULT_FONT, "InstructionsPanelTest"))
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
}