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

import com.android.tools.idea.gradle.structure.model.VariablesProvider
import com.android.tools.idea.gradle.structure.model.meta.*
import javax.swing.table.DefaultTableColumnModel
import javax.swing.table.DefaultTableModel
import javax.swing.table.TableColumn
import javax.swing.table.TableColumnModel

/**
 * A property editor [ModelPropertyEditor] for properties of simple list types.
 */
class ListPropertyEditor<ModelT, ValueT : Any, out ModelPropertyT : ModelListProperty<ModelT, ValueT>>(
  model: ModelT,
  property: ModelPropertyT,
  editor: PropertyEditorFactory<Unit, ModelSimpleProperty<Unit, ValueT>, ValueT>,
  variablesProvider: VariablesProvider?
) :
  CollectionPropertyEditor<ModelT, ModelPropertyT, ValueT>(model, property, editor, variablesProvider),
  ModelPropertyEditor<ModelT, List<ValueT>> {

  override fun updateProperty() = throw UnsupportedOperationException()

  override fun dispose() = Unit

  private var valueToText: Map<ValueT, String>

  init {
    val possibleValues = property.getKnownValues(model) ?: listOf()
    valueToText = possibleValues.associate { it.value to it.description }
    loadValue()
  }

  override fun createTableModel(): DefaultTableModel {
    val tableModel = DefaultTableModel()
    tableModel.addColumn("item")
    for (item in property.getEditableValues(model)) {
      tableModel.addRow(arrayOf(item.getValue().parsedValue.getText(valueToText)))
    }
    return tableModel
  }

  override fun getRowElement(rowIndex: Int): ModelPropertyCore<Unit, ValueT> = property.getEditableValues(model)[rowIndex]

  override fun createColumnModel(): TableColumnModel {
    return DefaultTableColumnModel().apply {
      addColumn(TableColumn(0).apply {
        headerValue = "V"
        cellEditor = MyCellEditor()
      })
    }
  }

  override fun getValueText(): String = throw UnsupportedOperationException()
  override fun getValue(): ParsedValue<List<ValueT>> = throw UnsupportedOperationException()
}

fun <ModelT, ValueT : Any, ModelPropertyT : ModelListProperty<ModelT, ValueT>> listPropertyEditor(
  editor: PropertyEditorFactory<Unit, ModelSimpleProperty<Unit, ValueT>, ValueT>
):
    PropertyEditorFactory<ModelT, ModelPropertyT, List<ValueT>> =
    { model, property, variablesProvider -> ListPropertyEditor(model, property, editor, variablesProvider) }
