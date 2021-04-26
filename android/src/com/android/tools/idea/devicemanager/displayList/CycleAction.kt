/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.devicemanager.displayList

import com.android.sdklib.internal.avd.AvdInfo
import com.android.tools.idea.devicemanager.displayList.columns.AvdActionsColumnInfo
import com.intellij.ui.table.TableView
import com.intellij.util.ui.ListTableModel
import java.awt.KeyboardFocusManager
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.JComponent

// TODO(qumeric): ideally we should be able to reach preconfigured devices as well
class CycleAction(
  private val table: TableView<AvdInfo>,
  private val model: ListTableModel<AvdInfo>,
  private val actionsColumnRenderer: AvdActionsColumnInfo,
  private val parent: JComponent,
  private val backward: Boolean
) : AbstractAction() {
  override fun actionPerformed(evt: ActionEvent) {
    val cycleFunction = if (backward) ::cycleBackward else ::cycleForward
    cycleFunction(table.selectedRow, table.selectedColumn, model.findColumn(actionsColumnRenderer.name))

    val selectedRow = table.selectedRow
    if (selectedRow != -1) {
      table.editCellAt(selectedRow, table.selectedColumn)
    }
    parent.repaint()
  }

  private fun cycleForward(selectedRow: Int, selectedColumn: Int, actionsColumn: Int) {
    if (selectedColumn == actionsColumn && selectedRow == table.rowCount - 1) {
      // We're in the last cell of the table. Check whether we can cycle action buttons
      if (!actionsColumnRenderer.cycleFocus(table.selectedObject, false)) {
        // At the end of action buttons. Remove selection and leave table.
        val avdInfo = table.selectedObject
        val cellEditor = actionsColumnRenderer.getEditor(avdInfo)
        cellEditor.stopCellEditing()
        table.removeRowSelectionInterval(selectedRow, selectedRow)
        val manager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
        manager.focusNextComponent(table)
      }
    }
    else if (selectedColumn != actionsColumn && selectedRow != -1) {
      // We're in the table, but not on the action column. Select the action column.
      table.setColumnSelectionInterval(actionsColumn, actionsColumn)
      actionsColumnRenderer.cycleFocus(table.selectedObject, false)
    }
    else if (selectedRow == -1 || !actionsColumnRenderer.cycleFocus(table.selectedObject, false)) {
      // We aren't in the table yet, or we are in the actions column and at the end of the focusable actions. Move to the next row
      // and select the first column
      table.setColumnSelectionInterval(0, 0)
      table.setRowSelectionInterval(selectedRow + 1, selectedRow + 1)
    }
  }

  private fun cycleBackward(selectedRow: Int, selectedColumn: Int, actionsColumn: Int) {
    if (selectedColumn == 0 && selectedRow == 0) {
      // We're in the first cell of the table. Remove selection and leave table.
      table.removeRowSelectionInterval(selectedRow, selectedRow)
      KeyboardFocusManager.getCurrentKeyboardFocusManager().focusPreviousComponent()
    }
    else if ((selectedColumn == actionsColumn) && (selectedRow != -1) &&
             !actionsColumnRenderer.cycleFocus(table.selectedObject, true)) {
      // We're on an actions column. If we fail to cycle actions, select the first cell in the row.
      table.setColumnSelectionInterval(0, 0)
    }
    else if (selectedRow == -1 || selectedColumn != actionsColumn) {
      // We aren't in the table yet, or we're not in the actions column. Move to the previous (or last) row.
      // and select the actions column
      val newSelectedRow = if (selectedRow == -1) table.rowCount - 1 else selectedRow - 1
      table.setRowSelectionInterval(newSelectedRow, newSelectedRow)
      table.setColumnSelectionInterval(actionsColumn, actionsColumn)
      actionsColumnRenderer.cycleFocus(table.selectedObject, true)
    }
  }
}
