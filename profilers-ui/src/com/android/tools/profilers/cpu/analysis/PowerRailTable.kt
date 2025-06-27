/*
 * Copyright (C) 2023 The Android Open Source Project
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
import com.android.tools.adtui.common.border
import com.android.tools.adtui.common.primaryContentBackground
import com.android.tools.adtui.model.AspectObserver
import com.android.tools.adtui.model.Range
import com.android.tools.adtui.ui.HideablePanel
import com.android.tools.profilers.StringFormattingUtils.formatLongValueWithCommas
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.cpu.analysis.PowerRailTableUtils.POWER_RAIL_TOTAL_VALUE_IN_RANGE_TOOLTIP_MSG
import com.android.tools.profilers.cpu.analysis.PowerRailTableUtils.computeAveragePowerInRange
import com.android.tools.profilers.cpu.analysis.PowerRailTableUtils.computeCumulativeEnergyInRange
import com.android.tools.profilers.cpu.analysis.PowerRailTableUtils.computePowerUsageRange
import com.android.tools.profilers.cpu.systemtrace.PowerCounterData
import com.android.tools.profilers.cpu.systemtrace.PowerRailTrackModel.Companion.POWER_RAIL_UNIT
import com.google.common.annotations.VisibleForTesting
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import icons.StudioIcons
import java.awt.Component
import java.awt.FlowLayout
import java.text.DecimalFormat
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.table.AbstractTableModel
import javax.swing.table.TableCellRenderer
import javax.swing.table.TableRowSorter

/**
 * UI component for a table presenting the average power and cumulative energy consumption per power rail in the user-selected range.
 * Takes a map of power rail names to their respective [PowerCounterData] and generates [PowerRailRow]s.
 * The table will look like this:
 * | Power Rail | Average power (mW) | Cumulative consumption (µWs) |
 * | Foo        | 20.0               | 123,456,789                  |
 * | Bar        | 50.0               | 987,654,321                  |
 *
 * @param powerRailCounters map of [String] to [PowerCounterData], where [PowerCounterData] includes the cumulative power series data
 * @param range a range to query power data on
 */
class PowerRailTable(val profilers: StudioProfilers,
                     val powerRailCounters: Map<String, PowerCounterData>,
                     val selectionRange: Range,
                     val captureRange: Range,
                     title: String = "Power Rails") {
  val component: JComponent
  val observer = AspectObserver()

  @VisibleForTesting
  val table: JTable

  init {
    table = JBTable(PowerRailTableModel()).apply {
      autoCreateRowSorter = true
      showVerticalLines = true
      showHorizontalLines = false

      columnModel.getColumn(Column.RAIL_NAME.ordinal).cellRenderer = CustomBorderTableCellRenderer()
      val avgPowerColumnRenderer = object : CustomBorderTableCellRenderer() {
        override fun getTableCellRendererComponent(table: JTable?,
                                                   value: Any?,
                                                   isSelected: Boolean,
                                                   hasFocus: Boolean,
                                                   row: Int,
                                                   column: Int): Component {
          val decimalFormat = DecimalFormat("#.##")
          val newValue = decimalFormat.format(value)
          return super.getTableCellRendererComponent(table, newValue, isSelected, hasFocus, row, column)
        }
      }
      columnModel.getColumn(Column.AVERAGE.ordinal).cellRenderer = avgPowerColumnRenderer

      // The following is an override of the non-header cells of the Cumulative column's rendering behavior.
      // This override formats the cumulative values with commas to be more readable.
      val cumulativeColumnRenderer = object : CustomBorderTableCellRenderer() {
        override fun getTableCellRendererComponent(table: JTable?,
                                                   value: Any?,
                                                   isSelected: Boolean,
                                                   hasFocus: Boolean,
                                                   row: Int,
                                                   column: Int): Component {
          val newValue = if (value is Long && column == Column.CONSUMPTION.ordinal) formatLongValueWithCommas(value) else value
          return super.getTableCellRendererComponent(table, newValue, isSelected, hasFocus, row, column)
        }
      }
      columnModel.getColumn(Column.CONSUMPTION.ordinal).cellRenderer = cumulativeColumnRenderer
    }

    // The following is an override of the default table header rendering behavior.
    // This override will create a custom JLabel component to host the Cumulative column header with the following properties:
    // (1) Contains an info icon
    // (2) Contains a tooltip warning user about the data's accuracy
    table.tableHeader.columnModel.getColumn(
      Column.CONSUMPTION.ordinal).headerRenderer = TableCellRenderer { table, value, isSelected, hasFocus, row, column ->
      val headerComponent: Component = table.tableHeader.defaultRenderer.getTableCellRendererComponent(table, value, isSelected, hasFocus,
                                                                                                       row, column)
      JLabel().apply {
        // All other table headers are left justified by default, so we left justify this custom table header as well.
        layout = FlowLayout(FlowLayout.LEFT)
        border = JBUI.Borders.empty()

        if (headerComponent is JComponent) {
          // This customer header component does not have the default margin that other table header's get, so we add it.
          headerComponent.border = JBUI.Borders.emptyLeft(2)
        }
        // Add the header label/text.
        add(headerComponent)

        // Add the info icon.
        val iconComp = JLabel()
        iconComp.icon = StudioIcons.Common.INFO_INLINE
        iconComp.border = JBUI.Borders.empty()
        add(iconComp)

        // Add the info tooltip regarding the data's accuracy.
        toolTipText = POWER_RAIL_TOTAL_VALUE_IN_RANGE_TOOLTIP_MSG
      }
    }

    // During creation of the table, we force the consumption column to default its sorting to be in descending order.
    table.setRowSorter(TableRowSorter(table.getModel()))
    val consumptionRowIndex = Column.CONSUMPTION.ordinal
    // Toggle twice as first toggle switches it to ascending order and second toggle switches it to descending order.
    table.rowSorter.toggleSortOrder(consumptionRowIndex)
    table.rowSorter.toggleSortOrder(consumptionRowIndex)

    val tableContainer = JPanel(TabularLayout("*", "Fit,Fit")).apply {
      border = JBUI.Borders.customLine(com.android.tools.adtui.common.border, 2)
      isOpaque = false

      // When JTable is put in a container other than JScrollPane, both itself and its header need to be added.
      add(table.tableHeader, TabularLayout.Constraint(0, 0))
      add(table, TabularLayout.Constraint(1, 0))
    }
    val contentBorder = JBUI.Borders.merge(JBUI.Borders.customLine(border, 1), JBUI.Borders.emptyTop(8), true)
    val timeRange = SummaryDetailsViewBase.formatTimeRangeAsString(selectionRange = selectionRange,
                                                                   relativeZeroPoint = captureRange.min.toLong(), separator = '→')
    val titleWithTimeRange = "<b>$title</b> (Range: $timeRange)"
    component = HideablePanel.Builder(titleWithTimeRange, tableContainer)
      .setPanelBorder(JBUI.Borders.empty())
      .setContentBorder(contentBorder)
      .build()
      .apply {
        background = primaryContentBackground
      }

    // Add listener to RANGE aspect to update the displayed range in the table.
    selectionRange.addDependency(observer).onChange(Range.Aspect.RANGE) {
      val updatedTimeRange = SummaryDetailsViewBase.formatTimeRangeAsString(selectionRange = selectionRange,
                                                                            relativeZeroPoint = captureRange.min.toLong(), separator = '→')
      val updatedTitleWithTimeRange = "<b>$title</b> (Range: $updatedTimeRange)"
      component.setTitle(updatedTitleWithTimeRange)
    }
  }

  /**
   * Table model for representing thread state distribution data.
   */
  private inner class PowerRailTableModel : AbstractTableModel() {
    private val observer = AspectObserver()
    private var dataRows = listOf<PowerRailRow>()

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

    private fun computePowerSummary() {
      dataRows = powerRailCounters.map {
        val powerUsageRange = computePowerUsageRange(it.value.cumulativeData, selectionRange)
        // When the range selection only contains one or zero data points, it is possible for the
        // upper bound's timestamp (x) to be less than or equal to the lower bound's timestamp (x).
        // Thus, in this case, the cumulative value should be 0 as there is no positive difference
        // in start and end power values.
        val consumption = computeCumulativeEnergyInRange(powerUsageRange)
        val average = computeAveragePowerInRange(powerUsageRange)
        PowerRailRow(it.key, consumption, average)
      }
      fireTableDataChanged()
    }

    init {
      selectionRange.addDependency(observer).onChange(Range.Aspect.RANGE) { computePowerSummary() }
      computePowerSummary()
    }
  }

  /**
   * Column definition for the thread state table.
   *
   * @param type use Java number classes (e.g. [java.lang.Long]) to ensure proper sorting in JTable
   */
  private enum class Column(val displayName: String, val type: Class<*>) {
    RAIL_NAME("Rail name", String::class.java) {
      override fun getValueFrom(data: PowerRailRow): String {
        return data.name
      }
    },
    AVERAGE("Average power (mW)", java.lang.Double::class.java) {
      override fun getValueFrom(data: PowerRailRow): Double {
        return data.average
      }
    },
    CONSUMPTION("Cumulative consumption ($POWER_RAIL_UNIT)", java.lang.Long::class.java) {
      override fun getValueFrom(data: PowerRailRow): Long {
        return data.consumption
      }
    };

    abstract fun getValueFrom(data: PowerRailRow): Any
  }
}

/**
 * Represents an individual row of the power usage table.
 */
private data class PowerRailRow(
  val name: String,
  val consumption: Long,
  val average: Double
)