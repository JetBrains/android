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

import com.android.tools.idea.gradle.model.ndk.v1.IdeNativeAndroidProject
import com.android.tools.idea.gradle.model.ndk.v1.IdeNativeVariantAbi
import com.android.tools.idea.gradle.project.facet.ndk.NdkFacet
import com.intellij.openapi.module.Module
import com.intellij.serialization.PropertyMapping
import java.io.File

/**
 * Used to track serialization version. This value should be updated when the serialized form of [NdkModuleModel] is changed in a backward-
 * incompatible way. Note that adding or deleting field is generally safe. Previous nonexistent fields will just be populated with null
 * value.
 */
private const val NDK_MODULE_MODEL_SYNC_VERSION = "2020-06-30/4"

data class NdkModuleModel
@PropertyMapping("moduleName", "rootDirPath", "selectedVariant", "selectedAbi", "ndkModel", "syncVersion") private constructor(
  private val moduleName: String,
  val rootDirPath: File,
  val selectedVariant: String,
  val selectedAbi: String,
  val ndkModel: NdkModel,
  private val syncVersion: String
) : ModuleModel, INdkModel by ndkModel {

  /**
   * Creates an [NdkModuleModel] from V1 Android Gradle Plugin models. See [V1NdkModel] for more details.
   */
  constructor(
    moduleName: String,
    rootDirPath: File,
    selectedVariant: String,
    selectedAbi: String,
    androidProject: IdeNativeAndroidProject,
    variantAbi: List<IdeNativeVariantAbi>
  ) : this(moduleName, rootDirPath, selectedVariant, selectedAbi, V1NdkModel(androidProject, variantAbi), NDK_MODULE_MODEL_SYNC_VERSION)

  /** Creates an [NdkModuleModel] from V2 Android Gradle Plugin models. See [V2NdkModel] for more details. */
  constructor(
    moduleName: String,
    rootDirPath: File,
    selectedVariant: String,
    selectedAbi: String,
    ndkModel: V2NdkModel
  ) : this(moduleName, rootDirPath, selectedVariant, selectedAbi, ndkModel, NDK_MODULE_MODEL_SYNC_VERSION)

  init {
    // If the serialization version does not match, this aborts the deserialization process and the IDE will just function as if no value
    // was serialized in the first place.
    require(syncVersion == NDK_MODULE_MODEL_SYNC_VERSION) {
      "Attempting to deserialize a model of incompatible version ($syncVersion)"
    }
  }

  override fun getModuleName() = moduleName

  fun getDefaultVariantAbi(): VariantAbi? =
    allVariantAbis.firstOrNull { (variant, abi) -> variant == "debug" && (abi == "x86" || abi == "x86_64") } ?: allVariantAbis.firstOrNull()

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
