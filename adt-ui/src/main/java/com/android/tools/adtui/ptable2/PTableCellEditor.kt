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
package com.android.tools.adtui.ptable2

import javax.swing.JComponent

/**
 * Provider for a cell editor for a [PTable].
 */
interface PTableCellEditor {

  /**
   * Returns an editor component the editor.
   *
   * A return value of null means the cell is not editable.
   */
  val editorComponent: JComponent?

  /**
   * Return the value currently in the editor.
   *
   * This method is called when editing
   */
  val value: String?

  /**
   * Return true to enable the [toggleValue] method below.
   */
  val isBooleanEditor: Boolean

  /**
   * Toggle a state and immediately stop editing.
   *
   * This method is only called if [isBooleanEditor] returns true.
   */
  fun toggleValue()

  /**
   * Called to request focus in the editor.
   */
  fun requestFocus()

  /**
   * Editing was cancelled.
   */
  fun cancelEditing()

  /**
   * Close is called when the editor is no longer used.
   */
  fun close(oldTable: PTable)
}

class DefaultPTableCellEditor : PTableCellEditor {

  override val editorComponent: JComponent? = null

  override val value: String?
    get() = null

  override val isBooleanEditor: Boolean
    get() = false

  override fun toggleValue() {}

  override fun requestFocus() {}

  override fun cancelEditing() {}

  override fun close(oldTable: PTable) {}
}

class DefaultPTableCellEditorProvider : PTableCellEditorProvider {
  val editor = DefaultPTableCellEditor()

  override fun invoke(table: PTable, property: PTableItem, column: PTableColumn): PTableCellEditor {
    return editor
  }
}
