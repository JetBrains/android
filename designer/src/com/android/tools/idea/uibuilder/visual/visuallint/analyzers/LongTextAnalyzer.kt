/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.visual.visuallint.analyzers

import android.widget.TextView
import com.android.ide.common.rendering.api.ViewInfo
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.rendering.RenderResult
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintAnalyzer
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintErrorType
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintInspection
import com.android.utils.HtmlBuilder

/**
 * Maximum length of a line of text, according to Material Design guidelines.
 */
private const val MAX_LENGTH = 120

/**
 * [VisualLintAnalyzer] for issues where a line of text is longer than [MAX_LENGTH] characters.
 */
object LongTextAnalyzer : VisualLintAnalyzer() {
  override val type: VisualLintErrorType
    get() = VisualLintErrorType.LONG_TEXT

  override val backgroundEnabled: Boolean
    get() = LongTextAnalyzerInspection.longTextBackground

  override fun findIssues(renderResult: RenderResult, model: NlModel): List<VisualLintIssueContent> {
    val issues = mutableListOf<VisualLintIssueContent>()
    val viewsToAnalyze = ArrayDeque(renderResult.rootViews)
    while (viewsToAnalyze.isNotEmpty()) {
      val view = viewsToAnalyze.removeLast()
      view.children.forEach { viewsToAnalyze.addLast(it) }
      if (hasLongText(view)) {
        issues.add(createIssueContent(view))
      }
    }
    return issues
  }

  private fun hasLongText(view: ViewInfo): Boolean {
    (view.viewObject as? TextView)?.layout?.let {
      for (i in 0 until it.lineCount) {
        val numChars = it.getLineVisibleEnd(i) - it.getEllipsisCount(i) - it.getLineStart(i) + 1
        if (numChars > MAX_LENGTH) {
          return true
        }
      }
    }
    return false
  }

  private fun createIssueContent(view: ViewInfo): VisualLintIssueContent {
    val summary = "${nameWithId(view)} has lines containing more than 120 characters"
    val url = "https://material.io/design/layout/responsive-layout-grid.html#breakpoints"
    val provider = { count: Int ->
      HtmlBuilder()
        .add("${simpleName(view)} has lines containing more than 120 characters in ${previewConfigurations(count)}.")
        .newline()
        .add("Material Design recommends reducing the width of TextView or switching to a ")
        .addLink("multi-column layout", url)
        .add(" for breakpoints >= 600dp.")
    }
    return VisualLintIssueContent(view, summary, provider)
  }
}

object LongTextAnalyzerInspection: VisualLintInspection(VisualLintErrorType.LONG_TEXT, "longTextBackground") {
  var longTextBackground = true
}
