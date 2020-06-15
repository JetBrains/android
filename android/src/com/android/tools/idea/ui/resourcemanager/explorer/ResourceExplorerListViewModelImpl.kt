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
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.StyleItemResourceValue
import com.android.ide.common.repository.GradleCoordinate
import com.android.ide.common.resources.ResourceItem
import com.android.ide.common.resources.ResourceItemWithVisibility
import com.android.ide.common.resources.ResourceRepository
import com.android.ide.common.resources.ResourceResolver
import com.android.ide.common.resources.ResourceVisitor
import com.android.resources.ResourceType
import com.android.resources.ResourceVisibility
import com.android.tools.idea.actions.OpenStringResourceEditorAction
import com.android.tools.idea.configurations.Configuration
import com.android.tools.idea.editors.theme.ResolutionUtils
import com.android.tools.idea.res.ResourceRepositoryManager
import com.android.tools.idea.res.SampleDataResourceItem
import com.android.tools.idea.resources.aar.AarResourceRepository
import com.android.tools.idea.ui.resourcemanager.ImageCache
import com.android.tools.idea.ui.resourcemanager.explorer.ResourceExplorerListViewModel.UpdateUiReason
import com.android.tools.idea.ui.resourcemanager.model.Asset
import com.android.tools.idea.ui.resourcemanager.model.DesignAsset
import com.android.tools.idea.ui.resourcemanager.model.FilterOptions
import com.android.tools.idea.ui.resourcemanager.model.ResourceAssetSet
import com.android.tools.idea.ui.resourcemanager.model.ResourceDataManager
import com.android.tools.idea.ui.resourcemanager.model.TypeFilter
import com.android.tools.idea.ui.resourcemanager.model.TypeFilterKind
import com.android.tools.idea.ui.resourcemanager.model.resolveValue
import com.android.tools.idea.ui.resourcemanager.rendering.AssetPreviewManager
import com.android.tools.idea.ui.resourcemanager.rendering.AssetPreviewManagerImpl
import com.android.tools.idea.ui.resourcemanager.rendering.getReadableConfigurations
import com.android.tools.idea.ui.resourcemanager.rendering.getReadableValue
import com.android.tools.idea.util.androidFacet
import com.android.utils.usLocaleCapitalize
import com.intellij.codeInsight.navigation.NavigationUtil
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.psi.PsiBinaryFile
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.xml.XmlFileImpl
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.ui.speedSearch.SpeedSearch
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.util.AndroidUtils
import org.jetbrains.plugins.groovy.lang.psi.util.childrenOfType
import java.util.Locale
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletableFuture.completedFuture
import java.util.concurrent.CompletableFuture.supplyAsync
import java.util.function.Supplier
import kotlin.properties.Delegates

private const val UNRESOLVED_VALUE = "Could not resolve"

/**
 * ViewModel for [com.android.tools.idea.ui.resourcemanager.explorer.ResourceExplorerListView]
 * to manage resources in the provided [facet].
 *
 * @param facet Starting [AndroidFacet] for the view model.
 * @param configuration The [Configuration] used to obtain Theme Attributes.
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
  private val configuration: Configuration?,
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

  override val assetPreviewManager: AssetPreviewManager = AssetPreviewManagerImpl(facet, listViewImageCache, resourceResolver)

  /**
   * Doing it this way since otherwise there's a bigger delay to get the high quality image on the screen if there's a low quality image in
   * place (from the cache used for [assetPreviewManager]), among other ui issues.
   */
  override val summaryPreviewManager: AssetPreviewManager by lazy {
    AssetPreviewManagerImpl(facet, summaryImageCache, resourceResolver)
  }

  override fun clearImageCache(asset: DesignAsset) {
    listViewImageCache.clear(asset)
  }

  override fun facetUpdated(newFacet: AndroidFacet) {
    facetUpdaterCallback?.invoke(newFacet)
  }

  private fun getModuleResources(forFacet: AndroidFacet, type: ResourceType, typeFilters: List<TypeFilter>): ResourceSection {
    val moduleRepository = ResourceRepositoryManager.getModuleResources(forFacet)
    val sortedResources = moduleRepository.namespaces.flatMap { namespace ->
      moduleRepository.getResourcesAndApplyFilters(namespace, type, true, typeFilters)
    }.sortedBy { it.name }

    return createResourceSection(forFacet.module.name, sortedResources)
  }

  /**
   * Returns a list of local module and their resources that the current module depends on.
   */
  private fun getDependentModuleResources(forFacet: AndroidFacet,
                                          type: ResourceType,
                                          typeFilters: List<TypeFilter>): List<ResourceSection> {
    return AndroidUtils.getAndroidResourceDependencies(forFacet.module).asSequence()
      .flatMap { dependentFacet ->
        val moduleRepository = ResourceRepositoryManager.getModuleResources(dependentFacet)
        moduleRepository.namespaces.asSequence()
          .map { namespace -> moduleRepository.getResourcesAndApplyFilters(namespace, type, true, typeFilters) }
          .filter { it.isNotEmpty() }
          .map { createResourceSection(dependentFacet.module.name, it.sortedBy(ResourceItem::getName)) }
      }.toList()
  }

  /**
   * Returns a map from the library name to its resource items
   */
  private fun getLibraryResources(forFacet: AndroidFacet, type: ResourceType, typeFilters: List<TypeFilter>): List<ResourceSection> {
    val repoManager = ResourceRepositoryManager.getInstance(forFacet)
    return repoManager.libraryResources
      .flatMap { lib ->
        // Create a section for each library
        lib.namespaces.asSequence()
          .map { namespace -> return@map lib.getResourcesAndApplyFilters(namespace, type, false, typeFilters) }
          .filter { it.isNotEmpty() }
          .map { createResourceSection(userReadableLibraryName(lib), it.sortedBy(ResourceItem::getName)) }.toList()
      }
  }

  /** Returns [ResourceType.SAMPLE_DATA] resources that match the content type of the requested [type]. E.g: Images for Drawables. */
  private fun getSampleDataResources(forFacet: AndroidFacet, type: ResourceType): ResourceSection {
    val repoManager = ResourceRepositoryManager.getInstance(forFacet).appResources
    val resources = repoManager.namespaces.flatMap { namespace ->
      repoManager.getResources(namespace, ResourceType.SAMPLE_DATA) { sampleItem ->
        if (sampleItem is SampleDataResourceItem) {
          when (type) {
            ResourceType.SAMPLE_DATA -> true
            ResourceType.DRAWABLE -> sampleItem.contentType == SampleDataResourceItem.ContentType.IMAGE
            ResourceType.STRING -> sampleItem.contentType != SampleDataResourceItem.ContentType.IMAGE
            else -> false
          }
        }
        else false
      }
    }
    return createResourceSection(ResourceType.SAMPLE_DATA.displayName, resources)
  }

  /**
   * Returns a [ResourceSection] of the Android Framework resources.
   */
  private fun getAndroidResources(forFacet: AndroidFacet, type: ResourceType, typeFilters: List<TypeFilter>): ResourceSection? {
    val repoManager = ResourceRepositoryManager.getInstance(forFacet)
    val languages = if (type == ResourceType.STRING) repoManager.languagesInProject else emptySet<String>()
    val frameworkRepo = repoManager.getFrameworkResources(languages) ?: return null
    val resources = frameworkRepo.namespaces.flatMap { namespace ->
      return@flatMap frameworkRepo.getResourcesAndApplyFilters(namespace, type, false, typeFilters)
    }.sortedBy { it.name }
    return createResourceSection(SdkConstants.ANDROID_NS_NAME, resources)
  }

  /**
   * Returns a [ResourceSection] of attributes ([ResourceType.ATTR]) from the theme defined either in the module's manifest or from the
   * configuration in the current file (e.g: the theme defined in the Layout Editor).
   *
   * However, this approach means that it might not display some attributes that are expected to be used in a layout if the configuration is
   * not set up correctly.
   */
  private fun getThemeAttributes(forFacet: AndroidFacet, type: ResourceType, typeFilters: List<TypeFilter>): ResourceSection? {
    if (configuration == null) return null

    val projectThemeAttributes =
      ResourceRepositoryManager.getInstance(forFacet).projectResources.let { resourceRepository ->
        resourceRepository.getNonPrivateAttributeResources(resourceRepository.namespaces.toList(),
                                                           resourceResolver,
                                                           configuration,
                                                           type,
                                                           typeFilters)
      }
    val libraryThemeAttributes = hashMapOf<String, ResourceItem>()
    ResourceRepositoryManager.getInstance(forFacet).libraryResources.forEach { resourceRepository ->
      resourceRepository.getNonPrivateAttributeResources(resourceRepository.namespaces.toList(),
                                                         resourceResolver,
                                                         configuration,
                                                         type,
                                                         typeFilters).let { libraryThemeAttributes.putAll(it) }
    }
    // Framework resources should have visibility properly defined. So we only get the public ones.
    val frameworkResources = configuration.frameworkResources
    val androidThemeAttributes = frameworkResources?.let {
      frameworkResources.getPublicOnlyAttributeResources(listOf(ResourceNamespace.ANDROID),
                                                         resourceResolver,
                                                         configuration,
                                                         type,
                                                         typeFilters)
    } ?: hashMapOf()

    // If any attributes are repeated, override them.
    // Project attributes takes precedence over external libraries attributes.
    // TODO: Eventually we'd want to give the user the option to use the namespace they want for overridden attributes.
    //  Eg: Choose '?attr/textColorPrimary' or '?android:attr/textColorPrimary'
    val themeAttributesMap: HashMap<String, ResourceItem> = androidThemeAttributes
    themeAttributesMap.putAll(libraryThemeAttributes)
    themeAttributesMap.putAll(projectThemeAttributes)
    val themeAttributes = themeAttributesMap.values.sortedBy { it.name }

    return createResourceSection("Theme attributes", themeAttributes, type)
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

  override fun getOtherModulesResourceLists(): CompletableFuture<List<ResourceSection>> = resourceExplorerSupplyAsync {
    val displayedModuleNames = mutableSetOf(facet.module.name)
    if (filterOptions.isShowModuleDependencies) {
      displayedModuleNames.addAll(AndroidUtils.getAndroidResourceDependencies(facet.module).map { it.module.name })
    }

    ModuleManager.getInstance(facet.module.project).modules.filter { module ->
      // Don't include modules that are already being displayed.
      !displayedModuleNames.contains(module.name)
    }.flatMap { module ->
      module.androidFacet?.let {
        getResourceSections(it,
                            showModuleDependencies = false,
                            showLibraries = false,
                            showSampleData = false,
                            showAndroidResources = false,
                            showThemeAttributes = false,
                            typeFilters = filterOptions.currentResourceTypeActiveOptions)
      } ?: emptyList()
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
      getThemeAttributes(forFacet, resourceType, typeFilters)?.let { resources.add(it) }
    }
    return resources
  }

  override fun getData(dataId: String?, selectedAssets: List<Asset>): Any? {
    return dataManager.getData(dataId, selectedAssets)
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
    psiElement?.let { NavigationUtil.openFileWithPsiElement(it, true, true) }
  }

  override val updateSelectedAssetSet: (assetSet: ResourceAssetSet) -> Unit = {
    updateSelectedAssetSetCallback?.invoke(it)
  }

  private fun ResourceRepository.getResourcesAndApplyFilters(namespace: ResourceNamespace,
                                                             type: ResourceType,
                                                             isLocalRepo: Boolean,
                                                             typeFilters: List<TypeFilter>): Collection<ResourceItem> {
    if (isLocalRepo) {
      if (typeFilters.isEmpty()) {
        return getResources(namespace, type).values()
      }
      else {
        return getResources(namespace, type) { resourceItem -> resourceItem.isValidForFilters(typeFilters) }
      }
    }
    else {
      // Only public resources for external resources.
      val publicResources = getPublicResources(namespace, type)
      if (typeFilters.isEmpty()) {
        return publicResources
      }
      else {
        return publicResources.filter { resourceItem -> resourceItem.isValidForFilters(typeFilters) }
      }
    }
  }

  private fun ResourceItem.isValidForFilters(typeFilters: List<TypeFilter>): Boolean {
    val psiElement = runReadAction { dataManager.findPsiElement(this) } ?: return false
    when(psiElement) {
      is XmlFile -> {
        val tag = runReadAction { psiElement.rootTag } ?: return false
        // To verify XML Tag filters, we just compare the XML root tag value to the filter value, but unless we are intentionally filtering
        // for data-binding layouts, we then take the first non-data XML tag.
        typeFilters.forEach { filter ->
          if (filter.kind == TypeFilterKind.XML_TAG) {
            if (tag.name == filter.value) {
              return true
            } else if (tag.name == SdkConstants.TAG_LAYOUT) {
              // Is data-binding, look for the non-data tag.
              val dataBindingViewTag = tag.childrenOfType<XmlTag>().firstOrNull { it.name != SdkConstants.TAG_DATA } ?: return false
              if (dataBindingViewTag.name == filter.value) {
                return true
              }
            }
          }
          if (filter.kind == TypeFilterKind.XML_TAG && tag.name == filter.value ) {
            return true
          }
        }
        return false
      }
      is PsiBinaryFile -> {
        val name = psiElement.name
        // To verify File filters, we look for the file extension, but we take the extension as the string after the first '.', since the
        // VirtualFile#getExtension method does not consider '.9.png' as an extension (returns 'png').
        typeFilters.forEach { filter ->
          val isFileFilter = filter.kind == TypeFilterKind.FILE
          val extension = name.substring(name.indexOf('.'))
          if (isFileFilter && extension.equals(filter.value, true)) {
            return true
          }
        }
        return false
      }
      else -> return false
    }
  }

  /** Returns only the attributes in a [ResourceRepository] that are explicitly [ResourceVisibility.PUBLIC]. */
  private fun ResourceRepository.getPublicOnlyAttributeResources(namespaces: List<ResourceNamespace>,
                                                                 resourceResolver: ResourceResolver,
                                                                 configuration: Configuration,
                                                                 targetType: ResourceType,
                                                                 typeFilters: List<TypeFilter>): HashMap<String, ResourceItem> {
    return getAttributeResources(namespaces, resourceResolver, configuration, targetType, typeFilters) {
      return@getAttributeResources (it as ResourceItemWithVisibility).visibility == ResourceVisibility.PUBLIC
    }
  }

  /**
   * Returns the attributes in a [ResourceRepository] that are not explicitly [ResourceVisibility.PRIVATE]. So it's assumed that if they are
   * not [ResourceVisibility.PRIVATE] they're as good as public since visibility is not always specified.
   */
  private fun ResourceRepository.getNonPrivateAttributeResources(namespaces: List<ResourceNamespace>,
                                                                 resourceResolver: ResourceResolver,
                                                                 configuration: Configuration,
                                                                 targetType: ResourceType,
                                                                 typeFilters: List<TypeFilter>): HashMap<String, ResourceItem> {
    return getAttributeResources(namespaces, resourceResolver, configuration, targetType, typeFilters) {
      return@getAttributeResources if (it is ResourceItemWithVisibility) {
        it.visibility != ResourceVisibility.PRIVATE
      }
      else true
    }
  }

  /**
   * Common function to extract theme attributes resources. Returns a map of the resource name and the actual [ResourceItem].
   *
   * Returns a map since it's expected for some of these attributes to be overridden from different [ResourceRepository]s. The map
   * simplifies that process.
   */
  private fun ResourceRepository.getAttributeResources(namespaces: List<ResourceNamespace>,
                                                       resourceResolver: ResourceResolver,
                                                       configuration: Configuration,
                                                       targetType: ResourceType,
                                                       typeFilters: List<TypeFilter>,
                                                       visibilityFilter: (ResourceItem) -> Boolean): HashMap<String, ResourceItem> {

    val attributesMap = hashMapOf<String, ResourceItem>()
    accept { resourceItem ->
      if (resourceItem.type == ResourceType.ATTR && namespaces.contains(resourceItem.namespace) && visibilityFilter(resourceItem)) {
        resourceResolver.findItemInTheme(resourceItem.referenceToSelf)?.let { attributeValue ->
          if (attributeValue is StyleItemResourceValue && (ResolutionUtils.getAttrType(attributeValue, configuration) == targetType)) {
            if (typeFilters.isNotEmpty()) {
              // Should be filtered
              val resolvedThemeAttribute = resourceResolver.resolveResValue(attributeValue)
              if (resolvedThemeAttribute is ResourceItem && resolvedThemeAttribute.isValidForFilters(typeFilters)) {
                attributesMap[resourceItem.name] = resourceItem
              }
            }
            else {
              attributesMap[resourceItem.name] = resourceItem
            }
          }
        }
      }
      return@accept ResourceVisitor.VisitResult.CONTINUE
    }
    return attributesMap
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

private fun createResourceSection(libraryName: String, resourceItems: List<ResourceItem>): ResourceSection {
  val designAssets = resourceItems
    .mapNotNull { Asset.fromResourceItem(it) }
    .groupBy(Asset::name)
    // TODO: Add an 'indexToPreview' or 'assetToPreview' value for ResourceAssetSet, instead of previewing the first asset by default.
    .map { (name, assets) -> ResourceAssetSet(name, assets) }
  return ResourceSection(libraryName, designAssets)
}

/** Creates a [ResourceSection] forcing the displayed [ResourceType]. E.g: Attributes resources may represent other type of resources. */
private fun createResourceSection(libraryName: String, resourceItems: List<ResourceItem>, resourceType: ResourceType): ResourceSection {
  val designAssets = resourceItems
    .mapNotNull { Asset.fromResourceItem(it, resourceType) }
    .groupBy(Asset::name)
    .map { (name, assets) -> ResourceAssetSet(name, assets) }
  return ResourceSection(libraryName, designAssets)
}

data class ResourceSection(
  val libraryName: String = "",
  val assetSets: List<ResourceAssetSet>)

private fun userReadableLibraryName(lib: AarResourceRepository) =
  lib.libraryName?.let {
    GradleCoordinate.parseCoordinateString(it)?.artifactId ?: it
  }
  ?: "library - failed name"

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
        psiElement.virtualFile.extension?.toUpperCase(Locale.US) ?: ""
      }
    }
    // Fallback for unsupported types in Drawables and Mip Maps
    resourceType == ResourceType.DRAWABLE || resourceType == ResourceType.MIPMAP -> resourceType.displayName + " File"
    else -> ""
  }
}