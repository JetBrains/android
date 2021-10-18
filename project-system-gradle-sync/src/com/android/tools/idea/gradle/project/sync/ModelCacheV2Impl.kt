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

import com.android.SdkConstants
import com.android.build.OutputFile
import com.android.builder.model.NativeAndroidProject
import com.android.builder.model.NativeArtifact
import com.android.builder.model.NativeFile
import com.android.builder.model.NativeSettings
import com.android.builder.model.NativeToolchain
import com.android.builder.model.NativeVariantAbi
import com.android.builder.model.NativeVariantInfo
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
import com.android.tools.idea.gradle.model.IdeAndroidProject
import com.android.tools.idea.gradle.model.IdeAndroidProjectType
import com.android.tools.idea.gradle.model.IdeArtifactLibrary
import com.android.tools.idea.gradle.model.IdeArtifactName
import com.android.tools.idea.gradle.model.IdeBuildType
import com.android.tools.idea.gradle.model.IdeBuildTypeContainer
import com.android.tools.idea.gradle.model.IdeClassField
import com.android.tools.idea.gradle.model.IdeDependencies
import com.android.tools.idea.gradle.model.IdeDependenciesInfo
import com.android.tools.idea.gradle.model.IdeJavaLibrary
import com.android.tools.idea.gradle.model.IdeLibrary
import com.android.tools.idea.gradle.model.IdeLintOptions
import com.android.tools.idea.gradle.model.IdeModuleLibrary
import com.android.tools.idea.gradle.model.IdeProductFlavor
import com.android.tools.idea.gradle.model.IdeProductFlavorContainer
import com.android.tools.idea.gradle.model.IdeSigningConfig
import com.android.tools.idea.gradle.model.IdeTestOptions
import com.android.tools.idea.gradle.model.IdeUnresolvedDependencies
import com.android.tools.idea.gradle.model.IdeVariantBuildInformation
import com.android.tools.idea.gradle.model.IdeViewBindingOptions
import com.android.tools.idea.gradle.model.impl.IdeAaptOptionsImpl
import com.android.tools.idea.gradle.model.impl.IdeAndroidArtifactImpl
import com.android.tools.idea.gradle.model.impl.IdeAndroidArtifactOutputImpl
import com.android.tools.idea.gradle.model.impl.IdeAndroidGradlePluginProjectFlagsImpl
import com.android.tools.idea.gradle.model.impl.IdeAndroidLibraryCore
import com.android.tools.idea.gradle.model.impl.IdeAndroidLibraryImpl
import com.android.tools.idea.gradle.model.impl.IdeAndroidProjectImpl
import com.android.tools.idea.gradle.model.impl.IdeApiVersionImpl
import com.android.tools.idea.gradle.model.impl.IdeBuildTasksAndOutputInformationImpl
import com.android.tools.idea.gradle.model.impl.IdeBuildTypeContainerImpl
import com.android.tools.idea.gradle.model.impl.IdeBuildTypeImpl
import com.android.tools.idea.gradle.model.impl.IdeClassFieldImpl
import com.android.tools.idea.gradle.model.impl.IdeDependenciesImpl
import com.android.tools.idea.gradle.model.impl.IdeDependenciesInfoImpl
import com.android.tools.idea.gradle.model.impl.IdeJavaArtifactImpl
import com.android.tools.idea.gradle.model.impl.IdeJavaCompileOptionsImpl
import com.android.tools.idea.gradle.model.impl.IdeJavaLibraryCore
import com.android.tools.idea.gradle.model.impl.IdeJavaLibraryImpl
import com.android.tools.idea.gradle.model.impl.IdeLintOptionsImpl
import com.android.tools.idea.gradle.model.impl.IdeModuleLibraryCore
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
import com.android.tools.idea.gradle.model.impl.ndk.v1.IdeNativeAndroidProjectImpl
import com.android.tools.idea.gradle.model.impl.ndk.v1.IdeNativeArtifactImpl
import com.android.tools.idea.gradle.model.impl.ndk.v1.IdeNativeFileImpl
import com.android.tools.idea.gradle.model.impl.ndk.v1.IdeNativeSettingsImpl
import com.android.tools.idea.gradle.model.impl.ndk.v1.IdeNativeToolchainImpl
import com.android.tools.idea.gradle.model.impl.ndk.v1.IdeNativeVariantAbiImpl
import com.android.tools.idea.gradle.model.impl.ndk.v1.IdeNativeVariantInfoImpl
import com.android.tools.idea.gradle.model.impl.ndk.v2.IdeNativeAbiImpl
import com.android.tools.idea.gradle.model.impl.ndk.v2.IdeNativeModuleImpl
import com.android.tools.idea.gradle.model.impl.ndk.v2.IdeNativeVariantImpl
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Lists
import java.io.File
import java.util.HashMap

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
  val androidLibraryCores: MutableMap<IdeAndroidLibraryCore, Instances<IdeAndroidLibraryCore, IdeAndroidLibrary>> = HashMap()
  val javaLibraryCores: MutableMap<IdeJavaLibraryCore, Instances<IdeJavaLibraryCore, IdeJavaLibrary>> = HashMap()
  val moduleLibraryCores: MutableMap<IdeModuleLibraryCore, IdeModuleLibraryCore> = HashMap()


  /**
   * Finds an existing or creates a new library instances that wraps the library [core]. When creating a new library for a core for which
   * there is neither regular nor provided library yet generates a unique library name based on its artifact address.
   *
   * Note: Naming mechanism is going to change in the future when dependencies and libraries are separated. We will try to assign more
   * meaningful names to libraries representing different artifact variants under the same Gradle coordinates.
   */
  fun <TCore : IdeArtifactLibrary, TLibrary : IdeArtifactLibrary> MutableMap<TCore, Instances<TCore, TLibrary>>.createOrGetNamedLibrary(
    core: TCore,
    isProvided: Boolean,
    factory: (core: TCore, name: String, isProvided: Boolean) -> TLibrary
  ): TLibrary {
    val instances = computeIfAbsent(core) { Instances(core, null, null) }

    // If both libraries are present their names are expected to match.
    if ((instances.regularLibrary?.name ?: instances.providedLibrary?.name) !=
      (instances.providedLibrary?.name ?: instances.regularLibrary?.name)) {
      error("Regular and provided library names are expected to match. Core: $core")
    }

    return instances.getLibrary(isProvided) ?: let {
      val libraryName =
        instances.regularLibrary?.name
        ?: instances.providedLibrary?.name
        ?: let {
          allocatedLibraryNames.generateLibraryName(core, projectBasePath = buildRootDirectory!!)
        }

      val library = factory(core, libraryName, isProvided)
      instances.setLibrary(isProvided, library)
      library
    }
  }

  fun deduplicateString(s: String): String = strings.putIfAbsent(s, s) ?: s
  fun String.deduplicate() = deduplicateString(this)
  fun deduplicateFile(f: File): File = File(f.path.deduplicate())
  fun sourceProviderFrom(provider: SourceProvider): IdeSourceProviderImpl {
    val folder: File? = provider.manifestFile.parentFile
    fun File.makeRelativeAndDeduplicate(): String = (if (folder != null) relativeToOrSelf(folder) else this).path.deduplicate()
    fun Collection<File>.makeRelativeAndDeduplicate(): Collection<String> = map { it.makeRelativeAndDeduplicate() }
    return IdeSourceProviderImpl(
      myName = provider.name,
      myFolder = folder,
      myManifestFile = provider.manifestFile.makeRelativeAndDeduplicate(),
      myJavaDirectories = provider.javaDirectories.makeRelativeAndDeduplicate(),
      myKotlinDirectories = copy(provider::kotlinDirectories, mapper = { it }).makeRelativeAndDeduplicate(),
      myResourcesDirectories = provider.resourcesDirectories.makeRelativeAndDeduplicate(),
      myAidlDirectories = provider.aidlDirectories?.makeRelativeAndDeduplicate() ?: mutableListOf(),
      myRenderscriptDirectories = provider.renderscriptDirectories?.makeRelativeAndDeduplicate() ?: mutableListOf(),
      myResDirectories = provider.resDirectories?.makeRelativeAndDeduplicate() ?: mutableListOf(),
      myAssetsDirectories = provider.assetsDirectories?.makeRelativeAndDeduplicate() ?: mutableListOf(),
      myJniLibsDirectories = provider.jniLibsDirectories.makeRelativeAndDeduplicate(),
      myShadersDirectories = copy(provider::shadersDirectories, mapper = { it })?.makeRelativeAndDeduplicate() ?: mutableListOf(),
      myMlModelsDirectories = copy(provider::mlModelsDirectories, mapper = { it })?.makeRelativeAndDeduplicate() ?: mutableListOf()
    )
  }

  fun classFieldFrom(classField: ClassField): IdeClassFieldImpl {
    return IdeClassFieldImpl(
      name = classField.name,
      type = classField.type,
      value = classField.value
    )
  }

  fun vectorDrawablesOptionsFrom(options: VectorDrawablesOptions): IdeVectorDrawablesOptionsImpl {
    return IdeVectorDrawablesOptionsImpl(useSupportLibrary = options.useSupportLibrary)
  }

  fun apiVersionFrom(version: ApiVersion): IdeApiVersionImpl {
    return IdeApiVersionImpl(
      apiLevel = version.apiLevel,
      codename = version.codename,
      apiString = version.codename ?: version.apiLevel.toString()
    )
  }

  fun signingConfigFrom(config: SigningConfig): IdeSigningConfigImpl {
    return IdeSigningConfigImpl(
      name = config.name,
      storeFile = config.storeFile,
      storePassword = config.storePassword,
      keyAlias = config.keyAlias
    )
  }

  fun productFlavorFrom(flavor: ProductFlavor): IdeProductFlavorImpl {
    return IdeProductFlavorImpl(
      name = flavor.name,
      resValues = copy(flavor::resValues, ::classFieldFrom) ?: mapOf(),
      proguardFiles = ImmutableList.copyOf(flavor.proguardFiles),
      consumerProguardFiles = ImmutableList.copyOf(flavor.consumerProguardFiles),
      // AGP may return internal Groovy GString implementation as a value in
      // manifestPlaceholders
      // map. It cannot be serialized
      // with IDEA's external system serialization. We convert values to String to
      // make them
      // usable as they are converted to String by
      // the manifest merger anyway.
      manifestPlaceholders = ImmutableMap.copyOf(flavor.manifestPlaceholders.entries.associate { it.key to it.value.toString() }),
      applicationIdSuffix = flavor.applicationIdSuffix,
      versionNameSuffix = copy(flavor::versionNameSuffix),
      multiDexEnabled = copy(flavor::multiDexEnabled),
      testInstrumentationRunnerArguments = ImmutableMap.copyOf(flavor.testInstrumentationRunnerArguments),
      resourceConfigurations = ImmutableList.copyOf(flavor.resourceConfigurations),
      vectorDrawables = copyModel(flavor.vectorDrawables, ::vectorDrawablesOptionsFrom),
      dimension = flavor.dimension,
      applicationId = flavor.applicationId,
      versionCode = flavor.versionCode,
      versionName = flavor.versionName,
      minSdkVersion = copyModel(flavor.minSdkVersion, ::apiVersionFrom),
      targetSdkVersion = copyModel(flavor.targetSdkVersion, ::apiVersionFrom),
      maxSdkVersion = flavor.maxSdkVersion,
      testApplicationId = flavor.testApplicationId,
      testInstrumentationRunner = flavor.testInstrumentationRunner,
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
      applicationId = flavor.applicationId ?: applicationId
      applicationIdSuffix = applicationIdSuffix?.plus(if (flavor.applicationIdSuffix != null) ".${flavor.applicationIdSuffix}" else "")
      versionNameSuffix = versionNameSuffix?.plus(flavor.versionNameSuffix.orEmpty())
    }
    return IdeProductFlavorImpl(
      name = "",
      resValues = resValues,
      proguardFiles = emptyList(),
      consumerProguardFiles = emptyList(),
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
      } else {
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
      } else if (container.name.startsWith("testFixtures")) {
        "_test_fixtures_"
      } else "_unit_test_",
      sourceProvider = copyModel(container, ::sourceProviderFrom)
    )
  }

  fun productFlavorContainerFrom(
    productFlavor: ProductFlavor,
    container: SourceSetContainer): IdeProductFlavorContainerImpl {
    return IdeProductFlavorContainerImpl(
      productFlavor = copyModel(productFlavor, ::productFlavorFrom),
      sourceProvider = copyModel(container.sourceProvider, ::sourceProviderFrom),
      extraSourceProviders = listOfNotNull(
        copyModel(container.androidTestSourceProvider, ::sourceProviderContainerFrom),
        copyModel(container.unitTestSourceProvider, ::sourceProviderContainerFrom),
        copyModel(container.testFixturesSourceProvider, ::sourceProviderContainerFrom)
      )
    )
  }

  fun buildTypeFrom(buildType: BuildType): IdeBuildTypeImpl {
    return IdeBuildTypeImpl(
      name = buildType.name,
      resValues = copy(buildType::resValues, ::classFieldFrom) ?: mapOf(),
      proguardFiles = ImmutableList.copyOf(buildType.proguardFiles),
      consumerProguardFiles = ImmutableList.copyOf(buildType.consumerProguardFiles),
      // AGP may return internal Groovy GString implementation as a value in
      // manifestPlaceholders
      // map. It cannot be serialized
      // with IDEA's external system serialization. We convert values to String to
      // make them
      // usable as they are converted to String by
      // the manifest merger anyway.
      manifestPlaceholders = ImmutableMap.copyOf(buildType.manifestPlaceholders.entries.associate { it.key to it.value.toString() }),
      applicationIdSuffix = buildType.applicationIdSuffix,
      versionNameSuffix = copy(buildType::versionNameSuffix),
      multiDexEnabled = copy(buildType::multiDexEnabled),
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
      buildType = copyModel(buildType, ::buildTypeFrom),
      sourceProvider = copyModel(container.sourceProvider, ::sourceProviderFrom),
      extraSourceProviders = listOfNotNull(
        copyModel(container.androidTestSourceProvider, ::sourceProviderContainerFrom),
        copyModel(container.unitTestSourceProvider, ::sourceProviderContainerFrom),
        copyModel(container.testFixturesSourceProvider, ::sourceProviderContainerFrom)
      )
    )
  }

  fun getV2SymbolFilePath(androidLibrary: Library): String {
    return try {
      androidLibrary.androidLibraryData?.symbolFile?.path ?: ""
    }
    catch (e: UnsupportedOperationException) {
      File(androidLibrary.androidLibraryData?.resFolder?.parentFile, SdkConstants.FN_RESOURCE_TEXT).path
    }
  }

  /**
   * @param androidLibrary Instance returned by android plugin.
   * path to build directory for all modules.
   * @return Instance of [IdeLibrary] based on dependency type.
   */
  fun androidLibraryFrom(androidLibrary: Library, providedLibraries: Set<LibraryIdentity>): IdeLibrary {
    val libraryInfo = androidLibrary.libraryInfo ?: error("libraryInfo missing for ${androidLibrary.key}")

    val androidLibraryData = androidLibrary.androidLibraryData ?: error("androidLibraryData missing for ${androidLibrary.key}")

    val core = IdeAndroidLibraryCore.create(
      artifactAddress = "${libraryInfo.group}:${libraryInfo.name}:${libraryInfo.version}@aar",
      folder = androidLibraryData.resFolder.parentFile ?: File(""), // TODO: verify this always true

      artifact = androidLibrary.artifact ?: File(""),
      lintJar = androidLibrary.lintJar?.path,

      manifest = androidLibraryData.manifest.path ?: "",
      compileJarFiles = androidLibraryData.compileJarFiles.map { it.path },
      runtimeJarFiles = androidLibraryData.runtimeJarFiles.map { it.path },
      resFolder = androidLibraryData.resFolder.path ?: "",
      resStaticLibrary = copy(androidLibraryData::resStaticLibrary),
      assetsFolder = androidLibraryData.assetsFolder.path ?: "",
      jniFolder = androidLibraryData.jniFolder.path ?: "",
      aidlFolder = androidLibraryData.aidlFolder.path ?: "",
      renderscriptFolder = androidLibraryData.renderscriptFolder.path ?: "",
      proguardRules = androidLibraryData.proguardRules.path ?: "",
      externalAnnotations = androidLibraryData.externalAnnotations.path ?: "",
      publicResources = androidLibraryData.publicResources.path ?: "",
      symbolFile = getV2SymbolFilePath(androidLibrary),
      deduplicate = { strings.getOrPut(this) { this } }
    )
    val isProvided = providedLibraries.contains(libraryInfo.toIdentity())
    return androidLibraryCores.createOrGetNamedLibrary(core, isProvided, ::IdeAndroidLibraryImpl)
  }

  /**
   * @param javaLibrary Instance of type [LibraryType.JAVA_LIBRARY] returned by android plugin.
   * @return Instance of [Library] based on dependency type.
   */
  fun javaLibraryFrom(javaLibrary: Library, providedLibraries: Set<LibraryIdentity>): IdeLibrary {
    val libraryInfo = javaLibrary.libraryInfo ?: error("libraryInfo missing for ${javaLibrary.key}")
    val core = IdeJavaLibraryCore(
      artifactAddress = "${libraryInfo.group}:${libraryInfo.name}:${libraryInfo.version}@jar",
      artifact = javaLibrary.artifact!!
    )
    val isProvided = providedLibraries.contains(libraryInfo.toIdentity())
    return javaLibraryCores.createOrGetNamedLibrary(core, isProvided, ::IdeJavaLibraryImpl)
  }

  fun libraryFrom(projectPath: String, buildId: String, variant: String?, lintJar: File?): IdeLibrary {
    val core = IdeModuleLibraryCore(buildId, projectPath, variant, lintJar?.path)
    return IdeModuleLibraryImpl(moduleLibraryCores.internCore(core))
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
    val librariesById = mutableMapOf<String, IdeLibrary>()
    fun createModuleLibrary(
      visited: MutableSet<String>,
      projectPath: String,
      artifactAddress: String,
      variant: String?,
      lintJar: File?,
      buildId: String
    ) {
      if (!visited.contains(artifactAddress)) {
        visited.add(artifactAddress)
        librariesById.computeIfAbsent(artifactAddress) { libraryFrom(projectPath, buildNameMap[buildId]!!.absolutePath, variant, lintJar) }
      }
    }

    fun populateProjectDependencies(libraries: List<Library>, visited: MutableSet<String>) {
      for (identifier in libraries) {
        val projectInfo = identifier.projectInfo!!
        val variantNameResolver = getVariantNameResolver(buildNameMap[projectInfo.buildId]!!, projectInfo.projectPath)
        val variantName = variantNameResolver(
          projectInfo.attributes["com.android.build.api.attributes.BuildTypeAttr"],
          { dimension -> projectInfo.attributes[dimension] ?: error("$dimension attribute not found. Library: ${identifier.key}") }
        )
        createModuleLibrary(
          visited,
          projectInfo.projectPath, // this should always be non-null as this is a module library
          identifier.key,
          variantName,
          identifier.lintJar,
          projectInfo.buildId
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
          librariesById.computeIfAbsent(address) { javaLibraryFrom(javaLibrary, providedLibraries) }
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
    ) : LibrariesByType {
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
          librariesById.computeIfAbsent(address) { androidLibraryFrom(androidLibrary, providedLibraries) }
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
      val androidLibraries = ImmutableList.builder<IdeAndroidLibrary>()
      val javaLibraries = ImmutableList.builder<IdeJavaLibrary>()
      val moduleDependencies = ImmutableList.builder<IdeModuleLibrary>()
      for (address in artifactAddresses) {
        when (val library = librariesById[address]!!) {
          is IdeAndroidLibrary -> androidLibraries.add(library)
          is IdeJavaLibrary -> javaLibraries.add(library)
          is IdeModuleLibrary -> moduleDependencies.add(library)
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

  fun unresolvedDependenciesFrom(unresolvedDependencies: List<UnresolvedDependency>): List<IdeUnresolvedDependencies> {
    return unresolvedDependencies.map { IdeUnresolvedDependenciesImpl(it.name, it.cause) }
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

  fun convertV2ArtifactName(name: String): IdeArtifactName = when (name) {
    "_main_" -> IdeArtifactName.MAIN
    "_android_test_" -> IdeArtifactName.ANDROID_TEST
    "_unit_test_" -> IdeArtifactName.UNIT_TEST
    "_test_fixtures_" -> IdeArtifactName.TEST_FIXTURES
    else -> error("Invalid android artifact name: $name")
  }

  fun androidArtifactFrom(
    name: String,
    basicArtifact: BasicArtifact,
    artifact: AndroidArtifact,
    artifactDependencies: ArtifactDependencies,
    libraries: Map<String, Library>,
    getVariantNameResolver: (buildId: File, projectPath: String) -> VariantNameResolver,
    buildNameMap: Map<String, File>,
  ): IdeAndroidArtifactImpl {
    val testInfo = artifact.testInfo
    val bundleInfo = artifact.bundleInfo
    return IdeAndroidArtifactImpl(
      name = convertV2ArtifactName(name),
      compileTaskName = artifact.compileTaskName,
      assembleTaskName = artifact.assembleTaskName,
      classesFolder = artifact.classesFolders.single(),
      javaResourcesFolder = null,
      ideSetupTaskNames = copy(artifact::ideSetupTaskNames).toList(),
      mutableGeneratedSourceFolders = copy(artifact::generatedSourceFolders, ::deduplicateFile).distinct().toMutableList(),
      variantSourceProvider = copyNewModel(basicArtifact::variantSourceProvider, ::sourceProviderFrom),
      multiFlavorSourceProvider = copyNewModel(basicArtifact::multiFlavorSourceProvider, ::sourceProviderFrom),
      additionalClassesFolders = artifact.additionalClassesFolders,
      level2Dependencies = dependenciesFrom(artifactDependencies, libraries, getVariantNameResolver, buildNameMap),
      unresolvedDependencies = copyNewModel(artifactDependencies::unresolvedDependencies, ::unresolvedDependenciesFrom) ?: emptyList(),
      applicationId = "",
      generatedResourceFolders = copy(artifact::generatedResourceFolders, ::deduplicateFile).distinct(),
      signingConfigName = artifact.signingConfigName,
      abiFilters = ImmutableSet.copyOf(artifact.abiFilters.orEmpty()),
      isSigned = artifact.isSigned,
      additionalRuntimeApks = if (testInfo != null) copy(testInfo::additionalRuntimeApks, ::deduplicateFile) else emptyList(),
      testOptions = copyNewModel(artifact::testInfo, ::testOptionsFrom),
      buildInformation = IdeBuildTasksAndOutputInformationImpl(
        assembleTaskName = artifact.assembleTaskName.deduplicate(),
        assembleTaskOutputListingFile =
        copyNewModel(artifact::assembleTaskOutputListingFile, ::deduplicateFile)?.takeUnless { it.path.isEmpty() }?.path,
        bundleTaskName = if (bundleInfo != null) copyNewModel(bundleInfo::bundleTaskName, ::deduplicateString) else null,
        bundleTaskOutputListingFile = if (bundleInfo != null) {
          copyNewModel(bundleInfo::bundleTaskOutputListingFile, ::deduplicateFile)?.path
        }
        else null,
        apkFromBundleTaskName = if (bundleInfo != null) copyNewModel(bundleInfo::apkFromBundleTaskName, ::deduplicateString) else null,
        apkFromBundleTaskOutputListingFile = if (bundleInfo != null) {
          copyNewModel(bundleInfo::apkFromBundleTaskOutputListingFile, ::deduplicateFile)?.path
        }
        else null
      ),
      codeShrinker = convertCodeShrinker(copy(artifact::codeShrinker)),
      isTestArtifact = name == "_android_test_"
    )
  }

  fun javaArtifactFrom(
    name: String,
    basicArtifact: BasicArtifact,
    artifact: JavaArtifact,
    variantDependencies: ArtifactDependencies,
    libraries: Map<String, Library>,
    getVariantNameResolver: (buildId: File, projectPath: String) -> VariantNameResolver,
    buildNameMap: Map<String, File>
  ): IdeJavaArtifactImpl {
    return IdeJavaArtifactImpl(
      name = convertV2ArtifactName(name),
      compileTaskName = artifact.compileTaskName,
      assembleTaskName = artifact.assembleTaskName,
      classesFolder = artifact.classesFolders.single(),
      javaResourcesFolder = null,
      ideSetupTaskNames = copy(artifact::ideSetupTaskNames, ::deduplicateString).toList(),
      mutableGeneratedSourceFolders = copy(artifact::generatedSourceFolders, ::deduplicateFile).distinct().toMutableList(),
      variantSourceProvider = copyNewModel(basicArtifact::variantSourceProvider, ::sourceProviderFrom),
      multiFlavorSourceProvider = copyNewModel(basicArtifact::multiFlavorSourceProvider, ::sourceProviderFrom),
      additionalClassesFolders = artifact.additionalClassesFolders,
      level2Dependencies = dependenciesFrom(variantDependencies, libraries, getVariantNameResolver, buildNameMap),
      unresolvedDependencies = copyNewModel(variantDependencies::unresolvedDependencies, ::unresolvedDependenciesFrom) ?: emptyList(),
      mockablePlatformJar = copy(artifact::mockablePlatformJar),
      isTestArtifact = name == "_unit_test_"
    )
  }

  fun ideTestedTargetVariantFrom(testedTargetVariant: TestedTargetVariant): IdeTestedTargetVariantImpl = IdeTestedTargetVariantImpl(
    targetProjectPath = testedTargetVariant.targetProjectPath,
    targetVariant = testedTargetVariant.targetVariant
  )

  fun getTestedTargetVariants(variant: Variant): List<IdeTestedTargetVariantImpl> {
    if (variant.testedTargetVariant == null) return emptyList()
    return listOf(copyModel(variant.testedTargetVariant!!, ::ideTestedTargetVariantFrom))
  }

  fun variantFrom(
    androidProject: IdeAndroidProject,
    basicVariant: BasicVariant,
    variant: Variant,
    modelVersion: GradleVersion?,
    variantDependencies: VariantDependencies,
    getVariantNameResolver: (buildId: File, projectPath: String) -> VariantNameResolver,
    buildNameMap: Map<String, File>
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
      name = variant.name,
      displayName = variant.displayName,
      mainArtifact = copyModel(basicVariant.mainArtifact, variant.mainArtifact) { basicArtifact, artifact ->
        androidArtifactFrom(
          name = "_main_",
          basicArtifact = basicArtifact,
          artifact = artifact,
          artifactDependencies = variantDependencies.mainArtifact,
          libraries = variantDependencies.libraries,
          getVariantNameResolver = getVariantNameResolver,
          buildNameMap = buildNameMap
        )
      },
      // If AndroidArtifact isn't null, then same goes for the ArtifactDependencies.
      unitTestArtifact = copyModel(basicVariant.unitTestArtifact, variant.unitTestArtifact) { basicArtifact, artifact ->
        javaArtifactFrom(
          name = "_unit_test_",
          basicArtifact = basicArtifact,
          artifact = artifact,
          variantDependencies = variantDependencies.unitTestArtifact!!,
          libraries = variantDependencies.libraries,
          getVariantNameResolver = getVariantNameResolver,
          buildNameMap = buildNameMap
        )
      },
      androidTestArtifact = copyModel(basicVariant.androidTestArtifact, variant.androidTestArtifact) { basicArtifact, artifact ->
        androidArtifactFrom(
          name = "_android_test_",
          basicArtifact = basicArtifact,
          artifact = artifact,
          artifactDependencies = variantDependencies.androidTestArtifact!!,
          libraries = variantDependencies.libraries,
          getVariantNameResolver = getVariantNameResolver,
          buildNameMap = buildNameMap
        )
      },
      testFixturesArtifact = copyModel(basicVariant.testFixturesArtifact, variant.testFixturesArtifact) { basicArtifact, artifact ->
        androidArtifactFrom(
          name = "_test_fixtures_",
          basicArtifact = basicArtifact,
          artifact = artifact,
          artifactDependencies = variantDependencies.testFixturesArtifact!!,
          libraries = variantDependencies.libraries,
          getVariantNameResolver = getVariantNameResolver,
          buildNameMap = buildNameMap
        )
      },
      buildType = basicVariant.buildType ?: "",
      productFlavors = ImmutableList.copyOf(basicVariant.productFlavors),
      minSdkVersion = copyModel(variant.mainArtifact.minSdkVersion) { apiVersionFrom(it) },
      targetSdkVersion = copyModel(variant.mainArtifact.targetSdkVersionOverride) { apiVersionFrom(it) },
      maxSdkVersion = variant.mainArtifact.maxSdkVersion,
      versionCode = mergedFlavor.versionCode,
      versionNameWithSuffix = mergedFlavor.versionName?.let { it + versionNameSuffix },
      versionNameSuffix = versionNameSuffix,
      instantAppCompatible = (modelVersion != null && variant.isInstantAppCompatible),
      vectorDrawablesUseSupportLibrary = mergedFlavor.vectorDrawables?.useSupportLibrary ?: false,
      resourceConfigurations = mergedFlavor.resourceConfigurations,
      testApplicationId = mergedFlavor.testApplicationId,
      testInstrumentationRunner = mergedFlavor.testInstrumentationRunner,
      testInstrumentationRunnerArguments = mergedFlavor.testInstrumentationRunnerArguments,
      testedTargetVariants = getTestedTargetVariants(variant),
      resValues = merge({ resValues }, { resValues }, ::combineMaps),
      proguardFiles = merge({ proguardFiles }, { proguardFiles }, ::combineSets),
      consumerProguardFiles = merge({ consumerProguardFiles }, { consumerProguardFiles }, ::combineSets),
      manifestPlaceholders = merge({ manifestPlaceholders }, { manifestPlaceholders }, ::combineMaps),
      deprecatedPreMergedApplicationId = null
    )
  }

  fun nativeAbiFrom(nativeAbi: NativeAbi): IdeNativeAbiImpl {
    return IdeNativeAbiImpl(
      name = nativeAbi.name,
      sourceFlagsFile = nativeAbi.sourceFlagsFile,
      symbolFolderIndexFile = nativeAbi.symbolFolderIndexFile,
      buildFileIndexFile = nativeAbi.buildFileIndexFile,
      additionalProjectFilesIndexFile = copy(nativeAbi::additionalProjectFilesIndexFile)
    )
  }

  fun nativeVariantFrom(nativeVariant: NativeVariant): IdeNativeVariantImpl {
    return IdeNativeVariantImpl(
      name = nativeVariant.name,
      abis = copy(nativeVariant::abis, ::nativeAbiFrom)
    )
  }

  fun nativeFileFrom(file: NativeFile): IdeNativeFileImpl {
    return IdeNativeFileImpl(
      filePath = file.filePath,
      settingsName = file.settingsName,
      workingDirectory = file.workingDirectory
    )
  }

  fun nativeArtifactFrom(artifact: NativeArtifact): IdeNativeArtifactImpl {
    return IdeNativeArtifactImpl(
      name = artifact.name,
      toolChain = artifact.toolChain,
      groupName = artifact.groupName,
      sourceFiles = copy(artifact::getSourceFiles, ::nativeFileFrom),
      exportedHeaders = copy(artifact::getExportedHeaders, ::deduplicateFile),
      abi = copy(artifact::getAbi) ?: "",
      targetName = copy(artifact::getTargetName) ?: "",
      outputFile = artifact.outputFile
    )
  }

  fun nativeToolchainFrom(toolchain: NativeToolchain): IdeNativeToolchainImpl {
    return IdeNativeToolchainImpl(
      name = toolchain.name,
      cCompilerExecutable = toolchain.cCompilerExecutable,
      cppCompilerExecutable = toolchain.cppCompilerExecutable
    )
  }

  fun nativeVariantFrom(variantInfo: NativeVariantInfo): IdeNativeVariantInfoImpl {
    return IdeNativeVariantInfoImpl(
      abiNames = copy(variantInfo::getAbiNames, ::deduplicateString),
      buildRootFolderMap = copy(variantInfo::getBuildRootFolderMap, ::deduplicateFile) ?: mapOf()
    )
  }

  fun nativeSettingsFrom(settings: NativeSettings): IdeNativeSettingsImpl {
    return IdeNativeSettingsImpl(
      name = settings.name,
      compilerFlags = copy(settings::getCompilerFlags, ::deduplicateString)
    )
  }

  fun nativeAndroidProjectFrom(project: NativeAndroidProject, ndkVersion: String?): IdeNativeAndroidProjectImpl {
    return IdeNativeAndroidProjectImpl(
      modelVersion = project.modelVersion,
      apiVersion = project.apiVersion,
      name = project.name,
      buildFiles = copy(project::getBuildFiles, ::deduplicateFile),
      variantInfos = copy(project::getVariantInfos, ::nativeVariantFrom),
      artifacts = copy(project::getArtifacts, ::nativeArtifactFrom),
      toolChains = copy(project::getToolChains, ::nativeToolchainFrom),
      settings = copy(project::getSettings, ::nativeSettingsFrom),
      fileExtensions = copy(project::getFileExtensions, ::deduplicateString),
      defaultNdkVersion = copy(project::getDefaultNdkVersion),
      ndkVersion = ndkVersion ?: copy(project::getDefaultNdkVersion),
      buildSystems = copy(project::getBuildSystems, ::deduplicateString)
    )
  }

  fun nativeVariantAbiFrom(variantAbi: NativeVariantAbi): IdeNativeVariantAbiImpl {
    return IdeNativeVariantAbiImpl(
      buildFiles = copy(variantAbi::getBuildFiles, ::deduplicateFile),
      artifacts = copy(variantAbi::getArtifacts, ::nativeArtifactFrom),
      toolChains = copy(variantAbi::getToolChains, ::nativeToolchainFrom),
      settings = copy(variantAbi::getSettings, ::nativeSettingsFrom),
      fileExtensions = copy(variantAbi::getFileExtensions, ::deduplicateString) ?: mapOf(),
      variantName = variantAbi.variantName,
      abi = variantAbi.abi
    )
  }

  fun nativeModuleFrom(nativeModule: NativeModule): IdeNativeModuleImpl {
    return IdeNativeModuleImpl(
      name = nativeModule.name,
      variants = copy(nativeModule::variants, ::nativeVariantFrom),
      nativeBuildSystem = when (nativeModule.nativeBuildSystem) {
        NativeBuildSystem.NDK_BUILD -> com.android.tools.idea.gradle.model.ndk.v2.NativeBuildSystem.NDK_BUILD
        NativeBuildSystem.CMAKE -> com.android.tools.idea.gradle.model.ndk.v2.NativeBuildSystem.CMAKE
        // No forward compatibility. Old Studio cannot open projects with newer AGP.
        else -> error("Unknown native build system: ${nativeModule.nativeBuildSystem}")
      },
      ndkVersion = nativeModule.ndkVersion,
      defaultNdkVersion = nativeModule.defaultNdkVersion,
      externalNativeBuildFile = nativeModule.externalNativeBuildFile
    )
  }

  fun lintOptionsFrom(options: LintOptions, modelVersion: GradleVersion?): IdeLintOptionsImpl = IdeLintOptionsImpl(
    baselineFile = if (modelVersion != null)
      options.baselineFile
    else
      null,
    lintConfig = copy(options::lintConfig),
    severityOverrides = options.severityOverrides?.let { ImmutableMap.copyOf(it) },
    isCheckTestSources = modelVersion != null && options.isCheckTestSources,
    isCheckDependencies = options.isCheckDependencies,
    disable = copy(options::disable, ::deduplicateString),
    enable = copy(options::enable, ::deduplicateString),
    check = options.check?.let { ImmutableSet.copyOf(it) },
    isAbortOnError = options.isAbortOnError,
    isAbsolutePaths = options.isAbsolutePaths,
    isNoLines = options.isNoLines,
    isQuiet = options.isQuiet,
    isCheckAllWarnings = options.isCheckAllWarnings,
    isIgnoreWarnings = options.isIgnoreWarnings,
    isWarningsAsErrors = options.isWarningsAsErrors,
    isIgnoreTestSources = options.isIgnoreTestSources,
    isCheckGeneratedSources = options.isCheckGeneratedSources,
    isExplainIssues = options.isExplainIssues,
    isShowAll = options.isShowAll,
    textReport = options.textReport,
    textOutput = copy(options::textOutput),
    htmlReport = options.htmlReport,
    htmlOutput = copy(options::htmlOutput),
    xmlReport = options.xmlReport,
    xmlOutput = copy(options::xmlOutput),
    isCheckReleaseBuilds = options.isCheckReleaseBuilds
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

  fun buildInformationInfoFrom(variant: Variant): IdeBuildTasksAndOutputInformationImpl = IdeBuildTasksAndOutputInformationImpl(
    assembleTaskName = variant.mainArtifact.assembleTaskName,
    assembleTaskOutputListingFile = variant.mainArtifact.assembleTaskOutputListingFile?.absolutePath,
    bundleTaskName = variant.mainArtifact.bundleInfo?.bundleTaskName,
    bundleTaskOutputListingFile = variant.mainArtifact.bundleInfo?.bundleTaskOutputListingFile?.absolutePath,
    apkFromBundleTaskName = variant.mainArtifact.bundleInfo?.apkFromBundleTaskName,
    apkFromBundleTaskOutputListingFile = variant.mainArtifact.bundleInfo?.apkFromBundleTaskOutputListingFile?.absolutePath
  )

  fun ideVariantBuildInformationFrom(variant: Variant): IdeVariantBuildInformation = IdeVariantBuildInformationImpl(
    variantName = variant.name,
    buildInformation = buildInformationInfoFrom(variant)
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
    val defaultConfigCopy: IdeProductFlavorContainer = copyModel(androidDsl.defaultConfig, basicProject.mainSourceSet,
                                                                 ::productFlavorContainerFrom)
    val buildTypesCopy: Collection<IdeBuildTypeContainer> = copy(androidDsl::buildTypes, basicProject::buildTypeSourceSets,
                                                                 ::buildTypeContainerFrom)
    val productFlavorCopy: Collection<IdeProductFlavorContainer> = copy(androidDsl::productFlavors, basicProject::productFlavorSourceSets,
                                                                        ::productFlavorContainerFrom)
    val variantNamesCopy: Collection<String> = copy(fun(): Collection<String> = project.variants.map { it.name }, ::deduplicateString)
    val flavorDimensionCopy: Collection<String> = copy(androidDsl::flavorDimensions, ::deduplicateString)
    val bootClasspathCopy: Collection<String> = ImmutableList.copyOf(basicProject.bootClasspath.map { it.absolutePath })
    val signingConfigsCopy: Collection<IdeSigningConfig> = copy(androidDsl::signingConfigs, ::signingConfigFrom)
    val lintOptionsCopy: IdeLintOptions = copyModel(androidDsl.lintOptions, { lintOptionsFrom(it, parsedModelVersion) })
    val javaCompileOptionsCopy = copyModel(project.javaCompileOptions, ::javaCompileOptionsFrom)
    val aaptOptionsCopy = copyModel(androidDsl.aaptOptions, ::aaptOptionsFrom)
    val dynamicFeaturesCopy: Collection<String> = copy(project::dynamicFeatures, ::deduplicateString) ?: listOf()
    val variantBuildInformation = createVariantBuildInformation(project, parsedModelVersion)
    val viewBindingOptionsCopy: IdeViewBindingOptions? = copyNewModel(project::viewBindingOptions, ::viewBindingOptionsFrom)
    val dependenciesInfoCopy: IdeDependenciesInfo? = copyNewModel(androidDsl::dependenciesInfo, ::dependenciesInfoFrom)
    val buildToolsVersionCopy = copy(androidDsl::buildToolsVersion)
    val groupId = androidDsl.groupId
    val lintChecksJarsCopy: List<File>? = copy(project::lintChecksJars, ::deduplicateFile)
    val isBaseSplit = basicProject.projectType == ProjectType.APPLICATION
    val agpFlags: IdeAndroidGradlePluginProjectFlags = androidGradlePluginProjectFlagsFrom(project.flags)

    return IdeAndroidProjectImpl(
      modelVersion = modelsVersions.agp,
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
      agpFlags = agpFlags)
  }

  return object : ModelCache {
    override fun variantFrom(
      androidProject: IdeAndroidProject,
      variant: com.android.builder.model.Variant,
      modelVersion: GradleVersion?,
      androidModuleId: ModuleId
    ): IdeVariantImpl = throw UnsupportedOperationException()

    override fun variantFrom(
      androidProject: IdeAndroidProject,
      basicVariant: BasicVariant,
      variant: Variant,
      modelVersion: GradleVersion?,
      variantDependencies: VariantDependencies,
      getVariantNameResolver: (buildId: File, projectPath: String) -> VariantNameResolver,
      buildNameMap: Map<String, File>
    ): IdeVariantImpl = variantFrom(
      androidProject = androidProject,
      basicVariant = basicVariant,
      variant = variant,
      modelVersion = modelVersion,
      variantDependencies = variantDependencies,
      getVariantNameResolver = getVariantNameResolver,
      buildNameMap = buildNameMap
    )

    override fun androidProjectFrom(project: com.android.builder.model.AndroidProject): IdeAndroidProjectImpl =
      throw UnsupportedOperationException()

    override fun androidProjectFrom(
      basicProject: BasicAndroidProject,
      project: AndroidProject,
      androidVersion: Versions,
      androidDsl: AndroidDsl
    ): IdeAndroidProjectImpl = androidProjectFrom(basicProject, project, androidVersion, androidDsl)

    override fun androidArtifactOutputFrom(output: OutputFile): IdeAndroidArtifactOutputImpl =
      throw UnsupportedOperationException("OutputFile is deprecated for AGP 7.0+")

    override fun nativeModuleFrom(nativeModule: NativeModule): IdeNativeModuleImpl = nativeModuleFrom(nativeModule)

    // For native models, if we don't find v2 models, we fall back to V1.
    override fun nativeVariantAbiFrom(variantAbi: NativeVariantAbi): IdeNativeVariantAbiImpl = nativeVariantAbiFrom(variantAbi)

    override fun nativeAndroidProjectFrom(project: NativeAndroidProject, ndkVersion: String): IdeNativeAndroidProjectImpl =
      nativeAndroidProjectFrom(project, ndkVersion)
  }
}

@JvmName("copy1")
private inline fun <T : Any?> copy(propertyInvoker: () -> T?): T? {
  return propertyInvoker()
}

private inline fun <T : Any> copy(propertyInvoker: () -> T): T {
  return propertyInvoker()
}

private inline fun <K, V> copy(original: () -> Collection<K>?, mapper: (K) -> V): List<V>? =
  ModelCache.safeGet(original, null)?.map(mapper)

private inline fun <K, V, R> copy(original: () -> Map<K, V>?, mapper: (V) -> R): Map<K, R>? =
  ModelCache.safeGet(original, mapOf())?.mapValues { (_, v) -> mapper(v) }

private inline fun <K, R, V> copy(o1: () -> Collection<K>, o2: () -> Collection<R>, mapper: (K, R) -> V): List<V> {
  val original1 = ModelCache.safeGet(o1, listOf())
  val original2 = ModelCache.safeGet(o2, listOf())
  return original1.zip(original2).toMap().map { (k, v) -> mapper(k, v) }
}

private class Instances<TCore, TLibrary>(
  val core: TCore,
  var regularLibrary: TLibrary? = null,
  var providedLibrary: TLibrary? = null,
) {
    fun getLibrary(isProvided: Boolean): TLibrary? = if (isProvided) providedLibrary else regularLibrary
    fun setLibrary(isProvided: Boolean, library: TLibrary) {
      if (isProvided) providedLibrary = library else regularLibrary = library
    }
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
