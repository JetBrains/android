/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.structure

import com.android.tools.adtui.common.AdtUiUtils
import com.android.tools.adtui.common.ColoredIconGenerator
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.uibuilder.handlers.ViewHandlerManager
import com.android.tools.lint.detector.api.stripIdPrefix
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.NamedColorUtil
import com.intellij.util.ui.UIUtil
import icons.StudioIcons
import java.awt.Component
import java.awt.FlowLayout
import java.awt.Font
import java.awt.font.TextAttribute
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.plaf.basic.BasicTreeUI
import javax.swing.tree.TreeCellRenderer
import javax.swing.tree.TreePath


/**
 * This cell renderer displays two labels.
 * The first one shows the id of the component if present, or the tag and the icon.
 * The second one shows the title returned by the handler.
 *
 * Labels are then trimmed with an ellipsis if they don't fit the inside the tree.
 * We also ensure that we leave enough space to paint the error badges on the right.
 */
class NlTreeCellRenderer(
  private val myBadgeHandler: NlTreeBadgeHandler
) : JPanel(FlowLayout(FlowLayout.LEFT, 0, JBUI.scale(2))),
    TreeCellRenderer {
  private val primaryLabel = JLabel()
  private val secondaryLabel = JLabel().apply {
    border = JBUI.Borders.emptyLeft(8)
    foreground = NamedColorUtil.getInactiveTextColor()
    font = UIUtil.getLabelFont(UIUtil.FontSize.SMALL)
  }

  private val primaryLabelMetrics = primaryLabel.getFontMetrics(primaryLabel.font)
  private val secondaryLabelMetrics = secondaryLabel.getFontMetrics(secondaryLabel.font)
  private val nlComponentFont = primaryLabel.font
  private val otherFont = primaryLabel.font.deriveFont(Font.ITALIC)

  init {
    alignmentY = CENTER_ALIGNMENT
    add(primaryLabel)
    add(secondaryLabel)
  }

  override fun getTreeCellRendererComponent(
    tree: JTree,
    value: Any,
    selected: Boolean,
    expanded: Boolean,
    leaf: Boolean,
    row: Int,
    hasFocus: Boolean
  ): Component {
    primaryLabel.text = null
    primaryLabel.icon = null
    secondaryLabel.text = null
    val treeFocused = tree.hasFocus()
    primaryLabel.foreground = UIUtil.getListForeground(selected, treeFocused)
    secondaryLabel.foreground = if(selected && treeFocused) primaryLabel.foreground else UIUtil.getLabelDisabledForeground()

    if (value is String) {
      primaryLabel.text = value
      primaryLabel.font = otherFont
      return this
    }

    if (value !is NlComponent) return this

    // Change the font and forground based on its visibility
    when (getVisibilityFromParents(value)) {
      NlVisibilityModel.Visibility.NONE -> {
        primaryLabel.font = nlComponentFont
      }
      NlVisibilityModel.Visibility.VISIBLE -> {
        primaryLabel.font = nlComponentFont
      }
      NlVisibilityModel.Visibility.INVISIBLE -> {
        primaryLabel.font = nlComponentFont
        primaryLabel.foreground = UIUtil.makeTransparent(primaryLabel.foreground, primaryLabel.background, 0.5)
      }
      NlVisibilityModel.Visibility.GONE -> {
        primaryLabel.font = nlComponentFont.deriveFont(
          nlComponentFont.attributes + (TextAttribute.STRIKETHROUGH to TextAttribute.STRIKETHROUGH_ON))
        primaryLabel.foreground = UIUtil.makeTransparent(primaryLabel.foreground, primaryLabel.background, 0.5)
      }
    }

    val path = tree.getPathForRow(row)
    val leftOffset = getLeftOffset(tree, path)

    val facet = value.model.facet
    val handler = if (!facet.isDisposed) ViewHandlerManager.get(facet).getHandler(value) else null

    primaryLabel.icon = handler?.getIcon(value)?.let {
      if (selected && treeFocused) ColoredIconGenerator.generateWhiteIcon(it) else it
    } ?: StudioIcons.LayoutEditor.Palette.VIEW

    val id = stripIdPrefix(value.id)
    var primaryLabelText = id.ifBlank { handler?.getTitle(value) }
    if (primaryLabelText.isNullOrBlank()) {
      primaryLabelText = value.tagName
    }
    var secondaryLabelText = handler?.getTitleAttributes(value) ?: ""

    val treeContainerWidth = tree.width
    val cellWidth = treeContainerWidth - leftOffset

    primaryLabel.text = primaryLabelText
    secondaryLabel.text = secondaryLabelText

    if (!(tree as NlComponentTree).shouldDisplayFittedText(row)) return this

    // Trim text
    var availableSpace = computeAvailableSpace(row, primaryLabel.icon, cellWidth)
    val previousLength = primaryLabelText.length
    primaryLabelText = AdtUiUtils.shrinkToFit(primaryLabelText, primaryLabelMetrics, availableSpace.toFloat())

    // If the primary text has been shrunk, there is no need to display the secondary
    secondaryLabelText = if (primaryLabelText.length == previousLength) {
      availableSpace -= primaryLabelMetrics.stringWidth(primaryLabelText)
      AdtUiUtils.shrinkToFit(secondaryLabelText, secondaryLabelMetrics, availableSpace.toFloat())
    }
    else {
      ""
    }
    toolTipText = createTooltipText(value.tagName, primaryLabelText, secondaryLabelText)
    primaryLabel.text = primaryLabelText
    secondaryLabel.text = secondaryLabelText
    return this
  }

  private fun createTooltipText(tagName: String, primaryLabelText: String, secondaryLabelText: String) =
    """
    <html>
        ${tagName.substringAfterLast('.', tagName)}<br/>
        ${getHiddenText(primaryLabelText, secondaryLabelText)}
    </html>
    """.trimIndent()

  /**
   * We only want to show the hidden part of the cell in the tooltip:
   * if nothing is hidden there is no tooltip and if only the second part is
   * hidden, we only display the second part in the tooltip.
   */
  private fun getHiddenText(primaryLabelText: String, secondaryLabelText: String): String {
    var tooltip: String = if (primaryLabel.text != primaryLabelText) primaryLabel.text else ""
    if (secondaryLabel.text != secondaryLabelText) {
      tooltip += " ${secondaryLabel.text}"
    }
    return tooltip
  }

  private fun getLeftOffset(tree: JTree, path: TreePath?): Int {
    val ui = tree.ui as BasicTreeUI
    val depth = path?.pathCount ?: 1
    return (ui.leftChildIndent + ui.rightChildIndent) * depth
  }

  private fun computeAvailableSpace(row: Int, icon: Icon?, cellWidth: Int) =
    (cellWidth
     - primaryLabel.iconTextGap
     - myBadgeHandler.getTotalBadgeWidth(row)
     - (icon?.iconWidth ?: 0))
}
