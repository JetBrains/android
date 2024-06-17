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
import com.android.tools.idea.gradle.model.IdeArtifactName
import com.android.tools.idea.gradle.model.IdeBasicVariant
import com.android.tools.idea.gradle.model.IdeBuildTypeContainer
import com.android.tools.idea.gradle.model.IdeClassField
import com.android.tools.idea.gradle.model.IdeDependencies
import com.android.tools.idea.gradle.model.IdeJavaArtifact
import com.android.tools.idea.gradle.model.IdeLibraryModelResolver
import com.android.tools.idea.gradle.model.IdeProductFlavorContainer
import com.android.tools.idea.gradle.model.IdeSourceProvider
import com.android.tools.idea.gradle.model.IdeTestOptions
import com.android.tools.idea.gradle.model.IdeVariant
import com.android.tools.idea.gradle.model.IdeVariantCore
import com.android.tools.idea.gradle.model.impl.IdeVariantImpl
import com.android.tools.idea.gradle.model.variantNames
import com.android.tools.idea.gradle.util.BaselineProfileUtil.getGenerateBaselineProfileTaskName
import com.android.tools.idea.model.AndroidModel
import com.android.tools.idea.model.Namespacing
import com.android.tools.idea.model.TestExecutionOption
import com.android.tools.idea.model.TestOptions
import com.android.tools.idea.projectsystem.CommonTestType
import com.android.tools.idea.projectsystem.TestComponentType
import com.android.tools.lint.client.api.LintClient.Companion.getGradleDesugaring
import com.android.tools.lint.detector.api.Desugaring
import com.android.utils.usLocaleCapitalize
import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.ImmutableMap
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.jetbrains.rd.util.getOrCreate
import org.jetbrains.android.facet.AndroidFacet
import java.io.File
import java.util.EnumSet
import java.util.Locale

/**
 * Contains Android-Gradle related state necessary for configuring an IDEA project based on a user-selected build variant.
 */
class GradleAndroidModel(
  private val data: GradleAndroidModelData,
  val project: Project,
  private val ideLibraryModelResolver: IdeLibraryModelResolver
) : AndroidModel {

  private val myBuildTypesByName: Map<String, IdeBuildTypeContainer> =
    androidProject.multiVariantData?.buildTypes.orEmpty().associateBy {it.buildType.name }
  private val myProductFlavorsByName: Map<String, IdeProductFlavorContainer> =
    androidProject.multiVariantData?.productFlavors.orEmpty().associateBy { it.productFlavor.name }
  private val myCachedBasicVariantsByName: Map<String, IdeBasicVariant> =
    data.androidProject.basicVariants.associateBy { it.name }
  private val myCachedVariantsByName: Map<String, IdeVariantCore> = data.variants.associateBy { it.name }
  private val myCachedResolvedVariantsByName: Map<String, IdeVariant> =
    myCachedVariantsByName.mapValues { (_, value) -> IdeVariantImpl(value, ideLibraryModelResolver) }

  val agpVersion: AgpVersion = AgpVersion.parse(androidProject.agpVersion) // Fail sync if the reported version cannot be parsed.
  val features: AndroidModelFeatures = AndroidModelFeatures(agpVersion)
  val moduleName: String get() = data.moduleName
  val rootDirPath: File get() = data.rootDirPath
  val androidProject: IdeAndroidProject get() = data.androidProject
  val selectedVariantName: String get() = data.selectedVariantName
  val selectedBasicVariant: IdeBasicVariant get() = myCachedBasicVariantsByName[selectedVariantName] ?: unknownSelectedVariant()
  val selectedVariant: IdeVariant get() = myCachedResolvedVariantsByName[selectedVariantName] ?: unknownSelectedVariant()
  val selectedVariantCore: IdeVariantCore get() = myCachedVariantsByName[selectedVariantName] ?: unknownSelectedVariant()

  /**
   * @return the version code associated with the merged flavor of the selected variant, or `null` if none have been set.
   */
  val versionCode: Int? get() = selectedVariant.versionCode
  val buildTypeNames: Set<String> get() = myBuildTypesByName.keys
  val productFlavorNames: Set<String> get() = myProductFlavorsByName.keys
  val productFlavorNamesByFlavorDimension: Map<String, List<String>>
    get() = myProductFlavorsByName
      .mapNotNull { it.value.productFlavor.dimension?.let { dimension -> dimension to it.key } }
      .sortedBy { androidProject.flavorDimensions.indexOf(it.first) }
      .groupBy({ it.first }, { it.second })
  val variantNames: Collection<String> get() = androidProject.variantNames
  val variants: List<IdeVariant> get() = myCachedResolvedVariantsByName.values.toList()

  fun findBasicVariantByName(variantName: String): IdeBasicVariant? = myCachedBasicVariantsByName[variantName]
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
      else -> selectedVariant.deviceTestArtifacts.find { it.name == IdeArtifactName.ANDROID_TEST }
    }
  }

  /**
   * Returns the [IdeAndroidArtifact] that should be used for screenshot testing.
   *
   *
   * For screenshot test-only modules this is the main artifact.
   */
  fun getArtifactForScreenshotTest(): IdeJavaArtifact? {
    return selectedVariant.hostTestArtifacts.find { it.name == IdeArtifactName.SCREENSHOT_TEST }
  }

  fun getGradleConnectedTestTaskNameForSelectedVariant(): String {
    return selectedVariantCore.deviceTestArtifacts.find { it.name == IdeArtifactName.ANDROID_TEST }?.testOptions?.instrumentedTestTaskName
           ?: "connected${selectedVariantName.usLocaleCapitalize()}AndroidTest" // fallback for v1 models
  }

  fun getGenerateBaselineProfileTaskNameForSelectedVariant(useAllVariants: Boolean): String? {
    val variant = if (useAllVariants) "" else selectedVariantName.replaceFirstChar {
      if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
    }

    return getGenerateBaselineProfileTaskName(variant, agpVersion)
  }

  val selectedAndroidTestCompileDependencies: IdeDependencies? get() =
    selectedVariant.deviceTestArtifacts.find { it.name == IdeArtifactName.ANDROID_TEST }?.compileClasspath

  val mainArtifact: IdeAndroidArtifact get() = selectedVariant.mainArtifact
  val defaultSourceProvider: IdeSourceProvider get() = androidProject.defaultSourceProvider.sourceProvider!!
  val activeSourceProviders: List<IdeSourceProvider> get() = data.activeSourceProviders
  val hostTestSourceProviders: Map<TestComponentType.HostTest, List<IdeSourceProvider>> get() = data.hostTestSourceProviders
  val deviceTestSourceProviders: Map<TestComponentType.DeviceTest, List<IdeSourceProvider>> get() = data.deviceTestSourceProviders
  val testFixturesSourceProviders: List<IdeSourceProvider> get() = data.testFixturesSourceProviders
  val allSourceProviders: List<IdeSourceProvider> get() = data.allSourceProviders
  val allHostTestSourceProviders: Map<TestComponentType.HostTest, List<IdeSourceProvider>> get() = data.allHostTestSourceProviders
  val allDeviceTestSourceProviders: Map<TestComponentType.DeviceTest, List<IdeSourceProvider>> get() = data.allDeviceSourceProviders
  val allTestFixturesSourceProviders: List<IdeSourceProvider> get() = data.allTestFixturesSourceProviders

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
    return buildSet {
      androidProject.basicVariants.forEach { variant ->
        variant.applicationId?.let { add(it) }
        variant.testApplicationId?.let { add(it) }
      }
    }
  }

  override fun isDebuggable(): Boolean {
    // TODO(b/288091803): Figure out if kotlin multiplatform android modules should be marked debuggable
    if (androidProject.projectType == IdeAndroidProjectType.PROJECT_TYPE_KOTLIN_MULTIPLATFORM) {
      return true
    }

    val buildTypeContainer = myBuildTypesByName[selectedVariant.buildType]
      ?: error("Build type ${selectedVariant.buildType} not found")
    return buildTypeContainer.buildType.isDebuggable
  }

  fun getBuildType(variant: IdeBasicVariant): IdeBuildTypeContainer {
    return myBuildTypesByName[variant.buildType] ?: error("Build type ${variant.buildType} not found")
  }

  private val myMinSdkVersion: AndroidVersion by lazy(LazyThreadSafetyMode.PUBLICATION) {
    var minSdkVersion = selectedVariant.minSdkVersion
    if (minSdkVersion.codename != null) {
      val defaultConfigVersion = androidProject.multiVariantData?.defaultConfig?.minSdkVersion
      if (defaultConfigVersion != null) {
        minSdkVersion = defaultConfigVersion
      }
      val flavors = selectedVariant.productFlavors
      for (flavor in flavors) {
        val productFlavor = myProductFlavorsByName[flavor]!!
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
    var result = androidProject.multiVariantData?.defaultConfig?.applicationId != null
    if (!result) {
      val variant = selectedVariant
      val flavors = variant.productFlavors
      for (flavor in flavors) {
        val productFlavor = myProductFlavorsByName[flavor]!!
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
    return getGradleDesugaring(agpVersion, data.getJavaLanguageLevel(), androidProject.javaCompileOptions.isCoreLibraryDesugaringEnabled)
  }

  override fun getResValues(): Map<String, DynamicResourceValue> {
    return classFieldsToDynamicResourceValues(selectedVariant.resValues)
  }

  override fun getTestOptions(): TestOptions {
    val testArtifact = selectedVariant.deviceTestArtifacts.find { it.name == IdeArtifactName.ANDROID_TEST }
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

  @VisibleForTesting
  fun containsTheSameDataAs(that: GradleAndroidModel) = this.data == that.data

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
