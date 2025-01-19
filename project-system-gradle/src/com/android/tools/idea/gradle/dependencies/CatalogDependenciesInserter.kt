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
import com.android.ide.common.gradle.RichVersion
import com.android.ide.common.repository.keysMatch
import com.android.ide.common.repository.pickLibraryVariableName
import com.android.ide.common.repository.pickVersionVariableName
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.dsl.api.GradleVersionCatalogModel
import com.android.tools.idea.gradle.dsl.api.GradleVersionCatalogsModel
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencySpec
import com.android.tools.idea.gradle.dsl.api.dependencies.DependenciesModel
import com.android.tools.idea.gradle.dsl.api.dependencies.VersionDeclarationModel
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel
import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.utils.addIfNotNull

@Suppress("AddDependencyUsage")
class CatalogDependenciesInserter(private val projectModel: ProjectBuildModel) : DependenciesInserter() {

  override fun addPlatformDependency(
    configuration: String,
    dependency: String,
    enforced: Boolean,
    parsedModel: GradleBuildModel,
    matcher: DependencyMatcher): Set<PsiFile> {
    val buildscriptDependencies = parsedModel.dependencies()
    return getOrAddDependencyToCatalog(projectModel, dependency, matcher) { alias, updatedFiles ->
      val reference = ReferenceTo(getCatalogModel().libraries().findProperty(alias), buildscriptDependencies)
      if (!buildscriptDependencies.hasArtifact(matcher)) {
        buildscriptDependencies.addPlatformArtifact(configuration, reference, enforced).also {
          updatedFiles.addIfNotNull(parsedModel.psiFile)
        }
      }
    }
  }

  override fun addDependency(configuration: String,
                             dependency: String,
                             excludes: List<ArtifactDependencySpec>,
                             parsedModel: GradleBuildModel,
                             matcher: DependencyMatcher,
                             sourceSetName: String?): Set<PsiFile> =
    getOrAddDependencyToCatalog(projectModel, dependency, matcher) { alias, changedFiles ->
      val dependenciesModel = getDependenciesModel(sourceSetName, parsedModel)
      if (dependenciesModel != null && !dependenciesModel.hasArtifact(matcher)) {
        val reference = ReferenceTo(getCatalogModel().libraries().findProperty(alias), dependenciesModel)
        dependenciesModel.addArtifact(configuration, reference, excludes).also {
          changedFiles.addIfNotNull(parsedModel.psiFile)
        }
      }
    }

  override fun updateDependencyVersion(dependency: Dependency,
                                       buildModel: GradleBuildModel) {
    check(dependency.version != null) { "Version must not be null for updateDependencyVersion" }
    val catalogsModel = projectModel.versionCatalogsModel

    findDependency(dependency, buildModel)?.let { artifact ->
      if (artifact.isVersionCatalogDependency)
        updateCatalogLibrary(catalogsModel, artifact, dependency.version!!)
      else
        super.updateDependencyVersion(dependency, buildModel)
    }
  }

  private fun getDependenciesModel(sourceSetName: String?, parsedModel: GradleBuildModel): DependenciesModel? {
    return if (sourceSetName != null) {
      parsedModel.kotlin().sourceSets().find { it.name() == sourceSetName }?.dependencies()
    }
    else {
      parsedModel.dependencies()
    }
  }

  private fun getCatalogModel(): GradleVersionCatalogModel = getCatalogModel(projectModel)

  companion object {
    val log = Logger.getInstance(DependenciesHelper::class.java)

    internal fun getOrAddDependencyToCatalog(projectModel: ProjectBuildModel,
                                             dependency: String,
                                            matcher: DependencyMatcher,
                                            handler: (Alias, MutableSet<PsiFile>) -> Unit): Set<PsiFile> {
      val updatedFiles = mutableSetOf<PsiFile>()
      val (alias, updatedFile) = getOrAddDependencyToCatalog(projectModel, dependency, matcher)
      updatedFiles.addIfNotNull(updatedFile)
      alias ?: return updatedFiles
      handler.invoke(alias, updatedFiles)
      return updatedFiles
    }

    internal fun getCatalogModel(projectModel: ProjectBuildModel): GradleVersionCatalogModel {
      val catalogModel = DependenciesHelper.getDefaultCatalogModel(projectModel)
      // check invariant that at this point catalog must be available as algorithm chose to add dependency to catalog
      check(catalogModel != null) { "Catalog ${DependenciesHelper.getDefaultCatalogName(projectModel)} must be available to add dependency" }
      return catalogModel
    }

    private fun getOrAddDependencyToCatalog(projectModel: ProjectBuildModel, dependency: String, matcher: DependencyMatcher): Pair<Alias?, PsiFile?> {
      val catalogModel = getCatalogModel(projectModel)
      val result = findCatalogDeclaration(catalogModel, matcher)?.let { Pair(it, null) } ?: Pair(addCatalogLibrary(catalogModel, dependency),
                                                                                                 catalogModel.psiFile)
      if (result.first == null) {
        log.warn("Cannot add catalog reference to build as we cannot find/add catalog declaration")
      }
      return result
    }

    private fun findCatalogDeclaration(catalogModel: GradleVersionCatalogModel,
                                       matcher: DependencyMatcher): Alias? {
      val declarations = catalogModel.libraryDeclarations().getAll()
      return declarations.filter { matcher.match(it.value) }.map { it.key }.firstOrNull()
    }

    @JvmStatic
    fun addCatalogLibrary(catalogModel: GradleVersionCatalogModel,
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
      }
      else {
        libraries.addDeclaration(alias, dependency.name, group)
      }
      return alias
    }

    @JvmStatic
    fun addCatalogLibrary(catalogModel: GradleVersionCatalogModel,
                          coordinate: String): Alias? {

      val dependency = Dependency.parse(coordinate)
      if (dependency.group == null) {
        log.warn("Cannot add catalog library. Wrong format: $coordinate")
        return null
      }
      return addCatalogLibrary(catalogModel, dependency)
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
  }
}