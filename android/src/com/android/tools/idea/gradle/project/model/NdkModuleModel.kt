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
import java.io.File
import java.util.ArrayList
import java.util.Collections
import java.util.HashMap
import java.util.Objects


class NdkModuleModel(
  moduleName: String,
  rootDirPath: File,
  androidProject: IdeNativeAndroidProject,
  variantAbi: List<IdeNativeVariantAbi>) : ModuleModel {

  // State of this model
  private val moduleName = moduleName
  val rootDirPath = rootDirPath
  val androidProject = androidProject
  val variantAbi = variantAbi

  // Computed or mutable data below here
  @Transient val features: NdkModelFeatures
  @Transient private var modelVersion: GradleVersion? = null

  // Map of all variants, key: debug-x86, value: NdkVariantName(debug, x86).
  @Transient private val variantNamesByVariantAndAbiName: MutableMap<String, NdkVariantName> = HashMap()

  // Map of synced variants. For full-variants sync, contains all variants form myVariantNames.
  @Transient private val variantsByName: MutableMap<String, NdkVariant> = HashMap()
  @Transient private val toolchainsByName: MutableMap<String, NativeToolchain> = HashMap()
  @Transient private val settingsByName: MutableMap<String, NativeSettings> = HashMap()
  @Transient private var selectedVariantName: String? = null

  private fun populateModuleFields() {
    if (variantAbi.isEmpty()) {
      // Full-variants sync.
      populateForFullVariantsSync()
    }
    else {
      // Single-variant sync.
      populateForSingleVariantSync()
    }
    if (variantsByName.isEmpty()) {
      // There will mostly be at least one variant, but create a dummy variant when there are none.
      variantsByName[DummyNdkVariant.variantNameWithAbi] = NdkVariant(DummyNdkVariant.variantNameWithAbi,
                                                                      features.isExportedHeadersSupported)
      variantNamesByVariantAndAbiName[DummyNdkVariant.variantNameWithAbi] = NdkVariantName(DummyNdkVariant.variantNameWithoutAbi,
                                                                                           DummyNdkVariant.abiName)
    }
  }

  // Call this method for full variants sync.
  private fun populateForFullVariantsSync() {
    for (artifact in androidProject.artifacts) {
      val variantName = if (features.isGroupNameSupported) artifact.groupName else artifact.name
      val ndkVariantName = NdkVariantName(variantName, artifact.abi)
      var variant = variantsByName[ndkVariantName.displayName]
      if (variant == null) {
        variant = NdkVariant(ndkVariantName.displayName, features.isExportedHeadersSupported)
        variantsByName[ndkVariantName.displayName] = variant
        variantNamesByVariantAndAbiName[ndkVariantName.displayName] = ndkVariantName
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
        val ndkVariantName = NdkVariantName(key, abi)
        variantNamesByVariantAndAbiName[ndkVariantName.displayName] = ndkVariantName
      }
    }
    for (variantAbi in variantAbi) {
      populateForNativeVariantAbi(variantAbi)
    }
  }

  private fun populateForNativeVariantAbi(variantAbi: IdeNativeVariantAbi) {
    val variantName = getNdkVariantName(variantAbi.variantName, variantAbi.abi)
    val variant = NdkVariant(variantName, features.isExportedHeadersSupported)
    for (artifact in variantAbi.artifacts) {
      variant.addArtifact(artifact)
    }
    variantsByName[variantName] = variant

    // populate toolchains
    populateToolchains(variantAbi.toolChains)

    // populate settings
    populateSettings(variantAbi.settings)
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

  private fun initializeSelectedVariant() {
    val variantNames: Set<String> = variantsByName.keys
    assert(variantNames.isNotEmpty())
    if (variantNames.size == 1) {
      selectedVariantName = Iterables.getOnlyElement(variantNames)
      return
    }
    for (variantName in variantNames) {
      if (variantName == "debug" || variantName == getNdkVariantName("debug", "x86")) {
        selectedVariantName = variantName
        return
      }
    }
    val sortedVariantNames = ArrayList(variantNames)
    Collections.sort(sortedVariantNames)
    assert(!sortedVariantNames.isEmpty())
    selectedVariantName = sortedVariantNames[0]
  }

  override fun getModuleName() = moduleName

  /**
   * Returns a list of all NdkVariant names. For single-variant sync, some variant names may not synced.
   */
  val ndkVariantNames : Set<String> get() = variantNamesByVariantAndAbiName.keys

  /**
   * Returns the artifact name of a given ndkVariantName, which will be used as variant name for non-native models.
   *
   * @param ndkVariantName the display name of ndk variant. For example: debug-x86.
   */
  fun getVariantName(ndkVariantName: String): String {
    val result = variantNamesByVariantAndAbiName[ndkVariantName]
                 ?: throw RuntimeException(String.format(
                   "Variant named '%s' but only variants named '%s' were found.",
                   ndkVariantName,
                   Joiner.on(",").join(variantNamesByVariantAndAbiName.keys)))
    return result.variant
  }

  /**
   * Returns the abi name of a given ndkVariantName.
   *
   * @param ndkVariantName the display name of ndk variant. For example: debug-x86.
   */
  fun getAbiName(ndkVariantName: String) = variantNamesByVariantAndAbiName[ndkVariantName]!!.abi


  val variants: Collection<NdkVariant> get() = variantsByName.values

  val selectedVariant get() = variantsByName[selectedVariantName]!!


  /**
   * @return true if the variant model with given name has been requested before.
   */
  fun variantExists(variantName: String) = variantsByName.containsKey(variantName)

  fun setSelectedVariantName(name: String) {
    // Select from synced variants.
    val variantNames: Collection<String> = variantsByName.keys
    if (variantNames.contains(name)) {
      selectedVariantName = name
    }
    else {
      initializeSelectedVariant()
    }
  }

  fun findToolchain(toolchainName: String) = toolchainsByName[toolchainName]

  fun findSettings(settingsName: String) = settingsByName[settingsName]

  override fun hashCode(): Int {
    // Hashcode should consist of what's written in writeObject.
    // Everything else is derived from these so those don't matter wrt to identity.
    return Objects.hash(
      moduleName,
      rootDirPath,
      androidProject,
      variantAbi)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null) return false
    if (other !is NdkModuleModel) return false
    if (moduleName != other.moduleName) return false
    if (rootDirPath.path != other.rootDirPath.path) return false
    if (androidProject != other.androidProject) return false
    return variantAbi == other.variantAbi
  }

  // If there are no real NDK variants (e.g., there are no artifacts to deduce variants from), a dummy variant will
  // be created.
  object DummyNdkVariant {
    const val variantNameWithoutAbi = "---"
    const val abiName = "--"
    const val variantNameWithAbi = "$variantNameWithoutAbi-$abiName"
  }

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
    fun getNdkVariantName(variant: String, abi: String): String {
      return "$variant-$abi"
    }
  }

  init {
    modelVersion = GradleVersion.tryParse(this.androidProject.modelVersion)
    features = NdkModelFeatures(modelVersion)
    populateModuleFields()
    initializeSelectedVariant()
  }
}