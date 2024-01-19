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
package com.android.tools.property.panel.impl.ui

import com.android.tools.adtui.common.secondaryPanelBackground
import com.android.tools.adtui.stdui.StandardDimensions.OUTER_BORDER_UNSCALED
import com.android.tools.property.panel.api.EnumValue
import com.android.tools.property.panel.api.HeaderEnumValue
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes.GRAYED_ATTRIBUTES
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.BorderFactory
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JSeparator
import javax.swing.UIManager

private const val INDENT_NO_HEADER = 0
private const val INDENT_WITH_HEADER = 10

/**
 * Cell renderer for the ComboBox standard control.
 *
 * This renderer supports the display of headers, separators, and indented items.
 */
open class EnumValueListCellRenderer : ColoredListCellRenderer<EnumValue>() {
  private val separator = createCenteredSeparator()
  private val headerLabel = JBLabel()

  init {
    headerLabel.foreground = JBColor(0x444444, 0xCCCCCC)
    headerLabel.border = JBUI.Borders.emptyLeft(TOP_ITEM_INDENT)
    ipad.left = 0
    ipad.right = 0
  }

  private fun createCenteredSeparator(): JComponent {
    // Make the separator appear in the middle of the list element.
    // Remove this when CompoBoxWithWidePopup no longer requires all elements to have this minimum
    // height.
    // See WideSelectionListUI.updateLayoutState
    val separator = JPanel(BorderLayout())
    val line = JSeparator()
    val panel =
      object : JPanel(BorderLayout()) {
        override fun updateUI() {
          super.updateUI()
          updateBorder(this, line)
        }
      }
    panel.add(line, BorderLayout.CENTER)
    panel.background = secondaryPanelBackground
    separator.add(panel, BorderLayout.CENTER)
    separator.background = secondaryPanelBackground
    return separator
  }

  protected fun updateBorder(panel: JPanel, line: JComponent) {
    val rowHeight = UIManager.getInt("List.rowHeight")
    val spacing = (rowHeight - line.preferredSize.height) / 2 - JBUI.scale(OUTER_BORDER_UNSCALED)
    panel.border = BorderFactory.createEmptyBorder(spacing, 0, spacing, 0)
  }

  override fun getListCellRendererComponent(
    list: JList<out EnumValue>,
    item: EnumValue?,
    index: Int,
    selected: Boolean,
    hasFocus: Boolean,
  ): Component? {
    clear()
    when (item) {
      null -> return this
      EnumValue.SEPARATOR -> return separator
      is HeaderEnumValue -> return getHeaderRenderer(item.header, item.headerIcon)
      else -> return super.getListCellRendererComponent(list, item, index, selected, hasFocus)
    }
  }

  protected open fun getHeaderRenderer(header: String, headerIcon: Icon?): Component {
    headerLabel.text = header
    headerLabel.icon = headerIcon
    return headerLabel
  }

  override fun customizeCellRenderer(
    list: JList<out EnumValue>,
    item: EnumValue,
    index: Int,
    selected: Boolean,
    hasFocus: Boolean,
  ) {
    ipad.left = indent(item, index)
    customize(item)
  }

  protected open fun customize(item: EnumValue) {
    if (item.value == null) {
      append(item.display, GRAYED_ATTRIBUTES)
    } else {
      append(item.display)
    }
  }

  // The index will be -1 if this is a dropdown (i.e. a non editable comboBox) when displaying the
  // selected value
  private fun indent(item: EnumValue, index: Int) =
    when {
      index < 0 -> 0
      item.indented -> SUB_ITEM_INDENT
      else -> TOP_ITEM_INDENT
    }

  companion object {
    private val TOP_ITEM_INDENT = JBUI.scale(INDENT_NO_HEADER)
    private val SUB_ITEM_INDENT = JBUI.scale(INDENT_WITH_HEADER)
  }
}
