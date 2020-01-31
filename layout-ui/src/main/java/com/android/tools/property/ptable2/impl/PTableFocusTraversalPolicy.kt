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
package com.android.tools.property.ptable2.impl

import com.intellij.util.IJSwingUtilities
import java.awt.Component
import java.awt.Container
import java.awt.KeyboardFocusManager
import javax.swing.JTable
import javax.swing.LayoutFocusTraversalPolicy

/**
 * FocusTraversalPolicy for [PTableImpl].
 *
 * Setup focus traversal keys such that tab takes focus out of the table if the user is not editing.
 * When the user is editing the focus traversal keys will move to the next editable cell.
 */
class PTableFocusTraversalPolicy(val table: JTable) : LayoutFocusTraversalPolicy() {

  override fun getComponentAfter(aContainer: Container, aComponent: Component): Component? {
    val after = super.getComponentAfter(aContainer, aComponent)
    if (after != null && after != table) {
      return after
    }
    return editNextEditableCell(true)
  }

  override fun getComponentBefore(aContainer: Container, aComponent: Component): Component? {
    val before = super.getComponentBefore(aContainer, aComponent)
    if (before != null && before != table) {
      return before
    }
    return editNextEditableCell(false)
  }

  private fun editNextEditableCell(forwards: Boolean): Component? {
    if (!table.isVisible) {
      // If the table isn't visible: do not try to start cell editing
      return null
    }
    val rows = table.rowCount
    val columns = table.columnCount
    val pos = when {
      table.isEditing -> PTablePosition(table.editingRow, table.editingColumn, rows, columns)
      forwards -> PTablePosition(-1, columns - 1, rows, columns)
      else -> PTablePosition(rows, 0, rows, columns)
    }

    while (true) {
      if (!pos.next(forwards)) {
        break
      }
      if (table.isCellEditable(pos.row, pos.column)) {
        table.setRowSelectionInterval(pos.row, pos.row)

        // b/37132037 Remove focus from the editor before hiding the editor.
        // When we are transferring focus to another cell we will have to remove the current
        // editor. The auto focus transfer in Container.removeNotify will cause another undesired
        // focus event. This is an attempt to avoid that.
        // The auto focus transfer is a common problem for applications see this open bug: JDK-6210779.
        //val editor = table.editorComponent
        //if (editor != null && IJSwingUtilities.hasFocus(editor)) {
        //  KeyboardFocusManager.getCurrentKeyboardFocusManager().clearFocusOwner()
        //}

        if (table.editCellAt(pos.row, pos.column)) {
          val component = getFocusCandidateFromNewlyCreatedEditor(forwards)
          if (component != null) {
            return component
          }
        }
      }
    }
    // We can't find an editable cell.
    // Delegate focus out of the table.
    return null
  }

  // Note: When an editor was just created, the label and the new editor are the only children of the table.
  // The editor created may be composed of multiple focusable components.
  // Use LayoutFocusTraversalPolicy to identify the next focus candidate.
  private fun getFocusCandidateFromNewlyCreatedEditor(forwards: Boolean): Component? {
    if (forwards) {
      return super.getFirstComponent(table)
    }
    else {
      return super.getLastComponent(table)
    }
  }

  override fun getFirstComponent(aContainer: Container): Component? {
    return editNextEditableCell(true)
  }

  override fun getLastComponent(aContainer: Container): Component? {
    return editNextEditableCell(false)
  }

  override fun getDefaultComponent(aContainer: Container): Component? {
    return getFirstComponent(aContainer)
  }

  override fun accept(aComponent: Component?): Boolean {
    if (aComponent == table) {
      return false
    }
    return super.accept(aComponent)
  }
}
