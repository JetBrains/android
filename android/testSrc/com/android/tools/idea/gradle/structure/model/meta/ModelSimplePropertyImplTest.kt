/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.model.meta

import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase
import com.android.tools.idea.gradle.dsl.model.ext.ResolvedPropertyModelImpl
import com.android.tools.idea.gradle.structure.model.helpers.parseBoolean
import com.android.tools.idea.gradle.structure.model.helpers.parseInt
import com.android.tools.idea.gradle.structure.model.helpers.parseString
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.MatcherAssert.assertThat

class ModelSimplePropertyImplTest : GradleFileModelTestCase() {

  object Model : ModelDescriptor<Model, Model, Model> {
    override fun getResolved(model: Model): Model? = null
    override fun getParsed(model: Model): Model? = this
    override fun setModified(model: Model) = Unit
  }

  private fun <T : Any> GradlePropertyModel.wrap(
      parse: (String) -> ParsedValue<T>,
      caster: ResolvedPropertyModel.() -> T?
  ): ModelSimpleProperty<Model, T> {
    val resolved = ResolvedPropertyModelImpl(this)
    return Model.property(
        "description",
        getResolvedValue = { null },
        getParsedValue = { resolved.caster() },
        getParsedRawValue = { resolved.dslText() },
        setParsedValue = { resolved.setValue(it) },
        setParsedRawValue = { resolved.setDslText(it) },
        clearParsedValue = { resolved.clear() },
        parse = { parse(it) }
    )
  }

  private fun <T : Any> ModelSimpleProperty<Model, T>.testValue() = (getParsedValue(Model) as? ParsedValue.Set.Parsed<T>)?.value
  private fun <T : Any> ModelSimpleProperty<Model, T>.testSetValue(value: T?) =
      setParsedValue(Model, if (value != null) ParsedValue.Set.Parsed(value = value) else ParsedValue.NotSet())

  private fun <T : Any> ModelSimpleProperty<Model, T>.testSetReference(value: String) =
      setParsedValue(Model, ParsedValue.Set.Parsed(dslText = DslText(DslMode.REFERENCE, value), value = null))

  private fun <T : Any> ModelSimpleProperty<Model, T>.testSetInterpolatedString(value: String) =
      setParsedValue(Model, ParsedValue.Set.Parsed(dslText = DslText(DslMode.INTERPOLATED_STRING, value), value = null))

  fun testPropertyValues() {
    val text = """
               ext {
                 propValue = 'value'
                 prop25 = 25
                 propTrue = true
                 propRef = propValue
                 propInterpolated = "${'$'}{prop25}th"
                 propUnresolved = unresolvedReference
                 propOtherExpression1 = z(1)
                 propOtherExpression2 = 1 + 2
               }""".trimIndent()
    writeToBuildFile(text)

    val extModel = gradleBuildModel.ext()

    val propValue = extModel.findProperty("propValue").wrap(::parseString, ResolvedPropertyModel::asString)
    val prop25 = extModel.findProperty("prop25").wrap(::parseInt, ResolvedPropertyModel::asInt)
    val propTrue = extModel.findProperty("propTrue").wrap(::parseBoolean, ResolvedPropertyModel::asBoolean)
    val propRef = extModel.findProperty("propRef").wrap(::parseString, ResolvedPropertyModel::asString)
    val propInterpolated = extModel.findProperty("propInterpolated").wrap(::parseString, ResolvedPropertyModel::asString)
    val propUnresolved = extModel.findProperty("propUnresolved").wrap(::parseString, ResolvedPropertyModel::asString)
    val propOtherExpression1 = extModel.findProperty("propOtherExpression1").wrap(::parseString, ResolvedPropertyModel::asString)
    val propOtherExpression2 = extModel.findProperty("propOtherExpression2").wrap(::parseString, ResolvedPropertyModel::asString)

    assertThat(propValue.testValue(), equalTo("value"))
    assertThat(prop25.testValue(), equalTo(25))
    assertThat(propTrue.testValue(), equalTo(true))
    assertThat(propRef.testValue(), equalTo("value"))
    assertThat(propInterpolated.testValue(), equalTo("25th"))
    assertThat(propUnresolved.testValue(), nullValue())
    assertThat(propOtherExpression1.testValue(), nullValue())
    assertThat(propOtherExpression2.testValue(), nullValue())
  }

  fun testWritePropertyValues() {
    val text = """
               ext {
                 propValue = 'value'
                 prop25 = 25
                 propTrue = true
                 propRef = propValue
                 propInterpolated = "${'$'}{prop25}th"
                 propUnresolved = unresolvedReference
                 propOtherExpression1 = z(1)
                 propOtherExpression2 = 1 + 2
               }""".trimIndent()
    writeToBuildFile(text)

    val extModel = gradleBuildModel.ext()

    val propValue = extModel.findProperty("propValue").wrap(::parseString, ResolvedPropertyModel::asString)
    val prop25 = extModel.findProperty("prop25").wrap(::parseInt, ResolvedPropertyModel::asInt)
    val propTrue = extModel.findProperty("propTrue").wrap(::parseBoolean, ResolvedPropertyModel::asBoolean)
    val propRef = extModel.findProperty("propRef").wrap(::parseString, ResolvedPropertyModel::asString)
    val propInterpolated = extModel.findProperty("propInterpolated").wrap(::parseString, ResolvedPropertyModel::asString)
    val propUnresolved = extModel.findProperty("propUnresolved").wrap(::parseString, ResolvedPropertyModel::asString)
    val propOtherExpression1 = extModel.findProperty("propOtherExpression1").wrap(::parseString, ResolvedPropertyModel::asString)
    val propOtherExpression2 = extModel.findProperty("propOtherExpression2").wrap(::parseString, ResolvedPropertyModel::asString)

    propValue.testSetValue("changed")
    assertThat(propValue.testValue(), equalTo("changed"))

    prop25.testSetValue(26)
    assertThat(prop25.testValue(), equalTo(26))

    propTrue.testSetValue(null)
    assertThat(propTrue.testValue(), nullValue())

    propInterpolated.testSetInterpolatedString("${'$'}{prop25} items")
    assertThat(propInterpolated.testValue(), equalTo("26 items"))

    propRef.testSetReference("propInterpolated")
    assertThat(propRef.testValue(), equalTo("26 items"))

    propUnresolved.testSetValue("reset")
    assertThat(propUnresolved.testValue(), equalTo("reset"))
    assertThat(propOtherExpression1.testValue(), nullValue())
    assertThat(propOtherExpression2.testValue(), nullValue())
  }
}
