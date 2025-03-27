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

import com.android.ide.common.repository.pickPluginVariableName
import com.android.ide.common.repository.pickPluginVersionVariableName
import com.android.tools.idea.gradle.dependencies.CatalogDependenciesInserter.Companion.getCatalogModel
import com.android.tools.idea.gradle.dependencies.PluginsInserter.TryAddResult
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.dsl.api.GradleVersionCatalogModel
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencySpec
import com.android.tools.idea.gradle.dsl.api.dependencies.VersionDeclarationModel
import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo
import com.android.tools.idea.gradle.dsl.api.settings.PluginsBlockModel
import com.intellij.psi.PsiFile
import org.gradle.api.plugins.JavaPlatformPlugin
import org.jetbrains.kotlin.utils.addIfNotNull

class CatalogPluginsInserter(private val projectModel: ProjectBuildModel) : CommonPluginsInserter(projectModel) {

  override fun addClasspathDependency(dependency: String,
                                      excludes: List<ArtifactDependencySpec>,
                                      matcher: DependencyMatcher): Set<PsiFile> {
    val buildModel = projectModel.projectBuildModel ?: return setOf()
    return CatalogDependenciesInserter.getOrAddDependencyToCatalog(projectModel, dependency, matcher) { alias, updatedFiles ->
      val buildscriptDependencies = buildModel.buildscript().dependencies()
      val reference = ReferenceTo(getCatalogModel().libraries().findProperty(alias), buildscriptDependencies)
      if (!buildscriptDependencies.hasArtifact(matcher)) {
        buildscriptDependencies.addArtifact(JavaPlatformPlugin.CLASSPATH_CONFIGURATION_NAME, reference, excludes).also {
          updatedFiles.addIfNotNull(buildModel.psiFile)
        }
      }
    }
  }

  // Avoid insertion to buildscript when using catalog
  override fun tryAddToBuildscriptDependencies(
    classpathDependency: String,
    classpathMatcher: DependencyMatcher
  ): PluginsInserter.TryAddResult = TryAddResult.failed()

  // Avoid insertion to buildscript when using catalog
  override fun tryAddClasspathDependencyWithVersionVariable(
    dependency: String,
    variableName: String,
    excludes: List<ArtifactDependencySpec>,
    matcher: DependencyMatcher
  ): TryAddResult = TryAddResult.failed()

  override fun addClasspathDependencyWithVersionVariable(dependency: String,
                                                         variableName: String,
                                                         excludes: List<ArtifactDependencySpec>,
                                                         matcher: DependencyMatcher): Set<PsiFile> {
    val buildModel = projectModel.projectBuildModel ?: return setOf()
    val buildscriptDependencies = buildModel.buildscript().dependencies()
    return CatalogDependenciesInserter.getOrAddDependencyToCatalog(projectModel, dependency, matcher) { alias, updatedFiles ->
      val reference = ReferenceTo(getCatalogModel().libraries().findProperty(alias), buildscriptDependencies)
      if (!buildscriptDependencies.hasArtifact(matcher)) {
        buildscriptDependencies.addArtifact(JavaPlatformPlugin.CLASSPATH_CONFIGURATION_NAME, reference, excludes).also {
          updatedFiles.addIfNotNull(buildModel.psiFile)
        }
      }
    }
  }

  private fun getCatalogModel(): GradleVersionCatalogModel = getCatalogModel(projectModel)

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

  companion object {

    private fun findCatalogPluginDeclaration(catalogModel: GradleVersionCatalogModel,
                                             matcher: PluginMatcher): Alias? {
      val declarations = catalogModel.pluginDeclarations().getAll()
      return declarations.filter { matcher.match(it.value) }.map { it.key }.firstOrNull()
    }

    private fun getOrAddCatalogVersionForPlugin(catalogModel: GradleVersionCatalogModel,
                                                pluginId: String,
                                                version: String): VersionDeclarationModel? {
      val agpPluginIds = AgpPlugin.values().map { it.id }.toSet()
      return when {
        agpPluginIds.contains(pluginId) -> getAgpVersion(catalogModel, version)
        KotlinPlugin.values().find { it.id == pluginId } != null -> getKotlinVersion(catalogModel, version)
        else -> addVersionDeclaration(catalogModel, pluginId, version)
      }
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
  LINT("com.android.lint"),
  KOTLIN_MULTIPLATFORM_LIBRARY("com.android.kotlin.multiplatform.library"),
  ;

  companion object {
    const val defaultVersionName = "agp"
  }
}

enum class KotlinPlugin(val id: String, val defaultPluginName: String) {
  KOTLIN_ANDROID("org.jetbrains.kotlin.android", "kotlin-android"),
  KOTLIN_COMPOSE("org.jetbrains.kotlin.plugin.compose", "kotlin-compose"),
  KOTLIN_MULTIPLATFORM("org.jetbrains.kotlin.multiplatform", "kotlin-multiplatform"),
  ;

  companion object {
    const val defaultVersionName = "kotlin"
  }
}