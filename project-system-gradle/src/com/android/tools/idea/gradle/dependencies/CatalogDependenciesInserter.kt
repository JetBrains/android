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

import android.annotation.SuppressLint
import com.android.ide.common.gradle.Dependency
import com.android.ide.common.gradle.RichVersion
import com.android.ide.common.repository.keysMatch
import com.android.ide.common.repository.pickLibraryVariableName
import com.android.ide.common.repository.pickPluginVariableName
import com.android.ide.common.repository.pickPluginVersionVariableName
import com.android.ide.common.repository.pickVersionVariableName
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.dsl.api.GradleVersionCatalogModel
import com.android.tools.idea.gradle.dsl.api.GradleVersionCatalogsModel
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencySpec
import com.android.tools.idea.gradle.dsl.api.dependencies.VersionDeclarationModel
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel
import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo
import com.android.tools.idea.gradle.dsl.api.settings.PluginsBlockModel
import com.android.tools.idea.gradle.dsl.api.settings.VersionCatalogModel
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiFile
import org.gradle.api.plugins.JavaPlatformPlugin
import org.jetbrains.kotlin.utils.addIfNotNull

@SuppressLint("AddDependencyUsage")
class CatalogDependenciesInserter(private val projectModel: ProjectBuildModel) : DependenciesInserter(projectModel) {
  @JvmOverloads
  override fun addClasspathDependency(dependency: String,
                                      excludes: List<ArtifactDependencySpec>,
                                      matcher: DependencyMatcher): Set<PsiFile> {
    val buildModel = projectModel.projectBuildModel ?: return setOf()
    return getOrAddDependencyToCatalog(dependency, matcher) { alias, updatedFiles ->
      val buildscriptDependencies = buildModel.buildscript().dependencies()
      val reference = ReferenceTo(getCatalogModel().libraries().findProperty(alias), buildscriptDependencies)
      if (!buildscriptDependencies.hasArtifact(matcher)) {
        buildscriptDependencies.addArtifact(JavaPlatformPlugin.CLASSPATH_CONFIGURATION_NAME, reference, excludes).also {
          updatedFiles.addIfNotNull(buildModel.psiFile)
        }
      }
    }
  }

  @JvmOverloads
  override fun addPlatformDependency(
    configuration: String,
    dependency: String,
    enforced: Boolean,
    parsedModel: GradleBuildModel,
    matcher: DependencyMatcher): Set<PsiFile> {
    val buildscriptDependencies = parsedModel.dependencies()
    return getOrAddDependencyToCatalog(dependency, matcher) { alias, updatedFiles ->
      val reference = ReferenceTo(getCatalogModel().libraries().findProperty(alias), buildscriptDependencies)
      if (!buildscriptDependencies.hasArtifact(matcher)) {
        buildscriptDependencies.addPlatformArtifact(configuration, reference, enforced).also {
          updatedFiles.addIfNotNull(parsedModel.psiFile)
        }
      }
    }
  }

  @JvmOverloads
  override fun addClasspathDependencyWithVersionVariable(dependency: String,
                                                         variableName: String,
                                                         excludes: List<ArtifactDependencySpec>,
                                                         matcher: DependencyMatcher): Set<PsiFile> {
    val buildModel = projectModel.projectBuildModel ?: return setOf()
    val buildscriptDependencies = buildModel.buildscript().dependencies()
    return getOrAddDependencyToCatalog(dependency, matcher) { alias, updatedFiles ->
      val reference = ReferenceTo(getCatalogModel().libraries().findProperty(alias), buildscriptDependencies)
      if (!buildscriptDependencies.hasArtifact(matcher)) {
        buildscriptDependencies.addArtifact(JavaPlatformPlugin.CLASSPATH_CONFIGURATION_NAME, reference, excludes).also {
          updatedFiles.addIfNotNull(buildModel.psiFile)
        }
      }
    }
  }

  // We ignore setting plugin block as we cannot add reference to catalog here
  override fun addPlugin(pluginId: String,
                         version: String,
                         apply: Boolean?,
                         settingsPlugins: PluginsBlockModel,
                         buildModel: GradleBuildModel,
                         matcher: PluginMatcher): Set<PsiFile> {
    // buildModel may be root project of multimodule project or module project itself
    val moduleInsertion = isSingleModuleProject() || buildModel.psiFile != projectModel.projectBuildModel?.psiFile
    return getOrAddPluginToCatalog(pluginId, version, matcher) { alias, changedFiles ->
      val reference = ReferenceTo(getCatalogModel().plugins().findProperty(alias))
      if (moduleInsertion && !buildModel.hasPlugin(matcher)) {
        buildModel.applyPlugin(reference, null)
        changedFiles.addIfNotNull(buildModel.psiFile)
      }
    }
  }

  override fun addPlugin(pluginId: String,
                         version: String,
                         apply: Boolean?,
                         projectPlugins: GradleBuildModel,
                         buildModel: GradleBuildModel,
                         matcher: PluginMatcher): Set<PsiFile> {
    // Inserting project level plugins in case
    // - projectPlugins in project file
    // - it's not single module project
    val insertProjectPlugins = shouldInsertProjectPlugins() && !projectPlugins.hasPlugin(matcher)
    // Insert plugins for module in case
    // - buildModel is a separate module
    // - buildModel is a single module project
    val moduleInsertion = shouldInsertModulePlugins(projectPlugins, buildModel)
    return getOrAddPluginToCatalog(pluginId, version, matcher) { alias, changedFiles ->
      val reference = ReferenceTo(getCatalogModel().plugins().findProperty(alias))
      if (insertProjectPlugins) {
        projectPlugins.applyPlugin(reference, apply)
        changedFiles.addIfNotNull(projectPlugins.psiElement?.containingFile)
      }
      if (!buildModel.hasPlugin(matcher) && moduleInsertion) {
        buildModel.applyPlugin(reference, null)
        changedFiles.addIfNotNull(buildModel.psiFile)
      }
    }
  }

  /**
   * Adds plugin to module but insert/check version catalog first
   * Project build file/settings stay intact
   *
   * If the plugin has already been applied to the module, but it's not already in the version
   * catalog, the version catalog is not updated (otherwise the plugin would be added to the
   * version catalog, but the catalog reference would not actually be used).
   */
  override fun addPluginToModule(pluginId: String,
                                 version: String,
                                 buildModel: GradleBuildModel,
                                 matcher: PluginMatcher): Set<PsiFile> {
    if (buildModel.hasPlugin(matcher) &&
        findCatalogPluginDeclaration(getCatalogModel(), matcher) == null) {
      return emptySet()
    }
    return getOrAddPluginToCatalog(pluginId, version, matcher) { alias, changedFiles ->
      val reference = ReferenceTo(getCatalogModel().plugins().findProperty(alias))
      if (!buildModel.hasPlugin(matcher)) {
        buildModel.applyPlugin(reference, null)
        changedFiles.addIfNotNull(buildModel.psiElement?.containingFile)
      }
    }
  }

  override fun addDependency(configuration: String,
                             dependency: String,
                             excludes: List<ArtifactDependencySpec>,
                             parsedModel: GradleBuildModel,
                             matcher: DependencyMatcher): Set<PsiFile> =
    getOrAddDependencyToCatalog(dependency, matcher) { alias, changedFiles ->
      val dependenciesModel = parsedModel.dependencies()
      val reference = ReferenceTo(getCatalogModel().libraries().findProperty(alias), dependenciesModel)
      if (!dependenciesModel.hasArtifact(matcher)) {
        dependenciesModel.addArtifact(configuration, reference, excludes).also {
          changedFiles.addIfNotNull(parsedModel.psiFile)
        }
      }
    }

  private fun getOrAddPluginToCatalog(plugin: String, version: String, matcher: PluginMatcher): Pair<Alias?, PsiFile?> {
    val catalogModel = getCatalogModel()
    val result = findCatalogPluginDeclaration(catalogModel, matcher)?.let { Pair(it, null) } ?: Pair(
      addCatalogPlugin(catalogModel, plugin, version),
      catalogModel.psiFile
    )
    if (result.first == null) {
      log.warn("Cannot add catalog reference to build as we cannot find/add catalog declaration")
    }
    return result
  }

  private fun getOrAddPluginToCatalog(pluginId: String,
                                      version: String,
                                      matcher: PluginMatcher,
                                      handler: (Alias, MutableSet<PsiFile>) -> Unit): Set<PsiFile> {
    val (alias, changedFile) = getOrAddPluginToCatalog(pluginId, version, matcher)
    val changedFiles = mutableSetOf<PsiFile>()
    changedFiles.addIfNotNull(changedFile)
    alias ?: return changedFiles
    handler.invoke(alias, changedFiles)
    return changedFiles
  }

  private fun findCatalogPluginDeclaration(catalogModel: GradleVersionCatalogModel,
                                           matcher: PluginMatcher): Alias? {
    val declarations = catalogModel.pluginDeclarations().getAll()
    return declarations.filter { matcher.match(it.value) }.map { it.key }.firstOrNull()
  }

  private fun getOrAddDependencyToCatalog(dependency: String,
                                          matcher: DependencyMatcher,
                                          handler: (Alias, MutableSet<PsiFile>) -> Unit): Set<PsiFile> {
    val updatedFiles = mutableSetOf<PsiFile>()
    val (alias, updatedFile) = getOrAddDependencyToCatalog(dependency, matcher)
    updatedFiles.addIfNotNull(updatedFile)
    alias ?: return updatedFiles
    handler.invoke(alias, updatedFiles)
    return updatedFiles
  }

  private fun getOrAddDependencyToCatalog(dependency: String, matcher: DependencyMatcher): Pair<Alias?, PsiFile?> {
    val catalogModel = getCatalogModel()
    val result = findCatalogDeclaration(catalogModel, matcher)?.let { Pair(it, null) } ?: Pair(addCatalogLibrary(catalogModel, dependency),
                                                                                               catalogModel.psiFile)
    if (result.first == null) {
      log.warn("Cannot add catalog reference to build as we cannot find/add catalog declaration")
    }
    return result
  }


  private fun getCatalogModel(): GradleVersionCatalogModel {
    val catalogModel = DependenciesHelper.getDefaultCatalogModel(projectModel)
    // check invariant that at this point catalog must be available as algorithm chose to add dependency to catalog
    check(catalogModel != null) { "Catalog ${DependenciesHelper.getDefaultCatalogName(projectModel)} must be available to add dependency" }
    return catalogModel
  }

  private fun findCatalogDeclaration(catalogModel: GradleVersionCatalogModel,
                                     matcher: DependencyMatcher): Alias? {
    val declarations = catalogModel.libraryDeclarations().getAll()
    return declarations.filter { matcher.match(it.value) }.map { it.key }.firstOrNull()
  }

  companion object {
    val log = Logger.getInstance(DependenciesHelper::class.java)

    @JvmStatic fun addCatalogLibrary(catalogModel: GradleVersionCatalogModel,
                                     dependency: Dependency): Alias? {
      val libraries = catalogModel.libraryDeclarations()
      val names = libraries.getAllAliases()

      val group = dependency.group
      if (group == null) {
        log.warn("Cannot add catalog library (missing group): $dependency")
        return null
      }

      val alias: Alias = pickLibraryVariableName(dependency, false, names)

      if (dependency.version != null) {
        val version = addCatalogVersionForLibrary(catalogModel, dependency)
        if (version == null) {
          log.warn("Cannot add catalog library (wrong version format): $dependency") // this depends on correct version syntax
          return null
        }

        libraries.addDeclaration(alias, dependency.name, group, ReferenceTo(version, libraries))
      } else {
        libraries.addDeclaration(alias, dependency.name, group)
      }
      return alias
    }

    @JvmStatic
    fun addCatalogPlugin(catalogModel: GradleVersionCatalogModel,
                         pluginId: String,
                         version: String): Alias? {
      val plugins = catalogModel.pluginDeclarations()
      val names = plugins.getAllAliases()

      val defaultName = KotlinPlugin.values().find { it.id == pluginId }?.defaultPluginName?.takeIf { it !in names }

      val alias: Alias = pickPluginVariableName(defaultName ?: pluginId, names)

      val versionModel = getOrAddCatalogVersionForPlugin(catalogModel, pluginId, version)
      if (versionModel == null) {
        log.warn("Cannot add catalog library (wrong version format): $version") // this depends on correct version syntax
        return null
      }
      plugins.addDeclaration(alias, pluginId, ReferenceTo(versionModel, plugins))
      return alias
    }

    @JvmStatic fun addCatalogLibrary(catalogModel: GradleVersionCatalogModel,
                                     coordinate: String): Alias? {

      val dependency = Dependency.parse(coordinate)
      if (dependency.group == null) {
        log.warn("Cannot add catalog library. Wrong format: $coordinate")
        return null
      }
      return addCatalogLibrary(catalogModel, dependency)
    }

    @JvmStatic
    fun updateCatalogLibrary(catalogsModel: GradleVersionCatalogsModel,
                             dependency: ArtifactDependencyModel,
                             version: RichVersion): Boolean {
      val catalogModel: GradleVersionCatalogModel = dependency.findVersionCatalogModel(catalogsModel) ?: return false
      val alias = dependency.getAlias() ?: return false
      val libraries = catalogModel.libraryDeclarations()

      //construct new dependency with version
      val dependencySpec = Dependency.parse(dependency.spec.compactNotation()).copy(version = version)

      val result = libraries.getAll().entries.find { keysMatch(it.key, alias) } ?: return false
      val version = addCatalogVersionForLibrary(catalogModel, dependencySpec) ?: return false.also {
        log.warn("Cannot update catalog library (wrong version format): $dependency")
      }

      result.value.updateVersion(version)
      return true
    }

    private fun ArtifactDependencyModel.findVersionCatalogModel(catalogsModel: GradleVersionCatalogsModel): GradleVersionCatalogModel? {
      val referenceString: String = completeModel().unresolvedModel.getValue(GradlePropertyModel.STRING_TYPE) ?: return null
      val catalogName = referenceString.substringBefore(".")
      return catalogsModel.getVersionCatalogModel(catalogName)
    }

    private fun ArtifactDependencyModel.getAlias(): String? {
      val referenceString: String = completeModel().unresolvedModel.getValue(GradlePropertyModel.STRING_TYPE) ?: return null
      if (!referenceString.contains(".")) return null
      return referenceString.substringAfter(".")
    }

    private fun getOrAddCatalogVersionForPlugin(catalogModel: GradleVersionCatalogModel, pluginId: String, version: String): VersionDeclarationModel? {
      val agpPluginIds = AgpPlugin.values().map { it.id }.toSet()
      return when {
        agpPluginIds.contains(pluginId) -> getAgpVersion(catalogModel, version)
        KotlinPlugin.values().find { it.id == pluginId } != null -> getKotlinVersion(catalogModel, version)
        else -> addVersionDeclaration(catalogModel, pluginId, version)
      }
    }

    private fun getAgpVersion(catalogModel: GradleVersionCatalogModel, version: String): VersionDeclarationModel? =
      getPredefinedVersion(catalogModel, version, AgpPlugin.values().map { it.id }.toSet(), AgpPlugin.defaultVersionName)

    private fun getKotlinVersion(catalogModel: GradleVersionCatalogModel, version: String): VersionDeclarationModel? =
      getPredefinedVersion(catalogModel, version, KotlinPlugin.values().map { it.id }.toSet(),
                           KotlinPlugin.defaultVersionName)

    private fun getPredefinedVersion(catalogModel: GradleVersionCatalogModel,
                                     version: String,
                                     ids: Set<String>,
                                     defaultName: String): VersionDeclarationModel? {
      return catalogModel.findVersion(ids) ?: addVersionDeclaration(catalogModel, defaultName, version)
    }

    private fun addVersionDeclaration(
      catalogModel: GradleVersionCatalogModel,
      pluginId: String,
      version: String
    ): VersionDeclarationModel? {
      val versions = catalogModel.versionDeclarations()
      val names = versions.getAllAliases()
      val alias: Alias = pickPluginVersionVariableName(pluginId, names)
      return versions.addDeclaration(alias, version)
    }

    private fun GradleVersionCatalogModel.findVersion(ids: Set<String>): VersionDeclarationModel? {
      val plugins = pluginDeclarations().getAll().values
      for (plugin in plugins) {
        if (
          plugin.id().valueAsString() in ids &&
          plugin.version().completeModel()?.rawElement?.parent?.name == "versions"
        ) {
          val alias = plugin.version().completeModel()?.name
          versionDeclarations().getAll()[alias]?.let { return it }
        }
      }
      return null
    }

    private fun addCatalogVersionForLibrary(catalogModel: GradleVersionCatalogModel,
                                            dependency: Dependency): VersionDeclarationModel? {
      val versions = catalogModel.versionDeclarations()
      val names = versions.getAllAliases()
      val alias: Alias = pickVersionVariableName(dependency, names)
      // TODO(b/279886738): we could actually generate and use a VersionDeclarationSpec here from the dependency version
      return when (val identifier = dependency.version?.toIdentifier()) {
        null -> null
        else -> versions.addDeclaration(alias, identifier)
      }
    }

    enum class AgpPlugin(val id: String) {
      APPLICATION("com.android.application"),
      LIBRARY("com.android.library"),
      TEST("com.android.test"),
      ASSET_PACK("com.android.asset-pack"),
      ASSET_PACK_BUNDLE("com.android.asset-pack-bundle"),
      DYNAMIC_FEATURE("com.android.dynamic-feature"),
      FUSED_LIBRARY("com.android.fused-library"),
      INTERNAL_SETTINGS("com.android.internal.settings"),
      SETTINGS("com.android.settings"),
      LINT("com.android.lint")
      ;

      companion object {
        const val defaultVersionName = "agp"
      }
    }

    enum class KotlinPlugin(val id: String, val defaultPluginName: String) {
      KOTLIN_ANDROID("org.jetbrains.kotlin.android", "kotlin-android"),
      KOTLIN_COMPOSE("org.jetbrains.kotlin.plugin.compose", "kotlin-compose")
      ;

      companion object {
        const val defaultVersionName = "kotlin"
      }
    }
  }

}