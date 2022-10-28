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
import com.android.buildanalyzer.common.AndroidGradlePluginAttributionData
import com.android.buildanalyzer.common.TaskCategory
import com.android.buildanalyzer.common.TaskCategoryIssue
import com.android.tools.idea.flags.StudioFlags

class TaskCategoryWarningsAnalyzer : BaseAnalyzer<TaskCategoryWarningsAnalyzer.Result>(),
                                     BuildAttributionReportAnalyzer,
                                     PostBuildProcessAnalyzer {
   private val taskCategoryIssues = mutableListOf<TaskCategoryIssue>()
   private var agpSupportsTaskCategories: Boolean = false

  override fun calculateResult(): Result = when {
    !StudioFlags.BUILD_ANALYZER_CATEGORY_ANALYSIS.get() -> FeatureDisabled
    !agpSupportsTaskCategories -> NoDataFromAGP
    else -> IssuesResult(taskCategoryIssues.toList())
  }

  override fun cleanupTempState() {
    taskCategoryIssues.clear()
  }

  override fun receiveBuildAttributionReport(androidGradlePluginAttributionData: AndroidGradlePluginAttributionData) {
    agpSupportsTaskCategories = androidGradlePluginAttributionData.taskNameToTaskInfoMap.any {
      it.value.taskCategoryInfo.primaryTaskCategory != TaskCategory.UNKNOWN
    }
    taskCategoryIssues.addAll(androidGradlePluginAttributionData.taskCategoryIssues)
  }

  override fun runPostBuildAnalysis(analyzersResult: BuildEventsAnalyzersProxy, studioProvidedInfo: StudioProvidedInfo) {
    if (analyzersResult.annotationProcessorsAnalyzer.result.nonIncrementalAnnotationProcessorsData.isNotEmpty()) {
      taskCategoryIssues.add(TaskCategoryIssue.JAVA_NON_INCREMENTAL_ANNOTATION_PROCESSOR)
    }
    ensureResultCalculated()
  }

  sealed class Result: AnalyzerResult

  object FeatureDisabled: Result()

  object NoDataFromAGP: Result()

  data class IssuesResult(
    val taskCategoryIssues: List<TaskCategoryIssue>
  ): Result()
}
