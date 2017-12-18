/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.tools.idea.resourceExplorer.importer.ImportersProvider
import com.android.tools.idea.resourceExplorer.importer.getAssetSets
import com.android.tools.idea.resourceExplorer.model.DesignAsset
import com.android.tools.idea.resourceExplorer.model.DesignAssetListModel
import com.android.tools.idea.resourceExplorer.model.DesignAssetSet
import com.android.tools.idea.resourceExplorer.synchronisation.SynchronizationListener
import com.android.tools.idea.resourceExplorer.synchronisation.SynchronizationManager
import com.android.tools.idea.resourceExplorer.view.DesignAssetExplorer
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.android.facet.AndroidFacet
import java.awt.Image
import java.awt.image.BufferedImage

private val LOGGER = Logger.getInstance(ExternalDesignAssetExplorer::class.java)
private val EMPTY_IMAGE = BufferedImage(1, 1, BufferedImage.TYPE_BYTE_BINARY)

/**
 * ViewModel for [com.android.tools.idea.resourceExplorer.view.ExternalResourceBrowser]
 * to manage design resources outside the project
 */
class ExternalDesignAssetExplorer(
    val facet: AndroidFacet,
    private val fileHelper: ResourceFileHelper,
    private val importersProvider: ImportersProvider,
    private val synchronizationManager: SynchronizationManager
) : DesignAssetExplorer {

  override val designAssetListModel = DesignAssetListModel()

  init {
    synchronizationManager.addListener(object : SynchronizationListener {
      override fun resourceAdded(file: VirtualFile) {
        designAssetListModel.refresh()
      }

      override fun resourceRemoved(file: VirtualFile) {
        designAssetListModel.refresh()
      }
    })
  }

  /**
   * Set the directory to browse
   */
  fun setDirectory(directory: VirtualFile) {
    if (directory.isValid && directory.isDirectory) {
      designAssetListModel.setAssets(
          getAssetSets(directory, importersProvider.supportedFileTypes)
              .sortedBy { (name, _) -> name })
    }
    else {
      LOGGER.error("${directory.path} is not a valid directory")
      return
    }
  }

  /**
   * Import this [DesignAssetSet] into the project
   */
  fun importDesignAssetSet(selectedValue: DesignAssetSet) {
    selectedValue.designAssets.forEach { asset ->
      // TODO use plugin to convert the asset
      fileHelper.copyInProjectResources(asset, selectedValue.name, facet)

    }
  }

  override fun getPreview(asset: DesignAsset): Image {
    val extension = asset.file.extension
    return if (!extension.isNullOrEmpty()) {
      importersProvider.getImportersForExtension(extension!!).firstOrNull()?.getSourcePreview(asset)
          ?: EMPTY_IMAGE
    }
    else {
      EMPTY_IMAGE
    }
  }

  fun getSynchronizationStatus(assetSet: DesignAssetSet) =
      synchronizationManager.getSynchronizationStatus(assetSet)

  override fun getStatusLabel(assetSet: DesignAssetSet) = getSynchronizationStatus(assetSet).name

}
