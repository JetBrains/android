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
import kotlin.math.roundToInt

class ColorValuePanelTest : IdeaTestCase() {

  fun testChangeColorModeFromRGBToHSB() {
    val model = ColorPickerModel()
    val panel = ColorValuePanel(model)
    panel.setSize(300, 300)

    panel.currentAlphaFormat = ColorValuePanel.AlphaFormat.BYTE
    panel.currentColorFormat = ColorValuePanel.ColorFormat.RGB
    model.setColor(Color.YELLOW)

    assertEquals(Color.YELLOW.alpha.toString(), panel.alphaField.text)
    assertEquals(Color.YELLOW.red.toString(), panel.colorField1.text)
    assertEquals(Color.YELLOW.green.toString(), panel.colorField2.text)
    assertEquals(Color.YELLOW.blue.toString(), panel.colorField3.text)
    assertEquals(Integer.toHexString(Color.YELLOW.rgb).toUpperCase(), panel.hexField.text)

    panel.currentColorFormat = ColorValuePanel.ColorFormat.HSB
    val hsb = Color.RGBtoHSB(Color.YELLOW.red, Color.YELLOW.green, Color.YELLOW.blue, null)
    assertEquals(Color.YELLOW.alpha.toString(), panel.alphaField.text)
    assertEquals((hsb[0] * 360).roundToInt().toString(), panel.colorField1.text)
    assertEquals((hsb[1] * 100).roundToInt().toString(), panel.colorField2.text)
    assertEquals((hsb[2] * 100).roundToInt().toString(), panel.colorField3.text)
    assertEquals(Integer.toHexString(Color.YELLOW.rgb).toUpperCase(), panel.hexField.text)
  }

  fun testChangeColorModeFromHSBToRGB() {
    val model = ColorPickerModel()
    val panel = ColorValuePanel(model)
    panel.setSize(300, 300)

    panel.currentAlphaFormat = ColorValuePanel.AlphaFormat.BYTE
    panel.currentColorFormat = ColorValuePanel.ColorFormat.HSB
    val argb = (0x12 shl 24) or (0x00FFFFFF and Color.HSBtoRGB(0.3f, 0.4f, 0.5f))
    val color = Color(argb, true)
    model.setColor(color)

    assertEquals(0x12.toString(), panel.alphaField.text)
    assertEquals((0.3f * 360).roundToInt().toString(), panel.colorField1.text)
    assertEquals((0.4f * 100).roundToInt().toString(), panel.colorField2.text)
    assertEquals((0.5f * 100).roundToInt().toString(), panel.colorField3.text)
    assertEquals(Integer.toHexString(color.rgb).toUpperCase(), panel.hexField.text)

    panel.currentColorFormat = ColorValuePanel.ColorFormat.RGB
    assertEquals(color.alpha.toString(), panel.alphaField.text)
    assertEquals(color.red.toString(), panel.colorField1.text)
    assertEquals(color.green.toString(), panel.colorField2.text)
    assertEquals(color.blue.toString(), panel.colorField3.text)
    assertEquals(Integer.toHexString(color.rgb).toUpperCase(), panel.hexField.text)
  }

  fun testChangeAlphaModeFromByteToPercentage() {
    val model = ColorPickerModel()
    val panel = ColorValuePanel(model)
    panel.setSize(300, 300)

    panel.currentAlphaFormat = ColorValuePanel.AlphaFormat.BYTE
    model.setColor(Color.YELLOW)

    assertEquals(Color.YELLOW.alpha.toString(), panel.alphaField.text)

    panel.currentAlphaFormat = ColorValuePanel.AlphaFormat.PERCENTAGE
    assertEquals((Color.YELLOW.alpha * 100f / 0xFF).roundToInt().toString(), panel.alphaField.text)
  }

  fun testChangeAlphaModeFromPercentageToByte() {
    val model = ColorPickerModel()
    val panel = ColorValuePanel(model)
    panel.setSize(300, 300)

    panel.currentAlphaFormat = ColorValuePanel.AlphaFormat.PERCENTAGE
    val color = Color(0x80 shl 24, true)
    model.setColor(color)

    assertEquals("50", panel.alphaField.text)

    panel.currentAlphaFormat = ColorValuePanel.AlphaFormat.BYTE

    assertEquals(color.alpha.toString(), panel.alphaField.text)
  }

  fun testChangeModelWillUpdatePanelInRGBMode() {
    val model = ColorPickerModel()
    val panel = ColorValuePanel(model)
    panel.setSize(300, 300)

    panel.currentAlphaFormat = ColorValuePanel.AlphaFormat.BYTE
    panel.currentColorFormat = ColorValuePanel.ColorFormat.RGB

    model.setColor(Color.YELLOW)
    assertEquals(Color.YELLOW.alpha.toString(), panel.alphaField.text)
    assertEquals(Color.YELLOW.red.toString(), panel.colorField1.text)
    assertEquals(Color.YELLOW.green.toString(), panel.colorField2.text)
    assertEquals(Color.YELLOW.blue.toString(), panel.colorField3.text)
    assertEquals(Integer.toHexString(Color.YELLOW.rgb).toUpperCase(), panel.hexField.text)

    val newColor = Color(0x40, 0x50, 0x60, 0x70)
    model.setColor(newColor)
    assertEquals(newColor.alpha.toString(), panel.alphaField.text)
    assertEquals(newColor.red.toString(), panel.colorField1.text)
    assertEquals(newColor.green.toString(), panel.colorField2.text)
    assertEquals(newColor.blue.toString(), panel.colorField3.text)
    assertEquals("70405060", panel.hexField.text)
  }

  fun testChangeFieldsInRGBMode() {
    val model = ColorPickerModel()
    val panel = ColorValuePanel(model)
    panel.setSize(300, 300)

    panel.currentAlphaFormat = ColorValuePanel.AlphaFormat.BYTE
    panel.currentColorFormat = ColorValuePanel.ColorFormat.RGB

    panel.colorField1.text = "200"
    panel.colorField2.text = "150"
    panel.colorField3.text = "100"
    panel.alphaField.text = "50"

    panel.updateAlarm.flush()

    assertEquals(Color(200, 150, 100, 50), model.color)

    val redHex = 200.toString(16)
    val greenHex = 150.toString(16)
    val blueHex = 100.toString(16)
    val alphaHex = 50.toString(16)
    val hex = (alphaHex + redHex + greenHex + blueHex).toUpperCase()

    assertEquals(hex, panel.hexField.text)
  }

  fun testChangeHexFieldInRGBMode() {
    val model = ColorPickerModel()
    val panel = ColorValuePanel(model)
    panel.setSize(300, 300)

    panel.currentAlphaFormat = ColorValuePanel.AlphaFormat.BYTE
    panel.currentColorFormat = ColorValuePanel.ColorFormat.RGB
    panel.hexField.text = "10ABCDEF"
    panel.updateAlarm.flush()

    assertEquals(Color(0xAB, 0xCD, 0xEF, 0x10), model.color)

    val red = 0xAB.toString()
    val green = 0xCD.toString()
    val blue = 0xEF.toString()
    val alpha = 0x10.toString()

    assertEquals(red, panel.colorField1.text)
    assertEquals(green, panel.colorField2.text)
    assertEquals(blue, panel.colorField3.text)
    assertEquals(alpha, panel.alphaField.text)
  }

  fun testChangeModelWillUpdatePanelInHSBMode() {
    val model = ColorPickerModel()
    val panel = ColorValuePanel(model)
    panel.setSize(300, 300)

    panel.currentAlphaFormat = ColorValuePanel.AlphaFormat.BYTE
    panel.currentColorFormat = ColorValuePanel.ColorFormat.HSB

    model.setColor(Color.YELLOW)
    val yellowHsb = Color.RGBtoHSB(Color.YELLOW.red, Color.YELLOW.green, Color.YELLOW.blue, null)
    assertEquals(Color.YELLOW.alpha.toString(), panel.alphaField.text)
    assertEquals((yellowHsb[0] * 360).roundToInt().toString(), panel.colorField1.text)
    assertEquals((yellowHsb[1] * 100).roundToInt().toString(), panel.colorField2.text)
    assertEquals((yellowHsb[2] * 100).roundToInt().toString(), panel.colorField3.text)
    assertEquals(Integer.toHexString(Color.YELLOW.rgb).toUpperCase(), panel.hexField.text)

    val newColor = Color(0x40, 0x50, 0x60, 0x70)
    val newColorHsb = Color.RGBtoHSB(newColor.red, newColor.green, newColor.blue, null)
    model.setColor(newColor)
    assertEquals(newColor.alpha.toString(), panel.alphaField.text)
    assertEquals((newColorHsb[0] * 360).roundToInt().toString(), panel.colorField1.text)
    assertEquals((newColorHsb[1] * 100).roundToInt().toString(), panel.colorField2.text)
    assertEquals((newColorHsb[2] * 100).roundToInt().toString(), panel.colorField3.text)
    assertEquals("70405060", panel.hexField.text)
  }

  fun testChangeFieldsInHSBMode() {
    val model = ColorPickerModel()
    val panel = ColorValuePanel(model)
    panel.setSize(300, 300)

    panel.currentAlphaFormat = ColorValuePanel.AlphaFormat.BYTE
    panel.currentColorFormat = ColorValuePanel.ColorFormat.HSB

    panel.colorField1.text = "180"
    panel.colorField2.text = "50"
    panel.colorField3.text = "30"
    panel.alphaField.text = "200"

    panel.updateAlarm.flush()

    val rgbValue = Color.HSBtoRGB(180 / 360f, 50 / 100f, 30 / 100f)
    val color = Color((200 shl 24) or (0x00FFFFFF and rgbValue), true)
    assertEquals(color, model.color)
    assertEquals((200.toString(16) + (0x00FFFFFF and rgbValue).toString(16)).toUpperCase(), panel.hexField.text)
  }

  fun testChangeHexFieldInHSBMode() {
    val model = ColorPickerModel()
    val panel = ColorValuePanel(model)
    panel.setSize(300, 300)

    panel.currentAlphaFormat = ColorValuePanel.AlphaFormat.BYTE
    panel.currentColorFormat = ColorValuePanel.ColorFormat.HSB
    panel.hexField.text = "10ABCDEF"
    panel.updateAlarm.flush()

    assertEquals(Color(0xAB, 0xCD, 0xEF, 0x10), model.color)

    val hsb = Color.RGBtoHSB(0xAB, 0xCD, 0xEF, null)

    val hue = (hsb[0] * 360).roundToInt().toString()
    val saturation = (hsb[1] * 100).roundToInt().toString()
    val brightness = (hsb[2] * 100).roundToInt().toString()
    val alpha = 0x10.toString()

    assertEquals(hue, panel.colorField1.text)
    assertEquals(saturation, panel.colorField2.text)
    assertEquals(brightness, panel.colorField3.text)
    assertEquals(alpha, panel.alphaField.text)
  }

  fun testChangeAlphaFieldsInByteMode() {
    val model = ColorPickerModel()
    val panel = ColorValuePanel(model)
    panel.setSize(300, 300)

    panel.currentAlphaFormat = ColorValuePanel.AlphaFormat.BYTE
    model.setColor(Color(0xFF, 0xFF, 0xFF, 0xFF), null)

    panel.alphaField.text = "200"
    panel.updateAlarm.flush()

    assertEquals(200, model.color.alpha)
  }

  fun testChangeAlphaFieldsInPercentageMode() {
    val model = ColorPickerModel()
    val panel = ColorValuePanel(model)
    panel.setSize(300, 300)

    panel.currentAlphaFormat = ColorValuePanel.AlphaFormat.PERCENTAGE
    model.setColor(Color(0xFF, 0xFF, 0xFF, 0xFF), null)

    panel.alphaField.text = "50"
    panel.updateAlarm.flush()

    assertEquals(0x80, model.color.alpha)
  }

  fun testChangeHexFieldWhenAlphaIsByteMode() {
    val model = ColorPickerModel()
    val panel = ColorValuePanel(model)
    panel.setSize(300, 300)

    panel.currentAlphaFormat = ColorValuePanel.AlphaFormat.BYTE
    panel.hexField.text = "10ABCDEF"
    panel.updateAlarm.flush()

    assertEquals("16", panel.alphaField.text)
  }

  fun testChangeHexFieldWhenAlphaIsPercentageMode() {
    val model = ColorPickerModel()
    val panel = ColorValuePanel(model)
    panel.setSize(300, 300)

    panel.currentAlphaFormat = ColorValuePanel.AlphaFormat.BYTE
    panel.hexField.text = "80ABCDEF"
    panel.updateAlarm.flush()

    assertEquals("128", panel.alphaField.text)
  }
}
