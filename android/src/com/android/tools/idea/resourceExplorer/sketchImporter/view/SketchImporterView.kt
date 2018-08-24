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
import com.android.tools.idea.resourceExplorer.sketchImporter.presenter.SketchImporterPresenter
import com.android.tools.idea.resourceExplorer.view.DrawableResourceCellRenderer
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.openapi.ui.DialogWrapper
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

const val FILTER_EXPORTABLE_CHECKBOX_TEXT = "Only show exportable assets"
const val FILTER_EXPORTABLE_TOOLTIP_TEXT = "Any item that has at least one export format in Sketch is considered exportable"

class SketchImporterView {

  var presenter: SketchImporterPresenter? = null
  private lateinit var disposable: Disposable

  private val previewPanel = JPanel(VerticalFlowLayout())

  private val configurationPanel: JPanel = JPanel(BorderLayout()).apply {
    preferredSize = PANEL_SIZE
    add(JScrollPane(previewPanel).apply {
      border = null
      horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
    }, BorderLayout.CENTER)
  }

  /**
   * Maps page IDs to the panel associated with the page.
   */
  private val assetListMap = HashMap<String, JList<*>>()

  fun addFilterExportableButton(defaultState: Boolean) {
    val filterExportableButton = JCheckBox(FILTER_EXPORTABLE_CHECKBOX_TEXT).apply {
      toolTipText = FILTER_EXPORTABLE_TOOLTIP_TEXT
      isSelected = defaultState
      horizontalTextPosition = JCheckBox.LEFT
      horizontalAlignment = JCheckBox.RIGHT
      addItemListener { event ->
        presenter?.filterExportable(event.stateChange)
      }
    }
    configurationPanel.add(filterExportableButton, BorderLayout.NORTH)
  }

  /**
   * Adds a preview for the [listModel] corresponding to the page named [pageName] of type [pageTypes] [[selectedTypeIndex]])
   */
  fun addAssetPage(pageId: String,
                   pageName: String,
                   pageTypes: Array<String>,
                   selectedTypeIndex: Int,
                   listModel: List<DesignAssetSet>) {
    val pagePanel = JPanel(BorderLayout())
    pagePanel.add(createPageHeader(pageId, pageName, pageTypes, selectedTypeIndex), BorderLayout.NORTH)
    val assetList = createAssetList(listModel)

    if (assetList == null) {
      pagePanel.add(JLabel("No valid assets"))
    }
    else {
      pagePanel.add(assetList)
      assetListMap[pageId] = assetList
    }
    previewPanel.add(pagePanel)
  }

  private fun createAssetList(assetList: List<DesignAssetSet>): JList<*>? {
    val presenter = presenter ?: return null
    if (assetList.isNotEmpty()) {
      return JList<DesignAssetSet>().apply {
        cellRenderer = DrawableResourceCellRenderer(disposable, presenter::fetchImage) { repaint() }
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
   * Creates page header containing the [pageName] and the [JComboBox] with the [pageTypes], where [selectedTypeIndex] is selected.
   */
  private fun createPageHeader(pageId: String,
                               pageName: String,
                               pageTypes: Array<String>,
                               selectedTypeIndex: Int): JComponent {
    return JPanel(BorderLayout()).apply {
      val nameLabel = JBLabel(pageName)
      nameLabel.font = nameLabel.font.deriveFont(24f)
      add(nameLabel)

      val pageTypeList = JComboBox(pageTypes).apply {
        selectedIndex = selectedTypeIndex
        addActionListener { event ->
          val cb = event.source as JComboBox<*>
          presenter?.pageTypeChange(pageId, cb.selectedItem as String)
        }
      }
      add(pageTypeList, BorderLayout.EAST)

      border = PAGE_HEADER_BORDER
    }
  }

  /**
   * Creates the dialog allowing the user to preview and choose which assets they would like to import from the sketch file.
   */
  fun createImportDialog(project: Project) {
    with(DialogBuilder(project)) {
      setCenterPanel(configurationPanel)
      setOkOperation {
        presenter?.importFilesIntoProject()
        dialogWrapper.close(DialogWrapper.OK_EXIT_CODE)
      }
      setTitle("Choose the assets you would like to import")
      showModal(true)
      disposable = dialogWrapper.disposable
    }
  }

  /**
   * Refresh the preview associated with the file with [pageId].
   */
  fun refreshPreview(pageId: String, listModel: List<DesignAssetSet>) {
    val assetList = assetListMap[pageId] ?: return
    @Suppress("UNCHECKED_CAST")
    (assetList.model as CollectionListModel<DesignAssetSet>).replaceAll(listModel)
  }

  /**
   * Clear everything in the preview panel.
   */
  fun clearPreview() {
    previewPanel.removeAll()
  }

  /**
   * Repaint the preview panel.
   */
  fun repaintPreview() {
    previewPanel.repaint()
  }

  /**
   * Revalidate the preview panel.
   */
  fun revalidatePreview() {
    previewPanel.revalidate()
  }
}
