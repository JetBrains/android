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

import com.android.ide.common.repository.GradleCoordinate
import com.android.ide.common.repository.GradleCoordinate.*
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

  private fun findCatalogDeclaration(catalogModel: GradleVersionCatalogModel,
                                     coordinate: String): Alias? {
    val declarations = catalogModel.libraryDeclarations().getAll()
    return declarations.filter { it.value.compactNotation() == coordinate }.map { it.key }.firstOrNull()
  }

  companion object {
    val log = Logger.getInstance(DependenciesHelper::class.java)
    @JvmStatic fun addCatalogLibrary(catalogModel: GradleVersionCatalogModel,
                                     coordinate: GradleCoordinate): Alias {
      val libraries = catalogModel.libraryDeclarations()
      val names = libraries.getAllAliases()
      val alias: Alias = pickLibraryVariableName(coordinate, false, names)

      val reference = addCatalogVersion(catalogModel, coordinate)
      log.warn("Cannot add catalog library. Wrong version format.") // this depends on correct version syntax
      libraries.addDeclaration(alias, coordinate.artifactId, coordinate.groupId, reference!!)
      return alias
    }

    @JvmStatic fun addCatalogLibrary(catalogModel: GradleVersionCatalogModel,
                                     coordinate: String): Alias? {
      val gc = parseCoordinateString(coordinate)
      if(gc == null){
        log.warn("Cannot add catalog library. Wrong format:$coordinate")
        return null
      }
      return addCatalogLibrary(catalogModel, gc)
    }

    private fun addCatalogVersion(catalogModel: GradleVersionCatalogModel,
                                  gc: GradleCoordinate): ReferenceTo? {
      val versions = catalogModel.versionDeclarations()
      val names = versions.getAllAliases()
      val alias: Alias = pickVersionVariableName(gc, names)
      return versions.addDeclaration(alias, gc.revision)
    }

  }

  private fun addCatalogReference(alias: Alias,
                                  configName: String,
                                  excludes:List<ArtifactDependencySpec>,
                                  catalogModel: GradleVersionCatalogModel,
                                  dependenciesModel: DependenciesModel) {
    val reference = ReferenceTo(catalogModel.libraries().findProperty(alias), dependenciesModel)
    dependenciesModel.addArtifact(configName, reference, excludes)
  }
}