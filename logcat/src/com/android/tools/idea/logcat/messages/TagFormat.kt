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

import com.intellij.openapi.util.text.StringUtil

private const val DEFAULT_LENGTH = 23
private const val MIN_LENGTH = 10
private const val ELLIPSIS_LEN = 3

/** Provides formatting for the log tag. */
internal data class TagFormat(
  val maxLength: Int = DEFAULT_LENGTH,
  val hideDuplicates: Boolean = false,
  val enabled: Boolean = true,
  val colorize: Boolean = true,
) {
  init {
    assert(maxLength >= MIN_LENGTH)
  }

  fun format(tag: String, previousTag: String?): String {
    if (!enabled) {
      return ""
    }
    if (hideDuplicates && tag == previousTag) {
      return "".padEnd(maxLength + 1)
    }
    if (tag == "") {
      return "<no-tag>".padEnd(maxLength + 1)
    }
    if (tag.length > maxLength) {
      return StringUtil.shortenTextWithEllipsis(tag, maxLength, (maxLength - ELLIPSIS_LEN) / 2) +
        " "
    }
    return tag.padEnd(maxLength + 1)
  }

  fun width() = if (enabled) maxLength + 1 else 0
}
