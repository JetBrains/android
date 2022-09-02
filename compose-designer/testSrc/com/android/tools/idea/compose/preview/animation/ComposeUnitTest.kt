/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.animation

import androidx.compose.animation.tooling.ComposeAnimatedProperty
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.Test

class ComposeUnitTest {

  @Test
  fun parseInt() {
    val composeUnit = ComposeUnit.parse(ComposeAnimatedProperty("", 1))
    assertNotNull(composeUnit)
    composeUnit as ComposeUnit.Unit1D
    assertEquals(1, composeUnit.component1)
    assertEquals(listOf(1), composeUnit.components)
    assertEquals("1", composeUnit.toString(0))
    assertEquals("1", composeUnit.toString())
    assertFalse { composeUnit is ComposeUnit.Unit2D<*> }
  }

  @Test
  fun parseDouble() {
    val composeUnit = ComposeUnit.parse(ComposeAnimatedProperty("", 1.2345))
    assertNotNull(composeUnit)
    composeUnit as ComposeUnit.Unit1D
    assertEquals(1.2345, composeUnit.component1)
    assertEquals(listOf(1.2345), composeUnit.components)
    assertEquals("1.2345", composeUnit.toString(0))
    assertEquals("1.2345", composeUnit.toString())
    assertFalse { composeUnit is ComposeUnit.Unit2D<*> }
  }

  @Test
  fun parseFloat() {
    val composeUnit = ComposeUnit.parse(ComposeAnimatedProperty("", 1.2345f))
    assertNotNull(composeUnit)
    composeUnit as ComposeUnit.Unit1D
    assertEquals(1.2345f, composeUnit.component1)
    assertEquals(listOf(1.2345f), composeUnit.components)
    assertEquals("1.2345", composeUnit.toString(0))
    assertEquals("1.2345", composeUnit.toString())
    assertFalse { composeUnit is ComposeUnit.Unit2D<*> }
  }

  @Test
  fun parseDp() {
    @Suppress("unused") // Methods are called via reflection by tests.
    class Dp {
      fun getValue() = 1.2345f
    }

    val composeUnit = ComposeUnit.Dp.create(Dp())
    assertNotNull(composeUnit)
    assertEquals(1.2345f, composeUnit.component1)
    assertEquals("1.2345dp", composeUnit.toString(0))
    assertEquals("1.2345dp", composeUnit.toString())
    assertEquals(listOf(1.2345f), composeUnit.components)
  }

  @Suppress("unused") // Methods are called via reflection by tests.
  class ValidIntOffset {
    fun `unbox-impl`() = 0L

    companion object {
      @JvmStatic fun `getX-impl`(impl: Long) = 1

      @JvmStatic fun `getY-impl`(impl: Long) = 2
    }
  }

  @Test
  fun parseIntOffset() {
    val composeUnit = ComposeUnit.IntOffset.create(ValidIntOffset())
    assertNotNull(composeUnit)
    assertEquals(1, composeUnit.component1)
    assertEquals(2, composeUnit.component2)
    assertEquals("x ( 1 , _ )", composeUnit.toString(0))
    assertEquals("y ( _ , 2 )", composeUnit.toString(1))
    assertEquals("( 1 , 2 )", composeUnit.toString())
    assertEquals(listOf(1, 2), composeUnit.components)
  }

  @Suppress("unused") // Methods are called via reflection by tests.
  class ValidIntSize {
    fun `unbox-impl`() = 0L

    companion object {
      @JvmStatic fun `getWidth-impl`(impl: Long) = 1

      @JvmStatic fun `getHeight-impl`(impl: Long) = 2
    }
  }

  @Test
  fun parseIntSize() {
    val composeUnit = ComposeUnit.IntSize.create(ValidIntSize())
    assertNotNull(composeUnit)
    assertEquals(1, composeUnit.component1)
    assertEquals(2, composeUnit.component2)
    assertEquals("width ( 1 , _ )", composeUnit.toString(0))
    assertEquals("height ( _ , 2 )", composeUnit.toString(1))
    assertEquals("( 1 , 2 )", composeUnit.toString())
    assertEquals(listOf(1, 2), composeUnit.components)
  }

  @Suppress("unused") // Methods are called via reflection by tests.
  class ValidSize {
    fun `unbox-impl`() = 0L

    companion object {
      @JvmStatic fun `getWidth-impl`(impl: Long) = 1.1f

      @JvmStatic fun `getHeight-impl`(impl: Long) = 2.2f
    }
  }

  @Test
  fun parseSize() {
    val composeUnit = ComposeUnit.Size.create(ValidSize())
    assertNotNull(composeUnit)
    assertEquals(1.1f, composeUnit.component1)
    assertEquals(2.2f, composeUnit.component2)
    assertEquals("width ( 1.1 , _ )", composeUnit.toString(0))
    assertEquals("height ( _ , 2.2 )", composeUnit.toString(1))
    assertEquals("( 1.1 , 2.2 )", composeUnit.toString())
    assertEquals(listOf(1.1f, 2.2f), composeUnit.components)
  }

  @Suppress("unused") // Methods are called via reflection by tests.
  class ValidOffset {
    fun `unbox-impl`() = 0L

    companion object {
      @JvmStatic fun `getX-impl`(impl: Long) = 1.1f

      @JvmStatic fun `getY-impl`(impl: Long) = 2.2f
    }
  }

  @Test
  fun parseOffset() {
    val composeUnit = ComposeUnit.Offset.create(ValidOffset())
    assertNotNull(composeUnit)
    assertEquals(1.1f, composeUnit.component1)
    assertEquals(2.2f, composeUnit.component2)
    assertEquals("x ( 1.1 , _ )", composeUnit.toString(0))
    assertEquals("y ( _ , 2.2 )", composeUnit.toString(1))
    assertEquals("( 1.1 , 2.2 )", composeUnit.toString())
    assertEquals(listOf(1.1f, 2.2f), composeUnit.components)
  }

  @Suppress("unused") // Methods are called via reflection by tests.
  class ValidColor {
    fun `unbox-impl`() = 0L

    companion object {
      @JvmStatic fun `getRed-impl`(impl: Long) = 0.1f

      @JvmStatic fun `getGreen-impl`(impl: Long) = 0.2f

      @JvmStatic fun `getBlue-impl`(impl: Long) = 0.3f

      @JvmStatic fun `getAlpha-impl`(impl: Long) = 0.4f
    }
  }

  @Test
  fun parseColor() {
    val composeUnit = ComposeUnit.Color.create(ValidColor())
    assertNotNull(composeUnit)
    assertEquals(0.1f, composeUnit.component1)
    assertEquals(0.2f, composeUnit.component2)
    assertEquals(0.3f, composeUnit.component3)
    assertEquals(0.4f, composeUnit.component4)
    assertEquals("red ( 0.1 , _ , _ , _ )", composeUnit.toString(0))
    assertEquals("green ( _ , 0.2 , _ , _ )", composeUnit.toString(1))
    assertEquals("blue ( _ , _ , 0.3 , _ )", composeUnit.toString(2))
    assertEquals("alpha ( _ , _ , _ , 0.4 )", composeUnit.toString(3))
    assertEquals("( 0.1 , 0.2 , 0.3 , 0.4 )", composeUnit.toString())
    assertEquals(listOf(0.1f, 0.2f, 0.3f, 0.4f), composeUnit.components)
    assertNotNull(composeUnit.color)
  }

  @Test
  fun parseInvalidDp() {
    @Suppress("unused") // Methods are called via reflection by tests.
    class Dp {
      fun getValue() = 1 // Not a float.
    }

    val composeUnit = ComposeUnit.Dp.create(Dp())
    assertNull(composeUnit)
  }

  @Test
  fun parseInvalidDpWithoutMethod() {
    class Dp

    val composeUnit = ComposeUnit.Dp.create(Dp())
    assertNull(composeUnit)
  }

  @Test
  fun parseRect() {
    @Suppress("unused") // Methods are called via reflection by tests.
    class Rect {
      fun getLeft() = 1.222f
      fun getTop() = 2.222f
      fun getRight() = 3.222f
      fun getBottom() = 4.222f
    }

    val composeUnit = ComposeUnit.Rect.create(Rect())
    assertNotNull(composeUnit)
    assertEquals(1.222f, composeUnit.component1)
    assertEquals(2.222f, composeUnit.component2)
    assertEquals(3.222f, composeUnit.component3)
    assertEquals(4.222f, composeUnit.component4)
    assertEquals("left ( 1.222 , _ , _ , _ )", composeUnit.toString(0))
    assertEquals("top ( _ , 2.222 , _ , _ )", composeUnit.toString(1))
    assertEquals("right ( _ , _ , 3.222 , _ )", composeUnit.toString(2))
    assertEquals("bottom ( _ , _ , _ , 4.222 )", composeUnit.toString(3))
    assertEquals("( 1.222 , 2.222 , 3.222 , 4.222 )", composeUnit.toString())
    assertEquals(listOf(1.222f, 2.222f, 3.222f, 4.222f), composeUnit.components)
  }

  @Test
  fun parseInvalidRect() {
    @Suppress("unused") // Methods are called via reflection by tests.
    class Rect {
      // Not float values.
      fun getLeft() = 1
      fun getTop() = 2
      fun getRight() = 3
      fun getBottom() = 4
    }

    val composeUnit = ComposeUnit.Rect.create(Rect())
    assertNull(composeUnit)
  }

  @Test
  fun parseInvalidRectWithoutMethods() {
    @Suppress("unused") // Methods are called via reflection by tests.
    class Rect {
      fun getLeft() = 1f
      fun getTop() = 2f
      // No getRight() and getBottom() methods.
    }

    val composeUnit = ComposeUnit.Rect.create(Rect())
    assertNull(composeUnit)
  }

  @Test
  fun parseInvalidIntSize() {
    class IntSize

    val composeUnit = ComposeUnit.IntSize.create(IntSize())
    assertNull(composeUnit)
  }

  @Test
  fun parseInvalidIntOffset() {
    class IntOffset

    val composeUnit = ComposeUnit.IntSize.create(IntOffset())
    assertNull(composeUnit)
  }

  @Test
  fun parseInvalidSize() {
    class Size

    val composeUnit = ComposeUnit.Size.create(Size())
    assertNull(composeUnit)
  }

  @Test
  fun parseInvalidOffset() {
    class Offset

    val composeUnit = ComposeUnit.Offset.create(Offset())
    assertNull(composeUnit)
  }

  @Test
  fun parseInvalidColor() {
    class Color

    val composeUnit = ComposeUnit.Color.create(Color())
    assertNull(composeUnit)
  }

  @Test
  fun parseUnknown() {
    val composeUnit = ComposeUnit.parse(ComposeAnimatedProperty("", "Unknown"))
    assertNotNull(composeUnit)
    assertEquals("Unknown", composeUnit.toString())
    assertEquals("Unknown", composeUnit.toString(1))
    assertEquals(1, composeUnit.components.size)
  }

  @Test
  fun createValidColor() {
    val composeUnit = ComposeUnit.Color(0.1f, 0.1f, 0.1f, 0.1f)
    assertNotNull(composeUnit)
    assertNotNull(composeUnit.color)
  }

  @Test
  fun createInvalidColor() {
    val composeUnit = ComposeUnit.Color(10f, 10f, 10f, 10f)
    assertNotNull(composeUnit)
    assertNull(composeUnit.color)
  }

  @Test
  fun parseNull() {
    val result = ComposeUnit.parseValue(null)
    assertNull(result)
  }
}
