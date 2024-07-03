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

import org.junit.Assert
import org.junit.Test

class AnimationUnitTest {
  @Test
  fun parseIntUnit() {
    val parsed = AnimationUnit.IntUnit(1).parseUnit { "2" }!!
    Assert.assertEquals(listOf(2), parsed.components)
  }

  @Test
  fun parseInvalidIntUnit() {
    Assert.assertNull(AnimationUnit.IntUnit(1).parseUnit { "2.3" })
    Assert.assertNull(AnimationUnit.IntUnit(1).parseUnit { "hello" })
    Assert.assertNull(AnimationUnit.IntUnit(1).parseUnit { "true" })
    Assert.assertNull(AnimationUnit.IntUnit(1).parseUnit { "2." })
    Assert.assertNull(AnimationUnit.IntUnit(1).parseUnit { "2f" })
  }

  @Test
  fun parseDoubleUnit() {
    val parsed = AnimationUnit.DoubleUnit(1.0).parseUnit { "2" }!!
    Assert.assertEquals(listOf(2.0), parsed.components)
  }

  @Test
  fun parseInvalidDoubleUnit() {
    Assert.assertNull(AnimationUnit.DoubleUnit(1.0).parseUnit { "2L" })
    Assert.assertNull(AnimationUnit.DoubleUnit(1.0).parseUnit { "hello" })
    Assert.assertNull(AnimationUnit.DoubleUnit(1.0).parseUnit { "true" })
  }

  @Test
  fun parseFloatUnit() {
    val parsed = AnimationUnit.FloatUnit(1f).parseUnit { "2" }!!
    Assert.assertEquals(listOf(2f), parsed.components)
  }

  @Test
  fun parseInvalidFloatUnit() {
    Assert.assertNull(AnimationUnit.FloatUnit(1f).parseUnit { "2L" })
    Assert.assertNull(AnimationUnit.FloatUnit(1f).parseUnit { "hello" })
    Assert.assertNull(AnimationUnit.FloatUnit(1f).parseUnit { "true" })
  }

  @Test
  fun testHashCodeAndEquals() {
    // IntUnit
    val intUnit1 = AnimationUnit.IntUnit(42)
    val intUnit2 = AnimationUnit.IntUnit(42)
    val differentIntUnit = AnimationUnit.IntUnit(99)

    Assert.assertEquals(intUnit1.hashCode(), intUnit2.hashCode())
    Assert.assertNotEquals(intUnit1.hashCode(), differentIntUnit.hashCode())
    Assert.assertEquals(intUnit1, intUnit2)
    Assert.assertNotEquals(intUnit1, differentIntUnit)

    // DoubleUnit
    val doubleUnit1 = AnimationUnit.DoubleUnit(3.14)
    val doubleUnit2 = AnimationUnit.DoubleUnit(3.14)
    val differentDoubleUnit = AnimationUnit.DoubleUnit(2.71828)

    Assert.assertEquals(doubleUnit1.hashCode(), doubleUnit2.hashCode())
    Assert.assertNotEquals(doubleUnit1.hashCode(), differentDoubleUnit.hashCode())
    Assert.assertEquals(doubleUnit1, doubleUnit2)
    Assert.assertNotEquals(doubleUnit1, differentDoubleUnit)

    // FloatUnit
    val floatUnit1 = AnimationUnit.FloatUnit(1.23f)
    val floatUnit2 = AnimationUnit.FloatUnit(1.23f)
    val differentFloatUnit = AnimationUnit.FloatUnit(4.56f)

    Assert.assertEquals(floatUnit1.hashCode(), floatUnit2.hashCode())
    Assert.assertNotEquals(floatUnit1.hashCode(), differentFloatUnit.hashCode())
    Assert.assertEquals(floatUnit1, floatUnit2)
    Assert.assertNotEquals(floatUnit1, differentFloatUnit)

    // StringUnit
    val stringUnit1 = AnimationUnit.StringUnit("Hello")
    val stringUnit2 = AnimationUnit.StringUnit("Hello")
    val differentStringUnit = AnimationUnit.StringUnit("World")

    Assert.assertEquals(stringUnit1.hashCode(), stringUnit2.hashCode())
    Assert.assertNotEquals(stringUnit1.hashCode(), differentStringUnit.hashCode())
    Assert.assertEquals(stringUnit1, stringUnit2)
    Assert.assertNotEquals(stringUnit1, differentStringUnit)

    // UnitUnknown
    val unknownUnit1 = AnimationUnit.UnitUnknown("SomeValue")
    val unknownUnit2 = AnimationUnit.UnitUnknown("SomeValue")
    val differentUnknownUnit = AnimationUnit.UnitUnknown(12345)

    Assert.assertEquals(unknownUnit1.hashCode(), unknownUnit2.hashCode())
    Assert.assertNotEquals(unknownUnit1.hashCode(), differentUnknownUnit.hashCode())
    Assert.assertEquals(unknownUnit1, unknownUnit2)
    Assert.assertNotEquals(unknownUnit1, differentUnknownUnit)
  }

  @Test
  fun testHashCodeAndEqualsWithMultipleComponents() {
    class TestUnit(vararg components: Any) :
      AnimationUnit.BaseUnit<Any>(*components) { // Vararg constructor
      override fun parseUnit(getValue: (Int) -> String?) = null // Not needed for testing

      override fun getPickerTitle(): String = "Test Unit"
    }
    val testUnit1 = TestUnit("Hello", 42, true)
    val testUnit2 = TestUnit("Hello", 42, true)
    val differentTestUnit1 = TestUnit("Hello", 42) // Different size
    val differentTestUnit2 = TestUnit("Hello", true, 42) // Different order

    Assert.assertEquals(testUnit1.hashCode(), testUnit2.hashCode())
    Assert.assertNotEquals(testUnit1.hashCode(), differentTestUnit1.hashCode())
    Assert.assertNotEquals(testUnit1.hashCode(), differentTestUnit2.hashCode())
    Assert.assertEquals(testUnit1, testUnit2)
    Assert.assertNotEquals(testUnit1, differentTestUnit1)
    Assert.assertNotEquals(testUnit1, differentTestUnit2)
  }
}
