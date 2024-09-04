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
package com.android.tools.idea.preview.animation

import kotlin.test.assertNotNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimatedPropertyTest {

  @Test
  fun buildFlatCurve() {
    val builder = AnimatedProperty.Builder()
    builder.add(0, AnimationUnit.FloatUnit(10f))
    builder.add(1, AnimationUnit.FloatUnit(10f))
    builder.add(2, AnimationUnit.FloatUnit(10f))
    builder.add(3, AnimationUnit.FloatUnit(10f))
    builder.add(4, AnimationUnit.FloatUnit(10f))
    val result = builder.build()
    assertNotNull(result)
    assertEquals(result.startMs, 0)
    assertEquals(result.endMs, 4)
    assertEquals(result.dimension, 1)
    assertEquals(result.components.size, 1)
    assertTrue(result.grouped)

    result.components[0].let {
      assertFalse(it.linkToNext)
      assertEquals(10.0, it.maxValue, 0.0)
      assertEquals(10.0, it.minValue, 0.0)
      assertEquals(mapOf(0 to 10.0, 1 to 10.0, 2 to 10.0, 3 to 10.0, 4 to 10.0), it.points)
    }
  }

  @Test
  fun buildCurveWithStartEndPointsSet() {
    val result =
      AnimatedProperty.Builder()
        .add(2, AnimationUnit.FloatUnit(10f))
        .add(3, AnimationUnit.FloatUnit(15f))
        .setStartTimeMs(0)
        .setEndTimeMs(4)
        .build()
    assertNotNull(result)
    assertEquals(result.startMs, 0)
    assertEquals(result.endMs, 4)
    assertEquals(result.dimension, 1)
    assertEquals(result.components.size, 1)
    assertTrue(result.grouped)

    result.components[0].let {
      assertFalse(it.linkToNext)
      assertEquals(15.0, it.maxValue, 0.0)
      assertEquals(10.0, it.minValue, 0.0)
      assertEquals(mapOf(2 to 10.0, 3 to 15.0), it.points)
    }
  }

  @Test
  fun buildRectCurve() {
    val builder = AnimatedProperty.Builder()
    builder.add(10, TestMultiUnitFloat(6f, 3f, 6f, 2f))
    builder.add(15, TestMultiUnitFloat(1f, 2f, 3f, 4f))
    builder.add(5, TestMultiUnitFloat(1f, 3f, 6f, 3f))
    val result = builder.build()
    assertNotNull(result)
    assertEquals(4, result.dimension)
    assertEquals(15, result.endMs)
    assertEquals(5, result.startMs)
    assertEquals(4, result.components.size)
    assertFalse(result.grouped)

    result.components[0].let {
      assertTrue(it.linkToNext)
      assertEquals(6.0, it.maxValue, 0.0)
      assertEquals(1.0, it.minValue, 0.0)
      assertEquals(mapOf(5 to 1.0, 10 to 6.0, 15 to 1.0), it.points)
    }

    result.components[1].let {
      assertTrue(it.linkToNext)
      assertEquals(3.0, it.maxValue, 0.0)
      assertEquals(2.0, it.minValue, 0.0)
      assertEquals(mapOf(5 to 3.0, 10 to 3.0, 15 to 2.0), it.points)
    }

    result.components[2].let {
      assertTrue(it.linkToNext)
      assertEquals(6.0, it.maxValue, 0.0)
      assertEquals(3.0, it.minValue, 0.0)
      assertEquals(mapOf(5 to 6.0, 10 to 6.0, 15 to 3.0), it.points)
    }

    result.components[3].let {
      assertFalse(it.linkToNext)
      assertEquals(4.0, it.maxValue, 0.0)
      assertEquals(2.0, it.minValue, 0.0)
      assertEquals(mapOf(5 to 3.0, 10 to 2.0, 15 to 4.0), it.points)
    }
  }

  @Test
  fun buildRectWithExactlySameCurves() {
    val builder = AnimatedProperty.Builder()

    builder.add(5, TestMultiUnitFloat(1f, 3f, 0f, -9f))
    builder.add(10, TestMultiUnitFloat(2f, 6f, 300f, -6f))
    builder.add(15, TestMultiUnitFloat(3f, 9f, 600f, -3f))
    val result = builder.build()
    assertNotNull(result)
    assertEquals(1, result.dimension)
    assertEquals(15, result.endMs)
    assertEquals(5, result.startMs)
    assertEquals(1, result.components.size)
    assertTrue(result.grouped)

    result.components[0].let {
      assertFalse(it.linkToNext)
      assertEquals(3.0, it.maxValue, 0.0)
      assertEquals(1.0, it.minValue, 0.0)
      assertEquals(mapOf(5 to 1.0, 10 to 2.0, 15 to 3.0), it.points)
    }
  }

  @Test
  fun buildRectWithSimilarCurves() {
    mapOf(97f to false, 99f to true, 101f to true, 103f to false).forEach {
      val builder = AnimatedProperty.Builder()
      builder.add(5, TestMultiUnitFloat(1f, 1f, 0f, -9f))
      builder.add(10, TestMultiUnitFloat(2f, 2f, it.key, -6f))
      builder.add(15, TestMultiUnitFloat(3f, 3f, 200f, -3f))
      val result = builder.build()
      assertEquals("Grouped for ${it.key}", it.value, result!!.grouped)
    }
  }

  @Test
  fun buildRectCurveWithOnePoint() {
    val builder = AnimatedProperty.Builder()
    builder.add(10, TestMultiUnitFloat(6f, 3f, 6f, 2f))
    val result = builder.build()
    assertNotNull(result)
    assertEquals(1, result.dimension)
    assertEquals(10, result.endMs)
    assertEquals(10, result.startMs)
    assertEquals(1, result.components.size)
    assertTrue(result.grouped)

    result.components[0].let {
      assertFalse(it.linkToNext)
      assertEquals(6.0, it.maxValue, 0.0)
      assertEquals(6.0, it.minValue, 0.0)
      assertEquals(mapOf(10 to 6.0), it.points)
    }
  }

  @Test
  fun buildWithInvalidDimensions() {
    val builder = AnimatedProperty.Builder()
    builder.add(10, TestMultiUnitFloat(6f, 3f, 6f, 2f))
    builder.add(15, AnimationUnit.IntUnit(1))
    builder.add(5, TestMultiUnitFloat(1f, 3f, 6f, 3f))
    val result = builder.build()
    assertNull(result)
  }

  @Test
  fun buildWithInvalidTypes() {
    val builder = AnimatedProperty.Builder()
    // Rect and Color are with the same dimension but types are different
    builder.add(10, TestMultiUnitFloat(6f, 3f, 6f, 2f))
    builder.add(5, TestMultiUnitInt(6, 3, 6, 2))
    val result = builder.build()
    assertNull(result)
  }

  @Test
  fun buildEmpty() {
    val builder = AnimatedProperty.Builder()
    val result = builder.build()
    assertNull(result)
  }

  @Test
  fun buildWithEmptyValues() {
    val builder = AnimatedProperty.Builder()
    builder.setStartTimeMs(0)
    builder.setEndTimeMs(100)

    val result = builder.build()
    assertNull(result)
  }

  class TestMultiUnitFloat(value1: Float, value2: Float, value3: Float, value4: Float) :
    AnimationUnit.BaseUnit<Float>(value1, value2, value3, value4), AnimationUnit.NumberUnit<Float> {
    override fun parseUnit(getValue: (Int) -> String?): AnimationUnit.Unit<*>? {
      return null
    }

    override fun getPickerTitle(): String = "testFloat"
  }

  class TestMultiUnitInt(value1: Int, value2: Int, value3: Int, value4: Int) :
    AnimationUnit.BaseUnit<Int>(value1, value2, value3, value4), AnimationUnit.NumberUnit<Int> {
    override fun parseUnit(getValue: (Int) -> String?): AnimationUnit.Unit<*>? {
      return null
    }

    override fun getPickerTitle(): String = "testInt"
  }
}
