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

import com.android.tools.lint.client.api.Configuration
import com.android.tools.lint.client.api.ConfigurationHierarchy
import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.JarFileIssueRegistry
import com.android.tools.lint.client.api.LintOptionsConfiguration
import com.android.tools.lint.client.api.LintXmlConfiguration
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.model.LintModelLintOptions

fun getDefinedSeverityImpl(
  superGetDefinedSeverity: () -> Severity?,
  issue: Issue,
  visibleDefault: Severity,
  enabledIssues: Set<Issue>,
  disabledIssues: Set<Issue>?,
): Severity? {
  // Special case: Setting disabledIssues to null (rather than empty set) allows IDE features that
  // use Lint (such as UnusedResourcesProcessor and WrongThreadInterproceduralAction) to only enable
  // the issues in enabledIssues, even if they are disabled in the Gradle config, etc., and without
  // implicitly enabling all third party issues.
  if (disabledIssues == null) {
    return if (enabledIssues.contains(issue)) {
      // Even if Issue.isEnabledByDefault() is false, this will still be something like WARNING or
      // ERROR.
      visibleDefault
    } else {
      Severity.IGNORE
    }
  }
  // Otherwise, we assume the enabled and disabled issues are from an inspection profile.
  if (enabledIssues.contains(issue)) {
    // The issue was enabled in the inspection profile. Defer to the super method, which may get
    // some explicit severity from Gradle, etc. If the super config explicitly sets severity to
    // IGNORE, then the issue will be ignored, even though it was enabled in the profile. But if
    // the super returns null, we return visibleDefault instead of null, which ensures we overrule
    // the case where Issue.isEnabledByDefault() is false.
    return superGetDefinedSeverity() ?: visibleDefault
  }

  if (issue == IssueRegistry.BASELINE_USED || issue == IssueRegistry.BASELINE_FIXED) {
    return Severity.INFORMATIONAL
  }

  if (disabledIssues.contains(issue)) {
    return Severity.IGNORE
  }

  // The issue was not present in the inspection profile. Third party issues discovered during this
  // lint run will not yet have been added to the profile, but we still want them to run. We use the
  // default severity (defer to super).
  val isThirdPartyIssue = issue.registry is JarFileIssueRegistry
  if (isThirdPartyIssue) return superGetDefinedSeverity()

  return Severity.IGNORE
}

/**
 * Configuration used in IDE projects, unless it's a Gradle project with custom lint.xml or severity
 * overrides, in which case [LintIdeGradleConfiguration] is used instead
 */
class LintIdeConfiguration(
  configurations: ConfigurationHierarchy,
  project: Project,
  private val enabledIssues: Set<Issue>,
  private val disabledIssues: Set<Issue>?,
) : LintXmlConfiguration(configurations, project) {

  override fun getDefinedSeverity(
    issue: Issue,
    source: Configuration,
    visibleDefault: Severity,
  ): Severity? =
    getDefinedSeverityImpl(
      superGetDefinedSeverity = { super.getDefinedSeverity(issue, source, visibleDefault) },
      issue = issue,
      visibleDefault = visibleDefault,
      enabledIssues = enabledIssues,
      disabledIssues = disabledIssues,
    )
}

/**
 * Configuration used in Gradle projects which specify a custom lint.xml file or severity overrides
 * or both
 */
class LintIdeGradleConfiguration(
  configurations: ConfigurationHierarchy,
  lintOptions: LintModelLintOptions,
  private val enabledIssues: Set<Issue>,
  private val disabledIssues: Set<Issue>?,
) : LintOptionsConfiguration(configurations, lintOptions) {

  override fun getDefinedSeverity(
    issue: Issue,
    source: Configuration,
    visibleDefault: Severity,
  ): Severity? =
    getDefinedSeverityImpl(
      superGetDefinedSeverity = { super.getDefinedSeverity(issue, source, visibleDefault) },
      issue = issue,
      visibleDefault = visibleDefault,
      enabledIssues = enabledIssues,
      disabledIssues = disabledIssues,
    )
}
