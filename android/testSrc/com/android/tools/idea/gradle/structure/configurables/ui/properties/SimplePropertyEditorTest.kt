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
import org.hamcrest.CoreMatchers
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.nullValue
import org.junit.Assert.*
import org.junit.Assume.assumeThat
import org.junit.Ignore
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import javax.swing.ListModel

class SimplePropertyEditorTest {

  class Model {
    var modified: Boolean = false
  }

  class ResolvedModel {
    var value: String? = "resolved"
  }

  class ParsedModel {
    var value: String? = "parsed"
    var dsl: DslText? = null
  }

  private val model = Model()
  private val resolvedModel = ResolvedModel()
  private val parsedModel = ParsedModel()

  private val modelDescriptor = object : ModelDescriptor<Model, ResolvedModel, ParsedModel> {
    override fun getResolved(model: Model): ResolvedModel? = resolvedModel
    override fun getParsed(model: Model): ParsedModel? = parsedModel
    override fun setModified(model: Model) {
      model.modified = true
    }
  }
  private var wellKnownValues = listOf(ValueDescriptor("1", "one"), ValueDescriptor("2", "two"))

  private val property = ModelSimplePropertyImpl(
    modelDescriptor,
    "Description",
    defaultValueGetter = { "default" },
    getResolvedValue = { value },
    getParsedValue = { value },
    getParsedRawValue = { dsl ?: DslText(mode = DslMode.LITERAL, text = value.orEmpty()) },
    setParsedValue = { value = it; dsl = null },
    setParsedRawValue = { value = null; dsl = it; },
    parser = {
      when {
        it.isEmpty() -> ParsedValue.NotSet()
        it == "invalid" -> ParsedValue.Set.Invalid("invalid", "invalid text message")
        else -> ParsedValue.Set.Parsed(value = it)
      }
    },
    knownValuesGetter = { wellKnownValues })

  @Test
  fun loadsValue() {
    val editor = simplePropertyEditor(model, property)
    assertThat(editor.selectedItem as String, equalTo("parsed"))
  }

  @Test
  fun loadsWellKnownValue() {
    parsedModel.value = "1"
    val editor = simplePropertyEditor(model, property)
    assertThat(editor.selectedItem as String, equalTo("one"))
  }

  @Test
  fun loadsReference() {
    parsedModel.dsl = DslText(DslMode.REFERENCE, "some_reference")
    val editor = simplePropertyEditor(model, property)
    assertThat(editor.selectedItem as String, equalTo("\$some_reference"))
  }

  @Test
  fun loadsReferenceResolvedIntoKnownValue() {
    parsedModel.dsl = DslText(DslMode.REFERENCE, "some_reference")
    parsedModel.value = "1"
    val editor = simplePropertyEditor(model, property)
    assertThat(editor.selectedItem as String, equalTo("\$some_reference"))
  }

  @Test
  fun loadsInterpolatedString() {
    parsedModel.dsl = DslText(DslMode.INTERPOLATED_STRING, "some \${reference}")
    val editor = simplePropertyEditor(model, property)
    assertThat(editor.selectedItem as String, equalTo("\"some \${reference}\""))
  }

  @Test
  fun loadsOtherUnparsedDslText() {
    parsedModel.dsl = DslText(DslMode.OTHER_UNPARSED_DSL_TEXT, "1 + z(x)")
    parsedModel.value = null
    val editor = simplePropertyEditor(model, property)
    assertThat(editor.selectedItem as String, equalTo("\$\$1 + z(x)"))
  }

  @Test
  fun loadsDropDownList() {
    val variablesProvider = mock(VariablesProvider::class.java)
    `when`(variablesProvider.getAvailableVariablesForType(String::class.java)).thenReturn(
      listOf(
        "var1" to "val1",
        "var2" to "val2"
      )
    )
    val editor = simplePropertyEditor(model, property, variablesProvider)
    assertThat(editor.getModel().getItems(), CoreMatchers.hasItems("one", "two", "\$var1", "\$var2"))
  }

  @Test
  fun reloadsDropDownList() {
    val variablesProvider = mock(VariablesProvider::class.java)
    `when`(variablesProvider.getAvailableVariablesForType(String::class.java)).thenReturn(
      listOf(
        "var1" to "val1",
        "var2" to "val2"
      )
    )
    val editor = simplePropertyEditor(model, property, variablesProvider)
    assertThat(editor.getModel().getItems(), CoreMatchers.hasItems("one", "two", "\$var1", "\$var2"))

    wellKnownValues = listOf(ValueDescriptor("1", "one"), ValueDescriptor("2", "two"), ValueDescriptor("3", "three"))
    editor.selectedItem = "two"
    editor.simulateEditorGotFocus()

    assertThat(editor.getModel().getItems(), CoreMatchers.hasItems("one", "two", "three", "\$var1", "\$var2"))
    assertThat(parsedModel.value, equalTo("2"))
    assertThat(editor.selectedItem as String, equalTo("two"))

    wellKnownValues = listOf(ValueDescriptor("1", "one"), ValueDescriptor("3", "three"))
    editor.simulateEditorGotFocus()

    assertThat(editor.getModel().getItems(), CoreMatchers.hasItems("one", "three", "\$var1", "\$var2"))
    assertThat(parsedModel.value, equalTo("2"))
    assertThat(editor.selectedItem as String, equalTo("2"))
  }

  @Test
  fun updatesValue() {
    val editor = simplePropertyEditor(model, property)
    editor.selectedItem = "abc"
    assertThat(parsedModel.value, equalTo("abc"))
  }

  @Test
  fun updatesToNullValue() {
    val editor = simplePropertyEditor(model, property)
    editor.selectedItem = ""
    assertThat(parsedModel.value, nullValue())
  }

  @Test
  fun updatesToReference() {
    val editor = simplePropertyEditor(model, property)
    editor.selectedItem = "\$other.reference"
    assertThat(parsedModel.value, nullValue())
    assertThat(parsedModel.dsl?.mode, equalTo(DslMode.REFERENCE))
    assertThat(parsedModel.dsl?.text, equalTo("other.reference"))
  }

  @Test
  fun updatesToInterpolatedString() {
    val editor = simplePropertyEditor(model, property)
    // TODO(b/72088238): Decide on the exact format.
    editor.selectedItem = "\"\$a and \$b\""
    assertThat(parsedModel.value, nullValue())
    assertThat(parsedModel.dsl?.mode, equalTo(DslMode.INTERPOLATED_STRING))
    assertThat(parsedModel.dsl?.text, equalTo("\$a and \$b"))
  }

  @Test
  fun updatesFromWellKnownValueDescription() {
    val editor = simplePropertyEditor(model, property)
    editor.selectedItem = "two"
    assertThat(parsedModel.value, equalTo("2"))
  }

  @Test
  @Ignore("b/72088462")
  fun updatesToOtherUnresolvedDslText() {
    // TODO(b/72088462): Decide what the expectations are.
  }


  @Test
  fun updateProperty() {
    val editor = simplePropertyEditor(model, property)
    editor.editor.item = "abc"
    // Our assumption is that changing the text editor content directly does not immediately raise the notification and thus it is possible
    // to test updateProperty() method.
    assumeThat(parsedModel.value, equalTo("parsed"))

    editor.updateProperty()
    assertThat(parsedModel.value, equalTo("abc"))
  }

  @Test
  fun getValue() {
    val editor = simplePropertyEditor(model, property)
    editor.editor.item = "abc"
    // Our assumption is that changing the text editor content directly does not immediately raise the notification and thus it is possible
    // to test updateProperty() method.
    assumeThat(parsedModel.value, equalTo("parsed"))

    assertThat(editor.getValueText(), equalTo("abc"))
    assertThat((editor.getValue() as ParsedValue.Set.Parsed).dslText, nullValue())
  }

  @Test
  fun dispose() {
    val editor = simplePropertyEditor(model, property)
    editor.dispose()
    editor.selectedItem = "abc"
    assertThat(parsedModel.value, equalTo("parsed"))
  }
  @Test
  fun handlesInvalidInput() {
    val editor = simplePropertyEditor(model, property)
    editor.selectedItem = "invalid"  // "invalid" is recognised as an invalid input by the test parser.
    // Right now invalid input is ignored.
    assertThat(parsedModel.value, equalTo("parsed"))
  }
}

private fun SimplePropertyEditor<*, *, *>.simulateEditorGotFocus() {
  // Directly invoke the action the editor performs on receiving the focus since the detached component cannot be focused.
  loadKnownValues()
  reloadValue()
}

private fun <T> ListModel<T>.getItems(): List<T> {
  val result = mutableListOf<T>()
  for (i in 0 until size) {
    result.add(getElementAt(i))
  }
  return result.toList()
}

