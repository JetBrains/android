/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.model.android

import com.android.builder.model.AndroidProject.PROJECT_TYPE_APP
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.gradle.structure.model.PsArtifactDependencySpec
import com.android.tools.idea.gradle.structure.model.PsModule
import com.android.tools.idea.gradle.structure.model.PsProject
import com.android.tools.idea.gradle.structure.model.repositories.search.AndroidSdkRepositories
import com.android.tools.idea.gradle.structure.model.repositories.search.ArtifactRepository
import com.android.tools.idea.gradle.util.GradleUtil.getAndroidModuleIcon
import com.intellij.openapi.module.Module
import javax.swing.Icon

class PsAndroidModule(
  parent: PsProject,
  resolvedModel: Module,
  gradlePath: String,
  private val gradleModel: AndroidModuleModel,
  parsedModel: GradleBuildModel
) : PsModule(parent, resolvedModel, gradlePath, parsedModel), PsAndroidModel {

  private var buildTypeCollection: PsBuildTypeCollection? = null
  private var productFlavorCollection: PsProductFlavorCollection? = null
  private var variantCollection: PsVariantCollection? = null
  private var dependencyCollection: PsAndroidDependencyCollection? = null
  private var signingConfigCollection: PsSigningConfigCollection? = null

  val isLibrary: Boolean get() = gradleModel.androidProject.projectType != PROJECT_TYPE_APP

  val buildTypes: List<PsBuildType> get() = getOrCreateBuildTypeCollection().items()
  val productFlavors: List<PsProductFlavor> get() = getOrCreateProductFlavorCollection().items()
  val variants: List<PsVariant> get() = getOrCreateVariantCollection().items()
  val dependencies: PsAndroidDependencyCollection get() = getOrCreateDependencyCollection()
  val signingConfigs: List<PsSigningConfig> get() = getOrCreateSigningConfigCollection().items()
  val defaultConfig = PsAndroidModuleDefaultConfig(this)
  val flavorDimensions: Collection<String>
    get() {
      val result = mutableSetOf<String>()
      result.addAll(gradleModel.androidProject.flavorDimensions)
      val parsedFlavorDimensions = parsedModel?.android()?.flavorDimensions()?.toList()
      if (parsedFlavorDimensions != null) {
        result.addAll(parsedFlavorDimensions.map { v -> v.toString() })
      }
      return result
    }

  fun findBuildType(buildType: String): PsBuildType? = getOrCreateBuildTypeCollection().findElement(buildType)

  fun findProductFlavor(name: String): PsProductFlavor? = getOrCreateProductFlavorCollection().findElement(name)

  fun findVariant(name: String): PsVariant? = getOrCreateVariantCollection().findElement(name)

  fun findSigningConfig(signingConfig: String): PsSigningConfig? = getOrCreateSigningConfigCollection().findElement(signingConfig)

  override fun canDependOn(module: PsModule): Boolean =
    // 'module' is either a Java library or an AAR module.
    (module as? PsAndroidModule)?.isLibrary == true

  override fun getGradleModel(): AndroidModuleModel = gradleModel

  override fun getIcon(): Icon? = getAndroidModuleIcon(gradleModel)

  override fun getGradlePath(): String = super.getGradlePath()!!

  override fun getResolvedModel(): Module = super.getResolvedModel()!!

  override fun getArtifactRepositories(): List<ArtifactRepository> {
    val repositories = mutableListOf<ArtifactRepository>()
    populateRepositories(repositories)
    var repository = AndroidSdkRepositories.getAndroidRepository()
    if (repository != null) {
      repositories.add(repository)
    }
    repository = AndroidSdkRepositories.getGoogleRepository()
    if (repository != null) {
      repositories.add(repository)
    }
    return repositories
  }

  override fun getConfigurations(): List<String> = throw UnsupportedOperationException()

  override fun addLibraryDependency(library: String, scopesNames: List<String>) {
    // Update/reset the "parsed" model.
    addLibraryDependencyToParsedModel(scopesNames, library)

    resetDependencies()

    val spec = PsArtifactDependencySpec.create(library)!!
    fireLibraryDependencyAddedEvent(spec)
    isModified = true
  }

  override fun addModuleDependency(modulePath: String, scopesNames: List<String>) {
    // Update/reset the "parsed" model.
    addModuleDependencyToParsedModel(scopesNames, modulePath)

    resetDependencies()

    fireModuleDependencyAddedEvent(modulePath)
    isModified = true
  }

  override fun setLibraryDependencyVersion(
    spec: PsArtifactDependencySpec,
    configurationName: String,
    newVersion: String
  ) {
    var modified = false
    val matchingDependencies = dependencies
      .findLibraryDependencies(spec.group, spec.name)
      .filter { it -> it.spec == spec && it.configurationNames.contains(configurationName) }
    // Usually there should be only one item in the matchingDependencies list. However, if there are duplicate entries in the config file
    // it might differ. We update all of them.

    for (dependency in matchingDependencies) {
      assert(dependency.parsedModels.size == 1)
      for (parsedDependency in dependency.parsedModels) {
        assert(parsedDependency is ArtifactDependencyModel)
        val artifactDependencyModel = parsedDependency as ArtifactDependencyModel
        artifactDependencyModel.setVersion(newVersion)
        modified = true
      }
    }
    if (modified) {
      resetDependencies()
      for (dependency in matchingDependencies) {
        fireDependencyModifiedEvent(dependency)
      }
      isModified = true
    }
  }

  fun addNewBuildType(name: String): PsBuildType = getOrCreateBuildTypeCollection().addNew(name)

  fun removeBuildType(buildType: PsBuildType) = getOrCreateBuildTypeCollection().remove(buildType.name)

  fun addNewFlavorDimension(newName: String) {
    assert(parsedModel != null)
    val androidModel = parsedModel!!.android()!!
    androidModel.flavorDimensions().addListValue().setValue(newName)
    isModified = true
  }

  fun removeFlavorDimension(flavorDimension: String) {
    assert(parsedModel != null)
    val androidModel = parsedModel!!.android()!!

    val model = androidModel.flavorDimensions().getListValue(flavorDimension)
    if (model != null) {
      model.delete()
      isModified = true
    }
  }

  fun addNewProductFlavor(name: String): PsProductFlavor = getOrCreateProductFlavorCollection().addNew(name)

  fun removeProductFlavor(productFlavor: PsProductFlavor) = getOrCreateProductFlavorCollection().remove(productFlavor.name)

  fun addNewSigningConfig(name: String): PsSigningConfig = getOrCreateSigningConfigCollection().addNew(name)

  fun removeSigningConfig(signingConfig: PsSigningConfig) = getOrCreateSigningConfigCollection().remove(signingConfig.name)


  private fun getOrCreateBuildTypeCollection(): PsBuildTypeCollection =
    buildTypeCollection ?: PsBuildTypeCollection(this).also { buildTypeCollection = it }

  private fun getOrCreateProductFlavorCollection(): PsProductFlavorCollection =
    productFlavorCollection ?: PsProductFlavorCollection(this).also { productFlavorCollection = it }

  private fun getOrCreateVariantCollection(): PsVariantCollection =
    variantCollection ?: PsVariantCollection(this).also { variantCollection = it }

  private fun getOrCreateDependencyCollection(): PsAndroidDependencyCollection =
    dependencyCollection ?: PsAndroidModuleDependencyCollection(this).also { dependencyCollection = it }

  private fun getOrCreateSigningConfigCollection(): PsSigningConfigCollection =
    signingConfigCollection ?: PsSigningConfigCollection(this).also { signingConfigCollection = it }

  private fun resetDependencies() {
    dependencyCollection = null
    variants.forEach { variant -> variant.forEachArtifact { artifact -> artifact.resetDependencies() } }
  }
}
