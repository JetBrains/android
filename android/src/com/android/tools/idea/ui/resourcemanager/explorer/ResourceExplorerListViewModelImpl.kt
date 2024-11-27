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

import com.android.SdkConstants
import com.android.ide.common.resources.ResourceItem
import com.android.ide.common.resources.ResourceResolver
import com.android.resources.ResourceType
import com.android.tools.idea.actions.OpenStringResourceEditorAction
import com.android.tools.idea.res.AndroidDependenciesCache
import com.android.tools.idea.ui.resourcemanager.explorer.ResourceExplorerListViewModel.UpdateUiReason
import com.android.tools.idea.ui.resourcemanager.findCompatibleFacets
import com.android.tools.idea.ui.resourcemanager.model.Asset
import com.android.tools.idea.ui.resourcemanager.model.DesignAsset
import com.android.tools.idea.ui.resourcemanager.model.FilterOptions
import com.android.tools.idea.ui.resourcemanager.model.ResourceAssetSet
import com.android.tools.idea.ui.resourcemanager.model.ResourceDataManager
import com.android.tools.idea.ui.resourcemanager.model.ResourceSection
import com.android.tools.idea.ui.resourcemanager.model.TypeFilter
import com.android.tools.idea.ui.resourcemanager.model.getAndroidResources
import com.android.tools.idea.ui.resourcemanager.model.getDependentModuleResources
import com.android.tools.idea.ui.resourcemanager.model.getLibraryResources
import com.android.tools.idea.ui.resourcemanager.model.getModuleResources
import com.android.tools.idea.ui.resourcemanager.model.getSampleDataResources
import com.android.tools.idea.ui.resourcemanager.model.getThemeAttributes
import com.android.tools.idea.ui.resourcemanager.model.resolveValue
import com.android.tools.idea.ui.resourcemanager.rendering.AssetPreviewManager
import com.android.tools.idea.ui.resourcemanager.rendering.AssetPreviewManagerImpl
import com.android.tools.idea.ui.resourcemanager.rendering.ImageCache
import com.android.tools.idea.ui.resourcemanager.rendering.getReadableConfigurations
import com.android.tools.idea.ui.resourcemanager.rendering.getReadableValue
import com.android.utils.usLocaleCapitalize
import com.intellij.codeInsight.navigation.openFileWithPsiElement
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiBinaryFile
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.xml.XmlFileImpl
import com.intellij.psi.util.childrenOfType
import com.intellij.psi.xml.XmlTag
import com.intellij.ui.speedSearch.SpeedSearch
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.EdtExecutorService
import org.jetbrains.android.facet.AndroidFacet
import java.util.Locale
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletableFuture.completedFuture
import java.util.concurrent.CompletableFuture.supplyAsync
import java.util.function.BiConsumer
import java.util.function.Supplier
import kotlin.properties.Delegates

private const val UNRESOLVED_VALUE = "Could not resolve"

/**
 * ViewModel for [com.android.tools.idea.ui.resourcemanager.explorer.ResourceExplorerListView]
 * to manage resources in the provided [facet].
 *
 * @param facet Starting [AndroidFacet] for the view model.
 * @param resourceResolver [ResourceResolver] that it's used to obtain most of the information for resources.
 * @param filterOptions The [FilterOptions] that defines the resources to include in [getResourceSections].
 * @param defaultResourceType The initial value of [currentResourceType].
 * @param listViewImageCache The [ImageCache] for previews in the resources list.
 * @param summaryImageCache The [ImageCache] for resource previews in the summary view.
 * @param selectAssetAction Optional callback for asset selection, default behavior opens the asset's file.
 * @param updateSelectedAssetSetCallback Optional callback, called whenever the selection is updated with the corresponding asset.
 */
class ResourceExplorerListViewModelImpl(
  override val facet: AndroidFacet,
  private val contextFile: VirtualFile?,
  private val resourceResolver: ResourceResolver,
  override val filterOptions: FilterOptions,
  defaultResourceType: ResourceType,
  private val listViewImageCache: ImageCache,
  private val summaryImageCache: ImageCache,
  selectAssetAction: ((asset: Asset) -> Unit)? = null,
  updateSelectedAssetSetCallback: ((assetSet: ResourceAssetSet) -> Unit)? = null
) : ResourceExplorerListViewModel {
  /**
   * callback called when the resource model have change. This happen when the facet is changed.
   */
  override var updateUiCallback: ((UpdateUiReason) -> Unit)? = null

  override var facetUpdaterCallback: ((facet: AndroidFacet) -> Unit)? = null

  override var currentResourceType: ResourceType by Delegates.observable(defaultResourceType) { _, oldValue, newValue ->
    if (newValue != oldValue) {
      updateUiCallback?.invoke(UpdateUiReason.RESOURCE_TYPE_CHANGED)
    }
  }

  private val dataManager = ResourceDataManager(facet)

  override val selectedTabName: String get() = currentResourceType.displayName

  override val speedSearch = SpeedSearch(true).apply {
    if (filterOptions.searchString.isNotEmpty()) {
      updatePattern(filterOptions.searchString)
    }
  }

  /** Returns actions related to the resources being displayed. These do not directly affect/interact with the [ResourceExplorerListView]. */
  override val externalActions: Collection<ActionGroup>
    get() =
      when (currentResourceType) {
        ResourceType.STRING -> listOf(DefaultActionGroup().apply {
          add(object : OpenStringResourceEditorAction() {
            override fun displayTextInToolbar() = true
          })
        })
        else -> emptyList()
      }

  override val assetPreviewManager: AssetPreviewManager = AssetPreviewManagerImpl(facet, listViewImageCache, resourceResolver, contextFile)

  /**
   * Doing it this way since otherwise there's a bigger delay to get the high quality image on the screen if there's a low quality image in
   * place (from the cache used for [assetPreviewManager]), among other ui issues.
   */
  override val summaryPreviewManager: AssetPreviewManager by lazy {
    AssetPreviewManagerImpl(facet, summaryImageCache, resourceResolver, contextFile)
  }

  override fun clearCacheForCurrentResources() {
    getCurrentModuleResourceLists().whenCompleteAsync(BiConsumer { lists, throwable ->
      if (throwable == null) {
        val designAssets = lists.flatMap { it.assetSets.flatMap { it.assets.filterIsInstance<DesignAsset>() } }
        designAssets.forEach(::clearImageCache)
        updateUiCallback?.invoke(UpdateUiReason.IMAGE_CACHE_CHANGED)
      }
    }, EdtExecutorService.getInstance())
  }

  override fun clearImageCache(asset: DesignAsset) {
    listViewImageCache.clear(asset)
  }

  override fun facetUpdated(newFacet: AndroidFacet) {
    facetUpdaterCallback?.invoke(newFacet)
  }

  override fun getCurrentModuleResourceLists(): CompletableFuture<List<ResourceSection>> = resourceExplorerSupplyAsync {
    getResourceSections(facet,
                        showModuleDependencies = filterOptions.isShowModuleDependencies,
                        showLibraries = filterOptions.isShowLibraries,
                        showSampleData = filterOptions.isShowSampleData,
                        showAndroidResources = filterOptions.isShowFramework,
                        showThemeAttributes = filterOptions.isShowThemeAttributes,
                        typeFilters = filterOptions.currentResourceTypeActiveOptions)
  }

  override fun getOtherModulesResourceLists(): CompletableFuture<List<ResourceSection>> = resourceExplorerSupplyAsync supplier@{
    val displayedModuleNames = mutableSetOf(facet.module.name)
    if (filterOptions.isShowModuleDependencies) {
      displayedModuleNames.addAll(AndroidDependenciesCache.getAndroidResourceDependencies(facet.module).map { it.module.name })
    }

    return@supplier findCompatibleFacets(facet.module.project).filter { facet ->
      // Don't include modules that are already being displayed.
      !displayedModuleNames.contains(facet.module.name)
    }.flatMap { facet ->
      getResourceSections(
        forFacet = facet,
        showModuleDependencies = false,
        showLibraries = false,
        showSampleData = false,
        showAndroidResources = false,
        showThemeAttributes = false,
        typeFilters = filterOptions.currentResourceTypeActiveOptions
      )
    }
  }


  private fun getResourceSections(forFacet: AndroidFacet,
                                  showModuleDependencies: Boolean,
                                  showLibraries: Boolean,
                                  showSampleData: Boolean,
                                  showAndroidResources: Boolean,
                                  showThemeAttributes: Boolean,
                                  typeFilters: List<TypeFilter> = emptyList()): List<ResourceSection> {
    val resourceType = currentResourceType
    val resources = mutableListOf<ResourceSection>()
    if (showSampleData) {
      resources.add(getSampleDataResources(forFacet, resourceType))
    }
    resources.add(getModuleResources(forFacet, resourceType, typeFilters))
    if (showModuleDependencies) {
      resources.addAll(getDependentModuleResources(forFacet, resourceType, typeFilters))
    }
    if (showLibraries) {
      resources.addAll(getLibraryResources(forFacet, resourceType, typeFilters))
    }
    if (showAndroidResources) {
      getAndroidResources(forFacet, resourceType, typeFilters)?.let { resources.add(it) }
    }
    if (showThemeAttributes) {
      getThemeAttributes(forFacet, resourceType, typeFilters, resourceResolver)?.let { resources.add(it) }
    }
    return resources
  }

  override fun uiDataSnapshot(sink: DataSink, selectedAssets: List<Asset>) {
    dataManager.uiDataSnapshot(sink, selectedAssets)
  }

  override fun getResourceSummaryMap(resourceAssetSet: ResourceAssetSet): CompletableFuture<Map<String, String>> {
    val assetToPick = resourceAssetSet.getHighestDensityAsset()

    val valueMap = mutableMapOf(
      Pair("Name", resourceAssetSet.name),
      Pair("Reference", assetToPick.resourceUrl.toString())
    )

    if (resourceAssetSet.assets.size > 1) {
      // If there's more than one configuration, list them in the configuration map instead.
      return completedFuture(valueMap.toMap())
    }
    return resourceExplorerSupplyAsync {
      resourceAssetSet.assets.first().let { asset ->
        val value = resourceResolver.resolveValue(asset)
        val resolvedResource = (value as? ResourceItem) ?: asset.resourceItem
        runReadAction {
          dataManager.findPsiElement(resolvedResource)?.let { psiElement ->
            getResourceDataType(asset, psiElement).takeIf { it.isNotBlank() }?.let { dataTypeName ->
              // The data type of the resource (eg: Type: Animated vector)
              valueMap["Type"] = dataTypeName
            }
          }
        }
        val configuration = asset.resourceItem.getReadableConfigurations()
        valueMap["Configuration"] = configuration
        // The resolved value of the resource (eg: Value: Hello World)
        valueMap["Value"] = value?.getReadableValue() ?: UNRESOLVED_VALUE
      }
      return@resourceExplorerSupplyAsync valueMap.toMap()
    }
  }

  override fun getResourceConfigurationMap(resourceAssetSet: ResourceAssetSet): CompletableFuture<Map<String, String>> {
    if (resourceAssetSet.assets.size == 1) {
      // Don't use these values if it only contains one configuration entry.
      return completedFuture(emptyMap<String, String>())
    }
    return resourceExplorerSupplyAsync {
      return@resourceExplorerSupplyAsync resourceAssetSet.assets.map { asset ->
        val value = resourceResolver.resolveValue(asset)
        val resolvedResource = (value as? ResourceItem) ?: asset.resourceItem
        var dataTypeName = ""
        runReadAction {
          dataManager.findPsiElement(resolvedResource)?.let { psiElement ->
            dataTypeName = getResourceDataType(asset, psiElement).takeIf { it.isNotBlank() }?.let { "${it} - " } ?: ""
          }
        }
        Pair(asset.resourceItem.getReadableConfigurations(),
             if (value == null) UNRESOLVED_VALUE else (dataTypeName + value.getReadableValue()))
      }.toMap()
    }
  }

  override val doSelectAssetAction: (asset: Asset) -> Unit = selectAssetAction ?: { asset ->
    val psiElement = dataManager.findPsiElement(asset.resourceItem)
    psiElement?.let { openFileWithPsiElement(it, true, true) }
  }

  override val updateSelectedAssetSet: (assetSet: ResourceAssetSet) -> Unit = {
    updateSelectedAssetSetCallback?.invoke(it)
  }

  private val FilterOptions.currentResourceTypeActiveOptions get () = typeFiltersModel.getActiveFilters(currentResourceType)
}

/**
 * Common wrapper for methods that returns resource information in a [CompletableFuture]. Makes sure the method is run in a background
 * thread for long-running operations.
 */
private fun <T>resourceExplorerSupplyAsync(runnable: () -> T): CompletableFuture<T> =
  supplyAsync<T>(Supplier<T> {
    runnable()
  }, AppExecutorUtil.getAppExecutorService())

/**
 * For a resolved resource, returns the readable name of the declared resource data type. This is usually the root of the element defined in
 * the XML.
 *
 * For unknown/unhandled data types (eg: resource is defined as a png file instead of xml) it may return a default name for that
 * [ResourceType]. Eg: for a foo.png drawable return "Drawable File"
 *
 * Eg: for <animated-vector></animated-vector> returns "Animated vector"
 */
private fun getResourceDataType(asset: Asset, psiElement: PsiElement): String {
  val resourceType = asset.type

  return when {
    psiElement is XmlFileImpl -> {
      // Check first if it's an XmlFile and get the root tag
      var prefix = ""
      var name = ""
      psiElement.rootTag?.let { tag ->
        if (tag.name == SdkConstants.TAG_LAYOUT) {
          // For data binding layouts we look for the non-data tag.
          prefix = "Data Binding"
          tag.childrenOfType<XmlTag>().firstOrNull { it.name != SdkConstants.TAG_DATA }?.let { name = it.name }
        }
        else {
          name = tag.name
        }
        // Handle package specific types (Eg: androidx.constraint.ConstraintLayout)
        name = name.substringAfterLast(".")
        // Handle compounded types (Eg: animated-vector)
        name = name.replace('-', ' ')
        name = name.usLocaleCapitalize()
      }
      return if (prefix.isNotEmpty()) "$prefix ($name)" else name
    }
    // If it's not defined in XML, they are usually referenced as the actual file extension (jpg, png, webp, etc...)
    psiElement is PsiBinaryFile && psiElement.virtualFile.extension != null -> {
      if (psiElement.virtualFile.name.endsWith(SdkConstants.DOT_9PNG, true)) {
        "9-Patch"
      } else {
        psiElement.virtualFile.extension?.uppercase(Locale.US) ?: ""
      }
    }
    // Fallback for unsupported types in Drawables and Mip Maps
    resourceType == ResourceType.DRAWABLE || resourceType == ResourceType.MIPMAP -> resourceType.displayName + " File"
    else -> ""
  }
}