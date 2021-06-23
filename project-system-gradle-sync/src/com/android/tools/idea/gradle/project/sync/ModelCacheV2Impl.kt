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
import com.android.builder.model.v2.ide.BaseArtifact
import com.android.builder.model.v2.ide.GraphItem
import com.android.builder.model.v2.ide.JavaArtifact
import com.android.builder.model.v2.ide.JavaCompileOptions
import com.android.builder.model.v2.ide.Library
import com.android.builder.model.v2.ide.LibraryType
import com.android.builder.model.v2.ide.LintOptions
import com.android.builder.model.v2.ide.ProjectType
import com.android.builder.model.v2.ide.SourceProvider
import com.android.builder.model.v2.ide.SourceSetContainer
import com.android.builder.model.v2.ide.TestInfo
import com.android.builder.model.v2.ide.TestedTargetVariant
import com.android.builder.model.v2.ide.Variant
import com.android.builder.model.v2.ide.VectorDrawablesOptions
import com.android.builder.model.v2.ide.ViewBindingOptions
import com.android.builder.model.v2.models.AndroidDsl
import com.android.builder.model.v2.models.AndroidProject
import com.android.builder.model.v2.models.GlobalLibraryMap
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
import com.android.tools.idea.gradle.model.IdeVariantBuildInformation
import com.android.tools.idea.gradle.model.IdeViewBindingOptions
import com.android.tools.idea.gradle.model.impl.BuildFolderPaths
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
import com.intellij.util.containers.addIfNotNull
import java.io.File
import java.util.HashMap

internal fun modelCacheV2Impl(buildFolderPaths: BuildFolderPaths): ModelCache {
  val strings: MutableMap<String, String> = HashMap()
  val androidLibraryCores: MutableMap<IdeAndroidLibraryCore, IdeAndroidLibraryCore> = HashMap()
  val javaLibraryCores: MutableMap<IdeJavaLibraryCore, IdeJavaLibraryCore> = HashMap()
  val moduleLibraryCores: MutableMap<IdeModuleLibraryCore, IdeModuleLibraryCore> = HashMap()

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

  // TODO(b/188413335): we shouldn't be looking for patterns in the path. Rework classesFolder on IDE side.
  fun classFolderFrom(classesFolders: Set<File>): File {
    return classesFolders.first { it.absolutePath.contains("/javac/") }
  }

  // TODO(b/188413335): we shouldn't be looking for patterns in the path. Rework classesFolder on IDE side.
  fun additionalClassesFoldersFrom(classesFolders: Set<File>): List<File> {
    return classesFolders.filter { !it.absolutePath.contains("/javac/") && !it.absolutePath.contains("/java_res/") }.distinct()
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
    productFlavors: List<IdeProductFlavor>
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
      applicationId = applicationId?.plus(if (applicationIdSuffix != null) ".${applicationIdSuffix}" else ""),
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
      // As we no longer have ArtifactMetaData, we use hardcoded values for androidTests and unitTests artifacts.

      artifactName = if (container.name.startsWith("androidTest")) "_android_test_" else "_unit_test_",
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
        copyModel(container.unitTestSourceProvider, ::sourceProviderContainerFrom)
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
        copyModel(container.unitTestSourceProvider, ::sourceProviderContainerFrom)
      )
    )
  }

  fun getV2SymbolFilePath(androidLibrary: Library): String {
    return try {
      androidLibrary.symbolFile?.path ?: ""
    }
    catch (e: UnsupportedOperationException) {
      File(androidLibrary.resFolder?.parentFile, SdkConstants.FN_RESOURCE_TEXT).path
    }
  }

  /**
   * @param androidLibrary Instance returned by android plugin.
   * path to build directory for all modules.
   * @return Instance of [IdeLibrary] based on dependency type.
   */
  fun androidLibraryFrom(androidLibrary: Library, providedLibraries: List<Library>): IdeLibrary {
    val core = IdeAndroidLibraryCore.create(
      artifactAddress = androidLibrary.artifactAddress,
      folder = androidLibrary.resFolder?.parentFile ?: File(""), // TODO: verify this always true

      manifest = androidLibrary.manifest?.path ?: "",
      compileJarFiles = androidLibrary.compileJarFiles!!.map { it.path },
      runtimeJarFiles = androidLibrary.runtimeJarFiles!!.map { it.path },
      resFolder = androidLibrary.resFolder?.path ?: "",
      resStaticLibrary = copy(androidLibrary::resStaticLibrary),
      assetsFolder = androidLibrary.assetsFolder?.path ?: "",
      jniFolder = androidLibrary.jniFolder?.path ?: "",
      aidlFolder = androidLibrary.aidlFolder?.path ?: "",
      renderscriptFolder = androidLibrary.renderscriptFolder?.path ?: "",
      proguardRules = androidLibrary.proguardRules?.path ?: "",
      lintJar = androidLibrary.lintJar?.path ?: "",
      externalAnnotations = androidLibrary.externalAnnotations?.path ?: "",
      publicResources = androidLibrary.publicResources?.path ?: "",
      artifact = androidLibrary.artifact ?: File(""),
      symbolFile = getV2SymbolFilePath(androidLibrary),
      deduplicate = { strings.getOrPut(this) { this } }
    )
    val isProvided = providedLibraries.contains(androidLibrary)
    return IdeAndroidLibraryImpl(androidLibraryCores.internCore(core), isProvided)
  }

  /**
   * @param javaLibrary Instance of type [LibraryType.JAVA_LIBRARY] returned by android plugin.
   * @return Instance of [Library] based on dependency type.
   */
  fun javaLibraryFrom(javaLibrary: Library, providedLibraries: List<Library>): IdeLibrary {
    val core = IdeJavaLibraryCore(
      artifactAddress = javaLibrary.artifactAddress,
      artifact = javaLibrary.artifact!!
    )
    val isProvided = providedLibraries.contains(javaLibrary)
    return IdeJavaLibraryImpl(javaLibraryCores.internCore(core), isProvided)
  }

  fun libraryFrom(projectPath: String, buildId: String?, variant: String?, lintJar: File?): IdeLibrary {
    val core = IdeModuleLibraryCore(buildId, projectPath, variant, lintJar?.path)
    return IdeModuleLibraryImpl(moduleLibraryCores.internCore(core))
  }

  fun createFromDependencies(dependencies: ArtifactDependencies, libraryMap: GlobalLibraryMap): IdeDependencies {
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
      buildId: String?
    ) {
      if (!visited.contains(artifactAddress)) {
        visited.add(artifactAddress)
        librariesById.computeIfAbsent(artifactAddress) { libraryFrom(projectPath, buildId, variant, lintJar) }
      }
    }

    fun populateProjectDependencies(libraries: List<Library>, visited: MutableSet<String>) {
      for (identifier in libraries) {
        createModuleLibrary(
          visited,
          identifier.projectPath!!, // this should always be non-null as this is a module library
          identifier.artifactAddress,
          identifier.variant,
          identifier.lintJar,
          identifier.buildId
        )
      }
    }

    fun populateJavaLibraries(
      javaLibraries: Collection<Library>,
      providedLibraries: List<Library>,
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

    fun getTypedLibraries(
      dependencies: List<GraphItem>,
      libraryMap: GlobalLibraryMap,
      androidLibraries: MutableList<Library>,
      javaLibraries: MutableList<Library>,
      projectLibraries: MutableList<Library>
    ) {
      for (dependency in dependencies) {
        val library = libraryMap.libraries[dependency.artifactAddress] ?: continue
        if (library.type == LibraryType.ANDROID_LIBRARY && !androidLibraries.contains(library)) androidLibraries.add(
          library)  // Can we check by Library object here ?
        if (library.type == LibraryType.JAVA_LIBRARY && !javaLibraries.contains(library)) javaLibraries.add(library)
        if (library.type == LibraryType.PROJECT && !projectLibraries.contains(library)) projectLibraries.add(library)
        // Get transitive dependencies as well.
        getTypedLibraries(dependency.dependencies, libraryMap, androidLibraries, javaLibraries, projectLibraries)
      }
      return
    }

    fun getRuntimeLibraries(
      runtimeDependencies: List<GraphItem>?,
      compileDependencies: List<GraphItem>?,
      libraryMap: GlobalLibraryMap
    ): List<File> {
      // Get runtimeOnly libraries: this means libraries that are not available in the compile graph.
      fun getRuntimeLibraries(runtimeDependencies: List<GraphItem>?,
                              compileDependenciesArtifacts: List<String>?,
                              runtimeLibraries: MutableList<File>) {
        if (runtimeDependencies == null) return
        for (dependency in runtimeDependencies) {
          // Filter out dependencies included in the compile graph.
          if (compileDependenciesArtifacts?.contains(dependency.artifactAddress) == true) continue
          val library = libraryMap.libraries[dependency.artifactAddress] ?: continue
          // TODO(b/189109819) : We need artifact address for runtime libraries as well.
          if (library.type != LibraryType.PROJECT && library.artifact != null) runtimeLibraries.add(library.artifact!!)
          // Get transitive dependencies.
          getRuntimeLibraries(dependency.dependencies, compileDependenciesArtifacts, runtimeLibraries)
        }
      }

      val runtimeLibraries = mutableListOf<File>()
      getRuntimeLibraries(runtimeDependencies, compileDependencies?.map { it.artifactAddress }, runtimeLibraries)

      return runtimeLibraries
    }

    fun populateAndroidLibraries(
      androidLibraries: Collection<Library>,
      providedLibraries: List<Library>,
      visited: MutableSet<String>
    ) {
      for (androidLibrary in androidLibraries) {
        val address = androidLibrary.artifactAddress
        if (!visited.contains(address)) {
          visited.add(address)
          librariesById.computeIfAbsent(address) { androidLibraryFrom(androidLibrary, providedLibraries) }
        }
      }
    }

    fun getProvidedLibraries(
      compileDependencies: List<GraphItem>,
      runtimeDependencies: List<GraphItem>?,
      libraryMap: GlobalLibraryMap
    ): List<Library> {
      val providedDependencies = mutableListOf<Library>()
      if (runtimeDependencies != null) {
        for (graphItem in compileDependencies) {
          if (!runtimeDependencies.contains(graphItem)) {
            providedDependencies.addIfNotNull(libraryMap.libraries[graphItem.artifactAddress])
          }
        }
      }
      else {
        compileDependencies.forEach {
          providedDependencies.addIfNotNull(libraryMap.libraries[it.artifactAddress])
        }
      }
      return providedDependencies
    }

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
      val androidLibraries = mutableListOf<Library>()
      val javaLibraries = mutableListOf<Library>()
      val projectLibraries = mutableListOf<Library>()
      getTypedLibraries(dependencies.compileDependencies, libraryMap, androidLibraries, javaLibraries, projectLibraries)
      val providedLibraries = getProvidedLibraries(dependencies.compileDependencies, dependencies.runtimeDependencies, libraryMap)
      populateAndroidLibraries(androidLibraries, providedLibraries, visited)
      populateJavaLibraries(javaLibraries, providedLibraries, visited)
      populateProjectDependencies(projectLibraries, visited)
      val runtimeLibraries = getRuntimeLibraries(dependencies.runtimeDependencies, dependencies.compileDependencies, libraryMap)
      return createIdeDependencies(visited, runtimeLibraries)
    }
    return createIdeDependenciesInstance()
  }

  /**
   * Create [IdeDependencies] from [BaseArtifact].
   */
  fun dependenciesFrom(artifactDependencies: ArtifactDependencies, libraryMap: GlobalLibraryMap): IdeDependencies {
    return createFromDependencies(artifactDependencies, libraryMap)
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
    else -> error("Invalid android artifact name: $name")
  }

  fun androidArtifactFrom(
    name: String,
    artifact: AndroidArtifact,
    artifactDependencies: ArtifactDependencies,
    libraryMap: GlobalLibraryMap
  ): IdeAndroidArtifactImpl {
    val testInfo = artifact.testInfo
    val bundleInfo = artifact.bundleInfo
    return IdeAndroidArtifactImpl(
      name = convertV2ArtifactName(name),
      compileTaskName = artifact.compileTaskName,
      assembleTaskName = artifact.assembleTaskName,
      classesFolder = classFolderFrom(artifact.classesFolders),
      javaResourcesFolder = null,
      ideSetupTaskNames = copy(artifact::ideSetupTaskNames).toList(),
      mutableGeneratedSourceFolders = copy(artifact::generatedSourceFolders, ::deduplicateFile).distinct().toMutableList(),
      variantSourceProvider = copyNewModel(artifact::variantSourceProvider, ::sourceProviderFrom),
      multiFlavorSourceProvider = copyNewModel(artifact::multiFlavorSourceProvider, ::sourceProviderFrom),
      additionalClassesFolders = additionalClassesFoldersFrom(artifact.classesFolders),
      level2Dependencies = dependenciesFrom(artifactDependencies, libraryMap),
      outputs = emptyList(),  // this is a deprecated property.

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
    artifact: JavaArtifact,
    variantDependencies: ArtifactDependencies,
    libraryMap: GlobalLibraryMap
  ): IdeJavaArtifactImpl {
    return IdeJavaArtifactImpl(
      name = convertV2ArtifactName(name),
      compileTaskName = artifact.compileTaskName,
      assembleTaskName = artifact.assembleTaskName,
      classesFolder = classFolderFrom(artifact.classesFolders),
      javaResourcesFolder = null,
      ideSetupTaskNames = copy(artifact::ideSetupTaskNames, ::deduplicateString).toList(),
      mutableGeneratedSourceFolders = copy(artifact::generatedSourceFolders, ::deduplicateFile).distinct().toMutableList(),
      variantSourceProvider = copyNewModel(artifact::variantSourceProvider, ::sourceProviderFrom),
      multiFlavorSourceProvider = copyNewModel(artifact::multiFlavorSourceProvider, ::sourceProviderFrom),
      additionalClassesFolders = additionalClassesFoldersFrom(artifact.classesFolders).toList(),
      level2Dependencies = dependenciesFrom(variantDependencies, libraryMap),
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
    variant: Variant,
    modelVersion: GradleVersion?,
    variantDependencies: VariantDependencies,
    libraryMap: GlobalLibraryMap
  ): IdeVariantImpl {
    // To get merged flavors for V2, we merge flavors from default config and all the flavors.
    val mergedFlavor = mergeProductFlavorsFrom(
      androidProject.defaultConfig.productFlavor,
      androidProject.productFlavors.map { it.productFlavor }.filter { variant.productFlavors.contains(it.name) }.toList()
    )

    val buildType = androidProject.buildTypes.find { it.buildType.name == variant.buildType }?.buildType

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
      mainArtifact = copyModel(variant.mainArtifact) { androidArtifactFrom("_main_", it, variantDependencies.mainArtifact, libraryMap) },
      // If AndroidArtifact isn't null, then same goes for the ArtifactDependencies.
      androidTestArtifact = copyModel(variant.androidTestArtifact) {
        androidArtifactFrom("_android_test_", it, variantDependencies.androidTestArtifact!!, libraryMap)
      },
      unitTestArtifact = copyModel(variant.unitTestArtifact) {
        javaArtifactFrom("_unit_test_", it, variantDependencies.unitTestArtifact!!, libraryMap)
      },
      buildType = variant.buildType ?: "",
      productFlavors = ImmutableList.copyOf(variant.productFlavors),
      minSdkVersion = copyModel(variant.mainArtifact.minSdkVersion) { apiVersionFrom(it) },
      targetSdkVersion = copyModel(variant.mainArtifact.targetSdkVersion) { apiVersionFrom(it) },
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
      deprecatedPreMergedApplicationId = mergedFlavor.applicationId ?: androidProject.namespace
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

  fun Map<AndroidGradlePluginProjectFlags.BooleanFlag, Boolean>.getV2BooleanFlag(
    flag: AndroidGradlePluginProjectFlags.BooleanFlag
  ): Boolean = this[flag] ?: flag.legacyDefault

  fun createV2IdeAndroidGradlePluginProjectFlagsImpl(
    booleanFlagMap: Map<AndroidGradlePluginProjectFlags.BooleanFlag, Boolean>?
  ): IdeAndroidGradlePluginProjectFlagsImpl {
    return if (booleanFlagMap != null) {
      IdeAndroidGradlePluginProjectFlagsImpl(
        applicationRClassConstantIds =
        booleanFlagMap.getV2BooleanFlag(AndroidGradlePluginProjectFlags.BooleanFlag.APPLICATION_R_CLASS_CONSTANT_IDS),
        testRClassConstantIds = booleanFlagMap.getV2BooleanFlag(AndroidGradlePluginProjectFlags.BooleanFlag.TEST_R_CLASS_CONSTANT_IDS),
        transitiveRClasses = booleanFlagMap.getV2BooleanFlag(AndroidGradlePluginProjectFlags.BooleanFlag.TRANSITIVE_R_CLASS),
        usesCompose = booleanFlagMap.getV2BooleanFlag(AndroidGradlePluginProjectFlags.BooleanFlag.JETPACK_COMPOSE),
        mlModelBindingEnabled = booleanFlagMap.getV2BooleanFlag(AndroidGradlePluginProjectFlags.BooleanFlag.ML_MODEL_BINDING)
      )
    }
    else {
      IdeAndroidGradlePluginProjectFlagsImpl(
        applicationRClassConstantIds = false,
        testRClassConstantIds = false,
        transitiveRClasses = false,
        usesCompose = false,
        mlModelBindingEnabled = false
      )
    }
  }

  fun androidGradlePluginProjectFlagsFrom(flags: AndroidGradlePluginProjectFlags): IdeAndroidGradlePluginProjectFlagsImpl =
    createV2IdeAndroidGradlePluginProjectFlagsImpl(flags.booleanFlagMap)

  fun copyProjectType(projectType: ProjectType): IdeAndroidProjectType = when (projectType) {
    // TODO(b/187504821): is the number of supported project type in V2 reduced ? this is a restricted list compared to V1.

    ProjectType.APPLICATION -> IdeAndroidProjectType.PROJECT_TYPE_APP
    ProjectType.LIBRARY -> IdeAndroidProjectType.PROJECT_TYPE_LIBRARY
    ProjectType.TEST -> IdeAndroidProjectType.PROJECT_TYPE_TEST
    ProjectType.DYNAMIC_FEATURE -> IdeAndroidProjectType.PROJECT_TYPE_DYNAMIC_FEATURE
    else -> error("Unknown Android project type: $projectType")
  }

  fun androidProjectFrom(project: AndroidProject, modelsVersions: Versions, androidDsl: AndroidDsl): IdeAndroidProjectImpl {
    val parsedModelVersion = GradleVersion.tryParse(modelsVersions.agp)
    val defaultConfigCopy: IdeProductFlavorContainer = copyModel(androidDsl.defaultConfig, project.mainSourceSet,
                                                                 ::productFlavorContainerFrom)
    val buildTypesCopy: Collection<IdeBuildTypeContainer> = copy(androidDsl::buildTypes, project::buildTypeSourceSets,
                                                                 ::buildTypeContainerFrom)
    val productFlavorCopy: Collection<IdeProductFlavorContainer> = copy(androidDsl::productFlavors, project::productFlavorSourceSets,
                                                                        ::productFlavorContainerFrom)
    val variantNamesCopy: Collection<String> = copy(fun(): Collection<String> = project.variants.map { it.name }, ::deduplicateString)
    val flavorDimensionCopy: Collection<String> = copy(androidDsl::flavorDimensions, ::deduplicateString)
    val bootClasspathCopy: Collection<String> = ImmutableList.copyOf(project.bootClasspath.map { it.absolutePath })
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
    val lintRuleJarsCopy: List<File>? = copy(project::lintRuleJars, ::deduplicateFile)
    val isBaseSplit = project.projectType == ProjectType.APPLICATION
    val agpFlags: IdeAndroidGradlePluginProjectFlags = androidGradlePluginProjectFlagsFrom(project.flags)

    return IdeAndroidProjectImpl(
      modelVersion = modelsVersions.agp,
      name = project.path,
      defaultConfig = defaultConfigCopy,
      buildTypes = buildTypesCopy,
      productFlavors = productFlavorCopy,
      variantNames = variantNamesCopy,
      flavorDimensions = flavorDimensionCopy,
      compileTarget = androidDsl.compileTarget,
      bootClasspath = bootClasspathCopy,
      signingConfigs = signingConfigsCopy,
      lintOptions = lintOptionsCopy,
      lintRuleJars = lintRuleJarsCopy,
      javaCompileOptions = javaCompileOptionsCopy,
      aaptOptions = aaptOptionsCopy,
      buildFolder = project.buildFolder,
      dynamicFeatures = dynamicFeaturesCopy,
      variantsBuildInformation = variantBuildInformation,
      viewBindingOptions = viewBindingOptionsCopy,
      dependenciesInfo = dependenciesInfoCopy,
      buildToolsVersion = buildToolsVersionCopy,
      resourcePrefix = project.resourcePrefix,
      groupId = groupId,
      namespace = project.namespace,
      testNamespace = project.androidTestNamespace,
      projectType = copyProjectType(project.projectType),
      isBaseSplit = isBaseSplit,
      agpFlags = agpFlags)
  }

  return object : ModelCache {
    override fun variantFrom(
      androidProject: IdeAndroidProject,
      variant: com.android.builder.model.Variant,
      modelVersion: GradleVersion?,
      androidModulesIds: List<ModuleId>
    ): IdeVariantImpl = throw UnsupportedOperationException()

    override fun variantFrom(
      androidProject: IdeAndroidProject,
      variant: Variant,
      modelVersion: GradleVersion?,
      variantDependencies: VariantDependencies,
      libraryMap: GlobalLibraryMap
    ): IdeVariantImpl = variantFrom(androidProject, variant, modelVersion, variantDependencies, libraryMap)

    override fun androidProjectFrom(project: com.android.builder.model.AndroidProject): IdeAndroidProjectImpl =
      throw UnsupportedOperationException()

    override fun androidProjectFrom(
      project: AndroidProject,
      androidVersion: Versions,
      androidDsl: AndroidDsl
    ): IdeAndroidProjectImpl = androidProjectFrom(project, androidVersion, androidDsl)

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
