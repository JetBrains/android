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
import com.android.tools.componenttree.api.NodeType
import com.intellij.openapi.util.Key
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import javax.swing.BoxLayout
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.tree.TreeCellRenderer

val BADGE_ITEM = Key<BadgeItem>("BADGE_ITEM")

/**
 * [TreeCellRenderer] for a [TreeImpl].
 *
 * This renderer facilitates the delegation to the proper node type renderer.
 * There is also support for hiding the badges during expansion using the expandableItemsHandler of the tree.
 */
class TreeCellRendererImpl(
  tree: TreeImpl,
  badges: List<BadgeItem>,
  private val model: ComponentTreeModelImpl
) : TreeCellRenderer {

  private val panel = PanelRenderer(tree, badges)

  override fun getTreeCellRendererComponent(
    tree: JTree,
    value: Any?,
    selected: Boolean,
    expanded: Boolean,
    leaf: Boolean,
    row: Int,
    hasFocus: Boolean
  ): Component {
    val renderer = model.rendererOf(value) ?: return panel.apply { removeAll() }
    // the "hasFocus" parameter is wrong when there are multiple selected nodes so check the tree instead:
    val component = renderer.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, tree.hasFocus())
    panel.add(component, BorderLayout.WEST)
    panel.currentDepth = model.computeDepth(value)
    panel.updateBadges(value, row, selected, tree.hasFocus())
    return panel
  }
}

/**
 * Renderer used as implementation a [TreeCellRenderer].
 *
 * The contents is a combination of the renderer specified by the [NodeType] of the current
 * row value and a panel of badges.
 */
private class PanelRenderer(
  private val tree: TreeImpl,
  private val badges: List<BadgeItem>
) : JPanel() {
  private val emptyIcon = EmptyIcon.ICON_16
  private val badgePanel = JPanel()

  var currentDepth: Int = 1

  init {
    layout = PanelRendererLayout()
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
        badgePanel.add(label)
      }
      add(badgePanel, BorderLayout.CENTER)
    }
  }

  fun updateBadges(value: Any?, row: Int, selected: Boolean, hasFocus: Boolean) {
    for ((i, badge) in badges.withIndex()) {
      val label = badgePanel.getComponent(i) as JBLabel
      val icon = value?.let { badge.getIcon(it) }
      val badgeIcon = icon?.let { if (selected && hasFocus) ColoredIconGenerator.generateWhiteIcon(it) else it }
      label.icon = badgeIcon ?: emptyIcon
      label.toolTipText = value?.let { badge.getTooltipText(it) }
    }
    badgePanel.isVisible = !tree.isRowCurrentlyExpanded(row)
  }

  private inner class PanelRendererLayout : BorderLayout() {
    override fun layoutContainer(target: Container) {
      super.layoutContainer(target)
      val width = badgePanel.preferredSize.width
      badgePanel.setBounds(tree.computeMaxRenderWidth(currentDepth), badgePanel.y, width, badgePanel.height)
    }
  }
}
