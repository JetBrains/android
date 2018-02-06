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
import com.intellij.openapi.actionSystem.ActionToolbarPosition
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.AbstractTableCellEditor
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.table.DefaultTableModel
import javax.swing.table.TableColumnModel
import kotlin.reflect.KProperty

/**
 * A base for editors of properties of [ModelT] which are collections of values of type [ValueT].
 */
abstract class CollectionPropertyEditor<ModelT, out ModelPropertyT : ModelCollectionProperty<ModelT, *, ValueT>, ValueT : Any>(
  val model: ModelT,
  val property: ModelPropertyT,
  private val editor: PropertyEditorFactory<Unit, ModelSimpleProperty<Unit, ValueT>, ValueT>,
  private val variablesProvider: VariablesProvider?
) : JPanel(BorderLayout()) {

  private var beingLoaded = false
  val component: JComponent get() = this

  private val table: JBTable = JBTable().also {
    add(
      ToolbarDecorator.createDecorator(it)
            .setAddAction({})
            .setRemoveAction({})
            .setPreferredSize(Dimension(450, 100))
            .setToolbarPosition(ActionToolbarPosition.RIGHT)
            .createPanel())
  }

  protected fun loadValue() {
    beingLoaded = true
    try {
      val tableModel = createTableModel()
      table.model = tableModel
      table.columnModel = createColumnModel()
    }
    finally {
      beingLoaded = false
    }
  }

  protected abstract fun createTableModel(): DefaultTableModel
  protected abstract fun createColumnModel(): TableColumnModel
  protected abstract fun getRowElement(rowIndex: Int): ModelPropertyCore<Unit, ValueT>

  inner class MyCellEditor : AbstractTableCellEditor() {
    private var currentRow: Int = -1
    private var currentProperty: ModelPropertyCore<Unit, ValueT>? = null
    private var lastEditor: ModelPropertyEditor<Unit, ValueT>? = null
    private val bindingProperty = BindingProperty()

    override fun getTableCellEditorComponent(table: JTable?, value: Any?, isSelected: Boolean, row: Int, column: Int): Component? {
      currentRow = row
      currentProperty = getRowElement(currentRow)
      val editor = this@CollectionPropertyEditor.editor(Unit, bindingProperty, variablesProvider)
      lastEditor = editor
      return editor.component
    }

    override fun stopCellEditing(): Boolean {
      return super.stopCellEditing().also {
        if (it) {
          lastEditor?.updateProperty()
          currentRow = -1
          currentProperty = null
          lastEditor?.dispose()
        }
      }
    }

    override fun cancelCellEditing() {
      currentRow = -1
      currentProperty = null
      lastEditor?.dispose()
      super.cancelCellEditing()
    }

    override fun getCellEditorValue(): Any = lastEditor!!.getValueText()

    inner class BindingProperty : ModelSimpleProperty<Unit, ValueT> {
      override val description: String = "Binding Property"
      override fun getParsedValue(model: Unit): ParsedValue<ValueT> = currentProperty?.getParsedValue(Unit) ?: ParsedValue.NotSet()
      override fun getResolvedValue(model: Unit): ResolvedValue<ValueT> = ResolvedValue.NotResolved()
      override fun setParsedValue(model: Unit, value: ParsedValue<ValueT>) = currentProperty?.setParsedValue(Unit, value) ?: Unit
      override fun getDefaultValue(model: Unit): ValueT? = null
      override fun parse(value: String): ParsedValue<ValueT> = property.parse(value)
      override fun getKnownValues(model: Unit): List<ValueDescriptor<ValueT>>? =
        property.getKnownValues(this@CollectionPropertyEditor.model)

      override fun getValue(thisRef: Unit, property: KProperty<*>): ParsedValue<ValueT> =
        getParsedValue(thisRef)

      override fun setValue(thisRef: Unit, property: KProperty<*>, value: ParsedValue<ValueT>) =
        setParsedValue(thisRef, value)
    }
  }
}

