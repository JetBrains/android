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

import com.android.tools.adtui.ptable.PTableCellEditor
import com.android.tools.adtui.ptable.PTableCellEditorProvider
import com.android.tools.adtui.ptable.PTableItem
import java.awt.Component
import javax.swing.JTable

/**
 * Singleton passed to PTable to return [LITableCellEditor]
 */
object LITTableCellEditorProvider : PTableCellEditorProvider {
  private var myDefaultEditor: LITableCellEditor = LITableCellEditor()

  override fun getCellEditor(item: PTableItem, column: Int): PTableCellEditor = myDefaultEditor
}

/**
 * Handles logic for editing a cell
 */
class LITableCellEditor : PTableCellEditor() {
  private var myEditor: LIComponentEditor = LIComponentEditor()
  private var myTable: JTable? = null

  override fun getTableCellEditorComponent(table: JTable, value: Any, isSelected: Boolean, row: Int, column: Int): Component {
    myEditor.property = (value as PTableItem)
    startCellEditing(table, row)
    return myEditor.component
  }

  private fun startCellEditing(table: JTable, row: Int) {
    myTable = table
  }

  override fun getCellEditorValue(): Any? {
    return myEditor.value
  }

  override fun stopCellEditing(): Boolean {
    if (myTable != null) {
      myTable?.requestFocus()
      if (!super.stopCellEditing()) {
        return false
      }
      myTable = null
      myEditor.property = LITableItem.EMPTY
    }
    return true
  }
}
