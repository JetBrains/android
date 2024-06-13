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
package com.android.tools.idea.gradle.declarative.psi

fun String.escape(): String {
  val sb = StringBuilder()
  for (char in this) {
    when (char) {
      '\'', '\"', '\\', '\$' -> sb.append('\\').append(char)
      '\t' -> sb.append("\\t")
      '\b' -> sb.append("\\b")
      '\n' -> sb.append("\\n")
      '\r' -> sb.append("\\r")
      else -> sb.append(char)
    }
  }
  return sb.toString()
}

private fun String.unicodeSequenceAt(pos: Int): Pair<Char, Int>? {
  var code = 0
  for (i in 0 until 4) {
    if (pos + i >= this.length) return null
    code = code * 16 + (this[pos+i].digitToIntOrNull(16) ?: return null)
  }
  return code.toChar() to pos + 4
}

private fun String.unescapeSequenceAt(pos: Int): Pair<Char, Int>? {
  if (pos >= this.length) return null
  return when (this[pos]) {
    't' -> '\t' to pos+1
    'b' -> '\b' to pos+1
    'n' -> '\n' to pos+1
    'r' -> '\r' to pos+1
    '\'' -> '\'' to pos+1
    '\"' -> '\"' to pos+1
    '\\' -> '\\' to pos+1
    '\$' -> '\$' to pos+1
    'u' -> unicodeSequenceAt(pos+1)
    else -> null
  }
}

fun String.unescape(): String? {
  val sb = StringBuilder()
  var i = 0
  while (i < length) {
    when (val char = this[i++]) {
      '\\' -> unescapeSequenceAt(i)?.let { p -> sb.append(p.first).also { i = p.second } } ?: return null
      else -> sb.append(char)
    }
  }
  return sb.toString()
}