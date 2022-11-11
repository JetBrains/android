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
package com.android.build.attribution.ui.data.builder

import com.android.build.attribution.analyzers.BuildEventsAnalysisResult
import com.android.build.attribution.analyzers.TaskCategoryWarningsAnalyzer
import com.android.build.attribution.data.AnnotationProcessorData
import com.android.build.attribution.ui.data.TaskCategoryIssueUiData
import com.android.build.attribution.ui.getLink
import com.android.build.attribution.ui.getWarningMessage
import com.android.buildanalyzer.common.TaskCategory
import com.android.buildanalyzer.common.TaskCategoryIssue

class TaskCategoryIssueUiDataContainer(
  private val buildAnalysisResult: BuildEventsAnalysisResult
) {

  fun issuesForCategory(taskCategory: TaskCategory, severity: TaskCategoryIssue.Severity): List<TaskCategoryIssueUiData> {
    if (buildAnalysisResult.getTaskCategoryWarningsAnalyzerResult() !is TaskCategoryWarningsAnalyzer.IssuesResult) {
      return emptyList()
    }
    return (buildAnalysisResult.getTaskCategoryWarningsAnalyzerResult() as TaskCategoryWarningsAnalyzer.IssuesResult)
      .taskCategoryIssues.filter { issue ->
        issue.taskCategory == taskCategory && issue.severity == severity
      }.map {
        it.toUiData(buildAnalysisResult.getAnnotationProcessorsData())
      }
  }

  fun getTaskCategoryIssues(taskCategory: TaskCategory, severity: TaskCategoryIssue.Severity): List<TaskCategoryIssue> {
    if (buildAnalysisResult.getTaskCategoryWarningsAnalyzerResult() !is TaskCategoryWarningsAnalyzer.IssuesResult) {
      return emptyList()
    }
    return (buildAnalysisResult.getTaskCategoryWarningsAnalyzerResult() as TaskCategoryWarningsAnalyzer.IssuesResult)
      .taskCategoryIssues.filter { issue ->
        issue.taskCategory == taskCategory && issue.severity == severity
      }
  }

  private fun TaskCategoryIssue.toUiData(nonIncrementalAnnotationProcessors: List<AnnotationProcessorData>) = TaskCategoryIssueUiData(
    this,
    this.getWarningMessage(nonIncrementalAnnotationProcessors),
    this.getLink()
  )
}