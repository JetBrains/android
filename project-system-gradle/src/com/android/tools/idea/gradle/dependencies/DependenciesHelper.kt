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

import com.android.ide.common.repository.GradleCoordinate.*
import com.android.ide.common.repository.pickLibraryVariableName
import com.android.tools.idea.gradle.dependencies.AddDependencyPolicy.Companion.calculateAddDependencyPolicy
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.dsl.api.GradleVersionCatalogModel
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.dsl.api.dependencies.DependenciesModel
import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo
import com.android.tools.idea.gradle.dsl.api.settings.VersionCatalogModel

class DependenciesHelper(private val projectModel: ProjectBuildModel) {

  fun addDependency(configuration:String, dependency:String, parsedModel: GradleBuildModel){
    when(calculateAddDependencyPolicy(projectModel)) {
      AddDependencyPolicy.VERSION_CATALOG -> {
        val catalogModel = projectModel.versionCatalogsModel.getVersionCatalogModel(VersionCatalogModel.DEFAULT_CATALOG_NAME)
        val dependenciesModel = parsedModel.dependencies()
        assert(catalogModel != null) { "Catalog ${VersionCatalogModel.DEFAULT_CATALOG_NAME} must be available on add dependency" }
        val alias = findCatalogDeclaration(catalogModel!!, dependency) ?: addCatalogLibrary(catalogModel, dependency)
        addCatalogReference(alias, configuration, catalogModel, dependenciesModel)
      }
      AddDependencyPolicy.BUILD_FILE -> {
        val dependenciesModel = parsedModel.dependencies()
        dependenciesModel.addArtifact(configuration, dependency);
      }
    }
  }

  private fun findCatalogDeclaration(catalogModel: GradleVersionCatalogModel,
                                     coordinate: String): String? {
    val declarations = catalogModel.libraryDeclarations().getAll()
    return declarations.filter { it.value.compactNotation() == coordinate }.map { it.key }.firstOrNull()
  }

  private fun addCatalogLibrary(catalogModel: GradleVersionCatalogModel,
                                coordinate: String): String {
    val gc = parseCoordinateString(coordinate)
    assert(gc != null)
    val libraries = catalogModel.libraryDeclarations()
    val names = libraries.getAllAliases()
    val alias: String = pickLibraryVariableName(gc!!, false, names)
    libraries.addDeclaration(alias, coordinate)
    return alias
  }

  private fun addCatalogReference(alias:String,
                                  configName:String,
                                  catalogModel: GradleVersionCatalogModel,
                                  dependenciesModel: DependenciesModel){
    val reference = ReferenceTo(catalogModel.libraries().findProperty(alias), dependenciesModel)
    dependenciesModel.addArtifact(configName, reference)
  }
}