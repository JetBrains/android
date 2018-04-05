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

import com.android.tools.adtui.HtmlLabel
import com.android.tools.idea.gradle.structure.configurables.ui.RenderedComboBox
import com.android.tools.idea.gradle.structure.configurables.ui.TextRenderer
import com.android.tools.idea.gradle.structure.model.VariablesProvider
import com.android.tools.idea.gradle.structure.model.meta.*
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.ui.ComboBox
import java.awt.Dimension
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent
import javax.swing.text.DefaultCaret

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
) : RenderedComboBox<ParsedValue<PropertyT>>(DefaultComboBoxModel<ParsedValue<PropertyT>>()), ModelPropertyEditor<ModelT, PropertyT> {
  private var knownValueRenderers: Map<PropertyT?, ValueRenderer> = mapOf()
  private var disposed = false

  override val component: JComponent = this
  override val statusComponent: HtmlLabel = HtmlLabel().also {
    // Note: this is important to be the first step to prevent automatic scrolling of the container to the last added label.
    (it.caret as DefaultCaret).updatePolicy = DefaultCaret.NEVER_UPDATE
    HtmlLabel.setUpAsHtmlLabel(it, font)
  }

  override fun getPreferredSize(): Dimension {
    val dimensions = super.getPreferredSize()
    return if (dimensions.width < 200) {
      Dimension(200, dimensions.height)
    }
    else dimensions
  }

  override fun getValue(): ParsedValue<PropertyT> =
    @Suppress("UNCHECKED_CAST")
    editor.item as ParsedValue<PropertyT>

  override fun getValueText(): String = getValue().getText()

  override fun updateProperty() {
    if (disposed) throw IllegalStateException()
    // It is important to avoid applying the unchanged values since the application
    // process while not intended may change the "psi"-representation of the value.
    // it is especially important in the case of invalid/unparsed values.
    if (isEditorChanged()) {
      applyChanges(getValue())
    }
  }

  override fun dispose() {
    disposed = true
  }

  @VisibleForTesting
  fun loadKnownValues() {
    val availableVariables: List<ParsedValue.Set.Parsed<PropertyT>>? = getAvailableVariables()
    val possibleValues = getKnowValues()
    val knownValues = possibleValues.keys.map {
      if (it != null) ParsedValue.Set.Parsed(it)
      else ParsedValue.NotSet
    } + availableVariables.orEmpty()
    knownValueRenderers = possibleValues

    setKnownValues(knownValues)
  }

  private fun getKnowValues(): Map<PropertyT?, ValueRenderer> {
    val defaultValue = property.getDefaultValue(model)
    val knownValues = property.getKnownValues(model)
    return buildKnownValueRenderers(knownValues, defaultValue)
  }

  private fun loadValue(value: PropertyValue<PropertyT>) {
    setValue(value.parsedValue.normalizeForEditorAndLookup())
    setStatusHtmlText(getStatusHtmlText(value))
  }

  private fun setStatusHtmlText(statusHtmlText: String) {
    statusComponent.text = statusHtmlText
  }

  private fun getStatusHtmlText(value: PropertyValue<PropertyT>): String {
    // TODO(solodkyy): Consider resolving well-known values and handling long string.
    fun PropertyT?.formatValue() = this?.toString()

    val parsedValue = value.parsedValue
    val resolvedValue = value.resolved
    val defaultValue = property.getDefaultValue(model)

    val effectiveEditorValue = when (parsedValue) {
      is ParsedValue.Set.Parsed -> parsedValue.value
      is ParsedValue.NotSet -> defaultValue
      else -> null
    }
    val resolvedValueText = when (resolvedValue) {
      is ResolvedValue.Set -> when {
        effectiveEditorValue != resolvedValue.resolved -> resolvedValue.resolved.formatValue()
        else -> null
      }
      is ResolvedValue.NotResolved -> null
    }
    return buildString {
      if (resolvedValueText != null) {
        append(" -> ")
        append(resolvedValueText)
      }
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

  private fun getAvailableVariables(): List<ParsedValue.Set.Parsed<PropertyT>>? =
    variablesProvider?.getAvailableVariablesForType(elementType)?.map {
      ParsedValue.Set.Parsed(
        value = it.second,
        dslText = DslText(mode = DslMode.REFERENCE, text = it.first)
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

  override fun parseEditorText(text: String): ParsedValue<PropertyT>? = when {
    text.startsWith("\$\$") -> ParsedValue.Set.Parsed(value = null, dslText = DslText(DslMode.OTHER_UNPARSED_DSL_TEXT, text.substring(2)))
    text.startsWith("\$") -> ParsedValue.Set.Parsed<PropertyT>(value = null, dslText = DslText(DslMode.REFERENCE, text.substring(1)))
    text.startsWith("\"") && text.endsWith("\"") ->
      ParsedValue.Set.Parsed<PropertyT>(value = null,
                                        dslText = DslText(DslMode.INTERPOLATED_STRING, text.substring(1, text.length - 1)))
    else -> property.parse(text)
  }


  override fun toEditorText(anObject: ParsedValue<PropertyT>?): String = when (anObject) {
    null -> ""
    else -> anObject.getText()
  }

  override fun TextRenderer.renderCell(value: ParsedValue<PropertyT>?) {
    (value ?: ParsedValue.NotSet).renderTo(this, knownValueRenderers)
  }

  init {
    setEditable(true)

    loadKnownValues()

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
    reloadValue()
  }
}

inline fun <ModelT, reified PropertyT : Any, ModelPropertyT : ModelSimpleProperty<ModelT, PropertyT>> simplePropertyEditor(
  model: ModelT,
  property: ModelPropertyT,
  variablesProvider: VariablesProvider? = null
): SimplePropertyEditor<ModelT, PropertyT, ModelPropertyT> =
  SimplePropertyEditor(PropertyT::class.java, model, property, variablesProvider)

private fun <T : Any> ParsedValue<T>.normalizeForEditorAndLookup() =
  if (this is ParsedValue.Set.Parsed && value != null && dslText?.mode == DslMode.LITERAL)
    ParsedValue.Set.Parsed(dslText = null, value = value)
  else
    this

