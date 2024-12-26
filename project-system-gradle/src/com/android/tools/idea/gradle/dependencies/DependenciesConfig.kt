/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.gradle.dependencies

typealias ConfigName = String
typealias Dependency = String

data class DependenciesConfig(
  val plugins: List<PluginDescription>,
  val pluginMatcherFactory: (PluginDescription) -> PluginMatcher,
  val pluginInsertionConfig: PluginInsertionConfig,
  val dependencies: List<DependencyDescription>,
  val dependencyMatcherFactory: (ConfigName, Dependency) -> DependencyMatcher,
  val platforms: List<PlatformDescription>,
){
  companion object {
    fun defaultConfig() =
      DependenciesConfig(
        listOf(),
        { descr -> IdPluginMatcher(descr.pluginId) },
        PluginInsertionConfig.defaultInsertionConfig(),
        listOf(),
        { configName, dependency -> ExactDependencyMatcher(configName, dependency) },
        listOf()
      )
  }

  fun withPlugin(plugin: PluginDescription) = this.copy(plugins = plugins + plugin)
  fun withPlugins(newPlugins: List<PluginDescription>) = this.copy(plugins = plugins + newPlugins)
  fun withDependency(dependency: DependencyDescription) = this.copy(dependencies = dependencies + dependency)
  fun withDependencies(newDependencies: List<DependencyDescription>) = this.copy(dependencies = dependencies + newDependencies)
  fun withPlatform(platform: PlatformDescription) = this.copy(platforms = platforms + platform)
  fun withPlatforms(newPlatforms: List<PlatformDescription>) = this.copy(platforms = platforms + newPlatforms)
}

data class PluginDescription(val pluginId: String, val version: String, val classpathModule: String)
data class DependencyDescription(val configurationName: ConfigName, val dependency: Dependency)
data class PlatformDescription(val configurationName: ConfigName, val dependency: Dependency, val enforced: Boolean)
