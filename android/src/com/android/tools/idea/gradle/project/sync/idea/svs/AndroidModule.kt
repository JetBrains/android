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

import com.android.ide.common.gradle.model.IdeAndroidProject
import com.android.ide.common.gradle.model.IdeVariant
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
import org.jetbrains.kotlin.gradle.KotlinGradleModel
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider

@UsedInBuildAction
abstract class GradleModule(val gradleProject: BasicGradleProject) {
  abstract fun deliverModels(consumer: ProjectImportModelProvider.BuildModelConsumer)
  val findModelRoot: Model get() = gradleProject
  val id = createUniqueModuleId(gradleProject)

  var projectSyncIssues: List<SyncIssueData>? = null; private set
  fun setSyncIssues(issues: List<SyncIssueData>) {
    projectSyncIssues = issues
  }

  protected inner class ModelConsumer(val buildModelConsumer: ProjectImportModelProvider.BuildModelConsumer) {
    inline fun <reified T : Any> T.deliver() {
      buildModelConsumer.consumeProjectModel(gradleProject, this, T::class.java)
    }
  }
}

/**
 * The container class for Java module, containing its Android models handled by the Android plugin.
 */
@UsedInBuildAction
class JavaModule(
  gradleProject: BasicGradleProject,
  private val kotlinGradleModel: KotlinGradleModel?
) : GradleModule(gradleProject) {
  override fun deliverModels(consumer: ProjectImportModelProvider.BuildModelConsumer) {
    with(ModelConsumer(consumer)) {
      kotlinGradleModel?.deliver()
    }
  }
}

/**
 * The container class for Android module, containing its Android model, Variant models, and dependency modules.
 */
@UsedInBuildAction
class AndroidModule internal constructor(
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
  private val nativeModule: IdeNativeModule?
) : GradleModule(gradleProject) {
  val projectType: Int get() = androidProject.projectType

  /** Names of all currently fetch variants (currently pre single-variant-sync only). */
  val fetchedVariantNames: Collection<String> = prefetchedVariants?.map { it.name }?.toSet().orEmpty()

  fun getVariantAbiNames(variantName: String): Collection<String>? {
    fun unsafeGet() = nativeModule?.variants?.firstOrNull { it.name == variantName }?.abis?.map { it.name }
                      ?: nativeAndroidProject?.variantInfos?.get(variantName)?.abiNames
    return safeGet(::unsafeGet, null)
  }


  enum class NativeModelVersion { None, V1, V2 }

  val nativeModelVersion: NativeModelVersion = when {
    nativeModule != null -> NativeModelVersion.V2
    nativeAndroidProject != null -> NativeModelVersion.V1
    else -> NativeModelVersion.None
  }

  var syncedVariant: IdeVariant? = null
  var syncedNativeVariant: IdeNativeVariantAbi? = null

  var additionalClassifierArtifacts: AdditionalClassifierArtifactsModel? = null
  var kotlinGradleModel: KotlinGradleModel? = null

  /** Returns the list of all libraries this currently selected variant depends on (and temporarily maybe some of the
   * libraries other variants depend on.
   **/
  fun getLibraryDependencies(): Collection<ArtifactIdentifier> {
    // Get variants from AndroidProject if it's not empty, otherwise get from VariantGroup.
    // The first case indicates full-variants sync and the later single-variant sync.
    val variants = prefetchedVariants ?: listOfNotNull(syncedVariant)
    return collectIdentifiers(variants)
  }

  override fun deliverModels(consumer: ProjectImportModelProvider.BuildModelConsumer) {
    // For now, use one model cache per module. It is does deliver the smallest memory footprint, but this is what we get after
    // models are deserialized from the DataNode cache anyway. This will be replaced with a model cache per sync when shared libraries
    // are moved out of `IdeAndroidProject` and delivered to the IDE separately.
    val selectedVariantName =
      syncedVariant?.name
      ?: prefetchedVariants?.map { it.name }?.getDefaultOrFirstItem("debug")
      ?: throw AndroidSyncException("No variants found for '${gradleProject.path}'. Check build files to ensure at least one variant exists.")

    val ideAndroidModels = IdeAndroidModels(
      androidProject,
      syncedVariant?.let { listOf(it) } ?: prefetchedVariants.orEmpty(),
      selectedVariantName,
      projectSyncIssues.orEmpty(),
      nativeModule,
      nativeAndroidProject,
      syncedNativeVariant
    )
    with(ModelConsumer(consumer)) {
      ideAndroidModels.deliver()
      kotlinGradleModel?.deliver()
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

