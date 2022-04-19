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

class CachedDerivedPropertyTest {

  @Test
  fun `derived property only changes when source changes`() {
    var recompCount = 0
    val obj = object {
      var name: String = ""
      val nameLength: Int by CachedDerivedProperty({name}, { str -> str.length.also { recompCount++ } })
    }

    assertThat(obj.nameLength).isEqualTo(0)
    assertThat(recompCount).isEqualTo(1)

    obj.name = "foo"
    assertThat(obj.nameLength).isEqualTo(3)
    assertThat(recompCount).isEqualTo(2)

    obj.name = "foo"
    assertThat(obj.nameLength).isEqualTo(3)
    assertThat(recompCount).isEqualTo(2)

    obj.name = "bs"
    assertThat(obj.nameLength).isEqualTo(2)
    assertThat(recompCount).isEqualTo(3)
  }

  @Test
  fun `last result used for incremental computation`() {
    var log = listOf<Int>()
    fun sumFromTo(from: Int, to: Int) = (from..to).sum().also {
      log = (from..to).toList()
    }

    val obj = object {
      var bound: Int = 0
      val sumUpToBound: Int by CachedDerivedProperty({bound}, { bound, cache -> when (cache) {
        null -> sumFromTo(1, bound)
        else -> {
          val (oldBound, oldSum) = cache
          when {
            oldBound < bound -> oldSum + sumFromTo(oldBound + 1, bound)
            else -> oldSum - sumFromTo(bound + 1, oldBound)
          }
        }
      } })
    }

    assertThat(obj.sumUpToBound).isEqualTo(0)

    obj.bound = 10
    assertThat(obj.sumUpToBound).isEqualTo((1 .. 10).sum())
    assertThat(log).isEqualTo((1 .. 10).toList())

    obj.bound = 11
    assertThat(obj.sumUpToBound).isEqualTo((1 .. 11).sum())
    assertThat(log).isEqualTo(listOf(11))
  }
}