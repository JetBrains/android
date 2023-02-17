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
package com.android.tools.idea.gradle.structure.model.android

import com.android.tools.idea.gradle.structure.model.meta.asString

internal class PsProductFlavorCollection(parent: PsAndroidModule)
  : PsMutableCollectionBase<PsProductFlavor, PsProductFlavorKey, PsAndroidModule>(parent) {
  init {
    refresh()
  }

  override fun getKeys(from: PsAndroidModule): Set<PsProductFlavorKey> {
    val result = mutableSetOf<PsProductFlavorKey>()
    // If there is only one dimension and a product flavor is not configured with any dimension it is assumed to belong to the available
    // one.
    val silentDimension =
      from.parsedModel?.android()?.flavorDimensions()?.toList()?.takeIf { it.size == 1 }?.let { it[0]?.toString() }.orEmpty()
    result.addAll(
      from.parsedModel?.android()
        ?.productFlavors()
        ?.map { PsProductFlavorKey(it.dimension().asString() ?: silentDimension, it.name()) }.orEmpty())
    result.addAll(
      from.resolvedModel?.androidProject?.multiVariantData
        ?.productFlavors
        ?.map { PsProductFlavorKey(it.productFlavor.dimension.orEmpty(), it.productFlavor.name) }.orEmpty())
    return result
  }

  override fun create(key: PsProductFlavorKey): PsProductFlavor =
    PsProductFlavor(parent, renamed = { oldKey, newKey -> renamed(entries[oldKey] ?: error("Old key not found: $oldKey"), newKey) })

  override fun update(key: PsProductFlavorKey, model: PsProductFlavor) {
    model.init(
      parent.resolvedModel?.androidProject?.multiVariantData?.productFlavors?.map { it.productFlavor }?.firstOrNull { it.name == key.name },
      parent.parsedModel?.android()?.productFlavors()?.firstOrNull { it.name() == key.name }
    )
  }

  override fun instantiateNew(key: PsProductFlavorKey) {
    if (entries.keys.any { it.name == key.name }) throw RuntimeException("Duplicate flavor name: ${key.name}")
    parent
      .parsedModel!!
      .android()
      .addProductFlavor(key.name)
      .also {
        it.dimension().setValue(key.dimension)
      }
  }

  override fun removeExisting(key: PsProductFlavorKey) {
    parent.parsedModel!!.android().removeProductFlavor(key.name)
  }
}
