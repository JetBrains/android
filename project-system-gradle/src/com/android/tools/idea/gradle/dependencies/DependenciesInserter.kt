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

import com.android.ide.common.gradle.Dependency
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencySpec
import com.android.tools.idea.gradle.dsl.api.dependencies.DependenciesModel
import com.android.tools.idea.gradle.dsl.api.settings.PluginsBlockModel
import com.android.tools.idea.gradle.dsl.api.settings.PluginsModel
import com.intellij.psi.PsiFile
import org.gradle.api.plugins.JavaPlatformPlugin.CLASSPATH_CONFIGURATION_NAME
import org.jetbrains.kotlin.utils.addIfNotNull

open class DependenciesInserter(private val projectModel: ProjectBuildModel) {
  @JvmOverloads
  open fun addClasspathDependency(dependency: String,
                             excludes: List<ArtifactDependencySpec> = listOf(),
                             matcher: DependencyMatcher = ExactDependencyMatcher(CLASSPATH_CONFIGURATION_NAME, dependency)): Set<PsiFile> {
    val updatedFiles = mutableSetOf<PsiFile>()
    val buildModel = projectModel.projectBuildModel ?: return updatedFiles
    val buildscriptDependencies = buildModel.buildscript().dependencies()
    if (!buildscriptDependencies.hasArtifact(matcher)) {
      buildscriptDependencies.addArtifact(CLASSPATH_CONFIGURATION_NAME, dependency, excludes).also {
        updatedFiles.addIfNotNull(buildModel.psiFile)
      }
    }
    return updatedFiles
  }

  open fun addPlatformDependency(
    configuration: String,
    dependency: String,
    enforced: Boolean,
    parsedModel: GradleBuildModel,
    matcher: DependencyMatcher = ExactDependencyMatcher(configuration, dependency)): Set<PsiFile> {
    val buildscriptDependencies = parsedModel.dependencies()
    val updatedFiles = mutableSetOf<PsiFile>()

    if (!buildscriptDependencies.hasArtifact(matcher)) {
      buildscriptDependencies.addPlatformArtifact(configuration, dependency, enforced).also {
        updatedFiles.addIfNotNull(parsedModel.psiFile)
      }
    }

    return updatedFiles
  }

  /**
   * Adds plugin to catalog if it exists, to settings defined with settingsPlugins and to module itself
   *
   * It does not change root project build file as plugin information is going to settings plugin block
   */
  open fun addPlugin(pluginId: String,
                version: String,
                apply: Boolean?,
                settingsPlugins: PluginsBlockModel,
                buildModel: GradleBuildModel,
                matcher: PluginMatcher = IdPluginMatcher(pluginId)): Set<PsiFile> {
    val changedFiles = mutableSetOf<PsiFile>()

    // buildModel may be root project of multimodule project or module project itself
    val moduleInsertion = isSingleModuleProject() || buildModel.psiFile != projectModel.projectBuildModel?.psiFile

    settingsPlugins.applyPlugin(pluginId, version, apply)
    changedFiles.addIfNotNull(settingsPlugins.psiElement?.containingFile)
    if (moduleInsertion) {
      addPlugin(pluginId, buildModel, matcher)?.also { changedFiles.add(it) }
    }

    return changedFiles
  }

  /**
   * Method adds plugin to catalog if there is one, then to settings/project script (defined by projectPlugins) if it's not there
   * and then to specific project defined by buildModel
   */
  open fun addPlugin(pluginId: String,
                version: String,
                apply: Boolean?,
                projectPlugins: GradleBuildModel,
                buildModel: GradleBuildModel,
                matcher: PluginMatcher = IdPluginMatcher(pluginId)): Set<PsiFile> {
    val changedFiles = mutableSetOf<PsiFile>()

    // Inserting project level plugins in case
    // - projectPlugins in project file
    // - it's not single module project
    val insertProjectPlugins = shouldInsertProjectPlugins() && !projectPlugins.hasPlugin(matcher)
    // Insert plugins for module in case
    // - buildModel is a separate module
    // - buildModel is a single module project
    val moduleInsertion = shouldInsertModulePlugins(projectPlugins, buildModel)

    if (insertProjectPlugins) {
      projectPlugins.applyPlugin(pluginId, version, apply)
      changedFiles.addIfNotNull(projectPlugins.psiElement?.containingFile)
    }
    if (moduleInsertion) {
      addPlugin(pluginId, buildModel, matcher)?.also { changedFiles.add(it) }
    }
    return changedFiles
  }

  /**
   * Adds plugin to module but insert/check version catalog first
   * Project build file/settings stay intact
   */
  open fun addPluginToModule(pluginId: String,
                        version: String,
                        buildModel: GradleBuildModel,
                        matcher: PluginMatcher = IdPluginMatcher(pluginId)): Set<PsiFile> {
    val changedFiles = mutableSetOf<PsiFile>()
    addPlugin(pluginId, buildModel, matcher)?.also { changedFiles.add(it) }
    return changedFiles
  }

  open fun addDependency(configuration: String,
                    dependency: String,
                    excludes: List<ArtifactDependencySpec>,
                    parsedModel: GradleBuildModel,
                    matcher: DependencyMatcher): Set<PsiFile> {
    val updateFiles = mutableSetOf<PsiFile>()
    val dependenciesModel = parsedModel.dependencies()
    if (!dependenciesModel.hasArtifact(matcher)) {
      dependenciesModel.addArtifact(configuration, dependency, excludes).also {
        updateFiles.addIfNotNull(dependenciesModel.psiElement?.containingFile)
      }
    }
    return updateFiles
  }

  @JvmOverloads
  open fun addClasspathDependencyWithVersionVariable(dependency: String,
                                                variableName: String,
                                                excludes: List<ArtifactDependencySpec> = listOf(),
                                                matcher: DependencyMatcher = ExactDependencyMatcher(CLASSPATH_CONFIGURATION_NAME,
                                                                                                      dependency)): Set<PsiFile> {
    val updatedFiles = mutableSetOf<PsiFile>()
    val buildModel = projectModel.projectBuildModel ?: return updatedFiles
    val buildscriptDependencies = buildModel.buildscript().dependencies()

    val parsedDependency = Dependency.parse(dependency)
    val version = parsedDependency.version?.toIdentifier()
    buildModel.buildscript().ext().findProperty(variableName).setValue(version!!)
    updatedFiles.addIfNotNull(buildModel.psiFile)
    if (!buildscriptDependencies.hasArtifact(matcher)) {
      buildscriptDependencies.addArtifact(
        CLASSPATH_CONFIGURATION_NAME,
        "${parsedDependency.group}:${parsedDependency.name}:\$$variableName",
        excludes)
    }
    return updatedFiles
  }


  internal fun shouldInsertModulePlugins(projectPlugins: GradleBuildModel, buildModel: GradleBuildModel) =
    projectPlugins.getPluginsPsiElement()?.containingFile != buildModel.psiFile || isSingleModuleProject()

  internal fun shouldInsertProjectPlugins() = !isSingleModuleProject()

  internal fun isSingleModuleProject() =
    projectModel.projectSettingsModel?.modulePaths()?.let { it.size < 2 } ?: true


  /**
   * Adds plugin without version - it adds plugin declaration directly to build script file.
   */
  fun addPlugin(pluginId: String, buildModel: GradleBuildModel, matcher: PluginMatcher = IdPluginMatcher(pluginId)): PsiFile? =
    if (!buildModel.hasPlugin(matcher)) {
      buildModel.applyPlugin(pluginId)
      buildModel.psiFile
    }
    else
      null

  internal fun PluginsModel.hasPlugin(matcher: PluginMatcher): Boolean =
    plugins().any { matcher.match(it) }


  internal fun DependenciesModel.hasArtifact(matcher: DependencyMatcher): Boolean =
    artifacts().any { matcher.match(it) }

  /**
   * This is short version of addDependency function.
   * Assuming there is no excludes and algorithm will search exact dependency declaration in catalog if exists.
   */
  fun addDependency(configuration: String, dependency: String, parsedModel: GradleBuildModel) =
    addDependency(configuration, dependency, listOf(), parsedModel, ExactDependencyMatcher(configuration, dependency))

}