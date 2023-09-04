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
package com.android.tools.idea.ui.resourcemanager

import com.android.ide.common.resources.ResourceItem
import com.android.resources.ResourceType
import com.android.tools.idea.help.AndroidWebHelpProvider
import com.android.tools.idea.ui.resourcemanager.explorer.ResourceExplorerToolbar
import com.android.tools.idea.ui.resourcemanager.explorer.ResourceExplorerToolbarViewModel
import com.android.tools.idea.ui.resourcemanager.explorer.ResourceExplorerView
import com.android.tools.idea.ui.resourcemanager.explorer.ResourceExplorerViewModel
import com.android.tools.idea.ui.resourcemanager.importer.ImportersProvider
import com.android.tools.idea.ui.resourcemanager.importer.ResourceImportDragTarget
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ui.JBUI
import org.jetbrains.android.facet.AndroidFacet
import java.awt.BorderLayout
import javax.swing.JPanel
import kotlin.properties.Delegates

internal const val RES_MANAGER_PREF_KEY = "ResourceManagerPrefKey"

internal val MANAGER_SUPPORTED_RESOURCES
  get() =
    arrayOf(ResourceType.DRAWABLE, ResourceType.COLOR, ResourceType.LAYOUT, ResourceType.MIPMAP,
            ResourceType.STRING, ResourceType.NAVIGATION, ResourceType.ANIM, ResourceType.ANIMATOR,
            ResourceType.INTERPOLATOR, ResourceType.TRANSITION, ResourceType.FONT, ResourceType.MENU,
            ResourceType.STYLE, ResourceType.ARRAY, ResourceType.BOOL, ResourceType.DIMEN,
            ResourceType.FRACTION, ResourceType.INTEGER, ResourceType.PLURALS, ResourceType.XML)

internal val RESOURCE_DEBUG = System.getProperty("res.manag.debug", "false")?.toBoolean() ?: false

private val LOG = Logger.getInstance(ResourceExplorer::class.java)

/**
 * The resource explorer lets the user browse resources from the provided [AndroidFacet]
 */
class ResourceExplorer private constructor(
  facet: AndroidFacet,
  private val resourceExplorerViewModel: ResourceExplorerViewModel,
  private val resourceExplorerView: ResourceExplorerView,
  private val toolbarViewModel: ResourceExplorerToolbarViewModel,
  private val toolbar: ResourceExplorerToolbar,
  private val resourceImportDragTarget: ResourceImportDragTarget)
  : JPanel(BorderLayout()), Disposable, DataProvider {

  var facet by Delegates.observable(facet) { _, _, newValue -> updateFacet(newValue) }

  init {
    toolbarViewModel.facetUpdaterCallback = { newValue -> this.facet = newValue }
    toolbarViewModel.resourceUpdaterCallback = { name, type -> selectAsset(name, type) }
    toolbarViewModel.refreshResourcesPreviewsCallback = { resourceExplorerViewModel.refreshPreviews() }
    resourceExplorerViewModel.facetUpdaterCallback = { newValue -> this.facet = newValue }
    resourceExplorerViewModel.resourceTypeUpdaterCallback = this::updateResourceType

    val centerContainer = JPanel(BorderLayout())
    centerContainer.add(toolbar, BorderLayout.NORTH)
    centerContainer.add(resourceExplorerView)
    add(centerContainer, BorderLayout.CENTER)
    Disposer.register(this, resourceExplorerViewModel)
    Disposer.register(this, resourceExplorerView)
  }

  companion object {
    private val DIALOG_PREFERRED_SIZE get() = JBUI.size(850, 620)

    /**
     * Create a new instance of [ResourceExplorer] optimized to be used in a [com.intellij.openapi.wm.ToolWindow]
     */
    @JvmStatic
    fun createForToolWindow(facet: AndroidFacet): ResourceExplorer {
      val importersProvider = ImportersProvider()
      val resourceExplorerViewModel = ResourceExplorerViewModel.createResManagerViewModel(facet)
      val toolbarViewModel = ResourceExplorerToolbarViewModel(
        facet,
        resourceExplorerViewModel.supportedResourceTypes[resourceExplorerViewModel.resourceTypeIndex],
        importersProvider,
        resourceExplorerViewModel.filterOptions
      )
      val resourceImportDragTarget = ResourceImportDragTarget(facet, importersProvider)
      val toolbar = ResourceExplorerToolbar.create(toolbarViewModel, moduleComboEnabled = true)
      val resourceExplorerView = ResourceExplorerView(
        viewModel = resourceExplorerViewModel,
        resourceImportDragTarget = resourceImportDragTarget
      )
      return ResourceExplorer(
        facet,
        resourceExplorerViewModel,
        resourceExplorerView,
        toolbarViewModel,
        toolbar,
        resourceImportDragTarget)
    }

    /**
     * Create a new instance of [ResourceExplorer] to be used as resource picker.
     * See [ResourceExplorerViewModel.createResPickerViewModel].
     */
    fun createResourcePicker(
      facet: AndroidFacet,
      types: Array<ResourceType>,
      preselectedResourceName: String?,
      preferredResourceType: ResourceType?,
      showSampleData: Boolean,
      showThemeAttributes: Boolean,
      currentFile: VirtualFile?,
      updateResourceCallback: (resourceItem: ResourceItem) -> Unit,
      doSelectResourceCallback: (resourceItem: ResourceItem) -> Unit
    ): ResourceExplorer {
      val importersProvider = ImportersProvider()
      val resourceExplorerViewModel = ResourceExplorerViewModel.createResPickerViewModel(
        facet = facet,
        configurationContextFile = currentFile,
        preferredResourceTab = preferredResourceType ?: types.first(),
        supportedResourceTypes = types,
        showSampleData = showSampleData,
        showThemeAttributes = showThemeAttributes,
        selectAssetAction = { asset -> doSelectResourceCallback(asset.resourceItem) },
        updateResourceCallback = updateResourceCallback
      )
      val toolbarViewModel = ResourceExplorerToolbarViewModel(
        facet,
        resourceExplorerViewModel.supportedResourceTypes[resourceExplorerViewModel.resourceTypeIndex],
        importersProvider,
        resourceExplorerViewModel.filterOptions
      )
      val resourceImportDragTarget = ResourceImportDragTarget(facet, importersProvider)
      val toolbar = ResourceExplorerToolbar.create(toolbarViewModel, moduleComboEnabled = false)
      val resourceExplorerView = ResourceExplorerView(
        viewModel = resourceExplorerViewModel,
        preselectedResourceName = preselectedResourceName,
        resourceImportDragTarget = resourceImportDragTarget,
        withMultiModuleSearch = false,
        withSummaryView = true,
        withDetailView = false,
        multiSelection = false
      )
      val explorer = ResourceExplorer(
        facet = facet,
        resourceExplorerViewModel = resourceExplorerViewModel,
        resourceExplorerView = resourceExplorerView,
        toolbarViewModel = toolbarViewModel,
        toolbar = toolbar,
        resourceImportDragTarget = resourceImportDragTarget
      )
      explorer.preferredSize = DIALOG_PREFERRED_SIZE
      return explorer
    }
  }

  private fun updateFacet(facet: AndroidFacet) {
    resourceExplorerViewModel.facet = facet
    resourceImportDragTarget.facet = facet
    toolbarViewModel.facet = facet
  }

  private fun updateResourceType(resourceType: ResourceType) {
    toolbarViewModel.resourceType = resourceType
  }

  override fun dispose() {
  }

  override fun getData(dataId: String): Any? =
    when (dataId) {
      PlatformCoreDataKeys.HELP_ID.name -> AndroidWebHelpProvider.HELP_PREFIX + "studio/write/resource-manager"
      else -> null
    }

  /**
   * Selects an asset in the [ResourceExplorer] from the resource's [VirtualFile]. E.g: Select the 'main_activity' Layout resource from the
   * file '...app/res/layout/main_activity.xml'.
   */
  fun selectAsset(facet: AndroidFacet, path: VirtualFile) {
    updateFacet(facet)
    resourceExplorerView.selectAsset(path)
  }

  /**
   * Refresh the resources lists if they are outdated from the Resources repository.
   *
   * There's typically no need to call this unless there's certainty that the lists are outdated.
   */
  fun refreshIfOutdated() {
    LOG.debug("Requested to refresh resources")
    resourceExplorerViewModel.refreshOnResourcesChange()
  }

  /**
   * Selects an asset in the [ResourceExplorer] from a given resource name and its [ResourceType]. E.g: Select the 'main_activity' resource
   * which is a [ResourceType.LAYOUT]. Assumes the [ResourceExplorer] has the correct [AndroidFacet] defined (or it doesn't need to change).
   *
   * @param newResource True if the resource was recently added (i.e: created in the same EDT call).
   */
  private fun selectAsset(resourceName: String, type: ResourceType) {
    resourceExplorerViewModel.resourceTypeIndex = resourceExplorerViewModel.supportedResourceTypes.indexOf(type)
    resourceExplorerView.selectAsset(resourceName, true)
  }
}