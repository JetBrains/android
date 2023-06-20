/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.resource

import com.android.testutils.TestUtils
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.psi.PsiElement
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class LambdaResolverTest {
  // TODO: Investigate why inMemory() fails on Windows...
  @get:Rule val projectRule = AndroidProjectRule.onDisk()

  @Before
  fun before() {
    val fixture = projectRule.fixture
    fixture.testDataPath = TestUtils.resolveWorkspacePath("tools/adt/idea/layout-inspector/testData/compose").toString()
    fixture.copyFileToProject("java/androidx/compose/runtime/Composable.kt")
    fixture.copyFileToProject("java/com/example/MyLambdas.kt")
  }

  @Test
  fun testFindLambdaLocation() = runReadAction {
    checkLambda("1", 34, 34, 34, "{ 1 }")
    checkLambda("2", 34, 34, 34, "{ 2 }")
    checkLambda("3", 34, 34, 34, "{ f2({ 3 }, { 4 }) }")
    checkLambda("3$1", 34, 34, 34, "{ 3 }")
    checkLambda("3$2", 34, 34, 34, "{ 4 }")
    checkLambda("1", 38, 38, 38, "{ -1 }")
    checkLambda("1", 26, 26, 26, "{ 1 }")
    checkLambda("2", 26, 26, 26, "{ 2 }")
    checkLambda("3", 26, 26, 26, "{ f2({ 3 }, { 4 }) }")
    checkLambda("3$1", 26, 26, 26, "{ 3 }")
    checkLambda("3$2", 26, 26, 26, "{ 4 }")
    checkLambda("9$2", 28, 28, 28, "{ 2 }")
    checkLambda("1", 5, 5, 5, "{ it }")
    checkLambda("1", 8, 8, 6, """
      { number ->
        // The line numbers from JVMTI of this lambda, starts AFTER this comment...
        number * number
      }
      """.trimIndent())
    checkLambda("1", 50, 54, 49, """
      {
        intArrayOf(
          1,
          2,
          3,
          4
        )
      }
      """.trimIndent())
    checkLambda("1", 10, 10, 10, null) // A function reference should not be found as a lambda expr
    checkLambda("1", 100, 120, 89, null) // Lambda of inline function (lines are out of range)
  }

  @Test
  fun testFindLambdaLocationWithinComposable() = runReadAction {
    checkLambda("1", 79, 79, 79, "{ it - 1 }")
    checkLambda("lambda-10\$1", 80, 86, 79, """
      {
        Element(l1 = { it + 1 }, l2 = { Element() }, l3 = { it + 2 }) {
          Element(l1 = { it + 3 }, l3 = { it + 4 }) {
            Element()
            Element(l1 = { 1 })
          }
        }
      }
      """.trimIndent())
    checkLambda("lambda-10\$1\$1", 80, 80, 80, "{ it + 1 }")
    checkLambda("lambda-10\$1\$2", 80, 80, 80, "{ Element() }")
    checkLambda("lambda-10\$1\$3", 80, 80, 80, "{ it + 2 }")
    checkLambda("lambda-9\$1", 81, 85, 80, """
      {
        Element(l1 = { it + 3 }, l3 = { it + 4 }) {
          Element()
          Element(l1 = { 1 })
        }
      }
      """.trimIndent())
    checkLambda("lambda-9\$1\$1", 81, 81, 81, "{ it + 3 }")
    checkLambda("lambda-9\$1\$2", 81, 81, 81, "{ it + 4 }")
    checkLambda("lambda-8\$1", 82, 83, 81, """
      {
        Element()
        Element(l1 = { 1 })
      }
      """.trimIndent())
    checkLambda("lambda-8\$1\$1", 83, 83, 83, "{ 1 }")
  }

  @Test
  fun testFindFunctionReferenceLocation() = runReadAction {
    check("1", "f3", 10, 10, 10, "::f3")
    check("1", "fx", 23, 23, 23, "::fx")
    check("4", "f3", 27, 27, 27, "::f3")
    check("5", "f4", 27, 27, 27, "::f4")
    check("1", "f5", 27, 27, 27, "::f5")
    check("2", "f6", 27, 27, 27, "::f6")
    check("8", "f6", 28, 28, 28, "::f6")
    check("1", "f1", 5, 5, 5, null) // A lambda expression should not be found as a fct ref
  }

  @Test
  fun testFindLambdaFromUnknownFile() = runReadAction {
    val resourceLookup = ResourceLookup(projectRule.project)
    val result = resourceLookup.findLambdaLocation("com.example", "MyOtherFile.kt", "l$1", "", 102, 107)
    assertThat(result.source).isEqualTo("MyOtherFile.kt:unknown")
    assertThat(result.navigatable).isNull()
  }

  private fun checkLambda(lambdaName: String, startLine: Int, endLine: Int, expectedStartLine: Int, expectedText: String? = null) =
    check(lambdaName, functionName = "", startLine, endLine, expectedStartLine, expectedText)

  private fun check(
    lambdaName: String,
    functionName: String,
    startLine: Int,
    endLine: Int,
    expectedStartLine: Int,
    expectedText: String?
  ) {
    val resourceLookup = ResourceLookup(projectRule.project)
    val result = resourceLookup.findLambdaLocation("com.example", "MyLambdas.kt", lambdaName, functionName, startLine, endLine)
    if (expectedText == null) {
      val fileDescriptor = result.navigatable as? OpenFileDescriptor
      assertThat(fileDescriptor).isNotNull()
      assertThat(fileDescriptor?.line).isEqualTo(expectedStartLine - 1)
      assertThat(fileDescriptor?.column).isEqualTo(0)
    }
    else {
      var actual = (result.navigatable as? PsiElement)?.text?.trim()
      if (actual != null && actual.startsWith("{\n")) {
        actual = "{\n" + actual.substring(2).trimIndent()
      }
      assertThat(actual).isEqualTo(expectedText.trim().trimIndent())
    }
    assertThat(result.source).isEqualTo("MyLambdas.kt:$expectedStartLine")
  }
}
