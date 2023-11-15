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

import com.android.tools.adtui.TooltipComponent
import com.android.tools.adtui.TooltipView
import com.android.tools.adtui.model.AspectObserver
import com.android.tools.adtui.stdui.TimelineTable
import com.android.tools.adtui.stdui.TooltipLayeredPane
import com.android.tools.adtui.table.ConfigColumnTableAspect
import com.android.tools.idea.appinspection.inspectors.network.model.NetworkInspectorAspect
import com.android.tools.idea.appinspection.inspectors.network.model.NetworkInspectorModel
import com.android.tools.idea.appinspection.inspectors.network.view.NetworkInspectorViewState
import com.android.tools.idea.appinspection.inspectors.network.view.connectionsview.ConnectionColumn.TIMELINE
import com.android.tools.idea.appinspection.inspectors.network.view.constants.DEFAULT_BACKGROUND
import com.android.tools.idea.appinspection.inspectors.network.view.constants.ROW_HEIGHT_PADDING
import com.android.tools.idea.appinspection.inspectors.network.view.constants.TOOLTIP_BACKGROUND
import com.android.tools.idea.appinspection.inspectors.network.view.constants.TOOLTIP_BORDER
import com.android.tools.idea.appinspection.inspectors.network.view.constants.TOOLTIP_TEXT
import com.android.tools.idea.appinspection.inspectors.network.view.rules.registerEnterKeyAction
import java.awt.KeyboardFocusManager
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JTable
import javax.swing.JTextPane
import javax.swing.ListSelectionModel
import javax.swing.event.ListSelectionEvent
import javax.swing.table.TableCellRenderer

/**
 * This class responsible for displaying table of connections information (e.g. url, duration,
 * timeline) for network inspector. Each row in the table represents a single connection.
 */
class ConnectionsView(
  private val model: NetworkInspectorModel,
  private val parentPane: TooltipLayeredPane
) : AspectObserver() {

  private val tableModel = ConnectionsTableModel(model.selectionRangeDataFetcher)
  private val connectionsTable: JTable

  val component: JComponent
    get() = connectionsTable

  init {
    connectionsTable =
      TimelineTable.create(tableModel, model.timeline, TIMELINE.displayString, true)
    customizeConnectionsTable()
    ConfigColumnTableAspect.apply(connectionsTable, NetworkInspectorViewState.getInstance().columns)
    createTooltip()
    model.aspect.addDependency(this).onChange(NetworkInspectorAspect.SELECTED_CONNECTION) {
      updateTableSelection()
    }
  }

  private fun customizeConnectionsTable() {
    connectionsTable.autoCreateRowSorter = true

    ConnectionColumn.values().forEach {
      setRenderer(it, it.getCellRenderer(connectionsTable, model))
    }

    connectionsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
    connectionsTable.addMouseListener(
      object : MouseAdapter() {
        override fun mouseClicked(e: MouseEvent) {
          val row = connectionsTable.rowAtPoint(e.point)
          if (row != -1) {
            model.detailContent = NetworkInspectorModel.DetailContent.CONNECTION
          }
        }
      }
    )
    connectionsTable.registerEnterKeyAction {
      if (connectionsTable.selectedRow != -1) {
        model.detailContent = NetworkInspectorModel.DetailContent.CONNECTION
      }
    }
    connectionsTable.selectionModel.addListSelectionListener { e: ListSelectionEvent ->
      if (e.valueIsAdjusting) {
        return@addListSelectionListener // Only handle listener on last event, not intermediate
        // events
      }
      val selectedRow = connectionsTable.selectedRow
      if (0 <= selectedRow && selectedRow < tableModel.rowCount) {
        val modelRow = connectionsTable.convertRowIndexToModel(selectedRow)
        model.setSelectedConnection(tableModel.getConnectionData(modelRow))
      }
    }
    connectionsTable.background = DEFAULT_BACKGROUND
    connectionsTable.showVerticalLines = true
    connectionsTable.showHorizontalLines = false
    val defaultFontHeight = connectionsTable.getFontMetrics(connectionsTable.font).height
    connectionsTable.rowMargin = 0
    connectionsTable.rowHeight = defaultFontHeight + ROW_HEIGHT_PADDING
    connectionsTable.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, null)
    connectionsTable.setFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS, null)
    model.selectionRangeDataFetcher.addOnChangedListener {
      // Although the selected row doesn't change on range moved, we do this here to prevent
      // flickering that otherwise occurs in our table.
      updateTableSelection()
    }
  }

  private fun setRenderer(column: ConnectionColumn, renderer: TableCellRenderer) {
    connectionsTable.columnModel.getColumn(column.ordinal).cellRenderer = renderer
  }

  private fun createTooltip() {
    val textPane = JTextPane()
    textPane.isEditable = false
    textPane.border = TOOLTIP_BORDER
    textPane.background = TOOLTIP_BACKGROUND
    textPane.foreground = TOOLTIP_TEXT
    textPane.font = TooltipView.TOOLTIP_BODY_FONT
    val tooltip = TooltipComponent.Builder(textPane, connectionsTable, parentPane).build()
    tooltip.registerListenersOn(connectionsTable)
    connectionsTable.addMouseMotionListener(
      object : MouseAdapter() {
        override fun mouseMoved(e: MouseEvent) {
          val row = connectionsTable.rowAtPoint(e.point)
          if (row >= 0) {
            tooltip.isVisible = true
            val url = tableModel.getConnectionData(connectionsTable.convertRowIndexToModel(row)).url
            textPane.text = url
          } else {
            tooltip.isVisible = false
          }
        }
      }
    )
  }

  private fun updateTableSelection() {
    val selectedData = model.selectedConnection
    if (selectedData != null) {
      for (i in 0 until tableModel.rowCount) {
        if (tableModel.getConnectionData(i).id == selectedData.id) {
          val row = connectionsTable.convertRowIndexToView(i)
          connectionsTable.setRowSelectionInterval(row, row)
          return
        }
      }
    } else {
      connectionsTable.clearSelection()
    }
  }
}
