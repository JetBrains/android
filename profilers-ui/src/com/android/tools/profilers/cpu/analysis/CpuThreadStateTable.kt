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
import com.android.tools.adtui.common.primaryContentBackground
import com.android.tools.adtui.model.AspectObserver
import com.android.tools.adtui.model.DataSeries
import com.android.tools.adtui.model.Range
import com.android.tools.adtui.ui.HideablePanel
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.cpu.ThreadState
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.diagnostic.Logger
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import java.util.EnumMap
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.table.AbstractTableModel
import kotlin.math.max
import com.android.tools.adtui.common.border as BorderColor

/**
 * UI component for presenting the thread state distribution table. The table aggregates thread state information, e.g. total duration,
 * occurrences.
 *
 * Takes a list of [ThreadState]s and generate [ThreadStateRow]s.
 * For input of
 * | Running | Sleeping | Running |
 * | 00:00   | 00:01    | 00:02   |
 * the table will look like this:
 * | Thread State | Duration | %     | Occurrences |
 * | Running      | 2s       | 66.7% | 2           |
 * | Sleeping     | 1s       | 33.3% | 1           |
 *
 * @param threadStateSeriesList list of [DataSeries], each containing thread states of a thread
 * @param range a range to query thread state on
 */
class CpuThreadStateTable(val profilers: StudioProfilers,
                          val threadStateSeriesList: List<DataSeries<ThreadState>>,
                          val range: Range) {
  val component: JComponent

  @VisibleForTesting
  val table: JTable

  init {
    table = JBTable(ThreadStateTableModel()).apply {
      autoCreateRowSorter = true
      showVerticalLines = true
      showHorizontalLines = false
      columnModel.getColumn(Column.THREAD_STATE.ordinal).cellRenderer = CustomBorderTableCellRenderer()
      columnModel.getColumn(Column.TIME.ordinal).cellRenderer = DurationRenderer()
      columnModel.getColumn(Column.PERCENT.ordinal).cellRenderer = PercentRenderer()
      // Integers are right aligned by default. Cast them to String for left alignment.
      columnModel.getColumn(Column.OCCURRENCES.ordinal).cellRenderer = IntegerAsStringTableCellRender()
    }

    val tableContainer = JPanel(TabularLayout("*", "Fit,Fit")).apply {
      border = JBUI.Borders.customLine(BorderColor, 2)
      isOpaque = false

      // When JTable is put in a container other than JScrollPane, both itself and its header need to be added.
      add(table.tableHeader, TabularLayout.Constraint(0, 0))
      add(table, TabularLayout.Constraint(1, 0))
    }
    val contentBorder = JBUI.Borders.merge(JBUI.Borders.customLine(BorderColor, 1), JBUI.Borders.empty(8, 0, 0, 0), true)
    component = HideablePanel.Builder("States", tableContainer)
      .setPanelBorder(JBUI.Borders.empty())
      .setContentBorder(contentBorder)
      .build()
      .apply {
        background = primaryContentBackground
      }
  }

  /**
   * Table model for representing thread state distribution data.
   */
  private inner class ThreadStateTableModel : AbstractTableModel() {
    private val observer = AspectObserver()
    private var dataRows = listOf<ThreadStateRow>()

    override fun getRowCount(): Int {
      return dataRows.size
    }

    override fun getColumnCount(): Int {
      return Column.values().size
    }

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
      return Column.values()[columnIndex].getValueFrom(dataRows[rowIndex])
    }

    override fun getColumnClass(columnIndex: Int): Class<*> {
      return Column.values()[columnIndex].type
    }

    override fun getColumnName(column: Int): String {
      return Column.values()[column].displayName
    }

    private fun computeDistribution() {
      val threadStateToRow = EnumMap<ThreadState, ThreadStateRow>(ThreadState::class.java)
      val totalDuration = range.length * threadStateSeriesList.size
      // Iterate through each thread.
      threadStateSeriesList.forEach { threadStateSeries ->
        // Temp variable to save t+1 for duration calculation.
        var nextTimestamp = range.max.toLong()
        threadStateSeries.getDataForRange(range)
          // To calculate duration we need to iterate from the end.
          .asReversed()
          .asSequence()
          .filter { it.x < range.max.toLong() }
          .forEach { threadStateDataPoint ->
            threadStateToRow
              // Retrieve the row for the thread state, instantiate one if absent.
              .getOrPut(threadStateDataPoint.value, { ThreadStateRow(threadStateDataPoint.value, totalDuration) })
              .apply {
                // duration = nextTimestamp - currentTimestamp
                // For the first element, currentTimestamp should be Range.min.
                val duration = nextTimestamp - max(range.min.toLong(), threadStateDataPoint.x)
                if (duration >= 0) {
                  this.duration += duration
                  this.occurrences += 1
                  nextTimestamp = threadStateDataPoint.x
                } else {
                  getLogger().warn("Negative duration in thread state table: $duration.")
                }
              }
          }
      }
      // Sort by duration and update table data.
      dataRows = threadStateToRow.values.toList().sortedByDescending { it.duration }
      fireTableDataChanged()
    }

    init {
      range.addDependency(observer).onChange(Range.Aspect.RANGE) { computeDistribution() }
      computeDistribution()
    }
  }

  /**
   * Column definition for the thread state table.
   *
   * @param type use Java number classes (e.g. [java.lang.Long]) to ensure proper sorting in JTable
   */
  private enum class Column(val displayName: String, val type: Class<*>) {
    THREAD_STATE("Thread State", String::class.java) {
      override fun getValueFrom(data: ThreadStateRow): Any {
        return data.threadState.displayName
      }
    },
    TIME("Duration", java.lang.Long::class.java) {
      override fun getValueFrom(data: ThreadStateRow): Any {
        return data.duration
      }
    },
    PERCENT("%", java.lang.Double::class.java) {
      override fun getValueFrom(data: ThreadStateRow): Any {
        return data.percentage
      }
    },
    OCCURRENCES("Occurrences", java.lang.Long::class.java) {
      override fun getValueFrom(data: ThreadStateRow): Any {
        return data.occurrences
      }
    };

    abstract fun getValueFrom(data: ThreadStateRow): Any
  }
}

/**
 * Represents an individual row of the thread state table.
 */
private data class ThreadStateRow(val threadState: ThreadState,
                                  private val totalDuration: Double,
                                  var duration: Long = 0,
                                  var occurrences: Long = 0) {
  val percentage get() = duration / totalDuration
}

private fun getLogger() = Logger.getInstance(CpuThreadStateTable::class.java)