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

import com.android.tools.idea.logcat.message.LogcatMessage
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.markup.TextAttributes

/**
 * Accumulates fragments of text into a text buffer and a list of colored ranges.
 */
internal class TextAccumulator {
  private val stringBuilder = StringBuilder()

  val text: String get() = stringBuilder.toString()

  val textAttributesRanges = mutableListOf<Range<TextAttributes>>()
  val textAttributesKeyRanges = mutableListOf<Range<TextAttributesKey>>()
  val messageRanges = mutableListOf<Range<LogcatMessage>>()

  fun accumulate(
    text: String,
    textAttributes: TextAttributes? = null,
    textAttributesKey: TextAttributesKey? = null): TextAccumulator {
    assert(textAttributes == null || textAttributesKey == null) { "Only one of textAttributesKey and textAttributesKeyKey can be set" }
    val start = stringBuilder.length
    val end = start + text.length
    stringBuilder.append(text)
    if (textAttributes != null) {
      textAttributesRanges.add(Range(start, end, textAttributes))
    }
    else if (textAttributesKey != null) {
      textAttributesKeyRanges.add(Range(start, end, textAttributesKey))
    }
    return this
  }

  fun getTextLength() = stringBuilder.length

  fun addMessageRange(start: Int, end: Int, message: LogcatMessage) {
    messageRanges.add(Range(start, end, message))
  }

  internal data class Range<T>(val start: Int, val end: Int, val data: T)

}
