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
package com.android.build.attribution

import com.android.build.attribution.analyzers.AlwaysRunTasksAnalyzer
import com.android.build.attribution.analyzers.AnnotationProcessorsAnalyzer
import com.android.build.attribution.analyzers.BuildEventsAnalysisResult
import com.android.build.attribution.analyzers.ConfigurationCacheCompatibilityTestFlow
import com.android.build.attribution.analyzers.ConfigurationCachingCompatibilityProjectResult
import com.android.build.attribution.analyzers.ConfigurationCachingTurnedOn
import com.android.build.attribution.analyzers.CriticalPathAnalyzer
import com.android.build.attribution.analyzers.DownloadsAnalyzer
import com.android.build.attribution.analyzers.GarbageCollectionAnalyzer
import com.android.build.attribution.analyzers.JetifierUsageAnalyzerResult
import com.android.build.attribution.analyzers.NoncacheableTasksAnalyzer
import com.android.build.attribution.analyzers.ProjectConfigurationAnalyzer
import com.android.build.attribution.analyzers.TaskCategoryWarningsAnalyzer
import com.android.build.attribution.analyzers.TasksConfigurationIssuesAnalyzer
import com.android.build.attribution.data.AlwaysRunTaskData
import com.android.build.attribution.data.AnnotationProcessorData
import com.android.build.attribution.data.GarbageCollectionData
import com.android.build.attribution.data.PluginBuildData
import com.android.build.attribution.data.PluginConfigurationData
import com.android.build.attribution.data.PluginData
import com.android.build.attribution.data.ProjectConfigurationData
import com.android.build.attribution.data.TaskData
import com.android.build.attribution.data.TasksSharingOutputData
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker
import kotlinx.collections.immutable.toImmutableMap
import org.jetbrains.kotlin.utils.addToStdlib.sumByLong

sealed interface AbstractBuildAnalysisResult {
  fun getBuildSessionID(): String
}

data class BuildAnalysisResults(
  private val buildRequestData: GradleBuildInvoker.Request.RequestData,
  private val annotationProcessorAnalyzerResult: AnnotationProcessorsAnalyzer.Result,
  private val alwaysRunTasksAnalyzerResult: AlwaysRunTasksAnalyzer.Result,
  private val criticalPathAnalyzerResult: CriticalPathAnalyzer.Result,
  private val noncacheableTasksAnalyzerResult: NoncacheableTasksAnalyzer.Result,
  private val garbageCollectionAnalyzerResult: GarbageCollectionAnalyzer.Result,
  private val projectConfigurationAnalyzerResult: ProjectConfigurationAnalyzer.Result,
  private val tasksConfigurationIssuesAnalyzerResult: TasksConfigurationIssuesAnalyzer.Result,
  private val configurationCachingCompatibilityAnalyzerResult: ConfigurationCachingCompatibilityProjectResult,
  private val jetifierUsageAnalyzerResult: JetifierUsageAnalyzerResult,
  private val downloadsAnalyzerResult: DownloadsAnalyzer.Result,
  private val taskCategoryWarningsAnalyzerResult: TaskCategoryWarningsAnalyzer.Result,
  private val buildSessionID: String,
  private val taskMap: Map<String, TaskData>,
  private val pluginMap: Map<String, PluginData>
) : AbstractBuildAnalysisResult, BuildEventsAnalysisResult {

  @Override
  override fun getBuildRequestData() : GradleBuildInvoker.Request.RequestData {
    return buildRequestData
  }

  fun getAnnotationProcessorAnalyzerResult(): AnnotationProcessorsAnalyzer.Result {
    return annotationProcessorAnalyzerResult
  }

  fun getTaskMap(): Map<String, TaskData> {
    return taskMap
  }

  fun getPluginMap(): Map<String, PluginData> {
    return pluginMap
  }

  fun getProjectConfigurationAnalyzerResult(): ProjectConfigurationAnalyzer.Result {
    return projectConfigurationAnalyzerResult
  }

  fun getCriticalPathAnalyzerResult(): CriticalPathAnalyzer.Result {
    return criticalPathAnalyzerResult
  }
  fun getBuildStartedTimestamp(): Long {
    return criticalPathAnalyzerResult.buildStartedTimestamp
  }

  fun getGarbageCollectionAnalyzerResult(): GarbageCollectionAnalyzer.Result {
    return garbageCollectionAnalyzerResult
  }

  @Override
  override fun getBuildFinishedTimestamp() : Long{
    return criticalPathAnalyzerResult.buildFinishedTimestamp
  }

  override fun getAnnotationProcessorsData(): List<AnnotationProcessorData> {
    return annotationProcessorAnalyzerResult.annotationProcessorsData
  }

  override fun getNonIncrementalAnnotationProcessorsData(): List<AnnotationProcessorData> {
    return annotationProcessorAnalyzerResult.nonIncrementalAnnotationProcessorsData
  }

  override fun getTotalBuildTimeMs(): Long {
    return criticalPathAnalyzerResult.buildFinishedTimestamp-criticalPathAnalyzerResult.buildStartedTimestamp
  }

  override fun getConfigurationPhaseTimeMs(): Long {
    return criticalPathAnalyzerResult.run {
      val firstTaskStartTime = tasksDeterminingBuildDuration.minByOrNull { it.executionStartTime } ?.executionStartTime
      (firstTaskStartTime ?: buildFinishedTimestamp) - buildStartedTimestamp
    }
  }

  override fun getCriticalPathTasks(): List<TaskData> {
    return criticalPathAnalyzerResult.tasksDeterminingBuildDuration.filter(TaskData::isOnTheCriticalPath)
  }

  override fun getTasksDeterminingBuildDuration(): List<TaskData> {
    return criticalPathAnalyzerResult.tasksDeterminingBuildDuration
  }

  override fun getPluginsDeterminingBuildDuration(): List<PluginBuildData> {
    return criticalPathAnalyzerResult.pluginsDeterminingBuildDuration
  }

  override fun getTotalConfigurationData(): ProjectConfigurationData = projectConfigurationAnalyzerResult.run {
    val totalConfigurationTime = projectsConfigurationData.sumByLong { it.totalConfigurationTimeMs }
    val totalPluginConfiguration = pluginsConfigurationDataMap.map { entry ->
      PluginConfigurationData(entry.key, entry.value)
    }
    val totalConfigurationSteps = projectsConfigurationData.flatMap { it.configurationSteps }.groupBy { it.type }.map { entry ->
      ProjectConfigurationData.ConfigurationStep(entry.key, entry.value.sumByLong { it.configurationTimeMs })
    }
    return ProjectConfigurationData("Total Configuration Data", totalConfigurationTime, totalPluginConfiguration, totalConfigurationSteps)
  }

  override fun getProjectsConfigurationData(): List<ProjectConfigurationData> {
    return projectConfigurationAnalyzerResult.projectsConfigurationData
  }

  override fun getAlwaysRunTasks(): List<AlwaysRunTaskData> {
    return alwaysRunTasksAnalyzerResult.alwaysRunTasks
  }

  override fun getNonCacheableTasks(): List<TaskData> {
    return noncacheableTasksAnalyzerResult.noncacheableTasks
  }

  override fun getTasksSharingOutput(): List<TasksSharingOutputData> {
    return tasksConfigurationIssuesAnalyzerResult.tasksSharingOutput
  }

  override fun getAppliedPlugins(): Map<String, List<PluginData>> {
    return projectConfigurationAnalyzerResult.allAppliedPlugins.toImmutableMap()
  }

  override fun getConfigurationCachingCompatibility(): ConfigurationCachingCompatibilityProjectResult {
    return configurationCachingCompatibilityAnalyzerResult
  }

  override fun getJetifierUsageResult(): JetifierUsageAnalyzerResult {
    return jetifierUsageAnalyzerResult
  }

  override fun getGarbageCollectionData(): List<GarbageCollectionData> {
    return garbageCollectionAnalyzerResult.garbageCollectionData
  }

  override fun getTotalGarbageCollectionTimeMs(): Long {
    return garbageCollectionAnalyzerResult.totalGarbageCollectionTimeMs
  }

  override fun getJavaVersion(): Int? {
    return garbageCollectionAnalyzerResult.javaVersion
  }

  override fun isGCSettingSet(): Boolean? {
    return garbageCollectionAnalyzerResult.isSettingSet
  }

  override fun buildUsesConfigurationCache(): Boolean = configurationCachingCompatibilityAnalyzerResult.let {
    it == ConfigurationCachingTurnedOn || it == ConfigurationCacheCompatibilityTestFlow
  }

  override fun getDownloadsAnalyzerResult(): DownloadsAnalyzer.Result {
    return downloadsAnalyzerResult
  }

  override fun getTaskCategoryWarningsAnalyzerResult(): TaskCategoryWarningsAnalyzer.Result {
    return taskCategoryWarningsAnalyzerResult
  }

  override fun getBuildSessionID(): String {
    return buildSessionID
  }
}

data class FailureResult(
  private val buildSessionID: String,
  val failureType: Type
) : AbstractBuildAnalysisResult {
  override fun getBuildSessionID(): String = buildSessionID

  enum class Type { BUILD_FAILURE, ANALYSIS_FAILURE }
}
