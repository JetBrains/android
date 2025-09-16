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
package com.android.tools.idea.layoutinspector.stateinspection

import com.intellij.execution.filters.CompositeFilter
import com.intellij.execution.impl.ConsoleViewUtil
import com.intellij.execution.impl.EditorHyperlinkSupport
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Expirable
import com.intellij.psi.search.GlobalSearchScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.annotations.TestOnly

/** A Hyperlink detector that adds hyperlinks to an [Editor] */
internal class StateInspectionHyperLinkDetector(
  private val project: Project,
  private val editor: EditorEx,
  scope: CoroutineScope,
  parentDisposable: Disposable,
) {
  private val editorHyperlinkSupport = EditorHyperlinkSupport.get(editor)
  private val filter = CompositeFilter(project)
  private var isDisposed = false
  private val expirableToken = Expirable { isDisposed }

  @TestOnly val filterJob: Job

  init {
    Disposer.register(parentDisposable) { isDisposed = true }
    filterJob =
      scope.launch {
        // Add all standard filters (this will include hyperlinks of the fileName of each line)
        // Performed as a background task based on `ConsoleViewImpl.updatePredefinedFiltersLater()`
        // TODO: Remove filterJob when Intellij allows us to specify a testDispatcher in a
        // readAction.
        val filters =
          smartReadAction(project) {
            ConsoleViewUtil.computeConsoleFilters(
              project,
              null,
              GlobalSearchScope.allScope(project),
            )
          }
        filters.forEach { filter.addFilter(it) }
      }
  }

  fun detectHyperlinks() {
    // The state reads is static content, so we will always detect links in the entire document:
    val startLine = 0
    val endLine = editor.document.getLineNumber(editor.document.textLength)
    editorHyperlinkSupport.highlightHyperlinksLater(filter, startLine, endLine, expirableToken)
  }
}
