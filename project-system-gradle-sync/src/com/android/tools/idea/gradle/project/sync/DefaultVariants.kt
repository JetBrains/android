/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.builder.model.v2.dsl.BuildType
import com.android.builder.model.v2.dsl.ProductFlavor
import com.android.builder.model.v2.ide.BasicVariant
import org.jetbrains.annotations.VisibleForTesting

fun List<BasicVariant>.getDefaultVariant(buildTypes: List<BuildType>, productFlavors: List<ProductFlavor>): String? {
  return map { VariantDef(it.name, it.buildType, it.productFlavors) }
    .getDefaultVariant(
      userPreferredBuildTypes = buildTypes.filter { it.isDefault == true }.map { it.name }.toSet(),
      userPreferredProductFlavors = productFlavors.filter { it.isDefault == true }.map { it.name }.toSet(),
    )
}

@VisibleForTesting
class VariantDef(val name: String, val buildType: String?, val productFlavors: List<String>)

@VisibleForTesting
fun List<VariantDef>.getDefaultVariant(
  userPreferredBuildTypes: Set<String>,
  userPreferredProductFlavors: Set<String>,
): String? {
  val effectiveFlavorDimensions = this.minOfOrNull { it.productFlavors.size } ?: return null
  val availableDimensionIndices = 0 until effectiveFlavorDimensions
  fun <T: Comparable<T>> Comparator<VariantDef>.thenByProductFlavor(selector: (flavor: String) -> T) =
    availableDimensionIndices.fold(this) { acc, index -> acc.thenBy {
      selector(it.productFlavors[index])
    }}

  fun prefer(condition: Boolean): Int = if (condition) 0 else 1

  val comparator =
    compareBy<VariantDef> { prefer(it.buildType in userPreferredBuildTypes) }
      .thenByProductFlavor { prefer(it in userPreferredProductFlavors) }
      .thenBy { prefer(it.buildType == "debug") }
      .thenByProductFlavor { it }
      .thenBy { it.buildType }

  return this.minWithOrNull(comparator)?.name
}

