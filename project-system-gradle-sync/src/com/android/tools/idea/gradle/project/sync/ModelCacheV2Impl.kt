/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync

import com.android.builder.model.v2.ModelSyncFile
import com.android.builder.model.v2.dsl.BuildType
import com.android.builder.model.v2.dsl.ClassField
import com.android.builder.model.v2.dsl.DependenciesInfo
import com.android.builder.model.v2.dsl.ProductFlavor
import com.android.builder.model.v2.dsl.SigningConfig
import com.android.builder.model.v2.ide.AaptOptions
import com.android.builder.model.v2.ide.AndroidArtifact
import com.android.builder.model.v2.ide.AndroidGradlePluginProjectFlags
import com.android.builder.model.v2.ide.ApiVersion
import com.android.builder.model.v2.ide.ArtifactDependencies
import com.android.builder.model.v2.ide.BasicArtifact
import com.android.builder.model.v2.ide.BasicVariant
import com.android.builder.model.v2.ide.GraphItem
import com.android.builder.model.v2.ide.JavaArtifact
import com.android.builder.model.v2.ide.JavaCompileOptions
import com.android.builder.model.v2.ide.Library
import com.android.builder.model.v2.ide.LibraryInfo
import com.android.builder.model.v2.ide.LibraryType
import com.android.builder.model.v2.ide.LintOptions
import com.android.builder.model.v2.ide.ProjectType
import com.android.builder.model.v2.ide.SourceProvider
import com.android.builder.model.v2.ide.SourceSetContainer
import com.android.builder.model.v2.ide.TestInfo
import com.android.builder.model.v2.ide.TestedTargetVariant
import com.android.builder.model.v2.ide.UnresolvedDependency
import com.android.builder.model.v2.ide.Variant
import com.android.builder.model.v2.ide.VectorDrawablesOptions
import com.android.builder.model.v2.ide.ViewBindingOptions
import com.android.builder.model.v2.models.AndroidDsl
import com.android.builder.model.v2.models.AndroidProject
import com.android.builder.model.v2.models.BasicAndroidProject
import com.android.builder.model.v2.models.VariantDependencies
import com.android.builder.model.v2.models.Versions
import com.android.builder.model.v2.models.ndk.NativeAbi
import com.android.builder.model.v2.models.ndk.NativeBuildSystem
import com.android.builder.model.v2.models.ndk.NativeModule
import com.android.builder.model.v2.models.ndk.NativeVariant
import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.gradle.model.CodeShrinker
import com.android.tools.idea.gradle.model.IdeAaptOptions
import com.android.tools.idea.gradle.model.IdeAndroidGradlePluginProjectFlags
import com.android.tools.idea.gradle.model.IdeAndroidLibrary
import com.android.tools.idea.gradle.model.IdeAndroidLibraryDependency
import com.android.tools.idea.gradle.model.IdeAndroidProject
import com.android.tools.idea.gradle.model.IdeAndroidProjectType
import com.android.tools.idea.gradle.model.IdeArtifactLibrary
import com.android.tools.idea.gradle.model.IdeArtifactName
import com.android.tools.idea.gradle.model.IdeBuildTasksAndOutputInformation
import com.android.tools.idea.gradle.model.IdeBuildType
import com.android.tools.idea.gradle.model.IdeBuildTypeContainer
import com.android.tools.idea.gradle.model.IdeClassField
import com.android.tools.idea.gradle.model.IdeDependencies
import com.android.tools.idea.gradle.model.IdeDependenciesInfo
import com.android.tools.idea.gradle.model.IdeDependency
import com.android.tools.idea.gradle.model.IdeJavaLibrary
import com.android.tools.idea.gradle.model.IdeJavaLibraryDependency
import com.android.tools.idea.gradle.model.IdeLibrary
import com.android.tools.idea.gradle.model.IdeLintOptions
import com.android.tools.idea.gradle.model.IdeLintOptions.Companion.SEVERITY_DEFAULT_ENABLED
import com.android.tools.idea.gradle.model.IdeLintOptions.Companion.SEVERITY_ERROR
import com.android.tools.idea.gradle.model.IdeLintOptions.Companion.SEVERITY_FATAL
import com.android.tools.idea.gradle.model.IdeLintOptions.Companion.SEVERITY_IGNORE
import com.android.tools.idea.gradle.model.IdeLintOptions.Companion.SEVERITY_INFORMATIONAL
import com.android.tools.idea.gradle.model.IdeLintOptions.Companion.SEVERITY_WARNING
import com.android.tools.idea.gradle.model.IdeModelSyncFile
import com.android.tools.idea.gradle.model.IdeModuleDependency
import com.android.tools.idea.gradle.model.IdeModuleLibrary
import com.android.tools.idea.gradle.model.IdeModuleSourceSet
import com.android.tools.idea.gradle.model.IdeProductFlavor
import com.android.tools.idea.gradle.model.IdeProductFlavorContainer
import com.android.tools.idea.gradle.model.IdeSigningConfig
import com.android.tools.idea.gradle.model.IdeTestOptions
import com.android.tools.idea.gradle.model.IdeUnresolvedDependencies
import com.android.tools.idea.gradle.model.IdeVariantBuildInformation
import com.android.tools.idea.gradle.model.IdeViewBindingOptions
import com.android.tools.idea.gradle.model.impl.IdeAaptOptionsImpl
import com.android.tools.idea.gradle.model.impl.IdeAndroidArtifactImpl
import com.android.tools.idea.gradle.model.impl.IdeAndroidGradlePluginProjectFlagsImpl
import com.android.tools.idea.gradle.model.impl.IdeAndroidLibraryCore
import com.android.tools.idea.gradle.model.impl.IdeAndroidLibraryDependencyImpl
import com.android.tools.idea.gradle.model.impl.IdeAndroidLibraryImpl
import com.android.tools.idea.gradle.model.impl.IdeAndroidProjectImpl
import com.android.tools.idea.gradle.model.impl.IdeApiVersionImpl
import com.android.tools.idea.gradle.model.impl.IdeBuildTasksAndOutputInformationImpl
import com.android.tools.idea.gradle.model.impl.IdeBuildTypeContainerImpl
import com.android.tools.idea.gradle.model.impl.IdeBuildTypeImpl
import com.android.tools.idea.gradle.model.impl.IdeClassFieldImpl
import com.android.tools.idea.gradle.model.impl.IdeCustomSourceDirectoryImpl
import com.android.tools.idea.gradle.model.impl.IdeDependenciesImpl
import com.android.tools.idea.gradle.model.impl.IdeDependenciesInfoImpl
import com.android.tools.idea.gradle.model.impl.IdeJavaArtifactImpl
import com.android.tools.idea.gradle.model.impl.IdeJavaCompileOptionsImpl
import com.android.tools.idea.gradle.model.impl.IdeJavaLibraryCore
import com.android.tools.idea.gradle.model.impl.IdeJavaLibraryDependencyImpl
import com.android.tools.idea.gradle.model.impl.IdeJavaLibraryImpl
import com.android.tools.idea.gradle.model.impl.IdeLintOptionsImpl
import com.android.tools.idea.gradle.model.impl.IdeModelSyncFileImpl
import com.android.tools.idea.gradle.model.impl.IdeModuleDependencyImpl
import com.android.tools.idea.gradle.model.impl.IdeModuleLibraryImpl
import com.android.tools.idea.gradle.model.impl.IdeProductFlavorContainerImpl
import com.android.tools.idea.gradle.model.impl.IdeProductFlavorImpl
import com.android.tools.idea.gradle.model.impl.IdeSigningConfigImpl
import com.android.tools.idea.gradle.model.impl.IdeSourceProviderContainerImpl
import com.android.tools.idea.gradle.model.impl.IdeSourceProviderImpl
import com.android.tools.idea.gradle.model.impl.IdeTestOptionsImpl
import com.android.tools.idea.gradle.model.impl.IdeTestedTargetVariantImpl
import com.android.tools.idea.gradle.model.impl.IdeUnresolvedDependenciesImpl
import com.android.tools.idea.gradle.model.impl.IdeVariantBuildInformationImpl
import com.android.tools.idea.gradle.model.impl.IdeVariantImpl
import com.android.tools.idea.gradle.model.impl.IdeVectorDrawablesOptionsImpl
import com.android.tools.idea.gradle.model.impl.IdeViewBindingOptionsImpl
import com.android.tools.idea.gradle.model.impl.ThrowingIdeDependencies
import com.android.tools.idea.gradle.model.impl.ndk.v1.IdeNativeAndroidProjectImpl
import com.android.tools.idea.gradle.model.impl.ndk.v1.IdeNativeVariantAbiImpl
import com.android.tools.idea.gradle.model.impl.ndk.v2.IdeNativeAbiImpl
import com.android.tools.idea.gradle.model.impl.ndk.v2.IdeNativeModuleImpl
import com.android.tools.idea.gradle.model.impl.ndk.v2.IdeNativeVariantImpl
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Lists
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

// NOTE: The implementation is structured as a collection of nested functions to ensure no recursive dependencies are possible between
//       models unless explicitly handled by nesting. The same structure expressed as classes allows recursive data structures and thus we
//       cannot validate the structure at compile time.
internal fun modelCacheV2Impl(buildRootDirectory: File?): ModelCache {

  val strings: MutableMap<String, String> = HashMap()
  // Library names are expected to be unique, and thus we track already allocated library names to be able to uniqualize names when
  // necessary.
  val allocatedLibraryNames: MutableSet<String> = HashSet()

  // Different modules (Gradle projects) may (usually do) share the same libraries. We create up to two library instances in this case.
  // One is when the library is used as a regular dependency and one when it is used as a "provided" dependency. This is going to change
  // when we add support for dependency graphs and different entities are used to represent libraries and dependencies.
  // We use mutable [Instances] objects to keep record of already instantiated and named library objects for each of the cases.
  val androidLibraryCores: MutableMap<IdeAndroidLibraryCore, IdeAndroidLibrary> = HashMap()
  val javaLibraryCores: MutableMap<IdeJavaLibraryCore, IdeJavaLibrary> = HashMap()
  val moduleLibraryCores: MutableMap<IdeModuleLibraryImpl, IdeModuleLibraryImpl> = HashMap()


  /**
   * Finds an existing or creates a new library instances that wraps the library [core]. When creating a new library for a core for which
   * there is neither regular nor provided library yet generates a unique library name based on its artifact address.
   *
   * Note: Naming mechanism is going to change in the future when dependencies and libraries are separated. We will try to assign more
   * meaningful names to libraries representing different artifact variants under the same Gradle coordinates.
   */
  fun <TCore : IdeArtifactLibrary, TLibrary : IdeArtifactLibrary> MutableMap<TCore, TLibrary>.createOrGetNamedLibrary(
    core: TCore,
    factory: (core: TCore, name: String) -> TLibrary
  ): TLibrary {
    return computeIfAbsent(core) {
      val libraryName = allocatedLibraryNames.generateLibraryName(core, projectBasePath = buildRootDirectory!!)
      factory(core, libraryName)
    }
  }

  fun String.deduplicate() = strings.putIfAbsent(this, this) ?: this
  fun List<String>.deduplicateStrings(): List<String> = this.map { it.deduplicate() }
  fun Map<String, String>.deduplicateStrings(): Map<String, String> = map { (k, v) -> k.deduplicate() to v.deduplicate() }.toMap()
  fun Set<String>.deduplicateStrings(): Set<String> =  this.map { it.deduplicate() }.toSet()
  fun Collection<String>.deduplicateStrings(): Collection<String> = this.map { it.deduplicate() }

  fun File.deduplicateFile(): File = File(path.deduplicate())
  fun List<File>.deduplicateFiles() = map { it.deduplicateFile() }
  fun Collection<File>.deduplicateFiles() = map { it.deduplicateFile() }

  fun sourceProviderFrom(provider: SourceProvider): IdeSourceProviderImpl {
    val folder: File? = provider.manifestFile.parentFile?.deduplicateFile()
    fun File.makeRelativeAndDeduplicate(): String = (if (folder != null) relativeToOrSelf(folder) else this).path.deduplicate()
    fun Collection<File>.makeRelativeAndDeduplicate(): Collection<String> = map { it.makeRelativeAndDeduplicate() }
    return IdeSourceProviderImpl(
      myName = provider.name.deduplicate(),
      myFolder = folder,
      myManifestFile = provider.manifestFile.makeRelativeAndDeduplicate(),
      myJavaDirectories = provider.javaDirectories.makeRelativeAndDeduplicate(),
      myKotlinDirectories = provider.kotlinDirectories.makeRelativeAndDeduplicate(),
      myResourcesDirectories = provider.resourcesDirectories.makeRelativeAndDeduplicate(),
      myAidlDirectories = provider.aidlDirectories?.makeRelativeAndDeduplicate() ?: mutableListOf(),
      myRenderscriptDirectories = provider.renderscriptDirectories?.makeRelativeAndDeduplicate() ?: mutableListOf(),
      myResDirectories = provider.resDirectories?.makeRelativeAndDeduplicate() ?: mutableListOf(),
      myAssetsDirectories = provider.assetsDirectories?.makeRelativeAndDeduplicate() ?: mutableListOf(),
      myJniLibsDirectories = provider.jniLibsDirectories.makeRelativeAndDeduplicate(),
      myShadersDirectories = provider.shadersDirectories?.makeRelativeAndDeduplicate() ?: mutableListOf(),
      myMlModelsDirectories = provider.mlModelsDirectories?.makeRelativeAndDeduplicate() ?: mutableListOf(),
      myCustomSourceDirectories = provider.customDirectories?.map {
        IdeCustomSourceDirectoryImpl(it.sourceTypeName, folder, it.directory.makeRelativeAndDeduplicate())
      } ?: emptyList(),
    )
  }

  fun classFieldFrom(classField: ClassField): IdeClassFieldImpl {
    return IdeClassFieldImpl(
      name = classField.name.deduplicate(),
      type = classField.type.deduplicate(),
      value = classField.value.deduplicate(),
    )
  }

  fun vectorDrawablesOptionsFrom(options: VectorDrawablesOptions): IdeVectorDrawablesOptionsImpl {
    return IdeVectorDrawablesOptionsImpl(useSupportLibrary = options.useSupportLibrary)
  }

  fun apiVersionFrom(version: ApiVersion): IdeApiVersionImpl {
    val codename = version.codename?.deduplicate()
    val apiString = codename ?: version.apiLevel.toString().deduplicate()
    return IdeApiVersionImpl(
      apiLevel = version.apiLevel,
      codename = codename,
      apiString = apiString
    )
  }

  fun signingConfigFrom(config: SigningConfig): IdeSigningConfigImpl {
    return IdeSigningConfigImpl(
      name = config.name.deduplicate(),
      storeFile = config.storeFile?.deduplicateFile(),
      storePassword = config.storePassword?.deduplicate(),
      keyAlias = config.keyAlias?.deduplicate()
    )
  }

  fun productFlavorFrom(flavor: ProductFlavor): IdeProductFlavorImpl {
    return IdeProductFlavorImpl(
      name = flavor.name.deduplicate(),
      resValues = flavor.resValues?.mapValues { classFieldFrom(it.value) } ?: mapOf(),
      proguardFiles = ImmutableList.copyOf(flavor.proguardFiles.deduplicateFiles()),
      consumerProguardFiles = ImmutableList.copyOf(flavor.consumerProguardFiles.deduplicateFiles()),
      // AGP may return internal Groovy GString implementation as a value in
      // manifestPlaceholders
      // map. It cannot be serialized
      // with IDEA's external system serialization. We convert values to String to
      // make them
      // usable as they are converted to String by
      // the manifest merger anyway.
      manifestPlaceholders = ImmutableMap.copyOf(flavor.manifestPlaceholders.entries.associate { it.key to it.value.toString() }),
      applicationIdSuffix = flavor.applicationIdSuffix?.deduplicate(),
      versionNameSuffix = flavor.versionNameSuffix?.deduplicate(),
      multiDexEnabled = flavor.multiDexEnabled,
      testInstrumentationRunnerArguments = ImmutableMap.copyOf(flavor.testInstrumentationRunnerArguments.deduplicateStrings()),
      resourceConfigurations = ImmutableList.copyOf(flavor.resourceConfigurations.deduplicateStrings()),
      vectorDrawables = vectorDrawablesOptionsFrom(flavor.vectorDrawables),
      dimension = flavor.dimension?.deduplicate(),
      applicationId = flavor.applicationId?.deduplicate(),
      versionCode = flavor.versionCode,
      versionName = flavor.versionName?.deduplicate(),
      minSdkVersion = flavor.minSdkVersion?.let { it: ApiVersion -> apiVersionFrom(it) },
      targetSdkVersion = flavor.targetSdkVersion?.let { it: ApiVersion -> apiVersionFrom(it) },
      maxSdkVersion = flavor.maxSdkVersion,
      testApplicationId = flavor.testApplicationId?.deduplicate(),
      testInstrumentationRunner = flavor.testInstrumentationRunner?.deduplicate(),
      testFunctionalTest = flavor.testFunctionalTest,
      testHandleProfiling = flavor.testHandleProfiling
    )
  }

  fun mergeProductFlavorsFrom(
    defaultConfig: IdeProductFlavor,
    productFlavors: List<IdeProductFlavor>,
    projectType: IdeAndroidProjectType
  ): IdeProductFlavor {

    var applicationId = defaultConfig.applicationId
    var applicationIdSuffix = defaultConfig.applicationIdSuffix
    val consumerProguardFiles = mutableListOf<File>()
    consumerProguardFiles.addAll(defaultConfig.consumerProguardFiles)
    var versionNameSuffix = defaultConfig.versionNameSuffix
    var versionCode = defaultConfig.versionCode
    var versionName = defaultConfig.versionName
    var testApplicationId = defaultConfig.testApplicationId
    var testInstrumentationRunner = defaultConfig.testInstrumentationRunner
    val testInstrumentationRunnerArguments = mutableMapOf<String, String>()
    testInstrumentationRunnerArguments.putAll(defaultConfig.testInstrumentationRunnerArguments)
    var testHandleProfiling = defaultConfig.testHandleProfiling
    var testFunctionalTest = defaultConfig.testFunctionalTest
    val resourceConfigurations = mutableListOf<String>()
    resourceConfigurations.addAll(defaultConfig.resourceConfigurations)
    val manifestPlaceholder = mutableMapOf<String, String>()
    manifestPlaceholder.putAll(defaultConfig.manifestPlaceholders)
    val proguardFiles = mutableListOf<File>()
    proguardFiles.addAll(defaultConfig.proguardFiles)
    val resValues = mutableMapOf<String, IdeClassField>()
    resValues.putAll(defaultConfig.resValues)
    var multiDexEnabled = defaultConfig.multiDexEnabled
    var vectorDrawables = defaultConfig.vectorDrawables

    for (flavor in Lists.reverse(productFlavors)) {
      versionCode = flavor.versionCode ?: versionCode
      versionName = flavor.versionName ?: versionName
      testApplicationId = flavor.testApplicationId ?: testApplicationId
      testInstrumentationRunner = flavor.testInstrumentationRunner ?: testInstrumentationRunner
      testInstrumentationRunnerArguments.putAll(flavor.testInstrumentationRunnerArguments)
      testHandleProfiling = flavor.testHandleProfiling ?: testHandleProfiling
      testFunctionalTest = flavor.testFunctionalTest ?: testFunctionalTest
      resourceConfigurations.addAll(flavor.resourceConfigurations)
      manifestPlaceholder.putAll(flavor.manifestPlaceholders)
      resValues.putAll(flavor.resValues)
      multiDexEnabled = flavor.multiDexEnabled ?: multiDexEnabled
      if (flavor.vectorDrawables?.useSupportLibrary != null) {
        vectorDrawables = IdeVectorDrawablesOptionsImpl(flavor.vectorDrawables!!.useSupportLibrary)
      }
    }
    for (flavor in productFlavors) {
      if (flavor.proguardFiles.isNotEmpty() && flavor.consumerProguardFiles.isNotEmpty()) {
        proguardFiles.addAll(flavor.proguardFiles)
        consumerProguardFiles.addAll(flavor.consumerProguardFiles)
      }
      applicationId = flavor.applicationId ?: applicationId
      applicationIdSuffix = applicationIdSuffix?.plus(if (flavor.applicationIdSuffix != null) ".${flavor.applicationIdSuffix}" else "")
      versionNameSuffix = versionNameSuffix.orEmpty() + flavor.versionNameSuffix.orEmpty()
    }
    return IdeProductFlavorImpl(
      name = "",
      resValues = resValues,
      proguardFiles = proguardFiles,
      consumerProguardFiles = consumerProguardFiles,
      manifestPlaceholders = manifestPlaceholder.entries.associate { it.key to it.value },
      applicationIdSuffix = applicationIdSuffix,
      versionNameSuffix = versionNameSuffix,
      multiDexEnabled = multiDexEnabled,
      testInstrumentationRunnerArguments = ImmutableMap.copyOf(testInstrumentationRunnerArguments),
      resourceConfigurations = ImmutableList.copyOf(resourceConfigurations),
      vectorDrawables = vectorDrawables,
      dimension = "",
      applicationId =
      if (projectType == IdeAndroidProjectType.PROJECT_TYPE_TEST) {
        testApplicationId
      }
      else {
        applicationId?.plus(if (applicationIdSuffix != null) ".${applicationIdSuffix}" else "")
      },
      versionCode = versionCode,
      versionName = versionName,
      minSdkVersion = null,
      targetSdkVersion = null,
      maxSdkVersion = null,
      testApplicationId = testApplicationId,
      testInstrumentationRunner = testInstrumentationRunner,
      testFunctionalTest = testFunctionalTest,
      testHandleProfiling = testHandleProfiling
    )
  }

  fun sourceProviderContainerFrom(container: SourceProvider): IdeSourceProviderContainerImpl {
    return IdeSourceProviderContainerImpl(
      // As we no longer have ArtifactMetaData, we use hardcoded values for androidTests, unitTests and testFixtures artifacts.

      artifactName = if (container.name.startsWith("androidTest")) {
        "_android_test_"
      }
      else if (container.name.startsWith("testFixtures")) {
        "_test_fixtures_"
      }
      else "_unit_test_",
      sourceProvider = sourceProviderFrom(container)
    )
  }

  fun productFlavorContainerFrom(
    productFlavor: ProductFlavor,
    container: SourceSetContainer?): IdeProductFlavorContainerImpl {
    return IdeProductFlavorContainerImpl(
      productFlavor = productFlavorFrom(productFlavor),
      sourceProvider = container?.sourceProvider?.let { it: SourceProvider -> sourceProviderFrom(it) },
      extraSourceProviders = listOfNotNull(
        container?.androidTestSourceProvider?.let { it: SourceProvider -> sourceProviderContainerFrom(it) },
        container?.unitTestSourceProvider?.let { it: SourceProvider -> sourceProviderContainerFrom(it) },
        container?.testFixturesSourceProvider?.let { it: SourceProvider -> sourceProviderContainerFrom(it) }
      )
    )
  }

  fun buildTypeFrom(buildType: BuildType): IdeBuildTypeImpl {
    return IdeBuildTypeImpl(
      name = buildType.name.deduplicate(),
      resValues = buildType.resValues?.mapValues { classFieldFrom(it.value) } ?: mapOf(),
      proguardFiles = ImmutableList.copyOf(buildType.proguardFiles.deduplicateFiles()),
      consumerProguardFiles = ImmutableList.copyOf(buildType.consumerProguardFiles.deduplicateFiles()),
      // AGP may return internal Groovy GString implementation as a value in
      // manifestPlaceholders
      // map. It cannot be serialized
      // with IDEA's external system serialization. We convert values to String to
      // make them
      // usable as they are converted to String by
      // the manifest merger anyway.
      manifestPlaceholders = ImmutableMap.copyOf(buildType.manifestPlaceholders.entries.associate { it.key to it.value.toString() }.deduplicateStrings()),
      applicationIdSuffix = buildType.applicationIdSuffix?.deduplicate(),
      versionNameSuffix = buildType.versionNameSuffix?.deduplicate(),
      multiDexEnabled = buildType.multiDexEnabled,
      isDebuggable = buildType.isDebuggable,
      isJniDebuggable = buildType.isJniDebuggable,
      isRenderscriptDebuggable = buildType.isRenderscriptDebuggable,
      renderscriptOptimLevel = buildType.renderscriptOptimLevel,
      isMinifyEnabled = buildType.isMinifyEnabled,
      isZipAlignEnabled = buildType.isZipAlignEnabled
    )
  }

  fun buildTypeContainerFrom(buildType: BuildType, container: SourceSetContainer): IdeBuildTypeContainerImpl {
    return IdeBuildTypeContainerImpl(
      buildType = buildTypeFrom(buildType),
      sourceProvider = sourceProviderFrom(container.sourceProvider),
      extraSourceProviders = listOfNotNull(
        container.androidTestSourceProvider?.let { sourceProviderContainerFrom(it) },
        container.unitTestSourceProvider?.let { sourceProviderContainerFrom(it) },
        container.testFixturesSourceProvider?.let { sourceProviderContainerFrom(it) }
      )
    )
  }

  /**
   * @param androidLibrary Instance returned by android plugin.
   * path to build directory for all modules.
   * @return Instance of [IdeLibrary] based on dependency type.
   */
  fun androidLibraryFrom(androidLibrary: Library): IdeAndroidLibrary {
    val libraryInfo = androidLibrary.libraryInfo ?: error("libraryInfo missing for ${androidLibrary.key}")

    val androidLibraryData = androidLibrary.androidLibraryData ?: error("androidLibraryData missing for ${androidLibrary.key}")

    val core = IdeAndroidLibraryCore.create(
      artifactAddress = "${libraryInfo.group}:${libraryInfo.name}:${libraryInfo.version}@aar",
      folder = androidLibraryData.resFolder.parentFile.deduplicateFile() ?: File(""), // TODO: verify this always true

      artifact = androidLibrary.artifact ?: File(""),
      lintJar = androidLibrary.lintJar?.path,

      manifest = androidLibraryData.manifest.path ?: "",
      compileJarFiles = androidLibraryData.compileJarFiles.map { it.path },
      runtimeJarFiles = androidLibraryData.runtimeJarFiles.map { it.path },
      resFolder = androidLibraryData.resFolder.path ?: "",
      resStaticLibrary = androidLibraryData.resStaticLibrary,
      assetsFolder = androidLibraryData.assetsFolder.path ?: "",
      jniFolder = androidLibraryData.jniFolder.path ?: "",
      aidlFolder = androidLibraryData.aidlFolder.path ?: "",
      renderscriptFolder = androidLibraryData.renderscriptFolder.path ?: "",
      proguardRules = androidLibraryData.proguardRules.path ?: "",
      externalAnnotations = androidLibraryData.externalAnnotations.path ?: "",
      publicResources = androidLibraryData.publicResources.path ?: "",
      symbolFile = androidLibraryData.symbolFile.path,
      deduplicate = { strings.getOrPut(this) { this } }
    )
    return androidLibraryCores.createOrGetNamedLibrary(core, ::IdeAndroidLibraryImpl)
  }

  /**
   * @param javaLibrary Instance of type [LibraryType.JAVA_LIBRARY] returned by android plugin.
   * @return Instance of [Library] based on dependency type.
   */
  fun javaLibraryFrom(javaLibrary: Library): IdeJavaLibrary {
    val libraryInfo = javaLibrary.libraryInfo ?: error("libraryInfo missing for ${javaLibrary.key}")
    val core = IdeJavaLibraryCore(
      artifactAddress = "${libraryInfo.group}:${libraryInfo.name}:${libraryInfo.version}@jar",
      artifact = javaLibrary.artifact!!
    )
    return javaLibraryCores.createOrGetNamedLibrary(core, ::IdeJavaLibraryImpl)
  }

  fun libraryFrom(
    projectPath: String,
    buildId: String,
    variant: String?,
    lintJar: File?,
    isTestFixturesComponent: Boolean
  ): IdeModuleLibrary {
    val core = IdeModuleLibraryImpl(
      buildId = buildId,
      projectPath = projectPath,
      variant = variant,
      lintJar = lintJar?.path?.let(::File),
      sourceSet = if (isTestFixturesComponent) IdeModuleSourceSet.TEST_FIXTURES else IdeModuleSourceSet.MAIN
    )
    return moduleLibraryCores.internCore(core)
  }

  fun createFromDependencies(
    dependencies: ArtifactDependencies,
    libraries: Map<String, Library>,
    getVariantNameResolver: (buildId: File, projectPath: String) -> VariantNameResolver,
    buildNameMap: Map<String, File>
  ): IdeDependencies {
    // Map from unique artifact address to level2 library instance. The library instances are
    // supposed to be shared by all artifacts. When creating IdeLevel2Dependencies, check if current library is available in this map,
    // if it's available, don't create new one, simple add reference to it. If it's not available, create new instance and save
    // to this map, so it can be reused the next time when the same library is added.
    val librariesById = mutableMapOf<String, IdeDependency<*>>()
    fun createModuleLibrary(
      visited: MutableSet<String>,
      projectPath: String,
      artifactAddress: String,
      variant: String?,
      lintJar: File?,
      buildId: String,
      isTestFixturesComponent: Boolean
    ) {
      if (!visited.contains(artifactAddress)) {
        visited.add(artifactAddress)
        librariesById.computeIfAbsent(artifactAddress) {
          IdeModuleDependencyImpl(libraryFrom(projectPath, buildNameMap[buildId]!!.absolutePath, variant, lintJar, isTestFixturesComponent))
        }
      }
    }

    fun populateProjectDependencies(libraries: List<Library>, visited: MutableSet<String>) {
      for (identifier in libraries) {
        val projectInfo = identifier.projectInfo!!
        val variantNameResolver = getVariantNameResolver(buildNameMap[projectInfo.buildId]!!, projectInfo.projectPath)
        // TODO(b/203750717): Model this explicitly in the tooling model.
        val variantName = variantNameResolver(
          projectInfo.buildType,
          { dimension -> projectInfo.productFlavors[dimension] ?: error("$dimension attribute not found. Library: ${identifier.key}") }
        )
        createModuleLibrary(
          visited,
          projectInfo.projectPath, // this should always be non-null as this is a module library
          identifier.key,
          variantName,
          identifier.lintJar,
          projectInfo.buildId,
          projectInfo.isTestFixtures
        )
      }
    }

    fun populateJavaLibraries(
      javaLibraries: Collection<Library>,
      providedLibraries: Set<LibraryIdentity>,
      visited: MutableSet<String>
    ) {
      for (javaLibrary in javaLibraries) {
        val address = javaLibrary.artifact!!.path
        if (!visited.contains(address)) {
          visited.add(address)
          val isProvided = providedLibraries.contains(javaLibrary.libraryInfo?.toIdentity())
          librariesById.computeIfAbsent(address) {
            IdeJavaLibraryDependencyImpl(javaLibraryFrom(javaLibrary), isProvided)
          }
        }
      }
    }

    class LibrariesByType(
      val androidLibraries: List<Library>,
      val javaLibraries: List<Library>,
      val projectLibraries: List<Library>
    )

    fun getTypedLibraries(
      dependencies: List<Library>
    ): LibrariesByType {
      return dependencies.groupBy { it.type }.let {
        LibrariesByType(
          androidLibraries = it[LibraryType.ANDROID_LIBRARY] ?: emptyList(),
          javaLibraries = it[LibraryType.JAVA_LIBRARY] ?: emptyList(),
          projectLibraries = it[LibraryType.PROJECT] ?: emptyList()
        )
      }
    }

    /*
    Flattens a direct acyclic graph of dependencies into a list that includes each node only once and is the result of traversal in the
    depth-first pre-order order.
     */
    fun List<GraphItem>.toFlatLibraryList(): List<Library> {
      val result = mutableListOf<Library>()
      // We process items in the order that the recursive depth-first pre-order traversal would achieve. This is for compatibility
      // with v1 models and will change soon when we start exposing graphs to the IDE.
      val seenGraphItemLibraryKeys = HashSet<String>()
      val queue = ArrayDeque(this@toFlatLibraryList.asReversed())
      while (queue.isNotEmpty()) {
        val item = queue.removeLast()
        if (seenGraphItemLibraryKeys.add(item.key)) {
          queue.addAll(item.dependencies.asReversed().asSequence().filter { !seenGraphItemLibraryKeys.contains(it.key) })
          val library = libraries[item.key]
          if (library != null) {
            result.add(library)
          }
        }
      }
      return result
    }

    fun getRuntimeLibraries(
      runtimeDependencies: List<Library>,
      compileDependencies: List<Library>
    ): List<File> {
      val compileLibraryIdentities = compileDependencies.mapNotNull { it.libraryInfo?.toIdentity() }.toSet()

      return runtimeDependencies
        .filter {
          val id = it.libraryInfo?.toIdentity() ?: return@filter false
          id !in compileLibraryIdentities
        }
        .flatMap { it.getJarFilesForRuntimeClasspath() }
        .toList()
    }

    fun populateAndroidLibraries(
      androidLibraries: Collection<Library>,
      providedLibraries: Set<LibraryIdentity>,
      visited: MutableSet<String>
    ) {
      for (androidLibrary in androidLibraries) {
        val address = androidLibrary.key
        if (!visited.contains(address)) {
          visited.add(address)
          val isProvided = providedLibraries.contains(androidLibrary.libraryInfo?.toIdentity())
          librariesById.computeIfAbsent(address) {
            IdeAndroidLibraryDependencyImpl(androidLibraryFrom(androidLibrary), isProvided)
          }
        }
      }
    }

    fun getProvidedLibraries(
      compileDependencies: List<Library>,
      runtimeDependencies: List<Library>
    ): Set<LibraryIdentity> =
      compileDependencies.mapNotNull { it.libraryInfo?.toIdentity() }.toSet() -
      runtimeDependencies.mapNotNull { it.libraryInfo?.toIdentity() }.toSet()

    fun createIdeDependencies(
      artifactAddresses: Collection<String>,
      runtimeOnlyJars: Collection<File>
    ): IdeDependencies {
      val androidLibraries = ImmutableList.builder<IdeAndroidLibraryDependency>()
      val javaLibraries = ImmutableList.builder<IdeJavaLibraryDependency>()
      val moduleDependencies = ImmutableList.builder<IdeModuleDependency>()
      for (address in artifactAddresses) {
        when (val library = librariesById[address]!!) {
          is IdeAndroidLibraryDependency -> androidLibraries.add(library)
          is IdeJavaLibraryDependency -> javaLibraries.add(library)
          is IdeModuleDependency -> moduleDependencies.add(library)
          else -> throw UnsupportedOperationException("Unknown library type " + library::class.java)
        }
      }
      return IdeDependenciesImpl(
        androidLibraries = androidLibraries.build(),
        javaLibraries = javaLibraries.build(),
        moduleDependencies = moduleDependencies.build(),
        runtimeOnlyClasses = ImmutableList.copyOf(runtimeOnlyJars))
    }

    fun createIdeDependenciesInstance(): IdeDependencies {
      val visited = mutableSetOf<String>()
      val compileDependencies = dependencies.compileDependencies.toFlatLibraryList()
      val runtimeDependencies = dependencies.runtimeDependencies.toFlatLibraryList()
      val typedLibraries = getTypedLibraries(compileDependencies)
      val providedLibraries = getProvidedLibraries(compileDependencies, runtimeDependencies)
      populateAndroidLibraries(typedLibraries.androidLibraries, providedLibraries, visited)
      populateJavaLibraries(typedLibraries.javaLibraries, providedLibraries, visited)
      populateProjectDependencies(typedLibraries.projectLibraries, visited)
      val runtimeLibraries = getRuntimeLibraries(runtimeDependencies, compileDependencies)
      return createIdeDependencies(visited, runtimeLibraries)
    }
    return createIdeDependenciesInstance()
  }

  /**
   * Create [IdeDependencies] from [ArtifactDependencies].
   */
  fun dependenciesFrom(
    artifactDependencies: ArtifactDependencies,
    libraries: Map<String, Library>,
    getVariantNameResolver: (buildId: File, projectPath: String) -> VariantNameResolver,
    buildNameMap: Map<String, File>
  ): IdeDependencies {
    return createFromDependencies(artifactDependencies, libraries, getVariantNameResolver, buildNameMap)
  }

  fun List<UnresolvedDependency>.unresolvedDependenciesFrom(): List<IdeUnresolvedDependencies> {
    return map { IdeUnresolvedDependenciesImpl(it.name, it.cause) }
  }

  fun convertV2Execution(execution: TestInfo.Execution?): IdeTestOptions.Execution? {
    return if (execution == null) null
    else when (execution) {
      TestInfo.Execution.HOST -> IdeTestOptions.Execution.HOST
      TestInfo.Execution.ANDROID_TEST_ORCHESTRATOR -> IdeTestOptions.Execution.ANDROID_TEST_ORCHESTRATOR
      TestInfo.Execution.ANDROIDX_TEST_ORCHESTRATOR -> IdeTestOptions.Execution.ANDROIDX_TEST_ORCHESTRATOR
      else -> throw IllegalStateException("Unknown execution option: $execution")
    }
  }

  fun testOptionsFrom(testOptions: TestInfo): IdeTestOptionsImpl {
    return IdeTestOptionsImpl(
      animationsDisabled = testOptions.animationsDisabled,
      execution = convertV2Execution(testOptions.execution)
    )
  }

  fun convertCodeShrinker(codeShrinker: com.android.builder.model.v2.ide.CodeShrinker?): CodeShrinker? {
    return if (codeShrinker == null) null
    else when (codeShrinker) {
      com.android.builder.model.v2.ide.CodeShrinker.PROGUARD -> CodeShrinker.PROGUARD
      com.android.builder.model.v2.ide.CodeShrinker.R8 -> CodeShrinker.R8
      else -> throw IllegalStateException("Unknown code shrinker option: $codeShrinker")
    }
  }

  /**
   * Converts a [ModelSyncFile] from the Gradle Sync Model to the internal Ide Model.
   */
  fun modelSyncFileFrom(modelSyncFile: ModelSyncFile): IdeModelSyncFile {
    return IdeModelSyncFileImpl(
      // TODO(b/205713031): Parse syncType and handle unknown values.
      modelSyncType = IdeModelSyncFile.IdeModelSyncType.BASIC,
      taskName = modelSyncFile.taskName.deduplicate(),
      syncFile = modelSyncFile.syncFile.deduplicateFile()
    )
  }

  fun convertV2ArtifactName(name: String): IdeArtifactName = when (name) {
    "_main_" -> IdeArtifactName.MAIN
    "_android_test_" -> IdeArtifactName.ANDROID_TEST
    "_unit_test_" -> IdeArtifactName.UNIT_TEST
    "_test_fixtures_" -> IdeArtifactName.TEST_FIXTURES
    else -> error("Invalid android artifact name: $name")
  }

  fun buildTasksOutputInformationFrom(artifact: AndroidArtifact): IdeBuildTasksAndOutputInformation = IdeBuildTasksAndOutputInformationImpl(
    assembleTaskName = artifact.assembleTaskName,
    assembleTaskOutputListingFile = artifact.assembleTaskOutputListingFile?.path?.takeUnless { it.isEmpty() }?.deduplicate(),
    bundleTaskName = artifact.bundleInfo?.bundleTaskName,
    bundleTaskOutputListingFile = artifact.bundleInfo?.bundleTaskOutputListingFile?.path?.takeUnless { it.isEmpty() }?.deduplicate(),
    apkFromBundleTaskName = artifact.bundleInfo?.apkFromBundleTaskName,
    apkFromBundleTaskOutputListingFile = artifact.bundleInfo?.apkFromBundleTaskOutputListingFile?.path?.takeUnless { it.isEmpty() }?.deduplicate()
  )

  fun androidArtifactFrom(
    name: String,
    basicArtifact: BasicArtifact,
    artifact: AndroidArtifact
  ): IdeAndroidArtifactImpl {
    val testInfo = artifact.testInfo
    return IdeAndroidArtifactImpl(
      name = convertV2ArtifactName(name),
      compileTaskName = artifact.compileTaskName,
      assembleTaskName = artifact.assembleTaskName,
      classesFolder = artifact.classesFolders,
      ideSetupTaskNames = artifact.ideSetupTaskNames.toList(),
      generatedSourceFolders = artifact.generatedSourceFolders.deduplicateFiles().distinct(),
      variantSourceProvider = basicArtifact.variantSourceProvider?.let { sourceProviderFrom(it) },
      multiFlavorSourceProvider = basicArtifact.multiFlavorSourceProvider?.let { sourceProviderFrom(it) },
      level2Dependencies = ThrowingIdeDependencies(),
      unresolvedDependencies = emptyList(),
      applicationId = "",
      generatedResourceFolders = artifact.generatedResourceFolders.deduplicateFiles().distinct(),
      signingConfigName = artifact.signingConfigName?.deduplicate(),
      abiFilters = ImmutableSet.copyOf(artifact.abiFilters.orEmpty()),
      isSigned = artifact.isSigned,
      additionalRuntimeApks = testInfo?.additionalRuntimeApks?.deduplicateFiles() ?: emptyList(),
      testOptions = artifact.testInfo?.let { testOptionsFrom(it) },
      buildInformation = buildTasksOutputInformationFrom(artifact),
      codeShrinker = convertCodeShrinker(artifact.codeShrinker),
      isTestArtifact = name == "_android_test_",
      modelSyncFiles = artifact.modelSyncFiles.map { modelSyncFileFrom(it) },
    )
  }

  fun androidArtifactFrom(
    artifact: IdeAndroidArtifactImpl,
    artifactDependencies: ArtifactDependencies,
    libraries: Map<String, Library>,
    getVariantNameResolver: (buildId: File, projectPath: String) -> VariantNameResolver,
    buildNameMap: Map<String, File>,
  ): IdeAndroidArtifactImpl {
    return artifact.copy(
      level2Dependencies = dependenciesFrom(artifactDependencies, libraries, getVariantNameResolver, buildNameMap),
      unresolvedDependencies = artifactDependencies.unresolvedDependencies.unresolvedDependenciesFrom(),
    )
  }

  fun javaArtifactFrom(
    name: String,
    basicArtifact: BasicArtifact,
    artifact: JavaArtifact
  ): IdeJavaArtifactImpl {
    return IdeJavaArtifactImpl(
      name = convertV2ArtifactName(name),
      compileTaskName = artifact.compileTaskName,
      assembleTaskName = artifact.assembleTaskName,
      classesFolder = artifact.classesFolders,
      ideSetupTaskNames = artifact.ideSetupTaskNames.deduplicateStrings(),
      generatedSourceFolders = artifact.generatedSourceFolders.deduplicateFiles().distinct(),
      variantSourceProvider = basicArtifact.variantSourceProvider?.let { sourceProviderFrom(it) },
      multiFlavorSourceProvider = basicArtifact.multiFlavorSourceProvider?.let { sourceProviderFrom(it) },
      level2Dependencies = ThrowingIdeDependencies(),
      unresolvedDependencies = emptyList(),
      mockablePlatformJar = artifact.mockablePlatformJar,
      isTestArtifact = name == "_unit_test_"
    )
  }

  fun javaArtifactFrom(
    artifact: IdeJavaArtifactImpl,
    variantDependencies: ArtifactDependencies,
    libraries: Map<String, Library>,
    getVariantNameResolver: (buildId: File, projectPath: String) -> VariantNameResolver,
    buildNameMap: Map<String, File>
  ): IdeJavaArtifactImpl {
    return artifact.copy(
      level2Dependencies = dependenciesFrom(variantDependencies, libraries, getVariantNameResolver, buildNameMap),
      unresolvedDependencies = variantDependencies.unresolvedDependencies.unresolvedDependenciesFrom(),
    )
  }

  fun ideTestedTargetVariantFrom(testedTargetVariant: TestedTargetVariant): IdeTestedTargetVariantImpl = IdeTestedTargetVariantImpl(
    targetProjectPath = testedTargetVariant.targetProjectPath.deduplicate(),
    targetVariant = testedTargetVariant.targetVariant.deduplicate()
  )

  fun getTestedTargetVariants(variant: Variant): List<IdeTestedTargetVariantImpl> {
    if (variant.testedTargetVariant == null) return emptyList()
    return listOf(ideTestedTargetVariantFrom(variant.testedTargetVariant!!))
  }

  fun variantFrom(
    androidProject: IdeAndroidProject,
    basicVariant: BasicVariant,
    variant: Variant,
    modelVersion: GradleVersion?
  ): IdeVariantImpl {
    // To get merged flavors for V2, we merge flavors from default config and all the flavors.
    val mergedFlavor = mergeProductFlavorsFrom(
      androidProject.defaultConfig.productFlavor,
      androidProject.productFlavors.map { it.productFlavor }.filter { basicVariant.productFlavors.contains(it.name) }.toList(),
      androidProject.projectType
    )

    val buildType = androidProject.buildTypes.find { it.buildType.name == basicVariant.buildType }?.buildType

    fun <T> merge(f: IdeProductFlavor.() -> T, b: IdeBuildType.() -> T, combine: (T?, T?) -> T): T {
      return combine(mergedFlavor.f(), buildType?.b())
    }

    fun <T> combineMaps(u: Map<String, T>?, v: Map<String, T>?): Map<String, T> = u.orEmpty() + v.orEmpty()
    fun <T> combineSets(u: Collection<T>?, v: Collection<T>?): Collection<T> = (u?.toSet().orEmpty() + v.orEmpty()).toList()

    val versionNameSuffix =
      if (mergedFlavor.versionNameSuffix == null && buildType?.versionNameSuffix == null) null
      else mergedFlavor.versionNameSuffix.orEmpty() + buildType?.versionNameSuffix.orEmpty()
    return IdeVariantImpl(
      name = variant.name.deduplicate(),
      displayName = variant.displayName.deduplicate(),
      mainArtifact = androidArtifactFrom("_main_", basicVariant.mainArtifact, variant.mainArtifact),
      // If AndroidArtifact isn't null, then same goes for the ArtifactDependencies.
      unitTestArtifact = variant.unitTestArtifact?.let { it: JavaArtifact ->
        javaArtifactFrom("_unit_test_", basicVariant.unitTestArtifact!!, it)
      },
      androidTestArtifact = variant.androidTestArtifact?.let { it: AndroidArtifact ->
        androidArtifactFrom("_android_test_", basicVariant.androidTestArtifact!!, it)
      },
      testFixturesArtifact = variant.testFixturesArtifact?.let { it: AndroidArtifact ->
        androidArtifactFrom("_test_fixtures_", basicVariant.testFixturesArtifact!!, it)
      },
      buildType = basicVariant.buildType?.deduplicate() ?: "",
      productFlavors = ImmutableList.copyOf(basicVariant.productFlavors.deduplicateStrings()),
      minSdkVersion = apiVersionFrom(variant.mainArtifact.minSdkVersion),
      targetSdkVersion = variant.mainArtifact.targetSdkVersionOverride?.let { it: ApiVersion -> apiVersionFrom(it) },
      maxSdkVersion = variant.mainArtifact.maxSdkVersion,
      versionCode = mergedFlavor.versionCode,
      versionNameWithSuffix = mergedFlavor.versionName?.let { it + versionNameSuffix.orEmpty() }?.deduplicate(),
      versionNameSuffix = versionNameSuffix?.deduplicate(),
      instantAppCompatible = (modelVersion != null && variant.isInstantAppCompatible),
      vectorDrawablesUseSupportLibrary = mergedFlavor.vectorDrawables?.useSupportLibrary ?: false,
      resourceConfigurations = mergedFlavor.resourceConfigurations.deduplicateStrings(),
      testApplicationId = mergedFlavor.testApplicationId?.deduplicate(),
      testInstrumentationRunner = mergedFlavor.testInstrumentationRunner?.deduplicate(),
      testInstrumentationRunnerArguments = mergedFlavor.testInstrumentationRunnerArguments.deduplicateStrings(),
      testedTargetVariants = getTestedTargetVariants(variant),
      resValues = merge({ resValues }, { resValues }, ::combineMaps),
      proguardFiles = merge({ proguardFiles }, { proguardFiles }, ::combineSets),
      consumerProguardFiles = merge({ consumerProguardFiles }, { consumerProguardFiles }, ::combineSets),
      manifestPlaceholders = merge({ manifestPlaceholders }, { manifestPlaceholders }, ::combineMaps),
      deprecatedPreMergedApplicationId = null
    )
  }

  fun variantFrom(
    variant: IdeVariantImpl,
    variantDependencies: VariantDependencies,
    getVariantNameResolver: (buildId: File, projectPath: String) -> VariantNameResolver,
    buildNameMap: Map<String, File>
  ): IdeVariantImpl {
    return variant.copy(
      mainArtifact = variant.mainArtifact.let {
        androidArtifactFrom(it, variantDependencies.mainArtifact, variantDependencies.libraries, getVariantNameResolver, buildNameMap)
      },
      unitTestArtifact = variant.unitTestArtifact?.let {
        javaArtifactFrom(it, variantDependencies.unitTestArtifact!!, variantDependencies.libraries, getVariantNameResolver, buildNameMap)
      },
      androidTestArtifact = variant.androidTestArtifact?.let {
        androidArtifactFrom(it, variantDependencies.androidTestArtifact!!, variantDependencies.libraries, getVariantNameResolver,
                            buildNameMap)
      },
      testFixturesArtifact = variant.testFixturesArtifact?.let {
        androidArtifactFrom(it, variantDependencies.testFixturesArtifact!!, variantDependencies.libraries, getVariantNameResolver,
                            buildNameMap)
      },
    )
  }

  fun nativeAbiFrom(nativeAbi: NativeAbi): IdeNativeAbiImpl {
    return IdeNativeAbiImpl(
      name = nativeAbi.name.deduplicate(),
      sourceFlagsFile = nativeAbi.sourceFlagsFile.deduplicateFile(),
      symbolFolderIndexFile = nativeAbi.symbolFolderIndexFile.deduplicateFile(),
      buildFileIndexFile = nativeAbi.buildFileIndexFile.deduplicateFile(),
      additionalProjectFilesIndexFile = nativeAbi.additionalProjectFilesIndexFile.deduplicateFile()
    )
  }

  fun nativeVariantFrom(nativeVariant: NativeVariant): IdeNativeVariantImpl {
    return IdeNativeVariantImpl(
      name = nativeVariant.name.deduplicate(),
      abis = nativeVariant.abis.map { nativeAbiFrom(it) },
    )
  }

  fun nativeModuleFrom(nativeModule: NativeModule): IdeNativeModuleImpl {
    return IdeNativeModuleImpl(
      name = nativeModule.name.deduplicate(),
      variants = nativeModule.variants.map { nativeVariantFrom(it) },
      nativeBuildSystem = when (nativeModule.nativeBuildSystem) {
        NativeBuildSystem.NDK_BUILD -> com.android.tools.idea.gradle.model.ndk.v2.NativeBuildSystem.NDK_BUILD
        NativeBuildSystem.CMAKE -> com.android.tools.idea.gradle.model.ndk.v2.NativeBuildSystem.CMAKE
        NativeBuildSystem.NINJA -> com.android.tools.idea.gradle.model.ndk.v2.NativeBuildSystem.NINJA
        // No forward compatibility. Old Studio cannot open projects with newer AGP.
        else -> error("Unknown native build system: ${nativeModule.nativeBuildSystem}")
      },
      ndkVersion = nativeModule.ndkVersion,
      defaultNdkVersion = nativeModule.defaultNdkVersion,
      externalNativeBuildFile = nativeModule.externalNativeBuildFile
    )
  }

  fun severityOverridesFrom(lintOptions: LintOptions): Map<String, Int>? {
    val severityOverrides = mutableMapOf<String, Int>()
    lintOptions.fatal.forEach { severityOverrides[it] = SEVERITY_FATAL }
    lintOptions.error.forEach { severityOverrides[it] = SEVERITY_ERROR }
    lintOptions.warning.forEach { severityOverrides[it] = SEVERITY_WARNING }
    lintOptions.informational.forEach { severityOverrides[it] = SEVERITY_INFORMATIONAL }
    lintOptions.disable.forEach { severityOverrides[it] = SEVERITY_IGNORE }
    lintOptions.enable.forEach { severityOverrides[it] = SEVERITY_DEFAULT_ENABLED }
    return severityOverrides.ifEmpty { null }
  }

  fun lintOptionsFrom(options: LintOptions, modelVersion: GradleVersion?): IdeLintOptionsImpl = IdeLintOptionsImpl(
    baselineFile = if (modelVersion != null)
      options.baseline
    else
      null,
    lintConfig = options.lintConfig,
    severityOverrides = severityOverridesFrom(options),
    isCheckTestSources = modelVersion != null && options.checkTestSources,
    isCheckDependencies = options.checkDependencies,
    disable = options.disable.deduplicateStrings(),
    enable = options.enable.deduplicateStrings(),
    check = options.checkOnly.deduplicateStrings(),
    isAbortOnError = options.abortOnError,
    isAbsolutePaths = options.absolutePaths,
    isNoLines = options.noLines,
    isQuiet = options.quiet,
    isCheckAllWarnings = options.checkAllWarnings,
    isIgnoreWarnings = options.ignoreWarnings,
    isWarningsAsErrors = options.warningsAsErrors,
    isIgnoreTestSources = options.ignoreTestSources,
    isIgnoreTestFixturesSources = options.ignoreTestFixturesSources,
    isCheckGeneratedSources = options.checkGeneratedSources,
    isExplainIssues = options.explainIssues,
    isShowAll = options.showAll,
    textReport = options.textReport,
    textOutput = options.textOutput,
    htmlReport = options.htmlReport,
    htmlOutput = options.htmlOutput,
    xmlReport = options.xmlReport,
    xmlOutput = options.xmlOutput,
    isCheckReleaseBuilds = options.checkReleaseBuilds
  )

  fun javaCompileOptionsFrom(options: JavaCompileOptions): IdeJavaCompileOptionsImpl {
    return IdeJavaCompileOptionsImpl(
      encoding = options.encoding,
      sourceCompatibility = options.sourceCompatibility,
      targetCompatibility = options.targetCompatibility,
      isCoreLibraryDesugaringEnabled = options.isCoreLibraryDesugaringEnabled
    )
  }

  fun convertNamespacing(namespacing: AaptOptions.Namespacing): IdeAaptOptions.Namespacing {
    return when (namespacing) {
      AaptOptions.Namespacing.DISABLED -> IdeAaptOptions.Namespacing.DISABLED
      AaptOptions.Namespacing.REQUIRED -> IdeAaptOptions.Namespacing.REQUIRED
      else -> throw IllegalStateException("Unknown namespacing option: $namespacing")
    }
  }

  fun aaptOptionsFrom(original: AaptOptions): IdeAaptOptionsImpl {
    return IdeAaptOptionsImpl(namespacing = convertNamespacing(original.namespacing))
  }

  fun ideVariantBuildInformationFrom(variant: Variant): IdeVariantBuildInformation = IdeVariantBuildInformationImpl(
    variantName = variant.name,
    buildInformation = buildTasksOutputInformationFrom(variant.mainArtifact)
  )

  fun createVariantBuildInformation(
    project: AndroidProject,
    agpVersion: GradleVersion?
  ): Collection<IdeVariantBuildInformation> {
    return if (agpVersion != null) {
      project.variants.map(::ideVariantBuildInformationFrom)
    }
    else emptyList()
  }

  fun viewBindingOptionsFrom(model: ViewBindingOptions): IdeViewBindingOptionsImpl {
    return IdeViewBindingOptionsImpl(enabled = model.isEnabled)
  }

  fun dependenciesInfoFrom(model: DependenciesInfo) = IdeDependenciesInfoImpl(
    includeInApk = model.includeInApk,
    includeInBundle = model.includeInBundle
  )

  fun androidGradlePluginProjectFlagsFrom(flags: AndroidGradlePluginProjectFlags): IdeAndroidGradlePluginProjectFlagsImpl =
    IdeAndroidGradlePluginProjectFlagsImpl(
      applicationRClassConstantIds =
      AndroidGradlePluginProjectFlags.BooleanFlag.APPLICATION_R_CLASS_CONSTANT_IDS.getValue(flags),
      testRClassConstantIds = AndroidGradlePluginProjectFlags.BooleanFlag.TEST_R_CLASS_CONSTANT_IDS.getValue(flags),
      transitiveRClasses = AndroidGradlePluginProjectFlags.BooleanFlag.TRANSITIVE_R_CLASS.getValue(flags),
      usesCompose = AndroidGradlePluginProjectFlags.BooleanFlag.JETPACK_COMPOSE.getValue(flags),
      mlModelBindingEnabled = AndroidGradlePluginProjectFlags.BooleanFlag.ML_MODEL_BINDING.getValue(flags),
      unifiedTestPlatformEnabled = AndroidGradlePluginProjectFlags.BooleanFlag.UNIFIED_TEST_PLATFORM.getValue(flags),
    )

  fun copyProjectType(projectType: ProjectType): IdeAndroidProjectType = when (projectType) {
    // TODO(b/187504821): is the number of supported project type in V2 reduced ? this is a restricted list compared to V1.

    ProjectType.APPLICATION -> IdeAndroidProjectType.PROJECT_TYPE_APP
    ProjectType.LIBRARY -> IdeAndroidProjectType.PROJECT_TYPE_LIBRARY
    ProjectType.TEST -> IdeAndroidProjectType.PROJECT_TYPE_TEST
    ProjectType.DYNAMIC_FEATURE -> IdeAndroidProjectType.PROJECT_TYPE_DYNAMIC_FEATURE
    else -> error("Unknown Android project type: $projectType")
  }

  fun androidProjectFrom(
    basicProject: BasicAndroidProject,
    project: AndroidProject,
    modelsVersions: Versions,
    androidDsl: AndroidDsl
  ): IdeAndroidProjectImpl {
    val parsedModelVersion = GradleVersion.tryParse(modelsVersions.agp)
    val defaultConfigCopy: IdeProductFlavorContainer = productFlavorContainerFrom(androidDsl.defaultConfig, basicProject.mainSourceSet)
    val buildTypesCopy: Collection<IdeBuildTypeContainer> = zip(androidDsl.buildTypes, basicProject.buildTypeSourceSets,
                                                                ::buildTypeContainerFrom)
    val productFlavorCopy: Collection<IdeProductFlavorContainer> = zip(androidDsl.productFlavors, basicProject.productFlavorSourceSets,
                                                                       ::productFlavorContainerFrom)
    val variantNamesCopy: Collection<String> = project.variants.map { it.name }
    val flavorDimensionCopy: Collection<String> = androidDsl.flavorDimensions.deduplicateStrings()
    val bootClasspathCopy: Collection<String> = ImmutableList.copyOf(basicProject.bootClasspath.map { it.absolutePath })
    val signingConfigsCopy: Collection<IdeSigningConfig> = androidDsl.signingConfigs.map { signingConfigFrom(it) }
    val lintOptionsCopy: IdeLintOptions = lintOptionsFrom(androidDsl.lintOptions, parsedModelVersion)
    val javaCompileOptionsCopy = javaCompileOptionsFrom(project.javaCompileOptions)
    val aaptOptionsCopy = aaptOptionsFrom(androidDsl.aaptOptions)
    val dynamicFeaturesCopy: Collection<String> = project.dynamicFeatures?.deduplicateStrings() ?: listOf()
    val variantBuildInformation = createVariantBuildInformation(project, parsedModelVersion)
    val viewBindingOptionsCopy: IdeViewBindingOptions? = project.viewBindingOptions?.let { viewBindingOptionsFrom(it) }
    val dependenciesInfoCopy: IdeDependenciesInfo? = androidDsl.dependenciesInfo?.let { dependenciesInfoFrom(it) }
    val buildToolsVersionCopy = androidDsl.buildToolsVersion
    val groupId = androidDsl.groupId
    val lintChecksJarsCopy: List<File> = project.lintChecksJars.deduplicateFiles()
    val isBaseSplit = basicProject.projectType == ProjectType.APPLICATION
    val agpFlags: IdeAndroidGradlePluginProjectFlags = androidGradlePluginProjectFlagsFrom(project.flags)

    return IdeAndroidProjectImpl(
      agpVersion = modelsVersions.agp,
      name = basicProject.path,
      defaultConfig = defaultConfigCopy,
      buildTypes = buildTypesCopy,
      productFlavors = productFlavorCopy,
      variantNames = variantNamesCopy,
      flavorDimensions = flavorDimensionCopy,
      compileTarget = androidDsl.compileTarget,
      bootClasspath = bootClasspathCopy,
      signingConfigs = signingConfigsCopy,
      lintOptions = lintOptionsCopy,
      lintChecksJars = lintChecksJarsCopy,
      javaCompileOptions = javaCompileOptionsCopy,
      aaptOptions = aaptOptionsCopy,
      buildFolder = basicProject.buildFolder,
      dynamicFeatures = dynamicFeaturesCopy,
      variantsBuildInformation = variantBuildInformation,
      viewBindingOptions = viewBindingOptionsCopy,
      dependenciesInfo = dependenciesInfoCopy,
      buildToolsVersion = buildToolsVersionCopy,
      resourcePrefix = project.resourcePrefix,
      groupId = groupId,
      namespace = project.namespace,
      testNamespace = project.androidTestNamespace,
      projectType = copyProjectType(basicProject.projectType),
      isBaseSplit = isBaseSplit,
      agpFlags = agpFlags,
      isKaptEnabled = false
    )
  }

  return object : ModelCache.V2 {
    private val lock = ReentrantLock()
    override fun variantFrom(
      androidProject: IdeAndroidProject,
      basicVariant: BasicVariant,
      variant: Variant,
      modelVersion: GradleVersion?
    ): IdeVariantImpl = lock.withLock { variantFrom(androidProject, basicVariant, variant, modelVersion) }

    override fun variantFrom(
      variant: IdeVariantImpl,
      variantDependencies: VariantDependencies,
      getVariantNameResolver: (buildId: File, projectPath: String) -> VariantNameResolver,
      buildNameMap: Map<String, File>
    ): IdeVariantImpl = lock.withLock { variantFrom(variant, variantDependencies, getVariantNameResolver, buildNameMap) }

    override fun androidProjectFrom(
      basicProject: BasicAndroidProject,
      project: AndroidProject,
      androidVersion: Versions,
      androidDsl: AndroidDsl
    ): IdeAndroidProjectImpl = lock.withLock { androidProjectFrom(basicProject, project, androidVersion, androidDsl)}

    override fun nativeModuleFrom(nativeModule: NativeModule): IdeNativeModuleImpl = lock.withLock { nativeModuleFrom(nativeModule) }

    override fun nativeVariantAbiFrom(variantAbi: com.android.builder.model.NativeVariantAbi): IdeNativeVariantAbiImpl =
      throw UnsupportedOperationException("com.android.builder.model.NativeVariantAbi is a model v1 concept")

    override fun nativeAndroidProjectFrom(project: com.android.builder.model.NativeAndroidProject,
                                          ndkVersion: String): IdeNativeAndroidProjectImpl =
      throw UnsupportedOperationException("com.android.builder.model.NativeAndroidProject is a model v1 concept")
  }
}

private inline fun <K, R, V> zip(original1: Collection<K>, original2: Collection<R>, mapper: (K, R) -> V): List<V> {
  return original1.zip(original2).toMap().map { (k, v) -> mapper(k, v) }
}

private fun <T> MutableMap<T, T>.internCore(core: T): T = putIfAbsent(core, core) ?: core

private fun MutableSet<String>.generateLibraryName(core: IdeArtifactLibrary, projectBasePath: File): String {
  val baseLibraryName = convertToLibraryName(core.artifactAddress, projectBasePath)
  var candidateLibraryName = baseLibraryName
  var suffix = 0
  while (!this.add(candidateLibraryName)) {
    suffix++
    candidateLibraryName = "$baseLibraryName ($suffix)"
  }
  return candidateLibraryName
}


private data class LibraryIdentity(
  val group: String,
  val name: String,
  val version: String,
  val attributesExceptUsage: Map<String, String>,
  val capabilities: List<String>,
)

private fun Library.getJarFilesForRuntimeClasspath(): List<File> =
  when (type) {
    LibraryType.ANDROID_LIBRARY -> androidLibraryData?.runtimeJarFiles.orEmpty()
    LibraryType.JAVA_LIBRARY -> listOfNotNull(artifact)
    else -> emptyList()
  }

private fun LibraryInfo.toIdentity() =
  LibraryIdentity(
    name,
    group,
    version,
    attributes.filterKeys { it != "org.gradle.usage" },
    capabilities
  )


