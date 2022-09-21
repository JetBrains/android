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
import com.android.build.attribution.proto.converters.BuildResultsProtoMessageConverter
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.util.toIoFile
import com.android.utils.FileUtils
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

class BuildAnalyzerStorageManagerImpl(
  val project: Project
) : BuildAnalyzerStorageManager {
  private var buildResults: AbstractBuildAnalysisResult? = null
  private var historicBuildResults: MutableMap<String, BuildAnalysisResults> = mutableMapOf()
  private val dataFolder = project.guessProjectDir()?.toIoFile()?.resolve("build-analyzer-history-data")
  private val log: Logger get() = Logger.getInstance("Build Analyzer")


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
    if(dataFolder != null) {
      FileUtils.deleteDirectoryContents(dataFolder)
      return true
    }
    else {
      log.error("could not find build-analyzer-history-data folder.")
      return false
    }
  }

  override fun storeNewBuildResults(analyzersProxy: BuildEventsAnalyzersProxy, buildID: String, requestHolder: BuildRequestHolder): BuildAnalysisResults {
    val buildResults = createBuildResultsObject(analyzersProxy, buildID, requestHolder)
    this.buildResults = buildResults
    notifyDataListeners()
    if (StudioFlags.BUILD_ANALYZER_HISTORY.get()) {
      historicBuildResults[buildID] = buildResults
      storeBuildResultsInFile(buildResults)
    }
    return buildResults
  }

  override fun recordNewFailure(buildID: String, failureType: FailureResult.Type) {
    this.buildResults = FailureResult(buildID, failureType)
    notifyDataListeners()
  }

  /**
   * Converts build analysis results into a protobuf-generated data structure, that is then stored in byte form in a file. If there is an
   * error during file storage, then an IOException is logged and False is returned. If the folder containing build results cannot be resolved
   * then False is returned and file storage is not attempted. If the process succeeds then True is returned.
   *
   * @return Boolean
   */
  @VisibleForTesting
  fun storeBuildResultsInFile(buildResults: BuildAnalysisResults): Boolean {
    if (dataFolder == null) {
      log.error("build-analyzer-history-data could not be resolved")
      return false
    }
    try {
      FileUtils.mkdirs(dataFolder)
      val buildResultFile = File(dataFolder, buildResults.getBuildSessionID())
      buildResultFile.createNewFile()
      BuildResultsProtoMessageConverter.convertBuildAnalysisResultsFromObjectToBytes(
        buildResults,
        buildResults.getPluginMap(),
        buildResults.getTaskMap()
      ).writeDelimitedTo(FileOutputStream(buildResultFile))
      return true
    }
    catch (e: IOException) {
      log.error("Error when attempting to store build results with ID ${buildResults.getBuildSessionID()} in file.")
      return false
    }
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
  @VisibleForTesting
  fun getHistoricBuildResultsFromFileByID(buildSessionID: String): BuildAnalysisResults {
    try {
      dataFolder?.let {
        val stream = FileInputStream(dataFolder.resolve(buildSessionID))
        val message = BuildAnalysisResultsMessage.parseDelimitedFrom(stream)
        return BuildResultsProtoMessageConverter
          .convertBuildAnalysisResultsFromBytesToObject(message)
      } ?: throw IOException("No data storage folder")
    }
    catch (e: Exception) {
      throw IOException("Error reading in build results file with ID: $buildSessionID", e)
    }
  }

  override fun getHistoricBuildResultByID(buildID: String): BuildAnalysisResults {
    return historicBuildResults[buildID] ?: throw NoSuchElementException("No such build result was found.")
  }

  /**
   * Does not take in input, returns the size of the build-analyzer-history-data folder in bytes.
   * If it fails to locate the folder then 0 is returned.
   * @return Bytes
   */
  override fun getCurrentBuildHistoryDataSize() : Long {
    var size = 0L
    if(dataFolder != null) {
      FileUtils.mkdirs(dataFolder)
      FileUtils.getAllFiles(dataFolder).forEach { size += it.length() }
    }
    return size
  }

  /**
   * Does not take an input, returns the number of files in the build-analyzer-history-data folder.
   * If it fails to locate the folder then 0 is returned.
   * @return Number of files in build-analyzer-history-data folder
   */
  override fun getNumberOfBuildFilesStored() : Int {
    var size = 0
    if(dataFolder != null) {
      FileUtils.mkdirs(dataFolder)
      size = FileUtils.getAllFiles(dataFolder).size()
    }
    return size
  }

  override fun getListOfHistoricBuildDescriptors(): Set<BuildDescriptor> {
    return historicBuildResults.values.map { buildAnalysisResults ->
      BuildDescriptor(
        buildAnalysisResults.getBuildSessionID(),
        buildAnalysisResults.getBuildFinishedTimestamp(),
        buildAnalysisResults.getTotalBuildTimeMs()
      )
    }.toSet()
  }

  override fun hasData(): Boolean {
    return buildResults != null
  }
}