/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.profilers.cpu.analysis

import com.android.tools.adtui.PaginatedTableView
import com.android.tools.adtui.TabularLayout
import com.android.tools.adtui.model.AbstractPaginatedTableModel
import com.android.tools.adtui.model.Range
import com.android.tools.profilers.cpu.CaptureNode
import com.android.tools.profilers.cpu.analysis.CaptureNodeDetailTable.Companion.PAGE_SIZE_VALUES
import com.google.common.annotations.VisibleForTesting
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.RowSorter
import javax.swing.SortOrder
import javax.swing.table.AbstractTableModel
import com.android.tools.adtui.common.border as BorderColor

/**
 * UI component for presenting capture node details, such as duration, CPU duration etc.
 *
 * @param viewRange if not null, selection will update this range to the selected node; otherwise, row selection is disabled.
 * @param initialPageSize if less than [Int.MAX_VALUE], the table will be paginated. If set to one of [PAGE_SIZE_VALUES], the value will be
 * pre-selected in the dropdown.
 */
class CaptureNodeDetailTable(captureNodes: List<CaptureNode>,
                             captureRange: Range,
                             viewRange: Range? = null,
                             initialPageSize: Int = Int.MAX_VALUE) {
  @VisibleForTesting
  val table: JBTable

  /**
   * Container of the underlying [JBTable], [javax.swing.JScrollPane] if scrollable, [JPanel] otherwise.
   */
  val component: JComponent

  private val extendedCaptureNodes = captureNodes.map { ExtendedCaptureNode(it) }.toMutableList()
  private val tableModel = CaptureNodeDetailTableModel(initialPageSize, captureRange)

  init {
    if (initialPageSize < Int.MAX_VALUE) {
      PaginatedTableView(tableModel, PAGE_SIZE_VALUES).let {
        table = it.table
        component = it.component
      }
    }
    else {
      table = JBTable(tableModel).apply {
        autoCreateRowSorter = true
      }
      component = JPanel(TabularLayout("*", "Fit,Fit")).apply {
        border = JBUI.Borders.customLine(BorderColor, 1)
        isOpaque = false

        // When JTable is put in a container other than JScrollPane, both itself and its header need to be added.
        add(table.tableHeader, TabularLayout.Constraint(0, 0))
        add(table, TabularLayout.Constraint(1, 0))
      }
    }
    table.apply {
      showVerticalLines = true
      showHorizontalLines = false
      emptyText.text = "No events in the selected range"
      columnModel.getColumn(Column.START_TIME.ordinal).cellRenderer = TimestampRenderer()
      columnModel.getColumn(Column.NAME.ordinal).cellRenderer = CustomBorderTableCellRenderer()
      columnModel.getColumn(Column.WALL_DURATION.ordinal).cellRenderer = DurationRenderer()
      columnModel.getColumn(Column.SELF_TIME.ordinal).cellRenderer = DurationRenderer()
      columnModel.getColumn(Column.CPU_DURATION.ordinal).cellRenderer = DurationRenderer()
      columnModel.getColumn(Column.CPU_SELF_TIME.ordinal).cellRenderer = DurationRenderer()

      if (viewRange != null) {
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        selectionModel.addListSelectionListener {
          if (selectedRow >= 0) {
            extendedCaptureNodes[convertRowIndexToModel(selectedRow)].node.let {
              viewRange.set(it.startGlobal.toDouble(), it.endGlobal.toDouble())
            }
          }
        }
      }
      else {
        rowSelectionAllowed = false
        columnSelectionAllowed = false
        cellSelectionEnabled = false
      }
    }
  }

  /**
   * @property PAGE_SIZE_VALUES list of pre-populated page sizes.
   */
  companion object {
    val PAGE_SIZE_VALUES = arrayOf(10, 25, 50, 100)
  }

  /**
   * Class to pre-computes and cache self time values of a capture node, since [AbstractTableModel.getValueAt] is called very frequently,
   * e.g. whenever mouse hovers a cell.
   */
  private data class ExtendedCaptureNode(val node: CaptureNode) {
    val selfGlobal = node.endGlobal - node.startGlobal - node.children.asSequence().map { it.endGlobal - it.startGlobal }.sum()
    val selfThread = node.endThread - node.startThread - node.children.asSequence().map { it.endThread - it.startThread }.sum()
  }

  /**
   * Table model for the capture node detail table.
   */
  private inner class CaptureNodeDetailTableModel(pageSize: Int, private val captureRange: Range) : AbstractPaginatedTableModel(pageSize) {
    override fun getDataSize(): Int {
      return extendedCaptureNodes.size
    }

    override fun getDataValueAt(dataIndex: Int, columnIndex: Int): Any {
      return Column.values()[columnIndex].getValueFrom(extendedCaptureNodes[dataIndex], captureRange)
    }

    override fun sortData(sortKeys: List<RowSorter.SortKey>) {
      if (sortKeys.isNotEmpty()) {
        val sortKey = sortKeys[0]
        if (sortKey.sortOrder == SortOrder.ASCENDING) {
          extendedCaptureNodes.sortWith(getColumnComparator(sortKey.column))
        }
        else {
          extendedCaptureNodes.sortWith(getColumnComparator(sortKey.column).reversed())
        }
      }
    }

    override fun getColumnCount(): Int {
      return Column.values().size
    }

    override fun getColumnClass(columnIndex: Int): Class<*> {
      return Column.values()[columnIndex].type
    }

    override fun getColumnName(column: Int): String {
      return Column.values()[column].displayName
    }

    private fun getColumnComparator(columnIndex: Int): Comparator<ExtendedCaptureNode> {
      return Column.values()[columnIndex].getComparator()
    }
  }

  /**
   * Column definition for the capture node details table.
   *
   * @param type use Java number classes (e.g. [java.lang.Long]) to ensure proper sorting in JTable
   */
  private enum class Column(val displayName: String, val type: Class<*>) {
    START_TIME("Start Time", java.lang.Long::class.java) {
      override fun getValueFrom(data: ExtendedCaptureNode, captureRange: Range): Any {
        // Display start time relative to capture start time.
        return data.node.startGlobal - captureRange.min.toLong()
      }

      override fun getComparator(): Comparator<ExtendedCaptureNode> {
        return compareBy { it.node.startGlobal }
      }
    },
    NAME("Name", String::class.java) {
      override fun getValueFrom(data: ExtendedCaptureNode, captureRange: Range): Any {
        return data.node.data.name
      }

      override fun getComparator(): Comparator<ExtendedCaptureNode> {
        return compareBy { it.node.data.name }
      }
    },
    WALL_DURATION("Wall Duration", java.lang.Long::class.java) {
      override fun getValueFrom(data: ExtendedCaptureNode, captureRange: Range): Any {
        return data.node.endGlobal - data.node.startGlobal
      }

      override fun getComparator(): Comparator<ExtendedCaptureNode> {
        return compareBy { it.node.endGlobal - it.node.startGlobal }
      }
    },
    SELF_TIME("Self Time", java.lang.Long::class.java) {
      override fun getValueFrom(data: ExtendedCaptureNode, captureRange: Range): Any {
        return data.selfGlobal
      }

      override fun getComparator(): Comparator<ExtendedCaptureNode> {
        return compareBy { it.selfGlobal }
      }
    },
    CPU_DURATION("CPU Duration", java.lang.Long::class.java) {
      override fun getValueFrom(data: ExtendedCaptureNode, captureRange: Range): Any {
        return data.node.endThread - data.node.startThread
      }

      override fun getComparator(): Comparator<ExtendedCaptureNode> {
        return compareBy { it.node.endThread - it.node.startThread }
      }
    },
    CPU_SELF_TIME("CPU Self Time", java.lang.Long::class.java) {
      override fun getValueFrom(data: ExtendedCaptureNode, captureRange: Range): Any {
        return data.selfThread
      }

      override fun getComparator(): Comparator<ExtendedCaptureNode> {
        return compareBy { it.selfThread }
      }
    };

    abstract fun getValueFrom(data: ExtendedCaptureNode, captureRange: Range): Any
    abstract fun getComparator(): Comparator<ExtendedCaptureNode>
  }
}