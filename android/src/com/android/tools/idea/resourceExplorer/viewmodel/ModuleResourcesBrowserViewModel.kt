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
import com.android.resources.ResourceType
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.configurations.ResourceResolverCache
import com.android.tools.idea.model.MergedManifest
import com.android.tools.idea.res.AppResourceRepository
import com.android.tools.idea.res.ModuleResourceRepository
import com.android.tools.idea.res.resolveDrawable
import com.android.tools.idea.resourceExplorer.importer.SynchronizationManager
import com.android.tools.idea.resourceExplorer.model.DesignAsset
import com.android.tools.idea.resourceExplorer.model.DesignAssetSet
import com.android.tools.idea.resourceExplorer.plugin.DesignAssetRendererManager
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.VfsUtil
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
  synchronizationManager: SynchronizationManager // TODO listen for update
) {

  val resourceResolver = createResourceResolver()

  /**
   * Return a preview of the [DesignAsset]
   */
  fun getDrawablePreview(dimension: Dimension, designAssetSet: DesignAssetSet): ListenableFuture<out Image?> {
    val resolveValue = designAssetSet.resolveValue() ?: return Futures.immediateFuture(null)
    val drawable = resourceResolver.resolveDrawable(resolveValue, facet.module.project)
    val file = if (drawable != null) {
      VfsUtil.findFileByIoFile(drawable, true) ?: return Futures.immediateFuture(null)
    } else {
      designAssetSet.getHighestDensityAsset().file
    }
    return DesignAssetRendererManager.getInstance().getViewer(file)
      .getImage(file, facet.module, dimension)
  }

  fun getResourceValues(type: ResourceType): List<DesignAssetSet> {
    // TODO see if we return one listModel per Namespace)
    return (getModuleResources(type) + getLibrariesResources(type))
      .map { DesignAsset(it) }
      .groupBy { it.name }
      .map { (name, assets) -> DesignAssetSet(name, assets) }
  }

  private fun getModuleResources(type: ResourceType): List<ResourceItem> {
    val moduleRepository = ModuleResourceRepository.getOrCreateInstance(facet)
    return moduleRepository.namespaces.flatMap { namespace ->
      moduleRepository.getItemsOfType(namespace, type)
        .flatMap { resourceItem -> moduleRepository.getResourceItems(namespace, type, resourceItem) }
    }
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

  private fun DesignAssetSet.resolveValue(): ResourceValue? {
    val resourceItem = this.getHighestDensityAsset().resourceItem
    val resolvedValue = resourceResolver.resolveResValue(resourceItem.resourceValue)
    if (resolvedValue == null) {
      LOG.warn("${resourceItem.name} couldn't be resolved")
    }
    return resolvedValue
  }
}
