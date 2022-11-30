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
package com.android.tools.idea.uibuilder.lint

import com.android.tools.idea.common.error.Issue
import com.android.tools.idea.common.error.IssueSource
import com.android.tools.idea.common.error.NlComponentIssueSource
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.uibuilder.surface.NlAtfIssue
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintIssueProvider
import com.intellij.lang.ASTNode
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import com.intellij.psi.xml.XmlChildRole
import org.jsoup.Jsoup

typealias CommonPanelIssueSet = MutableSet<CommonProblemsPanelIssue>

/** Issues to be used in common problems panel */
class CommonProblemsPanelIssue(issue: Issue) : Issue() {
  override val summary: String = issue.summary
  override val description: String = issue.description
  override val severity: HighlightSeverity = issue.severity
  override val source: IssueSource = issue.source
  override val category: String = issue.category

  /** Returns the text range of the issue. */
  val range: TextRange?
    get() {
      when (source) {
        is VisualLintIssueProvider.VisualLintIssueSource -> {
          source.components.forEach { component ->
            getTextRange(component)?.let { return it }
        }}
        is NlComponentIssueSource -> return source.component?.let { getTextRange(it) }

      }

      return null
    }

  /** Returns formatted plain strings (from html description) */
  val formattedDescription: String get() = Jsoup.parse(this.description).text()

  override fun equals(other: Any?): Boolean {
    if (other === this) return true
    if (other !is CommonProblemsPanelIssue) return false
    return other.severity == severity
           && other.summary == summary
           && other.description == description
           && other.category == category
           && other.range == range
  }

  override fun hashCode(): Int {
    var result = 13
    result += 17 * severity.hashCode()
    result += 19 * summary.hashCode()
    result += 23 * description.hashCode()
    result += 29 * category.hashCode()
    result += 31 * range.hashCode()
    return result
  }
}

/** Displays all issues in [annotationResult] to [holder] with appropriate range as source text */
fun showIssuesInCommonProblemsPanel(annotationResult: CommonPanelIssueSet?, holder: AnnotationHolder) {
  annotationResult?.forEach { issue ->
    val builder = holder.newAnnotation(issue.severity, issue.formattedDescription)
    issue.range?.let {
      builder.range(it)
    }
    builder.needsUpdateOnTyping(true).create()
  }
}
