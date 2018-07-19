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

import com.android.tools.idea.gradle.structure.configurables.ui.*
import com.android.tools.idea.gradle.structure.model.PsVariablesScope
import com.android.tools.idea.gradle.structure.model.meta.*
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import java.awt.Dimension
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import javax.swing.DefaultComboBoxModel
import javax.swing.Icon

/**
 * A property editor [ModelPropertyEditor] for properties of simple (not complex) types.
 *
 * This is a [ComboBox] based editor allowing manual text entry as well as entry by selecting an item from the list of values provided by
 * [ModelSimpleProperty.getKnownValues]. Text free text input is parsed by [ModelSimpleProperty.parse].
 */
class SimplePropertyEditor<PropertyT : Any, ModelPropertyT : ModelPropertyCore<PropertyT>>(
  property: ModelPropertyT,
  propertyContext: ModelPropertyContext<PropertyT>,
  variablesScope: PsVariablesScope?,
  private val extensions: List<EditorExtensionAction<PropertyT, ModelPropertyT>>
) :
  PropertyEditorBase<ModelPropertyT, PropertyT>(property, propertyContext, variablesScope),
  ModelPropertyEditor<PropertyT>,
  ModelPropertyEditorFactory<PropertyT, ModelPropertyT> {

  private var knownValueRenderers: Map<ParsedValue<PropertyT>, ValueRenderer> = mapOf()
  private var disposed = false
  private var knownValuesFuture: ListenableFuture<Unit>? = null  // Accessed only from the EDT.
  private val formatter = propertyContext.valueFormatter()

  private val renderedComboBox = object : RenderedComboBox<Annotated<ParsedValue<PropertyT>>>(DefaultComboBoxModel()) {

    override fun getPreferredSize(): Dimension {
      val dimensions = super.getPreferredSize()
      return if (dimensions.width < 200) {
        Dimension(200, dimensions.height)
      }
      else dimensions
    }

    override fun parseEditorText(text: String): Annotated<ParsedValue<PropertyT>>? = when {
      text.startsWith("\$\$") ->
        ParsedValue.Set.Parsed(value = null, dslText = DslText.OtherUnparsedDslText(text.substring(2))).annotated()
      text.startsWith("\$") ->
        ParsedValue.Set.Parsed<PropertyT>(value = null, dslText = DslText.Reference(text.substring(1))).annotated()
      text.startsWith("\"") && text.endsWith("\"") ->
        ParsedValue.Set.Parsed<PropertyT>(value = null,
                                                dslText = DslText.InterpolatedString(text.substring(1, text.length - 1))).annotated()
      else -> propertyContext.parse(text)
    }

    override fun toEditorText(anObject: Annotated<ParsedValue<PropertyT>>?): String = when (anObject) {
      null -> ""
      // Annotations are not part of the value.
      else -> anObject.value.getText(formatter)
    }

    override fun TextRenderer.renderCell(value: Annotated<ParsedValue<PropertyT>>?) {
      (value ?: ParsedValue.NotSet.annotated()).renderTo(this, formatter, knownValueRenderers)
    }

    override fun createEditorExtensions(): List<Extension> = extensions.map {action ->
      object : Extension {
        override fun getIcon(hovered: Boolean): Icon = action.icon
        override fun getTooltip(): String = action.tooltip
        override fun getActionOnClick(): Runnable = Runnable { action.invoke(property, this@SimplePropertyEditor, this@SimplePropertyEditor) }
      }
    }

    fun loadKnownValues() {
      val availableVariables: List<Annotated<ParsedValue.Set.Parsed<PropertyT>>>? = getAvailableVariables()

      knownValuesFuture?.cancel(false)

      knownValuesFuture =
        propertyContext.getKnownValues().continueOnEdt { knownValues ->
          val possibleValues = buildKnownValueRenderers(knownValues!!, formatter, property.defaultValueGetter?.invoke())
          knownValueRenderers = possibleValues
          knownValues to possibleValues
        }.invokeLater { (knownValues, possibleValues) ->
          setKnownValues(
            (possibleValues.keys.toList().map { it.annotated() } +
             availableVariables?.filter { knownValues.isSuitableVariable(it) }.orEmpty()
            )
          )
          knownValuesFuture = null
        }
    }

    private fun setStatus(status: ValueRenderer) {
      statusComponent.clear()
      status.renderTo(statusComponentRenderer)
    }

    private fun getStatusRenderer(valueAnnotation: ValueAnnotation?): ValueRenderer =
      (valueAnnotation as? ValueAnnotation.Error).let {
        if (it != null) {
          object: ValueRenderer {
            override fun renderTo(textRenderer: TextRenderer): Boolean {
              textRenderer.append(it.message, SimpleTextAttributes.ERROR_ATTRIBUTES)
              return true
            }
          }
        } else {
          object: ValueRenderer {
            override fun renderTo(textRenderer: TextRenderer): Boolean = false
          }
        }
      }

    internal fun reloadValue(annotatedPropertyValue: Annotated<PropertyValue<PropertyT>>) {
      setValue(annotatedPropertyValue.value.parsedValue.let { annotatedParsedValue ->
        if (annotatedParsedValue.annotation != null && knownValueRenderers.containsKey(annotatedParsedValue.value))
          annotatedParsedValue.value.annotated()
        else annotatedParsedValue
      })
      setStatus(getStatusRenderer(annotatedPropertyValue.annotation))
      updateModified()
    }

    internal fun applyChanges(annotatedValue: Annotated<ParsedValue<PropertyT>>) {
      property.setParsedValue(annotatedValue.value)
    }

    private fun onEditorChanged() =
      when (updateProperty()) {
        UpdatePropertyOutcome.UPDATED -> reloadValue(property.getValue())
        UpdatePropertyOutcome.NOT_CHANGED -> Unit
        UpdatePropertyOutcome.INVALID -> Unit
      }

    private fun getAvailableVariables(): List<Annotated<ParsedValue.Set.Parsed<PropertyT>>>? =
      variablesScope?.getAvailableVariablesFor(propertyContext)

    fun addFocusGainedListener(listener: () -> Unit) {
      val focusListener = object : FocusListener {
        override fun focusLost(e: FocusEvent?) = Unit
        override fun focusGained(e: FocusEvent?) = listener()
      }
      editor.editorComponent.addFocusListener(focusListener)
      addFocusListener(focusListener)
    }

    /**
     * Returns [true] if the value currently being edited in the combo-box editor differs the last manually set value.
     *
     * (Returns [false] if the editor has not yet been initialized).
     */
    fun isEditorChanged() = lastValueSet != null && getValue().value != lastValueSet?.value

    init {
      setEditable(true)

      addActionListener {
        if (!disposed && !beingLoaded) {
          onEditorChanged()
        }
      }

      addFocusGainedListener {
        if (!disposed) {
          reloadIfNotChanged()
        }
      }
    }
  }

  override val component: RenderedComboBox<Annotated<ParsedValue<PropertyT>>> = renderedComboBox
  override val statusComponent: SimpleColoredComponent = SimpleColoredComponent()
  private val statusComponentRenderer = statusComponent.toRenderer()

  override fun getValue(): Annotated<ParsedValue<PropertyT>> =
    @Suppress("UNCHECKED_CAST")
    (renderedComboBox.editor.item as Annotated<ParsedValue<PropertyT>>)

  override fun updateProperty(): UpdatePropertyOutcome {
    if (disposed) throw IllegalStateException()
    // It is important to avoid applying the unchanged values since the application
    // process while not intended may change the "psi"-representation of the value.
    // it is especially important in the case of invalid/unparsed values.
    if (renderedComboBox.isEditorChanged()) {
      val annotatedValue = getValue()
      if (annotatedValue.annotation is ValueAnnotation.Error) return UpdatePropertyOutcome.INVALID
      renderedComboBox.applyChanges(annotatedValue)
      return UpdatePropertyOutcome.UPDATED
    }
    return UpdatePropertyOutcome.NOT_CHANGED
  }

  override fun dispose() {
    knownValuesFuture?.cancel(false)
    disposed = true
  }

  override fun reload() {
    renderedComboBox.loadKnownValues()
    renderedComboBox.reloadValue(property.getValue())
  }

  internal fun reloadIfNotChanged() {
    renderedComboBox.loadKnownValues()
    if (!renderedComboBox.isEditorChanged()) {  // Do not override a not applied invalid value.
      renderedComboBox.reloadValue(property.getValue())
    }
  }

  override fun createNew(property: ModelPropertyT): ModelPropertyEditor<PropertyT> =
    SimplePropertyEditor(property, propertyContext, variablesScope, extensions)

  init {
    reload()
  }
}
