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
import com.android.tools.idea.gradle.structure.model.VariablesProvider
import com.android.tools.idea.gradle.structure.model.meta.*
import org.hamcrest.CoreMatchers.*
import org.junit.Assert.assertThat
import org.junit.Assume.assumeThat
import org.junit.Ignore
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.awt.event.ActionEvent
import javax.swing.ListModel
import javax.swing.text.JTextComponent

class SimplePropertyEditorTest {

  class Model {
    var modified: Boolean = false
  }

  class ParsedModel {
    var value: String? = "value"
    var dsl: DslText? = DslText(DslMode.LITERAL, "value")
  }

  class ResolvedModel {
    // Be default the resolved value should match the configured one since this is what we expect from a successful sync.
    var value: String? = "value"
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
  private var defaultValue: String? = "default"
  private var wellKnownValues = listOf(ValueDescriptor("1", "one"), ValueDescriptor("2", "two"))
  private var translateDsl = mutableMapOf<String, String>()


  private val property = ModelSimplePropertyImpl(
    modelDescriptor,
    "Description",
    defaultValueGetter = { defaultValue },
    getResolvedValue = { value },
    getParsedValue = { value },
    getParsedRawValue = { dsl ?: if (value != null) DslText(mode = DslMode.LITERAL, text = value) else null },
    setParsedValue = { value = it; dsl = it?.let { DslText(DslMode.LITERAL, it.toString()) } },
    setParsedRawValue = { value = translateDsl[it.text]; dsl = it; },
    parser = {
      when {
        it.isEmpty() -> ParsedValue.NotSet
        it == "invalid" -> ParsedValue.Set.Invalid("invalid", "invalid text message")
        else -> ParsedValue.Set.Parsed(value = it)
      }
    },
    knownValuesGetter = { wellKnownValues })

  @Test
  fun loadsValue() {
    val editor = simplePropertyEditor(model, property)
    assertThat<Any?>(editor.selectedItem, equalTo("value".asSimpleParsed()))
    assertThat(editor.testPlainTextStatus, equalTo(""))
  }

  @Test
  fun loadsValueNotMatchingResolved() {
    resolvedModel.value = "other"
    val editor = simplePropertyEditor(model, property)
    assertThat<Any?>(editor.selectedItem, equalTo("value".asSimpleParsed()))
    assertThat(editor.testPlainTextStatus, equalTo(" -> other"))
  }

  @Test
  fun loadsWellKnownValue() {
    parsedModel.dsl = DslText(DslMode.LITERAL, "1")
    parsedModel.value = "1"
    resolvedModel.value = "1"
    val editor = simplePropertyEditor(model, property)
    assertThat<Any?>(editor.selectedItem, equalTo("1".asSimpleParsed()))
    assertThat(editor.testPlainTextStatus, equalTo(""))
  }

  @Test
  fun loadsNotSetValue() {
    parsedModel.dsl = null
    parsedModel.value = null
    resolvedModel.value = null
    val editor = simplePropertyEditor(model, property)
    assertThat<Any?>(editor.selectedItem, equalTo(ParsedValue.NotSet))
    assertThat(editor.testPlainTextStatus, equalTo(""))
  }

  @Test
  fun loadsNotSetValue_noDefault() {
    parsedModel.dsl = null
    parsedModel.value = null
    resolvedModel.value = null
    defaultValue = null
    val editor = simplePropertyEditor(model, property)
    assertThat<Any?>(editor.selectedItem, equalTo(ParsedValue.NotSet))
    assertThat(editor.testPlainTextStatus, equalTo(""))
  }

  @Test
  fun loadsReference() {
    parsedModel.dsl = DslText(DslMode.REFERENCE, "some_reference")
    val editor = simplePropertyEditor(model, property)
    assertThat<Any?>(editor.selectedItem, equalTo(("some_reference" to "value").asParsed()))
    assertThat(editor.testPlainTextStatus, equalTo(""))
  }

  @Test
  fun loadsReferenceNotMatchingResolved() {
    resolvedModel.value = "other"
    parsedModel.dsl = DslText(DslMode.REFERENCE, "some_reference")
    val editor = simplePropertyEditor(model, property)
    assertThat<Any?>(editor.selectedItem, equalTo(("some_reference" to "value").asParsed()))
    assertThat(editor.testPlainTextStatus, equalTo(" -> other"))
  }

  @Test
  fun loadsReferenceResolvedIntoKnownValue() {
    parsedModel.dsl = DslText(DslMode.REFERENCE, "some_reference")
    parsedModel.value = "1"
    resolvedModel.value = "1"
    val editor = simplePropertyEditor(model, property)
    assertThat<Any?>(editor.selectedItem, equalTo(("some_reference" to "1").asParsed()))
    assertThat(editor.testPlainTextStatus, equalTo(""))
  }

  @Test
  fun loadsInterpolatedString() {
    parsedModel.dsl = DslText(DslMode.INTERPOLATED_STRING, "some \${reference}")
    parsedModel.value = "some value"
    resolvedModel.value = "some value"
    val editor = simplePropertyEditor(model, property)
    assertThat<Any?>(editor.selectedItem, equalTo(
      ParsedValue.Set.Parsed(value = "some value", dslText = DslText(DslMode.INTERPOLATED_STRING, "some \${reference}"))))
    assertThat(editor.testPlainTextStatus, equalTo(""))
  }

  @Test
  fun loadsOtherUnparsedDslText() {
    parsedModel.dsl = DslText(DslMode.OTHER_UNPARSED_DSL_TEXT, "1 + z(x)")
    parsedModel.value = null
    val editor = simplePropertyEditor(model, property)
    assertThat<Any?>(editor.selectedItem,
                     equalTo(ParsedValue.Set.Parsed(value = null, dslText = DslText(DslMode.OTHER_UNPARSED_DSL_TEXT, "1 + z(x)"))))
    assertThat(editor.testPlainTextStatus, equalTo(" -> value"))
  }

  @Test
  fun loadsDropDownList() {
    val variablesProvider = mock(VariablesProvider::class.java)
    val var1 = "var1" to "val1"
    val var2 = "var2" to "val2"
    `when`(variablesProvider.getAvailableVariablesForType(String::class.java)).thenReturn(
      listOf(
        var1,
        var2
      )
    )
    val editor = simplePropertyEditor(model, property, variablesProvider)
    assertThat(editor.getModel().getItems(),
               hasItems("1".asSimpleParsed(), "2".asSimpleParsed(), var1.asParsed(), var2.asParsed()))
  }

  @Test
  fun reloadsDropDownList() {
    val variablesProvider = mock(VariablesProvider::class.java)
    val var1 = "var1" to "val1"
    val var2 = "var2" to "val2"
    `when`(variablesProvider.getAvailableVariablesForType(String::class.java)).thenReturn(
      listOf(
        var1,
        var2
      )
    )
    val editor = simplePropertyEditor(model, property, variablesProvider)
    assertThat(editor.getModel().getItems(),
               hasItems("1".asSimpleParsed(), "2".asSimpleParsed(), var1.asParsed(), var2.asParsed()))

    wellKnownValues = listOf(ValueDescriptor("1", "one"), ValueDescriptor("2", "two"), ValueDescriptor("3", "three"))
    editor.commitTestText("2")
    editor.simulateEditorGotFocus()

    assertThat(editor.getModel().getItems(),
               hasItems("1".asSimpleParsed(), "2".asSimpleParsed(), "3".asSimpleParsed(), var1.asParsed(), var2.asParsed()))
    assertThat(parsedModel.value, equalTo("2"))
    assertThat<Any?>(editor.selectedItem, equalTo("2".asSimpleParsed()))

    wellKnownValues = listOf(ValueDescriptor("1", "one"), ValueDescriptor("3", "three"))
    editor.simulateEditorGotFocus()

    assertThat(editor.getModel().getItems(),
               hasItems("1".asSimpleParsed(), "3".asSimpleParsed(), var1.asParsed(), var2.asParsed()))
    assertThat(parsedModel.value, equalTo("2"))
    assertThat<Any?>(editor.selectedItem, equalTo("2".asSimpleParsed()))
  }

  @Test
  fun updatesValue() {
    val editor = simplePropertyEditor(model, property)
    editor.commitTestText("abc")
    assertThat(parsedModel.value, equalTo("abc"))
    // TODO(b/73811870): Assert the status message was updated correctly.
    assertThat(editor.testPlainTextStatus, equalTo(" -> value"))
    assertThat<Any?>(editor.selectedItem, equalTo("abc".asSimpleParsed()))
  }

  @Test
  fun updatesToNullValue() {
    val editor = simplePropertyEditor(model, property)
    editor.commitTestText("")
    assertThat<Any?>(parsedModel.value, nullValue())
    // TODO(b/73811870): Assert the status message was updated correctly.
    assertThat(editor.testPlainTextStatus, equalTo(" -> value"))
    assertThat<Any?>(editor.selectedItem, equalTo(ParsedValue.NotSet))
  }

  @Test
  fun updatesToReference() {
    val editor = simplePropertyEditor(model, property)
    val refValue = "value"
    translateDsl["other.reference"] = refValue
    editor.commitTestText("\$other.reference")
    assertThat(parsedModel.value, equalTo(refValue))
    assertThat(parsedModel.dsl?.mode, equalTo(DslMode.REFERENCE))
    assertThat(parsedModel.dsl?.text, equalTo("other.reference"))
    // TODO(b/73811870): Assert the status message was updated correctly.
    assertThat(editor.testPlainTextStatus, equalTo(""))
    assertThat<Any?>(editor.selectedItem,
                     equalTo(ParsedValue.Set.Parsed(value = refValue, dslText = DslText(DslMode.REFERENCE, "other.reference"))))
  }

  @Test
  fun updatesToInterpolatedString() {
    val editor = simplePropertyEditor(model, property)
    // TODO(b/72088238): Decide on the exact format.
    val interpolatedValue = "AAA and BBB"
    translateDsl["\$a and \$b"] = interpolatedValue
    editor.commitTestText("\"\$a and \$b\"")
    assertThat(parsedModel.value, equalTo(interpolatedValue))
    assertThat(parsedModel.dsl?.mode, equalTo(DslMode.INTERPOLATED_STRING))
    assertThat(parsedModel.dsl?.text, equalTo("\$a and \$b"))
    // TODO(b/73811870): Assert the status message was updated correctly.
    assertThat(editor.testPlainTextStatus, equalTo(" -> value"))
    assertThat<Any?>(editor.selectedItem,
                     equalTo(
                       ParsedValue.Set.Parsed(value = interpolatedValue, dslText = DslText(DslMode.INTERPOLATED_STRING, "\$a and \$b"))))
  }

  @Test
  @Ignore("b/72088462")
  fun updatesToOtherUnresolvedDslText() {
    // TODO(b/72088462): Decide what the expectations are.
  }


  @Test
  fun updateProperty() {
    val editor = simplePropertyEditor(model, property)
    editor.setTestText("abc")
    // Our assumption is that changing the text editor content directly does not immediately raise the notification and thus it is possible
    // to test updateProperty() method.
    assumeThat(parsedModel.value, equalTo("value"))

    editor.updateProperty()
    assertThat(parsedModel.value, equalTo("abc"))
  }

  @Test
  fun getValue() {
    val editor = simplePropertyEditor(model, property)
    editor.setTestText("abc")
    // Our assumption is that changing the text editor content directly does not immediately raise the notification and thus it is possible
    // to test updateProperty() method.
    assumeThat(parsedModel.value, equalTo("value"))

    assertThat(editor.getValueText(), equalTo("abc"))
    assertThat((editor.getValue() as ParsedValue.Set.Parsed).dslText, nullValue())
  }

  @Test
  fun dispose() {
    val editor = simplePropertyEditor(model, property)
    editor.dispose()
    editor.commitTestText("abc")
    assertThat(parsedModel.value, equalTo("value"))
    assertThat(editor.testPlainTextStatus, equalTo(""))
  }

  @Test
  fun handlesInvalidInput() {
    val editor = simplePropertyEditor(model, property)
    editor.commitTestText("invalid")  // "invalid" is recognised as an invalid input by the test parser.
    assertThat(parsedModel.value, nullValue())
    assertThat(parsedModel.dsl, equalTo(DslText(DslMode.OTHER_UNPARSED_DSL_TEXT, "invalid")))
  }
}

private fun SimplePropertyEditor<*, *, *>.simulateEditorGotFocus() {
  // Directly invoke the action the editor performs on receiving the focus since the detached component cannot be focused.
  loadKnownValues()
  reloadValue()
}

private val spacesMatcher = Regex("\\s+")
private val HtmlLabel.normalizedPlainText: String get() = document.getText(0, document.length).replace(spacesMatcher, " ")
private val SimplePropertyEditor<*, *, *>.testPlainTextStatus: String
  get() = (this.statusComponent as? HtmlLabel)?.normalizedPlainText.orEmpty()

private fun <T> ListModel<T>.getItems(): List<T> {
  val result = mutableListOf<T>()
  for (i in 0 until size) {
    result.add(getElementAt(i))
  }
  return result.toList()
}

private fun String.asSimpleParsed(): ParsedValue<String> = ParsedValue.Set.Parsed(value = this)
private fun String.asParsed(): ParsedValue<String> = ParsedValue.Set.Parsed(value = this, dslText = DslText(DslMode.LITERAL, this))
private fun <T : Any> Pair<String, T>.asParsed() = ParsedValue.Set.Parsed(dslText = DslText(DslMode.REFERENCE, first), value = second)
private fun SimplePropertyEditor<*, *, *>.setTestText(text: String) {
  (getEditor().editorComponent as JTextComponent).text = text
}

private fun SimplePropertyEditor<*, *, *>.commitTestText(text: String) {
  setTestText(text)
  actionPerformed(ActionEvent(getEditor(), 0, null))
}
