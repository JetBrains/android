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

import com.android.tools.idea.gradle.structure.configurables.ui.toRenderer
import com.android.tools.idea.gradle.structure.model.VariablesProvider
import com.android.tools.idea.gradle.structure.model.meta.*
import com.intellij.openapi.actionSystem.ActionToolbarPosition
import com.intellij.ui.SimpleColoredComponent
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
import javax.swing.table.TableCellRenderer
import javax.swing.table.TableColumnModel

/**
 * A base for editors of properties which are collections of values of type [ValueT].
 */
abstract class
CollectionPropertyEditor<out ModelPropertyT : ModelCollectionPropertyCore<*>, ValueT : Any>(
  val property: ModelPropertyT,
  val propertyContext: ModelPropertyContext<ValueT>,
  private val editor: PropertyEditorFactory<ModelPropertyCore<ValueT>, ModelPropertyContext<ValueT>, ValueT>,
  private val variablesProvider: VariablesProvider?,
  private val extensions: List<EditorExtensionAction>
) : JPanel(BorderLayout()) {

  val component: JComponent get() = this
  val statusComponent: JComponent? = null
  private var beingLoaded = false
  protected var tableModel: DefaultTableModel? = null ; private set
  private val formatter = propertyContext.valueFormatter()
  private val knownValueRenderers: Map<ParsedValue<ValueT>, ValueRenderer> =
    buildKnownValueRenderers(propertyContext.getKnownValues().get(), formatter, null)

  protected val table: JBTable = JBTable()
    .apply {
      rowHeight = calculateMinRowHeight()
    }
    .also {
      add(
        ToolbarDecorator.createDecorator(it)
          .setAddAction { addItem() }
          .setRemoveAction { removeItem() }
          .setPreferredSize(Dimension(450, 100))
          .setToolbarPosition(ActionToolbarPosition.RIGHT)
          .createPanel()
      )
    }

  protected fun loadValue() {
    beingLoaded = true
    try {
      tableModel = createTableModel()
      table.model = tableModel
      table.columnModel = createColumnModel()
    }
    finally {
      beingLoaded = false
    }
  }

  protected abstract fun createTableModel(): DefaultTableModel
  protected abstract fun createColumnModel(): TableColumnModel
  protected abstract fun getValueAt(row: Int): ParsedValue<ValueT>
  protected abstract fun setValueAt(row: Int, value: ParsedValue<ValueT>)
  protected abstract fun addItem()
  protected abstract fun removeItem()

  private fun calculateMinRowHeight() = editor(SimplePropertyStub(), propertyContext, null, extensions).component.minimumSize.height

  protected fun ParsedValue<ValueT>.toTableModelValue() = Value(this)

  /**
   * A [ParsedValue] wrapper for the table model that defines a [toString] implementation compatible with the implementation
   * in [MyCellEditor].
   */
  protected inner class Value(val value: ParsedValue<ValueT>) {
    override fun toString(): String = value.getText(formatter)
  }

  inner class MyCellRenderer: TableCellRenderer {
    override fun getTableCellRendererComponent(table: JTable?,
                                               value: Any?,
                                               isSelected: Boolean,
                                               hasFocus: Boolean,
                                               row: Int,
                                               column: Int): Component {
      @Suppress("UNCHECKED_CAST")
      val parsedValue = (value as CollectionPropertyEditor<*, ValueT>.Value?)?.value ?: ParsedValue.NotSet
      return SimpleColoredComponent().also { parsedValue.renderTo(it.toRenderer(), formatter, knownValueRenderers) }
    }

  }

  inner class MyCellEditor : AbstractTableCellEditor() {
    private var currentRow: Int = -1
    private var lastEditor: ModelPropertyEditor<ValueT>? = null
    private var lastValue: ParsedValue<ValueT>? = null
    private val bindingProperty = BindingProperty()

    override fun getTableCellEditorComponent(table: JTable?, value: Any?, isSelected: Boolean, row: Int, column: Int): Component? {
      currentRow = row
      val editor = this@CollectionPropertyEditor.editor(bindingProperty, propertyContext, variablesProvider, extensions)
      lastEditor = editor
      lastValue = null
      return editor.component
    }

    override fun stopCellEditing(): Boolean {
      lastEditor?.updateProperty()
      lastValue = bindingProperty.getParsedValue()
      currentRow = -1
      lastEditor?.dispose()
      lastEditor = null
      fireEditingStopped()
      return true
    }

    override fun cancelCellEditing() {
      lastValue = lastEditor?.getValue()
      currentRow = -1
      lastEditor?.dispose()
      lastEditor = null
      super.cancelCellEditing()
    }

    override fun getCellEditorValue(): Any = (lastValue ?: lastEditor!!.getValue()).toTableModelValue()

    inner class BindingProperty : ModelPropertyCore<ValueT> {
      override fun getParsedValue(): ParsedValue<ValueT> = getValueAt(currentRow)
      override fun getResolvedValue(): ResolvedValue<ValueT> = ResolvedValue.NotResolved()
      override fun setParsedValue(value: ParsedValue<ValueT>) = setValueAt(currentRow, value)
      override val defaultValueGetter: (() -> ValueT?)? = null
    }
  }
}

class SimplePropertyStub<ValueT : Any> : ModelPropertyCore<ValueT> {
  override fun getParsedValue(): ParsedValue<ValueT> = ParsedValue.NotSet
  override fun setParsedValue(value: ParsedValue<ValueT>) = Unit
  override fun getResolvedValue(): ResolvedValue<ValueT> = ResolvedValue.NotResolved()
  override val defaultValueGetter: (() -> ValueT?)? = null
}
