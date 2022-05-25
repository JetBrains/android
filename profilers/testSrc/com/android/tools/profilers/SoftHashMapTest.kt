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
package com.android.tools.profilers

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SoftHashMapTest {
  @Test
  fun `map gives back what was put in (modulo null)`() {
    val m = SoftHashMap<Int, String>()
    (0 .. 10).forEach { m[it] = it.toString() }
    fun check() = (0 .. 10).forEach { assertThat(m[it]).isAnyOf(it.toString(), null) }
    check()
    repeat(3) { System.gc() }
    check()
  }

  @Test
  fun `soft references are cleared when not enough memory`() {
    val m = SoftHashMap<Int, String>()
    (0 .. 10).forEach { m[it] = it.toString() }
    try {
      tailrec fun allocTillDeath(size: Int) {
        IntArray(size)
        allocTillDeath(size * 2 + 1)
      }
      allocTillDeath(1)
    } catch (_: OutOfMemoryError) {
      assertThat(m.keys).isNotEmpty()
      assertThat(m.values).isEmpty()
    }
  }
}
