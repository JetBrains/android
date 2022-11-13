/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.streaming.emulator

import com.android.tools.idea.streaming.scaledUnbiased
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.fail
import org.junit.Test

/**
 * Tests for functions defined in EmulatorUtils.kt.
 */
class EmulatorUtilsTest {

  @Test
  fun testIntScaledUnbiased() {
    assertThat(0.scaledUnbiased(2, 6)).isEqualTo(1)
    assertThat(1.scaledUnbiased(2, 6)).isEqualTo(4)
    assertThat(0.scaledUnbiased(3, 9)).isEqualTo(1)
    assertThat(1.scaledUnbiased(3, 9)).isEqualTo(4)
    assertThat(2.scaledUnbiased(3, 9)).isEqualTo(7)

    for (i in 3..512) {
      for (j in 2..i) {
        for (k in 0 until j) {
          val s = k.scaledUnbiased(j, i)
          // Check trivial conversion.
          if (j == i && s != k) {
            fail("$k.scaledUnbiased($j, $i) = $s, it is not equal $k")
          }
          // Check range.
          if (s !in 0 until i) {
            fail("$k.scaledUnbiased($j, $i) = $s, it doesn't belong to the [0, ${i - 1}] interval")
          }
          // Check reverse conversion.
          val r = s.scaledUnbiased(i, j)
          if (r != k) {
            fail("$k.scaledUnbiased($j, $i) = $s but $s.scaledUnbiased($i, $j) = $r, it is not equal $k")
          }
        }
      }
    }
  }
}