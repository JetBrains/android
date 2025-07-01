/*
 * Copyright (C) 2025 The Android Open Source Project
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

import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.tree.ui.Control
import com.intellij.ui.tree.ui.DefaultControl
import java.awt.BasicStroke
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Insets
import java.awt.Rectangle
import java.awt.event.MouseWheelEvent
import java.awt.event.MouseWheelListener
import java.util.concurrent.ConcurrentHashMap
import javax.swing.JScrollPane
import javax.swing.event.ChangeListener
import javax.swing.event.TreeSelectionEvent
import javax.swing.event.TreeSelectionListener
import javax.swing.plaf.basic.BasicTreeUI
import javax.swing.tree.AbstractLayoutCache
import javax.swing.tree.TreePath
import kotlin.math.max

/**
 * This custom TreeUI handles horizontal scrolling for components within the tree by adjusting the
 * components dimensions created by each node. And ensures components extend to the right edge by
 * managing indentation, a value known to the UI via getRowX.
 */
class ColumnTreeUI(
  private val table: TreeTableImpl,
  private val hScrollBarPanel: ColumnTreeScrollPanel,
  private val scrollPane: JScrollPane,
  private val autoScroll: Boolean,
  private val showSupportLines: () -> Boolean,
  private val isCallStackNode: (TreePath) -> Boolean,
) : BasicTreeUI() {
  private var stateChangeListener: ChangeListener? = null
  private val treeNodesWidth = ConcurrentHashMap<Int, Int>()
  private var mouseWheelListener: MouseWheelListener? = null
  private var treeSelectionListener: TreeSelectionListener? = null
  private val control = DefaultControl()
  private val defaultPainter = Control.Painter.DEFAULT

  override fun installListeners() {
    super.installListeners()
    if (stateChangeListener == null) {
      stateChangeListener = ChangeListener {
        tree.treeOffset = hScrollBarPanel.getModel().value
        treeState.invalidateSizes()
        table.invalidate()
        table.repaint()
      }
      hScrollBarPanel.getModel().addChangeListener(stateChangeListener)
    }
    mouseWheelListener =
      object : MouseWheelListener {
        override fun mouseWheelMoved(e: MouseWheelEvent?) {
          if (e == null || !e.isShiftDown) {
            return
          }
          val column = table.columnAtPoint(e.point)
          // only scroll component tree column.
          if (column == 0) {
            hScrollBarPanel.getModel().value += e.wheelRotation * hScrollBarPanel.getUnitIncrement()
          }
        }
      }

    if (autoScroll) {
      treeSelectionListener =
        object : TreeSelectionListener {
          override fun valueChanged(e: TreeSelectionEvent?) {
            // Check if a new node has been selected.
            if (e?.newLeadSelectionPath == null) {
              return
            }
            val row = getRowForPath(table.tree, e.path)
            autoScrollVertically(e.path)
            autoScrollHorizontally(row)
          }
        }
    }
    scrollPane.addMouseWheelListener(mouseWheelListener)
    table.tree.addTreeSelectionListener(treeSelectionListener)
  }

  /** Attempt to scroll the tree vertically so that the selected row is centered vertically. */
  private fun autoScrollVertically(path: TreePath?) {
    val viewPosition = scrollPane.viewport.viewPosition
    val rowBounds = table.tree.getPathBounds(path)

    if (
      rowBounds == null ||
        (rowBounds.y + rowBounds.height >= viewPosition.y &&
          rowBounds.y <= viewPosition.y + scrollPane.viewport.extentSize.height)
    ) {
      // Don't scroll if the selected node is already visible.
      return
    }
    val viewportHeight = scrollPane.viewport.extentSize.height

    // Scroll to center the selected node vertically.
    if (rowBounds.y < viewPosition.y) {
      // When scrolling down, scrollRectToVisible function would make
      // the element appear at the top.
      // Subtracting half the viewport height shifts the scroll position
      // to bring the element into the middle of the visible area.
      rowBounds.y -= viewportHeight / 2
    } else {
      rowBounds.y += viewportHeight / 2
    }
    table.scrollRectToVisible(rowBounds)
  }

  private fun autoScrollHorizontally(selectedRow: Int) {
    var selectRowPath = table.tree.getPathForRow(selectedRow)
    var path = selectRowPath
    // Get the furthest visible parent row in the tree viewport.
    while (
      isPathVisible(path?.parentPath) &&
        isSelectedRowAtLeastHalfVisible(path?.parentPath, selectRowPath)
    ) {
      path = path?.parentPath
    }
    // Scroll horizontally to the furthest visible parent.
    path.let {
      val rec = getPathBounds(table.tree, path)
      if (rec != null) {
        hScrollBarPanel.getModel().value =
          max(0, rec.x + tree.treeOffset - max(expandedIcon.iconWidth, collapsedIcon.iconWidth))
      }
    }
  }

  private fun isSelectedRowAtLeastHalfVisible(
    ancestorPath: TreePath?,
    selectRowPath: TreePath,
  ): Boolean {
    val ancestorBounds = table.tree.getPathBounds(ancestorPath)
    val selectRowBounds = table.tree.getPathBounds(selectRowPath)
    if (ancestorBounds == null || selectRowBounds == null) {
      return false
    }
    val ancestorStartX = ancestorBounds.x - expandedIcon.iconWidth
    val selectRowStartX = selectRowBounds.x
    val columnWidth = table.getColumnModel().getColumn(0).getWidth()

    return selectRowStartX - ancestorStartX <= columnWidth / 2
  }

  private fun isPathVisible(path: TreePath?): Boolean {
    val rowBounds = table.tree.getPathBounds(path)

    val viewPosition = scrollPane.viewport.viewPosition
    // Counts the start of the item even if it's partially visible.
    val startY = viewPosition.y - (rowBounds?.height?.div(2) ?: 0)
    val endY = viewPosition.y + scrollPane.viewport.extentSize.height

    return rowBounds != null && rowBounds.y > startY && rowBounds.y <= endY
  }

  override fun uninstallListeners() {
    super.uninstallListeners()
    if (stateChangeListener != null) {
      hScrollBarPanel.getModel().removeChangeListener(stateChangeListener!!)
      stateChangeListener = null
    }
    if (mouseWheelListener != null) {
      scrollPane.removeMouseWheelListener(mouseWheelListener)
      mouseWheelListener = null
    }
    if (treeSelectionListener != null) {
      table.tree.removeTreeSelectionListener(treeSelectionListener)
      treeSelectionListener = null
    }
  }

  override fun isLocationInExpandControl(path: TreePath, mouseX: Int, mouseY: Int): Boolean {
    return super.isLocationInExpandControl(path, mouseX + tree.treeOffset, mouseY)
  }

  override fun createNodeDimensions(): AbstractLayoutCache.NodeDimensions {
    return object : NodeDimensionsHandler() {
      override fun getNodeDimensions(
        value: Any,
        row: Int,
        depth: Int,
        expanded: Boolean,
        size: Rectangle,
      ): Rectangle {
        val dimensions = super.getNodeDimensions(value, row, depth, expanded, size)
        treeNodesWidth[row] = getRowX(row, depth) + dimensions.width
        dimensions.x -= tree.treeOffset

        updateScrollBar()
        return dimensions
      }
    }
  }

  private fun updateScrollBar() {
    var mostExpandedNodeWidth = 0
    for (r in 0 until tree.rowCount) {
      mostExpandedNodeWidth = maxOf(mostExpandedNodeWidth, treeNodesWidth.getOrElse(r) { 0 })
    }
    hScrollBarPanel.updateScrollBar(mostExpandedNodeWidth)
  }

  /** Copied from [BasicTreeUI] to control the support lines visibility. */
  override fun paintHorizontalPartOfLeg(
    g: Graphics,
    clipBounds: Rectangle,
    insets: Insets,
    bounds: Rectangle,
    path: TreePath,
    row: Int,
    isExpanded: Boolean,
    hasBeenExpanded: Boolean,
    isLeaf: Boolean,
  ) {
    if (!showSupportLines.invoke()) {
      return
    }

    val depth = path.pathCount - 1
    if ((depth == 0 || (depth == 1 && !isRootVisible)) && !showsRootHandles) {
      return
    }

    val indent =
      defaultPainter.getControlOffset(control, 2, false) -
        defaultPainter.getControlOffset(control, 1, false)
    val spaceForControlLine = indent - control.width / 2 - JBUIScale.scale(ICON_DEFAULT_PADDING)
    if (depth > 1 && spaceForControlLine > JBUIScale.scale(ICON_DEFAULT_PADDING)) {
      val lineY = bounds.y + bounds.height / 2
      val leftX =
        table.tree
          .getPathBounds(path.parentPath)
          ?.bounds
          ?.x
          ?.minus(control.width / 2)
          ?.minus(JBUIScale.scale(1))
      val rightX =
        if (isLeaf) bounds.x - JBUIScale.scale(ICON_DEFAULT_PADDING)
        else bounds.x - control.width - JBUIScale.scale(ICON_DEFAULT_PADDING)

      g.color = hashColor
      if (leftX != null && leftX < rightX) {
        val isCallStack = isCallStackNode(path)
        if (isCallStack) {
          drawDashedLine(g, lineY, leftX, rightX, false)
        } else {
          g.drawLine(leftX, lineY, rightX, lineY)
        }
      }
    }
  }

  /** Copied from [BasicTreeUI] to control the support lines visibility. */
  override fun paintVerticalPartOfLeg(
    g: Graphics,
    clipBounds: Rectangle,
    insets: Insets,
    path: TreePath,
  ) {
    if (!showSupportLines.invoke()) {
      return
    }

    val depth = path.pathCount - 1
    if (depth == 0 && !showsRootHandles && !isRootVisible) {
      return
    }

    val lineX = table.tree.getPathBounds(path.parentPath)?.bounds?.x?.plus(control.width / 2)

    val clipLeft = clipBounds.x
    val clipRight = clipBounds.x + (clipBounds.width - 1)

    if (lineX != null && lineX >= clipLeft && lineX <= clipRight) {
      val clipTop = clipBounds.y
      val clipBottom = clipBounds.y + clipBounds.height
      val parentBounds = getPathBounds(tree, path)
      val lastChildBounds = getPathBounds(tree, getLastChildPath(path))

      if (lastChildBounds == null) return

      var top: Int

      top =
        if (parentBounds == null) {
          (insets.top + verticalLegBuffer).coerceAtLeast(clipTop)
        } else (parentBounds.y + parentBounds.height + verticalLegBuffer).coerceAtLeast(clipTop)

      if (depth == 0 && !isRootVisible) {
        val model = model

        if (model != null) {
          val root = model.root

          if (model.getChildCount(root) > 0) {
            val firstChildPath = path.pathByAddingChild(model.getChild(root, 0))
            val firstChildBounds = getPathBounds(tree, firstChildPath)
            if (firstChildBounds != null)
              top =
                (insets.top + verticalLegBuffer).coerceAtLeast(
                  firstChildBounds.y + firstChildBounds.height / 2
                )
          }
        }
      }
      val bottom = (lastChildBounds.y + (lastChildBounds.height / 2)).coerceAtMost(clipBottom)
      if (top <= bottom) {
        g.color = hashColor
        paintVerticalLine(g, tree, lineX, top - JBUIScale.scale(1), bottom)
      }
    }
  }

  /** Copied from [BasicTreeUI] and modified the gap and dash length. */
  private fun drawDashedLine(g: Graphics, v: Int, v1: Int, v2: Int, isVertical: Boolean) {
    if (v1 >= v2) {
      return
    }
    var start = v1

    val g2d = g as Graphics2D
    val oldStroke = g2d.stroke
    val dashLength = 2f
    val gapLength = 3f
    val dashedStroke =
      BasicStroke(
        2f, // Line width
        BasicStroke.CAP_BUTT,
        BasicStroke.JOIN_ROUND,
        0f,
        floatArrayOf(dashLength, gapLength),
        0f,
      )
    g2d.stroke = dashedStroke

    if (isVertical) {
      g2d.drawLine(v, start, v, v2)
    } else {
      g2d.drawLine(start, v, v2, v)
    }

    g2d.stroke = oldStroke
  }
}
