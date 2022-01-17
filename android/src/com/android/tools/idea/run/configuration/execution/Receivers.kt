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
package com.android.tools.idea.run.configuration.execution

import com.android.ddmlib.MultiLineReceiver
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType

internal open class AndroidLaunchReceiver(private val isCancelledCheck: () -> Boolean,
                                          private val consoleView: ConsoleView) : MultiLineReceiver() {
  private val entireOutput = StringBuilder()
  override fun isCancelled() = isCancelledCheck()

  override fun processNewLines(lines: Array<String>) = lines.forEach {
    entireOutput.append(it).append("\n")
    consoleView.print(it + "\n", ConsoleViewContentType.NORMAL_OUTPUT)
  }

  fun getOutput(): String {
    return entireOutput.toString()
  }
}

internal open class CommandResultReceiver(isCancelledCheck: () -> Boolean, consoleView: ConsoleView) : AndroidLaunchReceiver(
  isCancelledCheck, consoleView) {

  private val resultCodePattern = "result=(\\d+)".toRegex()
  var resultCode: Int? = null

  override fun processNewLines(lines: Array<String>) {
    super.processNewLines(lines)
    lines.forEach { line -> extractPattern(line, resultCodePattern)?.let { resultCode = it.toInt() } }
  }

  companion object {
    const val SUCCESS_CODE = 1
  }
}

internal fun extractPattern(line: String, pattern: Regex): String? {
  return pattern.find(line)?.groupValues?.getOrNull(1)
}