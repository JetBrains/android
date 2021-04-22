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

import com.android.tools.idea.gradle.model.ndk.v1.IdeNativeVariantAbi
import com.android.tools.idea.gradle.model.IdeVariant

/**
 * Container used to group [IdeVariant]s and [IdeNativeVariantAbi]s for a module.
 * Used to store cached variants and native variants from previous sync.
 */
class CachedVariants(
  private val variants: List<IdeVariant>,
  private val nativeVariants: List<IdeNativeVariantAbi>
) {

  companion object {
    @JvmField
    val EMPTY: CachedVariants = CachedVariants(emptyList(), emptyList())
  }

  fun getVariantsExcept(fetchedVariants: Collection<IdeVariant>): List<IdeVariant> {
    val fetchedNames = fetchedVariants.map { it.name }.toSet()
    return variants.filter { !fetchedNames.contains(it.name) }
  }

  fun getNativeVariantsExcept(fetchedVariants: Collection<IdeNativeVariantAbi>): List<IdeNativeVariantAbi> {
    val fetchedNames = fetchedVariants.map { it.abi }.toSet()
    return nativeVariants.filter { !fetchedNames.contains(it.abi) }
  }
}