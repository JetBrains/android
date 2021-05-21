// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.android.tools.idea.gradle.project.sync

import java.io.Serializable

data class SelectedVariant(
  /**
   * The id of the module in the form returned by [Modules.createUniqueModuleId].
   */
  val moduleId: String,

  val variantName: String,

  val abiName: String?,

  /**
   * An instance of [VariantDetails] which describes [variantName]. `null` if no models were available when constructing this instance.
   */
  val details: VariantDetails?
) : Serializable

data class VariantDetails(
  val name: String,
  val buildType: String,

  /**
   * Dimension name to flavor name pairs in the dimension order. emptyList() if there is no flavors or dimensions defined.
   */
  val flavors: List<Pair<String, String>>
) : Serializable

data class SelectedVariants(
  /**
   * Dimension name to selected variant name map.
   */
  val selectedVariants: Map<String, SelectedVariant>
) : Serializable {
  fun getSelectedVariant(moduleId: String): String? = selectedVariants[moduleId]?.variantName
  fun getSelectedAbi(moduleId: String): String? = selectedVariants[moduleId]?.abiName
}