/*
 * Copyright (C) 2017 The Android Open Source Project
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
import com.android.tools.idea.gradle.structure.model.PsVariablesScope
import com.android.tools.idea.gradle.structure.model.meta.Annotated
import com.android.tools.idea.gradle.structure.model.meta.ModelListPropertyCore
import com.android.tools.idea.gradle.structure.model.meta.ModelPropertyContext
import com.android.tools.idea.gradle.structure.model.meta.ModelPropertyCore
import com.android.tools.idea.gradle.structure.model.meta.ParsedValue
import com.android.tools.idea.gradle.structure.model.meta.getValue
import com.intellij.ui.SimpleColoredComponent
import javax.swing.table.DefaultTableColumnModel
import javax.swing.table.DefaultTableModel
import javax.swing.table.TableCellEditor
import javax.swing.table.TableColumn
import javax.swing.table.TableColumnModel

/**
 * A property editor [ModelPropertyEditor] for properties of simple list types.
 */
class ListPropertyEditor<ValueT : Any, ModelPropertyT : ModelListPropertyCore<ValueT>>(
  property: ModelPropertyT,
  propertyContext: ModelPropertyContext<ValueT>,
  editor: PropertyEditorCoreFactory<ModelPropertyCore<ValueT>, ModelPropertyContext<ValueT>, ValueT>,
  variablesScope: PsVariablesScope?,
  logValueEdited: () -> Unit
) :
  CollectionPropertyEditor<ModelPropertyT, ValueT>(property, propertyContext, editor, variablesScope, logValueEdited),
  ModelPropertyEditor<List<ValueT>>, ModelPropertyEditorFactory<List<ValueT>, ModelPropertyT> {

  override fun updateProperty(): UpdatePropertyOutcome = throw UnsupportedOperationException()

  override fun reload() = loadValue()

  override fun dispose() = Unit

  init {
    loadValue()
  }

  override val statusComponent: SimpleColoredComponent = SimpleColoredComponent()

  override fun createTableModel(): DefaultTableModel {
    val tableModel = DefaultTableModel()
    tableModel.addColumn("item")
    for (item in property.getEditableValues()) {
      tableModel.addRow(arrayOf(item.getParsedValue().toTableModelValue()))
    }
    return tableModel
  }

  override fun createColumnModel(): TableColumnModel {
    return DefaultTableColumnModel().apply {
      addColumn(TableColumn(0).apply {
        headerValue = "V"
        cellEditor = MyCellEditor()
        cellRenderer = MyCellRenderer()
      })
    }
  }

  override fun getValue(): Annotated<ParsedValue<List<ValueT>>> = throw UnsupportedOperationException()

  override fun addItem() {
    tableModel?.let { tableModel ->
      val index = tableModel.rowCount
      val modelPropertyCore = property.addItem(index)
      tableModel.addRow(arrayOf(modelPropertyCore.getValue().value.parsedValue.toTableModelValue()))
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
          property.deleteItem(index)
          tableModel.removeRow(index)
        }
      }
    }
  }

  override fun getPropertyAt(row: Int) = property.getEditableValues()[row]

  override fun createNew(
    property: ModelPropertyT,
    cellEditor: TableCellEditor?,
    isPropertyContext: Boolean
  ): ModelPropertyEditor<List<ValueT>> =
    ListPropertyEditor(property, propertyContext, editor, variablesScope) { /* no usage logging */}
}

