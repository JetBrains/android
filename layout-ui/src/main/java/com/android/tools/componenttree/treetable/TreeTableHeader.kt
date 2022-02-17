/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.componenttree.treetable

import com.android.tools.componenttree.api.ColumnInfo
import com.intellij.ui.JBColor
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import javax.swing.JComponent
import javax.swing.SwingUtilities
import javax.swing.plaf.basic.BasicTableHeaderUI
import javax.swing.table.JTableHeader

/**
 * A [JTableHeader] that is using [BasicTableHeaderUI] and paints divider lines.
 *
 * The default [JTableHeader] using DarculaTableHeaderUI will unconditionally paint column divider
 * lines between all columns. We only want them where they are defined by the specified [ColumnInfo] instances.
 *
 * Draw a divider between the header and the table content see [paintBottomSeparator].
 *
 * The component keeps track of the hover column and will use the tooltipText and cursor for the component found
 * under the mouse pointer. Also: all mouse clicks will be forwarded.
 */
class TreeTableHeader(private val treeTable: TreeTableImpl) : JTableHeader(treeTable.columnModel) {
  private var hoverColumn = -1
  private var hoverCachedComponent: Component? = null

  init {
    reorderingAllowed = false
    val mouseListener = HoverMouseListener()
    addMouseListener(mouseListener)
    addMouseMotionListener(mouseListener)
  }

  override fun getPreferredSize(): Dimension {
    val size = super.getPreferredSize()
    size.height++
    return size
  }

  override fun paintComponent(g: Graphics) {
    super.paintComponent(g)
    treeTable.paintColumnDividers(g)
    paintBottomSeparator(g)
  }

  override fun updateUI() {
    setUI(BasicTableHeaderUI())
  }

  override fun getToolTipText(event: MouseEvent): String? {
    updateHoverColumn(event)
    return hoverComponentAt(event)?.toolTipText
  }

  private fun paintBottomSeparator(g: Graphics) {
    val g2 = g.create()
    g2.color = JBColor.border()
    g2.drawLine(0, height - 1, width, height - 1)
    g2.dispose()
  }

  private fun updateHoverColumn(event: MouseEvent) {
    val column = table.columnAtPoint(event.point)
    if (column != hoverColumn) {
      hoverColumn = column
      hoverCachedComponent = null
    }
    cursor = hoverComponentAt(event)?.cursor ?: Cursor.getDefaultCursor()
  }

  private fun hoverComponentAt(event: MouseEvent): JComponent? {
    val columnComponent = hoverComponent ?: return null
    val cellRect = getHeaderRect(hoverColumn)
    columnComponent.setBounds(0, 0, cellRect.width, cellRect.height)
    columnComponent.doLayout()
    val point = event.point.also { it.translate(-cellRect.x, -cellRect.y) }
    return SwingUtilities.getDeepestComponentAt(columnComponent, point.x, point.y) as? JComponent
  }

  private val hoverComponent: Component?
    get() {
      if (hoverCachedComponent == null && hoverColumn != -1) {
        val aColumn = columnModel.getColumn(hoverColumn)
        val renderer = aColumn.headerRenderer ?: defaultRenderer
        hoverCachedComponent = renderer.getTableCellRendererComponent(table, aColumn.headerValue, false, false, -1, hoverColumn)
      }
      return hoverCachedComponent
    }

  private inner class HoverMouseListener : MouseAdapter() {
    override fun mouseEntered(event: MouseEvent) {
      updateHoverColumn(event)
    }

    override fun mouseMoved(event: MouseEvent) {
      updateHoverColumn(event)
    }

    override fun mouseWheelMoved(event: MouseWheelEvent) {
      updateHoverColumn(event)
    }

    override fun mouseExited(event: MouseEvent) {
      hoverColumn = -1
      hoverCachedComponent = null
    }

    override fun mousePressed(event: MouseEvent) = redispatchMouseEvent(event)
    override fun mouseReleased(event: MouseEvent) = redispatchMouseEvent(event)
    override fun mouseClicked(event: MouseEvent) = redispatchMouseEvent(event)

    private fun redispatchMouseEvent(event: MouseEvent) {
      updateHoverColumn(event)
      val component = hoverComponentAt(event) ?: return
      val cellRect = getHeaderRect(hoverColumn)
      val point = event.point.also { it.translate(-cellRect.x, -cellRect.y) }
      generateSequence(component) { it.parent as JComponent? }.forEach { point.translate(-it.x, -it.y) }
      @Suppress("DEPRECATION")
      val newEvent = MouseEvent(component,
                                event.id,
                                event.getWhen(), event.modifiers or event.modifiersEx,
                                point.x, point.y,
                                event.xOnScreen,
                                event.yOnScreen,
                                event.clickCount,
                                event.isPopupTrigger,
                                event.button)
      component.dispatchEvent(newEvent)
    }
  }
}
