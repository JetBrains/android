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

import com.android.build.attribution.BuildAttributionWarningsFilter
import com.android.build.attribution.data.PluginData
import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.configuration.ProjectConfigurationFinishEvent
import org.gradle.tooling.events.configuration.ProjectConfigurationSuccessResult
import java.time.Duration

/**
 * Analyzer for reporting plugins slowing down project configuration.
 */
class ProjectConfigurationAnalyzer(override val warningsFilter: BuildAttributionWarningsFilter) : BuildEventsAnalyzer {
  val pluginsSlowingConfiguration = ArrayList<ProjectConfigurationData>()
  val projectsConfigurationData = ArrayList<ProjectConfigurationData>()

  override fun receiveEvent(event: ProgressEvent) {
    if (event is ProjectConfigurationFinishEvent) {
      val result = event.result
      if (result is ProjectConfigurationSuccessResult) {
        val pluginsConfigurationData = result.pluginApplicationResults.map {
          PluginConfigurationData(PluginData(it.plugin), it.totalConfigurationTime)
        }

        addProjectConfigurationData(pluginsConfigurationData, event.descriptor.project.projectPath, result.endTime - result.startTime)
      }
    }
  }

  private fun addProjectConfigurationData(pluginsConfigurationData: List<PluginConfigurationData>,
                                          project: String,
                                          totalConfigurationTime: Long) {
    projectsConfigurationData.add(ProjectConfigurationData(pluginsConfigurationData, project, totalConfigurationTime))
    analyzePluginsSlowingConfiguration(pluginsConfigurationData, project, totalConfigurationTime)
  }

  /**
   * Having android gradle plugin as a baseline, we report plugins that has a longer configuration time than agp as plugins slowing
   * configuration.
   */
  private fun analyzePluginsSlowingConfiguration(pluginsConfigurationData: List<PluginConfigurationData>,
                                                 project: String,
                                                 totalConfigurationTime: Long) {
    val androidGradlePlugin = pluginsConfigurationData.find { isAndroidGradlePlugin(it.plugin) } ?: return

    pluginsConfigurationData.filter {
      it.configurationDuration > androidGradlePlugin.configurationDuration &&
      warningsFilter.applyPluginSlowingConfigurationFilter(it.plugin.displayName)
    }.let {
      if (it.isNotEmpty()) {
        pluginsSlowingConfiguration.add(ProjectConfigurationData(it, project, totalConfigurationTime))
      }
    }
  }

  override fun onBuildStart() {
    pluginsSlowingConfiguration.clear()
    projectsConfigurationData.clear()
  }

  override fun onBuildSuccess() {
    // nothing to be done
  }

  override fun onBuildFailure() {
    pluginsSlowingConfiguration.clear()
    projectsConfigurationData.clear()
  }

  data class PluginConfigurationData(val plugin: PluginData, val configurationDuration: Duration)

  data class ProjectConfigurationData(val pluginsConfigurationData: List<PluginConfigurationData>,
                                      val project: String,
                                      val totalConfigurationTime: Long)
}
