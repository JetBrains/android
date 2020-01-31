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

import com.android.build.attribution.BuildAttributionWarningsFilter
import com.android.build.attribution.data.TaskContainer
import com.android.build.attribution.data.TasksSharingOutputData
import com.android.ide.common.attribution.AndroidGradlePluginAttributionData
import org.gradle.tooling.events.ProgressEvent

/**
 * An analyzer that looks for misconfigured tasks. Tasks that declare the same output are considered misconfigured.
 */
class TasksConfigurationIssuesAnalyzer(override val warningsFilter: BuildAttributionWarningsFilter, taskContainer: TaskContainer)
  : BaseTasksAnalyzer(taskContainer), BuildAttributionReportAnalyzer {
  var tasksSharingOutput: List<TasksSharingOutputData> = emptyList()

  override fun onBuildStart() {
    super.onBuildStart()
    tasksSharingOutput = emptyList()
  }

  override fun receiveBuildAttributionReport(androidGradlePluginAttributionData: AndroidGradlePluginAttributionData) {
    tasksSharingOutput = androidGradlePluginAttributionData.tasksSharingOutput.map { entry ->
      TasksSharingOutputData(entry.key, entry.value.mapNotNull(this::getTask))
    }.filter { entry -> entry.taskList.size > 1 }
  }
}
