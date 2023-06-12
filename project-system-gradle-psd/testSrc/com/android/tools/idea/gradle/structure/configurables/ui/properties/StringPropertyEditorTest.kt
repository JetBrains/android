/*
 * Copyright (C) 2022 The Android Open Source Project
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
import com.android.tools.idea.gradle.structure.model.meta.annotated
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.testFramework.TestApplicationManager
import com.intellij.testFramework.UsefulTestCase
import com.intellij.ui.components.JBTextField
import org.hamcrest.CoreMatchers
import org.junit.Assert
import org.junit.Assume
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import kotlin.reflect.KProperty


typealias Model = Unit

class StringPropertyEditorTest : UsefulTestCase() {

  class ParsedModel {
    var value: Annotated<ParsedValue<String>> = "value".asAnnotatedParsed()
  }

  private val parsedModel = ParsedModel()

  private var defaultValue: String? = "default"
  private var wellKnownValuesFuture: ListenableFuture<List<ValueDescriptor<String>>> = Futures.immediateFuture(listOf())

  private val property
    get() = object : ModelPropertyBase<Model, String>(), ModelSimpleProperty<Model, String> {
      override val parser: (String) -> Annotated<ParsedValue<String>>
        get() = { value ->
          ParsedValue.Set.Parsed(value, DslText.Literal).annotated()
        }
      override val formatter: (String) -> String get() = { it }
      override val knownValuesGetter: (Model) -> ListenableFuture<List<ValueDescriptor<String>>> get() = { wellKnownValuesFuture }
      override val description: String = "Description"

      override fun bind(model: Model): ModelPropertyCore<String> = object : ModelPropertyCore<String> {
        override val description: String = "Description"
        override fun getParsedValue(): Annotated<ParsedValue<String>> = parsedModel.value
        override fun setParsedValue(value: ParsedValue<String>) {
          parsedModel.value = value.annotated()
        }

        override fun getResolvedValue(): ResolvedValue<String> = throw UnsupportedOperationException()

        override val defaultValueGetter: (() -> String?)? get() = defaultValue?.let { { it } }
        override val variableScope: (() -> PsVariablesScope?)? get() = { null }
        override fun annotateParsedResolvedMismatch(): ValueAnnotation? = null
        override val isModified: Boolean? get() = false
      }

      override fun getValue(thisRef: Model, property: KProperty<*>): ParsedValue<String> = throw UnsupportedOperationException()
      override fun setValue(thisRef: Model, property: KProperty<*>, value: ParsedValue<String>) = throw UnsupportedOperationException()
    }

  override fun setUp() {
    super.setUp()
    TestApplicationManager.getInstance()
  }

  fun testUpdatesValue() {
    val editor = StringPropertyEditor(property.bind(Model), property.bindContext(Model), listOf())
    editor.commitTestText("abc")
    Assert.assertThat(parsedModel.value, CoreMatchers.equalTo("abc".asAnnotatedParsed()))
  }

  fun testDispose() {
    val editor = StringPropertyEditor(property.bind(Model), property.bindContext(Model), listOf())
    editor.dispose()
    try {
      editor.commitTestText("abc")
      fail("updateProperty should throw exception when disposed");
    }
    catch (_: IllegalStateException) {
    }
    Assert.assertThat(parsedModel.value, CoreMatchers.equalTo("value".asAnnotatedParsed()))
  }

  fun testLoosingFocus() {
    val editor = StringPropertyEditor(property.bind(Model), property.bindContext(Model), listOf())
    editor.setTestText("abc")
    Assume.assumeThat(parsedModel.value, CoreMatchers.equalTo("value".asAnnotatedParsed()))
    editor.getFocusListeners().forEach { it.focusLost(FocusEvent(editor.component, 0)) }
    Assert.assertThat(parsedModel.value, CoreMatchers.equalTo("abc".asAnnotatedParsed()))
  }

}

private fun String.asAnnotatedParsed(): Annotated<ParsedValue<String>> = ParsedValue.Set.Parsed(value = this,
                                                                                                dslText = DslText.Literal).annotated()
private fun StringPropertyEditor<*>.commitTestText(text: String) {
  setTestText(text)
  updateProperty()
}

private fun StringPropertyEditor<*>.setTestText(text: String) {
  (component as JBTextField).text = text
}

private fun StringPropertyEditor<*>.getFocusListeners():Array<FocusListener> = (component as JBTextField).focusListeners
