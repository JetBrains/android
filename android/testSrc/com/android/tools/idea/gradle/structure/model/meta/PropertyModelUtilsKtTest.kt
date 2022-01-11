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
import com.android.tools.idea.gradle.structure.PROPERTY_MODEL_UTILS_TEST_AS_FILE
import com.android.tools.idea.gradle.structure.PROPERTY_MODEL_UTILS_TEST_AS_LANGUAGE_LEVEL
import com.android.tools.idea.gradle.structure.PROPERTY_MODEL_UTILS_TEST_DSL_TEXT
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.RunsInEdt
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import java.io.File

@RunsInEdt
class PropertyModelUtilsKtTest : PsdGradleFileModelTestCase() {

  private fun GradlePropertyModel.wrap(): ResolvedPropertyModel = resolve()

  @Test
  fun testAsString() {
    writeToBuildFile(PROPERTY_MODEL_UTILS_TEST_DSL_TEXT)

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
    writeToBuildFile(PROPERTY_MODEL_UTILS_TEST_DSL_TEXT)

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
    writeToBuildFile(PROPERTY_MODEL_UTILS_TEST_DSL_TEXT)

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
    writeToBuildFile(PROPERTY_MODEL_UTILS_TEST_DSL_TEXT)

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
    writeToBuildFile(PROPERTY_MODEL_UTILS_TEST_AS_FILE)

    val extModel = gradleBuildModel.ext()

    val propFile = extModel.findProperty("propFile").wrap()

    assertThat(propFile.asFile(), equalTo(File("tmp1")))
  }

  @Test
  fun testAsLanguageLevel() {
    writeToBuildFile(PROPERTY_MODEL_UTILS_TEST_AS_LANGUAGE_LEVEL)

    val compileOptionsModel = gradleBuildModel.android().compileOptions()

    val sourceCompatibility = compileOptionsModel.sourceCompatibility().wrap()
    val targetCompatibility = compileOptionsModel.targetCompatibility().wrap()

    assertThat(sourceCompatibility.asLanguageLevel(), equalTo(LanguageLevel.JDK_1_8))
    assertThat(targetCompatibility.asLanguageLevel(), equalTo(LanguageLevel.JDK_11))
  }

  @Test
  fun testDslText() {
    writeToBuildFile(PROPERTY_MODEL_UTILS_TEST_DSL_TEXT)

    val extModel = gradleBuildModel.ext()

    val propValue = extModel.findProperty("propValue").wrap()
    val prop25 = extModel.findProperty("prop25").wrap()
    val propTrue = extModel.findProperty("propTrue").wrap()
    val propRef = extModel.findProperty("propRef").wrap()
    val propInterpolated = extModel.findProperty("propInterpolated").wrap()
    val propUnresolved = extModel.findProperty("propUnresolved").wrap()
    val propOtherExpression1 = extModel.findProperty("propOtherExpression1").wrap()
    val propOtherExpression2 = extModel.findProperty("propOtherExpression2").wrap()

    assertThat(propValue.dslText(effectiveValueIsNull = false), equalTo<Annotated<DslText>?>(DslText.Literal.annotated()))
    assertThat(prop25.dslText(effectiveValueIsNull = false), equalTo<Annotated<DslText>?>(DslText.Literal.annotated()))
    assertThat(prop25.dslText(effectiveValueIsNull = true), equalTo<Annotated<DslText>?>(DslText.OtherUnparsedDslText("25").annotated()))
    assertThat(propTrue.dslText(effectiveValueIsNull = false), equalTo<Annotated<DslText>?>(DslText.Literal.annotated()))
    assertThat(propRef.dslText(effectiveValueIsNull = false), equalTo<Annotated<DslText>?>(DslText.Reference("propValue").annotated()))
    assertThat(propInterpolated.dslText(effectiveValueIsNull = false),
               equalTo<Annotated<DslText>?>(DslText.InterpolatedString("${'$'}{prop25}").annotated()))
    assertThat(propUnresolved.dslText(effectiveValueIsNull = true), equalTo<Annotated<DslText>?>(
      DslText.Reference("unresolvedReference").annotateWithError("Unresolved reference: unresolvedReference")))
    // This following statement tests the case of known unresolved references which are resolved by property getters.
    assertThat(propUnresolved.dslText(effectiveValueIsNull = false), equalTo<Annotated<DslText>?>(
      DslText.Reference("unresolvedReference").annotated()))
    assertThat(propOtherExpression1.dslText(effectiveValueIsNull = true),
               equalTo<Annotated<DslText>?>(DslText.OtherUnparsedDslText("z(1)").annotated()))
    assertThat(propOtherExpression2.dslText(effectiveValueIsNull = true),
               equalTo<Annotated<DslText>?>(DslText.OtherUnparsedDslText("1 + 2").annotated()))
    // This following statements test the case of known invalid expressions which are resolved by property getters.
    assertThat(propOtherExpression1.dslText(effectiveValueIsNull = false),
               equalTo<Annotated<DslText>?>(DslText.OtherUnparsedDslText("z(1)").annotated()))
    assertThat(propOtherExpression2.dslText(effectiveValueIsNull = false),
               equalTo<Annotated<DslText>?>(DslText.OtherUnparsedDslText("1 + 2").annotated()))
  }
}