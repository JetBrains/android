/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.diagnostics

class SanitizedBuilder {
  /**
   * A regex to identify and capture Linux-style absolute file paths within log file lines.
   *
   * 1. The positive lookbehind `(?<=...)` matches the character that immediately precedes the path. This delimiter
   * can be a whitespace character, a colon, or an equals sign. Having those delimiter, we will be able to identify
   * strings like "java.desktop/java.awt=ALL-UNNAMED" and "com/intellij/openapi/vfs/impl" (package name in Windows)
   * as not a path. But also capture "-XX:ErrorFile=/usr/local/google/home/taorantr" and
   * "/kotlinx-coroutines-slf4j-1.8.0-intellij.jar:/usr/local/google/home/taorantr" as a valid linux file path.
   *
   * 2. The second capturing group `(/.*?)` non-greedily captures the path itself.
   *
   * 3. The positive lookahead `(?=...)` defines the end of the path. It stops the match right before one of the
   * following delimiters is found (without including the delimiter in the match, so we can use such delimiter for next match):
   * - `\s[\s-]`: Two consecutive spaces or a space followed by a hyphen (signaling a new command-line argument).
   * - `"` or `'` or `)`: Illegal characters in file path.
   * - `:`: A colon (which often separates paths in a classpath).
   * - `$`: The end of the line.
   */
  private val LINUX_MULTIPLE_FILE_PATHS = "(?<=^|[\\s:=])(/.*?)(?=\\s[\\s-]|\"|'|\\)|:|$)"

  /**
   * A regex to identify and capture Windows-style absolute file paths within log file lines.
   *
   * 1. The first capturing group `([a-zA-Z]:[\\/].*?)` non-greedily captures the path itself.
   * - It must start with a drive letter, a colon, and either a backslash `\` or forward slash `/`.
   * The reason for not look at any preceding delimiter is that we will need three consecutive
   * characters to all match the pattern to determine a file path in Windows, much less likely to match
   * actual useful data than a single slash in linux.
   *
   * 2. The positive lookahead `(?=...)` defines the end of the path. It stops the match right before one of the
   * following delimiters is found (without including the delimiter in the match, so we can use such delimiter for next match):
   * - `\s[\s-]`: Two consecutive spaces or a space followed by a hyphen (signaling a new command-line argument).
   * - `[<>:";/|?*]`: Illegal characters in file path.
   * - `;`: A semicolon (which often separates paths in a classpath).
   * - `$`: The end of the line.
   */
  private val WINDOWS_MULTIPLE_FILE_PATHS = "([a-zA-Z]:[\\\\/].*?)(?=\\s[\\s-]|[<>:\"|?*]|;|$)"

  /**
   * A regex to identify and capture Linux-style absolute file paths within log file lines until EOL
   *
   * 1. the positive lookbehind `(?<=...)` defines the start of the path. It starts the match right after
   * a) the beginning of the input string
   * b) one of the following delimiters is found (without including the delimiter in the match)
   *
   * 2. The second capturing group `(/.*)` greedily captures the path until end of line.
   */
  private val LINUX_FILE_PATH_TO_EOL = "(?<=^|[\\s:=\"])(/.*)"

  /**
   * A regex to identify and capture Windows-style absolute file paths within log file lines until EOL,
   * starts with a drive letter, a colon, and either a backslash `\` or forward slash `/`, greedily
   * capture the path until end of line.
   */
  private val WINDOWS_FILE_PATH_TO_EOL = "([a-zA-Z]:[\\\\/].*)"

  private val builder = StringBuilder()

  fun sanitizeUntilEOL(line: String) {
    if (line.isEmpty()) {
      builder.append(line + "\n")
      return
    }

    builder.append(line.replace((LINUX_FILE_PATH_TO_EOL + "|" + WINDOWS_FILE_PATH_TO_EOL).toRegex(), "<elided>") + "\n")
  }

  fun sanitizeMultiplePaths(line: String) {
    var line = line
    if (line.isEmpty()) {
      builder.append(line + "\n")
      return
    }

    line = line.replace(LINUX_MULTIPLE_FILE_PATHS.toRegex(), "<elided>")

    builder.append(line.replace(WINDOWS_MULTIPLE_FILE_PATHS.toRegex(), "<elided>") + "\n")
  }

  override fun toString(): String {
    return builder.toString()
  }
}
