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

import com.android.build.attribution.BuildAnalysisResultsMessage.CriticalPathAnalyzerResult
import com.android.build.attribution.BuildAnalysisResultsMessage.NoncacheableTasksAnalyzerResult
import com.android.build.attribution.BuildAnalysisResultsMessage.ProjectConfigurationAnalyzerResult
import com.android.build.attribution.BuildAnalysisResultsMessage.AlwaysRunTasksAnalyzerResult
import com.android.build.attribution.BuildAnalysisResultsMessage.AnnotationProcessorsAnalyzerResult
import com.android.build.attribution.BuildAnalysisResultsMessage.DownloadsAnalyzerResult
import com.android.build.attribution.BuildAnalysisResultsMessage.GarbageCollectionAnalyzerResult
import com.android.build.attribution.BuildAnalysisResultsMessage.ProjectConfigurationAnalyzerResult.PluginDataLongMap
import com.android.build.attribution.BuildAnalysisResultsMessage.TasksConfigurationIssuesAnalyzerResult
import com.android.build.attribution.analyzers.AlwaysRunTasksAnalyzer
import com.android.build.attribution.analyzers.AnalyzerNotRun
import com.android.build.attribution.analyzers.AnnotationProcessorsAnalyzer
import com.android.build.attribution.analyzers.CriticalPathAnalyzer
import com.android.build.attribution.analyzers.DownloadsAnalyzer
import com.android.build.attribution.analyzers.GarbageCollectionAnalyzer
import com.android.build.attribution.analyzers.JetifierCanBeRemoved
import com.android.build.attribution.analyzers.JetifierNotUsed
import com.android.build.attribution.analyzers.JetifierRequiredForLibraries
import com.android.build.attribution.analyzers.JetifierUsageAnalyzerResult
import com.android.build.attribution.analyzers.JetifierUsageProjectStatus
import com.android.build.attribution.analyzers.JetifierUsedCheckRequired
import com.android.build.attribution.analyzers.NoDataFromSavedResult
import com.android.build.attribution.analyzers.NoncacheableTasksAnalyzer
import com.android.build.attribution.analyzers.ProjectConfigurationAnalyzer
import com.android.build.attribution.analyzers.TaskCategoryWarningsAnalyzer
import com.android.build.attribution.analyzers.TasksConfigurationIssuesAnalyzer
import com.android.build.attribution.data.AlwaysRunTaskData
import com.android.build.attribution.data.AnnotationProcessorData
import com.android.build.attribution.data.BuildRequestHolder
import com.android.build.attribution.data.GarbageCollectionData
import com.android.build.attribution.data.PluginBuildData
import com.android.build.attribution.data.PluginConfigurationData
import com.android.build.attribution.data.PluginData
import com.android.build.attribution.data.ProjectConfigurationData
import com.android.build.attribution.data.TaskData
import com.android.build.attribution.data.TasksSharingOutputData
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker
import com.android.tools.idea.gradle.util.BuildMode
import com.google.wireless.android.sdk.stats.BuildDownloadsAnalysisData
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.project.Project
import java.io.File
import java.time.Duration

class BuildResultsProtoMessageConverter(val project: Project) {

  fun convertBuildAnalysisResultsFromObjectToBytes(
    buildResults: BuildAnalysisResults,
    plugins: Map<String, PluginData>,
    tasks: Map<String, TaskData>
  ) : BuildAnalysisResultsMessage {
    val analyzersDataBuilder = BuildAnalysisResultsMessage.newBuilder()
    // TODO(b/246728389): Add field for TaskCategoryWarningsAnalyzerResult
    analyzersDataBuilder.requestData = transformRequestData(buildResults.getBuildRequestData())
    analyzersDataBuilder.annotationProcessorsAnalyzerResult = transformAnnotationProcessorsAnalyzerResult(
      buildResults.getAnnotationProcessorAnalyzerResult()
    )
    analyzersDataBuilder.alwaysRunTasksAnalyzerResult = transformAlwaysRunTasksAnalyzerData(buildResults.getAlwaysRunTasks())
    analyzersDataBuilder.criticalPathAnalyzerResult = transformCriticalPathAnalyzerResult(buildResults.getCriticalPathAnalyzerResult())
    analyzersDataBuilder.noncacheableTasksAnalyzerResult = transformNoncacheableTaskData(buildResults.getNonCacheableTasks())
    analyzersDataBuilder.garbageCollectionAnalyzerResult = transformGarbageCollectionAnalyzerResult(
      buildResults.getGarbageCollectionAnalyzerResult()
    )
    analyzersDataBuilder.projectConfigurationAnalyzerResult = transformProjectConfigurationAnalyzerResult(
      buildResults.getProjectConfigurationAnalyzerResult()
    )
    analyzersDataBuilder.tasksConfigurationAnalyzerResult = transformTaskConfigurationAnalyzerResult(buildResults.getTasksSharingOutput())
    analyzersDataBuilder.jetifierUsageAnalyzerResult = transformJetifierUsageAnalyzerResult(buildResults.getJetifierUsageResult())
    analyzersDataBuilder.downloadsAnalyzerResult = transformDownloadsAnalyzerResult(buildResults.getDownloadsAnalyzerResult())
    analyzersDataBuilder.buildSessionID = buildResults.getBuildSessionID()
    analyzersDataBuilder.pluginCache = BuildAnalysisResultsMessage.PluginCache.newBuilder()
      .addAllValues(plugins.values.map(::transformPluginData)).build()
    analyzersDataBuilder.taskCache = BuildAnalysisResultsMessage.TaskCache.newBuilder()
      .addAllValues(tasks.values.map(::transformTaskData)).build()
    return analyzersDataBuilder.build()
  }

  private fun transformRequestData(requestData: GradleBuildInvoker.Request.RequestData) =
    BuildAnalysisResultsMessage.RequestData.newBuilder()
      .setBuildMode(transformBuildMode(requestData.mode))
      .setRootProjectPathString(requestData.rootProjectPath.path)
      .addAllGradleTasks(requestData.gradleTasks)
      .addAllEnv(requestData.env.map{ transformEnv(it.key, it.value) })
      .setIsPassParentEnvs(requestData.isPassParentEnvs)
      .build()

  private fun transformEnv(key: String, value: String) =
    BuildAnalysisResultsMessage.RequestData.Env.newBuilder()
      .setEnvKey(key)
      .setEnvValue(value)
      .build()
  private fun transformBuildMode(mode: BuildMode?) = when(mode) {
    BuildMode.CLEAN -> BuildAnalysisResultsMessage.RequestData.BuildMode.CLEAN
    BuildMode.ASSEMBLE-> BuildAnalysisResultsMessage.RequestData.BuildMode.ASSEMBLE
    BuildMode.REBUILD -> BuildAnalysisResultsMessage.RequestData.BuildMode.REBUILD
    BuildMode.COMPILE_JAVA -> BuildAnalysisResultsMessage.RequestData.BuildMode.COMPILE_JAVA
    BuildMode.SOURCE_GEN -> BuildAnalysisResultsMessage.RequestData.BuildMode.SOURCE_GEN
    BuildMode.BUNDLE -> BuildAnalysisResultsMessage.RequestData.BuildMode.BUNDLE
    BuildMode.APK_FROM_BUNDLE -> BuildAnalysisResultsMessage.RequestData.BuildMode.APK_FROM_BUNDLE
    BuildMode.DEFAULT_BUILD_MODE -> BuildAnalysisResultsMessage.RequestData.BuildMode.UNSPECIFIED
    null -> BuildAnalysisResultsMessage.RequestData.BuildMode.UNSPECIFIED
  }

  fun transformAnnotationProcessorsAnalyzerResult(annotationProcessorsAnalyzerResult: AnnotationProcessorsAnalyzer.Result)
  : AnnotationProcessorsAnalyzerResult =
      AnnotationProcessorsAnalyzerResult.newBuilder()
        .addAllAnnotationProcessorsData(annotationProcessorsAnalyzerResult.annotationProcessorsData.map(::transformAnnotationProcessorsDatum))
        .addAllNonIncrementalAnnotationProcessorsData(annotationProcessorsAnalyzerResult.nonIncrementalAnnotationProcessorsData.map(::transformAnnotationProcessorsDatum))
        .build()

  private fun transformAnnotationProcessorsDatum(annotationProcessorData: AnnotationProcessorData) =
    AnnotationProcessorsAnalyzerResult.AnnotationProcessorsData.newBuilder()
      .setClassName(annotationProcessorData.className)
      .setCompilationDuration(transformDuration(annotationProcessorData.compilationDuration))
      .build()

  private fun transformDuration(duration: Duration) : BuildAnalysisResultsMessage.Duration =
    BuildAnalysisResultsMessage.Duration.newBuilder()
      .setSeconds(duration.seconds)
      .setNanos(duration.nano)
      .build()

  fun transformAlwaysRunTasksAnalyzerData(alwaysRunTasks: List<AlwaysRunTaskData>) :
    AlwaysRunTasksAnalyzerResult? =
    AlwaysRunTasksAnalyzerResult.newBuilder()
      .addAllAlwaysRunTasksData(alwaysRunTasks.map{ transformAlwaysRunTaskData(it) })
      .build()

  private fun transformAlwaysRunTaskData(alwaysRunTaskData: AlwaysRunTaskData) :
    AlwaysRunTasksAnalyzerResult.AlwaysRunTasksData {
    val arTaskData = AlwaysRunTasksAnalyzerResult.AlwaysRunTasksData.newBuilder()
      .setReason((transformAlwaysRunTaskReason(alwaysRunTaskData.rerunReason)))
    arTaskData.taskId = alwaysRunTaskData.taskData.getTaskPath()
    return arTaskData.build()
  }

  private fun transformAlwaysRunTaskReason(reason: AlwaysRunTaskData.Reason) =
    when(reason) {
      AlwaysRunTaskData.Reason.NO_OUTPUTS_WITH_ACTIONS ->
        AlwaysRunTasksAnalyzerResult.AlwaysRunTasksData.Reason.NO_OUTPUTS_WITH_ACTIONS
      AlwaysRunTaskData.Reason.UP_TO_DATE_WHEN_FALSE ->
        AlwaysRunTasksAnalyzerResult.AlwaysRunTasksData.Reason.UP_TO_DATE_WHEN_FALSE
    }

  private fun transformTaskData(taskData: TaskData) =
    BuildAnalysisResultsMessage.TaskData.newBuilder()
      .setTaskName(taskData.taskName)
      .setOriginPluginId(taskData.originPlugin.idName)
      .setProjectPath(taskData.projectPath)
      .setExecutionStartTime(taskData.executionStartTime)
      .setExecutionEndTime(taskData.executionEndTime)
      .setExecutionMode(transformExecutionMode(taskData.executionMode))
      .addAllExecutionReasons(taskData.executionReasons)
      .build()

  private fun transformExecutionMode(executionMode: TaskData.TaskExecutionMode) : BuildAnalysisResultsMessage.TaskData.TaskExecutionMode {
    return when (executionMode) {
      TaskData.TaskExecutionMode.FROM_CACHE -> BuildAnalysisResultsMessage.TaskData.TaskExecutionMode.FROM_CACHE
      TaskData.TaskExecutionMode.UP_TO_DATE -> BuildAnalysisResultsMessage.TaskData.TaskExecutionMode.UP_TO_DATE
      TaskData.TaskExecutionMode.INCREMENTAL -> BuildAnalysisResultsMessage.TaskData.TaskExecutionMode.INCREMENTAL
      TaskData.TaskExecutionMode.FULL -> BuildAnalysisResultsMessage.TaskData.TaskExecutionMode.FULL
    }
  }

  private fun transformPluginData(pluginData: PluginData) : BuildAnalysisResultsMessage.PluginData =
    BuildAnalysisResultsMessage.PluginData.newBuilder()
      .setPluginType(transformPluginType(pluginData.pluginType))
      .setIdName(pluginData.idName)
      .build()

  private fun transformPluginType(pluginType: PluginData.PluginType) =
    when (pluginType) {
      PluginData.PluginType.UNKNOWN -> BuildAnalysisResultsMessage.PluginData.PluginType.UNKNOWN
      PluginData.PluginType.SCRIPT -> BuildAnalysisResultsMessage.PluginData.PluginType.SCRIPT
      PluginData.PluginType.BUILDSRC_PLUGIN -> BuildAnalysisResultsMessage.PluginData.PluginType.BUILDSRC_PLUGIN
      PluginData.PluginType.BINARY_PLUGIN -> BuildAnalysisResultsMessage.PluginData.PluginType.BINARY_PLUGIN
    }

  fun transformCriticalPathAnalyzerResult(criticalPathAnalyzerData: CriticalPathAnalyzer.Result) : CriticalPathAnalyzerResult =
    CriticalPathAnalyzerResult.newBuilder()
      .addAllTaskIdsDeterminingBuildDuration(criticalPathAnalyzerData.tasksDeterminingBuildDuration.map{ it.getTaskPath() })
      .addAllPluginsDeterminingBuildDuration(criticalPathAnalyzerData.pluginsDeterminingBuildDuration.map(::transformPluginBuildData))
      .setBuildFinishedTimestamp(criticalPathAnalyzerData.buildFinishedTimestamp)
      .setBuildStartedTimestamp(criticalPathAnalyzerData.buildStartedTimestamp)
      .build()

  private fun transformPluginBuildData(pluginBuildData: PluginBuildData) =
    CriticalPathAnalyzerResult.PluginBuildData.newBuilder()
      .setBuildDuration(pluginBuildData.buildDuration)
      .setPluginID(pluginBuildData.plugin.idName)
      .build()

  fun transformNoncacheableTaskData(noncacheableTaskData: List<TaskData>) =
    NoncacheableTasksAnalyzerResult.newBuilder().addAllNoncacheableTaskIds(
      noncacheableTaskData.map{ it.getTaskPath() }
    ).build()

  fun transformGarbageCollectionAnalyzerResult(garbageCollectionAnalyzerResult: GarbageCollectionAnalyzer.Result)
    : GarbageCollectionAnalyzerResult {
    val garbageCollectionAnalyzerResultBuilder = GarbageCollectionAnalyzerResult.newBuilder()
    garbageCollectionAnalyzerResultBuilder.addAllGarbageCollectionData(
      (garbageCollectionAnalyzerResult.garbageCollectionData).map(::transformGarbageCollectionDatum))
    if(garbageCollectionAnalyzerResult.javaVersion != null) {
      garbageCollectionAnalyzerResultBuilder.javaVersion = garbageCollectionAnalyzerResult.javaVersion
    }
    when(garbageCollectionAnalyzerResult.isSettingSet) {
      true -> garbageCollectionAnalyzerResultBuilder.isSettingSet =  GarbageCollectionAnalyzerResult.TrueFalseUnknown.TRUE
      false -> garbageCollectionAnalyzerResultBuilder.isSettingSet = GarbageCollectionAnalyzerResult.TrueFalseUnknown.FALSE
      null -> garbageCollectionAnalyzerResultBuilder.isSettingSet = GarbageCollectionAnalyzerResult.TrueFalseUnknown.UNKNOWN
    }
    return garbageCollectionAnalyzerResultBuilder.build()
  }

  private fun transformGarbageCollectionDatum(garbageCollectionDatum: GarbageCollectionData)
    = GarbageCollectionAnalyzerResult.GarbageCollectionData.newBuilder()
    .setCollectionTimeMs(garbageCollectionDatum.collectionTimeMs)
    .setName(garbageCollectionDatum.name)
    .build()

  fun transformProjectConfigurationAnalyzerResult(projectConfigurationAnalyzerResult: ProjectConfigurationAnalyzer.Result): ProjectConfigurationAnalyzerResult =
    ProjectConfigurationAnalyzerResult.newBuilder()
      .addAllPluginsConfigurationDataMap(
        projectConfigurationAnalyzerResult.pluginsConfigurationDataMap.map {
          transformPluginsDataLongMap(it.key, it.value)
        }
      )
      .addAllProjectConfigurationData(projectConfigurationAnalyzerResult.projectsConfigurationData.map(::transformProjectConfigurationData))
      .addAllAllAppliedPlugins(
        projectConfigurationAnalyzerResult.allAppliedPlugins.map {
          transformStringPluginDataMap(it.key, it.value)
        }
      )
      .build()

  private fun transformPluginsDataLongMap(pluginData: PluginData, long: Long) : PluginDataLongMap =
      PluginDataLongMap.newBuilder()
        .setPluginData(transformPluginData(pluginData))
        .setLong(long)
        .build()


  private fun transformStringPluginDataMap(appliedPlugins: String, plugins: List<PluginData>) : ProjectConfigurationAnalyzerResult.StringPluginDataMap =
    ProjectConfigurationAnalyzerResult.StringPluginDataMap.newBuilder()
      .setAppliedPlugins(appliedPlugins)
      .addAllPlugins(plugins.map(::transformPluginData))
      .build()

  private fun transformProjectConfigurationData(projectConfigurationData: ProjectConfigurationData) =
    ProjectConfigurationAnalyzerResult.ProjectConfigurationData.newBuilder()
      .addAllPluginsConfigurationData((projectConfigurationData.pluginsConfigurationData).map(::transformPluginConfigurationData))
      .addAllConfigurationSteps((projectConfigurationData.configurationSteps).map(::transformConfigurationStep))
      .setProjectPath(projectConfigurationData.projectPath)
      .setTotalConfigurationTime(projectConfigurationData.totalConfigurationTimeMs)
      .build()

  fun transformTaskConfigurationAnalyzerResult(tasksSharingOutputData: List<TasksSharingOutputData>)
    : TasksConfigurationIssuesAnalyzerResult? =
    TasksConfigurationIssuesAnalyzerResult.newBuilder()
      .addAllTasksSharingOutputData(tasksSharingOutputData.map(::transformTasksSharingOutputData))
      .build()

  private fun transformTasksSharingOutputData(tasksSharingOutputData: TasksSharingOutputData) =
    TasksConfigurationIssuesAnalyzerResult.TasksSharingOutputData.newBuilder()
      .addAllTaskIdList(tasksSharingOutputData.taskList.map{ it.getTaskPath() })
      .setOutputFilePath(tasksSharingOutputData.outputFilePath)
      .build()

  private fun transformPluginConfigurationData (pluginConfigurationData: PluginConfigurationData) =
    ProjectConfigurationAnalyzerResult.ProjectConfigurationData.PluginConfigurationData.newBuilder()
      .setPlugin(transformPluginData(pluginConfigurationData.plugin))
      .setConfigurationTimeMS(pluginConfigurationData.configurationTimeMs)
      .build()

  private fun transformConfigurationStep(configurationStep: ProjectConfigurationData.ConfigurationStep) =
    ProjectConfigurationAnalyzerResult.ProjectConfigurationData.ConfigurationStep.newBuilder()
      .setType(transformConfigurationStepTypes(configurationStep.type))
      .setConfigurationTimeMs(configurationStep.configurationTimeMs)
      .build()

  private fun transformConfigurationStepTypes(type: ProjectConfigurationData.ConfigurationStep.Type) =
    when(type) {
      ProjectConfigurationData.ConfigurationStep.Type.NOTIFYING_BUILD_LISTENERS ->
        ProjectConfigurationAnalyzerResult.ProjectConfigurationData.ConfigurationStep.Type.NOTIFYING_BUILD_LISTENERS
      ProjectConfigurationData.ConfigurationStep.Type.RESOLVING_DEPENDENCIES ->
        ProjectConfigurationAnalyzerResult.ProjectConfigurationData.ConfigurationStep.Type.RESOLVING_DEPENDENCIES
      ProjectConfigurationData.ConfigurationStep.Type.COMPILING_BUILD_SCRIPTS ->
        ProjectConfigurationAnalyzerResult.ProjectConfigurationData.ConfigurationStep.Type.COMPILING_BUILD_SCRIPTS
      ProjectConfigurationData.ConfigurationStep.Type.EXECUTING_BUILD_SCRIPT_BLOCKS ->
        ProjectConfigurationAnalyzerResult.ProjectConfigurationData.ConfigurationStep.Type.EXECUTING_BUILD_SCRIPT_BLOCKS
      ProjectConfigurationData.ConfigurationStep.Type.OTHER->
        ProjectConfigurationAnalyzerResult.ProjectConfigurationData.ConfigurationStep.Type.OTHER
    }


  fun transformJetifierUsageAnalyzerResult(jetifierUsageAnalyzerResult: JetifierUsageAnalyzerResult)
    : BuildAnalysisResultsMessage.JetifierUsageAnalyzerResult {
    val jetifierUsageAnalyzerResultBuilder = BuildAnalysisResultsMessage.JetifierUsageAnalyzerResult.newBuilder()
      .setProjectStatus(transformProjectStatus(jetifierUsageAnalyzerResult.projectStatus))
      .setCheckJetifierBuild(jetifierUsageAnalyzerResult.checkJetifierBuild)
    if(jetifierUsageAnalyzerResult.lastCheckJetifierBuildTimestamp != null) {
      jetifierUsageAnalyzerResultBuilder.lastCheckJetifierBuildTimestamp = jetifierUsageAnalyzerResult.lastCheckJetifierBuildTimestamp
    }
    return jetifierUsageAnalyzerResultBuilder.build()
  }

  private fun transformProjectStatus(jetifierUsageProjectStatus: JetifierUsageProjectStatus) =
    when(jetifierUsageProjectStatus) {
      AnalyzerNotRun -> BuildAnalysisResultsMessage.JetifierUsageAnalyzerResult.JetifierUsageProjectStatus.ANALYZER_NOT_RUN
      JetifierNotUsed -> BuildAnalysisResultsMessage.JetifierUsageAnalyzerResult.JetifierUsageProjectStatus.JETIFIER_NOT_USED
      JetifierUsedCheckRequired -> BuildAnalysisResultsMessage.JetifierUsageAnalyzerResult.JetifierUsageProjectStatus.JETIFIER_USED_CHECK_REQUIRED
      JetifierCanBeRemoved -> BuildAnalysisResultsMessage.JetifierUsageAnalyzerResult.JetifierUsageProjectStatus.JETIFIER_CAN_BE_REMOVED
      is JetifierRequiredForLibraries -> BuildAnalysisResultsMessage.JetifierUsageAnalyzerResult.JetifierUsageProjectStatus.IS_JETIFIER_REQUIRED_FOR_LIBRARIES
    }

  fun transformDownloadsAnalyzerResult(downloadsAnalyzerResult: DownloadsAnalyzer.Result) : DownloadsAnalyzerResult = when (downloadsAnalyzerResult) {
    is DownloadsAnalyzer.ActiveResult -> DownloadsAnalyzerResult.newBuilder()
      .setActiveResult(transformActiveResult(downloadsAnalyzerResult.repositoryResults))
      .setResultStatus(DownloadsAnalyzerResult.ResultStatus.ACTIVE_RESULT)
      .build()
    is DownloadsAnalyzer.GradleDoesNotProvideEvents -> DownloadsAnalyzerResult.newBuilder()
      .setResultStatus(DownloadsAnalyzerResult.ResultStatus.GRADLE_DOES_NOT_PROVIDE_EVENTS)
      .build()
    is DownloadsAnalyzer.AnalyzerIsDisabled -> DownloadsAnalyzerResult.newBuilder()
      .setResultStatus(DownloadsAnalyzerResult.ResultStatus.ANALYZER_IS_DISABLED)
      .build()
  }

  private fun transformActiveResult(repositoryResults: List<DownloadsAnalyzer.RepositoryResult>) =
    DownloadsAnalyzerResult.ActiveResult.newBuilder()
      .addAllRepositoryResult(repositoryResults.map(::transformRepositoryResult))
      .build()

  private fun transformDownloadResult(downloadResult: DownloadsAnalyzer.DownloadResult) : DownloadsAnalyzerResult.DownloadResult {
    val result = DownloadsAnalyzerResult.DownloadResult.newBuilder()
      .setTimestamp(downloadResult.timestamp)
      .setUrl(downloadResult.url)
      .setStatus(transformDownloadStatus(downloadResult.status))
      .setDuration(downloadResult.duration)
      .setBytes(downloadResult.bytes)
      .setFailureMessage(downloadResult.failureMessage)
    if(downloadResult.repository is DownloadsAnalyzer.OtherRepository) {
      result.repository = transformOtherRepository(downloadResult.repository)
    }
    else {
      result.repository = transformRepository(downloadResult.repository)
    }
    return result.build()
  }

  private fun transformDownloadStatus(status: DownloadsAnalyzer.DownloadStatus) =
    when(status) {
      DownloadsAnalyzer.DownloadStatus.SUCCESS -> DownloadsAnalyzerResult.DownloadResult.DownloadStatus.SUCCESS
      DownloadsAnalyzer.DownloadStatus.MISSED -> DownloadsAnalyzerResult.DownloadResult.DownloadStatus.MISSED
      DownloadsAnalyzer.DownloadStatus.FAILURE -> DownloadsAnalyzerResult.DownloadResult.DownloadStatus.FAILURE
    }

  private fun transformRepositoryResult(repositoryResult: DownloadsAnalyzer.RepositoryResult) : DownloadsAnalyzerResult.ActiveResult.RepositoryResult {
    val result = DownloadsAnalyzerResult.ActiveResult.RepositoryResult.newBuilder()
      .addAllDownloads(repositoryResult.downloads.map(::transformDownloadResult))
    if(repositoryResult.repository is DownloadsAnalyzer.OtherRepository) {
      result.repository = transformOtherRepository(repositoryResult.repository)
    }
    else {
      result.repository = transformRepository(repositoryResult.repository)
    }
    return result.build()
  }

  private fun transformOtherRepository(repository: DownloadsAnalyzer.OtherRepository) =
    DownloadsAnalyzerResult.Repository.newBuilder()
      .setAnalyticsType(transformRepositoryType(repository.analyticsType))
      .setHost(repository.host)
      .build()

  private fun transformRepository(repository: DownloadsAnalyzer.Repository) =
    DownloadsAnalyzerResult.Repository.newBuilder()
      .setAnalyticsType(transformRepositoryType(repository.analyticsType))
      .build()

  private fun transformRepositoryType(repositoryType: BuildDownloadsAnalysisData.RepositoryStats.RepositoryType) =
    when(repositoryType) {
      BuildDownloadsAnalysisData.RepositoryStats.RepositoryType.UNKNOWN_REPOSITORY ->
        DownloadsAnalyzerResult.Repository.RepositoryType.UNKNOWN_REPOSITORY
      BuildDownloadsAnalysisData.RepositoryStats.RepositoryType.GOOGLE ->
        DownloadsAnalyzerResult.Repository.RepositoryType.GOOGLE
      BuildDownloadsAnalysisData.RepositoryStats.RepositoryType.MAVEN_CENTRAL ->
        DownloadsAnalyzerResult.Repository.RepositoryType.MAVEN_CENTRAL
      BuildDownloadsAnalysisData.RepositoryStats.RepositoryType.JCENTER ->
        DownloadsAnalyzerResult.Repository.RepositoryType.JCENTER
      BuildDownloadsAnalysisData.RepositoryStats.RepositoryType.OTHER_REPOSITORY ->
        DownloadsAnalyzerResult.Repository.RepositoryType.OTHER_REPOSITORY
    }

  fun convertBuildAnalysisResultsFromBytesToObject(buildResultsMsg: BuildAnalysisResultsMessage): BuildAnalysisResults {
    val requestData = constructRequestData(buildResultsMsg.requestData)
    val tasks = mutableMapOf<String, TaskData>()
    val plugins = mutableMapOf<String, PluginData>()
    buildResultsMsg.pluginCache.valuesList.map{ PluginData(constructPluginType(it.pluginType), it.idName) }.forEach{ plugins[it.idName] = it }
    constructTaskData(buildResultsMsg.taskCache.valuesList, plugins).forEach{ tasks[it.getTaskPath()] = it }
    val annotationProcessorsAnalyzerResult = constructAnnotationProcessorsAnalyzerResult(buildResultsMsg.annotationProcessorsAnalyzerResult)
    val alwaysRunTaskAnalyzerResult = constructAlwaysRunTaskAnalyzerResult(buildResultsMsg.alwaysRunTasksAnalyzerResult, tasks)
    val criticalPathAnalyzerResult = constructCriticalPathAnalyzerResult(buildResultsMsg.criticalPathAnalyzerResult, tasks, plugins)
    val noncacheableTaskAnalyzerResult = constructNoncacheableTasksAnalyzerResult(buildResultsMsg.noncacheableTasksAnalyzerResult, tasks)
    val garbageCollectionAnalyzerResult = constructGarbageCollectionAnalyzerResult(buildResultsMsg.garbageCollectionAnalyzerResult)
    val projectConfigurationAnalyzerResult = constructProjectConfigurationAnalyzerResult(buildResultsMsg.projectConfigurationAnalyzerResult)
    val tasksConfigurationIssuesAnalyzerResult = constructTasksConfigurationAnalyzerResult(buildResultsMsg.tasksConfigurationAnalyzerResult, tasks)
    val configurationCachingCompatibilityAnalyzerResult = NoDataFromSavedResult
    val jetifierUsageAnalyzerResult = constructJetifierUsageAnalyzerResult(buildResultsMsg.jetifierUsageAnalyzerResult)
    val downloadAnalyzerResult = constructDownloadsAnalyzerResult(buildResultsMsg.downloadsAnalyzerResult)
    val buildSessionID: String = buildResultsMsg.buildSessionID
    return BuildAnalysisResults(
      buildRequestData = requestData,
      annotationProcessorAnalyzerResult = annotationProcessorsAnalyzerResult,
      alwaysRunTasksAnalyzerResult = alwaysRunTaskAnalyzerResult,
      criticalPathAnalyzerResult = criticalPathAnalyzerResult,
      noncacheableTasksAnalyzerResult = noncacheableTaskAnalyzerResult,
      garbageCollectionAnalyzerResult = garbageCollectionAnalyzerResult,
      projectConfigurationAnalyzerResult = projectConfigurationAnalyzerResult,
      tasksConfigurationIssuesAnalyzerResult = tasksConfigurationIssuesAnalyzerResult,
      configurationCachingCompatibilityAnalyzerResult = configurationCachingCompatibilityAnalyzerResult,
      jetifierUsageAnalyzerResult = jetifierUsageAnalyzerResult,
      downloadsAnalyzerResult = downloadAnalyzerResult,
      // TODO(b/246728389): Add TaskCategoryWarningsAnalyzerResult
      taskCategoryWarningsAnalyzerResult = TaskCategoryWarningsAnalyzer.Result(emptyList()),
      buildSessionID = buildSessionID,
      taskMap = tasks,
      pluginMap = plugins
    )
  }

  private fun constructAnnotationProcessorsData(
    annotationProcessorData: MutableList<AnnotationProcessorsAnalyzerResult.AnnotationProcessorsData>
  ) : MutableList<AnnotationProcessorData> {
    val annotationProcessorDataConverted = mutableListOf<AnnotationProcessorData>()
    for(annotationProcessorsDatum in annotationProcessorData) {
      val value = annotationProcessorsDatum.className
      val compilationDuration = annotationProcessorsDatum.compilationDuration
      annotationProcessorDataConverted.add(AnnotationProcessorData(value, Duration.ofSeconds(compilationDuration.seconds, compilationDuration.nanos.toLong())))
    }
    return annotationProcessorDataConverted
  }

  fun constructAnnotationProcessorsAnalyzerResult(
    annotationProcessorsAnalyzerResult: AnnotationProcessorsAnalyzerResult
  ) : AnnotationProcessorsAnalyzer.Result = AnnotationProcessorsAnalyzer.Result(
    constructAnnotationProcessorsData(annotationProcessorsAnalyzerResult.annotationProcessorsDataList),
    constructAnnotationProcessorsData(annotationProcessorsAnalyzerResult.nonIncrementalAnnotationProcessorsDataList)
  )

  fun constructAlwaysRunTaskAnalyzerResult(
    alwaysRunTasksAnalyzerResult: AlwaysRunTasksAnalyzerResult,
    tasks: Map<String, TaskData>
  ) : AlwaysRunTasksAnalyzer.Result {
    val alwaysRunTaskData = mutableListOf<AlwaysRunTaskData>()
    for (alwaysRunTaskDatum in alwaysRunTasksAnalyzerResult.alwaysRunTasksDataList) {
      val taskData = tasks[alwaysRunTaskDatum.taskId]
      val reason = when (alwaysRunTaskDatum.reason) {
        AlwaysRunTasksAnalyzerResult.AlwaysRunTasksData.Reason.NO_OUTPUTS_WITH_ACTIONS -> AlwaysRunTaskData.Reason.NO_OUTPUTS_WITH_ACTIONS
        AlwaysRunTasksAnalyzerResult.AlwaysRunTasksData.Reason.UP_TO_DATE_WHEN_FALSE -> AlwaysRunTaskData.Reason.UP_TO_DATE_WHEN_FALSE
        AlwaysRunTasksAnalyzerResult.AlwaysRunTasksData.Reason.UNRECOGNIZED -> throw IllegalStateException("Unrecognised reason")
        AlwaysRunTasksAnalyzerResult.AlwaysRunTasksData.Reason.UNSPECIFIED -> throw IllegalStateException("Unrecognised reason")
        null -> throw IllegalStateException("Unrecognised reason")
      }
      taskData?.let { AlwaysRunTaskData(it, reason!!) }?.let { alwaysRunTaskData.add(it) }
    }
    return AlwaysRunTasksAnalyzer.Result(alwaysRunTaskData)
  }

  fun constructCriticalPathAnalyzerResult(criticalPathAnalyzerResult: CriticalPathAnalyzerResult, tasks: MutableMap<String, TaskData>, plugins: MutableMap<String, PluginData>)
    : CriticalPathAnalyzer.Result{
    val tasksDeterminingBuildDuration = criticalPathAnalyzerResult.taskIdsDeterminingBuildDurationList.map{ tasks[it] }
    val pluginsDeterminingBuildDuration = mutableListOf<PluginBuildData>()
    criticalPathAnalyzerResult.pluginsDeterminingBuildDurationList.forEach {
      pluginsDeterminingBuildDuration.add(PluginBuildData(PluginData(plugins[it.pluginID]!!.pluginType, it.pluginID), it.buildDuration))
    }
    return CriticalPathAnalyzer.Result(
      tasksDeterminingBuildDuration.mapNotNull { it },
      pluginsDeterminingBuildDuration,
      criticalPathAnalyzerResult.buildStartedTimestamp,
      criticalPathAnalyzerResult.buildFinishedTimestamp
    )
  }

  fun constructNoncacheableTasksAnalyzerResult(
    noncacheableTasksAnalyzerResult: NoncacheableTasksAnalyzerResult,
    tasks: MutableMap<String, TaskData>
  ) = NoncacheableTasksAnalyzer.Result(noncacheableTasksAnalyzerResult.noncacheableTaskIdsList.mapNotNull{ tasks[it] })

 fun constructGarbageCollectionAnalyzerResult(
    garbageCollectionAnalyzerResult: GarbageCollectionAnalyzerResult
  ) : GarbageCollectionAnalyzer.Result {
    val garbageCollectionData: MutableList<GarbageCollectionData> = mutableListOf()
    val isSettingSet = when(garbageCollectionAnalyzerResult.isSettingSet) {
      GarbageCollectionAnalyzerResult.TrueFalseUnknown.TRUE -> true
      GarbageCollectionAnalyzerResult.TrueFalseUnknown.FALSE -> false
      GarbageCollectionAnalyzerResult.TrueFalseUnknown.UNKNOWN -> throw IllegalStateException("Unrecognized setting state")
      GarbageCollectionAnalyzerResult.TrueFalseUnknown.UNRECOGNIZED -> throw IllegalStateException("Unrecognized setting state")
      null -> throw IllegalStateException("Unrecognized setting state")

    }
    val javaVersion = when(garbageCollectionAnalyzerResult.javaVersion) {
      0 -> throw IllegalStateException("Unrecognized java version")
      else -> garbageCollectionAnalyzerResult.javaVersion
    }
    garbageCollectionAnalyzerResult.garbageCollectionDataList.forEach{ garbageCollectionData.add(GarbageCollectionData(it.name, it.collectionTimeMs)) }
    return GarbageCollectionAnalyzer.Result(garbageCollectionData,javaVersion, isSettingSet)
  }

  fun constructProjectConfigurationAnalyzerResult(projectConfigurationAnalyzerResult: ProjectConfigurationAnalyzerResult)
    : ProjectConfigurationAnalyzer.Result {
    val projectConfigurationData = mutableListOf<ProjectConfigurationData>()
    for (projectConfigurationDatum in projectConfigurationAnalyzerResult.projectConfigurationDataList) {
      val projectPath = projectConfigurationDatum.projectPath
      val totalConfigurationTime = projectConfigurationDatum.totalConfigurationTime
      val pluginsConfigurationData = mutableListOf<PluginConfigurationData>()
      for (pluginConfigurationDatum in projectConfigurationDatum.pluginsConfigurationDataList) {
        pluginsConfigurationData
          .add(
            PluginConfigurationData(
              PluginData(
                constructPluginType(pluginConfigurationDatum.plugin.pluginType), pluginConfigurationDatum.plugin.idName),
              pluginConfigurationDatum.configurationTimeMS
            )
          )
      }
      val configurationSteps = mutableListOf<ProjectConfigurationData.ConfigurationStep>()
      for (configurationStep in projectConfigurationDatum.configurationStepsList) {
        val configurationStepType = when (configurationStep.type) {
          ProjectConfigurationAnalyzerResult.ProjectConfigurationData.ConfigurationStep.Type.NOTIFYING_BUILD_LISTENERS
          -> ProjectConfigurationData.ConfigurationStep.Type.NOTIFYING_BUILD_LISTENERS
          ProjectConfigurationAnalyzerResult.ProjectConfigurationData.ConfigurationStep.Type.RESOLVING_DEPENDENCIES
          -> ProjectConfigurationData.ConfigurationStep.Type.RESOLVING_DEPENDENCIES
          ProjectConfigurationAnalyzerResult.ProjectConfigurationData.ConfigurationStep.Type.COMPILING_BUILD_SCRIPTS
          -> ProjectConfigurationData.ConfigurationStep.Type.COMPILING_BUILD_SCRIPTS
          ProjectConfigurationAnalyzerResult.ProjectConfigurationData.ConfigurationStep.Type.EXECUTING_BUILD_SCRIPT_BLOCKS
          -> ProjectConfigurationData.ConfigurationStep.Type.EXECUTING_BUILD_SCRIPT_BLOCKS
          ProjectConfigurationAnalyzerResult.ProjectConfigurationData.ConfigurationStep.Type.UNKNOWN
          -> throw IllegalStateException("Unrecognized type")
          ProjectConfigurationAnalyzerResult.ProjectConfigurationData.ConfigurationStep.Type.OTHER
          -> ProjectConfigurationData.ConfigurationStep.Type.OTHER
          ProjectConfigurationAnalyzerResult.ProjectConfigurationData.ConfigurationStep.Type.UNRECOGNIZED
          -> throw IllegalStateException("Unrecognized type")
          null -> throw IllegalStateException("Unrecognized type")
        }
        configurationSteps.add(ProjectConfigurationData.ConfigurationStep(configurationStepType, configurationStep.configurationTimeMs))
      }
      projectConfigurationData.add(
        ProjectConfigurationData(projectPath, totalConfigurationTime, pluginsConfigurationData, configurationSteps))
    }
    val pluginDataMap = mutableMapOf<PluginData, Long>()
    projectConfigurationAnalyzerResult.pluginsConfigurationDataMapList.forEach {
      val pluginData = PluginData(constructPluginType(it.pluginData.pluginType), it.pluginData.idName)
      pluginDataMap[pluginData] = it.long
    }
    val appliedPlugins = mutableMapOf<String, List<PluginData>>()
    projectConfigurationAnalyzerResult.allAppliedPluginsList.forEach {
      appliedPlugins[it.appliedPlugins] = it.pluginsList.map { plugin ->
        PluginData(constructPluginType(plugin.pluginType), plugin.idName)
      }
    }
    return ProjectConfigurationAnalyzer.Result(pluginDataMap, projectConfigurationData, appliedPlugins)
  }

  fun constructTasksConfigurationAnalyzerResult(
    tasksConfigurationAnalyzerResult: TasksConfigurationIssuesAnalyzerResult,
    tasks: Map<String, TaskData>
  ) : TasksConfigurationIssuesAnalyzer.Result {
    val tasksSharingOutputData = mutableListOf<TasksSharingOutputData>()
    for (task in tasksConfigurationAnalyzerResult.tasksSharingOutputDataList) {
      val outputFilePath = task.outputFilePath
      val taskList = task.taskIdListList.mapNotNull{ tasks[it] }
      tasksSharingOutputData.add(TasksSharingOutputData(outputFilePath, taskList))
    }
    return TasksConfigurationIssuesAnalyzer.Result(tasksSharingOutputData)
  }

  private fun constructJetifierUsageAnalyzerResult(jetifierUsageAnalyzerResult: BuildAnalysisResultsMessage.JetifierUsageAnalyzerResult)
    : JetifierUsageAnalyzerResult {
    val lastCheckJetifierBuildTimestamp = when(jetifierUsageAnalyzerResult.lastCheckJetifierBuildTimestamp) {
      0L -> null
      else -> jetifierUsageAnalyzerResult.lastCheckJetifierBuildTimestamp
    }
    return JetifierUsageAnalyzerResult(AnalyzerNotRun, lastCheckJetifierBuildTimestamp, jetifierUsageAnalyzerResult.checkJetifierBuild)

  }

  fun constructDownloadsAnalyzerResult(downloadsAnalyzerResult: DownloadsAnalyzerResult) : DownloadsAnalyzer.Result {
    val downloadAnalyzerResult: DownloadsAnalyzer.Result
    when (downloadsAnalyzerResult.resultStatus) {
      DownloadsAnalyzerResult.ResultStatus.ACTIVE_RESULT -> {
        val repositoryResults = mutableListOf<DownloadsAnalyzer.RepositoryResult>()
        for (repositoryResult in downloadsAnalyzerResult.activeResult.repositoryResultList) {
          val downloadResults = mutableListOf<DownloadsAnalyzer.DownloadResult>()
          for (download in repositoryResult.downloadsList) {
            val repositoryType = constructRepositoryType(repositoryResult.repository.analyticsType, repositoryResult.repository.host)
            val status = when (download.status) {
              DownloadsAnalyzerResult.DownloadResult.DownloadStatus.SUCCESS -> DownloadsAnalyzer.DownloadStatus.SUCCESS
              DownloadsAnalyzerResult.DownloadResult.DownloadStatus.FAILURE -> DownloadsAnalyzer.DownloadStatus.FAILURE
              DownloadsAnalyzerResult.DownloadResult.DownloadStatus.MISSED -> DownloadsAnalyzer.DownloadStatus.MISSED
              DownloadsAnalyzerResult.DownloadResult.DownloadStatus.UNKNOWN -> throw IllegalStateException("Unrecognised download status")
              DownloadsAnalyzerResult.DownloadResult.DownloadStatus.UNRECOGNIZED -> throw IllegalStateException("Unrecognised download status")
            }
            downloadResults.add(
              DownloadsAnalyzer.DownloadResult(
                download.timestamp,
                repositoryType!!,
                download.url,
                status,
                download.duration,
                download.bytes,
                download.failureMessage
              )
            )
            repositoryResults.add(DownloadsAnalyzer.RepositoryResult(repositoryType!!, downloadResults))
          }
        }
        downloadAnalyzerResult = DownloadsAnalyzer.ActiveResult(repositoryResults)
      }
      DownloadsAnalyzerResult.ResultStatus.GRADLE_DOES_NOT_PROVIDE_EVENTS -> downloadAnalyzerResult = DownloadsAnalyzer.GradleDoesNotProvideEvents
      else -> downloadAnalyzerResult = DownloadsAnalyzer.AnalyzerIsDisabled
    }
    return downloadAnalyzerResult
  }

  private fun constructRequestData(requestData: BuildAnalysisResultsMessage.RequestData)
  : GradleBuildInvoker.Request.RequestData {
    val buildMode = when (requestData.buildMode) {
      BuildAnalysisResultsMessage.RequestData.BuildMode.CLEAN -> BuildMode.CLEAN
      BuildAnalysisResultsMessage.RequestData.BuildMode.ASSEMBLE -> BuildMode.ASSEMBLE
      BuildAnalysisResultsMessage.RequestData.BuildMode.REBUILD -> BuildMode.REBUILD
      BuildAnalysisResultsMessage.RequestData.BuildMode.COMPILE_JAVA -> BuildMode.COMPILE_JAVA
      BuildAnalysisResultsMessage.RequestData.BuildMode.SOURCE_GEN -> BuildMode.SOURCE_GEN
      BuildAnalysisResultsMessage.RequestData.BuildMode.BUNDLE -> BuildMode.BUNDLE
      BuildAnalysisResultsMessage.RequestData.BuildMode.APK_FROM_BUNDLE -> BuildMode.APK_FROM_BUNDLE
      BuildAnalysisResultsMessage.RequestData.BuildMode.UNSPECIFIED -> throw IllegalStateException("Unrecognized build mode")
      BuildAnalysisResultsMessage.RequestData.BuildMode.UNRECOGNIZED -> throw IllegalStateException("Unrecognized build mode")
      null -> throw IllegalStateException("Unrecognized build mode")
    }
    val env = mutableMapOf<String, String>()
    requestData.envList.forEach { env[it.envKey] = it.envValue }
    return GradleBuildInvoker.Request.RequestData(
        mode = buildMode,
        rootProjectPath = File(requestData.rootProjectPathString),
        gradleTasks = requestData.gradleTasksList,
        jvmArguments = requestData.jvmArgumentsList,
        commandLineArguments = requestData.commandLineArgumentsList,
        env = env,
        isPassParentEnvs = requestData.isPassParentEnvs
      )
  }

  private fun constructRepositoryType(
    repositoryType: DownloadsAnalyzerResult.Repository.RepositoryType,
    host: String
  ) =
    when (repositoryType) {
      DownloadsAnalyzerResult.Repository.RepositoryType.UNKNOWN_REPOSITORY
      -> DownloadsAnalyzer.OtherRepository(host)
      DownloadsAnalyzerResult.Repository.RepositoryType.GOOGLE
      -> DownloadsAnalyzer.KnownRepository.GOOGLE
      DownloadsAnalyzerResult.Repository.RepositoryType.MAVEN_CENTRAL
      -> DownloadsAnalyzer.KnownRepository.MAVEN_CENTRAL
      DownloadsAnalyzerResult.Repository.RepositoryType.JCENTER
      -> DownloadsAnalyzer.KnownRepository.JCENTER
      DownloadsAnalyzerResult.Repository.RepositoryType.OTHER_REPOSITORY
        -> DownloadsAnalyzer.OtherRepository(host)
      DownloadsAnalyzerResult.Repository.RepositoryType.UNRECOGNIZED -> throw IllegalStateException("Unrecognized repository type")
    }

  private fun constructTaskData(taskData: List<BuildAnalysisResultsMessage.TaskData>, plugins: Map<String, PluginData>) : List<TaskData> {
    val taskDataList = mutableListOf<TaskData>()
    for(task in taskData) {
      val taskName = task.taskName
      val projectPath = task.projectPath
      val pluginType = plugins[task.originPluginId]?.pluginType
      val originPlugin = pluginType?.let { PluginData(it, task.originPluginId) }
      val executionStartTime = task.executionStartTime
      val executionEndTime = task.executionEndTime
      val executionMode = when (task.executionMode) {
        BuildAnalysisResultsMessage.TaskData.TaskExecutionMode.FROM_CACHE -> TaskData.TaskExecutionMode.FROM_CACHE
        BuildAnalysisResultsMessage.TaskData.TaskExecutionMode.UP_TO_DATE -> TaskData.TaskExecutionMode.UP_TO_DATE
        BuildAnalysisResultsMessage.TaskData.TaskExecutionMode.INCREMENTAL -> TaskData.TaskExecutionMode.INCREMENTAL
        BuildAnalysisResultsMessage.TaskData.TaskExecutionMode.FULL -> TaskData.TaskExecutionMode.FULL
        BuildAnalysisResultsMessage.TaskData.TaskExecutionMode.UNKNOWN -> throw IllegalStateException("Unrecognized task execution mode")
        BuildAnalysisResultsMessage.TaskData.TaskExecutionMode.UNRECOGNIZED -> throw IllegalStateException("Unrecognized task execution mode")
      }
      val executionReasons = task.executionReasonsList
      originPlugin
        ?.let {
          val data = TaskData(taskName, projectPath, it, executionStartTime, executionEndTime, executionMode!!, executionReasons)
          taskDataList.add(data)
        }
    }
    return taskDataList
  }

  private fun constructPluginType(type: BuildAnalysisResultsMessage.PluginData.PluginType) =
    when(type) {
      BuildAnalysisResultsMessage.PluginData.PluginType.BINARY_PLUGIN -> PluginData.PluginType.BINARY_PLUGIN
      BuildAnalysisResultsMessage.PluginData.PluginType.BUILDSRC_PLUGIN -> PluginData.PluginType.BUILDSRC_PLUGIN
      BuildAnalysisResultsMessage.PluginData.PluginType.SCRIPT -> PluginData.PluginType.SCRIPT
      BuildAnalysisResultsMessage.PluginData.PluginType.UNKNOWN -> PluginData.PluginType.UNKNOWN
      BuildAnalysisResultsMessage.PluginData.PluginType.UNRECOGNIZED -> throw IllegalStateException("Unrecognized plugin type")
    }
}