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

import com.android.ide.common.res2.ResourceItem
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceType
import com.android.tools.idea.res.ModuleResourceRepository
import com.android.tools.idea.resourceExplorer.importer.getBaseName
import com.android.tools.idea.resourceExplorer.model.DesignAsset
import com.android.tools.idea.resourceExplorer.model.DesignAssetListModel
import com.android.tools.idea.resourceExplorer.model.DesignAssetSet
import com.android.tools.idea.resourceExplorer.synchronisation.SynchronizationListener
import com.android.tools.idea.resourceExplorer.synchronisation.SynchronizationManager
import com.android.tools.idea.resourceExplorer.view.DesignAssetExplorer
import com.intellij.openapi.Disposable
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.android.facet.AndroidFacet
import java.awt.Image
import javax.imageio.ImageIO

/**
 * ViewModel for [InternalDesignAssetExplorer] to manage resources in the
 * provided [facet].
 */
class InternalDesignAssetExplorer(
    val facet: AndroidFacet,
    val parentDisposable: Disposable,
    synchronizationManager: SynchronizationManager
) : DesignAssetExplorer {

  override val designAssetListModel = DesignAssetListModel()

  init {
    designAssetListModel.setAssets(getModuleDesignAssets())
    synchronizationManager.addListener(object : SynchronizationListener {
      override fun resourceAdded(file: VirtualFile) {
        // TODO do not reparse all resources
        designAssetListModel.setAssets(getModuleDesignAssets())
      }

      override fun resourceRemoved(file: VirtualFile) {
        // TODO do not reparse all resources
        designAssetListModel.setAssets(getModuleDesignAssets())
      }
    })
  }

  /**
   * Returns a list of [DesignAssetSet] representing the resources in the facet.
   */
  private fun getModuleDesignAssets(): List<DesignAssetSet> {
    val repository = ModuleResourceRepository.getOrCreateInstance(facet)
    val type = ResourceType.DRAWABLE
    return repository.getItemsOfType(type)
        .flatMap { repository.getResourceItem(type, it) ?: emptyList() }
        .mapNotNull(this::resourceItemToDesignAsset)
        .groupBy { getBaseName(it.file) }
        .map { (name, assets) -> DesignAssetSet(name, assets) }
  }

  /**
   * Creates a [DesignAsset] from a [ResourceItem]
   */
  private fun resourceItemToDesignAsset(resourceItem: ResourceItem): DesignAsset? {
    val virtualFile = VfsUtil.findFileByIoFile(resourceItem.file, false) ?: return null
    val qualifiers = resourceItem.configuration.qualifiers.asList()
    return DesignAsset(virtualFile, qualifiers, ResourceFolderType.DRAWABLE)

  }

  /**
   * Return a preview of the [DesignAsset]
   */
  override fun getPreview(asset: DesignAsset): Image {
    // TODO use a plugin to display the preview
    return ImageIO.read(asset.file.inputStream)
  }

  override fun getStatusLabel(assetSet: DesignAssetSet) = ""
}
