/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.ui.resourcechooser

import com.android.ide.common.resources.ResourceResolver
import com.android.resources.ResourceType
import com.android.tools.adtui.model.stdui.DefaultCommonComboBoxModel
import com.android.tools.configurations.Configuration
import com.android.tools.idea.ui.resourcechooser.colorpicker2.PICKER_BACKGROUND_COLOR
import com.android.tools.idea.ui.resourcechooser.common.ResourcePickerSources
import com.android.tools.idea.ui.resourcechooser.util.createResourcePickerDialog
import com.android.tools.idea.ui.resourcemanager.ResourcePickerDialog
import com.android.tools.idea.ui.resourcemanager.model.ResourceAssetSet
import com.android.tools.idea.ui.resourcemanager.model.getAndroidResources
import com.android.tools.idea.ui.resourcemanager.model.getDependentModuleResources
import com.android.tools.idea.ui.resourcemanager.model.getLibraryResources
import com.android.tools.idea.ui.resourcemanager.model.getModuleResources
import com.android.tools.idea.ui.resourcemanager.model.getThemeAttributes
import com.android.tools.idea.ui.resourcemanager.rendering.AssetPreviewManagerImpl
import com.android.tools.idea.ui.resourcemanager.rendering.ImageCache
import com.intellij.concurrency.JobScheduler
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.CollectionListModel
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.speedSearch.NameFilteringListModel
import com.intellij.ui.speedSearch.SpeedSearch
import com.intellij.util.ModalityUiUtil
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.EdtExecutorService
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StatusText
import org.jetbrains.android.facet.AndroidFacet
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.ItemEvent
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.function.BiConsumer
import java.util.function.Supplier
import javax.swing.BorderFactory
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.LayoutFocusTraversalPolicy
import javax.swing.event.DocumentEvent
import kotlin.properties.Delegates

private const val CELL_WIDTH = 300
private const val PANEL_WIDTH = 300
private const val PANEL_HEIGHT = 400

// TODO(148391228): Support initial value
/**
 * A compact version of the [ResourcePickerDialog], lists resources of one [ResourceType], and a has drop down menu to change the source of
 * the resources being displayed: Project, Libraries, Android and Theme Attributes.
 *
 * @param facet The current [AndroidFacet]
 * @param configuration [Configuration] of the current file, provides the context to properly render resources and resolve theme attributes
 * @param selectedResourceCallback Called whenever there's a selection change, including the final selection from the [ResourcePickerDialog]
 * @param resourcePickerDialogOpenedCallback Called when the **Browse** label is clicked to open the [ResourcePickerDialog]
 */
class CompactResourcePicker(
  facet: AndroidFacet,
  contextFile: VirtualFile?,
  resourceResolver: ResourceResolver,
  resourceType: ResourceType,
  selectedPickerSources: List<ResourcePickerSources> = ResourcePickerSources.allSources(),
  selectedResourceCallback: (String) -> Unit,
  resourcePickerDialogOpenedCallback: () -> Unit,
  parentDisposable: Disposable
) : JPanel(BorderLayout()) {
  private val sources: List<ResourcePickerSources> = if (selectedPickerSources.isEmpty()) {
    // Make sure that the sources parameter does not return an empty list, otherwise default to all sources
    thisLogger().warn("Parameter selectedPickerSources is empty, will use all sources")
    ResourcePickerSources.allSources()
  }
  else {
    selectedPickerSources
  }

  private val componentPadding = JBEmptyBorder(8, 12, 8, 12)

  private val scaledCellHeight = resourceType.getScaledCellHeight()

  private var resourcesModel: Map<ResourcePickerSources, List<ResourceAssetSet>> by Delegates.observable(emptyMap()) { _, _, _ ->
    updateResourcesList(sources.first())
  }

  /**
   * The current list model for the [resourcesList] that can filter its items by name.
   */
  private var resourcesListModel: NameFilteringListModel<ResourceAssetSet>? = null

  /**
   * The [SpeedSearch] object used by the [resourcesListModel] to filter items.
   */
  private val speedSearch = SpeedSearch().apply {
    setEnabled(true)
    addChangeListener {
      resourcesListModel?.refilter()
    }
  }

  private val searchTextField = SearchTextField(true).apply {
    background = PICKER_BACKGROUND_COLOR
    addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) {
        speedSearch.updatePattern(e.document.getText(0, e.document.length))
      }
    })
  }

  private val resourceSourceComboBoxModel = DefaultCommonComboBoxModel<ResourcePickerSources>(sources.first().displayableName, sources)

  private val resourceSourceComboBox = ComboBox<ResourcePickerSources>(resourceSourceComboBoxModel).apply {
    isEditable = false
    isEnabled = false
    addItemListener { event ->
      if (event.stateChange == ItemEvent.SELECTED) {
        updateResourcesList(event.itemSelectable.selectedObjects.first() as ResourcePickerSources)
      }
    }
  }

  private val headerToolbar = JPanel(BorderLayout()).apply {
    background = PICKER_BACKGROUND_COLOR
    add(searchTextField, BorderLayout.WEST)
    add(resourceSourceComboBox, BorderLayout.CENTER)
    border = BorderFactory.createCompoundBorder(
      BorderFactory.createMatteBorder(0, 0, JBUIScale.scale(1), 0, JBUI.CurrentTheme.Popup.separatorColor()),
      componentPadding
    )
  }

  /**
   * A [JBList] used to display resources grouped in [ResourceAssetSet]s.
   *
   * Selection changes made in this list will trigger the resource selection callback.
   *
   * @see ResourceAssetSet
   * @see AssetPreviewManagerImpl
   * @see CompactResourceListCellRenderer
   */
  private val resourcesList = JBList<ResourceAssetSet>().apply {
    border = componentPadding
    emptyText.text = "" // No need to show any text right away (before loading is even started)
    background = PICKER_BACKGROUND_COLOR
    fixedCellHeight = scaledCellHeight
    fixedCellWidth = JBUIScale.scale(CELL_WIDTH)
    cellRenderer =
      CompactResourceListCellRenderer(
        AssetPreviewManagerImpl(facet, ImageCache.createImageCache(parentDisposable), resourceResolver),
        scaledCellHeight
      )
    addListSelectionListener { event ->
      if (!event.valueIsAdjusting) {
        selectedValue?.getHighestDensityAsset()?.resourceUrl?.toString()?.let { resourceName ->
          selectedResourceCallback(resourceName)
        }
      }
    }
    addFocusListener(object : FocusAdapter() {
      override fun focusGained(e: FocusEvent?) {
        // Auto-select the first element if the list gains focus and there's no selection.
        if (selectionModel.isSelectionEmpty && !selectionModel.valueIsAdjusting && model.size > 0) {
          selectionModel.setSelectionInterval(0, 0)
        }
      }
    })
  }

  private val resourcesView = JBScrollPane(resourcesList).apply {
    border = JBEmptyBorder(0)
    verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_ALWAYS
    horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
  }

  private val bottomToolbar = JPanel(BorderLayout()).apply {
    isOpaque = false
    border = BorderFactory.createCompoundBorder(
      BorderFactory.createMatteBorder(JBUIScale.scale(1), 0, 0, 0, JBUI.CurrentTheme.Popup.separatorColor()),
      componentPadding)
    val action = BrowseAction(facet, resourceType, contextFile, sources.contains(ResourcePickerSources.THEME_ATTR),
                              selectedResourceCallback, resourcePickerDialogOpenedCallback)
    add(ActionButtonWithText(action,
                             action.templatePresentation.clone(),
                             "",
                             JBUI.size(60, 0)).apply {
      foreground = JBUI.CurrentTheme.Link.Foreground.ENABLED
      isFocusable = true
    }, BorderLayout.EAST)
  }

  /**
   * JobScheduler future that when run, will make the resources list look like if it's loading. If the list loads before the job is run,
   * then it will simply be cancelled.
   */
  private val showAsLoadingFuture = JobScheduler.getScheduler().schedule(
    {
      ModalityUiUtil.invokeLaterIfNeeded(ModalityState.defaultModalityState()) {
        // Schedule the 'loading' state of the list, to avoid flashing in the UI
        resourcesList.setPaintBusy(true)
        resourcesList.emptyText.text = "Loading..."
      }
    }, 250L, TimeUnit.MILLISECONDS)

  init {
    populatePanel()
    loadResources(facet, resourceResolver, resourceType)
  }

  private fun populatePanel() {
    preferredSize = JBDimension(PANEL_WIDTH, PANEL_HEIGHT)
    maximumSize = JBDimension(PANEL_WIDTH, PANEL_HEIGHT)

    add(headerToolbar, BorderLayout.NORTH)
    add(resourcesView)
    add(bottomToolbar, BorderLayout.SOUTH)
    background = PICKER_BACKGROUND_COLOR
    searchTextField.preferredSize = Dimension(headerToolbar.preferredSize.width / 2, searchTextField.preferredSize.height)

    // Setup focus logic
    isFocusCycleRoot = true
    isFocusTraversalPolicyProvider = true
    focusTraversalPolicy = object : LayoutFocusTraversalPolicy() {
      override fun getDefaultComponent(aContainer: Container?): Component {
        return searchTextField
      }
    }
    addPropertyChangeListener("ancestor") {
      searchTextField.requestFocusInWindow()
    }
  }

  /**
   * Load every resource that can be displayed in a background thread, then populate the [resourcesModel] in the EDT.
   */
  private fun loadResources(facet: AndroidFacet,
                            resourceResolver: ResourceResolver,
                            type: ResourceType) {
    CompletableFuture.supplyAsync(Supplier {
      val resourcesMap = mutableMapOf<ResourcePickerSources, List<ResourceAssetSet>>()

      for (source in sources) {
        resourcesMap[source] = when (source) {
          // Project resources come from the current module and its dependencies.
          ResourcePickerSources.PROJECT -> ArrayList<ResourceAssetSet>().apply {
            addAll(getModuleResources(facet, type, emptyList()).assetSets)
            addAll(getDependentModuleResources(facet, type, emptyList()).flatMap { it.assetSets })
          }
          ResourcePickerSources.LIBRARY -> getLibraryResources(facet, type, emptyList()).flatMap { it.assetSets }
          ResourcePickerSources.ANDROID -> getAndroidResources(facet, type, emptyList())?.assetSets ?: emptyList()
          ResourcePickerSources.THEME_ATTR -> getThemeAttributes(facet, type, emptyList(), resourceResolver)?.assetSets ?: emptyList()
        }
      }
      return@Supplier resourcesMap
    }, AppExecutorUtil.getAppExecutorService()).whenCompleteAsync(BiConsumer { resourcesMap, _ ->
      showAsLoadingFuture.cancel(true)
      resourcesModel = resourcesMap
    }, EdtExecutorService.getScheduledExecutorInstance())
  }

  /**
   * Update the [resourcesList] with the resources from the selected [ResourcePickerSources].
   */
  private fun updateResourcesList(source: ResourcePickerSources) {
    resourcesList.setPaintBusy(false)
    resourcesList.emptyText.text = StatusText.getDefaultEmptyText()
    resourcesListModel = NameFilteringListModel<ResourceAssetSet>(
      // Re-apply the filter from the SearchField to the new list
      CollectionListModel<ResourceAssetSet>(resourcesModel.getValue(source)),
      { it.name },
      speedSearch::shouldBeShowing,
      { StringUtil.notNullize(speedSearch.filter) }
    )
    resourcesList.model = resourcesListModel
    resourceSourceComboBox.isEnabled = true
  }
}

private class BrowseAction(
  facet: AndroidFacet,
  resourceType: ResourceType,
  contextFile: VirtualFile?,
  showThemeAttributes: Boolean,
  selectedResourceCallback: (String) -> Unit,
  resourcePickerDialogOpenedCallback: () -> Unit
) : AnAction("Browse", "Open the Resource Picker dialog", null) {
  private val openResourcePickerDialog = {
    // Open the ResourcePickerDialog, the selected resource in the dialog is returned through the selection callback
    val resourcePickerDialog = createResourcePickerDialog(
      "Pick a Resource",
      null,
      facet,
      setOf(resourceType),
      resourceType,
      true,
      false,
      showThemeAttributes,
      contextFile
    )
    resourcePickerDialogOpenedCallback()
    if (resourcePickerDialog.showAndGet()) {
      resourcePickerDialog.resourceName?.let {
        selectedResourceCallback(it)
      }
    }
  }

  override fun displayTextInToolbar() = true

  override fun actionPerformed(e: AnActionEvent) {
    openResourcePickerDialog()
  }
}

private fun ResourceType.getScaledCellHeight(): Int =
  when (this) {
    // TODO(148391228): Use a different size for Layouts/Menus
    ResourceType.MIPMAP,
    ResourceType.DRAWABLE -> 34
    else -> 24
  }.let(JBUIScale::scale)