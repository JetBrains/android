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
 * Select previous column in [scrollableTable] and jump to [frozenTable] if needed.
 */
class SelectPreviousColumnAction(private val frozenTable: JTable, private val scrollableTable: JTable) : AbstractAction() {
  override fun actionPerformed(event: ActionEvent) {
    val rowIndex = scrollableTable.selectionModel.leadSelectionIndex
    if (scrollableTable.selectedColumn > 0) {
      val columnIndex = scrollableTable.selectedColumn - 1
      scrollableTable.changeSelection(rowIndex, columnIndex, false, false)
    }
    else {
      frozenTable.changeSelection(rowIndex, frozenTable.columnCount - 1, false, false)
      frozenTable.requestFocus()
    }
  }
}
