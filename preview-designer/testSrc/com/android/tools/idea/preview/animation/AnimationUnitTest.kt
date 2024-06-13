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
}
