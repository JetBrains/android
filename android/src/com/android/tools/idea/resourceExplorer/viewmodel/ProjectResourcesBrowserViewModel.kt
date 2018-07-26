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
import com.android.tools.idea.res.ResourceRepositoryManager
import com.android.tools.idea.res.resolveDrawable
import com.android.tools.idea.resourceExplorer.importer.SynchronizationManager
import com.android.tools.idea.resourceExplorer.model.DesignAsset
import com.android.tools.idea.resourceExplorer.model.DesignAssetSet
import com.android.tools.idea.resourceExplorer.plugin.DesignAssetRendererManager
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.android.facet.AndroidFacet
import java.awt.Dimension
import java.awt.Image
import kotlin.properties.Delegates


private val LOG = Logger.getInstance(ProjectResourcesBrowserViewModel::class.java)

/**
 * ViewModel for [com.android.tools.idea.resourceExplorer.view.ResourceExplorerView]
 * to manage resources in the provided [facet].
 */
class ProjectResourcesBrowserViewModel(
  facet: AndroidFacet,
  synchronizationManager: SynchronizationManager // TODO listen for update
) {


  /**
   * callback called when the resource model have change. This happen when the facet is changed.
   */
  var updateCallback: (() -> Unit)? = null

  var facet by Delegates.observable(facet) { _, _, _ -> updateCallback?.invoke() }
  val resourceResolver = createResourceResolver(facet)

  /**
   * Returns a preview of the [DesignAsset].
   */
  fun getDrawablePreview(dimension: Dimension, designAssetSet: DesignAssetSet): ListenableFuture<out Image?> {
    val resolveValue = designAssetSet.resolveValue() ?: return Futures.immediateFuture(null)
    val file = resourceResolver.resolveDrawable(resolveValue, facet.module.project)
               ?: designAssetSet.getHighestDensityAsset().file
    return DesignAssetRendererManager.getInstance().getViewer(file)
      .getImage(file, facet.module, dimension)
  }

  private fun getModuleResources(type: ResourceType): List<ResourceItem> {
    val moduleRepository = ResourceRepositoryManager.getModuleResources(facet)
    return moduleRepository.namespaces.flatMap { namespace ->
      moduleRepository.getItemsOfType(namespace, type)
        .flatMap { resourceItem -> moduleRepository.getResourceItems(namespace, type, resourceItem) }
    }
  }

  /**
   * Returns a map from the library name to its resource items
   */
  private fun getLibraryResources(type: ResourceType): List<Pair<String, List<ResourceItem>>> {
    val repoManager = ResourceRepositoryManager.getOrCreateInstance(facet)
    return repoManager.libraryResources
      .map { lib ->
        (lib.libraryName ?: "") to lib.namespaces.flatMap { namespace ->
          lib.getItemsOfType(namespace, type)
            .flatMap { resourceItem -> lib.getResourceItems(namespace, type, resourceItem) }
        }
      }
  }

  private fun createResourceResolver(androidFacet: AndroidFacet): ResourceResolver {
    val configurationManager = ConfigurationManager.getOrCreateInstance(androidFacet)
    val manifest = MergedManifest.get(androidFacet)
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

  fun getResourcesLists(resourceTypes: List<ResourceType>): List<ResourceSection> {
    return resourceTypes.flatMap { resourceType ->
      val moduleResources = createResourceSection(resourceType, facet.module.name, getModuleResources(resourceType))
      val librariesResources = getLibraryResources(resourceType)
        .map { (libName, resourceItems) ->
          createResourceSection(resourceType, libName, resourceItems)
        }
      listOf(moduleResources) + librariesResources
    }
  }
}

private fun createResourceSection(type: ResourceType,
                                  libraryName: String,
                                  resourceItems: List<ResourceItem>) =
  ResourceSection(type,
                  libraryName,
                  resourceItems.map { DesignAsset(it) }
                    .groupBy { it.name }
                    .map { (name, assets) -> DesignAssetSet(name, assets) })

data class ResourceSection(val type: ResourceType,
                           val libraryName: String = "",
                           val assets: List<DesignAssetSet>)
