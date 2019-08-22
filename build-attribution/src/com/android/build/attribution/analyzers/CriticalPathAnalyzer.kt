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
import com.android.build.attribution.data.PluginData
import com.android.build.attribution.data.TaskData
import com.android.ide.common.attribution.AndroidGradlePluginAttributionData
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.task.TaskFinishEvent
import org.gradle.tooling.events.task.TaskOperationDescriptor
import org.gradle.tooling.events.task.TaskSuccessResult
import kotlin.math.max

/**
 * An analyzer for calculating the critical path, that is the path of tasks determining the total build duration.
 */
class CriticalPathAnalyzer(override val warningsFilter: BuildAttributionWarningsFilter) : BuildEventsAnalyzer {
  private val tasksSet = HashSet<TaskData>()
  private val dependenciesMap = HashMap<TaskData, List<TaskData>>()

  val tasksCriticalPath = ArrayList<TaskData>()
  val pluginsCriticalPath = ArrayList<PluginBuildData>()

  var criticalPathDuration = 0L
    private set

  var totalBuildTime = 0L
    private set

  override fun receiveEvent(event: ProgressEvent) {
    if (event is FinishEvent && event.displayName == "Run build succeeded") {
      totalBuildTime = event.result.endTime - event.result.startTime
    }

    if (event is TaskFinishEvent && event.result is TaskSuccessResult) {
      val task = TaskData.createTaskData(event)
      val dependenciesList = ArrayList<TaskData>()

      event.descriptor.dependencies.forEach { dependency ->
        if (dependency is TaskOperationDescriptor) {
          tasksSet.find {
            it.getTaskPath() == dependency.taskPath && it.originPlugin.equals(dependency.originPlugin)
          }?.let {
            dependenciesList.add(it)
          }
        }
      }

      tasksSet.add(task)
      dependenciesMap[task] = dependenciesList
    }
  }

  /**
   * returns the duration of the critical path that starts from task [startTask]
   */
  private fun calculateCriticalPathStartingFromTask(
    startTask: TaskData,
    criticalPathFromTaskMap: MutableMap<TaskData, Long>
  ): Long {
    // Avoid recomputing the subpath critical path if it's already calculated
    criticalPathFromTaskMap[startTask]?.let { return it }

    var criticalPathDuration = 0L
    dependenciesMap[startTask]!!.forEach { dependency ->
      criticalPathDuration = max(criticalPathDuration, calculateCriticalPathStartingFromTask(dependency, criticalPathFromTaskMap))
    }

    // Add task execution time
    criticalPathDuration += startTask.executionTime
    // Memoize the calculated value in the map
    criticalPathFromTaskMap[startTask] = criticalPathDuration

    return criticalPathDuration
  }

  /**
   * We are using dynamic programming to calculate the critical path for the task graph that is a direct acyclic graph.
   *
   * The algorithm should run in linear time of the number of tasks and the number of dependencies in the graph. The memory used is in order
   * of the number of tasks in the graph.
   */
  private fun calculateTasksCriticalPath() {
    val criticalPathFromTaskMap = HashMap<TaskData, Long>()

    var startTask: TaskData? = null
    var currentCriticalPathDuration = -1L

    // Calculate the critical path starting from each task
    tasksSet.forEach {
      val criticalPathFromTask = calculateCriticalPathStartingFromTask(it, criticalPathFromTaskMap)
      if (currentCriticalPathDuration < criticalPathFromTask) {
        currentCriticalPathDuration = criticalPathFromTask
        startTask = it
      }
    }

    criticalPathDuration = currentCriticalPathDuration

    // Construct critical path
    while (startTask != null) {
      tasksCriticalPath.add(startTask!!)

      var nextTask: TaskData? = null
      currentCriticalPathDuration = -1

      dependenciesMap[startTask!!]!!.forEach { dependency ->
        val criticalPathFromTask = criticalPathFromTaskMap[dependency]!!
        if (currentCriticalPathDuration < criticalPathFromTask) {
          currentCriticalPathDuration = criticalPathFromTask
          nextTask = dependency
        }
      }

      startTask = nextTask
    }

    tasksCriticalPath.reverse()
  }

  private fun calculatePluginsCriticalPath() {
    // Group tasks in the critical path by plugin to get the plugins critical path
    val pluginBuildDurationMap = HashMap<PluginData, Long>()
    tasksCriticalPath.forEach { task ->
      val currentDuration = pluginBuildDurationMap.getOrDefault(task.originPlugin, 0L)
      pluginBuildDurationMap[task.originPlugin] = currentDuration + task.executionTime
    }

    pluginBuildDurationMap.forEach { (plugin, duration) -> pluginsCriticalPath.add(PluginBuildData(plugin, duration)) }
    pluginsCriticalPath.sortByDescending { it.buildDuration }
  }

  override fun onBuildStart() {
    tasksSet.clear()
    dependenciesMap.clear()
    tasksCriticalPath.clear()
    pluginsCriticalPath.clear()
    criticalPathDuration = 0
  }

  override fun onBuildSuccess(androidGradlePluginAttributionData: AndroidGradlePluginAttributionData?) {
    calculateTasksCriticalPath()
    calculatePluginsCriticalPath()
    tasksSet.clear()
    dependenciesMap.clear()
    if (androidGradlePluginAttributionData != null) {
      tasksCriticalPath.forEach { it.setTaskType(androidGradlePluginAttributionData.taskNameToClassNameMap[it.taskName]) }
    }
  }

  override fun onBuildFailure() {
    tasksSet.clear()
    dependenciesMap.clear()
  }

  data class PluginBuildData(val plugin: PluginData, val buildDuration: Long)
}
