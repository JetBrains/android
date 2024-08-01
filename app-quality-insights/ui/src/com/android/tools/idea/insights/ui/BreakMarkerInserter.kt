/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.insights.ui

// This code was copied from Gemini plugin's BreakMarkerInserter

/**
 * Adds [BREAK_MARKER] to tokens longer than [MIN_BREAKABLE_TOKEN_LENGTH]. This allows the text to
 * be broken in a word/line wrapped panel.
 */
object BreakMarkerInserter {
  const val BREAK_MARKER = "%%%%%break-goes-here%%%%%"
  private const val MIN_BREAKABLE_TOKEN_LENGTH = 15

  fun insertBreakMarkersInLongTokens(
    text: String,
    marker: String = BREAK_MARKER,
    minBreakableTokenLength: Int = MIN_BREAKABLE_TOKEN_LENGTH,
  ): String {
    val builder = StringBuilder(text.length)
    var tokenLength = 0
    for (char in text) {
      builder.append(char)
      if (char.isWhitespace()) {
        tokenLength = 0
        continue
      } else {
        tokenLength++
      }

      if (tokenLength >= minBreakableTokenLength && !char.isLetterOrDigit()) {
        builder.append(marker)
      }
    }
    return builder.toString()
  }
}
