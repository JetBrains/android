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
import com.android.tools.idea.gradle.structure.model.meta.ModelMapProperty
import com.android.tools.idea.gradle.structure.model.meta.ModelSimpleProperty
import com.android.tools.idea.gradle.structure.model.meta.ParsedValue
import com.android.tools.idea.gradle.structure.model.meta.PropertyEditorFactory
import javax.swing.table.DefaultTableColumnModel
import javax.swing.table.DefaultTableModel
import javax.swing.table.TableColumn
import javax.swing.table.TableColumnModel

/**
 * A property editor [ModelPropertyEditor] for properties of simple map types.
 */
class MapPropertyEditor<ModelT, ValueT : Any, out ModelPropertyT : ModelMapProperty<ModelT, ValueT>>(
  model: ModelT,
  property: ModelPropertyT,
  editor: PropertyEditorFactory<Unit, ModelSimpleProperty<Unit, ValueT>, ValueT>,
  variablesProvider: VariablesProvider?
) : CollectionPropertyEditor<ModelT, ModelPropertyT, ValueT>(model, property, editor, variablesProvider),
    ModelPropertyEditor<ModelT, Map<String, ValueT>> {

  init {
    loadValue()
  }

  override fun updateProperty() = throw UnsupportedOperationException()

  override fun dispose() = Unit

  override fun getValueAt(row: Int): ParsedValue<ValueT> {
    val entryKey = keyAt(row)
    val entryValue = if (entryKey == "") modelValueAt(row) else property.getEditableValues(model)[entryKey]?.getParsedValue(Unit)
    return entryValue ?: ParsedValue.NotSet()
  }

  override fun setValueAt(row: Int, value: ParsedValue<ValueT>) {
    val entryKey = keyAt(row)
    // If entryKey == "", we don't need to store the value in the property. It is, however, automatically stored in the table model and
    // it will be transferred to the property when the key value is set.
    if (entryKey != "") {
      (property.getEditableValues(model)[entryKey] ?: property.addEntry(model, entryKey)).setParsedValue(Unit, value)
    }
  }

  override fun createTableModel(): DefaultTableModel {
    val tableModel = DefaultTableModel()
    tableModel.addColumn("key")
    tableModel.addColumn("value")
    val value = property.getEditableValues(model)
    for ((k, v) in value.entries) {
      tableModel.addRow(arrayOf(k, v.getParsedValue(Unit).toTableModelValue()))
    }
    return tableModel
  }

  override fun createColumnModel(): TableColumnModel {
    return DefaultTableColumnModel().apply {
      addColumn(TableColumn(0, 50).apply { headerValue = "K" })
      addColumn(TableColumn(1).apply {
        headerValue = "V"
        cellEditor = MyCellEditor()
      })
    }
  }

  override fun getValueText(): String = throw UnsupportedOperationException()
  override fun getValue(): ParsedValue<Map<String, ValueT>> = throw UnsupportedOperationException()

  private fun keyAt(row: Int) = (table.model.getValueAt(row, 0) as? String).orEmpty()

  private fun modelValueAt(row: Int) =
    @Suppress("UNCHECKED_CAST")  // If it is of type Value, then generic type arguments are correct.
    (table.model.getValueAt(row, 1) as? CollectionPropertyEditor<ModelT, ModelPropertyT, ValueT>.Value)?.value

}

fun <ModelT, ValueT : Any, ModelPropertyT : ModelMapProperty<ModelT, ValueT>> mapPropertyEditor(
  editor: PropertyEditorFactory<Unit, ModelSimpleProperty<Unit, ValueT>, ValueT>
):
    PropertyEditorFactory<ModelT, ModelPropertyT, Map<String, ValueT>> =
  { model, property, variablesProvider -> MapPropertyEditor(model, property, editor, variablesProvider) }
