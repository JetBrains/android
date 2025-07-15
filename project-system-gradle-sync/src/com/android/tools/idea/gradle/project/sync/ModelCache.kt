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
import com.android.builder.model.AndroidProject
import com.android.builder.model.Library
import com.android.builder.model.NativeAndroidProject
import com.android.builder.model.NativeVariantAbi
import com.android.builder.model.Variant
import com.android.builder.model.v2.ide.BasicVariant
import com.android.builder.model.v2.models.AndroidDsl
import com.android.builder.model.v2.models.BasicAndroidProject
import com.android.builder.model.v2.models.VariantDependencies
import com.android.builder.model.v2.models.ndk.NativeModule
import com.android.ide.common.repository.AgpVersion
import com.android.ide.gradle.model.GradlePropertiesModel
import com.android.ide.gradle.model.LegacyAndroidGradlePluginProperties
import com.android.tools.idea.gradle.model.ARTIFACT_NAME_ANDROID_TEST
import com.android.tools.idea.gradle.model.ARTIFACT_NAME_MAIN
import com.android.tools.idea.gradle.model.ARTIFACT_NAME_SCREENSHOT_TEST
import com.android.tools.idea.gradle.model.ARTIFACT_NAME_TEST_FIXTURES
import com.android.tools.idea.gradle.model.ARTIFACT_NAME_UNIT_TEST
import com.android.tools.idea.gradle.model.IdeArtifactName
import com.android.tools.idea.gradle.model.impl.BuildFolderPaths
import com.android.tools.idea.gradle.model.impl.IdeAndroidArtifactCoreImpl
import com.android.tools.idea.gradle.model.impl.IdeAndroidArtifactOutputImpl
import com.android.tools.idea.gradle.model.impl.IdeAndroidProjectImpl
import com.android.tools.idea.gradle.model.impl.IdeJavaArtifactCoreImpl
import com.android.tools.idea.gradle.model.impl.IdeVariantCoreImpl
import com.android.tools.idea.gradle.model.impl.ndk.v1.IdeNativeAndroidProjectImpl
import com.android.tools.idea.gradle.model.impl.ndk.v1.IdeNativeVariantAbiImpl
import com.android.tools.idea.gradle.model.impl.ndk.v2.IdeNativeModuleImpl
import com.android.tools.idea.gradle.project.sync.ModelCache.Companion.LOCAL_AARS
import com.android.tools.idea.gradle.project.sync.ModelCache.Companion.LOCAL_JARS
import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.ImmutableSortedSet
import com.intellij.openapi.util.io.FileUtil
import org.gradle.tooling.model.gradle.BasicGradleProject
import org.jetbrains.annotations.SystemIndependent
import java.io.File

interface ModelCache {

  interface V1 : ModelCache {
    fun variantFrom(
      androidProject: IdeAndroidProjectImpl,
      variant: Variant,
      legacyAndroidGradlePluginProperties: LegacyAndroidGradlePluginProperties?,
      modelVersions: ModelVersions,
      androidModuleId: ModuleId
    ): ModelResult<IdeVariantWithPostProcessor>

    fun androidProjectFrom(
      rootBuildId: BuildId,
      buildId: BuildId,
      projectPath: String,
      project: AndroidProject,
      legacyAndroidGradlePluginProperties: LegacyAndroidGradlePluginProperties?,
      gradlePropertiesModel: GradlePropertiesModel,
      defaultVariantName: String?
    ): ModelResult<IdeAndroidProjectImpl>

    fun androidArtifactOutputFrom(output: OutputFile): IdeAndroidArtifactOutputImpl

    fun nativeVariantAbiFrom(variantAbi: NativeVariantAbi): IdeNativeVariantAbiImpl
    fun nativeAndroidProjectFrom(project: NativeAndroidProject, ndkVersion: String?): IdeNativeAndroidProjectImpl
  }

  interface V2 : ModelCache {
    /**
     * Converts V2's [BasicVariant] and [Variant] to an incomplete [IdeVariantImpl] instance which does not yet include
     * dependency information.
     */
    fun variantFrom(
      androidProject: IdeAndroidProjectImpl,
      basicVariant: BasicVariant,
      variant: com.android.builder.model.v2.ide.Variant,
      legacyAndroidGradlePluginProperties: LegacyAndroidGradlePluginProperties?
    ): ModelResult<IdeVariantCoreImpl>

    /**
     * Supplements an incomplete instance of [IdeVariantImpl] with dependency information from a [VariantDependencies] model.
     */
    fun variantFrom(
      ownerBuildId: BuildId,
      ownerProjectPath: String,
      variant: IdeVariantCoreImpl,
      variantDependencies: VariantDependenciesCompat,
      bootClasspath: Collection<String>,
      androidProjectPathResolver: AndroidProjectPathResolver,
      buildPathMap: Map<String, BuildId>
    ): ModelResult<IdeVariantWithPostProcessor>

    fun androidProjectFrom(
      rootBuildId: BuildId,
      buildId: BuildId,
      basicProject: BasicAndroidProject,
      project: com.android.builder.model.v2.models.AndroidProject,
      androidVersion: ModelVersions,
      androidDsl: AndroidDsl,
      legacyAndroidGradlePluginProperties: LegacyAndroidGradlePluginProperties?,
      gradlePropertiesModel: GradlePropertiesModel,
      defaultVariantName: String?
    ): ModelResult<IdeAndroidProjectImpl>
  }

  fun nativeModuleFrom(nativeModule: NativeModule): IdeNativeModuleImpl

  companion object {
    const val LOCAL_AARS = "__local_aars__"
    const val LOCAL_JARS = "__local_jars__"

    @JvmStatic
    fun createForTests(useV2BuilderModels: Boolean, agpVersion: AgpVersion): ModelCache {
      val internedModels = InternedModels(null)
      return if (useV2BuilderModels) {
        modelCacheV2Impl(
          internedModels,
          ModelVersions(
            agp = agpVersion,
            modelVersion = ModelVersion(Int.MAX_VALUE, Int.MAX_VALUE, "Fake model for tests"),
            minimumModelConsumer = null,
            ),
          syncTestMode = SyncTestMode.PRODUCTION,
        )
      } else {
        modelCacheV1Impl(internedModels, BuildFolderPaths())
      }
    }

    @JvmStatic
    fun createForPostBuildModels(): V1 {
      val internedModels = InternedModels(null)
      return modelCacheV1Impl(internedModels, BuildFolderPaths())
    }
  }
}

data class ModuleId(val gradlePath: String, val buildId: String)

fun BasicGradleProject.toModuleId() = ModuleId(path, projectIdentifier.buildIdentifier.rootDir.path)
/**
 * A [model] wrapper that knows how to post-process it once all models are fetched. [postProcess] method is expected to be invoked when the
 * cache is populated with all models and the returned value is supposed to be used instead the original [model].
 *
 * These wrappers are needed when data for an IDE model are scattered across multiple source models and might not yet be available at the
 * time when [model] is instantiated.
 */
class IdeModelWithPostProcessor<T>(
  val model: T,
  private val postProcessor: () -> T = { model }
) {
  fun postProcess(): T = postProcessor()
}

typealias IdeVariantWithPostProcessor = IdeModelWithPostProcessor<IdeVariantCoreImpl>

val IdeVariantWithPostProcessor.variant: IdeVariantCoreImpl get() = model
val IdeVariantWithPostProcessor.name: String get() = variant.name
val IdeVariantWithPostProcessor.mainArtifact: IdeAndroidArtifactCoreImpl get() = variant.mainArtifact
val IdeVariantWithPostProcessor.hostTestArtifacts: List<IdeJavaArtifactCoreImpl> get() = variant.hostTestArtifacts
val IdeVariantWithPostProcessor.deviceTestArtifacts: List<IdeAndroidArtifactCoreImpl> get() = variant.deviceTestArtifacts
val IdeVariantWithPostProcessor.testFixturesArtifact: IdeAndroidArtifactCoreImpl? get() = variant.testFixturesArtifact

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

fun convertArtifactName(name: String): IdeArtifactName = when (name) {
  ARTIFACT_NAME_MAIN -> IdeArtifactName.MAIN
  ARTIFACT_NAME_ANDROID_TEST -> IdeArtifactName.ANDROID_TEST
  ARTIFACT_NAME_UNIT_TEST -> IdeArtifactName.UNIT_TEST
  ARTIFACT_NAME_TEST_FIXTURES -> IdeArtifactName.TEST_FIXTURES
  ARTIFACT_NAME_SCREENSHOT_TEST -> IdeArtifactName.SCREENSHOT_TEST
  else -> error("Invalid android artifact name: $name")
}

/**
 * Converts the artifact address into a name that will be used by the IDE to represent the library.
 */
internal fun convertToLibraryName(libraryArtifactAddress: String, projectBasePath: File?): String {
  when {
    // TODO(xof): there are other magic prefixes like "__local_asars__", "__wrapped_aars__" (post AGP-7.3) and "artifacts" (pre AGP-7.3)
    //  that might be constructed from LibraryService
    libraryArtifactAddress.startsWith("$LOCAL_AARS:") -> libraryArtifactAddress.removePrefix("$LOCAL_AARS:")
    libraryArtifactAddress.startsWith("$LOCAL_JARS:") -> libraryArtifactAddress.removePrefix("$LOCAL_JARS:")
    else -> null
  }?.let { addressWithoutPrefix ->
    return adjustLocalLibraryName(
      File(addressWithoutPrefix.substringBefore(":")),
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
private fun adjustLocalLibraryName(artifactFile: File, projectBasePath: File?): @SystemIndependent String {
  val maybeRelative =
    if (projectBasePath != null && artifactFile.startsWith(projectBasePath)) artifactFile.relativeToOrSelf(projectBasePath) else artifactFile
  if (!FileUtil.filesEqual(maybeRelative, artifactFile)) {
    return FileUtil.toSystemIndependentName(File(".${File.separator}${maybeRelative}").path)
  }

  return FileUtil.toSystemIndependentName(artifactFile.path)
}
