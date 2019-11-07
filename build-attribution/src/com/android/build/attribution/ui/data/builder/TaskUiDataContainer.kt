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
package com.android.build.attribution.ui.data.builder

import com.android.build.attribution.analyzers.BuildEventsAnalysisResult
import com.android.build.attribution.analyzers.isAndroidPlugin
import com.android.build.attribution.analyzers.isGradlePlugin
import com.android.build.attribution.analyzers.isKotlinPlugin
import com.android.build.attribution.data.PluginData
import com.android.build.attribution.data.TaskData
import com.android.build.attribution.ui.data.PluginSourceType
import com.android.build.attribution.ui.data.TaskIssueUiData
import com.android.build.attribution.ui.data.TaskUiData
import com.android.build.attribution.ui.data.TimeWithPercentage

/**
 * This class holds [TaskUiData] representations for [TaskData] objects provided from build analyzers.
 * Clients of this class may assume that there is only one [TaskUiData] object for every [TaskData] object.
 */
class TaskUiDataContainer(
  buildAnalysisResult: BuildEventsAnalysisResult,
  val issuesContainer: TaskIssueUiDataContainer
) {

  private val tasksCache: MutableMap<TaskData, TaskUiData> = HashMap()
  private val criticalPathTasks: Set<TaskData> = buildAnalysisResult.getCriticalPathTasks().toHashSet()
  private val totalBuildTimeMs: Long = buildAnalysisResult.getTotalBuildTimeMs()

  fun getByTaskData(task: TaskData): TaskUiData = tasksCache.computeIfAbsent(task) {
    object : TaskUiData {
      override val pluginName: String = task.originPlugin.displayName
      override val sourceType: PluginSourceType = when {
        isAndroidPlugin(task.originPlugin) -> PluginSourceType.ANDROID_PLUGIN
        isKotlinPlugin(task.originPlugin) -> PluginSourceType.ANDROID_PLUGIN
        isGradlePlugin(task.originPlugin) -> PluginSourceType.ANDROID_PLUGIN
        task.originPlugin.pluginType == PluginData.PluginType.SCRIPT -> PluginSourceType.BUILD_SRC
        else -> PluginSourceType.THIRD_PARTY
      }
      override val module: String = task.projectPath
      override val taskPath: String = task.getTaskPath()
      override val taskType: String = task.taskType
      override val executionTime: TimeWithPercentage = TimeWithPercentage(task.executionTime, totalBuildTimeMs)
      override val executedIncrementally: Boolean = task.executionMode == TaskData.TaskExecutionMode.INCREMENTAL
      override val onCriticalPath: Boolean = task in criticalPathTasks
      override val reasonsToRun: List<String> = task.executionReasons
      override val issues: List<TaskIssueUiData>
        get() = issuesContainer.issuesForTask(task)
    }
  }
}
