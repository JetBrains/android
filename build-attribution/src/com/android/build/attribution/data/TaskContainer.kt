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

import com.android.ide.common.attribution.AndroidGradlePluginAttributionData
import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import org.gradle.tooling.events.task.TaskFinishEvent

/**
 * A cache object to unify [TaskData] objects and share them between different analyzers.
 */
data class TaskContainer(private val taskCache: Cache<String, TaskData> = CacheBuilder.newBuilder().build<String, TaskData>()) {

  fun getTask(taskPath: String): TaskData? {
    return taskCache.getIfPresent(taskPath)
  }

  fun getTask(event: TaskFinishEvent): TaskData {
    return taskCache.get(event.descriptor.taskPath) {
      TaskData.createTaskData(event)
    }
  }

  fun updateTasksData(androidGradlePluginAttributionData: AndroidGradlePluginAttributionData) {
    // Set the task type
    taskCache.asMap().values.forEach { task ->
      task.setTaskType(androidGradlePluginAttributionData.taskNameToClassNameMap[task.taskName])
    }
  }

  fun clear() {
    taskCache.invalidateAll()
  }
}
