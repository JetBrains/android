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

import java.io.Serializable

sealed class SyncActionOptions(val flags: GradleSyncStudioFlags, val syncTestMode: SyncTestMode) : Serializable

/**
 * A sync action fetching enough models to set up a project.
 */
sealed class SyncProjectActionOptions(flags: GradleSyncStudioFlags, syncTestMode: SyncTestMode) :
  SyncActionOptions(flags, syncTestMode), Serializable {
  abstract val additionalClassifierArtifactsAction: AdditionalClassifierArtifactsActionOptions
}

class AllVariantsSyncActionOptions(
  flags: GradleSyncStudioFlags,
  syncTestMode: SyncTestMode,
  override val additionalClassifierArtifactsAction: AdditionalClassifierArtifactsActionOptions
) : SyncProjectActionOptions(flags, syncTestMode), Serializable

data class SwitchVariantRequest(
  val moduleId: String,
  val variantName: String?,
  val abi: String?
): Serializable

class SingleVariantSyncActionOptions(
  flags: GradleSyncStudioFlags,
  syncTestMode: SyncTestMode,
  val selectedVariants: SelectedVariants,
  val switchVariantRequest: SwitchVariantRequest?,
  override val additionalClassifierArtifactsAction: AdditionalClassifierArtifactsActionOptions,
) : SyncProjectActionOptions(flags, syncTestMode), Serializable

class NativeVariantsSyncActionOptions(
  flags: GradleSyncStudioFlags,
  syncTestMode: SyncTestMode,
  /** moduleId => variantName where moduleId is by [com.android.tools.idea.gradle.project.sync.Modules.createUniqueModuleId] */
  val moduleVariants: Map<String, String>,
  val requestedAbis: Set<String>
) : SyncActionOptions(flags, syncTestMode), Serializable

class AdditionalClassifierArtifactsActionOptions(
  val cachedLibraries: Collection<String>,
  val downloadAndroidxUISamplesSources: Boolean
) : Serializable

data class GradleSyncStudioFlags(
  val studioFlagParallelSyncEnabled: Boolean,
  val studioFlagParallelSyncPrefetchVariantsEnabled: Boolean,
  val studioFlagUseV2BuilderModels: Boolean,
  val studioFlagDisableForcedUpgrades: Boolean,
  val studioFlagOutputSyncStats: Boolean,
  val studioHprofOutputDirectory: String,
) : Serializable

