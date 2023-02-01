/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.SdkConstants
import com.android.build.attribution.analytics.BuildAttributionAnalyticsManager
import com.android.build.attribution.analyzers.BuildAnalyzersWrapper
import com.android.build.attribution.analyzers.BuildEventsAnalyzersProxy
import com.android.build.attribution.analyzers.CHECK_JETIFIER_TASK_NAME
import com.android.build.attribution.analyzers.DownloadsAnalyzer
import com.android.build.attribution.data.BuildRequestHolder
import com.android.build.attribution.data.BuildInvocationType
import com.android.build.attribution.data.PluginContainer
import com.android.build.attribution.data.StudioProvidedInfo
import com.android.build.attribution.data.TaskContainer
import com.android.build.attribution.ui.BuildAttributionUiManager
import com.android.build.attribution.ui.analytics.BuildAttributionUiAnalytics
import com.android.build.attribution.ui.controllers.ConfigurationCacheTestBuildFlowRunner
import com.android.build.attribution.ui.invokeLaterIfNotDisposed
import com.android.build.output.DownloadsInfoUIModelNotifier
import com.android.build.output.DownloadsInfoPresentableEvent
import com.android.buildanalyzer.common.AndroidGradlePluginAttributionData
import com.android.ide.common.repository.AgpVersion
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.build.attribution.BasicBuildAttributionInfo
import com.android.tools.idea.gradle.project.build.attribution.BuildAttributionManager
import com.android.tools.idea.gradle.project.build.attribution.getAgpAttributionFileDir
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker
import com.android.utils.FileUtils
import com.google.common.annotations.VisibleForTesting
import com.intellij.build.BuildViewManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import org.gradle.tooling.events.ProgressEvent
import java.io.File
import java.util.UUID

class BuildAttributionManagerImpl(
  val project: Project
) : BuildAttributionManager {
  private val log: Logger get() = Logger.getInstance("Build Analyzer")
  private val taskContainer = TaskContainer()
  private val pluginContainer = PluginContainer()

  private var eventsProcessingFailedFlag: Boolean = false
  private var myCurrentBuildInvocationType: BuildInvocationType = BuildInvocationType.REGULAR_BUILD

  @get:VisibleForTesting
  val analyzersProxy = BuildEventsAnalyzersProxy(taskContainer, pluginContainer)
  @get:VisibleForTesting
  lateinit var currentBuildRequest: GradleBuildInvoker.Request
  private var currentBuildDisposable: Disposable? = null
  private val analyzersWrapper = BuildAnalyzersWrapper(analyzersProxy.buildAnalyzers, taskContainer, pluginContainer)

  override fun onBuildStart(request: GradleBuildInvoker.Request) {
    currentBuildRequest = request
    currentBuildDisposable = Disposer.newDisposable("BuildAnalyzer disposable for ${request.taskId}")
    eventsProcessingFailedFlag = false
    myCurrentBuildInvocationType = detectBuildType(request)
    analyzersWrapper.onBuildStart()
    ApplicationManager.getApplication().getService(KnownGradlePluginsService::class.java).asyncRefresh()
    analyzersProxy.buildAnalyzers.filterIsInstance<DownloadsAnalyzer>().singleOrNull()?.let {
      it.eventsProcessor.downloadsUiModelNotifier = DownloadsInfoUIModelNotifier(request.project, request.taskId)
      project.setUpDownloadsInfoNodeOnBuildOutput(request.taskId, currentBuildDisposable!!)
    }
  }

  override fun onBuildSuccess(request: GradleBuildInvoker.Request): BasicBuildAttributionInfo {
    val buildFinishedTimestamp = System.currentTimeMillis()
    val buildSessionId = UUID.randomUUID().toString()
    val buildRequestHolder = BuildRequestHolder(request)
    val attributionFileDir = getAgpAttributionFileDir(request.data)
    var agpVersion: AgpVersion? = null

    BuildAttributionAnalyticsManager(buildSessionId, project).use { analyticsManager ->
      analyticsManager.runLoggingPerformanceStats(
        toolingApiLatencyMs = buildFinishedTimestamp - analyzersProxy.criticalPathAnalyzer.result.buildFinishedTimestamp,
        numberOfGeneratedPartialResults = getNumberOfPartialResultsGenerated(attributionFileDir)
      ) {
        try {
          val attributionData = AndroidGradlePluginAttributionData.load(attributionFileDir)
          agpVersion = attributionData?.buildInfo?.agpVersion?.let { AgpVersion.tryParse(it) }
          val pluginsData = ApplicationManager.getApplication().getService(KnownGradlePluginsService::class.java).gradlePluginsData
          val studioProvidedInfo = StudioProvidedInfo.fromProject(project, buildRequestHolder, myCurrentBuildInvocationType)
          // If there was an error in events processing already there is no need to continue.
          if (!eventsProcessingFailedFlag) {
            analyzersWrapper.onBuildSuccess(attributionData, pluginsData, analyzersProxy, studioProvidedInfo)
            val analysisResults = BuildAnalyzerStorageManager.getInstance(project)
              .storeNewBuildResults(analyzersProxy, buildSessionId, BuildRequestHolder(currentBuildRequest))
            analyticsManager.logAnalyzersData(analysisResults.get())
            analyticsManager.logBuildSuccess(myCurrentBuildInvocationType)
          }
          else {
            analyticsManager.logAnalysisFailure(myCurrentBuildInvocationType)
            BuildAnalyzerStorageManager.getInstance(project).recordNewFailure(buildSessionId, FailureResult.Type.ANALYSIS_FAILURE)
          }
        }
        catch (t: Throwable) {
          log.error("Error during post-build analysis", t)
          analyticsManager.logAnalysisFailure(myCurrentBuildInvocationType)
          BuildAnalyzerStorageManager.getInstance(project).recordNewFailure(buildSessionId, FailureResult.Type.ANALYSIS_FAILURE)
        }
        finally {
          cleanup(attributionFileDir)
        }
      }
    }

    return BasicBuildAttributionInfo(agpVersion)
  }

  override fun onBuildFailure(request: GradleBuildInvoker.Request) {
    cleanup(getAgpAttributionFileDir(request.data))
    project.invokeLaterIfNotDisposed {
      val buildSessionId = UUID.randomUUID().toString()
      BuildAttributionAnalyticsManager(buildSessionId, project).use { analyticsManager ->
        analyticsManager.logBuildFailure(myCurrentBuildInvocationType)
        analyzersWrapper.onBuildFailure()
        BuildAnalyzerStorageManager.getInstance(project).recordNewFailure(buildSessionId, FailureResult.Type.BUILD_FAILURE)
      }
    }
  }

  private fun cleanup(attributionFileDir: File) {
    try {
      Disposer.dispose(currentBuildDisposable!!)
      currentBuildDisposable = null
    } catch (t: Throwable) {
      log.error("Error disposing build disposable", t)
    }
    try {
      FileUtils.deleteRecursivelyIfExists(FileUtils.join(attributionFileDir, SdkConstants.FD_BUILD_ATTRIBUTION))
    } catch (t: Throwable) {
      log.error("Error during build attribution files cleanup", t)
    }
  }

  override fun statusChanged(event: ProgressEvent?) {
    if (eventsProcessingFailedFlag) return
    try {
      if (event == null) return

      analyzersWrapper.receiveEvent(event)
    }
    catch (t: Throwable) {
      eventsProcessingFailedFlag = true
      log.error("Error during build events processing", t)
    }
  }

  override fun openResultsTab() = BuildAttributionUiManager.getInstance(project)
    .openTab(BuildAttributionUiAnalytics.TabOpenEventSource.BUILD_OUTPUT_LINK)

  override fun shouldShowBuildOutputLink(): Boolean = !(
    ConfigurationCacheTestBuildFlowRunner.getInstance(project).runningFirstConfigurationCacheBuild
    || eventsProcessingFailedFlag
                                                       )

  private fun detectBuildType(request: GradleBuildInvoker.Request): BuildInvocationType = when {
    ConfigurationCacheTestBuildFlowRunner.getInstance(project).isTestConfigurationCacheBuild(request) -> BuildInvocationType.CONFIGURATION_CACHE_TRIAL
    request.gradleTasks.contains(CHECK_JETIFIER_TASK_NAME) -> BuildInvocationType.CHECK_JETIFIER
    else -> BuildInvocationType.REGULAR_BUILD
  }

  private fun getNumberOfPartialResultsGenerated(attributionFileDir: File): Int? {
    return try {
      AndroidGradlePluginAttributionData.getPartialResultsDir(attributionFileDir).takeIf {
        it.exists()
      }?.listFiles()?.size
    } catch (e: Exception) {
      null
    }
  }

  private fun Project.setUpDownloadsInfoNodeOnBuildOutput(id: ExternalSystemTaskId, buildDisposable: Disposable) {
    if (!StudioFlags.BUILD_OUTPUT_DOWNLOADS_INFORMATION.get()) return
    val rootDownloadEvent = DownloadsInfoPresentableEvent(id, buildDisposable)
    val viewManager = getService(BuildViewManager::class.java)
    viewManager.onEvent(id, rootDownloadEvent)
  }
}