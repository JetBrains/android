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
 * Test for the [computeBestLayout] function defined in `MultiDisplayLayoutOptimizer.kt`.
 */
class MultiDisplayLayoutOptimizerTest {
  @Test
  fun test2Rectangles() {
    val rectangleSizes = listOf(Dimension(2, 4), Dimension(3, 2))
    assertThat(computeBestLayout(Dimension(4, 4), rectangleSizes).toString()).isEqualTo("[#0] | [#1] 0.40")
    assertThat(computeBestLayout(Dimension(4, 5), rectangleSizes).toString()).isEqualTo("[#0] — [#1] 0.67")
  }

  @Test
  fun test3Rectangles() {
    val rectangleSizes = listOf(Dimension(2, 4), Dimension(3, 2), Dimension(2, 2))
    assertThat(computeBestLayout(Dimension(7, 4), rectangleSizes).toString()).isEqualTo("[[#0] | [#1] 0.40] | [#2] 0.71")
    assertThat(computeBestLayout(Dimension(4, 4), rectangleSizes).toString()).isEqualTo("[[#0] — [#1] 0.67] | [#2] 0.60")
    assertThat(computeBestLayout(Dimension(4, 7), rectangleSizes).toString()).isEqualTo("[[#0] — [#1] 0.67] — [#2] 0.75")
  }

  @Test
  fun test4Rectangles() {
    val rectangleSizes = listOf(Dimension(2, 4), Dimension(3, 2), Dimension(4, 5), Dimension(2, 3))
    assertThat(computeBestLayout(Dimension(11, 5), rectangleSizes).toString()).isEqualTo("[[[#0] | [#1] 0.40] | [#2] 0.56] | [#3] 0.82")
    assertThat(computeBestLayout(Dimension(9, 5), rectangleSizes).toString()).isEqualTo("[[[#0] | [#1] 0.40] — [#1] 0.67] | [#3] 0.71")
    assertThat(computeBestLayout(Dimension(5, 5), rectangleSizes).toString()).isEqualTo("[[#0] — [#1] 0.67] | [[#0] — [#1] 0.67] 0.50")
    assertThat(computeBestLayout(Dimension(5, 7), rectangleSizes).toString()).isEqualTo("[[[#0] — [#1] 0.67] | [#3] 0.60] — [#1] 0.75")
    assertThat(computeBestLayout(Dimension(5, 11), rectangleSizes).toString()).isEqualTo("[[[#0] — [#1] 0.67] | [#3] 0.60] — [#2] 0.55")
    assertThat(computeBestLayout(Dimension(5, 14), rectangleSizes).toString()).isEqualTo("[[[#0] — [#1] 0.67] — [#2] 0.55] — [#3] 0.79")
  }
}