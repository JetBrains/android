/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.wear.preview.animation

import java.awt.Color
import org.junit.Assert.*
import org.junit.Test

class ColorUnitTest {

  @Test
  fun testConstructorWithColor() {
    val color = Color(123, 45, 67, 255)
    val colorUnit = ColorUnit(color)

    assertEquals(color, colorUnit.color)
    assertEquals(123, colorUnit.components[0])
    assertEquals(45, colorUnit.components[1])
    assertEquals(67, colorUnit.components[2])
    assertEquals(255, colorUnit.components[3])
  }

  @Test
  fun testConstructorWithRGBA() {
    val colorUnit = ColorUnit(123, 45, 67, 255)

    assertEquals(Color(123, 45, 67, 255), colorUnit.color)
    assertEquals(123, colorUnit.components[0])
    assertEquals(45, colorUnit.components[1])
    assertEquals(67, colorUnit.components[2])
    assertEquals(255, colorUnit.components[3])
  }

  @Test
  fun testConstructorWithRGBA_DefaultAlpha() {
    val colorUnit = ColorUnit(123, 45, 67)

    assertEquals(Color(123, 45, 67, 255), colorUnit.color)
    assertEquals(123, colorUnit.components[0])
    assertEquals(45, colorUnit.components[1])
    assertEquals(67, colorUnit.components[2])
    assertEquals(255, colorUnit.components[3])
  }

  @Test
  fun testParseColorUnit_ValidInt() {
    val argb = Color(123, 45, 67, 255).rgb
    val colorUnit = ColorUnit.parseColorUnit(argb)

    assertEquals(Color(123, 45, 67, 255), colorUnit.color)
  }

  @Test(expected = IllegalArgumentException::class)
  fun testParseColorUnit_InvalidInput() {
    ColorUnit.parseColorUnit("invalid")
  }

  @Test
  fun testToString() {
    val colorUnit = ColorUnit(123, 45, 67, 255)

    assertEquals("R: 123", colorUnit.toString(0))
    assertEquals("G: 45", colorUnit.toString(1))
    assertEquals("B: 67", colorUnit.toString(2))
    assertEquals("A: 255", colorUnit.toString(3))
    assertEquals("Invalid componentId", colorUnit.toString(4))
  }

  @Test
  fun testParseUnit_ValidString() {
    val getValue: (Int) -> String? = {
      when (it) {
        0 -> "R: 123, G: 45, B: 67, A: 255"
        else -> null
      }
    }
    val parsedUnit = ColorUnit(0, 0, 0, 0).parseUnit(getValue)

    assertNotNull(parsedUnit)
    assertTrue(parsedUnit is ColorUnit)
    assertEquals(Color(123, 45, 67, 255), (parsedUnit as ColorUnit).color)
  }

  @Test
  fun testParseUnit_InvalidString() {
    val getValue: (Int) -> String? = { "invalid" }
    val parsedUnit = ColorUnit(0, 0, 0, 0).parseUnit(getValue)

    assertNull(parsedUnit)
  }

  @Test
  fun testCreate() {
    val color = Color(123, 45, 67, 255)
    val colorUnit = ColorUnit(0, 0, 0, 0).create(color)

    assertEquals(color, colorUnit.color)
  }

  @Test
  fun testComponentAsDouble() {
    val colorUnit = ColorUnit(123, 45, 67, 255)

    assertEquals(123.0, colorUnit.componentAsDouble(0), 0.0)
    assertEquals(45.0, colorUnit.componentAsDouble(1), 0.0)
    assertEquals(67.0, colorUnit.componentAsDouble(2), 0.0)
    assertEquals(255.0, colorUnit.componentAsDouble(3), 0.0)
  }

  @Test
  fun testGetPickerTitle() {
    val colorUnit = ColorUnit(0, 0, 0, 0)
    val expectedTitle = "Color"

    assertEquals(expectedTitle, colorUnit.getPickerTitle())
  }
}
