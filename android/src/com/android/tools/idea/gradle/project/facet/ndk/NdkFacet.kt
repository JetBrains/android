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
package com.android.tools.idea.gradle.project.facet.ndk

import com.android.tools.idea.gradle.project.model.NdkModuleModel
import com.android.tools.idea.gradle.project.model.VariantAbi
import com.android.tools.idea.projectsystem.getHolderModule
import com.intellij.facet.Facet
import com.intellij.facet.FacetManager
import com.intellij.facet.FacetTypeId
import com.intellij.facet.FacetTypeRegistry
import com.intellij.facet.impl.FacetUtil
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.WriteExternalException

class NdkFacet(module: Module, name: String, configuration: NdkFacetConfiguration
) : Facet<NdkFacetConfiguration?>(facetType, module, name, configuration, null) {
  var ndkModuleModel: NdkModuleModel? = null
    private set

  override fun initFacet() {
    writeConfigurationToDisk()
  }

  /**
   * The currently selected variant and ABI for this module.
   *
   * The getter would always try to return a sensible value. That is, if the internally stored value is null or nonsense, the getter would
   * pick a default variant and ABI that's available. In the rare case that this module has no variant and ABI available at all (for
   * example, an ABI filter has filtered out all ABIs), the getter will return null.
   *
   * The setter accepts any value and internally this value is stored for later even if it's not among the available variant ABIs. This
   * ensures the behavior is stable across (possibly incorrect) changes on ABI filters.
   */
  var selectedVariantAbi: VariantAbi?
    get() = configuration.selectedVariantAbi.takeIf { it in ndkModuleModel?.allVariantAbis ?: emptySet() }
            ?: ndkModuleModel?.getDefaultVariantAbi()
    set(value) {
      if (configuration.selectedVariantAbi != value) {
        configuration.selectedVariantAbi = value
        writeConfigurationToDisk()
      }
    }

  private fun writeConfigurationToDisk() {
    try {
      FacetUtil.saveFacetConfiguration(configuration)
    }
    catch (e: WriteExternalException) {
      Logger.getInstance(NdkFacet::class.java).error("Unable to save contents of '$facetName' facet.", e)
    }
  }

  fun setNdkModuleModel(ndkModuleModel: NdkModuleModel) {
    this.ndkModuleModel = ndkModuleModel
    this.selectedVariantAbi = VariantAbi(ndkModuleModel.selectedVariant, ndkModuleModel.selectedAbi)
  }

  companion object {
    @JvmStatic
    val facetId: String = "native-android-gradle"

    @JvmStatic
    val facetName: String = "Native-Android-Gradle"

    @JvmStatic
    val facetTypeId = FacetTypeId<NdkFacet>(facetId)

    @JvmStatic
    fun getInstance(module: Module): NdkFacet? {
      return FacetManager.getInstance(module.getHolderModule()).getFacetByType(facetTypeId)
    }

    @JvmStatic
    val facetType: NdkFacetType
      get() {
        val facetType = FacetTypeRegistry.getInstance().findFacetType(facetId)
        assert(facetType is NdkFacetType)
        return facetType as NdkFacetType
      }
  }
}