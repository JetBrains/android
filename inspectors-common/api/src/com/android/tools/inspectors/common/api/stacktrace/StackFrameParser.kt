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
  private const val CLASS_NAME = "class"
  private const val METHOD_NAME = "method"
  private const val FILE_NAME = "file"
  private const val LINE_NUMBER = "line"

  private val patterns = listOf(
    "(?<$CLASS_NAME>.+)\\.(?<$METHOD_NAME>.+)\\((?<$FILE_NAME>.+):(?<$LINE_NUMBER>.+)\\)",
    "(?<$CLASS_NAME>.+)\\.(?<$METHOD_NAME>.+)\\((?<$FILE_NAME>.+)\\)",
  )

  private val expressions = patterns.map { Regex(it, RegexOption.IGNORE_CASE) }

  @JvmStatic
  fun parseFrame(line: String): CodeLocation? = tryParseFrame(expressions.firstNotNullOfOrNull { it.matchEntire(line) })

  private fun tryParseFrame(match: MatchResult?): CodeLocation? {
    if (match == null) {
      return null
    }

    val className = match.groups[CLASS_NAME]!!.value
    val methodName = match.groups[METHOD_NAME]!!.value
    val fileName = match.groups[FILE_NAME]!!.value

    // If there is no LINE_NUMBER group, we will throw an exception. If the line number is not an
    // integer, we will throw an exception. Either way, we will use an invalid line number in the
    // code location.
    val lineNumber = try {
      // Convert the line number from 1-base to 0-base. The Java stack traces use 1-base whereas we
      // use 0-based.
      Integer.parseInt(match.groups[LINE_NUMBER]!!.value) - 1
    } catch (e: Exception) {
      CodeLocation.INVALID_LINE_NUMBER
    }

    return CodeLocation.Builder(className).apply {
      setFileName(fileName)
      setMethodName(methodName)
      setLineNumber(lineNumber)
    }.build()
  }
}