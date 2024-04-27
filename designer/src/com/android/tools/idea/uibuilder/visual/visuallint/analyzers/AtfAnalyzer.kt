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

import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.uibuilder.model.viewInfo
import com.android.tools.idea.uibuilder.visual.colorblindmode.ColorBlindMode
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintAnalyzer
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintAtfAnalysis
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintAtfIssue
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintErrorType
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintInspection
import com.android.tools.rendering.RenderResult
import com.android.utils.HtmlBuilder

/** [VisualLintAnalyzer] for issues coming from the Accessibility Testing Framework. */
object AtfAnalyzer : VisualLintAnalyzer() {
  override val type: VisualLintErrorType
    get() = VisualLintErrorType.ATF

  override val backgroundEnabled: Boolean
    get() = AtfAnalyzerInspection.atfBackground

  /** Analyze the given [RenderResult] for issues related to ATF that overlaps with visual lint. */
  override fun findIssues(
    renderResult: RenderResult,
    model: NlModel
  ): List<VisualLintIssueContent> {
    val atfAnalyzer = VisualLintAtfAnalysis(model)
    val atfIssues = atfAnalyzer.validateAndUpdateLint(renderResult)
    return atfIssues.map { createVisualLintIssueContent(it) }.toList()
  }

  private fun createVisualLintIssueContent(issue: VisualLintAtfIssue) =
    if (issue.appliedColorBlindFilter() != ColorBlindMode.NONE && issue.isLowContrast()) {
      VisualLintIssueContent(
        issue.component.viewInfo,
        COLOR_BLIND_ISSUE_SUMMARY,
        VisualLintErrorType.ATF_COLORBLIND
      ) { count: Int ->
        colorBLindModeDescriptionProvider(issue, count)
      }
    } else {
      VisualLintIssueContent(issue.component.viewInfo, issue.summary) { _: Int ->
        HtmlBuilder().addHtml(issue.description)
      }
    }
}

class AtfAnalyzerInspection : VisualLintInspection(VisualLintErrorType.ATF, "atfBackground") {
  companion object {
    var atfBackground = true
  }
}

private const val COLOR_BLIND_ISSUE_SUMMARY = "Insufficient color contrast for color blind users"

private val colorBLindModeDescriptionProvider: (VisualLintAtfIssue, Int) -> HtmlBuilder =
  { issue, count ->
    val colorBlindFilter = issue.appliedColorBlindFilter().displayName
    val description = issue.description
    val contentDescription =
      StringBuilder()
        .append("Color contrast check fails for $colorBlindFilter ")
        .append(
          when (count) {
            0,
            1 -> "colorblind configuration"
            2 -> "and 1 other colorblind configuration"
            else -> "and ${count - 1} other colorblind configurations"
          },
        )
        .append(".<br>")
        .append(description)
        .toString()
    HtmlBuilder().addHtml(contentDescription)
  }
