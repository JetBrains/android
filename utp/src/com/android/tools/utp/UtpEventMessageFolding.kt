/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.utp

import com.intellij.execution.ConsoleFolding
import com.intellij.openapi.project.Project

/** A [ConsoleFolding] that folds UTP event messages from the console window. */
class UtpEventMessageFolding : ConsoleFolding() {
  override fun shouldFoldLine(project: Project, line: String): Boolean {
    val trimmedLine = line.trim()
    return trimmedLine.startsWith(TaskOutputProcessor.ON_RESULT_OPENING_TAG) &&
      trimmedLine.endsWith(TaskOutputProcessor.ON_RESULT_CLOSING_TAG)
  }

  override fun shouldBeAttachedToThePreviousLine(): Boolean = false

  override fun getPlaceholderText(project: Project, lines: MutableList<String>): String {
    return "<UTP Event Message>"
  }
}
