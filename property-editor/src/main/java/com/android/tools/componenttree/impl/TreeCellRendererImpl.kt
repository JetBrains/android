/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.componenttree.impl

import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.plaf.basic.BasicTreeUI
import javax.swing.tree.TreeCellRenderer

/**
 * [TreeCellRenderer] for a [TreeImpl].
 *
 * This renderer facilitates the delegation to the proper node type renderer.
 * There is also support for renderer that can expand using the expandableItemsHandler of the tree.
 */
class TreeCellRendererImpl(
  tree: TreeImpl,
  private val model: ComponentTreeModelImpl
) : TreeCellRenderer {

  private val panel = PanelRenderer(tree)

  override fun getTreeCellRendererComponent(tree: JTree?,
                                            value: Any?,
                                            selected: Boolean,
                                            expanded: Boolean,
                                            leaf: Boolean,
                                            row: Int,
                                            hasFocus: Boolean): Component {
    val renderer = model.rendererOf(value) ?: return panel.apply { removeAll() }
    val component = renderer.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus)
    panel.add(component, BorderLayout.CENTER)
    panel.currentRow = row
    return panel
  }

  private class PanelRenderer(val tree: TreeImpl) : JPanel(BorderLayout()) {
    var currentRow: Int = 0

    init {
      border = JBUI.Borders.empty()
      background = UIUtil.TRANSPARENT_COLOR
    }

    /**
     * Compute the preferred size of a tree node.
     *
     * The preferred size is an important concept in the tree implementations.
     * The TreeUI will cache these sizes and use them for various operations.
     * Here we choose to specify the preferred size as the actual size or
     * at least the width of the tree. (See setBounds for an explanation).
     */
    override fun getPreferredSize(): Dimension {
      val leftOffset = getLeftOffset(tree, currentRow)
      val size = super.getPreferredSize()
      size.width = Math.max(size.width, tree.width - leftOffset)
      return size
    }

    /**
     * Update the bounds of the renderer.
     *
     * This method is typically called before the renderer is used for anything
     * other than computing its preferred size. Unless this row is currently expanded
     * using the expandableItemsHandler of the tree: adjust the size to the width of the tree.
     * This allows the renderer of the nodes to adjust to the available size.
     */
    override fun setBounds(x: Int, y: Int, width: Int, height: Int) {
      if (tree.isRowCurrentlyExpanded(currentRow)) {
        super.setBounds(x, y, width, height)
      }
      else {
        super.setBounds(x, y, Math.min(tree.width - x, width), height)
      }
    }

    /**
     * Compute the left offset of a row in the tree.
     *
     * Note: This code is based on the internals of the UI for the tree e.g. the method [BasicTreeUI.getRowX].
     */
    private fun getLeftOffset(tree: JTree, row: Int): Int {
      val ui = tree.ui as BasicTreeUI
      val path = tree.getPathForRow(row)
      val depth = path?.pathCount ?: 1
      return (ui.leftChildIndent + ui.rightChildIndent) * (depth - 1)
    }
  }
}
