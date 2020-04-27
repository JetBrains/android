/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.build.attribution.ui.view

import com.android.build.attribution.ui.model.TasksTreePresentableNodeDescriptor
import com.android.build.attribution.ui.model.WarningsTreePresentableNodeDescriptor
import com.android.build.attribution.ui.warningIcon
import com.intellij.ide.ui.UISettings.Companion.setupAntialiasing
import com.intellij.ide.util.treeView.NodeRenderer
import com.intellij.ui.RelativeFont
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.tree.ui.DefaultTreeUI
import com.intellij.util.ui.UIUtil
import java.awt.Color
import java.awt.Graphics
import java.awt.Shape
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode

class BuildAnalyzerMasterTreeCellRenderer : NodeRenderer() {

  private var durationTextPresentation: RightAlignedDurationTextPresentation? = null

  init {
    putClientProperty(DefaultTreeUI.SHRINK_LONG_RENDERER, true)
  }

  override fun customizeCellRenderer(
    tree: JTree,
    value: Any?,
    selected: Boolean,
    expanded: Boolean,
    leaf: Boolean,
    row: Int,
    hasFocus: Boolean
  ) {
    cleanup()

    val node = value as DefaultMutableTreeNode
    val userObj = node.userObject
    if (userObj is TasksTreePresentableNodeDescriptor) {
      customize(userObj.presentation, selected, hasFocus)
    }
    else if (userObj is WarningsTreePresentableNodeDescriptor) {
      customize(userObj.presentation, selected, hasFocus)
    }
    else {
      super.customizeCellRenderer(tree, value, selected, expanded, leaf, row, hasFocus)
    }
  }

  private fun cleanup() {
    durationTextPresentation = null
  }

  private fun customize(nodePresentation: BuildAnalyzerTreeNodePresentation, selected: Boolean, hasFocus: Boolean) {
    if (nodePresentation.showWarnIcon) {
      icon = warningIcon()
    }
    append(nodePresentation.mainText, SimpleTextAttributes.REGULAR_ATTRIBUTES, true)
    append(" ${nodePresentation.suffix}", SimpleTextAttributes.GRAYED_ATTRIBUTES)

    durationTextPresentation = nodePresentation.rightAlignedSuffix.let { text ->
      val metrics = getFontMetrics(RelativeFont.SMALL.derive(font))
      RightAlignedDurationTextPresentation(
        durationText = text,
        durationWidth = metrics.stringWidth(text),
        durationOffset = metrics.height / 2,
        durationColor = if (selected) UIUtil.getTreeSelectionForeground(hasFocus)
        else SimpleTextAttributes.GRAYED_ATTRIBUTES.fgColor
      )
    }
  }

  override fun paintComponent(g: Graphics) {
    setupAntialiasing(g)
    var clip: Shape? = null
    var width = width
    val height = height
    if (isOpaque) {
      // paint background for expanded row
      g.color = background
      g.fillRect(0, 0, width, height)
    }
    durationTextPresentation?.let {
      width -= it.durationWidth + it.durationOffset
      if (width > 0 && height > 0) {
        g.color = it.durationColor
        g.font = RelativeFont.SMALL.derive(font)
        g.drawString(it.durationText, width + it.durationOffset / 2, SimpleColoredComponent.getTextBaseLine(g.fontMetrics, height))
        clip = g.clip
        g.clipRect(0, 0, width, height)
      }
    }
    super.paintComponent(g)
    // restore clip area if needed
    if (clip != null) g.clip = clip
  }

  private data class RightAlignedDurationTextPresentation(
    val durationText: String,
    val durationColor: Color,
    val durationWidth: Int,
    val durationOffset: Int
  )
}

/**
 * Tasks tree node presentation used by [BuildAnalyzerMasterTreeCellRenderer] to render the node.
 */
data class BuildAnalyzerTreeNodePresentation(
  /** Node main text rendered in standard font. */
  val mainText: String,
  /** Additional text after main rendered in grey. Used to show warnings counter. */
  val suffix: String = "",
  /** Text that is rendered on the right side. Used to show the execution time. */
  val rightAlignedSuffix: String = "",
  /** If warn icon should be rendered next to the node. */
  val showWarnIcon: Boolean = false
)