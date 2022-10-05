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

import com.android.build.attribution.analyzers.BuildEventsAnalyzersProxy
import com.android.build.attribution.data.BuildRequestHolder
import com.android.build.attribution.data.PluginContainer
import com.android.build.attribution.data.TaskContainer
import com.android.testutils.truth.PathSubject
import com.android.tools.idea.Projects
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth
import com.intellij.openapi.application.ApplicationManager
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.UUID
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class BuildAnalyzerStorageManagerTest {
  @get:Rule
  val projectRule = AndroidProjectRule.onDisk()

  private lateinit var previousSettingsState: BuildAnalyzerSettings.State

  @Before
  fun changeLimitSizeHistory() {
    previousSettingsState = BuildAnalyzerSettings.getInstance(projectRule.project).settingsState
    BuildAnalyzerSettings.getInstance(projectRule.project).settingsState.maxNumberOfBuildsStored = 10
  }

  @After
  fun cleanup() {
    StudioFlags.BUILD_ANALYZER_HISTORY.clearOverride()
    BuildAnalyzerSettings.getInstance(projectRule.project).settingsState = previousSettingsState
  }

  @Test(expected = IllegalStateException::class)
  fun testNullBuildResultsResponse() {
    BuildAnalyzerStorageManager.getInstance(projectRule.project).getLatestBuildAnalysisResults()
  }

  @Test
  fun testBuildResultsAreStored() {
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
                            buildAnalyzerResultData.buildRequestHolder).get()
    Truth.assertThat(
      BuildAnalyzerStorageManager
        .getInstance(projectRule.project).getSuccessfulResult().getBuildSessionID()
    ).isEqualTo("some buildID")
    Truth.assertThat(BuildAnalyzerStorageManager.getInstance(projectRule.project).getListOfHistoricBuildDescriptors()).isEqualTo(
      setOf(BuildDescriptorImpl("some buildID", buildFinishedTimestamp, buildDuration))
    )
  }

  @Test
  fun testDoesNotStoreResultsWithFalseFlag() {
    StudioFlags.BUILD_ANALYZER_HISTORY.override(false)
    val buildAnalyzerResultData = constructBuildAnalyzerResultData(buildID = "some buildID")
    BuildAnalyzerStorageManager.getInstance(projectRule.project)
      .storeNewBuildResults(buildAnalyzerResultData.analyzersProxy,
                            buildAnalyzerResultData.buildID,
                            buildAnalyzerResultData.buildRequestHolder).get()
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
                            buildAnalyzerResultData.buildRequestHolder).get()
    Truth.assertThat(listenerInvocationCounter).isEqualTo(1)
  }

  @Test
  fun testBuildResultsAreCleared() {
    StudioFlags.BUILD_ANALYZER_HISTORY.override(true)
    Truth.assertThat(BuildAnalyzerStorageManager.getInstance(projectRule.project).getNumberOfBuildResultsStored()).isEqualTo(0)
    BuildAnalyzerStorageManager.getInstance(projectRule.project).storeNewBuildResults(
      BuildEventsAnalyzersProxy(TaskContainer(), PluginContainer()),
      "someID",
      BuildRequestHolder(
        GradleBuildInvoker.Request.builder(projectRule.project, Projects.getBaseDirPath(projectRule.project), "assembleDebug").build()
      )
    ).get()
    BuildAnalyzerStorageManager.getInstance(projectRule.project).storeNewBuildResults(
      BuildEventsAnalyzersProxy(TaskContainer(), PluginContainer()),
      "someID2",
      BuildRequestHolder(
        GradleBuildInvoker.Request.builder(projectRule.project, Projects.getBaseDirPath(projectRule.project), "assembleDebug").build()
      )
    ).get()
    Truth.assertThat(BuildAnalyzerStorageManager.getInstance(projectRule.project).getNumberOfBuildResultsStored()).isEqualTo(2)
    BuildAnalyzerStorageManager.getInstance(projectRule.project).clearBuildResultsStored().get()
    Truth.assertThat(BuildAnalyzerStorageManager.getInstance(projectRule.project).getNumberOfBuildResultsStored()).isEqualTo(0)
  }

  @Test
  fun testCleanup() {
    StudioFlags.BUILD_ANALYZER_HISTORY.override(true)
    val storageManager = (BuildAnalyzerStorageManager.getInstance(projectRule.project) as BuildAnalyzerStorageManagerImpl)
    val limitSizeHistory = BuildAnalyzerSettings.getInstance(projectRule.project).settingsState.maxNumberOfBuildsStored
    var totalAdded = 0
    for (cntRecords in 1..limitSizeHistory) {
      val buildAnalyzerResultData = constructBuildAnalyzerResultData(totalAdded.toLong(),
                                                                     totalAdded.toLong() + 1,
                                                                     "$totalAdded")
      totalAdded++
      storageManager.storeNewBuildResults(
        buildAnalyzerResultData.analyzersProxy,
        buildAnalyzerResultData.buildID,
        buildAnalyzerResultData.buildRequestHolder).get()

      Truth.assertThat(storageManager.getListOfHistoricBuildDescriptors().size)
        .isEqualTo(cntRecords)
    }
    Truth.assertThat(storageManager.getListOfHistoricBuildDescriptors().size)
      .isEqualTo(limitSizeHistory)

    val addAdditional = 5

    val checkNotExists = mutableListOf<BuildDescriptor>()

    repeat(addAdditional) { countOver ->
      val oldest = storageManager.getListOfHistoricBuildDescriptors()
        .minByOrNull { descriptor -> descriptor.buildFinishedTimestamp }
      require(oldest != null)

      val dataFile = storageManager.fileManager.getFileFromBuildID(oldest.buildSessionID)
      PathSubject.assertThat(dataFile).exists()
      Truth.assertThat(oldest.buildSessionID).isEqualTo("$countOver")

      val buildAnalyzerResultData = constructBuildAnalyzerResultData(totalAdded.toLong(),
                                                                     totalAdded.toLong() + 1,
                                                                     "$totalAdded")
      totalAdded++
      storageManager.storeNewBuildResults(
        buildAnalyzerResultData.analyzersProxy,
        buildAnalyzerResultData.buildID,
        buildAnalyzerResultData.buildRequestHolder).get()

      checkNotExists.add(oldest)
    }

    for (oldest in checkNotExists) {
      Truth.assertThat(
        storageManager.descriptors.find { it.buildSessionID == oldest.buildSessionID }).isNull() // TODO mock and check cleanups separate
    }

    Truth.assertThat(storageManager.getNumberOfBuildResultsStored()).isAtMost(limitSizeHistory)
  }

  @Test
  fun fileCountLimitChanges() {
    StudioFlags.BUILD_ANALYZER_HISTORY.override(true)
    val storageManager = (BuildAnalyzerStorageManager.getInstance(projectRule.project) as BuildAnalyzerStorageManagerImpl)
    val limitSizeHistory = BuildAnalyzerSettings.getInstance(projectRule.project).settingsState.maxNumberOfBuildsStored
    for (totalAdded in 0 until limitSizeHistory) {
      val buildAnalyzerResultData = constructBuildAnalyzerResultData(totalAdded.toLong(),
                                                                     totalAdded.toLong() + 1,
                                                                     "$totalAdded")
      storageManager.storeNewBuildResults(
        buildAnalyzerResultData.analyzersProxy,
        buildAnalyzerResultData.buildID,
        buildAnalyzerResultData.buildRequestHolder).get()

      Truth.assertThat(storageManager.getListOfHistoricBuildDescriptors().size)
        .isEqualTo(totalAdded + 1)
    }
    Truth.assertThat(storageManager.getListOfHistoricBuildDescriptors().size)
      .isEqualTo(limitSizeHistory)
    val newLimitSizeHistory = limitSizeHistory / 2
    BuildAnalyzerSettings.getInstance(projectRule.project).settingsState.maxNumberOfBuildsStored = newLimitSizeHistory
    BuildAnalyzerStorageManager.getInstance(projectRule.project).onSettingsChange().get()
    Truth.assertThat(storageManager.getListOfHistoricBuildDescriptors().size)
      .isEqualTo(newLimitSizeHistory)

    for (recordNumber in (limitSizeHistory - newLimitSizeHistory) until limitSizeHistory) {
      val dataFile = storageManager.fileManager.getFileFromBuildID("$recordNumber")
      PathSubject.assertThat(dataFile).exists()
    }
  }

  @Test
  fun `test synchronous adding`() {
    StudioFlags.BUILD_ANALYZER_HISTORY.override(true)
    val limitSizeHistory = BuildAnalyzerSettings.getInstance(projectRule.project).settingsState.maxNumberOfBuildsStored
    val operationManager = OperationManager()
    val totalAdded = AtomicLong(0)
    val task1: () -> Boolean = {
      repeat(limitSizeHistory) {
        operationManager.store(storeBuildAnalyzerResultData(buildID = "task1-$it",
                                                            buildFinishedTimestamp = totalAdded.getAndIncrement()))
      }
      true
    }
    val task2: () -> Boolean = {
      repeat(limitSizeHistory) {
        operationManager.store(storeBuildAnalyzerResultData(buildID = "task2-$it",
                                                            buildFinishedTimestamp = totalAdded.getAndIncrement()))
      }
      true
    }
    val thread1 = ApplicationManager.getApplication().executeOnPooledThread(task1)
    val thread2 = ApplicationManager.getApplication().executeOnPooledThread(task2)
    Truth.assertThat(thread1.get(2, TimeUnit.SECONDS)).isTrue()
    Truth.assertThat(thread2.get(2, TimeUnit.SECONDS)).isTrue()
    operationManager.awaitAll()

    val descriptorsTimeFinished = BuildAnalyzerStorageManager.getInstance(projectRule.project)
      .getListOfHistoricBuildDescriptors().map { it.buildFinishedTimestamp }
    Truth.assertThat(descriptorsTimeFinished).containsExactlyElementsIn((limitSizeHistory) until totalAdded.get())
  }

  @Test
  fun `test synchronous adding and deleting`() {
    StudioFlags.BUILD_ANALYZER_HISTORY.override(true)
    val limitSizeHistory = BuildAnalyzerSettings.getInstance(projectRule.project).settingsState.maxNumberOfBuildsStored
    val operationManager = OperationManager()
    val totalAdded = AtomicLong(0)
    val task1: () -> Boolean = {
      repeat(limitSizeHistory) {
        operationManager.store(storeBuildAnalyzerResultData(buildID = "task1-$it",
                                                            buildFinishedTimestamp = totalAdded.getAndIncrement()))
      }
      (0 until limitSizeHistory).reversed().forEach {
        (BuildAnalyzerStorageManager.getInstance(projectRule.project) as BuildAnalyzerStorageManagerImpl)
                                 .deleteHistoricBuildResultByID("task1-$it")
      }
      true
    }
    val task2: () -> Boolean = {
      repeat(limitSizeHistory * 2) {
        operationManager.store(storeBuildAnalyzerResultData(buildID = "task2-$it",
                                                            buildFinishedTimestamp = totalAdded.getAndIncrement()))
      }
      true
    }
    val thread1 = ApplicationManager.getApplication().executeOnPooledThread(task1)
    val thread2 = ApplicationManager.getApplication().executeOnPooledThread(task2)
    Truth.assertThat(thread1.get(2, TimeUnit.SECONDS)).isTrue()
    Truth.assertThat(thread2.get(2, TimeUnit.SECONDS)).isTrue()
    operationManager.awaitAll()
    val descriptors = BuildAnalyzerStorageManager.getInstance(projectRule.project).getListOfHistoricBuildDescriptors()
    assertDescriptorsAreSequentiallyThatStartsWith(descriptors, "task2-", limitSizeHistory)
  }

  @Test
  fun `test clear while adding`() {
    StudioFlags.BUILD_ANALYZER_HISTORY.override(true)
    val limitSizeHistory = BuildAnalyzerSettings.getInstance(projectRule.project).settingsState.maxNumberOfBuildsStored
    val operatorManager = OperationManager()
    val totalAdded = AtomicLong(0)
    val task1: () -> Boolean = {
      repeat(2 * limitSizeHistory) {
        operatorManager.store(storeBuildAnalyzerResultData(buildID = "task1-$it",
                                                           buildFinishedTimestamp = totalAdded.getAndIncrement()))
      }
      true
    }
    val task2: () -> Boolean = {
      repeat(limitSizeHistory) {
        operatorManager.store(storeBuildAnalyzerResultData(buildID = "task2-$it",
                                                           buildFinishedTimestamp = totalAdded.getAndIncrement()))
      }
      operatorManager.store(BuildAnalyzerStorageManager.getInstance(projectRule.project).clearBuildResultsStored())
      true
    }
    val thread1 = ApplicationManager.getApplication().executeOnPooledThread(task1)
    val thread2 = ApplicationManager.getApplication().executeOnPooledThread(task2)
    Truth.assertThat(thread1.get(2, TimeUnit.SECONDS)).isTrue()
    Truth.assertThat(thread2.get(2, TimeUnit.SECONDS)).isTrue()
    operatorManager.awaitAll()
    val descriptors = BuildAnalyzerStorageManager.getInstance(projectRule.project).getListOfHistoricBuildDescriptors()
    assertDescriptorsAreSequentiallyThatStartsWith(descriptors, "task1-", limitSizeHistory)
  }

  private class OperationManager {
    private val operations = mutableListOf<Future<*>>()

    fun store(f: Future<*>) {
      synchronized(this) {
        operations.add(f)
      }
    }

    fun awaitAll() {
      synchronized(this) {
        operations.forEach { it.get() }
        operations.clear()
      }
    }
  }

  private fun assertDescriptorsAreSequentiallyThatStartsWith(descriptors: Set<BuildDescriptor>, startsWith: String, limitSizeHistory: Int) {
    val idsStartsWith = descriptors.filter { it.buildSessionID.startsWith(startsWith) }.map { it.buildSessionID.substringAfter(startsWith).toLong() }

    Truth.assertThat(descriptors.size).isAtMost(limitSizeHistory)
    if (idsStartsWith.isNotEmpty()) { // Check that ids goes sequentially
      val minimumId = idsStartsWith.minOf { it }
      Truth.assertThat(idsStartsWith.sorted()).containsExactlyElementsIn((minimumId until (minimumId + idsStartsWith.size)))
    }
  }

  data class BuildAnalyzerResultData(
    val analyzersProxy: BuildEventsAnalyzersProxy,
    val buildID: String,
    val buildRequestHolder: BuildRequestHolder
  )

  private fun constructBuildAnalyzerResultData(buildStartedTimestamp: Long = 12345,
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

  private fun storeBuildAnalyzerResultData(buildStartedTimestamp: Long = 12345,
                                           buildFinishedTimestamp: Long = 12345,
                                           buildID: String = UUID.randomUUID().toString()): Future<BuildAnalysisResults> {
    val result = constructBuildAnalyzerResultData(buildStartedTimestamp, buildFinishedTimestamp, buildID)
    return BuildAnalyzerStorageManager.getInstance(projectRule.project).storeNewBuildResults(
      result.analyzersProxy,
      result.buildID,
      result.buildRequestHolder)
  }
}