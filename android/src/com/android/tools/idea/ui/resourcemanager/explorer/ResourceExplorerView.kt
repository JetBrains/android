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
import com.android.tools.idea.ui.resourcemanager.ResourceManagerTracking
import com.android.tools.idea.ui.resourcemanager.importer.ResourceImportDragTarget
import com.android.tools.idea.ui.resourcemanager.model.Asset
import com.android.tools.idea.ui.resourcemanager.model.DesignAsset
import com.android.tools.idea.ui.resourcemanager.model.ResourceAssetSet
import com.android.tools.idea.ui.resourcemanager.model.designAssets
import com.android.tools.idea.ui.resourcemanager.rendering.DefaultIconProvider
import com.android.tools.idea.ui.resourcemanager.widget.DetailedPreview
import com.android.tools.idea.ui.resourcemanager.widget.LinkLabelSearchView
import com.android.tools.idea.ui.resourcemanager.widget.OverflowingTabbedPaneWrapper
import com.android.tools.idea.ui.resourcemanager.widget.Section
import com.android.tools.idea.ui.resourcemanager.widget.SectionList
import com.android.tools.idea.ui.resourcemanager.widget.SectionListModel
import com.android.tools.idea.util.androidFacet
import com.intellij.concurrency.JobScheduler
import com.intellij.icons.AllIcons
import com.intellij.ide.dnd.DnDManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.CollectionListModel
import com.intellij.ui.GuiUtils
import com.intellij.ui.JBColor
import com.intellij.ui.JBSplitter
import com.intellij.ui.PopupHandler
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.labels.LinkListener
import com.intellij.ui.speedSearch.NameFilteringListModel
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
import java.util.concurrent.TimeUnit
import java.util.function.BiConsumer
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSeparator
import javax.swing.JTabbedPane
import javax.swing.LayoutFocusTraversalPolicy
import javax.swing.ListSelectionModel
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.properties.Delegates


private val MAX_CELL_WIDTH get() = JBUI.scale(300)
private val LIST_CELL_SIZE get() = JBUI.scale(60)
private val MIN_CELL_WIDTH get() = JBUI.scale(150)
private const val DEFAULT_GRID_MODE = false
private val DEFAULT_CELL_WIDTH get() = LIST_CELL_SIZE
private val SECTION_HEADER_SECONDARY_COLOR get() = JBColor.border()

private val SECTION_HEADER_BORDER get() = BorderFactory.createCompoundBorder(
  JBUI.Borders.empty(4, 4, 8, 4),
  JBUI.Borders.customLine(SECTION_HEADER_SECONDARY_COLOR, 0, 0, 1, 0)
)

private val SECTION_LIST_BORDER get() = JBUI.Borders.empty()

private val SECTION_HEADER_LABEL_FONT get() = JBUI.Fonts.label().deriveFont(mapOf(
  TextAttribute.WEIGHT to TextAttribute.WEIGHT_SEMIBOLD,
  TextAttribute.SIZE to JBUI.scale(14f)
))

private val GRID_MODE_BACKGROUND = UIUtil.getPanelBackground()
private val LIST_MODE_BACKGROUND = UIUtil.getListBackground()

/**
 * Delay to wait for before showing the "Loading" state.
 *
 * If we don't delay showing the loading state, user might see a quick flickering
 * when switching tabs because of the quick change from old resources to loading view to new resources.
 */
private const val DELAY_BEFORE_LOADING_STATE = 100L // ms

/**
 * View meant to display [com.android.tools.idea.ui.resourcemanager.model.Asset] located
 * in the project.
 * It uses an [ResourceExplorerViewModelImpl] to populates the views
 */
class ResourceExplorerView(
  private val resourcesBrowserViewModel: ResourceExplorerViewModel,
  private val resourceImportDragTarget: ResourceImportDragTarget,
  withMultiModuleSearch: Boolean = true,
  withSummaryView: Boolean = false,
  private val withDetailView: Boolean = true, // TODO: Refactor detailView to follow a closer pattern with summaryView
  private val multiSelection: Boolean = true
) : JPanel(BorderLayout()), Disposable, DataProvider {

  private var updatePending = false

  /** Reference to the last [CompletableFuture] used to search for filtered resources in other modules */
  private var searchFuture: CompletableFuture<List<ResourceSection>>? = null

  private var fileToSelect: VirtualFile? = null

  private var previewSize = DEFAULT_CELL_WIDTH
    set(value) {
      if (value != field) {
        field = value
        sectionList.getLists().forEach {
          (it as AssetListView).thumbnailWidth = previewSize
        }
      }
    }

  private var gridMode: Boolean by Delegates.observable(
    DEFAULT_GRID_MODE) { _, _, newValue ->
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

  private val headerPanel = OverflowingTabbedPaneWrapper().apply {
    resourcesBrowserViewModel.resourceTypes.forEach {
      tabbedPane.addTab(it.displayName, null)
    }
    tabbedPane.addChangeListener { event ->
      val index = (event.source as JTabbedPane).model.selectedIndex
      resourcesBrowserViewModel.resourceTypeIndex = index
    }
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

    add(ActionManager.getInstance().createActionToolbar(
      "resourceExplorer",
      createBottomActions(), true).component, BorderLayout.EAST)
  }

  /**
   * A summary panel including some detailed information about the [ResourceAssetSet].
   * May contain information like:
   *
   * An icon preview, name of the resource, the reference to use the resource (i.e @drawable/resource_name) and the default value of the
   * resource or a table of the configurations and values defined in the [ResourceAssetSet].
   */
  private val summaryView = if (withSummaryView) DetailedPreview() else null

  /**
   * Mouse listener to invoke the popup menu.
   *
   * This custom implementation is needed to ensure that the clicked element is selected
   * before invoking the menu.
   */
  private val popupHandler = object : PopupHandler() {
    val actionManager = ActionManager.getInstance()
    val group = actionManager.getAction("ResourceExplorer") as ActionGroup

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
      ResourceManagerTracking.logAssetOpened(asset.type)
      resourcesBrowserViewModel.doSelectAssetAction(asset)
      return
    }
  }

  private fun showDetailView(designAssetSet: ResourceAssetSet) {
    val parent = parent
    parent.remove(this)
    val previousSelectedValue = sectionList.selectedValue

    val detailView = ResourceDetailView(designAssetSet, resourcesBrowserViewModel) { detailView ->
      parent.remove(detailView)
      parent.add(this@ResourceExplorerView)
      parent.revalidate()
      parent.repaint()
      sectionList.selectedValue = previousSelectedValue
    }

    parent.add(detailView)
    parent.revalidate()
    parent.repaint()
    detailView.requestFocusInWindow()
  }

  /**
   * Update the [summaryView] panel.
   * May populate the icon, the metadata and/or a [configuration, value] map.
   */
  private fun updateSummaryPreview() {
    if (summaryView == null) return
    val resourceAssetSet = sectionList.selectedValue as? ResourceAssetSet
    if (resourceAssetSet == null) return

    updateSummaryPreviewIcon()
    summaryView.apply {
      values = resourcesBrowserViewModel.getResourceConfigurationMap(resourceAssetSet)
      data = resourcesBrowserViewModel.getResourceSummaryMap(resourceAssetSet)
      validate()
      repaint()
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
      val previewProvider = resourcesBrowserViewModel.summaryPreviewManager.getPreviewProvider(assetForPreview.type)
      summaryView.icon = if (previewProvider !is DefaultIconProvider) previewProvider.getIcon(
        assetForPreview,
        JBUI.scale(DetailedPreview.PREVIEW_ICON_SIZE),
        JBUI.scale(DetailedPreview.PREVIEW_ICON_SIZE),
        refreshCallback = {
          updateSummaryPreviewIcon()
        }
      ) else null
      summaryView.repaint()
    }
  }

  init {
    DnDManager.getInstance().registerTarget(resourceImportDragTarget, this)

    resourcesBrowserViewModel.resourceChangedCallback = {
      populateResourcesLists()
      populateSearchLinkLabels()
    }
    populateResourcesLists()
    resourcesBrowserViewModel.speedSearch.addChangeListener {
      sectionList.getLists().filterIsInstance<AssetListView>().forEach()
      { assetListView ->
        assetListView.refilter()
        centerPanel.validate()
        centerPanel.repaint()
      }
      populateSearchLinkLabels()
    }

    add(getContentPanel())
    isFocusTraversalPolicyProvider = true
    focusTraversalPolicy = object : LayoutFocusTraversalPolicy() {
      override fun getFirstComponent(p0: Container?): Component {
        return sectionList.getLists().firstOrNull() ?: this@ResourceExplorerView
      }
    }
  }

  private fun getContentPanel(): JPanel {
    val explorerListPanel = JPanel(BorderLayout()).apply {
      add(headerPanel, BorderLayout.NORTH)
      add(centerPanel)
      add(footerPanel, BorderLayout.SOUTH)
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

  private fun getSelectedAssets(): List<Asset> {
    return sectionList.getLists()
      .flatMap { it.selectedValuesList }
      .filterIsInstance<ResourceAssetSet>()
      .flatMap(ResourceAssetSet::assets)
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

    val filter = resourcesBrowserViewModel.speedSearch.filter
    if (filter.isNotBlank()) {
      searchFuture = resourcesBrowserViewModel.getOtherModulesResourceLists()
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
    val search = resourcesBrowserViewModel.speedSearch
    // TODO: Get the facet when the module is being set in ResourceExplorerViewModel by passing the module name instead of the actual facet.
    // I.e: This class should not be fetching module objects.
    val modulesInProject = ModuleManager.getInstance(resourcesBrowserViewModel.facet.module.project).modules
    search.setEnabled(true)
    resourceSections.forEach { section ->
      val filteringModel = NameFilteringListModel(CollectionListModel(section.assetSets), { it.name }, search::shouldBeShowing,
                                                  { StringUtil.notNullize(filter) })
      filteringModel.refilter()
      val resourcesCount = filteringModel.size
      if (resourcesCount > 0) {
        modulesInProject.first { it.name == section.libraryName }.androidFacet?.let { facetToChange ->
          // Create [LinkLabel]s that when clicking them, changes the working module to the module in the given [AndroidFacet].
          moduleSearchView.addLabel(
            "${resourcesCount} resource${if (resourcesCount > 1) "s" else ""} found in '${facetToChange.module.name}'") {
            resourcesBrowserViewModel.facet = facetToChange
          }
        }
      }
    }
    contentSeparator.isVisible = moduleSearchView.isVisible
    centerPanel.validate()
    centerPanel.repaint()
  }

  private fun populateResourcesLists() {
    val selectedValue = sectionList.selectedValue
    val selectedIndices = sectionList.selectedIndices
    updatePending = true

    val future = resourcesBrowserViewModel.getCurrentModuleResourceLists()
      .whenCompleteAsync(BiConsumer { resourceLists, _ ->
        updatePending = false
        displayResources(resourceLists)
        selectIndicesIfNeeded(selectedValue, selectedIndices)
      }, EdtExecutorService.getInstance())

    if (!future.isDone) {
      JobScheduler.getScheduler().schedule(
        { GuiUtils.invokeLaterIfNeeded(this::displayLoading, ModalityState.defaultModalityState()) },
        DELAY_BEFORE_LOADING_STATE,
        TimeUnit.MILLISECONDS)
    }
  }

  private fun displayLoading() {
    if (!updatePending) {
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
    resourcesBrowserViewModel.facet.module.name, null,
    AssetListView(emptyList(), null).apply {
      setPaintBusy(true)
      setEmptyText("Loading...")
      background = this@ResourceExplorerView.background
    })

  private fun createEmptySection() = AssetSection<ResourceAssetSet>(
    resourcesBrowserViewModel.facet.module.name, null,
    AssetListView(emptyList(), null).apply {
      setEmptyText("No ${resourcesBrowserViewModel.selectedTabName.toLowerCase(Locale.US)} available")
      background = this@ResourceExplorerView.background
    })

  private fun selectIndicesIfNeeded(selectedValue: Any?, selectedIndices: List<IntArray?>) {
    val finalFileToSelect = fileToSelect
    if (finalFileToSelect != null) {
      selectAsset(finalFileToSelect)
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
  }

  fun selectAsset(virtualFile: VirtualFile) {
    resourcesBrowserViewModel.resourceTypeIndex = resourcesBrowserViewModel.getTabIndexForFile(virtualFile)
    if (virtualFile.isDirectory) {
      return
    }
    if (updatePending) {
      fileToSelect = virtualFile
    }
    else {
      doSelectAsset(virtualFile)
    }
  }

  private fun doSelectAsset(file: VirtualFile) {
    fileToSelect = null
    sectionList.getLists()
      .filterIsInstance<AssetListView>()
      .forEachIndexed { listIndex, list ->
        for (assetIndex in 0 until list.model.size) {
          if (list.model.getElementAt(assetIndex).designAssets.any { it.file == file }) {
            sectionList.selectedIndex = listIndex to assetIndex
            sectionList.scrollToSelection()
            list.requestFocusInWindow()
            return
          }
        }
      }
  }

  private fun createSection(section: ResourceSection) =
    AssetSection(section.libraryName, section.assetSets.size, AssetListView(section.assetSets, resourcesBrowserViewModel.speedSearch).apply {
      cellRenderer = DesignAssetCellRenderer(resourcesBrowserViewModel.assetPreviewManager)
      dragHandler.registerSource(this)
      addMouseListener(popupHandler)
      addMouseListener(mouseClickListener)
      addKeyListener(keyListener)
      selectionMode = if (multiSelection) ListSelectionModel.MULTIPLE_INTERVAL_SELECTION else ListSelectionModel.SINGLE_SELECTION
      this.addListSelectionListener {
        listeners.forEach { listener ->
          listener.onDesignAssetSetSelected(sectionList.selectedValue as? ResourceAssetSet)
        }
        updateSummaryPreview()
      }
      thumbnailWidth = this@ResourceExplorerView.previewSize
      isGridMode = this@ResourceExplorerView.gridMode
    })

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
    val size: Int?,
    override var list: JList<T>
  ) : Section<T> {

    private var listIsExpanded = true

    override var header: JComponent = createHeaderComponent()

    private fun createHeaderComponent() = JPanel(BorderLayout()).apply {
      isOpaque = false
      val itemNumber = this@AssetSection.size?.let { " ($it)" } ?: ""
      val nameLabel = JBLabel("${this@AssetSection.name}$itemNumber").apply {
        font = SECTION_HEADER_LABEL_FONT
        border = JBUI.Borders.empty(8, 0)
      }
      val linkLabel = LinkLabel(null, AllIcons.Ide.Notification.Collapse, LinkListener<String> { source, _ ->
        // Create a clickable label that toggles the expand/collapse icon every time is clicked, and hides/shows the list in this section.
        source.icon = if (listIsExpanded) AllIcons.Ide.Notification.Expand else AllIcons.Ide.Notification.Collapse
        source.setHoveringIcon(if (listIsExpanded) AllIcons.Ide.Notification.ExpandHover else AllIcons.Ide.Notification.CollapseHover)
        listIsExpanded = !listIsExpanded
        list.isVisible = listIsExpanded
        // Clear selection to avoid interaction issues.
        list.selectionModel.clearSelection()
      }).apply {
        setHoveringIcon(AllIcons.Ide.Notification.CollapseHover)
      }
      add(nameLabel, BorderLayout.WEST)
      add(linkLabel, BorderLayout.EAST)
      border = SECTION_HEADER_BORDER
    }
  }

  override fun getData(dataId: String): Any? {
    return resourcesBrowserViewModel.getData(dataId, getSelectedAssets())
  }

  override fun dispose() {
    DnDManager.getInstance().unregisterTarget(resourceImportDragTarget, this)
    searchFuture?.cancel(true)
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
    : ToggleAction("List mode", "Switch to list mode", StudioIcons.LayoutEditor.Palette.LIST_VIEW),
      DumbAware {

    override fun isSelected(e: AnActionEvent) = !gridMode

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      if (state) {
        ResourceManagerTracking.logSwitchToListMode()
        gridMode = false
        previewSize = LIST_CELL_SIZE
      }
    }
  }

  /**
   * Button to enable the grid view
   */
  private inner class GridModeButton
    : ToggleAction("Grid mode", "Switch to grid mode", StudioIcons.LayoutEditor.Palette.GRID_VIEW),
      DumbAware {

    override fun isSelected(e: AnActionEvent) = gridMode

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      if (state) {
        ResourceManagerTracking.logSwitchToGridMode()
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
