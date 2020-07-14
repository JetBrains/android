/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.builder.model.NativeSettings
import com.android.builder.model.NativeToolchain
import com.android.ide.common.gradle.model.IdeNativeAndroidProject
import com.android.ide.common.gradle.model.IdeNativeVariantAbi
import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.gradle.project.facet.ndk.NdkFacet
import com.intellij.openapi.module.Module
import com.intellij.serialization.PropertyMapping
import java.io.File
import java.util.HashMap

/**
 * Used to track serialization version. This value should be updated when the serialized form of [NdkModuleModel] is changed in a backward-
 * incompatible way. Note that adding or deleting field is generally safe. Previous nonexistent fields will just be populated with null
 * value.
 */
private const val NDK_MODULE_MODEL_SYNC_VERSION = "2020-06-30/3"

class NdkModuleModel
@PropertyMapping("moduleName", "rootDirPath", "androidProject", "nativeVariantAbis", "syncVersion") private constructor(
  private val moduleName: String,
  val rootDirPath: File,
  val androidProject: IdeNativeAndroidProject,
  val nativeVariantAbis: List<IdeNativeVariantAbi>,
  private val syncVersion: String) : ModuleModel {

  constructor(
    moduleName: String,
    rootDirPath: File,
    androidProject: IdeNativeAndroidProject,
    variantAbi: List<IdeNativeVariantAbi>
  ) : this(moduleName, rootDirPath, androidProject, variantAbi, NDK_MODULE_MODEL_SYNC_VERSION)

  init {
    // If the serialization version does not match, this aborts the deserialization process and the IDE will just function as if no value
    // was serialized in the first place.
    require(syncVersion == NDK_MODULE_MODEL_SYNC_VERSION) {
      "Attempting to deserialize a model of incompatible version ($syncVersion)"
    }
  }

  // Computed or mutable data below here
  @Transient
  val features: NdkModelFeatures = NdkModelFeatures(GradleVersion.tryParse(androidProject.modelVersion))

  /** Map of synced variants. For full-variants sync, contains all variant and ABIs from [allVariantAbis]. */
  @Transient
  private val ndkVariantsByVariantAbi: MutableMap<VariantAbi, NdkVariant> = HashMap()

  @Transient
  private val toolchainsByName: MutableMap<String, NativeToolchain> = HashMap()

  @Transient
  private val settingsByName: MutableMap<String, NativeSettings> = HashMap()

  @Transient
  val allVariantAbis: Collection<VariantAbi> = LinkedHashSet(
    androidProject.variantInfos
      .flatMap { (variant, info) ->
        info.abiNames.map { abi -> VariantAbi(variant, abi) }
      }
      .sortedBy { it.displayName }
  )

  @Transient
  val syncedVariantAbis: Collection<VariantAbi> = ndkVariantsByVariantAbi.keys

  fun getDefaultVariantAbi(): VariantAbi? =
    allVariantAbis.firstOrNull { (variant, abi) -> variant == "debug" && abi == "x86" } ?: allVariantAbis.firstOrNull()

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

  private fun populateToolchains(nativeToolchains: Collection<NativeToolchain>) {
    for (toolchain in nativeToolchains) {
      toolchainsByName[toolchain.name] = toolchain
    }
  }

  private fun populateSettings(nativeSettings: Collection<NativeSettings>) {
    for (settings in nativeSettings) {
      settingsByName[settings.name] = settings
    }
  }

  override fun getModuleName() = moduleName

  val variants: Collection<NdkVariant> get() = ndkVariantsByVariantAbi.values

  fun getSymbolFolders(): Map<VariantAbi, Set<File>> = ndkVariantsByVariantAbi.mapValues { (_, ndkVariant) ->
    ndkVariant.artifacts.mapNotNull { artifact ->
      artifact.outputFile?.takeIf { it.exists() }?.parentFile
    }.toSet()
  }

  fun getNdkVariant(variantAbi: VariantAbi?): NdkVariant? = ndkVariantsByVariantAbi[variantAbi]

  fun findToolchain(toolchainName: String) = toolchainsByName[toolchainName]

  fun findSettings(settingsName: String) = settingsByName[settingsName]

  fun getBuildFiles(): Collection<File> = androidProject.buildFiles
  fun getBuildSystems(): Collection<String> = androidProject.buildSystems
  fun getDefaultNdkVersion(): String = androidProject.defaultNdkVersion

  companion object {
    @JvmStatic
    fun get(module: Module): NdkModuleModel? {
      val facet = NdkFacet.getInstance(module)
      return facet?.let { get(it) }
    }

    @JvmStatic
    fun get(ndkFacet: NdkFacet): NdkModuleModel? {
      return ndkFacet.ndkModuleModel ?: return null
    }
  }
}
