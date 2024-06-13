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
import com.android.build.attribution.data.BuildInvocationType
import com.android.build.attribution.data.BuildRequestHolder
import com.android.build.attribution.data.PluginContainer
import com.android.build.attribution.data.StudioProvidedInfo
import com.android.build.attribution.data.TaskContainer
import com.android.build.attribution.ui.BuildAttributionUiManager
import com.android.build.attribution.ui.analytics.BuildAttributionUiAnalytics
import com.android.build.attribution.ui.controllers.ConfigurationCacheTestBuildFlowRunner
import com.android.build.attribution.ui.invokeLaterIfNotDisposed
import com.android.build.output.DownloadInfoDataModel
import com.android.build.output.DownloadsInfoPresentableBuildEvent
import com.android.buildanalyzer.common.AndroidGradlePluginAttributionData
import com.android.ide.common.repository.AgpVersion
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.build.attribution.BasicBuildAttributionInfo
import com.android.tools.idea.gradle.project.build.attribution.BuildAttributionManager
import com.android.tools.idea.gradle.project.build.attribution.getAgpAttributionFileDir
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker
import com.android.tools.idea.gradle.util.GradleVersions
import com.android.utils.FileUtils
import com.google.common.annotations.VisibleForTesting
import com.intellij.build.BuildViewManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.CheckedDisposable
import com.intellij.openapi.util.Disposer
import org.gradle.tooling.events.ProgressEvent
import java.io.File
import java.util.UUID

class BuildAttributionManagerImpl(
  val project: Project
) : BuildAttributionManager {
  private val log: Logger get() = Logger.getInstance("Build Analyzer")

  /** Class to group all data relevant to single build and avoid complex mutable state in the manager itself. */
  private class BuildAnalysisContext(
    val currentBuildRequest: GradleBuildInvoker.Request
  ) {
    val buildSessionId = UUID.randomUUID().toString()
    val currentBuildDisposable: CheckedDisposable = Disposer.newCheckedDisposable("BuildAnalyzer disposable for ${currentBuildRequest.taskId}")
    var eventsProcessingFailedFlag: Boolean = false
    val currentBuildInvocationType: BuildInvocationType = detectBuildType(currentBuildRequest)

    val taskContainer = TaskContainer()
    val pluginContainer = PluginContainer()
    val analyzersProxy = BuildEventsAnalyzersProxy(taskContainer, pluginContainer,
                                                   BuildAnalyzerStorageManager.getInstance(currentBuildRequest.project))
    val analyzersWrapper = BuildAnalyzersWrapper(analyzersProxy.buildAnalyzers, taskContainer, pluginContainer)

    val attributionFileDir = getAgpAttributionFileDir(currentBuildRequest.data)
    val buildRequestHolder = BuildRequestHolder(currentBuildRequest)
    val analyticsManager = BuildAttributionAnalyticsManager(buildSessionId, currentBuildRequest.project)


    private fun detectBuildType(request: GradleBuildInvoker.Request): BuildInvocationType = when {
      ConfigurationCacheTestBuildFlowRunner.getInstance(request.project).isTestConfigurationCacheBuild(request) -> BuildInvocationType.CONFIGURATION_CACHE_TRIAL
      request.gradleTasks.contains(CHECK_JETIFIER_TASK_NAME) -> BuildInvocationType.CHECK_JETIFIER
      else -> BuildInvocationType.REGULAR_BUILD
    }
  }

  private lateinit var currentBuildAnalysisContext: BuildAnalysisContext
  // Leave these fields temporarily to not break tests
  @get:VisibleForTesting
  val analyzersProxy: BuildEventsAnalyzersProxy get() = currentBuildAnalysisContext.analyzersProxy
  @get:VisibleForTesting
  val currentBuildRequest: GradleBuildInvoker.Request get() = currentBuildAnalysisContext.currentBuildRequest

  override fun onBuildStart(request: GradleBuildInvoker.Request) {
    currentBuildAnalysisContext = BuildAnalysisContext(request)

    ApplicationManager.getApplication().getService(KnownGradlePluginsService::class.java).asyncRefresh()
    if (!StudioFlags.BUILD_OUTPUT_DOWNLOADS_INFORMATION.get()) return
    currentBuildAnalysisContext.analyzersProxy.buildAnalyzers.filterIsInstance<DownloadsAnalyzer>().singleOrNull()?.let {
      val downloadsInfoDataModel = DownloadInfoDataModel(currentBuildAnalysisContext.currentBuildDisposable)
      it.eventsProcessor.downloadsInfoDataModel = downloadsInfoDataModel
      project.setUpDownloadsInfoNodeOnBuildOutput(request.taskId, currentBuildAnalysisContext.currentBuildDisposable, downloadsInfoDataModel)
    }
  }

  override fun onBuildSuccess(request: GradleBuildInvoker.Request): BasicBuildAttributionInfo {
    val buildFinishedProcessedTimestamp = System.currentTimeMillis()
    var agpVersion: AgpVersion? = null

    currentBuildAnalysisContext.run {
      analyticsManager.use { analyticsManager ->
        analyticsManager.runLoggingPerformanceStats(
          toolingApiLatencyMs = buildFinishedProcessedTimestamp - analyzersProxy.criticalPathAnalyzer.result.buildFinishedTimestamp,
          numberOfGeneratedPartialResults = getNumberOfPartialResultsGenerated(attributionFileDir)
        ) {
          try {
            val attributionData = AndroidGradlePluginAttributionData.load(attributionFileDir)
            agpVersion = attributionData?.buildInfo?.agpVersion?.let { AgpVersion.tryParse(it) }
            val pluginsData = ApplicationManager.getApplication().getService(KnownGradlePluginsService::class.java).gradlePluginsData
            val studioProvidedInfo = StudioProvidedInfo.fromProject(project, buildRequestHolder, currentBuildInvocationType)
            // If there was an error in events processing already there is no need to continue.
            if (!eventsProcessingFailedFlag) {
              analyzersWrapper.onBuildSuccess(attributionData, pluginsData, analyzersProxy, studioProvidedInfo)
              val analysisResults = BuildAnalyzerStorageManager.getInstance(project)
                .storeNewBuildResults(analyzersProxy, buildSessionId, BuildRequestHolder(currentBuildRequest))
              analyticsManager.logAnalyzersData(analysisResults.get())
              analyticsManager.logBuildSuccess(currentBuildInvocationType)
            }
            else {
              analyticsManager.logAnalysisFailure(currentBuildInvocationType)
              BuildAnalyzerStorageManager.getInstance(project).recordNewFailure(buildSessionId, FailureResult.Type.ANALYSIS_FAILURE)
            }
          }
          catch (t: Throwable) {
            log.error("Error during post-build analysis", t)
            analyticsManager.logAnalysisFailure(currentBuildInvocationType)
            BuildAnalyzerStorageManager.getInstance(project).recordNewFailure(buildSessionId, FailureResult.Type.ANALYSIS_FAILURE)
          }
          finally {
            cleanup(attributionFileDir)
          }
        }
      }
    }

    return BasicBuildAttributionInfo(agpVersion)
  }

  override fun onBuildFailure(request: GradleBuildInvoker.Request) {
    currentBuildAnalysisContext.run {
      cleanup(getAgpAttributionFileDir(request.data))
      project.invokeLaterIfNotDisposed {
        val buildSessionId = UUID.randomUUID().toString()
        BuildAttributionAnalyticsManager(buildSessionId, project).use { analyticsManager ->
          analyticsManager.logBuildFailure(currentBuildInvocationType)
          analyzersWrapper.onBuildFailure()
          BuildAnalyzerStorageManager.getInstance(project).recordNewFailure(buildSessionId, FailureResult.Type.BUILD_FAILURE)
        }
      }
    }
  }

  private fun BuildAnalysisContext.cleanup(attributionFileDir: File) {
    try {
      // There is a valid codepath that would result in this being already disposed and set tu null.
      // GradleTasksExecutorImpl.TaskImpl.reportAgpVersionMismatch throws exception redirecting to build failure path
      // AND it is called AFTER onBuildSuccess of build analyzer was already called resulting in cleanup happening twice.
      currentBuildDisposable.let { Disposer.dispose(it) }
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
    currentBuildAnalysisContext.run {
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
  }

  override fun openResultsTab() = BuildAttributionUiManager.getInstance(project)
    .openTab(BuildAttributionUiAnalytics.TabOpenEventSource.BUILD_OUTPUT_LINK)

  override fun shouldShowBuildOutputLink(): Boolean = !(
    ConfigurationCacheTestBuildFlowRunner.getInstance(project).runningFirstConfigurationCacheBuild
    || currentBuildAnalysisContext.eventsProcessingFailedFlag
                                                       )

  private fun getNumberOfPartialResultsGenerated(attributionFileDir: File): Int? {
    return try {
      AndroidGradlePluginAttributionData.getPartialResultsDir(attributionFileDir).takeIf {
        it.exists()
      }?.listFiles()?.size
    } catch (e: Exception) {
      null
    }
  }

  private fun Project.setUpDownloadsInfoNodeOnBuildOutput(id: ExternalSystemTaskId,
                                                          buildDisposable: CheckedDisposable,
                                                          downloadsInfoDataModel: DownloadInfoDataModel) {
    val gradleVersion = GradleVersions.getInstance().getGradleVersion(this)
    val rootDownloadEvent = DownloadsInfoPresentableBuildEvent(id, buildDisposable, System.currentTimeMillis(), gradleVersion, downloadsInfoDataModel)
    val viewManager = getService(BuildViewManager::class.java)
    viewManager.onEvent(id, rootDownloadEvent)
  }
}