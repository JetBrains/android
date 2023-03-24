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
import com.android.tools.idea.insights.InsightsProviderKey
import com.android.tools.idea.insights.IssueInFrame
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiFile

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

  fun retrieve(file: PsiFile, insightsProvider: InsightsProviderKey): List<AppInsight> {
    return issueSupplier(file)
      .also { logIssues(it, file) }
      .map { issueInFrame ->
        val oldOneBasedLineNumber = issueInFrame.crashFrame.frame.line.toInt()

        // Intellij line numbering is 0-based, whereas crashlytics is 1-based, so we
        // will have to ensure correct based line number when passing it around.
        AppInsight(
          line = oldOneBasedLineNumber - 1,
          issue = issueInFrame.issue,
          stackFrame = issueInFrame.crashFrame.frame,
          cause = issueInFrame.crashFrame.cause,
          provider = insightsProvider,
          markAsSelectedCallback = onIssueClick
        )
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
