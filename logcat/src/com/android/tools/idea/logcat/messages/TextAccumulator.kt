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

import com.android.ddmlib.Log.LogLevel
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
  val filterHintRanges = mutableListOf<Range<FilterHint>>()

  fun accumulate(
    text: String,
    textAttributes: TextAttributes? = null,
    textAttributesKey: TextAttributesKey? = null,
    filterHint: FilterHint? = null): TextAccumulator {
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
    if (filterHint != null) {
      filterHintRanges.add(Range(start, start + filterHint.length, filterHint))
    }
    return this
  }

  internal data class Range<T>(val start: Int, val end: Int, val data: T)

  internal sealed class FilterHint {
    /**
     * A [FilterHint] representing a Tag. Note that the length of the hint can be different from the length of the tag. For example, if
     * the tag is elided, the length will be shorter than the actual tag.
     */
    data class Tag(val tag: String, override val length: Int) : FilterHint() {
      override fun getFilter(): String = "tag:$tag"
    }

    /**
     * A [FilterHint] representing an AppName. Note that the length of the hint can be different from the length of the name. For example,
     * if the name is elided, the length will be shorter than the actual name.
     */
    data class AppName(val appName: String, override val length: Int) : FilterHint() {
      override fun getFilter(): String = "package:$appName"
    }

    /**
     * A [FilterHint] representing a [LogLevel]. The length of this hint is always 3 as in " I ".
     */
    data class Level(val level: LogLevel) : FilterHint() {
      override val length = 3
      override fun getFilter(): String = "level:${level.name}"
    }

    /**
     * The length of the range to be created.
     */
    abstract val length: Int

    /**
     * A filter this hint represents.
     */
    abstract fun getFilter(): String
  }
}
