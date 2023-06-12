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
package com.android.tools.idea.appinspection.inspectors.backgroundtask.ide

import com.android.tools.idea.appinspection.inspectors.backgroundtask.view.UiComponentsProvider
import com.android.tools.idea.codenavigation.CodeNavigator
import com.android.tools.idea.codenavigation.IntelliJNavSource
import com.android.tools.inspectors.common.api.ide.stacktrace.IntelliJStackTraceGroup
import com.android.tools.inspectors.common.api.stacktrace.StackTraceModel
import com.android.tools.inspectors.common.ui.stacktrace.StackTraceView
import com.android.tools.nativeSymbolizer.ProjectSymbolSource
import com.android.tools.nativeSymbolizer.SymbolFilesLocator
import com.android.tools.nativeSymbolizer.createNativeSymbolizer
import com.intellij.openapi.project.Project

class IntellijUiComponentsProvider(private val project: Project) : UiComponentsProvider {
  override val codeNavigator: CodeNavigator

  init {
    val locator = SymbolFilesLocator(ProjectSymbolSource(project))
    val symbolizer = createNativeSymbolizer(locator)
    codeNavigator =
      CodeNavigator(IntelliJNavSource(project, symbolizer), CodeNavigator.applicationExecutor)
  }

  override fun createStackTraceView(model: StackTraceModel): StackTraceView {
    return IntelliJStackTraceGroup(project).createStackView(model)
  }
}
