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

import java.awt.Rectangle
import java.awt.event.MouseWheelEvent
import java.awt.event.MouseWheelListener
import java.util.concurrent.ConcurrentHashMap
import javax.swing.JScrollPane
import javax.swing.event.ChangeListener
import javax.swing.plaf.basic.BasicTreeUI
import javax.swing.tree.AbstractLayoutCache
import javax.swing.tree.TreePath

/**
 * This custom TreeUI handles horizontal scrolling for components within the tree by adjusting the
 * components dimensions created by each node. And ensures components extend to the right edge by
 * managing indentation, a value known to the UI via getRowX.
 */
class ColumnTreeUI(
  private val table: TreeTableImpl,
  private val hScrollBarPanel: ColumnTreeScrollPanel,
  private val scrollPane: JScrollPane,
) : BasicTreeUI() {
  private var stateChangeListener: ChangeListener? = null
  private val treeNodesWidth = ConcurrentHashMap<Int, Int>()
  private var mouseWheelListener: MouseWheelListener? = null

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
    scrollPane.addMouseWheelListener(mouseWheelListener)
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
}
