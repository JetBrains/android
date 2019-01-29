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
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import java.io.File

class PropertyModelUtilsKtTest : GradleFileModelTestCase() {

  private fun GradlePropertyModel.wrap(): ResolvedPropertyModel = resolve()

  @Test
  fun testAsString() {
    val text = """
               ext {
                 propValue = 'value'
                 prop25 = 25
                 propTrue = true
                 propRef = propValue
                 propInterpolated = "${'$'}{prop25}"
                 propUnresolved = unresolvedReference
                 propOtherExpression1 = z(1)
                 propOtherExpression2 = 1 + 2
               }""".trimIndent()
    writeToBuildFile(text)

    val extModel = gradleBuildModel.ext()

    val propValue = extModel.findProperty("propValue").wrap()
    val prop25 = extModel.findProperty("prop25").wrap()
    val propTrue = extModel.findProperty("propTrue").wrap()
    val propRef = extModel.findProperty("propRef").wrap()
    val propInterpolated = extModel.findProperty("propInterpolated").wrap()
    val propUnresolved = extModel.findProperty("propUnresolved").wrap()
    val propOtherExpression1 = extModel.findProperty("propOtherExpression1").wrap()
    val propOtherExpression2 = extModel.findProperty("propOtherExpression2").wrap()

    assertThat(propValue.asString(), equalTo("value"))
    assertThat(prop25.asString(), equalTo("25"))
    assertThat(propTrue.asString(), nullValue())
    assertThat(propRef.asString(), equalTo("value"))
    assertThat(propInterpolated.asString(), equalTo("25"))
    assertThat(propUnresolved.asString(), nullValue())
    assertThat(propOtherExpression1.asString(), nullValue())
    assertThat(propOtherExpression2.asString(), nullValue())
  }

  @Test
  fun testAsAny() {
    val text = """
               ext {
                 propValue = 'value'
                 prop25 = 25
                 propTrue = true
                 propRef = propValue
                 propInterpolated = "${'$'}{prop25}"
                 propUnresolved = unresolvedReference
                 propOtherExpression1 = z(1)
                 propOtherExpression2 = 1 + 2
               }""".trimIndent()
    writeToBuildFile(text)

    val extModel = gradleBuildModel.ext()

    val propValue = extModel.findProperty("propValue").wrap()
    val prop25 = extModel.findProperty("prop25").wrap()
    val propTrue = extModel.findProperty("propTrue").wrap()
    val propRef = extModel.findProperty("propRef").wrap()
    val propInterpolated = extModel.findProperty("propInterpolated").wrap()
    val propUnresolved = extModel.findProperty("propUnresolved").wrap()
    val propOtherExpression1 = extModel.findProperty("propOtherExpression1").wrap()
    val propOtherExpression2 = extModel.findProperty("propOtherExpression2").wrap()

    assertThat(propValue.asAny(), equalTo<Any>("value"))
    assertThat(prop25.asAny(), equalTo<Any>(25))
    assertThat(propTrue.asAny(), equalTo<Any>(true))
    assertThat(propRef.asAny(), equalTo<Any>("value"))
    assertThat(propInterpolated.asAny(), equalTo<Any>("25"))
    assertThat(propUnresolved.asAny(), nullValue())
    assertThat(propOtherExpression1.asAny(), nullValue())
    assertThat(propOtherExpression2.asAny(), nullValue())
  }

  @Test
  fun testAsInt() {
    val text = """
               ext {
                 propValue = 'value'
                 prop25 = 25
                 propTrue = true
                 propRef = propValue
                 propInterpolated = "${'$'}{prop25}"
                 propUnresolved = unresolvedReference
                 propOtherExpression1 = z(1)
                 propOtherExpression2 = 1 + 2
               }""".trimIndent()
    writeToBuildFile(text)

    val extModel = gradleBuildModel.ext()

    val propValue = extModel.findProperty("propValue").wrap()
    val prop25 = extModel.findProperty("prop25").wrap()
    val propTrue = extModel.findProperty("propTrue").wrap()
    val propRef = extModel.findProperty("propRef").wrap()
    val propInterpolated = extModel.findProperty("propInterpolated").wrap()
    val propUnresolved = extModel.findProperty("propUnresolved").wrap()
    val propOtherExpression1 = extModel.findProperty("propOtherExpression1").wrap()
    val propOtherExpression2 = extModel.findProperty("propOtherExpression2").wrap()

    assertThat(propValue.asInt(), nullValue())
    assertThat(prop25.asInt(), equalTo(25))
    assertThat(propTrue.asInt(), nullValue())
    assertThat(propRef.asInt(), nullValue())
    assertThat(propInterpolated.asInt(), nullValue())
    assertThat(propUnresolved.asInt(), nullValue())
    assertThat(propOtherExpression1.asInt(), nullValue())
    assertThat(propOtherExpression2.asInt(), nullValue())
  }

  @Test
  fun testAsBoolean() {
    val text = """
               ext {
                 propValue = 'value'
                 prop25 = 25
                 propTrue = true
                 propRef = propValue
                 propInterpolated = "${'$'}{prop25}"
                 propUnresolved = unresolvedReference
                 propOtherExpression1 = z(1)
                 propOtherExpression2 = 1 + 2
               }""".trimIndent()
    writeToBuildFile(text)

    val extModel = gradleBuildModel.ext()

    val propValue = extModel.findProperty("propValue").wrap()
    val prop25 = extModel.findProperty("prop25").wrap()
    val propTrue = extModel.findProperty("propTrue").wrap()
    val propRef = extModel.findProperty("propRef").wrap()
    val propInterpolated = extModel.findProperty("propInterpolated").wrap()
    val propUnresolved = extModel.findProperty("propUnresolved").wrap()
    val propOtherExpression1 = extModel.findProperty("propOtherExpression1").wrap()
    val propOtherExpression2 = extModel.findProperty("propOtherExpression2").wrap()

    assertThat(propValue.asBoolean(), nullValue())
    assertThat(prop25.asBoolean(), nullValue())
    assertThat(propTrue.asBoolean(), equalTo(true))
    assertThat(propRef.asBoolean(), nullValue())
    assertThat(propInterpolated.asBoolean(), nullValue())
    assertThat(propUnresolved.asBoolean(), nullValue())
    assertThat(propOtherExpression1.asBoolean(), nullValue())
    assertThat(propOtherExpression2.asBoolean(), nullValue())
  }

  @Test
  fun testAsFile() {
    val text = """
               ext {
                 propFile = 'tmp1'
               }""".trimIndent()
    writeToBuildFile(text)

    val extModel = gradleBuildModel.ext()

    val propFile = extModel.findProperty("propFile").wrap()

    assertThat(propFile.asFile(), equalTo(File("tmp1")))
  }

  @Test
  fun testDslText() {
    val text = """
               ext {
                 propValue = 'value'
                 prop25 = 25
                 propTrue = true
                 propRef = propValue
                 propInterpolated = "${'$'}{prop25}"
                 propUnresolved = unresolvedReference
                 propOtherExpression1 = z(1)
                 propOtherExpression2 = 1 + 2
               }""".trimIndent()
    writeToBuildFile(text)

    val extModel = gradleBuildModel.ext()

    val propValue = extModel.findProperty("propValue").wrap()
    val prop25 = extModel.findProperty("prop25").wrap()
    val propTrue = extModel.findProperty("propTrue").wrap()
    val propRef = extModel.findProperty("propRef").wrap()
    val propInterpolated = extModel.findProperty("propInterpolated").wrap()
    val propUnresolved = extModel.findProperty("propUnresolved").wrap()
    val propOtherExpression1 = extModel.findProperty("propOtherExpression1").wrap()
    val propOtherExpression2 = extModel.findProperty("propOtherExpression2").wrap()

    assertThat(propValue.dslText(), equalTo<Annotated<DslText>?>(DslText.Literal.annotated()))
    assertThat(prop25.dslText(), equalTo<Annotated<DslText>?>(DslText.Literal.annotated()))
    assertThat(propTrue.dslText(), equalTo<Annotated<DslText>?>(DslText.Literal.annotated()))
    assertThat(propRef.dslText(), equalTo<Annotated<DslText>?>(DslText.Reference("propValue").annotated()))
    assertThat(propInterpolated.dslText(), equalTo<Annotated<DslText>?>(DslText.InterpolatedString("${'$'}{prop25}").annotated()))
    assertThat(propUnresolved.dslText(), equalTo<Annotated<DslText>?>(
      DslText.Reference("unresolvedReference").annotateWithError("Unresolved reference: unresolvedReference")))
    assertThat(propOtherExpression1.dslText(), equalTo<Annotated<DslText>?>(DslText.OtherUnparsedDslText("z(1)").annotated()))
    assertThat(propOtherExpression2.dslText(), equalTo<Annotated<DslText>?>(DslText.OtherUnparsedDslText("1 + 2").annotated()))
  }
}