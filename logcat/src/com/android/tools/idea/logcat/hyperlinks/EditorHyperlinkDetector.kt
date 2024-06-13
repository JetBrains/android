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
package com.android.tools.idea.logcat.hyperlinks

import com.android.tools.idea.studiobot.StudioBot
import com.intellij.execution.filters.Filter
import com.intellij.execution.impl.ConsoleViewUtil
import com.intellij.execution.impl.EditorHyperlinkSupport
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Expirable
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.concurrency.AppExecutorUtil
import java.util.concurrent.ExecutorService
import org.jetbrains.annotations.VisibleForTesting

/** A [HyperlinkDetector] that adds hyperlinks to an [Editor] */
internal class EditorHyperlinkDetector(
  private val project: Project,
  editor: EditorEx,
  parentDisposable: Disposable,
  modalityState: ModalityState,
  executor: ExecutorService = AppExecutorUtil.getAppExecutorService(),
) : HyperlinkDetector, Disposable {
  private val editorHyperlinkSupport = EditorHyperlinkSupport.get(editor)
  private val studioBot = StudioBot.getInstance()
  private var isDisposed = false

  private val expirableToken = Expirable { isDisposed }

  @VisibleForTesting val filter = SdkSourceRedirectFilter(project, SimpleFileLinkFilter(project))

  init {
    Disposer.register(parentDisposable, this)

    if (studioBot.isAvailable()) {
      filter.addFilter(StudioBotFilter(editor))
    }

    // Add all standard filters
    // Performed as a background task based on `ConsoleViewImpl.updatePredefinedFiltersLater()`
    ReadAction.nonBlocking<List<Filter>> {
        ConsoleViewUtil.computeConsoleFilters(project, null, GlobalSearchScope.allScope(project))
      }
      .expireWith(parentDisposable)
      .finishOnUiThread(modalityState) { filters: List<Filter> ->
        filters.forEach { filter.addFilter(it) }
      }
      .submit(executor)
  }

  override fun detectHyperlinks(startLine: Int, endLine: Int, sdk: Int?) {
    filter.apiLevel = sdk
    editorHyperlinkSupport.highlightHyperlinksLater(filter, startLine, endLine, expirableToken)
  }

  override fun dispose() {
    isDisposed = true
  }
}
