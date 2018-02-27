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
import com.android.tools.idea.gradle.structure.model.helpers.parseString
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.MatcherAssert.assertThat

class ModelMapPropertyImplTest : GradleFileModelTestCase() {
  var modifiedCount: Int = 0

  object TestModelDescriptor : ModelDescriptor<ModelMapPropertyImplTest, ModelMapPropertyImplTest, ModelMapPropertyImplTest> {
    override fun getResolved(model: ModelMapPropertyImplTest): ModelMapPropertyImplTest? = model
    override fun getParsed(model: ModelMapPropertyImplTest): ModelMapPropertyImplTest? = model
    override fun setModified(model: ModelMapPropertyImplTest) {
      model.modifiedCount++
    }
  }

  private fun <T : Any> GradlePropertyModel.wrap(
    parse: (String) -> ParsedValue<T>,
    caster: ResolvedPropertyModel.() -> T?
  ): ModelMapProperty<ModelMapPropertyImplTest, T> {
    val resolved = resolve()
    return TestModelDescriptor.mapProperty(
      "description",
      getResolvedValue = { null },
      getParsedProperty = { resolved },
      itemValueGetter = { caster() },
      itemValueSetter = { setValue(it) },
      parse = { parse(it) }
    )
  }

  private fun <T : Any> ModelPropertyCore<Unit, T>.testValue() = (getParsedValue(Unit) as? ParsedValue.Set.Parsed<T>)?.value
  private fun <T : Any> ModelPropertyCore<Unit, T>.testSetValue(value: T?) =
    setParsedValue(Unit, if (value != null) ParsedValue.Set.Parsed(value = value) else ParsedValue.NotSet())

  private fun <T : Any> ModelPropertyCore<Unit, T>.testSetReference(value: String) =
    setParsedValue(Unit, ParsedValue.Set.Parsed(dslText = DslText(DslMode.REFERENCE, value), value = null))

  private fun <T : Any> ModelPropertyCore<Unit, T>.testSetInterpolatedString(value: String) =
    setParsedValue(Unit, ParsedValue.Set.Parsed(dslText = DslText(DslMode.INTERPOLATED_STRING, value), value = null))

  fun testPropertyValues() {
    // TODO(b/72940492): Replace propC1 and propRef1 with propC and propRef respectively.
    val text = """
               ext {
                 propB = "2"
                 propC = "3"
                 propRef = propB
                 propInterpolated = "${'$'}{propB}nd"
                 propMap = [one: "1", "B": propB, "propC1": propC, propRef1: propRef, interpolated: propInterpolated]
                 propMapRef = propMap
               }""".trimIndent()
    writeToBuildFile(text)

    val extModel = gradleBuildModel.ext()

    val propMap = extModel.findProperty("propMap").wrap(::parseString, ResolvedPropertyModel::asString)
    val propMapRef = extModel.findProperty("propMapRef").wrap(::parseString, ResolvedPropertyModel::asString)

    fun validateValues(map: ModelMapProperty<ModelMapPropertyImplTest, String>) {
      val editableValues = map.getEditableValues(this)
      val propOne = editableValues["one"]
      val propB = editableValues["B"]
      val propC = editableValues["propC1"]
      val propRef = editableValues["propRef1"]
      val propInterpolated = editableValues["interpolated"]

      assertThat(propOne?.testValue(), equalTo("1"))
      assertThat(propB?.testValue(), equalTo("2"))
      assertThat(propC?.testValue(), equalTo("3"))
      assertThat(propRef?.testValue(), equalTo("2"))
      assertThat(propInterpolated?.testValue(), equalTo("2nd"))
    }

    validateValues(propMap)
    validateValues(propMapRef)
  }

  fun testWritePropertyValues() {
    // TODO(b/72940492): Replace propC1 and propRef1 with propC and propRef respectively.
    val text = """
               ext {
                 propB = "2"
                 propC1 = "3"
                 propRef1 = propB
                 propInterpolated = "${'$'}{propB}nd"
                 propMap = [one: "1", "B": propB, "propC": propC1, propRef: propRef1, interpolated: propInterpolated]
               }""".trimIndent()
    writeToBuildFile(text)

    val extModel = gradleBuildModel.ext()

    val map = extModel.findProperty("propMap").wrap(::parseString, ResolvedPropertyModel::asString)
    var editableValues = map.getEditableValues(this)

    editableValues["one"]?.testSetValue("A")
    editableValues["B"]?.testSetReference("propC1")
    editableValues["propC"]?.testSetInterpolatedString("${'$'}{propC1}rd")
    editableValues["propRef"]?.testSetValue("D")
    editableValues["interpolated"]?.testSetValue("E")
    assertThat(modifiedCount, equalTo(5))

    editableValues = map.getEditableValues(this)
    val propA = editableValues["one"]
    val prop3 = editableValues["B"]
    val prop3rd = editableValues["propC"]
    val propD = editableValues["propRef"]
    val propE = editableValues["interpolated"]

    assertThat(propA?.testValue(), equalTo("A"))
    assertThat(prop3?.testValue(), equalTo("3"))
    assertThat(prop3rd?.testValue(), equalTo("3rd"))
    assertThat(propD?.testValue(), equalTo("D"))
    assertThat(propE?.testValue(), equalTo("E"))
  }

  fun testEditMapKeys() {
    // TODO(b/72940492): Replace propC1 and propRef1 with propC and propRef respectively.
    val text = """
               ext {
                 propB = "2"
                 propC1 = "3"
                 propRef1 = propB
                 propInterpolated = "${'$'}{propB}nd"
                 propMap = [one: "1", "B": propB, "propC": propC1, propRef: propRef1, interpolated: propInterpolated]
               }""".trimIndent()
    writeToBuildFile(text)

    val extModel = gradleBuildModel.ext()

    val map = extModel.findProperty("propMap").wrap(::parseString, ResolvedPropertyModel::asString)
    map.deleteEntry(this, "B")
    val newInterpolated = map.changeEntryKey(this, "interpolated", "newInterpolated")
    val newPropC = map.changeEntryKey(this, "propC", "newPropC")
    val newPropRef = map.changeEntryKey(this, "propRef", "newPropRef")
    val newOne = map.changeEntryKey(this, "one", "newOne")
    // Add new.
    val newNew = map.addEntry(this, "new")  // Does not count as modified.
    newNew.testSetValue("new")
    // Add new and change it.
    val new2 = map.addEntry(this, "new2")  // Does not count as modified.
    new2.testSetReference("propC1")
    val newChanged = map.changeEntryKey(this, "new2", "newChanged")
    assertThat(modifiedCount, equalTo(8))

    assertThat(newOne.testValue(), equalTo("1"))
    assertThat(newPropC.testValue(), equalTo("3"))
    assertThat(newPropRef.testValue(), equalTo("2"))
    assertThat(newInterpolated.testValue(), equalTo("2nd"))
    assertThat(newNew.testValue(), equalTo("new"))
    assertThat(newChanged.testValue(), equalTo("3"))

    val editableValues = map.getEditableValues(this)
    val one = editableValues["newOne"]
    val b = editableValues["newB"]
    val propC = editableValues["newPropC"]
    val propRef = editableValues["newPropRef"]
    val interpolated = editableValues["newInterpolated"]
    val new = editableValues["new"]
    val changed = editableValues["newChanged"]

    assertThat(one?.testValue(), equalTo("1"))
    assertThat(b?.testValue(), nullValue())
    assertThat(propC?.testValue(), equalTo("3"))
    assertThat(propRef?.testValue(), equalTo("2"))
    assertThat(interpolated?.testValue(), equalTo("2nd"))
    assertThat(new?.testValue(), equalTo("new"))
    assertThat(changed?.testValue(), equalTo("3"))
  }
}
