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
package com.android.build.attribution.proto.converters

import com.android.build.attribution.BuildAnalysisResults
import com.android.build.attribution.BuildAnalysisResultsMessage
import com.android.build.attribution.analyzers.NoDataFromSavedResult
import com.android.build.attribution.analyzers.TaskCategoryWarningsAnalyzer
import com.android.build.attribution.data.PluginData
import com.android.build.attribution.data.TaskData
import com.android.build.attribution.proto.constructPluginType
import com.android.build.attribution.proto.transformPluginData

class BuildResultsProtoMessageConverter {

  companion object {

    fun convertBuildAnalysisResultsFromObjectToBytes(
      buildResults: BuildAnalysisResults,
      plugins: Map<String, PluginData>,
      tasks: Map<String, TaskData>
    ): BuildAnalysisResultsMessage {
      val analyzersDataBuilder = BuildAnalysisResultsMessage.newBuilder()
      analyzersDataBuilder.requestData = GradleBuildInvokerRequestRequestDataMessageConverter.transform(buildResults.getBuildRequestData())
      analyzersDataBuilder.annotationProcessorsAnalyzerResult = AnnotationProcessorsAnalyzerResultMessageConverter.transform(
        buildResults.getAnnotationProcessorAnalyzerResult()
      )
      analyzersDataBuilder.alwaysRunTasksAnalyzerResult = AlwaysRunTasksAnalyzerResultMessageConverter.transform(
        buildResults.getAlwaysRunTasks())
      analyzersDataBuilder.criticalPathAnalyzerResult = CriticalPathAnalyzerResultMessageConverter.transform(
        buildResults.getCriticalPathAnalyzerResult())
      analyzersDataBuilder.noncacheableTasksAnalyzerResult = NoncacheableTaskDataMessageConverter.transform(
        buildResults.getNonCacheableTasks())
      analyzersDataBuilder.garbageCollectionAnalyzerResult = GarbageCollectionAnalyzerResultMessageConverter.transform(
        buildResults.getGarbageCollectionAnalyzerResult()
      )
      analyzersDataBuilder.projectConfigurationAnalyzerResult = ProjectConfigurationAnalyzerResultMessageConverter.transform(
        buildResults.getProjectConfigurationAnalyzerResult()
      )
      analyzersDataBuilder.tasksConfigurationAnalyzerResult = TaskConfigurationAnalyzerResultMessageConverter.transform(
        buildResults.getTasksSharingOutput())
      analyzersDataBuilder.jetifierUsageAnalyzerResult = JetifierUsageAnalyzerResultMessageConverter.transform(
        buildResults.getJetifierUsageResult())
      analyzersDataBuilder.downloadsAnalyzerResult = DownloadsAnalyzerResultMessageConverter.transform(
        buildResults.getDownloadsAnalyzerResult())
      analyzersDataBuilder.taskCategoryWarningsAnalyzerResult = TaskCategoryWarningsAnalyzerResultConverter.transform(
        buildResults.getTaskCategoryWarningsAnalyzerResult())
      analyzersDataBuilder.buildSessionID = buildResults.getBuildSessionID()
      analyzersDataBuilder.pluginCache = BuildAnalysisResultsMessage.PluginCache.newBuilder()
        .addAllValues(plugins.values.map(::transformPluginData)).build()
      analyzersDataBuilder.taskCache = BuildAnalysisResultsMessage.TaskCache.newBuilder()
        .addAllValues(tasks.values.map(TaskDataMessageConverter.Companion::transform)).build()
      return analyzersDataBuilder.build()
    }

    fun convertBuildAnalysisResultsFromBytesToObject(buildResultsMsg: BuildAnalysisResultsMessage): BuildAnalysisResults {
      val requestData = GradleBuildInvokerRequestRequestDataMessageConverter.construct(buildResultsMsg.requestData)
      val tasks = mutableMapOf<String, TaskData>()
      val plugins = mutableMapOf<String, PluginData>()
      buildResultsMsg.pluginCache.valuesList.map {
        PluginData(constructPluginType(it.pluginType), it.idName)
      }.forEach { plugins[it.idName] = it }
      TaskDataMessageConverter.construct(buildResultsMsg.taskCache.valuesList, plugins).forEach { tasks[it.getTaskPath()] = it }
      val annotationProcessorsAnalyzerResult = AnnotationProcessorsAnalyzerResultMessageConverter.construct(
        buildResultsMsg.annotationProcessorsAnalyzerResult)
      val alwaysRunTaskAnalyzerResult = AlwaysRunTasksAnalyzerResultMessageConverter.construct(buildResultsMsg.alwaysRunTasksAnalyzerResult,
                                                                                               tasks)
      val criticalPathAnalyzerResult = CriticalPathAnalyzerResultMessageConverter.construct(buildResultsMsg.criticalPathAnalyzerResult,
                                                                                            tasks,
                                                                                            plugins)
      val noncacheableTaskAnalyzerResult = NoncacheableTaskDataMessageConverter.construct(buildResultsMsg.noncacheableTasksAnalyzerResult,
                                                                                          tasks)
      val garbageCollectionAnalyzerResult = GarbageCollectionAnalyzerResultMessageConverter.construct(
        buildResultsMsg.garbageCollectionAnalyzerResult)
      val projectConfigurationAnalyzerResult = ProjectConfigurationAnalyzerResultMessageConverter.construct(
        buildResultsMsg.projectConfigurationAnalyzerResult)
      val tasksConfigurationIssuesAnalyzerResult = TaskConfigurationAnalyzerResultMessageConverter.construct(
        buildResultsMsg.tasksConfigurationAnalyzerResult, tasks)
      val configurationCachingCompatibilityAnalyzerResult = NoDataFromSavedResult
      val jetifierUsageAnalyzerResult = JetifierUsageAnalyzerResultMessageConverter.construct(buildResultsMsg.jetifierUsageAnalyzerResult)
      val downloadAnalyzerResult = DownloadsAnalyzerResultMessageConverter.construct(buildResultsMsg.downloadsAnalyzerResult)
      val taskCategoryWarningsAnalyzerResult = TaskCategoryWarningsAnalyzerResultConverter.construct(buildResultsMsg.taskCategoryWarningsAnalyzerResult)
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
        taskCategoryWarningsAnalyzerResult = taskCategoryWarningsAnalyzerResult,
        buildSessionID = buildSessionID,
        taskMap = tasks,
        pluginMap = plugins
      )
    }
  }
}