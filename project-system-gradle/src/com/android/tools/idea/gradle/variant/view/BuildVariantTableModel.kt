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
package com.android.tools.idea.gradle.variant.view

import com.android.tools.idea.gradle.project.facet.ndk.NdkFacet
import com.android.tools.idea.gradle.project.facet.ndk.NdkFacet.Companion.getInstance
import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.android.tools.idea.gradle.project.model.NdkModuleModel
import com.android.tools.idea.gradle.project.model.NdkModuleModel.Companion.get
import com.android.tools.idea.gradle.project.model.VariantAbi
import com.android.tools.idea.gradle.project.sync.idea.getVariantAndAbi
import com.android.tools.idea.gradle.util.ModuleTypeComparator
import com.android.tools.idea.projectsystem.getAndroidFacets
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.annotations.VisibleForTesting
import javax.swing.table.DefaultTableModel

/**
 * Represents a single selectable item in the Build Variant dropdown.
 */
data class BuildVariantItem(val buildVariantName: String) : Comparable<BuildVariantItem> {
  override fun compareTo(other: BuildVariantItem): Int = buildVariantName.compareTo(other.buildVariantName)
  override fun toString(): String = buildVariantName
}

/**
 * Represents a single selectable item in the ABI dropdown.
 */
data class AbiItem(val abiName: String) : Comparable<AbiItem> {
  override fun compareTo(other: AbiItem): Int = abiName.compareTo(other.abiName)
  override fun toString(): String = abiName
}

data class BuildVariantTableRow(
  val module: Module, // It is safe to keep a reference since variants are reloaded each time sync completes.
  val variant: String,
  val abi: String?,
  val buildVariants: List<BuildVariantItem>,
  val abis: List<AbiItem>
) {

  fun buildVariantsAsArray(): Array<BuildVariantItem>? = buildVariants.takeUnless { it.isEmpty() }?.toTypedArray()
  fun abisAsArray(): Array<AbiItem>? = abis.takeUnless { it.isEmpty() }?.toTypedArray()

  fun variantItem(): BuildVariantItem = buildVariants.find { it.buildVariantName == variant } ?: error("Variant $variant not found")
  fun abiItem(): AbiItem? = abi?.let { abis.find { it.abiName == abi } ?: error("Abi $abi not found") }
}

/**
 * The model to use for the Build Variant table in the panel.
 */
@VisibleForTesting
class BuildVariantTableModel
private constructor(
  val rows: List<BuildVariantTableRow>,
  data: Array<out Array<out Any?>>,
  columnNames: Array<out Any>
) : DefaultTableModel(data, columnNames) {

  companion object {

    @JvmStatic
    fun createEmpty(): BuildVariantTableModel = create(emptyList())

    @JvmStatic
    fun create(project: Project ): BuildVariantTableModel {
      val rows = buildVariantTableModelRows(project)
      val hasVariants = rows.any { it.buildVariants.isNotEmpty() }
      return if (hasVariants) create(rows) else createEmpty()
    }

    private fun create(rows: List<BuildVariantTableRow>): BuildVariantTableModel {
      val hasAbis = rows.any { it.abis.isNotEmpty() }
      return BuildVariantTableModel(
        rows,
        rows.map { it.toArray(hasAbis) }.toTypedArray(),
        if (hasAbis) TABLE_COLUMN_NAMES_WITH_ABI else TABLE_COLUMN_NAMES_WITHOUT_ABI
      )
    }

    // Column headers for projects that only have Java/Kotlin code (i.e., no native code).
    private val TABLE_COLUMN_NAMES_WITHOUT_ABI = arrayOf("Module", "Active Build Variant")

    // Column headers for projects that also have native code.
    private val TABLE_COLUMN_NAMES_WITH_ABI = arrayOf("Module", "Active Build Variant", "Active ABI")
  }
}

private fun BuildVariantTableRow.toArray(hasAbis: Boolean): Array<Any?> = if (hasAbis) arrayOf(module, variant, abi)
else arrayOf(module, variant)

private fun buildVariantTableModelRows(project: Project) =
  project
    .getAndroidFacets()
    .sortedWith(compareBy(ModuleTypeComparator.INSTANCE) { it.module })
    .map { androidFacet ->
      val variantAndAbi = androidFacet.getVariantAndAbi()
      val buildVariantItems = getBuildVariantItems(androidFacet)
      val abiItems = getAbiItems(androidFacet, variantAndAbi.variant)
      BuildVariantTableRow(androidFacet.holderModule, variantAndAbi.variant, variantAndAbi.abi, buildVariantItems, abiItems)
    }

private fun getBuildVariantItems(facet: AndroidFacet): List<BuildVariantItem> {
  return GradleAndroidModel.get(facet)?.variantNames.orEmpty().map { BuildVariantItem(it) }.sorted()
}

private fun getAbiItems(facet: AndroidFacet, foVariant: String): List<AbiItem> {
  return getAbiNames(facet, foVariant).map { AbiItem(it) }.sorted()
}

private fun getAbiNames(facet: AndroidFacet, forVariant: String): Collection<String> {
  val ndkModuleModel = getNdkModuleModelIfNotJustDummy(facet) ?: return emptyList()
  val allVariantAbis = ndkModuleModel.allVariantAbis
  return allVariantAbis.filter { (variant): VariantAbi -> variant == forVariant }.map(VariantAbi::abi)
}

private fun getNdkModuleModelIfNotJustDummy(ndkFacet: NdkFacet): NdkModuleModel? {
  val ndkModel = get(ndkFacet)
  return if (ndkModel == null || ndkFacet.selectedVariantAbi == null) { // There are no valid NDK variants. Treat as if NdkModuleModel does not exist.
    null
  } else ndkModel
}

private fun getNdkModuleModelIfNotJustDummy(facet: AndroidFacet): NdkModuleModel? {
  val ndkFacet = getInstance(facet.holderModule) ?: return null
  return getNdkModuleModelIfNotJustDummy(ndkFacet)
}
