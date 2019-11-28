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
package com.android.build.attribution.analytics

import com.android.build.attribution.analyzers.BuildEventsAnalysisResult
import com.android.build.attribution.data.AlwaysRunTaskData
import com.android.build.attribution.data.AnnotationProcessorData
import com.android.build.attribution.data.PluginBuildData
import com.android.build.attribution.data.PluginConfigurationData
import com.android.build.attribution.data.PluginData
import com.android.build.attribution.data.ProjectConfigurationData
import com.android.build.attribution.data.TasksSharingOutputData
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.stats.withProjectId
import com.google.common.base.Stopwatch
import com.google.wireless.android.sdk.stats.AlwaysRunTasksAnalyzerData
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.AnnotationProcessorsAnalyzerData
import com.google.wireless.android.sdk.stats.BuildAttributionAnalyzersData
import com.google.wireless.android.sdk.stats.BuildAttributionPerformanceStats
import com.google.wireless.android.sdk.stats.BuildAttributionPluginIdentifier
import com.google.wireless.android.sdk.stats.BuildAttributionStats
import com.google.wireless.android.sdk.stats.CriticalPathAnalyzerData
import com.google.wireless.android.sdk.stats.ProjectConfigurationAnalyzerData
import com.google.wireless.android.sdk.stats.TasksConfigurationIssuesAnalyzerData
import com.intellij.openapi.project.Project
import java.io.Closeable
import java.util.concurrent.TimeUnit

class BuildAttributionAnalyticsManager(project: Project) : Closeable {
  private val eventBuilder = AndroidStudioEvent.newBuilder()
    .setKind(AndroidStudioEvent.EventKind.BUILD_ATTRIBUTION_STATS)
    .withProjectId(project)

  private val attributionStatsBuilder = BuildAttributionStats.newBuilder()

  fun recordPostBuildAnalysis(block: () -> Unit) {
    val watch = Stopwatch.createStarted()
    block()
    attributionStatsBuilder.setBuildAttributionPerformanceStats(
      BuildAttributionPerformanceStats.newBuilder().setPostBuildAnalysisDurationMs(watch.stop().elapsed(TimeUnit.MILLISECONDS)))
  }

  fun logAnalyzersData(analysisResult: BuildEventsAnalysisResult) {
    val analyzersDataBuilder = BuildAttributionAnalyzersData.newBuilder()

    analyzersDataBuilder.alwaysRunTasksAnalyzerData = transformAlwaysRunTasksAnalyzerData(analysisResult.getAlwaysRunTasks())
    analyzersDataBuilder.annotationProcessorsAnalyzerData =
      transformAnnotationProcessorsAnalyzerData(analysisResult.getNonIncrementalAnnotationProcessorsData())
    analyzersDataBuilder.criticalPathAnalyzerData = transformCriticalPathAnalyzerData(analysisResult.getCriticalPathDurationMs(),
                                                                                      analysisResult.getCriticalPathTasks().size,
                                                                                      analysisResult.getCriticalPathPlugins())
    analyzersDataBuilder.projectConfigurationAnalyzerData =
      transformProjectConfigurationAnalyzerData(analysisResult.getProjectsConfigurationData())
    analyzersDataBuilder.tasksConfigurationIssuesAnalyzerData =
      transformTasksConfigurationIssuesAnalyzerData(analysisResult.getTasksSharingOutput())

    attributionStatsBuilder.setBuildAttributionAnalyzersData(analyzersDataBuilder)
  }

  override fun close() {
    UsageTracker.log(eventBuilder.setBuildAttributionStats(attributionStatsBuilder))
  }

  private fun transformAlwaysRunTasksAnalyzerData(alwaysRunTasks: List<AlwaysRunTaskData>) =
    AlwaysRunTasksAnalyzerData.newBuilder()
      .addAllAlwaysRunTasks(alwaysRunTasks.map(::transformAlwaysRunTaskData))
      .build()

  private fun transformAnnotationProcessorsAnalyzerData(nonIncrementalAnnotationProcessors: List<AnnotationProcessorData>) =
    AnnotationProcessorsAnalyzerData.newBuilder()
      .addAllNonIncrementalAnnotationProcessors(nonIncrementalAnnotationProcessors.map(::transformAnnotationProcessorData))
      .build()

  private fun transformCriticalPathAnalyzerData(criticalPathDurationMs: Long,
                                                numberOfTasksOnCriticalPath: Int,
                                                pluginsCriticalPath: List<PluginBuildData>) =
    CriticalPathAnalyzerData.newBuilder()
      .setCriticalPathDurationMs(criticalPathDurationMs)
      .setNumberOfTasksOnCriticalPath(numberOfTasksOnCriticalPath)
      .addAllPluginsCriticalPath(pluginsCriticalPath.map(::transformPluginBuildData))
      .build()

  private fun transformProjectConfigurationAnalyzerData(projectConfigurationData: List<ProjectConfigurationData>) =
    ProjectConfigurationAnalyzerData.newBuilder()
      .addAllProjectConfigurationData(projectConfigurationData.map(::transformProjectConfigurationData))
      .build()

  private fun transformTasksConfigurationIssuesAnalyzerData(tasksSharingOutputData: List<TasksSharingOutputData>) =
    TasksConfigurationIssuesAnalyzerData.newBuilder()
      .addAllTasksSharingOutputData(tasksSharingOutputData.map(::transformTasksSharingOutputData))
      .build()

  private fun transformPluginType(pluginType: PluginData.PluginType) =
    when (pluginType) {
      PluginData.PluginType.UNKNOWN -> BuildAttributionPluginIdentifier.PluginType.UNKNOWN_TYPE
      PluginData.PluginType.PLUGIN -> BuildAttributionPluginIdentifier.PluginType.BINARY_PLUGIN
      PluginData.PluginType.SCRIPT -> BuildAttributionPluginIdentifier.PluginType.BUILD_SCRIPT
    }

  private fun transformPluginData(pluginData: PluginData): BuildAttributionPluginIdentifier {
    val pluginType = transformPluginType(pluginData.pluginType)
    val builder = BuildAttributionPluginIdentifier.newBuilder().setType(pluginType)
    if (pluginType == BuildAttributionPluginIdentifier.PluginType.BINARY_PLUGIN) {
      builder.pluginDisplayName = pluginData.displayName
    }
    return builder.build()
  }

  private fun transformPluginBuildData(pluginBuildData: PluginBuildData) =
    CriticalPathAnalyzerData.PluginBuildData.newBuilder()
      .setBuildDurationMs(pluginBuildData.buildDuration)
      .setPluginIdentifier(transformPluginData(pluginBuildData.plugin))
      .build()

  private fun transformAlwaysRunTaskReason(reason: AlwaysRunTaskData.Reason) =
    when (reason) {
      AlwaysRunTaskData.Reason.NO_OUTPUTS_WITHOUT_ACTIONS ->
        AlwaysRunTasksAnalyzerData.AlwaysRunTask.AlwaysRunReason.NO_OUTPUTS_WITHOUT_ACTIONS
      AlwaysRunTaskData.Reason.NO_OUTPUTS_WITH_ACTIONS ->
        AlwaysRunTasksAnalyzerData.AlwaysRunTask.AlwaysRunReason.NO_OUTPUTS_WITH_ACTIONS
      AlwaysRunTaskData.Reason.UP_TO_DATE_WHEN_FALSE ->
        AlwaysRunTasksAnalyzerData.AlwaysRunTask.AlwaysRunReason.UP_TO_DATE_WHEN_FALSE
    }

  private fun transformAlwaysRunTaskData(alwaysRunTaskData: AlwaysRunTaskData) =
    AlwaysRunTasksAnalyzerData.AlwaysRunTask.newBuilder()
      .setReason(transformAlwaysRunTaskReason(alwaysRunTaskData.rerunReason))
      .setPluginIdentifier(transformPluginData(alwaysRunTaskData.taskData.originPlugin))
      .build()

  private fun transformAnnotationProcessorData(annotationProcessorData: AnnotationProcessorData) =
    AnnotationProcessorsAnalyzerData.NonIncrementalAnnotationProcessor.newBuilder()
      .setCompilationDurationMs(annotationProcessorData.compilationDuration.toMillis())
      .setAnnotationProcessorClassName(annotationProcessorData.className)
      .build()

  private fun transformPluginConfigurationData(pluginConfigurationData: PluginConfigurationData) =
    ProjectConfigurationAnalyzerData.PluginConfigurationData.newBuilder()
      .setPluginConfigurationTimeMs(pluginConfigurationData.configurationTimeMs)
      .setPluginIdentifier(transformPluginData(pluginConfigurationData.plugin))
      .build()

  private fun transformProjectConfigurationData(projectConfigurationData: ProjectConfigurationData) =
    ProjectConfigurationAnalyzerData.ProjectConfigurationData.newBuilder()
      .setConfigurationTimeMs(projectConfigurationData.totalConfigurationTimeMs)
      .addAllPluginsConfigurationData(projectConfigurationData.pluginsConfigurationData.map(::transformPluginConfigurationData))
      .build()

  private fun transformTasksSharingOutputData(tasksSharingOutputData: TasksSharingOutputData) =
    TasksConfigurationIssuesAnalyzerData.TasksSharingOutputData.newBuilder()
      .addAllPluginsCreatedSharingOutputTasks(tasksSharingOutputData.taskList.map { transformPluginData(it.originPlugin) })
      .build()
}
