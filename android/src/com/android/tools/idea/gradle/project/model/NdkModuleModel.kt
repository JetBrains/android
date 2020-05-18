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
import java.util.Objects

class NdkModuleModel
@PropertyMapping("myModuleName", "myRootDirPath", "myAndroidProject", "myVariantAbi")
constructor(moduleName: String,
            rootDirPath: File,
            androidProject: IdeNativeAndroidProject,
            variantAbi: List<IdeNativeVariantAbi>) : ModuleModel {

  @Transient
  val features: NdkModelFeatures

  @Transient
  private var myModelVersion: GradleVersion? = null
  private val myModuleName: String = moduleName
  private val myRootDirPath: File = rootDirPath
  private val myAndroidProject: IdeNativeAndroidProject = androidProject
  private val myVariantAbi: MutableList<IdeNativeVariantAbi> = ArrayList()

  val rootDirPath get() = myRootDirPath
  val androidProject get() = myAndroidProject

  // Map of all variants, key: debug-x86, value: NdkVariantName(debug, x86).
  private val myVariantNamesByVariantAndAbiName: MutableMap<String?, NdkVariantName> = HashMap()

  // Map of synced variants. For full-variants sync, contains all variants form myVariantNames.
  private val myVariantsByName: MutableMap<String, NdkVariant> = HashMap()
  private val myToolchainsByName: MutableMap<String, NativeToolchain> = HashMap()
  private val mySettingsByName: MutableMap<String, NativeSettings> = HashMap()
  private var mySelectedVariantName: String? = null
  private fun populateModuleFields() {
    if (myVariantAbi.isEmpty()) {
      // Full-variants sync.
      populateForFullVariantsSync()
    }
    else {
      // Single-variant sync.
      populateForSingleVariantSync()
    }
    if (myVariantsByName.isEmpty()) {
      // There will mostly be at least one variant, but create a dummy variant when there are none.
      myVariantsByName[DummyNdkVariant.variantNameWithAbi] = NdkVariant(DummyNdkVariant.variantNameWithAbi,
                                                                        features.isExportedHeadersSupported)
      myVariantNamesByVariantAndAbiName[DummyNdkVariant.variantNameWithAbi] = NdkVariantName(DummyNdkVariant.variantNameWithoutAbi,
                                                                                             DummyNdkVariant.abiName)
    }
  }

  // Call this method for full variants sync.
  private fun populateForFullVariantsSync() {
    for (artifact in myAndroidProject.artifacts) {
      val variantName = if (features.isGroupNameSupported) artifact.groupName else artifact.name
      val ndkVariantName = NdkVariantName(variantName, artifact.abi)
      var variant = myVariantsByName[ndkVariantName.displayName]
      if (variant == null) {
        variant = NdkVariant(ndkVariantName.displayName, features.isExportedHeadersSupported)
        myVariantsByName[ndkVariantName.displayName] = variant
        myVariantNamesByVariantAndAbiName[ndkVariantName.displayName] = ndkVariantName
      }
      variant.addArtifact(artifact)
    }

    // populate toolchains
    populateToolchains(myAndroidProject.toolChains)

    // populate settings
    populateSettings(myAndroidProject.settings)
  }

  // Call this method for single variant sync.
  private fun populateForSingleVariantSync() {
    for ((key, value) in myAndroidProject.variantInfos) {
      for (abi in value.abiNames) {
        val ndkVariantName = NdkVariantName(key, abi)
        myVariantNamesByVariantAndAbiName[ndkVariantName.displayName] = ndkVariantName
      }
    }
    for (variantAbi in myVariantAbi) {
      populateForNativeVariantAbi(variantAbi)
    }
  }

  private fun populateForNativeVariantAbi(variantAbi: IdeNativeVariantAbi) {
    val variantName = getNdkVariantName(variantAbi.variantName, variantAbi.abi)
    val variant = NdkVariant(variantName, features.isExportedHeadersSupported)
    for (artifact in variantAbi.artifacts) {
      variant.addArtifact(artifact)
    }
    myVariantsByName[variantName] = variant

    // populate toolchains
    populateToolchains(variantAbi.toolChains)

    // populate settings
    populateSettings(variantAbi.settings)
  }

  private fun populateToolchains(nativeToolchains: Collection<NativeToolchain>) {
    for (toolchain in nativeToolchains) {
      myToolchainsByName[toolchain.name] = toolchain
    }
  }

  private fun populateSettings(nativeSettings: Collection<NativeSettings>) {
    for (settings in nativeSettings) {
      mySettingsByName[settings.name] = settings
    }
  }

  private fun initializeSelectedVariant() {
    val variantNames: Set<String> = myVariantsByName.keys
    assert(!variantNames.isEmpty())
    if (variantNames.size == 1) {
      mySelectedVariantName = Iterables.getOnlyElement(variantNames)
      return
    }
    for (variantName in variantNames) {
      if (variantName == "debug" || variantName == getNdkVariantName("debug", "x86")) {
        mySelectedVariantName = variantName
        return
      }
    }
    val sortedVariantNames = ArrayList(variantNames)
    Collections.sort(sortedVariantNames)
    assert(!sortedVariantNames.isEmpty())
    mySelectedVariantName = sortedVariantNames[0]
  }

  /**
   * Inject the Variant-Only Sync model to existing NdkModuleModel.
   * Since the build files were not changed from last sync, only add the new VariantAbi to existing list.
   *
   * @param variantAbi The NativeVariantAbi model obtained from Variant-Only sync.
   */
  fun addVariantOnlyModuleModel(variantAbi: IdeNativeVariantAbi) {
    myVariantAbi.add(variantAbi)
    populateForNativeVariantAbi(variantAbi)
  }

  private fun parseAndSetModelVersion() {
    myModelVersion = GradleVersion.tryParse(myAndroidProject.modelVersion)
  }

  override fun getModuleName(): String {
    return myModuleName
  }

  val variantAbi: List<IdeNativeVariantAbi>
    get() = myVariantAbi

  /**
   * Returns a list of all NdkVariant names. For single-variant sync, some variant names may not synced.
   */
  val ndkVariantNames: Collection<String?>
    get() = myVariantNamesByVariantAndAbiName.keys

  /**
   * Returns the artifact name of a given ndkVariantName, which will be used as variant name for non-native models.
   *
   * @param ndkVariantName the display name of ndk variant. For example: debug-x86.
   */
  fun getVariantName(ndkVariantName: String): String {
    val result = myVariantNamesByVariantAndAbiName[ndkVariantName]
                 ?: throw RuntimeException(String.format(
                   "Variant named '%s' but only variants named '%s' were found.",
                   ndkVariantName,
                   Joiner.on(",").join(myVariantNamesByVariantAndAbiName.keys)))
    return myVariantNamesByVariantAndAbiName[ndkVariantName]!!.variant
  }

  /**
   * Returns the abi name of a given ndkVariantName.
   *
   * @param ndkVariantName the display name of ndk variant. For example: debug-x86.
   */
  fun getAbiName(ndkVariantName: String): String {
    return myVariantNamesByVariantAndAbiName[ndkVariantName]!!.abi
  }

  val variants: Collection<NdkVariant>
    get() = myVariantsByName.values

  val selectedVariant: NdkVariant
    get() {
      val selected = myVariantsByName[mySelectedVariantName]!!
      return selected
    }

  /**
   * @return true if the variant model with given name has been requested before.
   */
  fun variantExists(variantName: String): Boolean {
    return myVariantsByName.containsKey(variantName)
  }

  fun setSelectedVariantName(name: String) {
    // Select from synced variants.
    val variantNames: Collection<String> = myVariantsByName.keys
    if (variantNames.contains(name)) {
      mySelectedVariantName = name
    }
    else {
      initializeSelectedVariant()
    }
  }

  fun findToolchain(toolchainName: String): NativeToolchain? {
    return myToolchainsByName[toolchainName]
  }

  fun findSettings(settingsName: String): NativeSettings? {
    return mySettingsByName[settingsName]
  }

  override fun hashCode(): Int {
    // Hashcode should consist of what's written in writeObject. Everything else is derived from these so those don't matter wrt to
    // identity.
    return Objects.hash(
      myModuleName,
      myRootDirPath,
      myAndroidProject,
      mySelectedVariantName,
      myVariantAbi)
  }

  override fun equals(obj: Any?): Boolean {
    if (this === obj) {
      return true
    }
    if (obj == null) {
      return false
    }
    if (obj !is NdkModuleModel) {
      return false
    }
    val that = obj
    if (myModuleName != that.myModuleName) {
      return false
    }
    if (myRootDirPath.path != that.myRootDirPath.path) {
      return false
    }
    if (myAndroidProject != that.myAndroidProject) {
      return false
    }
    if (mySelectedVariantName != that.mySelectedVariantName) {
      return false
    }
    return if (myVariantAbi != that.myVariantAbi) {
      false
    }
    else true
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
    myVariantAbi.addAll(variantAbi)
    parseAndSetModelVersion()
    features = NdkModelFeatures(myModelVersion)
    populateModuleFields()
    initializeSelectedVariant()
  }
}