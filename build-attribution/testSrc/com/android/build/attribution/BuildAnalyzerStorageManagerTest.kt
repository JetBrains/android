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
import com.android.build.attribution.analyzers.BuildEventsAnalyzersProxy
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
import com.google.common.truth.Truth
import com.android.build.attribution.data.PluginContainer
import com.android.build.attribution.data.PluginData
import com.android.build.attribution.data.ProjectConfigurationData
import com.android.build.attribution.data.TaskContainer
import com.android.build.attribution.data.TaskData
import com.android.build.attribution.data.TasksSharingOutputData
import com.android.tools.idea.Projects
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker
import com.android.tools.idea.gradle.util.BuildMode
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import org.jetbrains.kotlin.idea.gradleTooling.get
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.Duration
import java.util.UUID

class BuildAnalyzerStorageManagerTest {
  @get:Rule
  val projectRule = AndroidProjectRule.onDisk()

  @After
  fun cleanup() {
    StudioFlags.BUILD_ANALYZER_HISTORY.clearOverride()
  }

  @Test(expected = IllegalStateException::class)
  fun testNullBuildResultsResponse() {
    BuildAnalyzerStorageManager.getInstance(projectRule.project).getLatestBuildAnalysisResults()
  }

  @Test
  fun testBuildResultsAreStoredInMemory() {
    StudioFlags.BUILD_ANALYZER_HISTORY.override(true)
    val buildStartedTimestamp = 10L
    val buildDuration = 100L
    val buildFinishedTimestamp = buildStartedTimestamp + buildDuration
    val taskContainer = TaskContainer()
    val pluginContainer = PluginContainer()
    val analyzersProxy = BuildEventsAnalyzersProxy(taskContainer, pluginContainer)
    val setPrivateField: (Any, String, Any) -> Unit = { classInstance: Any, fieldName: String, newValue: Any ->
      val field = classInstance.javaClass.getDeclaredField(fieldName)
      field.isAccessible = true
      field.set(classInstance, newValue)
    }
    val criticalPathAnalyzer = analyzersProxy.criticalPathAnalyzer
    setPrivateField(criticalPathAnalyzer, "buildStartedTimestamp", buildStartedTimestamp)
    setPrivateField(criticalPathAnalyzer, "buildFinishedTimestamp", buildFinishedTimestamp)
    val request = GradleBuildInvoker.Request
      .builder(projectRule.project, Projects.getBaseDirPath(projectRule.project), "assembleDebug").build()
    BuildAnalyzerStorageManager.getInstance(projectRule.project)
      .storeNewBuildResults(analyzersProxy, "some buildID", BuildRequestHolder(request))
    Truth.assertThat(
      BuildAnalyzerStorageManager
        .getInstance(projectRule.project).getLatestBuildAnalysisResults().getBuildSessionID()
    ).isEqualTo("some buildID")
    Truth.assertThat(BuildAnalyzerStorageManager.getInstance(projectRule.project).getListOfHistoricBuildDescriptors()).isEqualTo(
      setOf(BuildDescriptor("some buildID", buildFinishedTimestamp, buildDuration))
    )
  }

  @Test
  fun testBuildResultsAreConvertedAndStoredInFile() {
    val storageManager = BuildAnalyzerStorageManagerImpl(projectRule.project)
    StudioFlags.BUILD_ANALYZER_HISTORY.override(true)
    val buildResults = constructBuildResultsObject()
    storageManager.storeBuildResultsInFile(buildResults)
    val buildResultsFromFile = storageManager.getHistoricBuildResultsFromFileByID(buildResults.getBuildSessionID())
    Truth.assertThat(buildResultsFromFile).isEqualTo(buildResults)
  }

  @Test
  fun testGetHistoricBuildByIDTrueFlag() {
    StudioFlags.BUILD_ANALYZER_HISTORY.override(true)
    val taskContainer = TaskContainer()
    val pluginContainer = PluginContainer()
    val analyzersProxy = BuildEventsAnalyzersProxy(taskContainer, pluginContainer)
    val request = GradleBuildInvoker.Request
      .builder(projectRule.project, Projects.getBaseDirPath(projectRule.project), "assembleDebug").build()
    BuildAnalyzerStorageManager.getInstance(projectRule.project)
      .storeNewBuildResults(analyzersProxy, "some buildID", BuildRequestHolder(request))
    Truth.assertThat(BuildAnalyzerStorageManager.getInstance(projectRule.project).getHistoricBuildResultByID("some buildID")
                       .getBuildSessionID()).isEqualTo("some buildID")
  }

  @Test
  fun testDoesNotStoreResultsWithFalseFlag() {
    StudioFlags.BUILD_ANALYZER_HISTORY.override(false)
    val taskContainer = TaskContainer()
    val pluginContainer = PluginContainer()
    val analyzersProxy = BuildEventsAnalyzersProxy(taskContainer, pluginContainer)
    val request = GradleBuildInvoker.Request
      .builder(projectRule.project, Projects.getBaseDirPath(projectRule.project), "assembleDebug").build()
    BuildAnalyzerStorageManager.getInstance(projectRule.project)
      .storeNewBuildResults(analyzersProxy, "some buildID", BuildRequestHolder(request))
    Truth.assertThat(BuildAnalyzerStorageManager.getInstance(projectRule.project).getListOfHistoricBuildDescriptors()).isEmpty()
  }

  @Test
  fun testListenerIsActive() {
    var listenerInvocationCounter = 0
    projectRule.project.messageBus.connect()
      .subscribe(BuildAnalyzerStorageManager.DATA_IS_READY_TOPIC, object : BuildAnalyzerStorageManager.Listener {
        override fun newDataAvailable() {
          listenerInvocationCounter += 1
        }
      })
    val taskContainer = TaskContainer()
    val pluginContainer = PluginContainer()
    val analyzersProxy = BuildEventsAnalyzersProxy(taskContainer, pluginContainer)
    val request = GradleBuildInvoker.Request
      .builder(projectRule.project, Projects.getBaseDirPath(projectRule.project), "assembleDebug").build()
    BuildAnalyzerStorageManager.getInstance(projectRule.project)
      .storeNewBuildResults(analyzersProxy, "some buildID", BuildRequestHolder(request))
    Truth.assertThat(listenerInvocationCounter).isEqualTo(1)
    Truth.assertThat(listenerInvocationCounter).isEqualTo(1)
  }

  private fun constructBuildResultsObject(): BuildAnalysisResults {
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
    val taskCategoryWarningsAnalyzerResult = TaskCategoryWarningsAnalyzer.Result(listOf())
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
      UUID.randomUUID().toString(),
      taskCache as HashMap<String, TaskData>,
      pluginCache as HashMap<String, PluginData>
    )
  }

  @Test
  fun testBuildResultsAreCleared() {
    StudioFlags.BUILD_ANALYZER_HISTORY.override(true)
    BuildAnalyzerStorageManager.getInstance(projectRule.project).storeNewBuildResults(
      BuildEventsAnalyzersProxy(TaskContainer(), PluginContainer()),
      "someID",
      BuildRequestHolder(
        GradleBuildInvoker.Request.builder(projectRule.project, Projects.getBaseDirPath(projectRule.project), "assembleDebug").build()
      )
    )
    BuildAnalyzerStorageManager.getInstance(projectRule.project).storeNewBuildResults(
      BuildEventsAnalyzersProxy(TaskContainer(), PluginContainer()),
      "someID2",
      BuildRequestHolder(
        GradleBuildInvoker.Request.builder(projectRule.project, Projects.getBaseDirPath(projectRule.project), "assembleDebug").build()
      )
    )
    Truth.assertThat(BuildAnalyzerStorageManager.getInstance(projectRule.project).getNumberOfBuildFilesStored()).isEqualTo(2)
    BuildAnalyzerStorageManager.getInstance(projectRule.project).clearBuildResultsStored()
    Truth.assertThat(BuildAnalyzerStorageManager.getInstance(projectRule.project).getNumberOfBuildFilesStored()).isEqualTo(0)
  }

  @Test
  fun testBuildFileSizeIsCalculated() {
    StudioFlags.BUILD_ANALYZER_HISTORY.override(true)
    BuildAnalyzerStorageManager.getInstance(projectRule.project).storeNewBuildResults(
      BuildEventsAnalyzersProxy(TaskContainer(), PluginContainer()),
      "someID",
      BuildRequestHolder(
        GradleBuildInvoker.Request.builder(projectRule.project, Projects.getBaseDirPath(projectRule.project), "assembleDebug").build()
      )
    )
    val fileSizeFirstIteration = BuildAnalyzerStorageManager.getInstance(projectRule.project).getCurrentBuildHistoryDataSize()
    Truth.assertThat(fileSizeFirstIteration).isGreaterThan(0)
    BuildAnalyzerStorageManager.getInstance(projectRule.project).storeNewBuildResults(
      BuildEventsAnalyzersProxy(TaskContainer(), PluginContainer()),
      "someID2",
      BuildRequestHolder(
        GradleBuildInvoker.Request.builder(projectRule.project, Projects.getBaseDirPath(projectRule.project), "assembleDebug").build()
      )
    )
    val fileSizeSecondIteration = BuildAnalyzerStorageManager.getInstance(projectRule.project).getCurrentBuildHistoryDataSize()
    Truth.assertThat(fileSizeSecondIteration).isGreaterThan(fileSizeFirstIteration)
  }

  @Test
  fun testNumberOfBuildResultsIsCalculated() {
    StudioFlags.BUILD_ANALYZER_HISTORY.override(true)
    BuildAnalyzerStorageManager.getInstance(projectRule.project).storeNewBuildResults(
      BuildEventsAnalyzersProxy(TaskContainer(), PluginContainer()),
      "someID",
      BuildRequestHolder(
        GradleBuildInvoker.Request.builder(projectRule.project, Projects.getBaseDirPath(projectRule.project), "assembleDebug").build()
      )
    )
    BuildAnalyzerStorageManager.getInstance(projectRule.project).storeNewBuildResults(
      BuildEventsAnalyzersProxy(TaskContainer(), PluginContainer()),
      "someID2",
      BuildRequestHolder(
        GradleBuildInvoker.Request.builder(projectRule.project, Projects.getBaseDirPath(projectRule.project), "assembleDebug").build()
      )
    )
    Truth.assertThat(BuildAnalyzerStorageManager.getInstance(projectRule.project).getNumberOfBuildFilesStored()).isEqualTo(2)
  }
}
