/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.tools.idea.insights.analytics.AppInsightsTracker
import com.android.tools.idea.insights.ui.vcs.InsightsAttachInlayDiffLinkFilter
import com.android.tools.idea.insights.ui.vcs.InsightsExceptionInfoCache
import com.google.common.truth.Truth
import com.intellij.execution.filters.ExceptionFilters
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.VisibleAreaListener
import com.intellij.openapi.editor.impl.event.EditorEventMulticasterImpl
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.LoggedErrorProcessor
import java.util.EnumSet

/**
 * Creates a console that's for APP Insights specific.
 *
 * An exception filter and a custom diff link inlay filter are installed.
 */
fun initConsoleWithFilters(project: Project, tracker: AppInsightsTracker): ConsoleViewImpl {
  val consoleBuilder =
    TextConsoleBuilderFactory.getInstance().createBuilder(project).apply {
      filters(ExceptionFilters.getFilters(GlobalSearchScope.allScope(project)))
    }

  val console = (consoleBuilder.console as ConsoleViewImpl)
  val exceptionInfoCache = InsightsExceptionInfoCache(project, GlobalSearchScope.allScope(project))

  console.addMessageFilter(InsightsAttachInlayDiffLinkFilter(exceptionInfoCache, console, tracker))
  console.component // call to init editor

  return console
}

fun ConsoleViewImpl.printAndHighlight(text: String) {
  print(text, ConsoleViewContentType.NORMAL_OUTPUT)

  WriteAction.run<RuntimeException>(this::flushDeferredText)
  waitAllRequests()
  editor.caretModel.moveToOffset(0)
}

/**
 * Here we explicitly remove listeners added in `EditorMouseHoverPopupManager` to sidestep false
 * leakages as the lifecycles of those listeners are tied to the application which are not excluded
 * when checking leaks. (I filed https://youtrack.jetbrains.com/issue/IDEA-323699 -- hopefully it
 * could be resolved.)
 */
fun cleanUpListenersFromEditorMouseHoverPopupManager() {
  val editorEventMulticaster =
    EditorFactory.getInstance().eventMulticaster as EditorEventMulticasterImpl

  editorEventMulticaster.listeners.onEach { (key, value) ->
    when (key) {
      CaretListener::class.java -> {
        val listener =
          value.firstOrNull {
            it.javaClass.name.startsWith(
              "com.intellij.openapi.editor.EditorMouseHoverPopupManager\$"
            )
          } as? CaretListener ?: return@onEach
        editorEventMulticaster.removeCaretListener(listener)
      }
      VisibleAreaListener::class.java -> {
        val listener =
          value.firstOrNull {
            it.javaClass.name.startsWith(
              "com.intellij.openapi.editor.EditorMouseHoverPopupManager\$"
            )
          } as? VisibleAreaListener ?: return@onEach
        editorEventMulticaster.removeVisibleAreaListener(listener)
      }
    }
  }
}

fun executeWithErrorProcessor(job: () -> Unit) {
  var error: String? = null
  val errorProcessor =
    object : LoggedErrorProcessor() {
      override fun processError(
        category: String,
        message: String,
        details: Array<out String>,
        t: Throwable?,
      ): Set<Action> {
        error = message
        return EnumSet.allOf(Action::class.java)
      }
    }

  LoggedErrorProcessor.executeWith<Throwable>(errorProcessor, job)
  Truth.assertThat(error).isNull()
}
