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

import com.android.tools.idea.gradle.dsl.api.ext.ExtModel
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel
import com.android.tools.idea.gradle.structure.PsdGradleFileModelTestCase
import com.android.tools.idea.gradle.structure.MODEL_LIST_PROPERTY_IMPL_PROPERTY_VALUES
import com.android.tools.idea.gradle.structure.MODEL_LIST_PROPERTY_IMPL_REBIND_RESOLVED_PROPERTY
import com.android.tools.idea.gradle.structure.MODEL_LIST_PROPERTY_IMPL_REBIND_RESOLVED_PROPERTY_EXPECTED
import com.android.tools.idea.gradle.structure.MODEL_MAP_PROPERTY_IMPL_PROPERTY_VALUES
import com.android.tools.idea.gradle.structure.model.android.asParsed
import com.android.tools.idea.gradle.structure.model.helpers.parseInt
import com.android.tools.idea.gradle.structure.model.helpers.parseString
import com.intellij.testFramework.RunsInEdt
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assume
import org.junit.Test

@RunsInEdt
class ModelListPropertyImplTest : PsdGradleFileModelTestCase() {

  object Model : ModelDescriptor<Model, Model, Model> {
    override fun getResolved(model: Model): Model? = null
    override fun getParsed(model: Model): Model? = this
    override fun prepareForModification(model: Model) = Unit
    override fun setModified(model: Model) = Unit
  }

  private fun <T : Any> GradlePropertyModel.wrap(
    parse: (String) -> Annotated<ParsedValue<T>>,
    caster: ResolvedPropertyModel.() -> T?
  ): ModelListPropertyCore<T> {
    val resolved = resolve()
    return Model.listProperty(
      "description",
      resolvedValueGetter = { null },
      parsedPropertyGetter = { resolved },
      getter = { caster() },
      setter = { setValue(it) },
      parser = { value -> parse(value) }
    ).bind(Model)
  }

  @Test
  fun testPropertyValues() {
    writeToBuildFile(MODEL_LIST_PROPERTY_IMPL_PROPERTY_VALUES)
    val extModel = gradleBuildModel.ext()

    val propList = extModel.findProperty("propList").wrap(::parseString, ResolvedPropertyModel::asString)
    assertThat(propList.isModified, equalTo(false))
    val propListRef = extModel.findProperty("propListRef").wrap(::parseString, ResolvedPropertyModel::asString)
    assertThat(propListRef.isModified, equalTo(false))

    fun validateValues(list: ModelListPropertyCore<String>) {
      val editableValues = list.getEditableValues()
      val propA = editableValues[0]
      val propB = editableValues[1]
      val propC = editableValues[2]
      val propRef = editableValues[3]
      val propInterpolated = editableValues[4]

      assertThat(propA.testValue(), equalTo("1"))
      assertThat(propA.isModified, equalTo(false))
      assertThat(propB.testValue(), equalTo("2"))
      assertThat(propB.isModified, equalTo(false))
      assertThat(propC.testValue(), equalTo("3"))
      assertThat(propB.isModified, equalTo(false))
      assertThat(propRef.testValue(), equalTo("2"))
      assertThat(propRef.isModified, equalTo(false))
      assertThat(propInterpolated.testValue(), equalTo("2nd"))
      assertThat(propInterpolated.isModified, equalTo(false))
    }

    validateValues(propList)
    validateValues(propListRef)
  }

  // TODO(b/72814329): Test parsed and resolved value matching annotation.

  @Test
  fun testWritePropertyValues() {
    writeToBuildFile(MODEL_LIST_PROPERTY_IMPL_PROPERTY_VALUES)

    val buildModel = gradleBuildModel
    val extModel = buildModel.ext()

    val list = extModel.findProperty("propList").wrap(::parseString, ResolvedPropertyModel::asString)
    var editableValues = list.getEditableValues()

    editableValues[0].testSetValue("A")
    editableValues[1].testSetReference("propC")
    editableValues[2].testSetInterpolatedString("${'$'}{propC}rd")
    editableValues[3].testSetValue("D")
    editableValues[4].testSetValue("E")

    assertThat(list.isModified, equalTo(true))

    fun verify(ext: ExtModel, expectModified: Boolean) {
      editableValues =
          ext.findProperty("propList").wrap(::parseString, ResolvedPropertyModel::asString).getEditableValues()
      val propA = editableValues[0]
      val prop3 = editableValues[1]
      val prop3rd = editableValues[2]
      val propD = editableValues[3]
      val propE = editableValues[4]

      assertThat(propA.testValue(), equalTo("A"))
      assertThat(propA.isModified, equalTo(expectModified))
      assertThat(prop3.testValue(), equalTo("3"))
      assertThat(prop3.isModified, equalTo(expectModified))
      assertThat(prop3rd.testValue(), equalTo("3rd"))
      assertThat(prop3rd.isModified, equalTo(expectModified))
      assertThat(propD.testValue(), equalTo("D"))
      assertThat(propD.isModified, equalTo(expectModified))
      assertThat(propE.testValue(), equalTo("E"))
      assertThat(propE.isModified, equalTo(expectModified))
    }

    verify(extModel, expectModified = true)
    applyChangesAndReparse(buildModel)
    verify(buildModel.ext(), expectModified = false)
  }

  @Test
  fun testAddRemoveValues() {
    writeToBuildFile(MODEL_LIST_PROPERTY_IMPL_PROPERTY_VALUES)
    val buildModel = gradleBuildModel
    val extModel = buildModel.ext()

    val list = extModel.findProperty("propList").wrap(::parseString, ResolvedPropertyModel::asString)

    list.deleteItem(0)
    assertThat(list.isModified, equalTo(true))
    var editableValues = list.getEditableValues()
    assertThat(editableValues[0].isModified, equalTo(false))
    assertThat(editableValues[3].isModified, equalTo(false))
    editableValues[0].testSetReference("propC")
    editableValues[1].testSetInterpolatedString("${'$'}{propC}rd")
    editableValues[2].testSetValue("D")
    editableValues[3].testSetValue("E")

    list.addItem(4).also {
      assertThat(it.isModified, equalTo(true))  // A newly inserted item is modified.
      it.testSetValue("ZZ")
    }

    fun verify(ext: ExtModel) {
      editableValues =
          ext.findProperty("propList").wrap(::parseString, ResolvedPropertyModel::asString).getEditableValues()
      val prop3 = editableValues[0]
      val prop3rd = editableValues[1]
      val propD = editableValues[2]
      val propE = editableValues[3]
      val propZZ = editableValues[4]

      assertThat(prop3.testValue(), equalTo("3"))
      assertThat(prop3rd.testValue(), equalTo("3rd"))
      assertThat(propD.testValue(), equalTo("D"))
      assertThat(propE.testValue(), equalTo("E"))
      assertThat(propZZ.testValue(), equalTo("ZZ"))
    }

    verify(extModel)
    applyChangesAndReparse(buildModel)
    verify(buildModel.ext())
  }

  @Test
  fun testInsertRemoveValues() {
    writeToBuildFile(MODEL_LIST_PROPERTY_IMPL_PROPERTY_VALUES)
    val buildModel = gradleBuildModel
    val extModel = buildModel.ext()

    val list = extModel.findProperty("propList").wrap(::parseString, ResolvedPropertyModel::asString)

    list.deleteItem(2)
    var editableValues = list.getEditableValues()

    //elements before and after deleted one are not modified
    assertThat(editableValues[0].isModified, equalTo(false))
    assertThat(editableValues[3].isModified, equalTo(false))

    editableValues[1].testSetInterpolatedString("${'$'}{propC}rd")
    assertThat(editableValues[1].isModified, equalTo(true))
    editableValues[2].testSetValue("D")
    assertThat(editableValues[2].isModified, equalTo(true))

    list.addItem(0).testSetValue("ZZ")
    editableValues = list.getEditableValues()
    assertThat(editableValues[0].isModified, equalTo(true))
    assertThat(editableValues[4].isModified, equalTo(false)) //last element is still not modified

    fun verify(ext: ExtModel) {
      editableValues =
          ext.findProperty("propList").wrap(::parseString, ResolvedPropertyModel::asString).getEditableValues()
      val propZZ = editableValues[0]
      val prop1 = editableValues[1]
      val prop3rd = editableValues[2]
      val propD = editableValues[3]
      val propE = editableValues[4]

      assertThat(propZZ.testValue(), equalTo("ZZ"))
      assertThat(prop1.testValue(), equalTo("1"))
      assertThat(prop3rd.testValue(), equalTo("3rd"))
      assertThat(propD.testValue(), equalTo("D"))
      assertThat(propE.testValue(), equalTo("2nd"))
    }

    verify(extModel)
    applyChangesAndReparse(buildModel)
    verify(buildModel.ext())
  }

  @Test
  fun testRebindResolvedProperty() {
    writeToBuildFile(MODEL_LIST_PROPERTY_IMPL_REBIND_RESOLVED_PROPERTY)
    val buildModelInstance = gradleBuildModel
    val extModel = buildModelInstance.ext()

    val propList = extModel.findProperty("propList").wrap(::parseInt, ResolvedPropertyModel::asInt)

    val newResolvedProperty = extModel.findProperty("ext.newVar").resolve()
    var localModified = false
    var localModifying = false
    @Suppress("UNCHECKED_CAST")
    val reboundProp = (propList.getEditableValues()[0] as GradleModelCoreProperty<Int, ModelPropertyCore<Int>>)
      .rebind(newResolvedProperty) { localModifying = true ; it (); localModified = true }
    assertThat(reboundProp.getParsedValue(), equalTo<Annotated<ParsedValue<Int>>>(ParsedValue.NotSet.annotated()))
    reboundProp.setParsedValue(1.asParsed())
    assertThat(reboundProp.getParsedValue(), equalTo<Annotated<ParsedValue<Int>>>(1.asParsed().annotated()))
    assertThat(localModified, equalTo(true))
    assertThat(localModifying, equalTo(true))
    assertThat(newResolvedProperty.isModified, equalTo(true))
    assertThat(newResolvedProperty.getValue(GradlePropertyModel.INTEGER_TYPE), equalTo(1))

    applyChangesAndReparse(buildModelInstance)
    verifyFileContents(buildFile, MODEL_LIST_PROPERTY_IMPL_REBIND_RESOLVED_PROPERTY_EXPECTED)
  }

  @Test
  fun testEditListInitAsLambda() {
    Assume.assumeTrue("Can init map with lambda in kotlin only", isKotlinScript)
    writeToBuildFile(MODEL_LIST_PROPERTY_IMPL_REBIND_RESOLVED_PROPERTY)
    val extModel = gradleBuildModel.ext()
    val propList = extModel.findProperty("propList2").wrap(::parseString, ResolvedPropertyModel::asString)
    assertThat(propList.getEditableValues().size, equalTo(0)) // do not parse kotlin lambda initializer for now
  }

}
