/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.model

import com.android.tools.idea.gradle.model.ndk.v1.IdeNativeAndroidProject
import com.android.tools.idea.gradle.model.ndk.v2.IdeNativeModule
import com.android.tools.idea.gradle.model.ndk.v1.IdeNativeVariantAbi
import com.android.tools.idea.gradle.model.ndk.v2.IdeNativeAbi
import com.android.tools.idea.gradle.model.ndk.v1.IdeNativeSettings
import com.android.tools.idea.gradle.model.ndk.v1.IdeNativeToolchain
import com.android.tools.idea.gradle.model.ndk.v2.NativeBuildSystem
import com.android.ide.common.repository.AgpVersion
import com.intellij.serialization.PropertyMapping
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.HashMap
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set

interface INdkModel {
  val features: NdkModelFeatures
  val allVariantAbis: Collection<VariantAbi>
  val syncedVariantAbis: Collection<VariantAbi>
  val symbolFolders: Map<VariantAbi, Set<File>>
  val buildFiles: Collection<File>
  val buildSystems: Collection<String>
  val defaultNdkVersion: String
  val ndkVersion: String
  val needsAbiSyncBeforeRun: Boolean
}

sealed class NdkModel : INdkModel

/**
 * Native information fetched from Android Gradle Plugin about this module using V1 API.
 *
 * Specifically, if single variant sync is turned on (default),
 *
 * - [androidProject] contains the overview of the native information in this module, including
 *   - available variants in this module
 *   - available ABIs for each variants
 * - [nativeVariantAbis] contains detailed build information for the synced variant and ABI
 *
 * If single variant sync is turned off,
 *
 * - [androidProject] contains the overview and additionally
 *   - detailed build information of all variants and ABIs avaialbe in this module
 * - [nativeVariantAbis] is always empty
 */
data class V1NdkModel(
  val androidProject: IdeNativeAndroidProject,
  val nativeVariantAbis: List<IdeNativeVariantAbi>
) : NdkModel() {

  @Transient
  override val features: NdkModelFeatures = NdkModelFeatures(AgpVersion.tryParse(androidProject.modelVersion))

  /** Map of synced variants. For full-variants sync, contains all variant and ABIs from [allVariantAbis]. */
  @Transient
  private val ndkVariantsByVariantAbi: MutableMap<VariantAbi, NdkVariant> = HashMap()

  @Transient
  private val toolchainsByName: MutableMap<String, IdeNativeToolchain> = HashMap()

  @Transient
  private val settingsByName: MutableMap<String, IdeNativeSettings> = HashMap()

  @Transient
  override val allVariantAbis: Collection<VariantAbi> = LinkedHashSet(
    androidProject.variantInfos
      .flatMap { (variant, info) ->
        info.abiNames.map { abi -> VariantAbi(variant, abi) }
      }
      .sortedBy { it.displayName }
  )

  @Transient
  override val syncedVariantAbis: Collection<VariantAbi> = ndkVariantsByVariantAbi.keys

  init {
    if (nativeVariantAbis.isEmpty()) {
      // Full-variants sync.
      populateForFullVariantsSync()
    }
    else {
      // Single-variant sync.
      populateForSingleVariantSync()
    }
  }

  // Call this method for full variants sync.
  private fun populateForFullVariantsSync() {
    for (artifact in androidProject.artifacts) {
      val variantName = if (features.isGroupNameSupported) artifact.groupName else artifact.name
      val variantAbi = VariantAbi(variantName, artifact.abi)
      var variant = ndkVariantsByVariantAbi[variantAbi]
      if (variant == null) {
        variant = NdkVariant(variantAbi.displayName, features.isExportedHeadersSupported)
        ndkVariantsByVariantAbi[variantAbi] = variant
      }
      variant.addArtifact(artifact)
    }

    // populate toolchains
    populateToolchains(androidProject.toolChains)

    // populate settings
    populateSettings(androidProject.settings)
  }

  // Call this method for single variant sync.
  private fun populateForSingleVariantSync() {
    for (variantAbi in nativeVariantAbis) {
      populateForNativeVariantAbi(variantAbi)
    }
  }

  private fun populateForNativeVariantAbi(nativeVariantAbi: IdeNativeVariantAbi) {
    val variantAbi = VariantAbi(nativeVariantAbi.variantName, nativeVariantAbi.abi)
    val variant = NdkVariant(variantAbi.displayName, features.isExportedHeadersSupported)
    for (artifact in nativeVariantAbi.artifacts) {
      variant.addArtifact(artifact)
    }
    ndkVariantsByVariantAbi[variantAbi] = variant

    // populate toolchains
    populateToolchains(nativeVariantAbi.toolChains)

    // populate settings
    populateSettings(nativeVariantAbi.settings)
  }

  private fun populateToolchains(nativeToolchains: Collection<IdeNativeToolchain>) {
    for (toolchain in nativeToolchains) {
      toolchainsByName[toolchain.name] = toolchain
    }
  }

  private fun populateSettings(nativeSettings: Collection<IdeNativeSettings>) {
    for (settings in nativeSettings) {
      settingsByName[settings.name] = settings
    }
  }

  val variants: Collection<NdkVariant> get() = ndkVariantsByVariantAbi.values

  override val symbolFolders: Map<VariantAbi, Set<File>> get() = ndkVariantsByVariantAbi.mapValues { (_, ndkVariant) ->
    ndkVariant.artifacts.mapNotNull { artifact ->
      artifact.outputFile?.takeIf { it.exists() }?.parentFile
    }.toSet()
  }

  @Transient
  override val buildFiles: Collection<File> = androidProject.buildFiles

  @Transient
  override val buildSystems: Collection<String> = androidProject.buildSystems

  @Transient
  override val defaultNdkVersion: String = androidProject.defaultNdkVersion

  @Transient
  override val ndkVersion: String = androidProject.ndkVersion

  override val needsAbiSyncBeforeRun: Boolean get() = true

  fun getNdkVariant(variantAbi: VariantAbi?): NdkVariant? = ndkVariantsByVariantAbi[variantAbi]
  fun findToolchain(toolchainName: String): IdeNativeToolchain? = toolchainsByName[toolchainName]
  fun findSettings(settingsName: String): IdeNativeSettings? = settingsByName[settingsName]
}

/**
 * Native information fetched from Android Gradle Plugin about this module using V2 API.
 *
 * Regardless of whether single varaint sync or full sync is used, the model contains the exact same [NativeModule] that contains an
 * overview of the native module.
 *
 * synced variant and ABIs would have the [NativeAbi.compileCommandsJsonFile], [NativeAbi.symbolFolderIndexFile], and
 * [NativeAbi.buildFileIndexFile] generated, containing detailed build information.
 */
data class V2NdkModel @PropertyMapping("agpVersion", "nativeModule") constructor(
  private val /* `val` declaration needed for serialization */ agpVersion: String,
  val nativeModule: IdeNativeModule
) : NdkModel() {

  @Transient
  override val features: NdkModelFeatures = NdkModelFeatures(AgpVersion.tryParse(agpVersion))

  @Transient
  val abiByVariantAbi: Map<VariantAbi, IdeNativeAbi> = nativeModule.variants.flatMap { variant ->
    variant.abis.map { abi ->
      VariantAbi(variant.name, abi.name) to abi
    }
  }.toMap()

  @Transient
  override val allVariantAbis: Collection<VariantAbi> = LinkedHashSet(abiByVariantAbi.keys.sortedBy { it.displayName })

  override val syncedVariantAbis: Collection<VariantAbi>
    get() = LinkedHashSet(
      abiByVariantAbi.entries.filter { (_, abi) -> abi.sourceFlagsFile.exists() }
        .map { (variantAbi, _) -> variantAbi }
        .sortedBy { it.displayName }
    )

  override val symbolFolders: Map<VariantAbi, Set<File>>
    get() = abiByVariantAbi.mapValues { (_, abi) ->
      abi.symbolFolderIndexFile.readIndexFile()
    }

  override val buildFiles: Collection<File> get() = abiByVariantAbi.values.flatMap { it.buildFileIndexFile.readIndexFile() }.toSet()

  @Transient
  override val buildSystems: Collection<String> = listOf(
    when (nativeModule.nativeBuildSystem) {
      NativeBuildSystem.CMAKE -> "cmake"
      NativeBuildSystem.NDK_BUILD -> "ndkBuild"
      NativeBuildSystem.NINJA -> "ninja"
    })

  @Transient
  override val defaultNdkVersion: String = nativeModule.defaultNdkVersion

  @Transient
  override val ndkVersion: String = nativeModule.ndkVersion

  override val needsAbiSyncBeforeRun: Boolean get() = false
}

private fun File.readIndexFile(): Set<File> = when {
  this.isFile -> this.readLines(StandardCharsets.UTF_8).map { File(it) }.toSet()
  else -> emptySet()
}
