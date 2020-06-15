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
package com.android.build.attribution.data

import org.gradle.tooling.events.OperationDescriptor
import org.jetbrains.kotlin.utils.addToStdlib.sumByLong

data class ProjectConfigurationData(val projectPath: String,
                                    val totalConfigurationTimeMs: Long,
                                    val pluginsConfigurationData: List<PluginConfigurationData>,
                                    val configurationSteps: List<ConfigurationStep>) {

  /**
   * Represent project configuration steps that are not plugins configuration
   */
  class ConfigurationStep(val type: Type, val configurationTimeMs: Long) {
    enum class Type {
      NOTIFYING_BUILD_LISTENERS {
        override fun isDescriptorOfType(descriptor: OperationDescriptor) = descriptor.name.startsWith("Notify beforeEvaluate listeners") ||
                                                                           descriptor.name.startsWith("Notify afterEvaluate listeners")
      },
      RESOLVING_DEPENDENCIES {
        override fun isDescriptorOfType(descriptor: OperationDescriptor) = descriptor.name.startsWith("Resolve dependencies of") ||
                                                                           descriptor.name.startsWith("Resolve files of")
      },
      COMPILING_BUILD_SCRIPTS {
        override fun isDescriptorOfType(descriptor: OperationDescriptor) = descriptor.name.startsWith("Compile script")
      },
      EXECUTING_BUILD_SCRIPT_BLOCKS {
        override fun isDescriptorOfType(descriptor: OperationDescriptor) = descriptor.displayName.startsWith("Execute '") &&
                                                                           descriptor.displayName.endsWith("' action")
      },
      OTHER {
        override fun isDescriptorOfType(descriptor: OperationDescriptor): Boolean = false
      };

      abstract fun isDescriptorOfType(descriptor: OperationDescriptor): Boolean
    }
  }

  class Builder(val projectPath: String) {

    private val pluginsConfigurationData = ArrayList<PluginConfigurationData>()

    private var notifyingBuildListenersTimeMs = 0L
    private var resolvingDependenciesTimeMs = 0L
    private var compilingBuildScriptsTimeMs = 0L
    private var executingBuildScriptsBlocksTimeMs = 0L

    fun addPluginConfigurationData(plugin: PluginData, configurationTimeMs: Long) {
      pluginsConfigurationData.add(PluginConfigurationData(plugin, configurationTimeMs))
    }

    fun subtractConfigurationStepTime(descriptor: OperationDescriptor, configurationTimeMs: Long) {
      // multiply configuration time by -1 to subtract it
      addConfigurationStepTime(descriptor, -1 * configurationTimeMs)
    }

    fun addConfigurationStepTime(descriptor: OperationDescriptor, configurationTimeMs: Long) {
      when {
        ConfigurationStep.Type.NOTIFYING_BUILD_LISTENERS.isDescriptorOfType(descriptor) -> {
          notifyingBuildListenersTimeMs += configurationTimeMs
        }
        ConfigurationStep.Type.RESOLVING_DEPENDENCIES.isDescriptorOfType(descriptor) -> {
          resolvingDependenciesTimeMs += configurationTimeMs
        }
        ConfigurationStep.Type.COMPILING_BUILD_SCRIPTS.isDescriptorOfType(descriptor) -> {
          compilingBuildScriptsTimeMs += configurationTimeMs
        }
        ConfigurationStep.Type.EXECUTING_BUILD_SCRIPT_BLOCKS.isDescriptorOfType(descriptor) -> {
          executingBuildScriptsBlocksTimeMs += configurationTimeMs
        }
      }
    }

    private fun getConfigurationSteps(totalConfigurationTimeMs: Long): List<ConfigurationStep> {
      val unaccountedConfigurationTime = totalConfigurationTimeMs -
                                         notifyingBuildListenersTimeMs -
                                         resolvingDependenciesTimeMs -
                                         compilingBuildScriptsTimeMs -
                                         executingBuildScriptsBlocksTimeMs -
                                         pluginsConfigurationData.sumByLong { it.configurationTimeMs }

      return listOf(ConfigurationStep(ConfigurationStep.Type.NOTIFYING_BUILD_LISTENERS, notifyingBuildListenersTimeMs),
                    ConfigurationStep(ConfigurationStep.Type.RESOLVING_DEPENDENCIES, resolvingDependenciesTimeMs),
                    ConfigurationStep(ConfigurationStep.Type.COMPILING_BUILD_SCRIPTS, compilingBuildScriptsTimeMs),
                    ConfigurationStep(ConfigurationStep.Type.EXECUTING_BUILD_SCRIPT_BLOCKS, executingBuildScriptsBlocksTimeMs),
                    ConfigurationStep(ConfigurationStep.Type.OTHER, unaccountedConfigurationTime))
        .filter { it.configurationTimeMs != 0L }
    }

    fun build(totalConfigurationTimeMs: Long): ProjectConfigurationData {
      return ProjectConfigurationData(projectPath,
                                      totalConfigurationTimeMs,
                                      pluginsConfigurationData,
                                      getConfigurationSteps(totalConfigurationTimeMs))
    }
  }
}
