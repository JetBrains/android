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
import com.android.build.attribution.ui.BuildAnalyzerBrowserLinks
import com.android.build.attribution.ui.data.IssueLevel
import com.android.ide.common.attribution.AndroidGradlePluginAttributionData
import com.android.ide.common.attribution.TaskCategory
import com.android.ide.common.attribution.BuildAnalyzerTaskCategoryIssue

class TaskCategoryWarningsAnalyzer(private val taskContainer: TaskContainer) : BaseAnalyzer<TaskCategoryWarningsAnalyzer.Result>(),
                                                                               BuildAttributionReportAnalyzer,
                                                                               PostBuildProcessAnalyzer {
   private val buildAnalyzerTaskCategoryIssues = mutableListOf<BuildAnalyzerTaskCategoryIssue>()

  override fun calculateResult(): Result = Result(buildAnalyzerTaskCategoryIssues.toList())

  override fun cleanupTempState() {
    buildAnalyzerTaskCategoryIssues.clear()
  }

  override fun receiveBuildAttributionReport(androidGradlePluginAttributionData: AndroidGradlePluginAttributionData) {
    buildAnalyzerTaskCategoryIssues.addAll(androidGradlePluginAttributionData.buildAnalyzerTaskCategoryIssues)
  }

  override fun runPostBuildAnalysis(analyzersResult: BuildEventsAnalyzersProxy, studioProvidedInfo: StudioProvidedInfo) {
    if (taskContainer.getTasks { it.primaryTaskCategory == TaskCategory.RENDERSCRIPT }.isNotEmpty()) {
      buildAnalyzerTaskCategoryIssues.add(BuildAnalyzerTaskCategoryIssue.RENDERSCRIPT_API_DEPRECATED)
    }
    if (taskContainer.getTasks { it.primaryTaskCategory == TaskCategory.AIDL }.isNotEmpty()) {
      buildAnalyzerTaskCategoryIssues.add(BuildAnalyzerTaskCategoryIssue.AVOID_AIDL_UNNECESSARY_USE)
    }
    val nonIncrementalAnnotationProcessors = analyzersResult.annotationProcessorsAnalyzer.result.nonIncrementalAnnotationProcessorsData
    if (nonIncrementalAnnotationProcessors.isNotEmpty()) {
      buildAnalyzerTaskCategoryIssues.add(BuildAnalyzerTaskCategoryIssue.JAVA_NON_INCREMENTAL_ANNOTATION_PROCESSOR)
    }
    ensureResultCalculated()
  }

  data class Result(val buildAnalyzerTaskCategoryIssues: List<BuildAnalyzerTaskCategoryIssue>): AnalyzerResult
}
