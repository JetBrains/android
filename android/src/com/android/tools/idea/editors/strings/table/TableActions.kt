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
 * Holds instances for the frozen and scrollable table for the implementations of actions.
 */
class ActionTable(
  /** The frozen table to the left */
  val leftTable: JTable,
  /** The current table: can be either the frozen table or the scrollable table */
  val currentTable: JTable,
  /** The scrollable table to the right */
  val rightTable: JTable
)

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
  PREVIOUS_COLUMN_CELL_ACTION("selectPreviousColumnCell")
}

/**
 * Implementation the actions for each action type.
 */
class TableAction(private val type: ActionType, private val table: ActionTable): AbstractAction(type.actionName) {
  override fun actionPerformed(event: ActionEvent) {
    val column = table.currentTable.selectedColumn
    when (type) {
      ActionType.FIRST_COLUMN_ACTION -> goto(column = 0, target = table.leftTable, extend = false)
      ActionType.FIRST_COLUMN_EXTEND_SELECTION_ACTION -> goto(column = 0, target = table.leftTable, extend = true)
      ActionType.LAST_COLUMN_ACTION -> goto(table.rightTable.columnCount - 1, table.rightTable, extend = false)
      ActionType.LAST_COLUMN_EXTEND_SELECTION_ACTION -> goto(table.rightTable.columnCount - 1, table.rightTable, extend = true)
      ActionType.NEXT_COLUMN_ACTION -> goto(column + 1, table.currentTable, extend = false)
      ActionType.PREVIOUS_COLUMN_ACTION -> goto(column - 1, table.currentTable, extend = false)
      ActionType.NEXT_COLUMN_EXTEND_SELECTION_ACTION -> goto(column + 1, table.currentTable, extend = true)
      ActionType.PREVIOUS_COLUMN_EXTEND_SELECTION_ACTION -> goto(column - 1, table.currentTable, extend = true)
      ActionType.NEXT_COLUMN_CELL_ACTION -> table.rightTable.transferFocus()
      ActionType.PREVIOUS_COLUMN_CELL_ACTION -> table.leftTable.transferFocusBackward()
    }
  }

  private fun goto(column: Int, target: JTable, extend: Boolean) {
    when {
      (column >= target.columnCount && target !== table.rightTable) -> goto(0, table.rightTable, extend)
      (column < 0 && target !== table.leftTable) -> goto(table.leftTable.columnCount - 1, table.leftTable, extend)
      target === table.currentTable -> target.changeSelection(target.selectionModel.leadSelectionIndex, column, false, extend)
      else -> {
        target.changeSelection(table.currentTable.selectionModel.leadSelectionIndex, column, false, extend)
        target.requestFocus()
      }
    }
  }
}
