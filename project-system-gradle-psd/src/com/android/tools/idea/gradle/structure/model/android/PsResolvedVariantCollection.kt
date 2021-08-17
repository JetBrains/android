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

import com.android.tools.idea.gradle.model.IdeVariant

internal class PsResolvedVariantCollection(parent: PsAndroidModule) : PsCollectionBase<PsVariant, PsVariantKey, PsAndroidModule>(parent) {
  init {
    refresh()
  }

  override fun getKeys(from: PsAndroidModule): Set<PsVariantKey> =
    from.resolvedModel?.variants?.map { PsVariantKey(it.buildType, it.productFlavors) }.orEmpty().toSet()

  override fun create(key: PsVariantKey): PsVariant = PsVariant(parent, key)

  override fun update(key: PsVariantKey, model: PsVariant) {
    val resolvedVariant =
      parent
        .resolvedModel
        ?.variants
        ?.singleOrNull { it.buildType == key.buildType && it.productFlavors == key.productFlavors } as? IdeVariant
                          ?: throw IllegalStateException("Cannot find a resolved variant named '$key'")
    model.init(resolvedVariant)
  }
}
