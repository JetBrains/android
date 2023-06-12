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
package com.android.tools.idea.insights.inspection

import com.android.tools.idea.insights.AppInsight
import com.android.tools.idea.insights.AppInsightsIssue
import com.android.tools.idea.insights.IssueInFrame
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.base.psi.getLineCount

/**
 * Maps issues provided by the [issueSupplier] to lines in a [PsiFile].
 *
 * The matching is naive, i.e. not verification beyond the existence of the line in the file is
 * done.
 */
class CrashToLineNaiveMapper(
  private val issueSupplier: (file: PsiFile) -> List<IssueInFrame>,
  private val onIssueClick: (AppInsightsIssue) -> Unit
) {
  private val logger = Logger.getInstance(CrashToLineNaiveMapper::class.java)

  fun retrieve(file: PsiFile): List<AppInsight> {
    return issueSupplier(file)
      .also { logIssues(it, file) }
      .mapNotNull { issueInFrame ->
        // Intellij line numbering is 0-based, crashlytics is 1-based
        val line = issueInFrame.crashFrame.frame.line.toInt() - 1
        if (file.getLineCount() > line && line >= 0)
          AppInsight(
            line,
            issueInFrame.issue,
            issueInFrame.crashFrame.frame,
            issueInFrame.crashFrame.cause,
            onIssueClick
          )
        else null
      }
  }

  private fun logIssues(issues: List<IssueInFrame>, file: PsiFile) {
    if (issues.isEmpty()) return
    val formattedIssues =
      issues
        .map { it.issue.issueDetails.subtitle.ifEmpty { "<missingSubtitle>" } }
        .reduce { acc, value -> acc + "\n" + value }
    logger.debug(
      "Found ${issues.size} issues related to ${file.name} [\n${formattedIssues}], analyzing..."
    )
  }
}
