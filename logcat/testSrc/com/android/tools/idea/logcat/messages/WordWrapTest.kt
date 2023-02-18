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
package com.android.tools.idea.logcat.messages

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tests for WordWrap
 */
class WordWrapTest {
  @Test
  fun wordWrap_shortText() {
    val text = "A short line"

    assertThat(wordWrap(text, 50)).isEqualTo(text)
  }

  @Test
  fun wordWrap_shortTextThatEndsWithNewline() {
    val text = "A short line\n"

    assertThat(wordWrap(text, 50)).isEqualTo(text)
  }

  @Test
  fun wordWrap_shortLines() {
    val text = """
      A few short lines 1
      A few short lines 2
      A few short lines 3
      A few short lines 4
    """.trimIndent()

    assertThat(wordWrap(text, 30)).isEqualTo(text)
  }

  @Test
  fun wordWrap_shortLinesThatEndWithNewline() {
    val text = """
      A few short lines 1
      A few short lines 2
      A few short lines 3
      A few short lines 4
      
    """.trimIndent()

    assertThat(wordWrap(text, 30)).isEqualTo(text)
  }

  @Test
  fun wordWrap_longLine() {
    val text = "A long line that we can split"

    assertThat(wordWrap(text, 15)).isEqualTo("""
      A long line
      that we can
      split
    """.trimIndent())
  }

  @Test
  fun wordWrap_longLineThatEndsWithNewline() {
    val text = "A long line that we can split\n"

    assertThat(wordWrap(text, 15)).isEqualTo("""
      A long line
      that we can
      split
      
    """.trimIndent())
  }

  @Test
  fun wordWrap_longLines() {
    val text = """
      A long line that we can split
      Short one
      Another long line that we can split
    """.trimIndent()

    assertThat(wordWrap(text, 15)).isEqualTo("""
      A long line
      that we can
      split
      Short one
      Another long
      line that we
      can split
    """.trimIndent())
  }

  @Test
  fun wordWrap_longLineWithLongWhitespace() {
    val text = "A   long  line    that    we   can    split"

    assertThat(wordWrap(text, 15)).isEqualTo("""
    A   long  line
    that    we
    can    split
    """.trimIndent())
  }

  /**
   * Note that while we support tabs as a word delimiter, we make no attempt to correctly word wrap based on the actual rendered tab width.
   *
   * So, for example, the text `1\t2` renders over 9 column, but we consider it to be 3 columns only.
   *
   * This is an edge case that's not worth the effort of getting it exactly right.
   */
  @Test
  fun wordWrap_tabs() {
    val text = "A\tline\twith\ttabs"

    assertThat(wordWrap(text, 11)).isEqualTo("""
      A	line	with
      tabs
    """.trimIndent())
  }
}