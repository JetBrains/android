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
package com.android.tools.idea.stats

import com.google.common.collect.Range
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.awt.image.BufferedImage

class MemoryProbeTest {
  @Test
  fun testString() {
    assertThat(check("Here is a string with text")).isEqualTo(40L)
  }

  @Test
  fun testByteArray() {
    assertThat(check(ByteArray(80))).isEqualTo(104)
    assertThat(check(ByteArray(7))).isEqualTo(32)
    assertThat(check(ByteArray(8))).isEqualTo(32)
    assertThat(check(ByteArray(9))).isEqualTo(40)
  }

  @Test
  fun testIntArray() {
    assertThat(check(IntArray(700))).isEqualTo(2824)
  }

  @Test
  fun testBufferedImage() {
    @Suppress("UndesirableClassUsage")
    val image = BufferedImage(800, 600, BufferedImage.TYPE_INT_ARGB)
    assertThat(check(image)).isEqualTo(896L)
  }

  @Test
  fun testCountObjectsOnlyOnce() {
    val first = Chain(1)
    val second = Chain(2)
    val third = Chain(3)
    first.next = second
    first.prev = third
    second.next = third
    second.prev = first
    third.next = first
    third.prev = second
    assertThat(check(first)).isIn(Range.closed(160L, 196L)) // Actual number can vary with JRE
  }

  private fun check(value: Any): Long {
    val includedPackagePrefixes = listOf(
      "sun.awt.image.",
      Chain::class.java.`package`.name + ".",
      java.awt.Image::class.java.`package`.name + "."
    )
    val checker = MemoryProbe(includedPackagePrefixes, excludeStaticFields = true)
    return checker.check(value)
  }

  @Suppress("unused")
  private class Chain(val number: Int) {
    var next: Chain? = null
    var prev: Chain? = null
  }
}
