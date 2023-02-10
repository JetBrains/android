/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.logcat.message


/**
 * Log Level enum.
 */

enum class LogLevel(val stringValue: String, val priorityLetter: Char) {
  VERBOSE("verbose", 'V'),
  DEBUG("debug", 'D'),
  INFO("info", 'I'),
  WARN("warn", 'W'),
  ERROR("error", 'E'),
  ASSERT("assert", 'A'),
  ;

  companion object {
    /**
     * Returns the [LogLevel] enum matching the specified string value.
     * @param value the string matching a `LogLevel` enum
     * @return a `LogLevel` object or `null` if no match were found.
     */
    fun getByString(value: String): LogLevel? {
      for (mode in LogLevel.values()) {
        if (mode.stringValue == value) {
          return mode
        }
      }
      return null
    }

    /**
     * Returns the [LogLevel] enum matching the specified letter.
     * @param letter the letter matching a `LogLevel` enum
     * @return a `LogLevel` object or `null` if no match were found.
     */
    fun getByLetter(letter: String): LogLevel? {
      for (mode in LogLevel.values()) {
        if (mode.priorityLetter == letter[0]) {
          return mode
        }
      }
      return null
    }
  }
}