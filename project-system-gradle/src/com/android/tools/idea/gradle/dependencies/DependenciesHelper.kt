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

import com.android.ide.common.gradle.Dependency
import com.android.ide.common.repository.pickLibraryVariableName
import com.android.ide.common.repository.pickVersionVariableName
import com.android.tools.idea.gradle.dependencies.AddDependencyPolicy.Companion.calculateAddDependencyPolicy
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.dsl.api.GradleVersionCatalogModel
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencySpec
import com.android.tools.idea.gradle.dsl.api.dependencies.DependenciesModel
import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo
import com.android.tools.idea.gradle.dsl.api.settings.VersionCatalogModel
import com.intellij.openapi.diagnostic.Logger

typealias Alias = String

class DependenciesHelper(private val projectModel: ProjectBuildModel) {


  fun addDependency(configuration: String,
                    dependency: String,
                    excludes: List<ArtifactDependencySpec>,
                    parsedModel: GradleBuildModel) {
    when (calculateAddDependencyPolicy(projectModel)) {
      AddDependencyPolicy.VERSION_CATALOG -> addDependencyToCatalog(configuration, dependency, excludes, parsedModel)
      AddDependencyPolicy.BUILD_FILE -> {
        val dependenciesModel = parsedModel.dependencies()
        dependenciesModel.addArtifact(configuration, dependency, excludes);
      }
    }
  }

  fun addDependency(configuration: String, dependency: String, parsedModel: GradleBuildModel) =
    addDependency(configuration, dependency, listOf(), parsedModel)

  private fun addDependencyToCatalog(configuration: String,
                                     dependency: String,
                                     excludes:List<ArtifactDependencySpec>,
                                     parsedModel: GradleBuildModel) {
    val catalogModel = projectModel.versionCatalogsModel.getVersionCatalogModel(VersionCatalogModel.DEFAULT_CATALOG_NAME)
    val dependenciesModel = parsedModel.dependencies()
    assert(catalogModel != null) { "Catalog ${VersionCatalogModel.DEFAULT_CATALOG_NAME} must be available on add dependency" }
    val alias = findCatalogDeclaration(catalogModel!!, dependency) ?: addCatalogLibrary(catalogModel, dependency)
    if (alias == null) {
      log.warn("Cannot add catalog reference to build as we cannot find/add catalog declaration")
      return
    }
    addCatalogReference(alias, configuration, excludes, catalogModel, dependenciesModel)
  }

  private fun addCatalogReference(alias: Alias,
                                  configName: String,
                                  excludes:List<ArtifactDependencySpec>,
                                  catalogModel: GradleVersionCatalogModel,
                                  dependenciesModel: DependenciesModel) {
    val reference = ReferenceTo(catalogModel.libraries().findProperty(alias), dependenciesModel)
    dependenciesModel.addArtifact(configName, reference, excludes)
  }

  private fun findCatalogDeclaration(catalogModel: GradleVersionCatalogModel,
                                     coordinate: String): Alias? {
    val declarations = catalogModel.libraryDeclarations().getAll()
    return declarations.filter { it.value.compactNotation() == coordinate }.map { it.key }.firstOrNull()
  }

  companion object {
    val log = Logger.getInstance(DependenciesHelper::class.java)
    @JvmStatic fun addCatalogLibrary(catalogModel: GradleVersionCatalogModel,
                                     dependency: Dependency): Alias? {
      val libraries = catalogModel.libraryDeclarations()
      val names = libraries.getAllAliases()
      val reference = addCatalogVersion(catalogModel, dependency)
      if (reference == null) {
        log.warn("Cannot add catalog library (wrong version format): $dependency") // this depends on correct version syntax
        return null
      }
      val group = dependency.group
      if (group == null) {
        log.warn("Cannot add catalog library (missing group): $dependency")
        return null
      }
      val alias: Alias = pickLibraryVariableName(dependency, false, names)

      libraries.addDeclaration(alias, dependency.name, group, reference)
      return alias
    }

    @JvmStatic fun addCatalogLibrary(catalogModel: GradleVersionCatalogModel,
                                     coordinate: String): Alias? {

      val dependency = Dependency.parse(coordinate)
      if(dependency.group == null || dependency.version == null) {
        log.warn("Cannot add catalog library. Wrong format: $coordinate")
        return null
      }
      return addCatalogLibrary(catalogModel, dependency)
    }

    private fun addCatalogVersion(catalogModel: GradleVersionCatalogModel,
                                  dependency: Dependency): ReferenceTo? {
      val versions = catalogModel.versionDeclarations()
      val names = versions.getAllAliases()
      val alias: Alias = pickVersionVariableName(dependency, names)
      // TODO(b/279886738): we could actually generate and use a VersionDeclarationSpec here from the dependency version
      return when (val identifier = dependency.version?.toIdentifier()) {
        null -> null
        else -> versions.addDeclaration(alias, identifier)
      }
    }
  }
}