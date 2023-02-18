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

private val wordSplitterRegex = "(?=\\b\\s+)".toRegex()

/** Transforms a string to a word-wrapped representation */
internal fun wordWrap(text: String, width: Int): String {
  if (text.length <= width) {
    return text
  }

  return buildString {
    val lines = text.split("\n")
    lines.forEachIndexed lines@{ index, line ->
      try {
        if (line.length <= width) {
          append(line)
          return@lines
        }

        val words = line.split(wordSplitterRegex)
        val first = words.first()
        append(first)
        var lineLen = first.length
        words.drop(1).forEach { word ->
          val wordLen = word.length
          if (lineLen + wordLen > width) {
            append("\n")
            val trimmed = word.trimStart()
            append(trimmed)
            lineLen = trimmed.length
          }
          else {
            append(word)
            lineLen += wordLen
          }
        }

      }
      finally {
        if (index < lines.size - 1) {
          append("\n")
        }
      }
    }
  }
}
