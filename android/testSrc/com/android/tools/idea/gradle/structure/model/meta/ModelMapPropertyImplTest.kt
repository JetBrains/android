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
import com.android.tools.idea.gradle.structure.PsdGradleFileModelTestCase
import com.android.tools.idea.gradle.structure.MODEL_MAP_PROPERTY_IMPL_PROPERTY_VALUES
import com.android.tools.idea.gradle.structure.model.helpers.parseString
import com.intellij.testFramework.RunsInEdt
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

@RunsInEdt
class ModelMapPropertyImplTest : PsdGradleFileModelTestCase() {
  var modifiedCount: Int = 0
  var prepareForModificationCount: Int = 0

  object TestModelDescriptor : ModelDescriptor<ModelMapPropertyImplTest, ModelMapPropertyImplTest, ModelMapPropertyImplTest> {
    override fun getResolved(model: ModelMapPropertyImplTest): ModelMapPropertyImplTest? = model
    override fun getParsed(model: ModelMapPropertyImplTest): ModelMapPropertyImplTest? = model
    override fun prepareForModification(model: ModelMapPropertyImplTest) {
      model.prepareForModificationCount++
    }
    override fun setModified(model: ModelMapPropertyImplTest) {
      model.modifiedCount++
    }
  }

  private fun <T : Any> GradlePropertyModel.wrap(
    parse: (String) -> Annotated<ParsedValue<T>>,
    caster: ResolvedPropertyModel.() -> T?
  ): ModelMapPropertyCore<T> {
    val resolved = resolve()
    return TestModelDescriptor.mapProperty(
      "description",
      resolvedValueGetter = { null },
      parsedPropertyGetter = { resolved },
      getter = { caster() },
      setter = { setValue(it) },
      parser = { value -> parse(value) }
    ).bind(this@ModelMapPropertyImplTest)
  }

  @Test
  fun testPropertyValues() {
    writeToBuildFile(MODEL_MAP_PROPERTY_IMPL_PROPERTY_VALUES)
    val extModel = gradleBuildModel.ext()

    val propMap = extModel.findProperty("propMap").wrap(::parseString, ResolvedPropertyModel::asString)
    assertThat(propMap.isModified, equalTo(false))
    val propMapRef = extModel.findProperty("propMapRef").wrap(::parseString, ResolvedPropertyModel::asString)
    assertThat(propMapRef.isModified, equalTo(false))

    fun validateValues(map: ModelMapPropertyCore<String>) {
      val editableValues = map.getEditableValues()
      val propOne = editableValues["one"]
      val propB = editableValues["B"]
      val propC = editableValues["propC1"]
      val propRef = editableValues["propRef1"]
      val propInterpolated = editableValues["interpolated"]

      assertThat(propOne?.testValue(), equalTo("1"))
      assertThat(propOne?.isModified, equalTo(false))
      assertThat(propB?.testValue(), equalTo("2"))
      assertThat(propB?.isModified, equalTo(false))
      assertThat(propC?.testValue(), equalTo("3"))
      assertThat(propC?.isModified, equalTo(false))
      assertThat(propRef?.testValue(), equalTo("2"))
      assertThat(propRef?.isModified, equalTo(false))
      assertThat(propInterpolated?.testValue(), equalTo("2nd"))
      assertThat(propInterpolated?.isModified, equalTo(false))
    }

    validateValues(propMap)
    validateValues(propMapRef)
  }

  // TODO(b/72814329): Test parsed and resolved value matching annotation.

  @Test
  fun testWritePropertyValues() {
    writeToBuildFile(MODEL_MAP_PROPERTY_IMPL_PROPERTY_VALUES)
    val extModel = gradleBuildModel.ext()

    val map = extModel.findProperty("propMap").wrap(::parseString, ResolvedPropertyModel::asString)
    var editableValues = map.getEditableValues()

    editableValues["one"]?.testSetValue("A")
    assertThat(map.isModified, equalTo(true))
    assertThat(editableValues["one"]?.isModified, equalTo(true))
    assertThat(editableValues["B"]?.isModified, equalTo(false))  // Other entries remain unmodified.
    editableValues["B"]?.testSetReference("propC")
    editableValues["propC1"]?.testSetInterpolatedString("${'$'}{propC}rd")
    editableValues["propRef1"]?.testSetValue("D")
    editableValues["interpolated"]?.testSetValue("E")
    assertThat(modifiedCount, equalTo(5))
    assertThat(prepareForModificationCount, equalTo(5))

    editableValues = map.getEditableValues()
    val propA = editableValues["one"]
    val prop3 = editableValues["B"]
    val prop3rd = editableValues["propC1"]
    val propD = editableValues["propRef1"]
    val propE = editableValues["interpolated"]

    assertThat(propA?.testValue(), equalTo("A"))
    assertThat(prop3?.testValue(), equalTo("3"))
    assertThat(prop3rd?.testValue(), equalTo("3rd"))
    assertThat(propD?.testValue(), equalTo("D"))
    assertThat(propE?.testValue(), equalTo("E"))
  }

  @Test
  fun testEditMapKeys() {
    writeToBuildFile(MODEL_MAP_PROPERTY_IMPL_PROPERTY_VALUES)

    val extModel = gradleBuildModel.ext()

    val map = extModel.findProperty("propMap").wrap(::parseString, ResolvedPropertyModel::asString)
    map.deleteEntry("B")
    assertThat(map.isModified, equalTo(true))
    // Deleting an entry does not make other entries modified.
    assertThat(map.getEditableValues()["interpolated"]?.isModified, equalTo(false))
    val newInterpolated = map.changeEntryKey("interpolated", "newInterpolated")
    assertThat(map.getEditableValues()["newInterpolated"]?.isModified, equalTo(true))
    // Changing the key of an entry does not make other entries modified.
    assertThat(map.getEditableValues()["propC1"]?.isModified, equalTo(false))
    val newPropC = map.changeEntryKey("propC1", "newPropC")
    val newPropRef = map.changeEntryKey("propRef1", "newPropRef")
    val newOne = map.changeEntryKey("one", "newOne")
    // Add new.
    val newNew = map.addEntry("new")  // Does count as modified. This is required to auto-instantiate "debug" alike entities.
    newNew.testSetValue("new")
    // Add new and change it.
    val new2 = map.addEntry("new2")  // Does count as modified. This is required to auto-instantiate "debug" alike entities.
    new2.testSetReference("propC")
    val newChanged = map.changeEntryKey("new2", "newChanged")
    assertThat(modifiedCount, equalTo(10))
    assertThat(prepareForModificationCount, equalTo(10))

    assertThat(newOne.testValue(), equalTo("1"))
    assertThat(newPropC.testValue(), equalTo("3"))
    assertThat(newPropRef.testValue(), equalTo("2"))
    assertThat(newInterpolated.testValue(), equalTo("2nd"))
    assertThat(newNew.testValue(), equalTo("new"))
    assertThat(newChanged.testValue(), equalTo("3"))

    val editableValues = map.getEditableValues()
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
