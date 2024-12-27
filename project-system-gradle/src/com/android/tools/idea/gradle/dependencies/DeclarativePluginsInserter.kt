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

import com.android.tools.idea.gradle.dependencies.PluginInsertionConfig.Companion.defaultInsertionConfig
import com.android.tools.idea.gradle.dependencies.PluginInsertionConfig.MatchedStrategy
import com.android.tools.idea.gradle.dsl.api.AndroidDeclarativeType
import com.android.tools.idea.gradle.dsl.api.BasePluginsModel
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.dsl.api.GradleDeclarativeSettingsModel
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencySpec
import com.android.tools.idea.gradle.dsl.api.settings.PluginsBlockModel
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.utils.addIfNotNull

class DeclarativePluginsInserter(private val projectModel: ProjectBuildModel) : PluginsInserter(projectModel) {
  override fun applySettingsPlugin(pluginId: String,
                                   version: String): Set<PsiFile> {
    val changedFiles = mutableSetOf<PsiFile>()

    getSettingsModel()?.plugins()?.let {
      it.applyPlugin(pluginId, version)
      changedFiles.addIfNotNull(projectModel.declarativeSettingsModel?.psiFile)
    }

    return changedFiles
  }

  /**
   * Applying plugin to module for declarative means nothing as all plugins can only be on settings
   */
  override fun addPlugin(pluginId: String, buildModel: GradleBuildModel, matcher: PluginMatcher): PsiFile =
    throw IllegalStateException("Add plugin to module (addPlugin function) is impossible for declarative. Low level API like this will be removed eventually")

  override fun addPluginToModule(pluginId: String,
                             version: String,
                             buildModel: GradleBuildModel,
                             matcher: PluginMatcher): Set<PsiFile> =
    throw IllegalStateException("Add plugin to module (addPluginToModule function) is impossible for declarative. Low level API like this will be removed eventually")

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

  override fun addPluginOrClasspath(
    pluginId: String,
    classpathModule: String,
    version: String,
    buildModels: List<GradleBuildModel>,
    matcherFactory: (String, String) -> PluginMatcher,
    classpathMatcher: DependencyMatcher,
    config: PluginInsertionConfig
  ): Set<PsiFile> = applyPlugin(pluginId, version, buildModels, config.whenFoundSame, matcherFactory)


  override fun findPlaceAndAddPlugin(pluginId: String,
                                     version: String,
                                     buildModels: List<GradleBuildModel>,
                                     matcherFactory: (String, String) -> PluginMatcher): Set<PsiFile> =
    applyPlugin(pluginId, version, buildModels, defaultInsertionConfig().whenFoundSame, matcherFactory)

  override fun addClasspathDependency(dependency: String,
                                      excludes: List<ArtifactDependencySpec>,
                                      matcher: DependencyMatcher): Set<PsiFile> =
    throw IllegalStateException("Add classpath is impossible for declarative. Low level API like this will be removed eventually")

  override fun addClasspathDependencyWithVersionVariable(dependency: String,
                                                     variableName: String,
                                                     excludes: List<ArtifactDependencySpec>,
                                                     matcher: DependencyMatcher): Set<PsiFile> =
    throw IllegalStateException("Add classpath with variable is impossible for declarative. Low level API like this will be removed eventually")

  private fun applyPlugin(pluginId: String,
                          version: String?,
                          buildModels: List<GradleBuildModel>,
                          whenFoundPlugin: MatchedStrategy,
                          matcherFactory: (String, String) -> PluginMatcher = { id, _ -> IdPluginMatcher(id) }): Set<PsiFile> {
    val changedFiles = mutableSetOf<PsiFile>()
    projectModel.declarativeSettingsModel?.plugins() ?: run {
      log.warn("Settings file does not exist so cannot insert plugin for declarative project")
      return changedFiles
    }
    val ecosystemPlugin = pluginToEcosystemPluginMap.get(pluginId)
    if (ecosystemPlugin != null) {
      // assuming ecosystem version is the same as for real plugin
      val ecosystemPluginVersion = version
                                   ?: throw IllegalArgumentException("Version cannot be null for ecosystem plugin for $pluginId")

      val component = getEcosystemPlugin(pluginId)
      if (component == null) {
        log.warn("Unknown declarative plugin $pluginId")
        return changedFiles
      }
      val ecosystemMatcher = matcherFactory(ecosystemPlugin, ecosystemPluginVersion)
      if (!hasPlugin(ecosystemMatcher)) {
        applySettingsPlugin(ecosystemPlugin, ecosystemPluginVersion).also { changedFiles.addAll(it) }
        buildModels.forEach { addModuleComponent(component, it).also { changedFiles.addAll(it) } }
      }
      else if (whenFoundPlugin == MatchedStrategy.UPDATE_VERSION) {
        updatePlugin(ecosystemPlugin, ecosystemPluginVersion).also { changedFiles.addAll(it) }
      }
    }
    else {
      if (version != null) {
        val pluginMatcher = matcherFactory(pluginId, version)
        if (!hasPlugin(pluginMatcher)) {
          applySettingsPlugin(pluginId, version).also { changedFiles.addAll(it) }
        }
        else if (whenFoundPlugin == MatchedStrategy.UPDATE_VERSION) {
          updatePlugin(pluginId, version).also { changedFiles.addAll(it) }
        }
        // TODO - not clear how to apply non ecosystem plugins

      }
      else {
        // TODO - insert gradle ecosystem plugin
      }
    }
    return changedFiles
  }

  private fun updatePlugin(pluginId: String,
                           version: String): Set<PsiFile> {
    val plugin = projectModel.declarativeSettingsModel?.plugins()?.plugins()?.firstOrNull { it.name().toString() == pluginId && it.version().toString() != version }
    if (plugin != null) {
      plugin.version().resolve().setValue(version)
      val result = mutableSetOf<PsiFile>()
      result.addIfNotNull(projectModel.declarativeSettingsModel?.psiFile)
      return result
    }
    return setOf()
  }

  private fun hasPlugin(
    pluginMatcher: PluginMatcher,
  ): Boolean {
    val settingsPlugins =
      projectModel.declarativeSettingsModel?.plugins()?.plugins()
    return settingsPlugins?.any { pluginMatcher.match(it) } == true
  }

  private fun addModuleComponent(plugin: EcosystemPlugin, buildModel: GradleBuildModel): Set<PsiFile> {
    val changedFiles = mutableSetOf<PsiFile>()
    val declarativeBuildModel = projectModel.getDeclarativeModuleBuildModel(buildModel.virtualFile)
    val type = when (plugin) {
      EcosystemPlugin.APPLICATION -> AndroidDeclarativeType.APPLICATION
      EcosystemPlugin.LIBRARY -> AndroidDeclarativeType.LIBRARY
    }
    return declarativeBuildModel?.let { model ->
      if (model.existingAndroidElement() == null) {
        model.createAndroidElement(type)
        changedFiles.addIfNotNull(model.psiFile)
      }
      changedFiles
    } ?: changedFiles
  }

  private fun getEcosystemPlugin(plugin: String): EcosystemPlugin? =
    EcosystemPlugin.entries.find { it.pluginId == plugin }

  private fun getSettingsModel(): GradleDeclarativeSettingsModel? {
    val settingsFile = projectModel.declarativeSettingsModel
    if (settingsFile == null)
      log.warn("Settings file does not exist so cannot insert declaration into plugin{} block")
    return settingsFile
  }

  private fun BasePluginsModel.hasPlugins(matcher: PluginMatcher) = plugins().any { matcher.match(it) }

  companion object {
    val log = Logger.getInstance(DependenciesHelper::class.java)
    val pluginToEcosystemPluginMap: Map<String, String> =
      listOf("com.android.application",
             "com.android.library",
             "com.android.test",
             "com.android.asset-pack",
             "com.android.dynamic-feature").associateWith { "com.android.ecosystem" }
  }
}

enum class EcosystemPlugin(val pluginId: String) {
  APPLICATION("com.android.application"),
  LIBRARY("com.android.library"),
  /*
  uncomment once AGP ecosystem  will support more
  TEST("com.android.test"),
  ASSET_PACK("com.android.asset-pack"),
  DYNAMIC_FEATURE("com.android.dynamic-feature")
  */
}