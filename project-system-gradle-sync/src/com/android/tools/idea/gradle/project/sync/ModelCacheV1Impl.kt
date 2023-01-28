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

import com.android.AndroidProjectTypes
import com.android.SdkConstants
import com.android.build.FilterData
import com.android.build.OutputFile
import com.android.build.VariantOutput
import com.android.builder.model.AaptOptions
import com.android.builder.model.AndroidArtifact
import com.android.builder.model.AndroidGradlePluginProjectFlags
import com.android.builder.model.AndroidLibrary
import com.android.builder.model.AndroidProject
import com.android.builder.model.ApiVersion
import com.android.builder.model.BaseArtifact
import com.android.builder.model.BuildType
import com.android.builder.model.BuildTypeContainer
import com.android.builder.model.ClassField
import com.android.builder.model.Dependencies
import com.android.builder.model.DependenciesInfo
import com.android.builder.model.JavaArtifact
import com.android.builder.model.JavaCompileOptions
import com.android.builder.model.JavaLibrary
import com.android.builder.model.Library
import com.android.builder.model.LintOptions
import com.android.builder.model.MavenCoordinates
import com.android.builder.model.NativeAndroidProject
import com.android.builder.model.NativeArtifact
import com.android.builder.model.NativeFile
import com.android.builder.model.NativeSettings
import com.android.builder.model.NativeToolchain
import com.android.builder.model.NativeVariantAbi
import com.android.builder.model.NativeVariantInfo
import com.android.builder.model.ProductFlavor
import com.android.builder.model.ProductFlavorContainer
import com.android.builder.model.SigningConfig
import com.android.builder.model.SourceProvider
import com.android.builder.model.SourceProviderContainer
import com.android.builder.model.SyncIssue
import com.android.builder.model.TestOptions
import com.android.builder.model.TestedTargetVariant
import com.android.builder.model.Variant
import com.android.builder.model.VariantBuildInformation
import com.android.builder.model.VectorDrawablesOptions
import com.android.builder.model.ViewBindingOptions
import com.android.builder.model.v2.models.ndk.NativeAbi
import com.android.builder.model.v2.models.ndk.NativeBuildSystem
import com.android.builder.model.v2.models.ndk.NativeModule
import com.android.builder.model.v2.models.ndk.NativeVariant
import com.android.ide.common.repository.AgpVersion
import com.android.ide.gradle.model.GradlePropertiesModel
import com.android.ide.gradle.model.LegacyApplicationIdModel
import com.android.tools.idea.gradle.model.CodeShrinker
import com.android.tools.idea.gradle.model.IdeAaptOptions
import com.android.tools.idea.gradle.model.IdeAndroidProjectType
import com.android.tools.idea.gradle.model.IdeArtifactName
import com.android.tools.idea.gradle.model.IdeDependencies
import com.android.tools.idea.gradle.model.IdeFilterData
import com.android.tools.idea.gradle.model.IdeMavenCoordinates
import com.android.tools.idea.gradle.model.IdeModuleWellKnownSourceSet
import com.android.tools.idea.gradle.model.IdeSyncIssue
import com.android.tools.idea.gradle.model.IdeTestOptions
import com.android.tools.idea.gradle.model.IdeUnresolvedLibrary
import com.android.tools.idea.gradle.model.LibraryReference
import com.android.tools.idea.gradle.model.impl.BuildFolderPaths
import com.android.tools.idea.gradle.model.impl.IdeAaptOptionsImpl
import com.android.tools.idea.gradle.model.impl.IdeAndroidArtifactCoreImpl
import com.android.tools.idea.gradle.model.impl.IdeAndroidArtifactOutputImpl
import com.android.tools.idea.gradle.model.impl.IdeAndroidGradlePluginProjectFlagsImpl
import com.android.tools.idea.gradle.model.impl.IdeAndroidLibraryImpl
import com.android.tools.idea.gradle.model.impl.IdeAndroidProjectImpl
import com.android.tools.idea.gradle.model.impl.IdeApiVersionImpl
import com.android.tools.idea.gradle.model.impl.IdeBasicVariantImpl
import com.android.tools.idea.gradle.model.impl.IdeBuildTasksAndOutputInformationImpl
import com.android.tools.idea.gradle.model.impl.IdeBuildTypeContainerImpl
import com.android.tools.idea.gradle.model.impl.IdeBuildTypeImpl
import com.android.tools.idea.gradle.model.impl.IdeClassFieldImpl
import com.android.tools.idea.gradle.model.impl.IdeDependenciesCoreImpl
import com.android.tools.idea.gradle.model.impl.IdeDependenciesInfoImpl
import com.android.tools.idea.gradle.model.impl.IdeDependencyCoreImpl
import com.android.tools.idea.gradle.model.impl.IdeFilterDataImpl
import com.android.tools.idea.gradle.model.impl.IdeJavaArtifactCoreImpl
import com.android.tools.idea.gradle.model.impl.IdeJavaCompileOptionsImpl
import com.android.tools.idea.gradle.model.impl.IdeJavaLibraryImpl
import com.android.tools.idea.gradle.model.impl.IdeLintOptionsImpl
import com.android.tools.idea.gradle.model.impl.IdeMavenCoordinatesImpl
import com.android.tools.idea.gradle.model.impl.IdeMultiVariantDataImpl
import com.android.tools.idea.gradle.model.impl.IdePreResolvedModuleLibraryImpl
import com.android.tools.idea.gradle.model.impl.IdeProductFlavorContainerImpl
import com.android.tools.idea.gradle.model.impl.IdeProductFlavorImpl
import com.android.tools.idea.gradle.model.impl.IdeProjectPathImpl
import com.android.tools.idea.gradle.model.impl.IdeSigningConfigImpl
import com.android.tools.idea.gradle.model.impl.IdeExtraSourceProviderImpl
import com.android.tools.idea.gradle.model.impl.IdeSourceProviderContainerImpl
import com.android.tools.idea.gradle.model.impl.IdeSourceProviderImpl
import com.android.tools.idea.gradle.model.impl.IdeSyncIssueImpl
import com.android.tools.idea.gradle.model.impl.IdeTestOptionsImpl
import com.android.tools.idea.gradle.model.impl.IdeTestedTargetVariantImpl
import com.android.tools.idea.gradle.model.impl.IdeUnresolvedLibraryTableImpl
import com.android.tools.idea.gradle.model.impl.IdeVariantBuildInformationImpl
import com.android.tools.idea.gradle.model.impl.IdeVariantCoreImpl
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
import com.android.tools.idea.gradle.model.impl.throwingIdeDependencies
import com.android.tools.idea.gradle.project.sync.ModelCache.Companion.LOCAL_AARS
import com.android.tools.idea.gradle.project.sync.ModelCache.Companion.LOCAL_JARS
import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import java.io.File
import java.io.FileFilter
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal fun modelCacheV1Impl(internedModels: InternedModels, buildFolderPaths: BuildFolderPaths, lock: ReentrantLock): ModelCache.V1 {

  fun deduplicateString(s: String): String = internedModels.intern(s)
  fun String.deduplicate() = internedModels.intern(this)
  fun deduplicateFile(f: File): File = File(f.path.deduplicate())

  fun sourceProviderFrom(provider: SourceProvider, mlModelBindingEnabled: Boolean): IdeSourceProviderImpl {
    val folder: File? = provider.manifestFile.parentFile

    fun File.makeRelativeAndDeduplicate(): String = (if (folder != null) relativeToOrSelf(folder) else this).path.deduplicate()
    fun Collection<File>.makeRelativeAndDeduplicate(): Collection<String> = map { it.makeRelativeAndDeduplicate() }

    return IdeSourceProviderImpl(
      myName = provider.name,
      myFolder = folder,
      myManifestFile = provider.manifestFile.makeRelativeAndDeduplicate(),
      myJavaDirectories = provider.javaDirectories.makeRelativeAndDeduplicate(),
      myKotlinDirectories = copy(provider::getKotlinDirectories, mapper = { it }).makeRelativeAndDeduplicate(),
      myResourcesDirectories = provider.resourcesDirectories.makeRelativeAndDeduplicate(),
      myAidlDirectories = provider.aidlDirectories.makeRelativeAndDeduplicate(),
      myRenderscriptDirectories = provider.renderscriptDirectories.makeRelativeAndDeduplicate(),
      myResDirectories = provider.resDirectories.makeRelativeAndDeduplicate(),
      myAssetsDirectories = provider.assetsDirectories.makeRelativeAndDeduplicate(),
      myJniLibsDirectories = provider.jniLibsDirectories.makeRelativeAndDeduplicate(),
      myShadersDirectories = copy(provider::getShadersDirectories, mapper = { it }).makeRelativeAndDeduplicate(),
      myMlModelsDirectories =
      if (mlModelBindingEnabled) copy(provider::getMlModelsDirectories, mapper = { it }).makeRelativeAndDeduplicate() else emptyList(),
      myCustomSourceDirectories = emptyList(),
      myBaselineProfileDirectories = emptyList(),
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

  fun copyVectorDrawables(flavor: ProductFlavor): IdeVectorDrawablesOptionsImpl? {
    val vectorDrawables: VectorDrawablesOptions = try {
      flavor.vectorDrawables
    } catch (e: UnsupportedOperationException) {
      return null
    }
    return copyModel(vectorDrawables, ::vectorDrawablesOptionsFrom)
  }

  fun apiVersionFrom(version: ApiVersion): IdeApiVersionImpl {
    return IdeApiVersionImpl(apiLevel = version.apiLevel, codename = version.codename, apiString = version.apiString)
  }

  fun signingConfigFrom(config: SigningConfig): IdeSigningConfigImpl {
    return IdeSigningConfigImpl(
      name = config.getName(),
      storeFile = config.storeFile,
      storePassword = config.storePassword,
      keyAlias = config.keyAlias
    )
  }

  fun productFlavorFrom(flavor: ProductFlavor): IdeProductFlavorImpl {
    return IdeProductFlavorImpl(
      name = flavor.name,
      resValues = copy(flavor::resValues, ::classFieldFrom),
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
      versionNameSuffix = copyNewProperty(flavor::versionNameSuffix),
      multiDexEnabled = copyNewProperty(flavor::multiDexEnabled),
      testInstrumentationRunnerArguments = ImmutableMap.copyOf(flavor.testInstrumentationRunnerArguments),
      resourceConfigurations = ImmutableList.copyOf(flavor.resourceConfigurations),
      vectorDrawables = copyVectorDrawables(flavor),
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

  fun extraSourceProviderFrom(container: SourceProviderContainer, mlModelBindingEnabled: Boolean): IdeExtraSourceProviderImpl {
    return IdeExtraSourceProviderImpl(
      artifactName = container.artifactName,
      sourceProvider = copyModel(container.sourceProvider, mlModelBindingEnabled, ::sourceProviderFrom)
    )
  }

  fun sourceProviderContainerFrom(
    container: ProductFlavorContainer,
    mlModelBindingEnabled: Boolean
  ): IdeSourceProviderContainerImpl {
    fun extraSourceProviderFrom(container: SourceProviderContainer) = extraSourceProviderFrom(container, mlModelBindingEnabled)

    return IdeSourceProviderContainerImpl(
      sourceProvider = container.sourceProvider?.let { copyModel(it, mlModelBindingEnabled, ::sourceProviderFrom) },
      extraSourceProviders = copy(container::getExtraSourceProviders, ::extraSourceProviderFrom)
    )
  }

  fun productFlavorContainerFrom(
    container: ProductFlavorContainer,
    mlModelBindingEnabled: Boolean
  ): IdeProductFlavorContainerImpl {
    fun extraSourceProviderFrom(container: SourceProviderContainer) = extraSourceProviderFrom(container, mlModelBindingEnabled)

    return IdeProductFlavorContainerImpl(
      productFlavor = copyModel(container.productFlavor, ::productFlavorFrom),
      sourceProvider = container.sourceProvider?.let { copyModel(it, mlModelBindingEnabled, ::sourceProviderFrom) },
      extraSourceProviders = copy(container::getExtraSourceProviders, ::extraSourceProviderFrom)
    )
  }


  fun buildTypeFrom(buildType: BuildType): IdeBuildTypeImpl {
    return IdeBuildTypeImpl(
      name = buildType.name,
      resValues = copy(buildType::resValues, ::classFieldFrom),
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
      versionNameSuffix = copyNewProperty(buildType::versionNameSuffix),
      multiDexEnabled = copyNewProperty(buildType::multiDexEnabled),
      isDebuggable = buildType.isDebuggable,
      isJniDebuggable = buildType.isJniDebuggable,
      isRenderscriptDebuggable = buildType.isRenderscriptDebuggable,
      renderscriptOptimLevel = buildType.renderscriptOptimLevel,
      isMinifyEnabled = buildType.isMinifyEnabled,
      isZipAlignEnabled = buildType.isZipAlignEnabled
    )
  }

  fun buildTypeContainerFrom(container: BuildTypeContainer, mlModelBindingEnabled: Boolean): IdeBuildTypeContainerImpl {
    fun sourceProviderContainerFrom(container: SourceProviderContainer) = extraSourceProviderFrom(container, mlModelBindingEnabled)

    return IdeBuildTypeContainerImpl(
      buildType = copyModel(container.buildType, ::buildTypeFrom),
      sourceProvider = container.sourceProvider?.let { copyModel(it, mlModelBindingEnabled, ::sourceProviderFrom) },
      extraSourceProviders = copy(container::getExtraSourceProviders, ::sourceProviderContainerFrom)
    )
  }

  fun createIdeModuleLibrary(library: AndroidLibrary, projectPath: String): LibraryReference {
    val rootBuildFile = File(copyNewProperty(library::getBuildId) ?: buildFolderPaths.rootBuildId!!)
    val buildId = BuildId(rootBuildFile)
    val moduleLibrary = IdePreResolvedModuleLibraryImpl(
      buildId = buildId.asString,
      projectPath = projectPath,
      variant = copyNewProperty(library::getProjectVariant),
      lintJar = copyNewProperty(library::getLintJar)?.path?.let(::File),
      sourceSet = IdeModuleWellKnownSourceSet.MAIN
    )
    return internedModels.getOrCreate(moduleLibrary)
  }

  fun createIdeModuleLibrary(library: JavaLibrary, projectPath: String): LibraryReference {
    val rootBuildFile = File(copyNewProperty(library::getBuildId) ?: buildFolderPaths.rootBuildId!!)
    val buildId = BuildId(rootBuildFile)
    val moduleLibrary = IdePreResolvedModuleLibraryImpl(
      buildId = buildId.asString,
      projectPath = projectPath,
      variant = null,
      lintJar = null,
      sourceSet = IdeModuleWellKnownSourceSet.MAIN
    )
    return internedModels.getOrCreate(moduleLibrary)
  }

  fun mavenCoordinatesFrom(coordinates: MavenCoordinates): IdeMavenCoordinatesImpl {
    return IdeMavenCoordinatesImpl(
      groupId = coordinates.groupId,
      artifactId = coordinates.artifactId,
      version = coordinates.version,
      packaging = coordinates.packaging,
      classifier = coordinates.classifier
    )
  }

  fun mavenCoordinatesFrom(localJar: File): IdeMavenCoordinatesImpl {
    return IdeMavenCoordinatesImpl(
      groupId = LOCAL_AARS,
      artifactId = localJar.path,
      version = "unspecified",
      packaging = "jar",
      classifier = null
    )
  }


  fun computeResolvedCoordinate(library: Library): IdeMavenCoordinatesImpl {
    // Although getResolvedCoordinates is annotated with @NonNull, it can return null for plugin 1.5,
    // when the library dependency is from local jar.
    @Suppress("SENSELESS_COMPARISON")
    return if (library.resolvedCoordinates != null) {
      mavenCoordinatesFrom(library.resolvedCoordinates)
    } else {
      val jarFile: File =
        if (library is JavaLibrary) {
          library.jarFile
        } else {
          (library as AndroidLibrary).bundle
        }
      mavenCoordinatesFrom(jarFile)
    }
  }

  /**
   * @param projectIdentifier Instance of ProjectIdentifier.
   * @return The artifact address that can be used as unique identifier in global library map.
   */
  fun computeAddress(projectIdentifier: Dependencies.ProjectIdentifier): String {
    return projectIdentifier.buildId + "@@" + projectIdentifier.projectPath
  }

  /**
   * @param library Instance of level 1 Library.
   * @return The artifact address that can be used as unique identifier in global library map.
   */
  fun computeAddress(library: Library): String {
    // If the library is an android module dependency, use projectId:projectPath::variant as unique identifier.
    // MavenCoordinates cannot be used because it doesn't contain variant information, which results
    // in the same MavenCoordinates for different variants of the same module.
    if (library.project != null && library is AndroidLibrary) {
      return ((copyNewProperty(library::getBuildId)).orEmpty()
        + library.getProject()
        + "::"
        + library.projectVariant)
    }
    val coordinate: IdeMavenCoordinates = computeResolvedCoordinate(library)
    var artifactId = coordinate.artifactId
    if (artifactId.startsWith(":")) {
      artifactId = artifactId.substring(1)
    }
    artifactId = artifactId.replace(':', '.')
    var address = coordinate.groupId + ":" + artifactId + ":" + coordinate.version
    val classifier = coordinate.classifier
    if (classifier != null) {
      address = "$address:$classifier"
    }
    val packaging = coordinate.packaging
    address = "$address@$packaging"
    return address
  }

  fun getSymbolFilePath(androidLibrary: AndroidLibrary): String {
    return try {
      androidLibrary.symbolFile.path
    } catch (e: UnsupportedOperationException) {
      File(androidLibrary.folder, SdkConstants.FN_RESOURCE_TEXT).path
    }
  }

  class IdeDependencyCoreAndIsProvided(val dependency: IdeDependencyCoreImpl, val isProvided: Boolean)

  fun makeDependency(
    libraryReference: LibraryReference,
    isProvided: Boolean
  ) = IdeDependencyCoreAndIsProvided(IdeDependencyCoreImpl(libraryReference, null), isProvided)

  fun libraryFrom(androidLibrary: AndroidLibrary): IdeDependencyCoreAndIsProvided {
    // If the dependency is a sub-module that wraps local aar, it should be considered as external dependency, i.e. type LIBRARY_ANDROID.
    // In AndroidLibrary, getProject() of such dependency returns non-null project name, but they should be converted to IdeLevel2AndroidLibrary.
    // Identify such case with the location of aar bundle.
    // If the aar bundle is inside of build directory of sub-module, then it's regular library module dependency, otherwise it's a wrapped aar module.
    val projectPath = androidLibrary.project
    return if (projectPath != null && !isLocalAarModule(buildFolderPaths, androidLibrary)) {
      makeDependency(createIdeModuleLibrary(androidLibrary, projectPath), isProvided = false)
    } else {
      val artifactAddress = computeAddress(androidLibrary)
      val unnamedLibrary = IdeAndroidLibraryImpl.create(
        artifactAddress = artifactAddress,
        name = "",
        folder = androidLibrary.folder,
        manifest = androidLibrary.manifest.path,
        compileJarFiles = listOfNotNull(
          copyNewProperty(androidLibrary::getCompileJarFile)?.path
        ) + androidLibrary.localJars.map { it.path },
        runtimeJarFiles = listOf(androidLibrary.jarFile.path) + androidLibrary.localJars.map { it.path },
        resFolder = androidLibrary.resFolder.path,
        resStaticLibrary = copyNewProperty(androidLibrary::getResStaticLibrary),
        assetsFolder = androidLibrary.assetsFolder.path,
        jniFolder = androidLibrary.jniFolder.path,
        aidlFolder = androidLibrary.aidlFolder.path,
        renderscriptFolder = androidLibrary.renderscriptFolder.path,
        proguardRules = androidLibrary.proguardRules.path,
        lintJar = androidLibrary.lintJar.path,
        srcJar = null,
        docJar = null,
        samplesJar = null,
        externalAnnotations = androidLibrary.externalAnnotations.path,
        publicResources = androidLibrary.publicResources.path,
        artifact = androidLibrary.bundle,
        symbolFile = getSymbolFilePath(androidLibrary),
        deduplicate = { deduplicate() }
      )
      val isProvided = copyNewProperty(androidLibrary::isProvided, false)

      makeDependency(internedModels.getOrCreate(unnamedLibrary), isProvided)
    }
  }

  fun libraryFrom(javaLibrary: JavaLibrary): IdeDependencyCoreAndIsProvided {
    val project = copyNewProperty(javaLibrary::getProject)
    return if (project != null) {
      // Java modules don't have variant.
      makeDependency(createIdeModuleLibrary(javaLibrary, project), isProvided = false)
    } else {
      val artifactAddress = computeAddress(javaLibrary)
      val unnamedLibrary = IdeJavaLibraryImpl(
        artifactAddress = artifactAddress,
        name = "",
        artifact = javaLibrary.jarFile,
        srcJar = null,
        docJar = null,
        samplesJar = null
      )
      val isProvided = copyNewProperty(javaLibrary::isProvided, false)

      makeDependency(internedModels.getOrCreate(unnamedLibrary), isProvided)
    }
  }

  fun libraryFrom(jarFile: File): IdeDependencyCoreAndIsProvided {
    val artifactAddress = "${ModelCache.LOCAL_JARS}:" + jarFile.path + ":unspecified"
    val unnamedLibrary = IdeJavaLibraryImpl(artifactAddress, "", jarFile, null, null, null)
    return makeDependency(internedModels.getOrCreate(unnamedLibrary), false)
  }

  fun libraryFrom(projectPath: String, buildId: String, variantName: String?): IdeDependencyCoreAndIsProvided {
    val buildIdFile = File(buildId)
    val core = IdePreResolvedModuleLibraryImpl(
      buildId = BuildId(buildIdFile).asString,
      projectPath = projectPath,
      variant = variantName,
      lintJar = null,
      sourceSet = IdeModuleWellKnownSourceSet.MAIN
    )
    return makeDependency(internedModels.getOrCreate(core), isProvided = false)
  }

  fun createFromDependencies(
    dependencies: Dependencies,
    variantName: String?,
    androidModuleId: ModuleId?,
    bootClasspath: Collection<String>
  ): IdeModelWithPostProcessor<IdeDependenciesCoreImpl> {
    // Map from unique artifact address to level2 library instance. The library instances are
    // supposed to be shared by all artifacts. When creating IdeLevel2Dependencies, check if current library is available in this map,
    // if it's available, don't create new one, simple add reference to it. If it's not available, create new instance and save
    // to this map, so it can be reused the next time when the same library is added.
    val dependenciesById = mutableMapOf<String, IdeDependencyCoreAndIsProvided>()

    fun createModuleLibrary(
      visited: MutableSet<String>,
      projectPath: String,
      artifactAddress: String,
      buildId: String,
      variantName: String?
    ) {
      if (visited.add(artifactAddress)) {
        dependenciesById.computeIfAbsent(artifactAddress) { libraryFrom(projectPath, buildId, variantName) }
      }
    }

    fun populateModuleDependencies(
      dependencies: Dependencies,
      visited: MutableSet<String>,
      variantName: String?,
      androidModuleId: ModuleId?
    ) {
      for (identifier in dependencies.javaModules) {
        createModuleLibrary(
          visited,
          identifier.projectPath,
          computeAddress(identifier),
          identifier.buildId,
          if (androidModuleId != null &&
            androidModuleId.gradlePath == identifier.projectPath &&
            androidModuleId.buildId == identifier.buildId
          ) {
            variantName
          } else {
            null
          }
        )
      }
    }

    fun populateJavaLibraries(
      javaLibraries: Collection<JavaLibrary>,
      visited: MutableSet<String>
    ) {
      for (javaLibrary in javaLibraries) {
        val address = computeAddress(javaLibrary)
        if (visited.add(address)) {
          dependenciesById.computeIfAbsent(address) { libraryFrom(javaLibrary) }
          if (javaLibrary.dependencies.isNotEmpty()) error("JavaLibrary.dependencies is expected to be empty")
        }
      }
    }

    fun populateBootclasspathLibrariesLibraries(
      bootClasspath: Collection<String>,
      visited: MutableSet<String>
    ) {
      getUsefulBootClasspathLibraries(bootClasspath).forEach { jarFile ->
        val address = jarFile.path
        if (visited.add(jarFile.path)) {   // Any unique key identifying the library  is suitable.
          dependenciesById.computeIfAbsent(address) { libraryFrom(jarFile) }
        }
      }
    }

    fun populateAndroidLibraries(
      androidLibraries: Collection<AndroidLibrary>,
      visited: MutableSet<String>
    ) {
      for (androidLibrary in androidLibraries) {
        val address = computeAddress(androidLibrary)
        if (visited.add(address)) {
          dependenciesById.computeIfAbsent(address) { libraryFrom(androidLibrary) }
          if (androidLibrary.libraryDependencies.isNotEmpty()) error("AndroidLibrary.libraryDependencies is expected to be empty")
          if (androidLibrary.javaDependencies.isNotEmpty()) error("AndroidLibrary.javaDependencies is expected to be empty")
        }
      }
    }

    /**
     * Wraps a `.jar` file into a library assuming it comes from an old version of Gradle/AGP, which do not support models V2 and thus
     * the layout of such libraries in the Gradle cache is known.
     *
     * This is needed to support Android resources in projects using older AGP versions correctly.
     */
    fun wrapIntoLibrary(jarFile: File): LibraryReference {
      val jarsDir: File? = jarFile.parentFile
      val aarLibraryDir = jarsDir?.parentFile?.absoluteFile
      val manifestFile = aarLibraryDir?.resolve("AndroidManifest.xml")?.absoluteFile
      return if (manifestFile?.isFile == true) {
        val apiJarFile = aarLibraryDir.resolve("api.jar").takeIf { it.isFile }
        val runtimeJarFiles =
          if (jarsDir.isDirectory) {
            jarsDir
              .listFiles(FileFilter { it.name.endsWith(".jar") })
              ?.map { it.absolutePath }
              .orEmpty()
          } else {
            listOf(jarFile.absolutePath)
          }
        internedModels.getOrCreate(
          IdeAndroidLibraryImpl.create(
            // NOTE: [artifactAddress] needs to be in this form to meet LintModelFactory expectations.
            artifactAddress = "$LOCAL_AARS:" + jarFile.path + ":unspecified",
            name = "",
            folder = aarLibraryDir,
            manifest = manifestFile.absolutePath,
            compileJarFiles = apiJarFile?.let { listOf(it.absolutePath) } ?: runtimeJarFiles,
            runtimeJarFiles = runtimeJarFiles,
            resFolder = aarLibraryDir.resolve("res").absolutePath,
            resStaticLibrary = aarLibraryDir.resolve("res.apk").takeIf { it.exists() },
            assetsFolder = aarLibraryDir.resolve("assets").absolutePath,
            jniFolder = aarLibraryDir.resolve("jni").absolutePath,
            aidlFolder = aarLibraryDir.resolve("aidl").absolutePath,
            renderscriptFolder = aarLibraryDir.resolve("rs").absolutePath,
            proguardRules = aarLibraryDir.resolve("proguard.txt").absolutePath,
            lintJar = null,
            srcJar = null,
            docJar = null,
            samplesJar = null,
            externalAnnotations = aarLibraryDir.resolve("annotations.zip").absolutePath,
            publicResources = aarLibraryDir.resolve("public.txt").absolutePath,
            artifact = null,
            symbolFile = aarLibraryDir.resolve("R.txt").absolutePath,
            deduplicate = internedModels::intern
          )
        )
      } else {
        // NOTE: [artifactAddress] needs to be in this form to meet LintModelFactory expectations.
        internedModels.getOrCreate(IdeJavaLibraryImpl("$LOCAL_JARS:" + jarFile.path + ":unspecified", "", jarFile, null, null, null))
      }
    }

    fun createInstance(
      artifactAddresses: Collection<String>,
      runtimeOnlyClasses: Collection<File>
    ): IdeModelWithPostProcessor<IdeDependenciesCoreImpl> {
      return IdeModelWithPostProcessor(
        IdeDependenciesCoreImpl(
          dependencies = artifactAddresses.map { address -> dependenciesById[address]!!.dependency }
        ),
        postProcessor = fun(): IdeDependenciesCoreImpl {
          val refByArtifact = internedModels.artifactToLibraryReferenceMap ?: error("ModelCache.prepare() hasn't been called.")

          // (1) Any compile classpath dependencies are runtime classpath dependencies, if they are not `isProvided`.
          val regularRuntimeNotProvidedLibraryDependencies =
            artifactAddresses
              .map { address -> dependenciesById[address]!! }
              .filter { !it.isProvided }
              .map { it.dependency }

          // (2) Assume any runtime only classes pointing to existing libraries come from these libraries.
          val existingLibrariesReferredToByRuntimeOnlyClasses =
            runtimeOnlyClasses
              .mapNotNull { refByArtifact[it] }
              .distinct()

          // (3) Wrap any other runtime only classes into library instances.
          val librariesRecoveredFromRuntimeOnlyClasses =
            runtimeOnlyClasses
              .filter { refByArtifact[it] == null } // Not included in the previous step.
              .map(::wrapIntoLibrary)
              .distinct()

          val runtimeDependenciesRecoveredFromRuntimeOnlyClasses =
            (existingLibrariesReferredToByRuntimeOnlyClasses + librariesRecoveredFromRuntimeOnlyClasses)
              .map {
                IdeDependencyCoreImpl(it, null)
              }

          return IdeDependenciesCoreImpl(regularRuntimeNotProvidedLibraryDependencies + runtimeDependenciesRecoveredFromRuntimeOnlyClasses)
        }
      )
    }

    fun createInstance(): IdeModelWithPostProcessor<IdeDependenciesCoreImpl> {

      val runtimeOnlyClasses: Collection<File> = try {
        dependencies.runtimeOnlyClasses
      } catch (e: UnsupportedOperationException) {
        // Gradle older than 3.4.
        emptyList()
      }

      val visited = mutableSetOf<String>()
      populateAndroidLibraries(dependencies.libraries, visited)
      populateJavaLibraries(dependencies.javaLibraries, visited)
      populateBootclasspathLibrariesLibraries(bootClasspath, visited)
      populateModuleDependencies(dependencies, visited, variantName, androidModuleId)
      return createInstance(visited, runtimeOnlyClasses)
    }

    return createInstance()
  }

  /**
   * Create [IdeDependencies] from [BaseArtifact].
   */
  fun dependenciesFrom(
    artifact: BaseArtifact,
    variantName: String?,
    androidModuleId: ModuleId?,
    bootClasspath: Collection<String>
  ): IdeModelWithPostProcessor<IdeDependenciesCoreImpl> {
    return createFromDependencies(artifact.dependencies, variantName, androidModuleId, bootClasspath)
  }

  fun filterDataFrom(data: FilterData): IdeFilterDataImpl {
    return IdeFilterDataImpl(identifier = data.identifier, filterType = data.filterType)
  }

  fun copyFilters(output: VariantOutput): Collection<IdeFilterData> {
    return copy(
      fun(): Collection<FilterData> =
        try {
          output.filters
        } catch (ignored: UnsupportedOperationException) {
          output.outputs.flatMap(OutputFile::getFilters)
        },
      ::filterDataFrom
    )
  }

  fun androidArtifactOutputFrom(output: OutputFile): IdeAndroidArtifactOutputImpl {
    return IdeAndroidArtifactOutputImpl(
      filters = copyFilters(output),
      versionCode = output.versionCode,
      outputFile = copyNewProperty({ output.outputFile }, output.mainOutputFile.outputFile)
    )
  }

  fun convertExecution(execution: TestOptions.Execution?): IdeTestOptions.Execution? {
    return if (execution == null) null
    else when (execution) {
      TestOptions.Execution.HOST -> IdeTestOptions.Execution.HOST
      TestOptions.Execution.ANDROID_TEST_ORCHESTRATOR -> IdeTestOptions.Execution.ANDROID_TEST_ORCHESTRATOR
      TestOptions.Execution.ANDROIDX_TEST_ORCHESTRATOR -> IdeTestOptions.Execution.ANDROIDX_TEST_ORCHESTRATOR
      else -> throw IllegalStateException("Unknown execution option: $execution")
    }
  }

  fun testOptionsFrom(testOptions: TestOptions): IdeTestOptionsImpl {
    return IdeTestOptionsImpl(
      animationsDisabled = testOptions.animationsDisabled,
      execution = convertExecution(testOptions.execution)
    )
  }

  fun convertCodeShrinker(codeShrinker: com.android.builder.model.CodeShrinker?): CodeShrinker? {
    return if (codeShrinker == null) null
    else when (codeShrinker) {
      com.android.builder.model.CodeShrinker.PROGUARD -> CodeShrinker.PROGUARD
      com.android.builder.model.CodeShrinker.R8 -> CodeShrinker.R8
      else -> throw IllegalStateException("Unknown code shrinker option: $codeShrinker")
    }
  }

  fun androidArtifactFrom(
    artifact: AndroidArtifact,
    bootClasspath: Collection<String>,
    agpVersion: AgpVersion?,
    variantName: String?,
    variantNameForDependencies: String?,
    androidModuleId: ModuleId?,
    legacyApplicationIdModel: LegacyApplicationIdModel?,
    mlModelBindingEnabled: Boolean,
    projectType: IdeAndroidProjectType
  ): IdeModelWithPostProcessor<IdeAndroidArtifactCoreImpl> {
    fun sourceProviderFrom(provider: SourceProvider) = sourceProviderFrom(provider, mlModelBindingEnabled)
    val isAppMainArtifact = artifact.name == AndroidProject.ARTIFACT_MAIN && projectType == IdeAndroidProjectType.PROJECT_TYPE_APP

    val dependencies = dependenciesFrom(artifact, variantNameForDependencies, androidModuleId, bootClasspath)
    val ideArtifactName = convertArtifactName(artifact.name)
    // Legacy feature plugins are both library-like, and application/dynamic-feature-like
    // (with separate 'Feature'-suffixed variants for the application/dynamic-feature-like functionality)
    // From the IDE perspective attach the <variant>Feature applicationId to the library-like variant.
    val apkVariantName = if (projectType == IdeAndroidProjectType.PROJECT_TYPE_FEATURE) variantName + "Feature" else variantName
    val applicationId = when (ideArtifactName) {
      // NB: the model will not be available for things that are not applicable, e.g. library and dynamic feature main
      IdeArtifactName.MAIN -> legacyApplicationIdModel?.componentToApplicationIdMap?.get(apkVariantName)
      IdeArtifactName.ANDROID_TEST -> legacyApplicationIdModel?.componentToApplicationIdMap?.get(variantName + "AndroidTest")
      IdeArtifactName.UNIT_TEST, IdeArtifactName.TEST_FIXTURES -> null
    }
    val androidArtifactCoreImpl = IdeAndroidArtifactCoreImpl(
      name = ideArtifactName,
      compileTaskName = artifact.compileTaskName,
      assembleTaskName = artifact.assembleTaskName,
      classesFolder = listOf(listOf(artifact.classesFolder), artifact.additionalClassesFolders.sorted()).flatten(),
      ideSetupTaskNames = copyNewPropertyWithDefault(artifact::getIdeSetupTaskNames,
                                                     defaultValue = { setOf(artifact.sourceGenTaskName) }).toList(),
      generatedSourceFolders = copy(
        artifact::getGeneratedSourceFolders,
        ::deduplicateFile
      ).distinct(), // The source model can contain duplicates.
      variantSourceProvider = copyNewModel(artifact::getVariantSourceProvider, ::sourceProviderFrom),
      multiFlavorSourceProvider = copyNewModel(artifact::getMultiFlavorSourceProvider, ::sourceProviderFrom),
      compileClasspathCore = dependencies.model,
      runtimeClasspathCore = throwingIdeDependencies(),
      unresolvedDependencies = emptyList(),
      applicationId = applicationId,
      generatedResourceFolders = copy(artifact::getGeneratedResourceFolders, ::deduplicateFile).distinct(),
      signingConfigName = artifact.signingConfigName,
      abiFilters = ImmutableSet.copyOf( // In AGP 4.0 and below abiFilters was nullable, normalize null to empty set.
        artifact.abiFilters.orEmpty()
      ),
      isSigned = artifact.isSigned,
      additionalRuntimeApks = copy(artifact::getAdditionalRuntimeApks, ::deduplicateFile),
      testOptions = copyNewModel(artifact::getTestOptions, ::testOptionsFrom),
      buildInformation = IdeBuildTasksAndOutputInformationImpl(
        assembleTaskName = artifact.assembleTaskName.deduplicate(),
        assembleTaskOutputListingFile =
        copyNewModel(artifact::getAssembleTaskOutputListingFile, ::deduplicateString)?.takeUnless { it.isEmpty() },
        // BundleTaskName is only applicable for the main artifact of an APPLICATION project.
        bundleTaskName = if (isAppMainArtifact) copyNewModel(artifact::getBundleTaskName, ::deduplicateString) else null,
        bundleTaskOutputListingFile = copyNewModel(artifact::getBundleTaskOutputListingFile, ::deduplicateString),
        // apkFromBundleTaskName is only applicable for the main artifact of an APPLICATION project.
        apkFromBundleTaskName = if (isAppMainArtifact) copyNewModel(artifact::getApkFromBundleTaskName, ::deduplicateString) else null,
        apkFromBundleTaskOutputListingFile = copyNewModel(artifact::getApkFromBundleTaskOutputListingFile, ::deduplicateString),
      ),
      codeShrinker = convertCodeShrinker(copyNewProperty(artifact::getCodeShrinker)),
      isTestArtifact = artifact.name == AndroidProject.ARTIFACT_ANDROID_TEST,
      modelSyncFiles = listOf(),
      privacySandboxSdkInfo = null,
      desugaredMethodsFiles = emptyList()
    )
    return IdeModelWithPostProcessor(
      androidArtifactCoreImpl,
      postProcessor = fun(): IdeAndroidArtifactCoreImpl {
        return androidArtifactCoreImpl.copy(runtimeClasspathCore = dependencies.postProcess())
      }
    )
  }

  fun javaArtifactFrom(
    artifact: JavaArtifact,
    bootClasspath: Collection<String>,
    variantName: String?,
    androidModuleId: ModuleId,
    mlModelBindingEnabled: Boolean
  ): IdeModelWithPostProcessor<IdeJavaArtifactCoreImpl> {
    fun sourceProviderFrom(provider: SourceProvider) = sourceProviderFrom(provider, mlModelBindingEnabled)

    val dependencies = dependenciesFrom(artifact, variantName, androidModuleId, bootClasspath)
    val javaArtifactCoreImpl = IdeJavaArtifactCoreImpl(
      name = convertArtifactName(artifact.name),
      compileTaskName = artifact.compileTaskName,
      assembleTaskName = artifact.assembleTaskName,
      classesFolder = listOf(artifact.classesFolder) + artifact.additionalClassesFolders.sorted(),
      ideSetupTaskNames = copy(artifact::getIdeSetupTaskNames, ::deduplicateString).toList(),
      generatedSourceFolders = copy(artifact::getGeneratedSourceFolders, ::deduplicateFile).distinct(),
      variantSourceProvider = copyNewModel(artifact::getVariantSourceProvider, ::sourceProviderFrom),
      multiFlavorSourceProvider = copyNewModel(artifact::getMultiFlavorSourceProvider, ::sourceProviderFrom),
      compileClasspathCore = dependencies.model,
      runtimeClasspathCore = throwingIdeDependencies(),
      unresolvedDependencies = emptyList(),
      mockablePlatformJar = copyNewProperty(artifact::getMockablePlatformJar),
      isTestArtifact = artifact.name == AndroidProject.ARTIFACT_UNIT_TEST
    )
    return IdeModelWithPostProcessor(
      javaArtifactCoreImpl,
      postProcessor = fun(): IdeJavaArtifactCoreImpl {
        return javaArtifactCoreImpl.copy(runtimeClasspathCore = dependencies.postProcess())
      }
    )
  }

  fun getTestedTargetVariants(variant: Variant): List<IdeTestedTargetVariantImpl> {
    return try {
      copy(variant::getTestedTargetVariants) { targetVariant: TestedTargetVariant ->
        IdeTestedTargetVariantImpl(
          targetProjectPath = targetVariant.targetProjectPath,
          targetVariant = targetVariant.targetVariant
        )
      }
    } catch (e: UnsupportedOperationException) {
      emptyList()
    }
  }

  fun variantFrom(
    androidProject: IdeAndroidProjectImpl,
    variant: Variant,
    legacyApplicationIdModel: LegacyApplicationIdModel?,
    modelVersion: AgpVersion?,
    androidModuleId: ModuleId
  ): ModelResult<IdeVariantWithPostProcessor> {
    val mergedFlavor = copyModel(variant.mergedFlavor, ::productFlavorFrom)
    val buildType = androidProject.multiVariantData?.buildTypes.orEmpty().find { it.buildType.name == variant.buildType }?.buildType

    fun <T> merge(f: IdeProductFlavorImpl.() -> T, b: IdeBuildTypeImpl.() -> T, combine: (T?, T?) -> T): T {
      return combine(mergedFlavor.f(), buildType?.b())
    }

    fun <T> combineMaps(u: Map<String, T>?, v: Map<String, T>?): Map<String, T> = u.orEmpty() + v.orEmpty()
    fun <T> combineSets(u: Collection<T>?, v: Collection<T>?): Collection<T> = (u?.toSet().orEmpty() + v.orEmpty()).toList()

    val versionNameSuffix =
      if (mergedFlavor.versionNameSuffix == null && buildType?.versionNameSuffix == null) null
      else mergedFlavor.versionNameSuffix.orEmpty() + buildType?.versionNameSuffix.orEmpty()
    val mainArtifact = copyModel(variant.mainArtifact) {
      androidArtifactFrom(
        artifact = it,
        bootClasspath = androidProject.bootClasspath,
        agpVersion = modelVersion,
        variantName = variant.name,
        // For main artifacts, we shouldn't use the variant's name in module dependencies, but Test projects are an exception because
        // we only have one main artifact that is a test artifact, so we need to handle this as a special case.
        variantNameForDependencies = if (androidProject.projectType == IdeAndroidProjectType.PROJECT_TYPE_TEST) variant.name else null,
        androidModuleId = if (androidProject.projectType == IdeAndroidProjectType.PROJECT_TYPE_TEST) androidModuleId else null,
        legacyApplicationIdModel = legacyApplicationIdModel,
        mlModelBindingEnabled = androidProject.agpFlags.mlModelBindingEnabled,
        projectType = androidProject.projectType
      )
    }

    val unitTestArtifact = copy(variant::getExtraJavaArtifacts) {
      javaArtifactFrom(
        artifact = it,
        bootClasspath = androidProject.bootClasspath,
        variantName = variant.name,
        androidModuleId = androidModuleId,
        mlModelBindingEnabled = androidProject.agpFlags.mlModelBindingEnabled
      )
    }.firstOrNull { it.model.isTestArtifact }

    val androidTestArtifact = copy(variant::getExtraAndroidArtifacts) {
      androidArtifactFrom(
        artifact = it,
        bootClasspath = androidProject.bootClasspath,
        agpVersion = modelVersion,
        variantName = variant.name,
        variantNameForDependencies = variant.name,
        androidModuleId = androidModuleId,
        legacyApplicationIdModel = legacyApplicationIdModel,
        mlModelBindingEnabled = androidProject.agpFlags.mlModelBindingEnabled,
        projectType = androidProject.projectType
      )
    }.firstOrNull { it.model.isTestArtifact }

    val variantCoreImpl = IdeVariantCoreImpl(
      name = variant.name,
      displayName = variant.displayName,
      mainArtifact = mainArtifact.model,
      unitTestArtifact = unitTestArtifact?.model,
      androidTestArtifact = androidTestArtifact?.model,
      testFixturesArtifact = null,
      buildType = variant.buildType,
      productFlavors = ImmutableList.copyOf(variant.productFlavors),
      minSdkVersion = mergedFlavor.minSdkVersion ?: IdeApiVersionImpl(1, null, "1"),
      targetSdkVersion = mergedFlavor.targetSdkVersion,
      maxSdkVersion = mergedFlavor.maxSdkVersion,
      versionCode = mergedFlavor.versionCode,
      versionNameWithSuffix = mergedFlavor.versionName?.let { it + versionNameSuffix.orEmpty() },
      versionNameSuffix = versionNameSuffix,
      instantAppCompatible = (modelVersion != null &&
        modelVersion.isAtLeast(3, 3, 0, "alpha", 10, true) &&
        variant.isInstantAppCompatible),
      vectorDrawablesUseSupportLibrary = mergedFlavor.vectorDrawables?.useSupportLibrary ?: false,
      resourceConfigurations = mergedFlavor.resourceConfigurations,
      testInstrumentationRunner = mergedFlavor.testInstrumentationRunner,
      testInstrumentationRunnerArguments = mergedFlavor.testInstrumentationRunnerArguments,
      testedTargetVariants = getTestedTargetVariants(variant),
      resValues = merge({ resValues }, { resValues }, ::combineMaps),
      proguardFiles = merge({ proguardFiles }, { proguardFiles }, ::combineSets),
      consumerProguardFiles = merge({ consumerProguardFiles }, { consumerProguardFiles }, ::combineSets),
      manifestPlaceholders = merge({ manifestPlaceholders }, { manifestPlaceholders }, ::combineMaps),
      deprecatedPreMergedApplicationId = mergedFlavor.applicationId,
      deprecatedPreMergedTestApplicationId = mergedFlavor.testApplicationId,
      desugaredMethodsFiles = listOf()
    )
    return ModelResult.create {
      IdeVariantWithPostProcessor(
        variantCoreImpl,
        postProcessor = fun(): IdeVariantCoreImpl {
          return variantCoreImpl.copy(
            mainArtifact = mainArtifact.postProcess(),
            androidTestArtifact = androidTestArtifact?.postProcess(),
            unitTestArtifact = unitTestArtifact?.postProcess()
          )
        }
      )
    }
  }

  fun nativeAbiFrom(nativeAbi: NativeAbi): IdeNativeAbiImpl {
    return IdeNativeAbiImpl(
      name = nativeAbi.name,
      sourceFlagsFile = nativeAbi.sourceFlagsFile,
      symbolFolderIndexFile = nativeAbi.symbolFolderIndexFile,
      buildFileIndexFile = nativeAbi.buildFileIndexFile,
      additionalProjectFilesIndexFile = copyNewPropertyWithDefault(nativeAbi::additionalProjectFilesIndexFile) { null }
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
      abi = copyNewProperty(artifact::getAbi, ""),
      targetName = copyNewProperty(artifact::getTargetName, ""),
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
      buildRootFolderMap = copy(variantInfo::getBuildRootFolderMap, ::deduplicateFile)
    )
  }

  fun nativeSettingsFrom(settings: NativeSettings): IdeNativeSettingsImpl {
    return IdeNativeSettingsImpl(
      name = settings.name,
      compilerFlags = copy(settings::getCompilerFlags, ::deduplicateString)
    )
  }

  fun nativeAndroidProjectFrom(project: NativeAndroidProject, ndkVersion: String?): IdeNativeAndroidProjectImpl {
    val defaultNdkVersion = copyNewProperty(project::getDefaultNdkVersion, "")
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
      defaultNdkVersion = defaultNdkVersion,
      ndkVersion = ndkVersion ?: defaultNdkVersion,
      buildSystems = copy(project::getBuildSystems, ::deduplicateString)
    )
  }

  fun nativeVariantAbiFrom(variantAbi: NativeVariantAbi): IdeNativeVariantAbiImpl {
    return IdeNativeVariantAbiImpl(
      buildFiles = copy(variantAbi::getBuildFiles, ::deduplicateFile),
      artifacts = copy(variantAbi::getArtifacts, ::nativeArtifactFrom),
      toolChains = copy(variantAbi::getToolChains, ::nativeToolchainFrom),
      settings = copy(variantAbi::getSettings, ::nativeSettingsFrom),
      fileExtensions = copy(variantAbi::getFileExtensions, ::deduplicateString),
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

  fun lintOptionsFrom(options: LintOptions, modelVersion: AgpVersion?): IdeLintOptionsImpl = IdeLintOptionsImpl(
    baselineFile = if (modelVersion != null)
      options.baselineFile
    else
      null,
    lintConfig = copyNewProperty(options::lintConfig),
    severityOverrides = options.severityOverrides?.let { ImmutableMap.copyOf(it) },
    isCheckTestSources = modelVersion != null &&
      options.isCheckTestSources,
    isCheckDependencies = copyNewProperty({ options.isCheckDependencies }, false),
    disable = copy(options::disable, ::deduplicateString),
    enable = copy(options::enable, ::deduplicateString),
    check = options.check.let { ImmutableSet.copyOf(it) },
    isAbortOnError = copyNewProperty({ options.isAbortOnError }, true),
    isAbsolutePaths = copyNewProperty({ options.isAbsolutePaths }, true),
    isNoLines = copyNewProperty({ options.isNoLines }, false),
    isQuiet = copyNewProperty({ options.isQuiet }, false),
    isCheckAllWarnings = copyNewProperty({ options.isCheckAllWarnings }, false),
    isIgnoreWarnings = copyNewProperty({ options.isIgnoreWarnings }, false),
    isWarningsAsErrors = copyNewProperty({ options.isWarningsAsErrors }, false),
    isIgnoreTestSources = copyNewProperty({ options.isIgnoreTestSources }, false),
    isIgnoreTestFixturesSources = false, // testFixtures are not supported in model v1
    isCheckGeneratedSources = copyNewProperty({ options.isCheckGeneratedSources }, false),
    isExplainIssues = copyNewProperty({ options.isExplainIssues }, true),
    isShowAll = copyNewProperty({ options.isShowAll }, false),
    textReport = copyNewProperty({ options.textReport }, false),
    textOutput = copyNewProperty(options::textOutput),
    htmlReport = copyNewProperty({ options.htmlReport }, true),
    htmlOutput = copyNewProperty(options::htmlOutput),
    xmlReport = copyNewProperty({ options.xmlReport }, true),
    xmlOutput = copyNewProperty(options::xmlOutput),
    isCheckReleaseBuilds = copyNewProperty({ options.isCheckReleaseBuilds }, true)
  )

  fun javaCompileOptionsFrom(options: JavaCompileOptions): IdeJavaCompileOptionsImpl {
    return IdeJavaCompileOptionsImpl(
      encoding = options.encoding,
      sourceCompatibility = options.sourceCompatibility,
      targetCompatibility = options.targetCompatibility,
      isCoreLibraryDesugaringEnabled = copyNewProperty({ options.isCoreLibraryDesugaringEnabled }, false)
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
    return IdeAaptOptionsImpl(
      namespacing = convertNamespacing(copyNewProperty({ original.namespacing }, AaptOptions.Namespacing.DISABLED))
    )
  }

  fun ideVariantBuildInformationFrom(
    model: VariantBuildInformation,
    projectType: Int
  ): IdeVariantBuildInformationImpl {
    return IdeVariantBuildInformationImpl(
      variantName = model.variantName,
      buildInformation = IdeBuildTasksAndOutputInformationImpl(
        assembleTaskName = model.assembleTaskName,
        assembleTaskOutputListingFile = model.assembleTaskOutputListingFile,
        bundleTaskName = if (projectType == AndroidProjectTypes.PROJECT_TYPE_APP) model.bundleTaskName else null,
        bundleTaskOutputListingFile = model.bundleTaskOutputListingFile,
        apkFromBundleTaskName = if (projectType == AndroidProjectTypes.PROJECT_TYPE_APP) model.apkFromBundleTaskName else null,
        apkFromBundleTaskOutputListingFile = model.apkFromBundleTaskOutputListingFile
      )
    )
  }

  fun createVariantBuildInformation(
    project: AndroidProject,
    agpVersion: AgpVersion?
  ): Collection<IdeVariantBuildInformationImpl> {
    return if (agpVersion != null && agpVersion.compareIgnoringQualifiers("4.1.0") >= 0) {
      // make deep copy of VariantBuildInformation.
      project.variantsBuildInformation.map { variantBuildInformation ->
        ideVariantBuildInformationFrom(variantBuildInformation, project.projectType)
      }
    } else emptyList()
    // VariantBuildInformation is not available.
  }

  fun viewBindingOptionsFrom(model: ViewBindingOptions): IdeViewBindingOptionsImpl {
    return IdeViewBindingOptionsImpl(enabled = model.isEnabled)
  }

  fun dependenciesInfoFrom(model: DependenciesInfo) = IdeDependenciesInfoImpl(
    includeInApk = model.includeInApk,
    includeInBundle = model.includeInBundle
  )

  fun Map<AndroidGradlePluginProjectFlags.BooleanFlag, Boolean>.getBooleanFlag(flag: AndroidGradlePluginProjectFlags.BooleanFlag): Boolean =
    this[flag]
      ?: flag.legacyDefault

  fun createIdeAndroidGradlePluginProjectFlagsImpl(
    booleanFlagMap: Map<AndroidGradlePluginProjectFlags.BooleanFlag, Boolean>,
    gradlePropertiesModel: GradlePropertiesModel,
  ): IdeAndroidGradlePluginProjectFlagsImpl {
    return IdeAndroidGradlePluginProjectFlagsImpl(
      applicationRClassConstantIds =
      booleanFlagMap.getBooleanFlag(AndroidGradlePluginProjectFlags.BooleanFlag.APPLICATION_R_CLASS_CONSTANT_IDS),
      testRClassConstantIds = booleanFlagMap.getBooleanFlag(AndroidGradlePluginProjectFlags.BooleanFlag.TEST_R_CLASS_CONSTANT_IDS),
      transitiveRClasses = booleanFlagMap.getBooleanFlag(AndroidGradlePluginProjectFlags.BooleanFlag.TRANSITIVE_R_CLASS),
      usesCompose = booleanFlagMap.getBooleanFlag(AndroidGradlePluginProjectFlags.BooleanFlag.JETPACK_COMPOSE),
      mlModelBindingEnabled = booleanFlagMap.getBooleanFlag(AndroidGradlePluginProjectFlags.BooleanFlag.ML_MODEL_BINDING),
      unifiedTestPlatformEnabled = booleanFlagMap.getBooleanFlag(AndroidGradlePluginProjectFlags.BooleanFlag.UNIFIED_TEST_PLATFORM),
      useAndroidX = gradlePropertiesModel.useAndroidX ?: com.android.builder.model.v2.ide.AndroidGradlePluginProjectFlags.BooleanFlag.USE_ANDROID_X.legacyDefault
    )
  }

  fun Int.toIdeAndroidProjectType(): IdeAndroidProjectType = when (this) {
    AndroidProjectTypes.PROJECT_TYPE_APP -> IdeAndroidProjectType.PROJECT_TYPE_APP
    AndroidProjectTypes.PROJECT_TYPE_LIBRARY -> IdeAndroidProjectType.PROJECT_TYPE_LIBRARY
    AndroidProjectTypes.PROJECT_TYPE_TEST -> IdeAndroidProjectType.PROJECT_TYPE_TEST
    AndroidProjectTypes.PROJECT_TYPE_ATOM -> IdeAndroidProjectType.PROJECT_TYPE_ATOM
    AndroidProjectTypes.PROJECT_TYPE_INSTANTAPP -> IdeAndroidProjectType.PROJECT_TYPE_INSTANTAPP
    AndroidProjectTypes.PROJECT_TYPE_FEATURE -> IdeAndroidProjectType.PROJECT_TYPE_FEATURE
    AndroidProjectTypes.PROJECT_TYPE_DYNAMIC_FEATURE -> IdeAndroidProjectType.PROJECT_TYPE_DYNAMIC_FEATURE
    else -> error("Unknown Android project type: $this")
  }

  fun getProjectType(project: AndroidProject, modelVersion: AgpVersion?): IdeAndroidProjectType {
    if (modelVersion != null) {
      return project.projectType.toIdeAndroidProjectType()
    }
    // Support for old Android Gradle Plugins must be maintained.
    return if (project.isLibrary) IdeAndroidProjectType.PROJECT_TYPE_LIBRARY else IdeAndroidProjectType.PROJECT_TYPE_APP
  }

  fun basicVariantFrom(name: String, legacyApplicationIdModel: LegacyApplicationIdModel?): IdeBasicVariantImpl {
    return IdeBasicVariantImpl(
      name,
      applicationId = legacyApplicationIdModel?.componentToApplicationIdMap?.get(name),
      testApplicationId = legacyApplicationIdModel?.componentToApplicationIdMap?.get(name + "AndroidTest")
    )
  }

  fun androidProjectFrom(
    rootBuildId: BuildId,
    buildId: BuildId,
    buildName: String,
    projectPath: String,
    project: AndroidProject,
    legacyApplicationIdModel: LegacyApplicationIdModel?,
    gradlePropertiesModel: GradlePropertiesModel,
  ): ModelResult<IdeAndroidProjectImpl> {
    // Old plugin versions do not return model version.
    val parsedModelVersion = AgpVersion.tryParse(project.modelVersion)

    val projectFlags = copyNewProperty(project::getFlags)
    val mlModelBindingEnabled = projectFlags?.booleanFlagMap?.getBooleanFlag(AndroidGradlePluginProjectFlags.BooleanFlag.ML_MODEL_BINDING)
      ?: false

    fun productFlavorContainerFrom(container: ProductFlavorContainer) = productFlavorContainerFrom(container, mlModelBindingEnabled)
    fun buildTypeContainerFrom(container: BuildTypeContainer) = buildTypeContainerFrom(container, mlModelBindingEnabled)


    fun sourceProviderContainerFrom(container: ProductFlavorContainer) = sourceProviderContainerFrom(container, mlModelBindingEnabled)

    val defaultConfigCopy: IdeProductFlavorImpl = copyModel(project.defaultConfig.productFlavor, ::productFlavorFrom)
    val defaultConfigSourcesCopy: IdeSourceProviderContainerImpl = copyModel(project.defaultConfig, ::sourceProviderContainerFrom)
    val buildTypesCopy: Collection<IdeBuildTypeContainerImpl> = copy(project::getBuildTypes, ::buildTypeContainerFrom)
    val productFlavorCopy: Collection<IdeProductFlavorContainerImpl> = copy(project::getProductFlavors, ::productFlavorContainerFrom)
    val basicVariantsCopy: Collection<IdeBasicVariantImpl> =
      (
        if (parsedModelVersion != null && parsedModelVersion < MODEL_VERSION_3_2_0)
          copy(fun(): Collection<String> = project.variants.map { it.name }, ::deduplicateString)
        else
          copy(project::getVariantNames, ::deduplicateString)
        )
        .map { basicVariantFrom(it, legacyApplicationIdModel) }
    val flavorDimensionCopy: Collection<String> = copy(project::getFlavorDimensions, ::deduplicateString)
    val bootClasspathCopy: Collection<String> = ImmutableList.copyOf(project.bootClasspath)
    val signingConfigsCopy: Collection<IdeSigningConfigImpl> = copy(project::getSigningConfigs, ::signingConfigFrom)
    val lintOptionsCopy: IdeLintOptionsImpl = copyModel(project.lintOptions, { lintOptionsFrom(it, parsedModelVersion) })
    val javaCompileOptionsCopy = copyModel(project.javaCompileOptions, ::javaCompileOptionsFrom)
    val aaptOptionsCopy = copyModel(project.aaptOptions, ::aaptOptionsFrom)
    val dynamicFeaturesCopy: Collection<String> = copy(project::getDynamicFeatures, ::deduplicateString)
    val variantBuildInformation = createVariantBuildInformation(project, parsedModelVersion)
    val viewBindingOptionsCopy: IdeViewBindingOptionsImpl? = copyNewModel(project::getViewBindingOptions, ::viewBindingOptionsFrom)
    val dependenciesInfoCopy: IdeDependenciesInfoImpl? = copyNewModel(project::getDependenciesInfo, ::dependenciesInfoFrom)
    val buildToolsVersionCopy = copyNewProperty(project::getBuildToolsVersion)
    val groupId = if (parsedModelVersion != null && parsedModelVersion.isAtLeast(3, 6, 0, "alpha", 5, false)) project.groupId else null
    val namespace = copyNewProperty(project::getNamespace)
    val testNamespace = copyNewProperty(project::getAndroidTestNamespace)
    val lintRuleJarsCopy: List<File> = copy(project::getLintRuleJars, ::deduplicateFile)

    // AndroidProject#isBaseSplit is always non null.
    val isBaseSplit = copyNewProperty({ project.isBaseSplit }, false)
    val agpFlags: IdeAndroidGradlePluginProjectFlagsImpl =
      createIdeAndroidGradlePluginProjectFlagsImpl(projectFlags?.booleanFlagMap ?: emptyMap(), gradlePropertiesModel)
    return ModelResult.create {
      IdeAndroidProjectImpl(
        agpVersion = project.modelVersion,
        projectPath = IdeProjectPathImpl(
          rootBuildId = rootBuildId.asFile,
          buildId = buildId.asFile,
          buildName = buildName,
          projectPath = projectPath
        ),
        defaultSourceProvider = defaultConfigSourcesCopy,
        multiVariantData = IdeMultiVariantDataImpl(
          defaultConfig = defaultConfigCopy,
          buildTypes = buildTypesCopy,
          productFlavors = productFlavorCopy,
        ),
        basicVariants = basicVariantsCopy,
        flavorDimensions = flavorDimensionCopy,
        compileTarget = project.compileTarget,
        bootClasspath = bootClasspathCopy,
        signingConfigs = signingConfigsCopy,
        lintOptions = lintOptionsCopy,
        lintChecksJars = lintRuleJarsCopy,
        javaCompileOptions = javaCompileOptionsCopy,
        aaptOptions = aaptOptionsCopy,
        buildFolder = project.buildFolder,
        dynamicFeatures = dynamicFeaturesCopy,
        baseFeature = null,
        variantsBuildInformation = variantBuildInformation,
        viewBindingOptions = viewBindingOptionsCopy,
        dependenciesInfo = dependenciesInfoCopy,
        buildToolsVersion = buildToolsVersionCopy,
        resourcePrefix = project.resourcePrefix,
        groupId = groupId,
        namespace = namespace,
        testNamespace = testNamespace,
        projectType = getProjectType(project, parsedModelVersion),
        isBaseSplit = isBaseSplit,
        agpFlags = agpFlags,
        isKaptEnabled = false,
        desugarLibraryConfigFiles = listOf(),
      )
    }
  }

  return object : ModelCache.V1 {
    override val libraryLookup: (LibraryReference) -> IdeUnresolvedLibrary = internedModels::lookup
    override fun createLibraryTable(): IdeUnresolvedLibraryTableImpl = internedModels.createLibraryTable()

    override fun variantFrom(
      androidProject: IdeAndroidProjectImpl,
      variant: Variant,
      legacyApplicationIdModel: LegacyApplicationIdModel?,
      modelVersion: AgpVersion?,
      androidModuleId: ModuleId
    ): ModelResult<IdeVariantWithPostProcessor> =
      lock.withLock { variantFrom(androidProject, variant, legacyApplicationIdModel, modelVersion, androidModuleId) }

    override fun androidProjectFrom(
      rootBuildId: BuildId,
      buildId: BuildId,
      buildName: String,
      projectPath: String,
      project: AndroidProject,
      legacyApplicationIdModel: LegacyApplicationIdModel?,
      gradlePropertiesModel: GradlePropertiesModel,
    ): ModelResult<IdeAndroidProjectImpl> {
      return lock.withLock {
        androidProjectFrom(rootBuildId, buildId, buildName, projectPath, project, legacyApplicationIdModel, gradlePropertiesModel)
      }
    }

    override fun androidArtifactOutputFrom(output: OutputFile): IdeAndroidArtifactOutputImpl =
      lock.withLock { androidArtifactOutputFrom(output) }

    override fun nativeModuleFrom(nativeModule: NativeModule): IdeNativeModuleImpl = lock.withLock { nativeModuleFrom(nativeModule) }
    override fun nativeVariantAbiFrom(variantAbi: NativeVariantAbi): IdeNativeVariantAbiImpl =
      lock.withLock { nativeVariantAbiFrom(variantAbi) }

    override fun nativeAndroidProjectFrom(
      project: NativeAndroidProject,
      ndkVersion: String?
    ): IdeNativeAndroidProjectImpl = lock.withLock { nativeAndroidProjectFrom(project, ndkVersion) }
  }
}

val MODEL_VERSION_3_2_0 = AgpVersion.parse("3.2.0")

internal inline fun <T> safeGet(original: () -> T, default: T): T = try {
  original()
} catch (ignored: UnsupportedOperationException) {
  default
}

private inline fun <T> copyNewPropertyWithDefault(propertyInvoker: () -> T, defaultValue: () -> T): T {
  return try {
    propertyInvoker()
  } catch (ignored: UnsupportedOperationException) {
    defaultValue()
  }
}

/**
 * NOTE: Multiple overloads are intentionally ambiguous to prevent lambdas from being used directly.
 *       Please use function references or anonymous functions which seeds type inference.
 **/
private inline fun <T : Any> copyNewProperty(propertyInvoker: () -> T, defaultValue: T): T {
  return try {
    propertyInvoker()
  } catch (ignored: UnsupportedOperationException) {
    defaultValue
  }
}

/**
 * NOTE: Multiple overloads are intentionally ambiguous to prevent lambdas from being used directly.
 *       Please use function references or anonymous functions which seeds type inference.
 **/
@Suppress("unused", "UNUSED_PARAMETER")
private inline fun <T : Collection<*>> copyNewProperty(propertyInvoker: () -> T, defaultValue: T): Unit = error(
  "Cannot be called. Use copy() method."
)

/**
 * NOTE: Multiple overloads are intentionally ambiguous to prevent lambdas from being used directly.
 *       Please use function references or anonymous functions which seeds type inference.
 **/
@Suppress("unused", "UNUSED_PARAMETER")
private inline fun <T : Map<*, *>> copyNewProperty(propertyInvoker: () -> T, defaultValue: T): Unit = error(
  "Cannot be called. Use copy() method."
)

/**
 * NOTE: Multiple overloads are intentionally ambiguous to prevent lambdas from being used directly.
 *       Please use function references or anonymous functions which seeds type inference.
 **/
@JvmName("impossibleCopyNewCollectionProperty")
@Suppress("unused", "UNUSED_PARAMETER")
private inline fun <T : Collection<*>?> copyNewProperty(propertyInvoker: () -> T): Unit = error("Cannot be called. Use copy() method.")

/**
 * NOTE: Multiple overloads are intentionally ambiguous to prevent lambdas from being used directly.
 *       Please use function references or anonymous functions which seeds type inference.
 **/
@JvmName("impossibleCopyNewMapProperty")
@Suppress("unused", "UNUSED_PARAMETER")
private inline fun <T : Map<*, *>?> copyNewProperty(propertyInvoker: () -> T): Unit = error("Cannot be called. Use copy() method.")

private fun <T> MutableMap<T, T>.internCore(core: T): T = putIfAbsent(core, core) ?: core

private inline fun <K, V : Any> copyNewModel(
  getter: () -> K?,
  mapper: (K) -> V
): V? {
  return try {
    val key: K? = getter()
    if (key != null) mapper(key) else null
  } catch (ignored: UnsupportedOperationException) {
    null
  }
}

private inline fun <K : Any, V> copyModel(key: K, mappingFunction: (K) -> V): V = mappingFunction(key)

@JvmName("copyModelNullable")
private inline fun <K : Any, V> copyModel(key: K?, mappingFunction: (K) -> V): V? = key?.let(mappingFunction)

private inline fun <K, V> copy(original: () -> Collection<K>, mapper: (K) -> V): List<V> =
  safeGet(original, listOf()).map(mapper)

private inline fun <K, V> copy(original: () -> Set<K>, mapper: (K) -> V): Set<V> =
  safeGet(original, setOf()).map(mapper).toSet()

private inline fun <K, V, R> copy(original: () -> Map<K, V>, mapper: (V) -> R): Map<K, R> =
  safeGet(original, mapOf()).mapValues { (_, v) -> mapper(v) }

internal inline fun <K : Any, R : Any, V> copyModel(key: K, key2: R, mappingFunction: (K, R) -> V): V = mappingFunction(key, key2)

/**
 * NOTE: Multiple overloads are intentionally ambiguous to prevent lambdas from being used directly.
 *       Please use function references or anonymous functions which seeds type inference.
 **/
private inline fun <T : Any?> copyNewProperty(propertyInvoker: () -> T?): T? {
  return try {
    propertyInvoker()
  } catch (ignored: UnsupportedOperationException) {
    null
  }
}

/** Indicates whether the given library is a module wrapping an AAR file.  */
@VisibleForTesting
fun isLocalAarModule(buildFolderPaths: BuildFolderPaths, androidLibrary: AndroidLibrary): Boolean {
  val projectPath = androidLibrary.project ?: return false
  val buildFolderPath = buildFolderPaths.findBuildFolderPath(
    projectPath,
    copyNewProperty(androidLibrary::getBuildId)
  )
  // If the aar bundle is inside of build directory, then it's a regular library module dependency, otherwise it's a wrapped aar module.
  return (buildFolderPath != null &&
    // Comparing two absolute paths received from Gradle and thus they don't need canonicalization.
    !androidLibrary.bundle.path.startsWith(buildFolderPath.path))
}

internal fun Collection<SyncIssue>.toSyncIssueData(): List<IdeSyncIssue> {
  return map { syncIssue ->
    IdeSyncIssueImpl(
      message = syncIssue.message,
      data = syncIssue.data,
      multiLineMessage = safeGet(syncIssue::multiLineMessage, null)?.toList(),
      severity = syncIssue.severity,
      type = syncIssue.type
    )
  }
}

internal fun LegacyApplicationIdModel?.getProblemsAsSyncIssues(): List<IdeSyncIssue> {
  return this?.problems.orEmpty().map { problem ->
    IdeSyncIssueImpl(
      message = problem.message ?: "Unknown error in LegacyApplicationIdModelBuilder",
      data = null,
      multiLineMessage = problem.stackTraceAsMultiLineMessage(),
      severity = IdeSyncIssue.SEVERITY_WARNING,
      type = IdeSyncIssue.TYPE_APPLICATION_ID_MUST_NOT_BE_DYNAMIC
    )
  }
}

private fun Throwable.stackTraceAsMultiLineMessage(): List<String> =
  StringWriter().use { stringWriter -> PrintWriter(stringWriter).use { printStackTrace(it) }; stringWriter.toString().split(System.lineSeparator()) }
