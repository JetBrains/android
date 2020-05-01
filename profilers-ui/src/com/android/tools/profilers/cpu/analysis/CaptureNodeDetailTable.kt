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
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.table.AbstractTableModel
import com.android.tools.adtui.common.border as BorderColor

/**
 * UI component for presenting capture node details, such as duration, CPU duration etc.
 */
class CaptureNodeDetailTable(private val captureNodes: List<CaptureNode>,
                             private val captureRange: Range) {
  val table: JTable
  val component = JPanel(TabularLayout("*", "Fit,Fit"))

  init {
    table = JBTable(CaptureNodeDetailTableModel()).apply {
      autoCreateRowSorter = true
      showVerticalLines = true
      showHorizontalLines = false
      columnModel.getColumn(Column.START_TIME.ordinal).cellRenderer = TimestampRenderer()
      columnModel.getColumn(Column.WALL_DURATION.ordinal).cellRenderer = DurationRenderer()
      columnModel.getColumn(Column.CPU_DURATION.ordinal).cellRenderer = DurationRenderer()
    }
    component.apply {
      border = JBUI.Borders.customLine(BorderColor, 1)
      isOpaque = false

      // When JTable is put in a container other than JScrollPane, both itself and its header need to be added.
      add(table.tableHeader, TabularLayout.Constraint(0, 0))
      add(table, TabularLayout.Constraint(1, 0))
    }
  }

  /**
   * Table model for the capture node detail table.
   */
  private inner class CaptureNodeDetailTableModel : AbstractTableModel() {
    override fun getRowCount(): Int {
      return captureNodes.size
    }

    override fun getColumnCount(): Int {
      return Column.values().size
    }

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
      return Column.values()[columnIndex].getValueFrom(captureNodes[rowIndex], captureRange)
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
   */
  private enum class Column(val displayName: String, val type: Class<*>) {
    START_TIME("Start Time", Long::class.java) {
      override fun getValueFrom(data: CaptureNode, captureRange: Range): Any {
        // Display start time relative to capture start time.
        return data.startGlobal - captureRange.min.toLong()
      }
    },
    WALL_DURATION("Wall Duration", Long::class.java) {
      override fun getValueFrom(data: CaptureNode, captureRange: Range): Any {
        return data.endGlobal - data.startGlobal
      }
    },
    CPU_DURATION("CPU Duration", Long::class.java) {
      override fun getValueFrom(data: CaptureNode, captureRange: Range): Any {
        return data.endThread - data.startThread
      }
    };

    abstract fun getValueFrom(data: CaptureNode, captureRange: Range): Any
  }
}