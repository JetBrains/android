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
package com.android.tools.property.ptable.impl

import com.intellij.ui.table.JBTable
import com.intellij.util.IJSwingUtilities
import java.awt.KeyboardFocusManager
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import javax.swing.table.TableModel

open class PFormTableImpl(model: TableModel) : JBTable(model) {

  init {
    isFocusTraversalPolicyProvider = true
    focusTraversalPolicy = PTableFocusTraversalPolicy(this)
    super.resetDefaultFocusTraversalKeys()

    super.addFocusListener(
      object : FocusAdapter() {
        override fun focusGained(event: FocusEvent) {
          when (event.cause) {
            FocusEvent.Cause.TRAVERSAL_FORWARD -> transferFocusToFirstEditor()
            FocusEvent.Cause.TRAVERSAL_BACKWARD -> transferFocusToLastEditor()
            else -> return // keep focus on the table
          }
        }
      }
    )
  }

  private fun transferFocusToFirstEditor() {
    if (isEmpty || hasAnyEditableCells()) {
      // If this table is empty just move the focus to the next component after the table.
      // If there is an editable cell, use the PTableFocusTraversalPolicy to start editing the first
      // editable cell.
      // Clear the selection such since getFirstComponent will start at the currently selected row
      // if there is one.
      clearSelection()
      val editor = focusTraversalPolicy.getFirstComponent(this)
      editor?.requestFocusInWindow() ?: transferFocus()
    } else {
      // If no cells are editable, accept focus in the table and select the first row.
      setRowSelectionInterval(0, 0)
      scrollCellIntoView(0, 0)
    }
  }

  private fun transferFocusToLastEditor() {
    if (isEmpty || hasAnyEditableCells()) {
      // If this table is empty just move the focus to the next component before the table.
      // If there is an editable cell, use the PTableFocusTraversalPolicy to start editing the last
      // editable cell.
      // Clear the selection such since getLastComponent could start at the currently selected row
      // if there is one.
      clearSelection()
      val editor = focusTraversalPolicy.getLastComponent(this)
      editor?.requestFocusInWindow() ?: transferFocusBackward()
    } else {
      // If no cells are editable, accept focus in the table and select the last row.
      setRowSelectionInterval(rowCount - 1, rowCount - 1)
      scrollCellIntoView(rowCount - 1, rowCount - 1)
    }
  }

  private fun hasAnyEditableCells(): Boolean {
    for (row in 0 until rowCount) {
      for (column in 0..1) {
        if (isCellEditable(row, column)) {
          return true
        }
      }
    }
    return false
  }

  // When an editor is present, do not accept focus on the table itself.
  // This fixes a problem when navigating backwards.
  // The LayoutFocusTraversalPolicy for the container of the table would include
  // the table as the last possible focus component when navigating backwards.
  override fun isFocusable(): Boolean {
    return super.isFocusable() && !isEditing && rowCount > 0
  }

  override fun removeEditor() {
    // b/37132037 Remove focus from the editor before hiding the editor.
    // When we are transferring focus to another cell we will have to remove the current
    // editor. The auto focus transfer in Container.removeNotify will cause another undesired
    // focus event. This is an attempt to avoid that.
    // The auto focus transfer is a common problem for applications see this open bug: JDK-6210779.
    val editor = editorComponent
    if (editor != null && IJSwingUtilities.hasFocus(editor)) {
      KeyboardFocusManager.getCurrentKeyboardFocusManager().clearFocusOwner()
    }
    super.removeEditor()
  }

  fun scrollCellIntoView(row: Int, column: Int) {
    scrollRectToVisible(getCellRect(row, column, true))
  }
}
