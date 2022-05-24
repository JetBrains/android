/*
 * Copyright (C) 2020 The Android Open Source Project
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
import kotlin.math.sin

class CachedFunctionTest {

  @Test
  fun `cached function computes same as uncached`() {
    val countedSin = CountedFunction(::sin)
    val cachedSin = CachedFunction(countedSin)
    val x = Math.random()
    repeat(5) {
      assertThat(cachedSin(x)).isEqualTo(sin(x))
    }
    assertThat(countedSin.invocationCount).isEqualTo(1)
  }

  @Test
  fun `cached function recomputes when invalidated`() {
    val countedSin = CountedFunction(::sin)
    val cachedSin = CachedFunction(countedSin)
    val x = Math.random()
    val tries = 5
    repeat(tries) {
      assertThat(cachedSin(x)).isEqualTo(sin(x))
      cachedSin.invalidate()
    }
    assertThat(countedSin.invocationCount).isEqualTo(tries)
  }

  @Test
  fun `LRU cache remembers recent items and stays within cap`() {
    val countedInc = CountedFunction(Int::inc)
    val cachedInc = CachedFunction(CappedLRUMap(3), countedInc)
    for (i in 1..10) {
      assertThat(cachedInc(i)).isEqualTo(i.inc())
    }
    assertThat(countedInc.invocationCount).isEqualTo(10)
    repeat(10) {
      cachedInc(8)
      cachedInc(9)
      cachedInc(10)
    }
    assertThat(countedInc.invocationCount).isEqualTo(10)
    cachedInc(7)
    assertThat(countedInc.invocationCount).isEqualTo(11)
  }

  private class CountedFunction<X, Y>(private val f: (X) -> Y) : (X) -> Y {
    var invocationCount = 0
      private set

    override fun invoke(x: X): Y = f(x).also { invocationCount++ }
  }
}