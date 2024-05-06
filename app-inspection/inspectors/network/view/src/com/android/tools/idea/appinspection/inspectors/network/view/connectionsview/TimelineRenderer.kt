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
package com.android.tools.idea.appinspection.inspectors.network.view.connectionsview

import com.android.tools.adtui.model.Timeline
import com.android.tools.adtui.stdui.TimelineTable
import com.android.tools.idea.appinspection.inspectors.network.view.ConnectionsStateChart
import java.awt.Component
import javax.swing.JTable
import javax.swing.event.TableModelEvent
import javax.swing.event.TableModelListener

internal class TimelineRenderer(private val table: JTable, timeline: Timeline) :
  TimelineTable.CellRenderer(timeline, true), TableModelListener {
  /**
   * Keep in sync 1:1 with [ConnectionsTableModel.dataList]. When the table asks for the chart to
   * render, it will be converted from model index to view index.
   */
  private val connectionsCharts = mutableListOf<ConnectionsStateChart>()

  init {
    table.model.addTableModelListener(this)
    tableChanged(TableModelEvent(table.model))
  }

  override fun getTableCellRendererComponent(isSelected: Boolean, row: Int): Component {
    val chart = connectionsCharts[table.convertRowIndexToModel(row)]
    chart.colors.setColorIndex(if (isSelected) 1 else 0)
    chart.component.border = TimelineTable.GridBorder(table)
    return chart.component
  }

  override fun tableChanged(e: TableModelEvent) {
    connectionsCharts.clear()
    val model = table.model as ConnectionsTableModel
    for (i in 0 until model.rowCount) {
      val chart = ConnectionsStateChart(model.getConnectionData(i), activeRange)
      chart.setHeightGap(0.3f)
      connectionsCharts.add(chart)
    }
  }
}
