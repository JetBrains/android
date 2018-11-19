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
package com.android.tools.idea.resourceExplorer.viewmodel

import com.android.tools.idea.resourceExplorer.importer.DesignAssetImporter
import com.android.tools.idea.resourceExplorer.importer.ImportersProvider
import com.android.tools.idea.resourceExplorer.importer.chooseDesignAssets
import com.android.tools.idea.resourceExplorer.model.DesignAsset
import com.android.tools.idea.resourceExplorer.model.DesignAssetSet
import com.android.tools.idea.resourceExplorer.plugin.DesignAssetRendererManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.ui.JBUI
import org.jetbrains.android.facet.AndroidFacet
import java.awt.Image
import java.util.concurrent.CompletableFuture

/**
 * ViewModel for [ResourceImportDialogViewModel]
 */
class ResourceImportDialogViewModel(val facet: AndroidFacet,
                                    assetSets: List<DesignAssetSet>,
                                    private val designAssetImporter: DesignAssetImporter = DesignAssetImporter(),
                                    private val importersProvider: ImportersProvider = ImportersProvider()
) {

  private val assetSetsToImport = assetSets.associate { it.name to it }.toMutableMap()

  val assetSets get() = assetSetsToImport.values

  private val rendererManager = DesignAssetRendererManager.getInstance()
  val fileCount: Int get() = assetSets.sumBy { it.designAssets.size }
  var updateCallback: () -> Unit = {}

  fun doImport() {
    designAssetImporter.importDesignAssets(assetSetsToImport.values, facet)
  }

  fun getAssetPreview(asset: DesignAsset): CompletableFuture<out Image?> {
    return rendererManager
      .getViewer(asset.file)
      .getImage(asset.file, facet.module, JBUI.size(50))
  }

  fun getItemNumberString(assetSet: DesignAssetSet) =
    "(${assetSet.designAssets.size} ${StringUtil.pluralize("item", assetSet.designAssets.size)})"

  /**
   * Remove the [asset] from the list of [DesignAsset]s to import.
   * @return the [DesignAssetSet] that was containing the [asset]
   */
  fun removeAsset(asset: DesignAsset): DesignAssetSet {
    val designAssetSet = assetSetsToImport.values.first { it.designAssets.contains(asset) }
    designAssetSet.designAssets -= asset
    if (designAssetSet.designAssets.isEmpty()) {
      assetSetsToImport.remove(designAssetSet.name)
    }
    updateCallback()
    return designAssetSet
  }

  /**
   * Invoke a path chooser and add new files to the list of assets to import.
   * If a file is already present, it won't be added and new [DesignAsset] will be merged
   * with [DesignAssetSet] of the same name.
   *
   * [assetAddedCallback] will be called with a new or existing [DesignAssetSet] and
   * a list of newly added [DesignAsset]s, which means that it's a subset of [DesignAssetSet.designAssets].
   * This allows the view to merge the new [DesignAsset] within a potential existing view of a [DesignAssetSet].
   * The callback won't be called if there is no new file.
   */
  fun importMoreAssets(assetAddedCallback: (DesignAssetSet, List<DesignAsset>) -> Unit) {
    chooseDesignAssets(importersProvider) { newAssetSets ->
      newAssetSets.forEach {
        addAssetSet(it, assetAddedCallback)
      }
    }
  }

  private fun addAssetSet(it: DesignAssetSet,
                          assetAddedCallback: (DesignAssetSet, List<DesignAsset>) -> Unit) {
    val existingAssetSet = assetSetsToImport[it.name]
    if (existingAssetSet != null) {
      val existingPaths = existingAssetSet.designAssets.map { designAsset -> designAsset.file.path }.toSet()
      val onlyNewFiles = it.designAssets.filter { designAsset -> designAsset.file.path !in existingPaths }
      if (onlyNewFiles.isNotEmpty()) {
        existingAssetSet.designAssets += onlyNewFiles
        assetAddedCallback(existingAssetSet, onlyNewFiles)
      }
    }
    else {
      assetSetsToImport[it.name] = it
      assetAddedCallback(it, it.designAssets)
    }
  }
}
