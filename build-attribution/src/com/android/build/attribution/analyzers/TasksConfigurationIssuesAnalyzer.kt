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
package com.android.build.attribution.analyzers

import com.android.build.attribution.data.StudioProvidedInfo
import com.android.build.attribution.data.TaskContainer
import com.android.build.attribution.data.TasksSharingOutputData
import com.android.buildanalyzer.common.AndroidGradlePluginAttributionData

/**
 * An analyzer that looks for misconfigured tasks. Tasks that declare the same output are considered misconfigured.
 */
class TasksConfigurationIssuesAnalyzer(
  private val taskContainer: TaskContainer
) : BaseAnalyzer<TasksConfigurationIssuesAnalyzer.Result>(),
    BuildAttributionReportAnalyzer,
    PostBuildProcessAnalyzer {

  private var tasksSharingOutput: Map<String, List<String>> = emptyMap()

  override fun cleanupTempState() {
    tasksSharingOutput = emptyMap()
  }

  override fun receiveBuildAttributionReport(androidGradlePluginAttributionData: AndroidGradlePluginAttributionData) {
    tasksSharingOutput = androidGradlePluginAttributionData.tasksSharingOutput
  }

  override fun runPostBuildAnalysis(analyzersResult: BuildEventsAnalyzersProxy, studioProvidedInfo: StudioProvidedInfo) {
    ensureResultCalculated()
  }

  override fun calculateResult(): Result = Result(
    tasksSharingOutput = tasksSharingOutput.map { entry ->
      TasksSharingOutputData(entry.key, entry.value.mapNotNull(taskContainer::getTask))
    }.filter { entry -> entry.taskList.size > 1 }
  )

  data class Result(val tasksSharingOutput: List<TasksSharingOutputData>) : AnalyzerResult
}
