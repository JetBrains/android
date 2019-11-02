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

import com.android.build.attribution.analyzers.BuildEventsAnalyzersResultsProvider
import com.android.build.attribution.analyzers.CriticalPathAnalyzer
import com.android.build.attribution.data.TaskData
import com.android.build.attribution.ui.data.BuildAttributionReportUiData
import com.android.build.attribution.ui.data.BuildSummary
import com.android.build.attribution.ui.data.CriticalPathPluginUiData
import com.android.build.attribution.ui.data.CriticalPathPluginsUiData
import com.android.build.attribution.ui.data.CriticalPathTasksUiData
import com.android.build.attribution.ui.data.TimeWithPercentage


/**
 * A Builder class for a data structure holding the data gathered by Gradle build analyzers.
 * The data structure of the report is described in UiDataModel.kt
 */
class BuildAttributionReportBuilder(
  val analyzersProxy: BuildEventsAnalyzersResultsProvider,
  val buildFinishedTimestamp: Long
) {

  private val taskUiDataContainer: TaskUiDataContainer = TaskUiDataContainer(analyzersProxy)

  fun build(): BuildAttributionReportUiData {
    val buildSummary = createBuildSummary()
    return object : BuildAttributionReportUiData {
      override val buildSummary: BuildSummary = buildSummary
      override val criticalPathTasks = createCriticalPathTasks(buildSummary.criticalPathDuration)
      override val criticalPathPlugins = createCriticalPathPlugins(buildSummary.criticalPathDuration)
    }
  }

  private fun createBuildSummary() = object : BuildSummary {
    override val buildFinishedTimestamp = this@BuildAttributionReportBuilder.buildFinishedTimestamp
    override val totalBuildDuration = TimeWithPercentage(analyzersProxy.getTotalBuildTime(), analyzersProxy.getTotalBuildTime())
    override val criticalPathDuration = TimeWithPercentage(analyzersProxy.getCriticalPathDuration(), analyzersProxy.getTotalBuildTime())
  }


  private fun createCriticalPathTasks(criticalPathDuration: TimeWithPercentage) = object : CriticalPathTasksUiData {
    override val criticalPathDuration = criticalPathDuration
    override val miscStepsTime = criticalPathDuration.supplement()
    override val tasks = analyzersProxy.getTasksCriticalPath()
      .map { taskUiDataContainer.getByTaskData(it) }
      .sortedByDescending { it.executionTime }
  }

  private fun createCriticalPathPlugins(criticalPathDuration: TimeWithPercentage): CriticalPathPluginsUiData {
    val taskByPlugin = analyzersProxy.getTasksCriticalPath().groupBy { it.originPlugin }
    return object : CriticalPathPluginsUiData {
      override val criticalPathDuration = criticalPathDuration
      override val miscStepsTime = criticalPathDuration.supplement()
      override val plugins = analyzersProxy.getPluginsCriticalPath()
        .map {
          createCriticalPathPluginUiData(taskByPlugin[it.plugin].orEmpty(), it)
        }
        .sortedByDescending { it.criticalPathDuration }
    }
  }

  private fun createCriticalPathPluginUiData(
    criticalPathTasks: List<TaskData>,
    pluginCriticalPathBuildData: CriticalPathAnalyzer.PluginBuildData
  ) = object : CriticalPathPluginUiData {
    override val name = pluginCriticalPathBuildData.plugin.displayName
    override val criticalPathDuration = TimeWithPercentage(pluginCriticalPathBuildData.buildDuration, analyzersProxy.getTotalBuildTime())
    override val criticalPathTasks = criticalPathTasks.map { taskUiDataContainer.getByTaskData(it) }
  }
}