// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.idea.resourceExplorer.plugin

import com.android.tools.idea.resourceExplorer.model.DesignAsset
import com.intellij.openapi.diagnostic.Logger
import javax.imageio.ImageIO
import javax.swing.JPanel

val logger = Logger.getInstance(RasterResourceImporter::class.java)

/**
 * [ResourceImporter] that handles the importation of raster type images
 * (png, jpg, etc...)
 */
class RasterResourceImporter : ResourceImporter {
  companion object {

    val imageTypeExtensions = ImageIO.getReaderFormatNames().toSet()
  }

  override fun getPresentableName() = "Simple Image Importer"

  override fun getSupportedFileTypes(): Set<String> = RasterResourceImporter.imageTypeExtensions

  override fun supportsBatchImport(): Boolean = true

  override fun getConfigurationPanel(callback: ConfigurationDoneCallback): JPanel? {
    callback.configurationDone()
    return null
  }

  override fun userCanEditQualifiers(): Boolean = true

  override fun getSourcePreview(asset: DesignAsset): DesignAssetRenderer? =
    DesignAssetRendererManager.getInstance().getViewer(RasterAssetRenderer::class.java)

  override fun getImportPreview(asset: DesignAsset): DesignAssetRenderer? = getSourcePreview(asset)
}
