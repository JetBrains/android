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
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.ui.ComboBox
import java.awt.Color
import java.awt.Dimension
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent
import javax.swing.JTextField

/**
 * A property editor [ModelPropertyEditor] for properties of simple (not complex) types.
 *
 * This is a [ComboBox] based editor allowing manual text entry as well as entry by selecting an item from the list of values provided by
 * [ModelSimpleProperty.getKnownValues]. Text free text input is parsed by [ModelSimpleProperty.parse].
 */
class SimplePropertyEditor<ModelT, PropertyT : Any, out ModelPropertyT : ModelSimpleProperty<ModelT, PropertyT>>(
  val elementType: Class<PropertyT>,
  val model: ModelT,
  val property: ModelPropertyT,
  private val variablesProvider: VariablesProvider?
) : ComboBox<String>(), ModelPropertyEditor<ModelT, PropertyT> {
  private var textToParsedValue: Map<String, ParsedValue<PropertyT>> = mapOf()
  private var valueToText: Map<PropertyT, String> = mapOf()
  private var beingLoaded = false
  private var disposed = false
  private var lastTextSet: String? = null



  override val component: JComponent = this

  override fun getPreferredSize(): Dimension {
    val dimensions = super.getPreferredSize()
    return if (dimensions.width < 200) {
      Dimension(200, dimensions.height)
    } else dimensions
  }

  override fun getValue(): ParsedValue<PropertyT> {
    // Note: it is fine to get the current value of a disposed editor.
    val text = editor.item.toString()
    return when {
      text.startsWith("\$") -> ParsedValue.Set.Parsed<PropertyT>(value = null, dslText = DslText(DslMode.REFERENCE, text.substring(1)))
      text.startsWith("\"") && text.endsWith("\"") ->
        ParsedValue.Set.Parsed<PropertyT>(value = null, dslText = DslText(DslMode.INTERPOLATED_STRING, text.substring(1, text.length - 1)))
      else -> textToParsedValue[text] ?: property.parse(text)
    }
  }

  override fun getValueText(): String = getValue().getText(valueToText)

  override fun updateProperty() {
    if (disposed) throw IllegalStateException()
    // It is important to avoid applying the unchanged values since the application
    // process while not intended may change the "psi"-representation of the value.
    // it is especially important in the case of invalid/unparsed values.
    if (isChanged()) {
      applyChanges(getValue())
    }
  }

  override fun dispose() {
    disposed = true
  }

  private fun isChanged() = editor.item.toString() != lastTextSet

  private fun setText(text: String) {
    lastTextSet = text
    selectedItem = text
  }

  private fun setColorAndTooltip(toolTipText: String? = null, background: Color? = null) {
    val jTextField = editor.editorComponent as? JTextField
    if (jTextField != null) {
      if (toolTipText != null) jTextField.toolTipText = toolTipText
      if (background != null) jTextField.background = background
    }
  }

  @VisibleForTesting
  fun loadKnownValues() {
    val availableVariables = getAvailableVariables()
    val possibleValues = property.getKnownValues(model) ?: listOf()
    textToParsedValue =
        (possibleValues.map { it.description to ParsedValue.Set.Parsed(value = it.value) } + (availableVariables ?: listOf())).toMap()
    valueToText = possibleValues.associate { it.value to it.description }
    val comboBoxModel = DefaultComboBoxModel<String>(textToParsedValue.keys.toTypedArray()).apply {
      selectedItem = super.getSelectedItem()
    }
    super.setModel(comboBoxModel)
  }

  private fun loadValue(value: PropertyValue<PropertyT>) {
    beingLoaded = true
    try {
      setText(value.parsedValue.getText(valueToText))
      val defaultValue = property.getDefaultValue(model)
      when {
        value.resolved is ResolvedValue.NotResolved && value.parsedValue is ParsedValue.Set -> {
          setColorAndTooltip(
            toolTipText = "[Set but not resolved - not yet synced?]",
            background = Color.GREEN
          )
        }
        value.resolved is ResolvedValue.Set &&
            (value.parsedValue is ParsedValue.Set.Parsed &&
                value.resolved.resolved != value.parsedValue.value ||
                value.parsedValue is ParsedValue.NotSet &&
                value.resolved.resolved != defaultValue)
        -> {
          setColorAndTooltip(
            toolTipText = "[Set does not match resolved? - '${value.resolved.resolved.toString()}']",
            background = Color.YELLOW
          )
        }
        value.parsedValue is ParsedValue.Set.Invalid -> {
          setColorAndTooltip(
            toolTipText = "[Invalid?]",
            background = Color.RED
          )
        }
        value.parsedValue is ParsedValue.Set.Parsed -> {
          setColorAndTooltip(
            toolTipText = " = ${value.parsedValue.value.toString()}"
          )
        }
      }
    } finally {
      beingLoaded = false
    }
  }

  @VisibleForTesting
  fun reloadValue() {
    loadValue(property.getValue(model))
  }

  private fun applyChanges(value: ParsedValue<PropertyT>) {
    when (value) {
      is ParsedValue.Set.Invalid -> Unit
      else -> property.setParsedValue(model!!, value)
    }
  }

  private fun getAvailableVariables(): List<Pair<String, ParsedValue.Set.Parsed<PropertyT>>>? =
    variablesProvider?.getAvailableVariablesForType(elementType)?.map {
      val referenceText = "\$${it.first}"
      referenceText to ParsedValue.Set.Parsed(
        value = it.second,
        dslText = DslText(mode = DslMode.REFERENCE, text = referenceText)
      )
    }

  private fun addFocusGainedListener(listener: () -> Unit) {
    val focusListener = object : FocusListener {
      override fun focusLost(e: FocusEvent?) = Unit
      override fun focusGained(e: FocusEvent?) = listener()
    }
    editor.editorComponent.addFocusListener(focusListener)
    addFocusListener(focusListener)
  }

  init {
    minLength = 60
    setEditable(true)

    super.setModel(DefaultComboBoxModel<String>())

    loadKnownValues()
    reloadValue()

    addActionListener {
      if (!disposed && !beingLoaded) {
        updateProperty()
        reloadValue()
      }
    }
    addFocusGainedListener {
      if (!disposed) {
        loadKnownValues()
        reloadValue()
      }
    }
  }
}

inline fun <ModelT, reified PropertyT : Any, ModelPropertyT : ModelSimpleProperty<ModelT, PropertyT>> simplePropertyEditor(
  model: ModelT,
  property: ModelPropertyT,
  variablesProvider: VariablesProvider? = null
): SimplePropertyEditor<ModelT, PropertyT, ModelPropertyT> =
  SimplePropertyEditor(PropertyT::class.java, model, property, variablesProvider)

