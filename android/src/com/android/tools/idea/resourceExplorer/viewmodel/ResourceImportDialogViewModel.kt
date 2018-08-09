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

import com.android.ide.common.resources.configuration.DensityQualifier
import com.android.tools.idea.resourceExplorer.importer.DesignAssetImporter
import com.android.tools.idea.resourceExplorer.model.DesignAsset
import com.android.tools.idea.resourceExplorer.model.DesignAssetSet
import com.android.tools.idea.resourceExplorer.plugin.DesignAssetRendererManager
import com.intellij.util.ui.JBUI
import org.jetbrains.android.facet.AndroidFacet
import java.awt.Image

/**
 * ViewModel for [ResourceImportDialogViewModel]
 */
class ResourceImportDialogViewModel(private val facet: AndroidFacet,
                                    private val assetSets: List<DesignAssetSet>,
                                    private val designAssetImporter: DesignAssetImporter = DesignAssetImporter()
) {

  private val rendererManager = DesignAssetRendererManager.getInstance()

  fun doImport() {
    designAssetImporter.importDesignAssets(assetSets, facet)
  }

  fun getAssetDensity(asset: DesignAsset) =
    asset.qualifiers.firstOrNull { it is DensityQualifier }?.folderSegment ?: "default"

  fun getAssetPreview(asset: DesignAsset): Image? {
    return rendererManager
      .getViewer(asset.file)
      .getImage(asset.file, null, JBUI.size(50))
      .get()
  }

  fun getRealSize(asset: DesignAsset): String {
    // TODO get the real size for this icon (without creating a BufferedImage)
    return "64x64"
  }
}
