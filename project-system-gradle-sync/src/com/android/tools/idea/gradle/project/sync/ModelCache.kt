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
import com.android.build.OutputFile
import com.android.builder.model.AndroidGradlePluginProjectFlags.BooleanFlag
import com.android.builder.model.AndroidLibrary
import com.android.builder.model.AndroidProject
import com.android.builder.model.NativeAndroidProject
import com.android.builder.model.NativeVariantAbi
import com.android.builder.model.TestOptions
import com.android.builder.model.Variant
import com.android.builder.model.v2.models.ndk.NativeModule
import com.android.tools.idea.gradle.model.CodeShrinker
import com.android.tools.idea.gradle.model.IdeAndroidProject
import com.android.tools.idea.gradle.model.IdeAndroidProjectType
import com.android.tools.idea.gradle.model.IdeArtifactName
import com.android.tools.idea.gradle.model.IdeTestOptions
import com.android.tools.idea.gradle.model.impl.BuildFolderPaths
import com.android.tools.idea.gradle.model.impl.IdeAndroidArtifactOutputImpl
import com.android.tools.idea.gradle.model.impl.IdeAndroidGradlePluginProjectFlagsImpl
import com.android.tools.idea.gradle.model.impl.IdeAndroidProjectImpl
import com.android.tools.idea.gradle.model.impl.IdeVariantImpl
import com.android.tools.idea.gradle.model.impl.ndk.v1.IdeNativeAndroidProjectImpl
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

internal fun <T> MutableMap<T, T>.internCore(core: T): T = putIfAbsent(core, core) ?: core

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
  else -> error("Invalid android artifact name: $name")
}