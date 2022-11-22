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
package com.android.tools.idea.execution.common.adb.shell

import com.android.ddmlib.MultiLineReceiver
import java.util.regex.Pattern

/**
 * An output receiver which stores all output and matches error messages.
 */
class ErrorMatchingReceiver : MultiLineReceiver() {
  private var errorType = NO_ERROR
  private var failureMessage: String? = null
  val output = StringBuilder()

  override fun processNewLines(lines: Array<String>) {
    for (line in lines) {
      if (line.isNotEmpty()) {
        val failureMatcher = FAILURE.matcher(line)
        if (failureMatcher.matches()) {
          failureMessage = failureMatcher.group(1)
        }
        val errorMatcher = TYPED_ERROR.matcher(line)
        if (errorMatcher.matches()) {
          errorType = errorMatcher.group(1).toInt()
          failureMessage = line
        }
        else if (line.startsWith(ERROR_PREFIX) && errorType == NO_ERROR) {
          errorType = UNTYPED_ERROR
          failureMessage = line
        }
      }
      output.append(line).append('\n')
    }
  }

  override fun isCancelled(): Boolean {
    return false
  }

  fun hasError(): Boolean {
    return errorType != NO_ERROR
  }

  companion object {
    private const val NO_ERROR = -2
    private const val UNTYPED_ERROR = -1
    private val FAILURE = Pattern.compile("Failure\\s+\\[(.*)\\]")
    private val TYPED_ERROR = Pattern.compile("Error\\s+[Tt]ype\\s+(\\d+).*")
    private const val ERROR_PREFIX = "Error"
  }
}