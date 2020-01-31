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

import com.android.tools.adtui.stdui.StandardDimensions
import com.android.tools.property.panel.api.EnumValue
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes.GRAYED_ATTRIBUTES
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.BorderFactory
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JSeparator

private const val INDENT_NO_HEADER = 0
private const val INDENT_WITH_HEADER = 10

/**
 * Cell renderer for the ComboBox standard control.
 *
 * This renderer supports the display of headers, separators, and indented items.
 */
open class EnumValueListCellRenderer : ColoredListCellRenderer<EnumValue>() {
  private val panel = JPanel(BorderLayout())
  private val separator = JSeparator()
  private val header = JBLabel()

  init {
    header.foreground = JBColor(0x444444, 0xCCCCCC)
    header.border = JBUI.Borders.emptyLeft(TOP_ITEM_INDENT)
  }

  override fun getListCellRendererComponent(
    list: JList<out EnumValue>, item: EnumValue?, index: Int, selected: Boolean, hasFocus: Boolean
  ): Component? {
    clear()
    if (item == null) return this
    val lineItem = super.getListCellRendererComponent(list, item, index, selected, hasFocus)
    if (index < 0) return lineItem
    separator.isVisible = item.separator
    header.isVisible = item.header.isNotEmpty()
    header.text = item.header
    header.background = list.background
    panel.removeAll()
    panel.background = list.background
    panel.add(separator, BorderLayout.NORTH)
    panel.add(header, BorderLayout.CENTER)
    panel.add(lineItem, BorderLayout.SOUTH)
    return panel
  }

  override fun customizeCellRenderer(list: JList<out EnumValue>, item: EnumValue, index: Int, selected: Boolean, hasFocus: Boolean) {
    border = BorderFactory.createEmptyBorder(0, indent(item, index), 0, 0)
    customize(item)
  }

  protected open fun customize(item: EnumValue) {
    if(item.value == null) {
      append(item.display, GRAYED_ATTRIBUTES)
    }
    else {
      append(item.display)
    }
  }

  // The index will be -1 if this is a dropdown (i.e. a non editable comboBox) when displaying the selected value
  private fun indent(item: EnumValue, index: Int) =
    when {
      index < 0 -> 0
      item.indented -> SUB_ITEM_INDENT
      else -> TOP_ITEM_INDENT
    }

  companion object {
    private val TOP_ITEM_INDENT = StandardDimensions.HORIZONTAL_PADDING + JBUI.scale(INDENT_NO_HEADER)
    private val SUB_ITEM_INDENT = StandardDimensions.HORIZONTAL_PADDING + JBUI.scale(INDENT_WITH_HEADER)
  }
}
