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
import com.intellij.openapi.observable.properties.AtomicProperty
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.getProjectDataPath
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Future
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class BuildAnalyzerStorageManagerImpl(
  val project: Project
) : BuildAnalyzerStorageManager {
  // last built result, cannot be deleted(only replaced with another result)
  @Volatile
  private var buildResults: AbstractBuildAnalysisResult? = null
  val fileManager = BuildAnalyzerStorageFileManager(project.getProjectDataPath("build-analyzer-history-data").toFile())

  @VisibleForTesting
  val descriptors = BuildDescriptorStorageService.getInstance(project).state.descriptors
  private val inMemoryResults = ConcurrentHashMap<String, BuildAnalysisResults>()

  private val workingWithDiskLock = ReentrantLock()

  private val storageDescriptor = BuildAnalyzerStorageDescriptor(fileManager.totalFilesSize,
                                                                 AtomicProperty(getNumberOfBuildResultsStored()))

  init {
    onSettingsChange()
  }

  @Synchronized
  @Slow
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
  override fun clearBuildResultsStored(): Future<*> =
    deleteFirstNRecords { descriptors.size }

  /**
   * Stores new result and at some point of time count of result can be more than [BuildAnalyzerSettings.State.maxNumberOfBuildsStored], because clearing process is on the background
   */
  override fun storeNewBuildResults(analyzersProxy: BuildEventsAnalyzersProxy,
                                    buildID: String,
                                    requestHolder: BuildRequestHolder): Future<BuildAnalysisResults> {
    val buildResults = createBuildResultsObject(analyzersProxy, buildID, requestHolder)
    this.buildResults = buildResults
    notifyDataListeners()
    if (StudioFlags.BUILD_ANALYZER_HISTORY.get()) {
      descriptors.add(BuildDescriptorImpl(buildResults.getBuildSessionID(),
                                          buildResults.getBuildFinishedTimestamp(),
                                          buildResults.getTotalBuildTimeMs()))
      updateDescriptor()
      inMemoryResults[buildResults.getBuildSessionID()] = buildResults
      val onBackground: () -> BuildAnalysisResults = {
        workingWithDiskLock.withLock {
          fileManager.storeBuildResultsInFile(buildResults)
          inMemoryResults.remove(buildResults.getBuildSessionID())
        }
        deleteOldRecords().get()
        buildResults
      }
      return ApplicationManager.getApplication().executeOnPooledThread(onBackground)
    }
    return CompletableFuture.completedFuture(buildResults)
  }

  @Slow
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
   * @exception java.io.IOException
   */
  @Slow
  override fun getHistoricBuildResultByID(buildID: String): BuildAnalysisResults {
    inMemoryResults[buildID]?.let {
      return it
    }
    val result: BuildAnalysisResults
    workingWithDiskLock.withLock {
      result = fileManager.getHistoricBuildResultByID(buildID)
    }
    return result
  }

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
  override fun getNumberOfBuildResultsStored(): Int =
    descriptors.size

  override fun getStorageDescriptor(): BuildAnalyzerStorageDescriptor = storageDescriptor

  override fun onSettingsChange() =
    deleteOldRecords()

  override fun deleteHistoricBuildResultByID(buildID: String): Future<*> {
    getListOfHistoricBuildDescriptors().firstOrNull { it.buildSessionID == buildID }?.let { result ->
      descriptors.remove(result)
      updateDescriptor()
      inMemoryResults.remove(buildID)
      return ApplicationManager.getApplication().executeOnPooledThread {
        workingWithDiskLock.withLock {
          fileManager.deleteHistoricBuildResultByID(buildID)
        }
      }
    }
    return CompletableFuture.completedFuture(null)
  }

  override fun getListOfHistoricBuildDescriptors(): Set<BuildDescriptor> {
    val result = mutableSetOf<BuildDescriptor>()
    val it = descriptors.iterator()
    while (true) {
      val x: BuildDescriptor
      try {
        x = it.next()
      }
      catch (_: NoSuchElementException) {
        break
      }
      result.add(x)
    }
    return result
  }

  override fun hasData(): Boolean {
    return buildResults != null
  }

  private fun updateDescriptor() {
    storageDescriptor.numberOfBuildResultsStored.set(getNumberOfBuildResultsStored())
  }

  /**
   * Deletes old records while count of descriptors in list is more than [BuildAnalyzerSettings.State.maxNumberOfBuildsStored]
   */
  private fun deleteOldRecords(): Future<*> =
    deleteFirstNRecords { descriptors.size - BuildAnalyzerSettings.getInstance(project).state.maxNumberOfBuildsStored }

  private val deletingLock = ReentrantLock()

  /**
   * Delete not more than n records from the head of descriptors
   */
  private fun deleteFirstNRecords(lazy: () -> Int): Future<*> =
    ApplicationManager.getApplication().executeOnPooledThread {
      deletingLock.withLock {
        val n = lazy()
        repeat(n) {
          getListOfHistoricBuildDescriptors().minByOrNull { it.buildFinishedTimestamp }?.let { descriptor ->
            deleteHistoricBuildResultByID(descriptor.buildSessionID)
          }
        }
      }
    }
}