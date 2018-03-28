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
import com.android.tools.idea.resourceExplorer.viewmodel.ModuleResourcesBrowserViewModel
import com.android.tools.idea.resourceExplorer.widget.Section
import com.android.tools.idea.resourceExplorer.widget.SectionList
import com.android.tools.idea.resourceExplorer.widget.SectionListModel
import com.intellij.ui.CollectionListModel
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import javax.swing.*

private const val HEIGHT_WIDTH_RATIO = 3 / 4f
private const val DEFAULT_CELL_WIDTH = 300
private const val COMPACT_MODE_TRIGGER_SIZE = 150
private const val SECTION_CELL_MARGIN = 4
private const val SECTION_CELL_MARGIN_LEFT = 8
private const val COLORED_BORDER_WIDTH = 4
private val SECTION_HEADER_SECONDARY_COLOR = Gray.x66
private val SECTION_HEADER_BORDER = BorderFactory.createCompoundBorder(
  BorderFactory.createEmptyBorder(0, 0, 8, 0),
  JBUI.Borders.customLine(SECTION_HEADER_SECONDARY_COLOR, 0, 0, 1, 0)
)

/**
 * View meant to display [com.android.tools.idea.resourceExplorer.model.DesignAsset] located
 * in the project.
 * It uses an [ModuleResourcesBrowserViewModel] to populates the views
 */
class ModuleResourceBrowser(
  private val resourcesBrowserViewModel: ModuleResourcesBrowserViewModel
) : JPanel(BorderLayout()) {

  var cellWidth = DEFAULT_CELL_WIDTH
    set(value) {
      field = value
      sectionList.getLists().forEach {
        it.setupListUI()
      }
    }

  private val listeners = mutableListOf<SelectionListener>()

  private val sectionListModel: SectionListModel = SectionListModel()
  private val sectionList: SectionList = SectionList(sectionListModel)

  init {

    sectionList.setSectionListCellRenderer(createSectionListCellRenderer())
    populateSectionListModel()

    val mainComponent = sectionList.mainComponent
    mainComponent.border = JBUI.Borders.empty(8)
    add(mainComponent)

    val sections = sectionList.sectionsComponent
    sections.preferredSize = JBUI.size(132, -1)
    sections.border = JBUI.Borders.customLine(JBColor.border(), 0, 0, 0, 1)
    add(sections, BorderLayout.WEST)
  }

  private fun createColorList(): JList<DesignAssetSet> {
    return JList<DesignAssetSet>().apply {
      model = CollectionListModel(resourcesBrowserViewModel.getResourceValues(ResourceType.COLOR))
      cellRenderer = ColorResourceCellRenderer(resourcesBrowserViewModel.facet.module.project, resourcesBrowserViewModel.resourceResolver)
      setupListUI()
    }
  }

  private fun createDrawableList(): JList<DesignAssetSet> {
    return JList<DesignAssetSet>().apply {
      model = CollectionListModel(resourcesBrowserViewModel.getResourceValues(ResourceType.DRAWABLE))
      cellRenderer = DrawableResourceCellRenderer(resourcesBrowserViewModel, { _ -> this.repaint() })
      setupListUI()
    }
  }

  private fun populateSectionListModel() {
    sectionListModel.addSection(AssetSection("Drawable", createDrawableList()))
    sectionListModel.addSection(AssetSection("Colors", createColorList()))
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
    fixedCellHeight = (fixedCellWidth * HEIGHT_WIDTH_RATIO).toInt()
    layoutOrientation = JList.HORIZONTAL_WRAP
    visibleRowCount = 0
    val renderer = cellRenderer
    if (renderer is DesignAssetCellRenderer) {
      renderer.useSmallMargins = cellWidth < COMPACT_MODE_TRIGGER_SIZE
    }
  }

  class AssetSection<T>(
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
}