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
import com.google.common.util.concurrent.Futures.immediateFuture
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.intellij.idea.IdeaTestApplication
import com.intellij.testFramework.UsefulTestCase
import com.intellij.ui.components.JBTextField
import org.hamcrest.CoreMatchers.*
import org.junit.Assert.assertThat
import org.junit.Assume.assumeThat
import org.junit.Ignore
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.awt.event.ActionEvent
import javax.swing.ListModel
import javax.swing.plaf.basic.BasicComboBoxEditor
import javax.swing.text.JTextComponent

class SimplePropertyEditorTest  : UsefulTestCase() {

  class Model {
    var modified: Boolean = false
  }

  class ParsedModel {
    var setParsedValueCalls = 0
    var setParsedRawValueCalls = 0
    var value: String? = "value"
    var dsl: DslText? = DslText.Literal
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
  private var translateDsl = mutableMapOf<DslText, String>()
  private var wellKnownValuesFuture: ListenableFuture<List<ValueDescriptor<String>>> =
    immediateFuture(listOf(ValueDescriptor("1", "one"), ValueDescriptor("2", "two")))

  private val property get() = ModelSimplePropertyImpl(
    modelDescriptor,
    "Description",
    defaultValueGetter = defaultValue?.let { { _ : Model -> it } },
    getResolvedValue = { value },
    getParsedValue = { value },
    getParsedRawValue = { dsl ?: if (value != null) DslText.Literal else null },
    setParsedValue = {
      setParsedValueCalls++
      value = it; dsl = it?.let { DslText.Literal }
    },
    setParsedRawValue = {
      setParsedValueCalls++
      value = translateDsl[it]; dsl = it;
    },
    parser = { _: Nothing?, it ->
      when {
        it.isEmpty() -> ParsedValue.NotSet
        it == "invalid" -> ParsedValue.Set.Invalid("invalid", "invalid text message")
        else -> ParsedValue.Set.Parsed(it, DslText.Literal)
      }
    },
    formatter = { _, value -> value },
    knownValuesGetter = { _: Nothing?, _ -> wellKnownValuesFuture },
    variableMatchingStrategy = VariableMatchingStrategy.WELL_KNOWN_VALUE)

  override fun setUp() {
    super.setUp()
    IdeaTestApplication.getInstance()
  }

  fun testLoadsValue() {
    val editor = simplePropertyEditor(property.bind(model), property.bindContext(null, model))
    assertThat<Any?>(editor.selectedItem, equalTo("value".asSimpleParsed()))
    assertThat(editor.testPlainTextStatus, equalTo(""))
  }

  fun testLoadsValueNotMatchingResolved() {
    resolvedModel.value = "other"
    defaultValue = null  // It should not matter whether it is set ot not. Make sure that if it is not set we still report the difference.
    val editor = simplePropertyEditor(property.bind(model), property.bindContext(null, model))
    assertThat<Any?>(editor.selectedItem, equalTo("value".asSimpleParsed()))
    assertThat(editor.testPlainTextStatus, equalTo(" -> other"))
  }

  fun testLoadsWellKnownValue() {
    parsedModel.dsl = DslText.Literal
    parsedModel.value = "1"
    resolvedModel.value = "1"
    val editor = simplePropertyEditor(property.bind(model), property.bindContext(null, model))
    assertThat<Any?>(editor.selectedItem, equalTo("1".asSimpleParsed()))
    assertThat(editor.testPlainTextStatus, equalTo(""))
  }

  fun testLoadsDelayedWellKnownValue() {
    parsedModel.dsl = DslText.Literal
    parsedModel.value = "1"
    resolvedModel.value = "1"
    val values = wellKnownValuesFuture.get() // Capture default test values.
    val settableWellKnownValuesFuture = SettableFuture.create<List<ValueDescriptor<String>>>()
    wellKnownValuesFuture = settableWellKnownValuesFuture
    val editor = simplePropertyEditor(property.bind(model), property.bindContext(null, model))
    assertThat(editor.testWatermark(), equalTo("1"))
    assertThat<Any?>(editor.selectedItem, equalTo("1".asSimpleParsed()))
    assertThat(editor.testPlainTextStatus, equalTo(""))
    assertThat(parsedModel.setParsedRawValueCalls, equalTo(0))
    assertThat(parsedModel.setParsedValueCalls, equalTo(0))
    settableWellKnownValuesFuture.set(values)
    assertThat(editor.testWatermark(), equalTo("1 (one)"))
    assertThat(parsedModel.setParsedRawValueCalls, equalTo(0))
    assertThat(parsedModel.setParsedValueCalls, equalTo(0))
  }

  fun testLoadsNotSetValue() {
    parsedModel.dsl = null
    parsedModel.value = null
    resolvedModel.value = null
    val editor = simplePropertyEditor(property.bind(model), property.bindContext(null, model))
    assertThat<Any?>(editor.selectedItem, equalTo(ParsedValue.NotSet))
    assertThat(editor.testPlainTextStatus, equalTo(""))
  }

  fun testLoadsNotSetValue_resolved() {
    parsedModel.dsl = null
    parsedModel.value = null
    resolvedModel.value = "default"  // Matches the "default" value.
    val editor = simplePropertyEditor(property.bind(model), property.bindContext(null, model))
    assertThat<Any?>(editor.selectedItem, equalTo(ParsedValue.NotSet))
    assertThat(editor.testPlainTextStatus, equalTo(""))
  }

  fun testLoadsNotSetValue_resolvedNonDefault() {
    parsedModel.dsl = null
    parsedModel.value = null
    resolvedModel.value = "resolved"
    val editor = simplePropertyEditor(property.bind(model), property.bindContext(null, model))
    assertThat<Any?>(editor.selectedItem, equalTo(ParsedValue.NotSet))
    assertThat(editor.testPlainTextStatus, equalTo(" -> resolved"))
  }

  fun testLoadsNotSetValue_noDefault_resolved() {
    parsedModel.dsl = null
    parsedModel.value = null
    resolvedModel.value = "resolved"
    defaultValue = null
    val editor = simplePropertyEditor(property.bind(model), property.bindContext(null, model))
    assertThat<Any?>(editor.selectedItem, equalTo(ParsedValue.NotSet))
    assertThat(editor.testPlainTextStatus, equalTo(""))
  }

  fun testLoadsReference() {
    parsedModel.dsl = DslText.Reference("some_reference")
    val editor = simplePropertyEditor(property.bind(model), property.bindContext(null, model))
    assertThat<Any?>(editor.selectedItem, equalTo(("some_reference" to "value").asParsed()))
    assertThat(editor.testPlainTextStatus, equalTo(""))
  }

  fun testLoadsReferenceNotMatchingResolved() {
    resolvedModel.value = "other"
    parsedModel.dsl = DslText.Reference("some_reference")
    val editor = simplePropertyEditor(property.bind(model), property.bindContext(null, model))
    assertThat<Any?>(editor.selectedItem, equalTo(("some_reference" to "value").asParsed()))
    assertThat(editor.testPlainTextStatus, equalTo(" -> other"))
  }

  fun testLoadsReferenceResolvedIntoKnownValue() {
    parsedModel.dsl = DslText.Reference("some_reference")
    parsedModel.value = "1"
    resolvedModel.value = "1"
    val editor = simplePropertyEditor(property.bind(model), property.bindContext(null, model))
    assertThat<Any?>(editor.selectedItem, equalTo(("some_reference" to "1").asParsed()))
    assertThat(editor.testPlainTextStatus, equalTo(""))
  }

  fun testLoadsInterpolatedString() {
    parsedModel.dsl = DslText.InterpolatedString("some \${reference}")
    parsedModel.value = "some value"
    resolvedModel.value = "some value"
    val editor = simplePropertyEditor(property.bind(model), property.bindContext(null, model))
    assertThat<Any?>(editor.selectedItem, equalTo(
      ParsedValue.Set.Parsed(value = "some value", dslText = DslText.InterpolatedString("some \${reference}"))))
    assertThat(editor.testPlainTextStatus, equalTo(""))
  }

  fun testLoadsOtherUnparsedDslText() {
    parsedModel.dsl = DslText.OtherUnparsedDslText("1 + z(x)")
    parsedModel.value = null
    val editor = simplePropertyEditor(property.bind(model), property.bindContext(null, model))
    assertThat<Any?>(editor.selectedItem,
                     equalTo(ParsedValue.Set.Parsed(value = null, dslText = DslText.OtherUnparsedDslText("1 + z(x)"))))
    assertThat(editor.testPlainTextStatus, equalTo(" -> value"))
  }

  fun testLoadsDropDownList() {
    val variablesProvider = mock(VariablesProvider::class.java)
    val var1 = "var1" to "1"
    val var2 = "var2" to "2"
    val var3 = "var3" to "3"
    val property = this.property
    val propertyContext = property.bindContext(null, model)
    `when`(variablesProvider.getAvailableVariablesFor(propertyContext)).thenReturn(
      listOf(
        var1.asParsed(),
        var2.asParsed(),
        var3.asParsed()
      )
    )
    val editor = simplePropertyEditor(property.bind(model), propertyContext, variablesProvider)
    assertThat(editor.getModel().getItems(),
               hasItems("1".asSimpleParsed(), "2".asSimpleParsed(), var1.asParsed(), var2.asParsed()))
  }

  fun testReloadsDropDownList() {
    val variablesProvider = mock(VariablesProvider::class.java)
    val var1 = "var1" to "1"
    val var2 = "var2" to "2"
    val var3 = "var3" to "3"
    val property = this.property
    val propertyContext = property.bindContext(null, model)
    `when`(variablesProvider.getAvailableVariablesFor(propertyContext)).thenReturn(
      listOf(
        var1.asParsed(),
        var2.asParsed(),
        var3.asParsed()
      )
    )
    val editor = simplePropertyEditor(property.bind(model), propertyContext, variablesProvider)
    assertThat(editor.getModel().getItems(),
               hasItems("1".asSimpleParsed(), "2".asSimpleParsed(), var1.asParsed(), var2.asParsed()))

    wellKnownValuesFuture = immediateFuture(listOf(ValueDescriptor("1", "one"), ValueDescriptor("2", "two"), ValueDescriptor("3", "three")))
    editor.commitTestText("2")
    editor.simulateEditorGotFocus()

    assertThat(editor.getModel().getItems(),
               hasItems("1".asSimpleParsed(), "2".asSimpleParsed(), "3".asSimpleParsed(),
                        var1.asParsed(), var2.asParsed(), var3.asParsed()))
    assertThat(parsedModel.value, equalTo("2"))
    assertThat<Any?>(editor.selectedItem, equalTo("2".asSimpleParsed()))

    wellKnownValuesFuture = immediateFuture(listOf(ValueDescriptor("1", "one"), ValueDescriptor("3", "three")))
    editor.simulateEditorGotFocus()

    assertThat(editor.getModel().getItems(),
               hasItems("1".asSimpleParsed(), "3".asSimpleParsed(), var1.asParsed(), var3.asParsed()))
    assertThat(parsedModel.value, equalTo("2"))
    assertThat<Any?>(editor.selectedItem, equalTo("2".asSimpleParsed()))
  }

  fun testUpdatesValue() {
    val editor = simplePropertyEditor(property.bind(model), property.bindContext(null, model))
    editor.commitTestText("abc")
    assertThat(parsedModel.value, equalTo("abc"))
    // TODO(b/73811870): Assert the status message was updated correctly.
    assertThat(editor.testPlainTextStatus, equalTo(" -> value"))
    assertThat<Any?>(editor.selectedItem, equalTo("abc".asSimpleParsed()))
  }

  fun testUpdatesToNullValue() {
    val editor = simplePropertyEditor(property.bind(model), property.bindContext(null, model))
    editor.commitTestText("")
    assertThat<Any?>(parsedModel.value, nullValue())
    // TODO(b/73811870): Assert the status message was updated correctly.
    assertThat(editor.testPlainTextStatus, equalTo(" -> value"))
    assertThat<Any?>(editor.selectedItem, equalTo(ParsedValue.NotSet))
  }

  fun testUpdatesToReference() {
    val editor = simplePropertyEditor(property.bind(model), property.bindContext(null, model))
    val refValue = "value"
    translateDsl[DslText.Reference("other.reference")] = refValue
    editor.commitTestText("\$other.reference")
    assertThat(parsedModel.value, equalTo(refValue))
    assertThat<DslText>(parsedModel.dsl, equalTo(DslText.Reference("other.reference")))
    // TODO(b/73811870): Assert the status message was updated correctly.
    assertThat(editor.testPlainTextStatus, equalTo(""))
    assertThat<Any?>(editor.selectedItem,
                     equalTo(ParsedValue.Set.Parsed(value = refValue, dslText = DslText.Reference("other.reference"))))
  }

  fun testUpdatesToInterpolatedString() {
    val editor = simplePropertyEditor(property.bind(model), property.bindContext(null, model))
    // TODO(b/72088238): Decide on the exact format.
    val interpolatedValue = "AAA and BBB"
    translateDsl[DslText.InterpolatedString("\$a and \$b")] = interpolatedValue
    editor.commitTestText("\"\$a and \$b\"")
    assertThat(parsedModel.value, equalTo(interpolatedValue))
    assertThat<DslText>(parsedModel.dsl, equalTo(DslText.InterpolatedString("\$a and \$b")))
    // TODO(b/73811870): Assert the status message was updated correctly.
    assertThat(editor.testPlainTextStatus, equalTo(" -> value"))
    assertThat<Any?>(editor.selectedItem,
                     equalTo(
                       ParsedValue.Set.Parsed(value = interpolatedValue, dslText = DslText.InterpolatedString("\$a and \$b"))))
  }

  @Ignore("b/72088462")
  fun /*test*/UpdatesToOtherUnresolvedDslText() {
    // TODO(b/72088462): Decide what the expectations are.
  }


  fun testUpdateProperty() {
    val editor = simplePropertyEditor(property.bind(model), property.bindContext(null, model))
    editor.setTestText("abc")
    // Our assumption is that changing the text editor content directly does not immediately raise the notification and thus it is possible
    // to test updateProperty() method.
    assumeThat(parsedModel.value, equalTo("value"))

    editor.updateProperty()
    assertThat(parsedModel.value, equalTo("abc"))
  }

  fun testGetValue() {
    val editor = simplePropertyEditor(property.bind(model), property.bindContext(null, model))
    editor.setTestText("abc")
    // Our assumption is that changing the text editor content directly does not immediately raise the notification and thus it is possible
    // to test updateProperty() method.
    assumeThat(parsedModel.value, equalTo("value"))

    assertThat(editor.toEditorText(editor.getValue()), equalTo("abc"))
    assertThat<DslText>((editor.getValue() as ParsedValue.Set.Parsed).dslText, equalTo(DslText.Literal))
  }

  fun testDispose() {
    val editor = simplePropertyEditor(property.bind(model), property.bindContext(null, model))
    editor.dispose()
    editor.commitTestText("abc")
    assertThat(parsedModel.value, equalTo("value"))
    assertThat(editor.testPlainTextStatus, equalTo(""))
  }

  fun testHandlesInvalidInput() {
    val editor = simplePropertyEditor(property.bind(model), property.bindContext(null, model))
    editor.commitTestText("invalid")  // "invalid" is recognised as an invalid input by the test parser.
    assertThat(parsedModel.value, nullValue())
    assertThat<DslText>(parsedModel.dsl, equalTo(DslText.OtherUnparsedDslText("invalid")))
  }
}

private fun SimplePropertyEditor<*, *>.simulateEditorGotFocus() {
  // Directly invoke the action the editor performs on receiving the focus since the detached component cannot be focused.
  loadKnownValues()
  reloadValue()
}

private val spacesMatcher = Regex("\\s+")
private val HtmlLabel.normalizedPlainText: String get() = document.getText(0, document.length).replace(spacesMatcher, " ")
private val SimplePropertyEditor<*, *>.testPlainTextStatus: String
  get() = (this.statusComponent as? HtmlLabel)?.normalizedPlainText.orEmpty()

private fun <T> ListModel<T>.getItems(): List<T> {
  val result = mutableListOf<T>()
  for (i in 0 until size) {
    result.add(getElementAt(i))
  }
  return result.toList()
}

private fun String.asSimpleParsed(): ParsedValue<String> = ParsedValue.Set.Parsed(this, DslText.Literal)
private fun String.asParsed(): ParsedValue<String> = ParsedValue.Set.Parsed(value = this, dslText = DslText.Literal)
private fun <T : Any> Pair<String, T>.asParsed() = ParsedValue.Set.Parsed(dslText = DslText.Reference(first), value = second)
private fun SimplePropertyEditor<*, *>.setTestText(text: String) {
  (getEditor().editorComponent as JTextComponent).text = text
}

private fun SimplePropertyEditor<*, *>.commitTestText(text: String) {
  setTestText(text)
  actionPerformed(ActionEvent(getEditor(), 0, null))
}

private fun SimplePropertyEditor<*, *>.testWatermark(): String? =
  ((this.getEditor() as? BasicComboBoxEditor)?.editorComponent as? JBTextField)?.emptyText?.text