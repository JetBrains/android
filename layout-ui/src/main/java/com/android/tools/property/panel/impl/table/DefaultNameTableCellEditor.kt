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
package com.android.tools.property.panel.impl.table

import com.android.tools.property.ptable2.DefaultPTableCellEditor
import com.android.tools.property.ptable2.PTable
import com.android.tools.property.ptable2.PTableCellEditor
import com.android.tools.property.ptable2.PTableGroupItem

/**
 * Cell editor for a table group item.
 */
class DefaultNameTableCellEditor : DefaultPTableCellEditor() {
  private var lastTable: PTable? = null
  private var lastItem: PTableGroupItem? = null
  private var component2 = DefaultNameComponent(::toggle)

  override var editorComponent = DefaultNameComponent(::toggle)
    private set

  fun nowEditing(table: PTable, item: PTableGroupItem): PTableCellEditor {
    swap()
    editorComponent.setUpItem(table, item, table.depth(item), true, true, table.isExpanded(item))
    lastTable = table
    lastItem = item
    return this
  }

  override fun close(oldTable: PTable) {
    lastTable = null
    lastItem = null
  }

  override fun requestFocus() {
    editorComponent.requestFocusInWindow()
  }

  private fun toggle() {
    lastItem?.let { lastTable?.toggle(it) }
  }

  /**
   * Toggle between 2 editor components.
   *
   * This is done to allow a focus transfer from 1 PTableGroupItem to another.
   * If the same component is used this would fail since Component.transferFocus()
   * will be a noop if the new component already has focus or if the new component
   * is the same instance we are trying to transfer from. The relevant lines in
   * Component.transferFocus are:
   *
   * <code>
   *    if (toFocus != null && !toFocus.isFocusOwner() && toFocus != this) {
   *      res = toFocus.requestFocusInWindow(CausedFocusEvent.Cause.TRAVERSAL_FORWARD);
   *    }
   * </code>
   */
  private fun swap() {
    val old = editorComponent
    editorComponent = component2
    component2 = old
  }
}
