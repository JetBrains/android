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

import com.android.tools.adtui.common.AdtUiUtils
import com.android.tools.idea.ui.resourcemanager.importer.ResourceImportDragTarget
import com.android.tools.idea.ui.resourcemanager.model.ResourceAssetSet
import com.android.tools.idea.ui.resourcemanager.widget.DetailedPreview
import com.android.tools.idea.ui.resourcemanager.widget.OverflowingTabbedPaneWrapper
import com.intellij.ide.dnd.DnDManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.JBSplitter
import com.intellij.util.concurrency.EdtExecutorService
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.util.concurrent.CompletableFuture
import java.util.function.BiConsumer
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JPanel
import javax.swing.JTabbedPane

/**
 * The basic view of the Resource Explorer, has a center panel where [ResourceExplorerListView] will be added. Has the ResourceType tabs on
 * the top, and some basic view options at the bottom.
 *
 * This view uses [ResourceExplorerViewModel] to know when to add or update [ResourceExplorerListView] and handles its external ui calls
 * that can be made (external selection, grid/list mode, zoom).
 */
class ResourceExplorerView(
  private val viewModel: ResourceExplorerViewModel,
  preselectedResourceName: String? = null,
  private val resourceImportDragTarget: ResourceImportDragTarget,
  private val withMultiModuleSearch: Boolean = true,
  private val withSummaryView: Boolean = false,
  private val withDetailView: Boolean = true, // TODO: Refactor detailView to follow a closer pattern with summaryView
  private val multiSelection: Boolean = true
) : JPanel(BorderLayout()), Disposable {

  private var fileToSelect: VirtualFile? = null
  private var resourceToSelect: String? = preselectedResourceName

  private val resourcesTabsPanel = OverflowingTabbedPaneWrapper().apply {
    viewModel.supportedResourceTypes.forEach {
      tabbedPane.add(it.displayName, null)
    }
    tabbedPane.selectedIndex = viewModel.resourceTypeIndex
    tabbedPane.addChangeListener { event ->
      val index = (event.source as JTabbedPane).model.selectedIndex
      viewModel.resourceTypeIndex = index
      this.requestFocus()
    }
  }

  private val topActionsPanel = JPanel().apply {
    layout = BoxLayout(this, BoxLayout.Y_AXIS)
    isOpaque = false
  }

  private val headerPanel = JPanel().apply {
    layout = BoxLayout(this, BoxLayout.Y_AXIS)
    add(resourcesTabsPanel)
    add(topActionsPanel)
  }

  private val centerPanel = JPanel().apply {
    layout = BoxLayout(this, BoxLayout.Y_AXIS)
    isOpaque = false
    border = JBUI.Borders.empty()
  }

  /**
   * A summary panel including some detailed information about the [ResourceAssetSet].
   * May contain information like:
   *
   * An icon preview, name of the resource, the reference to use the resource (i.e @drawable/resource_name) and the default value of the
   * resource or a table of the configurations and values defined in the [ResourceAssetSet].
   */
  private val summaryView: DetailedPreview? = if (withSummaryView) DetailedPreview() else null

  private var listView: ResourceExplorerListView? = null

  private var listViewJob: CompletableFuture<ResourceExplorerListViewModel>? = null

  init {
    DnDManager.getInstance().registerTarget(resourceImportDragTarget, this)
    add(getContentPanel())

    viewModel.updateResourceTabCallback = {
      resourcesTabsPanel.tabbedPane.selectedIndex = viewModel.resourceTypeIndex
    }
    viewModel.populateResourcesCallback = {
      populateResources()
    }
    populateResources()
  }

  private fun populateResources() {
    listView?.let { Disposer.dispose(it) }
    listView = null
    listViewJob?.cancel(true)
    listViewJob = viewModel.createResourceListViewModel().whenCompleteAsync(BiConsumer { listViewModel, _ ->
      // TODO: Add a loading screen if this process takes too long.
      listView = createResourcesListView(listViewModel).also {
        if (!Disposer.isDisposed(this)) {
          centerPanel.removeAll()
          centerPanel.add(it)
          Disposer.register(this, it)
        } else {
          Disposer.dispose(it)
        }
      }
      selectIfNeeded()
    }, EdtExecutorService.getInstance())
  }

  override fun dispose() {
    DnDManager.getInstance().unregisterTarget(resourceImportDragTarget, this)
  }

  private fun getContentPanel(): JPanel {
    val explorerListPanel = JPanel(BorderLayout()).apply {
      add(headerPanel, BorderLayout.NORTH)
      add(centerPanel, BorderLayout.CENTER)
    }
    if (summaryView == null) {
      return explorerListPanel
    }

    return JBSplitter(0.6f).apply {
      explorerListPanel.border = BorderFactory.createMatteBorder(0, 0, 0, JBUI.scale(1), AdtUiUtils.DEFAULT_BORDER_COLOR)
      isShowDividerControls = true
      isShowDividerIcon = true
      dividerWidth = JBUI.scale(10)
      firstComponent = explorerListPanel
      secondComponent = summaryView
    }
  }

  private fun selectIfNeeded() {
    val file = fileToSelect
    val resourceName = resourceToSelect
    if (file != null) {
      fileToSelect = null
      selectAsset(file)
    }
    else if (resourceName != null) {
      resourceToSelect = null
      selectAsset(resourceName, false)
    }
  }

  fun selectAsset(virtualFile: VirtualFile) {
    if (virtualFile.isDirectory) return
    viewModel.resourceTypeIndex = viewModel.getTabIndexForFile(virtualFile)
    if (listView == null) {
      fileToSelect = virtualFile
    }
    else {
      listView?.selectAsset(virtualFile)
    }
  }

  fun selectAsset(resourceName: String, recentlyAdded: Boolean) {
    if (listView == null) {
      resourceToSelect = resourceName
    }
    else {
      listView?.selectAsset(resourceName, recentlyAdded)
    }
  }

  private fun createResourcesListView(viewModel: ResourceExplorerListViewModel): ResourceExplorerListView {
    return ResourceExplorerListView(viewModel,
                                    resourceImportDragTarget,
                                    withMultiModuleSearch = withMultiModuleSearch,
                                    summaryView = summaryView,
                                    withDetailView = withDetailView,
                                    multiSelection = multiSelection)
  }
}