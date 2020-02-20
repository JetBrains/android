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
package com.android.tools.idea.sqlite.ui.tableView

import com.android.tools.adtui.common.primaryContentBackground
import com.android.tools.adtui.stdui.CommonButton
import com.android.tools.idea.sqlite.model.SqliteColumn
import com.android.tools.idea.sqlite.model.SqliteRow
import com.android.tools.idea.sqlite.ui.notifyError
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.ColoredTableCellRenderer
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.FlowLayout
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableCellRenderer

/**
 * Abstraction on the UI component used to display tables.
 */
class TableViewImpl : TableView {
  companion object {
    private const val tableIsEmptyText = "Table is empty"
    private const val loadingText = "Loading data..."
  }
  private val listeners = mutableListOf<TableView.Listener>()
  private val pageSizeDefaultValues = listOf(5, 10, 20, 25, 50)
  private var isLoading = false

  private lateinit var columns: List<SqliteColumn>

  private val rootPanel = JPanel(BorderLayout())
  override val component: JComponent = rootPanel

  private val readOnlyLabel = JLabel("Results are read-only")

  private val firstRowsPageButton = CommonButton("First", AllIcons.Actions.Play_first)
  private val lastRowsPageButton = CommonButton("Last", AllIcons.Actions.Play_last)

  private val previousRowsPageButton = CommonButton("Previous", AllIcons.Actions.Play_back)
  private val nextRowsPageButton = CommonButton("Next", AllIcons.Actions.Play_forward)

  private val pageSizeComboBox = ComboBox<Int>()

  private val refreshButton = CommonButton("Refresh table", AllIcons.Actions.Refresh)

  private val table = JBTable()

  private val tableActionsPanel = JPanel(FlowLayout(FlowLayout.LEFT))

  init {
    val centerPanel = JPanel(BorderLayout())
    val southPanel = JPanel(BorderLayout())
    rootPanel.add(tableActionsPanel, BorderLayout.NORTH)
    rootPanel.add(centerPanel, BorderLayout.CENTER)
    rootPanel.add(southPanel, BorderLayout.SOUTH)

    tableActionsPanel.name = "table-actions-panel"

    readOnlyLabel.name = "read-only-label"
    readOnlyLabel.border = BorderFactory.createEmptyBorder(0, 4, 0, 0)
    southPanel.add(readOnlyLabel, BorderLayout.WEST)
    val pagingControlsPanel = JPanel(FlowLayout(FlowLayout.LEFT))
    southPanel.add(pagingControlsPanel, BorderLayout.EAST)

    firstRowsPageButton.toolTipText = "First"
    pagingControlsPanel.add(firstRowsPageButton)
    firstRowsPageButton.addActionListener { listeners.forEach { it.loadFirstRowsInvoked() }}

    previousRowsPageButton.toolTipText = "Previous"
    pagingControlsPanel.add(previousRowsPageButton)
    previousRowsPageButton.addActionListener { listeners.forEach { it.loadPreviousRowsInvoked() }}

    pageSizeComboBox.isEditable = true
    pageSizeDefaultValues.forEach { pageSizeComboBox.addItem(it) }
    pagingControlsPanel.add(pageSizeComboBox)
    pageSizeComboBox.addActionListener { listeners.forEach { it.rowCountChanged((pageSizeComboBox.selectedItem as Int)) } }

    nextRowsPageButton.toolTipText = "Next"
    pagingControlsPanel.add(nextRowsPageButton)
    nextRowsPageButton.addActionListener { listeners.forEach { it.loadNextRowsInvoked() }}

    lastRowsPageButton.toolTipText = "Last"
    pagingControlsPanel.add(lastRowsPageButton)
    lastRowsPageButton.addActionListener { listeners.forEach { it.loadLastRowsInvoked() }}

    refreshButton.toolTipText = "Sync table"
    tableActionsPanel.add(refreshButton)
    refreshButton.addActionListener{ listeners.forEach { it.refreshDataInvoked() } }

    table.background = primaryContentBackground
    table.emptyText.text = tableIsEmptyText
    table.tableHeader.defaultRenderer = MyTableHeaderRenderer()
    table.tableHeader.reorderingAllowed = false
    table.tableHeader.addMouseListener(object : MouseAdapter() {
      override fun mouseClicked(e: MouseEvent) {
        if (isLoading) return

        val columnIndex = table.columnAtPoint(e.point)
        if (columnIndex <= 0) return

        listeners.forEach { it.toggleOrderByColumnInvoked(columns[columnIndex - 1]) }
      }
    })

    table.setDefaultRenderer(String::class.java, MyColoredTableCellRenderer())

    val scrollPane = JBScrollPane(table)

    scrollPane.addComponentListener(object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent) {
        setAutoResizeMode()
      }
    })

    centerPanel.add(scrollPane, BorderLayout.CENTER)
  }

  override var isTableActionsRowVisible: Boolean = true
    set(value) { tableActionsPanel.isVisible = value; field = value }

  override fun showPageSizeValue(maxRowCount: Int) {
    pageSizeComboBox.selectedItem = maxRowCount
  }

  override fun resetView() {
    table.model = MyTableModel(emptyList())
  }

  override fun removeRows() {
    (table.model as MyTableModel).removeAllRows()
  }

  override fun startTableLoading() {
    table.emptyText.text = loadingText
    isLoading = true
  }

  override fun showTableColumns(columns: List<SqliteColumn>) {
    this.columns = columns
    table.model = MyTableModel(columns)

    table.columnModel.getColumn(0).maxWidth = JBUI.scale(60)
    table.columnModel.getColumn(0).resizable = false

    setAutoResizeMode()
  }

  override fun showTableRowBatch(rows: List<SqliteRow>) {
    (table.model as MyTableModel).addRows(rows)
  }

  override fun stopTableLoading() {
    table.emptyText.text = tableIsEmptyText

    table.setPaintBusy(false)

    isLoading = false
  }

  override fun reportError(message: String, t: Throwable?) {
    notifyError(message, t)
  }

  override fun setFetchPreviousRowsButtonState(enable: Boolean) {
    previousRowsPageButton.isEnabled = enable
    firstRowsPageButton.isEnabled = enable
  }

  override fun setFetchNextRowsButtonState(enable: Boolean) {
    nextRowsPageButton.isEnabled = enable
    lastRowsPageButton.isEnabled = enable
  }

  override fun setEditable(isEditable: Boolean) {
    (table.model as MyTableModel).isEditable = isEditable
    readOnlyLabel.isVisible = !isEditable
  }

  override fun addListener(listener: TableView.Listener) {
    listeners.add(listener)
  }

  override fun removeListener(listener: TableView.Listener) {
    listeners.remove(listener)
  }

  /**
   * Changes the auto resize mode of JTable so that if the preferred width of the table is less than the width of the parent,
   * the table is set to AUTO_RESIZE_SUBSEQUENT_COLUMNS, to fill the parent's width.
   * Otherwise, if the preferred width of the table is greater than or equal to the width of the parent,
   * horizontal scrolling is enabled with AUTO_RESIZE_OFF.
   */
  private fun setAutoResizeMode() {
    if (table.preferredSize.width < table.parent.width) {
      table.autoResizeMode = JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS
    }
    else {
      table.autoResizeMode = JTable.AUTO_RESIZE_OFF
    }
  }

  private class MyTableHeaderRenderer : TableCellRenderer {
    private val columnNameLabel = DefaultTableCellRenderer()
    private val panel = JPanel(BorderLayout())

    init {
      val sortIcon = DefaultTableCellRenderer()
      sortIcon.icon = AllIcons.General.ArrowSplitCenterV
      columnNameLabel.icon = AllIcons.Nodes.DataColumn
      columnNameLabel.iconTextGap = 8

      panel.background = Color(0, 0, 0, 0)
      panel.add(columnNameLabel, BorderLayout.CENTER)
      panel.add(sortIcon, BorderLayout.EAST)
    }

    override fun getTableCellRendererComponent(
      table: JTable,
      value: Any,
      selected: Boolean,
      focused: Boolean,
      viewRowIndex: Int,
      viewColumnIndex: Int
    ): Component {
      if (viewColumnIndex == 0) {
        columnNameLabel.icon = null
        (panel.getComponent(panel.componentCount - 1) as DefaultTableCellRenderer).icon = null
      } else {
        columnNameLabel.icon = AllIcons.Nodes.DataColumn
        (panel.getComponent(panel.componentCount - 1) as DefaultTableCellRenderer).icon = AllIcons.General.ArrowSplitCenterV
      }

      columnNameLabel.text = value as String
      return panel
    }
  }

  private class MyColoredTableCellRenderer : ColoredTableCellRenderer() {
    override fun customizeCellRenderer(
      table: JTable?,
      value: Any?,
      selected: Boolean,
      focused: Boolean,
      viewRowIndex: Int,
      viewColumnIndex: Int
    ) {
      if (viewColumnIndex == 0) {
        background = EditorColorsManager.getInstance().globalScheme.getColor(EditorColors.GUTTER_BACKGROUND)
      } else {
        background = primaryContentBackground
      }

      append(value as String)
    }
  }

  private inner class MyTableModel(val columns: List<SqliteColumn>) : AbstractTableModel() {

    private val rows = mutableListOf<SqliteRow>()
    var isEditable = false

    fun addRows(newRows: List<SqliteRow>) {
      val startIndex = rows.size
      val endIndex = startIndex + newRows.size
      rows.addAll(newRows)
      fireTableRowsInserted(startIndex, endIndex)
    }

    fun removeAllRows() {
      val endIndex = rows.size
      rows.clear()
      fireTableRowsDeleted(0, endIndex)
    }

    override fun getColumnName(modelColumnIndex: Int): String {
      return if (modelColumnIndex == 0) {
        ""
      } else {
        columns[modelColumnIndex - 1].name
      }
    }

    override fun getColumnClass(modelColumnIndex: Int) = String::class.java

    override fun getColumnCount() = columns.size + 1

    override fun getRowCount() = rows.size

    override fun getValueAt(modelRowIndex: Int, modelColumnIndex: Int): String {
      return if (modelColumnIndex == 0) {
        (modelRowIndex + 1).toString()
      } else {
        rows[modelRowIndex].values[modelColumnIndex - 1].value.toString()
      }
    }

    override fun setValueAt(newValue: Any, modelRowIndex: Int, modelColumnIndex: Int) {
      assert(modelColumnIndex > 0) { "Setting value of column at index 0 is not allowed" }

      val row = rows[modelRowIndex]
      val column = columns[modelColumnIndex - 1]
      listeners.forEach { it.updateCellInvoked(row, column, newValue) }
    }

    override fun isCellEditable(modelRowIndex: Int, modelColumnIndex: Int) = modelColumnIndex != 0 && isEditable
  }
}