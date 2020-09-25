/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.idea.svs

import com.android.builder.model.AndroidProject
import com.android.builder.model.NativeAndroidProject
import com.android.builder.model.NativeVariantAbi
import com.android.builder.model.SyncIssue
import com.android.builder.model.Variant
import com.android.builder.model.v2.models.ndk.NativeModule
import com.android.ide.common.gradle.model.IdeAndroidProject
import com.android.ide.common.gradle.model.IdeVariant
import com.android.ide.common.gradle.model.impl.ModelCache
import com.android.ide.common.gradle.model.impl.ModelCache.Companion.safeGet
import com.android.ide.common.gradle.model.ndk.v1.IdeNativeAndroidProject
import com.android.ide.common.gradle.model.ndk.v1.IdeNativeVariantAbi
import com.android.ide.common.gradle.model.ndk.v2.IdeNativeModule
import com.android.ide.common.repository.GradleCoordinate
import com.android.ide.common.repository.GradleVersion
import com.android.ide.gradle.model.ArtifactIdentifier
import com.android.ide.gradle.model.ArtifactIdentifierImpl
import com.android.ide.gradle.model.artifacts.AdditionalClassifierArtifactsModel
import com.android.tools.idea.gradle.project.sync.Modules.createUniqueModuleId
import com.android.tools.idea.gradle.project.sync.idea.UsedInBuildAction
import com.android.tools.idea.gradle.project.sync.idea.issues.AndroidSyncException
import com.android.tools.idea.gradle.project.sync.issues.SyncIssueData
import org.gradle.tooling.model.Model
import org.gradle.tooling.model.gradle.BasicGradleProject
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider

@UsedInBuildAction
abstract class GradleModule(val gradleProject: BasicGradleProject) {
  abstract fun deliverModels(consumer: ProjectImportModelProvider.BuildModelConsumer)

  var projectSyncIssues: List<SyncIssueData>? = null; private set
  fun setSyncIssues(issues: Collection<SyncIssue>) {
    projectSyncIssues = issues.map { syncIssue ->
      SyncIssueData(
        message = syncIssue.message,
        data = syncIssue.data,
        multiLineMessage = safeGet(syncIssue::multiLineMessage, null)?.toList(),
        severity = syncIssue.severity,
        type = syncIssue.type
      )
    }
  }

  protected inner class ModelConsumer(val buildModelConsumer: ProjectImportModelProvider.BuildModelConsumer) {
    inline fun <reified T : Any> T.deliver() {
      buildModelConsumer.consumeProjectModel(gradleProject, this, T::class.java)
    }
  }
}

/**
 * The container class for Android module, containing its Android model, Variant models, and dependency modules.
 */
@UsedInBuildAction
class AndroidModule private constructor(
  val modelVersion: GradleVersion?,
  gradleProject: BasicGradleProject,
  val androidProject: IdeAndroidProject,
  /** All configured variant names if supported by the AGP version. */
  val allVariantNames: Set<String>?,
  val defaultVariantName: String?,
  private val prefetchedVariants: List<IdeVariant>?,
  /** Old V1 model. It's only set if [nativeModule] is not set. */
  private val nativeAndroidProject: IdeNativeAndroidProject?,
  /** New V2 model. It's only set if [nativeAndroidProject] is not set. */
  private val nativeModule: IdeNativeModule?,
  private val modelCache: ModelCache
) : GradleModule(gradleProject) {
  companion object {
    @JvmStatic
    fun create(
      gradleProject: BasicGradleProject,
      androidProject: AndroidProject,
      nativeAndroidProject: NativeAndroidProject?,
      nativeModule: NativeModule?,
      modelCache: ModelCache
    ): AndroidModule {
      val modelVersionString = safeGet(androidProject::getModelVersion, "")
      val modelVersion: GradleVersion? = GradleVersion.tryParseAndroidGradlePluginVersion(modelVersionString)

      val ideAndroidProject = modelCache.androidProjectFrom(androidProject)
      val idePrefetchedVariants =
        safeGet(androidProject::getVariants, emptyList())
          .map { modelCache.variantFrom(it, modelVersion) }
          .takeUnless { it.isEmpty() }

      // Single-variant-sync models have variantNames property and pre-single-variant sync model should have all variants present instead.
      val allVariantNames: Set<String>? = (safeGet(androidProject::getVariantNames, null)
                                           ?: idePrefetchedVariants?.map { it.name })?.toSet()

      val defaultVariantName: String? = safeGet(androidProject::getDefaultVariant, null) ?: allVariantNames?.getDefaultOrFirstItem("debug")

      val ideNativeAndroidProject = nativeAndroidProject?.let(modelCache::nativeAndroidProjectFrom)
      val ideNativeModule = nativeModule?.let(modelCache::nativeModuleFrom)

      val androidModule = AndroidModule(
        modelVersion,
        gradleProject,
        ideAndroidProject,
        allVariantNames,
        defaultVariantName,
        idePrefetchedVariants,
        ideNativeAndroidProject,
        ideNativeModule,
        modelCache
      )

      safeGet(androidProject::getSyncIssues, null)?.let {
        // It will be overridden if we receive something here but also a proper sync issues model later.
        syncIssues ->
        androidModule.setSyncIssues(syncIssues)
      }

      return androidModule
    }
  }

  val findModelRoot: Model get() = gradleProject
  val projectType: Int get() = androidProject.projectType

  /** Names of all currently fetch variants (currently pre single-variant-sync only). */
  val fetchedVariantNames: Collection<String> = prefetchedVariants?.map { it.name }?.toSet().orEmpty()

  fun getVariantAbiNames(variantName: String): Collection<String>? {
    fun unsafeGet() = nativeModule?.variants?.firstOrNull { it.name == variantName }?.abis?.map { it.name }
                      ?: nativeAndroidProject?.variantInfos?.get(variantName)?.abiNames
    return safeGet(::unsafeGet, null)
  }

  val id = createUniqueModuleId(gradleProject)

  enum class NativeModelVersion { None, V1, V2 }

  val nativeModelVersion: NativeModelVersion = when {
    nativeModule != null -> NativeModelVersion.V2
    nativeAndroidProject != null -> NativeModelVersion.V1
    else -> NativeModelVersion.None
  }

  private val additionallySyncedVariants: MutableList<IdeVariant> = mutableListOf()
  private val additionallySyncedNativeVariants: MutableList<IdeNativeVariantAbi> = mutableListOf()

  fun addVariant(variant: Variant): IdeVariant {
    val ideVariant = modelCache.variantFrom(variant, modelVersion)
    additionallySyncedVariants.add(ideVariant)
    return ideVariant
  }

  fun addNativeVariant(variant: NativeVariantAbi): IdeNativeVariantAbi {
    val ideNativeVariantAbi = modelCache.nativeVariantAbiFrom(variant)
    additionallySyncedNativeVariants.add(ideNativeVariantAbi)
    return ideNativeVariantAbi
  }

  var additionalClassifierArtifacts: AdditionalClassifierArtifactsModel? = null

  /** Returns the list of all libraries this currently selected variant depends on (and temporarily maybe some of the
   * libraries other variants depend on.
   **/
  fun getLibraryDependencies(): Collection<ArtifactIdentifier> {
    // Get variants from AndroidProject if it's not empty, otherwise get from VariantGroup.
    // The first case indicates full-variants sync and the later single-variant sync.
    val variants = prefetchedVariants ?: additionallySyncedVariants
    return collectIdentifiers(variants)
  }

  override fun deliverModels(consumer: ProjectImportModelProvider.BuildModelConsumer) {
    // For now, use one model cache per module. It is does deliver the smallest memory footprint, but this is what we get after
    // models are deserialized from the DataNode cache anyway. This will be replaced with a model cache per sync when shared libraries
    // are moved out of `IdeAndroidProject` and delivered to the IDE separately.
    val selectedVariantName =
      additionallySyncedVariants.firstOrNull()?.name
      ?: prefetchedVariants?.map { it.name }?.getDefaultOrFirstItem("debug")
      ?: throw AndroidSyncException("No variants found for '${gradleProject.path}'. Check build files to ensure at least one variant exists.")

    val ideAndroidModels = IdeAndroidModels(
      androidProject,
      additionallySyncedVariants.takeUnless { it.isEmpty() } ?: prefetchedVariants.orEmpty(),
      selectedVariantName,
      projectSyncIssues.orEmpty(),
      nativeModule,
      nativeAndroidProject,
      additionallySyncedNativeVariants
    )
    with(ModelConsumer(consumer)) {
      ideAndroidModels.deliver()
      additionalClassifierArtifacts?.deliver()
    }
  }
}

data class ModuleConfiguration(val id: String, val variant: String, val abi: String?)

@UsedInBuildAction
class NativeVariantsAndroidModule private constructor(
  gradleProject: BasicGradleProject,
  private val nativeVariants: List<IdeNativeVariantAbi>? // Null means V2.
) : GradleModule(gradleProject) {
  companion object {
    fun createV2(gradleProject: BasicGradleProject): NativeVariantsAndroidModule = NativeVariantsAndroidModule(gradleProject, null)
    fun createV1(gradleProject: BasicGradleProject, nativeVariants: List<IdeNativeVariantAbi>): NativeVariantsAndroidModule =
      NativeVariantsAndroidModule(gradleProject, nativeVariants)
  }

  override fun deliverModels(consumer: ProjectImportModelProvider.BuildModelConsumer) {
    with(ModelConsumer(consumer)) {
      IdeAndroidNativeVariantsModels(nativeVariants, projectSyncIssues.orEmpty()).deliver()
    }
  }
}

@UsedInBuildAction
fun Collection<String>.getDefaultOrFirstItem(defaultValue: String): String? =
  if (contains(defaultValue)) defaultValue else minBy { it }

@UsedInBuildAction
private fun collectIdentifiers(variants: Collection<IdeVariant>): List<ArtifactIdentifier> {
  return variants.asSequence()
    .flatMap { sequenceOf(it.mainArtifact, it.androidTestArtifact, it.unitTestArtifact).filterNotNull() }
    .flatMap { it.level2Dependencies.androidLibraries.asSequence() + it.level2Dependencies.javaLibraries.asSequence() }
    .mapNotNull { GradleCoordinate.parseCoordinateString(it.artifactAddress) }
    .map { ArtifactIdentifierImpl(it.groupId, it.artifactId, it.version?.toString().orEmpty()) }
    .distinct()
    .toList()
}

