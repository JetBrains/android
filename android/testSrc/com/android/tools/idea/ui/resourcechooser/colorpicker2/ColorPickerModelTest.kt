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

import com.intellij.ui.picker.ColorListener
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito
import java.awt.Color

class ColorPickerModelTest {

  @Test
  fun testDefaultColor() {
    val model = ColorPickerModel()
    assertEquals(Color(0xFF, 0xFF, 0xFF, 0xFF), model.color)
    assertEquals(0xFF, model.red)
    assertEquals(0xFF, model.green)
    assertEquals(0xFF, model.blue)
    assertEquals(0xFF, model.alpha)
    assertEquals("ffffffff", model.hex)
    assertEquals(0.0f, model.hue)
    assertEquals(0.0f, model.saturation)
    assertEquals(1.0f, model.brightness)
  }

  @Test
  fun testGivingOriginalColor() {
    val model = ColorPickerModel(Color(0x09, 0x56, 0xaf, 0xcd))
    assertEquals(Color(0x09, 0x56, 0xaf, 0xcd), model.color)
    assertEquals(0x09, model.red)
    assertEquals(0x56, model.green)
    assertEquals(0xaf, model.blue)
    assertEquals(0xcd, model.alpha)
    assertEquals("cd0956af", model.hex)
    assertEquals(0.58935744f, model.hue)
    assertEquals(0.94857144f, model.saturation)
    assertEquals(0.6862745f, model.brightness)
  }

  @Test
  fun testSetColor() {
    val model = ColorPickerModel()
    assertEquals(Color(0xFF, 0xFF, 0xFF, 0xFF), model.color)

    model.setColor(Color(0x66, 0x88, 0xAA, 0xCC), null)
    assertEquals(Color(0x66, 0x88, 0xAA, 0xCC), model.color)
    assertEquals(0x66, model.red)
    assertEquals(0x88, model.green)
    assertEquals(0xAA, model.blue)
    assertEquals(0xCC, model.alpha)
    assertEquals("cc6688aa", model.hex)
    assertEquals(0.5833333f, model.hue)
    assertEquals(0.4f, model.saturation)
    assertEquals(0.6666667f, model.brightness)
  }

  @Test
  fun testSetColorWithoutAlpha() {
    val model = ColorPickerModel(Color(0xFF, 0x00, 0x88))
    // If the input Color doesn't have alpha argument, it means its alpha is 0xFF. (See constructor of Color)
    assertEquals(Color(0xFF, 0x00, 0x88, 0xFF), model.color)
    assertEquals(0xFF, model.red)
    assertEquals(0x00, model.green)
    assertEquals(0x88, model.blue)
    assertEquals(0xFF, model.alpha)
    assertEquals("ffff0088", model.hex)
    assertEquals(0.9111111f, model.hue)
    assertEquals(1.0f, model.saturation)
    assertEquals(1.0f, model.brightness)
  }

  @Test
  fun testAddingColorListener() {
    val model = ColorPickerModel(Color(0xFF, 0x00, 0x88))
    val listener = Mockito.mock(ColorListener::class.java)
    model.addListener(listener)
    val newColor = Color(0x00, 0x88, 0xFF)
    model.setColor(newColor)
    Mockito.verify(listener, Mockito.times(1)).colorChanged(newColor, null)
  }
}
