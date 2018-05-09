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

import com.android.tools.idea.gradle.structure.model.VariablesProvider
import com.android.tools.idea.gradle.structure.model.meta.*
import com.intellij.util.ui.AbstractTableCellEditor
import java.awt.Component
import java.awt.TextField
import javax.swing.JTable
import javax.swing.table.DefaultTableColumnModel
import javax.swing.table.DefaultTableModel
import javax.swing.table.TableColumn
import javax.swing.table.TableColumnModel

/**
 * A property editor [ModelPropertyEditor] for properties of simple map types.
 */
class MapPropertyEditor<ValueT : Any, out ModelPropertyT : ModelMapPropertyCore<ValueT>>(
  property: ModelPropertyT,
  propertyContext: ModelPropertyContext<ValueT>,
  editor: PropertyEditorFactory<ModelPropertyCore<ValueT>, ModelPropertyContext<ValueT>, ValueT>,
  variablesProvider: VariablesProvider?,
  extensions: List<EditorExtensionAction>
) : CollectionPropertyEditor<ModelPropertyT, ValueT>(property, propertyContext, editor, variablesProvider, extensions),
    ModelPropertyEditor<Map<String, ValueT>> {

  init {
    loadValue()
  }

  override fun updateProperty() = throw UnsupportedOperationException()

  override fun dispose() = Unit

  override fun getValueAt(row: Int): ParsedValue<ValueT> {
    val entryKey = keyAt(row)
    val entryValue = if (entryKey == "") modelValueAt(row) else property.getEditableValues()[entryKey]?.getParsedValue()
    return entryValue ?: ParsedValue.NotSet
  }

  override fun setValueAt(row: Int, value: ParsedValue<ValueT>) {
    val entryKey = keyAt(row)
    // If entryKey == "", we don't need to store the value in the property. It is, however, automatically stored in the table model and
    // it will be transferred to the property when the key value is set.
    if (entryKey != "") {
      (property.getEditableValues()[entryKey] ?: property.addEntry(entryKey)).setParsedValue(value)
    }
  }

  override fun addItem() {
    tableModel?.let { tableModel ->
      val index = tableModel.rowCount
      tableModel.addRow(arrayOf("", ParsedValue.NotSet.toTableModelValue()))
      table.selectionModel.setSelectionInterval(index, index)
      table.editCellAt(index, 0)
    }
  }

  override fun removeItem() {
    tableModel?.let { tableModel ->
      table.removeEditor()
      val selection = table.selectionModel
      for (index in selection.maxSelectionIndex downTo selection.minSelectionIndex) {
        if (table.selectionModel.isSelectedIndex(index)) {
          val key = (tableModel.getValueAt(index, 0) as String?).orEmpty()
          if (key != "") {
            property.deleteEntry(key)
            tableModel.removeRow(index)
          }
        }
      }
    }
  }

  override fun createTableModel(): DefaultTableModel {
    val tableModel = DefaultTableModel()
    tableModel.addColumn("key")
    tableModel.addColumn("value")
    val value = property.getEditableValues()
    for ((k, v) in value.entries) {
      tableModel.addRow(arrayOf(k, v.getParsedValue().toTableModelValue()))
    }
    return tableModel
  }

  override fun createColumnModel(): TableColumnModel {
    return DefaultTableColumnModel().apply {
      addColumn(TableColumn(0, 50).apply {
        headerValue = "K"
        cellEditor = MyKeyCellEditor()
      })
      addColumn(TableColumn(1).apply {
        headerValue = "V"
        cellEditor = MyCellEditor()
        cellRenderer = MyCellRenderer()
      })
    }
  }

  override fun getValue(): ParsedValue<Map<String, ValueT>> = throw UnsupportedOperationException()

  private fun keyAt(row: Int) = (table.model.getValueAt(row, 0) as? String).orEmpty()

  private fun modelValueAt(row: Int) =
    @Suppress("UNCHECKED_CAST")  // If it is of type Value, then generic type arguments are correct.
    (table.model.getValueAt(row, 1) as? CollectionPropertyEditor<ModelPropertyT, ValueT>.Value)?.value

  inner class MyKeyCellEditor : AbstractTableCellEditor() {
    private var currentRow: Int = -1
    private var currentKey: String? = null
    private var lastEditor: TextField? = null

    override fun getTableCellEditorComponent(table: JTable?, value: Any?, isSelected: Boolean, row: Int, column: Int): Component? {
      currentRow = row
      currentKey = keyAt(row)
      lastEditor = TextField().apply {
        text = currentKey
      }
      return lastEditor
    }

    override fun stopCellEditing(): Boolean {
      return super.stopCellEditing().also {
        if (it) {
          val oldKey = currentKey!!
          val newKey = lastEditor!!.text!!
          when {
            oldKey == "" -> {
              val addedEntry = property.addEntry(newKey)
              @Suppress("UNCHECKED_CAST")
              val modelValue: Value? =
                table.model.getValueAt(currentRow, 1) as? CollectionPropertyEditor<ModelPropertyT, ValueT>.Value
              if (modelValue != null) {
                addedEntry.setParsedValue(modelValue.value)
              }
            }
            newKey == "" -> property.deleteEntry(oldKey)
            else -> property.changeEntryKey(oldKey, newKey)
          }
          currentRow = -1
          currentKey = null
        }
      }
    }

    override fun cancelCellEditing() {
      currentRow = -1
      currentKey = null
      super.cancelCellEditing()
    }

    override fun getCellEditorValue(): Any = lastEditor!!.text
  }
}

fun <ValueT : Any, ModelPropertyT : ModelMapPropertyCore<ValueT>> mapPropertyEditor(
  editor: PropertyEditorFactory<ModelPropertyCore<ValueT>, ModelPropertyContext<ValueT>, ValueT>
):
  PropertyEditorFactory<ModelPropertyT, ModelPropertyContext<ValueT>, Map<String, ValueT>> =
  { property, propertyContext, variablesProvider, extensions ->
    MapPropertyEditor(property, propertyContext, editor, variablesProvider, extensions)
  }
