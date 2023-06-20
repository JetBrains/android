/*
 * Copyright (C) 2020 The Android Open Source Project
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
@file:JvmName("CommandLineDecoderUtils")
package com.android.tools.idea.streaming.emulator

/**
 * Parses a command line string into individual arguments. The arguments are separated by
 * whitespace characters. Each argument is optionally enclosed into double quotes. Double quotes
 * inside quoted arguments and whitespaces inside a non-quoted ones are escaped by backslashes.
 * Backslashes inside arguments are doubled. A character preceded by a single backslash is taken
 * literally and doesn't have any special meaning.
 */
fun decodeCommandLine(commandLine: String): List<String> {
  val args = ArrayList<String>(16)
  val argBuilder = StringBuilder()
  var insideArgument = false
  var quoted = false
  var escaped = false
  for (c in commandLine.trimStart()) {
    if (insideArgument) {
      if (escaped) {
        argBuilder.append(c)
        escaped = false
      }
      else {
        when {
          quoted && c == '"' || !quoted && c.isWhitespace() -> {
            args.add(argBuilder.toString())
            argBuilder.clear()
            insideArgument = false
            quoted = false
          }
          c == '\\' -> escaped = true
          else -> argBuilder.append(c)
        }
      }
    }
    else {
      if (!c.isWhitespace()) {
        insideArgument = true
        when (c) {
          '"' -> quoted = true
          '\\' -> escaped = true
          else -> argBuilder.append(c)
        }
      }
    }
  }
  if (argBuilder.isNotEmpty()) {
    args.add(argBuilder.toString())
  }
  return args
}
