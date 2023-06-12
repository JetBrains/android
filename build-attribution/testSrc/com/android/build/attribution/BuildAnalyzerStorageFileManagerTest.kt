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
import com.android.build.attribution.analyzers.AnalyzerNotRun
import com.android.build.attribution.analyzers.AnnotationProcessorsAnalyzer
import com.android.build.attribution.analyzers.CriticalPathAnalyzer
import com.android.build.attribution.analyzers.DownloadsAnalyzer
import com.android.build.attribution.analyzers.GarbageCollectionAnalyzer
import com.android.build.attribution.analyzers.JetifierUsageAnalyzerResult
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
import com.android.build.attribution.data.PluginData
import com.android.build.attribution.data.ProjectConfigurationData
import com.android.build.attribution.data.TaskData
import com.android.build.attribution.data.TasksSharingOutputData
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker
import com.android.tools.idea.gradle.util.BuildMode
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.utils.FileUtils
import com.google.common.truth.Truth
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.time.Duration
import java.util.UUID

class BuildAnalyzerStorageFileManagerTest {
  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  @get:Rule
  var tmpFolder: TemporaryFolder = TemporaryFolder()

  @Test
  fun testBuildResultsAreConvertedAndStoredInFile() {
    val storageManager = BuildAnalyzerStorageFileManager(tmpFolder.root)
    StudioFlags.BUILD_ANALYZER_HISTORY.override(true)
    val buildResults = constructBuildResultsObject()
    storageManager.storeBuildResultsInFile(buildResults)
    val buildResultsFromFile = storageManager.getHistoricBuildResultByID(buildResults.getBuildSessionID())
    Truth.assertThat(buildResultsFromFile).isEqualTo(buildResults)
  }

  @Test
  fun testBuildFileSizeIsCalculated() {
    val fileManager = BuildAnalyzerStorageFileManager(tmpFolder.root)
    val buildID1 = "build_number_1"
    val buildID2 = "build_number_2"
    fileManager.storeBuildResultsInFile(constructBuildResultsObject(buildID1))
    val fileSizeFirstIteration = fileManager.getCurrentBuildHistoryDataSize()
    Truth.assertThat(fileSizeFirstIteration).isEqualTo(fileManager.getFileFromBuildID(buildID1).length())

    fileManager.storeBuildResultsInFile(constructBuildResultsObject(buildID2))
    val fileSizeSecondIteration = fileManager.getCurrentBuildHistoryDataSize()
    Truth.assertThat(fileSizeSecondIteration).isEqualTo(fileSizeFirstIteration + fileManager.getFileFromBuildID(buildID2).length())
  }

  @Test
  fun testNumberOfBuildResultsIsCalculated() {
    val fileManager = BuildAnalyzerStorageFileManager(tmpFolder.root)
    val buildID1 = "build_number_1"
    val buildID2 = "build_number_2"
    fileManager.storeBuildResultsInFile(constructBuildResultsObject(buildID1))
    Truth.assertThat(countOfFiles(tmpFolder.root)).isEqualTo(1)

    fileManager.storeBuildResultsInFile(constructBuildResultsObject(buildID2))
    Truth.assertThat(countOfFiles(tmpFolder.root)).isEqualTo(2)
  }

  @Test
  fun deleteHistoricBuildResultByID() {
    val fileManager = BuildAnalyzerStorageFileManager(tmpFolder.root)
    val buildID1 = "build_number_1"
    val buildID2 = "build_number_2"
    fileManager.storeBuildResultsInFile(constructBuildResultsObject(buildID1))
    Truth.assertThat(countOfFiles(tmpFolder.root)).isEqualTo(1)

    fileManager.storeBuildResultsInFile(constructBuildResultsObject(buildID2))
    Truth.assertThat(countOfFiles(tmpFolder.root)).isEqualTo(2)
    fileManager.deleteHistoricBuildResultByID(buildID1)
    Truth.assertThat(countOfFiles(tmpFolder.root)).isEqualTo(1)
    fileManager.deleteHistoricBuildResultByID(buildID2)
    Truth.assertThat(countOfFiles(tmpFolder.root)).isEqualTo(0)
  }

  @Test(expected = Throwable::class)
  fun `store should throw error if can't write to directory`() {
    Truth.assertThat(tmpFolder.root.setReadOnly()).isTrue()
    val storageManager = BuildAnalyzerStorageFileManager(tmpFolder.root)
    val buildResults = constructBuildResultsObject()
    storageManager.storeBuildResultsInFile(buildResults)
  }

  @Test(expected = IOException::class)
  fun `get should throw error if file doesn't exist`() {
    val storageManager = BuildAnalyzerStorageFileManager(tmpFolder.root)
    storageManager.getHistoricBuildResultByID("no-such-file")
  }

  @Test
  fun `delete should not throw error if file doesn't exist`() {
    val fileManager = BuildAnalyzerStorageFileManager(tmpFolder.root)
    fileManager.deleteHistoricBuildResultByID("no-such-file")
  }

  private fun countOfFiles(dir: File) =
    FileUtils.getAllFiles(dir).size()

  private fun constructBuildResultsObject(buildID: String = UUID.randomUUID().toString()): BuildAnalysisResults {
    val requestHolder = BuildRequestHolder(
      GradleBuildInvoker.Request(
        BuildMode.DEFAULT_BUILD_MODE,
        projectRule.project,
        File(projectRule.project.projectFilePath),
        emptyList(),
        ExternalSystemTaskId.create(ProjectSystemId(""), ExternalSystemTaskType.EXECUTE_TASK, projectRule.project)
      )
    )
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
    val annotationProcessorsAnalyzerResult = AnnotationProcessorsAnalyzer.Result(annotationProcessorData,
                                                                                 nonIncrementalAnnotationProcessorData)
    val taskCache = mutableMapOf<String, TaskData>()
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
    taskCache[alwaysRunTaskDatum.taskData.getTaskPath()] = alwaysRunTaskDatum.taskData
    alwaysRunTaskData.add(alwaysRunTaskDatum)
    val alwaysRunTaskDataResult = AlwaysRunTasksAnalyzer.Result(alwaysRunTaskData)
    val pluginCache = mutableMapOf<String, PluginData>()
    val criticalPathData = mutableListOf<TaskData>()
    val pluginDatum = PluginData(PluginData.PluginType.BINARY_PLUGIN, "id name")
    val criticalPathDatum = TaskData(
      "task name 2",
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
      listOf(PluginBuildData(pluginDatum, 12345)),
      12345,
      12345
    )
    val taskDatum = TaskData(
      "task name 3",
      "project path",
      PluginData(PluginData.PluginType.BUILDSRC_PLUGIN, "id name"),
      12345,
      12345,
      TaskData.TaskExecutionMode.FULL,
      listOf("abc", "def", "ghi")
    )
    val noncacheableTasksAnalyzerResult = NoncacheableTasksAnalyzer.Result(listOf(taskDatum))
    val garbageCollectionAnalyzerResult = GarbageCollectionAnalyzer.Result(listOf(GarbageCollectionData("name", 12345)), 12345, true)
    val pluginsConfigurationDataMap = mutableMapOf<PluginData, Long>()
    val projectConfigurationData = mutableListOf<ProjectConfigurationData>()
    val allAppliedPlugins = mutableMapOf<String, List<PluginData>>()
    pluginsConfigurationDataMap[PluginData(PluginData.PluginType.BINARY_PLUGIN, "id name")] = 12345
    projectConfigurationData.add(ProjectConfigurationData("project path", 12345, listOf(), listOf()))
    allAppliedPlugins["id"] = listOf(PluginData(PluginData.PluginType.BINARY_PLUGIN, "id name"))
    val projectConfigurationAnalyzerResult = ProjectConfigurationAnalyzer.Result(
      pluginsConfigurationDataMap,
      projectConfigurationData,
      allAppliedPlugins
    )
    val taskData = TasksSharingOutputData(taskDatum.getTaskPath(), listOf(taskDatum))
    taskCache[taskDatum.getTaskPath()] = taskDatum
    val taskConfigurationAnalyzerResult = TasksConfigurationIssuesAnalyzer.Result(listOf(taskData))
    val configurationCachingCompatibilityAnalyzerResult = NoDataFromSavedResult
    taskCache[taskDatum.getTaskPath()] = taskDatum
    val jetifierUsageAnalyzerResult = JetifierUsageAnalyzerResult(AnalyzerNotRun)
    val downloadAnalyzerResult = DownloadsAnalyzer.AnalyzerIsDisabled
    val taskCategoryWarningsAnalyzerResult = TaskCategoryWarningsAnalyzer.NoDataFromAGP
    return BuildAnalysisResults(
      requestHolder.buildRequest.data,
      annotationProcessorsAnalyzerResult,
      alwaysRunTaskDataResult,
      criticalPathAnalyzerResult,
      noncacheableTasksAnalyzerResult,
      garbageCollectionAnalyzerResult,
      projectConfigurationAnalyzerResult,
      taskConfigurationAnalyzerResult,
      configurationCachingCompatibilityAnalyzerResult,
      jetifierUsageAnalyzerResult,
      downloadAnalyzerResult,
      taskCategoryWarningsAnalyzerResult,
      buildID,
      taskCache as HashMap<String, TaskData>,
      pluginCache as HashMap<String, PluginData>
    )
  }
}