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
package com.android.tools.idea.ui.resourcechooser.colorpicker2

import org.junit.Assert.*
import org.junit.Test
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import javax.swing.KeyStroke
import kotlin.math.ceil

class HueSliderComponentTest {

  @Test
  fun testChangeSliderValue() {
    val slider = HueSliderComponent()
    slider.setSize(1000, 100)

    slider.knobPosition = 0
    assertEquals(0, slider.value)

    slider.knobPosition = slider.sliderWidth / 2
    assertEquals(180, slider.value)

    slider.knobPosition = slider.sliderWidth
    assertEquals(360, slider.value)
  }

  @Test
  fun testChangeValue() {
    val slider = HueSliderComponent()
    slider.setSize(1000, 100)

    slider.value = 0
    assertEquals(0, slider.knobPosition)

    // The mapping between knobPosition and value may have some bias due to the floating issue. Bias is acceptable.
    slider.value = 180
    assertEquals(slider.sliderWidth / 2, slider.knobPosition, ceil(slider.sliderWidth / 2f))

    slider.value = 360
    assertEquals(slider.sliderWidth, slider.knobPosition)
  }

  @Test
  fun testKeyEvent() {
    val slider = HueSliderComponent()
    slider.setSize(1000, 100)

    run {
      // Test left key
      val key = KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0)
      val action = slider.getActionForKeyStroke(key)!!
      val actionEvent = ActionEvent(slider, 0, key.keyChar.toString(), key.modifiers)

      slider.value = 180
      action.actionPerformed(actionEvent)
      assertEquals(180 - SLIDE_UNIT, slider.value)

      slider.value = 0
      action.actionPerformed(actionEvent)
      assertEquals(0, slider.value)
    }

    run {
      // Test left key with alt
      val key = KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, KeyEvent.ALT_DOWN_MASK)
      val action = slider.getActionForKeyStroke(key)!!
      val actionEvent = ActionEvent(slider, 0, key.keyChar.toString(), key.modifiers)

      slider.value = 180
      action.actionPerformed(actionEvent)
      assertEquals(180 - 10 * SLIDE_UNIT, slider.value)

      slider.value = 0
      action.actionPerformed(actionEvent)
      assertEquals(0, slider.value)

      slider.value = 6
      action.actionPerformed(actionEvent)
      assertEquals(0, slider.value)
    }

    run {
      // Test right key
      val key = KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0)
      val action = slider.getActionForKeyStroke(key)!!
      val actionEvent = ActionEvent(slider, 0, key.keyChar.toString(), key.modifiers)

      slider.value = 180
      action.actionPerformed(actionEvent)
      assertEquals(180 + SLIDE_UNIT, slider.value)

      slider.value = 360
      action.actionPerformed(actionEvent)
      assertEquals(360, slider.value)
    }

    run {
      // Test right key with alt
      val key = KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, KeyEvent.ALT_DOWN_MASK)
      val action = slider.getActionForKeyStroke(key)!!
      val actionEvent = ActionEvent(slider, 0, key.keyChar.toString(), key.modifiers)

      slider.value = 180
      action.actionPerformed(actionEvent)
      assertEquals(180 + SLIDE_UNIT * 10, slider.value)

      slider.value = 360
      action.actionPerformed(actionEvent)
      assertEquals(360, slider.value)

      slider.value = 355
      action.actionPerformed(actionEvent)
      assertEquals(360, slider.value)
    }
  }

  private fun assertEquals(expect: Int, actual: Int, delta: Number) = assertEquals(expect.toDouble(), actual.toDouble(), delta.toDouble())
}
