/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.ide.common.gradle.model.IdeVariantHeader
import com.android.utils.appendCamelCase

data class VariantSelectionChange(
  /**
   * The name of the build type in the diffed variant if different from the build type name in the base variant.
   */
  val buildType: String? = null,

  /**
   * Pairs of the dimension and flavor names which are different in the diffed variant when compared with the base variant.
   */
  val flavors: Map<String, String> = emptyMap()
) {
  val isEmpty: Boolean get() = buildType == null && flavors.isEmpty()

  companion object {
    val EMPTY = VariantSelectionChange()

    /**
     * Extracts the dimensions and values that differ between two compatible variants [base] and [from].
     */
    fun extractVariantSelectionChange(from: VariantDetails, base: VariantDetails?): VariantSelectionChange? {
      // We cannot process variant changes when variant definitions changed.
      if (from.flavors.map { it.first } != base?.flavors?.map { it.first }) return null

      val otherFlavors = base.flavors.toMap()
      return VariantSelectionChange(
        buildType = from.buildType.takeUnless { it == base.buildType },
        flavors = from.flavors.filter { (dimension, newFlavor) -> otherFlavors[dimension] != newFlavor }.toMap()
      )
    }
  }
}

fun createVariantDetailsFrom(dimensions: Collection<String>, variant: IdeVariantHeader): VariantDetails =
  VariantDetails(
    variant.name,
    variant.buildType,
    if (dimensions.size == variant.productFlavors.size) dimensions.zip(variant.productFlavors) else emptyList()
  )


fun VariantDetails.applyChange(selectionChange: VariantSelectionChange): VariantDetails {
  val newBuildType = selectionChange.buildType ?: buildType
  val newFlavors = flavors.map { (dimension, flavor) -> dimension to (selectionChange.flavors[dimension] ?: flavor) }
  return VariantDetails(
    buildVariantName(newBuildType, newFlavors.asSequence().map { it.second }),
    newBuildType,
    newFlavors
  )
}

fun buildVariantName(buildType: String, flavors: Sequence<String>): String {
  return buildString {
    flavors.forEach { appendCamelCase(it) }
    appendCamelCase(buildType)
  }
}