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
package com.android.tools.idea.common.property2.impl.table

import com.android.tools.adtui.common.AdtSecondaryPanel
import com.android.tools.adtui.ptable2.*
import com.android.tools.idea.common.property2.api.*
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.border.Border

/**
 * A simple text cell renderer for displaying the value of a [PTableItem].
 *
 * The properties values in a table are rendered using the control actually
 * used to edit the value. Cache these controls and their editor model for
 * each [ControlType].
 */
class EditorBasedTableCellRenderer<in P : PropertyItem>(private val itemClass: Class<P>,
                                                        private val controlTypeProvider: ControlTypeProvider<P>,
                                                        private val editorProvider: EditorProvider<P>,
                                                        private val defaultRenderer: PTableCellRenderer) : PTableCellRenderer {
  private val componentCache = mutableMapOf<ControlKey, Pair<PropertyEditorModel, JComponent>>()

  override fun getEditorComponent(table: PTable, item: PTableItem, column: PTableColumn,
                                  isSelected: Boolean, hasFocus: Boolean): JComponent? {
    if (!itemClass.isInstance(item) || !table.tableModel.isCellEditable(item, column)) {
      return defaultRenderer.getEditorComponent(table, item, column, isSelected, hasFocus)
    }
    val property = itemClass.cast(item)
    val controlType = controlTypeProvider(property)
    val icon = findActionIcon(property)
    val key = ControlKey(controlType, icon)
    val (model, editor) = componentCache[key] ?: createEditor(key, property, column, table.gridLineColor)
    model.property = property
    return editor
  }

  private fun createEditor(key: ControlKey, property: P, column: PTableColumn, gridLineColor: Color): Pair<PropertyEditorModel, JComponent> {
    val (model, editor) = editorProvider.createEditor(property, asTableCellEditor = true)
    val panel = AdtSecondaryPanel(BorderLayout())
    panel.add(editor, BorderLayout.CENTER)
    panel.border = createBorder(column, editor, gridLineColor)
    val result = Pair(model, panel)
    componentCache[key] = result
    return result
  }

  private fun createBorder(column: PTableColumn, editor: JComponent, gridLineColor: Color): Border =
    when (column) {
      PTableColumn.NAME -> JBUI.Borders.empty(0, LEFT_STANDARD_INDENT - editor.insets.left, 0, 0)
      PTableColumn.VALUE -> JBUI.Borders.customLine(gridLineColor, 0, 1, 0, 0)
    }

  private fun findActionIcon(property: P): Icon? {
    val actionSupport = property as? ActionButtonSupport ?: return null
    return if (actionSupport.showActionButton) actionSupport.getActionIcon(false) else null
  }

  private data class ControlKey(val type: ControlType, val actionIcon: Icon?)
}
