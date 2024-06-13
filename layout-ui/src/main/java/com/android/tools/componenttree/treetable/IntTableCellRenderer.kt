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
import javax.swing.JLabel
import javax.swing.JTable
import javax.swing.border.Border
import javax.swing.table.TableCellRenderer

/** Renderer should use this for creating a border based on the [ColumnInfo] specification. */
fun ColumnInfo.createBorder(): Border =
  with(insets) { JBUI.Borders.empty(top, left + if (leftDivider) 1 else 0, bottom, right) }

/** Renderer used each [IntColumn] specified. */
class IntTableCellRenderer(private val columnInfo: IntColumn) : TableCellRenderer, JBLabel() {
  init {
    horizontalAlignment = JLabel.CENTER
    border = columnInfo.createBorder()
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
    text = intValue?.toString() ?: ""
    background = UIUtil.getTableBackground(isSelected, focused)
    foreground =
      columnInfo.foreground.takeIf { !isSelected || !focused }
        ?: UIUtil.getTableForeground(isSelected, focused)
    toolTipText = columnInfo.getTooltipText(value)
    return this
  }
}
