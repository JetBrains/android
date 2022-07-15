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
package com.android.build.attribution.analyzers

import com.android.build.attribution.data.GradlePluginsData
import com.android.build.attribution.data.PluginContainer
import com.android.build.attribution.data.StudioProvidedInfo
import com.android.build.attribution.data.TaskContainer
import com.android.ide.common.attribution.AndroidGradlePluginAttributionData
import org.gradle.tooling.events.ProgressEvent

class BuildAnalyzersWrapper(
  private val buildAnalyzers: List<BaseAnalyzer<out AnalyzerResult>>,
  private val taskContainer: TaskContainer,
  private val pluginContainer: PluginContainer
) {

  private val buildEventsAnalyzers = buildAnalyzers.filterIsInstance<BuildEventsAnalyzer>()
  private val buildAttributionReportAnalyzers = buildAnalyzers.filterIsInstance<BuildAttributionReportAnalyzer>()
  private val knownPluginsDataAnalyzers = buildAnalyzers.filterIsInstance<KnownPluginsDataAnalyzer>()
  private val postBuildAnalyzers = buildAnalyzers.filterIsInstance<PostBuildProcessAnalyzer>()

  fun onBuildStart() {
    taskContainer.clear()
    pluginContainer.clear()
    buildAnalyzers.forEach(BaseAnalyzer<*>::onBuildStart)
  }

  fun onBuildSuccess(
    androidGradlePluginAttributionData: AndroidGradlePluginAttributionData?,
    gradlePluginsData: GradlePluginsData,
    analyzersResult: BuildEventsAnalyzersProxy,
    studioProvidedInfo: StudioProvidedInfo
  ) {

    if (androidGradlePluginAttributionData != null) {
      taskContainer.updateTasksData(androidGradlePluginAttributionData)
      pluginContainer.updatePluginsData(androidGradlePluginAttributionData)
    }

    if (androidGradlePluginAttributionData != null) {
      buildAttributionReportAnalyzers.forEach { it.receiveBuildAttributionReport(androidGradlePluginAttributionData) }
    }

    knownPluginsDataAnalyzers.forEach { it.receiveKnownPluginsData(gradlePluginsData) }

    postBuildAnalyzers.forEach { it.runPostBuildAnalysis(analyzersResult, studioProvidedInfo) }
  }

  fun onBuildFailure() {
    buildAnalyzers.forEach(BaseAnalyzer<*>::onBuildFailure)
  }

  fun receiveEvent(event: ProgressEvent) {
    buildEventsAnalyzers.forEach { it.receiveEvent(event) }
  }
}