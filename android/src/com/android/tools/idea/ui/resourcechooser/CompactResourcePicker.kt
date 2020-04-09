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
import com.android.tools.idea.configurations.Configuration
import com.android.tools.idea.ui.resourcechooser.colorpicker2.PICKER_BACKGROUND_COLOR
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
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.CollectionListModel
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.GuiUtils
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.speedSearch.NameFilteringListModel
import com.intellij.ui.speedSearch.SpeedSearch
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
  configuration: Configuration,
  resourceResolver: ResourceResolver,
  resourceType: ResourceType,
  selectedResourceCallback: (String) -> Unit,
  resourcePickerDialogOpenedCallback: () -> Unit,
  parentDisposable: Disposable
) {
  private val scaledCellHeight = resourceType.getScaledCellHeight()

  private var resourcesModel: Map<ResourceSource, List<ResourceAssetSet>> by Delegates.observable(emptyMap()) { _, _, _ ->
    updateResourcesList(ResourceSource.PROJECT)
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

  private val resourceSourceComboBoxModel = DefaultCommonComboBoxModel<ResourceSource>("Project", ResourceSource.values().toList())

  private val resourceSourceComboBox = ComboBox<ResourceSource>(resourceSourceComboBoxModel).apply {
    isEditable = false
    isEnabled = false
    addItemListener { event ->
      if (event.stateChange == ItemEvent.SELECTED) {
        updateResourcesList(event.itemSelectable.selectedObjects.first() as ResourceSource)
      }
    }
  }

  private val headerToolbar = JPanel(BorderLayout()).apply {
    background = PICKER_BACKGROUND_COLOR
    add(searchTextField, BorderLayout.WEST)
    add(resourceSourceComboBox)
    border = BorderFactory.createCompoundBorder(
      BorderFactory.createMatteBorder(0, 0, JBUIScale.scale(1), 0, JBUI.CurrentTheme.Popup.separatorColor()),
      JBEmptyBorder(5, 2, 5, 2)
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
    emptyText.text = "" // No need to show any text right away (before loading is even started)
    background = PICKER_BACKGROUND_COLOR
    cellRenderer = CompactResourceListCellRenderer(
      AssetPreviewManagerImpl(facet, ImageCache.createImageCache(parentDisposable), resourceResolver),
      scaledCellHeight)
    fixedCellHeight = JBUIScale.scale(scaledCellHeight)
    addListSelectionListener { event ->
      if (!event.valueIsAdjusting) {
        selectedValue?.getHighestDensityAsset()?.resourceUrl?.toString()?.let { resourceName ->
          selectedResourceCallback(resourceName)
        }
      }
    }
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
      JBEmptyBorder(5, 0, 4, 5))
    val action = BrowseAction(facet, resourceType, configuration, selectedResourceCallback, resourcePickerDialogOpenedCallback)
    add(ActionButtonWithText(action,
                             action.templatePresentation,
                             "",
                             JBUI.size(60, 0)).apply {
      foreground = JBUI.CurrentTheme.Link.linkColor()
      isFocusable = true
    }, BorderLayout.EAST)
  }

  /**
   * The main panel of the [CompactResourcePicker].
   */
  val component = JPanel(BorderLayout()).apply {
    preferredSize = JBDimension(PANEL_WIDTH, PANEL_HEIGHT)
    maximumSize = JBDimension(PANEL_WIDTH, PANEL_HEIGHT)

    add(headerToolbar, BorderLayout.NORTH)
    add(resourcesView)
    add(bottomToolbar, BorderLayout.SOUTH)
    background = PICKER_BACKGROUND_COLOR
    searchTextField.preferredSize = Dimension(this.preferredSize.width - resourceSourceComboBox.preferredSize.width,
                                              searchTextField.preferredSize.height)

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
   * JobScheduler future that when run, will make the resources list look like if it's loading. If the list loads before the job is run,
   * then it will simply be cancelled.
   */
  private val showAsLoadingFuture = JobScheduler.getScheduler().schedule(
    {
      GuiUtils.invokeLaterIfNeeded(
        {
          // Schedule the 'loading' state of the list, to avoid flashing in the UI
          resourcesList.setPaintBusy(true)
          resourcesList.emptyText.text = "Loading..."
        }, ModalityState.defaultModalityState())
    }, 250L, TimeUnit.MILLISECONDS)

  init {
    loadResources(facet, resourceResolver, resourceType)
  }

  /**
   * Load every resource that can be displayed in a background thread, then populate the [resourcesModel] in the EDT.
   */
  private fun loadResources(facet: AndroidFacet,
                            resourceResolver: ResourceResolver,
                            type: ResourceType) {
    CompletableFuture.supplyAsync(Supplier {
      val projectResources = ArrayList<ResourceAssetSet>().apply {
        addAll(getModuleResources(facet, type, emptyList()).assetSets)
        addAll(getDependentModuleResources(facet, type, emptyList()).flatMap { it.assetSets })
      }
      val libraryResources = getLibraryResources(facet, type, emptyList()).flatMap { it.assetSets }
      val androidResources = getAndroidResources(facet, type, emptyList())?.assetSets ?: emptyList()
      val themeAttributes = getThemeAttributes(facet, type, emptyList(), resourceResolver)?.assetSets ?: emptyList()
      return@Supplier mapOf<ResourceSource, List<ResourceAssetSet>>(
        Pair(ResourceSource.PROJECT, projectResources),
        Pair(ResourceSource.LIBRARY, libraryResources),
        Pair(ResourceSource.ANDROID, androidResources),
        Pair(ResourceSource.THEME_ATTR, themeAttributes)
      )
    }, AppExecutorUtil.getAppExecutorService()).whenCompleteAsync(BiConsumer { resourcesMap, _ ->
      showAsLoadingFuture.cancel(true)
      resourcesModel = resourcesMap
    }, EdtExecutorService.getScheduledExecutorInstance())
  }

  /**
   * Update the [resourcesList] with the resources from the selected [ResourceSource].
   */
  private fun updateResourcesList(source: ResourceSource) {
    resourcesList.setPaintBusy(false)
    resourcesList.emptyText.text = StatusText.DEFAULT_EMPTY_TEXT
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

/**
 * An enum for the different possible sources where resources could be loaded from.
 */
private enum class ResourceSource(val displayableName: String) {
  /**
   * For all local resources, this is the resources from the current module and all the local modules it depends on.
   */
  PROJECT("Project"),
  /**
   * For resources from all the external libraries available for the current module.
   */
  LIBRARY("Libraries"),
  /**
   * For resources that are part of the Android Framework.
   */
  ANDROID("Android"),
  /**
   * For all [ResourceType.ATTR] resources that have a valid mapping to a resource of desired [ResourceType].
   *
   * Depends on the selected theme in the [Configuration] of the current file.
   */
  THEME_ATTR("Theme Attributes");

  override fun toString(): String {
    return displayableName
  }
}

private class BrowseAction(
  facet: AndroidFacet,
  resourceType: ResourceType,
  configuration: Configuration,
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
      configuration.file
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