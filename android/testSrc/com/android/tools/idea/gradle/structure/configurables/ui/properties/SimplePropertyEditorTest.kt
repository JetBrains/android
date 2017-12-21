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

import com.android.tools.idea.gradle.dsl.api.values.GradleNotNullValue
import com.android.tools.idea.gradle.dsl.api.values.GradleNullableValue
import com.android.tools.idea.gradle.structure.model.meta.ModelDescriptor
import com.android.tools.idea.gradle.structure.model.meta.ModelSimplePropertyImpl
import com.android.tools.idea.gradle.structure.model.meta.ParsedValue
import com.android.tools.idea.gradle.structure.model.meta.ValueDescriptor
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SimplePropertyEditorTest {

  class Model {
    var modified: Boolean = false
  }

  class ResolvedModel {
    var value: String? = "resolved"
  }

  class ParsedModel {
    var value: GradleNullableValue<String> = makeGradleValue("parsed")
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
      { "default" },
      { value },
      { value },
      { value = makeGradleValue(it) },
      {
        when {
          it.isEmpty() -> ParsedValue.NotSet()
          it == "invalid" -> ParsedValue.Set.Invalid("invalid", "invalid text message")
          else -> ParsedValue.Set.Parsed(value = it)
        }
      },
      { listOf(ValueDescriptor("1", "one"), ValueDescriptor("2", "two")) })

  @Test
  fun loadsValue() {
    val editor = SimplePropertyEditor(model, property)
    assertEquals("parsed", editor.selectedItem)
  }

  @Test
  fun loadsWellKnownValue() {
    parsedModel.value = makeGradleValue("1")
    val editor = SimplePropertyEditor(model, property)
    assertEquals("one", editor.selectedItem)
  }

  @Test
  fun updatesValue() {
    val editor = SimplePropertyEditor(model, property)
    editor.selectedItem = "abc"
    assertEquals("abc", parsedModel.value.value())
  }

  @Test
  fun updatesToNullValue() {
    val editor = SimplePropertyEditor(model, property)
    editor.selectedItem = ""
    assertNull(parsedModel.value.value())
  }

  @Test
  fun updatesFromWellKnownValueDescription() {
    val editor = SimplePropertyEditor(model, property)
    editor.selectedItem = "two"
    assertEquals("2", parsedModel.value.value())
  }

  @Test
  fun handlesInvalidInput() {
    val editor = SimplePropertyEditor(model, property)
    editor.selectedItem = "invalid"  // "invalid" is recognised as an invalid input by the test parser. 
    // Right now invalid input is ignored.
    assertEquals("parsed", parsedModel.value.value())
  }
}

fun makeGradleValue(text: String?) = object : GradleNullableValue<String> {
  override fun getFile(): VirtualFile = throw NotImplementedError()
  override fun value(): String? = text
  override fun getPropertyName(): String = "property"
  override fun getPsiElement(): PsiElement? = null
  override fun getDslText(): String? = text?.let { "\"$text\"" }
  override fun getResolvedVariables(): MutableMap<String, GradleNotNullValue<Any>> = mutableMapOf()
}