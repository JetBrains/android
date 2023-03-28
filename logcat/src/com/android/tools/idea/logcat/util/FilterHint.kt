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
package com.android.tools.idea.logcat.util

import com.android.tools.idea.logcat.message.LogLevel

internal sealed class FilterHint {
  /**
   * A [FilterHint] representing a Tag. Note that the length of the hint can be different from the length of the tag. For example, if
   * the tag is elided, the length will be shorter than the actual tag.
   */
  data class Tag(override val text: String, override val length: Int) : FilterHint() {
    override fun getFilter(): String = "tag:$text"
  }

  /**
   * A [FilterHint] representing an AppName. Note that the length of the hint can be different from the length of the name. For example,
   * if the name is elided, the length will be shorter than the actual name.
   */
  data class AppName(override val text: String, override val length: Int) : FilterHint() {
    override fun getFilter(): String = "package:$text"
  }

  /**
   * A [FilterHint] representing a [LogLevel]. The length of this hint is always 3 as in " I ".
   */
  data class Level(val level: LogLevel) : FilterHint() {
    override val text = " ${level.priorityLetter} "
    override val length = 3
    override fun getFilter(): String = "level:${level.name}"
  }

  /**
   * The text behind the range
   */
  abstract val text: String

  /**
   * The length of the range to be created.
   */
  abstract val length: Int

  /**
   * True if the range text is elided.
   */
  fun isElided() : Boolean = text.length > length

  /**
   * A filter this hint represents.
   */
  abstract fun getFilter(): String
}