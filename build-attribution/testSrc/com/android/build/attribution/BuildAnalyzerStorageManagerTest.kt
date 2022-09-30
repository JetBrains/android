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
import com.intellij.testFramework.UsefulTestCase.assertThrows
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.IOException
import java.util.UUID

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
        buildAnalyzerResultData.buildRequestHolder)

      Truth.assertThat(storageManager.getListOfHistoricBuildDescriptors().size)
        .isEqualTo(cntRecords)
    }
    Truth.assertThat(storageManager.getListOfHistoricBuildDescriptors().size)
      .isEqualTo(limitSizeHistory)

    val addAdditional = 5
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
        buildAnalyzerResultData.buildRequestHolder)

      PathSubject.assertThat(File(oldest.buildSessionID)).doesNotExist()
      Truth.assertThat(storageManager.descriptors.find { it.buildSessionID == oldest.buildSessionID }).isNull()
      assertThrows(IOException::class.java) {
        BuildAnalyzerStorageManager.getInstance(projectRule.project).getHistoricBuildResultByID(oldest.buildSessionID)
      }
    }

    // Check that all needed files are stored
    for (recordNumber in addAdditional until totalAdded) {
      val dataFile = storageManager.fileManager.getFileFromBuildID("$recordNumber")
      PathSubject.assertThat(dataFile).exists()
    }
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
        buildAnalyzerResultData.buildRequestHolder)

      Truth.assertThat(storageManager.getListOfHistoricBuildDescriptors().size)
        .isEqualTo(totalAdded + 1)
    }
    Truth.assertThat(storageManager.getListOfHistoricBuildDescriptors().size)
      .isEqualTo(limitSizeHistory)
    val newLimitSizeHistory = limitSizeHistory / 2
    BuildAnalyzerSettings.getInstance(projectRule.project).settingsState.maxNumberOfBuildsStored = newLimitSizeHistory
    BuildAnalyzerConfigurableProvider(projectRule.project).createConfigurable().apply() // Apply settings change
    Truth.assertThat(storageManager.getListOfHistoricBuildDescriptors().size)
      .isEqualTo(newLimitSizeHistory)

    for (recordNumber in (limitSizeHistory - newLimitSizeHistory) until limitSizeHistory) {
      val dataFile = storageManager.fileManager.getFileFromBuildID("$recordNumber")
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
}
