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
import com.android.build.attribution.data.BuildRequestHolder
import com.android.build.attribution.data.BuildInvocationType
import com.android.build.attribution.data.PluginContainer
import com.android.build.attribution.data.StudioProvidedInfo
import com.android.build.attribution.data.TaskContainer
import com.android.build.attribution.ui.BuildAttributionUiManager
import com.android.build.attribution.ui.analytics.BuildAttributionUiAnalytics
import com.android.build.attribution.ui.controllers.ConfigurationCacheTestBuildFlowRunner
import com.android.build.attribution.ui.invokeLaterIfNotDisposed
import com.android.ide.common.attribution.AndroidGradlePluginAttributionData
import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.gradle.project.build.attribution.BasicBuildAttributionInfo
import com.android.tools.idea.gradle.project.build.attribution.BuildAttributionManager
import com.android.tools.idea.gradle.project.build.attribution.getAgpAttributionFileDir
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker
import com.android.utils.FileUtils
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
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
  private val analyzersWrapper = BuildAnalyzersWrapper(analyzersProxy.buildAnalyzers, taskContainer, pluginContainer)

  override fun onBuildStart(request: GradleBuildInvoker.Request) {
    currentBuildRequest = request
    eventsProcessingFailedFlag = false
    myCurrentBuildInvocationType = detectBuildType(request)
    analyzersWrapper.onBuildStart()
    ApplicationManager.getApplication().getService(KnownGradlePluginsService::class.java).asyncRefresh()
  }

  override fun onBuildSuccess(request: GradleBuildInvoker.Request): BasicBuildAttributionInfo {
    val buildFinishedTimestamp = System.currentTimeMillis()
    val buildSessionId = UUID.randomUUID().toString()
    val buildRequestHolder = BuildRequestHolder(request)
    val attributionFileDir = getAgpAttributionFileDir(request.data)
    var agpVersion: GradleVersion? = null

    BuildAttributionAnalyticsManager(buildSessionId, project).use { analyticsManager ->
      analyticsManager.runLoggingPerformanceStats(
        buildFinishedTimestamp - analyzersProxy.criticalPathAnalyzer.result.buildFinishedTimestamp) {
        try {
          val attributionData = AndroidGradlePluginAttributionData.load(attributionFileDir)
          agpVersion = attributionData?.buildInfo?.agpVersion?.let { GradleVersion.tryParseAndroidGradlePluginVersion(it) }
          val pluginsData = ApplicationManager.getApplication().getService(KnownGradlePluginsService::class.java).gradlePluginsData
          val studioProvidedInfo = StudioProvidedInfo.fromProject(project, buildRequestHolder, myCurrentBuildInvocationType)
          // If there was an error in events processing already there is no need to continue.
          if (!eventsProcessingFailedFlag) {
            analyzersWrapper.onBuildSuccess(attributionData, pluginsData, analyzersProxy, studioProvidedInfo)
            BuildAnalyzerStorageManager.getInstance(project)
              .storeNewBuildResults(analyzersProxy, buildSessionId, BuildRequestHolder(currentBuildRequest))
            analyticsManager.logAnalyzersData(BuildAnalyzerStorageManager.getInstance(project).getLatestBuildAnalysisResults())
            analyticsManager.logBuildSuccess(myCurrentBuildInvocationType)
          }
          else {
            analyticsManager.logAnalysisFailure(myCurrentBuildInvocationType)
            //TODO (b/184273397): currently show general failure state, same as for failed build. Adjust in further refactorings.
            BuildAttributionUiManager.getInstance(project).onBuildFailure(buildSessionId)
          }
        }
        catch (t: Throwable) {
          log.error("Error during post-build analysis", t)
          analyticsManager.logAnalysisFailure(myCurrentBuildInvocationType)
          BuildAttributionUiManager.getInstance(project).onBuildFailure(buildSessionId)
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
        BuildAttributionUiManager.getInstance(project).onBuildFailure(buildSessionId)
      }
    }
  }

  private fun cleanup(attributionFileDir: File) {
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
}