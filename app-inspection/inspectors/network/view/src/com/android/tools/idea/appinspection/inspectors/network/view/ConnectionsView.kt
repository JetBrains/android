/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.appinspection.inspectors.network.view

import com.android.tools.adtui.TooltipComponent
import com.android.tools.adtui.TooltipView
import com.android.tools.adtui.model.AspectObserver
import com.android.tools.adtui.model.StreamingTimeline
import com.android.tools.adtui.model.formatter.NumberFormatter
import com.android.tools.adtui.stdui.BorderlessTableCellRenderer
import com.android.tools.adtui.stdui.TimelineTable
import com.android.tools.adtui.stdui.TooltipLayeredPane
import com.android.tools.idea.appinspection.inspectors.network.model.NetworkInspectorAspect
import com.android.tools.idea.appinspection.inspectors.network.model.NetworkInspectorModel
import com.android.tools.idea.appinspection.inspectors.network.model.httpdata.HttpData
import com.android.tools.idea.appinspection.inspectors.network.model.httpdata.HttpData.Companion.getUrlName
import com.android.tools.idea.appinspection.inspectors.network.model.httpdata.SelectionRangeDataFetcher
import com.android.tools.idea.appinspection.inspectors.network.view.constants.DEFAULT_BACKGROUND
import com.android.tools.idea.appinspection.inspectors.network.view.constants.ROW_HEIGHT_PADDING
import com.android.tools.idea.appinspection.inspectors.network.view.constants.TOOLTIP_BACKGROUND
import com.android.tools.idea.appinspection.inspectors.network.view.constants.TOOLTIP_BORDER
import com.android.tools.idea.appinspection.inspectors.network.view.constants.TOOLTIP_TEXT
import com.android.tools.idea.appinspection.inspectors.network.view.rules.registerEnterKeyAction
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.util.text.StringUtil
import java.awt.Component
import java.awt.KeyboardFocusManager
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.swing.JComponent
import javax.swing.JTable
import javax.swing.JTextPane
import javax.swing.ListSelectionModel
import javax.swing.event.ChangeEvent
import javax.swing.event.ListSelectionEvent
import javax.swing.event.TableColumnModelEvent
import javax.swing.event.TableColumnModelListener
import javax.swing.event.TableModelEvent
import javax.swing.event.TableModelListener
import javax.swing.table.AbstractTableModel

/**
 * This class responsible for displaying table of connections information (e.g url, duration, timeline)
 * for network inspector. Each row in the table represents a single connection.
 */
class ConnectionsView(private val model: NetworkInspectorModel, private val parentPane: TooltipLayeredPane) : AspectObserver() {
  /**
   * Columns for each connection information
   */
  @VisibleForTesting
  enum class Column(var widthPercentage: Double, val type: Class<*>) {
    NAME(0.25, String::class.java) {
      override fun getValueFrom(data: HttpData): Any {
        return getUrlName(data.url)
      }
    },
    SIZE(0.25 / 4, java.lang.Integer::class.java) {
      override fun getValueFrom(data: HttpData): Any {
        return data.responsePayload.size()
      }
    },
    TYPE(0.25 / 4, String::class.java) {
      override fun getValueFrom(data: HttpData): Any {
        val type = data.responseHeader.contentType
        val mimeTypeParts = type.mimeType.split("/")
        return mimeTypeParts[mimeTypeParts.size - 1]
      }
    },
    STATUS(0.25 / 4, java.lang.Integer::class.java) {
      override fun getValueFrom(data: HttpData): Any {
        return data.responseHeader.statusCode
      }
    },
    TIME(0.25 / 4, java.lang.Long::class.java) {
      override fun getValueFrom(data: HttpData): Any {
        return data.connectionEndTimeUs - data.requestStartTimeUs
      }
    },
    TIMELINE(0.5, java.lang.Long::class.java) {
      override fun getValueFrom(data: HttpData): Any {
        return data.requestStartTimeUs
      }
    };

    fun toDisplayString(): String {
      return name.lowercase(Locale.getDefault()).capitalize()
    }

    abstract fun getValueFrom(data: HttpData): Any
  }

  private val tableModel = ConnectionsTableModel(model.selectionRangeDataFetcher)
  private val connectionsTable: JTable
  private var columnWidthsInitialized = false
  val component: JComponent
    get() = connectionsTable


  init {
    connectionsTable = TimelineTable.create(tableModel, model.timeline, Column.TIMELINE.toDisplayString(), true)
    customizeConnectionsTable()
    createTooltip()
    model.aspect.addDependency(this).onChange(NetworkInspectorAspect.SELECTED_CONNECTION) { updateTableSelection() }
  }

  private fun customizeConnectionsTable() {
    connectionsTable.autoCreateRowSorter = true
    connectionsTable.columnModel.getColumn(Column.NAME.ordinal).cellRenderer = BorderlessTableCellRenderer()
    connectionsTable.columnModel.getColumn(Column.SIZE.ordinal).cellRenderer = SizeRenderer()
    connectionsTable.columnModel.getColumn(Column.TYPE.ordinal).cellRenderer = BorderlessTableCellRenderer()
    connectionsTable.columnModel.getColumn(Column.STATUS.ordinal).cellRenderer = StatusRenderer()
    connectionsTable.columnModel.getColumn(Column.TIME.ordinal).cellRenderer = TimeRenderer()
    connectionsTable.columnModel.getColumn(Column.TIMELINE.ordinal).cellRenderer = TimelineRenderer(connectionsTable, model.timeline)
    connectionsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
    connectionsTable.addMouseListener(object : MouseAdapter() {
      override fun mouseClicked(e: MouseEvent) {
        val row = connectionsTable.rowAtPoint(e.point)
        if (row != -1) {
          model.detailContent = NetworkInspectorModel.DetailContent.CONNECTION
        }
      }
    })
    connectionsTable.registerEnterKeyAction {
      if (connectionsTable.selectedRow != -1) {
        model.detailContent = NetworkInspectorModel.DetailContent.CONNECTION
      }
    }
    connectionsTable.selectionModel.addListSelectionListener { e: ListSelectionEvent ->
      if (e.valueIsAdjusting) {
        return@addListSelectionListener   // Only handle listener on last event, not intermediate events
      }
      val selectedRow = connectionsTable.selectedRow
      if (0 <= selectedRow && selectedRow < tableModel.rowCount) {
        val modelRow = connectionsTable.convertRowIndexToModel(selectedRow)
        model.setSelectedConnection(tableModel.getHttpData(modelRow))
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
    connectionsTable.addComponentListener(object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent) {
        Column.values().forEachIndexed { i, column ->
          getColumnForIndex(i).preferredWidth = (connectionsTable.width * column.widthPercentage).toInt()
        }
        // Mark the column widths as initialized once we set their widths here
        columnWidthsInitialized = true
      }
    })
    model.selectionRangeDataFetcher.addOnChangedListener {
      // Although the selected row doesn't change on range moved, we do this here to prevent
      // flickering that otherwise occurs in our table.
      updateTableSelection()
    }
    // Fix column positions in the header
    connectionsTable.tableHeader.reorderingAllowed = false
    connectionsTable.columnModel.addColumnModelListener(object : TableColumnModelListener {
      override fun columnAdded(e: TableColumnModelEvent) = Unit
      override fun columnRemoved(e: TableColumnModelEvent) = Unit
      override fun columnMoved(e: TableColumnModelEvent) = Unit
      override fun columnSelectionChanged(e: ListSelectionEvent?) = Unit

      override fun columnMarginChanged(e: ChangeEvent) {
        // Let the columns be set to their original widths during init process before retaining their widths.
        // Not doing so leads to each column having equal widths.
        if (!columnWidthsInitialized) return
        Column.values().forEachIndexed { index, column ->
          column.widthPercentage = getColumnForIndex(index).width.toDouble() / connectionsTable.width
        }
      }
    })
  }

  private fun getColumnForIndex(index: Int) = connectionsTable.columnModel.getColumn(connectionsTable.convertColumnIndexToView(index))

  private fun createTooltip() {
    val textPane = JTextPane()
    textPane.isEditable = false
    textPane.border = TOOLTIP_BORDER
    textPane.background = TOOLTIP_BACKGROUND
    textPane.foreground = TOOLTIP_TEXT
    textPane.font = TooltipView.TOOLTIP_BODY_FONT
    val tooltip = TooltipComponent.Builder(textPane, connectionsTable, parentPane).build()
    tooltip.registerListenersOn(connectionsTable)
    connectionsTable.addMouseMotionListener(object : MouseAdapter() {
      override fun mouseMoved(e: MouseEvent) {
        val row = connectionsTable.rowAtPoint(e.point)
        if (row >= 0) {
          tooltip.isVisible = true
          val url = tableModel.getHttpData(connectionsTable.convertRowIndexToModel(row)).url
          textPane.text = url
        }
        else {
          tooltip.isVisible = false
        }
      }
    })
  }

  private fun updateTableSelection() {
    val selectedData = model.selectedConnection
    if (selectedData != null) {
      for (i in 0 until tableModel.rowCount) {
        if (tableModel.getHttpData(i).id == selectedData.id) {
          val row = connectionsTable.convertRowIndexToView(i)
          connectionsTable.setRowSelectionInterval(row, row)
          return
        }
      }
    }
    else {
      connectionsTable.clearSelection()
    }
  }

  private class ConnectionsTableModel(selectionRangeDataFetcher: SelectionRangeDataFetcher) : AbstractTableModel() {
    private lateinit var dataList: List<HttpData>

    init {
      selectionRangeDataFetcher.addOnChangedListener { httpDataList ->
        dataList = httpDataList
        fireTableDataChanged()
      }
    }

    override fun getRowCount(): Int {
      return dataList.size
    }

    override fun getColumnCount(): Int {
      return Column.values().size
    }

    override fun getColumnName(column: Int): String {
      return Column.values()[column].toDisplayString()
    }

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
      val data = dataList[rowIndex]
      return Column.values()[columnIndex].getValueFrom(data)
    }

    override fun getColumnClass(columnIndex: Int): Class<*> {
      return Column.values()[columnIndex].type
    }

    fun getHttpData(rowIndex: Int): HttpData {
      return dataList[rowIndex]
    }
  }

  private class SizeRenderer : BorderlessTableCellRenderer() {
    init {
      horizontalAlignment = RIGHT
    }

    override fun setValue(value: Any) {
      val bytes = value as Int
      text = if (bytes >= 0) NumberFormatter.formatFileSize(bytes.toLong()) else ""
    }
  }

  private class StatusRenderer : BorderlessTableCellRenderer() {
    override fun setValue(value: Any) {
      val status = value as Int
      text = if (status > -1) status.toString() else ""
    }
  }

  private class TimeRenderer : BorderlessTableCellRenderer() {
    init {
      horizontalAlignment = RIGHT
    }

    override fun setValue(value: Any) {
      val durationUs = value as Long
      text = if (durationUs >= 0) {
        val durationMs = TimeUnit.MICROSECONDS.toMillis(durationUs)
        StringUtil.formatDuration(durationMs)
      }
      else {
        ""
      }
    }
  }

  private class TimelineRenderer(private val table: JTable, timeline: StreamingTimeline) :
    TimelineTable.CellRenderer(timeline, true), TableModelListener {
    /**
     * Keep in sync 1:1 with [ConnectionsTableModel.dataList]. When the table asks for the
     * chart to render, it will be converted from model index to view index.
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
        val chart = ConnectionsStateChart(model.getHttpData(i), activeRange)
        chart.setHeightGap(0.3f)
        connectionsCharts.add(chart)
      }
    }
  }
}
