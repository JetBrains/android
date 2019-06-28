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

import com.intellij.testFramework.JavaProjectTestCase
import java.awt.Color
import java.awt.Dimension

class ColorAdjustPanelTest : JavaProjectTestCase() {

  fun testChangeModelColorWillUpdateAllComponent() {
    val model = ColorPickerModel()
    val panel = ColorAdjustPanel(model, FakeColorPipetteProvider())
    panel.size = Dimension(1000, 300)

    val indicator = panel.colorIndicator
    val hueSlide = panel.hueSlider
    val alphaSlide = panel.alphaSlider

    model.setColor(Color.BLUE, null)
    assertEquals(Color.BLUE, indicator.color)
    val blueHue = Color.RGBtoHSB(Color.BLUE.red, Color.BLUE.green, Color.BLUE.blue, null)[0]
    assertEquals(Math.round(blueHue * 360), hueSlide.value)
    assertEquals(Color.BLUE, alphaSlide.sliderBackgroundColor)

    model.setColor(Color.YELLOW, null)
    assertEquals(Color.YELLOW, indicator.color)
    val yellowHue = Color.RGBtoHSB(Color.YELLOW.red, Color.YELLOW.green, Color.YELLOW.blue, null)[0]
    assertEquals(Math.round(yellowHue * 360), hueSlide.value)
    assertEquals(Color.YELLOW, alphaSlide.sliderBackgroundColor)
  }
}
