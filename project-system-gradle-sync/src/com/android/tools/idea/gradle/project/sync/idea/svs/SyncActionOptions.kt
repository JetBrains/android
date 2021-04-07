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
package com.android.tools.idea.gradle.project.sync.idea.svs

import java.io.Serializable

sealed class SyncActionOptions(val flags: GradleSyncStudioFlags) : Serializable

/**
 * A sync action fetching enough models to set up a project.
 */
sealed class SyncProjectActionOptions(flags: GradleSyncStudioFlags) : SyncActionOptions(flags), Serializable {
  abstract val additionalClassifierArtifactsAction: AdditionalClassifierArtifactsActionOptions
}

class FullSyncActionOptions(
  flags: GradleSyncStudioFlags,
  override val additionalClassifierArtifactsAction: AdditionalClassifierArtifactsActionOptions
) : SyncProjectActionOptions(flags), Serializable

class SingleVariantSyncActionOptions(
  flags: GradleSyncStudioFlags,
  val selectedVariants: SelectedVariants,
  val moduleIdWithVariantSwitched: String?,
  override val additionalClassifierArtifactsAction: AdditionalClassifierArtifactsActionOptions
) : SyncProjectActionOptions(flags), Serializable

class NativeVariantsSyncActionOptions(
  flags: GradleSyncStudioFlags,
  /** moduleId => variantName where moduleId is by [com.android.tools.idea.gradle.project.sync.Modules.createUniqueModuleId] */
  val moduleVariants: Map<String, String>,
  val requestedAbis: Set<String>
) : SyncActionOptions(flags), Serializable

class AdditionalClassifierArtifactsActionOptions(
  val cachedLibraries: Collection<String>,
  val downloadAndroidxUISamplesSources: Boolean
) : Serializable

data class GradleSyncStudioFlags(
  val studioFlagParallelSyncEnabled: Boolean,
  val studioFlagParallelSyncPrefetchVariantsEnabled: Boolean,
) : Serializable

