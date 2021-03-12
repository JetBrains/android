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
@file:Suppress("DEPRECATION")

package com.android.tools.idea.gradle.project.model

import com.android.AndroidProjectTypes
import com.android.SdkConstants
import com.android.build.FilterData
import com.android.build.OutputFile
import com.android.build.VariantOutput
import com.android.builder.model.AaptOptions
import com.android.builder.model.AndroidArtifact
import com.android.builder.model.AndroidGradlePluginProjectFlags
import com.android.builder.model.AndroidGradlePluginProjectFlags.BooleanFlag
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
import com.android.builder.model.TestOptions
import com.android.builder.model.TestedTargetVariant
import com.android.builder.model.Variant
import com.android.builder.model.VariantBuildInformation
import com.android.builder.model.VectorDrawablesOptions
import com.android.builder.model.ViewBindingOptions
import com.android.builder.model.v2.models.ndk.NativeAbi
import com.android.builder.model.v2.models.ndk.NativeModule
import com.android.builder.model.v2.models.ndk.NativeVariant
import com.android.ide.common.gradle.model.CodeShrinker
import com.android.ide.common.gradle.model.IdeAaptOptions
import com.android.ide.common.gradle.model.IdeAndroidArtifactOutput
import com.android.ide.common.gradle.model.IdeAndroidGradlePluginProjectFlags
import com.android.ide.common.gradle.model.IdeAndroidLibrary
import com.android.ide.common.gradle.model.IdeAndroidProject
import com.android.ide.common.gradle.model.IdeAndroidProjectType
import com.android.ide.common.gradle.model.IdeBuildType
import com.android.ide.common.gradle.model.IdeBuildTypeContainer
import com.android.ide.common.gradle.model.IdeDependencies
import com.android.ide.common.gradle.model.IdeDependenciesInfo
import com.android.ide.common.gradle.model.IdeFilterData
import com.android.ide.common.gradle.model.IdeJavaLibrary
import com.android.ide.common.gradle.model.IdeLibrary
import com.android.ide.common.gradle.model.IdeLintOptions
import com.android.ide.common.gradle.model.IdeMavenCoordinates
import com.android.ide.common.gradle.model.IdeModuleLibrary
import com.android.ide.common.gradle.model.IdeProductFlavor
import com.android.ide.common.gradle.model.IdeProductFlavorContainer
import com.android.ide.common.gradle.model.IdeSigningConfig
import com.android.ide.common.gradle.model.IdeTestOptions
import com.android.ide.common.gradle.model.IdeVariantBuildInformation
import com.android.ide.common.gradle.model.IdeVectorDrawablesOptions
import com.android.ide.common.gradle.model.IdeViewBindingOptions
import com.android.ide.common.gradle.model.impl.BuildFolderPaths
import com.android.ide.common.gradle.model.impl.IdeAaptOptionsImpl
import com.android.ide.common.gradle.model.impl.IdeAndroidArtifactImpl
import com.android.ide.common.gradle.model.impl.IdeAndroidArtifactOutputImpl
import com.android.ide.common.gradle.model.impl.IdeAndroidGradlePluginProjectFlagsImpl
import com.android.ide.common.gradle.model.impl.IdeAndroidLibraryCore
import com.android.ide.common.gradle.model.impl.IdeAndroidLibraryImpl
import com.android.ide.common.gradle.model.impl.IdeAndroidProjectImpl
import com.android.ide.common.gradle.model.impl.IdeApiVersionImpl
import com.android.ide.common.gradle.model.impl.IdeBuildTypeContainerImpl
import com.android.ide.common.gradle.model.impl.IdeBuildTypeImpl
import com.android.ide.common.gradle.model.impl.IdeClassFieldImpl
import com.android.ide.common.gradle.model.impl.IdeDependenciesImpl
import com.android.ide.common.gradle.model.impl.IdeDependenciesInfoImpl
import com.android.ide.common.gradle.model.impl.IdeFilterDataImpl
import com.android.ide.common.gradle.model.impl.IdeJavaArtifactImpl
import com.android.ide.common.gradle.model.impl.IdeJavaCompileOptionsImpl
import com.android.ide.common.gradle.model.impl.IdeJavaLibraryCore
import com.android.ide.common.gradle.model.impl.IdeJavaLibraryImpl
import com.android.ide.common.gradle.model.impl.IdeLintOptionsImpl
import com.android.ide.common.gradle.model.impl.IdeMavenCoordinatesImpl
import com.android.ide.common.gradle.model.impl.IdeModuleLibraryCore
import com.android.ide.common.gradle.model.impl.IdeModuleLibraryImpl
import com.android.ide.common.gradle.model.impl.IdeProductFlavorContainerImpl
import com.android.ide.common.gradle.model.impl.IdeProductFlavorImpl
import com.android.ide.common.gradle.model.impl.IdeSigningConfigImpl
import com.android.ide.common.gradle.model.impl.IdeSourceProviderContainerImpl
import com.android.ide.common.gradle.model.impl.IdeSourceProviderImpl
import com.android.ide.common.gradle.model.impl.IdeTestOptionsImpl
import com.android.ide.common.gradle.model.impl.IdeTestedTargetVariantImpl
import com.android.ide.common.gradle.model.impl.IdeVariantBuildInformationImpl
import com.android.ide.common.gradle.model.impl.IdeVariantImpl
import com.android.ide.common.gradle.model.impl.IdeVectorDrawablesOptionsImpl
import com.android.ide.common.gradle.model.impl.IdeViewBindingOptionsImpl
import com.android.tools.idea.gradle.project.model.ModelCache.Companion.LOCAL_AARS
import com.android.ide.common.gradle.model.impl.ndk.v1.IdeNativeAndroidProjectImpl
import com.android.ide.common.gradle.model.impl.ndk.v1.IdeNativeArtifactImpl
import com.android.ide.common.gradle.model.impl.ndk.v1.IdeNativeFileImpl
import com.android.ide.common.gradle.model.impl.ndk.v1.IdeNativeSettingsImpl
import com.android.ide.common.gradle.model.impl.ndk.v1.IdeNativeToolchainImpl
import com.android.ide.common.gradle.model.impl.ndk.v1.IdeNativeVariantAbiImpl
import com.android.ide.common.gradle.model.impl.ndk.v1.IdeNativeVariantInfoImpl
import com.android.ide.common.gradle.model.impl.ndk.v2.IdeNativeAbiImpl
import com.android.ide.common.gradle.model.impl.ndk.v2.IdeNativeModuleImpl
import com.android.ide.common.gradle.model.impl.ndk.v2.IdeNativeVariantImpl
import com.android.ide.common.gradle.model.ndk.v2.NativeBuildSystem
import com.android.ide.common.repository.GradleVersion
import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import com.google.common.collect.ImmutableSortedSet
import java.io.File
import java.util.HashMap

interface ModelCache {
  fun variantFrom(androidProject: IdeAndroidProject, variant: Variant, modelVersion: GradleVersion?): IdeVariantImpl
  fun androidProjectFrom(project: AndroidProject): IdeAndroidProjectImpl
  fun androidArtifactOutputFrom(output: OutputFile): IdeAndroidArtifactOutputImpl

  fun nativeModuleFrom(nativeModule: NativeModule): IdeNativeModuleImpl
  fun nativeVariantAbiFrom(variantAbi: NativeVariantAbi): IdeNativeVariantAbiImpl
  fun nativeAndroidProjectFrom(project: NativeAndroidProject): IdeNativeAndroidProjectImpl

  companion object {
    const val LOCAL_AARS = "__local_aars__"

    @JvmStatic
    fun create(buildFolderPaths: BuildFolderPaths): ModelCache = modelCacheImpl(buildFolderPaths)

    @JvmStatic
    fun create(): ModelCache = modelCacheImpl(BuildFolderPaths())

    @JvmStatic
    @VisibleForTesting
    fun createForTesting(buildFolderPaths: BuildFolderPaths): ModelCacheTesting = modelCacheImpl(buildFolderPaths)

    @JvmStatic
    @VisibleForTesting
    fun createForTesting(): ModelCacheTesting = modelCacheImpl(BuildFolderPaths())

    @JvmStatic
    inline fun <T> safeGet(original: () -> T, default: T): T = try {
      original()
    }
    catch (ignored: UnsupportedOperationException) {
      default
    }
  }
}

@VisibleForTesting
interface ModelCacheTesting : ModelCache {
  fun aaptOptionsFrom(original: AaptOptions): IdeAaptOptionsImpl
  fun androidArtifactFrom(artifact: AndroidArtifact, agpVersion: GradleVersion?): IdeAndroidArtifactImpl
  fun apiVersionFrom(version: ApiVersion): IdeApiVersionImpl
  fun buildTypeContainerFrom(container: BuildTypeContainer): IdeBuildTypeContainerImpl
  fun buildTypeFrom(buildType: BuildType): IdeBuildTypeImpl
  fun classFieldFrom(classField: ClassField): IdeClassFieldImpl
  fun filterDataFrom(data: FilterData): IdeFilterDataImpl
  fun javaArtifactFrom(artifact: JavaArtifact): IdeJavaArtifactImpl
  fun javaCompileOptionsFrom(options: JavaCompileOptions): IdeJavaCompileOptionsImpl
  fun productFlavorContainerFrom(container: ProductFlavorContainer): IdeProductFlavorContainerImpl
  fun productFlavorFrom(flavor: ProductFlavor): IdeProductFlavorImpl
  fun signingConfigFrom(config: SigningConfig): IdeSigningConfigImpl
  fun sourceProviderContainerFrom(container: SourceProviderContainer): IdeSourceProviderContainerImpl
  fun testedTargetVariantFrom(variant: TestedTargetVariant): IdeTestedTargetVariantImpl
  fun testOptionsFrom(testOptions: TestOptions): IdeTestOptionsImpl
  fun vectorDrawablesOptionsFrom(options: VectorDrawablesOptions): IdeVectorDrawablesOptionsImpl
  fun viewBindingOptionsFrom(model: ViewBindingOptions): IdeViewBindingOptionsImpl
  fun sourceProviderFrom(provider: SourceProvider): IdeSourceProviderImpl
  fun lintOptionsFrom(options: LintOptions, modelVersion: GradleVersion?): IdeLintOptionsImpl
  fun nativeToolchainFrom(toolchain: NativeToolchain): IdeNativeToolchainImpl
  fun nativeArtifactFrom(artifact: NativeArtifact): IdeNativeArtifactImpl
  fun nativeFileFrom(file: NativeFile): IdeNativeFileImpl
  fun nativeSettingsFrom(settings: NativeSettings): IdeNativeSettingsImpl
  fun androidGradlePluginProjectFlagsFrom(flags: AndroidGradlePluginProjectFlags): IdeAndroidGradlePluginProjectFlagsImpl
  fun dependenciesFrom(artifact: BaseArtifact): IdeDependencies
  fun libraryFrom(javaLibrary: JavaLibrary): IdeLibrary
  fun libraryFrom(projectPath: String, artifactAddress: String, buildId: String?): IdeLibrary
  fun computeAddress(library: Library): String
  fun isLocalAarModule(androidLibrary: AndroidLibrary): Boolean
  fun mavenCoordinatesFrom(coordinates: MavenCoordinates): IdeMavenCoordinatesImpl
}

private val MODEL_VERSION_3_2_0 = GradleVersion.parse("3.2.0")

private fun modelCacheImpl(buildFolderPaths: BuildFolderPaths): ModelCacheTesting {

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
      myKotlinDirectories = copy(provider::getKotlinDirectories, mapper = { it }).makeRelativeAndDeduplicate(),
      myResourcesDirectories = provider.resourcesDirectories.makeRelativeAndDeduplicate(),
      myAidlDirectories = provider.aidlDirectories.makeRelativeAndDeduplicate(),
      myRenderscriptDirectories = provider.renderscriptDirectories.makeRelativeAndDeduplicate(),
      myResDirectories = provider.resDirectories.makeRelativeAndDeduplicate(),
      myAssetsDirectories = provider.assetsDirectories.makeRelativeAndDeduplicate(),
      myJniLibsDirectories = provider.jniLibsDirectories.makeRelativeAndDeduplicate(),
      myShadersDirectories = copy(provider::getShadersDirectories, mapper = { it }).makeRelativeAndDeduplicate(),
      myMlModelsDirectories = copy(provider::getMlModelsDirectories, mapper = { it }).makeRelativeAndDeduplicate()
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

  fun copyVectorDrawables(flavor: ProductFlavor): IdeVectorDrawablesOptions? {
    val vectorDrawables: VectorDrawablesOptions = try {
      flavor.vectorDrawables
    }
    catch (e: UnsupportedOperationException) {
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

  fun sourceProviderContainerFrom(container: SourceProviderContainer): IdeSourceProviderContainerImpl {
    return IdeSourceProviderContainerImpl(
      artifactName = container.artifactName,
      sourceProvider = copyModel(container.sourceProvider, ::sourceProviderFrom)
    )
  }

  fun productFlavorContainerFrom(
    container: ProductFlavorContainer): IdeProductFlavorContainerImpl {
    return IdeProductFlavorContainerImpl(
      productFlavor = copyModel(container.productFlavor, ::productFlavorFrom),
      sourceProvider = copyModel(container.sourceProvider, ::sourceProviderFrom),
      extraSourceProviders = copy(container::getExtraSourceProviders, ::sourceProviderContainerFrom)
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

  fun buildTypeContainerFrom(container: BuildTypeContainer): IdeBuildTypeContainerImpl {
    return IdeBuildTypeContainerImpl(
      buildType = copyModel(container.buildType, ::buildTypeFrom),
      sourceProvider = copyModel(container.sourceProvider, ::sourceProviderFrom),
      extraSourceProviders = copy(container::getExtraSourceProviders, ::sourceProviderContainerFrom)
    )
  }

  /** Indicates whether the given library is a module wrapping an AAR file.  */
  fun isLocalAarModule(androidLibrary: AndroidLibrary): Boolean {
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

  fun createIdeModuleLibrary(library: AndroidLibrary, artifactAddress: String, projectPath: String): IdeLibrary {
    val core = IdeModuleLibraryCore(
      artifactAddress = artifactAddress,
      buildId = copyNewProperty(library::getBuildId),
      projectPath = projectPath,
      variant = copyNewProperty(library::getProjectVariant),
      lintJar = copyNewProperty(library::getLintJar)?.path
    )
    val isProvided = copyNewProperty(library::isProvided, false)
    return IdeModuleLibraryImpl(moduleLibraryCores.internCore(core), isProvided)
  }

  fun createIdeModuleLibrary(library: JavaLibrary, artifactAddress: String, projectPath: String): IdeLibrary {
    val core = IdeModuleLibraryCore(
      artifactAddress = artifactAddress,
      buildId = copyNewProperty(library::getBuildId),
      projectPath = projectPath,
      variant = null,
      lintJar = null
    )
    val isProvided = copyNewProperty(library::isProvided, false)
    return IdeModuleLibraryImpl(moduleLibraryCores.internCore(core), isProvided)
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
    }
    else {
      val jarFile: File =
        if (library is JavaLibrary) {
          library.jarFile
        }
        else {
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
    try {
      if (library.project != null && library is AndroidLibrary) {
        return ((copyNewProperty(library::getBuildId)).orEmpty()
                + library.getProject()
                + "::"
                + library.projectVariant)
      }
    }
    catch (ex: UnsupportedOperationException) {
      // getProject() isn't available for pre-2.0 plugins. Proceed with MavenCoordinates.
      // Anyway pre-2.0 plugins don't have variant information for module dependency.
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

  /**
   * @param androidLibrary Instance of [AndroidLibrary] returned by android plugin.
   * path to build directory for all modules.
   * @return Instance of [Library] based on dependency type.
   */
  fun libraryFrom(androidLibrary: AndroidLibrary): IdeLibrary {
    // If the dependency is a sub-module that wraps local aar, it should be considered as external dependency, i.e. type LIBRARY_ANDROID.
    // In AndroidLibrary, getProject() of such dependency returns non-null project name, but they should be converted to IdeLevel2AndroidLibrary.
    // Identify such case with the location of aar bundle.
    // If the aar bundle is inside of build directory of sub-module, then it's regular library module dependency, otherwise it's a wrapped aar module.
    val projectPath = androidLibrary.project
    return if (projectPath != null && !isLocalAarModule(androidLibrary)) {
      createIdeModuleLibrary(androidLibrary, computeAddress(androidLibrary), projectPath)
    }
    else {
      val core = IdeAndroidLibraryCore.create(
        artifactAddress = computeAddress(androidLibrary),
        folder = androidLibrary.folder,
        manifest = androidLibrary.manifest.path,
        jarFile = androidLibrary.jarFile.path,
        compileJarFile = (copyNewProperty(androidLibrary::getCompileJarFile) ?: androidLibrary.jarFile).path,
        resFolder = androidLibrary.resFolder.path,
        resStaticLibrary = copyNewProperty(androidLibrary::getResStaticLibrary),
        assetsFolder = androidLibrary.assetsFolder.path,
        localJars = androidLibrary.localJars.map { it.path },
        jniFolder = androidLibrary.jniFolder.path,
        aidlFolder = androidLibrary.aidlFolder.path,
        renderscriptFolder = androidLibrary.renderscriptFolder.path,
        proguardRules = androidLibrary.proguardRules.path,
        lintJar = androidLibrary.lintJar.path,
        externalAnnotations = androidLibrary.externalAnnotations.path,
        publicResources = androidLibrary.publicResources.path,
        artifact = androidLibrary.bundle,
        symbolFile = getSymbolFilePath(androidLibrary),
        deduplicate = { strings.getOrPut(this) { this } }
      )
      val isProvided = copyNewProperty(androidLibrary::isProvided, false)
      IdeAndroidLibraryImpl(androidLibraryCores.internCore(core), isProvided)
    }
  }

  /**
   * @param javaLibrary Instance of [JavaLibrary] returned by android plugin.
   * @return Instance of [Library] based on dependency type.
   */
  fun libraryFrom(javaLibrary: JavaLibrary): IdeLibrary {
    val project = copyNewProperty(javaLibrary::getProject)
    return if (project != null) {
      // Java modules don't have variant.
      createIdeModuleLibrary(javaLibrary, computeAddress(javaLibrary), project)
    }
    else {
      val core = IdeJavaLibraryCore(
              artifactAddress = computeAddress(javaLibrary),
              artifact = javaLibrary.jarFile
      )
      val isProvided = copyNewProperty(javaLibrary::isProvided, false)
      IdeJavaLibraryImpl(javaLibraryCores.internCore(core), isProvided)
    }
  }

  fun libraryFrom(projectPath: String, artifactAddress: String, buildId: String?): IdeLibrary {
    val core = IdeModuleLibraryCore(projectPath, artifactAddress, buildId)
    return IdeModuleLibraryImpl(moduleLibraryCores.internCore(core), isProvided = false)
  }

  /** Call this method on level 1 Dependencies model.  */
  fun createFromDependencies(dependencies: Dependencies): IdeDependencies {
    // Map from unique artifact address to level2 library instance. The library instances are
    // supposed to be shared by all artifacts. When creating IdeLevel2Dependencies, check if current library is available in this map,
    // if it's available, don't create new one, simple add reference to it. If it's not available, create new instance and save
    // to this map, so it can be reused the next time when the same library is added.
    val librariesById = mutableMapOf<String, IdeLibrary>()

    fun createModuleLibrary(
      visited: MutableSet<String>,
      projectPath: String,
      artifactAddress: String,
      buildId: String?
    ) {
      if (!visited.contains(artifactAddress)) {
        visited.add(artifactAddress)
        librariesById.computeIfAbsent(artifactAddress) { libraryFrom(projectPath, artifactAddress, buildId) }
      }
    }

    fun populateModuleDependencies(dependencies: Dependencies, visited: MutableSet<String>) {
      try {
        for (identifier in dependencies.javaModules) {
          createModuleLibrary(
            visited,
            identifier.projectPath,
            computeAddress(identifier),
            identifier.buildId)
        }
      }
      catch (ignored: UnsupportedOperationException) {
        // Dependencies::getJavaModules is available for AGP 3.1+. Use
        // Dependencies::getProjects for the old plugins.
        for (projectPath in dependencies.projects) {
          createModuleLibrary(visited, projectPath, projectPath, null)
        }
      }
    }

    fun getJavaDependencies(androidLibrary: AndroidLibrary): Collection<JavaLibrary> {
      return try {
        androidLibrary.javaDependencies
      }
      catch (e: UnsupportedOperationException) {
        emptyList()
      }
    }

    fun populateJavaLibraries(
      javaLibraries: Collection<JavaLibrary>,
      visited: MutableSet<String>) {
      for (javaLibrary in javaLibraries) {
        val address = computeAddress(javaLibrary)
        if (!visited.contains(address)) {
          visited.add(address)
          librariesById.computeIfAbsent(address) { libraryFrom(javaLibrary) }
          populateJavaLibraries(javaLibrary.dependencies, visited)
        }
      }
    }

    fun populateAndroidLibraries(
      androidLibraries: Collection<AndroidLibrary>,
      visited: MutableSet<String>
    ) {
      for (androidLibrary in androidLibraries) {
        val address = computeAddress(androidLibrary)
        if (!visited.contains(address)) {
          visited.add(address)
          librariesById.computeIfAbsent(address) { libraryFrom(androidLibrary) }
          populateAndroidLibraries(androidLibrary.libraryDependencies, visited)
          populateJavaLibraries(getJavaDependencies(androidLibrary), visited)
        }
      }
    }

    fun createInstance(
      artifactAddresses: Collection<String>,
      runtimeOnlyJars: Collection<File>
    ): IdeDependencies {
      val androidLibraries = ImmutableList.builder<IdeAndroidLibrary>()
      val javaLibraries = ImmutableList.builder<IdeJavaLibrary>()
      val moduleDependencies = ImmutableList.builder<IdeModuleLibrary>()
      for (address in artifactAddresses) {
        val library = librariesById[address]!!
        // TODO(solodkyy): Build typed collections directly in populate methods.
        when (library) {
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

    fun createInstance(): IdeDependencies {
      val visited = mutableSetOf<String>()
      populateAndroidLibraries(dependencies.libraries, visited)
      populateJavaLibraries(dependencies.javaLibraries, visited)
      populateModuleDependencies(dependencies, visited)
      val jars: Collection<File> = try {
        dependencies.runtimeOnlyClasses
      }
      catch (e: UnsupportedOperationException) {
        // Gradle older than 3.4.
        emptyList()
      }
      return createInstance(visited, jars)
    }

    return createInstance()
  }

  /**
   * Create [IdeDependencies] from [BaseArtifact].
   */
  fun dependenciesFrom(artifact: BaseArtifact): IdeDependencies {
    return createFromDependencies(artifact.dependencies)
  }

  fun filterDataFrom(data: FilterData): IdeFilterDataImpl {
    return IdeFilterDataImpl(identifier = data.identifier, filterType = data.filterType)
  }

  fun copyFilters(output: VariantOutput): Collection<IdeFilterData> {
    return copy(
      fun(): Collection<FilterData> =
        try {
          output.filters
        }
        catch (ignored: UnsupportedOperationException) {
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

  fun copyOutputs(
    artifact: AndroidArtifact,
    agpVersion: GradleVersion?
  ): List<IdeAndroidArtifactOutput> {
    // getOutputs is deprecated in AGP 4.0.0.
    if (agpVersion != null && agpVersion.compareIgnoringQualifiers("4.0.0") >= 0) {
      return emptyList()
    }
    return try {
      copy(artifact::getOutputs, ::androidArtifactOutputFrom)
    } catch (_: RuntimeException) {
      // Handle "Invalid main APK outputs".
      emptyList()
    }
  }

  fun testOptionsFrom(testOptions: TestOptions): IdeTestOptionsImpl {
    return IdeTestOptionsImpl(
      animationsDisabled = testOptions.animationsDisabled,
      execution = convertExecution(testOptions.execution)
    )
  }

  fun androidArtifactFrom(
    artifact: AndroidArtifact,
    agpVersion: GradleVersion?
  ): IdeAndroidArtifactImpl {
    return IdeAndroidArtifactImpl(
      name = artifact.name,
      compileTaskName = artifact.compileTaskName,
      assembleTaskName = artifact.assembleTaskName,
      assembleTaskOutputListingFile = copyNewProperty({ artifact.assembleTaskOutputListingFile }, ""),
      classesFolder = artifact.classesFolder,
      javaResourcesFolder = copyNewProperty(artifact::getJavaResourcesFolder),
      ideSetupTaskNames = copyNewPropertyWithDefault(artifact::getIdeSetupTaskNames,
                                                     defaultValue = { setOf(artifact.sourceGenTaskName) }).toList(),
      mutableGeneratedSourceFolders = copy(artifact::getGeneratedSourceFolders,
                                           ::deduplicateFile).distinct().toMutableList(), // The source model can contain duplicates.
      variantSourceProvider = copyNewModel(artifact::getVariantSourceProvider, ::sourceProviderFrom),
      multiFlavorSourceProvider = copyNewModel(artifact::getMultiFlavorSourceProvider, ::sourceProviderFrom),
      additionalClassesFolders = copy(artifact::getAdditionalClassesFolders, ::deduplicateFile).toList(),
      level2Dependencies = dependenciesFrom(artifact),
      outputs = copyOutputs(artifact, agpVersion),
      applicationId = artifact.applicationId,
      generatedResourceFolders = copy(artifact::getGeneratedResourceFolders, ::deduplicateFile).distinct(),
      signingConfigName = artifact.signingConfigName,
      abiFilters = ImmutableSet.copyOf( // In AGP 4.0 and below abiFilters was nullable, normalize null to empty set.
        artifact.abiFilters.orEmpty()),
      isSigned = artifact.isSigned,
      additionalRuntimeApks = copy(artifact::getAdditionalRuntimeApks, ::deduplicateFile),
      testOptions = copyNewModel(artifact::getTestOptions, ::testOptionsFrom),
      bundleTaskName = copyNewModel(artifact::getBundleTaskName, ::deduplicateString),
      bundleTaskOutputListingFile = copyNewModel(artifact::getBundleTaskOutputListingFile, ::deduplicateString),
      apkFromBundleTaskName = copyNewModel(artifact::getApkFromBundleTaskName, ::deduplicateString),
      apkFromBundleTaskOutputListingFile = copyNewModel(artifact::getApkFromBundleTaskOutputListingFile, ::deduplicateString),
      codeShrinker = convertCodeShrinker(copyNewProperty(artifact::getCodeShrinker)),
      isTestArtifact = artifact.name == AndroidProject.ARTIFACT_ANDROID_TEST
    )
  }

  fun javaArtifactFrom(artifact: JavaArtifact): IdeJavaArtifactImpl {
    return IdeJavaArtifactImpl(
      name = artifact.name,
      compileTaskName = artifact.compileTaskName,
      assembleTaskName = artifact.assembleTaskName,
      assembleTaskOutputListingFile = copyNewProperty({ artifact.assembleTaskOutputListingFile }, ""),
      classesFolder = artifact.classesFolder,
      javaResourcesFolder = copyNewProperty(artifact::getJavaResourcesFolder),
      ideSetupTaskNames = copy(artifact::getIdeSetupTaskNames, ::deduplicateString).toList(),
      mutableGeneratedSourceFolders = copy(artifact::getGeneratedSourceFolders, ::deduplicateFile).distinct().toMutableList(),
      variantSourceProvider = copyNewModel(artifact::getVariantSourceProvider, ::sourceProviderFrom),
      multiFlavorSourceProvider = copyNewModel(artifact::getMultiFlavorSourceProvider, ::sourceProviderFrom),
      additionalClassesFolders = copy(artifact::getAdditionalClassesFolders, ::deduplicateFile).toList(),
      level2Dependencies = dependenciesFrom(artifact),
      mockablePlatformJar = copyNewProperty(artifact::getMockablePlatformJar),
      isTestArtifact = artifact.name == AndroidProject.ARTIFACT_UNIT_TEST
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
    }
    catch (e: UnsupportedOperationException) {
      emptyList()
    }
  }

  fun variantFrom(
    androidProject: IdeAndroidProject,
    variant: Variant,
    modelVersion: GradleVersion?
  ): IdeVariantImpl {
      val mergedFlavor = copyModel(variant.mergedFlavor, ::productFlavorFrom)
      val buildType = androidProject.buildTypes.find { it.buildType.name == variant.buildType }?.buildType

      fun <T> merge(f: IdeProductFlavor.() -> T, b:IdeBuildType.() -> T, combine: (T?, T?) -> T): T {
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
      mainArtifact = copyModel(variant.mainArtifact) { androidArtifactFrom(it, modelVersion) },
      androidTestArtifact =
          copy(variant::getExtraAndroidArtifacts) { androidArtifactFrom(it, modelVersion) }.firstOrNull { it.isTestArtifact },
      unitTestArtifact = copy(variant::getExtraJavaArtifacts) { javaArtifactFrom(it) }.firstOrNull { it.isTestArtifact },
      buildType = variant.buildType,
      productFlavors = ImmutableList.copyOf(variant.productFlavors),
      minSdkVersion = mergedFlavor.minSdkVersion,
      targetSdkVersion = mergedFlavor.targetSdkVersion,
      maxSdkVersion = mergedFlavor.maxSdkVersion,
      versionCode = mergedFlavor.versionCode,
      versionNameWithSuffix = mergedFlavor.versionName?.let { it + versionNameSuffix },
      versionNameSuffix = versionNameSuffix,
      instantAppCompatible = (modelVersion != null &&
                  modelVersion.isAtLeast(3, 3, 0, "alpha", 10, true) &&
                  variant.isInstantAppCompatible),
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
      deprecatedPreMergedApplicationId = mergedFlavor.applicationId
      )
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

  fun nativeAndroidProjectFrom(project: NativeAndroidProject): IdeNativeAndroidProjectImpl {
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
      defaultNdkVersion = copyNewProperty(project::getDefaultNdkVersion, ""),
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
        com.android.builder.model.v2.models.ndk.NativeBuildSystem.NDK_BUILD -> NativeBuildSystem.NDK_BUILD
        com.android.builder.model.v2.models.ndk.NativeBuildSystem.CMAKE -> NativeBuildSystem.CMAKE
        // No forward compatibility. Old Studio cannot open projects with newer AGP.
        else -> error("Unknown native build system: ${nativeModule.nativeBuildSystem}")
      },
      ndkVersion = nativeModule.ndkVersion,
      defaultNdkVersion = nativeModule.defaultNdkVersion,
      externalNativeBuildFile = nativeModule.externalNativeBuildFile
    )
  }

  fun lintOptionsFrom(options: LintOptions, modelVersion: GradleVersion?): IdeLintOptionsImpl = IdeLintOptionsImpl(
    baselineFile = if (modelVersion != null && modelVersion.isAtLeast(2, 3, 0, "beta", 2, true))
      options.baselineFile
    else
      null,
    lintConfig = copyNewProperty(options::getLintConfig),
    severityOverrides = options.severityOverrides?.let { ImmutableMap.copyOf(it) },
    isCheckTestSources = modelVersion != null &&
                         modelVersion.isAtLeast(2, 4, 0) &&
                         options.isCheckTestSources,
    isCheckDependencies = copyNewProperty({ options.isCheckDependencies }, false),
    disable = copy(options::getDisable, ::deduplicateString),
    enable = copy(options::getEnable, ::deduplicateString),
    check = options.check?.let { ImmutableSet.copyOf(it) },
    isAbortOnError = copyNewProperty({ options.isAbortOnError }, true),
    isAbsolutePaths = copyNewProperty({ options.isAbsolutePaths }, true),
    isNoLines = copyNewProperty({ options.isNoLines }, false),
    isQuiet = copyNewProperty({ options.isQuiet }, false),
    isCheckAllWarnings = copyNewProperty({ options.isCheckAllWarnings }, false),
    isIgnoreWarnings = copyNewProperty({ options.isIgnoreWarnings }, false),
    isWarningsAsErrors = copyNewProperty({ options.isWarningsAsErrors }, false),
    isIgnoreTestSources = copyNewProperty({ options.isIgnoreTestSources }, false),
    isCheckGeneratedSources = copyNewProperty({ options.isCheckGeneratedSources }, false),
    isExplainIssues = copyNewProperty({ options.isExplainIssues }, true),
    isShowAll = copyNewProperty({ options.isShowAll }, false),
    textReport = copyNewProperty({ options.textReport }, false),
    textOutput = copyNewProperty(options::getTextOutput),
    htmlReport = copyNewProperty({ options.htmlReport }, true),
    htmlOutput = copyNewProperty(options::getHtmlOutput),
    xmlReport = copyNewProperty({ options.xmlReport }, true),
    xmlOutput = copyNewProperty(options::getXmlOutput),
    isCheckReleaseBuilds = copyNewProperty({ options.isCheckReleaseBuilds }, true)
  )

  fun javaCompileOptionsFrom(options: JavaCompileOptions): IdeJavaCompileOptionsImpl {
    return IdeJavaCompileOptionsImpl(
      encoding = options.encoding,
      sourceCompatibility = options.sourceCompatibility,
      targetCompatibility = options.targetCompatibility,
      isCoreLibraryDesugaringEnabled = copyNewProperty({ options.isCoreLibraryDesugaringEnabled }, false))
  }

  fun aaptOptionsFrom(original: AaptOptions): IdeAaptOptionsImpl {
    return IdeAaptOptionsImpl(
      namespacing = convertNamespacing(copyNewProperty({ original.namespacing }, AaptOptions.Namespacing.DISABLED))
    )
  }

  fun ideVariantBuildInformationFrom(model: VariantBuildInformation): IdeVariantBuildInformation = IdeVariantBuildInformationImpl(
    variantName = model.variantName,
    assembleTaskName = model.assembleTaskName,
    assembleTaskOutputListingFile = model.assembleTaskOutputListingFile,
    bundleTaskName = model.bundleTaskName,
    bundleTaskOutputListingFile = model.bundleTaskOutputListingFile,
    apkFromBundleTaskName = model.apkFromBundleTaskName,
    apkFromBundleTaskOutputListingFile = model.apkFromBundleTaskOutputListingFile
  )

  fun createVariantBuildInformation(
    project: AndroidProject,
    agpVersion: GradleVersion?
  ): Collection<IdeVariantBuildInformation> {
    return if (agpVersion != null && agpVersion.compareIgnoringQualifiers("4.1.0") >= 0) {
      // make deep copy of VariantBuildInformation.
      project.variantsBuildInformation.map(::ideVariantBuildInformationFrom)
    }
    else emptyList()
    // VariantBuildInformation is not available.
  }

  fun viewBindingOptionsFrom(model: ViewBindingOptions): IdeViewBindingOptionsImpl {
    return IdeViewBindingOptionsImpl(enabled = model.isEnabled)
  }

  fun dependenciesInfoFrom(model: DependenciesInfo) = IdeDependenciesInfoImpl(
    includeInApk = model.includeInApk,
    includeInBundle = model.includeInBundle
  )

  fun androidGradlePluginProjectFlagsFrom(flags: AndroidGradlePluginProjectFlags): IdeAndroidGradlePluginProjectFlagsImpl =
    createIdeAndroidGradlePluginProjectFlagsImpl(flags.booleanFlagMap)

  fun androidProjectFrom(project: AndroidProject): IdeAndroidProjectImpl {
    // Old plugin versions do not return model version.
    val parsedModelVersion = GradleVersion.tryParse(project.modelVersion)

    val defaultConfigCopy: IdeProductFlavorContainer = copyModel(project.defaultConfig, ::productFlavorContainerFrom)
    val buildTypesCopy: Collection<IdeBuildTypeContainer> = copy(project::getBuildTypes, ::buildTypeContainerFrom)
    val productFlavorCopy: Collection<IdeProductFlavorContainer> = copy(project::getProductFlavors, ::productFlavorContainerFrom)
    val variantNamesCopy: Collection<String> =
            if (parsedModelVersion != null && parsedModelVersion < MODEL_VERSION_3_2_0)
                copy(fun(): Collection<String> = project.variants.map { it.name }, ::deduplicateString)
            else
                copy(project::getVariantNames, ::deduplicateString)
    val flavorDimensionCopy: Collection<String> = copy(project::getFlavorDimensions, ::deduplicateString)
    val bootClasspathCopy: Collection<String> = ImmutableList.copyOf(project.bootClasspath)
    val signingConfigsCopy: Collection<IdeSigningConfig> = copy(project::getSigningConfigs, ::signingConfigFrom)
    val lintOptionsCopy: IdeLintOptions = copyModel(project.lintOptions, { lintOptionsFrom(it, parsedModelVersion) })
    val javaCompileOptionsCopy = copyModel(project.javaCompileOptions, ::javaCompileOptionsFrom)
    val aaptOptionsCopy = copyModel(project.aaptOptions, ::aaptOptionsFrom)
    val dynamicFeaturesCopy: Collection<String> = copy(project::getDynamicFeatures, ::deduplicateString)
    val variantBuildInformation = createVariantBuildInformation(project, parsedModelVersion)
    val viewBindingOptionsCopy: IdeViewBindingOptions? = copyNewModel(project::getViewBindingOptions, ::viewBindingOptionsFrom)
    val dependenciesInfoCopy: IdeDependenciesInfo? = copyNewModel(project::getDependenciesInfo, ::dependenciesInfoFrom)
    val buildToolsVersionCopy = copyNewProperty(project::getBuildToolsVersion)
    val ndkVersionCopy = copyNewProperty(project::getNdkVersion)
    val groupId = if (parsedModelVersion != null && parsedModelVersion.isAtLeast(3, 6, 0, "alpha", 5, false)) project.groupId else null
    val lintRuleJarsCopy: List<File>? = copy(project::getLintRuleJars, ::deduplicateFile)

    // AndroidProject#isBaseSplit is always non null.
    val isBaseSplit = copyNewProperty({ project.isBaseSplit }, false)
    val agpFlags: IdeAndroidGradlePluginProjectFlags = copyNewProperty(
      { androidGradlePluginProjectFlagsFrom(project.flags) },
      createIdeAndroidGradlePluginProjectFlagsImpl()
    )
    return IdeAndroidProjectImpl(
      modelVersion = project.modelVersion,
      name = project.name,
      defaultConfig = defaultConfigCopy,
      buildTypes = buildTypesCopy,
      productFlavors = productFlavorCopy,
      variantNames = variantNamesCopy,
      flavorDimensions = flavorDimensionCopy,
      compileTarget = project.compileTarget,
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
      ndkVersion = ndkVersionCopy,
      resourcePrefix = project.resourcePrefix,
      groupId = groupId,
      projectType = getProjectType(project, parsedModelVersion),
      isBaseSplit = isBaseSplit,
      agpFlags = agpFlags)
  }

  fun testedTargetVariantFrom(variant: TestedTargetVariant): IdeTestedTargetVariantImpl {
    return IdeTestedTargetVariantImpl(targetProjectPath = variant.targetProjectPath, targetVariant = variant.targetVariant)
  }

  return object : ModelCache, ModelCacheTesting {
    override fun aaptOptionsFrom(original: AaptOptions): IdeAaptOptionsImpl = aaptOptionsFrom(original)
    override fun androidArtifactFrom(artifact: AndroidArtifact, agpVersion: GradleVersion?): IdeAndroidArtifactImpl =
      androidArtifactFrom(artifact, agpVersion)

    override fun apiVersionFrom(version: ApiVersion): IdeApiVersionImpl = apiVersionFrom(version)
    override fun buildTypeContainerFrom(container: BuildTypeContainer): IdeBuildTypeContainerImpl = buildTypeContainerFrom(container)
    override fun buildTypeFrom(buildType: BuildType): IdeBuildTypeImpl = buildTypeFrom(buildType)
    override fun classFieldFrom(classField: ClassField): IdeClassFieldImpl = classFieldFrom(classField)
    override fun filterDataFrom(data: FilterData): IdeFilterDataImpl = filterDataFrom(data)
    override fun javaArtifactFrom(artifact: JavaArtifact): IdeJavaArtifactImpl = javaArtifactFrom(artifact)
    override fun javaCompileOptionsFrom(options: JavaCompileOptions): IdeJavaCompileOptionsImpl = javaCompileOptionsFrom(options)
    override fun productFlavorContainerFrom(container: ProductFlavorContainer): IdeProductFlavorContainerImpl =
      productFlavorContainerFrom(container)

    override fun productFlavorFrom(flavor: ProductFlavor): IdeProductFlavorImpl = productFlavorFrom(flavor)
    override fun signingConfigFrom(config: SigningConfig): IdeSigningConfigImpl = signingConfigFrom(config)
    override fun sourceProviderContainerFrom(container: SourceProviderContainer): IdeSourceProviderContainerImpl =
      sourceProviderContainerFrom(container)

    override fun testedTargetVariantFrom(variant: TestedTargetVariant): IdeTestedTargetVariantImpl = testedTargetVariantFrom(variant)
    override fun testOptionsFrom(testOptions: TestOptions): IdeTestOptionsImpl = testOptionsFrom(testOptions)
    override fun vectorDrawablesOptionsFrom(options: VectorDrawablesOptions): IdeVectorDrawablesOptionsImpl =
      vectorDrawablesOptionsFrom(options)

    override fun viewBindingOptionsFrom(model: ViewBindingOptions): IdeViewBindingOptionsImpl = viewBindingOptionsFrom(model)
    override fun sourceProviderFrom(provider: SourceProvider): IdeSourceProviderImpl = sourceProviderFrom(provider)
    override fun lintOptionsFrom(options: LintOptions, modelVersion: GradleVersion?): IdeLintOptionsImpl =
      lintOptionsFrom(options, modelVersion)

    override fun nativeToolchainFrom(toolchain: NativeToolchain): IdeNativeToolchainImpl = nativeToolchainFrom(toolchain)
    override fun nativeArtifactFrom(artifact: NativeArtifact): IdeNativeArtifactImpl = nativeArtifactFrom(artifact)
    override fun nativeFileFrom(file: NativeFile): IdeNativeFileImpl = nativeFileFrom(file)
    override fun nativeSettingsFrom(settings: NativeSettings): IdeNativeSettingsImpl = nativeSettingsFrom(settings)
    override fun androidGradlePluginProjectFlagsFrom(flags: AndroidGradlePluginProjectFlags): IdeAndroidGradlePluginProjectFlagsImpl =
      androidGradlePluginProjectFlagsFrom(flags)

    override fun dependenciesFrom(artifact: BaseArtifact): IdeDependencies = dependenciesFrom(artifact)
    override fun libraryFrom(javaLibrary: JavaLibrary): IdeLibrary = libraryFrom(javaLibrary)
    override fun libraryFrom(projectPath: String, artifactAddress: String, buildId: String?): IdeLibrary =
      libraryFrom(projectPath, artifactAddress, buildId)

    override fun computeAddress(library: Library): String = computeAddress(library)
    override fun isLocalAarModule(androidLibrary: AndroidLibrary): Boolean = isLocalAarModule(androidLibrary)
    override fun mavenCoordinatesFrom(coordinates: MavenCoordinates): IdeMavenCoordinatesImpl = mavenCoordinatesFrom(coordinates)

    override fun variantFrom(androidProject: IdeAndroidProject, variant: Variant, modelVersion: GradleVersion?): IdeVariantImpl =
        variantFrom(androidProject, variant, modelVersion)
    override fun androidProjectFrom(project: AndroidProject): IdeAndroidProjectImpl = androidProjectFrom(project)
    override fun androidArtifactOutputFrom(output: OutputFile): IdeAndroidArtifactOutputImpl = androidArtifactOutputFrom(output)
    override fun nativeModuleFrom(nativeModule: NativeModule): IdeNativeModuleImpl = nativeModuleFrom(nativeModule)
    override fun nativeVariantAbiFrom(variantAbi: NativeVariantAbi): IdeNativeVariantAbiImpl = nativeVariantAbiFrom(variantAbi)
    override fun nativeAndroidProjectFrom(project: NativeAndroidProject): IdeNativeAndroidProjectImpl = nativeAndroidProjectFrom(project)
  }
}

private inline fun <K : Any, V> copyModel(key: K, mappingFunction: (K) -> V): V = mappingFunction(key)

@JvmName("copyModelNullable")
private inline fun <K : Any, V> copyModel(key: K?, mappingFunction: (K) -> V): V? = key?.let(mappingFunction)

private inline fun <K, V : Any> copyNewModel(
  getter: () -> K?,
  mapper: (K) -> V
): V? {
  return try {
    val key: K? = getter()
    if (key != null) mapper(key) else null
  }
  catch (ignored: UnsupportedOperationException) {
    null
  }
}

/**
 * NOTE: Multiple overloads are intentionally ambiguous to prevent lambdas from being used directly.
 *       Please use function references or anonymous functions which seeds type inference.
 **/
@Suppress("unused", "UNUSED_PARAMETER")
private inline fun <T : Collection<*>> copyNewProperty(propertyInvoker: () -> T, defaultValue: T): Unit = error(
  "Cannot be called. Use copy() method.")

/**
 * NOTE: Multiple overloads are intentionally ambiguous to prevent lambdas from being used directly.
 *       Please use function references or anonymous functions which seeds type inference.
 **/
@Suppress("unused", "UNUSED_PARAMETER")
private inline fun <T : Map<*, *>> copyNewProperty(propertyInvoker: () -> T, defaultValue: T): Unit = error(
  "Cannot be called. Use copy() method.")

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

/**
 * NOTE: Multiple overloads are intentionally ambiguous to prevent lambdas from being used directly.
 *       Please use function references or anonymous functions which seeds type inference.
 **/
private inline fun <T : Any> copyNewProperty(propertyInvoker: () -> T, defaultValue: T): T {
  return try {
    propertyInvoker()
  }
  catch (ignored: UnsupportedOperationException) {
    defaultValue
  }
}

/**
 * NOTE: Multiple overloads are intentionally ambiguous to prevent lambdas from being used directly.
 *       Please use function references or anonymous functions which seeds type inference.
 **/
private inline fun <T : Any?> copyNewProperty(propertyInvoker: () -> T?): T? {
  return try {
    propertyInvoker()
  }
  catch (ignored: UnsupportedOperationException) {
    null
  }
}

private inline fun <K, V> copy(original: () -> Collection<K>, mapper: (K) -> V): List<V> =
  ModelCache.safeGet(original, listOf()).map(mapper)

private inline fun <K, V> copy(original: () -> Set<K>, mapper: (K) -> V): Set<V> =
  ModelCache.safeGet(original, setOf()).map(mapper).toSet()

@JvmName("copyNullableCollection")
private inline fun <K, V> copy(original: () -> Collection<K>?, mapper: (K) -> V): List<V>? =
  ModelCache.safeGet(original, null)?.map(mapper)

private inline fun <K, V, R> copy(original: () -> Map<K, V>, mapper: (V) -> R): Map<K, R> =
  ModelCache.safeGet(original, mapOf()).mapValues { (_, v) -> mapper(v) }

private inline fun <T> copyNewPropertyWithDefault(propertyInvoker: () -> T, defaultValue: () -> T): T {
  return try {
    propertyInvoker()
  }
  catch (ignored: UnsupportedOperationException) {
    defaultValue()
  }
}

private fun <T> MutableMap<T, T>.internCore(core: T): T = putIfAbsent(core, core) ?: core

private fun getSymbolFilePath(androidLibrary: AndroidLibrary): String {
  return try {
    androidLibrary.symbolFile.path
  }
  catch (e: UnsupportedOperationException) {
    File(androidLibrary.folder, SdkConstants.FN_RESOURCE_TEXT).path
  }
}

private fun convertExecution(execution: TestOptions.Execution?): IdeTestOptions.Execution? {
  return if (execution == null) null
  else when (execution) {
    TestOptions.Execution.HOST -> IdeTestOptions.Execution.HOST
    TestOptions.Execution.ANDROID_TEST_ORCHESTRATOR -> IdeTestOptions.Execution.ANDROID_TEST_ORCHESTRATOR
    TestOptions.Execution.ANDROIDX_TEST_ORCHESTRATOR -> IdeTestOptions.Execution.ANDROIDX_TEST_ORCHESTRATOR
    else -> throw IllegalStateException("Unknown execution option: $execution")
  }
}

private fun convertCodeShrinker(codeShrinker: com.android.builder.model.CodeShrinker?): CodeShrinker? {
  return if (codeShrinker == null) null
  else when (codeShrinker) {
    com.android.builder.model.CodeShrinker.PROGUARD -> CodeShrinker.PROGUARD
    com.android.builder.model.CodeShrinker.R8 -> CodeShrinker.R8
    else -> throw IllegalStateException("Unknown code shrinker option: $codeShrinker")
  }
}

private fun convertNamespacing(namespacing: AaptOptions.Namespacing): IdeAaptOptions.Namespacing {
  return when (namespacing) {
    AaptOptions.Namespacing.DISABLED -> IdeAaptOptions.Namespacing.DISABLED
    AaptOptions.Namespacing.REQUIRED -> IdeAaptOptions.Namespacing.REQUIRED
    else -> throw IllegalStateException("Unknown namespacing option: $namespacing")
  }
}

private fun Int.toIdeAndroidProjectType(): IdeAndroidProjectType = when(this) {
    AndroidProjectTypes.PROJECT_TYPE_APP -> IdeAndroidProjectType.PROJECT_TYPE_APP
    AndroidProjectTypes.PROJECT_TYPE_LIBRARY -> IdeAndroidProjectType.PROJECT_TYPE_LIBRARY
    AndroidProjectTypes.PROJECT_TYPE_TEST -> IdeAndroidProjectType.PROJECT_TYPE_TEST
    AndroidProjectTypes.PROJECT_TYPE_ATOM -> IdeAndroidProjectType.PROJECT_TYPE_ATOM
    AndroidProjectTypes.PROJECT_TYPE_INSTANTAPP -> IdeAndroidProjectType.PROJECT_TYPE_INSTANTAPP
    AndroidProjectTypes.PROJECT_TYPE_FEATURE -> IdeAndroidProjectType.PROJECT_TYPE_FEATURE
    AndroidProjectTypes.PROJECT_TYPE_DYNAMIC_FEATURE -> IdeAndroidProjectType.PROJECT_TYPE_DYNAMIC_FEATURE
    else -> error("Unknown Android project type: $this")
}

private fun getProjectType(project: AndroidProject, modelVersion: GradleVersion?): IdeAndroidProjectType {
  if (modelVersion != null && modelVersion.isAtLeast(2, 3, 0)) {
    return project.projectType.toIdeAndroidProjectType()
  }
  // Support for old Android Gradle Plugins must be maintained.
  return if (project.isLibrary) IdeAndroidProjectType.PROJECT_TYPE_LIBRARY else IdeAndroidProjectType.PROJECT_TYPE_APP
}

@VisibleForTesting
/** For older AGP versions pick a variant name based on a heuristic  */
fun getDefaultVariant(variantNames: Collection<String>): String? {
  // Corner case of variant filter accidentally removing all variants.
  if (variantNames.isEmpty()) {
    return null
  }

  // Favor the debug variant
  if (variantNames.contains("debug")) {
    return "debug"
  }
  // Otherwise the first alphabetically that has debug as a build type.
  val sortedNames = ImmutableSortedSet.copyOf(variantNames)
  for (variantName in sortedNames) {
    if (variantName.endsWith("Debug")) {
      return variantName
    }
  }
  // Otherwise fall back to the first alphabetically
  return sortedNames.first()
}

private fun createIdeAndroidGradlePluginProjectFlagsImpl(booleanFlagMap: Map<BooleanFlag, Boolean>): IdeAndroidGradlePluginProjectFlagsImpl {
  return IdeAndroidGradlePluginProjectFlagsImpl(
    applicationRClassConstantIds = booleanFlagMap.getBooleanFlag(BooleanFlag.APPLICATION_R_CLASS_CONSTANT_IDS),
    testRClassConstantIds = booleanFlagMap.getBooleanFlag(BooleanFlag.TEST_R_CLASS_CONSTANT_IDS),
    transitiveRClasses = booleanFlagMap.getBooleanFlag(BooleanFlag.TRANSITIVE_R_CLASS),
    usesCompose = booleanFlagMap.getBooleanFlag(BooleanFlag.JETPACK_COMPOSE),
    mlModelBindingEnabled = booleanFlagMap.getBooleanFlag(BooleanFlag.ML_MODEL_BINDING)
  )
}

/**
 * Create an empty set of flags for older AGPs and for studio serialization.
 */
private fun createIdeAndroidGradlePluginProjectFlagsImpl() = createIdeAndroidGradlePluginProjectFlagsImpl(booleanFlagMap = emptyMap())

private fun Map<BooleanFlag, Boolean>.getBooleanFlag(flag: BooleanFlag): Boolean = this[flag] ?: flag.legacyDefault

