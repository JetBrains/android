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
package com.android.tools.inspectors.common.api.stacktrace

import com.android.tools.idea.codenavigation.CodeLocation

/**
 * Class which wraps a single stack frame in a Java stack trace.
 *
 * E.g. "a.b.FooClass.someFunc(FooClass.java:123
 */
object StackFrameParser {
  @JvmStatic
  fun tryParseFrame(line: String): CodeLocation? {
    val className = getClassName(line) ?: return null

    return CodeLocation.Builder(className).apply {
      setFileName(getFileName(line))
      setMethodName(getMethodName(line))

      // Make sure we don't do INVALID_LINE_NUMBER - 1 by checking the line number value.
      val lineNumber = getLineNumber(line)
      setLineNumber(if (lineNumber == CodeLocation.INVALID_LINE_NUMBER) CodeLocation.INVALID_LINE_NUMBER else lineNumber - 1)
    }.build()
  }

  @JvmStatic
  fun parseFrame(line: String): CodeLocation {
    val location = tryParseFrame(line)

    if (location != null) {
      return location
    }

    throw IllegalStateException("Trying to create CodeLocation from an incomplete StackFrameParser. Line contents: '$line'")
  }

  private fun getClassName(line: String): String? {
    val lastDot = getLastDot(line)
    return if (lastDot == -1) {
      null
    } else line.substring(0, lastDot)
  }

  private fun getFileName(line: String): String? {
    val start = getOpenParen(line)
    val end = getLastColon(line)
    return if (start == -1 || start >= end) {
      null
    } else line.substring(start + 1, end)
  }

  private fun getMethodName(line: String): String? {
    val start = getLastDot(line)
    val end = getOpenParen(line)
    return if (start == -1 || start >= end) {
      null
    } else line.substring(start + 1, end)
  }

  private fun getLineNumber(line: String): Int {
    val start = getLastColon(line)
    val end = getCloseParen(line)
    return if (start >= end || start == -1) {
      CodeLocation.INVALID_LINE_NUMBER
    } else try {
      line.substring(start + 1, end).toInt()
    } catch (e: Exception) {
      CodeLocation.INVALID_LINE_NUMBER
    }
  }

  private fun getLastColon(line: String): Int {
    return line.lastIndexOf(':')
  }

  private fun getLastDot(line: String): Int {
    return line.lastIndexOf('.', getOpenParen(line))
  }

  private fun getOpenParen(line: String): Int {
    return line.indexOf('(')
  }

  private fun getCloseParen(line: String): Int {
    return line.indexOf(')')
  }
}