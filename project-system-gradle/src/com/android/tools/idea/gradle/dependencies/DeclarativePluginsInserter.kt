/*
 * Copyright (C) 2025 The Android Open Source Project
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

import com.android.tools.idea.gradle.dsl.api.BasePluginsModel
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.dsl.api.GradleDeclarativeSettingsModel
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.dsl.api.settings.PluginsBlockModel
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.utils.addIfNotNull

class DeclarativePluginsInserter(private val projectModel: ProjectBuildModel) : PluginsInserter(projectModel) {
  override fun applySettingsPlugin(pluginId: String,
                                   version: String): Set<PsiFile> {
    val changedFiles = mutableSetOf<PsiFile>()

    getSettingsModel()?.plugins()?.let {
      it.applyPlugin(pluginId, version)
      changedFiles.addIfNotNull(projectModel.projectSettingsModel?.psiFile)
    }

    return changedFiles
  }

  /**
   * Applying plugin to module for declarative means nothing as all plugins can only be on settings
   */
  override fun addPlugin(pluginId: String, buildModel: GradleBuildModel, matcher: PluginMatcher): PsiFile? = null

  override fun addPlugin(pluginId: String,
                         version: String,
                         apply: Boolean?,
                         settingsPlugins: PluginsBlockModel,
                         buildModel: GradleBuildModel,
                         matcher: PluginMatcher): Set<PsiFile> {
    val changedFiles = mutableSetOf<PsiFile>()

    getSettingsModel()?.plugins()?.let { settings ->
      if (!settings.hasPlugins(matcher)) applySettingsPlugin(pluginId, version).also { changedFiles.addAll(it) }
    }

    return changedFiles
  }

  private fun getSettingsModel(): GradleDeclarativeSettingsModel? {
    val settingsFile = projectModel.declarativeSettingsModel
    if (settingsFile == null)
      log.warn("Settings file does not exist so cannot insert declaration into plugin{} block")
    return settingsFile
  }

  private fun BasePluginsModel.hasPlugins(matcher: PluginMatcher) = plugins().any { matcher.match(it) }
}