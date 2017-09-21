/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.sqlite.ui

import com.intellij.util.ui.JBUI
import java.awt.Component
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JTable
import javax.swing.SwingConstants
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableCellRenderer

/**
 * Implementation of [DefaultTableCellRenderer] for header cells of the [JTable] used to display values of a
 * [com.android.tools.idea.sqlite.model.SqliteResultSet].
 */
class ResultSetTreeHeaderRenderer(private val tableCellRenderer: TableCellRenderer) : DefaultTableCellRenderer() {
  private val TEXT_RENDERER_HORIZ_PADDING = 6
  private val TEXT_RENDERER_VERT_PADDING = 4

  override fun getTableCellRendererComponent(table: JTable?, value: Any,
                                             isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int): Component {
    val component = tableCellRenderer.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
    if (component is JLabel) {
      component.horizontalAlignment = SwingConstants.LEADING
    }
    if (component is JComponent) {
      component.border = JBUI.Borders.empty(TEXT_RENDERER_VERT_PADDING, TEXT_RENDERER_HORIZ_PADDING)
    }
    return component
  }
}