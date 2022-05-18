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

import com.android.annotations.concurrency.GuardedBy
import com.android.ide.common.repository.GradleVersion
import com.android.projectmodel.DynamicResourceValue
import com.android.sdklib.AndroidVersion
import com.android.sdklib.devices.Abi
import com.android.tools.idea.gradle.model.IdeAaptOptions
import com.android.tools.idea.gradle.model.IdeAndroidArtifact
import com.android.tools.idea.gradle.model.IdeAndroidArtifactCore
import com.android.tools.idea.gradle.model.IdeAndroidProject
import com.android.tools.idea.gradle.model.IdeAndroidProjectType
import com.android.tools.idea.gradle.model.IdeArtifactName
import com.android.tools.idea.gradle.model.IdeBuildTasksAndOutputInformation
import com.android.tools.idea.gradle.model.IdeBuildTypeContainer
import com.android.tools.idea.gradle.model.IdeDependencies
import com.android.tools.idea.gradle.model.IdeLibraryModelResolver
import com.android.tools.idea.gradle.model.IdeProductFlavorContainer
import com.android.tools.idea.gradle.model.IdeSourceProvider
import com.android.tools.idea.gradle.model.IdeTestOptions
import com.android.tools.idea.gradle.model.IdeVariant
import com.android.tools.idea.gradle.model.IdeVariantCore
import com.android.tools.idea.gradle.model.impl.IdeVariantCoreImpl
import com.android.tools.idea.gradle.model.impl.IdeVariantImpl
import com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys
import com.android.tools.idea.gradle.util.GenericBuiltArtifactsWithTimestamp
import com.android.tools.idea.gradle.util.GenericBuiltArtifactsWithTimestamp.Companion.mostRecentNotNull
import com.android.tools.idea.gradle.util.GradleUtil
import com.android.tools.idea.gradle.util.LastBuildOrSyncService
import com.android.tools.idea.gradle.util.OutputType
import com.android.tools.idea.gradle.util.getOutputListingFile
import com.android.tools.idea.gradle.util.loadBuildOutputListingFile
import com.android.tools.idea.gradle.util.variantOutputInformation
import com.android.tools.idea.model.AndroidModel
import com.android.tools.idea.model.Namespacing
import com.android.tools.idea.model.TestExecutionOption
import com.android.tools.idea.model.TestOptions
import com.android.tools.lint.client.api.LintClient.Companion.getGradleDesugaring
import com.android.tools.lint.detector.api.Desugaring
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.pom.java.LanguageLevel
import com.intellij.serialization.PropertyMapping
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData
import java.io.File
import java.util.EnumSet

/**
 * Contains Android-Gradle related state necessary for configuring an IDEA project based on a user-selected build variant.
 */
class GradleAndroidModel @PropertyMapping(
  "myAndroidSyncVersion",
  "myModuleName",
  "rootDirPath",
  "androidProject",
  "myCachedVariantsByName",
  "mySelectedVariantName"
) constructor(
  androidSyncVersion: String,
  moduleName: String,
  rootDirPath: File,
  androidProject: IdeAndroidProject,
  cachedVariantsByName: Map<String, IdeVariantCoreImpl>,
  variantName: String
) : AndroidModuleModel {
  @Transient
  private var myModule: Module? = null

  @Transient
  private var myIdeLibraryModelResolver: IdeLibraryModelResolver? = null

  @Transient
  private var myCachedResolvedVariantsByName: Map<String, IdeVariant>? = null

  @Transient
  val features: AndroidModelFeatures

  @Transient
  private val myAgpVersion: GradleVersion

  @Transient
  private var myMinSdkVersion: AndroidVersion? = null

  @Transient
  private val myBuildTypesByName: Map<String, IdeBuildTypeContainer>

  @Transient
  private val myProductFlavorsByName: Map<String, IdeProductFlavorContainer>

  @Transient
  private var myOverridesManifestPackage: Boolean? = null

  @GuardedBy("myGenericBuiltArtifactsMap")
  @Transient
  private val myGenericBuiltArtifactsMap: MutableMap<String, GenericBuiltArtifactsWithTimestamp>

  val projectSystemId: ProjectSystemId

  private val myAndroidSyncVersion: String
  private val myModuleName: String

  /**
   * @return the path of the root directory of the imported Android-Gradle project. The returned path belongs to the IDEA module containing
   * the build.gradle file.
   */
  val rootDirPath: File
  val androidProject: IdeAndroidProject

  private val myCachedVariantsByName: Map<String, IdeVariantCoreImpl>
  private val mySelectedVariantName: String

  init {
    require(androidSyncVersion == ourAndroidSyncVersion) {
      String.format(
        "Attempting to deserialize a model of incompatible version (%s)",
        androidSyncVersion
      )
    }
    myAndroidSyncVersion = ourAndroidSyncVersion
    projectSystemId = GradleUtil.GRADLE_SYSTEM_ID
    myModuleName = moduleName
    this.rootDirPath = rootDirPath
    this.androidProject = androidProject
    myCachedVariantsByName = cachedVariantsByName
    mySelectedVariantName = variantName
    myAgpVersion =
      GradleVersion.parseAndroidGradlePluginVersion(androidProject.agpVersion) // Fail sync if the reported version cannot be parsed.
    features = AndroidModelFeatures(myAgpVersion)
    myBuildTypesByName = androidProject.buildTypes.associateBy { it.buildType.name }
    myProductFlavorsByName = androidProject.productFlavors.associateBy { it.productFlavor.name }
    myGenericBuiltArtifactsMap = HashMap()
  }

  /**
   * Sets the IDE module this model is for, this should always be set on creation or re-attachment of the module to the project.
   * @param module
   */
  fun setModuleAndResolver(module: Module, resolver: IdeLibraryModelResolver) {
    myModule = module
    setResolver(resolver)
  }

  /**
   * Sets the IDE module this model is for, this should always be set on creation or re-attachment of the module to the project.
   */
  fun setResolver(resolver: IdeLibraryModelResolver) {
    myIdeLibraryModelResolver = resolver
    myCachedResolvedVariantsByName = myCachedVariantsByName.mapValues { (_, value) -> IdeVariantImpl(value, resolver) }
  }

  override fun getAgpVersion(): GradleVersion = myAgpVersion

  override fun getApplicationId(): String {
    @Suppress("DEPRECATION")
    return if (features.isBuildOutputFileSupported) {
      getApplicationIdUsingCache(mySelectedVariantName)
    } else selectedVariant.mainArtifact.applicationId
  }

  override fun getAllApplicationIds(): Set<String> {
    val ids: MutableSet<String> = HashSet()
    for (variant in variants) {
      val applicationId = getApplicationIdUsingCache(variant.name)
      if (AndroidModel.UNINITIALIZED_APPLICATION_ID != applicationId) {
        ids.add(applicationId)
      }
    }
    return ids
  }

  override fun isDebuggable(): Boolean {
    val buildTypeContainer = findBuildType(selectedVariant.buildType)
      ?: error("Build type ${selectedVariant.buildType} not found")
    return buildTypeContainer.buildType.isDebuggable
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
  override fun getMinSdkVersion(): AndroidVersion {
    if (myMinSdkVersion == null) {
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
      myMinSdkVersion = convertVersion(minSdkVersion, null)
    }
    return myMinSdkVersion!!
  }

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

  /**
   * Returns whether this project fully overrides the manifest package (with applicationId in the
   * default config or one of the product flavors) in the current variant.
   */
  override fun overridesManifestPackage(): Boolean {
    if (myOverridesManifestPackage == null) {
      myOverridesManifestPackage = androidProject.defaultConfig.productFlavor.applicationId != null
      val variant = selectedVariant
      val flavors = variant.productFlavors
      for (flavor in flavors) {
        val productFlavor = findProductFlavor(flavor)!!
        if (productFlavor.productFlavor.applicationId != null) {
          myOverridesManifestPackage = true
          break
        }
      }
      // The build type can specify a suffix, but it will be merged with the manifest
      // value if not specified in a flavor/default config, so only flavors count
    }
    return myOverridesManifestPackage ?: false
  }

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

  override fun getModuleName(): String = myModuleName

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

  /**
   * @return the version code associated with the merged flavor of the selected variant, or `null` if none have been set.
   */
  val versionCode: Int?
    get() = selectedVariant.versionCode

  fun findBuildType(name: String): IdeBuildTypeContainer? = myBuildTypesByName[name]

  val buildTypes: Set<String>
    get() = myBuildTypesByName.keys
  val productFlavors: Set<String>
    get() = myProductFlavorsByName.keys

  fun findProductFlavor(name: String): IdeProductFlavorContainer? = myProductFlavorsByName[name]

  /**
   * @return the selected build variant.
   */
  val selectedVariant: IdeVariant
    get() {
      return (myCachedResolvedVariantsByName ?: error("Module dependencies are not yet resolved."))[mySelectedVariantName]!!
    }

  /**
   * @return the selected build variant.
   */
  val selectedVariantCore: IdeVariantCore
    get() = myCachedVariantsByName[mySelectedVariantName] ?: error("Unknown selected variant: $mySelectedVariantName")

  val selectedVariantName: String
    get() = mySelectedVariantName

  val variants: List<IdeVariant>
    get() = (myCachedResolvedVariantsByName ?: error("Module dependencies are not yet resolved.")).values.toList()

  fun findVariantByName(variantName: String): IdeVariant? {
    return (myCachedResolvedVariantsByName ?: error("Module dependencies are not yet resolved."))[variantName]
  }

  fun findVariantCoreByName(variantName: String): IdeVariantCore? = myCachedVariantsByName[variantName]
  fun getBuildTypeNames(): Collection<String> = myBuildTypesByName.keys
  fun getProductFlavorNames(): Collection<String> = myProductFlavorsByName.keys

  val variantNames: Collection<String>
    get() = androidProject.variantNames ?: myCachedVariantsByName.keys

  fun getJavaLanguageLevel(): LanguageLevel? {
    val compileOptions = androidProject.javaCompileOptions
    val sourceCompatibility = compileOptions.sourceCompatibility
    return LanguageLevel.parse(sourceCompatibility)
  }

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

  fun getArtifactForTestFixtures(): IdeAndroidArtifact? = selectedVariant.testFixturesArtifact

  fun getTestExecutionStrategy(): IdeTestOptions.Execution? = getArtifactForAndroidTest()?.testOptions?.execution

  val selectedMainCompileDependencies: IdeDependencies
    get() = this.mainArtifact.compileClasspath

  val selectedMainRuntimeDependencies: IdeDependencies
    get() = this.mainArtifact.runtimeClasspath

  val selectedAndroidTestCompileDependencies: IdeDependencies?
    get() = selectedVariant.androidTestArtifact?.compileClasspath

  val mainArtifact: IdeAndroidArtifact
    get() = selectedVariant.mainArtifact
  val mainArtifactCore: IdeAndroidArtifactCore
    get() = selectedVariantCore.mainArtifact
  val defaultSourceProvider: IdeSourceProvider
    get() = androidProject.defaultConfig.sourceProvider!!
  val activeSourceProviders: List<IdeSourceProvider>
    get() = collectMainSourceProviders(selectedVariantCore)
  val unitTestSourceProviders: List<IdeSourceProvider>
    get() = collectUnitTestSourceProviders(selectedVariantCore)
  val androidTestSourceProviders: List<IdeSourceProvider>
    get() = collectAndroidTestSourceProviders(selectedVariantCore)
  val testFixturesSourceProviders: List<IdeSourceProvider>
    get() = collectTestFixturesSourceProviders(selectedVariantCore)

  val allSourceProviders: List<IdeSourceProvider>
    get() = collectAllSourceProviders()
  val allUnitTestSourceProviders: List<IdeSourceProvider>
    get() = collectAllUnitTestSourceProviders()
  val allAndroidTestSourceProviders: List<IdeSourceProvider>
    get() = collectAllAndroidTestSourceProviders()
  val allTestFixturesSourceProviders: List<IdeSourceProvider>
    get() = collectAllTestFixturesSourceProviders()

  fun getTestSourceProviders(artifactName: IdeArtifactName): List<IdeSourceProvider> {
    return when (artifactName) {
      IdeArtifactName.ANDROID_TEST -> collectAndroidTestSourceProviders(selectedVariantCore)
      IdeArtifactName.UNIT_TEST -> collectUnitTestSourceProviders(selectedVariantCore)
      IdeArtifactName.MAIN -> emptyList()
      IdeArtifactName.TEST_FIXTURES -> emptyList()
    }
  }

  private fun getApplicationIdUsingCache(variantName: String): String {
    val variantOutputInformation = androidProject.variantsBuildInformation.variantOutputInformation(variantName)
      ?: return AndroidModel.UNINITIALIZED_APPLICATION_ID
    return getApplicationIdUsingCache(variantOutputInformation)
  }

  fun getApplicationIdUsingCache(variantOutputInformation: IdeBuildTasksAndOutputInformation): String {
    val (genericBuiltArtifacts) = mostRecentNotNull(
      getGenericBuiltArtifactsWithTimestamp(variantOutputInformation, OutputType.Apk),
      getGenericBuiltArtifactsWithTimestamp(variantOutputInformation, OutputType.ApkFromBundle)
    ) ?: return AndroidModel.UNINITIALIZED_APPLICATION_ID
    val (_, _, applicationId) = genericBuiltArtifacts
      ?: return AndroidModel.UNINITIALIZED_APPLICATION_ID
    return applicationId
  }

  private fun getGenericBuiltArtifactsWithTimestamp(
    variantOutputInformation: IdeBuildTasksAndOutputInformation,
    outputType: OutputType
  ): GenericBuiltArtifactsWithTimestamp? {
    val buildOutputListingFile = variantOutputInformation.getOutputListingFile(outputType) ?: return null
    val artifactsWithTimestamp = getGenericBuiltArtifactsUsingCache(buildOutputListingFile)
    return if (artifactsWithTimestamp.genericBuiltArtifacts == null) {
      null
    } else artifactsWithTimestamp
  }

  private fun getGenericBuiltArtifactsUsingCache(buildOutputListingFile: String): GenericBuiltArtifactsWithTimestamp {
    val module = myModule ?: error("GradleAndroidModel is not attached to a facet")
    return synchronized(myGenericBuiltArtifactsMap) {
      val artifactsWithTimestamp = myGenericBuiltArtifactsMap[buildOutputListingFile]
      val lastSyncOrBuild: Long = module.project.getService(LastBuildOrSyncService::class.java).lastBuildOrSyncTimeStamp
      if (artifactsWithTimestamp == null || lastSyncOrBuild >= artifactsWithTimestamp.timeStamp) {
        GenericBuiltArtifactsWithTimestamp(loadBuildOutputListingFile(buildOutputListingFile), System.currentTimeMillis())
          .also {
            myGenericBuiltArtifactsMap[buildOutputListingFile] = it
          }
      } else {
        artifactsWithTimestamp
      }
    }
  }

  companion object {
    private const val ourAndroidSyncVersion = "2022-05-17/1"

    @JvmStatic
    fun get(module: Module): GradleAndroidModel? = AndroidModel.get(module) as? GradleAndroidModel

    @JvmStatic
    fun get(androidFacet: AndroidFacet): GradleAndroidModel? = AndroidModel.get(androidFacet) as? GradleAndroidModel

    fun findFromModuleDataNode(dataNode: DataNode<*>): GradleAndroidModel? {
      return when (dataNode.key) {
        ProjectKeys.MODULE -> ExternalSystemApiUtil.find(dataNode, AndroidProjectKeys.ANDROID_MODEL)?.data
        GradleSourceSetData.KEY -> dataNode.parent?.let { findFromModuleDataNode(it)}
        else -> null
      }
    }

    @JvmStatic
    fun create(
      moduleName: String,
      rootDirPath: File,
      androidProject: IdeAndroidProject,
      cachedVariants: Collection<IdeVariantCoreImpl>,
      variantName: String
    ): GradleAndroidModel {
      return GradleAndroidModel(
        ourAndroidSyncVersion,
        moduleName,
        rootDirPath,
        androidProject,
        cachedVariants.associateBy { it.name },
        variantName
      )
    }
  }
}