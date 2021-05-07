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

package com.android.tools.idea.gradle.project.sync

import com.android.AndroidProjectTypes
import com.android.SdkConstants
import com.android.build.FilterData
import com.android.build.OutputFile
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
import com.android.builder.model.ProductFlavor
import com.android.builder.model.ProductFlavorContainer
import com.android.builder.model.SigningConfig
import com.android.builder.model.SourceProvider
import com.android.builder.model.SourceProviderContainer
import com.android.builder.model.TestOptions
import com.android.builder.model.TestedTargetVariant
import com.android.builder.model.Variant
import com.android.builder.model.VectorDrawablesOptions
import com.android.builder.model.ViewBindingOptions
import com.android.builder.model.v2.models.ndk.NativeModule
import com.android.tools.idea.gradle.model.CodeShrinker
import com.android.tools.idea.gradle.model.IdeAndroidProject
import com.android.tools.idea.gradle.model.IdeAndroidProjectType
import com.android.tools.idea.gradle.model.IdeArtifactName
import com.android.tools.idea.gradle.model.IdeDependencies
import com.android.tools.idea.gradle.model.IdeLibrary
import com.android.tools.idea.gradle.model.IdeTestOptions
import com.android.tools.idea.gradle.model.impl.BuildFolderPaths
import com.android.tools.idea.gradle.model.impl.IdeAaptOptionsImpl
import com.android.tools.idea.gradle.model.impl.IdeAndroidArtifactImpl
import com.android.tools.idea.gradle.model.impl.IdeAndroidArtifactOutputImpl
import com.android.tools.idea.gradle.model.impl.IdeAndroidGradlePluginProjectFlagsImpl
import com.android.tools.idea.gradle.model.impl.IdeAndroidProjectImpl
import com.android.tools.idea.gradle.model.impl.IdeApiVersionImpl
import com.android.tools.idea.gradle.model.impl.IdeBuildTypeContainerImpl
import com.android.tools.idea.gradle.model.impl.IdeBuildTypeImpl
import com.android.tools.idea.gradle.model.impl.IdeClassFieldImpl
import com.android.tools.idea.gradle.model.impl.IdeFilterDataImpl
import com.android.tools.idea.gradle.model.impl.IdeJavaArtifactImpl
import com.android.tools.idea.gradle.model.impl.IdeJavaCompileOptionsImpl
import com.android.tools.idea.gradle.model.impl.IdeLintOptionsImpl
import com.android.tools.idea.gradle.model.impl.IdeMavenCoordinatesImpl
import com.android.tools.idea.gradle.model.impl.IdeProductFlavorContainerImpl
import com.android.tools.idea.gradle.model.impl.IdeProductFlavorImpl
import com.android.tools.idea.gradle.model.impl.IdeSigningConfigImpl
import com.android.tools.idea.gradle.model.impl.IdeSourceProviderContainerImpl
import com.android.tools.idea.gradle.model.impl.IdeSourceProviderImpl
import com.android.tools.idea.gradle.model.impl.IdeTestOptionsImpl
import com.android.tools.idea.gradle.model.impl.IdeTestedTargetVariantImpl
import com.android.tools.idea.gradle.model.impl.IdeVariantImpl
import com.android.tools.idea.gradle.model.impl.IdeVectorDrawablesOptionsImpl
import com.android.tools.idea.gradle.model.impl.IdeViewBindingOptionsImpl
import com.android.tools.idea.gradle.model.impl.ndk.v1.IdeNativeAndroidProjectImpl
import com.android.tools.idea.gradle.model.impl.ndk.v1.IdeNativeArtifactImpl
import com.android.tools.idea.gradle.model.impl.ndk.v1.IdeNativeFileImpl
import com.android.tools.idea.gradle.model.impl.ndk.v1.IdeNativeSettingsImpl
import com.android.tools.idea.gradle.model.impl.ndk.v1.IdeNativeToolchainImpl
import com.android.tools.idea.gradle.model.impl.ndk.v1.IdeNativeVariantAbiImpl
import com.android.tools.idea.gradle.model.impl.ndk.v2.IdeNativeModuleImpl
import com.android.ide.common.repository.GradleVersion
import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.ImmutableSortedSet
import java.io.File

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
    fun create(buildFolderPaths: BuildFolderPaths): ModelCache  {
      return modelCacheV1Impl(buildFolderPaths)
    }

    @JvmStatic
    fun create(): ModelCache {
      return modelCacheV1Impl(BuildFolderPaths())
    }

    @JvmStatic
    inline fun <T> safeGet(original: () -> T, default: T): T = try {
      original()
    }
    catch (ignored: UnsupportedOperationException) {
      default
    }
  }
}

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

internal val MODEL_VERSION_3_2_0 = GradleVersion.parse("3.2.0")

internal inline fun <K : Any, V> copyModel(key: K, mappingFunction: (K) -> V): V = mappingFunction(key)

internal inline fun <K : Any, R: Any, V> copyModel(key: K, key2: R, mappingFunction: (K, R) -> V): V = mappingFunction(key, key2)

@JvmName("copyModelNullable")
internal inline fun <K : Any, V> copyModel(key: K?, mappingFunction: (K) -> V): V? = key?.let(mappingFunction)

internal inline fun <K, V : Any> copyNewModel(
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
internal inline fun <T : Collection<*>> copyNewProperty(propertyInvoker: () -> T, defaultValue: T): Unit = error(
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
internal inline fun <T : Any> copyNewProperty(propertyInvoker: () -> T, defaultValue: T): T {
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
internal inline fun <T : Any?>copyNewProperty(propertyInvoker: () -> T?): T? {
  return try {
    propertyInvoker()
  }
  catch (ignored: UnsupportedOperationException) {
    null
  }
}

internal inline fun <K, V> copy(original: () -> Collection<K>, mapper: (K) -> V): List<V> =
  ModelCache.safeGet(original, listOf()).map(mapper)

internal inline fun <K, R, V> copy(o1: () -> Collection<K>, o2: () -> Collection<R>,  mapper: (K, R) -> V): List<V> {
  val original1 = ModelCache.safeGet(o1, listOf())
  val original2 = ModelCache.safeGet(o2, listOf())
  return original1.zip(original2).toMap().map { (k, v) -> mapper(k, v) }
}

internal inline fun <K, V> copy(original: () -> Set<K>, mapper: (K) -> V): Set<V> =
  ModelCache.safeGet(original, setOf()).map(mapper).toSet()

@JvmName("copyNullableCollection")
internal inline fun <K, V> copy(original: () -> Collection<K>?, mapper: (K) -> V): List<V>? =
  ModelCache.safeGet(original, null)?.map(mapper)

internal inline fun <K, V, R> copy(original: () -> Map<K, V>, mapper: (V) -> R): Map<K, R> =
  ModelCache.safeGet(original, mapOf()).mapValues { (_, v) -> mapper(v) }

@JvmName("copyNullableMap")
internal inline fun <K, V, R> copy(original: () -> Map<K, V>?, mapper: (V) -> R): Map<K, R>? =
  ModelCache.safeGet(original, mapOf())?.mapValues { (_, v) -> mapper(v) }

internal inline fun <K, V, R> copyMapList(original: () -> Map<K, V?>, mapper: (K, V?) -> R): List<R> =
  ModelCache.safeGet(original, mapOf()).map { (k, v) -> mapper(k, v) }

internal inline fun <T> copyNewPropertyWithDefault(propertyInvoker: () -> T, defaultValue: () -> T): T {
  return try {
    propertyInvoker()
  }
  catch (ignored: UnsupportedOperationException) {
    defaultValue()
  }
}

internal fun <T> MutableMap<T, T>.internCore(core: T): T = putIfAbsent(core, core) ?: core

internal fun getSymbolFilePath(androidLibrary: AndroidLibrary): String {
  return try {
    androidLibrary.symbolFile.path
  }
  catch (e: UnsupportedOperationException) {
    File(androidLibrary.folder, SdkConstants.FN_RESOURCE_TEXT).path
  }
}

internal fun convertExecution(execution: TestOptions.Execution?): IdeTestOptions.Execution? {
  return if (execution == null) null
  else when (execution) {
    TestOptions.Execution.HOST -> IdeTestOptions.Execution.HOST
    TestOptions.Execution.ANDROID_TEST_ORCHESTRATOR -> IdeTestOptions.Execution.ANDROID_TEST_ORCHESTRATOR
    TestOptions.Execution.ANDROIDX_TEST_ORCHESTRATOR -> IdeTestOptions.Execution.ANDROIDX_TEST_ORCHESTRATOR
    else -> throw IllegalStateException("Unknown execution option: $execution")
  }
}

internal fun convertCodeShrinker(codeShrinker: com.android.builder.model.CodeShrinker?): CodeShrinker? {
  return if (codeShrinker == null) null
  else when (codeShrinker) {
    com.android.builder.model.CodeShrinker.PROGUARD -> CodeShrinker.PROGUARD
    com.android.builder.model.CodeShrinker.R8 -> CodeShrinker.R8
    else -> throw IllegalStateException("Unknown code shrinker option: $codeShrinker")
  }
}

internal fun convertCodeShrinker(codeShrinker: com.android.builder.model.v2.ide.CodeShrinker?): CodeShrinker? {
  return if (codeShrinker == null) null
  else when (codeShrinker) {
    com.android.builder.model.v2.ide.CodeShrinker.PROGUARD -> CodeShrinker.PROGUARD
    com.android.builder.model.v2.ide.CodeShrinker.R8 -> CodeShrinker.R8
    else -> throw IllegalStateException("Unknown code shrinker option: $codeShrinker")
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

internal fun getProjectType(project: AndroidProject, modelVersion: GradleVersion?): IdeAndroidProjectType {
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

@JvmName("createIdeAndroidGradlePluginProjectFlagsImpl")
fun createIdeAndroidGradlePluginProjectFlagsImpl(booleanFlagMap: Map<BooleanFlag, Boolean>): IdeAndroidGradlePluginProjectFlagsImpl {
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
internal fun createIdeAndroidGradlePluginProjectFlagsImpl() = createIdeAndroidGradlePluginProjectFlagsImpl(booleanFlagMap = emptyMap())

internal fun Map<BooleanFlag, Boolean>.getBooleanFlag(flag: BooleanFlag): Boolean = this[flag] ?: flag.legacyDefault

internal fun convertArtifactName(name: String): IdeArtifactName = when(name) {
  AndroidProject.ARTIFACT_MAIN -> IdeArtifactName.MAIN
  AndroidProject.ARTIFACT_ANDROID_TEST -> IdeArtifactName.ANDROID_TEST
  AndroidProject.ARTIFACT_UNIT_TEST -> IdeArtifactName.UNIT_TEST
  else -> error("Invalid android artifact name: $name")
}

internal fun convertV2ArtifactName(name: String): IdeArtifactName = when(name) {
  "_main_" -> IdeArtifactName.MAIN
  "_android_test_" -> IdeArtifactName.ANDROID_TEST
  "_unit_test_" -> IdeArtifactName.UNIT_TEST
  else -> error("Invalid android artifact name: $name")
}