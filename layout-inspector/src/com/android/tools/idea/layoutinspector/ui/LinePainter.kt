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
package com.android.tools.idea.layoutinspector.ui

import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.tree.TreeSettings
import com.android.tools.idea.layoutinspector.tree.TreeViewNode
import com.intellij.ui.JBColor
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.tree.ui.Control
import com.intellij.ui.treeStructure.Tree
import org.jetbrains.annotations.VisibleForTesting
import java.awt.Component
import java.awt.Graphics
import javax.swing.tree.TreeModel
import javax.swing.tree.TreePath

val LINES = LinePainter(Control.Painter.DEFAULT)

class LinePainter(private val basePainter: Control.Painter) : Control.Painter by basePainter {

  override fun paint(
    c: Component,
    g: Graphics,
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    control: Control,
    depth: Int,
    leaf: Boolean,
    expanded: Boolean,
    selected: Boolean
  ) {
    val tree = c as Tree
    val treeSettings = LayoutInspector.get(tree)?.treeSettings
    val path = tree.getClosestPathForLocation(x + width / 2, y + height / 2) ?: return
    basePainter.paint(c, g, x, y, width, height, control, depth, leaf, expanded, selected)
    g.color = JBColor.GRAY

    val model = tree.model
    if ((depth == 0 || depth == 1 && !tree.isRootVisible) && !tree.showsRootHandles) {
      return
    }

    var node = nodeOf(path)
    var parent = path.parentPath
    var lastNode = getLastOfMultipleChildren(model, treeSettings, nodeOf(parent))

    // Horizontal line:
    val indent = getControlOffset(control, 2, false) - getControlOffset(control, 1, false)
    val spaceForControlLine = indent - control.width / 2 - JBUIScale.scale(4)
    if (depth > 1 && lastNode != null && spaceForControlLine > JBUIScale.scale(4)) {
      val lineY = y + height / 2
      val leftX = x + getControlOffset(control, depth - 1, false) + control.width / 2
      val rightX = x + (if (leaf) getRendererOffset(control, depth, true) else getControlOffset(control, depth, false)) - JBUIScale.scale(4)
      if (leftX < rightX) {
        g.drawLine(leftX, lineY, rightX, lineY)
      }
    }

    // Vertical lines:
    var directChild = true
    var lineDepth = depth - 1
    while (parent != null && lineDepth > 0) {
      if (lastNode != null && (node !== lastNode || directChild)) {
        val xMid = x + getControlOffset(control, lineDepth, false) + control.width / 2
        val bottom = if (node === lastNode) y + height / 2 else y + height
        g.drawLine(xMid, y, xMid, bottom)
      }
      node = nodeOf(parent)
      parent = parent.parentPath
      lastNode = getLastOfMultipleChildren(model, treeSettings, nodeOf(parent))
      directChild = false
      lineDepth--
    }
  }

  private fun nodeOf(path: TreePath): TreeViewNode =
    path.lastPathComponent as TreeViewNode

  /**
   * Return the last of the children if support lines are to be drawn, otherwise null.
   *
   * This gets a little tricky around:
   * - callstack view: here we do NOT want to show support lines when multiple calls are shown as children under this [node].
   * - filtered views: if a [node] has multiple children as a result of one of its children were filtered out, we DO want support lines.
   */
  @VisibleForTesting
  fun getLastOfMultipleChildren(model: TreeModel, treeSettings: TreeSettings?, node: TreeViewNode): TreeViewNode? {
    val count = node.children.size
    val last = if (count > 1) model.getChild(node, count - 1) as TreeViewNode else return null
    val parent = ViewNode.readAccess { last.view.parent }
    return if (treeSettings?.let { parent?.findClosestUnfilteredNode(it)?.treeNode } === node) last else null
  }
}
