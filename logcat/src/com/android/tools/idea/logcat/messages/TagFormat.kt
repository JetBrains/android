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

import com.google.gson.annotations.Expose
import com.intellij.openapi.util.text.StringUtil
import kotlin.math.max

private const val DEFAULT_LENGTH = 23
private const val MIN_LENGTH = 10
private const val PREFIX_LEN = 6
/**
 * Provides formatting for the log tag.
 */
internal class TagFormat(maxLength: Int = DEFAULT_LENGTH, val hideDuplicates: Boolean = false, val enabled: Boolean = true) {
  @Transient // Exclude from serialization
  private val noTag = "<no-tag>".padEnd(maxLength + 1)
  @Transient // Exclude from serialization
  private val dupTag = "".padEnd(maxLength + 1)

  private val maxLength = max(MIN_LENGTH, maxLength)

  fun format(tag: String, previousTag: String?): String {
    if (!enabled) {
      return ""
    }
    if (hideDuplicates && tag == previousTag) {
      return dupTag
    }
    if (tag == "") {
      return noTag
    }
    if (tag.length > maxLength) {
      return StringUtil.shortenTextWithEllipsis(tag, maxLength, maxLength - PREFIX_LEN) + " "
    }
    return tag.padEnd(maxLength + 1)
  }
}
