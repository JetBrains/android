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

import com.android.tools.adtui.TabularLayout
import com.android.tools.adtui.TooltipComponent
import com.android.tools.adtui.TooltipView.TOOLTIP_BODY_FONT
import com.android.tools.adtui.common.AdtUiUtils
import com.android.tools.adtui.model.AspectObserver
import com.android.tools.adtui.model.Range
import com.android.tools.adtui.model.formatter.TimeFormatter
import com.android.tools.adtui.stdui.BorderlessTableCellRenderer
import com.android.tools.adtui.stdui.TimelineTable
import com.android.tools.adtui.stdui.TooltipLayeredPane
import com.android.tools.idea.appinspection.inspectors.network.model.NetworkInspectorAspect
import com.android.tools.idea.appinspection.inspectors.network.model.NetworkInspectorModel
import com.android.tools.idea.appinspection.inspectors.network.model.httpdata.HttpData
import com.android.tools.idea.appinspection.inspectors.network.model.httpdata.HttpData.Companion.getUrlName
import com.android.tools.idea.appinspection.inspectors.network.model.httpdata.SelectionRangeDataFetcher
import com.android.tools.idea.appinspection.inspectors.network.view.constants.DEFAULT_BACKGROUND
import com.android.tools.idea.appinspection.inspectors.network.view.constants.NETWORK_RECEIVING_COLOR
import com.android.tools.idea.appinspection.inspectors.network.view.constants.NETWORK_SENDING_COLOR
import com.android.tools.idea.appinspection.inspectors.network.view.constants.NETWORK_THREADS_VIEW_TOOLTIP_DIVIDER
import com.android.tools.idea.appinspection.inspectors.network.view.constants.SMALL_FONT
import com.android.tools.idea.appinspection.inspectors.network.view.constants.TOOLTIP_BACKGROUND
import com.android.tools.idea.appinspection.inspectors.network.view.constants.TOOLTIP_BORDER
import com.android.tools.idea.appinspection.inspectors.network.view.constants.TOOLTIP_TEXT
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.BasicStroke
import java.awt.Component
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.KeyboardFocusManager
import java.awt.RenderingHints
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.font.TextAttribute
import java.awt.geom.Rectangle2D
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.SwingUtilities
import javax.swing.event.TableModelEvent
import javax.swing.event.TableModelListener
import javax.swing.table.AbstractTableModel
import javax.swing.table.TableRowSorter

/**
 * Displays network connection information of all threads.
 */
class ThreadsView(model: NetworkInspectorModel, parentPane: TooltipLayeredPane) {
  private enum class Column(val displayName: String) {
    NAME("Initiating thread"), TIMELINE("Timeline")
  }

  private val threadsTable: JTable
  private val observer: AspectObserver
  val component: JComponent
    get() = threadsTable

  init {
    val tableModel = ThreadsTableModel(model.selectionRangeDataFetcher)
    threadsTable = TimelineTable.create(tableModel, model.timeline, Column.TIMELINE.displayName, true)
    val timelineRenderer = TimelineRenderer(threadsTable, model)
    threadsTable.getColumnModel().getColumn(Column.NAME.ordinal).cellRenderer = BorderlessTableCellRenderer()
    threadsTable.getColumnModel().getColumn(Column.TIMELINE.ordinal).cellRenderer = timelineRenderer
    threadsTable.setBackground(DEFAULT_BACKGROUND)
    threadsTable.setShowVerticalLines(true)
    threadsTable.setShowHorizontalLines(false)
    threadsTable.setCellSelectionEnabled(false)
    threadsTable.setFocusable(false)
    threadsTable.setRowMargin(0)
    threadsTable.setRowHeight(ROW_HEIGHT)
    threadsTable.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, null)
    threadsTable.setFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS, null)
    threadsTable.addComponentListener(object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent) {
        threadsTable.getColumnModel().getColumn(Column.NAME.ordinal).preferredWidth = (threadsTable.getWidth() * 1.0 / 8).toInt()
        threadsTable.getColumnModel().getColumn(Column.TIMELINE.ordinal).preferredWidth = (threadsTable.getWidth() * 7.0 / 8).toInt()
      }
    })
    val sorter = TableRowSorter(tableModel)
    sorter.setComparator(Column.NAME.ordinal, Comparator.comparing { obj: String -> obj })
    sorter.setComparator(Column.TIMELINE.ordinal, Comparator.comparing { data: List<HttpData> -> data[0].requestStartTimeUs })
    threadsTable.setRowSorter(sorter)
    threadsTable.addMouseListener(object : MouseAdapter() {
      override fun mouseClicked(e: MouseEvent) {
        val selection = timelineRenderer.activeRange
        val data = findHttpDataUnderCursor(threadsTable, selection, e)
        if (data != null) {
          model.setSelectedConnection(data)
          model.detailContent = NetworkInspectorModel.DetailContent.CONNECTION
          e.consume()
        }
      }
    })
    threadsTable.addMouseMotionListener(TooltipView(threadsTable, parentPane, timelineRenderer.activeRange))
    observer = AspectObserver()
    model.aspect.addDependency(observer)
      .onChange(NetworkInspectorAspect.SELECTED_CONNECTION) {
        timelineRenderer.updateRows()
        threadsTable.repaint()
      }
  }

  private class ThreadsTableModel(selectionRangeDataFetcher: SelectionRangeDataFetcher) : AbstractTableModel() {
    private val threads = mutableListOf<List<HttpData>>()

    init {
      selectionRangeDataFetcher.addOnChangedListener { httpDataList -> httpDataChanged(httpDataList) }
    }

    private fun httpDataChanged(dataList: List<HttpData>) {
      threads.clear()
      if (dataList.isEmpty()) {
        fireTableDataChanged()
        return
      }
      val groupedThreads = dataList
        .filter { it.javaThreads.isNotEmpty() }
        .groupBy { it.javaThreads[0].id }

      // Sort by thread name, so that they're consistently displayed in alphabetical order.
      threads.addAll(groupedThreads.values.sortedWith(compareBy({ it[0].javaThreads[0].name }, { it[0].javaThreads[0].id })))
      fireTableDataChanged()
    }

    override fun getRowCount() = threads.size

    override fun getColumnCount() = 2

    override fun getColumnName(column: Int) = Column.values()[column].displayName

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
      return if (columnIndex == Column.NAME.ordinal) {
        threads[rowIndex][0].javaThreads[0].name
      }
      else {
        threads[rowIndex]
      }
    }
  }

  private class TimelineRenderer(private val table: JTable, private val model: NetworkInspectorModel) :
    TimelineTable.CellRenderer(model.timeline, true), TableModelListener {
    private val connectionsInfo = mutableListOf<JComponent>()

    init {
      table.model.addTableModelListener(this)
      tableChanged(TableModelEvent(table.model))
    }

    override fun tableChanged(e: TableModelEvent) {
      updateRows()
    }

    fun updateRows() {
      connectionsInfo.clear()
      for (index in 0 until table.model.rowCount) {
        val data = table.model.getValueAt(index, 1) as List<HttpData>
        connectionsInfo.add(ConnectionsInfoComponent(table, data, model, activeRange))
      }
    }

    override fun getTableCellRendererComponent(isSelected: Boolean, row: Int): Component {
      return connectionsInfo[table.convertRowIndexToModel(row)]
    }
  }

  /**
   * A component that responsible for rendering information of the given connections,
   * such as connection names, warnings, and lifecycle states.
   */
  private class ConnectionsInfoComponent(
    private val table: JTable,
    private val dataList: List<HttpData>,
    private val model: NetworkInspectorModel,
    private val range: Range
  ) : JComponent() {

    init {
      font = SMALL_FONT
      foreground = JBColor.BLACK
      background = DEFAULT_BACKGROUND
    }

    override fun paintComponent(g: Graphics) {
      super.paintComponent(g)
      val g2d: Graphics2D = g.create() as Graphics2D
      g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF)
      g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
      for (i in dataList.indices) {
        val data = dataList[i]
        val endLimit = if (i + 1 < dataList.size) {
          rangeToPosition(dataList[i + 1].requestStartTimeUs.toDouble())
        }
        else width.toDouble()
        drawState(g2d, data, endLimit)
        drawConnectionName(g2d, data, endLimit)
      }
      if (model.selectedConnection != null && dataList.contains(model.selectedConnection)) {
        drawSelection(g2d, model.selectedConnection!!, width.toDouble())
      }
      g2d.dispose()
    }

    private fun drawState(g2d: Graphics2D, data: HttpData, endLimit: Double) {
      var prev = rangeToPosition(data.requestStartTimeUs.toDouble())
      g2d.color = NETWORK_SENDING_COLOR
      if (data.responseStartTimeUs > 0) {
        val download = rangeToPosition(data.responseStartTimeUs.toDouble())
        // draw sending
        g2d.fill(Rectangle2D.Double(prev, (height - STATE_HEIGHT) / 2.0, download - prev, STATE_HEIGHT.toDouble()))
        g2d.color = NETWORK_RECEIVING_COLOR
        prev = download
      }
      val end = if (data.connectionEndTimeUs > 0) rangeToPosition(data.connectionEndTimeUs.toDouble()) else endLimit
      g2d.fill(Rectangle2D.Double(prev, (height - STATE_HEIGHT) / 2.0, end - prev, STATE_HEIGHT.toDouble()))
    }

    private fun drawConnectionName(g2d: Graphics2D, data: HttpData, endLimit: Double) {
      g2d.font = font
      g2d.color = foreground
      val start = rangeToPosition(data.requestStartTimeUs.toDouble())
      val end = if (data.connectionEndTimeUs > 0) rangeToPosition(data.connectionEndTimeUs.toDouble()) else endLimit
      val metrics = getFontMetrics(font)
      val text = AdtUiUtils.shrinkToFit(getUrlName(data.url), metrics, (end - start - 2 * NAME_PADDING).toFloat())
      val availableSpace = end - start - metrics.stringWidth(text)
      g2d.drawString(text, (start + availableSpace / 2.0).toFloat(), ((height - metrics.height) * 0.5 + metrics.ascent).toFloat())
    }

    private fun drawSelection(g2d: Graphics2D, data: HttpData, endLimit: Double) {
      val start = rangeToPosition(data.requestStartTimeUs.toDouble())
      val end = if (data.connectionEndTimeUs > 0) rangeToPosition(data.connectionEndTimeUs.toDouble()) else endLimit
      g2d.stroke = BasicStroke(SELECTION_OUTLINE_BORDER.toFloat())
      g2d.color = table.selectionBackground
      val rect = Rectangle2D.Double(
        start - SELECTION_OUTLINE_PADDING,
        (height - STATE_HEIGHT) / 2.0 - SELECTION_OUTLINE_PADDING,
        end - start + 2 * SELECTION_OUTLINE_PADDING,
        STATE_HEIGHT + 2 * SELECTION_OUTLINE_PADDING.toDouble()
      )
      g2d.draw(rect)
    }

    private fun rangeToPosition(r: Double): Double {
      return (r - range.min) / range.length * width
    }

    companion object {
      private const val NAME_PADDING = 6
    }
  }

  private class TooltipView(
    private val table: JTable,
    parentPane: TooltipLayeredPane,
    private val range: Range) : MouseAdapter() {
    private val content = JPanel(TabularLayout("*", "*")).apply {
      border = TOOLTIP_BORDER
      background = TOOLTIP_BACKGROUND
      font = TOOLTIP_BODY_FONT
    }
    private val tooltipComponent = TooltipComponent.Builder(content, table, parentPane).build().apply {
      registerListenersOn(table)
      isVisible = false
    }

    override fun mouseMoved(e: MouseEvent) {
      tooltipComponent.isVisible = false
      val data = findHttpDataUnderCursor(table, range, e)
      data?.let { showTooltip(it) }
    }

    private fun showTooltip(data: HttpData) {
      tooltipComponent.isVisible = true
      val urlName = getUrlName(data.url)
      val duration = data.connectionEndTimeUs - data.requestStartTimeUs
      content.removeAll()
      addToContent(newTooltipLabel(urlName))
      val durationLabel = newTooltipLabel(TimeFormatter.getSingleUnitDurationString(duration))
      durationLabel.foreground = TOOLTIP_TEXT
      addToContent(durationLabel)
      if (data.javaThreads.size > 1) {
        val divider = JPanel()
        divider.preferredSize = Dimension(0, 5)
        divider.border = JBUI.Borders.customLineBottom(NETWORK_THREADS_VIEW_TOOLTIP_DIVIDER)
        divider.background = content.background
        content.add(divider, TabularLayout.Constraint(content.componentCount, 0))
        val alsoAccessedByLabel = newTooltipLabel("Also accessed by:")
        alsoAccessedByLabel.font = alsoAccessedByLabel.font.deriveFont(
          mapOf(TextAttribute.WEIGHT to TextAttribute.WEIGHT_BOLD)
        )
        addToContent(alsoAccessedByLabel)
        for (i in 1 until data.javaThreads.size) {
          val label = newTooltipLabel(data.javaThreads[i].name)
          addToContent(label)
          if (i == data.javaThreads.size - 1) {
            label.border = JBUI.Borders.empty(5, 0)
          }
        }
      }
    }

    private fun addToContent(component: JComponent) {
      component.border = JBUI.Borders.emptyTop(5)
      content.add(component, TabularLayout.Constraint(content.componentCount, 0))
    }

    private fun newTooltipLabel(text: String): JLabel {
      val label = JLabel(text)
      label.foreground = TOOLTIP_TEXT
      label.font = TOOLTIP_BODY_FONT
      return label
    }
  }

  companion object {
    private val STATE_HEIGHT = JBUI.scale(15)
    private val SELECTION_OUTLINE_PADDING = JBUI.scale(3)
    private val SELECTION_OUTLINE_BORDER = JBUI.scale(2)
    private val ROW_HEIGHT = STATE_HEIGHT + 2 * (SELECTION_OUTLINE_BORDER + SELECTION_OUTLINE_PADDING)
    private fun findHttpDataUnderCursor(table: JTable, range: Range, e: MouseEvent): HttpData? {
      val p = SwingUtilities.convertPoint(e.component, e.point, table)
      val row = table.rowAtPoint(p)
      val column = table.columnAtPoint(p)
      if (row == -1 || column == -1) {
        return null
      }
      if (column == Column.TIMELINE.ordinal) {
        val cellBounds = table.getCellRect(row, column, false)
        val modelIndex = table.convertRowIndexToModel(row)
        val dataList = table.model.getValueAt(modelIndex, 1) as List<HttpData>
        val at = positionToRange((p.x - cellBounds.x).toDouble(), cellBounds.getWidth(), range)
        for (data in dataList) {
          if (data.requestStartTimeUs <= at && at <= data.connectionEndTimeUs) {
            return data
          }
        }
      }
      return null
    }

    private fun positionToRange(x: Double, width: Double, range: Range): Double {
      return x * range.length / width + range.min
    }
  }
}