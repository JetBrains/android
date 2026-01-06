/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.streaming.core

import com.google.common.truth.Truth.assertThat
import java.awt.Dimension
import org.junit.Test

/**
 * Tests for the [computeBestLayout] function defined in `MultiDisplayLayoutOptimizer.kt`.
 */
class MultiDisplayLayoutOptimizerTest {
  @Test
  fun test1Rectangle() {
    val rectangleSizes = listOf(Dimension(200, 400))
    assertThat(computeBestLayout(Dimension(400, 400), rectangleSizes).toString()).isEqualTo("#0")
  }

  @Test
  fun test2Rectangles1() {
    val rectangleSizes = listOf(Dimension(200, 400), Dimension(300, 200))
    assertThat(computeBestLayout(Dimension(400, 400), rectangleSizes).toString()).isEqualTo("[#0] | [#1] 0.40")
    assertThat(computeBestLayout(Dimension(400, 500), rectangleSizes).toString()).isEqualTo("[#0] — [#1] 0.67")
  }

  @Test
  fun test2Rectangles2() {
    val rectangleSizes = listOf(Dimension(500, 1000), Dimension(400, 400))
    assertThat(computeBestLayout(Dimension(500, 500), rectangleSizes).toString()).isEqualTo("[#0] | [#1] 0.50")
  }

  @Test
  fun test3Rectangles1() {
    val rectangleSizes = listOf(Dimension(200, 400), Dimension(300, 200), Dimension(200, 200))
    assertThat(computeBestLayout(Dimension(700, 400), rectangleSizes).toString()).isEqualTo("[[#0] | [#1] 0.40] | [#2] 0.71")
    assertThat(computeBestLayout(Dimension(400, 400), rectangleSizes).toString()).isEqualTo("[#0] | [[#1] — [#2] 0.40] 0.40")
    assertThat(computeBestLayout(Dimension(400, 700), rectangleSizes).toString()).isEqualTo("[[#0] | [#2] 0.50] — [#1] 0.60")
  }

  @Test
  fun test3Rectangles2() {
    val rectangleSizes = listOf(Dimension(1571, 3332), Dimension(720, 1280), Dimension(720, 1280))
    assertThat(computeBestLayout(Dimension(1084, 1862), rectangleSizes).toString()).isEqualTo("[#0] | [[#1] — [#2] 0.50] 0.69")
  }

  @Test
  fun test3Rectangles3() {
    val rectangleSizes = listOf(Dimension(200, 1000), Dimension(300, 800), Dimension(400, 500))
    assertThat(computeBestLayout(Dimension(1100, 1000), rectangleSizes).toString()).isEqualTo("[[#0] | [#1] 0.35] | [#2] 0.52")
  }

  @Test
  fun test4Rectangles() {
    val rectangleSizes = listOf(Dimension(200, 400), Dimension(300, 200), Dimension(400, 500), Dimension(200, 300))
    assertThat(computeBestLayout(Dimension(1100, 500), rectangleSizes).toString()).isEqualTo("[[[#0] | [#1] 0.40] | [#2] 0.56] | [#3] 0.82")
    assertThat(computeBestLayout(Dimension(900, 500), rectangleSizes).toString()).isEqualTo("[[[#0] | [#3] 0.43] — [#1] 0.67] | [#2] 0.45")
    assertThat(computeBestLayout(Dimension(500, 500), rectangleSizes).toString()).isEqualTo("[[#0] — [#3] 0.57] | [[#1] — [#2] 0.29] 0.33")
    assertThat(computeBestLayout(Dimension(500, 900), rectangleSizes).toString()).isEqualTo("[[#0] | [[#1] — [#3] 0.40] 0.45] — [#2] 0.50")
    assertThat(computeBestLayout(Dimension(500, 1100), rectangleSizes).toString()).isEqualTo("[[[#0] — [#1] 0.67] | [#3] 0.60] — [#2] 0.55")
    assertThat(computeBestLayout(Dimension(500, 1400), rectangleSizes).toString()).isEqualTo("[[[#0] — [#1] 0.67] — [#2] 0.55] — [#3] 0.79")
  }

  @Test
  fun test5Rectangles() {
    val rectangleSizes = listOf(Dimension(200, 400), Dimension(300, 200), Dimension(400, 500), Dimension(200, 300), Dimension(100, 100))
    assertThat(computeBestLayout(Dimension(1200, 500), rectangleSizes).toString())
        .isEqualTo("[[[[#0] | [#1] 0.40] | [#2] 0.56] | [#3] 0.82] | [#4] 0.92")
    assertThat(computeBestLayout(Dimension(900, 500), rectangleSizes).toString())
        .isEqualTo("[[[#0] | [[#1] — [#3] 0.40] 0.46] — [#4] 0.83] | [#2] 0.51")
    assertThat(computeBestLayout(Dimension(400, 500), rectangleSizes).toString())
        .isEqualTo("[[#0] — [#3] 0.57] | [[[#1] | [#4] 0.75] — [#2] 0.29] 0.33")
    assertThat(computeBestLayout(Dimension(400, 700), rectangleSizes).toString())
        .isEqualTo("[[[#0] — [#4] 0.80] | [[#1] — [#3] 0.40] 0.40] — [#2] 0.50")
    assertThat(computeBestLayout(Dimension(400, 1100), rectangleSizes).toString())
        .isEqualTo("[[[[#0] | [#3] 0.50] — [#1] 0.60] | [#4] 0.80] — [#2] 0.49")
    assertThat(computeBestLayout(Dimension(400, 1500), rectangleSizes).toString())
        .isEqualTo("[[[[#0] — [#1] 0.67] — [#2] 0.55] — [#3] 0.79] — [#4] 0.93")
  }
}