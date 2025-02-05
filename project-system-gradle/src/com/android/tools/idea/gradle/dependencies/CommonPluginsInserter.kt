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

import com.android.ide.common.gradle.Dependency
import com.android.tools.idea.gradle.dependencies.PluginInsertionConfig.Companion.defaultInsertionConfig
import com.android.tools.idea.gradle.dependencies.PluginInsertionConfig.MatchedStrategy
import com.android.tools.idea.gradle.dsl.api.BasePluginsModel
import com.android.tools.idea.gradle.dsl.api.BuildScriptModel
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.dsl.api.PluginModel
import com.android.tools.idea.gradle.dsl.api.PluginsModel
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencySpec
import com.android.tools.idea.gradle.dsl.api.dependencies.DependenciesModel
import com.android.tools.idea.gradle.dsl.api.repositories.RepositoriesModel
import com.android.tools.idea.gradle.dsl.api.settings.PluginManagementModel
import com.android.tools.idea.gradle.dsl.api.settings.PluginsBlockModel
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiFile
import org.gradle.api.plugins.JavaPlatformPlugin.CLASSPATH_CONFIGURATION_NAME
import org.jetbrains.kotlin.utils.addIfNotNull
import com.android.tools.idea.gradle.dependencies.PluginsInserter.TryAddResult

open class CommonPluginsInserter(private val projectModel: ProjectBuildModel): PluginsInserter {
  /**
   * Adds plugin in a smart way. Consider version catalog vs non version catalog projects.
   * Looks for pluginsManagement (settings) section, plugins block in root project buildscript,
   * buildscript dependencies section.
   * In case none above found (new project) we fall back to adding project level plugin alias
   * (as plugin itself is declared in version catalog for new projects) to root project plugin block.
   *
   * The plugin is applied to each of the modules in [buildModels]
   */
  override fun addPluginOrClasspath(
    pluginId: String,
    classpathModule: String,
    version: String,
    buildModels: List<GradleBuildModel>,
    matcherFactory: (String, String) -> PluginMatcher,
    classpathMatcher: DependencyMatcher,
    config: PluginInsertionConfig
  ): Set<PsiFile> {
    val classpathInfo = PluginClasspathInfo("$classpathModule:$version", classpathMatcher)
    return findPlaceAndAddPluginOrClasspath(pluginId, version, buildModels, matcherFactory(pluginId, version), classpathInfo, config)
  }

  override fun findPlaceAndAddPlugin(
    pluginId: String,
    version: String,
    buildModels: List<GradleBuildModel>,
    matcherFactory: (String, String) -> PluginMatcher
  ): Set<PsiFile> =
    findPlaceAndAddPluginOrClasspath(pluginId, version, buildModels, matcherFactory(pluginId, version), null, defaultInsertionConfig())

  data class PluginClasspathInfo(val dependency: String, val matcher: DependencyMatcher)

  private fun findPlaceAndAddPluginOrClasspath(
    pluginId: String,
    version: String,
    buildModels: List<GradleBuildModel>,
    pluginMatcher: PluginMatcher = IdPluginMatcher(pluginId),
    classpathInfo: PluginClasspathInfo?,
    config: PluginInsertionConfig
  ): Set<PsiFile> {
    val projectBuildModel = projectModel.projectBuildModel ?: error("Build model for root project not found")
    val updatedFiles = mutableSetOf<PsiFile>()
    if (!hasPlugin(pluginMatcher, classpathInfo?.matcher)) {
      val result = config.trySteps.map { tryStep ->
        tryStep.getAddLazyCall(this, config, pluginId, version, pluginMatcher, classpathInfo)
      }.firstOrNull { it.value.succeed }?.value

      // in case there is nothing - we force adding plugin to root project plugins block
      result?.changedFiles?.let { updatedFiles.addAll(it) } ?: updatedFiles.addAll(
        addPlugin(pluginId, version, apply = false, projectBuildModel, projectBuildModel)
      )
    }
    else if (config.whenFoundSame == MatchedStrategy.UPDATE_VERSION) {
      val result = config.trySteps.map { tryStep ->
        tryStep.getUpdateLazyCall(this, config, pluginId, version, classpathInfo)
      }.firstOrNull { it.value.succeed }?.value
      result?.changedFiles?.let { updatedFiles.addAll(it) } ?:
      projectBuildModel.psiFile?.let {
        updatedFiles.addAll(
          updatePlugin(pluginId, version, projectBuildModel, it).changedFiles
        )
      }
    }
    buildModels.forEach {
      updatedFiles.addAll(addPluginToModule(pluginId, version, it))
    }

    return updatedFiles
  }

  internal fun maybeAddRepoToPluginManagement(config: PluginInsertionConfig,
                                     version: String,
                                     result: TryAddResult,
                                     model: (ProjectBuildModel) -> PluginManagementModel?) {
    if (config.addRepoForSnapshots == true) result.appendWhenSuccess {
      model(projectModel)?.repositories()?.let { addRepositoryFor(version, it) }
    }
  }

  internal fun maybeAddRepoToBuildscript(config: PluginInsertionConfig,
                                version: String,
                                result: TryAddResult,
                                model: (GradleBuildModel) -> BuildScriptModel?) {
    if (config.addRepoForSnapshots == true) result.appendWhenSuccess {
      model(getProjectBuildModel())?.repositories()?.let { addRepositoryFor(version, it) }
    }
  }

  private fun getProjectBuildModel() =
    projectModel.projectBuildModel ?: error("Build model for root project not found")

  internal fun updatePluginForBuildModel(
    pluginId: String,
    version: String,
    getModel: (ProjectBuildModel) -> GradleBuildModel?
  ): TryAddResult {
    val model = getModel(projectModel) ?: return TryAddResult.failed()
    return updatePlugin(pluginId, version, model, model.psiFile)
  }

  internal fun updatePluginForPluginsBlock(
    pluginId: String,
    version: String,
    getModel: (ProjectBuildModel) -> PluginsBlockModel?
  ): TryAddResult {
    val model = getModel(projectModel) ?: return TryAddResult.failed()
    return updatePlugin(pluginId, version, model, model.psiElement?.containingFile)
  }

  override fun addRepositoryFor(version: String, model: RepositoriesModel): PsiFile? {
    var updated = false
    if (version.contains("SNAPSHOT")) {
      model.addMavenRepositoryByUrl("https://oss.sonatype.org/content/repositories/snapshots", "Sonatype OSS Snapshot Repository")
      updated = true
    }
    if (!model.containsMethodCall("jcenter")) {
      // Despite the name this doesn't add it if it's already there.
      updated = model.addRepositoryByMethodName("mavenCentral") || updated
    }
    return model.psiElement?.containingFile?.takeIf { updated }
  }

  internal open fun tryAddToBuildscriptDependencies(
    classpathDependency: String,
    classpathMatcher: DependencyMatcher
  ): TryAddResult {
    val buildModel = getProjectBuildModel()
    buildModel.buildscript().dependencies().takeIf { it.psiElement != null }
      ?.let {
        val changedFiles = addClasspathDependency(classpathDependency, listOf(), classpathMatcher)
        return TryAddResult(changedFiles, true)
      }
    return TryAddResult.failed()
  }

  internal fun tryAddToPluginsBlock(
    pluginId: String,
    version: String,
    matcher: PluginMatcher,
  ): TryAddResult {
    val buildModel = getProjectBuildModel()
    buildModel.plugins().takeIf { buildModel.pluginsPsiElement != null }
      ?.let { plugins ->
        val updatedFiles = mutableSetOf<PsiFile>()
        val existing = plugins.firstOrNull { matcher.match(it) }
        if (existing == null) {
          updatedFiles.addAll(
            addPlugin(pluginId, version, apply = false, buildModel, buildModel)
          )
        }
        return TryAddResult(updatedFiles, true)
      }
    return TryAddResult.failed()
  }

  private fun updatePlugin(pluginId: String,
                           version: String,
                           pluginModel: BasePluginsModel,
                           psiFile: PsiFile?): TryAddResult {
    val plugin = pluginModel.hasDifferentPluginVersion(pluginId, version)
    if (plugin != null) {
      plugin.version().resolve().setValue(version)
      val result = mutableSetOf<PsiFile>()
      result.addIfNotNull(psiFile)
      return TryAddResult(result,true)
    }
    return TryAddResult.failed()
  }

  internal fun tryAddToPluginsManagementBlock(
    pluginId: String,
    version: String,
    matcher: PluginMatcher
  ): TryAddResult {
    val buildModel = getProjectBuildModel()
    projectModel.projectSettingsModel?.pluginManagement()?.plugins()?.takeIf { it.psiElement != null }
      ?.let { plugins ->
        val existing = plugins.plugins().firstOrNull { matcher.match(it) }
        val updatedFiles = mutableSetOf<PsiFile>()
        if (existing == null) {
          updatedFiles.addAll(
            addPlugin(
              pluginId,
              version,
              apply = null,
              plugins,
              buildModel)
          )
        }

        return TryAddResult(updatedFiles, true)
      }
    return TryAddResult.failed()
  }

  /**
   * Returns whether the project already has the plugin (via pluginManagement block, plugins block,
   * or buildscript classpath dependency)
   */
  private fun hasPlugin(
    pluginMatcher: PluginMatcher,
    classpathMatcher: DependencyMatcher?
  ): Boolean {
    val pluginManagementPlugins =
      projectModel.projectSettingsModel?.pluginManagement()?.plugins()?.plugins()
    if (pluginManagementPlugins?.any { pluginMatcher.match(it) } == true) {
      return true
    }
    if (projectModel.projectBuildModel?.plugins()?.any { pluginMatcher.match(it) } == true) {
      return true
    }
    if (hasPluginInClasspath(classpathMatcher)) {
      return true
    }
    if (projectModel.projectBuildModel?.hasPlugin(pluginMatcher) == true) {
      return true
    }
    return false
  }

  private fun hasPluginInClasspath(classpathMatcher: DependencyMatcher?) =
    classpathMatcher != null && projectModel.projectBuildModel?.buildscript()?.dependencies()?.hasArtifact(classpathMatcher) == true

  override fun addClasspathDependency(dependency: String,
                                  excludes: List<ArtifactDependencySpec> ,
                                  matcher: DependencyMatcher): Set<PsiFile> {
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

  internal open fun addClasspathDependencyWithVersionVariable(dependency: String,
                                                     variableName: String,
                                                     excludes: List<ArtifactDependencySpec> ,
                                                     matcher: DependencyMatcher ): Set<PsiFile> {
    val updatedFiles = mutableSetOf<PsiFile>()
    val buildModel = projectModel.projectBuildModel ?: return updatedFiles
    val buildscriptDependencies = buildModel.buildscript().dependencies()

    if (!buildscriptDependencies.hasArtifact(matcher)) {
      val parsedDependency = Dependency.parse(dependency)
      val version = parsedDependency.version?.toIdentifier()
      buildModel.buildscript().ext().findProperty(variableName).setValue(version!!)
      buildscriptDependencies.addArtifact(
        CLASSPATH_CONFIGURATION_NAME,
        "${parsedDependency.group}:${parsedDependency.name}:\$$variableName",
        excludes)
      updatedFiles.addIfNotNull(buildModel.psiFile)
    }
    return updatedFiles
  }

  internal fun DependenciesModel.hasArtifact(matcher: DependencyMatcher): Boolean =
    artifacts().any { matcher.match(it) }

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

    declarePluginInPluginManagement(pluginId, version, apply, settingsPlugins).also { changedFiles.addAll(it) }

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
  override fun addPluginToModule(pluginId: String,
                             version: String,
                             buildModel: GradleBuildModel,
                             matcher: PluginMatcher): Set<PsiFile> {
    val changedFiles = mutableSetOf<PsiFile>()
    addPlugin(pluginId, buildModel, matcher)?.also { changedFiles.add(it) }
    return changedFiles
  }

  /**
   * Adds plugin without version - it adds plugin declaration directly to build script file.
   */
  override fun addPlugin(pluginId: String, buildModel: GradleBuildModel, matcher: PluginMatcher): PsiFile? =
    if (!buildModel.hasPlugin(matcher)) {
      buildModel.applyPlugin(pluginId)
      buildModel.psiFile
    }
    else
      null

  internal fun PluginsModel.hasPlugin(matcher: PluginMatcher): Boolean =
    plugins().any { matcher.match(it) }

  // Having plugins in settings pluginManagement.plugin block is a way to declare plugins with version, but not apply it immediately
  // Version catalog is a new way for declaring plugins with version, so we should avoid updating this section when there is a
  // catalog in project
  fun declarePluginInPluginManagement(pluginId: String,
                                      version: String,
                                      apply: Boolean?,
                                      settingsPlugins: PluginsBlockModel): Set<PsiFile> {
    val changedFiles = mutableSetOf<PsiFile>()
    settingsPlugins.applyPlugin(pluginId, version, apply)
    changedFiles.addIfNotNull(projectModel.projectSettingsModel?.psiFile)
    return changedFiles
  }

  // Adding plugin to settings file plugin{} block.
  // It does not init version catalog declaration yet so catalog references are illegible here.
  override fun applySettingsPlugin(pluginId: String,
                               version: String): Set<PsiFile> {
    val changedFiles = mutableSetOf<PsiFile>()
    val settingsFile = projectModel.projectSettingsModel
    if (settingsFile == null)
      log.warn("Settings file does not exist so cannot insert declaration into plugin{} block")

    settingsFile?.plugins()?.let {
      it.applyPlugin(pluginId, version)
      changedFiles.addIfNotNull(projectModel.projectSettingsModel?.psiFile)
    }

    return changedFiles
  }


  private fun BasePluginsModel.hasDifferentPluginVersion(pluginId: String, version: String): PluginModel? =
    plugins().firstOrNull { it.name().toString() == pluginId && it.version().toString() != version }

  private fun DependenciesModel.hasDifferentVersion(dep: Dependency): ArtifactDependencyModel? {
    return artifacts().firstOrNull {
      it.name().toString() == dep.name &&
      it.group().toString() == dep.group &&
      it.version().toString() != dep.version.toString()
    }
  }

  internal open fun tryAddClasspathDependencyWithVersionVariable(dependency: String,
                                                                 variableName: String,
                                                                 excludes: List<ArtifactDependencySpec> = listOf(),
                                                                 matcher: DependencyMatcher = ExactDependencyMatcher(
                                                                   CLASSPATH_CONFIGURATION_NAME,
                                                                   dependency)): TryAddResult {
    val buildModel = projectModel.projectBuildModel ?: return TryAddResult.failed()
    val buildscript = buildModel.buildscript()
    if (buildscript.psiElement != null) {
      val result = addClasspathDependencyWithVersionVariable(dependency, variableName, excludes, matcher)
      return TryAddResult(result, true)
    }
    return TryAddResult.failed()
  }

  internal fun updateDependencyVersion(
    dependency: String,
    dependenciesModel: (GradleBuildModel) -> DependenciesModel
  ): TryAddResult {
    val buildModel = getProjectBuildModel()
    val updatedFiles = mutableSetOf<PsiFile>()
    val dependencyObject = Dependency.parse(dependency)
    val artifact = dependenciesModel(buildModel).hasDifferentVersion(dependencyObject)
    val version = dependencyObject.version
    if (version != null && artifact != null) {
      artifact.version().resolve().setValue(version.toString())
      updatedFiles.addIfNotNull(buildModel.psiFile)
      return TryAddResult(updatedFiles,true)
    }
    return TryAddResult.failed()
  }


  internal fun shouldInsertModulePlugins(projectPlugins: GradleBuildModel, buildModel: GradleBuildModel) =
    projectPlugins.getPluginsPsiElement()?.containingFile != buildModel.psiFile || isSingleModuleProject()

  internal fun shouldInsertProjectPlugins() = !isSingleModuleProject()

  internal fun isSingleModuleProject() =
    projectModel.projectSettingsModel?.modulePaths()?.let { it.size < 2 } ?: true

  companion object {
    val log = Logger.getInstance(DependenciesHelper::class.java)
  }

}