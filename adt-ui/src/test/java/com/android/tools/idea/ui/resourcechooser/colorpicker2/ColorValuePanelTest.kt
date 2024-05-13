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

import com.intellij.testFramework.HeavyPlatformTestCase
import org.junit.Assert
import java.awt.Color
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import javax.swing.KeyStroke
import kotlin.math.roundToInt

class ColorValuePanelTest : HeavyPlatformTestCase() {

  fun testChangeColorModeFromRGBToHSB() {
    val model = ColorPickerModel()
    val panel = ColorValuePanel(model)
    panel.setSize(300, 300)

    panel.currentAlphaFormat = AlphaFormat.BYTE
    panel.currentColorFormat = ColorFormat.RGB
    model.setColor(Color.YELLOW)

    assertEquals(Color.YELLOW.alpha.toString(), panel.alphaField.text)
    assertEquals(Color.YELLOW.red.toString(), panel.colorField1.text)
    assertEquals(Color.YELLOW.green.toString(), panel.colorField2.text)
    assertEquals(Color.YELLOW.blue.toString(), panel.colorField3.text)
    assertEquals(Integer.toHexString(Color.YELLOW.rgb).uppercase(), panel.hexField.text)

    panel.currentColorFormat = ColorFormat.HSB
    val hsb = Color.RGBtoHSB(Color.YELLOW.red, Color.YELLOW.green, Color.YELLOW.blue, null)
    assertEquals(Color.YELLOW.alpha.toString(), panel.alphaField.text)
    assertEquals((hsb[0] * 360).roundToInt().toString(), panel.colorField1.text)
    assertEquals((hsb[1] * 100).roundToInt().toString(), panel.colorField2.text)
    assertEquals((hsb[2] * 100).roundToInt().toString(), panel.colorField3.text)
    assertEquals(Integer.toHexString(Color.YELLOW.rgb).uppercase(), panel.hexField.text)
  }

  fun testChangeColorModeFromHSBToRGB() {
    val model = ColorPickerModel()
    val panel = ColorValuePanel(model)
    panel.setSize(300, 300)

    panel.currentAlphaFormat = AlphaFormat.BYTE
    panel.currentColorFormat = ColorFormat.HSB
    val argb = (0x12 shl 24) or (0x00FFFFFF and Color.HSBtoRGB(0.3f, 0.4f, 0.5f))
    val color = Color(argb, true)
    model.setColor(color)

    assertEquals(0x12.toString(), panel.alphaField.text)
    assertEquals((0.3f * 360).roundToInt().toString(), panel.colorField1.text)
    assertEquals((0.4f * 100).roundToInt().toString(), panel.colorField2.text)
    assertEquals((0.5f * 100).roundToInt().toString(), panel.colorField3.text)
    assertEquals(Integer.toHexString(color.rgb).uppercase(), panel.hexField.text)

    panel.currentColorFormat = ColorFormat.RGB
    assertEquals(color.alpha.toString(), panel.alphaField.text)
    assertEquals(color.red.toString(), panel.colorField1.text)
    assertEquals(color.green.toString(), panel.colorField2.text)
    assertEquals(color.blue.toString(), panel.colorField3.text)
    assertEquals(Integer.toHexString(color.rgb).uppercase(), panel.hexField.text)
  }

  fun testChangeAlphaModeFromByteToPercentage() {
    val model = ColorPickerModel()
    val panel = ColorValuePanel(model)
    panel.setSize(300, 300)

    panel.currentAlphaFormat = AlphaFormat.BYTE
    model.setColor(Color.YELLOW)

    assertEquals(Color.YELLOW.alpha.toString(), panel.alphaField.text)

    panel.currentAlphaFormat = AlphaFormat.PERCENTAGE
    assertEquals((Color.YELLOW.alpha * 100f / 0xFF).roundToInt().toString(), panel.alphaField.text)
  }

  fun testChangeAlphaModeFromPercentageToByte() {
    val model = ColorPickerModel()
    val panel = ColorValuePanel(model)
    panel.setSize(300, 300)

    panel.currentAlphaFormat = AlphaFormat.PERCENTAGE
    val color = Color(0x80 shl 24, true)
    model.setColor(color)

    assertEquals("50", panel.alphaField.text)

    panel.currentAlphaFormat = AlphaFormat.BYTE

    assertEquals(color.alpha.toString(), panel.alphaField.text)
  }

  fun testChangeModelWillUpdatePanelInRGBMode() {
    val model = ColorPickerModel()
    val panel = ColorValuePanel(model)
    panel.setSize(300, 300)

    panel.currentAlphaFormat = AlphaFormat.BYTE
    panel.currentColorFormat = ColorFormat.RGB

    model.setColor(Color.YELLOW)
    assertEquals(Color.YELLOW.alpha.toString(), panel.alphaField.text)
    assertEquals(Color.YELLOW.red.toString(), panel.colorField1.text)
    assertEquals(Color.YELLOW.green.toString(), panel.colorField2.text)
    assertEquals(Color.YELLOW.blue.toString(), panel.colorField3.text)
    assertEquals(Integer.toHexString(Color.YELLOW.rgb).uppercase(), panel.hexField.text)

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

    panel.currentAlphaFormat = AlphaFormat.BYTE
    panel.currentColorFormat = ColorFormat.RGB

    panel.colorField1.text = "200"
    panel.colorField2.text = "150"
    panel.colorField3.text = "100"
    panel.alphaField.text = "50"

    panel.updateAlarm.drainRequestsInTest()

    assertEquals(Color(200, 150, 100, 50), model.color)

    val redHex = 200.toString(16)
    val greenHex = 150.toString(16)
    val blueHex = 100.toString(16)
    val alphaHex = 50.toString(16)
    val hex = (alphaHex + redHex + greenHex + blueHex).uppercase()

    assertEquals(hex, panel.hexField.text)
  }

  fun testChangeHexFieldInRGBMode() {
    val model = ColorPickerModel()
    val panel = ColorValuePanel(model)
    panel.setSize(300, 300)

    panel.currentAlphaFormat = AlphaFormat.BYTE
    panel.currentColorFormat = ColorFormat.RGB
    panel.hexField.text = "10ABCDEF"
    panel.updateAlarm.drainRequestsInTest()

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

    panel.currentAlphaFormat = AlphaFormat.BYTE
    panel.currentColorFormat = ColorFormat.HSB

    model.setColor(Color.YELLOW)
    val yellowHsb = Color.RGBtoHSB(Color.YELLOW.red, Color.YELLOW.green, Color.YELLOW.blue, null)
    assertEquals(Color.YELLOW.alpha.toString(), panel.alphaField.text)
    assertEquals((yellowHsb[0] * 360).roundToInt().toString(), panel.colorField1.text)
    assertEquals((yellowHsb[1] * 100).roundToInt().toString(), panel.colorField2.text)
    assertEquals((yellowHsb[2] * 100).roundToInt().toString(), panel.colorField3.text)
    assertEquals(Integer.toHexString(Color.YELLOW.rgb).uppercase(), panel.hexField.text)

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

    panel.currentAlphaFormat = AlphaFormat.BYTE
    panel.currentColorFormat = ColorFormat.HSB

    panel.colorField1.text = "180"
    panel.colorField2.text = "50"
    panel.colorField3.text = "30"
    panel.alphaField.text = "200"

    panel.updateAlarm.drainRequestsInTest()

    val rgbValue = Color.HSBtoRGB(180 / 360f, 50 / 100f, 30 / 100f)
    val color = Color((200 shl 24) or (0x00FFFFFF and rgbValue), true)
    assertEquals(color, model.color)
    assertEquals((200.toString(16) + (0x00FFFFFF and rgbValue).toString(16)).uppercase(), panel.hexField.text)
  }

  fun testChangeHexFieldInHSBMode() {
    val model = ColorPickerModel()
    val panel = ColorValuePanel(model)
    panel.setSize(300, 300)

    panel.currentAlphaFormat = AlphaFormat.BYTE
    panel.currentColorFormat = ColorFormat.HSB
    panel.hexField.text = "10ABCDEF"
    panel.updateAlarm.drainRequestsInTest()

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

    panel.currentAlphaFormat = AlphaFormat.BYTE
    model.setColor(Color(0xFF, 0xFF, 0xFF, 0xFF), null)

    panel.alphaField.text = "200"
    panel.updateAlarm.drainRequestsInTest()

    assertEquals(200, model.color.alpha)
  }

  fun testChangeAlphaFieldsInPercentageMode() {
    val model = ColorPickerModel()
    val panel = ColorValuePanel(model)
    panel.setSize(300, 300)

    panel.currentAlphaFormat = AlphaFormat.PERCENTAGE
    model.setColor(Color(0xFF, 0xFF, 0xFF, 0xFF), null)

    panel.alphaField.text = "50"
    panel.updateAlarm.drainRequestsInTest()

    assertEquals(0x80, model.color.alpha)
  }

  fun testChangeHexFieldWhenAlphaIsByteMode() {
    val model = ColorPickerModel()
    val panel = ColorValuePanel(model)
    panel.setSize(300, 300)

    panel.currentAlphaFormat = AlphaFormat.BYTE
    panel.hexField.text = "10ABCDEF"
    panel.updateAlarm.drainRequestsInTest()

    assertEquals("16", panel.alphaField.text)
  }

  fun testChangeHexFieldWhenAlphaIsPercentageMode() {
    val model = ColorPickerModel()
    val panel = ColorValuePanel(model)
    panel.setSize(300, 300)

    panel.currentAlphaFormat = AlphaFormat.BYTE
    panel.hexField.text = "80ABCDEF"
    panel.updateAlarm.drainRequestsInTest()

    assertEquals("128", panel.alphaField.text)
  }

  fun testHexFiledHasLeftPaddingZero() {
    val model = ColorPickerModel()
    val panel = ColorValuePanel(model)
    panel.setSize(300, 300)

    model.setColor(Color(0, 0, 0, 0x0F))
    assertEquals("0F000000", panel.hexField.text)

    model.setColor(Color(0xF0, 0, 0, 0))
    assertEquals("00F00000", panel.hexField.text)

    model.setColor(Color(0x0F, 0, 0, 0))
    assertEquals("000F0000", panel.hexField.text)

    model.setColor(Color(0, 0xF0, 0, 0))
    assertEquals("0000F000", panel.hexField.text)

    model.setColor(Color(0, 0x0F, 0, 0))
    assertEquals("00000F00", panel.hexField.text)

    model.setColor(Color(0, 0, 0xF0, 0))
    assertEquals("000000F0", panel.hexField.text)

    model.setColor(Color(0, 0, 0x0F, 0))
    assertEquals("0000000F", panel.hexField.text)

    model.setColor(Color(0, 0, 0, 0))
    assertEquals("00000000", panel.hexField.text)
  }

  fun testUpAndDownOnColorField() {
    val model = ColorPickerModel()
    val panel = ColorValuePanel(model)

    run {
      panel.currentAlphaFormat = AlphaFormat.BYTE
      val key = KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0)
      val action = panel.alphaField.getActionForKeyStroke(key)!!
      val actionEvent = ActionEvent(panel.alphaField, 0, key.keyChar.toString(), key.modifiers)

      model.setColor(Color(0, 0, 0, 128), null)
      action.actionPerformed(actionEvent)
      assertEquals(129, panel.alphaField.colorValue)

      model.setColor(Color(0, 0, 0, 255), null)
      action.actionPerformed(actionEvent)
      assertEquals(255, panel.alphaField.colorValue)
    }

    run {
      panel.currentAlphaFormat = AlphaFormat.PERCENTAGE
      val key = KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0)
      val action = panel.alphaField.getActionForKeyStroke(key)!!
      val actionEvent = ActionEvent(panel.alphaField, 0, key.keyChar.toString(), key.modifiers)

      model.setColor(Color(0, 0, 0, 128), null)
      action.actionPerformed(actionEvent)
      assertEquals(51, panel.alphaField.colorValue)

      model.setColor(Color(0, 0, 0, 255), null)
      action.actionPerformed(actionEvent)
      assertEquals(100, panel.alphaField.colorValue)
    }

    run {
      panel.currentAlphaFormat = AlphaFormat.BYTE
      val key = KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0)
      val action = panel.alphaField.getActionForKeyStroke(key)!!
      val actionEvent = ActionEvent(panel.alphaField, 0, key.keyChar.toString(), key.modifiers)

      model.setColor(Color(0, 0, 0, 128), null)
      action.actionPerformed(actionEvent)
      assertEquals(127, panel.alphaField.colorValue)

      model.setColor(Color(0, 0, 0, 0), null)
      action.actionPerformed(actionEvent)
      assertEquals(0, panel.alphaField.colorValue)
    }

    run {
      panel.currentAlphaFormat = AlphaFormat.PERCENTAGE
      val key = KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0)
      val action = panel.alphaField.getActionForKeyStroke(key)!!
      val actionEvent = ActionEvent(panel.alphaField, 0, key.keyChar.toString(), key.modifiers)

      model.setColor(Color(0, 0, 0, 128), null)
      action.actionPerformed(actionEvent)
      assertEquals(49, panel.alphaField.colorValue)

      model.setColor(Color(0, 0, 0, 0), null)
      action.actionPerformed(actionEvent)
      assertEquals(0, panel.alphaField.colorValue)
    }
  }

  fun testKeyEventOnAlphaLabel() {
    val model = ColorPickerModel()
    val panel = ColorValuePanel(model)

    val alphaButtonPanel = panel.alphaButtonPanel
    val key = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0, true)
    val action = alphaButtonPanel.getActionForKeyStroke(key)!!
    val actionEvent = ActionEvent(alphaButtonPanel, 0, key.keyChar.toString(), key.modifiers)

    panel.currentAlphaFormat = AlphaFormat.BYTE
    action.actionPerformed(actionEvent)
    Assert.assertEquals(AlphaFormat.PERCENTAGE, panel.currentAlphaFormat)

    action.actionPerformed(actionEvent)
    Assert.assertEquals(AlphaFormat.BYTE, panel.currentAlphaFormat)
  }

  fun testKeyEventOnColorFormatLabel() {
    val model = ColorPickerModel()
    val panel = ColorValuePanel(model)

    val colorFormatButtonPanel = panel.colorFormatButtonPanel
    val key = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0, true)
    val action = colorFormatButtonPanel.getActionForKeyStroke(key)!!
    val actionEvent = ActionEvent(colorFormatButtonPanel, 0, key.keyChar.toString(), key.modifiers)

    panel.currentColorFormat = ColorFormat.RGB
    action.actionPerformed(actionEvent)
    Assert.assertEquals(ColorFormat.HSB, panel.currentColorFormat)

    action.actionPerformed(actionEvent)
    Assert.assertEquals(ColorFormat.RGB, panel.currentColorFormat)
  }
}
