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
package com.android.build.attribution.ui.data.builder

import com.android.build.attribution.analyzers.BuildEventsAnalysisResult
import com.android.build.attribution.data.PluginConfigurationData
import com.android.build.attribution.data.ProjectConfigurationData
import com.android.build.attribution.ui.data.ConfigurationUiData
import com.android.build.attribution.ui.data.PluginConfigurationUiData
import com.android.build.attribution.ui.data.ProjectConfigurationUiData
import com.android.build.attribution.ui.data.TimeWithPercentage

/**
 * Builds the Plugins Configuration Time report from the data gathered by Gradle build analyzers.
 * It accesses analysis results from [analyzersProxy] and provides an implementation for [ConfigurationUiData].
 */
class ConfigurationTimesUiDataBuilder(
  val analyzersProxy: BuildEventsAnalysisResult
) {

  val totalConfigurationTimeMs = analyzersProxy.getConfigurationPhaseTimeMs()

  fun build(): ConfigurationUiData = createConfigurationUiData()

  private fun createConfigurationUiData(): ConfigurationUiData =
    object : ConfigurationUiData {
      override val totalConfigurationTime = TimeWithPercentage(totalConfigurationTimeMs, analyzersProxy.getTotalBuildTimeMs())
      override val projects: List<ProjectConfigurationUiData> = analyzersProxy.getProjectsConfigurationData()
        .map { createProjectConfigurationUiData(it) }
        .sortedByDescending { it.configurationTime }
      override val totalIssueCount: Int = projects.sumOf { it.issueCount }
    }

  private fun createProjectConfigurationUiData(projectData: ProjectConfigurationData): ProjectConfigurationUiData =
    object : ProjectConfigurationUiData {
      override val project = projectData.projectPath
      override val configurationTime = TimeWithPercentage(projectData.totalConfigurationTimeMs, totalConfigurationTimeMs)
      override val plugins = projectData.pluginsConfigurationData
        .map { createPluginConfigurationUiData(it) }
        .sortedByDescending { it.configurationTime }
      override val issueCount = plugins.count { it.slowsConfiguration } + plugins.sumOf { it.nestedIssueCount }
    }

  private fun createPluginConfigurationUiData(pluginData: PluginConfigurationData): PluginConfigurationUiData =
    object : PluginConfigurationUiData {
      override val pluginName = pluginData.plugin.displayName
      override val configurationTime = TimeWithPercentage(pluginData.configurationTimeMs, totalConfigurationTimeMs)
      override val slowsConfiguration = false
      override val nestedPlugins: List<PluginConfigurationUiData> = emptyList()
      override val nestedIssueCount: Int = nestedPlugins.count { it.slowsConfiguration } + nestedPlugins.sumOf { it.nestedIssueCount }
    }
}
