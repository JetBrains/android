/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.build.attribution.ui.data

import com.android.build.attribution.ui.durationString
import com.android.build.attribution.ui.percentageString
import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.actions.SendFeedbackAction
import com.android.tools.idea.gradle.project.ProjectStructure
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.runModalTask
import com.intellij.openapi.project.Project
import com.intellij.util.text.DateFormatUtil
import org.jetbrains.kotlin.utils.addToStdlib.sumByLong
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Comparator
import java.util.Date
import java.util.Locale

class TaskIssueBuganizerReporter(
  reportData: BuildAttributionReportUiData,
  private val project: Project
) {

  private val generator = TaskIssueBuganizerGenerator(
    reportData,
    { SendFeedbackAction.getDescription(project) },
    { ProjectStructure.getInstance(project).androidPluginVersions.allVersions }
  )

  fun reportIssue(taskIssue: TaskIssueUiData) {
    runModalTask("Collecting Data", project, true) {
      it.text = "Collecting feedback information"
      it.isIndeterminate = true
      val url = generator.generateUrl(taskIssue)
      Logger.getInstance(TaskIssueBuganizerReporter::class.java).debug("Bug reporting url clicked $url")
      BrowserUtil.browse(url, project)
    }
  }

}

class TaskIssueBuganizerGenerator(
  private val reportData: BuildAttributionReportUiData,
  private val platformInformationProvider: () -> String,
  private val agpVersionsProvider: () -> List<GradleVersion>
) {

  fun generateUrl(taskIssue: TaskIssueUiData) =
    // Use redirect defined in http://cl/281105303.
    "https://d.android.com/r/tools/build-attribution/bug?component=778729&template=1371325" +
    "&title=${URLEncoder.encode(generateReportTitle(taskIssue), StandardCharsets.UTF_8.name())}" +
    "&customFields=534281:${URLEncoder.encode(generateIssueKey(taskIssue), StandardCharsets.UTF_8.name())}" +
    "&description=${URLEncoder.encode(generateReportText(taskIssue), StandardCharsets.UTF_8.name())}"

  fun generateReportTitle(taskIssue: TaskIssueUiData): String {
    return "${taskIssue.bugReportTitle}: ${taskIssue.task.pluginName} ${taskIssue.task.name}"
  }

  fun generateReportText(taskIssue: TaskIssueUiData): String {
    return """
${generateHeaderText(taskIssue.task.pluginName)}

${taskIssue.type.uiName}
${taskIssue.bugReportBriefDescription}

Plugin: ${taskIssue.task.pluginName}
Task: ${taskIssue.task.name}
Task type: ${taskIssue.task.taskType}
${generateTaskExecutionText(taskIssue)}
====Build information:====
${generateBuildInformationText()}
====Platform information:====
${generatePlatformInformationText()}
""".trim()
  }

  private fun generateHeaderText(pluginName: String): String {
    val date = SimpleDateFormat("HH:mm, MMM dd, yyyy", Locale.US).format(Date(reportData.buildSummary.buildFinishedTimestamp))
    return "At ${date}, Android Studio detected an issue with Gradle plugin ${pluginName}"
  }

  private fun generateBuildInformationText(): String {
    return """
Execution date: ${DateFormatUtil.formatDateTime(reportData.buildSummary.buildFinishedTimestamp)}
Total build duration: ${reportData.buildSummary.totalBuildDuration.durationString()}
Configuration time: ${reportData.buildSummary.configurationDuration.commonString()}
Critical path tasks time: ${reportData.buildSummary.criticalPathDuration.commonString()}
Critical path tasks size: ${reportData.criticalPathTasks.size}
AGP versions: ${generateAgpVersionsString()}
""".trim()
  }

  private fun generatePlatformInformationText(): String {
    return platformInformationProvider().trim()
  }

  private fun generateTaskExecutionText(taskIssue: TaskIssueUiData): String {
    return buildString {
      val occurrences = findAllIssueOccurrences(taskIssue)
      val timeSum = TimeWithPercentage(
        occurrences.sumByLong { it.task.executionTime.timeMs },
        reportData.buildSummary.totalBuildDuration.totalMs
      )
      appendln("Issue detected in ${occurrences.size} module(s), total execution time was ${timeSum.commonString()}, by module:")
      for (issue in occurrences) {
        val line = "Execution mode: ${issue.task.executionMode}, " +
                   "time: ${issue.task.executionTime.commonString()}, " +
                   "on critical path: ${issue.task.onCriticalPath}"
        appendln("  $line")
      }
    }
  }

  private fun generateAgpVersionsString(): String =
    agpVersionsProvider().toSortedSet(Comparator.reverseOrder()).joinToString(limit = 5) { it.toString() }

  private fun findAllIssueOccurrences(taskIssue: TaskIssueUiData): List<TaskIssueUiData> {
    return reportData.issues
      .first { it.type == taskIssue.type }
      .issues
      .filter { it.isSameIssue(taskIssue) }
  }

  fun generateIssueKey(taskIssue: TaskIssueUiData): String =
    "${taskIssue.task.pluginName}_${taskIssue.task.name}_${taskIssue::class.simpleName}"

  private fun TimeWithPercentage.commonString(): String = "${durationString()} (${percentageString()})"

  /**
   * Checks if other issue is the same possibly from different module.
   */
  private fun TaskIssueUiData.isSameIssue(other: TaskIssueUiData): Boolean {
    return task.name == other.task.name &&
           task.pluginName == other.task.pluginName &&
           this::class == other::class
  }
}
