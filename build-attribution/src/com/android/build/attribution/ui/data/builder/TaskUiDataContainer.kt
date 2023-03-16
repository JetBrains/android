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
import com.android.build.attribution.data.PluginData
import com.android.build.attribution.data.TaskData
import com.android.build.attribution.ui.data.PluginSourceType
import com.android.build.attribution.ui.data.TaskIssueUiData
import com.android.build.attribution.ui.data.TaskUiData
import com.android.build.attribution.ui.data.TimeWithPercentage
import com.android.buildanalyzer.common.TaskCategory
import com.android.buildanalyzer.common.TaskCategoryIssue

/**
 * This class holds [TaskUiData] representations for [TaskData] objects provided from build analyzers.
 * Clients of this class may assume that there is only one [TaskUiData] object for every [TaskData] object.
 */
class TaskUiDataContainer(
  buildAnalysisResult: BuildEventsAnalysisResult,
  val issuesContainer: TaskIssueUiDataContainer,
  val taskCategoryIssuesContainer: TaskCategoryIssueUiDataContainer,
  private val criticalPathDuration: Long
) {

  private val tasksCache: MutableMap<TaskData, TaskUiData> = HashMap()
  private val tasksDeterminingBuildDuration: Set<TaskData> = buildAnalysisResult.getTasksDeterminingBuildDuration().toHashSet()
  private val configurationCacheUsed: Boolean = buildAnalysisResult.buildUsesConfigurationCache()

  fun getByTaskData(task: TaskData): TaskUiData = tasksCache.computeIfAbsent(task) {
    object : TaskUiData {
      override val pluginName: String = task.originPlugin.displayNameInProject(task.projectPath)
      override val sourceType: PluginSourceType = when {
        task.originPlugin.isAndroidPlugin() -> PluginSourceType.ANDROID_PLUGIN
        task.originPlugin.isKotlinPlugin() -> PluginSourceType.ANDROID_PLUGIN
        task.originPlugin.isGradlePlugin() -> PluginSourceType.ANDROID_PLUGIN
        task.originPlugin.isJavaPlugin() -> PluginSourceType.ANDROID_PLUGIN
        task.originPlugin.pluginType == PluginData.PluginType.BUILDSRC_PLUGIN ||
        task.originPlugin.pluginType == PluginData.PluginType.SCRIPT -> PluginSourceType.BUILD_SCRIPT
        else -> PluginSourceType.THIRD_PARTY
      }
      override val pluginUnknownBecauseOfCC: Boolean = task.originPlugin.pluginType == PluginData.PluginType.UNKNOWN &&
                                                       configurationCacheUsed

      override val module: String = task.projectPath
      override val name: String = task.taskName
      override val taskPath: String = task.getTaskPath()
      override val taskType: String = task.taskType
      override val executionTime: TimeWithPercentage = TimeWithPercentage(task.executionTime, criticalPathDuration)
      override val executedIncrementally: Boolean = task.executionMode == TaskData.TaskExecutionMode.INCREMENTAL
      override val executionMode: String = task.executionMode.name
      override val onLogicalCriticalPath: Boolean = task.isOnTheCriticalPath
      override val onExtendedCriticalPath: Boolean = task in tasksDeterminingBuildDuration
      override val reasonsToRun: List<String> = task.executionReasons
      override val issues: List<TaskIssueUiData>
        get() = issuesContainer.issuesForTask(task)
      override val primaryTaskCategory: TaskCategory = task.primaryTaskCategory
      override val secondaryTaskCategories: List<TaskCategory> = task.secondaryTaskCategories
      override val relatedTaskCategoryIssues = taskCategoryIssuesContainer.issuesForCategory(
        task.primaryTaskCategory, TaskCategoryIssue.Severity.WARNING
      )
    }
  }
}
