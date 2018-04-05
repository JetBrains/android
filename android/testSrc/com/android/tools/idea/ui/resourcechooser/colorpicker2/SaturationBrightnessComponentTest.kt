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
import java.awt.Color
import java.awt.event.MouseEvent

class SaturationBrightnessComponentTest {

  @Test
  fun testPickColor() {
    val model = ColorPickerModel()
    val brightnessComponent = SaturationBrightnessComponent(model)
    brightnessComponent.setSize(300, 300)

    model.setColor(Color(0x00, 0xFF, 0xFF, 0xFF))

    val centerX = brightnessComponent.x + brightnessComponent.width / 2
    val centerY = brightnessComponent.y + brightnessComponent.height / 2
    brightnessComponent.dispatchEvent(MouseEvent(brightnessComponent,
                                                 MouseEvent.MOUSE_PRESSED,
                                                 System.currentTimeMillis(),
                                                 0,
                                                 centerX,
                                                 centerY,
                                                 1,
                                                 false))

    assertEquals(Color(0x40, 0x80, 0x80, 0xFF), model.color)
  }

  @Test
  fun testUpdateColorWhenModelColorIsChanged() {
    val model = ColorPickerModel()
    val brightnessComponent = SaturationBrightnessComponent(model)
    brightnessComponent.setSize(300, 300)

    model.setColor(Color(0x00, 0xFF, 0xFF, 0x60))
    assertEquals(0.5f, brightnessComponent.hue, 0.01f)
    assertEquals(1.0f, brightnessComponent.saturation, 0.01f)
    assertEquals(1.0f, brightnessComponent.brightness, 0.01f)
    assertEquals(0x60, brightnessComponent.alpha)

    model.setColor(Color(0x80, 0x80, 0x80, 0xFF))
    assertEquals(0.0f, brightnessComponent.hue, 0.01f)
    assertEquals(0.0f, brightnessComponent.saturation, 0.01f)
    assertEquals(0.5f, brightnessComponent.brightness, 0.01f)
    assertEquals(0xFF, brightnessComponent.alpha)

    model.setColor(Color(0xA0, 0x80, 0x40, 0x00))
    assertEquals(0.11f, brightnessComponent.hue, 0.01f)
    assertEquals(0.6f, brightnessComponent.saturation, 0.01f)
    assertEquals(0.63f, brightnessComponent.brightness, 0.01f)
    assertEquals(0x00, brightnessComponent.alpha)
  }
}
