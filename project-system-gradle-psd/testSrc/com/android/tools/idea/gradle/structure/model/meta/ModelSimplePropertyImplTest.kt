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
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.INTEGER_TYPE
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel
import com.android.tools.idea.gradle.structure.PsdGradleFileModelTestCase
import com.android.tools.idea.gradle.structure.MODEL_SIMPLE_PROPERTY_IMPL_PROPERTY_INITIALIZER
import com.android.tools.idea.gradle.structure.MODEL_SIMPLE_PROPERTY_IMPL_PROPERTY_VALUES
import com.android.tools.idea.gradle.structure.MODEL_SIMPLE_PROPERTY_IMPL_REBIND_RESOLVED_PROPERTY
import com.android.tools.idea.gradle.structure.MODEL_SIMPLE_PROPERTY_IMPL_REBIND_RESOLVED_PROPERTY_EXPECTED
import com.android.tools.idea.gradle.structure.MODEL_SIMPLE_PROPERTY_IMPL_RESOLVED_VALUE_MATCHING
import com.android.tools.idea.gradle.structure.MODEL_SIMPLE_PROPERTY_IMPL_WRITE_PROPERTY_VALUES
import com.android.tools.idea.gradle.structure.model.android.asParsed
import com.android.tools.idea.gradle.structure.model.helpers.parseBoolean
import com.android.tools.idea.gradle.structure.model.helpers.parseInt
import com.android.tools.idea.gradle.structure.model.helpers.parseString
import com.intellij.testFramework.RunsInEdt
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

@RunsInEdt
class ModelSimplePropertyImplTest : PsdGradleFileModelTestCase() {

  object Model : ModelDescriptor<Model, Model, Model> {
    override fun getResolved(model: Model): Model? = this
    override fun getParsed(model: Model): Model? = this
    override fun prepareForModification(model: Model) = Unit
    override fun setModified(model: Model) = Unit
  }

  private fun <T : Any> GradlePropertyModel.wrap(
    parse: (String) -> Annotated<ParsedValue<T>>,
    caster: ResolvedPropertyModel.() -> T?,
    resolvedValue: T? = null
  ): ModelSimpleProperty<Model, T> {
    val resolved = resolve()
    return Model.property(
      "description",
      resolvedValueGetter = { resolvedValue },
      parsedPropertyGetter = { resolved },
      getter = { caster() },
      setter = { setValue(it) },
      parser = { value -> parse(value) }
    )
  }

  private fun <T : Any> ModelSimpleProperty<Model, T>.testValue() = bind(Model).testValue()
  private fun <T : Any> ModelSimpleProperty<Model, T>.testIsModified() = bind(Model).isModified
  private fun <T : Any> ModelSimpleProperty<Model, T>.testSetValue(value: T?) = bind(Model).testSetValue(value)
  private fun <T : Any> ModelSimpleProperty<Model, T>.testSetReference(value: String) = bind(Model).testSetReference(value)
  private fun <T : Any> ModelSimpleProperty<Model, T>.testSetInterpolatedString(value: String) =
    bind(Model).testSetInterpolatedString(value)

  @Test
  fun testPropertyValues() {
    writeToBuildFile(MODEL_SIMPLE_PROPERTY_IMPL_PROPERTY_VALUES)

    val extModel = gradleBuildModel.ext()

    val propValue = extModel.findProperty("propValue").wrap(::parseString, ResolvedPropertyModel::asString, resolvedValue = "value")
    val prop25 = extModel.findProperty("prop25").wrap(::parseInt, ResolvedPropertyModel::asInt, resolvedValue = 26)
    val propTrue = extModel.findProperty("propTrue").wrap(::parseBoolean, ResolvedPropertyModel::asBoolean)
    val propRef = extModel.findProperty("propRef").wrap(::parseString, ResolvedPropertyModel::asString, resolvedValue = "value")
    val propInterpolated = extModel.findProperty("propInterpolated").wrap(::parseString, ResolvedPropertyModel::asString)
    val propUnresolved = extModel.findProperty("propUnresolved").wrap(::parseString, ResolvedPropertyModel::asString)
    val propOtherExpression1 = extModel.findProperty("propOtherExpression1").wrap(::parseString, ResolvedPropertyModel::asString)
    val propOtherExpression2 = extModel.findProperty("propOtherExpression2").wrap(::parseString, ResolvedPropertyModel::asString)

    assertThat(propValue.testValue(), equalTo("value"))
    assertThat(propValue.testIsModified(), equalTo(false))
    assertThat(prop25.testValue(), equalTo(25))
    assertThat(prop25.testIsModified(), equalTo(false))
    assertThat(propTrue.testValue(), equalTo(true))
    assertThat(propTrue.testIsModified(), equalTo(false))
    assertThat(propRef.testValue(), equalTo("value"))
    assertThat(propRef.testIsModified(), equalTo(false))
    assertThat(propInterpolated.testValue(), equalTo("25th"))
    assertThat(propInterpolated.testIsModified(), equalTo(false))
    assertThat(propUnresolved.testValue(), nullValue())
    assertThat(propUnresolved.testIsModified(), equalTo(false))
    assertThat(propOtherExpression1.testValue(), nullValue())
    assertThat(propOtherExpression1.testIsModified(), equalTo(false))
    assertThat(propOtherExpression2.testValue(), nullValue())
    assertThat(propOtherExpression2.testIsModified(), equalTo(false))
  }

  @Test
  fun testResolvedValueMatching() {
    writeToBuildFile(MODEL_SIMPLE_PROPERTY_IMPL_RESOLVED_VALUE_MATCHING)

    val extModel = gradleBuildModel.ext()

    val propValue = extModel.findProperty("propValue").wrap(::parseString, ResolvedPropertyModel::asString, resolvedValue = "value")
    val prop25 = extModel.findProperty("prop25").wrap(::parseInt, ResolvedPropertyModel::asInt, resolvedValue = 26)
    val propTrue = extModel.findProperty("propTrue").wrap(::parseBoolean, ResolvedPropertyModel::asBoolean)
    val propRef = extModel.findProperty("propRef").wrap(::parseString, ResolvedPropertyModel::asString, resolvedValue = "value")

    assertThat(propValue.bind(Model).getValue().annotation, nullValue())
    assertThat(prop25.bind(Model).getValue().annotation, equalTo<ValueAnnotation?>(ValueAnnotation.Error("Resolved: 26")))
    assertThat(propTrue.bind(Model).getValue().annotation,
               equalTo<ValueAnnotation?>(ValueAnnotation.Warning("Resolved value is unavailable.")))
    assertThat(propRef.bind(Model).getValue().annotation, nullValue())
  }

  @Test
  fun testWritePropertyValues() {
    writeToBuildFile(MODEL_SIMPLE_PROPERTY_IMPL_WRITE_PROPERTY_VALUES)

    val extModel = gradleBuildModel.ext()

    val propValue = extModel.findProperty("propValue").wrap(::parseString, ResolvedPropertyModel::asString)
    val prop25 = extModel.findProperty("prop25").wrap(::parseInt, ResolvedPropertyModel::asInt)
    val propTrue = extModel.findProperty("propTrue").wrap(::parseBoolean, ResolvedPropertyModel::asBoolean)
    val propInterpolated = extModel.findProperty("propInterpolated").wrap(::parseString, ResolvedPropertyModel::asString)
    val propUnresolved = extModel.findProperty("propUnresolved").wrap(::parseString, ResolvedPropertyModel::asString)
    val propRef = extModel.findProperty("propRef").wrap(::parseString, ResolvedPropertyModel::asString)

    propValue.testSetValue("changed")
    assertThat(propValue.testValue(), equalTo("changed"))
    assertThat(propValue.testIsModified(), equalTo(true))
    assertThat(propRef.testIsModified(), equalTo(false))  // Changing a dependee does not make the dependent modified.

    prop25.testSetValue(26)
    assertThat(prop25.testValue(), equalTo(26))
    assertThat(prop25.testIsModified(), equalTo(true))

    propTrue.testSetValue(null)
    assertThat(propTrue.testValue(), nullValue())
    assertThat(propTrue.testIsModified(), equalTo(true))

    propInterpolated.testSetInterpolatedString("${'$'}{prop25} items")
    assertThat(propInterpolated.testValue(), equalTo("26 items"))
    assertThat(propInterpolated.testIsModified(), equalTo(true))

    propUnresolved.testSetValue("reset")
    assertThat(propUnresolved.testValue(), equalTo("reset"))
    assertThat(propUnresolved.testIsModified(), equalTo(true))

    propRef.testSetReference("propInterpolated")
    assertThat(propRef.testValue(), equalTo("26 items"))
    assertThat(propRef.testIsModified(), equalTo(true))

    prop25.testSetReference("25")
    assertThat(prop25.testValue(), equalTo(25))

    propTrue.testSetReference("2 + 2")
    assertThat<Annotated<ParsedValue<Boolean>>>(
      propTrue.bind(Model).getParsedValue(),
      equalTo<Annotated<ParsedValue<Boolean>>>(ParsedValue.Set.Parsed<Boolean>(null, DslText.OtherUnparsedDslText("2 + 2")).annotated()))
  }

  @Test
  fun testRebindResolvedProperty() {
    writeToBuildFile(MODEL_SIMPLE_PROPERTY_IMPL_REBIND_RESOLVED_PROPERTY)

    val buildModelInstance = gradleBuildModel
    val extModel = buildModelInstance.ext()

    val prop25 = extModel.findProperty("prop25").wrap(::parseInt, ResolvedPropertyModel::asInt, resolvedValue = 26).bind(Model)
    val newResolvedProperty = extModel.findProperty("newVar").resolve()
    var localModified = false
    var localModifying = false
    @Suppress("UNCHECKED_CAST")
    val reboundProp =
      (prop25 as GradleModelCoreProperty<Int, ModelPropertyCore<Int>>)
        .rebind(newResolvedProperty) { localModifying = true; it(); localModified = true }
    assertThat(reboundProp.getParsedValue(), equalTo<Annotated<ParsedValue<Int>>>(ParsedValue.NotSet.annotated()))
    reboundProp.setParsedValue(1.asParsed())
    assertThat(reboundProp.getParsedValue(), equalTo<Annotated<ParsedValue<Int>>>(1.asParsed().annotated()))
    assertThat(localModified, equalTo(true))
    assertThat(localModifying, equalTo(true))
    assertThat(newResolvedProperty.isModified, equalTo(true))
    assertThat(newResolvedProperty.getValue(INTEGER_TYPE), equalTo(1))

    applyChangesAndReparse(buildModelInstance)
    verifyFileContents(buildFile, MODEL_SIMPLE_PROPERTY_IMPL_REBIND_RESOLVED_PROPERTY_EXPECTED)
  }

  @Test
  fun testPropertyInitializer() {
    writeToBuildFile(MODEL_SIMPLE_PROPERTY_IMPL_PROPERTY_INITIALIZER)

    val buildModelInstance = gradleBuildModel
    val extModel = buildModelInstance.ext()
    val findProperty = extModel.findProperty("prop25")
    val resolved = findProperty.resolve()
    var property: ResolvedPropertyModel? = null
    val notYetProp25 = Model.property(
      "description",
      resolvedValueGetter = { 26 },
      parsedPropertyGetter = { property },
      parsedPropertyInitializer = { property = resolved; resolved },
      getter = { asInt() },
      setter = { setValue(it) },
      parser = { value -> (::parseInt)(value) }
    ).bind(Model)

    assertThat(notYetProp25.getParsedValue().value, equalTo<ParsedValue<Any>>(ParsedValue.NotSet))
    notYetProp25.setParsedValue(25.asParsed())
    assertThat(notYetProp25.getParsedValue().value, equalTo<ParsedValue<Any>>(25.asParsed()))
  }
}
