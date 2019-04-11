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
package com.android.tools.idea.ui.resourcemanager.view

import com.android.tools.idea.concurrent.EdtExecutor
import com.android.tools.idea.ui.resourcemanager.ResourceManagerTracking
import com.android.tools.idea.ui.resourcemanager.model.DesignAsset
import com.android.tools.idea.ui.resourcemanager.model.DesignAssetSet
import com.android.tools.idea.ui.resourcemanager.viewmodel.ProjectResourcesBrowserViewModel
import com.android.tools.idea.ui.resourcemanager.viewmodel.ResourceExplorerViewModel
import com.android.tools.idea.ui.resourcemanager.viewmodel.ResourceSection
import com.android.tools.idea.ui.resourcemanager.widget.Section
import com.android.tools.idea.ui.resourcemanager.widget.SectionList
import com.android.tools.idea.ui.resourcemanager.widget.SectionListModel
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
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.GuiUtils
import com.intellij.ui.JBColor
import com.intellij.ui.PopupHandler
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import icons.StudioIcons
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import java.awt.FlowLayout
import java.awt.Point
import java.awt.event.InputEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.font.TextAttribute
import java.util.concurrent.TimeUnit
import java.util.function.BiConsumer
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTabbedPane
import javax.swing.LayoutFocusTraversalPolicy
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.properties.Delegates


private val MAX_CELL_WIDTH = JBUI.scale(300)
private val LIST_CELL_SIZE = JBUI.scale(60)
private val MIN_CELL_WIDTH = JBUI.scale(150)
private const val DEFAULT_GRID_MODE = false
private val DEFAULT_CELL_WIDTH = LIST_CELL_SIZE
private val SECTION_HEADER_SECONDARY_COLOR = JBColor.border()

private val SECTION_HEADER_BORDER = BorderFactory.createCompoundBorder(
  JBUI.Borders.empty(4, 4, 8, 4),
  JBUI.Borders.customLine(SECTION_HEADER_SECONDARY_COLOR, 0, 0, 1, 0)
)

private val SECTION_LIST_BORDER = JBUI.Borders.empty()

private val SECTION_HEADER_LABEL_FONT = JBUI.Fonts.label().deriveFont(mapOf(
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
 * View meant to display [com.android.tools.idea.ui.resourcemanager.model.DesignAsset] located
 * in the project.
 * It uses an [ProjectResourcesBrowserViewModel] to populates the views
 */
class ResourceExplorerView(
  private val resourcesBrowserViewModel: ResourceExplorerViewModel,
  private val resourceImportDragTarget: ResourceImportDragTarget
) : JPanel(BorderLayout()), Disposable, DataProvider {

  private var updatePending = false

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

  private var gridMode: Boolean by Delegates.observable(DEFAULT_GRID_MODE) { _, _, newValue ->
    sectionList.background = if (newValue) GRID_MODE_BACKGROUND else LIST_MODE_BACKGROUND
    sectionList.getLists().forEach {
      (it as AssetListView).isGridMode = newValue
    }
  }

  private val listeners = mutableListOf<SelectionListener>()
  private val sectionListModel: SectionListModel = SectionListModel()
  private val dragHandler = resourceDragHandler()

  private val headerPanel = JTabbedPane(JTabbedPane.NORTH).apply {
    tabLayoutPolicy = JTabbedPane.SCROLL_TAB_LAYOUT
    resourcesBrowserViewModel.resourceTypes.forEach {
      addTab(it.displayName, null)
    }
    addChangeListener { event ->
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
        previewSize = max(MIN_CELL_WIDTH, min(MAX_CELL_WIDTH, (previewSize * (1 - event.preciseWheelRotation * 0.1)).roundToInt()))
      }
    }
  }

  private val footerPanel = JPanel(BorderLayout()).apply {
    border = JBUI.Borders.customLine(JBColor.border(), 1, 0, 0, 0)

    add(ActionManager.getInstance().createActionToolbar(
      "resourceExplorer",
      createBottomActions(), true).component, BorderLayout.EAST)
  }

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
   * @see openAssets
   */
  private val doubleClickListener = object : MouseAdapter() {
    override fun mouseClicked(e: MouseEvent) {
      if (!(e.clickCount == 2 && e.button == MouseEvent.BUTTON1)) {
        return
      }
      val assetListView = e.source as AssetListView
      val index = assetListView.locationToIndex(e.point)
      if (index >= 0) {
        val designAssetSet = assetListView.model.getElementAt(index)
        openAssets(designAssetSet)
      }
    }
  }

  private val keyListener = object : KeyAdapter() {
    override fun keyPressed(e: KeyEvent) {
      if (KeyEvent.VK_ENTER == e.keyCode) {
        val assetListView = e.source as AssetListView
        openAssets(assetListView.selectedValue)
      }
    }
  }

  /**
   * Replace the content of the view with a [ResourceDetailView] for the provided [designAssetSet].
   */
  private fun openAssets(designAssetSet: DesignAssetSet) {
    if (designAssetSet.designAssets.size == 1) {
      val asset = designAssetSet.designAssets.first()
      ResourceManagerTracking.logAssetOpened(asset.type)
      resourcesBrowserViewModel.openFile(asset)
      return
    }
    showDetailView(designAssetSet)
  }

  private fun showDetailView(designAssetSet: DesignAssetSet) {
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

  init {
    DnDManager.getInstance().registerTarget(resourceImportDragTarget, this)

    resourcesBrowserViewModel.resourceChangedCallback = ::populateResourcesLists
    populateResourcesLists()
    resourcesBrowserViewModel.speedSearch.addChangeListener {
      sectionList.getLists().filterIsInstance<AssetListView>().forEach(AssetListView::refilter)
    }

    add(headerPanel, BorderLayout.NORTH)
    add(sectionList)
    add(footerPanel, BorderLayout.SOUTH)
    isFocusTraversalPolicyProvider = true
    focusTraversalPolicy = object : LayoutFocusTraversalPolicy() {
      override fun getFirstComponent(p0: Container?): Component {
        return sectionList.getLists().firstOrNull() ?: this@ResourceExplorerView
      }
    }
  }

  private fun getSelectedAssets(): List<DesignAsset> {
    return sectionList.getLists()
      .flatMap { it.selectedValuesList }
      .filterIsInstance<DesignAssetSet>()
      .flatMap(DesignAssetSet::designAssets)
  }

  private fun populateResourcesLists() {
    val selectedValue = sectionList.selectedValue
    val selectedIndices = sectionList.selectedIndices
    updatePending = true

    val future = resourcesBrowserViewModel.getResourcesLists()
      .whenCompleteAsync(BiConsumer { resourceLists, _ ->
        updatePending = false
        displayResources(resourceLists)
        selectIndicesIfNeeded(selectedValue, selectedIndices)
      }, EdtExecutor.INSTANCE)

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
      .filterNot { it.assets.isEmpty() }
      .map(this::createSection)
      .toList()
    if (!sections.isEmpty()) {
      sectionListModel.addSections(sections)
    }
    else {
      sectionListModel.addSection(createEmptySection())
    }
  }

  private fun createLoadingSection() = AssetSection<DesignAssetSet>(
    resourcesBrowserViewModel.facet.module.name, null,
    AssetListView(emptyList(), null).apply {
      setPaintBusy(true)
      setEmptyText("Loading...")
      background = this@ResourceExplorerView.background
    })

  private fun createEmptySection() = AssetSection<DesignAssetSet>(
    resourcesBrowserViewModel.facet.module.name, null,
    AssetListView(emptyList(), null).apply {
      setEmptyText("No ${resourcesBrowserViewModel.selectedTabName.toLowerCase()} available")
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
    AssetSection(section.libraryName, section.assets.size, AssetListView(section.assets, resourcesBrowserViewModel.speedSearch).apply {
      cellRenderer = DesignAssetCellRenderer(resourcesBrowserViewModel.assetPreviewManager)
      dragHandler.registerSource(this)
      addMouseListener(popupHandler)
      addMouseListener(doubleClickListener)
      addKeyListener(keyListener)
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
    fun onDesignAssetSetSelected(designAssetSet: DesignAssetSet?)
  }

  private class AssetSection<T>(
    override var name: String,
    val size: Int?,
    override var list: JList<T>
  ) : Section<T> {

    override var header: JComponent = createHeaderComponent()

    private fun createHeaderComponent() = JPanel(FlowLayout(FlowLayout.LEFT, 4, 8)).apply {
      isOpaque = false
      val itemNumber = this@AssetSection.size?.let { " ($it)" } ?: ""
      val nameLabel = JBLabel("${this@AssetSection.name}$itemNumber").apply {
        font = SECTION_HEADER_LABEL_FONT
      }
      add(nameLabel)
      border = SECTION_HEADER_BORDER
    }
  }

  override fun getData(dataId: String): Any? {
    return resourcesBrowserViewModel.getData(dataId, getSelectedAssets())
  }

  override fun dispose() {
    DnDManager.getInstance().unregisterTarget(resourceImportDragTarget, this)
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
