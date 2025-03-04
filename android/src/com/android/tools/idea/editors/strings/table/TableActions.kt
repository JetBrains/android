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

/**
 * The actions overridden in the 2 tables.
 */
enum class ActionType(val actionName: String) {
  FIRST_COLUMN_ACTION("selectFirstColumn"),
  FIRST_COLUMN_EXTEND_SELECTION_ACTION("selectFirstColumnExtendSelection"),
  LAST_COLUMN_ACTION("selectLastColumn"),
  LAST_COLUMN_EXTEND_SELECTION_ACTION("selectLastColumnExtendSelection"),
  NEXT_COLUMN_ACTION("selectNextColumn"),
  PREVIOUS_COLUMN_ACTION("selectPreviousColumn"),
  NEXT_COLUMN_EXTEND_SELECTION_ACTION("selectNextColumnExtendSelection"),
  PREVIOUS_COLUMN_EXTEND_SELECTION_ACTION("selectPreviousColumnExtendSelection"),
  NEXT_COLUMN_CELL_ACTION("selectNextColumnCell"),
  PREVIOUS_COLUMN_CELL_ACTION("selectPreviousColumnCell"),

  NEXT_ROW("selectNextRow"),
  NEXT_ROW_CELL("selectNextRowCell"),
  NEXT_ROW_CHANGE_LEAD("selectNextRowChangeLead"),
  PREVIOUS_ROW("selectPreviousRow"),
  PREVIOUS_ROW_CELL("selectPreviousRowCell"),
  PREVIOUS_ROW_CHANGE_LEAD("selectPreviousRowChangeLead"),
  FIRST_ROW("selectFirstRow"),
  LAST_ROW("selectLastRow"),
  SCROLL_UP_CHANGE_SELECTION("scrollUpChangeSelection"),
  SCROLL_DOWN_CHANGE_SELECTION("scrollDownChangeSelection"),
  SELECT_ALL("selectAll"),
  CLEAR_SELECTION("clearSelection"),
}

/**
 * Implementation the actions for each action type.
 */
class TableAction(private val type: ActionType, private val table: FrozenColumnTable<*>): AbstractAction(type.actionName) {
  override fun actionPerformed(event: ActionEvent) {
    val row = table.selectedRow
    val column = table.selectedColumn
    when (type) {
      ActionType.FIRST_COLUMN_ACTION -> table.gotoColumn(0, false)
      ActionType.FIRST_COLUMN_EXTEND_SELECTION_ACTION -> table.gotoColumn(0, true)
      ActionType.LAST_COLUMN_ACTION -> table.gotoColumn(table.columnCount - 1, false)
      ActionType.LAST_COLUMN_EXTEND_SELECTION_ACTION -> table.gotoColumn(table.columnCount - 1, true)
      ActionType.NEXT_COLUMN_ACTION -> table.gotoColumn(column + 1, false)
      ActionType.PREVIOUS_COLUMN_ACTION -> table.gotoColumn(column - 1, false)
      ActionType.NEXT_COLUMN_EXTEND_SELECTION_ACTION -> table.gotoColumn(column + 1, true)
      ActionType.PREVIOUS_COLUMN_EXTEND_SELECTION_ACTION -> table.gotoColumn(column - 1, true)
      ActionType.NEXT_COLUMN_CELL_ACTION -> table.scrollableTable.transferFocus()
      ActionType.PREVIOUS_COLUMN_CELL_ACTION -> table.frozenTable.transferFocusBackward()
      ActionType.NEXT_ROW,
      ActionType.NEXT_ROW_CELL,
      ActionType.NEXT_ROW_CHANGE_LEAD -> table.gotoRow(row + 1)
      ActionType.PREVIOUS_ROW,
      ActionType.PREVIOUS_ROW_CELL,
      ActionType.PREVIOUS_ROW_CHANGE_LEAD -> table.gotoRow(row - 1)
      ActionType.SCROLL_UP_CHANGE_SELECTION -> table.scrollRow(false)
      ActionType.SCROLL_DOWN_CHANGE_SELECTION -> table.scrollRow(true)
      ActionType.FIRST_ROW -> table.gotoRow(0)
      ActionType.LAST_ROW -> table.gotoRow(table.rowCount)
      ActionType.SELECT_ALL -> table.selectAll()
      ActionType.CLEAR_SELECTION -> table.clearSelection()
    }
  }
}
