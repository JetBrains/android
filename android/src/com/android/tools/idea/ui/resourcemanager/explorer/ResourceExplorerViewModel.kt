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
import com.android.tools.idea.configurations.getAppThemeName
import com.android.tools.idea.configurations.getDefaultTheme
import com.android.tools.idea.res.ResourceNotificationManager
import com.android.tools.idea.res.getFolderType
import com.android.tools.idea.ui.resourcemanager.MANAGER_SUPPORTED_RESOURCES
import com.android.tools.idea.ui.resourcemanager.MODULE_NAME_KEY
import com.android.tools.idea.ui.resourcemanager.RES_MANAGER_PREF_KEY
import com.android.tools.idea.ui.resourcemanager.explorer.ResourceExplorerListViewModel.UpdateUiReason
import com.android.tools.idea.ui.resourcemanager.model.Asset
import com.android.tools.idea.ui.resourcemanager.model.FilterOptions
import com.android.tools.idea.ui.resourcemanager.model.FilterOptionsParams
import com.android.tools.idea.ui.resourcemanager.rendering.ImageCache
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.EdtExecutorService
import com.intellij.util.ui.update.MergingUpdateQueue
import org.jetbrains.android.dom.manifest.getPrimaryManifestXml
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.sdk.EmbeddedRenderTarget
import java.util.concurrent.CompletableFuture
import java.util.function.Function
import java.util.function.Supplier
import kotlin.properties.Delegates

private val LOG = Logger.getInstance(ResourceExplorerViewModel::class.java)

/**
 * The View Model for the [ResourceExplorerView].
 *
 * @param defaultFacet Initial [AndroidFacet]
 * @param contextFileForConfiguration A [VirtualFile] that holds a [Configuration], if given, it'll be used to get the [ResourceResolver].
 * @param supportedResourceTypes The given [ResourceType]s that will be listed in the Resource Explorer tabs.
 * @param modelState Some initial params of this view model that affect the state of the ui. E.g: Selected Resource Tab.
 * @param selectAssetAction If not null, this action will be called when double-clicking or pressing Enter on a resource.
 * @param updateResourceCallback If not null, this will be invoked whenever the selection changes, if there's more than one configuration,
 * it'll be given the highest density version.
 */
class ResourceExplorerViewModel private constructor(
  defaultFacet: AndroidFacet,
  private var contextFileForConfiguration: VirtualFile?,
  var supportedResourceTypes: Array<ResourceType>,
  private val modelState: ViewModelState,
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
    {
      updateFilterParamsInModelState()
      refreshListModel()
    },
    { updateListModelSpeedSearch(it) },
    modelState.filterParams
  )

  private val listViewImageCache = ImageCache.createImageCache(
    parentDisposable = this,
    mergingUpdateQueue = MergingUpdateQueue("queue", 1000, true, MergingUpdateQueue.ANY_COMPONENT, this, null, false))

  private val summaryImageCache = ImageCache.createImageCache(
    parentDisposable = this,
    mergingUpdateQueue = MergingUpdateQueue("queue", 1000, true, MergingUpdateQueue.ANY_COMPONENT, this, null, false))

  private var resourceVersion: ResourceNotificationManager.ResourceVersion? = null

  private val resourceNotificationManager = ResourceNotificationManager.getInstance(defaultFacet.module.project).apply {
    resourceVersion = getCurrentVersion(defaultFacet, null, null)
  }

  private val resourceNotificationListener = ResourceNotificationManager.ResourceChangeListener { reason ->
    if (reason.size == 1 && reason.contains(ResourceNotificationManager.Reason.EDIT)) {
      // We don't want to update all resources for every resource file edit.
      // TODO cache the resources, notify the view to only update the rendering of the edited resource.
      return@ResourceChangeListener
    }
    refreshOnResourcesChange()
  }

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
      selectedModuleName = newFacet.mainModule.name
      unsubscribeListener(oldFacet)
      subscribeListener(newFacet)
      facetUpdaterCallback(newFacet)
      populateResourcesCallback()
    }
  }

  private var selectedModuleName: String? = modelState.selectedModuleName
    set(value) {
      field = value
      modelState.selectedModuleName = value
    }

  var resourceTypeIndex: Int = supportedResourceTypes.indexOf(modelState.selectedResourceType)
    set(value) {
      if (value != field && supportedResourceTypes.indices.contains(value)) {
        field = value
        modelState.selectedResourceType = supportedResourceTypes[value]
        updateListModelResourceType(supportedResourceTypes[value])
        resourceTypeUpdaterCallback(supportedResourceTypes[value])
        updateResourceTabCallback()
      }
    }

  init {
    subscribeListener(defaultFacet)
  }

  /**
   * Refresh the previews of the current [listViewModel].
   */
  fun refreshPreviews() {
    listViewModel?.clearCacheForCurrentResources()
  }


  /**
   * Refreshes the current [listViewModel] if the list of resources is outdated.
   */
  internal fun refreshOnResourcesChange() {
    val currentVersion = resourceNotificationManager.getCurrentVersion(facet, null, null)
    if (resourceVersion != currentVersion) {
      resourceVersion = currentVersion
      refreshListModel()
    }
    else {
      LOG.debug("Resource update not needed")
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
            contextFileForConfiguration,
            resourceResolver,
            filterOptions,
            supportedResourceTypes[resourceTypeIndex],
            listViewImageCache,
            summaryImageCache,
            selectAssetAction,
            { assetSet ->
              updateResourceCallback?.invoke(assetSet.getHighestDensityAsset().resourceItem)
            }
          ).also {
            listViewModel = it
            it.facetUpdaterCallback = { newFacet -> this@ResourceExplorerViewModel.facet = newFacet }
            updateListModelIfNeeded()
          }
        }, EdtExecutorService.getInstance())
  }

  override fun dispose() {
    unsubscribeListener(facet)
  }

  private fun updateFilterParamsInModelState() {
    modelState.filterParams = FilterOptionsParams(
      moduleDependenciesInitialValue = filterOptions.isShowModuleDependencies,
      librariesInitialValue = filterOptions.isShowLibraries,
      androidResourcesInitialValue = filterOptions.isShowFramework,
      themeAttributesInitialValue = filterOptions.isShowThemeAttributes,
      showSampleData = filterOptions.isShowSampleData
    )
  }

  //region ListModel update functions
  private fun refreshListModel() {
    val listModel = listViewModel
    if (listModel == null) {
      refreshListModel = true
    }
    else {
      listModel.updateUiCallback?.invoke(UpdateUiReason.RESOURCES_CHANGED)
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

  private fun subscribeListener(facet: AndroidFacet) {
    resourceNotificationManager
      .addListener(resourceNotificationListener, facet, null, null)
  }

  private fun unsubscribeListener(oldFacet: AndroidFacet) {
    resourceNotificationManager
      .removeListener(resourceNotificationListener, oldFacet, null, null)
  }

  companion object {
    fun createResManagerViewModel(facet: AndroidFacet): ResourceExplorerViewModel =
      ResourceExplorerViewModel(
        facet,
        null,
        MANAGER_SUPPORTED_RESOURCES,
        ViewModelState(
          FilterOptionsParams(
            moduleDependenciesInitialValue = false,
            librariesInitialValue = false,
            showSampleData = false,
            androidResourcesInitialValue = false,
            themeAttributesInitialValue = false
          ),
          MANAGER_SUPPORTED_RESOURCES[0],
          ViewModelStateSaveParams(facet.module.project, RES_MANAGER_PREF_KEY)
        ),
        null,
        null
      )

    fun createResPickerViewModel(facet: AndroidFacet,
                                 configurationContextFile: VirtualFile?,
                                 preferredResourceTab: ResourceType,
                                 supportedResourceTypes: Array<ResourceType>,
                                 showSampleData: Boolean,
                                 showThemeAttributes: Boolean,
                                 selectAssetAction: ((asset: Asset) -> Unit)?,
                                 updateResourceCallback: ((resourceItem: ResourceItem) -> Unit)?
                                ): ResourceExplorerViewModel =
      ResourceExplorerViewModel(
        facet,
        configurationContextFile,
        supportedResourceTypes,
        ViewModelState(
          FilterOptionsParams(
            moduleDependenciesInitialValue = true,
            librariesInitialValue = true,
            showSampleData = showSampleData,
            androidResourcesInitialValue = true,
            themeAttributesInitialValue = showThemeAttributes
          ),
          preferredResourceTab
        ),
        selectAssetAction,
        updateResourceCallback
      )

  }
}

private const val FILTER_PARAMS_KEY = "FilterParams"
private const val LOCAL_MODULE_FILTER_KEY = "LocalModules"
private const val LIBRARIES_FILTER_KEY = "Libraries"
private const val FRAMEWORK_FILTER_KEY = "Framework"
private const val THEME_ATTR_FILTER_KEY = "ThemeAttributes"
private const val RESOURCE_TYPE_KEY = "ResourceType"

/**
 * Class that holds the initial state of [ResourceExplorerViewModel].
 *
 * If [saveParams] is not-null, it will save the latest changes of this state.
 */
private class ViewModelState(
  filterParams: FilterOptionsParams,
  selectedResourceType: ResourceType,
  private val saveParams: ViewModelStateSaveParams? = null,
  selectedModuleName: String? = null
) {

  private val defaultFilterParams: FilterOptionsParams = kotlin.run {
    return@run if (saveParams != null) {
      val filterKey = "${saveParams.preferencesKey}.$FILTER_PARAMS_KEY"
      val propertiesComponent = PropertiesComponent.getInstance(saveParams.project)
      val localModules = propertiesComponent.getBoolean("$filterKey.$LOCAL_MODULE_FILTER_KEY")
      val libraries = propertiesComponent.getBoolean("$filterKey.$LIBRARIES_FILTER_KEY")
      val framework = propertiesComponent.getBoolean("$filterKey.$FRAMEWORK_FILTER_KEY")
      val themeAttr = propertiesComponent.getBoolean("$filterKey.$THEME_ATTR_FILTER_KEY")
      FilterOptionsParams(
        moduleDependenciesInitialValue = localModules,
        librariesInitialValue = libraries,
        androidResourcesInitialValue = framework,
        themeAttributesInitialValue = themeAttr,
        showSampleData = filterParams.showSampleData
      )
    }
    else {
      filterParams
    }
  }

  private val defaultSelectedResourceType: ResourceType = kotlin.run {
    return@run if (saveParams != null) {
      PropertiesComponent.getInstance(saveParams.project).getValue("${saveParams.preferencesKey}.$RESOURCE_TYPE_KEY")?.let {
        ResourceType.valueOf(it)
      } ?: selectedResourceType
    }
    else {
      selectedResourceType
    }
  }

  private val defaultSelectedModuleName: String? = kotlin.run {
    return@run saveParams?.let {
      PropertiesComponent.getInstance(saveParams.project).getValue("${saveParams.preferencesKey}.$MODULE_NAME_KEY")
    } ?: selectedModuleName
  }

  var filterParams: FilterOptionsParams by Delegates.observable(defaultFilterParams) { _, _, newValue ->
    saveParams?.let {
      val filterKey = "${saveParams.preferencesKey}.$FILTER_PARAMS_KEY"
      val propertiesComponent = PropertiesComponent.getInstance(saveParams.project)
      propertiesComponent.setValue("$filterKey.$LOCAL_MODULE_FILTER_KEY", newValue.moduleDependenciesInitialValue)
      propertiesComponent.setValue("$filterKey.$LIBRARIES_FILTER_KEY", newValue.librariesInitialValue)
      propertiesComponent.setValue("$filterKey.$FRAMEWORK_FILTER_KEY", newValue.androidResourcesInitialValue)
      propertiesComponent.setValue("$filterKey.$THEME_ATTR_FILTER_KEY", newValue.themeAttributesInitialValue)
    }
  }

  var selectedResourceType: ResourceType by Delegates.observable(defaultSelectedResourceType) { _, _, newValue ->
    saveParams?.let {
      PropertiesComponent.getInstance(saveParams.project).setValue("${saveParams.preferencesKey}.$RESOURCE_TYPE_KEY", newValue.name)
    }
  }

  /**
   * Temporary persistent name of the selected module in a ResourceExplorer.
   */
  var selectedModuleName: String? by Delegates.observable(defaultSelectedModuleName) { _, _, newValue ->
    if(newValue != null) {
      saveParams?.let {
        PropertiesComponent.getInstance(saveParams.project).setValue("${saveParams.preferencesKey}.$MODULE_NAME_KEY", newValue)
      }
    }
  }
}

/**
 * Necessary parameters to save the state of [ViewModelState] on a project-level basis.
 */
private class ViewModelStateSaveParams(
  val project: Project,
  val preferencesKey: String
)

/**
 * Gets a [Configuration] in a background thread for the given facet. If the given file has its own configuration, that'll be used instead.
 */
private fun getConfiguration(facet: AndroidFacet, contextFile: VirtualFile? = null): CompletableFuture<Configuration?> =
  CompletableFuture.supplyAsync(Supplier {
    val configManager = ConfigurationManager.getOrCreateInstance(facet.module)
    var configuration: Configuration? = null
    contextFile?.let {
      configuration = configManager.getConfiguration(contextFile)
    }
    if (configuration == null) {
      runReadAction { facet.getPrimaryManifestXml() }?.let { manifestFile ->
        configuration = configManager.getConfiguration(manifestFile.virtualFile)
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
    val configurationManager = ConfigurationManager.getOrCreateInstance(facet.module)
    val theme = getApplicationTheme(facet)
    val target = configurationManager.highestApiTarget?.let { EmbeddedRenderTarget.getCompatibilityTarget(it) }
    return@Function configurationManager.resolverCache.getResourceResolver(target, theme, FolderConfiguration.createDefault())
  }, AppExecutorUtil.getAppExecutorService())
}

/**
 *  Try to get application theme from the manifest. And it falls back to the default theme if necessary.
 */
private fun getApplicationTheme(facet: AndroidFacet): String {
  val module = facet.module
  val appTheme = module.getAppThemeName()

  if (appTheme != null) {
    return appTheme
  }

  return module.getDefaultTheme(null, null, null)
}
