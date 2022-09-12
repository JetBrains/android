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

import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.gradle.structure.model.PsVariablesScope
import com.android.tools.idea.gradle.structure.model.meta.Annotated
import com.android.tools.idea.gradle.structure.model.meta.DslText
import com.android.tools.idea.gradle.structure.model.meta.ModelPropertyBase
import com.android.tools.idea.gradle.structure.model.meta.ModelPropertyCore
import com.android.tools.idea.gradle.structure.model.meta.ModelSimpleProperty
import com.android.tools.idea.gradle.structure.model.meta.ParsedValue
import com.android.tools.idea.gradle.structure.model.meta.ResolvedValue
import com.android.tools.idea.gradle.structure.model.meta.ValueAnnotation
import com.android.tools.idea.gradle.structure.model.meta.ValueDescriptor
import com.android.tools.idea.gradle.structure.model.meta.annotateParsedResolvedMismatchBy
import com.android.tools.idea.gradle.structure.model.meta.annotateWithError
import com.android.tools.idea.gradle.structure.model.meta.annotated
import com.google.common.util.concurrent.Futures.immediateFuture
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.intellij.testFramework.TestApplicationManager
import com.intellij.testFramework.UsefulTestCase
import com.intellij.ui.components.JBTextField
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.hasItems
import org.junit.Assert.assertThat
import org.junit.Assume.assumeThat
import org.mockito.Mockito.mock
import java.awt.event.ActionEvent
import javax.swing.ListModel
import javax.swing.text.JTextComponent
import kotlin.reflect.KProperty

class SimplePropertyEditorTest : UsefulTestCase() {

  class Model {
    var modified: Boolean = false
  }

  class ParsedModel {
    var setParsedValueCalls = 0
    var value: Annotated<ParsedValue<String>> = "value".asAnnotatedParsed()
  }

  class ResolvedModel {
    // Be default the resolved value should match the configured one since this is what we expect from a successful sync.
    var value: String? = "value"
  }

  private val model = Model()
  private val resolvedModel = ResolvedModel()
  private val parsedModel = ParsedModel()

  private var defaultValue: String? = "default"
  private var translateDsl = mutableMapOf<DslText, Annotated<ParsedValue<String>>>()
  private var wellKnownValuesFuture: ListenableFuture<List<ValueDescriptor<String>>> =
    immediateFuture(listOf(ValueDescriptor("1", "one"), ValueDescriptor("2", "two"),
                           ValueDescriptor(ParsedValue.Set.Parsed(null, DslText.Reference("well_known_reference")))))

  private val invalidValueParsed = ParsedValue.Set.Parsed("invalid", DslText.Literal)
                .annotateWithError("invalid text message")

  private val property
    get() = object : ModelPropertyBase<Model, String>(), ModelSimpleProperty<Model, String> {
      override val parser: (String) -> Annotated<ParsedValue<String>>
        get() = { value ->
          when {
            value.isEmpty() -> ParsedValue.NotSet.annotated()
            value == "invalid" -> invalidValueParsed
            else -> ParsedValue.Set.Parsed(value, DslText.Literal).annotated()
          }
        }
      override val formatter: (String) -> String get() = { it }
      override val knownValuesGetter: (Model) -> ListenableFuture<List<ValueDescriptor<String>>> get() = { wellKnownValuesFuture }
      override val description: String = "Description"

      override fun bind(model: Model): ModelPropertyCore<String> = object : ModelPropertyCore<String> {
        override val description: String = "Description"
        override fun getParsedValue(): Annotated<ParsedValue<String>> = parsedModel.value

        override fun setParsedValue(value: ParsedValue<String>) {
          parsedModel.value = when {
            value is ParsedValue.Set.Parsed &&
            (value.dslText is DslText.Reference || value.dslText is DslText.InterpolatedString) ->
              translateDsl[value.dslText] ?: value.annotateWithError("translateDsl[\"${value.dslText}\"] is not configured for the test.")
            else -> value.annotated()
          }
          parsedModel.setParsedValueCalls++
        }

        override fun getResolvedValue(): ResolvedValue<String> =
          if (resolvedModel.value != null) ResolvedValue.Set(resolvedModel.value) else ResolvedValue.NotResolved()

        override val defaultValueGetter: (() -> String?)? get() = defaultValue?.let { { it } }
        override val variableScope: (() -> PsVariablesScope?)? get() = { null }
        override fun annotateParsedResolvedMismatch(): ValueAnnotation? =
          annotateParsedResolvedMismatchBy { parsed, resolved -> parsed == resolved }

        override val isModified: Boolean? get() = false
      }

      override fun getValue(thisRef: Model, property: KProperty<*>): ParsedValue<String> = throw UnsupportedOperationException()

      override fun setValue(thisRef: Model, property: KProperty<*>, value: ParsedValue<String>) = throw UnsupportedOperationException()
    }

  override fun setUp() {
    super.setUp()
    TestApplicationManager.getInstance()
  }

  fun testLabel() {
    val editor = SimplePropertyEditor(property.bind(model), property.bindContext(model), null, listOf())
    assertThat(editor.labelComponent.text, equalTo("Description"))
  }

  fun testLoadsValue() {
    val editor = SimplePropertyEditor(property.bind(model), property.bindContext(model), null, listOf())
    assertThat(editor.selectedItem, equalTo("value".asAnnotatedParsed()))
    assertThat(editor.testPlainTextStatus, equalTo(""))
  }

  fun testReloadsValue() {
    val editor = SimplePropertyEditor(property.bind(model), property.bindContext(model), null, listOf())
    assertThat(editor.selectedItem, equalTo("value".asAnnotatedParsed()))
    assertThat(editor.testPlainTextStatus, equalTo(""))
    parsedModel.value = "value1".asAnnotatedParsed()
    editor.reload()
    assertThat(editor.selectedItem, equalTo("value1".asAnnotatedParsed()))
    assertThat(editor.testPlainTextStatus, equalTo("Resolved: value"))
    editor.setTestText("value")
    parsedModel.value = "value".asAnnotatedParsed()
    // Make sure the editor remains modified.
    assertThat(editor.selectedItem, equalTo("value1".asAnnotatedParsed()))
    // Ensure the editor reloads even when modified.
    editor.reload()
    assertThat(editor.selectedItem, equalTo("value".asAnnotatedParsed()))
    assertThat(editor.testPlainTextStatus, equalTo(""))
  }

  fun testLoadsValueNotMatchingResolved() {
    resolvedModel.value = "other"
    defaultValue = null  // It should not matter whether it is set ot not. Make sure that if it is not set we still report the difference.
    val editor = SimplePropertyEditor(property.bind(model), property.bindContext(model), null, listOf())
    assertThat(editor.selectedItem, equalTo("value".asAnnotatedParsed()))
    assertThat(editor.testPlainTextStatus, equalTo("Resolved: other"))
  }

  fun testLoadsWellKnownValue() {
    parsedModel.value = "1".asAnnotatedParsed()
    resolvedModel.value = "1"
    val editor = SimplePropertyEditor(property.bind(model), property.bindContext(model), null, listOf())
    assertThat(editor.selectedItem, equalTo("1".asAnnotatedParsed()))
    assertThat(editor.testPlainTextStatus, equalTo(""))
  }

  fun testLoadsDelayedWellKnownValue() {
    parsedModel.value = "1".asAnnotatedParsed()
    resolvedModel.value = "1"
    val values = wellKnownValuesFuture.get() // Capture default test values.
    val settableWellKnownValuesFuture = SettableFuture.create<List<ValueDescriptor<String>>>()
    wellKnownValuesFuture = settableWellKnownValuesFuture
    val editor = SimplePropertyEditor(property.bind(model), property.bindContext(model), null, listOf())
    assertThat(editor.testWatermark(), equalTo("1"))
    assertThat(editor.selectedItem, equalTo("1".asAnnotatedParsed()))
    assertThat(editor.testPlainTextStatus, equalTo(""))
    assertThat(parsedModel.setParsedValueCalls, equalTo(0))
    settableWellKnownValuesFuture.set(values)
    assertThat(editor.testWatermark(), equalTo("1 (one)"))
    assertThat(parsedModel.setParsedValueCalls, equalTo(0))
  }

  fun testLoadsNotSetValue() {
    parsedModel.value = ParsedValue.NotSet.annotated()
    resolvedModel.value = null
    val editor = SimplePropertyEditor(property.bind(model), property.bindContext(model), null, listOf())
    assertThat<Any?>(editor.selectedItem, equalTo(ParsedValue.NotSet.annotated()))
    assertThat(editor.testPlainTextStatus, equalTo(""))
  }

  fun testLoadsNotSetValue_resolved() {
    parsedModel.value = ParsedValue.NotSet.annotated()
    resolvedModel.value = "default"  // Matches the "default" value.
    val editor = SimplePropertyEditor(property.bind(model), property.bindContext(model), null, listOf())
    assertThat<Any?>(editor.selectedItem, equalTo(ParsedValue.NotSet.annotated()))
    assertThat(editor.testPlainTextStatus, equalTo(""))
  }

  fun testLoadsNotSetValue_resolvedNonDefault() {
    parsedModel.value = ParsedValue.NotSet.annotated()
    resolvedModel.value = "resolved"
    val editor = SimplePropertyEditor(property.bind(model), property.bindContext(model), null, listOf())
    assertThat<Any?>(editor.selectedItem, equalTo(ParsedValue.NotSet.annotated()))
    assertThat(editor.testPlainTextStatus, equalTo("Resolved: resolved"))
  }

  fun testLoadsNotSetValue_noDefault_resolved() {
    parsedModel.value = ParsedValue.NotSet.annotated()
    resolvedModel.value = "resolved"
    defaultValue = null
    val editor = SimplePropertyEditor(property.bind(model), property.bindContext(model), null, listOf())
    assertThat<Any?>(editor.selectedItem, equalTo(ParsedValue.NotSet.annotated()))
    assertThat(editor.testPlainTextStatus, equalTo(""))
  }

  fun testLoadsReference() {
    parsedModel.value = ParsedValue.Set.Parsed("value", DslText.Reference("some_reference")).annotated()
    val editor = SimplePropertyEditor(property.bind(model), property.bindContext(model), null, listOf())
    assertThat<Any?>(editor.selectedItem, equalTo(("some_reference" to "value").asAnnotatedParsed()))
    assertThat(editor.testPlainTextStatus, equalTo(""))
  }

  fun testLoadsReferenceNotMatchingResolved() {
    resolvedModel.value = "other"
    parsedModel.value = ParsedValue.Set.Parsed("value", DslText.Reference("some_reference")).annotated()
    val editor = SimplePropertyEditor(property.bind(model), property.bindContext(model), null, listOf())
    assertThat<Any?>(editor.selectedItem, equalTo(("some_reference" to "value").asAnnotatedParsed()))
    assertThat(editor.testPlainTextStatus, equalTo("Resolved: other"))
  }

  fun testLoadsReferenceResolvedIntoKnownValue() {
    parsedModel.value = ParsedValue.Set.Parsed("1", DslText.Reference("some_reference")).annotated()
    resolvedModel.value = "1"
    val editor = SimplePropertyEditor(property.bind(model), property.bindContext(model), null, listOf())
    assertThat<Any?>(editor.selectedItem, equalTo(("some_reference" to "1").asAnnotatedParsed()))
    assertThat(editor.testPlainTextStatus, equalTo(""))
  }

  fun testLoadsWellKnownReferenceWithError() {
    val knownReferenceParsedValue = ParsedValue.Set.Parsed(null, DslText.Reference("well_known_reference"))
    parsedModel.value = knownReferenceParsedValue.annotateWithError("error")
    resolvedModel.value = null
    val editor = SimplePropertyEditor(property.bind(model), property.bindContext(model), null, listOf())
    assertThat(editor.testWatermark(), equalTo("\$well_known_reference"))  // No error messages here.

    assertThat(editor.testPlainTextStatus, equalTo(""))
    assertThat<Any?>(editor.selectedItem, equalTo(knownReferenceParsedValue.annotated()))
  }

  fun testLoadsInterpolatedString() {
    parsedModel.value = ParsedValue.Set.Parsed("some value", DslText.InterpolatedString("some \${reference}")).annotated()
    resolvedModel.value = "some value"
    val editor = SimplePropertyEditor(property.bind(model), property.bindContext(model), null, listOf())
    assertThat<Any?>(editor.selectedItem, equalTo(
      ParsedValue.Set.Parsed(value = "some value", dslText = DslText.InterpolatedString("some \${reference}")).annotated()))
    assertThat(editor.testPlainTextStatus, equalTo(""))
  }

  fun testLoadsOtherUnparsedDslText() {
    parsedModel.value = ParsedValue.Set.Parsed(null, DslText.OtherUnparsedDslText("1 + z(x)")).annotated()
    val editor = SimplePropertyEditor(property.bind(model), property.bindContext(model), null, listOf())
    assertThat<Any?>(editor.selectedItem,
                     equalTo(ParsedValue.Set.Parsed(value = null, dslText = DslText.OtherUnparsedDslText("1 + z(x)")).annotated()))
    assertThat(editor.testPlainTextStatus, equalTo("Resolved: value"))
  }

  fun testLoadsDropDownList() {
    val variablesProvider = mock(PsVariablesScope::class.java)
    val var1 = "var1" to "1"
    val var2 = "var2" to "2"
    val var3 = "var3" to "3"
    val property = this.property
    val propertyContext = property.bindContext(model)
    whenever(variablesProvider.getAvailableVariablesFor(propertyContext)).thenReturn(
      listOf(
        var1.asAnnotatedParsed(),
        var2.asAnnotatedParsed(),
        var3.asAnnotatedParsed()
      )
    )
    val editor = SimplePropertyEditor(property.bind(model), propertyContext, variablesProvider, listOf())
    assertThat(editor.getModel().getItems(),
               hasItems("1".asAnnotatedParsed(), "2".asAnnotatedParsed(), var1.asAnnotatedParsed(), var2.asAnnotatedParsed()))
  }

  fun testReloadsDropDownList() {
    val variablesProvider = mock(PsVariablesScope::class.java)
    val var1 = "var1" to "1"
    val var2 = "var2" to "2"
    val var3 = "var3" to "3"
    val property = this.property
    val propertyContext = property.bindContext(model)
    whenever(variablesProvider.getAvailableVariablesFor(propertyContext)).thenReturn(
      listOf(
        var1.asAnnotatedParsed(),
        var2.asAnnotatedParsed(),
        var3.asAnnotatedParsed()
      )
    )
    val editor = SimplePropertyEditor(property.bind(model), propertyContext, variablesProvider, listOf())
    assertThat(editor.getModel().getItems(),
               hasItems("1".asAnnotatedParsed(), "2".asAnnotatedParsed(), var1.asAnnotatedParsed(), var2.asAnnotatedParsed()))

    wellKnownValuesFuture = immediateFuture(listOf(ValueDescriptor("1", "one"), ValueDescriptor("2", "two"), ValueDescriptor("3", "three")))
    editor.commitTestText("2")
    editor.reload()

    assertThat(editor.getModel().getItems(),
               hasItems("1".asAnnotatedParsed(), "2".asAnnotatedParsed(), "3".asAnnotatedParsed(),
                        var1.asAnnotatedParsed(), var2.asAnnotatedParsed(), var3.asAnnotatedParsed()))
    assertThat(parsedModel.value, equalTo("2".asAnnotatedParsed()))
    assertThat(editor.selectedItem, equalTo("2".asAnnotatedParsed()))

    wellKnownValuesFuture = immediateFuture(listOf(ValueDescriptor("1", "one"), ValueDescriptor("3", "three")))
    editor.reload()

    assertThat(editor.getModel().getItems(),
               hasItems("1".asAnnotatedParsed(), "3".asAnnotatedParsed(), var1.asAnnotatedParsed(), var3.asAnnotatedParsed()))
    assertThat(parsedModel.value, equalTo("2".asAnnotatedParsed()))
    assertThat(editor.selectedItem, equalTo("2".asAnnotatedParsed()))
  }

  fun testUpdatesValue() {
    val editor = SimplePropertyEditor(property.bind(model), property.bindContext(model), null, listOf())
    editor.commitTestText("abc")
    assertThat(parsedModel.value, equalTo("abc".asAnnotatedParsed()))
    // TODO(b/73811870): Assert the status message was updated correctly.
    assertThat(editor.testPlainTextStatus, equalTo("Resolved: value"))
    assertThat(editor.selectedItem, equalTo("abc".asAnnotatedParsed()))
  }

  fun testUpdatesToNullValue() {
    val editor = SimplePropertyEditor(property.bind(model), property.bindContext(model), null, listOf())
    editor.commitTestText("")
    assertThat<Annotated<ParsedValue<String>>>(parsedModel.value, equalTo(ParsedValue.NotSet.annotated()))
    // TODO(b/73811870): Assert the status message was updated correctly.
    assertThat(editor.testPlainTextStatus, equalTo("Resolved: value"))
    assertThat<Any?>(editor.selectedItem, equalTo(ParsedValue.NotSet.annotated()))
  }

  fun testUpdatesToReference() {
    val editor = SimplePropertyEditor(property.bind(model), property.bindContext(model), null, listOf())
    val refValue = "value"
    val referenceDslText = DslText.Reference("other.reference")
    translateDsl[referenceDslText] = ParsedValue.Set.Parsed(refValue, referenceDslText).annotated()
    editor.commitTestText("\$other.reference")
    assertThat<Annotated<ParsedValue<String>>>(parsedModel.value,
                                               equalTo(ParsedValue.Set.Parsed(refValue, referenceDslText).annotated()))
    // TODO(b/73811870): Assert the status message was updated correctly.
    assertThat(editor.testPlainTextStatus, equalTo(""))
    assertThat<Any?>(editor.selectedItem,
                     equalTo(ParsedValue.Set.Parsed(value = refValue, dslText = referenceDslText).annotated()))
  }

  fun testUpdatesToInterpolatedString() {
    val editor = SimplePropertyEditor(property.bind(model), property.bindContext(model), null, listOf())
    // TODO(b/72088238): Decide on the exact format.
    val interpolatedValue = "AAA and BBB"
    val dslText = DslText.InterpolatedString("\$a and \$b")
    translateDsl[dslText] = ParsedValue.Set.Parsed(interpolatedValue, dslText).annotated()
    editor.commitTestText("\"\$a and \$b\"")
    assertThat<Annotated<ParsedValue<String>>>(parsedModel.value,
                                               equalTo(
                                                 ParsedValue.Set.Parsed(interpolatedValue,
                                                                        dslText).annotated()))
    // TODO(b/73811870): Assert the status message was updated correctly.
    assertThat(editor.testPlainTextStatus, equalTo("Resolved: value"))
    assertThat<Any?>(editor.selectedItem,
                     equalTo(
                       ParsedValue.Set.Parsed(value = interpolatedValue, dslText = dslText).annotated()))
  }

  // TODO(72088462): Enable this test
  fun /*test*/UpdatesToOtherUnresolvedDslText() {
    // TODO(b/72088462): Decide what the expectations are.
  }


  fun testUpdateProperty() {
    val editor = SimplePropertyEditor(property.bind(model), property.bindContext(model), null, listOf())
    editor.setTestText("abc")
    // Our assumption is that changing the text editor content directly does not immediately raise the notification and thus it is possible
    // to test updateProperty() method.
    assumeThat(parsedModel.value, equalTo("value".asAnnotatedParsed()))

    editor.updateProperty()
    assertThat(parsedModel.value, equalTo("abc".asAnnotatedParsed()))
  }

  fun testGetValue() {
    val editor = SimplePropertyEditor(property.bind(model), property.bindContext(model), null, listOf())
    editor.setTestText("abc")
    // Our assumption is that changing the text editor content directly does not immediately raise the notification and thus it is possible
    // to test updateProperty() method.
    assumeThat(parsedModel.value, equalTo("value".asAnnotatedParsed()))

    assertThat(editor.testRenderedComboBox.toEditorText(editor.getValue()), equalTo("abc"))
    assertThat(editor.getValue(), equalTo("abc".asAnnotatedParsed()))
  }

  fun testDispose() {
    val editor = SimplePropertyEditor(property.bind(model), property.bindContext(model), null, listOf())
    editor.dispose()
    editor.commitTestText("abc")
    assertThat(parsedModel.value, equalTo("value".asAnnotatedParsed()))
    assertThat(editor.testPlainTextStatus, equalTo(""))
  }

  fun testHandlesInvalidInput() {
    val editor = SimplePropertyEditor(property.bind(model), property.bindContext(model), null, listOf())
    editor.commitTestText("invalid")  // "invalid" is recognised as an invalid input by the test parser.
    assertThat<Annotated<ParsedValue<String>>>(editor.getValue(), equalTo(invalidValueParsed))
    assertThat<Annotated<ParsedValue<String>>>(parsedModel.value, equalTo("value".asAnnotatedParsed()))
  }

  fun testCreateNew() {
    val variablesProvider = mock(PsVariablesScope::class.java)
    val var1 = "var1" to "1"
    val var2 = "var2" to "2"
    val var3 = "var3" to "3"
    val property = this.property
    val propertyContext = property.bindContext(model)
    whenever(variablesProvider.getAvailableVariablesFor(propertyContext)).thenReturn(
      listOf(
        var1.asAnnotatedParsed(),
        var2.asAnnotatedParsed(),
        var3.asAnnotatedParsed()
      )
    )
    val editor: SimplePropertyEditor<String, ModelPropertyCore<String>> =
      SimplePropertyEditor(property.bind(model), propertyContext, variablesProvider, listOf())
    val clone = editor.createNew(property.bind(model)) as SimplePropertyEditor<*, *>
    assertThat(clone.selectedItem, equalTo("value".asAnnotatedParsed()))
    assertThat(clone.testPlainTextStatus, equalTo(""))
    assertThat<List<*>>(clone.getModel().getItems(),
               hasItems("1".asAnnotatedParsed(), "2".asAnnotatedParsed(), var1.asAnnotatedParsed(), var2.asAnnotatedParsed()))
  }
}

private val SimplePropertyEditor<*, *>.testPlainTextStatus: String
  get() = this.statusComponent.getCharSequence(true).toString()

private fun <T> ListModel<T>.getItems(): List<T> {
  val result = mutableListOf<T>()
  for (i in 0 until size) {
    result.add(getElementAt(i))
  }
  return result.toList()
}

private fun String.asAnnotatedParsed(): Annotated<ParsedValue<String>> = ParsedValue.Set.Parsed(value = this,
                                                                                                dslText = DslText.Literal).annotated()

private fun <T : Any> Pair<String, T>.asAnnotatedParsed() = ParsedValue.Set.Parsed(dslText = DslText.Reference(first),
                                                                                   value = second).annotated()

@Suppress("UNCHECKED_CAST")
private val SimplePropertyEditor<*, *>.selectedItem
  get() = testRenderedComboBox.selectedItem as Annotated<ParsedValue<String>>

private fun <T : Any> SimplePropertyEditor<T, *>.getModel() = testRenderedComboBox.model
private fun SimplePropertyEditor<*, *>.setTestText(text: String) {
  (testRenderedComboBox.editor.editorComponent as JTextComponent).text = text
}

private fun SimplePropertyEditor<*, *>.commitTestText(text: String) {
  setTestText(text)
  testRenderedComboBox.actionPerformed(ActionEvent(testRenderedComboBox.editor, 0, null))
}

private fun SimplePropertyEditor<*, *>.testWatermark(): String? =
  (testRenderedComboBox.editor?.editorComponent as? JBTextField)?.emptyText?.text
