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
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.hasItems
import org.junit.Assert.assertThat
import org.junit.Assume.assumeThat
import org.junit.Ignore
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.awt.event.ActionEvent
import javax.swing.ListModel
import javax.swing.plaf.basic.BasicComboBoxEditor
import javax.swing.text.JTextComponent
import kotlin.reflect.KProperty

class SimplePropertyEditorTest : UsefulTestCase() {

  class Model {
    var modified: Boolean = false
  }

  class ParsedModel {
    var setParsedValueCalls = 0
    var value: ParsedValue<String> = "value".asParsed()
  }

  class ResolvedModel {
    // Be default the resolved value should match the configured one since this is what we expect from a successful sync.
    var value: String? = "value"
  }

  private val model = Model()
  private val resolvedModel = ResolvedModel()
  private val parsedModel = ParsedModel()

  private var defaultValue: String? = "default"
  private var translateDsl = mutableMapOf<DslText, String>()
  private var wellKnownValuesFuture: ListenableFuture<List<ValueDescriptor<String>>> =
    immediateFuture(listOf(ValueDescriptor("1", "one"), ValueDescriptor("2", "two")))

  private val property
    get() = object : ModelPropertyBase<Nothing?, Model, String>(), ModelSimpleProperty<Nothing?, Model, String> {
      override val parser: (Nothing?, String) -> ParsedValue<String>
        get() = { _, value ->
          when {
            value.isEmpty() -> ParsedValue.NotSet
            value == "invalid" -> ParsedValue.Set.Invalid("invalid", "invalid text message")
            else -> ParsedValue.Set.Parsed(value, DslText.Literal)
          }
        }
      override val formatter: (Nothing?, String) -> String get() = { _, it -> it }
      override val knownValuesGetter: (Nothing?, Model) -> ListenableFuture<List<ValueDescriptor<String>>> get() = { _, _ -> wellKnownValuesFuture }
      override val description: String = "Description"

      override fun bind(model: Model): ModelPropertyCore<String> = object : ModelPropertyCore<String> {
        override fun getParsedValue(): ParsedValue<String> = parsedModel.value

        override fun setParsedValue(value: ParsedValue<String>) {
          parsedModel.value = when {
            value is ParsedValue.Set.Parsed &&
            (value.dslText is DslText.Reference || value.dslText is DslText.InterpolatedString) ->
              ParsedValue.Set.Parsed(translateDsl[value.dslText], value.dslText)
            else -> value
          }
          parsedModel.setParsedValueCalls++
        }

        override fun getResolvedValue(): ResolvedValue<String> = if (resolvedModel.value != null) ResolvedValue.Set(
          resolvedModel.value)
        else ResolvedValue.NotResolved()

        override val defaultValueGetter: (() -> String?)? get() = defaultValue?.let { { it } }

      }

      override fun getValue(thisRef: Model, property: KProperty<*>): ParsedValue<String> = throw UnsupportedOperationException()

      override fun setValue(thisRef: Model, property: KProperty<*>, value: ParsedValue<String>) = throw UnsupportedOperationException()
    }

  override fun setUp() {
    super.setUp()
    IdeaTestApplication.getInstance()
  }

  fun testLoadsValue() {
    val editor = simplePropertyEditor(property.bind(model), property.bindContext(null, model))
    assertThat<Any?>(editor.selectedItem, equalTo("value".asParsed()))
    assertThat(editor.testPlainTextStatus, equalTo(""))
  }

  fun testLoadsValueNotMatchingResolved() {
    resolvedModel.value = "other"
    defaultValue = null  // It should not matter whether it is set ot not. Make sure that if it is not set we still report the difference.
    val editor = simplePropertyEditor(property.bind(model), property.bindContext(null, model))
    assertThat<Any?>(editor.selectedItem, equalTo("value".asParsed()))
    assertThat(editor.testPlainTextStatus, equalTo(" -> other"))
  }

  fun testLoadsWellKnownValue() {
    parsedModel.value = "1".asParsed()
    resolvedModel.value = "1"
    val editor = simplePropertyEditor(property.bind(model), property.bindContext(null, model))
    assertThat<Any?>(editor.selectedItem, equalTo("1".asParsed()))
    assertThat(editor.testPlainTextStatus, equalTo(""))
  }

  fun testLoadsDelayedWellKnownValue() {
    parsedModel.value = "1".asParsed()
    resolvedModel.value = "1"
    val values = wellKnownValuesFuture.get() // Capture default test values.
    val settableWellKnownValuesFuture = SettableFuture.create<List<ValueDescriptor<String>>>()
    wellKnownValuesFuture = settableWellKnownValuesFuture
    val editor = simplePropertyEditor(property.bind(model), property.bindContext(null, model))
    assertThat(editor.testWatermark(), equalTo("1"))
    assertThat<Any?>(editor.selectedItem, equalTo("1".asParsed()))
    assertThat(editor.testPlainTextStatus, equalTo(""))
    assertThat(parsedModel.setParsedValueCalls, equalTo(0))
    settableWellKnownValuesFuture.set(values)
    assertThat(editor.testWatermark(), equalTo("1 (one)"))
    assertThat(parsedModel.setParsedValueCalls, equalTo(0))
  }

  fun testLoadsNotSetValue() {
    parsedModel.value = ParsedValue.NotSet
    resolvedModel.value = null
    val editor = simplePropertyEditor(property.bind(model), property.bindContext(null, model))
    assertThat<Any?>(editor.selectedItem, equalTo(ParsedValue.NotSet))
    assertThat(editor.testPlainTextStatus, equalTo(""))
  }

  fun testLoadsNotSetValue_resolved() {
    parsedModel.value = ParsedValue.NotSet
    resolvedModel.value = "default"  // Matches the "default" value.
    val editor = simplePropertyEditor(property.bind(model), property.bindContext(null, model))
    assertThat<Any?>(editor.selectedItem, equalTo(ParsedValue.NotSet))
    assertThat(editor.testPlainTextStatus, equalTo(""))
  }

  fun testLoadsNotSetValue_resolvedNonDefault() {
    parsedModel.value = ParsedValue.NotSet
    resolvedModel.value = "resolved"
    val editor = simplePropertyEditor(property.bind(model), property.bindContext(null, model))
    assertThat<Any?>(editor.selectedItem, equalTo(ParsedValue.NotSet))
    assertThat(editor.testPlainTextStatus, equalTo(" -> resolved"))
  }

  fun testLoadsNotSetValue_noDefault_resolved() {
    parsedModel.value = ParsedValue.NotSet
    resolvedModel.value = "resolved"
    defaultValue = null
    val editor = simplePropertyEditor(property.bind(model), property.bindContext(null, model))
    assertThat<Any?>(editor.selectedItem, equalTo(ParsedValue.NotSet))
    assertThat(editor.testPlainTextStatus, equalTo(""))
  }

  fun testLoadsReference() {
    parsedModel.value = ParsedValue.Set.Parsed("value", DslText.Reference("some_reference"))
    val editor = simplePropertyEditor(property.bind(model), property.bindContext(null, model))
    assertThat<Any?>(editor.selectedItem, equalTo(("some_reference" to "value").asParsed()))
    assertThat(editor.testPlainTextStatus, equalTo(""))
  }

  fun testLoadsReferenceNotMatchingResolved() {
    resolvedModel.value = "other"
    parsedModel.value = ParsedValue.Set.Parsed("value", DslText.Reference("some_reference"))
    val editor = simplePropertyEditor(property.bind(model), property.bindContext(null, model))
    assertThat<Any?>(editor.selectedItem, equalTo(("some_reference" to "value").asParsed()))
    assertThat(editor.testPlainTextStatus, equalTo(" -> other"))
  }

  fun testLoadsReferenceResolvedIntoKnownValue() {
    parsedModel.value = ParsedValue.Set.Parsed("1", DslText.Reference("some_reference"))
    resolvedModel.value = "1"
    val editor = simplePropertyEditor(property.bind(model), property.bindContext(null, model))
    assertThat<Any?>(editor.selectedItem, equalTo(("some_reference" to "1").asParsed()))
    assertThat(editor.testPlainTextStatus, equalTo(""))
  }

  fun testLoadsInterpolatedString() {
    parsedModel.value = ParsedValue.Set.Parsed("some value", DslText.InterpolatedString("some \${reference}"))
    resolvedModel.value = "some value"
    val editor = simplePropertyEditor(property.bind(model), property.bindContext(null, model))
    assertThat<Any?>(editor.selectedItem, equalTo(
      ParsedValue.Set.Parsed(value = "some value", dslText = DslText.InterpolatedString("some \${reference}"))))
    assertThat(editor.testPlainTextStatus, equalTo(""))
  }

  fun testLoadsOtherUnparsedDslText() {
    parsedModel.value = ParsedValue.Set.Parsed(null, DslText.OtherUnparsedDslText("1 + z(x)"))
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
               hasItems("1".asParsed(), "2".asParsed(), var1.asParsed(), var2.asParsed()))
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
               hasItems("1".asParsed(), "2".asParsed(), var1.asParsed(), var2.asParsed()))

    wellKnownValuesFuture = immediateFuture(listOf(ValueDescriptor("1", "one"), ValueDescriptor("2", "two"), ValueDescriptor("3", "three")))
    editor.commitTestText("2")
    editor.reload()

    assertThat(editor.getModel().getItems(),
               hasItems("1".asParsed(), "2".asParsed(), "3".asParsed(),
                        var1.asParsed(), var2.asParsed(), var3.asParsed()))
    assertThat(parsedModel.value, equalTo("2".asParsed()))
    assertThat<Any?>(editor.selectedItem, equalTo("2".asParsed()))

    wellKnownValuesFuture = immediateFuture(listOf(ValueDescriptor("1", "one"), ValueDescriptor("3", "three")))
    editor.reload()

    assertThat(editor.getModel().getItems(),
               hasItems("1".asParsed(), "3".asParsed(), var1.asParsed(), var3.asParsed()))
    assertThat(parsedModel.value, equalTo("2".asParsed()))
    assertThat<Any?>(editor.selectedItem, equalTo("2".asParsed()))
  }

  fun testUpdatesValue() {
    val editor = simplePropertyEditor(property.bind(model), property.bindContext(null, model))
    editor.commitTestText("abc")
    assertThat(parsedModel.value, equalTo("abc".asParsed()))
    // TODO(b/73811870): Assert the status message was updated correctly.
    assertThat(editor.testPlainTextStatus, equalTo(" -> value"))
    assertThat<Any?>(editor.selectedItem, equalTo("abc".asParsed()))
  }

  fun testUpdatesToNullValue() {
    val editor = simplePropertyEditor(property.bind(model), property.bindContext(null, model))
    editor.commitTestText("")
    assertThat<ParsedValue<String>>(parsedModel.value, equalTo(ParsedValue.NotSet))
    // TODO(b/73811870): Assert the status message was updated correctly.
    assertThat(editor.testPlainTextStatus, equalTo(" -> value"))
    assertThat<Any?>(editor.selectedItem, equalTo(ParsedValue.NotSet))
  }

  fun testUpdatesToReference() {
    val editor = simplePropertyEditor(property.bind(model), property.bindContext(null, model))
    val refValue = "value"
    translateDsl[DslText.Reference("other.reference")] = refValue
    editor.commitTestText("\$other.reference")
    assertThat<ParsedValue<String>>(parsedModel.value, equalTo(ParsedValue.Set.Parsed(refValue, DslText.Reference("other.reference"))))
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
    assertThat<ParsedValue<String>>(parsedModel.value,
                                    equalTo(ParsedValue.Set.Parsed(interpolatedValue, DslText.InterpolatedString("\$a and \$b"))))
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
    assumeThat(parsedModel.value, equalTo("value".asParsed()))

    editor.updateProperty()
    assertThat(parsedModel.value, equalTo("abc".asParsed()))
  }

  fun testGetValue() {
    val editor = simplePropertyEditor(property.bind(model), property.bindContext(null, model))
    editor.setTestText("abc")
    // Our assumption is that changing the text editor content directly does not immediately raise the notification and thus it is possible
    // to test updateProperty() method.
    assumeThat(parsedModel.value, equalTo("value".asParsed()))

    assertThat(editor.component.toEditorText(editor.getValue()), equalTo("abc"))
    assertThat<DslText>((editor.getValue() as ParsedValue.Set.Parsed).dslText, equalTo(DslText.Literal))
  }

  fun testDispose() {
    val editor = simplePropertyEditor(property.bind(model), property.bindContext(null, model))
    editor.dispose()
    editor.commitTestText("abc")
    assertThat(parsedModel.value, equalTo("value".asParsed()))
    assertThat(editor.testPlainTextStatus, equalTo(""))
  }

  fun testHandlesInvalidInput() {
    val editor = simplePropertyEditor(property.bind(model), property.bindContext(null, model))
    editor.commitTestText("invalid")  // "invalid" is recognised as an invalid input by the test parser.
    assertThat<ParsedValue<String>>(parsedModel.value, equalTo(ParsedValue.Set.Parsed(null, DslText.OtherUnparsedDslText("invalid"))))
  }
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

private fun String.asParsed(): ParsedValue<String> = ParsedValue.Set.Parsed(value = this, dslText = DslText.Literal)
private fun <T : Any> Pair<String, T>.asParsed() = ParsedValue.Set.Parsed(dslText = DslText.Reference(first), value = second)
private val SimplePropertyEditor<*, *>.selectedItem get() = component.selectedItem
private fun <T : Any> SimplePropertyEditor<T, *>.getModel() = component.model
private fun SimplePropertyEditor<*, *>.setTestText(text: String) {
  (component.editor.editorComponent as JTextComponent).text = text
}

private fun SimplePropertyEditor<*, *>.commitTestText(text: String) {
  setTestText(text)
  component.actionPerformed(ActionEvent(component.editor, 0, null))
}

private fun SimplePropertyEditor<*, *>.testWatermark(): String? =
  ((component.editor as? BasicComboBoxEditor)?.editorComponent as? JBTextField)?.emptyText?.text