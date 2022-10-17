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
package com.android.tools.idea.gradle.project.model

import com.android.ide.common.repository.AgpVersion
import com.android.projectmodel.DynamicResourceValue
import com.android.resources.ResourceType
import com.android.sdklib.AndroidVersion
import com.android.sdklib.devices.Abi
import com.android.tools.idea.gradle.model.IdeAaptOptions
import com.android.tools.idea.gradle.model.IdeAndroidArtifact
import com.android.tools.idea.gradle.model.IdeAndroidProject
import com.android.tools.idea.gradle.model.IdeAndroidProjectType
import com.android.tools.idea.gradle.model.IdeBuildTypeContainer
import com.android.tools.idea.gradle.model.IdeClassField
import com.android.tools.idea.gradle.model.IdeDependencies
import com.android.tools.idea.gradle.model.IdeLibraryModelResolver
import com.android.tools.idea.gradle.model.IdeProductFlavorContainer
import com.android.tools.idea.gradle.model.IdeSourceProvider
import com.android.tools.idea.gradle.model.IdeTestOptions
import com.android.tools.idea.gradle.model.IdeVariant
import com.android.tools.idea.gradle.model.IdeVariantCore
import com.android.tools.idea.gradle.model.impl.IdeVariantImpl
import com.android.tools.idea.gradle.model.variantNames
import com.android.tools.idea.model.AndroidModel
import com.android.tools.idea.model.Namespacing
import com.android.tools.idea.model.TestExecutionOption
import com.android.tools.idea.model.TestOptions
import com.android.tools.lint.client.api.LintClient.Companion.getGradleDesugaring
import com.android.tools.lint.detector.api.Desugaring
import com.google.common.collect.ImmutableMap
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.pom.java.LanguageLevel
import com.jetbrains.rd.util.getOrCreate
import org.assertj.core.util.VisibleForTesting
import org.jetbrains.android.facet.AndroidFacet
import java.io.File
import java.util.EnumSet

/**
 * Contains Android-Gradle related state necessary for configuring an IDEA project based on a user-selected build variant.
 */
class GradleAndroidModel constructor(
  val data: GradleAndroidModelData,
  val project: Project,
  private val ideLibraryModelResolver: IdeLibraryModelResolver
) : AndroidModuleModel {

  private val agpVersion: AgpVersion = AgpVersion.parse(androidProject.agpVersion) // Fail sync if the reported version cannot be parsed.

  private val myBuildTypesByName: Map<String, IdeBuildTypeContainer> = androidProject.buildTypes.associateBy { it.buildType.name }
  private val myProductFlavorsByName: Map<String, IdeProductFlavorContainer> =
    androidProject.productFlavors.associateBy { it.productFlavor.name }
  private val myCachedVariantsByName: Map<String, IdeVariantCore> = data.variants.associateBy { it.name }
  private val myCachedResolvedVariantsByName: Map<String, IdeVariant> =
    myCachedVariantsByName.mapValues { (_, value) -> IdeVariantImpl(value, ideLibraryModelResolver) }

  val features: AndroidModelFeatures = AndroidModelFeatures(agpVersion)

  val moduleName: String get() = data.moduleName
  val rootDirPath: File get() = data.rootDirPath
  val androidProject: IdeAndroidProject get() = data.androidProject
  val selectedVariantName: String get() = data.selectedVariantName
  val selectedVariant: IdeVariant get() = myCachedResolvedVariantsByName[selectedVariantName] ?: unknownSelectedVariant()
  val selectedVariantCore: IdeVariantCore get() = myCachedVariantsByName[selectedVariantName] ?: unknownSelectedVariant()

  /**
   * @return the version code associated with the merged flavor of the selected variant, or `null` if none have been set.
   */
  val versionCode: Int? get() = selectedVariant.versionCode
  val buildTypeNames: Set<String> get() = myBuildTypesByName.keys
  val productFlavorNames: Set<String> get() = myProductFlavorsByName.keys
  val variantNames: Collection<String> get() = androidProject.variantNames ?: myCachedVariantsByName.keys
  val variants: List<IdeVariant> get() = myCachedResolvedVariantsByName.values.toList()

  fun findBuildType(name: String): IdeBuildTypeContainer? = myBuildTypesByName[name]
  fun findProductFlavor(name: String): IdeProductFlavorContainer? = myProductFlavorsByName[name]
  fun findVariantByName(variantName: String): IdeVariant? = myCachedResolvedVariantsByName[variantName]

  /**
   * Returns the [IdeAndroidArtifact] that should be used for instrumented testing.
   *
   *
   * For test-only modules this is the main artifact.
   */
  fun getArtifactForAndroidTest(): IdeAndroidArtifact? {
    return when (androidProject.projectType) {
      IdeAndroidProjectType.PROJECT_TYPE_TEST -> selectedVariant.mainArtifact
      else -> selectedVariant.androidTestArtifact
    }
  }

  val selectedMainCompileDependencies: IdeDependencies get() = this.mainArtifact.compileClasspath
  val selectedMainRuntimeDependencies: IdeDependencies get() = this.mainArtifact.runtimeClasspath
  val selectedAndroidTestCompileDependencies: IdeDependencies? get() = selectedVariant.androidTestArtifact?.compileClasspath

  val mainArtifact: IdeAndroidArtifact get() = selectedVariant.mainArtifact
  val defaultSourceProvider: IdeSourceProvider get() = androidProject.defaultConfig.sourceProvider!!
  val activeSourceProviders: List<IdeSourceProvider> get() = data.activeSourceProviders
  val unitTestSourceProviders: List<IdeSourceProvider> get() = data.unitTestSourceProviders
  val androidTestSourceProviders: List<IdeSourceProvider> get() = data.androidTestSourceProviders
  val testFixturesSourceProviders: List<IdeSourceProvider> get() = data.testFixturesSourceProviders

  val allSourceProviders: List<IdeSourceProvider> get() = data.allSourceProviders
  val allUnitTestSourceProviders: List<IdeSourceProvider> get() = data.allUnitTestSourceProviders
  val allAndroidTestSourceProviders: List<IdeSourceProvider> get() = data.allAndroidTestSourceProviders
  val allTestFixturesSourceProviders: List<IdeSourceProvider> get() = data.allTestFixturesSourceProviders

  fun getJavaLanguageLevel(): LanguageLevel? = data.getJavaLanguageLevel()

  override fun getAgpVersion(): AgpVersion = agpVersion

  /**
   * Returns the current application ID.
   *
   * Returns UNINITIALIZED_APPLICATION_ID in contexts that don't have an application ID, see comment on
   * [com.android.tools.idea.gradle.model.IdeAndroidArtifactCore.applicationId]
   */
  override fun getApplicationId(): String {
    return selectedVariant.mainArtifact.applicationId ?: AndroidModel.UNINITIALIZED_APPLICATION_ID
  }

  override fun getAllApplicationIds(): Set<String> {
    return androidProject.basicVariants.mapNotNull { variant -> variant.applicationId }.toSet()
  }

  override fun isDebuggable(): Boolean {
    val buildTypeContainer = findBuildType(selectedVariant.buildType)
      ?: error("Build type ${selectedVariant.buildType} not found")
    return buildTypeContainer.buildType.isDebuggable
  }

  private val myMinSdkVersion: AndroidVersion by lazy(LazyThreadSafetyMode.PUBLICATION) {
    var minSdkVersion = selectedVariant.minSdkVersion
    if (minSdkVersion.codename != null) {
      val defaultConfigVersion = androidProject.defaultConfig.productFlavor.minSdkVersion
      if (defaultConfigVersion != null) {
        minSdkVersion = defaultConfigVersion
      }
      val flavors = selectedVariant.productFlavors
      for (flavor in flavors) {
        val productFlavor = findProductFlavor(flavor)!!
        val flavorVersion = productFlavor.productFlavor.minSdkVersion
        if (flavorVersion != null) {
          minSdkVersion = flavorVersion
          break
        }
      }
    }
    convertVersion(minSdkVersion, null)
  }

  /**
   * Returns the `minSdkVersion` specified by the user (in the default config or product flavors).
   * This is normally the merged value, but for example when using preview platforms, the Gradle plugin
   * will set minSdkVersion and targetSdkVersion to match the level of the compileSdkVersion; in this case
   * we want tools like lint's API check to continue to look for the intended minSdkVersion specified in
   * the build.gradle file
   *
   * @return the [AndroidVersion] to use for this Gradle project, or `null` if not specified.
   */
  override fun getMinSdkVersion(): AndroidVersion = myMinSdkVersion

  override fun getRuntimeMinSdkVersion(): AndroidVersion {
    val minSdkVersion = selectedVariant.minSdkVersion
    return convertVersion(minSdkVersion, null)
  }

  override fun getTargetSdkVersion(): AndroidVersion? {
    val targetSdkVersion = selectedVariant.targetSdkVersion
    return if (targetSdkVersion != null) convertVersion(targetSdkVersion, null) else null
  }

  override fun getSupportedAbis(): EnumSet<Abi> {
    return selectedVariant.mainArtifact.abiFilters.mapNotNullTo(EnumSet.noneOf(Abi::class.java)) { Abi.getEnum(it) }
  }

  private val myOverridesManifestPackage: Boolean by lazy(LazyThreadSafetyMode.PUBLICATION) {
    var result = androidProject.defaultConfig.productFlavor.applicationId != null
    if (!result) {
      val variant = selectedVariant
      val flavors = variant.productFlavors
      for (flavor in flavors) {
        val productFlavor = findProductFlavor(flavor)!!
        if (productFlavor.productFlavor.applicationId != null) {
          result = true
          break
        }
      }
    }
    result
  }

  /**
   * Returns whether this project fully overrides the manifest package (with applicationId in the
   * default config or one of the product flavors) in the current variant.
   */
  override fun overridesManifestPackage(): Boolean = myOverridesManifestPackage

  override fun getNamespacing(): Namespacing {
    return when (val namespacing = androidProject.aaptOptions.namespacing) {
      IdeAaptOptions.Namespacing.DISABLED -> Namespacing.DISABLED
      IdeAaptOptions.Namespacing.REQUIRED -> Namespacing.REQUIRED
      else -> throw IllegalStateException("Unknown namespacing option: $namespacing")
    }
  }

  override fun getDesugaring(): Set<Desugaring> {
    return getGradleDesugaring(agpVersion, getJavaLanguageLevel(), androidProject.javaCompileOptions.isCoreLibraryDesugaringEnabled)
  }

  override fun getResValues(): Map<String, DynamicResourceValue> {
    @Suppress("DEPRECATION")
    return classFieldsToDynamicResourceValues(selectedVariant.mainArtifact.resValues)
  }

  override fun getTestOptions(): TestOptions {
    val testArtifact = selectedVariant.androidTestArtifact
    val testOptions = testArtifact?.testOptions
    val executionOption: TestExecutionOption? =
      when (val execution = testOptions?.execution) {
        null -> null
        IdeTestOptions.Execution.ANDROID_TEST_ORCHESTRATOR -> TestExecutionOption.ANDROID_TEST_ORCHESTRATOR
        IdeTestOptions.Execution.ANDROIDX_TEST_ORCHESTRATOR -> TestExecutionOption.ANDROIDX_TEST_ORCHESTRATOR
        IdeTestOptions.Execution.HOST -> TestExecutionOption.HOST
        else -> throw IllegalStateException("Unknown option: $execution")
      }
    val animationsDisabled = testOptions != null && testOptions.animationsDisabled
    return TestOptions(
      executionOption,
      animationsDisabled,
      selectedVariant.testInstrumentationRunner,
      selectedVariant.testInstrumentationRunnerArguments
    )
  }

  override fun getResourcePrefix(): String? = androidProject.resourcePrefix
  override fun isBaseSplit(): Boolean = androidProject.isBaseSplit
  override fun isInstantAppCompatible(): Boolean = selectedVariant.instantAppCompatible

  private fun unknownSelectedVariant(): Nothing = error("Unknown selected variant: $selectedVariantName")

  companion object {

    @JvmStatic
    fun get(module: Module): GradleAndroidModel? = AndroidModel.get(module) as? GradleAndroidModel

    @JvmStatic
    fun get(androidFacet: AndroidFacet): GradleAndroidModel? = AndroidModel.get(androidFacet) as? GradleAndroidModel

    @JvmStatic
    fun createFactory(project: Project, libraryResolver: IdeLibraryModelResolver): (GradleAndroidModelData) -> GradleAndroidModel {
      val models = mutableMapOf<GradleAndroidModelData, GradleAndroidModel>()
      return fun(data: GradleAndroidModelData): GradleAndroidModel {
        return models.getOrCreate(data) { GradleAndroidModel(it, project, libraryResolver) }
      }
    }
  }
}

@VisibleForTesting
fun classFieldsToDynamicResourceValues(classFields: Map<String, IdeClassField>): Map<String, DynamicResourceValue> {
  val result = HashMap<String, DynamicResourceValue>()
  for (field in classFields.values) {
    val resourceType = ResourceType.fromClassName(field.type)
    if (resourceType != null) {
      result[field.name] = DynamicResourceValue(resourceType, field.value)
    }
  }
  return ImmutableMap.copyOf(result)
}
