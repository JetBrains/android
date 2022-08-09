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
import com.android.build.attribution.data.BuildRequestHolder
import com.android.build.attribution.data.PluginBuildData
import com.android.build.attribution.data.TaskData
import com.android.build.attribution.data.TaskCategoryBuildData
import com.android.build.attribution.ui.data.BuildAttributionReportUiData
import com.android.build.attribution.ui.data.BuildSummary
import com.android.build.attribution.ui.data.ConfigurationUiData
import com.android.build.attribution.ui.data.CriticalPathPluginUiData
import com.android.build.attribution.ui.data.CriticalPathPluginsUiData
import com.android.build.attribution.ui.data.CriticalPathTaskCategoryUiData
import com.android.build.attribution.ui.data.CriticalPathTaskCategoriesUiData
import com.android.build.attribution.ui.data.CriticalPathTasksUiData
import com.android.build.attribution.ui.data.IssueLevel
import com.android.build.attribution.ui.data.TimeWithPercentage
import com.android.build.attribution.ui.displayName
import com.android.build.attribution.ui.getTaskCategoryInfo
import com.android.ide.common.attribution.TaskCategory
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker
import org.jetbrains.kotlin.utils.addToStdlib.sumByLong

/**
 * A Builder class for a data structure holding the data gathered by Gradle build analyzers.
 * The data structure of the report is described in UiDataModel.kt
 */
class BuildAttributionReportBuilder(
  val buildAnalysisResult: BuildEventsAnalysisResult,
  val buildFinishedTimestamp: Long,
  val buildRequestHolder: BuildRequestHolder
) {

  private val criticalPathDurationMs: Long = buildAnalysisResult.getTasksDeterminingBuildDuration().sumByLong { it.executionTime }
  private val issueUiDataContainer: TaskIssueUiDataContainer = TaskIssueUiDataContainer(buildAnalysisResult)
  private val taskUiDataContainer: TaskUiDataContainer = TaskUiDataContainer(buildAnalysisResult, issueUiDataContainer, criticalPathDurationMs)

  fun build(): BuildAttributionReportUiData {
    issueUiDataContainer.populate(taskUiDataContainer)
    val pluginConfigurationTimeReport = ConfigurationTimesUiDataBuilder(buildAnalysisResult).build()
    val buildSummary = createBuildSummary(pluginConfigurationTimeReport)
    return object : BuildAttributionReportUiData {
      override val successfulBuild: Boolean = true
      override val buildRequest: GradleBuildInvoker.Request
        get() = buildRequestHolder.buildRequest
      override val buildSummary: BuildSummary = buildSummary
      override val criticalPathTasks = createCriticalPathTasks(buildSummary.criticalPathDuration)
      override val criticalPathPlugins = createCriticalPathPlugins(buildSummary.criticalPathDuration)
      override val criticalPathTaskCategories = createCriticalPathTaskCategories(buildSummary.criticalPathDuration)
      override val issues = issueUiDataContainer.allIssueGroups()
      override val configurationTime = pluginConfigurationTimeReport
      override val annotationProcessors = AnnotationProcessorsReportBuilder(buildAnalysisResult).build()
      override val confCachingData = buildAnalysisResult.getConfigurationCachingCompatibility()
      override val jetifierData = buildAnalysisResult.getJetifierUsageResult()
      override val downloadsData = buildAnalysisResult.getDownloadsAnalyzerResult()
    }
  }

  private fun createBuildSummary(pluginConfigurationTimeReport: ConfigurationUiData) = object : BuildSummary {
    override val buildFinishedTimestamp = this@BuildAttributionReportBuilder.buildFinishedTimestamp
    override val totalBuildDuration = TimeWithPercentage(buildAnalysisResult.getTotalBuildTimeMs(), buildAnalysisResult.getTotalBuildTimeMs())
    override val criticalPathDuration = TimeWithPercentage(criticalPathDurationMs, buildAnalysisResult.getTotalBuildTimeMs())
    override val configurationDuration = pluginConfigurationTimeReport.totalConfigurationTime
    override val garbageCollectionTime =
      TimeWithPercentage(buildAnalysisResult.getTotalGarbageCollectionTimeMs(), buildAnalysisResult.getTotalBuildTimeMs())
    override val javaVersionUsed = buildAnalysisResult.getJavaVersion()
    override val isGarbageCollectorSettingSet = buildAnalysisResult.isGCSettingSet()
  }

  private fun createCriticalPathTasks(criticalPathDuration: TimeWithPercentage) = object : CriticalPathTasksUiData {
    override val criticalPathDuration = criticalPathDuration
    override val miscStepsTime = criticalPathDuration.supplement()
    override val tasks = buildAnalysisResult.getTasksDeterminingBuildDuration()
      .map { taskUiDataContainer.getByTaskData(it) }
      .sortedByDescending { it.executionTime }
    override val warningCount = tasks.flatMap { it.issues }.count { it.type.level == IssueLevel.WARNING }
    override val infoCount = tasks.flatMap { it.issues }.count { it.type.level == IssueLevel.INFO }
  }

  private fun createCriticalPathPlugins(criticalPathDuration: TimeWithPercentage): CriticalPathPluginsUiData {
    val taskByPlugin = buildAnalysisResult.getTasksDeterminingBuildDuration().groupBy { it.originPlugin }
    return object : CriticalPathPluginsUiData {
      override val criticalPathDuration = criticalPathDuration
      override val miscStepsTime = criticalPathDuration.supplement()
      override val entries = buildAnalysisResult.getPluginsDeterminingBuildDuration()
        .map {
          createCriticalPathPluginUiData(taskByPlugin[it.plugin].orEmpty(), it, criticalPathDuration)
        }
        .sortedByDescending { it.criticalPathDuration }
      override val warningCount = entries.sumOf { it.warningCount }
      override val infoCount = entries.sumOf { it.infoCount }
    }
  }

  private fun createCriticalPathPluginUiData(
    criticalPathTasks: List<TaskData>,
    pluginCriticalPathBuildData: PluginBuildData,
    totalCriticalPathDuration: TimeWithPercentage
  ) = object : CriticalPathPluginUiData {
    override val name = pluginCriticalPathBuildData.plugin.displayName
    override val criticalPathDuration = TimeWithPercentage(pluginCriticalPathBuildData.buildDuration, totalCriticalPathDuration.timeMs)
    override val criticalPathTasks = criticalPathTasks
      .map { taskUiDataContainer.getByTaskData(it) }
      .sortedByDescending { it.executionTime }
    override val issues = issueUiDataContainer.pluginIssueGroups(pluginCriticalPathBuildData.plugin)
    override val warningCount = issues.sumBy { it.warningCount }
    override val infoCount = issues.sumBy { it.infoCount }
  }

  private fun createCriticalPathTaskCategories(criticalPathDuration: TimeWithPercentage): CriticalPathTaskCategoriesUiData {
    val taskCategoryBuildDurationMap = HashMap<TaskCategory, Long>()
    buildAnalysisResult.getTasksDeterminingBuildDuration().forEach { taskData ->
      val taskCategory = taskData.primaryTaskCategory
      val currentDurationLabel = taskCategoryBuildDurationMap.getOrDefault(taskCategory, 0L)
      taskCategoryBuildDurationMap[taskCategory] = currentDurationLabel + taskData.executionTime
    }
    val taskCategoriesDeterminingBuildDuration = mutableListOf<TaskCategoryBuildData>()
    taskCategoryBuildDurationMap.forEach { (taskCategory, duration) ->
      taskCategoriesDeterminingBuildDuration.add(TaskCategoryBuildData(taskCategory, duration))
    }
    val taskByTaskCategory = buildAnalysisResult.getTasksDeterminingBuildDuration().groupBy { it.primaryTaskCategory }
    return object : CriticalPathTaskCategoriesUiData {
      override val criticalPathDuration = criticalPathDuration
      override val miscStepsTime = criticalPathDuration.supplement()
      override val entries = taskCategoriesDeterminingBuildDuration.map {
        createCriticalPathTaskCategoryUiData(taskByTaskCategory[it.taskCategory].orEmpty(), it, criticalPathDuration)
      }.sortedByDescending { it.criticalPathDuration }
      override val warningCount = entries.sumOf { it.warningCount }
      override val infoCount = entries.sumOf { it.infoCount }
    }
  }

  private fun createCriticalPathTaskCategoryUiData(
    criticalPathTasks: List<TaskData>,
    taskCategoryCriticalPathBuildData: TaskCategoryBuildData,
    totalCriticalPathDuration: TimeWithPercentage
  ) = object : CriticalPathTaskCategoryUiData {
    override val name = taskCategoryCriticalPathBuildData.taskCategory.displayName()
    override val criticalPathDuration = TimeWithPercentage(taskCategoryCriticalPathBuildData.buildDuration, totalCriticalPathDuration.timeMs)
    override val criticalPathTasks = criticalPathTasks
      .map { taskUiDataContainer.getByTaskData(it) }
      .sortedByDescending { it.executionTime }
    override val issues = issueUiDataContainer.taskCategoryIssueGroups(taskCategoryCriticalPathBuildData.taskCategory)
    override val warningCount = issues.sumBy { it.warningCount }
    override val infoCount = issues.sumBy { it.infoCount }
    override val taskCategoryInfo: String
      get() = taskCategoryCriticalPathBuildData.taskCategory.getTaskCategoryInfo()
  }

}