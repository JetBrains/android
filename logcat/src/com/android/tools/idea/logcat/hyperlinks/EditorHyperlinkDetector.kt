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

import com.android.tools.idea.explainer.IssueExplainer
import com.intellij.execution.filters.CompositeFilter
import com.intellij.execution.impl.ConsoleViewUtil
import com.intellij.execution.impl.EditorHyperlinkSupport
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.annotations.VisibleForTesting

/** A [HyperlinkDetector] that adds hyperlinks to an [Editor] */
internal class EditorHyperlinkDetector(private val project: Project, private val editor: EditorEx) :
  HyperlinkDetector {
  private val editorHyperlinkSupport = EditorHyperlinkSupport.get(editor)

  private val issueExplainer = IssueExplainer.get()

  @VisibleForTesting val filter = SdkSourceRedirectFilter(project, createFilters())

  override fun detectHyperlinks(startLine: Int, endLine: Int, sdk: Int?) {
    filter.apiLevel = sdk
    editorHyperlinkSupport.highlightHyperlinks(filter, startLine, endLine)
  }

  /**
   * Create a composite filter containing all the standard
   * [com.intellij.execution.filters.ConsoleFilterProvider] filters.
   *
   * In addition to the standard filters, also add our specialized [SimpleFileLinkFilter] which is
   * more reliable for project file links.
   *
   * Note that SimpleFileLinkFilter could potentially be injected via the
   * [com.intellij.execution.filters.ConsoleFilterProvider.FILTER_PROVIDERS] extension, we choose
   * not to do that because that would potentially affect all Console views. We limit this filter to
   * Logcat for now.
   */
  private fun createFilters(): CompositeFilter {
    val filters = buildList {
      addAll(
        ConsoleViewUtil.computeConsoleFilters(project, null, GlobalSearchScope.allScope(project))
      )
      add(SimpleFileLinkFilter(project))
      if (issueExplainer.isAvailable()) {
        add(StudioBotFilter(editor))
      }
    }
    return CompositeFilter(project, filters).apply { setForceUseAllFilters(true) }
  }
}
