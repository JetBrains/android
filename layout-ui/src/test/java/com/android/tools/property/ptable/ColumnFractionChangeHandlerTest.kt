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
package com.android.tools.property.ptable

import com.android.tools.adtui.model.stdui.ValueChangedListener
import com.android.tools.adtui.swing.FakeUi
import com.google.common.truth.Truth.assertThat
import com.intellij.ui.scale.JBUIScale.scale
import com.intellij.util.ui.JBDimension
import org.junit.Test
import java.awt.BorderLayout
import javax.swing.JPanel

class ColumnFractionChangeHandlerTest {

  @Test
  fun testMoveAndDrag() {
    val panel1 = JPanel(BorderLayout()).apply { size = JBDimension(50, 80) }
    val panel2 = JPanel().apply { preferredSize = JBDimension(10, 80) }
    val panel3 = JPanel().apply { preferredSize = JBDimension(40, 80) }
    panel1.add(panel2, BorderLayout.WEST)
    panel1.add(panel3, BorderLayout.CENTER)
    panel1.doLayout()

    val fraction = ColumnFraction(initialValue = 0.5f, resizeSupported = true)
    var fractionChanges = 0
    fraction.listeners.add(ValueChangedListener { fractionChanges++ })

    var modeChanges = 0
    var lastMode = false
    val handler = ColumnFractionChangeHandler(fraction, { panel3.x }, { panel1.width }) {
      lastMode = it
      modeChanges++
    }
    panel3.addMouseListener(handler)
    panel3.addMouseMotionListener(handler)

    // With an offset of 10, expect the range to be from 0-40 with a resize range of 13..18 (which with the offset corresponds to 23..28).
    val ui = FakeUi(panel3)
    ui.mouse.moveTo(0, 0)
    assertThat(lastMode).isFalse()
    assertThat(modeChanges).isEqualTo(0)

    ui.mouse.moveTo(scale(12), scale(5))
    assertThat(lastMode).isFalse()
    assertThat(modeChanges).isEqualTo(0)

    ui.mouse.moveTo(scale(13), scale(5))
    assertThat(lastMode).isTrue()
    assertThat(modeChanges).isEqualTo(1)

    ui.mouse.moveTo(scale(18), scale(5))
    assertThat(lastMode).isTrue()
    assertThat(modeChanges).isEqualTo(1)

    ui.mouse.moveTo(scale(19), scale(5))
    assertThat(lastMode).isFalse()
    assertThat(modeChanges).isEqualTo(2)

    ui.mouse.moveTo(scale(15), scale(5))
    assertThat(lastMode).isTrue()
    assertThat(modeChanges).isEqualTo(3)

    ui.mouse.press(scale(15), scale(5))
    assertThat(lastMode).isTrue()
    assertThat(modeChanges).isEqualTo(3)

    assertThat(fractionChanges).isEqualTo(0)
    ui.mouse.dragTo(scale(20), scale(5))
    assertThat(lastMode).isTrue()
    assertThat(modeChanges).isEqualTo(3)
    assertThat(fraction.value).isEqualTo(0.6f)
    assertThat(fractionChanges).isEqualTo(1)

    ui.mouse.release()

    ui.mouse.moveTo(scale(25), scale(5))
    assertThat(lastMode).isFalse()
    assertThat(modeChanges).isEqualTo(4)

    ui.mouse.moveTo(scale(21), scale(5))
    assertThat(lastMode).isTrue()
    assertThat(modeChanges).isEqualTo(5)

    // Move out of panel3: (will cause a mouse exit event)
    ui.mouse.moveTo(scale(21), scale(-5))
    assertThat(lastMode).isFalse()
    assertThat(modeChanges).isEqualTo(6)

    assertThat(fraction.value).isEqualTo(0.6f)
    assertThat(fractionChanges).isEqualTo(1)
  }
}
