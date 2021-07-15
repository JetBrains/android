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
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ComposeUnitTest {

  @Test
  fun parseInt() {
    val composeUnit = ComposeUnit.parse(ComposeAnimatedProperty("", 1))
    assertNotNull(composeUnit)
    composeUnit as ComposeUnit.Unit1D
    assertEquals(1, composeUnit.component1);
    assertEquals(listOf(1), composeUnit.components);
    assertEquals("1", composeUnit.toString(0))
    assertFalse { composeUnit is ComposeUnit.Unit2D<*> }
  }

  @Test
  fun parseDouble() {
    val composeUnit = ComposeUnit.parse(ComposeAnimatedProperty("", 1.2345))
    assertNotNull(composeUnit)
    composeUnit as ComposeUnit.Unit1D
    assertEquals(1.2345, composeUnit.component1);
    assertEquals(listOf(1.2345), composeUnit.components);
    assertEquals("1.2345", composeUnit.toString(0))
    assertFalse { composeUnit is ComposeUnit.Unit2D<*> }
  }

  @Test
  fun parseFloat() {
    val composeUnit = ComposeUnit.parse(ComposeAnimatedProperty("", 1.2345f))
    assertNotNull(composeUnit)
    composeUnit as ComposeUnit.Unit1D
    assertEquals(1.2345f, composeUnit.component1);
    assertEquals(listOf(1.2345f), composeUnit.components);
    assertEquals("1.2345", composeUnit.toString(0))
    assertFalse { composeUnit is ComposeUnit.Unit2D<*> }
  }

  @Test
  fun parseDp() {
    @Suppress("unused") // Used by tests via reflection
    class Dp {
      fun getValue() = 1.2345f
    }

    val composeUnit = ComposeUnit.Dp.create(Dp())
    assertNotNull(composeUnit)
    assertEquals(1.2345f, composeUnit.component1);
    assertEquals("1.2345dp", composeUnit.toString(0))
    assertEquals(listOf(1.2345f), composeUnit.components);
  }

  @Test
  fun parseInvalidDp() {
    @Suppress("unused") // Used by tests via reflection
    class Dp {
      fun getValue() = 1 //Not a float
    }

    val composeUnit = ComposeUnit.Dp.create(Dp())
    assertNull(composeUnit)
  }

  @Test
  fun parseRect() {
    @Suppress("unused") // Used by tests via reflection
    class Rect {
      fun getLeft() = 1.222f
      fun getTop() = 2.222f
      fun getRight() = 3.222f
      fun getBottom() = 4.222f
    }

    val composeUnit = ComposeUnit.Rect.create(Rect())
    assertNotNull(composeUnit)
    assertEquals(1.222f, composeUnit.component1);
    assertEquals(2.222f, composeUnit.component2);
    assertEquals(3.222f, composeUnit.component3);
    assertEquals(4.222f, composeUnit.component4);
    assertEquals("( 1.222 , _ , _ , _ )", composeUnit.toString(0))
    assertEquals("( _ , 2.222 , _ , _ )", composeUnit.toString(1))
    assertEquals("( _ , _ , 3.222 , _ )", composeUnit.toString(2))
    assertEquals("( _ , _ , _ , 4.222 )", composeUnit.toString(3))
    assertEquals(listOf(1.222f, 2.222f, 3.222f, 4.222f), composeUnit.components);
  }

  @Test
  fun parseInvalidRect() {
    @Suppress("unused") // Used by tests via reflection
    class Rect {
      // Not float values
      fun getLeft() = 1
      fun getTop() = 2
      fun getRight() = 3
      fun getBottom() = 4
    }

    val composeUnit = ComposeUnit.Rect.create(Rect())
    assertNull(composeUnit)
  }

  @Test
  fun parseInvalid() {
    val composeUnit = ComposeUnit.parse(ComposeAnimatedProperty("", "Invalid"))
    assertNull(composeUnit)
  }
}