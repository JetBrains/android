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
import com.android.build.attribution.data.PluginConfigurationData
import com.android.build.attribution.data.PluginContainer
import com.android.build.attribution.data.PluginData
import com.android.build.attribution.data.ProjectConfigurationData
import com.android.build.attribution.data.TaskContainer
import com.google.common.collect.ImmutableList
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.StartEvent
import org.gradle.tooling.events.SuccessResult
import org.gradle.tooling.events.configuration.ProjectConfigurationFinishEvent
import org.gradle.tooling.events.configuration.ProjectConfigurationStartEvent
import org.gradle.tooling.events.configuration.ProjectConfigurationSuccessResult
import java.time.Duration
import java.util.LinkedList

/**
 * Analyzer for reporting plugins slowing down project configuration.
 */
class ProjectConfigurationAnalyzer(override val warningsFilter: BuildAttributionWarningsFilter,
                                   taskContainer: TaskContainer,
                                   pluginContainer: PluginContainer) : BaseAnalyzer(taskContainer, pluginContainer), BuildEventsAnalyzer {
  val projectsConfigurationData = ArrayList<ProjectConfigurationData>()

  /**
   * The stack keeps track of the list of children for each plugin that is currently being applied.
   * Once a plugin is applied successfully, the top list of plugins in the stack will contain the children of this plugin (i.e. the plugins
   * that are directly configured by this plugin). After this list (the top of the stack) is removed, this plugin is added to the top of the
   * stack as it will contain the children of the parent plugin.
   *
   * Example:
   * apply pluginA started    stack = (())
   * apply pluginB started    stack = ((), ())
   * apply pluginC started    stack = ((), (), ())
   * apply pluginC finished   stack = ((), (pluginC))       pluginC children are ()
   * apply pluginB finished   stack = ((pluginB))           pluginB children are (pluginC)
   * apply pluginD started    stack = ((pluginB), ())
   * apply pluginD finished   stack = ((pluginB, pluginD))  pluginD children are ()
   * apply pluginA finished   stack = ()                    pluginA children are (pluginB, pluginD)
   *
   * Final plugin tree structure:
   *           -> pluginB -> pluginC
   * pluginA -|
   *           -> pluginD
   */
  private val pluginsStack = LinkedList<MutableList<PluginConfigurationData>>()

  private var currentlyConfiguredProjectPath: String? = null

  override fun receiveEvent(event: ProgressEvent) {
    if (event is ProjectConfigurationStartEvent) {
      currentlyConfiguredProjectPath = event.descriptor.project.projectPath
      pluginsStack.push(ArrayList())
    }
    else if (currentlyConfiguredProjectPath != null) {
      // project configuration finished
      if (event is ProjectConfigurationFinishEvent && event.result is ProjectConfigurationSuccessResult) {
        addProjectConfigurationData(pluginsStack.pop(), event.descriptor.project.projectPath,
                                    Duration.ofMillis(event.result.endTime - event.result.startTime))
        pluginsStack.clear()
        currentlyConfiguredProjectPath = null
      }
      // plugin/script configuration progress event is received
      else if (event.displayName.startsWith("Apply script") || event.displayName.startsWith("Apply plugin")) {
        if (event is StartEvent) {
          pluginsStack.push(ArrayList())
        }
        else if (event is FinishEvent && event.result is SuccessResult) {
          val pluginName = event.displayName.split(" ")[2]
          val pluginData = if (event.displayName.startsWith("Apply plugin")) getPlugin(PluginData.PluginType.PLUGIN, pluginName,
                                                                                       currentlyConfiguredProjectPath!!)
          else getPlugin(PluginData.PluginType.SCRIPT, pluginName, currentlyConfiguredProjectPath!!)
          val pluginConfigurationData = PluginConfigurationData(pluginData,
                                                                Duration.ofMillis(event.result.endTime - event.result.startTime),
                                                                pluginsStack.pop())

          pluginsStack.first.add(pluginConfigurationData)
        }
      }
    }
  }

  private fun addProjectConfigurationData(pluginsConfigurationData: List<PluginConfigurationData>,
                                          project: String,
                                          totalConfigurationTime: Duration) {
    projectsConfigurationData.add(
      ProjectConfigurationData(ImmutableList.copyOf(pluginsConfigurationData), project, totalConfigurationTime))
    analyzePluginsSlowingConfiguration(pluginsConfigurationData)
  }

  /**
   * Gets the user added plugins by expanding buildscript plugins to find the added binary plugins.
   */
  private fun getUserAddedPlugins(pluginsConfigurationData: List<PluginConfigurationData>,
                                  userAddedPluginsData: MutableList<PluginConfigurationData>) {
    pluginsConfigurationData.forEach { pluginData ->
      if (pluginData.plugin.pluginType == PluginData.PluginType.PLUGIN) {
        userAddedPluginsData.add(pluginData)
      }
      else {
        getUserAddedPlugins(pluginData.nestedPluginsConfigurationData, userAddedPluginsData)
      }
    }
  }

  /**
   * Having android gradle plugin as a baseline, we report plugins that have a longer configuration time than agp as plugins slowing
   * configuration.
   *
   * Note that pluginA that is added by the user could configure pluginB, and pluginB is the one causing configuration to be slow, however
   * we report pluginA as the slow one as it's more familiar to the user, and the nested plugins data should contain the information needed
   * for the plugin developers to figure out which nested plugin is the real cause for the plugin being slow.
   */
  private fun analyzePluginsSlowingConfiguration(pluginsConfigurationData: List<PluginConfigurationData>) {
    val userAddedPluginsData = ArrayList<PluginConfigurationData>()
    getUserAddedPlugins(pluginsConfigurationData, userAddedPluginsData)
    val androidGradlePlugin = userAddedPluginsData.find { isAndroidPlugin(it.plugin) } ?: return

    userAddedPluginsData.filter {
      it.configurationDuration > androidGradlePlugin.configurationDuration &&
      warningsFilter.applyPluginSlowingConfigurationFilter(it.plugin.displayName)
    }.forEach {
      it.isSlowingConfiguration = true
    }
  }

  override fun onBuildStart() {
    super.onBuildStart()
    projectsConfigurationData.clear()
    pluginsStack.clear()
    currentlyConfiguredProjectPath = null
  }

  override fun onBuildSuccess() {
    // nothing to be done
  }

  override fun onBuildFailure() {
    projectsConfigurationData.clear()
    pluginsStack.clear()
    currentlyConfiguredProjectPath = null
  }
}
