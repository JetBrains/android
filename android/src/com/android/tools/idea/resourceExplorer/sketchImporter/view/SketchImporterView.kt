/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.resourceExplorer.sketchImporter.view

import com.android.tools.idea.resourceExplorer.model.DesignAssetSet
import com.android.tools.idea.resourceExplorer.sketchImporter.presenter.PagePresenter
import com.android.tools.idea.resourceExplorer.sketchImporter.presenter.SketchImporterPresenter
import com.android.tools.idea.resourceExplorer.view.DrawableResourceCellRenderer
import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.ui.CollectionListModel
import com.intellij.ui.Gray
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.BorderFactory
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.ListSelectionModel


private val PAGE_HEADER_SECONDARY_COLOR = Gray.x66
private val PAGE_HEADER_BORDER = BorderFactory.createCompoundBorder(
  BorderFactory.createEmptyBorder(0, 0, 8, 0),
  JBUI.Borders.customLine(PAGE_HEADER_SECONDARY_COLOR, 0, 0, 1, 0)
)
private val PANEL_SIZE = JBUI.size(600, 400)
private val ASSET_FIXED_WIDTH = JBUI.scale(150)
private val ASSET_FIXED_HEIGHT = JBUI.scale(150)

const val IMPORT_DIALOG_TITLE = "Choose the assets you would like to import"
const val FILTER_EXPORTABLE_CHECKBOX_TEXT = "Only show exportable assets"
const val FILTER_EXPORTABLE_TOOLTIP_TEXT = "Any item that has at least one export format in Sketch is considered exportable"
const val NO_VALID_ASSETS_TEXT = "No valid assets"

class SketchImporterView : Disposable, JPanel(BorderLayout()) {

  override fun dispose() {}

  lateinit var presenter: SketchImporterPresenter
  private val pageViews = mutableListOf<PageView>()

  private val pagesPanel = JPanel(VerticalFlowLayout())

  init {
    preferredSize = PANEL_SIZE
    add(JScrollPane(pagesPanel).apply {
      border = null
      horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
    }, BorderLayout.CENTER)
  }

  fun addFilterExportableButton(defaultState: Boolean) {
    val filterExportableButton = JCheckBox(FILTER_EXPORTABLE_CHECKBOX_TEXT).apply {
      toolTipText = FILTER_EXPORTABLE_TOOLTIP_TEXT
      isSelected = defaultState
      horizontalTextPosition = JCheckBox.LEFT
      horizontalAlignment = JCheckBox.RIGHT
      addItemListener { event ->
        presenter.filterExportable(event.stateChange)
      }
    }
    add(filterExportableButton, BorderLayout.NORTH)
  }

  /**
   * Add a new [PageView] to the [SketchImporterView], associating it to the [pagePresenter].
   */
  fun createPageView(pagePresenter: PagePresenter) {
    val pageView = PageView(pagePresenter,
                            DrawableResourceCellRenderer(this, pagePresenter::fetchImage) { pagesPanel.repaint() })
    pagePresenter.view = pageView
    pageViews.add(pageView)
  }

  /**
   * Add the panels associated with the [PageView]s to the preview panel.
   */
  fun paintPages() {
    pageViews.forEach {
      pagesPanel.add(it)
    }
  }
}

class PageView(private val presenter: PagePresenter,
               private val drawableResourceCellRenderer: DrawableResourceCellRenderer
) : JPanel(BorderLayout()) {
  var assets: JList<*>? = null

  /**
   * Create/refresh the preview panel associated with the page.
   */
  fun refreshPreviewPanel(pageName: String,
                          pageTypes: Array<String>,
                          selectedTypeIndex: Int,
                          assetList: List<DesignAssetSet>) {
    removeAll()
    add(createHeader(pageName, pageTypes, selectedTypeIndex), BorderLayout.NORTH)
    val jList = createPreviewsList(assetList)

    if (jList == null) add(JLabel(NO_VALID_ASSETS_TEXT)) else add(jList)
    revalidate()
    repaint()
  }

  /**
   * Create a [JList] with the rendering of the [assetList] assets.
   */
  private fun createPreviewsList(assetList: List<DesignAssetSet>): JList<*>? {
    if (assetList.isNotEmpty()) {
      return JList<DesignAssetSet>().apply {
        cellRenderer = drawableResourceCellRenderer
        fixedCellWidth = ASSET_FIXED_WIDTH
        fixedCellHeight = ASSET_FIXED_HEIGHT
        selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
        visibleRowCount = -1
        layoutOrientation = JList.HORIZONTAL_WRAP
        model = CollectionListModel(assetList)
      }
    }
    return null
  }

  /**
   * Create a page header containing the [pageName] and the [JComboBox] with the [pageTypes], where [selectedTypeIndex] is selected.
   */
  private fun createHeader(pageName: String, pageTypes: Array<String>, selectedTypeIndex: Int): JComponent {
    return JPanel(BorderLayout()).apply {
      val nameLabel = JBLabel(pageName)
      nameLabel.font = nameLabel.font.deriveFont(24f)
      add(nameLabel)

      val pageTypeList = JComboBox(pageTypes).apply {
        selectedIndex = selectedTypeIndex
        addActionListener { event ->
          val cb = event.source as JComboBox<*>
          presenter.pageTypeChange(cb.selectedItem as String)
        }
      }
      add(pageTypeList, BorderLayout.EAST)

      border = PAGE_HEADER_BORDER
    }
  }
}
