/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.profilers.cpu.capturedetails

import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.instructions.InstructionsPanel
import com.android.tools.adtui.instructions.TextInstruction
import com.android.tools.profilers.cpu.capturedetails.CaptureDetailsView.NO_DATA_FOR_RANGE_MESSAGE
import com.android.tools.profilers.cpu.capturedetails.CaptureDetailsView.NO_DATA_FOR_THREAD_MESSAGE
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.awt.CardLayout
import javax.swing.JPanel

class CaptureDetailsViewTest {

  @Test
  fun testNoDataForRange() {
    val textInstruction = TreeWalker(CaptureDetailsView.getNoDataForRange())
      .descendants()
      .filterIsInstance<InstructionsPanel>()
      .first()
      .getRenderInstructionsForComponent(0)
      .first() as TextInstruction

    assertThat(textInstruction.text).isEqualTo(NO_DATA_FOR_RANGE_MESSAGE)
  }

  @Test
  fun testNoDataForThread() {
    val textInstruction = TreeWalker(CaptureDetailsView.getNoDataForThread())
      .descendants()
      .filterIsInstance<InstructionsPanel>()
      .first()
      .getRenderInstructionsForComponent(0)
      .first() as TextInstruction

    assertThat(textInstruction.text).isEqualTo(NO_DATA_FOR_THREAD_MESSAGE)
  }

  @Test
  fun testSwitchCardLayout() {
    val panel = JPanel(CardLayout())
    val cardContent = JPanel()
    val emptyContent = JPanel()
    panel.add(cardContent, CaptureDetailsView.CARD_CONTENT)
    panel.add(emptyContent, CaptureDetailsView.CARD_EMPTY_INFO)

    assertThat(cardContent.isVisible).isTrue()
    assertThat(emptyContent.isVisible).isFalse()

    CaptureDetailsView.switchCardLayout(panel, true)
    assertThat(cardContent.isVisible).isFalse()
    assertThat(emptyContent.isVisible).isTrue()

    CaptureDetailsView.switchCardLayout(panel, false)
    assertThat(cardContent.isVisible).isTrue()
    assertThat(emptyContent.isVisible).isFalse()
  }
}