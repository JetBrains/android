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
import com.android.ide.common.gradle.RichVersion
import com.android.ide.common.repository.keysMatch
import com.android.ide.common.repository.pickLibraryVariableName
import com.android.ide.common.repository.pickVersionVariableName
import com.android.tools.idea.gradle.dependencies.AddDependencyPolicy.Companion.calculateAddDependencyPolicy
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.dsl.api.GradleVersionCatalogModel
import com.android.tools.idea.gradle.dsl.api.GradleVersionCatalogsModel
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencySpec
import com.android.tools.idea.gradle.dsl.api.dependencies.VersionDeclarationModel
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.*
import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo
import com.android.tools.idea.gradle.dsl.api.settings.VersionCatalogModel
import com.intellij.openapi.diagnostic.Logger
import org.gradle.api.plugins.JavaPlatformPlugin.CLASSPATH_CONFIGURATION_NAME

typealias Alias = String

class DependenciesHelper(private val projectModel: ProjectBuildModel) {

  @JvmOverloads
  fun addPlatformDependency(
    configuration: String,
    dependency: String,
    enforced: Boolean,
    matcher: DependencyMatcher = ExactDependencyMatcher(dependency),
    parsedModel: GradleBuildModel) {
    val buildscriptDependencies = parsedModel.dependencies()
    when (calculateAddDependencyPolicy(projectModel)) {
      AddDependencyPolicy.VERSION_CATALOG -> getOrAddDependencyToCatalog(dependency, matcher)?.let { alias ->
        val reference = ReferenceTo(getCatalogModel().libraries().findProperty(alias), buildscriptDependencies)
        buildscriptDependencies.addPlatformArtifact(configuration, reference, enforced)
      }
      AddDependencyPolicy.BUILD_FILE -> {
        buildscriptDependencies.addPlatformArtifact(configuration, dependency, enforced)
      }
    }
  }

  @JvmOverloads
  fun addClasspathDependency(dependency: String,
                             excludes: List<ArtifactDependencySpec> = listOf(),
                             matcher: DependencyMatcher = ExactDependencyMatcher(dependency)) {
    val buildModel = projectModel.projectBuildModel ?: return
    val buildscriptDependencies = buildModel.buildscript().dependencies()
    when (calculateAddDependencyPolicy(projectModel)) {
      AddDependencyPolicy.VERSION_CATALOG -> getOrAddDependencyToCatalog(dependency, matcher)?.let { alias ->
        val reference = ReferenceTo(getCatalogModel().libraries().findProperty(alias), buildscriptDependencies)
        buildscriptDependencies.addArtifact(CLASSPATH_CONFIGURATION_NAME, reference, excludes)
      }
      AddDependencyPolicy.BUILD_FILE -> {
        buildscriptDependencies.addArtifact(CLASSPATH_CONFIGURATION_NAME, dependency, excludes)
      }
    }
  }

  fun addDependency(configuration: String,
                    dependency: String,
                    excludes: List<ArtifactDependencySpec>,
                    matcher: DependencyMatcher,
                    parsedModel: GradleBuildModel) {
    when (calculateAddDependencyPolicy(projectModel)) {
      AddDependencyPolicy.VERSION_CATALOG -> getOrAddDependencyToCatalog(dependency, matcher)?.let { alias ->
        val dependenciesModel = parsedModel.dependencies()
        val reference = ReferenceTo(getCatalogModel().libraries().findProperty(alias), dependenciesModel)
        dependenciesModel.addArtifact(configuration, reference, excludes)
      }
      AddDependencyPolicy.BUILD_FILE -> {
        val dependenciesModel = parsedModel.dependencies()
        dependenciesModel.addArtifact(configuration, dependency, excludes);
      }
    }
  }

  private fun getCatalogModel(): GradleVersionCatalogModel {
    val catalogModel = projectModel.versionCatalogsModel.getVersionCatalogModel(VersionCatalogModel.DEFAULT_CATALOG_NAME)
    // check invariant that at this point catalog must be available as algorithm chose to add dependency to catalog
    check(catalogModel != null) { "Catalog ${VersionCatalogModel.DEFAULT_CATALOG_NAME} must be available to add dependency" }
    return catalogModel
  }

  /**
   * This is short version of addDependency function.
   * Assuming there is no excludes and algorithm will search exact dependency declaration in catalog if exists.
   */
  fun addDependency(configuration: String, dependency: String, parsedModel: GradleBuildModel) =
    addDependency(configuration, dependency, listOf(), ExactDependencyMatcher(dependency), parsedModel)

  private fun getOrAddDependencyToCatalog(dependency: String, matcher: DependencyMatcher): Alias? {
    val catalogModel = getCatalogModel()
    val alias = findCatalogDeclaration(catalogModel, matcher) ?: addCatalogLibrary(catalogModel, dependency)
    if (alias == null) {
      log.warn("Cannot add catalog reference to build as we cannot find/add catalog declaration")
      return null
    }
    return alias
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

      if(dependency.version != null) {
        val version = addCatalogVersion(catalogModel, dependency)
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

    @JvmStatic fun addCatalogLibrary(catalogModel: GradleVersionCatalogModel,
                                     coordinate: String): Alias? {

      val dependency = Dependency.parse(coordinate)
      if(dependency.group == null) {
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
      val version = addCatalogVersion(catalogModel, dependencySpec) ?: return false.also {
        log.warn("Cannot update catalog library (wrong version format): $dependency")
      }

      result.value.updateVersion(version)
      return true
    }

    private fun ArtifactDependencyModel.findVersionCatalogModel(catalogsModel: GradleVersionCatalogsModel): GradleVersionCatalogModel? {
      val referenceString: String = completeModel().unresolvedModel.getValue(STRING_TYPE) ?: return null
      val catalogName = referenceString.substringBefore(".")
      return catalogsModel.getVersionCatalogModel(catalogName)
    }

    private fun ArtifactDependencyModel.getAlias(): String? {
      val referenceString: String = completeModel().unresolvedModel.getValue(STRING_TYPE) ?: return null
      if (!referenceString.contains(".")) return null
      return referenceString.substringAfter(".")
    }

    private fun addCatalogVersion(catalogModel: GradleVersionCatalogModel,
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
  }
}