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
package com.android.tools.idea.editors.layoutInspector.ptable

import com.android.tools.idea.editors.layoutInspector.ui.PropertiesTablePanel
import com.intellij.openapi.roots.libraries.LibraryTable
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.table.TableCellRenderer

/**
 * Renderer for an editable item, Used if the [LITableItem] has isEditable return true.
 */
class LIEditableItemCellRenderer : TableCellRenderer {
  private val myPanel: JPanel = JPanel(BorderLayout(if (SystemInfo.isMac) 0 else 2, 0))
  private val myTextField: JBTextField = JBTextField()

  init {
    myPanel.add(myTextField, BorderLayout.CENTER)
  }

  override fun getTableCellRendererComponent(
    table: JTable,
    value: Any,
    isSelected: Boolean,
    hasFocus: Boolean,
    row: Int,
    col: Int
  ): Component {
    val fg: Color
    val bg: Color
    if (isSelected) {
      fg = UIUtil.getTableSelectionForeground(true)
      bg = UIUtil.getTableSelectionBackground(true)
    } else {
      fg = UIUtil.getTableForeground()
      bg = PropertiesTablePanel.ITEM_BACKGROUND_COLOR
    }

    myPanel.background = bg
    myPanel.components.forEach {
      it.foreground = fg
      it.background = bg
    }
    
    with(value as LITableItem) {
      val text = this.value.orEmpty()
      myTextField.foreground = if (this.isDefaultValue(text)) fg else JBColor.BLUE
      myTextField.text = text
    }

    return myPanel
  }
}
