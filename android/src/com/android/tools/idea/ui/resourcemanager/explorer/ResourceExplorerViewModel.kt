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
import com.android.ide.common.resources.ResourceResolver
import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.resources.FolderTypeRelationship
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceType
import com.android.tools.idea.configurations.Configuration
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.model.MergedManifestManager
import com.android.tools.idea.res.getFolderType
import com.android.tools.idea.ui.resourcemanager.MANAGER_SUPPORTED_RESOURCES
import com.android.tools.idea.ui.resourcemanager.model.Asset
import com.android.tools.idea.ui.resourcemanager.model.FilterOptions
import com.android.tools.idea.ui.resourcemanager.model.FilterOptionsParams
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.EdtExecutorService
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.sdk.StudioEmbeddedRenderTarget
import java.util.concurrent.CompletableFuture
import java.util.function.Function
import java.util.function.Supplier
import kotlin.properties.Delegates

/**
 * The View Model for the [ResourceExplorerView].
 *
 * @param defaultFacet Initial [AndroidFacet]
 * @param contextFileForConfiguration A [VirtualFile] that holds a [Configuration], if given, it'll be used to get the [ResourceResolver].
 * @param supportedResourceTypes The given [ResourceType]s that will be listed in the Resource Explorer tabs.
 * @param filterParams The initial params for the [FilterOptions]. Used to show more resources (E.g: Resources form external libraries).
 * @param selectAssetAction If not null, this action will be called when double-clicking or pressing Enter on a resource.
 * @param updateResourceCallback If not null, this will be invoked whenever the selection changes, if there's more than one configuration,
 * it'll be given the highest density version.
 */
class ResourceExplorerViewModel(
  defaultFacet: AndroidFacet,
  private var contextFileForConfiguration: VirtualFile?,
  var supportedResourceTypes: Array<ResourceType>,
  private val filterParams: FilterOptionsParams,
  private val selectAssetAction: ((asset: Asset) -> Unit)? = null,
  private val updateResourceCallback: ((resourceItem: ResourceItem) -> Unit)? = null
) : Disposable {

  /**
   * The ViewModel of the resources list. Obtained asynchronously. Params obtained while it's being updated are saved, then applied.
   */
  private var listViewModel: ResourceExplorerListViewModel? = null

  //region ListModel update params
  private var refreshListModel: Boolean? = null
  private var listModelPattern: String? = null
  private var listModelResourceType: ResourceType? = null
  //endregion

  val filterOptions = FilterOptions.create(
    { refreshListModel() },
    { updateListModelSpeedSearch(it) },
    filterParams
  )

  /**
   * View callback for when the ResourceType has changed.
   */
  var updateResourceTabCallback: (() -> Unit) = {}

  /**
   * View callback whenever the resources lists needs to be repopulated.
   */
  var populateResourcesCallback: (() -> Unit) = {}

  /**
   * Callback called when the [AndroidFacet] has changed.
   */
  var facetUpdaterCallback: ((facet: AndroidFacet) -> Unit) = {}

  /**
   * Callback called when the current [ResourceType] has changed.
   */
  var resourceTypeUpdaterCallback: ((resourceType: ResourceType) -> Unit) = {}

  var facet: AndroidFacet by Delegates.observable(defaultFacet) { _, oldFacet, newFacet ->
    if (newFacet != oldFacet) {
      contextFileForConfiguration = null // AndroidFacet changed, optional Configuration file is not valid.
      facetUpdaterCallback(newFacet)
      populateResourcesCallback()
    }
  }

  var resourceTypeIndex: Int = 0
    set(value) {
      if (value != field && supportedResourceTypes.indices.contains(value)) {
        field = value
        updateListModelResourceType(supportedResourceTypes[value])
        resourceTypeUpdaterCallback(supportedResourceTypes[value])
        updateResourceTabCallback()
      }
    }

  fun getTabIndexForFile(virtualFile: VirtualFile): Int {
    val folderType = if (virtualFile.isDirectory) ResourceFolderType.getFolderType(virtualFile.name) else getFolderType(virtualFile)
    val type = folderType?.let { FolderTypeRelationship.getRelatedResourceTypes(it) }?.firstOrNull()
    return supportedResourceTypes.indexOf(type)
  }

  fun createResourceListViewModel(): CompletableFuture<ResourceExplorerListViewModel> {
    (listViewModel as? Disposable)?.let { Disposer.dispose(it) }
    listViewModel = null
    val configurationFuture = getConfiguration(facet, contextFileForConfiguration)
    return getResourceResolver(facet, configurationFuture)
      .thenApplyAsync(
        Function { resourceResolver ->
          ResourceExplorerListViewModelImpl(
            facet,
            configurationFuture.join(),
            resourceResolver,
            filterOptions,
            supportedResourceTypes[resourceTypeIndex],
            selectAssetAction,
            { assetSet ->
              updateResourceCallback?.invoke(assetSet.getHighestDensityAsset().resourceItem)
            }
          ).also {
            if (!Disposer.isDisposed(this)) {
              listViewModel = it
              it.facetUpdaterCallback = { newFacet -> this@ResourceExplorerViewModel.facet = newFacet }
              Disposer.register(this, it)
              updateListModelIfNeeded()
            } else {
              Disposer.dispose(it)
            }
          }
        }, EdtExecutorService.getInstance())
  }

  override fun dispose() {
    // Do nothing.
  }

  //region ListModel update functions
  private fun refreshListModel() {
    val listModel = listViewModel
    if (listModel == null) {
      refreshListModel = true
    }
    else {
      listModel.resourceChangedCallback?.invoke()
    }
  }

  private fun updateListModelSpeedSearch(pattern: String) {
    val listModel = listViewModel
    if (listModel == null) {
      listModelPattern = pattern
    }
    else {
      listModel.speedSearch.updatePattern(pattern)
    }
  }

  private fun updateListModelResourceType(resourceType: ResourceType) {
    val listModel = listViewModel
    if (listModel == null) {
      listModelResourceType = resourceType
    }
    else {
      listModel.currentResourceType = resourceType
    }
  }

  private fun updateListModelIfNeeded() {
    if (refreshListModel != null) {
      refreshListModel = null
      refreshListModel()
    }
    val pattern = listModelPattern
    if (pattern != null) {
      listModelPattern = null
      updateListModelSpeedSearch(pattern)
    }
    val resourceType = listModelResourceType
    if (resourceType != null) {
      listModelResourceType = null
      updateListModelResourceType(resourceType)
    }
  }
  //endregion

  companion object{
    fun createResManagerViewModel(facet: AndroidFacet): ResourceExplorerViewModel =
      ResourceExplorerViewModel(
        facet,
        null,
        MANAGER_SUPPORTED_RESOURCES,
        FilterOptionsParams(
          moduleDependenciesInitialValue = false,
          librariesInitialValue = false,
          showSampleData = false,
          androidResourcesInitialValue = false,
          themeAttributesInitialValue = false
        ),
        null,
        null
      )

    fun createResPickerViewModel(facet: AndroidFacet,
                                 configurationContextFile: VirtualFile?,
                                 supportedResourceTypes: Array<ResourceType>,
                                 showSampleData: Boolean,
                                 selectAssetAction: ((asset: Asset) -> Unit)?,
                                 updateResourceCallback: ((resourceItem: ResourceItem) -> Unit)?
                                ): ResourceExplorerViewModel =
      ResourceExplorerViewModel(
        facet,
        configurationContextFile,
        supportedResourceTypes,
        FilterOptionsParams(
          moduleDependenciesInitialValue = true,
          librariesInitialValue = true,
          showSampleData = showSampleData,
          androidResourcesInitialValue = true,
          themeAttributesInitialValue = true
        ),
        selectAssetAction,
        updateResourceCallback
      )
  }
}

/**
 * Gets a [Configuration] in a background thread for the given facet. If the given file has its own configuration, that'll be used instead.
 */
private fun getConfiguration(facet: AndroidFacet, contextFile: VirtualFile? = null): CompletableFuture<Configuration?> =
  CompletableFuture.supplyAsync(Supplier {
    val configManager = ConfigurationManager.getOrCreateInstance(facet)
    var configuration: Configuration? = null
    contextFile?.let {
      configuration = configManager.getConfiguration(contextFile)
    }
    if (configuration == null) {
      facet.module.project.projectFile?.let { projectFile ->
        configuration = configManager.getConfiguration(projectFile)
      }
    }
    return@Supplier configuration
  }, AppExecutorUtil.getAppExecutorService())

/**
 * Initializes the [ResourceResolver] in a background thread.
 *
 * @param facet The current [AndroidFacet], used to fallback to get a [ResourceResolver] in case [configurationFuture] cannot provide a
 * [Configuration].
 * @param configurationFuture A [CompletableFuture] that may return a [Configuration], if it does, it'll get the [ResourceResolver] from it.
 */
private fun getResourceResolver(
  facet: AndroidFacet,
  configurationFuture: CompletableFuture<Configuration?>
): CompletableFuture<ResourceResolver> {
  return configurationFuture.thenApplyAsync<ResourceResolver>(Function { configuration ->
    configuration?.let { return@Function it.resourceResolver }
    val configurationManager = ConfigurationManager.getOrCreateInstance(facet)
    val manifest = MergedManifestManager.getMergedManifestSupplier(facet.module).get().get() // Don't care if we block here.
    val theme = manifest.manifestTheme ?: manifest.getDefaultTheme(null, null, null)
    val target = configurationManager.highestApiTarget?.let { StudioEmbeddedRenderTarget.getCompatibilityTarget(it) }
    return@Function configurationManager.resolverCache.getResourceResolver(target, theme, FolderConfiguration.createDefault())
  }, AppExecutorUtil.getAppExecutorService())
}