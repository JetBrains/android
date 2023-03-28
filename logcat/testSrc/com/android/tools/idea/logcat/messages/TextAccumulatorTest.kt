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

import com.android.tools.idea.logcat.messages.TextAccumulator.Range
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.testFramework.UsefulTestCase.assertThrows
import org.junit.Test
import java.awt.Color

private val blue = TextAttributes().apply { foregroundColor = Color.blue }
private val red = TextAttributes().apply { foregroundColor = Color.red }
private val blueKey = TextAttributesKey.createTextAttributesKey("blue")
private val redKey = TextAttributesKey.createTextAttributesKey("red")

/**
 * Tests for [TextAccumulator]
 */
class TextAccumulatorTest {
  private val textAccumulator = TextAccumulator()

  @Test
  fun accumulate() {
    textAccumulator.accumulate("foo")
    textAccumulator.accumulate("bar")

    assertThat(textAccumulator.text).isEqualTo("foobar")
    assertThat(textAccumulator.textAttributesRanges).isEmpty()
    assertThat(textAccumulator.textAttributesKeyRanges).isEmpty()
  }

  @Test
  fun accumulate_withTextAttributes() {
    textAccumulator.accumulate("foo-")
    textAccumulator.accumulate("blue", textAttributes = blue)
    textAccumulator.accumulate("-bar-")
    textAccumulator.accumulate("red", textAttributes = red)

    assertThat(textAccumulator.text).isEqualTo("foo-blue-bar-red")
    assertThat(textAccumulator.textAttributesRanges).containsExactly(Range(4, 8, blue), Range(13, 16, red))
    assertThat(textAccumulator.textAttributesKeyRanges).isEmpty()
  }

  @Test
  fun accumulate_withTextAttributesKey() {
    textAccumulator.accumulate("foo-")
    textAccumulator.accumulate("blue", textAttributesKey = blueKey)
    textAccumulator.accumulate("-bar-")
    textAccumulator.accumulate("red", textAttributesKey = redKey)

    assertThat(textAccumulator.text).isEqualTo("foo-blue-bar-red")
    assertThat(textAccumulator.textAttributesKeyRanges).containsExactly(Range(4, 8, blueKey), Range(13, 16, redKey))
    assertThat(textAccumulator.textAttributesRanges).isEmpty()
  }

  @Test
  fun accumulate_mixed() {
    textAccumulator.accumulate("foo-")
    textAccumulator.accumulate("blue", textAttributes = blue)
    textAccumulator.accumulate("-bar-")
    textAccumulator.accumulate("red", textAttributesKey = redKey)

    assertThat(textAccumulator.text).isEqualTo("foo-blue-bar-red")
    assertThat(textAccumulator.textAttributesRanges).containsExactly(Range(4, 8, blue))
    assertThat(textAccumulator.textAttributesKeyRanges).containsExactly(Range(13, 16, redKey))
  }

  @Test
  fun accumulate_textAttributesAndKey() {
    assertThrows(AssertionError::class.java) { textAccumulator.accumulate("blue", textAttributes = blue, textAttributesKey = blueKey) }
  }
}
