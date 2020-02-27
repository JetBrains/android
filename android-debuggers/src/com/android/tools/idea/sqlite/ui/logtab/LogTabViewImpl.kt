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
package com.android.tools.idea.sqlite.ui.logtab

import com.android.annotations.concurrency.UiThread
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import java.awt.BorderLayout
import javax.swing.JPanel

@UiThread
class LogTabViewImpl(project: Project) : LogTabView {
  override val component = JPanel(BorderLayout())
  // TODO(next CL) extend LogConsoleBase instead
  private val consoleView = ConsoleViewImpl(project, true)

  init {
    component.add(consoleView.component, BorderLayout.CENTER)
    Disposer.register(project, consoleView)
  }

  override fun log(log: String) {
    consoleView.print(log, ConsoleViewContentType.NORMAL_OUTPUT)
    printNewLine(ConsoleViewContentType.NORMAL_OUTPUT)
  }

  override fun logError(log: String) {
    consoleView.print(log, ConsoleViewContentType.ERROR_OUTPUT)
    printNewLine(ConsoleViewContentType.ERROR_OUTPUT)
  }

  private fun printNewLine(contentType: ConsoleViewContentType) {
    consoleView.print("\n", contentType)
  }
}