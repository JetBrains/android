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

import com.android.build.OutputFile
import com.android.builder.model.AndroidLibrary
import com.android.builder.model.AndroidProject
import com.android.builder.model.Library
import com.android.builder.model.NativeAndroidProject
import com.android.builder.model.NativeVariantAbi
import com.android.builder.model.Variant
import com.android.builder.model.v2.ide.BasicVariant
import com.android.builder.model.v2.models.AndroidDsl
import com.android.builder.model.v2.models.Versions
import com.android.builder.model.v2.models.VariantDependencies
import com.android.builder.model.v2.models.ndk.NativeModule
import com.android.tools.idea.gradle.model.IdeAndroidProject
import com.android.tools.idea.gradle.model.IdeArtifactName
import com.android.tools.idea.gradle.model.impl.BuildFolderPaths
import com.android.tools.idea.gradle.model.impl.IdeAndroidArtifactOutputImpl
import com.android.tools.idea.gradle.model.impl.IdeAndroidProjectImpl
import com.android.tools.idea.gradle.model.impl.IdeVariantImpl
import com.android.tools.idea.gradle.model.impl.ndk.v1.IdeNativeAndroidProjectImpl
import com.android.tools.idea.gradle.model.impl.ndk.v1.IdeNativeVariantAbiImpl
import com.android.tools.idea.gradle.model.impl.ndk.v2.IdeNativeModuleImpl
import com.android.ide.common.repository.GradleVersion
import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.ImmutableSortedSet
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.annotations.SystemIndependent
import java.io.File

interface ModelCache {

  fun variantFrom(
    androidProject: IdeAndroidProject,
    variant: Variant,
    modelVersion: GradleVersion?,
    androidModuleId: ModuleId
  ): IdeVariantImpl
  fun variantFrom(
    androidProject: IdeAndroidProject,
    basicVariant: BasicVariant,
    variant: com.android.builder.model.v2.ide.Variant,
    modelVersion: GradleVersion?,
    variantDependencies: VariantDependencies,
    buildNameMap: Map<String, File>
  ): IdeVariantImpl
  fun androidProjectFrom(project: AndroidProject): IdeAndroidProjectImpl
  fun androidProjectFrom(
    basicProject: com.android.builder.model.v2.models.BasicAndroidProject,
    project: com.android.builder.model.v2.models.AndroidProject,
    androidVersion: Versions,
    androidDsl: AndroidDsl
  ): IdeAndroidProjectImpl
  fun androidArtifactOutputFrom(output: OutputFile): IdeAndroidArtifactOutputImpl

  fun nativeModuleFrom(nativeModule: NativeModule): IdeNativeModuleImpl
  fun nativeVariantAbiFrom(variantAbi: NativeVariantAbi): IdeNativeVariantAbiImpl
  fun nativeAndroidProjectFrom(project: NativeAndroidProject, ndkVersion: String): IdeNativeAndroidProjectImpl

  companion object {
    const val LOCAL_AARS = "__local_aars__"

    @JvmStatic
    fun create(useV2BuilderModels: Boolean, buildFolderPaths: BuildFolderPaths): ModelCache  {
      if (useV2BuilderModels) {
        return modelCacheV2Impl(buildFolderPaths.buildRootDirectory)
      }
      return modelCacheV1Impl(buildFolderPaths)
    }

    @JvmStatic
    fun create(useV2BuilderModels: Boolean): ModelCache {
      return if (useV2BuilderModels) {
        modelCacheV2Impl(null)
      }
      else {
        modelCacheV1Impl(BuildFolderPaths())
      }
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

data class ModuleId(val gradlePath: String, val buildId: String)

internal inline fun <K : Any, V> copyModel(key: K, mappingFunction: (K) -> V): V = mappingFunction(key)

internal inline fun <K : Any, R: Any, V> copyModel(key: K, key2: R, mappingFunction: (K, R) -> V): V = mappingFunction(key, key2)

@JvmName("copyModelNullable")
internal inline fun <K : Any, V> copyModel(key: K?, mappingFunction: (K) -> V): V? = key?.let(mappingFunction)

@JvmName("copyModelNullable")
internal inline fun <K : Any, R : Any, V> copyModel(key: K?,
                                                    key2: R?,
                                                    mappingFunction: (K, R) -> V): V? =
  if (key != null && key2 != null) mappingFunction(key, key2) else null

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

internal inline fun <K, V> copy(original: () -> Set<K>, mapper: (K) -> V): Set<V> =
  ModelCache.safeGet(original, setOf()).map(mapper).toSet()

internal inline fun <K, V, R> copy(original: () -> Map<K, V>, mapper: (V) -> R): Map<K, R> =
  ModelCache.safeGet(original, mapOf()).mapValues { (_, v) -> mapper(v) }

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

internal fun convertArtifactName(name: String): IdeArtifactName = when(name) {
  AndroidProject.ARTIFACT_MAIN -> IdeArtifactName.MAIN
  AndroidProject.ARTIFACT_ANDROID_TEST -> IdeArtifactName.ANDROID_TEST
  AndroidProject.ARTIFACT_UNIT_TEST -> IdeArtifactName.UNIT_TEST
  AndroidProject.ARTIFACT_TEST_FIXTURES -> IdeArtifactName.TEST_FIXTURES
  else -> error("Invalid android artifact name: $name")
}

/**
 * Converts the artifact address into a name that will be used by the IDE to represent the library.
 */
internal fun convertToLibraryName(libraryArtifactAddress: String, projectBasePath: File): String {
  if (libraryArtifactAddress.startsWith("${ModelCache.LOCAL_AARS}:")) {
    return adjustLocalLibraryName(
      File(libraryArtifactAddress.removePrefix("${ModelCache.LOCAL_AARS}:").substringBefore(":")),
      projectBasePath
    )
  }

  return convertMavenCoordinateStringToIdeLibraryName(libraryArtifactAddress)
}

/**
 * Converts the name of a maven form dependency from the format that is returned from the Android Gradle plugin [Library]
 * to the name that will be used to setup the library in the IDE. The Android Gradle plugin uses maven co-ordinates to
 * represent the library.
 *
 * In order to share the libraries between Android and non-Android modules we want to convert the artifact
 * co-ordinate string that will match the ones that would be set up in the IDE for non-android modules.
 *
 * Current this method removes any @jar from the end of the coordinate since IDEA defaults to this and doesn't display
 * it.
 */
private fun convertMavenCoordinateStringToIdeLibraryName(mavenCoordinate: String): String {
  return mavenCoordinate.removeSuffix("@jar")
}

/**
 * Attempts to shorten the library name by making paths relative and makes paths system independent.
 * Name shortening is required because the maximum allowed file name length is 256 characters and .jar files located in deep
 * directories in CI environments may exceed this limit.
 */
private fun adjustLocalLibraryName(artifactFile: File, projectBasePath: File): @SystemIndependent String {
  val maybeRelative = artifactFile.relativeToOrSelf(projectBasePath)
  if (!FileUtil.filesEqual(maybeRelative, artifactFile)) {
    return FileUtil.toSystemIndependentName(File(".${File.separator}${maybeRelative}").path)
  }

  return FileUtil.toSystemIndependentName(artifactFile.path)
}
