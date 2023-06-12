/*
 * Copyright (C) 2022 The Android Open Source Project
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
import com.android.tools.idea.gradle.structure.model.meta.ModelPropertyContext
import com.android.tools.idea.gradle.structure.model.meta.ModelPropertyCore
import com.android.tools.idea.gradle.structure.model.meta.ModelSimpleProperty
import com.android.tools.idea.gradle.structure.model.meta.ParsedValue
import com.android.tools.idea.gradle.structure.model.meta.ValueAnnotation
import com.android.tools.idea.gradle.structure.model.meta.getText
import com.android.tools.idea.gradle.structure.model.meta.valueFormatter
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.components.JBTextField
import java.awt.Dimension
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import javax.swing.JComponent
import javax.swing.table.TableCellEditor

/**
 * A property editor [ModelPropertyEditor] for catalog variables.
 * Catalog variables have string values only.
 * This is a [TextField] based editor allowing manual text entry.
*/
class StringPropertyEditor<ModelPropertyT : ModelPropertyCore<String>>(
  property: ModelPropertyT,
  propertyContext: ModelPropertyContext<String>,
  private val extensions: List<EditorExtensionAction<String, ModelPropertyT>>,
  cellEditor: TableCellEditor? = null,
  private val logValueEdited: () -> Unit = {}
) :
  PropertyEditorBase<ModelPropertyT, String>(property, propertyContext, null),
  ModelPropertyEditor<String>,
  ModelPropertyEditorFactory<String, ModelPropertyT> {

  init {
    check(extensions.count { it.isMainAction } <= 1) {
      "Multiple isMainAction == true editor extensions: ${extensions.filter { it.isMainAction }}"
    }
  }
  private var lastValueSet: String? = null
  private var disposed = false
  private val formatter = propertyContext.valueFormatter()

  private val textField = object : JBTextField() {

    override fun getPreferredSize(): Dimension {
      val dimensions = super.getPreferredSize()
      return if (dimensions.width < 200) {
        Dimension(200, dimensions.height)
      }
      else dimensions
    }

    fun addFocusLostListener(listener: () -> Unit) {
      val focusListener = object : FocusListener {
        override fun focusLost(e: FocusEvent?) = listener()
        override fun focusGained(e: FocusEvent?) = Unit
      }
      addFocusListener(focusListener)
    }

    /**
     * Returns [true] if the value currently being edited in the combo-box editor differs the last manually set value.
     *
     * (Returns [false] if the editor has not yet been initialized).
     */
    fun isEditorChanged() = lastValueSet != null && lastValueSet != text

    fun applyChanges(annotatedValue: Annotated<ParsedValue<String>>) {
      property.setParsedValue(annotatedValue.value)
    }

    init {
      if (cellEditor != null) {
        // Do not call registerTableCellEditor(cellEditor) which registers "JComboBox.isTableCellEditor" property which
        // breaks property editors.
        putClientProperty(ComboBox.TABLE_CELL_EDITOR_PROPERTY, cellEditor)
      }
      text = property.getParsedValue().value.getText(formatter)
      lastValueSet = text
      isEditable = true
      addFocusLostListener{
        if (!disposed) updateProperty()
      }
    }
  }

  override val component: JComponent = textField

  override val statusComponent: SimpleColoredComponent = SimpleColoredComponent()

  override fun getValue(): Annotated<ParsedValue<String>> =
    propertyContext.parseEditorText(textField.text.trim())

  override fun updateProperty(): UpdatePropertyOutcome {
    if (disposed) throw IllegalStateException()
    // It is important to avoid applying the unchanged values since the application
    // process while not intended may change the "psi"-representation of the value.
    // it is especially important in the case of invalid/unparsed values.
    if (textField.isEditorChanged()) {
      val annotatedValue = getValue()
      if (annotatedValue.annotation is ValueAnnotation.Error) return UpdatePropertyOutcome.INVALID
      textField.applyChanges(annotatedValue)
      logValueEdited()
      return UpdatePropertyOutcome.UPDATED
    }
    return UpdatePropertyOutcome.NOT_CHANGED
  }

  override fun dispose() {
    disposed = true
  }

  override fun reload() {
  }

  override fun reloadIfNotChanged() {
  }

  override fun createNew(
    property: ModelPropertyT,
    cellEditor: TableCellEditor?,
    isPropertyContext: Boolean
  ): ModelPropertyEditor<String> =
    stringVariablePropertyEditor(property, propertyContext, extensions, isPropertyContext, cellEditor)

  override fun addFocusListener(listener: FocusListener) {
    textField.addFocusListener(listener)
  }
}

fun <ModelPropertyT : ModelPropertyCore<String>> stringVariablePropertyEditor(
  boundProperty: ModelPropertyT,
  boundPropertyContext: ModelPropertyContext<String>,
  extensions: Collection<EditorExtensionAction<String, ModelPropertyT>>,
  isPropertyContext: Boolean,
  cellEditor: TableCellEditor?,
  logValueEdited: () -> Unit = { /* no usage tracking */ }
): StringPropertyEditor<ModelPropertyT> =
  StringPropertyEditor(
    boundProperty,
    boundPropertyContext,
    extensions.filter { it.isAvailableFor(boundProperty, isPropertyContext) },
    cellEditor,
    logValueEdited)
