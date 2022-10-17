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
import com.android.ide.common.repository.AgpVersion
import com.intellij.util.text.DateFormatUtil
import org.jetbrains.kotlin.utils.addToStdlib.sumByLong
import java.text.SimpleDateFormat
import java.util.Comparator
import java.util.Date
import java.util.Locale

class TaskIssueReportGenerator(
  private val reportData: BuildAttributionReportUiData,
  private val platformInformationProvider: () -> String,
  private val agpVersionsProvider: () -> List<AgpVersion>
) {

  fun generateReportText(taskData: TaskUiData): String {
    return """
${generateHeaderText(taskData.pluginName)}

${generateFoundIssuesText(taskData)}

Plugin: ${taskData.pluginName}
Task: ${taskData.name}
Task type: ${taskData.taskType}
${generateTaskExecutionText(taskData)}
====Build information:====
${generateBuildInformationText()}
====Platform information:====
${generatePlatformInformationText()}
""".trim()
  }

  private fun generateHeaderText(pluginName: String): String {
    val date = SimpleDateFormat("HH:mm, MMM dd, yyyy", Locale.US).format(
      Date(reportData.buildSummary.buildFinishedTimestamp))
    return "At ${date}, Android Studio detected the following issue(s) with Gradle plugin ${pluginName}"
  }

  private fun generateFoundIssuesText(taskData: TaskUiData): String =
    taskData.issues.joinToString(separator = "\n\n") { taskIssue -> "${taskIssue.type.uiName}\n${taskIssue.bugReportBriefDescription}" }

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

  private fun generateTaskExecutionText(taskData: TaskUiData): String {
    return buildString {
      val occurrences = findOtherTaskOccurrencesWithIssues(taskData)
      val timeSum = TimeWithPercentage(
        occurrences.sumByLong { it.executionTime.timeMs },
        reportData.buildSummary.criticalPathDuration.timeMs
      )
      appendln(
        "Issues for the same task were detected in ${occurrences.size} module(s), total execution time was ${timeSum.commonString()}, by module:")
      for (task in occurrences) {
        val line = "Execution mode: ${task.executionMode}, " +
                   "time: ${task.executionTime.commonString()}, " +
                   "determines build duration: ${task.onExtendedCriticalPath}, " +
                   "on critical path: ${task.onLogicalCriticalPath}, " +
                   task.issues.joinToString(prefix = "issues: ") { it.type.uiName }
        appendln("  $line")
      }
    }
  }

  private fun generateAgpVersionsString(): String =
    agpVersionsProvider().toSortedSet(Comparator.reverseOrder()).joinToString(limit = 5) { it.toString() }

  private fun findOtherTaskOccurrencesWithIssues(taskData: TaskUiData): List<TaskUiData> = reportData.issues.asSequence()
    .flatMap { issuesGroup -> issuesGroup.issues.asSequence() }
    .map { it.task }
    .filter { it.isSameTask(taskData) }
    .distinct()
    .sortedByDescending { it.executionTime.timeMs }
    .toList()

  private fun TimeWithPercentage.commonString(): String = "${durationString()} (${percentageString()})"

  /**
   * Checks if this task is same as other possible from different module.
   */
  private fun TaskUiData.isSameTask(other: TaskUiData): Boolean {
    return this.name == other.name &&
           this.pluginName == other.pluginName
  }
}