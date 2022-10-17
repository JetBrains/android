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

import com.android.tools.idea.gradle.model.IdeAndroidProject
import com.android.tools.idea.gradle.model.IdeVariantCore

internal const val FAKE_DIMENSION = "agp-missing-dimension-for-sync-only"

fun buildVariantNameResolver(androidProject: IdeAndroidProject, v2Variants: Collection<IdeVariantCore>): AndroidVariantResolver {
  val availableDimensions = androidProject.productFlavors.mapNotNull { it.productFlavor.dimension }.toSet()
  val dimensions = androidProject.flavorDimensions.filter { availableDimensions.contains(it) }
    // See: b/242856048 and b/242289523: `FAKE_DIMENSION` is not reported by the AGP in `androidProject.flavorDimensions`.
    .takeUnless { it.isEmpty() }
    ?: (if (availableDimensions == setOf(FAKE_DIMENSION)) availableDimensions else emptySet())

  val map = v2Variants
    .associate { variant ->
      variant.productFlavors.toList() + listOfNotNull(variant.buildType) to variant.name
    }

  return object : AndroidVariantResolver {
    override fun resolveVariant(buildType: String?, productFlavors: (dimension: String) -> String): String? {
      val flavors = dimensions.map(productFlavors)
      val key = flavors + listOfNotNull(buildType)
      return map.get(key)
    }
  }
}