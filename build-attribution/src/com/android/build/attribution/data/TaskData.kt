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

import com.android.ide.common.attribution.TaskCategory
import org.gradle.tooling.events.task.TaskFinishEvent
import org.gradle.tooling.events.task.TaskSuccessResult

/**
 * Represents key information about TaskData. Used in comparisons and as a key in the hash table
 *
 * @param taskName the name of the executed task, ex: mergeDebugResources
 * @param projectPath the path of the project that this task was executed in
 * @param originPlugin the plugin that registered the task
 */
data class TaskDataId(val taskName: String,
                      val projectPath: String,
                      val originPlugin: PluginData)

/**
 * Represents an executed task in a gradle build.
 * A task is uniquely identified by a combination of [taskName], [projectPath] and [originPlugin].
 *
 * @param taskId stores key information and is used in comparisons
 * @param executionStartTime the timestamp when the task started executing
 * @param executionEndTime the timestamp when the task finished executing
 * @param executionMode whether the task was fully executed, incrementally executed, fetch from cache, or was up to date
 * @param executionReasons the reasons why the task needed to run
 */
class TaskData(private val taskId: TaskDataId,
               val executionStartTime: Long,
               val executionEndTime: Long,
               val executionMode: TaskExecutionMode,
               val executionReasons: List<String>) {

  constructor(taskName: String,
              projectPath: String,
              originPlugin: PluginData,
              executionStartTime: Long,
              executionEndTime: Long,
              executionMode: TaskExecutionMode,
              executionReasons: List<String>) : this(TaskDataId(taskName, projectPath, originPlugin),
                                                     executionStartTime,
                                                     executionEndTime,
                                                     executionMode,
                                                     executionReasons)

  val taskName: String
    get() = this.taskId.taskName

  val projectPath: String
    get() = this.taskId.projectPath

  val originPlugin: PluginData
    get() = this.taskId.originPlugin

  /**
   * The execution duration of the task in milliseconds.
   */
  val executionTime: Long = executionEndTime - executionStartTime
  /**
   * Indicates whether the task is on the critical path of the current build.
   * This field is set later at the end of the build as it's determined by the critical path analyzer
   */
  var isOnTheCriticalPath: Boolean = false

  enum class TaskExecutionMode {
    FROM_CACHE,
    UP_TO_DATE,
    INCREMENTAL,
    FULL
  }

  /**
   * The class name of the task.
   * This field is set later at the end of the build as it's coming from AGP.
   */
  var taskType: String = UNKNOWN_TASK_TYPE
    private set

  /**
   * Primary execution function of the task.
   */
  var primaryTaskCategory: TaskCategory = TaskCategory.UNKNOWN
    private set

  /**
   * All other execution functions of the task.
   */
  var secondaryTaskCategories: List<TaskCategory> = mutableListOf()

  fun setTaskType(taskType: String?) {
    if (taskType != null) {
      this.taskType = taskType
    }
  }

  fun getTaskPath(): String {
    return "$projectPath:$taskName"
  }

  fun setTaskCategories(primaryTaskCategory: TaskCategory, secondaryTaskCategories: List<TaskCategory>) {
    setPrimaryTaskCategory(primaryTaskCategory)
    setSecondaryTaskCategory(secondaryTaskCategories)
  }

  private fun setPrimaryTaskCategory(primaryTaskCategory: TaskCategory) {
    this.primaryTaskCategory = when {
      isKotlinCompilationTask() -> TaskCategory.KOTLIN
      isJavaCompilationTask() || originPlugin.isJavaPlugin() -> TaskCategory.JAVA
      primaryTaskCategory != TaskCategory.UNKNOWN -> primaryTaskCategory
      originPlugin.isAndroidPlugin() -> TaskCategory.MISC
      originPlugin.isKotlinPlugin() -> TaskCategory.KOTLIN
      originPlugin.isGradlePlugin() -> TaskCategory.GRADLE
      else -> primaryTaskCategory
    }
  }

  private fun setSecondaryTaskCategory(secondaryTaskCategories: List<TaskCategory>) {
    this.secondaryTaskCategories = when {
      isJavaCompilationTask() || isKotlinCompilationTask() -> listOf(TaskCategory.COMPILATION)
      else -> secondaryTaskCategories
    }
  }

  fun isJavaCompilationTask(): Boolean {
    return taskType == "org.gradle.api.tasks.compile.JavaCompile"
  }
  fun isKotlinCompilationTask(): Boolean {
    return isKaptTask() || taskType == "org.jetbrains.kotlin.gradle.tasks.KotlinCompile"
  }

  override fun equals(other: Any?): Boolean {
    return other is TaskData &&
           taskId == other.taskId
  }

  override fun hashCode(): Int {
    return taskId.hashCode()
  }

  override fun toString(): String {
    return "TaskData(taskPath='${getTaskPath()}')"
  }

  fun isKaptTask(): Boolean {
    return taskType == "org.jetbrains.kotlin.gradle.internal.KaptTask" ||
           taskType == "org.jetbrains.kotlin.gradle.internal.KaptWithKotlincTask" ||
           taskType == "org.jetbrains.kotlin.gradle.internal.KaptWithoutKotlincTask" ||
           taskType == "org.jetbrains.kotlin.gradle.internal.KaptGenerateStubsTask"
  }

  fun isAndroidTask(): Boolean {
    return taskType.startsWith("com.android.build.gradle.")
  }

  fun isGradleTask(): Boolean {
    return taskType.startsWith("org.gradle.")
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

    fun createTaskData(taskFinishEvent: TaskFinishEvent, pluginContainer: PluginContainer): TaskData {
      val result = taskFinishEvent.result as TaskSuccessResult
      val taskPath = taskFinishEvent.descriptor.taskPath
      val lastColonIndex = taskPath.lastIndexOf(':')
      return TaskData(taskPath.substring(lastColonIndex + 1),
                      taskPath.substring(0, lastColonIndex),
                      pluginContainer.getPlugin(taskFinishEvent.descriptor.originPlugin, taskPath.substring(0, lastColonIndex)),
                      result.startTime,
                      result.endTime,
                      getTaskExecutionMode(result.isFromCache, result.isUpToDate, result.isIncremental),
                      result.executionReasons ?: emptyList())
    }
  }
}
