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

import com.android.tools.idea.gradle.structure.model.meta.*
import com.intellij.openapi.ui.ComboBox
import java.awt.Color
import java.awt.Dimension
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent
import javax.swing.JTextField

/**
 * A property editor [ModelPropertyEditor] for properties of simple (not complex) types.
 *
 * This is a [ComboBox] based editor allowing manual text entry as well as entry by selecting an item from the list of values provided by
 * [ModelSimpleProperty.getKnownValues]. Text free text input is parsed by [ModelSimpleProperty.parse].
 */
class SimplePropertyEditor<ModelT, PropertyT: Any, out ModelPropertyT: ModelSimpleProperty<ModelT, PropertyT>>(
    val model: ModelT,
    val property: ModelPropertyT
) : ComboBox<String>(), ModelPropertyEditor<ModelT> {

  private val textToValue: Map<String, PropertyT>
  private val valueToText: Map<PropertyT, String>
  private var beingLoaded = false

  override val component: JComponent = this

  override fun getPreferredSize(): Dimension {
    val dimensions = super.getPreferredSize()
    return if (dimensions.width < 200) {
      Dimension(200, dimensions.height)
    }
    else dimensions
  }

  private fun getParsedValue(): ParsedValue<PropertyT> {
    val text = editor.item.toString()
    return textToValue[text]?.let { ParsedValue.Set.Parsed(value = it) } ?: property.parse(text)
  }

  private fun setText(text: String) {
    selectedItem = text
  }

  private fun setValue(value: PropertyT?) {
    selectedItem = if (value == null) "" else (valueToText[value] ?: value.toString())
  }

  private fun setColorAndTooltip(toolTipText: String? = null, background: Color? = null) {
    val jTextField = editor.editorComponent as? JTextField
    if (jTextField != null) {
      if (toolTipText != null) jTextField.toolTipText = toolTipText
      if (background != null) jTextField.background = background
    }
  }

  private fun loadValue(value: PropertyValue<PropertyT>) {
    beingLoaded = true
    try {
      when (value.parsedValue) {
        is ParsedValue.NotSet -> {
          setText("")
        }
        is ParsedValue.Set.Parsed -> {
          setValue(value.parsedValue.value)
        }
        is ParsedValue.Set.Invalid -> {
          setText(value.parsedValue.dslText)
        }
      }
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
              toolTipText = "[Set does not match resolved?]",
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
              toolTipText = value.parsedValue.dslText?.text.orEmpty()
          )

        }
      }
    }
    finally {
      beingLoaded = false
    }
  }

  private fun applyChanges(value: ParsedValue<PropertyT>) {
    when (value) {
      is ParsedValue.Set.Invalid -> Unit
      else -> property.setValue(model!!, value)
    }
  }

  init {
    minLength = 60
    setEditable(true)

    val possibleValues = property.getKnownValues(model) ?: listOf()
    textToValue = possibleValues.associate { it.description to it.value }
    valueToText = possibleValues.associate { it.value to it.description }
    super.setModel(DefaultComboBoxModel<String>(textToValue.keys.toTypedArray()))

    loadValue(property.getValue(model))

    addActionListener {
      if (!beingLoaded) {
        applyChanges(getParsedValue())
      }
    }
  }
}