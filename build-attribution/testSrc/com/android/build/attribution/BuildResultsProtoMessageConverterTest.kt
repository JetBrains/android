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
import com.android.build.attribution.analyzers.CriticalPathAnalyzer
import com.android.build.attribution.analyzers.DownloadsAnalyzer
import com.android.build.attribution.analyzers.GarbageCollectionAnalyzer
import com.android.build.attribution.analyzers.NoncacheableTasksAnalyzer
import com.android.build.attribution.analyzers.ProjectConfigurationAnalyzer
import com.android.build.attribution.analyzers.TasksConfigurationIssuesAnalyzer
import com.android.build.attribution.data.AlwaysRunTaskData
import com.android.build.attribution.data.AnnotationProcessorData
import com.android.build.attribution.data.GarbageCollectionData
import com.android.build.attribution.data.PluginBuildData
import com.android.build.attribution.data.PluginData
import com.android.build.attribution.data.ProjectConfigurationData
import com.android.build.attribution.data.TaskData
import com.android.build.attribution.data.TasksSharingOutputData
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker
import com.android.tools.idea.gradle.util.BuildMode
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.Duration

class BuildResultsProtoMessageConverterTest {
  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  @Test
  fun testAnnotationProcessorsAnalyzerResult() {
    val annotationProcessorData = listOf(
        AnnotationProcessorData("com.google.auto.value.processor.AutoAnnotationProcessor", Duration.ofMillis(123)),
        AnnotationProcessorData("com.google.auto.value.processor.AutoValueBuilderProcessor", Duration.ofMillis(456)),
        AnnotationProcessorData("com.google.auto.value.processor.AutoOneOfProcessor", Duration.ofMillis(789)),
        AnnotationProcessorData("com.google.auto.value.processor.AutoValueProcessor", Duration.ofMillis(101)),
        AnnotationProcessorData("com.google.auto.value.extension.memoized.processor.MemoizedValidator", Duration.ofMillis(102)),
        AnnotationProcessorData("dagger.internal.codegen.ComponentProcessor", Duration.ofMillis(103))
    )
    val nonIncrementalAnnotationProcessorData = listOf(
        AnnotationProcessorData("com.google.auto.value.processor.AutoValueBuilderProcessor", Duration.ofMillis(456)),
        AnnotationProcessorData("com.google.auto.value.processor.AutoValueProcessor", Duration.ofMillis(101)),
        AnnotationProcessorData("dagger.internal.codegen.ComponentProcessor", Duration.ofMillis(103))
    )
    val annotationProcessorsAnalyzerResult = AnnotationProcessorsAnalyzer.Result(annotationProcessorData, nonIncrementalAnnotationProcessorData)
    val annotationProcessorsAnalyzerMessageResult = BuildResultsProtoMessageConverter(projectRule.project).transformAnnotationProcessorsAnalyzerResult(annotationProcessorsAnalyzerResult)
    val annotationProcessorAnalyzerResultConverted = BuildResultsProtoMessageConverter(projectRule.project).constructAnnotationProcessorsAnalyzerResult(annotationProcessorsAnalyzerMessageResult)
    Truth.assertThat(annotationProcessorAnalyzerResultConverted).isEqualTo(annotationProcessorsAnalyzerResult)
  }

  @Test
  fun testAlwaysRunTasksAnalyzerResult() {
    val cache = mutableMapOf<String, TaskData>()
    val alwaysRunTaskData = mutableListOf<AlwaysRunTaskData>()
    val alwaysRunTaskDatum = AlwaysRunTaskData(
      TaskData(
        "task name",
        "project path",
        PluginData(PluginData.PluginType.UNKNOWN, "id name"),
        12345,
        12345,
        TaskData.TaskExecutionMode.FULL,
        listOf("abc", "def", "ghi")
      ),
      AlwaysRunTaskData.Reason.NO_OUTPUTS_WITH_ACTIONS
    )
    cache[alwaysRunTaskDatum.taskData.getTaskPath()] = alwaysRunTaskDatum.taskData
    alwaysRunTaskData.add(alwaysRunTaskDatum)
    val alwaysRunTaskDataResult = AlwaysRunTasksAnalyzer.Result(alwaysRunTaskData)
    val alwaysRunTaskDataResultMessage = BuildResultsProtoMessageConverter(projectRule.project).transformAlwaysRunTasksAnalyzerData(alwaysRunTaskDataResult.alwaysRunTasks)
    val alwaysRunTaskDataResultConverted = BuildResultsProtoMessageConverter(projectRule.project).constructAlwaysRunTaskAnalyzerResult(alwaysRunTaskDataResultMessage!!, cache)
    Truth.assertThat(alwaysRunTaskDataResultConverted).isEqualTo(alwaysRunTaskDataResult)
  }

  @Test
  fun testCriticalPathAnalyzerResult() {
    val taskCache = mutableMapOf<String, TaskData>()
    val pluginCache = mutableMapOf<String, PluginData>()
    val criticalPathData = mutableListOf<TaskData>()
    val pluginDatum = PluginData(PluginData.PluginType.BINARY_PLUGIN, "id name")
    val criticalPathDatum = TaskData(
      "task name",
      "project path",
      pluginDatum,
      12345,
      12345,
      TaskData.TaskExecutionMode.FULL,
      listOf("abc", "def", "ghi")
    )
    taskCache[criticalPathDatum.getTaskPath()] = criticalPathDatum
    pluginCache[pluginDatum.idName] = pluginDatum
    criticalPathData.add(criticalPathDatum)
    val criticalPathAnalyzerResult = CriticalPathAnalyzer.Result(
      criticalPathData,
      listOf(PluginBuildData(pluginDatum,12345)),
      12345,
      12345
    )
    val criticalPathAnalyzerResultMessage = BuildResultsProtoMessageConverter(projectRule.project)
      .transformCriticalPathAnalyzerResult(criticalPathAnalyzerResult)
    val criticalPathAnalyzerResultConverted = BuildResultsProtoMessageConverter(projectRule.project)
      .constructCriticalPathAnalyzerResult(criticalPathAnalyzerResultMessage, taskCache, pluginCache)
    Truth.assertThat(criticalPathAnalyzerResult).isEqualTo(criticalPathAnalyzerResultConverted)
  }

  @Test
  fun testGarbageCollectionAnalyzerResult() {
    val result = GarbageCollectionAnalyzer.Result(listOf(GarbageCollectionData("name", 12345)), 12345, true)
    val resultMessage = BuildResultsProtoMessageConverter(projectRule.project).transformGarbageCollectionAnalyzerResult(result)
    val resultConverted = BuildResultsProtoMessageConverter(projectRule.project).constructGarbageCollectionAnalyzerResult(resultMessage)
    Truth.assertThat(result).isEqualTo(resultConverted)
  }

  @Test
  fun testGarbageCollectionAnalyzerResultNullValues() {
    val result = GarbageCollectionAnalyzer.Result(listOf(GarbageCollectionData("name", 12345)), null, null)
    val resultMessage = BuildResultsProtoMessageConverter(projectRule.project).transformGarbageCollectionAnalyzerResult(result)
    val resultConverted = BuildResultsProtoMessageConverter(projectRule.project).constructGarbageCollectionAnalyzerResult(resultMessage)
    Truth.assertThat(result).isEqualTo(resultConverted)
  }

  @Test
  fun testProjectConfigurationAnalyzerResult() {
    val pluginsConfigurationDataMap = mutableMapOf<PluginData, Long>()
    val projectConfigurationData = mutableListOf<ProjectConfigurationData>()
    val allAppliedPlugins = mutableMapOf<String, List<PluginData>>()
    pluginsConfigurationDataMap[PluginData(PluginData.PluginType.BINARY_PLUGIN, "id name")] = 12345
    projectConfigurationData.add(ProjectConfigurationData("project path", 12345, listOf(), listOf()))
    allAppliedPlugins["id"] = listOf(PluginData(PluginData.PluginType.BINARY_PLUGIN, "id name"))
    val result = ProjectConfigurationAnalyzer.Result(pluginsConfigurationDataMap, projectConfigurationData, allAppliedPlugins)
    val resultMessage = BuildResultsProtoMessageConverter(projectRule.project).transformProjectConfigurationAnalyzerResult(result)
    val resultConverted = BuildResultsProtoMessageConverter(projectRule.project).constructProjectConfigurationAnalyzerResult(resultMessage)
    Truth.assertThat(result).isEqualTo(resultConverted)
  }

  @Test
  fun testNonCacheableTasksAnalyzerResult() {
    val taskDatum = TaskData(
      "task name",
      "project path",
      PluginData(PluginData.PluginType.BUILDSRC_PLUGIN, "id name"),
      12345,
      12345,
      TaskData.TaskExecutionMode.FULL,
      listOf("abc", "def", "ghi")
    )
    val cache = mutableMapOf<String, TaskData>()
    cache[taskDatum.getTaskPath()] = taskDatum
    val result = NoncacheableTasksAnalyzer.Result(listOf(taskDatum))
    val resultMessage = BuildResultsProtoMessageConverter(projectRule.project).transformNoncacheableTaskData(result.noncacheableTasks)
    val resultConverted = BuildResultsProtoMessageConverter(projectRule.project).constructNoncacheableTasksAnalyzerResult(resultMessage, cache)
    Truth.assertThat(result).isEqualTo(resultConverted)
  }

  @Test
  fun testTasksConfigurationIssuesAnalyzerResult() {
    val taskDatum = TaskData(
      "task name",
      "project path",
      PluginData(PluginData.PluginType.SCRIPT, "id name"),
      12345,
      12345,
      TaskData.TaskExecutionMode.FULL,
      listOf("abc", "def", "ghi")
    )
    val taskData = TasksSharingOutputData(taskDatum.getTaskPath(), listOf(taskDatum))
    val cache = mutableMapOf<String, TaskData>()
    cache[taskDatum.getTaskPath()] = taskDatum
    val result = TasksConfigurationIssuesAnalyzer.Result(listOf(taskData))
    val resultMessage = BuildResultsProtoMessageConverter(projectRule.project).transformTaskConfigurationAnalyzerResult(result.tasksSharingOutput)
    val resultConverted = BuildResultsProtoMessageConverter(projectRule.project).constructTasksConfigurationAnalyzerResult(resultMessage!!, cache)
    Truth.assertThat(result).isEqualTo(resultConverted)
  }

  @Test
  fun testDownloadsAnalyzerResult() {
    val repoResult = DownloadsAnalyzer.RepositoryResult(
      DownloadsAnalyzer.OtherRepository("repository"),
      listOf(
        DownloadsAnalyzer.DownloadResult(
          123,
          DownloadsAnalyzer.OtherRepository("repository"),
          "url",
          DownloadsAnalyzer.DownloadStatus.SUCCESS,
          1234,
          5678,
          "failure"
        )
      )
    )
    val downloadResult = DownloadsAnalyzer.ActiveResult(listOf(repoResult))
    val resultMessage = BuildResultsProtoMessageConverter(projectRule.project).transformDownloadsAnalyzerResult(downloadResult)
    val resultConverted = BuildResultsProtoMessageConverter(projectRule.project).constructDownloadsAnalyzerResult(resultMessage)
    Truth.assertThat(resultConverted).isEqualTo(downloadResult)
  }

  @Test
  fun testRequestData() {
    val requestData = GradleBuildInvoker.Request.RequestData(
      BuildMode.DEFAULT_BUILD_MODE,
      File("rootproject"),
      listOf("task1", "task2"),
      listOf("e1", "e2"),
      listOf("c1", "c2"),
      mapOf(Pair("a", "b"), Pair("c","d")),
      false
    )
    val requestDataMessage = BuildResultsProtoMessageConverter(projectRule.project).transformRequestData(requestData)
    val resultConverted = BuildResultsProtoMessageConverter(projectRule.project).constructRequestData(requestDataMessage)
    Truth.assertThat(resultConverted).isEqualTo(requestData)
  }

  @Test
  fun testRequestDataNullMode() {
    val requestData = GradleBuildInvoker.Request.RequestData(
      null,
      File("root-project"),
      emptyList()
    )
    val requestDataMessage = BuildResultsProtoMessageConverter(projectRule.project).transformRequestData(requestData)
    val resultConverted = BuildResultsProtoMessageConverter(projectRule.project).constructRequestData(requestDataMessage)
    Truth.assertThat(resultConverted).isEqualTo(requestData)
  }
}