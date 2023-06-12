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
package com.android.tools.idea.lint.common

import com.android.tools.idea.lint.common.LintIdeSupport.Companion.get
import com.android.tools.lint.client.api.Configuration
import com.android.tools.lint.client.api.ConfigurationHierarchy
import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.LintOptionsConfiguration
import com.android.tools.lint.client.api.LintXmlConfiguration
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.model.LintModelLintOptions

/**
 * Configuration used in IDE projects, unless it's a Gradle project with custom lint.xml or severity
 * overrides, in which case [LintIdeGradleConfiguration] is used instead
 */
class LintIdeConfiguration(
  configurations: ConfigurationHierarchy,
  project: Project,
  private val issues: Set<Issue>
) : LintXmlConfiguration(configurations, project) {
  override fun getDefinedSeverity(
    issue: Issue,
    source: Configuration,
    visibleDefault: Severity
  ): Severity? {
    val known = issues.contains(issue)
    if (!known) {
      if (issue == IssueRegistry.BASELINE) {
        return Severity.INFORMATIONAL
      }

      // Allow third-party checks
      val builtin = LintIdeIssueRegistry.get()
      if (builtin.isIssueId(issue.id)) {
        return Severity.IGNORE
      }
    }
    return super.getDefinedSeverity(issue, source, visibleDefault)
  }
}

/**
 * Configuration used in Gradle projects which specify a custom lint.xml file or severity overrides
 * or both
 */
class LintIdeGradleConfiguration(
  configurations: ConfigurationHierarchy,
  lintOptions: LintModelLintOptions,
  private val issues: Set<Issue>
) : LintOptionsConfiguration(configurations, lintOptions) {
  override fun getDefinedSeverity(
    issue: Issue,
    source: Configuration,
    visibleDefault: Severity
  ): Severity? {
    val known = issues.contains(issue)
    if (!known) {
      if (issue == IssueRegistry.BASELINE) {
        return Severity.INFORMATIONAL
      }

      // Allow third-party checks
      val builtin = get().getIssueRegistry()
      if (builtin.isIssueId(issue.id)) {
        return Severity.IGNORE
      }
    }
    return super.getDefinedSeverity(issue, source, visibleDefault)
  }
}
