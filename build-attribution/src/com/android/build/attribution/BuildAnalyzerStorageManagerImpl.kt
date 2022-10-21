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

import com.android.annotations.concurrency.Slow
import com.android.build.attribution.analyzers.BuildEventsAnalyzersProxy
import com.android.build.attribution.analyzers.DownloadsAnalyzer
import com.android.build.attribution.data.BuildRequestHolder
import com.android.tools.idea.flags.StudioFlags
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.getProjectDataPath
import java.io.IOException

class BuildAnalyzerStorageManagerImpl(
  val project: Project
) : BuildAnalyzerStorageManager {
  private var buildResults: AbstractBuildAnalysisResult? = null
  val fileManager = BuildAnalyzerStorageFileManager(project.getProjectDataPath("build-analyzer-history-data").toFile())
  @VisibleForTesting
  val descriptors = BuildDescriptorStorageService.getInstance(project).state.descriptors

  init {
    onSettingsChange()
  }

  private fun notifyDataListeners() {
    project.messageBus.syncPublisher(BuildAnalyzerStorageManager.DATA_IS_READY_TOPIC).newDataAvailable()
  }

  private fun createBuildResultsObject(
    analyzersProxy: BuildEventsAnalyzersProxy,
    buildSessionID: String,
    requestHolder: BuildRequestHolder
  ): BuildAnalysisResults {
    return BuildAnalysisResults(
      buildRequestData = requestHolder.buildRequest.data,
      annotationProcessorAnalyzerResult = analyzersProxy.annotationProcessorsAnalyzer.result,
      alwaysRunTasksAnalyzerResult = analyzersProxy.alwaysRunTasksAnalyzer.result,
      criticalPathAnalyzerResult = analyzersProxy.criticalPathAnalyzer.result,
      noncacheableTasksAnalyzerResult = analyzersProxy.noncacheableTasksAnalyzer.result,
      garbageCollectionAnalyzerResult = analyzersProxy.garbageCollectionAnalyzer.result,
      projectConfigurationAnalyzerResult = analyzersProxy.projectConfigurationAnalyzer.result,
      tasksConfigurationIssuesAnalyzerResult = analyzersProxy.tasksConfigurationIssuesAnalyzer.result,
      configurationCachingCompatibilityAnalyzerResult = analyzersProxy.configurationCachingCompatibilityAnalyzer.result,
      jetifierUsageAnalyzerResult = analyzersProxy.jetifierUsageAnalyzer.result,
      downloadsAnalyzerResult = analyzersProxy.downloadsAnalyzer?.result ?: DownloadsAnalyzer.AnalyzerIsDisabled,
      taskCategoryWarningsAnalyzerResult = analyzersProxy.taskCategoryWarningsAnalyzer.result,
      buildSessionID = buildSessionID,
      taskMap = analyzersProxy.taskContainer.allTasks,
      pluginMap = analyzersProxy.pluginContainer.allPlugins
    )
  }

  /**
   * Returns the analysis results from the latest build in the form of a BuildAnalysisResults object. There are no arguments.
   * If no build results have been stored, then an IllegalStateException is thrown as there is nothing to return.
   * Only works with in memory IDs.
   *
   * @return BuildAnalysisResults
   * @exception IllegalStateException
   */
  override fun getLatestBuildAnalysisResults(): AbstractBuildAnalysisResult {
    if (hasData()) return buildResults!!
    else throw IllegalStateException("Storage Manager does not have data to return.")
  }

  /**
   * Attempts to delete the contents of build-analyzer-history-data. Returns true if the deletion is
   * successful, and otherwise returns false.
   *
   * @return Boolean
   */
  @Slow
  override fun clearBuildResultsStored(): Boolean {
    fileManager.clearAll()
    descriptors.clear()
    buildResults = null
    return true
  }

  @Slow
  override fun storeNewBuildResults(analyzersProxy: BuildEventsAnalyzersProxy,
                                    buildID: String,
                                    requestHolder: BuildRequestHolder): BuildAnalysisResults {
    val buildResults = createBuildResultsObject(analyzersProxy, buildID, requestHolder)
    this.buildResults = buildResults
    notifyDataListeners()
    if (StudioFlags.BUILD_ANALYZER_HISTORY.get()) {
      fileManager.storeBuildResultsInFile(buildResults)
      descriptors.add(BuildDescriptorImpl(buildResults.getBuildSessionID(),
                                          buildResults.getBuildFinishedTimestamp(),
                                          buildResults.getTotalBuildTimeMs()))
      deleteOldRecords()
    }
    return buildResults
  }

  override fun recordNewFailure(buildID: String, failureType: FailureResult.Type) {
    this.buildResults = FailureResult(buildID, failureType)
    notifyDataListeners()
  }

  /**
   * Reads in build results with the build session ID specified from bytes and converts them to a proto-structure,
   * and then converts them again to a BuildAnalysisResults object before returning them to the user.
   * If there is an issue resolving the file then an IOException is thrown. If there is an issue converting the proto-structure
   * to the BuildAnalysisResults object then the BuildResultsProtoMessageConverter class is responsible for handling the exception.
   *
   * @return BuildAnalysisResults
   * @exception IOException
   */
  @Slow
  override fun getHistoricBuildResultByID(buildID: String): BuildAnalysisResults =
    fileManager.getHistoricBuildResultByID(buildID)

  /**
   * Does not take in input, returns the size of the build-analyzer-history-data folder in bytes.
   * If it fails to locate the folder then 0 is returned.
   * @return Bytes
   */
  @Slow
  override fun getCurrentBuildHistoryDataSize(): Long =
    fileManager.getCurrentBuildHistoryDataSize()

  /**
   * Does not take an input, returns the number of files in the build-analyzer-history-data folder.
   * If it fails to locate the folder then 0 is returned.
   * @return Number of files in build-analyzer-history-data folder
   */
  @Slow
  override fun getNumberOfBuildFilesStored(): Int =
    fileManager.getNumberOfBuildFilesStored()

  @Slow
  override fun onSettingsChange() {
    deleteOldRecords()
  }

  @Slow
  override fun deleteHistoricBuildResultByID(buildID: String) =
    fileManager.deleteHistoricBuildResultByID(buildID)

  override fun getListOfHistoricBuildDescriptors(): Set<BuildDescriptor> = descriptors

  override fun hasData(): Boolean {
    return buildResults != null
  }

  /**
   * Deletes old records until count of descriptors in list is more than [limitSizeHistory]
   */
  @Slow
  private fun deleteOldRecords() {
    val limitSizeHistory = BuildAnalyzerSettings.getInstance(project).state.maxNumberOfBuildsStored
    require(limitSizeHistory >= 0) { "[limitSizeHistory] should not be less than 0" }
    while (descriptors.size > limitSizeHistory) {
      val oldestOne = descriptors.minByOrNull { it.buildFinishedTimestamp }
      require(oldestOne != null) { "List of descriptors is empty => 0 is more than [limitSizeHistory]" }
      fileManager.deleteHistoricBuildResultByID(oldestOne.buildSessionID)
      descriptors.remove(oldestOne)
    }
  }
}