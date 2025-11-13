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
package com.android.tools.idea.insights.ui

import com.intellij.execution.filters.TextConsoleBuilderImpl
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.ui.ConsoleViewPlace
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.unscramble.AnalyzeStacktraceUtil

internal val AQI_CONSOLE_VIEW_PLACE = ConsoleViewPlace("AQI")

class StackTraceConsoleBuilder(project: Project) : TextConsoleBuilderImpl(project) {
  init {
    filters(AnalyzeStacktraceUtil.EP_NAME.getExtensions(project))
  }

  override fun createConsole(): StackTraceConsoleView {
    return StackTraceConsoleView(project, scope, isViewer, isUsePredefinedMessageFilter)
  }
}

class StackTraceConsoleView(
  project: Project,
  scope: GlobalSearchScope,
  isViewer: Boolean,
  usePredefinedMessage: Boolean,
) : ConsoleViewImpl(project, scope, isViewer, usePredefinedMessage) {
  override fun getPlace() = AQI_CONSOLE_VIEW_PLACE
}
