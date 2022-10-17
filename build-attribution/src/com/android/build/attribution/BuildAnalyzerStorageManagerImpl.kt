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
import com.android.build.attribution.analyzers.DownloadsAnalyzer
import com.android.build.attribution.data.BuildRequestHolder
import com.intellij.openapi.project.Project
import com.android.tools.idea.flags.StudioFlags


class BuildAnalyzerStorageManagerImpl(
  val project: Project
) : BuildAnalyzerStorageManager {
  private var buildResults : BuildAnalysisResults? = null
  private var historicBuildResults : MutableMap<String, BuildAnalysisResults> = mutableMapOf()

  private fun notifyDataListeners() {
    var publisher = project.messageBus.syncPublisher(BuildAnalyzerStorageManager.DATA_IS_READY_TOPIC);
    publisher.newDataAvailable();
  }

  private fun createBuildResultsObject(analyzersProxy: BuildEventsAnalyzersProxy, buildSessionID : String, requestHolder : BuildRequestHolder): BuildAnalysisResults {
    return BuildAnalysisResults(
      requestHolder = requestHolder,
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
      buildSessionID = buildSessionID
    )
  }

  /**
   * Returns the analysis results from the latest build in the form of a BuildAnalysisResults object. There are no arguments.
   * If no build results have been stored, then an IllegalStatException is thrown as there is nothing to return.
   *
   * @return BuildAnalysisResults
   * @exception IllegalStateException
   */
  override fun getLatestBuildAnalysisResults() : BuildAnalysisResults {
    if(hasData()) return buildResults!!
    else throw IllegalStateException("Storage Manager does not have data to return.")
  }

  override fun storeNewBuildResults(analyzersProxy: BuildEventsAnalyzersProxy, buildID : String, requestHolder : BuildRequestHolder) {
    val buildResults = createBuildResultsObject(analyzersProxy, buildID, requestHolder)
    this.buildResults = buildResults
    if(StudioFlags.BUILD_ANALYZER_HISTORY.get()) historicBuildResults[buildID] = buildResults
    notifyDataListeners()
  }

  override fun getHistoricBuildResultByID(buildID : String) : BuildAnalysisResults {
    return historicBuildResults[buildID] ?: throw NoSuchElementException("No such build result was found.")
  }

  override fun getListOfHistoricBuildIDs() : Set<String> {
    return historicBuildResults.keys
  }

  override fun hasData(): Boolean {
    return buildResults != null
  }
}