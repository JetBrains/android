/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.ui.resourcemanager.explorer

import com.android.resources.ResourceType
import com.android.tools.idea.ui.resourcemanager.model.Asset
import com.android.tools.idea.ui.resourcemanager.model.DesignAsset
import com.android.tools.idea.ui.resourcemanager.model.FilterOptions
import com.android.tools.idea.ui.resourcemanager.model.ResourceAssetSet
import com.android.tools.idea.ui.resourcemanager.model.ResourceSection
import com.android.tools.idea.ui.resourcemanager.rendering.AssetPreviewManager
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.ui.speedSearch.SpeedSearch
import org.jetbrains.android.facet.AndroidFacet
import java.util.concurrent.CompletableFuture

/**
 * Interface for the view model of [com.android.tools.idea.ui.resourcemanager.explorer.ResourceExplorerListView].
 *
 * The production implementation is [ResourceExplorerListViewModelImpl].
 */
interface ResourceExplorerListViewModel {
  /**
   * The reason why [updateUiCallback] is being called.
   */
  enum class UpdateUiReason {
    /**
     * Resource information has changed, caused by adding/editing/removing resources or from changing Module, so the list of resources has
     * to be refreshed.
     */
    RESOURCES_CHANGED,
    /**
     * Resource Type of the model has changed, after this, the view should reset back to showing the list of resources if it was in any
     * other state, like showing resource configurations.
     */
    RESOURCE_TYPE_CHANGED,
    /**
     * The Image Cache has changed, it might be necessary to repaint.
     */
    IMAGE_CACHE_CHANGED
  }

  /**
   * Callback called when the model has changed. Could happened for anything listed in [UpdateUiReason].
   */
  var updateUiCallback: ((UpdateUiReason) -> Unit)?

  /**
   * Callback called when the [AndroidFacet] is changed.
   */
  var facetUpdaterCallback: ((facet: AndroidFacet) -> Unit)?

  /**
   * The current [ResourceType] of resources being fetched.
   */
  var currentResourceType: ResourceType

  val selectedTabName: String get() = ""

  val assetPreviewManager: AssetPreviewManager

  val summaryPreviewManager: AssetPreviewManager

  val facet: AndroidFacet

  val speedSearch: SpeedSearch

  val filterOptions: FilterOptions

  val externalActions: Collection<ActionGroup>

  /**
   * Clears the cached image for all resources being currently displayed for the [currentResourceType].
   *
   * This considers the fields in [FilterOptions], except for [FilterOptions.searchString].
   */
  fun clearCacheForCurrentResources()

  /**
   * Clears the cached image for the given [DesignAsset].
   *
   * Clearing the cached image will indirectly result in a new image being rendered and cached.
   */
  fun clearImageCache(asset: DesignAsset)

  /**
   * Returns a list of [ResourceSection] with one section per namespace, the first section being the
   * one containing the resource of the current module.
   */
  fun getCurrentModuleResourceLists(): CompletableFuture<List<ResourceSection>>

  /**
   * Similar to [getCurrentModuleResourceLists], but fetches resources for all other modules excluding the ones being displayed.
   */
  fun getOtherModulesResourceLists(): CompletableFuture<List<ResourceSection>>

  /**
   * Delegate method to handle calls to [com.intellij.openapi.actionSystem.DataProvider.getData].
   */
  fun uiDataSnapshot(sink: DataSink, selectedAssets: List<Asset>)

  /**
   * Returns a map of some specific resource details, typically: name, reference, type, configuration, value.
   */
  fun getResourceSummaryMap(resourceAssetSet: ResourceAssetSet): CompletableFuture<Map<String, String>>

  /**
   * Returns a map for resource configurations, used to map each defined configuration with the resolved value of the resource and some
   * extra details about the resolved value.
   *
   * Eg:
   * > anydpi-v26 &emsp; | &emsp; Adaptive icon - ic_launcher.xml
   *
   * > hdpi &emsp;&emsp;&emsp;&emsp;&nbsp; | &emsp; Mip Map File - ic_launcher.png
   */
  fun getResourceConfigurationMap(resourceAssetSet: ResourceAssetSet): CompletableFuture<Map<String, String>>

  /**
   * Action when selecting an [asset] (double click or select + ENTER key).
   */
  val doSelectAssetAction: (asset: Asset) -> Unit

  val updateSelectedAssetSet: ((assetSet: ResourceAssetSet) -> Unit)

  /**
   * Triggers an [AndroidFacet] change through [facetUpdaterCallback].
   *
   * Eg: Searching for resources matching 'ic' and clicking the LinkLabel to switch to module Foo that contains resources matching the
   * filter. All components of the ResourceExplorer should update to module Foo.
   */
  fun facetUpdated(newFacet: AndroidFacet)
}