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
package com.android.tools.idea.run.configuration.execution

import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType

internal fun ConsoleView.printShellCommand(command: String) {
  print("$ adb shell $command \n", ConsoleViewContentType.NORMAL_OUTPUT)
}

internal fun ConsoleView.print(text: String) {
  print(text + "\n", ConsoleViewContentType.NORMAL_OUTPUT)
}

internal fun ConsoleView.printError(error: String) {
  print(error + "\n", ConsoleViewContentType.ERROR_OUTPUT)
}