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

import com.intellij.openapi.editor.markup.TextAttributes

/**
 * Accumulates fragments of text into a text buffer and a list of colored ranges.
 */
internal class TextAccumulator {
  private val stringBuilder = StringBuilder()

  val text: String get() = stringBuilder.toString()

  val ranges = mutableListOf<HighlighterRange>()

  fun accumulate(text: String, textAttributes: TextAttributes? = null) {
    val start = stringBuilder.length
    stringBuilder.append(text)
    textAttributes?.let { ranges.add(HighlighterRange(start, start + text.length, it)) }
  }
}

/**
 * Defines a range of text with a specified color.
 */
internal data class HighlighterRange(val start: Int, val end: Int, val textAttributes: TextAttributes)
