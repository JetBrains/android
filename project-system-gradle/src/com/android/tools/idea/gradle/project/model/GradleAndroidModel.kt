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
import com.android.tools.idea.gradle.model.IdeAndroidArtifactCore
import com.android.tools.idea.gradle.model.IdeAndroidProject
import com.android.tools.idea.gradle.model.IdeAndroidProjectType
import com.android.tools.idea.gradle.model.IdeArtifactName
import com.android.tools.idea.gradle.model.IdeBasicVariant
import com.android.tools.idea.gradle.model.IdeBuildTypeContainer
import com.android.tools.idea.gradle.model.IdeClassField
import com.android.tools.idea.gradle.model.IdeDeclaredDependencies
import com.android.tools.idea.gradle.model.IdeDependencies
import com.android.tools.idea.gradle.model.IdeJavaArtifact
import com.android.tools.idea.gradle.model.IdeLibraryModelResolver
import com.android.tools.idea.gradle.model.IdeSourceProvider
import com.android.tools.idea.gradle.model.IdeTestOptions
import com.android.tools.idea.gradle.model.IdeVariant
import com.android.tools.idea.gradle.model.IdeVariantCore
import com.android.tools.idea.gradle.model.filteredVariantNames
import com.android.tools.idea.gradle.model.impl.IdeAndroidArtifactCoreImpl
import com.android.tools.idea.gradle.model.impl.IdeAndroidArtifactImpl
import com.android.tools.idea.gradle.model.impl.IdeAndroidProjectImpl
import com.android.tools.idea.gradle.model.impl.IdeBasicVariantImpl
import com.android.tools.idea.gradle.model.impl.IdeBuildTypeContainerImpl
import com.android.tools.idea.gradle.model.impl.IdeDeclaredDependenciesImpl
import com.android.tools.idea.gradle.model.impl.IdeJavaArtifactImpl
import com.android.tools.idea.gradle.model.impl.IdeLibraryModelResolverImpl
import com.android.tools.idea.gradle.model.impl.IdeProductFlavorContainerImpl
import com.android.tools.idea.gradle.model.impl.IdeVariantCoreImpl
import com.android.tools.idea.gradle.model.impl.IdeVariantImpl
import com.android.tools.idea.gradle.util.BaselineProfileUtil.getGenerateBaselineProfileTaskName
import com.android.tools.idea.model.AndroidModel
import com.android.tools.idea.model.Namespacing
import com.android.tools.idea.model.TestExecutionOption
import com.android.tools.idea.model.TestOptions
import com.android.tools.idea.projectsystem.TestComponentType
import com.android.tools.lint.client.api.LintClient.Companion.getGradleDesugaring
import com.android.tools.lint.detector.api.Desugaring
import com.android.utils.usLocaleCapitalize
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.pom.java.LanguageLevel
import com.jetbrains.rd.util.getOrCreate
import org.jetbrains.android.facet.AndroidFacet
import java.io.File
import java.util.EnumSet
import java.util.Locale
import org.jetbrains.annotations.VisibleForTesting

/**
 * Contains Android-Gradle related state necessary for configuring an IDEA project based on a user-selected build variant.
 */
open class GradleAndroidModelImpl(
  val data: GradleAndroidModelData,
) : GradleAndroidModel {
  constructor(other: GradleAndroidModelImpl) : this(other.data)
  // Need to be initialized here
  private val myBuildTypesByName: Map<String, IdeBuildTypeContainerImpl> =
    androidProject.multiVariantData?.buildTypes.orEmpty().associateBy { it.buildType.name }
  private val myProductFlavorsByName: Map<String, IdeProductFlavorContainerImpl> =
    androidProject.multiVariantData?.productFlavors.orEmpty().associateBy { it.productFlavor.name }
  private val myCachedBasicVariantsByName: Map<String, IdeBasicVariantImpl> =
    data.androidProject.basicVariants.associateBy { it.name }
  private val myCachedVariantsByName: Map<String, IdeVariantCoreImpl> = data.variants.associateBy { it.name }

  override val agpVersion: AgpVersion = AgpVersion.parse(androidProject.agpVersion) // Fail sync if the reported version cannot be parsed.
  override val features: AndroidModelFeatures = AndroidModelFeatures(agpVersion)
  override val moduleName: String get() = data.moduleName
  override val rootDirPath: File get() = data.rootDirPath
  override val androidProject: IdeAndroidProjectImpl get() = data.androidProject
  override val declaredDependencies: IdeDeclaredDependenciesImpl get() = data.declaredDependencies
  override val selectedVariantName: String get() = data.selectedVariantName
  override val selectedBasicVariant: IdeBasicVariantImpl get() = myCachedBasicVariantsByName[selectedVariantName] ?: unknownSelectedVariant()
  override val selectedVariant: IdeVariantCoreImpl get() = myCachedVariantsByName[selectedVariantName] ?: unknownSelectedVariant()

  /**
   * @return the version code associated with the merged flavor of the selected variant, or `null` if none have been set.
   */
  override val versionCode: Int? get() = selectedVariant.versionCode
  override val buildTypeNames: Set<String> get() = myBuildTypesByName.keys
  override val productFlavorNames: Set<String> get() = myProductFlavorsByName.keys
  override val productFlavorNamesByFlavorDimension: Map<String, List<String>>
    get() = myProductFlavorsByName
      .mapNotNull { it.value.productFlavor.dimension?.let { dimension -> dimension to it.key } }
      .sortedBy { androidProject.flavorDimensions.indexOf(it.first) }
      .groupBy({ it.first }, { it.second })
  override val filteredVariantNames: List<String> get() = androidProject.filteredVariantNames.toList()
  override val variants: List<IdeVariantCoreImpl> get() = myCachedVariantsByName.values.toList()
  override val filteredDebuggableVariants: Set<String> get() =
    androidProject.basicVariants.mapNotNull {
      if (myBuildTypesByName[it.buildType]?.buildType?.isDebuggable == true && !it.hideInStudio ) it.name else null
    }.toSet()
  override fun findBasicVariantByName(variantName: String): IdeBasicVariant? = myCachedBasicVariantsByName[variantName]
  override fun findVariantByName(variantName: String): IdeVariantCoreImpl? = myCachedVariantsByName[variantName]



  override fun getArtifactCoreForAndroidTest(): IdeAndroidArtifactCoreImpl? {
    return when (androidProject.projectType) {
      IdeAndroidProjectType.PROJECT_TYPE_TEST -> selectedVariant.mainArtifact
      else -> selectedVariant.deviceTestArtifacts.find { it.name == IdeArtifactName.ANDROID_TEST }
    }
  }


  override fun getGradleConnectedTestTaskNameForSelectedVariant(): String {
    return selectedVariant.deviceTestArtifacts.find { it.name == IdeArtifactName.ANDROID_TEST }?.testOptions?.instrumentedTestTaskName
           ?: "connected${selectedVariantName.usLocaleCapitalize()}AndroidTest" // fallback for v1 models
  }

  /**
   * Returns the name of the Gradle screenshot test task name for the selected mode.
   * TODO: Remove this method once a generic test suite support is ready b/394598774
   *
   * @param mode - can be "update" or "validate" for the two modes of screenshot test tasks.
   * @return The name of the Gradle screenshot test task.
   */
  override fun getGradleScreenshotTestTaskNameForSelectedVariant(mode: String): String {
    return "$mode${selectedVariantName.usLocaleCapitalize()}ScreenshotTest"
  }

  override fun getGenerateBaselineProfileTaskNameForSelectedVariant(useAllVariants: Boolean): String? {
    val variant = if (useAllVariants) "" else selectedVariantName.replaceFirstChar {
      if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
    }

    return getGenerateBaselineProfileTaskName(variant, agpVersion)
  }

  override val defaultSourceProvider: IdeSourceProvider? get() = androidProject.defaultSourceProvider.sourceProvider
  override val activeSourceProviders: List<IdeSourceProvider> get() = data.activeSourceProviders
  override val hostTestSourceProviders: Map<TestComponentType.HostTest, List<IdeSourceProvider>> get() = data.hostTestSourceProviders
  override val deviceTestSourceProviders: Map<TestComponentType.DeviceTest, List<IdeSourceProvider>> get() = data.deviceTestSourceProviders
  override val testFixturesSourceProviders: List<IdeSourceProvider> get() = data.testFixturesSourceProviders
  override val allSourceProviders: List<IdeSourceProvider> get() = data.allSourceProviders
  override val allHostTestSourceProviders: Map<TestComponentType.HostTest, List<IdeSourceProvider>> get() = data.allHostTestSourceProviders
  override val allDeviceTestSourceProviders: Map<TestComponentType.DeviceTest, List<IdeSourceProvider>> get() = data.allDeviceSourceProviders
  override val allTestFixturesSourceProviders: List<IdeSourceProvider> get() = data.allTestFixturesSourceProviders
  override val mainArtifact: IdeAndroidArtifactCoreImpl get() = selectedVariant.mainArtifact

  /**
   * Returns the current application ID.
   *
   * Returns UNINITIALIZED_APPLICATION_ID in contexts that don't have an application ID, see comment on
   * [com.android.tools.idea.gradle.model.IdeAndroidArtifactCore.applicationId]
   */
  override val applicationId get() =
    selectedVariant.mainArtifact.applicationId ?: AndroidModel.UNINITIALIZED_APPLICATION_ID

  override val allApplicationIds: Set<String>
    get() = buildSet {
      androidProject.basicVariants.forEach { variant ->
        variant.applicationId?.let { add(it) }
        variant.testApplicationId?.let { add(it) }
      }
    }

  override val isDebuggable: Boolean
    get() {
    // TODO(b/288091803): Figure out if kotlin multiplatform android modules should be marked debuggable
    if (androidProject.projectType == IdeAndroidProjectType.PROJECT_TYPE_KOTLIN_MULTIPLATFORM) {
      return true
    }

    val buildTypeContainer = myBuildTypesByName[selectedVariant.buildType]
      ?: error("Build type ${selectedVariant.buildType} not found")
    return buildTypeContainer.buildType.isDebuggable
  }

  override fun getBuildType(variant: IdeBasicVariant): IdeBuildTypeContainer? {
    return variant.buildType?.let { myBuildTypesByName[it] }
  }

  @Transient
  private var minSdkVersionField: AndroidVersion? = null


  /**
   * Returns the JVM `targetCompatibility` for the module.
   */
  override fun getTargetLanguageLevel(): LanguageLevel? = data.getJavaTargetLanguageLevel()

  /**
   * Returns the `minSdkVersion` specified by the user (in the default config or product flavors).
   * This is normally the merged value, but for example when using preview platforms, the Gradle plugin
   * will set minSdkVersion and targetSdkVersion to match the level of the compileSdkVersion; in this case
   * we want tools like lint's API check to continue to look for the intended minSdkVersion specified in
   * the build.gradle file
   *
   * @return the [AndroidVersion] to use for this Gradle project, or `null` if not specified.
   */
  override val minSdkVersion: AndroidVersion
    get() = synchronized(this) {
      minSdkVersionField ?: run {
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
      }.also {
        minSdkVersionField = it
      }
    }

  override val runtimeMinSdkVersion: AndroidVersion
    get() {
      val minSdkVersion = selectedVariant.minSdkVersion
      return convertVersion(minSdkVersion, null)
    }

  override val targetSdkVersion: AndroidVersion?
    get() {
      val targetSdkVersion = selectedVariant.targetSdkVersion
      return if (targetSdkVersion != null) convertVersion(targetSdkVersion, null) else null
    }

  override val supportedAbis: EnumSet<Abi>
    get() = selectedVariant.mainArtifact.abiFilters
      .mapNotNullTo(EnumSet.noneOf(Abi::class.java)) { Abi.getEnum(it) }

  @Transient
  private var overridesManifestPackageField: Boolean? = null

  /**
   * Returns whether this project fully overrides the manifest package (with applicationId in the
   * default config or one of the product flavors) in the current variant.
   */
  override fun overridesManifestPackage(): Boolean = synchronized(this) {
    overridesManifestPackageField ?: run {
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
    }.also {
      overridesManifestPackageField = it
    }
  }


  override val namespacing: Namespacing
    get() =
       when (androidProject.aaptOptions.namespacing) {
         IdeAaptOptions.Namespacing.DISABLED -> Namespacing.DISABLED
         IdeAaptOptions.Namespacing.REQUIRED -> Namespacing.REQUIRED
       }

  override val desugaring: Set<Desugaring>
    get() = getGradleDesugaring(
        agpVersion, data.getJavaSourceLanguageLevel(), androidProject.javaCompileOptions?.isCoreLibraryDesugaringEnabled == true
      )


  override val resValues: Map<String, DynamicResourceValue>
    get() = classFieldsToDynamicResourceValues(selectedVariant.resValues)

  override val testOptions: TestOptions
    get() {
    val testArtifact = selectedVariant.deviceTestArtifacts.find { it.name == IdeArtifactName.ANDROID_TEST }
    val testOptions = testArtifact?.testOptions
    val executionOption: TestExecutionOption? =
      when (testOptions?.execution) {
        null -> null
        IdeTestOptions.Execution.ANDROID_TEST_ORCHESTRATOR -> TestExecutionOption.ANDROID_TEST_ORCHESTRATOR
        IdeTestOptions.Execution.ANDROIDX_TEST_ORCHESTRATOR -> TestExecutionOption.ANDROIDX_TEST_ORCHESTRATOR
        IdeTestOptions.Execution.HOST -> TestExecutionOption.HOST
      }
    val animationsDisabled = testOptions != null && testOptions.animationsDisabled
    return TestOptions(
      executionOption,
      animationsDisabled,
      selectedVariant.testInstrumentationRunner,
      selectedVariant.testInstrumentationRunnerArguments
    )
  }

  override val resourcePrefix: String?
    get() = androidProject.resourcePrefix
  override val isBaseSplit: Boolean
    get() = androidProject.isBaseSplit
  override val isInstantAppCompatible: Boolean
    get() = selectedVariant.instantAppCompatible

  @VisibleForTesting
  fun containsTheSameDataAs(that: GradleAndroidModel) = data == (that as? GradleAndroidModelImpl)?.data
}

@VisibleForTesting
class GradleAndroidDependencyModelImpl(
  val gradleAndroidModel: GradleAndroidModelImpl,
  private val ideLibraryModelResolver: IdeLibraryModelResolverImpl
): GradleAndroidDependencyModel, GradleAndroidModelImpl(gradleAndroidModel) {
  private val myCachedResolvedVariantsByName: Map<String, IdeVariantImpl> =
    variants.associate { it.name to IdeVariantImpl(it, ideLibraryModelResolver) }
  override val selectedVariantWithDependencies: IdeVariantImpl get () = myCachedResolvedVariantsByName[selectedVariantName] ?: unknownSelectedVariant()
  override val variantsWithDependencies: List<IdeVariantImpl>
    get() = myCachedResolvedVariantsByName.values.toList()
  /** Returns the artifact used for instrumented testing. For test-only modules this is the main artifact. */
  override fun getArtifactForAndroidTest(): IdeAndroidArtifactImpl? {
    return when (androidProject.projectType) {
      IdeAndroidProjectType.PROJECT_TYPE_TEST -> selectedVariantWithDependencies.mainArtifact
      else -> selectedVariantWithDependencies.deviceTestArtifacts.find { it.name == IdeArtifactName.ANDROID_TEST }
    }
  }
  /** Returns the artifact used for screenshot testing. For screenshot test-only modules this is the main artifact. */
  override fun getArtifactForScreenshotTest(): IdeJavaArtifactImpl? {
    return selectedVariantWithDependencies.hostTestArtifacts.find { it.name == IdeArtifactName.SCREENSHOT_TEST }
  }

  override val selectedAndroidTestCompileDependencies: IdeDependencies? get() = getArtifactForAndroidTest()?.compileClasspath

  override val mainArtifactWithDependencies: IdeAndroidArtifactImpl get() = selectedVariantWithDependencies.mainArtifact

  @VisibleForTesting
  override fun containsTheSameDataAs(that: GradleAndroidDependencyModel) = gradleAndroidModel.containsTheSameDataAs((that as GradleAndroidDependencyModelImpl).gradleAndroidModel)
}

private fun GradleAndroidModel.unknownSelectedVariant(): Nothing = error("Unknown selected variant: $selectedVariantName")

sealed interface GradleAndroidModel: AndroidModel {
  companion object {
    @JvmStatic
    fun get(module: Module): GradleAndroidModel? = AndroidModel.get(module) as? GradleAndroidModel

    @JvmStatic
    fun get(androidFacet: AndroidFacet): GradleAndroidModel? = AndroidModel.get(androidFacet) as? GradleAndroidModel

    @JvmStatic
    fun create(project: Project, data: GradleAndroidModelData): GradleAndroidModel =
      GradleAndroidModelImpl(data)
  }

  val androidProject: IdeAndroidProject
  val agpVersion: AgpVersion
  val features: AndroidModelFeatures
  val moduleName: String
  val rootDirPath: File
  val declaredDependencies: IdeDeclaredDependencies
  val selectedVariantName: String
  val versionCode: Int?
  val buildTypeNames: Set<String>
  val selectedBasicVariant: IdeBasicVariant
  val productFlavorNames: Set<String>
  val productFlavorNamesByFlavorDimension: Map<String, List<String>>
  val filteredVariantNames: Collection<String>
  val defaultSourceProvider: IdeSourceProvider?
  val activeSourceProviders: List<IdeSourceProvider>
  val hostTestSourceProviders: Map<TestComponentType.HostTest, List<IdeSourceProvider>>
  val deviceTestSourceProviders: Map<TestComponentType.DeviceTest, List<IdeSourceProvider>>
  val testFixturesSourceProviders: List<IdeSourceProvider>
  val allSourceProviders: List<IdeSourceProvider>
  val allHostTestSourceProviders: Map<TestComponentType.HostTest, List<IdeSourceProvider>>
  val allDeviceTestSourceProviders: Map<TestComponentType.DeviceTest, List<IdeSourceProvider>>
  val allTestFixturesSourceProviders: List<IdeSourceProvider>
  fun findBasicVariantByName(variantName: String): IdeBasicVariant?
  fun getGradleScreenshotTestTaskNameForSelectedVariant(mode: String): String
  fun getGenerateBaselineProfileTaskNameForSelectedVariant(useAllVariants: Boolean): String?
  fun getBuildType(variant: IdeBasicVariant): IdeBuildTypeContainer?
  fun getTargetLanguageLevel(): LanguageLevel?
  val filteredDebuggableVariants: Set<String>
  val selectedVariant: IdeVariantCore
  val variants: List<IdeVariantCore>
  fun findVariantByName(variantName: String): IdeVariantCore?
  fun getArtifactCoreForAndroidTest(): IdeAndroidArtifactCore?
  fun getGradleConnectedTestTaskNameForSelectedVariant(): String
  val mainArtifact: IdeAndroidArtifactCore
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
  return result
}


sealed interface GradleAndroidDependencyModel: GradleAndroidModel {
  companion object {
    @JvmStatic
    fun get(module: Module): GradleAndroidDependencyModel? = AndroidModel.get(
      module) as? GradleAndroidDependencyModel

    @JvmStatic
    fun get(androidFacet: AndroidFacet): GradleAndroidDependencyModel? = AndroidModel.get(
      androidFacet) as? GradleAndroidDependencyModel

    @JvmStatic
    fun createFactory(project: Project, libraryResolver: IdeLibraryModelResolver): (GradleAndroidModelData) -> GradleAndroidDependencyModel {
      val models = mutableMapOf<GradleAndroidModelData, GradleAndroidDependencyModel>()
      return fun(data: GradleAndroidModelData): GradleAndroidDependencyModel {
        return models.getOrCreate(data) { GradleAndroidDependencyModelImpl(GradleAndroidModel.create(project, data) as GradleAndroidModelImpl,
                                                                           libraryResolver as IdeLibraryModelResolverImpl) }
      }
    }
  }
  fun getArtifactForScreenshotTest(): IdeJavaArtifact?
  val selectedAndroidTestCompileDependencies: IdeDependencies?
  val mainArtifactWithDependencies: IdeAndroidArtifact
  val selectedVariantWithDependencies: IdeVariant
  val variantsWithDependencies: List<IdeVariant>
  fun getArtifactForAndroidTest(): IdeAndroidArtifact?

  @VisibleForTesting
  fun containsTheSameDataAs(gradleAndroidModel: GradleAndroidDependencyModel): Boolean
}
