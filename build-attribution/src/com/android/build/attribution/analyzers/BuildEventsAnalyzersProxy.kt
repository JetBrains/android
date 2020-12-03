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
import com.android.build.attribution.data.AlwaysRunTaskData
import com.android.build.attribution.data.AnnotationProcessorData
import com.android.build.attribution.data.GarbageCollectionData
import com.android.build.attribution.data.PluginBuildData
import com.android.build.attribution.data.PluginConfigurationData
import com.android.build.attribution.data.PluginContainer
import com.android.build.attribution.data.ProjectConfigurationData
import com.android.build.attribution.data.TaskContainer
import com.android.build.attribution.data.TaskData
import com.android.build.attribution.data.TasksSharingOutputData
import org.jetbrains.kotlin.utils.addToStdlib.sumByLong

interface BuildEventsAnalysisResult {
  fun getAnnotationProcessorsData(): List<AnnotationProcessorData>
  fun getNonIncrementalAnnotationProcessorsData(): List<AnnotationProcessorData>
  fun getTotalBuildTimeMs(): Long
  fun getCriticalPathTasks(): List<TaskData>
  fun getTasksDeterminingBuildDuration(): List<TaskData>
  fun getPluginsDeterminingBuildDuration(): List<PluginBuildData>
  /**
   * Total configuration data summed over all subprojects.
   */
  fun getTotalConfigurationData(): ProjectConfigurationData

  /**
   * List of subprojects individual configuration data.
   */
  fun getProjectsConfigurationData(): List<ProjectConfigurationData>
  fun getAlwaysRunTasks(): List<AlwaysRunTaskData>
  fun getNonCacheableTasks(): List<TaskData>
  fun getTasksSharingOutput(): List<TasksSharingOutputData>

  /**
   * List of garbage collection data for this build.
   */
  fun getGarbageCollectionData(): List<GarbageCollectionData>

  /**
   * Total time spent in garbage collection for this build.
   */
  fun getTotalGarbageCollectionTimeMs(): Long
  fun getJavaVersion(): Int?
  fun isGCSettingSet(): Boolean?
}

/**
 * A way of interaction between the build events analyzers and the build attribution manager.
 * Used to fetch the final data from the analyzers after the build is complete.
 */
class BuildEventsAnalyzersProxy(
  warningsFilter: BuildAttributionWarningsFilter,
  taskContainer: TaskContainer,
  pluginContainer: PluginContainer
) : BuildEventsAnalysisResult {
  private val alwaysRunTasksAnalyzer = AlwaysRunTasksAnalyzer(warningsFilter, taskContainer, pluginContainer)
  private val annotationProcessorsAnalyzer = AnnotationProcessorsAnalyzer(warningsFilter, taskContainer, pluginContainer)
  private val criticalPathAnalyzer = CriticalPathAnalyzer(warningsFilter, taskContainer, pluginContainer)
  private val noncacheableTasksAnalyzer = NoncacheableTasksAnalyzer(warningsFilter, taskContainer, pluginContainer)
  private val garbageCollectionAnalyzer = GarbageCollectionAnalyzer(warningsFilter)
  private val projectConfigurationAnalyzer = ProjectConfigurationAnalyzer(warningsFilter, taskContainer, pluginContainer)
  private val tasksConfigurationIssuesAnalyzer = TasksConfigurationIssuesAnalyzer(warningsFilter, taskContainer, pluginContainer)

  fun getBuildEventsAnalyzers(): List<BuildEventsAnalyzer> = listOf(
    alwaysRunTasksAnalyzer,
    annotationProcessorsAnalyzer,
    criticalPathAnalyzer,
    projectConfigurationAnalyzer
  )

  fun getBuildAttributionReportAnalyzers(): List<BuildAttributionReportAnalyzer> = listOf(
    garbageCollectionAnalyzer,
    tasksConfigurationIssuesAnalyzer
  )

  override fun getAnnotationProcessorsData(): List<AnnotationProcessorData> {
    return annotationProcessorsAnalyzer.getAnnotationProcessorsData()
  }

  override fun getNonIncrementalAnnotationProcessorsData(): List<AnnotationProcessorData> {
    return annotationProcessorsAnalyzer.getNonIncrementalAnnotationProcessorsData()
  }

  override fun getTotalBuildTimeMs(): Long {
    return criticalPathAnalyzer.buildFinishedTimestamp - criticalPathAnalyzer.buildStartedTimestamp
  }

  fun getBuildFinishedTimestamp(): Long {
    return criticalPathAnalyzer.buildFinishedTimestamp
  }

  override fun getCriticalPathTasks(): List<TaskData> {
    return criticalPathAnalyzer.tasksDeterminingBuildDuration.filter(TaskData::isOnTheCriticalPath)
  }

  override fun getTasksDeterminingBuildDuration(): List<TaskData> {
    return criticalPathAnalyzer.tasksDeterminingBuildDuration
  }

  override fun getPluginsDeterminingBuildDuration(): List<PluginBuildData> {
    return criticalPathAnalyzer.pluginsDeterminingBuildDuration
  }

  override fun getGarbageCollectionData(): List<GarbageCollectionData> {
    return garbageCollectionAnalyzer.garbageCollectionData
  }

  override fun getTotalGarbageCollectionTimeMs(): Long {
    return getGarbageCollectionData().sumByLong { it.collectionTimeMs }
  }

  override fun getJavaVersion(): Int? {
    return garbageCollectionAnalyzer.javaVersion
  }

  override fun isGCSettingSet(): Boolean? {
    return garbageCollectionAnalyzer.isSettingSet
  }

  override fun getTotalConfigurationData(): ProjectConfigurationData {
    val totalConfigurationTime = projectConfigurationAnalyzer.projectsConfigurationData.sumByLong { it.totalConfigurationTimeMs }

    val totalPluginConfiguration = projectConfigurationAnalyzer.pluginsConfigurationDataMap.map { entry ->
      PluginConfigurationData(entry.key, entry.value)
    }

    val totalConfigurationSteps = projectConfigurationAnalyzer.projectsConfigurationData.flatMap { it.configurationSteps }.groupBy { it.type }.map { entry ->
      ProjectConfigurationData.ConfigurationStep(entry.key, entry.value.sumByLong { it.configurationTimeMs })
    }

    return ProjectConfigurationData("Total Configuration Data", totalConfigurationTime, totalPluginConfiguration, totalConfigurationSteps)
  }

  override fun getProjectsConfigurationData(): List<ProjectConfigurationData> {
    return projectConfigurationAnalyzer.projectsConfigurationData
  }

  override fun getAlwaysRunTasks(): List<AlwaysRunTaskData> {
    return alwaysRunTasksAnalyzer.alwaysRunTasks
  }

  override fun getNonCacheableTasks(): List<TaskData> {
    return noncacheableTasksAnalyzer.noncacheableTasks
  }

  override fun getTasksSharingOutput(): List<TasksSharingOutputData> {
    return tasksConfigurationIssuesAnalyzer.tasksSharingOutput
  }
}
