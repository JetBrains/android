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

import com.android.ide.common.rendering.api.ResourceValue
import com.android.ide.common.resources.ResourceItem
import com.android.ide.common.resources.ResourceResolver
import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceType
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.configurations.ResourceResolverCache
import com.android.tools.idea.model.MergedManifest
import com.android.tools.idea.res.AppResourceRepository
import com.android.tools.idea.res.ModuleResourceRepository
import com.android.tools.idea.resourceExplorer.importer.SynchronizationListener
import com.android.tools.idea.resourceExplorer.importer.SynchronizationManager
import com.android.tools.idea.resourceExplorer.model.DesignAsset
import com.android.tools.idea.resourceExplorer.model.DesignAssetListModel
import com.android.tools.idea.resourceExplorer.model.DesignAssetSet
import com.android.tools.idea.resourceExplorer.plugin.DesignAssetRendererManager
import com.android.tools.idea.resourceExplorer.view.DesignAssetExplorer
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.android.facet.AndroidFacet
import java.awt.Dimension
import java.awt.Image


private val LOG = Logger.getInstance(ModuleResourcesBrowserViewModel::class.java)

/**
 * ViewModel for [com.android.tools.idea.resourceExplorer.view.ModuleResourceBrowser]
 * to manage resources in the provided [facet].
 */
class ModuleResourcesBrowserViewModel(
  val facet: AndroidFacet,
  synchronizationManager: SynchronizationManager
) : DesignAssetExplorer {

  val resourceResolver = createResourceResolver()
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
    // TODO use the AppResourceRepository to get all resources from all the namespace
    // TODO Reuse the code from getColorListModel
    val repository =
      ModuleResourceRepository.getOrCreateInstance(facet)
    val type = ResourceType.DRAWABLE
    return repository.getItemsOfType(type)
      .flatMap { repository.getResourceItem(type, it) ?: emptyList() }
      .mapNotNull(this::resourceItemToDesignAsset)
      .groupBy { it.name }
      .map { (name, assets) -> DesignAssetSet(name, assets) }
  }

  /**
   * Creates a [DesignAsset] from a [ResourceItem]
   */
  private fun resourceItemToDesignAsset(resourceItem: ResourceItem): DesignAsset? {
    val virtualFile = resourceItem.file?.let { VfsUtil.findFileByIoFile(it, false) } ?: return null
    val qualifiers = resourceItem.configuration.qualifiers.asList()
    return DesignAsset(virtualFile, qualifiers, ResourceType.DRAWABLE)
  }

  /**
   * Return a preview of the [DesignAsset]
   */
  override fun getPreview(asset: DesignAsset, dimension: Dimension): ListenableFuture<out Image?> {
    return DesignAssetRendererManager.getInstance().getViewer(asset.file)
      .getImage(asset.file, facet.module, dimension)
  }

  override fun getStatusLabel(assetSet: DesignAssetSet) = ""

  fun getResourceValues(
    type: ResourceType
  ): Collection<ResourceValue> {

    // TODO see if we return one listModel per Namespace)
    return (getModuleResources(type) + getLibrariesResources(type))
      .mapNotNull { resourceItem -> resolveValue(resourceResolver, resourceItem) }
  }

  private fun getModuleResources(type: ResourceType): List<ResourceItem> {
    val moduleRepository = ModuleResourceRepository.getOrCreateInstance(facet)
    return moduleRepository.namespaces.flatMap { namespace ->
      moduleRepository.getItemsOfType(namespace, type)
        .flatMap { resourceItem -> moduleRepository.getResourceItems(namespace, type, resourceItem) }
    }
  }

  private fun resolveValue(
    resourceResolver: ResourceResolver, it: ResourceItem
  ): ResourceValue? {
    val resolvedValue = resourceResolver.resolveResValue(it.resourceValue)
    if (resolvedValue == null) {
      LOG.warn("${it.name} couldn't be resolved")
    }
    return resolvedValue
  }

  private fun getLibrariesResources(type: ResourceType): List<ResourceItem> {
    val repository = AppResourceRepository.getOrCreateInstance(facet)
    return repository.libraries.flatMap { lib ->
      lib.namespaces.flatMap { namespace ->
        lib.getItemsOfType(namespace, type)
          .flatMap { resourceItem -> lib.getResourceItems(namespace, type, resourceItem) }
      }
    }
  }

  private fun createResourceResolver(): ResourceResolver {
    val configurationManager = ConfigurationManager.getOrCreateInstance(facet)
    val manifest = MergedManifest.get(facet)
    val theme = manifest.manifestTheme ?: manifest.getDefaultTheme(null, null, null)
    return ResourceResolverCache(configurationManager).getResourceResolver(
      configurationManager.target,
      theme,
      FolderConfiguration.createDefault()
    )
  }
}
