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

import com.android.tools.adtui.common.ColoredIconGenerator
import com.android.tools.componenttree.api.BadgeItem
import com.intellij.openapi.util.Key
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import javax.swing.BoxLayout
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.plaf.basic.BasicTreeUI
import javax.swing.tree.TreeCellRenderer
import kotlin.math.max
import kotlin.math.min

val BADGE_ITEM = Key<BadgeItem>("BADGE_ITEM")

/**
 * [TreeCellRenderer] for a [TreeImpl].
 *
 * This renderer facilitates the delegation to the proper node type renderer.
 * There is also support for renderer that can expand using the expandableItemsHandler of the tree.
 */
class TreeCellRendererImpl(
  tree: TreeImpl,
  badges: List<BadgeItem>,
  private val model: ComponentTreeModelImpl
) : TreeCellRenderer {

  private val panel = PanelRenderer(tree, badges)

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
    panel.currentDepth = computeDepth(value)
    panel.updateBadges(value, tree?.hasFocus() ?: false && selected)
    return panel
  }

  // The first time around Tree.getPathForRow(row) may return null.
  // Compute the depth directly from the node value instead of using getPathForRow().
  private fun computeDepth(value: Any?): Int {
    return generateSequence(value) { model.parent(it) }.count()
  }

  private class PanelRenderer(
    private val tree: TreeImpl,
    private val badges: List<BadgeItem>
  ) : JPanel(BorderLayout()) {
    private val emptyIcon = EmptyIcon.ICON_16
    private val badgePanel = JPanel()

    var currentRow: Int = 0
    var currentDepth: Int = 1

    init {
      border = JBUI.Borders.empty()
      background = UIUtil.TRANSPARENT_COLOR
      if (badges.isNotEmpty()) {
        val layout = BoxLayout(badgePanel, BoxLayout.LINE_AXIS)
        badgePanel.layout = layout
        badgePanel.border = JBUI.Borders.empty()
        badgePanel.background = UIUtil.TRANSPARENT_COLOR
        badges.forEach {
          val label = JBLabel()
          label.putClientProperty(BADGE_ITEM, it)
          label.alignmentY = Component.CENTER_ALIGNMENT
          badgePanel.add(label)
        }
        add(badgePanel, BorderLayout.EAST)
      }
    }

    fun updateBadges(value: Any?, hasFocus: Boolean) {
      for ((i, badge) in badges.withIndex()) {
        val label = badgePanel.getComponent(i) as JBLabel
        val icon = value?.let { badge.getIcon(it) }
        val badgeIcon = icon?.let { if (hasFocus) ColoredIconGenerator.generateWhiteIcon(it) else it }
        label.icon = badgeIcon ?: emptyIcon
        label.toolTipText = value?.let { badge.getTooltipText(it) }
      }
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
      val leftOffset = getLeftOffset(tree)
      val size = super.getPreferredSize()
      size.width = max(size.width, tree.width - leftOffset)
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
        super.setBounds(x, y, min(tree.width - x, width), height)
      }
    }

    /**
     * Compute the left offset of a row in the tree.
     *
     * Note: This code is based on the internals of the UI for the tree e.g. the method [BasicTreeUI.getRowX].
     */
    private fun getLeftOffset(tree: JTree): Int {
      val ui = tree.ui as BasicTreeUI
      return (ui.leftChildIndent + ui.rightChildIndent) * (currentDepth - 1)
    }
  }
}
