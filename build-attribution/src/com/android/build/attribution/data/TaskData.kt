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

import org.gradle.tooling.events.task.TaskFinishEvent
import org.gradle.tooling.events.task.TaskSuccessResult

data class TaskData(val taskName: String,
                    val projectPath: String,
                    val originPlugin: PluginData,
                    val executionTime: Long,
                    val executionMode: TaskExecutionMode,
                    val executionReasons: List<String>) {
  enum class TaskExecutionMode {
    FROM_CACHE,
    UP_TO_DATE,
    INCREMENTAL,
    FULL
  }

  var taskType: String = UNKNOWN_TASK_TYPE
    private set

  fun setTaskType(taskType: String?) {
    if (taskType != null) {
      this.taskType = taskType
    }
  }

  fun getTaskPath(): String {
    return "$projectPath:$taskName"
  }

  companion object {
    const val UNKNOWN_TASK_TYPE = "UNKNOWN"

    private fun getTaskExecutionMode(isFromCache: Boolean, isUpToDate: Boolean, isIncremental: Boolean): TaskExecutionMode {
      if (isFromCache) {
        return TaskExecutionMode.FROM_CACHE
      }
      if (isUpToDate) {
        return TaskExecutionMode.UP_TO_DATE
      }
      if (isIncremental) {
        return TaskExecutionMode.INCREMENTAL
      }
      return TaskExecutionMode.FULL
    }

    fun createTaskData(taskFinishEvent: TaskFinishEvent): TaskData {
      val result = taskFinishEvent.result as TaskSuccessResult
      val taskPath = taskFinishEvent.descriptor.taskPath
      val lastColonIndex = taskPath.lastIndexOf(':')
      return TaskData(taskPath.substring(lastColonIndex + 1),
                      taskPath.substring(0, lastColonIndex),
                      PluginData(taskFinishEvent.descriptor.originPlugin),
                      result.endTime - result.startTime,
                      getTaskExecutionMode(result.isFromCache, result.isUpToDate, result.isIncremental),
                      result.executionReasons ?: emptyList())
    }
  }
}
