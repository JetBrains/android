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
package com.android.build.attribution.analyzers

import com.android.build.attribution.data.StudioProvidedInfo
import com.android.build.attribution.data.TaskContainer
import com.android.buildanalyzer.common.AndroidGradlePluginAttributionData
import com.android.buildanalyzer.common.TaskCategory
import com.android.buildanalyzer.common.TaskCategoryIssue

class TaskCategoryWarningsAnalyzer(private val taskContainer: TaskContainer) : BaseAnalyzer<TaskCategoryWarningsAnalyzer.Result>(),
                                                                               BuildAttributionReportAnalyzer,
                                                                               PostBuildProcessAnalyzer {
   private val taskCategoryIssues = mutableListOf<TaskCategoryIssue>()

  override fun calculateResult(): Result = Result(taskCategoryIssues.toList())

  override fun cleanupTempState() {
    taskCategoryIssues.clear()
  }

  override fun receiveBuildAttributionReport(androidGradlePluginAttributionData: AndroidGradlePluginAttributionData) {
    taskCategoryIssues.addAll(androidGradlePluginAttributionData.taskCategoryIssues)
  }

  override fun runPostBuildAnalysis(analyzersResult: BuildEventsAnalyzersProxy, studioProvidedInfo: StudioProvidedInfo) {
    if (taskContainer.getTasks { it.primaryTaskCategory == TaskCategory.RENDERSCRIPT }.isNotEmpty()) {
      taskCategoryIssues.add(TaskCategoryIssue.RENDERSCRIPT_API_DEPRECATED)
    }
    if (taskContainer.getTasks { it.primaryTaskCategory == TaskCategory.AIDL }.isNotEmpty()) {
      taskCategoryIssues.add(TaskCategoryIssue.AVOID_AIDL_UNNECESSARY_USE)
    }
    val nonIncrementalAnnotationProcessors = analyzersResult.annotationProcessorsAnalyzer.result.nonIncrementalAnnotationProcessorsData
    if (nonIncrementalAnnotationProcessors.isNotEmpty()) {
      taskCategoryIssues.add(TaskCategoryIssue.JAVA_NON_INCREMENTAL_ANNOTATION_PROCESSOR)
    }
    ensureResultCalculated()
  }

  data class Result(val taskCategoryIssues: List<TaskCategoryIssue>): AnalyzerResult
}
