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
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.rendering.RenderResult
import com.android.tools.idea.uibuilder.model.viewInfo
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintAnalyzer
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintAtfAnalysis
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintAtfIssue
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintErrorType
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintInspection
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintIssueProvider
import com.android.utils.HtmlBuilder
import com.google.android.apps.common.testing.accessibility.framework.checks.DuplicateClickableBoundsCheck

/**
 * [VisualLintAnalyzer] for issues coming from the Accessibility Testing Framework.
 */
object AtfAnalyzer : VisualLintAnalyzer() {
  override val type: VisualLintErrorType
    get() = VisualLintErrorType.ATF

  override val backgroundEnabled: Boolean
    get() = AtfAnalyzerInspection.atfBackground

  /**
   * Analyze the given [RenderResult] for issues related to ATF that overlaps with visual lint.
   * For now, it only runs [DuplicateClickableBoundsCheck] among all other atf checks.
   *
   * To run more checks, update the policy in [VisualLintAtfAnalysis.validateAndUpdateLint]
   */
  override fun findIssues(renderResult: RenderResult, model: NlModel): List<VisualLintIssueContent> {
    if (!StudioFlags.NELE_ATF_IN_VISUAL_LINT.get()) {
      return emptyList()
    }
    val atfAnalyzer = VisualLintAtfAnalysis(model)
    val atfIssues = atfAnalyzer.validateAndUpdateLint(renderResult)
    return atfIssues.map { createVisualLintIssueContent(it) }.toList()
  }

  private fun createVisualLintIssueContent(issue: VisualLintAtfIssue): VisualLintIssueContent {
    val content = { _: Int -> HtmlBuilder().addHtml(issue.description) }
    return VisualLintIssueContent(issue.component.viewInfo, issue.summary, content)
  }
}

object AtfAnalyzerInspection: VisualLintInspection(VisualLintErrorType.ATF, "atfBackground") {
  var atfBackground = true
}
