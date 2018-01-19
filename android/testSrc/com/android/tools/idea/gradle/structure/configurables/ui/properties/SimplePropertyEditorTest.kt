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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Ignore
import org.junit.Test

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
      knownValuesGetter = { listOf(ValueDescriptor("1", "one"), ValueDescriptor("2", "two")) })

  @Test
  fun loadsValue() {
    val editor = simplePropertyEditor(model, property)
    assertEquals("parsed", editor.selectedItem)
  }

  @Test
  fun loadsWellKnownValue() {
    parsedModel.value = "1"
    val editor = simplePropertyEditor(model, property)
    assertEquals("one", editor.selectedItem)
  }

  @Test
  fun loadsReference() {
    parsedModel.dsl = DslText(DslMode.REFERENCE, "some_reference")
    val editor = simplePropertyEditor(model, property)
    assertEquals("\$some_reference", editor.selectedItem)
  }

  @Test
  fun loadsReferenceResolvedIntoKnownValue() {
    parsedModel.dsl = DslText(DslMode.REFERENCE, "some_reference")
    parsedModel.value = "1"
    val editor = simplePropertyEditor(model, property)
    assertEquals("\$some_reference", editor.selectedItem)
  }

  @Test
  fun loadsInterpolatedString() {
    parsedModel.dsl = DslText(DslMode.INTERPOLATED_STRING, "some \${reference}")
    val editor = simplePropertyEditor(model, property)
    assertEquals("\"some \${reference}\"", editor.selectedItem)
  }

  @Test
  fun loadsOtherUnparsedDslText() {
    parsedModel.dsl = DslText(DslMode.OTHER_UNPARSED_DSL_TEXT, "1 + z(x)")
    val editor = simplePropertyEditor(model, property)
    assertEquals("\$\$1 + z(x)", editor.selectedItem)
  }

  @Test
  fun updatesValue() {
    val editor = simplePropertyEditor(model, property)
    editor.selectedItem = "abc"
    assertEquals("abc", parsedModel.value)
  }

  @Test
  fun updatesToNullValue() {
    val editor = simplePropertyEditor(model, property)
    editor.selectedItem = ""
    assertNull(parsedModel.value)
  }

  @Test
  fun updatesToReference() {
    val editor = simplePropertyEditor(model, property)
    editor.selectedItem = "\$other.reference"
    assertNull(parsedModel.value)
    assertEquals(DslMode.REFERENCE, parsedModel.dsl?.mode)
    assertEquals("other.reference", parsedModel.dsl?.text)
  }

  @Test
  fun updatesToInterpolatedString() {
    val editor = simplePropertyEditor(model, property)
    // TODO(b/72088238): Decide on the exact format.
    editor.selectedItem = "\"\$a and \$b\""
    assertNull(parsedModel.value)
    assertEquals(DslMode.INTERPOLATED_STRING, parsedModel.dsl?.mode)
    assertEquals("\$a and \$b", parsedModel.dsl?.text)
  }

  @Test
  fun updatesFromWellKnownValueDescription() {
    val editor = simplePropertyEditor(model, property)
    editor.selectedItem = "two"
    assertEquals("2", parsedModel.value)
  }

  @Test
  @Ignore("b/72088462")
  fun updatesToOtherUnresolvedDslText() {
    // TODO(b/72088462): Decide what the expectations are.
  }

  @Test
  fun handlesInvalidInput() {
    val editor = simplePropertyEditor(model, property)
    editor.selectedItem = "invalid"  // "invalid" is recognised as an invalid input by the test parser.
    // Right now invalid input is ignored.
    assertEquals("parsed", parsedModel.value)
  }
}
