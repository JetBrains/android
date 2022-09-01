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
import com.android.build.attribution.data.PluginContainer
import com.android.build.attribution.data.PluginData
import com.android.build.attribution.data.ProjectConfigurationData
import com.android.build.attribution.data.TaskContainer
import com.android.build.attribution.data.TaskData
import com.android.build.attribution.data.TasksSharingOutputData
import com.android.testutils.truth.PathSubject
import com.android.tools.idea.Projects
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker
import com.android.tools.idea.gradle.util.BuildMode
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.testFramework.UsefulTestCase.assertThrows
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.IOException
import java.time.Duration
import java.util.UUID
import kotlin.collections.HashMap
import kotlin.collections.List
import kotlin.collections.emptyList
import kotlin.collections.listOf
import kotlin.collections.mutableListOf
import kotlin.collections.mutableMapOf
import kotlin.collections.set
import kotlin.collections.setOf

class BuildAnalyzerStorageManagerTest {
  @get:Rule
  val projectRule = AndroidProjectRule.onDisk()

  @Before
  fun changeLimitSizeHistory() {
    BuildDescriptorStorageService.getInstance(projectRule.project).limitSizeHistory = 10
  }

  @After
  fun cleanup() {
    StudioFlags.BUILD_ANALYZER_HISTORY.clearOverride()
    BuildDescriptorStorageService.getInstance(projectRule.project).onSettingsChange()
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
    val buildAnalyzerResultData = constructBuildAnalyzerResultData(buildStartedTimestamp,
                                                                   buildFinishedTimestamp,
                                                                   "some buildID")
    BuildAnalyzerStorageManager.getInstance(projectRule.project)
      .storeNewBuildResults(buildAnalyzerResultData.analyzersProxy,
                            buildAnalyzerResultData.buildID,
                            buildAnalyzerResultData.buildRequestHolder)
    Truth.assertThat(
      BuildAnalyzerStorageManager
        .getInstance(projectRule.project).getSuccessfulResult().getBuildSessionID()
    ).isEqualTo("some buildID")
    Truth.assertThat(BuildAnalyzerStorageManager.getInstance(projectRule.project).getListOfHistoricBuildDescriptors()).isEqualTo(
      setOf(BuildDescriptorImpl("some buildID", buildFinishedTimestamp, buildDuration))
    )
  }

  @Test
  fun testBuildResultsAreConvertedAndStoredInFile() {
    val storageManager = BuildAnalyzerStorageManagerImpl(projectRule.project)
    StudioFlags.BUILD_ANALYZER_HISTORY.override(true)
    val buildResults = constructBuildResultsObject()
    storageManager.storeBuildResultsInFile(buildResults)
    val buildResultsFromFile = storageManager.getHistoricBuildResultByID(buildResults.getBuildSessionID())
    Truth.assertThat(buildResultsFromFile).isEqualTo(buildResults)
  }

  @Test
  fun testGetHistoricBuildByIDTrueFlag() {
    StudioFlags.BUILD_ANALYZER_HISTORY.override(true)
    val buildAnalyzerResultData = constructBuildAnalyzerResultData(buildID = "some buildID")
    BuildAnalyzerStorageManager.getInstance(projectRule.project)
      .storeNewBuildResults(buildAnalyzerResultData.analyzersProxy,
                            buildAnalyzerResultData.buildID,
                            buildAnalyzerResultData.buildRequestHolder)
    Truth.assertThat(BuildAnalyzerStorageManager.getInstance(projectRule.project).getHistoricBuildResultByID("some buildID")
                       .getBuildSessionID()).isEqualTo("some buildID")
  }

  @Test
  fun testDoesNotStoreResultsWithFalseFlag() {
    StudioFlags.BUILD_ANALYZER_HISTORY.override(false)
    val buildAnalyzerResultData = constructBuildAnalyzerResultData(buildID = "some buildID")
    BuildAnalyzerStorageManager.getInstance(projectRule.project)
      .storeNewBuildResults(buildAnalyzerResultData.analyzersProxy,
                            buildAnalyzerResultData.buildID,
                            buildAnalyzerResultData.buildRequestHolder)
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
    Truth.assertThat(listenerInvocationCounter).isEqualTo(0)
    val buildAnalyzerResultData = constructBuildAnalyzerResultData(buildID = "some buildID")
    BuildAnalyzerStorageManager.getInstance(projectRule.project)
      .storeNewBuildResults(buildAnalyzerResultData.analyzersProxy,
                            buildAnalyzerResultData.buildID,
                            buildAnalyzerResultData.buildRequestHolder)
    Truth.assertThat(listenerInvocationCounter).isEqualTo(1)
  }

  @Test
  fun testBuildResultsAreCleared() {
    StudioFlags.BUILD_ANALYZER_HISTORY.override(true)
    Truth.assertThat(BuildAnalyzerStorageManager.getInstance(projectRule.project).getNumberOfBuildFilesStored()).isEqualTo(0)
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

  @Test
  fun testCleanup() {
    StudioFlags.BUILD_ANALYZER_HISTORY.override(true)
    val limitSizeHistory = BuildDescriptorStorageService.getInstance(projectRule.project).limitSizeHistory
    var totalAdded = 0
    for (cntRecords in 1..limitSizeHistory) {
      val buildAnalyzerResultData = constructBuildAnalyzerResultData(totalAdded.toLong(),
                                                                     totalAdded.toLong() + 1,
                                                                     "$totalAdded")
      totalAdded++
      BuildAnalyzerStorageManager.getInstance(projectRule.project).storeNewBuildResults(
        buildAnalyzerResultData.analyzersProxy,
        buildAnalyzerResultData.buildID,
        buildAnalyzerResultData.buildRequestHolder)

      Truth.assertThat(BuildAnalyzerStorageManager.getInstance(projectRule.project).getListOfHistoricBuildDescriptors().size)
        .isEqualTo(cntRecords)
    }
    Truth.assertThat(BuildAnalyzerStorageManager.getInstance(projectRule.project).getListOfHistoricBuildDescriptors().size)
      .isEqualTo(limitSizeHistory)

    val addAdditional = 5
    repeat(addAdditional) { countOver ->
      val oldest = BuildAnalyzerStorageManager.getInstance(projectRule.project).getListOfHistoricBuildDescriptors()
        .minByOrNull { descriptor -> descriptor.buildFinishedTimestamp }
      require(oldest != null)

      val dataFile = getFileFromBuildID(oldest.buildSessionID)
      PathSubject.assertThat(dataFile).exists()
      Truth.assertThat(oldest.buildSessionID).isEqualTo("$countOver")

      val buildAnalyzerResultData = constructBuildAnalyzerResultData(totalAdded.toLong(),
                                                                     totalAdded.toLong() + 1,
                                                                     "$totalAdded")
      totalAdded++
      BuildAnalyzerStorageManager.getInstance(projectRule.project).storeNewBuildResults(
        buildAnalyzerResultData.analyzersProxy,
        buildAnalyzerResultData.buildID,
        buildAnalyzerResultData.buildRequestHolder)

      PathSubject.assertThat(File(oldest.buildSessionID)).doesNotExist()
      Truth.assertThat(BuildDescriptorStorageService.getInstance(projectRule.project).getDescriptors().find { it.buildSessionID == oldest.buildSessionID }).isNull()
      assertThrows(IOException::class.java) {
        BuildAnalyzerStorageManager.getInstance(projectRule.project).getHistoricBuildResultByID(oldest.buildSessionID)
      }
    }

    // Check that all needed files are stored
    for (recordNumber in addAdditional until totalAdded) {
      val dataFile = getFileFromBuildID("$recordNumber")
      PathSubject.assertThat(dataFile).exists()
    }
  }

  @Test
  fun fileCountLimitChanges() {
    StudioFlags.BUILD_ANALYZER_HISTORY.override(true)
    val limitSizeHistory = BuildDescriptorStorageService.getInstance(projectRule.project).limitSizeHistory
    for (totalAdded in 0 until limitSizeHistory) {
      val buildAnalyzerResultData = constructBuildAnalyzerResultData(totalAdded.toLong(),
                                                                     totalAdded.toLong() + 1,
                                                                     "$totalAdded")
      BuildAnalyzerStorageManager.getInstance(projectRule.project).storeNewBuildResults(
        buildAnalyzerResultData.analyzersProxy,
        buildAnalyzerResultData.buildID,
        buildAnalyzerResultData.buildRequestHolder)

      Truth.assertThat(BuildAnalyzerStorageManager.getInstance(projectRule.project).getListOfHistoricBuildDescriptors().size)
        .isEqualTo(totalAdded + 1)
    }
    Truth.assertThat(BuildAnalyzerStorageManager.getInstance(projectRule.project).getListOfHistoricBuildDescriptors().size)
      .isEqualTo(limitSizeHistory)
    val newLimitSizeHistory = limitSizeHistory / 2
    BuildAnalyzerSettings.getInstance(projectRule.project).settingsState.maxNumberOfBuildsStored = newLimitSizeHistory
    BuildAnalyzerConfigurableProvider(projectRule.project).createConfigurable().apply() // Apply settings change
    Truth.assertThat(BuildAnalyzerStorageManager.getInstance(projectRule.project).getListOfHistoricBuildDescriptors().size)
      .isEqualTo(newLimitSizeHistory)

    for (recordNumber in (limitSizeHistory - newLimitSizeHistory) until limitSizeHistory) {
      val dataFile = getFileFromBuildID("$recordNumber")
      PathSubject.assertThat(dataFile).exists()
    }
  }

  data class BuildAnalyzerResultData(
    val analyzersProxy: BuildEventsAnalyzersProxy,
    val buildID: String,
    val buildRequestHolder: BuildRequestHolder
  )

  fun constructBuildAnalyzerResultData(buildStartedTimestamp: Long = 12345,
                                       buildFinishedTimestamp: Long = 12345,
                                       buildID: String = UUID.randomUUID().toString()): BuildAnalyzerResultData {
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
    return BuildAnalyzerResultData(analyzersProxy, buildID, BuildRequestHolder(request))
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

  /**
   * Call [BuildAnalyzerStorageManagerImpl.getFileFromBuildID] on passed [buildID]
   */
  private fun getFileFromBuildID(buildID: String) : File {
    val getFileFromBuildIDMethod = BuildAnalyzerStorageManager.getInstance(projectRule.project)::class.java
      .getDeclaredMethod("getFileFromBuildID", String::class.java)
    getFileFromBuildIDMethod.isAccessible = true
    return getFileFromBuildIDMethod.invoke(BuildAnalyzerStorageManager.getInstance(projectRule.project),
                                           buildID) as File
  }
}
