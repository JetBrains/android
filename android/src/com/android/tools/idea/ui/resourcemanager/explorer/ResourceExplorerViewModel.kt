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

import com.android.ide.common.resources.ResourceItem
import com.android.resources.ResourceType
import com.android.tools.idea.ui.resourcemanager.MANAGER_SUPPORTED_RESOURCES
import com.android.tools.idea.ui.resourcemanager.model.Asset
import com.android.tools.idea.ui.resourcemanager.model.DesignAsset
import com.android.tools.idea.ui.resourcemanager.model.FilterOptions
import com.android.tools.idea.ui.resourcemanager.model.FilterOptionsParams
import com.android.tools.idea.ui.resourcemanager.model.ResourceAssetSet
import com.android.tools.idea.ui.resourcemanager.rendering.AssetPreviewManager
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.speedSearch.SpeedSearch
import org.jetbrains.android.facet.AndroidFacet
import java.util.concurrent.CompletableFuture

/**
 * Interface for the view model of [com.android.tools.idea.ui.resourcemanager.view.ResourceExplorerView]
 *
 * The production implementation is [ResourceExplorerViewModelImpl].
 */
interface ResourceExplorerViewModel {
  /**
   * callback called when the resource model has change. This happens when the facet is changed.
   */
  var resourceChangedCallback: (() -> Unit)?

  /**
   * Callback called when the [AndroidFacet] is changed.
   */
  var facetUpdaterCallback: ((facet: AndroidFacet) -> Unit)?

  /**
   * Callback called when the current [ResourceType] is changed. E.g: Selecting a different resource tab.
   */
  var resourceTypeUpdaterCallback: ((resourceType: ResourceType) -> Unit)?

  /**
   * The index in [resourceTypes] of the resource type being used. Changing the value
   * of this field should change the resources being shown.
   */
  var resourceTypeIndex: Int

  /**
   * The available resource types
   */
  val resourceTypes: Array<ResourceType>

  val selectedTabName: String get() = ""

  val assetPreviewManager: AssetPreviewManager

  val summaryPreviewManager: AssetPreviewManager

  var facet: AndroidFacet

  val speedSearch: SpeedSearch

  val filterOptions: FilterOptions

  val externalActions: Collection<ActionGroup>

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
  fun getData(dataId: String?, selectedAssets: List<Asset>): Any?

  /**
   * Returns a map of some specific resource details, typically: name, reference, type, configuration, value.
   */
  fun getResourceSummaryMap(resourceAssetSet: ResourceAssetSet): Map<String, String>

  /**
   * Returns a map for resource configurations, used to map each defined configuration with the resolved value of the resource and some
   * extra details about the resolved value.
   *
   * Eg:
   * > anydpi-v26 &emsp; | &emsp; Adaptive icon - ic_launcher.xml
   *
   * > hdpi &emsp;&emsp;&emsp;&emsp;&nbsp; | &emsp; Mip Map File - ic_launcher.png
   */
  fun getResourceConfigurationMap(resourceAssetSet: ResourceAssetSet): Map<String, String>

  /**
   * Action when selecting an [asset] (double click or select + ENTER key).
   */
  val doSelectAssetAction: (asset: Asset) -> Unit

  fun facetUpdated(newFacet: AndroidFacet, oldFacet: AndroidFacet)

  /**
   * Returns the index of the tab in which the given virtual file appears,
   * or -1 if the file is not found.
   *
   * For example if we pass "res/drawable/icon.png, the method should return
   * the index of the Drawable tab.
   */
  fun getTabIndexForFile(virtualFile: VirtualFile): Int

  /** Helper functions to create an instance of [ResourceExplorerViewModel].*/
  companion object {
    fun createResManagerViewModel(facet: AndroidFacet): ResourceExplorerViewModel
      = ResourceExplorerViewModelImpl(facet,
                                      null,
                                      FilterOptionsParams(moduleDependenciesInitialValue = false,
                                                          librariesInitialValue = false,
                                                          showSampleData = false,
                                                          androidResourcesInitialValue = false,
                                                          themeAttributesInitialValue = false),
                                      MANAGER_SUPPORTED_RESOURCES)


    fun createResPickerViewModel(facet: AndroidFacet,
                                 supportedResourceTypes: Array<ResourceType>,
                                 showSampleData: Boolean,
                                 currentFile: VirtualFile?,
                                 doSelectAssetCallback: (resource: ResourceItem) -> Unit): ResourceExplorerViewModel
      = ResourceExplorerViewModelImpl(facet,
                                      currentFile,
                                      FilterOptionsParams(moduleDependenciesInitialValue = true,
                                                          librariesInitialValue = false,
                                                          showSampleData = showSampleData,
                                                          androidResourcesInitialValue = true,
                                                          themeAttributesInitialValue = true),
                                      supportedResourceTypes) {
      // Callback should not have ResourceExplorerAsset dependency, so we return ResourceItem.
      asset -> doSelectAssetCallback.invoke(asset.resourceItem)
    }
  }
}