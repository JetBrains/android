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
package com.android.tools.idea.gradle.structure.configurables.ui.properties

import com.intellij.ui.TableUtil
import com.intellij.ui.treeStructure.treetable.TreeTable
import java.awt.event.ActionEvent
import java.util.EventObject
import javax.swing.JComponent
import javax.swing.JTable
import javax.swing.KeyStroke
import javax.swing.SwingUtilities


fun JTable.addTabKeySupportTo(editor: JComponent) {
  editor.registerKeyboardAction(::nextCell, KeyStroke.getKeyStroke("TAB"), TreeTable.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
  editor.registerKeyboardAction(::prevCell, KeyStroke.getKeyStroke("shift pressed TAB"), TreeTable.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
  editor.registerKeyboardAction(::nextCell, KeyStroke.getKeyStroke("ENTER"), TreeTable.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
}

private fun JTable.selectCell(row: Int, column: Int) {
  selectionModel.setSelectionInterval(row, row)
  columnModel.selectionModel.setSelectionInterval(column, column)
}

private fun JTable.nextCell(e: ActionEvent) {
  nextCell(e, editingRow, editingColumn)
}

private fun JTable.nextCell(e: EventObject, row: Int, col: Int) {
  val editPosition = row to col
  TableUtil.stopEditing(this)
  generateSequence(editPosition) {
    (if (it.second >= columnCount - 1) it.first + 1 to 0 else it.first to it.second + 1)
  }
      .drop(1)
      .takeWhile { it.first < rowCount }
      .firstOrNull { model.isCellEditable(it.first, it.second) == true }
      ?.let { (row, column) ->
        selectionModel.setSelectionInterval(row, row)
        scrollRectToVisible(getCellRect(row, column, true))
        selectCell(row, column)
        @Suppress("WrongInvokeLater")
        SwingUtilities.invokeLater { editCellAt(row, column, null) }
      }
}

private fun JTable.prevCell(e: ActionEvent) {
  val editPosition = editingRow to editingColumn
  TableUtil.stopEditing(this)
  generateSequence(editPosition) {
    val (nextRow, nextColumn) = if (it.second <= 0) it.first - 1 to columnCount - 1 else it.first to it.second - 1
    nextRow to nextColumn
  }
      .drop(1)
      .takeWhile { it.first >= 0 }
      .firstOrNull { model.isCellEditable(it.first, it.second) == true }
      ?.let { (row, column) ->
        selectionModel.setSelectionInterval(row, row)
        scrollRectToVisible(getCellRect(row, column, true))
        selectCell(row, column)
        @Suppress("WrongInvokeLater")
        SwingUtilities.invokeLater { editCellAt(row, column, e) }
      }
}

