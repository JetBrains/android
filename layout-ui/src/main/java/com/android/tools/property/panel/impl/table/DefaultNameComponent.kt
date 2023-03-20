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
package com.android.tools.property.panel.impl.table

import com.android.tools.adtui.common.ColoredIconGenerator
import com.android.tools.adtui.stdui.KeyStrokes
import com.android.tools.adtui.stdui.registerActionKey
import com.android.tools.property.panel.api.PropertyItem
import com.android.tools.property.panel.api.TableSupport
import com.android.tools.property.panel.impl.ui.PropertyTooltip
import com.android.tools.property.ptable.PTable
import com.android.tools.property.ptable.PTableGroupItem
import com.android.tools.property.ptable.PTableItem
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.ide.ui.laf.darcula.ui.DarculaTextBorder
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTable
import kotlin.math.max

const val LEFT_STANDARD_INDENT = 2
const val RIGHT_STANDARD_INDENT = 2
const val MIN_SPACING = 2
const val DEPTH_INDENT = 8

/**
 * Component used to display the name of a property in the properties panel.
 *
 * This component is used for both rendering and editing of group properties.
 * A group property is editable in the sense the user can expand and collapse
 * the group using the expand icon shown to the left of the property name.
 * A custom icon can be shown for non group items.
 */
class DefaultNameComponent(private val tableSupport: TableSupport? = null) : JPanel(BorderLayout()) {
  private val iconLabel = LabelWithFocusBorder()
  private val label = LabelWithTooltipFromParent()
  private var standardIndent = 0
  private var standardRightIndent = 0
  private var minSpacing = 0
  private var depthIndent = 0
  private var topOffset = 0
  private var iconWidth = 0
  private var labelFont = UIUtil.getLabelFont(UIUtil.FontSize.SMALL)
  private var expandedIcon = EmptyIcon.ICON_16
  private var collapsedIcon = EmptyIcon.ICON_16
  private var expandedWhiteIcon = EmptyIcon.ICON_16
  private var collapsedWhiteIcon = EmptyIcon.ICON_16

  val icon: Icon?
    get() = iconLabel.icon

  init {
    iconLabel.verticalAlignment = JLabel.TOP
    iconLabel.isFocusable = false
    iconLabel.addMouseListener(object : MouseAdapter() {
      override fun mousePressed(event: MouseEvent) {
        toggle()
      }
    })

    label.verticalAlignment = JLabel.TOP
    label.isFocusable = false
    updateUI()

    registerActionKey({ toggle() }, KeyStrokes.ENTER, "toggle")
    registerActionKey({ toggle() }, KeyStrokes.SPACE, "toggle")

    addMouseListener(object : MouseAdapter() {
      override fun mousePressed(event: MouseEvent) {
        requestFocusInWindow()
      }

      override fun mouseClicked(event: MouseEvent) {
        if (event.clickCount > 1) {
          toggle()
        }
      }
    })

    add(iconLabel, BorderLayout.WEST)
    add(label, BorderLayout.CENTER)
  }

  private fun toggle() {
    tableSupport?.toggleGroup()
  }

  override fun updateUI() {
    super.updateUI()
    topOffset = computeTopNameTableCellOffset()
    standardIndent = JBUI.scale(LEFT_STANDARD_INDENT)
    standardRightIndent = JBUI.scale(RIGHT_STANDARD_INDENT)
    minSpacing = JBUI.scale(MIN_SPACING)
    depthIndent = JBUI.scale(DEPTH_INDENT)
    iconWidth = UIUtil.getTreeCollapsedIcon().iconWidth
    expandedIcon = UIUtil.getTreeExpandedIcon()
    collapsedIcon = UIUtil.getTreeCollapsedIcon()
    expandedWhiteIcon = ColoredIconGenerator.generateWhiteIcon(UIUtil.getTreeExpandedIcon())
    collapsedWhiteIcon = ColoredIconGenerator.generateWhiteIcon(UIUtil.getTreeCollapsedIcon())
    labelFont = UIUtil.getLabelFont(UIUtil.FontSize.SMALL)
  }

  override fun getToolTipText(event: MouseEvent): String? {
    // Trick: Use the component from the event.source for tooltip in tables. See TableEditor.getToolTip().
    val component = event.source as? JTable ?: return null
    val tableRow = component.rowAtPoint(event.point)
    val tableColumn = component.columnAtPoint(event.point)
    if (tableRow < 0 || tableColumn < 0) {
      return null
    }
    val item = component.getValueAt(tableRow, tableColumn)
    val property = item as? PropertyItem ?: return null
    return PropertyTooltip.setToolTip(component, event, property, forValue = tableColumn == 1, text = "")
  }

  /**
   * Call this method to setup the component as a renderer or editor for a given property item.
   */
  fun setUpItem(table: PTable, item: PTableItem, depth: Int, isSelected: Boolean, hasFocus: Boolean, isExpanded: Boolean): JComponent {
    label.text = if (isExpanded) "<html><nobr>${item.name}</nobr></html>" else item.name
    label.font = labelFont
    background = UIUtil.getTableSelectionBackground(true)
    var indent = standardIndent + depth * depthIndent
    var iconTextGap = 0
    var iconTopOffset = 0
    when {
      item is PTableGroupItem -> {
        iconLabel.icon = getGroupNodeIcon(table.isExpanded(item), isSelected && hasFocus)
      }
      item is PropertyItem && item.namespaceIcon != null -> {
        iconLabel.icon = item.namespaceIcon?.let { if (isSelected && hasFocus) ColoredIconGenerator.generateWhiteIcon(it) else it }
      }
      else -> {
        iconLabel.icon = null
      }
    }

    if (isSelected && hasFocus) {
      label.foreground = UIUtil.getTreeSelectionForeground(true)
      background = UIUtil.getTreeSelectionBackground(true)
    }
    else {
      label.foreground = table.foregroundColor
      background = table.backgroundColor
    }

    // Some editors in the layout inspector are much taller than a single text editor.
    // Make the label look centered with a standard text editor, and top aligned with a taller editor.
    label.border = BorderFactory.createEmptyBorder(topOffset, 0, 0, standardRightIndent)

    if (iconLabel.icon != null) {
      iconTextGap = max(iconWidth - iconLabel.icon.iconWidth, minSpacing)
      iconTopOffset = topOffset + (label.preferredSize.height - iconLabel.icon.iconHeight) / 2 - 1
    }
    else {
      indent += iconWidth + minSpacing
    }
    iconLabel.border = BorderFactory.createEmptyBorder(iconTopOffset, indent, 0, iconTextGap)

    return this
  }

  private fun getGroupNodeIcon(isGroupExpanded: Boolean, isSelectedWithFocus: Boolean) =
    if (isSelectedWithFocus) {
      if (isGroupExpanded) expandedWhiteIcon else collapsedWhiteIcon
    }
    else {
      if (isGroupExpanded) expandedIcon else collapsedIcon
    }

  /**
   * Compute the top offset for making a small label appear vertical centered with a standard text editor.
   *
   * A standard editor is using a normal size font and a DarculaTextBorder.
   */
  private fun computeTopNameTableCellOffset(): Int {
    val label = JBLabel("M")
    label.font = UIUtil.getLabelFont(UIUtil.FontSize.SMALL)
    val height = label.preferredSize.height
    label.font = StartupUiUtil.labelFont
    label.border = DarculaTextBorder()
    return (label.preferredSize.height - height) / 2
  }

  /**
   * Label which delegates tooltip to the parent.
   *
   * The label itself does not have the context to display tooltip for the property.
   */
  private open class LabelWithTooltipFromParent : JBLabel() {
    override fun getToolTipText(event: MouseEvent): String? =
      (parent as? JComponent)?.getToolTipText(event)
  }

  /**
   * Label which displays a focus border around the label.
   *
   * This is used for the tree expansion icons.
   * Notice that it is the parent panel [DefaultNameComponent] that has
   * focus not the label.
   */
  private class LabelWithFocusBorder : LabelWithTooltipFromParent() {

    override fun paintComponent(g: Graphics) {
      super.paintComponent(g)
      val icon = icon ?: return
      if (parent?.hasFocus() == true && g is Graphics2D) {
        val indent = JBUI.scale(LEFT_STANDARD_INDENT)
        val insets = border.getBorderInsets(this)
        val g2 = g.create() as Graphics2D
        try {
          g2.translate(insets.left - indent, insets.top - indent)
          DarculaUIUtil.paintFocusBorder(g2, icon.iconWidth + 2 * indent, icon.iconHeight + 2 * indent, 0f, true)
        }
        finally {
          g2.dispose()
        }
      }
    }
  }
}
