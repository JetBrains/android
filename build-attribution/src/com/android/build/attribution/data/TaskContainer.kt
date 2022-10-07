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
package com.android.build.attribution.data

import com.android.buildanalyzer.common.AndroidGradlePluginAttributionData
import com.android.buildanalyzer.common.TaskCategory
import com.android.tools.idea.flags.StudioFlags.BUILD_ANALYZER_CATEGORY_ANALYSIS
import org.gradle.tooling.events.task.TaskFinishEvent

/**
 * A cache object to unify [TaskData] objects and share them between different analyzers.
 */
class TaskContainer {
  private val taskCache = HashMap<String, TaskData>()
  val allTasks: Map<String, TaskData> get() = taskCache

  fun getTask(taskPath: String): TaskData? {
    return taskCache[taskPath]
  }

  fun getTask(event: TaskFinishEvent, pluginContainer: PluginContainer): TaskData {
    return taskCache.getOrPut(event.descriptor.taskPath) {
      TaskData.createTaskData(event, pluginContainer)
    }
  }

  fun updateTasksData(androidGradlePluginAttributionData: AndroidGradlePluginAttributionData) {
    // Set the task type
    taskCache.values.forEach { task ->
      val taskInfo = androidGradlePluginAttributionData.taskNameToTaskInfoMap[task.taskName]
      task.setTaskType(taskInfo?.className)
      if (BUILD_ANALYZER_CATEGORY_ANALYSIS.get()) {
        val taskCategoryInfo = taskInfo?.taskCategoryInfo
        task.setTaskCategories(
          primaryTaskCategory = taskCategoryInfo?.primaryTaskCategory ?: TaskCategory.UNKNOWN,
          secondaryTaskCategories = taskCategoryInfo?.secondaryTaskCategories ?: emptyList()
        )
      }
    }
  }

  fun getTasks(predicate: (TaskData) -> Boolean) = taskCache.values.filter(predicate)

  fun clear() {
    taskCache.clear()
  }
}
