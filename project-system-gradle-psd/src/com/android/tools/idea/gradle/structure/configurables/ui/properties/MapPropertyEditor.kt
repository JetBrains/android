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

import com.android.tools.idea.gradle.structure.configurables.ui.PropertyEditorCoreFactory
import com.android.tools.idea.gradle.structure.configurables.ui.toRenderer
import com.android.tools.idea.gradle.structure.model.PsVariablesScope
import com.android.tools.idea.gradle.structure.model.meta.Annotated
import com.android.tools.idea.gradle.structure.model.meta.ModelMapPropertyCore
import com.android.tools.idea.gradle.structure.model.meta.ModelPropertyContext
import com.android.tools.idea.gradle.structure.model.meta.ModelPropertyCore
import com.android.tools.idea.gradle.structure.model.meta.ParsedValue
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.AbstractTableCellEditor
import com.intellij.util.ui.UIUtil
import java.awt.Component
import javax.swing.JTable
import javax.swing.JTextField
import javax.swing.table.DefaultTableColumnModel
import javax.swing.table.DefaultTableModel
import javax.swing.table.TableCellEditor
import javax.swing.table.TableCellRenderer
import javax.swing.table.TableColumn
import javax.swing.table.TableColumnModel

/**
 * A property editor [ModelPropertyEditor] for properties of simple map types.
 */
class MapPropertyEditor<ValueT : Any, ModelPropertyT : ModelMapPropertyCore<ValueT>>(
  property: ModelPropertyT,
  propertyContext: ModelPropertyContext<ValueT>,
  editor: PropertyEditorCoreFactory<ModelPropertyCore<ValueT>, ModelPropertyContext<ValueT>, ValueT>,
  variablesScope: PsVariablesScope?,
  private val logValueEdited: () -> Unit
) : CollectionPropertyEditor<ModelPropertyT, ValueT>(property, propertyContext, editor, variablesScope, logValueEdited),
    ModelPropertyEditor<Map<String, ValueT>>, ModelPropertyEditorFactory<Map<String, ValueT>, ModelPropertyT> {

  init {
    loadValue()
  }

  override val statusComponent: SimpleColoredComponent = SimpleColoredComponent()

  override fun updateProperty() = throw UnsupportedOperationException()

  override fun reload() = loadValue()

  override fun dispose() = Unit

  override fun getPropertyAt(row: Int) = getPropertyFor(keyAt(row))

  // It is fine to add a new property on get. The editor has already added a row for it if it's being requested.
  // TODO(b/79513471): Make sure no entries with empty keys remain when saving.
  private fun getPropertyFor(entryKey: String) = (property.getEditableValues()[entryKey] ?: property.addEntry(entryKey))

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
          property.deleteEntry(key)
          tableModel.removeRow(index)
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
        headerValue = "Key"
        cellEditor = MyKeyCellEditor()
        cellRenderer = TableCellRenderer { table, value, isSelected, hasFocus, row, column ->
          SimpleColoredComponent()
              .also {
                if (value != null) {
                  it.toRenderer()
                      .toSelectedTextRenderer(isSelected && hasFocus)
                      .append(value.toString(), SimpleTextAttributes.REGULAR_ATTRIBUTES)
                }
                if (isSelected) it.background = UIUtil.getListSelectionBackground(hasFocus)
              }
        }
      })
      addColumn(TableColumn(1).apply {
        headerValue = "Value"
        cellEditor = MyCellEditor()
        cellRenderer = MyCellRenderer()
      })
      columnSelectionAllowed = true
    }
  }

  override fun getValue(): Annotated<ParsedValue<Map<String, ValueT>>> = throw UnsupportedOperationException()

  private fun keyAt(row: Int) = (table.model.getValueAt(row, 0) as? String).orEmpty()

  inner class MyKeyCellEditor : AbstractTableCellEditor() {
    private var currentRow: Int = -1
    private var currentKey: String? = null
    private var lastEditor: JTextField? = null

    override fun getTableCellEditorComponent(table: JTable, value: Any?, isSelected: Boolean, row: Int, column: Int): Component? {
      currentRow = row
      currentKey = keyAt(row)
      lastEditor = JTextField().apply {
        text = currentKey
      }
      lastEditor?.let { table.addTabKeySupportTo(it) }
      return lastEditor
    }

    override fun stopCellEditing(): Boolean {
      return super.stopCellEditing().also {
        if (it && currentKey != null && lastEditor != null) {
          val oldKey = currentKey!!
          val newKey = lastEditor!!.text!!
          property.changeEntryKey(oldKey, newKey)
          currentRow = -1
          currentKey = null
          logValueEdited()
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

  override fun createNew(
    property: ModelPropertyT,
    cellEditor: TableCellEditor?,
    isPropertyContext: Boolean
  ): ModelPropertyEditor<Map<String, ValueT>> =
    MapPropertyEditor(property, propertyContext, editor, variablesScope) { /* no usage logging */ }
}

