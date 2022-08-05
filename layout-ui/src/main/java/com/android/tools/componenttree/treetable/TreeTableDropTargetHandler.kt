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
package com.android.tools.componenttree.treetable

import com.intellij.ui.ColorUtil
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.UIUtil
import java.awt.BasicStroke
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Polygon
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.Stroke
import java.awt.datatransfer.Transferable
import java.awt.dnd.DropTargetDragEvent
import java.awt.dnd.DropTargetDropEvent
import java.awt.dnd.DropTargetEvent
import java.awt.dnd.DropTargetListener
import javax.swing.tree.TreePath

class TreeTableDropTargetHandler(private val table: TreeTableImpl, private val getDraggedItem: () -> Any?) : DropTargetListener {
  private var lineColor = ColorUtil.brighter(UIUtil.getTreeSelectionBackground(true), 10)
  private var dashedStroke = createDashStroke()
  private var insertionRow = -1
  private var insertionDepth = -1
  private var insertionBounds: Rectangle? = null
  private var receiverRow = -1
  private var receiverBounds: Rectangle? = null

  override fun dragEnter(event: DropTargetDragEvent) {
    updateInsertionPoint(event)
  }

  override fun dragOver(event: DropTargetDragEvent) {
    updateInsertionPoint(event)
  }

  override fun dropActionChanged(event: DropTargetDragEvent) {
  }

  override fun dragExit(event: DropTargetEvent) {
    clearInsertionPoint()
  }

  override fun drop(event: DropTargetDropEvent) {
    val receiver = table.getValueAt(receiverRow, 0)
    val item = table.getValueAt(insertionRow, 0)
    val insertAfterLastItemInReceiver = insertionRow - receiverRow > table.tableModel.children(receiver).size
    val before = if (!insertAfterLastItemInReceiver) item else null
    val succeeded = table.tableModel.insert(receiver, event.transferable, before)
    if (succeeded) {
      event.acceptDrop(event.dropAction)
      event.dropComplete(true)
    }
    clearInsertionPoint()
  }

  fun updateUI() {
    lineColor = ColorUtil.brighter(UIUtil.getTreeSelectionBackground(true), 10)
    dashedStroke = createDashStroke()
  }

  fun reset() {
    clearInsertionPoint()
  }

  fun paintDropTargetPosition(g: Graphics) {
    if (insertionRow >= 0) {
      val g2 = g.create() as Graphics2D
      try {
        g2.color = lineColor
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        paintReceiverRectangle(g2)
        paintInsertionLine(g2)
        paintColumnLine(g2)
      }
      finally {
        g2.dispose()
      }
    }
  }

  private fun createDashStroke(): Stroke =
    BasicStroke(JBUIScale.scale(1.0f), BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER, JBUIScale.scale(10.0f),
                floatArrayOf(JBUIScale.scale(4f), JBUIScale.scale(4f)), 0.0f)

  private fun paintReceiverRectangle(g: Graphics2D) {
    receiverBounds?.let {
      val x = maxOf(0, it.x - JBUIScale.scale(2))
      val maxWidth = table.columnModel.getColumn(0).width
      val width = minOf(maxWidth - x, it.width + JBUIScale.scale(2))
      g.drawRect(x, it.y, width, it.height)
    }
  }

  private fun paintColumnLine(g: Graphics2D) {
    val x = (receiverBounds?.x ?: 0) + JBUIScale.scale(7)
    val y = receiverBounds?.bottom ?: 0
    g.stroke = dashedStroke
    g.drawLine(x, y, x, insertionBounds?.bottom ?: y)
  }

  private val Rectangle.bottom: Int
    get() = y + height

  private val Rectangle.right: Int
    get() = x + width

  private fun paintInsertionLine(g: Graphics2D) {
    val triangle = Polygon()
    val indicatorSize = JBUIScale.scale(6)
    val x = (receiverBounds?.x ?: 0) + JBUIScale.scale(6)
    val y = insertionBounds?.bottom ?: 0
    triangle.addPoint(x + indicatorSize, y)
    triangle.addPoint(x, y + indicatorSize / 2)
    triangle.addPoint(x, y - indicatorSize / 2)
    g.drawLine(x, y, insertionBounds?.right ?: x, y)
    g.drawPolygon(triangle)
    g.fillPolygon(triangle)
  }

  private fun updateInsertionPoint(event: DropTargetDragEvent) {
    val point = Point(event.location.x, event.location.y + table.rowHeight / 2)
    val column = table.columnAtPoint(point)
    if (column != 0) {
      event.acceptDrag(0)
      clearInsertionPoint()
      return
    }
    val newInsertionDepth = table.findDepthFromOffset(point.x)
    val newInsertionRow = table.rowAtPoint(point).takeIf { it >= 0 } ?: table.rowCount
    if (insertionRow != newInsertionRow || insertionDepth != newInsertionDepth) {
      if (findReceiver(event.transferable, newInsertionRow, newInsertionDepth)) {
        insertionRow = newInsertionRow
        insertionDepth = newInsertionDepth
        insertionBounds =
          if (!table.isEmpty) table.tree.getRowBounds(maxOf(0, insertionRow - 1))
          else Rectangle(0, 0, table.columnModel.getColumn(0).width, 0)
        table.repaint()
      }
      else {
        event.acceptDrag(0)
        clearInsertionPoint()
      }
    }
  }

  // This allows the user to select the previous ancestor by moving
  // the cursor to the left only if there is some ambiguity to define where
  // the insertion should be.
  // There is an ambiguity if either the user tries to insert the component after the last row
  // in between a component deeper than the one on the next row:
  // shall we insert at the component after the last leaf or after the last leaf ancestor
  // -- root
  //   |-- parent
  //       |-- child1
  //       |-- child2
  // ..................
  //       |-- potential Insertion point 1 <---
  //   |-- potential Insertion point 2     <---
  //
  private fun findReceiver(data: Transferable, insertionRow: Int, insertionDepth: Int): Boolean {
    receiverRow = -1
    receiverBounds = null
    var item = table.model.getValueAt(insertionRow - 1, 0) ?: return false
    var receiver = item.takeIf { canDropInto(item, data) }
    var index = 0
    while (index + 1 >= table.tableModel.getChildCount(item) &&
           (receiver == null || insertionDepth < table.tableModel.computeDepth(item))) {
      val parent = table.tableModel.parent(item) ?: break
      index = table.tableModel.getIndexOfChild(parent, item)
      item = parent
      receiver = item.takeIf { canDropInto(item, data) } ?: receiver
    }
    if (receiver == null) {
      return false
    }
    val path = TreePath(generateSequence(item) { table.tableModel.parent(it) }.toList().asReversed().toTypedArray())
    receiverRow = table.tree.getRowForPath(path)
    receiverBounds = table.tree.getPathBounds(path)
    return true
  }

  private fun canDropInto(receiver: Any, data: Transferable): Boolean =
    getDraggedItem()?.let { dragged -> generateSequence(receiver) { table.tableModel.parent(it) }.none { it === dragged} } ?: true &&
    table.tableModel.canInsert(receiver, data)

  private fun clearInsertionPoint() {
    val repaint = insertionRow >= 0
    insertionRow = -1
    insertionDepth = -1
    insertionBounds = null
    receiverRow = -1
    receiverBounds = null
    if (repaint) {
      table.repaint()
    }
  }
}
