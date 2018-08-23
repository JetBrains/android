/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.resourceExplorer.view

import com.android.resources.ResourceType
import com.android.tools.idea.resourceExplorer.model.DesignAssetSet
import com.android.tools.idea.resourceExplorer.viewmodel.ProjectResourcesBrowserViewModel
import com.android.tools.idea.resourceExplorer.viewmodel.ResourceSection
import com.android.tools.idea.resourceExplorer.widget.Section
import com.android.tools.idea.resourceExplorer.widget.SectionList
import com.android.tools.idea.resourceExplorer.widget.SectionListModel
import com.intellij.ide.dnd.DnDManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.JBMenuItem
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.CollectionListModel
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBMenu
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import icons.StudioIcons
import java.awt.BorderLayout
import java.awt.event.InputEvent
import javax.swing.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt


private val DEFAULT_CELL_WIDTH = JBUI.scale(150)
private val MAX_CELL_WIDTH = JBUI.scale(300)
private val MIN_CELL_WIDTH = JBUI.scale(100)
private val COMPACT_MODE_TRIGGER_SIZE = JBUI.scale(150)
private val SECTION_CELL_MARGIN = JBUI.scale(4)
private val SECTION_CELL_MARGIN_LEFT = JBUI.scale(8)
private val COLORED_BORDER_WIDTH = JBUI.scale(4)
private val SECTION_HEADER_SECONDARY_COLOR = Gray.x66
private val SECTION_HEADER_BORDER = BorderFactory.createCompoundBorder(
  JBUI.Borders.emptyBottom(8),
  JBUI.Borders.customLine(SECTION_HEADER_SECONDARY_COLOR, 0, 0, 1, 0)
)

private val ADD_BUTTON_SIZE = JBUI.size(30)

/**
 * View meant to display [com.android.tools.idea.resourceExplorer.model.DesignAsset] located
 * in the project.
 * It uses an [ProjectResourcesBrowserViewModel] to populates the views
 */
class ResourceExplorerView(
  private val resourcesBrowserViewModel: ProjectResourcesBrowserViewModel,
  private val resourceImportDragTarget: ResourceImportDragTarget
) : JPanel(BorderLayout()), Disposable {

  private var cellWidth = DEFAULT_CELL_WIDTH
    set(value) {
      val boundedValue = min(MAX_CELL_WIDTH, max(MIN_CELL_WIDTH, value))
      if (boundedValue != field) {
        field = boundedValue
        sectionList.getLists().forEach {
          it.setupListUI()
        }
      }
    }

  private val listeners = mutableListOf<SelectionListener>()

  private val sectionListModel: SectionListModel = SectionListModel()
  private val sectionList: SectionList = SectionList(sectionListModel)
  private val dragHandler = resourceDragHandler()

  private val headerPanel = Box.createVerticalBox().apply {
    add(JPanel(BorderLayout()).apply {
      val menuBar = JMenuBar()
      val addButton = JBMenu().apply {
        font = font.deriveFont(JBUI.scaleFontSize(24f))
        preferredSize = ADD_BUTTON_SIZE
        icon = StudioIcons.Common.ADD
      }

      addButton.add(JBMenuItem("Import .sketch file...").apply {
        addActionListener { _ -> resourcesBrowserViewModel.importSketchFile() }
      })

      // TODO add mnemonic, accelerator

      menuBar.add(addButton, BorderLayout.WEST)
      add(menuBar)
    })

    add(JTabbedPane(JTabbedPane.NORTH).apply {
      tabLayoutPolicy = JTabbedPane.SCROLL_TAB_LAYOUT
      resourcesBrowserViewModel.resourceTypes.forEach {
        addTab(it.displayName, null)
      }
      addChangeListener { event ->
        val index = (event.source as JTabbedPane).model.selectedIndex
        resourcesBrowserViewModel.resourceTypeIndex = index
      }
    })
  }

  private val listPanel: JBScrollPane = sectionList.mainComponent.apply {
    border = JBUI.Borders.empty(8)
    horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
    addMouseWheelListener { event ->
      val modifierKey = if (SystemInfo.isMac) InputEvent.META_MASK else InputEvent.CTRL_MASK
      val modifierPressed = (event.modifiers and modifierKey) == modifierKey
      if (modifierPressed) {
        cellWidth = (cellWidth * (1 - event.preciseWheelRotation * 0.1)).roundToInt()
      }
    }
  }

  private val footerPanel = JPanel(BorderLayout()).apply {
    border = JBUI.Borders.customLine(JBColor.border(), 1, 0, 0, 0)

    add(ActionManager.getInstance().createActionToolbar(
      "resourceExplorer",
      DefaultActionGroup(
        ActionButton(StudioIcons.LayoutEditor.Palette.GRID_VIEW) {},
        ActionButton(StudioIcons.LayoutEditor.Palette.LIST_VIEW) {},
        Separator(),
        ActionButton(StudioIcons.Common.ZOOM_OUT) { cellWidth = (cellWidth * 0.9).roundToInt() },
        ActionButton(StudioIcons.Common.ZOOM_IN) { cellWidth = (cellWidth * 1.1).roundToInt() }
      ), true).component, BorderLayout.EAST)
  }

  init {
    DnDManager.getInstance().registerTarget(resourceImportDragTarget, this)

    sectionList.setSectionListCellRenderer(createSectionListCellRenderer())
    resourcesBrowserViewModel.updateCallback = ::populateResourcesLists
    populateResourcesLists()

    add(headerPanel, BorderLayout.NORTH)
    add(listPanel)
    add(footerPanel, BorderLayout.SOUTH)
  }

  private fun populateResourcesLists() {
    sectionListModel.clear()
    resourcesBrowserViewModel.getResourcesLists()
      .filterNot { it.assets.isEmpty() }
      .forEach { (type, libName, assets): ResourceSection ->
        sectionListModel.addSection(AssetSection(libName, JList<DesignAssetSet>().apply {
          model = CollectionListModel(assets)
          cellRenderer = getRendererForType(type, this)
          dragHandler.registerSource(this)
          setupListUI()
        }))
      }
  }

  private fun createSectionListCellRenderer(): ListCellRenderer<Section<*>> {
    return ListCellRenderer { _, value, _, isSelected, _ ->
      val label = JLabel(value.name)
      if (isSelected) {
        label.isOpaque = true
        label.background = UIUtil.getPanelBackground().brighter()
        label.border = BorderFactory.createCompoundBorder(
          JBUI.Borders.customLine(JBColor.BLUE, 0, COLORED_BORDER_WIDTH, 0, 0),
          BorderFactory.createEmptyBorder(SECTION_CELL_MARGIN, SECTION_CELL_MARGIN_LEFT, SECTION_CELL_MARGIN, SECTION_CELL_MARGIN)
        )
      }
      else {
        label.border = BorderFactory.createEmptyBorder(
          SECTION_CELL_MARGIN,
          COLORED_BORDER_WIDTH + SECTION_CELL_MARGIN_LEFT,
          SECTION_CELL_MARGIN,
          SECTION_CELL_MARGIN
        )
      }
      label
    }
  }

  fun addSelectionListener(listener: SelectionListener) {
    listeners += listener
  }

  fun removeSelectionListener(listener: SelectionListener) {
    listeners -= listener
  }

  interface SelectionListener {
    fun onDesignAssetSetSelected(designAssetSet: DesignAssetSet?)
  }

  private fun <T : Any> JList<T>.setupListUI() {
    fixedCellWidth = cellWidth
    layoutOrientation = JList.HORIZONTAL_WRAP
    visibleRowCount = 0
    isOpaque = false
  }

  private fun getRendererForType(type: ResourceType, list: JList<*>): ListCellRenderer<DesignAssetSet> {
    val refreshCallBack = { index: Int ->
      list.repaint(list.getCellBounds(index, index))
    }
    return when (type) {
      ResourceType.DRAWABLE -> DrawableResourceCellRenderer(resourcesBrowserViewModel::getDrawablePreview, refreshCallBack)
      ResourceType.COLOR -> ColorResourceCellRenderer(resourcesBrowserViewModel.facet.module.project,
                                                      resourcesBrowserViewModel.resourceResolver)
      ResourceType.SAMPLE_DATA -> DrawableResourceCellRenderer(resourcesBrowserViewModel::getDrawablePreview, refreshCallBack)
      else -> ListCellRenderer { _, value, _, _, _ ->
        JLabel(value.name)
      }
    }
  }

  private class AssetSection<T>(
    override var name: String,
    override var list: JList<T>
  ) : Section<T> {

    override var header: JComponent = createHeaderComponent()

    private fun createHeaderComponent(): JComponent {

      return JPanel(BorderLayout()).apply {
        val nameLabel = JBLabel(this@AssetSection.name)
        nameLabel.font = nameLabel.font.deriveFont(24f)
        val countLabel = JBLabel(list.model.size.toString())
        countLabel.foreground = SECTION_HEADER_SECONDARY_COLOR
        add(nameLabel)
        add(countLabel, BorderLayout.EAST)

        border = SECTION_HEADER_BORDER
      }
    }
  }

  override fun dispose() {
    DnDManager.getInstance().unregisterTarget(resourceImportDragTarget, this)
  }
}

private class ActionButton(icon: Icon, private val action: () -> Unit) : AnAction(icon), DumbAware {
  override fun actionPerformed(e: AnActionEvent?) {
    action()
  }
}