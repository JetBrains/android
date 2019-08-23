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
package com.android.tools.idea.naveditor.property2.ui

import com.android.tools.adtui.common.AdtSecondaryPanel
import com.android.tools.idea.common.property.NlProperty
import com.android.tools.idea.naveditor.property.inspector.NAV_ACTION_ARGUMENTS_COMPONENT_NAME
import com.android.tools.idea.naveditor.property.inspector.NAV_ARGUMENTS_ROW_HEIGHT
import com.intellij.ui.components.JBTextField
import com.intellij.ui.table.JBTable
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.BorderFactory
import javax.swing.table.TableCellRenderer

class DefaultValuePanel(model: DefaultValueTableModel) : AdtSecondaryPanel(BorderLayout()) {
  val table = JBTable(model)

  init {
    table.name = NAV_ACTION_ARGUMENTS_COMPONENT_NAME
    table.rowHeight = NAV_ARGUMENTS_ROW_HEIGHT
    table.minimumSize = Dimension(0, 36)
    table.isOpaque = false
    table.emptyText.text = "No Arguments"

    addCellRenderer(false, "name", 0)
    addCellRenderer(false, "type", 1)
    addCellRenderer(true, "default value", 2)

    add(table)
  }

  private fun addCellRenderer(enabled: Boolean, emptyText: String, column: Int) {
    val cellRenderer = JBTextField()
    cellRenderer.isEnabled = enabled
    cellRenderer.emptyText.text = emptyText
    cellRenderer.border = BorderFactory.createEmptyBorder()
    table.columnModel.getColumn(column).cellRenderer = TableCellRenderer { _, value, _, _, _, _ ->
      cellRenderer.apply { text = value as? String }
    }
  }
}