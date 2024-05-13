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
import org.junit.Test
import org.mockito.Mockito
import java.awt.Color
import java.awt.Dimension
import java.awt.event.MouseEvent
import kotlin.test.assertNotEquals

class ColorAdjustPanelTest : HeavyPlatformTestCase() {

  @Test
  fun testPickColorFromHueSlider() {
    val model = ColorPickerModel()
    val panel = ColorAdjustPanel(model, FakeColorPipetteProvider())
    val slider = panel.hueSlider
    slider.setSize(300, 300)
    val initialColor = Color(0xFF, 0x00, 0x00, 0xFF)
    model.setColor(initialColor)
    val pressColor = Color(140, 255, 0, 0xFF)
    val dragColor = Color(0, 255, 255, 0xFF)
    val releaseColor = Color(140, 0, 255, 0xFF)

    val listener = Mockito.mock(ColorPickerListener::class.java)
    model.addListener(listener)

    testColorSlider(slider, initialColor, pressColor, dragColor, releaseColor, model, listener)
  }

  @Test
  fun testPickColorFromAlphaSlider() {
    val model = ColorPickerModel()
    val panel = ColorAdjustPanel(model, FakeColorPipetteProvider())
    val slider = panel.alphaSlider
    slider.setSize(300, 300)
    val initialColor = Color(0x00, 0x00, 0xFF, 0xFF)
    model.setColor(initialColor)
    val pressColor = Color(0x00, 0x00, 0xFF, 62)
    val dragColor = Color(0x00, 0x00, 0xFF, 128)
    val releaseColor = Color(0x00, 0x00, 0xFF, 193)

    val listener = Mockito.mock(ColorPickerListener::class.java)
    model.addListener(listener)

    testColorSlider(slider, initialColor, pressColor, dragColor, releaseColor, model, listener)
  }

  private fun testColorSlider(slider: SliderComponent<out Number>,
                              initialColor: Color,
                              pressColor: Color,
                              dragColor: Color,
                              releaseColor: Color,
                              model: ColorPickerModel,
                              listener: ColorPickerListener) {
    val centerY = slider.y + slider.height / 2

    slider.dispatchEvent(MouseEvent(slider,
                                    MouseEvent.MOUSE_PRESSED,
                                    System.currentTimeMillis(),
                                    0,
                                    slider.x + slider.width / 4,
                                    centerY,
                                    1,
                                    false))

    // pressing won't change the color in color model, but just trigger pickingColorChanged callback.
    Assert.assertEquals(initialColor, model.color)
    Mockito.verify(listener, Mockito.times(0)).colorChanged(pressColor, slider)
    Mockito.verify(listener, Mockito.times(1)).pickingColorChanged(pressColor, slider)

    slider.dispatchEvent(MouseEvent(slider,
                                    MouseEvent.MOUSE_DRAGGED,
                                    System.currentTimeMillis(),
                                    0,
                                    slider.x + slider.width / 2,
                                    centerY,
                                    1,
                                    false))
    // dragging won't change the color but trigger pickingColorChanged callback neither.
    Assert.assertEquals(initialColor, model.color)
    Mockito.verify(listener, Mockito.times(0)).colorChanged(dragColor, slider)
    Mockito.verify(listener, Mockito.times(1)).pickingColorChanged(dragColor, slider)


    slider.dispatchEvent(MouseEvent(slider,
                                    MouseEvent.MOUSE_RELEASED,
                                    System.currentTimeMillis(),
                                    0,
                                    slider.x + slider.width * 3 / 4,
                                    centerY,
                                    1,
                                    false))
    // releasing change the color of model but doesn't trigger pickingColorChanged callback.
    Assert.assertEquals(releaseColor, model.color)
    Mockito.verify(listener, Mockito.times(1)).colorChanged(releaseColor, slider)
    Mockito.verify(listener, Mockito.times(0)).pickingColorChanged(releaseColor, slider)
  }

  @Test
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

  @Test
  fun testHueSliderIsDraggableWhenColorIsWhiteOrBlack() {
    fun checkColor(c: Color) {
      val model = ColorPickerModel()
      val panel = ColorAdjustPanel(model, FakeColorPipetteProvider())
      val hueSlider = panel.hueSlider
      val alphaSlider = panel.alphaSlider

      panel.size = Dimension(1000, 300)
      hueSlider.size = Dimension(300, 300)
      alphaSlider.size = Dimension(300, 300)

      model.setColor(c)

      val oldHueKnobPosition = hueSlider.knobPosition
      val oldAlphaKnobPosition = alphaSlider.knobPosition

      simulateDragSlider(hueSlider, hueSlider.x + hueSlider.width / 4, hueSlider.x + hueSlider.width * 3 / 4)
      assertNotEquals(oldHueKnobPosition, hueSlider.knobPosition)
      assertEquals(oldAlphaKnobPosition, alphaSlider.knobPosition)

      val newHueKnobPosition = hueSlider.knobPosition

      simulateDragSlider(alphaSlider, alphaSlider.x + alphaSlider.width / 4, alphaSlider.x + alphaSlider.width * 3 / 4)
      assertEquals(newHueKnobPosition, hueSlider.knobPosition)
      assertNotEquals(oldAlphaKnobPosition, alphaSlider.knobPosition)
    }

    checkColor(Color.WHITE)
    checkColor(Color.BLACK)
  }

  private fun simulateDragSlider(slider: SliderComponent<out Number>, from: Int, to: Int) {
    val centerY = slider.y + slider.height / 2
    slider.dispatchEvent(MouseEvent(slider,
                                    MouseEvent.MOUSE_PRESSED,
                                    System.currentTimeMillis(),
                                    0,
                                    from,
                                    centerY,
                                    1,
                                    false))

    slider.dispatchEvent(MouseEvent(slider,
                                    MouseEvent.MOUSE_DRAGGED,
                                    System.currentTimeMillis(),
                                    0,
                                    (from + to) / 2,
                                    centerY,
                                    1,
                                    false))

    slider.dispatchEvent(MouseEvent(slider,
                                    MouseEvent.MOUSE_RELEASED,
                                    System.currentTimeMillis(),
                                    0,
                                    to,
                                    centerY,
                                    1,
                                    false))
  }
}
