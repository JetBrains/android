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

import com.android.tools.adtui.util.ActionToolbarUtil
import com.android.tools.idea.ui.resourcemanager.ResourceManagerTracking
import com.android.tools.idea.ui.resourcemanager.actions.ExpandAction
import com.android.tools.idea.ui.resourcemanager.actions.RefreshDesignAssetAction
import com.android.tools.idea.ui.resourcemanager.explorer.ResourceExplorerListViewModel.UpdateUiReason
import com.android.tools.idea.ui.resourcemanager.findCompatibleFacets
import com.android.tools.idea.ui.resourcemanager.importer.ResourceImportDragTarget
import com.android.tools.idea.ui.resourcemanager.model.Asset
import com.android.tools.idea.ui.resourcemanager.model.DesignAsset
import com.android.tools.idea.ui.resourcemanager.model.ResourceAssetSet
import com.android.tools.idea.ui.resourcemanager.model.ResourceSection
import com.android.tools.idea.ui.resourcemanager.model.designAssets
import com.android.tools.idea.ui.resourcemanager.rendering.DefaultIconProvider
import com.android.tools.idea.ui.resourcemanager.widget.DetailedPreview
import com.android.tools.idea.ui.resourcemanager.widget.LinkLabelSearchView
import com.android.tools.idea.ui.resourcemanager.widget.Section
import com.android.tools.idea.ui.resourcemanager.widget.SectionList
import com.android.tools.idea.ui.resourcemanager.widget.SectionListModel
import com.intellij.concurrency.JobScheduler
import com.intellij.icons.AllIcons
import com.intellij.ide.dnd.DnDManager
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.CollectionListModel
import com.intellij.ui.JBColor
import com.intellij.ui.PopupHandler
import com.intellij.ui.components.JBLabel
import com.intellij.ui.speedSearch.NameFilteringListModel
import com.intellij.util.ModalityUiUtil
import com.intellij.util.concurrency.EdtExecutorService
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import icons.StudioIcons
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.Point
import java.awt.event.InputEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.font.TextAttribute
import java.util.Locale
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.function.BiConsumer
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSeparator
import javax.swing.LayoutFocusTraversalPolicy
import javax.swing.ListSelectionModel
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.properties.Delegates

private val DEFAULT_LIST_MODE_WIDTH get() = JBUI.scale(60)
private val MAX_CELL_WIDTH get() = JBUI.scale(300)
private val LIST_CELL_SIZE get() = JBUI.scale(60)
private val MIN_CELL_WIDTH get() = JBUI.scale(150)
private val DEFAULT_CELL_WIDTH get() = LIST_CELL_SIZE
private val SECTION_HEADER_SECONDARY_COLOR get() = JBColor.border()

private val SECTION_HEADER_BORDER
  get() = BorderFactory.createCompoundBorder(
    JBUI.Borders.empty(4, 4, 8, 4),
    JBUI.Borders.customLine(SECTION_HEADER_SECONDARY_COLOR, 0, 0, 1, 0)
  )

private val SECTION_LIST_BORDER get() = JBUI.Borders.empty()

private val SECTION_HEADER_LABEL_FONT
  get() = JBUI.Fonts.label().deriveFont(mapOf(
    TextAttribute.WEIGHT to TextAttribute.WEIGHT_SEMIBOLD,
    TextAttribute.SIZE to JBUI.scaleFontSize(14f)
  ))

private val TOOLBAR_BORDER
  get() = BorderFactory.createCompoundBorder(
    JBUI.Borders.customLine(JBColor.border(), 0, 0, 1, 0),
    JBUI.Borders.empty(4, 2)
  )

private val GRID_MODE_BACKGROUND = UIUtil.getPanelBackground()
private val LIST_MODE_BACKGROUND = UIUtil.getListBackground()

/**
 * Delay to wait for before showing the "Loading" state.
 *
 * If we don't delay showing the loading state, user might see a quick flickering
 * when switching tabs because of the quick change from old resources to loading view to new resources.
 */
private const val MS_DELAY_BEFORE_LOADING_STATE = 100L // ms
private val UNIT_DELAY_BEFORE_LOADING_STATE = TimeUnit.MILLISECONDS

private const val GRID_MODE = "resourceExplorer.gridMode"
private const val PREVIEW_SIZE = "resourceExplorer.previewSize"

/**
 * View displaying [com.android.tools.idea.ui.resourcemanager.model.Asset]s located in the project.
 *
 * It uses an [ResourceExplorerListViewModelImpl] to populate the view.
 */
class ResourceExplorerListView(
  private val viewModel: ResourceExplorerListViewModel,
  private val resourceImportDragTarget: ResourceImportDragTarget,
  withMultiModuleSearch: Boolean = true,
  private val summaryView: DetailedPreview?,
  private val withDetailView: Boolean = true,
  private val multiSelection: Boolean = true
) : JPanel(BorderLayout()), Disposable, DataProvider {

  private var updatePending = false

  private var populateResourcesFuture: CompletableFuture<List<ResourceSection>>? = null

  /** Reference to the last [CompletableFuture] used to search for filtered resources in other modules */
  private var searchFuture: CompletableFuture<List<ResourceSection>>? = null
  private var showLoadingFuture: ScheduledFuture<*>? = null

  private var fileToSelect: VirtualFile? = null
  private var resourceToSelect: String? = null

  private var previewSize = PropertiesComponent.getInstance().getInt(PREVIEW_SIZE, DEFAULT_CELL_WIDTH)
    set(value) {
      if (value != field) {
        PropertiesComponent.getInstance().setValue(PREVIEW_SIZE, value, DEFAULT_CELL_WIDTH)
        field = value
        sectionList.getLists().forEach {
          (it as AssetListView).thumbnailWidth = previewSize
        }
      }
    }

  private var gridMode: Boolean by Delegates.observable(PropertiesComponent.getInstance().getBoolean(GRID_MODE)) { _, _, newValue ->
    PropertiesComponent.getInstance().setValue(GRID_MODE, newValue)
    val backgroundColor = if (newValue) GRID_MODE_BACKGROUND else LIST_MODE_BACKGROUND
    centerPanel.background = backgroundColor
    sectionList.background = backgroundColor
    moduleSearchView?.backgroundColor = backgroundColor
    sectionList.getLists().forEach {
      (it as AssetListView).isGridMode = newValue
    }
  }

  private val listeners = mutableListOf<SelectionListener>()
  private val sectionListModel: SectionListModel = SectionListModel()
  private val dragHandler = resourceDragHandler(resourceImportDragTarget)

  private val topActionsPanel = JPanel().apply {
    layout = BoxLayout(this, BoxLayout.Y_AXIS)
    isOpaque = false
  }

  private val sectionList: SectionList = SectionList(sectionListModel).apply {
    border = SECTION_LIST_BORDER
    background = if (gridMode) GRID_MODE_BACKGROUND else LIST_MODE_BACKGROUND
    horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
    verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_ALWAYS
    addMouseWheelListener { event ->
      val modifierKey = if (SystemInfo.isMac) InputEvent.META_MASK else InputEvent.CTRL_MASK
      val modifierPressed = (event.modifiers and modifierKey) == modifierKey
      if (modifierPressed && gridMode) {
        previewSize = max(MIN_CELL_WIDTH, min(
          MAX_CELL_WIDTH, (previewSize * (1 - event.preciseWheelRotation * 0.1)).roundToInt()))
      }
    }
  }

  private val contentSeparator = JSeparator().apply {
    isVisible = false
    minimumSize = Dimension(JBUI.scale(10), JBUI.scale(4))
    maximumSize = Dimension(Integer.MAX_VALUE, JBUI.scale(10))
  }

  /** A view to hold clickable labels to change modules when filtering resources. */
  private val moduleSearchView = if (withMultiModuleSearch) LinkLabelSearchView().apply {
    backgroundColor = if (gridMode) GRID_MODE_BACKGROUND else LIST_MODE_BACKGROUND
  } else null

  private val centerPanel = JPanel().apply {
    layout = BoxLayout(this@apply, BoxLayout.Y_AXIS)
    background = if (gridMode) GRID_MODE_BACKGROUND else LIST_MODE_BACKGROUND
    add(sectionList)
    if (moduleSearchView != null) {
      add(contentSeparator)
      add(moduleSearchView)
    }
  }

  private val footerPanel = JPanel(BorderLayout()).apply {
    border = JBUI.Borders.customLine(JBColor.border(), 1, 0, 0, 0)

    val bottomToolbar = ActionManager.getInstance().createActionToolbar("resourceExplorer", createBottomActions(), true)
    bottomToolbar.targetComponent = this@ResourceExplorerListView
    add(bottomToolbar.component, BorderLayout.EAST)
  }

  private val contentPanel: JPanel =
    JPanel(BorderLayout()).apply {
      add(topActionsPanel, BorderLayout.NORTH)
      add(centerPanel)
      add(footerPanel, BorderLayout.SOUTH)
    }

  /**
   * Mouse listener to invoke the popup menu.
   *
   * This custom implementation is needed to ensure that the clicked element is selected
   * before invoking the menu.
   */
  private val popupHandler = object : PopupHandler() {
    val actionManager = ActionManager.getInstance()
    val group = DefaultActionGroup().apply {
      add(RefreshDesignAssetAction { assets ->
        assets.forEach { viewModel.clearImageCache(it) }
        repaint()
      })
      addSeparator()
      add(actionManager.getAction("ResourceExplorer") as ActionGroup)
    }

    override fun invokePopup(comp: Component?, x: Int, y: Int) {
      val list = comp as JList<*>
      // Select the element before invoking the popup menu
      val clickedIndex = list.locationToIndex(Point(x, y))
      if (!list.isSelectedIndex(clickedIndex)) {
        list.selectedIndex = clickedIndex
      }
      val popupMenu = actionManager.createActionPopupMenu("ResourceExplorer", group)
      popupMenu.setTargetComponent(list)
      val menu = popupMenu.component
      menu.show(comp, x, y)
    }
  }

  /**
   * A mouse listener that opens a [ResourceDetailView] when double clicking
   * on an item from the list.
   * @see doSelectAssetAction
   */
  private val mouseClickListener = object : MouseAdapter() {
    override fun mouseClicked(e: MouseEvent) {
      if (!(e.clickCount == 2 && e.button == MouseEvent.BUTTON1)) {
        return
      }
      val assetListView = e.source as AssetListView
      val index = assetListView.locationToIndex(e.point)
      if (index >= 0) {
        val designAssetSet = assetListView.model.getElementAt(index)
        doSelectAssetAction(designAssetSet)
      }
    }
  }

  private val keyListener = object : KeyAdapter() {
    override fun keyPressed(e: KeyEvent) {
      if (KeyEvent.VK_ENTER == e.keyCode) {
        val assetListView = e.source as AssetListView
        doSelectAssetAction(assetListView.selectedValue)
      }
    }
  }

  /**
   * Replace the content of the view with a [ResourceDetailView] for the provided [designAssetSet].
   */
  private fun doSelectAssetAction(designAssetSet: ResourceAssetSet) {
    if (designAssetSet.assets.isNotEmpty()) {
      val asset = designAssetSet.assets.first()
      if (designAssetSet.assets.size > 1 && withDetailView) {
        showDetailView(designAssetSet)
        return
      }
      ResourceManagerTracking.logAssetOpened(viewModel.facet, asset.type)
      viewModel.doSelectAssetAction(asset)
      return
    }
  }

  private fun showDetailView(designAssetSet: ResourceAssetSet) {
    val previousSelectedValue = sectionList.selectedValue

    val detailView = ResourceDetailView(designAssetSet, viewModel) {
      setContentPanel()
      sectionList.selectedValue = previousSelectedValue
    }

    removeAll()
    add(detailView)
    revalidate()
    repaint()
    detailView.requestFocusInWindow()
  }

  /**
   * Update the [summaryView] panel.
   * May populate the icon, the metadata and/or a [configuration, value] map.
   */
  private fun updateSummaryPreview() {
    if (summaryView == null) return
    val resourceAssetSet = sectionList.selectedValue as? ResourceAssetSet ?: return

    updateSummaryPreviewIcon()
    summaryView.apply {
      val summaryMapFuture = viewModel.getResourceSummaryMap(resourceAssetSet)
      val configurationMapFuture = viewModel.getResourceConfigurationMap(resourceAssetSet)
      CompletableFuture.allOf(summaryMapFuture, configurationMapFuture).whenCompleteAsync(BiConsumer { _, _ ->
        data = summaryMapFuture.join()
        values = configurationMapFuture.join()
        validate()
        repaint()
      }, EdtExecutorService.getInstance())
    }
  }

  /**
   * Update the icon in the [summaryView] panel, if any. Also used for the refresh callback in the preview provider.
   */
  private fun updateSummaryPreviewIcon() {
    val assetSet = (sectionList.selectedValue as? ResourceAssetSet)?: return
    val assetForPreview = assetSet.getHighestDensityAsset()
    summaryView?.let {
      if (assetForPreview !is DesignAsset) {
        // TODO: Figure out a way to render a BaseAssets, for things like Tools -> SampleData.
        summaryView.icon = null
        summaryView.repaint()
        return
      }
      val previewProvider = viewModel.summaryPreviewManager.getPreviewProvider(assetForPreview.type)
      summaryView.icon = if (previewProvider !is DefaultIconProvider) previewProvider.getIcon(
        assetForPreview,
        JBUI.scale(DetailedPreview.PREVIEW_ICON_SIZE),
        JBUI.scale(DetailedPreview.PREVIEW_ICON_SIZE),
        this,
        refreshCallback = {
          updateSummaryPreviewIcon()
        }
      ) else null
      summaryView.repaint()
    }
  }

  init {
    DnDManager.getInstance().registerTarget(resourceImportDragTarget, this)

    viewModel.updateUiCallback = { reason ->
      when (reason) {
        UpdateUiReason.IMAGE_CACHE_CHANGED -> repaint()
        UpdateUiReason.RESOURCE_TYPE_CHANGED -> {
          setContentPanel()
          populateExternalActions()
          populateResourcesLists()
          populateSearchLinkLabels()
        }
        UpdateUiReason.RESOURCES_CHANGED -> {
          populateExternalActions()
          populateResourcesLists(keepScrollPosition = true)
          populateSearchLinkLabels()
        }
      }
    }
    populateExternalActions()
    populateResourcesLists()
    populateSearchLinkLabels()
    viewModel.speedSearch.addChangeListener {
      sectionList.getLists().filterIsInstance<AssetListView>().forEach { assetListView -> assetListView.refilter() }
      sectionList.getSections().filterIsInstance<AssetSection<AssetListView>>().forEach {
        section -> section.updateHeaderName((section.list as? AssetListView)?.getFilteredSize())
      }
      centerPanel.validate()
      centerPanel.repaint()
      populateSearchLinkLabels()
    }

    setContentPanel()
    isFocusTraversalPolicyProvider = true
    focusTraversalPolicy = object : LayoutFocusTraversalPolicy() {
      override fun getFirstComponent(p0: Container?): Component {
        return sectionList.getLists().firstOrNull() ?: this@ResourceExplorerListView
      }
    }
  }

  private fun setContentPanel() {
    removeAll()
    add(contentPanel)
    revalidate()
    repaint()
  }

  private fun getSelectedAssets(): List<Asset> {
    return sectionList.getLists()
      .flatMap { it.selectedValuesList }
      .filterIsInstance<ResourceAssetSet>()
      .flatMap(ResourceAssetSet::assets)
  }

  private fun populateExternalActions() {
    topActionsPanel.removeAll()
    val actionManager = ActionManager.getInstance()
    viewModel.externalActions.forEach {
      actionManager.createActionToolbar("resourceExplorer", it, true).let { actionToolbar ->
        actionToolbar.targetComponent = this@ResourceExplorerListView
        topActionsPanel.add(actionToolbar.component.apply { border = TOOLBAR_BORDER })
      }
    }
  }

  private fun populateSearchLinkLabels() {
    if (moduleSearchView == null) return
    searchFuture?.let { future ->
      if (!future.isDone()) {
        // Only one 'future' for getOtherModulesResourceLists may run at a time.
        future.cancel(true)
      }
    }

    moduleSearchView.clear()
    contentSeparator.isVisible = false

    val filter = viewModel.speedSearch.filter
    if (filter.isNotBlank()) {
      searchFuture = viewModel.getOtherModulesResourceLists()
        .whenCompleteAsync(BiConsumer { resourceLists, _ ->
          displaySearchLinkLabels(resourceLists, filter)
        }, EdtExecutorService.getInstance())
    }
    centerPanel.revalidate()
  }

  /**
   * Applies the filter in the SpeedSearch to the given resource sections, then, creates and displays LinkLabels to the modules with
   * resources matching the filter.
   *
   * @param filter Received filter string, since the filter in SpeedSearch might change at runtime while this is running.
   */
  private fun displaySearchLinkLabels(resourceSections: List<ResourceSection>, filter: String) {
    if (moduleSearchView == null) return // TODO: Log?
    val search = viewModel.speedSearch
    search.setEnabled(true)
    resourceSections.forEach { section ->
      val filteringModel = NameFilteringListModel(CollectionListModel(section.assetSets), { it.name }, search::shouldBeShowing,
                                                  { StringUtil.notNullize(filter) })
      filteringModel.refilter()
      val resourcesCount = filteringModel.size
      if (resourcesCount > 0) {
        // TODO: Get the facet when the module is being set in ResourceExplorerViewModel by passing the module name instead of the actual facet.
        // I.e: This class should not be fetching module objects.
        findCompatibleFacets(viewModel.facet.module.project).firstOrNull {
          it.module.name == section.libraryName
        }?.let { facetToChange ->
          // Create [LinkLabel]s that when clicking them, changes the working module to the module in the given [AndroidFacet].
          moduleSearchView.addLabel(
            "$resourcesCount ${StringUtil.pluralize("resource", resourcesCount)} found in '${facetToChange.module.name}'") {
            viewModel.facetUpdated(facetToChange)
          }
        }
      }
    }
    contentSeparator.isVisible = moduleSearchView.isVisible
    centerPanel.validate()
    centerPanel.repaint()
  }

  /**
   * Update the [sectionList] to show the current lists of resource. By default, the scroll
   * position will be reset to the top.
   *
   * @param keepScrollPosition: when true, the updated list will be automatically scrolled to
   *  the position it had before. This is the desired behaviour in some particular scenarios,
   *  and it is the caller's responsibility to decide depending on the context.
   */
  private fun populateResourcesLists(keepScrollPosition: Boolean = false) {
    val selectedValue = sectionList.selectedValue
    val selectedIndices = sectionList.selectedIndices
    val scrollPosition = getScrollPosition()
    updatePending = true
    populateResourcesFuture?.cancel(true)
    populateResourcesFuture = viewModel.getCurrentModuleResourceLists()
      .whenCompleteAsync(BiConsumer { resourceLists, _ ->
        updatePending = false
        displayResources(resourceLists)
        if (keepScrollPosition) setScrollPosition(scrollPosition)
        selectIndicesIfNeeded(selectedValue, selectedIndices)
      }, EdtExecutorService.getInstance())

    if (populateResourcesFuture?.isDone == false) {
      if (showLoadingFuture == null) {
        showLoadingFuture = JobScheduler.getScheduler().schedule(
          { ModalityUiUtil.invokeLaterIfNeeded(ModalityState.defaultModalityState(), this::displayLoading) },
          MS_DELAY_BEFORE_LOADING_STATE,
          UNIT_DELAY_BEFORE_LOADING_STATE)
      }
    }
  }

  private fun displayLoading() {
    showLoadingFuture = null
    if (populateResourcesFuture?.isDone ?: true) {
      return
    }
    sectionListModel.clear()
    sectionListModel.addSection(createLoadingSection())
  }

  private fun displayResources(resourceLists: List<ResourceSection>) {
    sectionListModel.clear()
    val sections = resourceLists
      .filterNot { it.assetSets.isEmpty() }
      .map(this::createSection)
      .toList()
    if (!sections.isEmpty()) {
      sectionListModel.addSections(sections)
    }
    else {
      sectionListModel.addSection(createEmptySection())
    }
    sectionList.validate()
    sectionList.repaint()
  }

  private fun createLoadingSection() = AssetSection<ResourceAssetSet>(
    viewModel.facet.module.name, null,
    AssetListView(emptyList(), null).apply {
      setPaintBusy(true)
      setEmptyText("Loading...")
      background = this@ResourceExplorerListView.background
    })

  private fun createEmptySection() = AssetSection<ResourceAssetSet>(
    viewModel.facet.module.name, null,
    AssetListView(emptyList(), null).apply {
      setEmptyText("No ${viewModel.selectedTabName.lowercase(Locale.US)} available")
      background = this@ResourceExplorerListView.background
    })

  private fun selectIndicesIfNeeded(selectedValue: Any?, selectedIndices: List<IntArray?>) {
    val finalFileToSelect = fileToSelect
    val finalResourceToSelect = resourceToSelect
    if (finalFileToSelect != null) {
      // Attempt to select resource by file, if it was pending.
      selectAsset(finalFileToSelect)
    }
    else if (finalResourceToSelect != null) {
      // Attempt to select resource by name, if it was pending.
      selectAsset(finalResourceToSelect, recentlyAdded = false)
    }
    else if (selectedValue != null) {
      // Attempt to reselect the previously selected element
      // If the value still exist in the list, just reselect it
      sectionList.selectedValue = selectedValue

      // Otherwise, like if the selected resource was renamed, we reselect the element
      // based on the indexes
      if (sectionList.selectedIndex == null) {
        sectionList.selectedIndices = selectedIndices
      }
    }

    // Guarantee that any other pending selection is cancelled. Having more than one of these is unintended behavior.
    fileToSelect = null
    resourceToSelect = null
  }

  private fun getScrollPosition(): Point {
    return sectionList.viewport.viewPosition
  }

  private fun setScrollPosition(scrollPosition: Point) {
    sectionList.viewport.viewPosition = scrollPosition
  }

  /**
   * Selects a [ResourceAssetSet] by a given [VirtualFile]. Depending of the file, the currently displayed resource type may change to
   * select the right resource.
   */
  fun selectAsset(virtualFile: VirtualFile) {
    if (virtualFile.isDirectory) return

    if (updatePending) {
      fileToSelect = virtualFile
    }
    else {
      doSelectAsset { assetSet ->
        assetSet.designAssets.any { it.file == virtualFile }.also { if (it) fileToSelect = null }
      }
    }
  }

  /**
   * Selects a listed [ResourceAssetSet] by its name.
   *
   * @param resourceName Name to look for in existing lists of resources.
   * @param recentlyAdded The resource might not be listed yet if it was recently added (awaiting resource notification).
   */
  fun selectAsset(resourceName: String, recentlyAdded: Boolean) {
    if (updatePending || recentlyAdded) {
      resourceToSelect = resourceName
    }
    if (!updatePending) {
      doSelectAsset isAsset@{ assetSet ->
        val found = assetSet.name == resourceName
        if (found) {
          resourceToSelect = null
        }
        return@isAsset found
      }
    }
  }

  private fun doSelectAsset(isDesiredAssetSet: (ResourceAssetSet) -> Boolean) {
    sectionList.getLists()
      .filterIsInstance<AssetListView>()
      .forEachIndexed { listIndex, list ->
        for (assetIndex in 0 until list.model.size) {
          if (isDesiredAssetSet(list.model.getElementAt(assetIndex))) {
            sectionList.selectedIndex = listIndex to assetIndex
            sectionList.scrollToSelection()
            list.requestFocusInWindow()
            return
          }
        }
      }
  }

  private fun createSection(section: ResourceSection): AssetSection<ResourceAssetSet> {
    val assetList = AssetListView(section.assetSets, viewModel.speedSearch).apply {
      cellRenderer = DesignAssetCellRenderer(viewModel.assetPreviewManager)
      dragHandler.registerSource(this)
      addMouseListener(popupHandler)
      addMouseListener(mouseClickListener)
      addKeyListener(keyListener)
      selectionMode = if (multiSelection) ListSelectionModel.MULTIPLE_INTERVAL_SELECTION else ListSelectionModel.SINGLE_SELECTION
      this.addListSelectionListener {
        listeners.forEach { listener ->
          listener.onDesignAssetSetSelected(sectionList.selectedValue as? ResourceAssetSet)
        }
        (sectionList.selectedValue as? ResourceAssetSet)?.let { viewModel.updateSelectedAssetSet(it) }
        updateSummaryPreview()
      }
      thumbnailWidth = this@ResourceExplorerListView.previewSize
      isGridMode = this@ResourceExplorerListView.gridMode
    }
    return AssetSection(section.libraryName, assetList.getFilteredSize(), assetList)
  }

  fun addSelectionListener(listener: SelectionListener) {
    listeners += listener
  }

  fun removeSelectionListener(listener: SelectionListener) {
    listeners -= listener
  }

  interface SelectionListener {
    /** Triggers when the [ResourceAssetSet] selection changes. */
    fun onDesignAssetSetSelected(resourceAssetSet: ResourceAssetSet?)
  }

  private class AssetSection<T>(
    override var name: String,
    size: Int?,
    override var list: JList<T>
  ) : Section<T> {

    private val headerNameLabel = JBLabel(buildName(size)).apply {
      font = SECTION_HEADER_LABEL_FONT
      border = JBUI.Borders.empty(8, 0)
    }

    override var header: JComponent = createHeaderComponent()

    fun updateHeaderName(newSize: Int?) {
      headerNameLabel.text = buildName(newSize)
    }

    private fun createHeaderComponent() = JPanel(BorderLayout()).apply {
      isOpaque = false

      val expandAction = object : ExpandAction() {
        override var expanded: Boolean = true
          set(value) {
            field = value
            list.isVisible = value
            // Clear selection to avoid interaction issues.
            list.selectionModel.clearSelection()
          }
      }

      val toolbar = ActionToolbarImpl("AssetSection", DefaultActionGroup(expandAction), true).apply {
        layoutPolicy = ActionToolbar.NOWRAP_LAYOUT_POLICY
      }
      expandAction.expanded = true

      add(headerNameLabel, BorderLayout.WEST)
      add(toolbar.component, BorderLayout.EAST)
      border = SECTION_HEADER_BORDER
    }

    private fun buildName(size: Int?): String {
      val itemNumber = size?.let { " ($it)" } ?: ""
      return "${this@AssetSection.name}$itemNumber"
    }
  }

  override fun getData(dataId: String): Any? {
    return viewModel.getData(dataId, getSelectedAssets())
  }

  override fun dispose() {
    DnDManager.getInstance().unregisterTarget(resourceImportDragTarget, this)
    populateResourcesFuture?.cancel(true)
    searchFuture?.cancel(true)
    showLoadingFuture?.cancel(true)
  }

  private fun createBottomActions(): DefaultActionGroup {
    return DefaultActionGroup(
      ListModeButton(),
      GridModeButton(),
      Separator(),
      ZoomMinus(),
      ZoomPlus()
    )
  }

  /**
   * Button to enable the list view
   */
  private inner class ListModeButton
    : ToggleAction("List mode", "Switch to list mode", StudioIcons.Common.LIST_VIEW), DumbAware {

    override fun isSelected(e: AnActionEvent) = !gridMode

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      if (state) {
        ResourceManagerTracking.logSwitchToListMode()
        gridMode = false
        previewSize = DEFAULT_LIST_MODE_WIDTH
      }
    }
  }

  /**
   * Button to enable the grid view
   */
  private inner class GridModeButton
    : ToggleAction("Grid mode", "Switch to grid mode", StudioIcons.Common.GRID_VIEW),
      DumbAware {

    override fun isSelected(e: AnActionEvent) = gridMode

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      if (state) {
        ResourceManagerTracking.logSwitchToGridMode(viewModel.facet)
        gridMode = true
        previewSize = MIN_CELL_WIDTH
      }
    }
  }

  /**
   * Button to scale down the icons. It is only enabled in grid mode.
   */
  private inner class ZoomMinus : AnAction("Zoom Out", "Decrease thumbnail size", AllIcons.General.ZoomOut), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
      previewSize = max(MIN_CELL_WIDTH, (previewSize * 0.9).roundToInt())
    }

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = gridMode && previewSize > MIN_CELL_WIDTH
    }
  }

  /**
   * Button to scale up the icons. It is only enabled in grid mode.
   */
  private inner class ZoomPlus : AnAction("Zoom In", "Increase thumbnail size", AllIcons.General.ZoomIn), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
      previewSize = min(MAX_CELL_WIDTH, (previewSize * 1.1).roundToInt())
    }

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = gridMode && previewSize < MAX_CELL_WIDTH
    }
  }
}
