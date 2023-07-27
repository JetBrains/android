/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.ide.common.gradle.RichVersion
import com.android.ide.common.repository.pickPluginVariableName
import com.android.ide.common.repository.pickPluginVersionVariableName
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.dsl.api.GradleVersionCatalogModel
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.dsl.api.dependencies.VersionDeclarationModel
import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo
import com.android.tools.idea.gradle.dsl.api.settings.VersionCatalogModel.DEFAULT_CATALOG_NAME
import com.intellij.openapi.diagnostic.Logger

class PluginsHelper(private val projectModel: ProjectBuildModel) {


  /**
   * High level method to add community plugins to project with or without catalog.
   * It automatically checks whether plugin with such version already exists in catalog and use alias.
   * It also adds declaration to root project build file with  "apply false" to use gradle cache
   */
  fun addPlugin(pluginId: String, version: String, parsedModel: GradleBuildModel) {
    when (AddDependencyPolicy.calculateAddDependencyPolicy(projectModel)) {
      AddDependencyPolicy.VERSION_CATALOG -> addPluginToCatalog(pluginId, version, parsedModel)
      AddDependencyPolicy.BUILD_FILE -> addPluginToBuildOnly(pluginId, version, parsedModel)
    }
  }

  /**
   * High level method to add core plugins as they are not associated with version and does not mention in catalog.
   */
  fun addCorePlugin(pluginId: String, parsedModel: GradleBuildModel) {
    parsedModel.applyPlugin(pluginId)
  }

  private fun addPluginToBuildOnly(pluginId: String, version: String, parsedModel: GradleBuildModel){
    val rootProjectModel: GradleBuildModel? = projectModel.projectBuildModel
    if(rootProjectModel != null && rootProjectModel.virtualFile != parsedModel.virtualFile) {
      rootProjectModel.applyPlugin(pluginId, version, false)
      parsedModel.applyPlugin(pluginId, null, true)
    } else {
      parsedModel.applyPlugin(pluginId, version, true)
    }
  }

  private fun executeForRootProjectFile(module: GradleBuildModel, f:(GradleBuildModel) -> Unit){
    val rootProjectModel: GradleBuildModel? = projectModel.projectBuildModel
    if(rootProjectModel != null && rootProjectModel.virtualFile != module.virtualFile) {
      // it makes sense to update rootProjectModel
      f(rootProjectModel)
    }
  }

  private fun addPluginToCatalog(pluginId: String,
                                 version:String,
                                 parsedModel: GradleBuildModel) {
    val catalogModel = projectModel.versionCatalogsModel.getVersionCatalogModel(DEFAULT_CATALOG_NAME)
    assert(catalogModel != null) { "Catalog $DEFAULT_CATALOG_NAME must be available on add dependency" }
    val compactNotation = "$pluginId:$version"
    val alias = findCatalogPluginDeclaration(catalogModel!!, compactNotation) ?: addCatalogPlugin(catalogModel, compactNotation)
    if (alias == null) {
      log.warn("Cannot add catalog reference to build as we cannot find/add catalog declaration")
      return
    }
    val reference = ReferenceTo(catalogModel.plugins().findProperty(alias), parsedModel.dependencies())

    executeForRootProjectFile(parsedModel){rootBuildFile -> rootBuildFile.applyPlugin(reference, false)}

    parsedModel.applyPlugin(reference, true)
  }

  /**
   * Search by plugin id and version
   */
  private fun findCatalogPluginDeclaration(catalogModel: GradleVersionCatalogModel,
                                           compactNotation: String): Alias? {
    val declarations = catalogModel.pluginDeclarations().getAll()
    return declarations.filter { it.value.compactNotation() == compactNotation }.map { it.key }.firstOrNull()
  }


  companion object {
    val log = Logger.getInstance(PluginsHelper::class.java)
    private fun String.parsePluginNotation():Pair<String,String?>{
      val pluginId = this.substringBefore(":")
      val version = this.substringAfter(":", "")
      return Pair(pluginId, version.ifEmpty { null })
    }
    fun addCatalogPlugin(catalogModel: GradleVersionCatalogModel,
                                     compactNotation: String): Alias? {
      val plugins = catalogModel.pluginDeclarations()
      val names = plugins.getAllAliases()
      val (pluginId,version) = compactNotation.parsePluginNotation()
      if (version == null) {
        log.warn("Cannot add plugin $compactNotation to catalog as version is null")
        return null
      }
      val richVersion = RichVersion.parse(version)
      val reference = addCatalogVersion(catalogModel, pluginId, richVersion)

      if (reference == null) {
        log.warn("Cannot add catalog plugin (wrong version format): $compactNotation") // this depends on correct version syntax
        return null
      }
      val alias: Alias = pickPluginVariableName(pluginId, names)

      plugins.addDeclaration(alias, pluginId, ReferenceTo(reference, plugins))
      return alias
    }

    private fun addCatalogVersion(catalogModel: GradleVersionCatalogModel,
                                  pluginId: String,
                                  version: RichVersion): VersionDeclarationModel? {
      val versions = catalogModel.versionDeclarations()
      val names = versions.getAllAliases()
      val alias: Alias =  pickPluginVersionVariableName(pluginId, names)
      return when (val identifier = version.toIdentifier()) {
        null -> null
        else -> versions.addDeclaration(alias, identifier)
      }
    }
  }
}