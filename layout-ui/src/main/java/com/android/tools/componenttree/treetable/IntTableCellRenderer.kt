/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.tools.componenttree.api.ColumnInfo
import com.android.tools.componenttree.api.IntColumn
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Component
import java.awt.Cursor
import java.awt.FlowLayout
import java.awt.Font
import java.awt.font.TextAttribute
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.border.Border
import javax.swing.table.TableCellRenderer

/** Renderer should use this for creating a border based on the [ColumnInfo] specification. */
fun ColumnInfo.createBorder(): Border = with(insets) { JBUI.Borders.empty(top, left + if (leftDivider) 1 else 0, bottom, right) }

/** Renderer used each [IntColumn] specified. */
class IntTableCellRenderer(private val columnInfo: IntColumn) : TableCellRenderer {
  private val panel = JPanel(FlowLayout())
  private val label = JBLabel()

  init {
    panel.add(label)
    panel.border = columnInfo.createBorder()
  }

  override fun getTableCellRendererComponent(
    table: JTable,
    value: Any,
    isSelected: Boolean,
    hasFocus: Boolean,
    row: Int,
    column: Int,
  ): Component {
    val intValue = columnInfo.getInt(value).takeIf { it != 0 }
    val focused = table.hasFocus()
    val asLink = columnInfo.isActionEnabled(value)
    label.text = intValue?.toString() ?: ""
    panel.background = UIUtil.getTableBackground(isSelected, focused)
    label.foreground =
      when {
        asLink -> JBUI.CurrentTheme.Link.Foreground.ENABLED
        isSelected && focused -> UIUtil.getTableForeground(true, true)
        else -> columnInfo.foreground ?: UIUtil.getTableForeground(isSelected, focused)
      }
    label.font = UIUtil.getLabelFont().withUnderline(asLink)
    panel.toolTipText = columnInfo.getTooltipText(value)
    if (columnInfo.hasCustomCursor) {
      label.cursor = if (asLink) Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) else Cursor.getDefaultCursor()
    }
    return panel
  }

  fun getRenderComponentForStringValue(table: JTable, value: String): Component {
    val component = getTableCellRendererComponent(table, 0, false, false, 0, 0)
    label.text = value
    return component
  }

  private fun Font.withUnderline(underline: Boolean): Font {
    if (!underline) {
      return this
    }
    return deriveFont(attributes + (TextAttribute.UNDERLINE to TextAttribute.UNDERLINE_ON))
  }
}
