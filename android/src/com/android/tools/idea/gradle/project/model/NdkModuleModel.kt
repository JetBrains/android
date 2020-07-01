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
import com.google.common.base.Joiner
import com.google.common.collect.Iterables
import com.intellij.openapi.module.Module
import com.intellij.serialization.PropertyMapping
import java.io.File
import java.util.ArrayList
import java.util.Collections
import java.util.HashMap

/**
 * Used to track serialization version. This value should be updated when the serialized form of [NdkModuleModel] is changed in a backward-
 * incompatible way. Note that adding or deleting field is generally safe. Previous nonexistent fields will just be populated with null
 * value.
 */
private const val NDK_MODULE_MODEL_SYNC_VERSION = "2020-06-30/2"

class NdkModuleModel
@PropertyMapping("moduleName", "rootDirPath", "androidProject", "variantAbi", "syncVersion") private constructor(
  private val moduleName: String,
  val rootDirPath: File,
  val androidProject: IdeNativeAndroidProject,
  val variantAbi: List<IdeNativeVariantAbi>,
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

  /** Index of [VariantAbi] by the display name. */
  @Transient
  private val variantAbiByDisplayName: MutableMap<String, VariantAbi> = HashMap()

  /** Map of synced variants. For full-variants sync, contains all variant and ABIs from [variantAbiByDisplayName]. */
  @Transient
  private val ndkVariantsByVariantAbi: MutableMap<String, NdkVariant> = HashMap()
  @Transient
  private val toolchainsByName: MutableMap<String, NativeToolchain> = HashMap()
  @Transient
  private val settingsByName: MutableMap<String, NativeSettings> = HashMap()
  @Transient
  private var selectedVariantAbiName: String? = null

  private fun populateModuleFields() {
    if (variantAbi.isEmpty()) {
      // Full-variants sync.
      populateForFullVariantsSync()
    }
    else {
      // Single-variant sync.
      populateForSingleVariantSync()
    }
    if (ndkVariantsByVariantAbi.isEmpty()) {
      // There will mostly be at least one variant, but create a dummy variant when there are none.
      ndkVariantsByVariantAbi[DUMMY_VARIANT_ABI.displayName] = NdkVariant(DUMMY_VARIANT_ABI.displayName, features.isExportedHeadersSupported)
      variantAbiByDisplayName[DUMMY_VARIANT_ABI.displayName] = DUMMY_VARIANT_ABI
    }
  }

  // Call this method for full variants sync.
  private fun populateForFullVariantsSync() {
    for (artifact in androidProject.artifacts) {
      val variantName = if (features.isGroupNameSupported) artifact.groupName else artifact.name
      val variantAbi = VariantAbi(variantName, artifact.abi)
      var variant = ndkVariantsByVariantAbi[variantAbi.displayName]
      if (variant == null) {
        variant = NdkVariant(variantAbi.displayName, features.isExportedHeadersSupported)
        ndkVariantsByVariantAbi[variantAbi.displayName] = variant
        variantAbiByDisplayName[variantAbi.displayName] = variantAbi
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
    for ((key, value) in androidProject.variantInfos) {
      for (abi in value.abiNames) {
        val variantAbi = VariantAbi(key, abi)
        variantAbiByDisplayName[variantAbi.displayName] = variantAbi
      }
    }
    for (variantAbi in variantAbi) {
      populateForNativeVariantAbi(variantAbi)
    }
  }

  private fun populateForNativeVariantAbi(nativeVariantAbi: IdeNativeVariantAbi) {
    val variantAbi = getNdkVariantAbiString(nativeVariantAbi.variantName, nativeVariantAbi.abi)
    val variant = NdkVariant(variantAbi, features.isExportedHeadersSupported)
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

  private fun initializeSelectedVariantAbi() {
    val variantAbis: Set<String> = ndkVariantsByVariantAbi.keys
    assert(variantAbis.isNotEmpty())
    if (variantAbis.size == 1) {
      selectedVariantAbiName = Iterables.getOnlyElement(variantAbis)
      return
    }
    for (variantAbi in variantAbis) {
      if (variantAbi == getNdkVariantAbiString("debug", "x86")) {
        selectedVariantAbiName = variantAbi
        return
      }
    }
    val sortedVariantAbis = ArrayList(variantAbis)
    Collections.sort(sortedVariantAbis)
    assert(!sortedVariantAbis.isEmpty())
    selectedVariantAbiName = sortedVariantAbis[0]
  }

  override fun getModuleName() = moduleName

  /**
   * Returns a list of all NdkVariant names. For single-variant sync, some variant names may not synced.
   */
  val ndkVariantAbis: Set<String> get() = variantAbiByDisplayName.keys

  /**
   * Returns the artifact name of a given ndkVariantName, which will be used as variant name for non-native models.
   *
   * @param variantAbi the display name of ndk variant. For example: debug-x86.
   */
  fun getVariantName(variantAbi: String): String {
    val result = variantAbiByDisplayName[variantAbi]
                 ?: throw RuntimeException(String.format(
                   "Variant named '%s' but only variants named '%s' were found.",
                   variantAbi,
                   Joiner.on(",").join(variantAbiByDisplayName.keys)))
    return result.variant
  }

  /**
   * Returns the abi name of a given ndkVariantName.
   *
   * @param variantAbi the display name of ndk variant. For example: debug-x86.
   */
  fun getAbiName(variantAbi: String) = variantAbiByDisplayName[variantAbi]!!.abi


  val variants: Collection<NdkVariant> get() = ndkVariantsByVariantAbi.values

  val selectedVariant: NdkVariant get() = ndkVariantsByVariantAbi[selectedVariantAbiName]!!

  init {
    populateModuleFields()
    initializeSelectedVariantAbi()
  }

  /**
   * @return true if the given variant-abi has been requested before.
   */
  fun variantAbiExists(variantAbi: String) = ndkVariantsByVariantAbi.containsKey(variantAbi)

  fun setSelectedVariantAbi(variantAbi: String) {
    // Select from synced variants.
    val variantAbis: Collection<String> = ndkVariantsByVariantAbi.keys
    if (variantAbis.contains(variantAbi)) {
      selectedVariantAbiName = variantAbi
    }
    else {
      initializeSelectedVariantAbi()
    }
  }

  fun findToolchain(toolchainName: String) = toolchainsByName[toolchainName]

  fun findSettings(settingsName: String) = settingsByName[settingsName]

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

    @JvmStatic
    fun getNdkVariantAbiString(variant: String, abi: String): String {
      return "$variant-$abi"
    }

    @JvmField
    val DUMMY_VARIANT_ABI = VariantAbi("---", "--")
  }

  data class VariantAbi(var variant: String, var abi: String) {
    @Transient
    val displayName: String = "$variant-$abi"
  }
}
