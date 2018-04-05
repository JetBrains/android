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

import com.intellij.testFramework.IdeaTestCase
import java.awt.Color

class ColorValuePanelTest : IdeaTestCase() {

  fun testChangeModelWillUpdatePanel() {
    val model = ColorPickerModel()
    val panel = ColorValuePanel(model)
    panel.setSize(300, 300)

    model.setColor(Color.YELLOW)
    assertEquals(panel.aField.text, Color.YELLOW.alpha.toString())
    assertEquals(panel.rField.text, Color.YELLOW.red.toString())
    assertEquals(panel.gField.text, Color.YELLOW.green.toString())
    assertEquals(panel.bField.text, Color.YELLOW.blue.toString())
    assertEquals(panel.hexField.text, Integer.toHexString(Color.YELLOW.rgb).toUpperCase())

    val newColor = Color(0x40, 0x50, 0x60, 0x70)
    model.setColor(newColor)
    assertEquals(panel.aField.text, newColor.alpha.toString())
    assertEquals(panel.rField.text, newColor.red.toString())
    assertEquals(panel.gField.text, newColor.green.toString())
    assertEquals(panel.bField.text, newColor.blue.toString())
    assertEquals(panel.hexField.text, "70405060")
  }

  fun testChangeARGBFields() {
    val model = ColorPickerModel()
    val panel = ColorValuePanel(model)
    panel.setSize(300, 300)

    panel.rField.text = "200"
    panel.gField.text = "150"
    panel.bField.text = "100"
    panel.aField.text = "50"

    panel.updateAlarm.flush()

    assertEquals(Color(200, 150, 100, 50), model.color)

    val redHex = 200.toString(16)
    val greenHex = 150.toString(16)
    val blueHex = 100.toString(16)
    val alphaHex = 50.toString(16)
    val hex = (alphaHex + redHex + greenHex + blueHex).toUpperCase()

    assertEquals(hex, panel.hexField.text)
  }

  fun testChangeHexField() {
    val model = ColorPickerModel()
    val panel = ColorValuePanel(model)
    panel.setSize(300, 300)

    panel.hexField.text = "10ABCDEF"

    panel.updateAlarm.flush()

    assertEquals(Color(0xAB, 0xCD, 0xEF, 0x10), model.color)

    val red = 0xAB.toString()
    val green = 0xCD.toString()
    val blue = 0xEF.toString()
    val alpha = 0x10.toString()

    assertEquals(red, panel.rField.text)
    assertEquals(green, panel.gField.text)
    assertEquals(blue, panel.bField.text)
    assertEquals(alpha, panel.aField.text)
  }
}
