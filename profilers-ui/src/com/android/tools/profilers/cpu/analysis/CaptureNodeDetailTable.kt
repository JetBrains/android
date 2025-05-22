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
import com.android.tools.adtui.model.Range
import com.android.tools.profilers.cpu.CaptureNode
import com.android.tools.profilers.cpu.analysis.CaptureNodeDetailTable.Companion.PAGE_SIZE_VALUES
import com.android.tools.profilers.cpu.analysis.TableUtils.changeRangeOnSelection
import com.android.tools.profilers.cpu.analysis.TableUtils.setColumnRenderers
import com.google.common.annotations.VisibleForTesting
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import javax.swing.JComponent
import javax.swing.JPanel
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
  private val tableModel =
    extendedCaptureNodes.asTableModel(getColumn = { it.getValue(captureRange) },
                                      getClass = Column::type,
                                      getName = Column::displayName,
                                      pageSize = initialPageSize)

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
      setColumnRenderers<Column> { when (it) {
        Column.START_TIME -> TimestampRenderer()
        Column.NAME -> CustomBorderTableCellRenderer()
        Column.WALL_DURATION, Column.WALL_SELF_TIME, Column.CPU_DURATION, Column.CPU_SELF_TIME -> DurationRenderer()
      } }

      if (viewRange != null) {
        changeRangeOnSelection(tableModel, { viewRange }, { it.node.startGlobal.toDouble() }, { it.node.endGlobal.toDouble()})
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
   * Column definition for the capture node details table.
   *
   * @param type use Java number classes (e.g. [java.lang.Long]) to ensure proper sorting in JTable
   */
  private enum class Column(val displayName: String,
                            val type: Class<*>,
                            val getValue: (Range) -> (ExtendedCaptureNode) -> Comparable<*>) {
    START_TIME("Start Time", java.lang.Long::class.java,
      // Display start time relative to capture start time.
               { range -> { data -> data.node.startGlobal - range.min.toLong() } }),
    NAME("Name", String::class.java, { { it.node.data.nameWithSuffix } }),
    WALL_DURATION("Wall Duration", java.lang.Long::class.java, { { it.node.endGlobal - it.node.startGlobal } }),
    WALL_SELF_TIME("Wall Self Time", java.lang.Long::class.java, { { it.selfGlobal } }),
    CPU_DURATION("CPU Duration", java.lang.Long::class.java, { { it.node.endThread - it.node.startThread } }),
    CPU_SELF_TIME("CPU Self Time", java.lang.Long::class.java, { { it.selfThread } });
  }
}