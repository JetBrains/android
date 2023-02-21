/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.lint

import com.android.builder.model.LintOptions
import com.android.tools.analytics.AnalyticsSettings
import com.android.tools.analytics.Anonymizer
import com.android.tools.analytics.CommonMetricsData
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.gradle.model.IdeAndroidProjectType
import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.android.tools.idea.lint.common.LintProblemData
import com.android.tools.idea.stats.withProjectId
import com.android.tools.lint.client.api.LintDriver
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Severity
import com.android.utils.NullLogger
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.LintAction
import com.google.wireless.android.sdk.stats.LintIssueId
import com.google.wireless.android.sdk.stats.LintIssueId.LintSeverity
import com.google.wireless.android.sdk.stats.LintPerformance
import com.google.wireless.android.sdk.stats.LintSession
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import java.io.File
import java.io.IOException
import java.nio.file.Path
import java.nio.file.Paths

/** Helper for submitting analytics for IDE usage of lint (for users who have opted in) */
class LintIdeAnalytics(private val project: com.intellij.openapi.project.Project) {
  /** Logs feedback from user on an individual issue */
  fun logFeedback(issue: String, feedback: LintAction.LintFeedback) {
    val event =
      AndroidStudioEvent.newBuilder()
        .apply {
          kind = AndroidStudioEvent.EventKind.LINT_ACTION
          computeProjectId(project)?.let { projectId = it }
          computeApplicationId(project)?.let { rawProjectId = it }
          val action =
            LintAction.newBuilder()
              .apply {
                issueId = issue
                lintFeedback = feedback
              }
              .build()
          lintAction = action
        }
        .withProjectId(project)
    UsageTracker.log(event)
  }

  /**
   * Logs feedback from user on a lint run (either on-the-fly in the editor, or explicit full
   * inspection run)
   */
  fun logSession(
    type: LintSession.AnalysisType,
    driver: LintDriver,
    severityModule: Module?,
    warnings1: List<LintProblemData>?,
    warnings2: Map<Issue, Map<File, List<LintProblemData>>>?
  ) {
    if (project.isDisposed) return

    if (!AnalyticsSettings.optedIn) {
      return
    }

    val session =
      LintSession.newBuilder()
        .apply {
          analysisType = type
          projectId = computeProjectId(project)
          lintPerformance = computePerformance(driver, type == LintSession.AnalysisType.IDE_FILE)
          baselineEnabled = driver.baseline != null
          includingGeneratedSources = driver.checkGeneratedSources
          includingTestSources = driver.checkTestSources
          includingDependencies = driver.checkDependencies
          for (issueBuilder in computeIssueData(warnings1, warnings2, severityModule).values) {
            addIssueIds(issueBuilder)
          }
        }
        .build()

    val event =
      AndroidStudioEvent.newBuilder()
        .apply {
          kind = AndroidStudioEvent.EventKind.LINT_SESSION
          lintSession = session
          javaProcessStats = CommonMetricsData.javaProcessStats
          jvmDetails = CommonMetricsData.jvmDetails
        }
        .withProjectId(project)

    UsageTracker.log(event)
  }

  private fun computePerformance(driver: LintDriver, singleFileAnalysis: Boolean): LintPerformance =
    LintPerformance.newBuilder()
      .apply {
        analysisTimeMs = System.currentTimeMillis() - driver.analysisStartTime
        fileCount = driver.fileCount.toLong()

        // When doing single file analysis we don't have an accurate module count for
        // the project etc; the below statistics aren't interesting and are misleading
        if (!singleFileAnalysis) {
          moduleCount = driver.moduleCount.toLong()
          javaSourceCount = driver.javaFileCount.toLong()
          kotlinSourceCount = driver.kotlinFileCount.toLong()
          resourceFileCount = driver.resourceFileCount.toLong()
          testSourceCount = driver.testSourceCount.toLong()
        }
      }
      .build()

  private fun recordSeverityOverride(
    map: HashMap<String, LintIssueId.Builder>,
    id: String,
    lintSeverity: LintSeverity
  ) {
    val builder = map[id]
    if (builder != null) {
      // already got severity from ProblemData entry
      return
    }
    LintIssueId.newBuilder().apply {
      map[id] = this
      issueId = id
      count = 0
      severity = lintSeverity
    }
  }

  // Mapping from Lint's severity enum to analytics severity
  private fun Severity.toAnalyticsSeverity(): LintSeverity =
    when (this) {
      Severity.FATAL -> LintSeverity.FATAL_SEVERITY
      Severity.ERROR -> LintSeverity.ERROR_SEVERITY
      Severity.WARNING -> LintSeverity.WARNING_SEVERITY
      Severity.INFORMATIONAL -> LintSeverity.INFORMATIONAL_SEVERITY
      Severity.IGNORE -> LintSeverity.IGNORE_SEVERITY
      else -> LintSeverity.UNKNOWN_SEVERITY
    }

  // Mapping from LintOptions int severities to analytics severity
  private fun Int.toAnalyticsSeverity(): LintSeverity =
    when (this) {
      LintOptions.SEVERITY_FATAL -> LintSeverity.FATAL_SEVERITY
      LintOptions.SEVERITY_ERROR -> LintSeverity.ERROR_SEVERITY
      LintOptions.SEVERITY_WARNING -> LintSeverity.WARNING_SEVERITY
      LintOptions.SEVERITY_INFORMATIONAL -> LintSeverity.INFORMATIONAL_SEVERITY
      LintOptions.SEVERITY_IGNORE -> LintSeverity.IGNORE_SEVERITY
      else -> LintSeverity.UNKNOWN_SEVERITY
    }

  private fun computeIssueData(
    warnings1: List<LintProblemData>?,
    warnings2: Map<Issue, Map<File, List<LintProblemData>>>?,
    severityModule: Module?
  ): Map<String, LintIssueId.Builder> {
    val map = HashMap<String, LintIssueId.Builder>(100)

    if (warnings1 != null) {
      recordIssueData(warnings1, map)
    }
    if (warnings2 != null) {
      for (fileMap in warnings2.values) {
        for (warnings in fileMap.values) {
          recordIssueData(warnings, map)
        }
      }
    }

    if (severityModule != null) {
      val model = GradleAndroidModel.get(severityModule)
      if (model != null) {
        try {
          val gradleModel = model.androidProject
          val lintOptions = gradleModel.lintOptions
          val overrides = lintOptions.severityOverrides
          if (!overrides.isNullOrEmpty()) {
            for ((id, severity) in overrides.entries) {
              recordSeverityOverride(map, id, severity.toAnalyticsSeverity())
            }
          }
        } catch (ignore: Throwable) { // safety measure around gradle model
        }
      }
    }

    return map
  }

  private fun recordIssueData(
    warnings: List<LintProblemData>,
    map: HashMap<String, LintIssueId.Builder>
  ) {
    for (warning in warnings) {
      val issue = warning.issue
      val id = issue.id
      val issueBuilder =
        map[id]
          ?: run {
            LintIssueId.newBuilder().apply {
              map[id] = this
              issueId = issue.id
              val configuredSeverity = warning.configuredSeverity
              severity =
                if (configuredSeverity == null || configuredSeverity == issue.defaultSeverity) {
                  LintSeverity.DEFAULT_SEVERITY
                } else {
                  configuredSeverity.toAnalyticsSeverity()
                }
            }
          }
      issueBuilder.count = issueBuilder.count + 1
    }
  }

  private fun computeApplicationId(project: com.intellij.openapi.project.Project): String? {
    //  TODO: There can be more than one. Update this once AndroidStudioEvent
    // supports a collection of project id's.
    // There might also be none: if you run lint on a Java or Kotlin-only library,
    // there's no application id.

    val moduleManager = ModuleManager.getInstance(project)
    for (module in moduleManager.modules) {
      val androidModel = GradleAndroidModel.get(module)
      if (androidModel != null) {
        if (androidModel.androidProject.projectType == IdeAndroidProjectType.PROJECT_TYPE_APP) {
          return androidModel.applicationId
        }
      }
    }
    return null
  }

  private fun computeProjectId(project: com.intellij.openapi.project.Project): String? =
    computeProjectId(Paths.get(project.basePath))

  private fun computeProjectId(projectPath: Path?): String? {
    projectPath ?: return null

    return try {
      Anonymizer.anonymizeUtf8(NullLogger(), projectPath.toAbsolutePath().toString())
    } catch (e: IOException) {
      "*ANONYMIZATION_ERROR*"
    }
  }
}
