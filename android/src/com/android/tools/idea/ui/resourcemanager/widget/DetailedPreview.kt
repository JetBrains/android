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
package com.android.tools.idea.ui.resourcemanager.widget

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Dimension
import java.util.Vector
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.DefaultTableModel
import kotlin.properties.Delegates

private const val PREVIEW_BOTTOM_MARGIN = 10

class DetailedPreview : JPanel(null) {
  companion object {
    const val PREVIEW_ICON_SIZE = 200
  }

  private val label = JBLabel(null, JBLabel.CENTER)
  private val valuesTableModel = object : DefaultTableModel(0, 2) {
    override fun isCellEditable(row: Int, column: Int) = false

    override fun getColumnClass(columnIndex: Int): Class<*> =
      if (columnIndex == 1) String::class.java else super.getColumnClass(columnIndex)
  }
  private val tableModel = object : DefaultTableModel(0, 2) {
    override fun isCellEditable(row: Int, column: Int) = false
  }

  /**
   * A metadata map. Displays the values in the form of ["Key:" "Value"].
   */
  var data: Map<String, String> by Delegates.observable(emptyMap()) { _, _, newValue ->
    tableModel.rowCount = newValue.size
    tableModel.setTableData(newValue)
    metadataTable.revalidate()
    metadataTable.repaint()
  }

  /**
   * A configuration/value map. Displays the map on a table with a header, reads "Configuration" for the keys, and "Value" for the values.
   */
  var values: Map<String, String> by Delegates.observable(emptyMap()) { _, _, newValue ->
    valuesContainer.isVisible = newValue.isNotEmpty()
    valuesTableModel.setTableData(newValue, "Configuration", "Value")

    // Make the scrollpane take the size of its content, instead of trying to take available space.
    // Once the layout takes the all the space, the scrollpane should allow to scroll.
    // TODO: Find a way to achieve this with layout managers instead.
    val tableHeight = valuesTable.tableHeader.preferredSize.height + (valuesTable.rowHeight * valuesTable.rowCount)
    valuesContainer.maximumSize = Dimension(Int.MAX_VALUE, tableHeight)
    valuesContainer.preferredSize = Dimension(valuesTable.preferredScrollableViewportSize.width, tableHeight)
    valuesContainer.revalidate()
    valuesContainer.repaint()
  }

  /**
   * An icon to preview. Will try to paint the icon centered over a chessboard with size: [PREVIEW_ICON_SIZE] (won't attempt to scale).
   */
  var icon: Icon? = null
    set(value) {
      field = value
      label.icon = value
      iconPreviewContainer.isVisible = value != null
    }

  private val iconPreviewContainer = ChessBoardPanel(BorderLayout()).apply {
    isVisible = false
    alignmentX = LEFT_ALIGNMENT
    border = BorderFactory.createCompoundBorder(JBUI.Borders.emptyBottom(PREVIEW_BOTTOM_MARGIN),
                                                JBUI.Borders.customLine(JBColor.border(), 1))
    showChessboard = true
    val previewContainerHeight = PREVIEW_ICON_SIZE + PREVIEW_BOTTOM_MARGIN
    preferredSize = JBUI.size(PREVIEW_ICON_SIZE, previewContainerHeight)
    minimumSize = JBUI.size(0, previewContainerHeight)
    maximumSize = JBUI.size(2000, previewContainerHeight)
    add(label)
  }

  private val metadataTable = JBTable(tableModel).apply {
    alignmentX = LEFT_ALIGNMENT
    rowHeight = JBUI.scale(28)
    rowMargin = JBUI.scale(8)
    background = UIUtil.getPanelBackground()
    setShowGrid(false)
  }

  private val valuesTable = JBTable(valuesTableModel).apply {
    alignmentX = LEFT_ALIGNMENT
    fillsViewportHeight = false
    tableHeader.reorderingAllowed = false
    (tableHeader.defaultRenderer as? DefaultTableCellRenderer)?.let { headerRenderer ->
      headerRenderer.horizontalAlignment = SwingConstants.LEFT
    }
    setDefaultRenderer(String::class.java, I18nStringCellRenderer())
    rowHeight = JBUI.scale(28)
    rowMargin = JBUI.scale(8)
    background = JBColor.white
    showVerticalLines = true
    showHorizontalLines = false
  }

  private val valuesContainer =
    JBScrollPane(valuesTable).apply {
      alignmentX = LEFT_ALIGNMENT
      border = JBUI.Borders.empty()
      isVisible = false
      isOpaque = false
    }

  init {
    layout = BoxLayout(this, BoxLayout.Y_AXIS)
    add(JBLabel("Preview").apply {
      horizontalAlignment = JBLabel.LEFT
      alignmentX = LEFT_ALIGNMENT
      border = JBUI.Borders.empty(8, 0)
    })
    add(iconPreviewContainer)
    add(metadataTable)
    add(Box.createRigidArea(JBDimension(0, 10)))
    add(valuesContainer)
  }
}

private fun DefaultTableModel.setTableData(dataMap: Map<String, String>, firstColumnName: String = "", secondColumnName: String = "") {
  val hasHeader = firstColumnName.isNotBlank() || secondColumnName.isNotBlank()
  this.setDataVector(
    dataMap
      .map { (key, value) ->
        Vector<String>(2).apply {
          addElement("$key${if(hasHeader) "" else ":"}")
          addElement(value)
        }
      }.toCollection(Vector<Vector<String>>(dataMap.size)), Vector<String>(listOf(firstColumnName, secondColumnName)))
}