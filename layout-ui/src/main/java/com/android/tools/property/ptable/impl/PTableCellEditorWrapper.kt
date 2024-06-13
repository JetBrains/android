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
package com.android.tools.property.ptable.impl

import com.android.tools.property.ptable.DefaultPTableCellEditor
import com.android.tools.property.ptable.PTableCellEditor
import com.intellij.util.ui.AbstractTableCellEditor
import java.awt.Component
import javax.swing.JTable

/**
 * A TableCellEditor that delegates to a [PTableCellEditor].
 *
 * A thin wrapper around a [PTableCellEditor] that can be used in a [JTable]. By default a
 * [DefaultPTableCellEditor] is used, but it can be overridden with a different implementation by
 * setting the [editor] property.
 */
class PTableCellEditorWrapper : AbstractTableCellEditor() {
  var editor: PTableCellEditor = DefaultPTableCellEditor()

  override fun getTableCellEditorComponent(
    table: JTable,
    value: Any?,
    isSelected: Boolean,
    row: Int,
    column: Int,
  ): Component? {
    return editor.editorComponent
  }

  override fun getCellEditorValue(): Any? {
    return editor.value
  }

  val isBooleanEditor: Boolean
    get() = editor.isBooleanEditor

  fun requestFocus() {
    editor.requestFocus()
  }

  fun toggleValue() {
    editor.toggleValue()
  }
}
