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

import com.android.build.attribution.data.PluginBuildData
import com.android.build.attribution.data.PluginContainer
import com.android.build.attribution.data.PluginData
import com.android.build.attribution.data.StudioProvidedInfo
import com.android.build.attribution.data.TaskContainer
import com.android.build.attribution.data.TaskData
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.task.TaskFinishEvent
import org.gradle.tooling.events.task.TaskOperationDescriptor
import org.gradle.tooling.events.task.TaskSuccessResult
import kotlin.math.max

/**
 * An analyzer for calculating the critical path, that is the path of tasks determining the total build duration.
 */
class CriticalPathAnalyzer(
  private val taskContainer: TaskContainer,
  private val pluginContainer: PluginContainer
) : BaseAnalyzer<CriticalPathAnalyzer.Result>(),
    BuildEventsAnalyzer,
    PostBuildProcessAnalyzer {
  private val tasksSet = HashSet<TaskData>()

  /**
   * Contains for each task, a list of tasks that this task depends on.
   */
  private val dependenciesMap = HashMap<TaskData, List<TaskData>>()

  private val tasksDeterminingBuildDuration = ArrayList<TaskData>()
  private val pluginsDeterminingBuildDuration = ArrayList<PluginBuildData>()

  private var buildStartedTimestamp = Long.MAX_VALUE
  private var buildFinishedTimestamp = Long.MIN_VALUE

  override fun receiveEvent(event: ProgressEvent) {
    // Since we stopped listening to generic events, we don't get build finished event. But we can calculate the build time from the start
    // of the first received event and the end of the last received event.
    if (event is FinishEvent) {
      buildStartedTimestamp = buildStartedTimestamp.coerceAtMost(event.result.startTime)
      buildFinishedTimestamp = buildFinishedTimestamp.coerceAtLeast(event.result.endTime)
    }

    if (event is TaskFinishEvent && event.result is TaskSuccessResult) {
      val task = taskContainer.getTask(event, pluginContainer)
      val dependenciesList = ArrayList<TaskData>()

      event.descriptor.dependencies.forEach { dependency ->
        if (dependency is TaskOperationDescriptor) {
          taskContainer.getTask(dependency.taskPath)?.let {
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
  private fun calculateTasksCriticalPathBasedOnDependencies(): List<TaskData> {
    val tasksCriticalPath = ArrayList<TaskData>()
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
    return tasksCriticalPath
  }

  /**
   * Runs binary search to find the first task that starts at or after the given [timestamp], if there is no such task returns -1.
   * [tasks] should be sorted in non decreasing order of execution start time.
   */
  private fun getIndexOfFirstTaskStartingAtOrAfterTimestamp(timestamp: Long, tasks: List<TaskData>, searchStartIndex: Int): Int {
    if (tasks.last().executionStartTime < timestamp) {
      return -1
    }

    var left = searchStartIndex
    var right = tasks.size - 1

    while (left < right) {
      val middle = left + (right - left) / 2
      if (tasks[middle].executionStartTime < timestamp) {
        left = middle + 1
      }
      else {
        right = middle
      }
    }

    return left
  }

  /**
   * Using dynamic programming to calculate tne critical path without having to create the graph, resulting in an O(N logN) runtime where
   * N is the number of elements in [tasks]
   *
   * [tasks] should be sorted in non decreasing order of execution start time.
   *
   * The algorithm works as follows:
   *
   * > initialize maxCriticalPathStartIndexInSuffix_i with i
   * > iterate through tasks from the end and for each task X,
   * >> find the first task Y that starts after X ends using binary search
   * >> maxCriticalPathStartIndexInSuffix_Y should contain the best choice for X
   * >> update criticalPathFromTask_X and bestChoiceIndex_X
   * >> check if criticalPathFromTask_X is less than the best critical path in the suffix [i + 1]
   * >>> if it's true then maxCriticalPathStartIndexInSuffix_i should point to maxCriticalPathStartIndexInSuffix_i+1
   * > construct and return the critical path
   */
  private fun calculateTasksCriticalPathBasedOnExecution(tasks: List<TaskData>): List<TaskData> {
    if (tasks.isEmpty()) {
      return tasks
    }

    // criticalPathFromTask[i] is the total execution time of the critical path that starts from task i
    val criticalPathFromTask = tasks.map { it.executionTime }.toMutableList()

    // maxCriticalPathStartIndexInSuffix[i] is the index of maximum length of the critical path if we started from a task in the range
    // [i, tasks.size - 1]
    val maxCriticalPathStartIndexInSuffix = List(tasks.size) { it }.toMutableList()

    // bestChoiceIndex[i] is the index of the next task in the max critical path that starts from task i
    // -1 means there are no tasks starting after this task finishes
    val bestChoiceIndex = List(tasks.size) { -1 }.toMutableList()

    for (i in tasks.size - 2 downTo 0) {
      // The index of the first task that starts at or after this task finishes
      val firstTaskIndex = getIndexOfFirstTaskStartingAtOrAfterTimestamp(tasks[i].executionEndTime, tasks, i + 1)

      if (firstTaskIndex != -1) {
        bestChoiceIndex[i] = maxCriticalPathStartIndexInSuffix[firstTaskIndex]
        criticalPathFromTask[i] = tasks[i].executionTime + criticalPathFromTask[bestChoiceIndex[i]]
      }

      if (criticalPathFromTask[i] < criticalPathFromTask[maxCriticalPathStartIndexInSuffix[i + 1]]) {
        maxCriticalPathStartIndexInSuffix[i] = maxCriticalPathStartIndexInSuffix[i + 1]
      }
    }

    // Construct the critical path
    val criticalPath = ArrayList<TaskData>()
    var index = maxCriticalPathStartIndexInSuffix[0]

    while (index != -1) {
      criticalPath.add(tasks[index])
      index = bestChoiceIndex[index]
    }
    return criticalPath
  }

  /**
   * Returns the tasks that are executed completely within the range [startTime, endTime].
   *
   * @param taskListSortedByStartTimeIterator is an iterator over the list of tasks sorted by start time.
   */
  private fun getTasksStrictlyInTimeRange(startTime: Long,
                                          endTime: Long,
                                          taskListSortedByStartTimeIterator: ListIterator<TaskData>): List<TaskData> {
    val tasksInBetween = ArrayList<TaskData>()
    while (taskListSortedByStartTimeIterator.hasNext()) {
      val currentTask = taskListSortedByStartTimeIterator.next()

      // currentTask starts before the given time range
      if (currentTask.executionStartTime < startTime) {
        continue
      }
      // currentTask starts after the given time range
      // Decrement the iterator so we don't consume this task as the iterator will be reused
      if (currentTask.executionStartTime >= endTime) {
        taskListSortedByStartTimeIterator.previous()
        break
      }
      // At this point currentTasks starts within the given range
      // check if currentTask ends after the given time range
      if (currentTask.executionEndTime > endTime) {
        continue
      }
      // currentTask is executed completely within the given time range
      // Critical path tasks are already added, this is to eliminate duplicates
      if (!currentTask.isOnTheCriticalPath) {
        tasksInBetween.add(currentTask)
      }
    }
    return tasksInBetween
  }

  /**
   * Returns the critical path of tasks that are executed completely within the range [startTime, endTime].
   *
   * @param taskListSortedByStartTimeIterator is an iterator over the list of tasks sorted by start time.
   */
  private fun getCriticalPathOfTasksStrictlyInTimeRange(startTime: Long,
                                                        endTime: Long,
                                                        taskListSortedByStartTimeIterator: ListIterator<TaskData>): List<TaskData> {
    return calculateTasksCriticalPathBasedOnExecution(getTasksStrictlyInTimeRange(startTime, endTime, taskListSortedByStartTimeIterator))
  }

  /**
   *
   * Given the critical path calculated from the dependency graph [tasksCriticalPath]
   *
   * For each two consecutive tasks in the critical path, find the tasks that were executed fully in between the end of the first task and
   * the start of the second task, from these tasks calculate a new critical path from the execution graph (which is a graph where task A
   * depends on task B if and only if the start time of task A >= the end time of task B)
   *
   * Do the last step for tasks that were executed fully before the start of the first task in the critical path and after the end of the
   * last task in the critical path
   *
   * The original critical path and the critical paths of the tasks in between will be the tasks determining build duration.
   *
   * The overall runtime of O(N logN + M), where N is the number of tasks and M is the number of dependencies (i.e. edges) in the dependency
   * graph.
   */
  private fun calculateTasksDeterminingBuildDuration(tasksCriticalPath: List<TaskData>) {
    val tasksDeterminingBuildDurationList = ArrayList<TaskData>()

    val taskListSortedByStartTime = tasksSet.sortedBy { it.executionStartTime }

    // Since the critical path tasks are not intersecting, and we iterate through them in start time order, we are able to reuse the
    // iterator across all queries without having to iterate through the whole list each time
    val listIterator = taskListSortedByStartTime.listIterator()

    if (tasksCriticalPath.isEmpty()) {
      tasksDeterminingBuildDuration.addAll(calculateTasksCriticalPathBasedOnExecution(
        taskListSortedByStartTime).filterNot { it.executionMode == TaskData.TaskExecutionMode.UP_TO_DATE })
      return
    }

    tasksCriticalPath.forEach { task ->
      task.isOnTheCriticalPath = true
    }

    // get critical path of tasks before the start time of the first task in the critical path
    tasksDeterminingBuildDurationList.addAll(
      getCriticalPathOfTasksStrictlyInTimeRange(0, tasksCriticalPath.first().executionStartTime, listIterator))

    for (i in 0 until tasksCriticalPath.size - 1) {
      val previousCriticalPathTask = tasksCriticalPath[i]
      val nextCriticalPathTask = tasksCriticalPath[i + 1]
      tasksDeterminingBuildDurationList.add(previousCriticalPathTask)

      // get critical path of tasks in between the end time of the previous critical path task and the start time of the next critical path
      // task
      tasksDeterminingBuildDurationList.addAll(
        getCriticalPathOfTasksStrictlyInTimeRange(previousCriticalPathTask.executionEndTime, nextCriticalPathTask.executionStartTime,
                                                  listIterator))
    }
    tasksDeterminingBuildDurationList.add(tasksCriticalPath.last())

    // get critical path of tasks after the end time of the last task in the critical path
    tasksDeterminingBuildDurationList.addAll(
      getCriticalPathOfTasksStrictlyInTimeRange(tasksCriticalPath.last().executionEndTime,
                                                Long.MAX_VALUE,
                                                listIterator))

    tasksDeterminingBuildDuration.addAll(
      tasksDeterminingBuildDurationList.filterNot { it.executionMode == TaskData.TaskExecutionMode.UP_TO_DATE })
  }

  private fun calculatePluginsDeterminingBuildDuration() {
    // Group tasks in the critical path by plugin to get the plugins critical path
    val pluginBuildDurationMap = HashMap<PluginData, Long>()
    tasksDeterminingBuildDuration.forEach { task ->
      val currentDuration = pluginBuildDurationMap.getOrDefault(task.originPlugin, 0L)
      pluginBuildDurationMap[task.originPlugin] = currentDuration + task.executionTime
    }

    pluginBuildDurationMap.forEach { (plugin, duration) ->
      pluginsDeterminingBuildDuration.add(PluginBuildData(plugin, duration))
    }
    pluginsDeterminingBuildDuration.sortByDescending { it.buildDuration }
  }

  override fun cleanupTempState() {
    tasksSet.clear()
    dependenciesMap.clear()
    tasksDeterminingBuildDuration.clear()
    pluginsDeterminingBuildDuration.clear()
    buildStartedTimestamp = Long.MAX_VALUE
    buildFinishedTimestamp = Long.MIN_VALUE
  }

  override fun runPostBuildAnalysis(analyzersResult: BuildEventsAnalyzersProxy, studioProvidedInfo: StudioProvidedInfo) {
    ensureResultCalculated()
  }

  override fun calculateResult(): Result {
    calculateTasksDeterminingBuildDuration(calculateTasksCriticalPathBasedOnDependencies())
    calculatePluginsDeterminingBuildDuration()
    return Result(
      tasksDeterminingBuildDuration.toList(),
      pluginsDeterminingBuildDuration.toList(),
      buildStartedTimestamp,
      buildFinishedTimestamp
    )
  }

  data class Result(
    val tasksDeterminingBuildDuration: List<TaskData>,
    val pluginsDeterminingBuildDuration: List<PluginBuildData>,
    val buildStartedTimestamp: Long,
    val buildFinishedTimestamp: Long
  ) : AnalyzerResult
}
