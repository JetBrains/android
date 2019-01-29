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
package com.android.tools.idea.gradle.structure.configurables.ui.properties

import com.android.tools.idea.gradle.structure.model.meta.Annotated
import com.android.tools.idea.gradle.structure.model.meta.ModelPropertyCore
import com.android.tools.idea.gradle.structure.model.meta.ParsedValue
import com.intellij.util.ui.AbstractTableCellEditor
import java.awt.Component
import javax.swing.JTable

abstract class PropertyCellEditor<ValueT : Any> : AbstractTableCellEditor() {
  private var currentRow: Int = -1
  private var currentRowProperty: ModelPropertyCore<out ValueT>? = null
  private var lastEditor: ModelPropertyEditor<ValueT>? = null
  private var lastValue: Annotated<ParsedValue<ValueT>>? = null

  abstract fun initEditorFor(row: Int): ModelPropertyEditor<ValueT>
  abstract fun Annotated<ParsedValue<ValueT>>.toModelValue(): Any

  override fun getTableCellEditorComponent(table: JTable?, value: Any?, isSelected: Boolean, row: Int, column: Int): Component? {
    currentRow = row
    val editor = initEditorFor(row)
    currentRowProperty = editor.property
    lastEditor = editor
    lastValue = null
    return editor.component
  }

  override fun stopCellEditing(): Boolean =
    when (lastEditor?.updateProperty()) {
      null,
      UpdatePropertyOutcome.UPDATED,
      UpdatePropertyOutcome.NOT_CHANGED -> {
        lastValue = currentRowProperty?.getParsedValue()
        currentRow = -1
        currentRowProperty = null
        lastEditor?.dispose()
        lastEditor = null
        fireEditingStopped()
        true
      }
      UpdatePropertyOutcome.INVALID -> false
    }

  override fun cancelCellEditing() {
    lastValue = null
    currentRow = -1
    currentRowProperty = null
    lastEditor?.dispose()
    lastEditor = null
    super.cancelCellEditing()
  }

  override fun getCellEditorValue(): Any? = (lastValue ?: lastEditor?.getValue())?.toModelValue()
}