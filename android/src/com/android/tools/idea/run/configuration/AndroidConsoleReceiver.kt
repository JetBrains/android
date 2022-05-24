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
package com.android.tools.idea.run.configuration

import com.android.ddmlib.MultiLineReceiver
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.progress.ProgressIndicatorProvider

open class AndroidConsoleReceiver(private val isCancelledCheck: () -> Boolean,
                                  private val consoleView: ConsoleView
) : MultiLineReceiver() {
  override fun isCancelled() = isCancelledCheck()

  override fun processNewLines(lines: Array<String>) = lines.forEach {
    consoleView.print(it + "\n", ConsoleViewContentType.NORMAL_OUTPUT)
  }
}

open class AndroidBackgroundTaskReceiver(console: ConsoleView) :
  AndroidConsoleReceiver({  ProgressIndicatorProvider.getGlobalProgressIndicator()?.isCanceled == true }, console)