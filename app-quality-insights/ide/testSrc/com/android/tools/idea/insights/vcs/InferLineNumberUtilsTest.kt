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
package com.android.tools.idea.insights.vcs

import com.android.tools.idea.insights.inspection.inferLineNumber
import com.google.common.truth.Truth.assertThat
import com.intellij.diff.util.Range
import org.junit.Test

class InferLineNumberUtilsTest {
  @Test
  fun `check no changes`() {
    val ranges = emptyList<Range>()
    assertThat(ranges.inferLineNumber(0)).isEqualTo(0)
    assertThat(ranges.inferLineNumber(100)).isEqualTo(100)
  }

  @Test
  fun `check inferred line number case 1`() {
    val ranges = listOf(Range(2, 5, 2, 4))

    // 0, 0
    // 1, 1
    // 2, 2*
    // 3, 3*
    // 4, -
    // 5, 4
    // 6, 5
    assertThat(ranges.inferLineNumber(0)).isEqualTo(0)
    assertThat(ranges.inferLineNumber(1)).isEqualTo(1)
    assertThat(ranges.inferLineNumber(2)).isEqualTo(null)
    assertThat(ranges.inferLineNumber(3)).isEqualTo(null)
    assertThat(ranges.inferLineNumber(4)).isEqualTo(null)
    assertThat(ranges.inferLineNumber(5)).isEqualTo(4)
    assertThat(ranges.inferLineNumber(6)).isEqualTo(5)
  }

  @Test
  fun `check inferred line number case 2`() {
    val ranges = listOf(Range(2, 4, 2, 5))

    // 0, 0
    // 1, 1
    // 2, 2*
    // 3, 3*
    // -, 4
    // 4, 5
    // 5, 6
    // 6, 7
    assertThat(ranges.inferLineNumber(0)).isEqualTo(0)
    assertThat(ranges.inferLineNumber(1)).isEqualTo(1)
    assertThat(ranges.inferLineNumber(2)).isEqualTo(null)
    assertThat(ranges.inferLineNumber(3)).isEqualTo(null)
    assertThat(ranges.inferLineNumber(4)).isEqualTo(5)
    assertThat(ranges.inferLineNumber(5)).isEqualTo(6)
    assertThat(ranges.inferLineNumber(6)).isEqualTo(7)
  }

  @Test
  fun `check inferred line number case 3`() {
    val ranges = listOf(Range(2, 4, 2, 5), Range(10, 11, 11, 15))

    // 0, 0
    // 1, 1
    // 2, 2*
    // 3, 3*
    // -, 4
    // 4, 5
    // ...
    // 8, 9
    // 9, 10
    // 10, -
    // -, 11
    // -, 12
    // -, 13
    // -, 14
    // 11, 15
    // 12, 16
    // 13, 17
    // 14, 18
    assertThat(ranges.inferLineNumber(0)).isEqualTo(0)
    assertThat(ranges.inferLineNumber(9)).isEqualTo(10)
    assertThat(ranges.inferLineNumber(10)).isEqualTo(null)
    assertThat(ranges.inferLineNumber(11)).isEqualTo(15)
    assertThat(ranges.inferLineNumber(12)).isEqualTo(16)
    assertThat(ranges.inferLineNumber(13)).isEqualTo(17)
  }
}
