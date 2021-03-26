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
import com.android.build.attribution.data.BuildRequestHolder
import com.android.build.attribution.data.PluginContainer
import com.android.build.attribution.data.StudioProvidedInfo
import com.android.build.attribution.data.TaskContainer
import com.android.build.attribution.ui.BuildAttributionUiManager
import com.android.build.attribution.ui.analytics.BuildAttributionUiAnalytics
import com.android.build.attribution.ui.controllers.ConfigurationCacheTestBuildFlowRunner
import com.android.build.attribution.ui.data.builder.BuildAttributionReportBuilder
import com.android.ide.common.attribution.AndroidGradlePluginAttributionData
import com.android.tools.idea.gradle.project.build.attribution.BuildAttributionManager
import com.android.tools.idea.gradle.project.build.attribution.getAgpAttributionFileDir
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker
import com.android.utils.FileUtils
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import org.gradle.tooling.events.ProgressEvent
import java.util.UUID

class BuildAttributionManagerImpl(
  val project: Project
) : BuildAttributionManager {
  private val taskContainer = TaskContainer()
  private val pluginContainer = PluginContainer()

  @get:VisibleForTesting
  val analyzersProxy = BuildEventsAnalyzersProxy(taskContainer, pluginContainer)
  private val analyzersWrapper = BuildAnalyzersWrapper(analyzersProxy.buildAnalyzers, taskContainer, pluginContainer)

  override fun onBuildStart() {
    analyzersWrapper.onBuildStart()
    ApplicationManager.getApplication().getService(KnownGradlePluginsService::class.java).asyncRefresh()
  }

  override fun onBuildSuccess(request: GradleBuildInvoker.Request) {
    val buildFinishedTimestamp = System.currentTimeMillis()
    val buildSessionId = UUID.randomUUID().toString()
    val buildRequestHolder = BuildRequestHolder(request)
    val attributionFileDir = getAgpAttributionFileDir(request)

    BuildAttributionAnalyticsManager(buildSessionId, project).use { analyticsManager ->
      analyticsManager.logBuildAttributionPerformanceStats(buildFinishedTimestamp - analyzersProxy.getBuildFinishedTimestamp()) {
        try {
          val attributionData = AndroidGradlePluginAttributionData.load(attributionFileDir)
          val pluginsData = ApplicationManager.getApplication().getService(KnownGradlePluginsService::class.java).gradlePluginsData
          val studioProvidedInfo = StudioProvidedInfo.fromProject(project, buildRequestHolder)
          analyzersWrapper.onBuildSuccess(attributionData, pluginsData, analyzersProxy, studioProvidedInfo)
        }
        finally {
          FileUtils.deleteRecursivelyIfExists(FileUtils.join(attributionFileDir, SdkConstants.FD_BUILD_ATTRIBUTION))
        }
      }

      analyticsManager.logAnalyzersData(analyzersProxy)

      BuildAttributionUiManager.getInstance(project).showNewReport(
        BuildAttributionReportBuilder(analyzersProxy, buildFinishedTimestamp, buildRequestHolder).build(),
        buildSessionId
      )
    }
  }

  override fun onBuildFailure(request: GradleBuildInvoker.Request) {
    val attributionFileDir = getAgpAttributionFileDir(request)
    FileUtils.deleteRecursivelyIfExists(FileUtils.join(attributionFileDir, SdkConstants.FD_BUILD_ATTRIBUTION))
    analyzersWrapper.onBuildFailure()
    BuildAttributionUiManager.getInstance(project).onBuildFailure(UUID.randomUUID().toString())
  }

  override fun statusChanged(event: ProgressEvent?) {
    if (event == null) return

    analyzersWrapper.receiveEvent(event)
  }

  override fun openResultsTab() = BuildAttributionUiManager.getInstance(project)
    .openTab(BuildAttributionUiAnalytics.TabOpenEventSource.BUILD_OUTPUT_LINK)

  override fun shouldShowBuildOutputLink(): Boolean = !ConfigurationCacheTestBuildFlowRunner.getInstance(project)
    .runningFirstConfigurationCacheBuild
}