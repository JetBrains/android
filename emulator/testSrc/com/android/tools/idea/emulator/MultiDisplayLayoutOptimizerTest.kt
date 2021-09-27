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
package com.android.tools.idea.emulator

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.awt.Dimension

/**
 * Tests for the [computeBestLayout] function defined in `MultiDisplayLayoutOptimizer.kt`.
 */
class MultiDisplayLayoutOptimizerTest {
  @Test
  fun test1Rectangle() {
    val rectangleSizes = listOf(Dimension(2, 4))
    assertThat(computeBestLayout(Dimension(4, 4), rectangleSizes).toString()).isEqualTo("#0")
  }

  @Test
  fun test2Rectangles() {
    val rectangleSizes = listOf(Dimension(2, 4), Dimension(3, 2))
    assertThat(computeBestLayout(Dimension(4, 4), rectangleSizes).toString()).isEqualTo("[#0] | [#1] 0.40")
    assertThat(computeBestLayout(Dimension(4, 5), rectangleSizes).toString()).isEqualTo("[#0] — [#1] 0.67")
  }

  @Test
  fun test3Rectangles1() {
    val rectangleSizes = listOf(Dimension(2, 4), Dimension(3, 2), Dimension(2, 2))
    assertThat(computeBestLayout(Dimension(7, 4), rectangleSizes).toString()).isEqualTo("[[#0] | [#1] 0.40] | [#2] 0.71")
    assertThat(computeBestLayout(Dimension(4, 4), rectangleSizes).toString()).isEqualTo("[#0] | [[#1] — [#2] 0.50] 0.40")
    assertThat(computeBestLayout(Dimension(4, 7), rectangleSizes).toString()).isEqualTo("[[#0] | [#2] 0.50] — [#1] 0.67")
  }

  @Test
  fun test3Rectangles2() {
    val rectangleSizes = listOf(Dimension(1571, 3332), Dimension(720, 1280), Dimension(720, 1280))
    assertThat(computeBestLayout(Dimension(1084, 1862), rectangleSizes).toString()).isEqualTo("[#0] | [[#1] — [#2] 0.50] 0.69")
  }

  @Test
  fun test4Rectangles() {
    val rectangleSizes = listOf(Dimension(2, 4), Dimension(3, 2), Dimension(4, 5), Dimension(2, 3))
    assertThat(computeBestLayout(Dimension(11, 5), rectangleSizes).toString()).isEqualTo("[[[#0] | [#1] 0.40] | [#2] 0.56] | [#3] 0.82")
    assertThat(computeBestLayout(Dimension(9, 5), rectangleSizes).toString()).isEqualTo("[[[#0] | [#3] 0.50] — [#1] 0.67] | [#2] 0.50")
    assertThat(computeBestLayout(Dimension(5, 5), rectangleSizes).toString()).isEqualTo("[[#0] — [#3] 0.57] | [[#1] — [#2] 0.29] 0.33")
    assertThat(computeBestLayout(Dimension(5, 9), rectangleSizes).toString()).isEqualTo("[[#0] | [[#1] — [#3] 0.40] 0.40] — [#2] 0.50")
    assertThat(computeBestLayout(Dimension(5, 11), rectangleSizes).toString()).isEqualTo("[[[#0] — [#1] 0.67] | [#3] 0.60] — [#2] 0.55")
    assertThat(computeBestLayout(Dimension(5, 14), rectangleSizes).toString()).isEqualTo("[[[#0] — [#1] 0.67] — [#2] 0.55] — [#3] 0.79")
  }

  @Test
  fun test5Rectangles() {
    val rectangleSizes = listOf(Dimension(2, 4), Dimension(3, 2), Dimension(4, 5), Dimension(2, 3), Dimension(1, 1))
    assertThat(computeBestLayout(Dimension(12, 5), rectangleSizes).toString())
        .isEqualTo("[[[[#0] | [#1] 0.40] | [#2] 0.56] | [#3] 0.82] | [#4] 0.92")
    assertThat(computeBestLayout(Dimension(9, 5), rectangleSizes).toString())
        .isEqualTo("[[[#0] | [[#1] — [#3] 0.40] 0.40] — [#4] 0.83] | [#2] 0.56")
    assertThat(computeBestLayout(Dimension(4, 5), rectangleSizes).toString())
        .isEqualTo("[[#0] — [#3] 0.57] | [[[#1] | [#4] 0.75] — [#2] 0.29] 0.33")
    assertThat(computeBestLayout(Dimension(4, 7), rectangleSizes).toString())
        .isEqualTo("[[[#0] — [#4] 0.80] | [[#1] — [#3] 0.40] 0.40] — [#2] 0.50")
    assertThat(computeBestLayout(Dimension(4, 11), rectangleSizes).toString())
        .isEqualTo("[[[[#0] | [#3] 0.50] — [#1] 0.67] | [#4] 0.80] — [#2] 0.55")
    assertThat(computeBestLayout(Dimension(4, 15), rectangleSizes).toString())
        .isEqualTo("[[[[#0] — [#1] 0.67] — [#2] 0.55] — [#3] 0.79] — [#4] 0.93")
  }
}