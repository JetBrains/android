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
package com.android.tools.idea.editors.strings.table

import com.google.common.truth.Truth.assertThat
import org.junit.Test

private const val tab = '\t'

/** Tests for functions defined in `GridPasteUtils.kt`. */
class GridPasteUtilsTest {

  @Test
  fun testSimple() {
    val str = """
      A1${tab}B1
      A2${tab}B2
    """.trimIndent()
    assertThat(str.splitIntoGrid()).containsExactly(
      listOf("A1", "B1"),
      listOf("A2", "B2"))
  }

  @Test
  fun testLowerTriangle() {
    val str = """
      A1
      A2${tab}B2
      A3${tab}B3${tab}C3
    """.trimIndent()
    assertThat(str.splitIntoGrid()).containsExactly(
      listOf("A1", "", ""),
      listOf("A2", "B2", ""),
      listOf("A3", "B3", "C3"))
  }

  @Test
  fun testUpperTriangle() {
    val str = """
      A1${tab}B1${tab}C1
      A2${tab}B2
      A3
    """.trimIndent()
    assertThat(str.splitIntoGrid()).containsExactly(
      listOf("A1", "B1", "C1"),
      listOf("A2", "B2", ""),
      listOf("A3", "", ""))
  }

  @Test
  fun testQuotedStrings() {
    val str = """
      "A1"${tab}"B1 first
      B1 second"
      "A${tab}2"${tab}" ""B2"" "
      A3${tab}"B""3
    """.trimIndent()
    assertThat(str.splitIntoGrid()).containsExactly(
      listOf("A1", "B1 first\nB1 second"),
      listOf("A${tab}2", " \"B2\" "),
      listOf("A3", "\"B\"\"3"))
  }

  @Test
  fun testQuotedStringsWithExceptions() {
    val str = """
      "A1 ""first""
      A1 second"${tab}"B1${tab}C1
      A2"${tab}"B2${tab}x"${tab}C2
      A3${tab}B3${tab}C3
    """.trimIndent()
    assertThat(str.splitIntoGrid()).containsExactly(
      listOf("A1 \"first\"\nA1 second", "\"B1", "C1"),
      listOf("A2\"", "B2\tx", "C2"),
      listOf("A3", "B3", "C3"))
  }

  @Test
  fun testQuotedStringsWithExceptionsLarge() {
    val str = """
      "A1 ""first""
      A1 second"${tab}"B1${tab}C1
      A2"${tab}"B2${tab}x"${tab}C2
      A3${tab}B3${tab}C3
      "A4 ""first""
      A4 second"${tab}"B4${tab}C4
      A5"${tab}"B5${tab}x"${tab}C5
      A6${tab}B6${tab}C6
      "A7 ""first""
      A7 second"${tab}"B7${tab}C7
      A8"${tab}"B8${tab}x"${tab}C8
      A9
    """.trimIndent()
    assertThat(str.splitIntoGrid()).containsExactly(
      listOf("A1 \"first\"\nA1 second", "\"B1", "C1"),
      listOf("A2\"", "B2\tx", "C2"),
      listOf("A3", "B3", "C3"),
      listOf("A4 \"first\"\nA4 second", "\"B4", "C4"),
      listOf("A5\"", "B5\tx", "C5"),
      listOf("A6", "B6", "C6"),
      listOf("A7 \"first\"\nA7 second", "\"B7", "C7"),
      listOf("A8\"", "B8\tx", "C8"),
      listOf("A9", "", ""))
  }
}
