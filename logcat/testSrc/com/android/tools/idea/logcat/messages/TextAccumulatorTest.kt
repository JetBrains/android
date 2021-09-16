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
package com.android.tools.idea.logcat.messages

import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.editor.markup.TextAttributes
import org.junit.Test
import java.awt.Color

private val blue = TextAttributes().apply { foregroundColor = Color.blue }
private val red = TextAttributes().apply { foregroundColor = Color.red }

/**
 * Tests for [TextAccumulator]
 */
class TextAccumulatorTest {
  @Test
  fun accumulate_noColor() {
    val buffer = TextAccumulator()

    buffer.accumulate("foo")
    buffer.accumulate("bar")

    assertThat(buffer.text).isEqualTo("foobar")
    assertThat(buffer.ranges).isEmpty()
  }

  @Test
  fun accumulate_withColor() {
    val buffer = TextAccumulator()

    buffer.accumulate("foo-")
    buffer.accumulate("blue", blue)
    buffer.accumulate("-bar-")
    buffer.accumulate("red", red)

    assertThat(buffer.text).isEqualTo("foo-blue-bar-red")
    assertThat(buffer.ranges).containsExactly(HighlighterRange(4, 8, blue), HighlighterRange(13, 16, red))
  }

  @Test
  fun clear() {
    val buffer = TextAccumulator()

    buffer.accumulate("foo", blue)
    buffer.clear()
    buffer.accumulate("bar", red)

    assertThat(buffer.text).isEqualTo("bar")
    assertThat(buffer.ranges).containsExactly(HighlighterRange(0, 3, red))
  }

}