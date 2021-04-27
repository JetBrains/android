package com.android.tools.idea.appinspection.inspectors.workmanager.view

import androidx.work.inspection.WorkManagerInspectorProtocol.Data
import androidx.work.inspection.WorkManagerInspectorProtocol.WorkInfo
import com.android.tools.adtui.common.ColoredIconGenerator
import com.android.tools.idea.appinspection.inspectors.workmanager.model.WorkManagerInspectorClient
import com.android.tools.idea.appinspection.inspectors.workmanager.model.WorkSelectionModel
import com.android.tools.idea.appinspection.inspectors.workmanager.model.WorksTableModel
import com.google.wireless.android.sdk.stats.AppInspectionEvent
import com.intellij.ui.table.JBTable
import java.awt.Component
import java.awt.Rectangle
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.table.DefaultTableCellRenderer

class WorksTableView(tab: WorkManagerInspectorTab,
                     client: WorkManagerInspectorClient,
                     workSelectionModel: WorkSelectionModel) : JBTable(WorksTableModel(client)) {
  private class WorksTableStateCellRenderer : DefaultTableCellRenderer() {
    override fun getTableCellRendererComponent(table: JTable?,
                                               value: Any?,
                                               isSelected: Boolean,
                                               hasFocus: Boolean,
                                               row: Int,
                                               column: Int): Component {

      val state = WorkInfo.State.forNumber(value as Int)
      super.getTableCellRendererComponent(table, state.capitalizedName(), isSelected, hasFocus, row, column)
      icon = if (isSelected) ColoredIconGenerator.generateWhiteIcon(state.icon()) else state.icon()
      return this
    }
  }

  private class WorksTableTimeCellRenderer : DefaultTableCellRenderer() {
    override fun getTableCellRendererComponent(table: JTable?,
                                               value: Any?,
                                               isSelected: Boolean,
                                               hasFocus: Boolean,
                                               row: Int,
                                               column: Int): Component =
      super.getTableCellRendererComponent(table, (value as Long).toFormattedTimeString(), isSelected, hasFocus, row, column)
  }

  private class WorksTableDataCellRenderer : DefaultTableCellRenderer() {
    override fun getTableCellRendererComponent(table: JTable?,
                                               value: Any?,
                                               isSelected: Boolean,
                                               hasFocus: Boolean,
                                               row: Int,
                                               column: Int): Component {
      val pair = value as Pair<*, *>
      val data = pair.second as Data
      val text = if (data.entriesList.isEmpty()) {
        if ((pair.first as WorkInfo.State).isFinished()) {
          foreground = WorkManagerInspectorColors.DATA_TEXT_NULL_COLOR
          WorkManagerInspectorBundle.message("table.data.null")

        }
        else {
          foreground = WorkManagerInspectorColors.DATA_TEXT_AWAITING_COLOR
          WorkManagerInspectorBundle.message("table.data.awaiting")
        }
      }
      else {
        foreground = null
        data.entriesList.joinToString(prefix = "{ ", postfix = " }") { "${it.key}: ${it.value}" }
      }
      super.getTableCellRendererComponent(table, text, isSelected, hasFocus, row, column)
      return this
    }
  }

  private var isFirstTimeLayout = true

  init {
    autoCreateRowSorter = true

    resetDefaultFocusTraversalKeys()
    isStriped = true
    autoResizeMode = AUTO_RESIZE_ALL_COLUMNS
    columnModel.getColumn(WorksTableModel.Column.ORDER.ordinal).cellRenderer = DefaultTableCellRenderer()
    columnModel.getColumn(WorksTableModel.Column.CLASS_NAME.ordinal).cellRenderer = DefaultTableCellRenderer()
    columnModel.getColumn(WorksTableModel.Column.STATE.ordinal).cellRenderer = WorksTableStateCellRenderer()
    columnModel.getColumn(WorksTableModel.Column.TIME_STARTED.ordinal).cellRenderer = WorksTableTimeCellRenderer()
    columnModel.getColumn(WorksTableModel.Column.DATA.ordinal).cellRenderer = WorksTableDataCellRenderer()

    // Update selected work when a new row is selected.
    selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
    selectionModel.addListSelectionListener {
      if (!it.valueIsAdjusting && selectedRow != -1) {
        // Do not open details view here as the selection updates may come from model changes.
        val newSelectedWork = client.lockedWorks { works -> works.getOrNull(convertRowIndexToModel(selectedRow)) }
        if (newSelectedWork != workSelectionModel.selectedWork) {
          workSelectionModel.setSelectedWork(newSelectedWork, WorkSelectionModel.Context.TABLE)
        }
      }
    }

    // Open details view when the table is clicked.
    addMouseListener(object : MouseAdapter() {
      override fun mouseClicked(e: MouseEvent) {
        if (rowAtPoint(e.point) in 0 until rowCount) {
          tab.isDetailsViewVisible = true
          client.tracker.trackWorkSelected(AppInspectionEvent.WorkManagerInspectorEvent.Context.TABLE_CONTEXT)
        }
      }
    })

    addKeyListener(object : KeyAdapter() {
      override fun keyPressed(e: KeyEvent) {
        if (e.keyCode == KeyEvent.VK_SPACE && selectedRow != -1) {
          tab.isDetailsViewVisible = true
          client.tracker.trackWorkSelected(AppInspectionEvent.WorkManagerInspectorEvent.Context.TABLE_CONTEXT)
        }
      }
    })

    // Add row selection after a new work is selected.
    workSelectionModel.registerWorkSelectionListener { work, context ->
      if (work != null && context != WorkSelectionModel.Context.TABLE) {
        val tableModelRow = client.lockedWorks { works ->
          works.indexOfFirst { it.id == work.id }
        }
        if (tableModelRow != -1 && tableModelRow < model.rowCount) {
          val tableRow = convertRowIndexToView(tableModelRow)
          // Check if the row converted from model is visible.
          if (tableRow != -1) {
            addRowSelectionInterval(tableRow, tableRow)
            if (context == WorkSelectionModel.Context.DETAILS) {
              scrollRectToVisible(Rectangle(getCellRect(tableRow, 0, true)))
            }
          }
        }
      }
    }
  }

  override fun doLayout() {
    if (isFirstTimeLayout) {
      isFirstTimeLayout = false
      // Adjust column width before doLayout() gets called for the first time.
      for (column in WorksTableModel.Column.values()) {
        columnModel.getColumn(column.ordinal).preferredWidth = (width * column.widthPercentage).toInt()
      }
    }
    super.doLayout()
  }
}
