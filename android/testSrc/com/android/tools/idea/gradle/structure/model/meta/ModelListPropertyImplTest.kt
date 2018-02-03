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
import com.android.tools.idea.gradle.structure.model.helpers.parseString
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat

class ModelListPropertyImplTest : GradleFileModelTestCase() {

  object Model : ModelDescriptor<Model, Model, Model> {
    override fun getResolved(model: Model): Model? = null
    override fun getParsed(model: Model): Model? = this
    override fun setModified(model: Model) = Unit
  }

  private fun <T : Any> GradlePropertyModel.wrap(
    parse: (String) -> ParsedValue<T>,
    caster: ResolvedPropertyModel.() -> T?
  ): ModelListProperty<Model, T> {
    val resolved = ResolvedPropertyModelImpl(this)
    return Model.listProperty(
      "description",
      getResolvedValue = { null },
      getParsedCollection = { resolved.asParsedListValue({ caster() }, { setValue(it) }) },
      getParsedRawValue = { resolved.dslText() },
      clearParsedValue = { resolved.clear() },
      setParsedRawValue = { resolved.setDslText(it) },
      parse = { parse(it) }
    )
  }

  private fun <T : Any> ModelPropertyCore<Unit, T>.testValue() = (getValue().parsedValue as? ParsedValue.Set.Parsed<T>)?.value
  private fun <T : Any> ModelPropertyCore<Unit, T>.testSetValue(value: T?) =
    setValue(if (value != null) ParsedValue.Set.Parsed(value = value) else ParsedValue.NotSet())

  private fun <T : Any> ModelPropertyCore<Unit, T>.testSetReference(value: String) =
    setValue(ParsedValue.Set.Parsed(dslText = DslText(DslMode.REFERENCE, value), value = null))

  private fun <T : Any> ModelPropertyCore<Unit, T>.testSetInterpolatedString(value: String) =
    setValue(ParsedValue.Set.Parsed(dslText = DslText(DslMode.INTERPOLATED_STRING, value), value = null))

  fun testPropertyValues() {
    val text = """
               ext {
                 propB = "2"
                 propC = "3"
                 propRef = propB
                 propInterpolated = "${'$'}{propB}nd"
                 propList = ["1", propB, propC, propRef, propInterpolated]
                 propListRef = propList
               }""".trimIndent()
    writeToBuildFile(text)

    val extModel = gradleBuildModel.ext()

    val propList = extModel.findProperty("propList").wrap(::parseString, ResolvedPropertyModel::asString)
    val propListRef = extModel.findProperty("propListRef").wrap(::parseString, ResolvedPropertyModel::asString)

    fun validateValues(list: ModelListProperty<Model, String>) {
      val editableValues = list.getEditableValues(Model)
      val propA = editableValues[0]
      val propB = editableValues[1]
      val propC = editableValues[2]
      val propRef = editableValues[3]
      val propInterpolated = editableValues[4]

      assertThat(propA.testValue(), equalTo("1"))
      assertThat(propB.testValue(), equalTo("2"))
      assertThat(propC.testValue(), equalTo("3"))
      assertThat(propRef.testValue(), equalTo("2"))
      assertThat(propInterpolated.testValue(), equalTo("2nd"))
    }

    validateValues(propList)
    validateValues(propListRef)
  }

  fun testWritePropertyValues() {
    val text = """
               ext {
                 propB = "2"
                 propC = "3"
                 propRef = propB
                 propInterpolated = "${'$'}{propB}nd"
                 propList = ["1", propB, propC, propRef, propInterpolated]
               }""".trimIndent()
    writeToBuildFile(text)

    val extModel = gradleBuildModel.ext()

    val list = extModel.findProperty("propList").wrap(::parseString, ResolvedPropertyModel::asString)
    var editableValues = list.getEditableValues(Model)

    editableValues[0].testSetValue("A")
    editableValues[1].testSetReference("propC")
    editableValues[2].testSetInterpolatedString("${'$'}{propC}rd")
    editableValues[3].testSetValue("D")
    editableValues[4].testSetValue("E")

    editableValues = list.getEditableValues(Model)
    val propA = editableValues[0]
    val prop3 = editableValues[1]
    val prop3rd = editableValues[2]
    val propD = editableValues[3]
    val propE = editableValues[4]

    assertThat(propA.testValue(), equalTo("A"))
    assertThat(prop3.testValue(), equalTo("3"))
    assertThat(prop3rd.testValue(), equalTo("3rd"))
    assertThat(propD.testValue(), equalTo("D"))
    assertThat(propE.testValue(), equalTo("E"))
  }
}
