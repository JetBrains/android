/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.surface

import com.android.tools.idea.common.error.Issue
import com.android.tools.idea.common.error.IssueModel
import com.android.tools.idea.common.error.IssueProvider
import com.android.tools.idea.common.error.IssueSource
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.validator.ValidatorData
import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.ImmutableCollection
import com.intellij.lang.annotation.HighlightSeverity
import java.awt.Desktop
import java.net.URL
import java.util.stream.Stream
import javax.swing.event.HyperlinkEvent
import javax.swing.event.HyperlinkListener

/**
 * Lint integrator for issues created by ATF (Accessibility Testing Framework)
 */
class AccessibilityLintIntegrator(private val issueModel: IssueModel) {

  private val ACCESSIBILITY_CATEGORY = "Accessibility"
  private val ACCESSIBILITY_ISSUE = "Accessibility Issue"
  private val CONTENT_LABELING = "CONTENT_LABELING"
  private val TOUCH_TARGET_SIZE = "TOUCH_TARGET_SIZE"
  private val LOW_CONTRAST = "LOW_CONTRAST"

  private val issueProvider: IssueProvider = object : IssueProvider() {
    override fun collectIssues(issueListBuilder: ImmutableCollection.Builder<Issue>) {
      issues.forEach {
        issueListBuilder.add(it)
      }
    }
  }

  /**
   * Returns the list of accessibility issues created by ATF.
   */
  val issues = HashSet<Issue>()

  /**
   * Clear all lints and disable atf lint.
   */
  fun disableAccessibilityLint() {
    issueModel.removeIssueProvider(issueProvider)
    issues.clear()
  }

  /**
   * Populate lints based on issues created through [createAnIssue]
   */
  fun populateLints() {
    issueModel.addIssueProvider(issueProvider)
  }

  private fun forceUpdate() {
    issueModel.removeIssueProvider(issueProvider)
    issueModel.addIssueProvider(issueProvider)
  }

  /**
   * Creates a single issue/lint that matches given parameters. Must call [populateLints] in order for issues to be visible.
   */
  fun createIssue(result: ValidatorData.Issue, component: NlComponent?) {
    if (result.mLevel == ValidatorData.Level.VERBOSE || result.mLevel == ValidatorData.Level.INFO) {
      return
    }
    val source = if (component == null) {
      IssueSource.NONE
    }
    else {
      IssueSource.fromNlComponent(component)
    }
    issues.add(object : Issue() {
      override val summary: String
        get() =
          when (result.mCategory) {
            CONTENT_LABELING -> "Content labels missing or confusing"
            TOUCH_TARGET_SIZE -> "Touch size too small"
            LOW_CONTRAST -> "Low contrast"
            else -> ACCESSIBILITY_ISSUE
          }

      override val description: String
        get() {
          if (result.mHelpfulUrl.isNullOrEmpty()) {
            return result.mMsg
          }
          return """${result.mMsg}<br><br>Learn more at <a href="${result.mHelpfulUrl}">${result.mHelpfulUrl}</a>"""
        }

      override val severity: HighlightSeverity
        get() {
          return when (result.mLevel) {
            ValidatorData.Level.ERROR -> HighlightSeverity.ERROR
            ValidatorData.Level.WARNING -> HighlightSeverity.WARNING
            else -> HighlightSeverity.INFORMATION
          }
        }

      override val source: IssueSource = source

      override val category: String = ACCESSIBILITY_CATEGORY

      override val fixes: Stream<Fix>
        get() {
          return convertToFix(this, component, result)
        }

      override val hyperlinkListener: HyperlinkListener?
        get() {
          if (result.mHelpfulUrl.isNullOrEmpty()) {
            return null
          }
          return HyperlinkListener {
            if (it.eventType == HyperlinkEvent.EventType.ACTIVATED) {
              Desktop.getDesktop().browse(URL(result.mHelpfulUrl).toURI())
            }
          }
        }
    })
  }

  private fun convertToFix(issue: Issue, component: NlComponent?, result: ValidatorData.Issue): Stream<Issue.Fix> {
    // TODO b/150331000 Implement this based on result later.
    val fixes = ArrayList<Issue.Fix>()
    return fixes.stream()
  }
}
