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
import com.android.tools.idea.gradle.dependencies.PluginInsertionConfig.Companion.defaultInsertionConfig
import com.android.tools.idea.gradle.dependencies.PluginInsertionConfig.MatchedStrategy
import com.android.tools.idea.gradle.dependencies.PluginInsertionConfig.PluginInsertionStep.BUILDSCRIPT_CLASSPATH
import com.android.tools.idea.gradle.dependencies.PluginInsertionConfig.PluginInsertionStep.BUILDSCRIPT_CLASSPATH_WITH_VARIABLE
import com.android.tools.idea.gradle.dependencies.PluginInsertionConfig.PluginInsertionStep.PLUGIN_BLOCK
import com.android.tools.idea.gradle.dependencies.PluginInsertionConfig.PluginInsertionStep.PLUGIN_MANAGEMENT
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.dsl.api.PluginModel
import com.android.tools.idea.gradle.dsl.api.PluginsModel
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencySpec
import com.android.tools.idea.gradle.dsl.api.dependencies.DependenciesModel
import com.android.tools.idea.gradle.dsl.api.repositories.RepositoriesModel
import com.android.tools.idea.gradle.dsl.api.settings.PluginsBlockModel
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiFile
import org.gradle.api.plugins.JavaPlatformPlugin.CLASSPATH_CONFIGURATION_NAME
import org.jetbrains.kotlin.utils.addIfNotNull

@Suppress("AddDependencyUsage")
open class DependenciesInserter(private val projectModel: ProjectBuildModel) {

  /**
   * Adds plugin in a smart way. Consider version catalog vs non version catalog projects.
   * Looks for pluginsManagement (settings) section, plugins block in root project buildscript,
   * buildscript dependencies section.
   * In case none above found (new project) we fall back to adding project level plugin alias
   * (as plugin itself is declared in version catalog for new projects) to root project plugin block.
   *
   * The plugin is applied to each of the modules in [buildModels]
   */
  open fun addPluginOrClasspath(
    pluginId: String,
    classpathModule: String,
    version: String,
    buildModels: List<GradleBuildModel>,
    pluginMatcher: PluginMatcher = IdPluginMatcher(pluginId),
    classpathMatcher: DependencyMatcher = GroupNameDependencyMatcher(CLASSPATH_CONFIGURATION_NAME, "$classpathModule:$version"),
    config: PluginInsertionConfig = defaultInsertionConfig()
  ): Set<PsiFile> {
    val classpathInfo = PluginClasspathInfo("$classpathModule:$version", classpathMatcher)
    return findPlaceAndAddPluginOrClasspath(pluginId, version, buildModels, pluginMatcher, classpathInfo, config)
  }

  open fun findPlaceAndAddPlugin(pluginId: String,
                     version: String,
                     buildModels: List<GradleBuildModel>,
                     pluginMatcher: PluginMatcher = IdPluginMatcher(pluginId)): Set<PsiFile> =
    findPlaceAndAddPluginOrClasspath(pluginId, version, buildModels, pluginMatcher, null, defaultInsertionConfig())

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
        getLazyCall(tryStep, config, pluginId, version, pluginMatcher, classpathInfo)
      }.firstOrNull { it.value.succeed }?.value

      // in case there is nothing - we force adding plugin to root project plugins block
      result?.changedFiles?.let { updatedFiles.addAll(it) } ?: updatedFiles.addAll(
        addPlugin(pluginId, version, apply = false, projectBuildModel, projectBuildModel)
      )
    }
    buildModels.forEach {
      updatedFiles.addAll(addPluginToModule(pluginId, version, it))
    }

    return updatedFiles
  }

  private fun getLazyCall(step: PluginInsertionConfig.PluginInsertionStep,
                          config: PluginInsertionConfig,
                          pluginId: String,
                          version: String,
                          pluginMatcher: PluginMatcher = IdPluginMatcher(pluginId),
                          classpathInfo: PluginClasspathInfo?): Lazy<TryAddResult> {
    val projectBuildModel = projectModel.projectBuildModel ?: error("Build model for root project not found")
    val matchedStrategy = config.whenFoundSame
    return when (step) {
      BUILDSCRIPT_CLASSPATH ->
        lazy {
          if (classpathInfo != null)
            tryAddToBuildscriptDependencies(classpathInfo.dependency, projectBuildModel, classpathInfo.matcher, matchedStrategy).also {
              if (config.addRepoForSnapshots == true) it.appendWhenSuccess {
                addRepositoryFor(version, projectBuildModel.buildscript().repositories())
              }
            }
          else
            TryAddResult.failed()
        }

      PLUGIN_MANAGEMENT -> lazy {
        tryAddToPluginsManagementBlock(pluginId, version, projectBuildModel, pluginMatcher, matchedStrategy).also {
          if (config.addRepoForSnapshots == true) it.appendWhenSuccess {
            projectModel.projectSettingsModel?.pluginManagement()?.repositories()?.let{ addRepositoryFor(version, it) }
          }
        }
      }

      PLUGIN_BLOCK -> lazy {
        tryAddToPluginsBlock(pluginId, version, projectBuildModel, pluginMatcher, matchedStrategy).also {
          if (config.addRepoForSnapshots == true) it.appendWhenSuccess {
            projectModel.projectSettingsModel?.pluginManagement()?.repositories()?.let{ addRepositoryFor(version, it) }
          }
        }
      }
      BUILDSCRIPT_CLASSPATH_WITH_VARIABLE -> lazy {
        if (config.variableName != null && classpathInfo != null) {
          tryAddClasspathDependencyWithVersionVariable(
            classpathInfo.dependency,
            config.variableName,
            listOf(),
            classpathInfo.matcher,
            matchedStrategy
          ).also {
            if (config.addRepoForSnapshots == true) it.appendWhenSuccess {
              addRepositoryFor(version, projectBuildModel.buildscript().repositories())
            }
          }
        }
        else
          TryAddResult.failed()
      }
    }
  }

  fun addRepositoryFor(version: String, model: RepositoriesModel): PsiFile? {
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

  // Files may be already in proper state, so we need additional flag `succeed` to make sure
  // all changes already there
  data class TryAddResult(val changedFiles: Set<PsiFile>, val succeed: Boolean) {
    companion object {
      fun failed() = TryAddResult(setOf(), false)
    }

    fun appendWhenSuccess(f: () -> PsiFile?): TryAddResult {
      if (succeed) {
        val set = mutableSetOf<PsiFile>()
        set.addAll(changedFiles)
        set.addIfNotNull(f())
        return TryAddResult(set, true)
      }
      return this
    }
  }

  internal open fun tryAddToBuildscriptDependencies(
    classpathDependency: String,
    buildModel: GradleBuildModel,
    classpathMatcher: DependencyMatcher,
    matchedStrategy: MatchedStrategy,
  ): TryAddResult {
    buildModel.buildscript().dependencies().takeIf { it.psiElement != null }
      ?.let {
        val changedFiles = addClasspathDependency(classpathDependency, listOf(), classpathMatcher, matchedStrategy)
        return TryAddResult(changedFiles, true)
      }
    return TryAddResult.failed()
  }

  private fun tryAddToPluginsBlock(
    pluginId: String,
    version: String,
    buildModel: GradleBuildModel,
    matcher: PluginMatcher,
    matchedStrategy: MatchedStrategy
  ): TryAddResult {
    buildModel.plugins().takeIf { buildModel.pluginsPsiElement != null }
      ?.let { plugins ->
        val updatedFiles = mutableSetOf<PsiFile>()
        val existing = plugins.firstOrNull { matcher.match(it) }
        if (existing == null) {
          updatedFiles.addAll(
            addPlugin(pluginId, version, apply = false, buildModel, buildModel)
          )
        } else if (matchedStrategy == MatchedStrategy.UPDATE_VERSION){
          val plugin = buildModel.hasDifferentPluginVersion(pluginId, version)
          if (plugin != null) {
            plugin.version().resolve().setValue(version)
            updatedFiles.addIfNotNull(buildModel.psiFile)
          }
        }
        return TryAddResult(updatedFiles, true)
      }
    return TryAddResult.failed()
  }

  private fun tryAddToPluginsManagementBlock(
    pluginId: String,
    version: String,
    buildModel: GradleBuildModel,
    matcher: PluginMatcher,
    matchedStrategy: MatchedStrategy,
  ): TryAddResult {
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
        } else if (matchedStrategy == MatchedStrategy.UPDATE_VERSION){
            val plugin = plugins.hasDifferentVersion(pluginId, version)
            if (plugin != null) {
              plugin.version().resolve().setValue(version)
              updatedFiles.addIfNotNull(buildModel.psiFile)
            }
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
    return false
  }

  private fun hasPluginInClasspath(classpathMatcher: DependencyMatcher?) =
    classpathMatcher != null && projectModel.projectBuildModel?.buildscript()?.dependencies()?.hasArtifact(classpathMatcher) == true

  @JvmOverloads
  open fun addClasspathDependency(dependency: String,
                                  excludes: List<ArtifactDependencySpec> = listOf(),
                                  matcher: DependencyMatcher = ExactDependencyMatcher(CLASSPATH_CONFIGURATION_NAME,
                                                                                      dependency),
                                  matchedStrategy: MatchedStrategy = MatchedStrategy.DO_NOTHING): Set<PsiFile> {
    val updatedFiles = mutableSetOf<PsiFile>()
    val buildModel = projectModel.projectBuildModel ?: return updatedFiles
    val buildscriptDependencies = buildModel.buildscript().dependencies()
    if (!buildscriptDependencies.hasArtifact(matcher)) {
      buildscriptDependencies.addArtifact(CLASSPATH_CONFIGURATION_NAME, dependency, excludes).also {
        updatedFiles.addIfNotNull(buildModel.psiFile)
      }
    }
    else if (matchedStrategy == MatchedStrategy.UPDATE_VERSION){
      buildscriptDependencies.updateDependencyVersion(dependency, updatedFiles, buildModel)
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
  open fun addPluginToModule(pluginId: String,
                             version: String,
                             buildModel: GradleBuildModel,
                             matcher: PluginMatcher = IdPluginMatcher(pluginId)): Set<PsiFile> {
    val changedFiles = mutableSetOf<PsiFile>()
    addPlugin(pluginId, buildModel, matcher)?.also { changedFiles.add(it) }
    return changedFiles
  }

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
  open fun applySettingsPlugin(pluginId: String,
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


  open fun addDependency(configuration: String,
                         dependency: String,
                         excludes: List<ArtifactDependencySpec>,
                         parsedModel: GradleBuildModel,
                         matcher: DependencyMatcher,
                         sourceSetName: String? = null): Set<PsiFile> {
    val updateFiles = mutableSetOf<PsiFile>()
    val dependenciesModel = getDependenciesModel(sourceSetName, parsedModel)
    if (dependenciesModel != null && !dependenciesModel.hasArtifact(matcher)) {
      dependenciesModel.addArtifact(configuration, dependency, excludes).also {
        updateFiles.addIfNotNull(dependenciesModel.psiElement?.containingFile)
      }
    }
    return updateFiles
  }

  internal fun findDependency(dependency: Dependency,
                              buildModel: GradleBuildModel):ArtifactDependencyModel?{
    val dependenciesModel = buildModel.dependencies()
    val richVersion = dependency.version
    var richVersionIdentifier: String? = null
    if (richVersion != null) richVersionIdentifier = richVersion.toIdentifier()

    val artifacts: List<ArtifactDependencyModel> = ArrayList(dependenciesModel.artifacts())
    for (artifact in artifacts) {
      if (dependency.group == artifact.group().toString()
          && dependency.name == artifact.name().forceString()
          && richVersionIdentifier != artifact.version().toString()) {
        return artifact
      }
    }
    return null
  }

  open fun updateDependencyVersion(dependency: Dependency,
                                   buildModel: GradleBuildModel) {
    check(dependency.version != null) { "Version must not be null for updateDependencyVersion" }
    findDependency(dependency, buildModel)?.let { artifact ->
      buildModel.dependencies().apply {
        remove(artifact)
        addArtifact(artifact.configurationName(), dependency.toString())
      }
    }
  }

  private fun getDependenciesModel(sourceSetName: String?, parsedModel: GradleBuildModel): DependenciesModel? {
    return if (sourceSetName != null) {
      parsedModel.kotlin().sourceSets().find { it.name() == sourceSetName }?.dependencies()
    } else {
      parsedModel.dependencies()
    }
  }

  internal open fun tryAddClasspathDependencyWithVersionVariable(dependency: String,
                                                        variableName: String,
                                                        excludes: List<ArtifactDependencySpec> = listOf(),
                                                        matcher: DependencyMatcher = ExactDependencyMatcher(CLASSPATH_CONFIGURATION_NAME,
                                                                                                            dependency),
                                                        matchedStrategy: MatchedStrategy = MatchedStrategy.DO_NOTHING): TryAddResult {
    val buildModel = projectModel.projectBuildModel ?: return TryAddResult.failed()
    val buildscript = buildModel.buildscript()
    if (buildscript.psiElement != null) {
      val result = addClasspathDependencyWithVersionVariable(dependency, variableName, excludes, matcher, matchedStrategy)
      return TryAddResult(result, true)
    }
    return TryAddResult.failed()
  }

  @JvmOverloads
  open fun addClasspathDependencyWithVersionVariable(dependency: String,
                                                     variableName: String,
                                                     excludes: List<ArtifactDependencySpec> = listOf(),
                                                     matcher: DependencyMatcher = ExactDependencyMatcher(CLASSPATH_CONFIGURATION_NAME,
                                                                                                         dependency),
                                                     matchedStrategy: MatchedStrategy = MatchedStrategy.DO_NOTHING): Set<PsiFile> {
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
    } else if (matchedStrategy == MatchedStrategy.UPDATE_VERSION) {
      buildscriptDependencies.updateDependencyVersion(dependency, updatedFiles, buildModel)
    }
    return updatedFiles
  }

  internal fun DependenciesModel.updateDependencyVersion(
    dependency: String,
    updatedFiles: MutableSet<PsiFile>,
    buildModel: GradleBuildModel
  ): Boolean {
    val dep = Dependency.parse(dependency)
    val artifact = hasDifferentVersion(dep)
    if (dep.version != null && artifact != null) {
      artifact.version().resolve().setValue(dep.version.toString())
      updatedFiles.addIfNotNull(buildModel.psiFile)
      return true
    }
    return false
  }


  internal fun shouldInsertModulePlugins(projectPlugins: GradleBuildModel, buildModel: GradleBuildModel) =
    projectPlugins.getPluginsPsiElement()?.containingFile != buildModel.psiFile || isSingleModuleProject()

  internal fun shouldInsertProjectPlugins() = !isSingleModuleProject()

  internal fun isSingleModuleProject() =
    projectModel.projectSettingsModel?.modulePaths()?.let { it.size < 2 } ?: true


  /**
   * Adds plugin without version - it adds plugin declaration directly to build script file.
   */
  open fun addPlugin(pluginId: String, buildModel: GradleBuildModel, matcher: PluginMatcher = IdPluginMatcher(pluginId)): PsiFile? =
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

  internal fun GradleBuildModel.hasDifferentPluginVersion(pluginId: String, version: String): PluginModel? =
    plugins().firstOrNull { it.name().toString() == pluginId && it.version().toString() != version }

  internal fun PluginsBlockModel.hasDifferentVersion(pluginId: String, version: String): PluginModel? =
    plugins().firstOrNull { it.name().toString() == pluginId && it.version().toString() != version }

  internal fun DependenciesModel.hasDifferentVersion(dep: Dependency): ArtifactDependencyModel? {
    return artifacts().firstOrNull {
      it.name().toString() == dep.name &&
      it.group().toString() == dep.group &&
      it.version().toString() != dep.version.toString()
    }
  }

  /**
   * This is short version of addDependency function.
   * Assuming there is no excludes and algorithm will search exact dependency declaration in catalog if exists.
   */
  fun addDependency(configuration: String, dependency: String, parsedModel: GradleBuildModel) =
    addDependency(configuration, dependency, listOf(), parsedModel, ExactDependencyMatcher(configuration, dependency))

  companion object {
    val log = Logger.getInstance(DependenciesHelper::class.java)
  }

}