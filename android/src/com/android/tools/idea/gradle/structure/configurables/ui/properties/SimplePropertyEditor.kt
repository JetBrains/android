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

import com.android.tools.idea.gradle.structure.configurables.ui.RenderedComboBox
import com.android.tools.idea.gradle.structure.configurables.ui.TextRenderer
import com.android.tools.idea.gradle.structure.configurables.ui.toRenderer
import com.android.tools.idea.gradle.structure.model.VariablesProvider
import com.android.tools.idea.gradle.structure.model.meta.*
import com.google.common.annotations.VisibleForTesting
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
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
  variablesProvider: VariablesProvider?,
  extensions: List<EditorExtensionAction>
) :
  PropertyEditorBase<ModelPropertyT, PropertyT>(property, propertyContext, variablesProvider, extensions),
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

    private fun setStatusHtmlText(status: ValueRenderer) {
      statusComponent.clear()
      status.renderTo(statusComponentRenderer)
    }

    private fun getStatusRenderer(annotatedPropertyValue: Annotated<PropertyValue<PropertyT>>): ValueRenderer =
      (annotatedPropertyValue.annotation as? ValueAnnotation.Error).let {
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

    @VisibleForTesting
    fun reloadValue() {
      val annotatedPropertyValue = property.getValue()
      setValue(annotatedPropertyValue.value.parsedValue)
      setStatusHtmlText(getStatusRenderer(annotatedPropertyValue))
      updateModified()
    }

    internal fun applyChanges(annotatedValue: Annotated<ParsedValue<PropertyT>>) {
      property.setParsedValue(annotatedValue.value)
    }

    private fun getAvailableVariables(): List<Annotated<ParsedValue.Set.Parsed<PropertyT>>>? =
      variablesProvider?.getAvailableVariablesFor(propertyContext)

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
     */
    fun isEditorChanged() = editor.item != lastValueSet?.value

    init {
      setEditable(true)

      addActionListener {
        if (!disposed && !beingLoaded) {
          updateProperty()
          reloadValue()
        }
      }

      addFocusGainedListener {
        if (!disposed) {
          reload()
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

  override fun updateProperty() {
    if (disposed) throw IllegalStateException()
    // It is important to avoid applying the unchanged values since the application
    // process while not intended may change the "psi"-representation of the value.
    // it is especially important in the case of invalid/unparsed values.
    if (renderedComboBox.isEditorChanged()) {
      renderedComboBox.applyChanges(getValue())
    }
  }

  override fun dispose() {
    knownValuesFuture?.cancel(false)
    disposed = true
  }

  @VisibleForTesting
  fun reload() {
    renderedComboBox.loadKnownValues()
    renderedComboBox.reloadValue()
  }

  override fun createNew(property: ModelPropertyT): ModelPropertyEditor<PropertyT> =
    simplePropertyEditor(property, propertyContext, variablesProvider, extensions)

  init {
    reload()
  }
}

fun <PropertyT : Any, ModelPropertyT : ModelPropertyCore<PropertyT>> simplePropertyEditor(
  property: ModelPropertyT,
  propertyContext: ModelPropertyContext<PropertyT>,
  variablesProvider: VariablesProvider? = null,
  extensions: List<EditorExtensionAction> = listOf()
): SimplePropertyEditor<PropertyT, ModelPropertyT> =
  SimplePropertyEditor(property, propertyContext, variablesProvider, extensions)


private fun <I, O> ListenableFuture<I>.continueOnEdt(continuation: (I) -> O) =
  Futures.transform(
    this, { continuation(it!!) },
    {
      val application = ApplicationManager.getApplication()
      if (application.isDispatchThread) {
        it.run()
      }
      else {
        application.invokeLater(it, ModalityState.any())
      }
    })

private fun <I, O> ListenableFuture<I>.invokeLater(continuation: (I) -> O) =
  Futures.transform(
    this, { continuation(it!!) },
    {
      val application = ApplicationManager.getApplication()
      if (application.isUnitTestMode) {
        it.run()
      }
      else {
        application.invokeLater(it, ModalityState.any())
      }
    })
