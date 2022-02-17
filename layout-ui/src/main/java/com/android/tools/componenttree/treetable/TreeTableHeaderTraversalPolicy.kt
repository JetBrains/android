/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.componenttree.treetable

import java.awt.Component
import java.awt.Container
import java.awt.FocusTraversalPolicy
import javax.swing.LayoutFocusTraversalPolicy

/**
 * A [FocusTraversalPolicy] that supports editable columns in a [TreeTableHeader].
 *
 * When navigating through the header, an attempt is made to navigate to a header column.
 * If a focus candidate can be found from a column editor the header is left in an editing state
 * and the focus will be transferred to the first (or last) component in the column.
 * This implementation allows multiple focusable component in a single column.
 */
class TreeTableHeaderTraversalPolicy(private val header: TreeTableHeader) : LayoutFocusTraversalPolicy() {

  override fun getComponentAfter(aContainer: Container, aComponent: Component): Component? {
    if (header.isEditing) {
      super.getComponentAfter(aContainer, aComponent)?.let { return it }
    }
    return editNextEditableCell(forwards = true, alreadyInHeader = true)
  }

  override fun getComponentBefore(aContainer: Container, aComponent: Component): Component? {
    if (header.isEditing) {
      super.getComponentBefore(aContainer, aComponent)?.let { return it }
    }
    return editNextEditableCell(forwards = false, alreadyInHeader = true)
  }

  private fun editNextEditableCell(forwards: Boolean, alreadyInHeader: Boolean): Component? {
    if (!header.isVisible) {
      // If the table isn't visible: do not try to start cell editing
      return null
    }
    val columns = header.columnCount
    var column = when {
      alreadyInHeader && header.isEditing -> header.editingColumn.next(forwards)
      forwards -> 0
      else -> columns - 1
    }

    while (column in 0 until columns) {
      if (header.editCellAt(column)) {
        val component = getFocusCandidateFromNewlyCreatedEditor(forwards)
        if (component != null) {
          return component
        }
        header.removeEditor()
      }

      column = column.next(forwards)
    }

    // We can't find an editable cell.
    // Delegate focus out of the table header.
    return null
  }

  private fun Int.next(forwards: Boolean) =
    this + if (forwards) 1 else -1

  // Note: When an editor was just created, the label and the new editor are the only children of the table.
  // The editor created may be composed of multiple focusable components.
  // Use LayoutFocusTraversalPolicy to identify the next focus candidate.
  private fun getFocusCandidateFromNewlyCreatedEditor(forwards: Boolean): Component? =
    if (forwards) {
      super.getFirstComponent(header)
    }
    else {
      super.getLastComponent(header)
    }

  override fun getFirstComponent(aContainer: Container): Component? {
    return editNextEditableCell(forwards = true, alreadyInHeader = false)
  }

  override fun getLastComponent(aContainer: Container): Component? {
    return editNextEditableCell(forwards = false, alreadyInHeader = false)
  }

  override fun getDefaultComponent(aContainer: Container): Component? {
    return getFirstComponent(aContainer)
  }

  override fun accept(aComponent: Component?): Boolean {
    if (aComponent == header) {
      return false
    }
    return super.accept(aComponent)
  }
}
