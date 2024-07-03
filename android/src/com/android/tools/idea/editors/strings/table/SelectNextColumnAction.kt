/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.editors.strings.table

import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.JTable

/**
 * Select next column in [frozenTable] and jump to [scrollableTable] if needed.
 */
class SelectNextColumnAction(private val frozenTable: JTable, private val scrollableTable: JTable) : AbstractAction() {
  override fun actionPerformed(event: ActionEvent) {
    val rowIndex = frozenTable.selectionModel.leadSelectionIndex
    if (frozenTable.selectedColumn < frozenTable.columnCount - 1) {
      val columnIndex = frozenTable.selectedColumn + 1
      frozenTable.changeSelection(rowIndex, columnIndex, false, false)
    }
    else {
      scrollableTable.changeSelection(rowIndex, 0, false, false)
      scrollableTable.requestFocus()
    }
  }
}
