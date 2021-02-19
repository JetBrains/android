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

import com.android.build.attribution.data.PluginContainer
import com.android.build.attribution.data.PluginData
import com.android.build.attribution.data.ProjectConfigurationData
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.OperationDescriptor
import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.SuccessResult
import org.gradle.tooling.events.configuration.ProjectConfigurationFinishEvent
import org.gradle.tooling.events.configuration.ProjectConfigurationStartEvent
import org.gradle.tooling.events.configuration.ProjectConfigurationSuccessResult

/**
 * Analyzer for attributing project configuration time.
 */
class ProjectConfigurationAnalyzer(
  private val pluginContainer: PluginContainer
) : BaseAnalyzer<ProjectConfigurationAnalyzer.Result>(), BuildEventsAnalyzer {
  private val applyPluginEventPrefix = "Apply plugin"

  /**
   * Contains for each plugin, the sum of configuration times for this plugin over all projects
   */
  private val pluginsConfigurationDataMap = HashMap<PluginData, Long>()

  /**
   * Contains a list of project configuration data for each configured project
   */
  private val projectsConfigurationData = ArrayList<ProjectConfigurationData>()

  /**
   * Contains a list of all applied plugins for each configured project.
   * May contain internal plugins
   */
  private val allAppliedPlugins = mutableMapOf<String, List<PluginData>>()

  /**
   * Builder for configuration data of the currently being configured project
   * If no projects are being configured currently, then it will be null
   */
  private var projectConfigurationBuilder: ProjectConfigurationData.Builder? = null

  private fun updatePluginConfigurationTime(plugin: PluginData, configurationTimeMs: Long) {
    val currentConfigurationTime = pluginsConfigurationDataMap.getOrDefault(plugin, 0L)
    pluginsConfigurationDataMap[plugin] = currentConfigurationTime + configurationTimeMs

    projectConfigurationBuilder!!.addPluginConfigurationData(plugin, configurationTimeMs)
  }

  override fun receiveEvent(event: ProgressEvent) {
    if (event is ProjectConfigurationStartEvent) {
      projectConfigurationBuilder = ProjectConfigurationData.Builder(event.descriptor.project.projectPath)
    }
    else if (projectConfigurationBuilder != null) {
      // project configuration finished
      if (event is ProjectConfigurationFinishEvent && event.result is ProjectConfigurationSuccessResult) {
        allAppliedPlugins[event.descriptor.project.projectPath] =
          (event.result as ProjectConfigurationSuccessResult).pluginApplicationResults.map {
            pluginContainer.getPlugin(it.plugin, event.descriptor.project.projectPath)
          }
        projectsConfigurationData.add(projectConfigurationBuilder!!.build(event.result.endTime - event.result.startTime))
        projectConfigurationBuilder = null
      }
      else if (event is FinishEvent && event.result is SuccessResult) {
        // plugin configuration finish event is received
        if (event.descriptor.name.startsWith(applyPluginEventPrefix)) {

          // Check that the parent is not another binary plugin, to make sure that this plugin was added by the user
          if (event.descriptor.parent?.name?.startsWith(applyPluginEventPrefix) != true) {
            val pluginName = event.descriptor.name.substring(applyPluginEventPrefix.length + 1)
            val plugin = pluginContainer.getPlugin(PluginData.PluginType.BINARY_PLUGIN, pluginName,
                                                   projectConfigurationBuilder!!.projectPath)
            val pluginConfigurationTime = event.result.endTime - event.result.startTime

            updatePluginConfigurationTime(plugin, pluginConfigurationTime)

            // check if the plugin was applied in a build script block or on beforeEvaluate / afterEvaluate, if so then we need to subtract
            // the plugin configuration time from this configuration step to not account for it twice
            val configurationStepDescriptor = getFirstConfigurationStepInParentEvents(event.descriptor)
            if (configurationStepDescriptor != null) {
              projectConfigurationBuilder!!.subtractConfigurationStepTime(configurationStepDescriptor, pluginConfigurationTime)
            }
          }
        }
        // a configuration step event received that doesn't have a parent that is a configuration step
        else if (ProjectConfigurationData.ConfigurationStep.Type.values().any { it.isDescriptorOfType(event.descriptor) } &&
                 getFirstConfigurationStepInParentEvents(event.descriptor.parent) == null) {
          projectConfigurationBuilder!!.addConfigurationStepTime(event.descriptor, event.result.endTime - event.result.startTime)
        }
      }
    }
  }

  /**
   * Iterates recursively from the top parent and down to the given descriptor, until a configuration step event is found.
   */
  private fun getFirstConfigurationStepInParentEvents(descriptor: OperationDescriptor?): OperationDescriptor? {
    if (descriptor == null) {
      return null
    }
    val configurationStepDescriptor = getFirstConfigurationStepInParentEvents(descriptor.parent)
    // if a configuration step is already found then return it
    if (configurationStepDescriptor != null) {
      return configurationStepDescriptor
    }
    // if this is a configuration step, then return it
    if (ProjectConfigurationData.ConfigurationStep.Type.values().any { it.isDescriptorOfType(descriptor) }) {
      return descriptor
    }
    return null
  }

  override fun cleanupTempState() {
    projectsConfigurationData.clear()
    pluginsConfigurationDataMap.clear()
    allAppliedPlugins.clear()
    projectConfigurationBuilder = null
  }

  override fun calculateResult(): Result = Result(
    pluginsConfigurationDataMap.toMap(),
    projectsConfigurationData.toList(),
    allAppliedPlugins.toMap()
  )

  data class Result(
    /**
     * Contains for each plugin, the sum of configuration times for this plugin over all projects
     */
    val pluginsConfigurationDataMap: Map<PluginData, Long>,

    /**
     * Contains a list of project configuration data for each configured project
     */
    val projectsConfigurationData: List<ProjectConfigurationData>,

    /**
     * Contains a list of all applied plugins for each configured project.
     * May contain internal plugins
     */
    val allAppliedPlugins: Map<String, List<PluginData>>
  ) : AnalyzerResult
}
