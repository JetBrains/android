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

import com.android.tools.adtui.TabularLayout
import com.android.tools.adtui.model.Range
import com.android.tools.profilers.cpu.CaptureNode
import com.google.common.annotations.VisibleForTesting
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.table.AbstractTableModel
import com.android.tools.adtui.common.border as BorderColor

/**
 * UI component for presenting capture node details, such as duration, CPU duration etc.
 *
 * @param viewRange if not null, selection will update this range to the selected node; otherwise, row selection is disabled.
 */
class CaptureNodeDetailTable(captureNodes: List<CaptureNode>,
                             captureRange: Range,
                             viewRange: Range? = null,
                             isScrollable: Boolean = false) {
  @VisibleForTesting
  val table: JTable

  /**
   * Container of the underlying [JTable], [javax.swing.JScrollPane] if scrollable, [JPanel] otherwise.
   */
  val component: JComponent

  private val extendedCaptureNodes = captureNodes.map { ExtendedCaptureNode(it) }

  init {
    table = JBTable(CaptureNodeDetailTableModel(captureRange)).apply {
      autoCreateRowSorter = true
      showVerticalLines = true
      showHorizontalLines = false
      columnModel.columnMargin = 10  // align headers and contents
      columnModel.getColumn(Column.START_TIME.ordinal).cellRenderer = TimestampRenderer()
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
    if (isScrollable) {
      component = JBScrollPane(table)
    }
    else {
      component = JPanel(TabularLayout("*", "Fit,Fit")).apply {
        border = JBUI.Borders.customLine(BorderColor, 1)
        isOpaque = false

        // When JTable is put in a container other than JScrollPane, both itself and its header need to be added.
        add(table.tableHeader, TabularLayout.Constraint(0, 0))
        add(table, TabularLayout.Constraint(1, 0))
      }
    }
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
  private inner class CaptureNodeDetailTableModel(private val captureRange: Range) : AbstractTableModel() {
    override fun getRowCount(): Int {
      return extendedCaptureNodes.size
    }

    override fun getColumnCount(): Int {
      return Column.values().size
    }

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
      return Column.values()[columnIndex].getValueFrom(extendedCaptureNodes[rowIndex], captureRange)
    }

    override fun getColumnClass(columnIndex: Int): Class<*> {
      return Column.values()[columnIndex].type
    }

    override fun getColumnName(column: Int): String {
      return Column.values()[column].displayName
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
    },
    NAME("Name", String::class.java) {
      override fun getValueFrom(data: ExtendedCaptureNode, captureRange: Range): Any {
        return data.node.data.name
      }
    },
    WALL_DURATION("Wall Duration", java.lang.Long::class.java) {
      override fun getValueFrom(data: ExtendedCaptureNode, captureRange: Range): Any {
        return data.node.endGlobal - data.node.startGlobal
      }
    },
    SELF_TIME("Self Time", java.lang.Long::class.java) {
      override fun getValueFrom(data: ExtendedCaptureNode, captureRange: Range): Any {
        return data.selfGlobal
      }
    },
    CPU_DURATION("CPU Duration", java.lang.Long::class.java) {
      override fun getValueFrom(data: ExtendedCaptureNode, captureRange: Range): Any {
        return data.node.endThread - data.node.startThread
      }
    },
    CPU_SELF_TIME("CPU Self Time", java.lang.Long::class.java) {
      override fun getValueFrom(data: ExtendedCaptureNode, captureRange: Range): Any {
        return data.selfThread
      }
    };

    abstract fun getValueFrom(data: ExtendedCaptureNode, captureRange: Range): Any
  }
}