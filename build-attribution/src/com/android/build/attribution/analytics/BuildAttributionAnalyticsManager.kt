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

import com.android.build.attribution.analyzers.AGPUpdateRequired
import com.android.build.attribution.analyzers.AnalyzerNotRun
import com.android.build.attribution.analyzers.BuildEventsAnalysisResult
import com.android.build.attribution.analyzers.ConfigurationCacheCompatibilityTestFlow
import com.android.build.attribution.analyzers.ConfigurationCachingCompatibilityProjectResult
import com.android.build.attribution.analyzers.ConfigurationCachingTurnedOff
import com.android.build.attribution.analyzers.ConfigurationCachingTurnedOn
import com.android.build.attribution.analyzers.DownloadsAnalyzer
import com.android.build.attribution.analyzers.IncompatiblePluginsDetected
import com.android.build.attribution.analyzers.JetifierCanBeRemoved
import com.android.build.attribution.analyzers.JetifierNotUsed
import com.android.build.attribution.analyzers.JetifierRequiredForLibraries
import com.android.build.attribution.analyzers.JetifierUsageAnalyzerResult
import com.android.build.attribution.analyzers.JetifierUsedCheckRequired
import com.android.build.attribution.analyzers.NoIncompatiblePlugins
import com.android.build.attribution.data.AlwaysRunTaskData
import com.android.build.attribution.data.AnnotationProcessorData
import com.android.build.attribution.data.BuildInvocationType
import com.android.build.attribution.data.PluginBuildData
import com.android.build.attribution.data.PluginConfigurationData
import com.android.build.attribution.data.PluginData
import com.android.build.attribution.data.ProjectConfigurationData
import com.android.build.attribution.data.TaskData
import com.android.build.attribution.data.TasksSharingOutputData
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.stats.withProjectId
import com.google.common.base.Stopwatch
import com.google.wireless.android.sdk.stats.AlwaysRunTasksAnalyzerData
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.AnnotationProcessorsAnalyzerData
import com.google.wireless.android.sdk.stats.BuildAttribuitionTaskIdentifier
import com.google.wireless.android.sdk.stats.BuildAttributionAnalyzersData
import com.google.wireless.android.sdk.stats.BuildAttributionPerformanceStats
import com.google.wireless.android.sdk.stats.BuildAttributionPluginIdentifier
import com.google.wireless.android.sdk.stats.BuildAttributionStats
import com.google.wireless.android.sdk.stats.BuildDownloadsAnalysisData
import com.google.wireless.android.sdk.stats.ConfigurationCacheCompatibilityData
import com.google.wireless.android.sdk.stats.CriticalPathAnalyzerData
import com.google.wireless.android.sdk.stats.JetifierUsageData
import com.google.wireless.android.sdk.stats.ProjectConfigurationAnalyzerData
import com.google.wireless.android.sdk.stats.TasksConfigurationIssuesAnalyzerData
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.utils.addToStdlib.sumByLong
import java.io.Closeable
import java.util.concurrent.TimeUnit

class BuildAttributionAnalyticsManager(
  buildSessionId: String,
  project: Project
) : Closeable {
  private val eventBuilder = AndroidStudioEvent.newBuilder()
    .setKind(AndroidStudioEvent.EventKind.BUILD_ATTRIBUTION_STATS)
    .withProjectId(project)

  private val attributionStatsBuilder = BuildAttributionStats.newBuilder().setBuildAttributionReportSessionId(buildSessionId)

  fun runLoggingPerformanceStats(toolingApiLatencyMs: Long, postBuildAnalysis: () -> Unit) {
    val watch = Stopwatch.createStarted()
    postBuildAnalysis()
    attributionStatsBuilder.setBuildAttributionPerformanceStats(
      BuildAttributionPerformanceStats.newBuilder()
        .setPostBuildAnalysisDurationMs(watch.stop().elapsed(TimeUnit.MILLISECONDS))
        .setToolingApiBuildFinishedEventLatencyMs(toolingApiLatencyMs)
    )
  }

  fun logAnalyzersData(analysisResult: BuildEventsAnalysisResult) {
    val analyzersDataBuilder = BuildAttributionAnalyzersData.newBuilder()

    analyzersDataBuilder.totalBuildTimeMs = analysisResult.getTotalBuildTimeMs()

    analyzersDataBuilder.alwaysRunTasksAnalyzerData = transformAlwaysRunTasksAnalyzerData(analysisResult.getAlwaysRunTasks())
    analyzersDataBuilder.annotationProcessorsAnalyzerData =
      transformAnnotationProcessorsAnalyzerData(analysisResult.getNonIncrementalAnnotationProcessorsData())
    analyzersDataBuilder.criticalPathAnalyzerData = transformCriticalPathAnalyzerData(
      analysisResult.getCriticalPathTasks().sumByLong { it.executionTime },
      analysisResult.getTasksDeterminingBuildDuration().sumByLong(TaskData::executionTime),
      analysisResult.getCriticalPathTasks().size,
      analysisResult.getTasksDeterminingBuildDuration().size,
      analysisResult.getPluginsDeterminingBuildDuration()
    )
    analyzersDataBuilder.projectConfigurationAnalyzerData =
      transformProjectConfigurationAnalyzerData(analysisResult.getProjectsConfigurationData(), analysisResult.getTotalConfigurationData())
    analyzersDataBuilder.tasksConfigurationIssuesAnalyzerData =
      transformTasksConfigurationIssuesAnalyzerData(analysisResult.getTasksSharingOutput())
    analyzersDataBuilder.configurationCacheCompatibilityData =
      transformConfigurationCacheCompatibilityData(analysisResult.getConfigurationCachingCompatibility())
    analyzersDataBuilder.jetifierUsageData = transformJetifierUsageData(analysisResult.getJetifierUsageResult())
    analysisResult.getDownloadsAnalyzerResult().let {
      if (it is DownloadsAnalyzer.ActiveResult) {
        analyzersDataBuilder.downloadsAnalysisData = transformDownloadsAnalyzerData(it)
      }
    }
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

  private fun transformCriticalPathAnalyzerData(
    criticalPathDurationMs: Long,
    tasksDeterminingBuildDurationMs: Long,
    numberOfTasksOnCriticalPath: Int,
    numberOfTasksDeterminingBuildDuration: Int,
    pluginsCriticalPath: List<PluginBuildData>
  ) = CriticalPathAnalyzerData.newBuilder()
    .setCriticalPathDurationMs(criticalPathDurationMs)
    .setTasksDeterminingBuildDurationMs(tasksDeterminingBuildDurationMs)
    .setNumberOfTasksOnCriticalPath(numberOfTasksOnCriticalPath)
    .setNumberOfTasksDeterminingBuildDuration(numberOfTasksDeterminingBuildDuration)
    .addAllPluginsCriticalPath(pluginsCriticalPath.map(::transformPluginBuildData))
    .build()

  private fun transformProjectConfigurationAnalyzerData(
    projectConfigurationData: List<ProjectConfigurationData>,
    totalConfigurationData: ProjectConfigurationData
  ) =
    ProjectConfigurationAnalyzerData.newBuilder()
      .addAllProjectConfigurationData(projectConfigurationData.map(::transformProjectConfigurationData))
      .setOverallConfigurationData(transformProjectConfigurationData(totalConfigurationData))
      .build()

  private fun transformTasksConfigurationIssuesAnalyzerData(tasksSharingOutputData: List<TasksSharingOutputData>) =
    TasksConfigurationIssuesAnalyzerData.newBuilder()
      .addAllTasksSharingOutputData(tasksSharingOutputData.map(::transformTasksSharingOutputData))
      .build()

  private fun transformPluginType(pluginType: PluginData.PluginType) =
    when (pluginType) {
      PluginData.PluginType.UNKNOWN -> BuildAttributionPluginIdentifier.PluginType.UNKNOWN_TYPE
      PluginData.PluginType.SCRIPT -> BuildAttributionPluginIdentifier.PluginType.BUILD_SCRIPT
      PluginData.PluginType.BUILDSRC_PLUGIN -> BuildAttributionPluginIdentifier.PluginType.BUILD_SRC
      PluginData.PluginType.BINARY_PLUGIN -> BuildAttributionPluginIdentifier.PluginType.OTHER_PLUGIN
    }

  private fun transformPluginData(pluginData: PluginData): BuildAttributionPluginIdentifier {
    val pluginType = transformPluginType(pluginData.pluginType)
    val builder = BuildAttributionPluginIdentifier.newBuilder().setType(pluginType)
    if (pluginType == BuildAttributionPluginIdentifier.PluginType.OTHER_PLUGIN) {
      builder.pluginDisplayName = pluginData.displayName
      builder.pluginClassName = pluginData.idName
    }
    return builder.build()
  }

  private fun getTaskClassName(taskData: TaskData) = taskData.taskType.split('.').last()

  private fun transformTaskData(taskData: TaskData) =
    BuildAttribuitionTaskIdentifier.newBuilder()
      .setTaskClassName(getTaskClassName(taskData))
      .setOriginPlugin(transformPluginData(taskData.originPlugin))
      .build()

  private fun transformPluginBuildData(pluginBuildData: PluginBuildData) =
    CriticalPathAnalyzerData.PluginBuildData.newBuilder()
      .setBuildDurationMs(pluginBuildData.buildDuration)
      .setPluginIdentifier(transformPluginData(pluginBuildData.plugin))
      .build()

  private fun transformAlwaysRunTaskReason(reason: AlwaysRunTaskData.Reason) =
    when (reason) {
      AlwaysRunTaskData.Reason.NO_OUTPUTS_WITH_ACTIONS ->
        AlwaysRunTasksAnalyzerData.AlwaysRunTask.AlwaysRunReason.NO_OUTPUTS_WITH_ACTIONS
      AlwaysRunTaskData.Reason.UP_TO_DATE_WHEN_FALSE ->
        AlwaysRunTasksAnalyzerData.AlwaysRunTask.AlwaysRunReason.UP_TO_DATE_WHEN_FALSE
    }

  private fun transformAlwaysRunTaskData(alwaysRunTaskData: AlwaysRunTaskData) =
    AlwaysRunTasksAnalyzerData.AlwaysRunTask.newBuilder()
      .setReason(transformAlwaysRunTaskReason(alwaysRunTaskData.rerunReason))
      .setTaskIdentifier(transformTaskData(alwaysRunTaskData.taskData))
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

  private fun transformConfigurationStepType(stepType: ProjectConfigurationData.ConfigurationStep.Type) =
    when (stepType) {
      ProjectConfigurationData.ConfigurationStep.Type.NOTIFYING_BUILD_LISTENERS ->
        ProjectConfigurationAnalyzerData.ConfigurationStep.StepType.NOTIFYING_BUILD_LISTENERS
      ProjectConfigurationData.ConfigurationStep.Type.RESOLVING_DEPENDENCIES ->
        ProjectConfigurationAnalyzerData.ConfigurationStep.StepType.RESOLVING_DEPENDENCIES
      ProjectConfigurationData.ConfigurationStep.Type.COMPILING_BUILD_SCRIPTS ->
        ProjectConfigurationAnalyzerData.ConfigurationStep.StepType.COMPILING_BUILD_SCRIPTS
      ProjectConfigurationData.ConfigurationStep.Type.EXECUTING_BUILD_SCRIPT_BLOCKS ->
        ProjectConfigurationAnalyzerData.ConfigurationStep.StepType.EXECUTING_BUILD_SCRIPT_BLOCKS
      ProjectConfigurationData.ConfigurationStep.Type.OTHER ->
        ProjectConfigurationAnalyzerData.ConfigurationStep.StepType.OTHER
    }

  private fun transformConfigurationStepData(configurationStep: ProjectConfigurationData.ConfigurationStep) =
    ProjectConfigurationAnalyzerData.ConfigurationStep.newBuilder()
      .setType(transformConfigurationStepType(configurationStep.type))
      .setConfigurationTimeMs(configurationStep.configurationTimeMs)
      .build()

  private fun transformProjectConfigurationData(projectConfigurationData: ProjectConfigurationData) =
    ProjectConfigurationAnalyzerData.ProjectConfigurationData.newBuilder()
      .setConfigurationTimeMs(projectConfigurationData.totalConfigurationTimeMs)
      .addAllPluginsConfigurationData(projectConfigurationData.pluginsConfigurationData.map(::transformPluginConfigurationData))
      .addAllConfigurationSteps(projectConfigurationData.configurationSteps.map(::transformConfigurationStepData))
      .build()

  private fun transformTasksSharingOutputData(tasksSharingOutputData: TasksSharingOutputData) =
    TasksConfigurationIssuesAnalyzerData.TasksSharingOutputData.newBuilder()
      .addAllTasksSharingOutput(tasksSharingOutputData.taskList.map(::transformTaskData))
      .build()

  private fun transformConfigurationCacheCompatibilityData(configurationCachingCompatibilityState: ConfigurationCachingCompatibilityProjectResult) =
    ConfigurationCacheCompatibilityData.newBuilder().apply {
      compatibilityState = when (configurationCachingCompatibilityState) {
        is AGPUpdateRequired -> ConfigurationCacheCompatibilityData.CompatibilityState.AGP_NOT_COMPATIBLE
        is NoIncompatiblePlugins -> ConfigurationCacheCompatibilityData.CompatibilityState.INCOMPATIBLE_PLUGINS_NOT_DETECTED
        is IncompatiblePluginsDetected -> ConfigurationCacheCompatibilityData.CompatibilityState.INCOMPATIBLE_PLUGINS_DETECTED
        ConfigurationCachingTurnedOn -> ConfigurationCacheCompatibilityData.CompatibilityState.CONFIGURATION_CACHE_TURNED_ON
        ConfigurationCacheCompatibilityTestFlow -> ConfigurationCacheCompatibilityData.CompatibilityState.CONFIGURATION_CACHE_TRIAL_FLOW_BUILD
        ConfigurationCachingTurnedOff -> ConfigurationCacheCompatibilityData.CompatibilityState.CONFIGURATION_CACHE_TURNED_OFF
      }
      if (configurationCachingCompatibilityState is IncompatiblePluginsDetected) {
        addAllIncompatiblePlugins(configurationCachingCompatibilityState.incompatiblePluginWarnings.map { transformPluginData(it.plugin) })
        addAllIncompatiblePlugins(configurationCachingCompatibilityState.upgradePluginWarnings.map { transformPluginData(it.plugin) })
      }
    }
      .build()

  private fun transformJetifierUsageData(jetifierUsageResult: JetifierUsageAnalyzerResult) =
    JetifierUsageData.newBuilder().apply {
      checkJetifierTaskBuild = jetifierUsageResult.checkJetifierBuild
      when (jetifierUsageResult.projectStatus) {
        AnalyzerNotRun -> null
        JetifierCanBeRemoved -> JetifierUsageData.JetifierUsageState.JETIFIER_CAN_BE_REMOVED
        JetifierNotUsed -> JetifierUsageData.JetifierUsageState.JETIFIER_NOT_USED
        JetifierUsedCheckRequired -> JetifierUsageData.JetifierUsageState.JETIFIER_USED_CHECK_REQUIRED
        is JetifierRequiredForLibraries -> JetifierUsageData.JetifierUsageState.JETIFIER_REQUIRED_FOR_LIBRARIES
      }?.let { jetifierUsageState = it }

      if (jetifierUsageResult.projectStatus is JetifierRequiredForLibraries) {
        numberOfLibrariesRequireJetifier = jetifierUsageResult.projectStatus.checkJetifierResult.dependenciesDependingOnSupportLibs.size
      }
    }
      .build()

  private fun transformDownloadsAnalyzerData(downloadsAnalyzerResult: DownloadsAnalyzer.ActiveResult): BuildDownloadsAnalysisData =
    BuildDownloadsAnalysisData.newBuilder().apply {
      addAllRepositories(downloadsAnalyzerResult.repositoryResults.map { repoResult ->
        BuildDownloadsAnalysisData.RepositoryStats.newBuilder().apply {
          repositoryType = repoResult.repository.analyticsType
          successRequestsCount = repoResult.successRequestsCount
          successRequestsTotalTimeMs = repoResult.successRequestsTimeMs
          successRequestsTotalBytesDownloaded = repoResult.successRequestsBytesDownloaded
          failedRequestsCount = repoResult.failedRequestsCount
          failedRequestsTotalTimeMs = repoResult.failedRequestsTimeMs
          failedRequestsTotalBytesDownloaded = repoResult.failedRequestsBytesDownloaded
          missedRequestsCount = repoResult.missedRequestsCount
          missedRequestsTotalTimeMs = repoResult.missedRequestsTimeMs
        }
          .build()
      })
    }
      .build()

  fun logBuildSuccess(buildInvocationType: BuildInvocationType) {
    attributionStatsBuilder.buildType = buildInvocationType.metricsType
    attributionStatsBuilder.buildAnalysisStatus = BuildAttributionStats.BuildAnalysisStatus.SUCCESS
  }

  fun logBuildFailure(buildInvocationType: BuildInvocationType) {
    attributionStatsBuilder.buildType = buildInvocationType.metricsType
    attributionStatsBuilder.buildAnalysisStatus = BuildAttributionStats.BuildAnalysisStatus.BUILD_FAILURE
  }

  fun logAnalysisFailure(buildInvocationType: BuildInvocationType) {
    attributionStatsBuilder.buildType = buildInvocationType.metricsType
    attributionStatsBuilder.buildAnalysisStatus = BuildAttributionStats.BuildAnalysisStatus.ANALYSIS_FAILURE
  }

}
