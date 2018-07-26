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
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.gradle.structure.model.*
import com.android.tools.idea.gradle.structure.model.java.PsJavaModule
import com.android.tools.idea.gradle.structure.model.meta.maybeValue
import com.android.tools.idea.gradle.structure.model.repositories.search.AndroidSdkRepositories
import com.android.tools.idea.gradle.structure.model.repositories.search.ArtifactRepository
import com.android.tools.idea.gradle.util.GradleUtil.getAndroidModuleIcon
import com.android.utils.combineAsCamelCase
import java.io.File
import javax.swing.Icon

class PsAndroidModule(
  parent: PsProject,
  override val gradlePath: String
) : PsModule(parent) {
  var resolvedModel: AndroidModuleModel? = null; private set
  override var projectType: PsModuleType = PsModuleType.UNKNOWN; private set
  var isLibrary: Boolean = false; private set
  override var rootDir: File? = null; private set
  override var icon: Icon? = null; private set

  private var buildTypeCollection: PsBuildTypeCollection? = null
  private var productFlavorCollection: PsProductFlavorCollection? = null
  private var variantCollection: PsVariantCollection? = null
  private var dependencyCollection: PsAndroidModuleDependencyCollection? = null
  private var signingConfigCollection: PsSigningConfigCollection? = null

  fun init(
    name: String,
    parentModule: PsModule?,
    resolvedModel: AndroidModuleModel?,
    parsedModel: GradleBuildModel?
  ) {
    super.init(name, parentModule, parsedModel)
    this.resolvedModel = resolvedModel

    projectType =
      moduleTypeFromAndroidModuleType(resolvedModel?.androidProject?.projectType).takeUnless { it == PsModuleType.UNKNOWN }
      ?: parsedModel?.parsedModelModuleType() ?: PsModuleType.UNKNOWN
    isLibrary = projectType.androidModuleType != PROJECT_TYPE_APP
    rootDir = resolvedModel?.rootDirPath ?: parsedModel?.virtualFile?.path?.let { File(it).parentFile }
    icon = projectType.androidModuleType?.let { getAndroidModuleIcon(it) }

    buildTypeCollection?.refresh()
    productFlavorCollection?.refresh()
    variantCollection?.refresh()
    dependencyCollection = null
    signingConfigCollection?.refresh()
  }

  val buildTypes: Collection<PsBuildType> get() = getOrCreateBuildTypeCollection()
  val productFlavors: Collection<PsProductFlavor> get() = getOrCreateProductFlavorCollection()
  val variants: Collection<PsVariant> get() = getOrCreateVariantCollection()
  override val dependencies: PsAndroidModuleDependencyCollection get() = getOrCreateDependencyCollection()
  val signingConfigs: Collection<PsSigningConfig> get() = getOrCreateSigningConfigCollection()
  val defaultConfig = PsAndroidModuleDefaultConfig(this)
  val flavorDimensions: Collection<String>
    get() {
      val result = mutableSetOf<String>()
      result.addAll(resolvedModel?.androidProject?.flavorDimensions.orEmpty())
      val parsedFlavorDimensions = parsedModel?.android()?.flavorDimensions()?.toList()
      if (parsedFlavorDimensions != null) {
        result.addAll(parsedFlavorDimensions.map { v -> v.toString() })
      }
      return result
    }

  fun findBuildType(buildType: String): PsBuildType? = getOrCreateBuildTypeCollection().findElement(buildType)

  fun findProductFlavor(name: String): PsProductFlavor? = getOrCreateProductFlavorCollection().findElement(name)

  fun findVariant(key: PsVariantKey): PsVariant? = getOrCreateVariantCollection().findElement(key)

  fun findSigningConfig(signingConfig: String): PsSigningConfig? = getOrCreateSigningConfigCollection().findElement(signingConfig)

  override fun canDependOn(module: PsModule): Boolean =
    // 'module' is either a Java library or an AAR module.
    (module as? PsAndroidModule)?.isLibrary == true || (module is PsJavaModule)

  override fun populateRepositories(repositories: MutableList<ArtifactRepository>) {
    super.populateRepositories(repositories)
    repositories.addAll(listOfNotNull(AndroidSdkRepositories.getAndroidRepository(), AndroidSdkRepositories.getGoogleRepository()))
  }

  // TODO(solodkyy): Return a collection of PsBuildConfiguration instead of strings.
  override fun getConfigurations(): List<String> {

    fun applicableArtifacts() = listOf("", "test", "androidTest")

    fun flavorsByDimension(dimension: String) =
      productFlavors.filter { it.dimension.maybeValue == dimension }.map { it.name }

    fun buildFlavorCombinations() = when {
      flavorDimensions.size > 1 -> flavorDimensions
        .fold(listOf(listOf(""))) { acc, dimension ->
          flavorsByDimension(dimension).flatMap { flavor ->
            acc.map { prefix -> prefix + flavor }
          }
        }
        .map { it.filter { it != "" }.combineAsCamelCase() }
      else -> listOf()  // There are no additional flavor combinations if there is only one flavor dimension.
    }

    fun applicableProductFlavors() =
      listOf("") + productFlavors.map { it.name } + buildFlavorCombinations()

    fun applicableBuildTypes(artifact: String) =
    // TODO(solodkyy): Include product flavor combinations
      when (artifact) {
        "androidTest" -> listOf("")  // androidTest is built only for the configured buildType.
        else -> listOf("") + buildTypes.map { it.name }
      }

    // TODO(solodkyy): When explicitly requested return other advanced scopes (compileOnly, api).
    fun applicableScopes() = listOf("implementation")

    val result = mutableListOf<String>()
    applicableArtifacts().forEach { artifact ->
      applicableProductFlavors().forEach { productFlavor ->
        applicableBuildTypes(artifact).forEach { buildType ->
          applicableScopes().forEach { scope ->
            result.add(listOf(artifact, productFlavor, buildType, scope).filter { it != "" }.combineAsCamelCase())
          }
        }
      }
    }
    return result.toList()
  }

  fun addNewBuildType(name: String): PsBuildType = getOrCreateBuildTypeCollection().addNew(name)

  fun removeBuildType(buildType: PsBuildType) = getOrCreateBuildTypeCollection().remove(buildType.name)

  fun addNewFlavorDimension(newName: String) {
    assert(parsedModel != null)
    val androidModel = parsedModel!!.android()
    androidModel.flavorDimensions().addListValue().setValue(newName)
    isModified = true
  }

  fun removeFlavorDimension(flavorDimension: String) {
    assert(parsedModel != null)
    val androidModel = parsedModel!!.android()

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

  private fun getOrCreateDependencyCollection(): PsAndroidModuleDependencyCollection =
    dependencyCollection ?: PsAndroidModuleDependencyCollection(this).also { dependencyCollection = it }

  private fun getOrCreateSigningConfigCollection(): PsSigningConfigCollection =
    signingConfigCollection ?: PsSigningConfigCollection(this).also { signingConfigCollection = it }

  override fun findLibraryDependencies(group: String?, name: String): List<PsDeclaredLibraryDependency> =
    dependencies.findLibraryDependencies(group, name)

  override fun resetDependencies() {
    resetDeclaredDependencies()
    resetResolvedDependencies()
  }

  internal fun resetResolvedDependencies() {
    variants.forEach { variant -> variant.forEachArtifact { artifact -> artifact.resetDependencies() } }
  }

  private fun resetDeclaredDependencies() {
    dependencyCollection = null
  }
}
