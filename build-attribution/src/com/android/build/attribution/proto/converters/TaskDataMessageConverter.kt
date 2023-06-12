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
package com.android.build.attribution.proto.converters

import com.android.build.attribution.BuildAnalysisResultsMessage
import com.android.build.attribution.data.PluginData
import com.android.build.attribution.data.TaskData
import com.android.build.attribution.proto.PairEnumFinder
import com.android.buildanalyzer.common.TaskCategory

class TaskDataMessageConverter {
  companion object {
    fun transform(taskData: TaskData): BuildAnalysisResultsMessage.TaskData =
      BuildAnalysisResultsMessage.TaskData.newBuilder()
        .setTaskName(taskData.taskName)
        .setOriginPluginId(taskData.originPlugin.idName)
        .setProjectPath(taskData.projectPath)
        .setExecutionStartTime(taskData.executionStartTime)
        .setExecutionEndTime(taskData.executionEndTime)
        .setExecutionMode(transformExecutionMode(taskData.executionMode))
        .addAllExecutionReasons(taskData.executionReasons)
        .setIsOnTheCriticalPath(taskData.isOnTheCriticalPath)
        .setTaskType(taskData.taskType)
        .setPrimaryTaskCategory(transformTaskCategory(taskData.primaryTaskCategory))
        .addAllSecondaryTaskCategories(taskData.secondaryTaskCategories.map(this::transformTaskCategory))
        .build()

    fun construct(taskData: List<BuildAnalysisResultsMessage.TaskData>, plugins: Map<String, PluginData>): List<TaskData> {
      val taskDataList = mutableListOf<TaskData>()
      for (task in taskData) {
        val taskName = task.taskName
        val projectPath = task.projectPath
        val pluginType = plugins[task.originPluginId]?.pluginType
        val originPlugin = pluginType?.let { PluginData(it, task.originPluginId) }
        val executionStartTime = task.executionStartTime
        val executionEndTime = task.executionEndTime
        val executionMode = constructExecutionMode(task.executionMode)
        val executionReasons = task.executionReasonsList
        val isOnTheCriticalPath = task.isOnTheCriticalPath
        val taskType = task.taskType
        if (task.primaryTaskCategory == BuildAnalysisResultsMessage.TaskData.TaskCategory.UNRECOGNIZED)
          throw IllegalStateException("Unrecognized task primary category")
        val primaryTaskCategory = constructTaskCategory(task.primaryTaskCategory)
        val secondaryTaskCategory = task.secondaryTaskCategoriesList.map(this::constructTaskCategory)
        originPlugin
          ?.let {
            val data = TaskData(taskName, projectPath, it, executionStartTime, executionEndTime, executionMode, executionReasons)
            data.isOnTheCriticalPath = isOnTheCriticalPath
            data.setTaskType(taskType)
            data.setTaskCategories(primaryTaskCategory, secondaryTaskCategory)
            taskDataList.add(data)
          }
      }
      return taskDataList
    }

    private fun transformExecutionMode(executionMode: TaskData.TaskExecutionMode): BuildAnalysisResultsMessage.TaskData.TaskExecutionMode =
      PairEnumFinder.aToB(executionMode)

    private fun transformTaskCategory(taskCategory: TaskCategory): BuildAnalysisResultsMessage.TaskData.TaskCategory =
      PairEnumFinder.aToB(taskCategory)

    private fun constructExecutionMode(executionMode: BuildAnalysisResultsMessage.TaskData.TaskExecutionMode): TaskData.TaskExecutionMode =
      PairEnumFinder.bToA(executionMode)

    private fun constructTaskCategory(taskCategoryMessage: BuildAnalysisResultsMessage.TaskData.TaskCategory): TaskCategory =
      PairEnumFinder.bToA(taskCategoryMessage)
  }
}