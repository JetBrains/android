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

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.android.tools.idea.gradle.project.model.NdkModuleModel
import com.android.tools.idea.gradle.project.sync.issues.SyncIssues
import com.android.tools.idea.gradle.repositories.search.AndroidSdkRepositories
import com.android.tools.idea.gradle.repositories.search.ArtifactRepository
import com.android.tools.idea.gradle.structure.model.ModuleKind
import com.android.tools.idea.gradle.structure.model.PsDeclaredDependency
import com.android.tools.idea.gradle.structure.model.PsDeclaredLibraryDependency
import com.android.tools.idea.gradle.structure.model.PsModelCollection
import com.android.tools.idea.gradle.structure.model.PsModule
import com.android.tools.idea.gradle.structure.model.PsModuleType
import com.android.tools.idea.gradle.structure.model.PsProject
import com.android.tools.idea.gradle.structure.model.java.PsJavaModule
import com.android.tools.idea.gradle.structure.model.meta.getValue
import com.android.tools.idea.gradle.structure.model.moduleTypeFromAndroidModuleType
import com.android.tools.idea.gradle.structure.model.parsedModelModuleType
import com.android.tools.idea.projectsystem.AndroidModuleSystem
import com.android.tools.idea.projectsystem.gradle.AndroidIconProviderProjectGradleToken
import com.android.utils.combineAsCamelCase
import com.android.utils.usLocaleCapitalize
import com.google.common.annotations.VisibleForTesting
import com.google.common.base.CharMatcher
import java.io.File
import javax.swing.Icon

// TODO(b/148838329): the first eight characters are disallowed by the Android Gradle Plugin: attempts to create names containing them throw
//  errors.  The last two, $ and ', are permitted in names by the plugin, but currently cause difficulty in the Dsl parsers/writers for
//  KotlinScript and Groovy respectively, and could be allowed if the Dsl supported them properly.
const val DISALLOWED_MESSAGE = "['/', ':', '<', '>', '\"', '?', '*', '|', '\$', ''']"
val DISALLOWED_IN_NAME: CharMatcher = CharMatcher.anyOf("/\\:<>\"?*|\$'")

class PsAndroidModule(
  parent: PsProject,
  override val gradlePath: String
) : PsModule(parent, ModuleKind.ANDROID) {
  override val descriptor by AndroidModuleDescriptors
  var resolvedModel: GradleAndroidModel? = null; private set
  var resolvedNativeModel: NdkModuleModel? = null; private set
  var resolvedSyncIssues: SyncIssues? = null ; private set
  override var projectType: PsModuleType = PsModuleType.UNKNOWN; private set
  var isLibrary: Boolean = false; private set
  override var rootDir: File? = null; private set
  override var icon: Icon? = null; private set

  private var buildTypeCollection: PsBuildTypeCollection? = null
  private var flavorDimensionCollection: PsFlavorDimensionCollection? = null
  private var productFlavorCollection: PsProductFlavorCollection? = null
  private var resolvedVariantCollection: PsResolvedVariantCollection? = null
  private var dependencyCollection: PsAndroidModuleDependencyCollection? = null
  private var signingConfigCollection: PsSigningConfigCollection? = null

  var buildToolsVersion by AndroidModuleDescriptors.buildToolsVersion
  var ndkVersion by AndroidModuleDescriptors.ndkVersion
  var compileSdkVersion by AndroidModuleDescriptors.compileSdkVersion
  var sourceCompatibility by AndroidModuleDescriptors.sourceCompatibility
  var targetCompatibility by AndroidModuleDescriptors.targetCompatibility
  var viewBindingEnabled by AndroidModuleDescriptors.viewBindingEnabled
  var includeDependenciesInfoInApk by AndroidModuleDescriptors.includeDependenciesInfoInApk

  fun init(
    name: String,
    parentModule: PsModule?,
    resolvedModel: GradleAndroidModel?,
    resolvedNativeModel: NdkModuleModel?,
    resolvedSyncIssues: SyncIssues?,
    parsedModel: GradleBuildModel?
  ) {
    super.init(name, parentModule, parsedModel)
    this.resolvedModel = resolvedModel
    this.resolvedNativeModel = resolvedNativeModel
    this.resolvedSyncIssues = resolvedSyncIssues

    projectType =
      moduleTypeFromAndroidModuleType(resolvedModel?.androidProject?.projectType).takeUnless { it == PsModuleType.UNKNOWN }
      ?: parsedModel?.parsedModelModuleType() ?: PsModuleType.UNKNOWN
    isLibrary = projectType.androidModuleType != AndroidModuleSystem.Type.TYPE_APP
    rootDir = resolvedModel?.rootDirPath ?: parsedModel?.virtualFile?.path?.let { File(it).parentFile }
    icon = projectType.androidModuleType?.let { AndroidIconProviderProjectGradleToken.getAndroidModuleIcon(it) }

    buildTypeCollection?.refresh()
    flavorDimensionCollection?.refresh()
    productFlavorCollection?.refresh()
    resolvedVariantCollection?.refresh()
    dependencyCollection?.let { it.refresh(); fireDependenciesReloadedEvent() }
    signingConfigCollection?.refresh()
  }

  val buildTypes: PsModelCollection<PsBuildType> get() = getOrCreateBuildTypeCollection()
  val productFlavors: PsModelCollection<PsProductFlavor> get() = getOrCreateProductFlavorCollection()
  val resolvedVariants: PsModelCollection<PsVariant> get() = getOrCreateResolvedVariantCollection()
  override val dependencies: PsAndroidModuleDependencyCollection get() = getOrCreateDependencyCollection()
  val signingConfigs: PsModelCollection<PsSigningConfig> get() = getOrCreateSigningConfigCollection()
  val defaultConfig = PsAndroidModuleDefaultConfig(this)
  val flavorDimensions: PsModelCollection<PsFlavorDimension> get() = getOrCreateFlavorDimensionCollection()

  fun findBuildType(buildType: String): PsBuildType? = getOrCreateBuildTypeCollection().findElement(buildType)

  fun findProductFlavor(dimension: String, name: String): PsProductFlavor? =
    getOrCreateProductFlavorCollection().findElement(PsProductFlavorKey(dimension, name))

  fun findVariant(key: PsVariantKey): PsVariant? = getOrCreateResolvedVariantCollection().findElement(key)

  fun findSigningConfig(signingConfig: String): PsSigningConfig? = getOrCreateSigningConfigCollection().findElement(signingConfig)

  fun findFlavorDimension(flavorDimensionName: String): PsFlavorDimension? =
    getOrCreateFlavorDimensionCollection().findElement(flavorDimensionName)

  override fun canDependOn(module: PsModule): Boolean =
    // 'module' is either a Java library or an AAR module.
    (module as? PsAndroidModule)?.isLibrary == true || (module is PsJavaModule)

  override fun populateRepositories(repositories: MutableList<ArtifactRepository>) {
    super.populateRepositories(repositories)
    repositories.addAll(listOfNotNull(AndroidSdkRepositories.getAndroidRepository(), AndroidSdkRepositories.getGoogleRepository()))
  }

  private fun flavorNamesByDimension(dimension: String) =
    productFlavors.filter { it.effectiveDimension == dimension }.map { it.name }

  private fun flavorNamesCartesianProduct(newFlavorName: String? = null, dimensionName: String? = null): List<String> =
    flavorDimensions
      .map { it.name to flavorNamesByDimension(it.name) }
      .fold(listOf(listOf<String>())) { acc, flavorNames ->
        when (flavorNames.first) {
          dimensionName -> acc.map { it + newFlavorName!! }
          else -> acc.flatMap { a -> flavorNames.second.map { a + it } }
        }
      }
      .map { it.combineAsCamelCase() }

  // TODO(xof): in the light of b/137551452, remaining uses of this function (checking for collisions when adding new build types
  //  or product flavors) should be converted to flavorNamesCartesianProduct.  (Using this makes the check slightly too stringent.)
  private fun buildFlavorCombinations(newFlavorName: String? = null, dimensionName: String? = null) = when {
    flavorDimensions.size > 1 -> flavorDimensions
      .fold(listOf(listOf(""))) { acc, dimension ->
        (if (dimensionName == dimension.name) listOf(newFlavorName!!) else flavorNamesByDimension(dimension.name))
          .flatMap { flavor ->
            acc.map { prefix -> prefix + flavor }
          }
      }
      .map { it.filter { it != "" }.combineAsCamelCase() }
    else -> listOf()  // There are no additional flavor combinations if there is only one flavor dimension.
  }

  // TODO(solodkyy): Return a collection of PsBuildConfiguration instead of strings.
  override fun getConfigurations(onlyImportantFor: ImportantFor?): List<String> {

    fun applicableArtifacts() = listOf("", "test", "androidTest")

    fun applicableBuildTypes(artifact: String) =
      when (artifact) {
        "androidTest" -> listOf("")  // androidTest is built only for the configured buildType.
        else -> listOf("") +
                (if (onlyImportantFor == null || onlyImportantFor == ImportantFor.LIBRARY) buildTypes.map { it.name } else listOf())
      }

    fun applicableScopes() = listOfNotNull(
      "implementation",
      "api".takeIf { onlyImportantFor == null || onlyImportantFor == ImportantFor.MODULE },
      "compileOnly".takeIf { onlyImportantFor == null },
      "runtimeOnly".takeIf { onlyImportantFor == null },
      "annotationProcessor".takeIf { onlyImportantFor == null })

    val result = mutableListOf<String>()
    applicableArtifacts().forEach { artifact ->
      applicableScopes().forEach { scope ->
        applicableBuildTypes(artifact).forEach { buildType ->
          // configurations that are simple buildType names
          result.add(listOf(artifact, "", buildType, scope).filter { it != "" }.combineAsCamelCase())
          flavorNamesCartesianProduct().forEach { productFlavor ->
            // configurations that are complete product flavors and an optional build type
            if (onlyImportantFor == null) {
              result.add(listOf(artifact, productFlavor, buildType, scope).filter { it != "" }.combineAsCamelCase())
            }
          }
        }
        if (onlyImportantFor == null || onlyImportantFor == ImportantFor.LIBRARY) {
          productFlavors.map { it.name }.forEach { productFlavor ->
            // configurations that are simple single-dimension productFlavor names (with no build type)
            result.add(listOf(artifact, productFlavor, "", scope).filter { it != "" }.combineAsCamelCase())
          }
        }
      }
    }
    return result.toList()
  }

  fun addNewBuildType(name: String): PsBuildType = getOrCreateBuildTypeCollection().addNew(name)

  private fun computeCurrentVariantSuffixes(): Set<String> {
    return (productFlavors.map { it.name } + buildFlavorCombinations())
      .flatMap { flavor -> buildTypes.map { buildType -> listOf(flavor, buildType.name).combineAsCamelCase().usLocaleCapitalize() } }
      .toSet()
  }

  private fun buildTypeCausesAmbiguity(name: String): Boolean {
    val variantSuffixes = computeCurrentVariantSuffixes()
    val currentFlavors = productFlavors.map { it.name } + buildFlavorCombinations()
    val potential = currentFlavors.map { listOf(it, name).combineAsCamelCase().usLocaleCapitalize() }
    return potential.any { variantSuffixes.contains(it) }
  }

  fun validateBuildTypeName(name: String): String? = when {
    name.isEmpty() -> "Build type name cannot be empty."
    name.startsWith("test") -> "Build type name cannot start with 'test'."
    name.startsWith("androidTest") -> "Build type name cannot start with 'androidTest'."
    name == "main" -> "Build type name cannot be 'main'."
    name == "lint" -> "Build type name cannot be 'lint'."
    DISALLOWED_IN_NAME.indexIn(name) >= 0 -> "Build type name cannot contain any of $DISALLOWED_MESSAGE: '$name'"
    getOrCreateBuildTypeCollection().any { it.name.usLocaleCapitalize() == name.usLocaleCapitalize() } -> "Duplicate build type name: '$name'"
    getOrCreateProductFlavorCollection().any { it.name.usLocaleCapitalize() == name.usLocaleCapitalize() } -> "Build type name cannot collide with product flavor: '$name'"
    buildTypeCausesAmbiguity(name) -> "Build type name '$name' would cause a configuration name ambiguity."
    else -> null
  }

  fun removeBuildType(buildType: PsBuildType) = getOrCreateBuildTypeCollection().remove(buildType.name)

  fun addNewFlavorDimension(name: String) = getOrCreateFlavorDimensionCollection().addNew(name)

  fun validateFlavorDimensionName(name: String): String? = when {
    name.isEmpty() -> "Flavor dimension name cannot be empty."
    DISALLOWED_IN_NAME.indexIn(name) >= 0 -> "Flavor dimension name cannot contain any of $DISALLOWED_MESSAGE: '$name'"
    getOrCreateFlavorDimensionCollection().any { it.name == name } -> "Duplicate flavor dimension name: '$name'"
    else -> null
  }

  fun removeFlavorDimension(flavorDimension: PsFlavorDimension) = getOrCreateFlavorDimensionCollection().remove(flavorDimension.name)

  fun addNewProductFlavor(dimension: String, name: String): PsProductFlavor =
    getOrCreateProductFlavorCollection().addNew(PsProductFlavorKey(dimension, name))

  private fun productFlavorCausesAmbiguity(name: String, dimension: String?): Boolean {
    if (dimension == null) return false
    val variantSuffixes = computeCurrentVariantSuffixes()
    val potential = (listOf(name) + buildFlavorCombinations(name, dimension))
      .flatMap { flavor -> buildTypes.map { listOf(flavor, it.name).combineAsCamelCase().usLocaleCapitalize() } }
    return potential.any { variantSuffixes.contains(it) }
  }

  fun validateProductFlavorName(name: String, dimension: String?): String? = when {
    name.isEmpty() -> "Product flavor name cannot be empty."
    name.startsWith("test") -> "Product flavor name cannot start with 'test'."
    name.startsWith("androidTest") -> "Product flavor name cannot start with 'androidTest'."
    name == "main" -> "Product flavor name cannot be 'main'."
    name == "lint" -> "Product flavor name cannot be 'lint'."
    DISALLOWED_IN_NAME.indexIn(name) >= 0 -> "Product flavor name cannot contain any of $DISALLOWED_MESSAGE: '$name'"
    getOrCreateProductFlavorCollection().any { it.name.usLocaleCapitalize() == name.usLocaleCapitalize() } -> "Duplicate product flavor name: '$name'"
    getOrCreateBuildTypeCollection().any { it.name.usLocaleCapitalize() == name.usLocaleCapitalize() } -> "Product flavor name cannot collide with build type: '$name'"
    productFlavorCausesAmbiguity(name, dimension) ->
      "Product flavor name '$name' in flavor dimension '$dimension' would cause a configuration name ambiguity."
    else -> null
  }

  fun removeProductFlavor(productFlavor: PsProductFlavor) =
    getOrCreateProductFlavorCollection()
      .remove(PsProductFlavorKey(productFlavor.effectiveDimension.orEmpty(), productFlavor.name))

  fun addNewSigningConfig(name: String): PsSigningConfig = getOrCreateSigningConfigCollection().addNew(name)

  fun validateSigningConfigName(name: String): String? = when {
    name.isEmpty() -> "Signing config name cannot be empty."
    DISALLOWED_IN_NAME.indexIn(name) >= 0 -> "Signing config name cannot contain any of $DISALLOWED_MESSAGE: '$name'"
    getOrCreateSigningConfigCollection().any { it.name == name } -> "Duplicate signing config name: '$name'"
    else -> null
  }

  fun removeSigningConfig(signingConfig: PsSigningConfig) = getOrCreateSigningConfigCollection().remove(signingConfig.name)

  // configurations applicable to specific flavors from two or more flavorDimensions, or to one specific flavor and one
  // buildType, require an explicit configuration declaration within a configurations block in the gradle build file.
  // see e.g. https://developer.android.com/studio/build/gradle-tips#target-specific-builds-with-dependency-configurations
  @VisibleForTesting
  fun configurationRequiresWorkaround(configurationName: String): Boolean {
    fun artifactFreeConfigurationRequiresWorkaround(
      configurationName: String,
      dimensions: List<List<String>>,
      dimensionIndex: Int,
      capitalize: Boolean,
      matches: Int
    ): Boolean {
      // dimensions is a list, each element except the last of which is a list of strings naming flavours in one flavour dimension.
      // The last element is a list of strings naming build types.
      if (dimensionIndex >= dimensions.size) return false
      return when (matches) {
        0 -> dimensions[dimensionIndex]
               .any {
                 val prefix = if (capitalize) it.usLocaleCapitalize() else it
                 configurationName.startsWith(prefix) &&
                 artifactFreeConfigurationRequiresWorkaround(configurationName.removePrefix(prefix), dimensions,
                                                             dimensionIndex + 1, true,
                                                             matches + 1)
               } ||
             artifactFreeConfigurationRequiresWorkaround(configurationName, dimensions, dimensionIndex + 1, capitalize, matches)
        1 -> dimensions[dimensionIndex].any { configurationName.startsWith(if (capitalize) it.usLocaleCapitalize() else it) } ||
             artifactFreeConfigurationRequiresWorkaround(configurationName, dimensions, dimensionIndex + 1, capitalize, matches)
        else -> false
      }
    }

    return flavorDimensions.isNotEmpty() &&
           (flavorDimensions.map { flavorNamesByDimension(it.name) } + listOf(buildTypes.map { it.name })).let { dimensions ->
             when {
               configurationName.startsWith("androidTest") ->
                 artifactFreeConfigurationRequiresWorkaround(configurationName.removePrefix("androidTest"), dimensions, 0, true, 0)
               configurationName.startsWith("test") ->
                 artifactFreeConfigurationRequiresWorkaround(configurationName.removePrefix("test"), dimensions, 0, true, 0)
               else -> artifactFreeConfigurationRequiresWorkaround(configurationName, dimensions, 0, false, 0)
             }
           }
  }

  override fun maybeAddConfiguration(configurationName: String) {
    parsedModel?.let { model ->
      val configurationModels = model.configurations().all()
      if (configurationRequiresWorkaround(configurationName)) {
        if (configurationModels.indexOfFirst { it.name() == configurationName } < 0) {
          model.configurations().addConfiguration(configurationName)
        }
      }
    }
  }

  override fun maybeRemoveConfiguration(configurationName: String) {
    parsedModel?.let { model ->
      val allDependencies = (dependencies.jars + dependencies.modules + dependencies.libraries)
      if (allDependencies.filter { (it as PsDeclaredDependency).configurationName == configurationName }.size == 1) {
        val configurationModel = model.configurations().all().firstOrNull { it.name() == configurationName }
        if (configurationModel != null && configurationModel.declaredProperties.isEmpty()) {
          model.configurations().removeConfiguration(configurationName)
        }
      }
    }
  }

  private fun getOrCreateBuildTypeCollection(): PsBuildTypeCollection =
    buildTypeCollection ?: PsBuildTypeCollection(this).also { buildTypeCollection = it }

  private fun getOrCreateFlavorDimensionCollection(): PsFlavorDimensionCollection =
    flavorDimensionCollection ?: PsFlavorDimensionCollection(this).also { flavorDimensionCollection = it }

  private fun getOrCreateProductFlavorCollection(): PsProductFlavorCollection =
    productFlavorCollection ?: PsProductFlavorCollection(this).also { productFlavorCollection = it }

  private fun getOrCreateResolvedVariantCollection(): PsResolvedVariantCollection =
    resolvedVariantCollection ?: PsResolvedVariantCollection(this).also { resolvedVariantCollection = it }

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

  fun resetProductFlavors() {
    productFlavorCollection?.refresh()
    flavorDimensionCollection?.refresh()  // (invalid) dimension may appear or disappear.
  }

  internal fun resetResolvedDependencies() {
    resolvedVariants.forEach { variant -> variant.forEachArtifact { artifact -> artifact.resetDependencies() } }
  }

  private fun resetDeclaredDependencies() {
    dependencyCollection?.refresh()
  }
}
