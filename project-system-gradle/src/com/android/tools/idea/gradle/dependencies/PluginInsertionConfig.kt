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

import com.android.tools.idea.gradle.dependencies.CommonPluginsInserter.PluginClasspathInfo
import com.android.tools.idea.gradle.dependencies.PluginsInserter.TryAddResult
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel

data class PluginInsertionConfig(
  val trySteps: LinkedHashSet<PluginInsertionStep>,
  val whenFoundSame: MatchedStrategy,
  val variableName: String? = null,
  val addRepoForSnapshots: Boolean? = null
) {

  // specifies what to do when found plugin with same id but different version
  enum class MatchedStrategy {
    UPDATE_VERSION, DO_NOTHING
  }

  sealed class PluginInsertionStep {
    abstract fun getAddLazyCall(
      helper: CommonPluginsInserter,
      config: PluginInsertionConfig,
      pluginId: String,
      version: String,
      pluginMatcher: PluginMatcher = IdPluginMatcher(pluginId),
      classpathInfo: PluginClasspathInfo?
    ): Lazy<TryAddResult>

    abstract fun getUpdateLazyCall(
      helper: CommonPluginsInserter,
      config: PluginInsertionConfig,
      pluginId: String,
      version: String,
      classpathInfo: PluginClasspathInfo?
    ): Lazy<TryAddResult>
  }

  object BuildscriptClasspathInsertionStep : PluginInsertionStep() {
    override fun getAddLazyCall(
      helper: CommonPluginsInserter,
      config: PluginInsertionConfig,
      pluginId: String,
      version: String,
      pluginMatcher: PluginMatcher,
      classpathInfo: PluginClasspathInfo?): Lazy<TryAddResult> =
      lazy {
        require(classpathInfo != null) {
          "classpathInfo name must not be null for BUILDSCRIPT_CLASSPATH"
        }
        helper.tryAddToBuildscriptDependencies(classpathInfo.dependency, classpathInfo.matcher).also {
          helper.maybeAddRepoToBuildscript(config, version, it) { model -> model.buildscript() }
        }
      }

    override fun getUpdateLazyCall(
      helper: CommonPluginsInserter,
      config: PluginInsertionConfig,
      pluginId: String,
      version: String,
      classpathInfo: PluginClasspathInfo?
    ): Lazy<TryAddResult> =
      lazy {
        require(classpathInfo != null) {
          "classpathDependency and classpathMatcher name must not be null for BUILDSCRIPT_CLASSPATH"
        }
        helper.updateDependencyVersion(classpathInfo.dependency) { projectBuildModel-> projectBuildModel.buildscript().dependencies() }.also {
          helper.maybeAddRepoToBuildscript(config, version, it) { model -> model.buildscript() }
        }
      }
  }

  object BuildscriptClasspathWithVariableInsertionStep : PluginInsertionStep() {
    override fun getAddLazyCall(
      helper: CommonPluginsInserter,
      config: PluginInsertionConfig,
      pluginId: String,
      version: String,
      pluginMatcher: PluginMatcher,
      classpathInfo: PluginClasspathInfo?): Lazy<TryAddResult> =
      lazy {
        require(config.variableName != null && classpathInfo != null) {
          "classpathInfo and classpathMatcher name must not be null for BUILDSCRIPT_CLASSPATH_WITH_VARIABLE"
        }
        helper.tryAddClasspathDependencyWithVersionVariable(
          classpathInfo.dependency,
          config.variableName,
          listOf(),
          classpathInfo.matcher
        ).also {
          helper.maybeAddRepoToBuildscript(config, version, it){ model -> model.buildscript() }
        }
      }

    override fun getUpdateLazyCall(
      helper: CommonPluginsInserter,
      config: PluginInsertionConfig,
      pluginId: String,
      version: String,
      classpathInfo: PluginClasspathInfo?
    ): Lazy<TryAddResult> =
      lazy {
        require(classpathInfo != null) {
          "classpathDependency and classpathMatcher name must not be null for BUILDSCRIPT_CLASSPATH"
        }
        helper.updateDependencyVersion(classpathInfo.dependency) { projectBuildModel-> projectBuildModel.buildscript().dependencies() }.also {
          helper.maybeAddRepoToBuildscript(config, version, it) { model -> model.buildscript() }
        }
      }
  }

  object PluginManagementInsertionStep : PluginInsertionStep() {
    override fun getAddLazyCall(
      helper: CommonPluginsInserter,
      config: PluginInsertionConfig,
      pluginId: String,
      version: String,
      pluginMatcher: PluginMatcher,
      classpathInfo: PluginClasspathInfo?): Lazy<TryAddResult> =
      lazy {
        helper.tryAddToPluginsManagementBlock(pluginId, version, pluginMatcher).also {
          helper.maybeAddRepoToPluginManagement(config, version, it) { model -> model.projectSettingsModel?.pluginManagement() }
        }
      }

    override fun getUpdateLazyCall(
      helper: CommonPluginsInserter,
      config: PluginInsertionConfig,
      pluginId: String,
      version: String,
      classpathInfo: PluginClasspathInfo?
    ): Lazy<TryAddResult> =
      lazy {
        helper.updatePluginForPluginsBlock(pluginId, version) {
          projectModel: ProjectBuildModel -> projectModel.projectSettingsModel?.pluginManagement()?.plugins()
        }.also {
          helper.maybeAddRepoToPluginManagement(config, version, it) { model -> model.projectSettingsModel?.pluginManagement() }
        }
      }
  }

  object PluginBlockInsertionStep : PluginInsertionStep() {
    override fun getAddLazyCall(
      helper: CommonPluginsInserter,
      config: PluginInsertionConfig,
      pluginId: String,
      version: String,
      pluginMatcher: PluginMatcher,
      classpathInfo: PluginClasspathInfo?): Lazy<TryAddResult> =
      lazy {
        helper.tryAddToPluginsBlock(pluginId, version, pluginMatcher).also {
          helper.maybeAddRepoToPluginManagement(config, version, it) { model -> model.projectSettingsModel?.pluginManagement() }
        }
      }

    override fun getUpdateLazyCall(
      helper: CommonPluginsInserter,
      config: PluginInsertionConfig,
      pluginId: String,
      version: String,
      classpathInfo: PluginClasspathInfo?
    ): Lazy<TryAddResult> =
      lazy {
        helper.updatePluginForBuildModel(pluginId, version) {
          projectModel: ProjectBuildModel -> projectModel.projectBuildModel
        }.also {
          helper.maybeAddRepoToPluginManagement(config, version, it) { model -> model.projectSettingsModel?.pluginManagement() }
        }
      }
  }

  companion object {
    fun defaultInsertionConfig(): PluginInsertionConfig {
      val steps = LinkedHashSet<PluginInsertionStep>()
      steps.addAll(listOf(PluginManagementInsertionStep,
                          PluginBlockInsertionStep,
                          BuildscriptClasspathInsertionStep))
      return PluginInsertionConfig(
        steps,
        MatchedStrategy.DO_NOTHING
      )
    }
  }
}


