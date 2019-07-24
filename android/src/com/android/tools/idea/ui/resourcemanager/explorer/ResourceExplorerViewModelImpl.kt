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

import com.android.ide.common.repository.GradleCoordinate
import com.android.ide.common.resources.ResourceItem
import com.android.resources.FolderTypeRelationship
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceType
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.res.ResourceNotificationManager
import com.android.tools.idea.res.ResourceRepositoryManager
import com.android.tools.idea.res.getFolderType
import com.android.tools.idea.resources.aar.AarResourceRepository
import com.android.tools.idea.ui.resourcemanager.ImageCache
import com.android.tools.idea.ui.resourcemanager.model.Asset
import com.android.tools.idea.ui.resourcemanager.model.FilterOptions
import com.android.tools.idea.ui.resourcemanager.model.ResourceAssetSet
import com.android.tools.idea.ui.resourcemanager.model.ResourceDataManager
import com.android.tools.idea.ui.resourcemanager.rendering.AssetPreviewManager
import com.android.tools.idea.ui.resourcemanager.rendering.AssetPreviewManagerImpl
import com.android.tools.idea.ui.resourcemanager.model.FilterOptionsParams
import com.android.tools.idea.ui.resourcemanager.rendering.getReadableConfigurations
import com.android.tools.idea.ui.resourcemanager.rendering.getReadableValue
import com.android.tools.idea.util.androidFacet
import com.android.utils.usLocaleCapitalize
import com.intellij.codeInsight.navigation.NavigationUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.xml.XmlFileImpl
import com.intellij.ui.speedSearch.SpeedSearch
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.update.MergingUpdateQueue
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.util.AndroidUtils
import java.util.concurrent.CompletableFuture
import java.util.function.Supplier
import kotlin.math.pow
import kotlin.properties.Delegates

private const val NO_VALUE = "No value"
private const val UNRESOLVED_VALUE = "Could not resolve"

/**
 * ViewModel for [com.android.tools.idea.ui.resourcemanager.view.ResourceExplorerView]
 * to manage resources in the provided [facet].
 *
 * @param filterInitialParams Sets the initial values for the filter options.
 * @param selectAssetAction Optional callback for asset selection, default behavior opens the asset's file.
 * @param supportedResourceTypes Resources to be displayed, each index should represent each resource type in the tabbed pane.
 */
class ResourceExplorerViewModelImpl(
  facet: AndroidFacet,
  filterInitialParams: FilterOptionsParams,
  private val supportedResourceTypes: Array<ResourceType>,
  selectAssetAction: ((asset: Asset) -> Unit)? = null
) : Disposable, ResourceExplorerViewModel {
  /**
   * callback called when the resource model have change. This happen when the facet is changed.
   */
  override var resourceChangedCallback: (() -> Unit)? = null

  override var facetUpdaterCallback: ((facet: AndroidFacet) -> Unit)? = null

  override var resourceTypeUpdaterCallback: ((resourceType: ResourceType) -> Unit)? = null

  override var facet by Delegates.observable(facet) { _, oldFacet, newFacet -> facetUpdated(newFacet, oldFacet) }

  private var resourceVersion: ResourceNotificationManager.ResourceVersion? = null

  private val resourceNotificationManager = ResourceNotificationManager.getInstance(facet.module.project)

  private val dataManager = ResourceDataManager(facet)

  private val listViewImageCache = ImageCache(
    mergingUpdateQueue = MergingUpdateQueue("queue", 1000, true, MergingUpdateQueue.ANY_COMPONENT, this, null, false))

  private val resourceNotificationListener = ResourceNotificationManager.ResourceChangeListener { reason ->
    if (reason.size == 1 && reason.contains(ResourceNotificationManager.Reason.EDIT)) {
      // We don't want to update all resources for every resource file edit.
      // TODO cache the resources, notify the view to only update the rendering of the edited resource.
      return@ResourceChangeListener
    }
    val currentVersion = resourceNotificationManager.getCurrentVersion(facet, null, null)
    if (resourceVersion == currentVersion) {
      return@ResourceChangeListener
    }
    resourceVersion = currentVersion
    resourceChangedCallback?.invoke()
  }

  /**
   * The index in [resourceTypes] of the resource type being used.
   */
  override var resourceTypeIndex = 0
    set(value) {
      if (field != value &&
          value in 0 until resourceTypes.size) {
        field = value
        resourceTypeUpdaterCallback?.invoke(resourceTypes[field])
        resourceChangedCallback?.invoke()
      }
    }

  override val resourceTypes: Array<ResourceType> get() = supportedResourceTypes

  override val selectedTabName: String get() = resourceTypes[resourceTypeIndex].displayName

  override val speedSearch = SpeedSearch(true)

  override val filterOptions = FilterOptions.create(
    { resourceChangedCallback?.invoke() },
    { speedSearch.updatePattern(it) },
    filterInitialParams)

  init {
    subscribeListener(facet)
    Disposer.register(this, listViewImageCache)
  }

  override var assetPreviewManager: AssetPreviewManager = AssetPreviewManagerImpl(facet, listViewImageCache)

  /**
   * TODO: Use [assetPreviewManager] instead.
   * Doing it this way since otherwise there's a bigger delay to get the high quality image on the screen if there's a low quality image in
   * place (from the cache used for [assetPreviewManager]), among other ui issues.
   */
  override val summaryPreviewManager: AssetPreviewManager by lazy {
    AssetPreviewManagerImpl(
      facet,
      ImageCache(
        maximumCapacity = (10 * 1024.0.pow(2)).toLong(), // 10 MB
        mergingUpdateQueue = MergingUpdateQueue("queue",
                                                1000,
                                                true,
                                                MergingUpdateQueue.ANY_COMPONENT,
                                                this,
                                                null,
                                                false)
      ).also { cache ->
        Disposer.register(this, cache)
      })
  }

  override fun facetUpdated(newFacet: AndroidFacet, oldFacet: AndroidFacet) {
    if (newFacet == oldFacet) return
    assetPreviewManager = AssetPreviewManagerImpl(newFacet, listViewImageCache)
    unsubscribeListener(oldFacet)
    subscribeListener(newFacet)
    dataManager.facet = newFacet
    resourceChangedCallback?.invoke()
    facetUpdaterCallback?.invoke(newFacet)
  }

  private fun subscribeListener(facet: AndroidFacet) {
    resourceNotificationManager
      .addListener(resourceNotificationListener, facet, null, null)
  }

  private fun unsubscribeListener(oldFacet: AndroidFacet) {
    resourceNotificationManager
      .removeListener(resourceNotificationListener, oldFacet, null, null)
  }

  private fun getModuleResources(forFacet: AndroidFacet, type: ResourceType): ResourceSection {
    val moduleRepository = ResourceRepositoryManager.getModuleResources(forFacet)
    val sortedResources = moduleRepository.namespaces
      .flatMap { namespace -> moduleRepository.getResources(namespace, type).values() }
      .sortedBy { it.name }
    return createResourceSection(forFacet.module.name, sortedResources)
  }

  /**
   * Returns a list of local module and their resources that the current module depends on.
   */
  private fun getDependentModuleResources(forFacet: AndroidFacet, type: ResourceType): List<ResourceSection> {
    return AndroidUtils.getAndroidResourceDependencies(forFacet.module).asSequence()
      .flatMap { dependentFacet ->
        val moduleRepository = ResourceRepositoryManager.getModuleResources(dependentFacet)
        moduleRepository.namespaces.asSequence()
          .map { namespace -> moduleRepository.getResources(namespace, type).values() }
          .filter { it.isNotEmpty() }
          .map {
            createResourceSection(dependentFacet.module.name, it.sortedBy(ResourceItem::getName))
          }
      }.toList()
  }

  /**
   * Returns a map from the library name to its resource items
   */
  private fun getLibraryResources(forFacet: AndroidFacet, type: ResourceType): List<ResourceSection> {
    val repoManager = ResourceRepositoryManager.getInstance(forFacet)
    return repoManager.libraryResources.asSequence()
      .flatMap { lib ->
        // Create a section for each library
        lib.namespaces.asSequence()
          .map { namespace -> lib.getResources(namespace, type).values() }
          .filter { it.isNotEmpty() }
          .map {
            createResourceSection(
              userReadableLibraryName(lib), it.sortedBy(ResourceItem::getName))
          }
      }
      .toList()
  }

  override fun getCurrentModuleResourceLists(): CompletableFuture<List<ResourceSection>> = CompletableFuture.supplyAsync(
    Supplier {
      getResourceSections(facet, filterOptions.isShowModuleDependencies, filterOptions.isShowLibraries)
    },
    AppExecutorUtil.getAppExecutorService())

  override fun getOtherModulesResourceLists(): CompletableFuture<List<ResourceSection>> = CompletableFuture.supplyAsync(
    Supplier {
      val displayedModuleNames = mutableSetOf(facet.module.name)
      if (filterOptions.isShowModuleDependencies) {
        displayedModuleNames.addAll(AndroidUtils.getAndroidResourceDependencies(facet.module).map { it.module.name })
      }

      ModuleManager.getInstance(facet.module.project).modules.filter { module ->
        // Don't include modules that are already being displayed.
        !displayedModuleNames.contains(module.name)
      }.flatMap { module ->
        module.androidFacet?.let { getResourceSections(it, showModuleDependencies = false, showLibraries = false) } ?: emptyList()
      }
    },
    AppExecutorUtil.getAppExecutorService())


  private fun getResourceSections(forFacet: AndroidFacet, showModuleDependencies: Boolean, showLibraries: Boolean): List<ResourceSection> {
    val resourceType = resourceTypes[resourceTypeIndex]
    var resources = listOf(getModuleResources(forFacet, resourceType))
    if (showModuleDependencies) {
      resources += getDependentModuleResources(forFacet, resourceType)
    }
    if (showLibraries) {
      resources += getLibraryResources(forFacet, resourceType)
    }
    return resources
  }

  override fun getTabIndexForFile(virtualFile: VirtualFile): Int {
    val folderType = if (virtualFile.isDirectory) ResourceFolderType.getFolderType(virtualFile.name) else getFolderType(virtualFile)
    val type = folderType?.let { FolderTypeRelationship.getRelatedResourceTypes(it) }?.firstOrNull()
    return resourceTypes.indexOf(type)
  }

  override fun dispose() {
    unsubscribeListener(facet)
  }

  override fun getData(dataId: String?, selectedAssets: List<Asset>): Any? {
    return dataManager.getData(dataId, selectedAssets)
  }

  override fun getResourceSummaryMap(resourceAssetSet: ResourceAssetSet): Map<String, String> {
    val assetToPick = resourceAssetSet.getHighestDensityAsset()
    val resourceToPick = assetToPick.resourceItem

    val valueMap = mutableMapOf(
      Pair("Name", resourceAssetSet.name),
      Pair("Reference", resourceToPick.referenceToSelf.resourceUrl.toString())
    )

    if (resourceAssetSet.assets.size > 1) {
      // If there's more than one configuration, list them in the configuration map instead.
      return valueMap
    }
    val projectFile = facet.module.project.projectFile
    val resourceResolver = projectFile?.let { ConfigurationManager.getOrCreateInstance(facet).getConfiguration(projectFile).resourceResolver }
    resourceAssetSet.assets.first().let { asset ->
      dataManager.findPsiElement(asset.resourceItem)?.let { psiElement ->
        getResourceDataType(asset, psiElement).takeIf { it.isNotBlank() }?.let { dataTypeName ->
          // The data type of the resource (eg: Type: Animated vector)
          valueMap.put("Type", dataTypeName)
        }
      }

      resourceResolver?.let {
        val configuration = asset.resourceItem.getReadableConfigurations()

        valueMap.put("Configuration", configuration)
        val value = resourceResolver.resolveResValue(asset.resourceItem.resourceValue)
        // The resolved value of the resource (eg: Value: Hello World)
        valueMap.put("Value", value?.getReadableValue()?: UNRESOLVED_VALUE)
      }
    }
    return valueMap
  }

  override fun getResourceConfigurationMap(resourceAssetSet: ResourceAssetSet): Map<String, String> {
    val projectFile = facet.module.project.projectFile
    if (projectFile == null || resourceAssetSet.assets.size == 1) {
      // Don't use these values if it only contains one configuration entry.
      return emptyMap()
    }
    val resourceResolver = ConfigurationManager.getOrCreateInstance(facet).getConfiguration(projectFile).resourceResolver
    return resourceAssetSet.assets.map { asset ->
      val value = resourceResolver.resolveResValue(asset.resourceItem.resourceValue)
      var dataTypeName = ""
      dataManager.findPsiElement(asset.resourceItem)?.let { psiElement ->
        dataTypeName = getResourceDataType(asset, psiElement).takeIf { it.isNotBlank() }?.let { "${it} - " } ?: ""
      }
      Pair(asset.resourceItem.getReadableConfigurations(),
           if (value == null) UNRESOLVED_VALUE else (dataTypeName + value.getReadableValue()))
    }.toMap()
  }

  override val doSelectAssetAction: (asset: Asset) -> Unit = selectAssetAction ?: { asset ->
    val psiElement = dataManager.findPsiElement(asset.resourceItem)
    psiElement?.let { NavigationUtil.openFileWithPsiElement(it, true, true) }
  }
}

private fun createResourceSection(libraryName: String, resourceItems: List<ResourceItem>): ResourceSection {
  val designAssets = resourceItems
    .mapNotNull { Asset.fromResourceItem(it) }
    .groupBy(Asset::name)
    .map { (name, assets) -> ResourceAssetSet(name, assets) }
  return ResourceSection(libraryName, designAssets)
}

data class ResourceSection(
  val libraryName: String = "",
  val assetSets: List<ResourceAssetSet>)

private fun userReadableLibraryName(lib: AarResourceRepository) =
  lib.libraryName?.let {
    GradleCoordinate.parseCoordinateString(it)?.artifactId
  }
  ?: ""

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
  var dataTypeName = ""
  val resourceType = asset.type

  if (resourceType == ResourceType.DRAWABLE || resourceType == ResourceType.MIPMAP) {
    // For drawables and mipmaps, if they are not defined in XML, they are usually referenced as the actual file (jpg, png, svg, etc...)
    dataTypeName = resourceType.displayName + " File"
  }

  if (psiElement is XmlFileImpl) {
    psiElement.rootTag?.let { tag ->
      dataTypeName = tag.name
      // Handle package specific types (Eg: androidx.constraint.ConstraintLayout)
      dataTypeName = dataTypeName.substringAfterLast(".")
      // Handle compounded types (Eg: animated-vector)
      dataTypeName = dataTypeName.replace('-', ' ')
      dataTypeName = dataTypeName.usLocaleCapitalize()
    }
  }
  return dataTypeName;
}
