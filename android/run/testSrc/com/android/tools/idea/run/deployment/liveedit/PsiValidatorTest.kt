/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.run.deployment.liveedit

import com.android.tools.idea.run.deployment.liveedit.analysis.createKtFile
import com.android.tools.idea.run.deployment.liveedit.analysis.modifyKtFile
import com.android.tools.idea.run.deployment.liveedit.tokens.FakeBuildSystemLiveEditServices
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.application.ReadAction
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.intellij.lang.annotations.Language
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class PsiValidatorTest {
  @get:Rule
  var projectRule = AndroidProjectRule.inMemory().withKotlin()

  private val fakeBuildSystemLiveEditServices = FakeBuildSystemLiveEditServices()

  @Before
  fun setUp() {
    fakeBuildSystemLiveEditServices.register(projectRule.testRootDisposable)
    setUpComposeInProjectFixture(projectRule)
  }

  @Test
  fun `modify initial property value`() {
    val exceptions = testCase(
      """
      val x = 100
      val y = 100
      """.trimIndent(),
      """
      val x = 999
      val y = 100
      """.trimIndent(),
    )

    assertEquals(1, exceptions.size)
    assertEquals(exceptions.single().error, LiveEditUpdateException.Error.UNSUPPORTED_SRC_CHANGE_FIELD_MODIFIED)
    assertTrue(exceptions.single().details.contains("modified property"))
  }

  @Test
  fun `modify property value in delegate`() {
    val exceptions = testCase(
      """
      val x: Int by lazy {
        100
      }
      """.trimIndent(),
      """
      val x: Int by lazy {
        999
      }
      """.trimIndent())
    assertEquals(1, exceptions.size)
    assertEquals(exceptions.single().error, LiveEditUpdateException.Error.UNSUPPORTED_SRC_CHANGE_FIELD_MODIFIED)
    assertTrue(exceptions.single().details.contains("modified property"))
  }

  @Test
  fun `add enum entry`() {
    val exceptions = testCase(
      """
      enum class Simple {
        VALUE_A,
        VALUE_B,
        VALUE_C
      }
      """.trimIndent(),
      """
      enum class Simple {
        VALUE_A,
        VALUE_B,
        VALUE_C,
        VALUE_D
      }
      """.trimIndent())
    assertEquals(1, exceptions.size)
    assertEquals(exceptions.single().error, LiveEditUpdateException.Error.UNSUPPORTED_SRC_CHANGE_ENUM)
    assertTrue(exceptions.single().details.contains("added enum entry"))
  }

  @Test
  fun `remove enum entry`() {
    val exceptions = testCase(
      """
      enum class Simple {
        VALUE_A,
        VALUE_B,
        VALUE_C
      }
      """.trimIndent(),
      """
      enum class Simple {
        VALUE_A,
        VALUE_B,
      }
      """.trimIndent())

    assertEquals(1, exceptions.size)
    assertEquals(exceptions.single().error, LiveEditUpdateException.Error.UNSUPPORTED_SRC_CHANGE_ENUM)
    assertTrue(exceptions.single().details.contains("removed enum entry"))
  }

  @Test
  fun `modify enum initializer`() {
    val exceptions = testCase(
      """
      enum class Complex(x: String = "") {
        VALUE_A("Hello"),
        VALUE_B,
        VALUE_C("Goodbye"),
      }
      """.trimIndent(),
      """
      enum class Complex(x: String = "") {
        VALUE_A("Hello"),
        VALUE_B("CHANGED"),
        VALUE_C("Goodbye"),
      }
      """.trimIndent())

    assertEquals(1, exceptions.size)
    assertEquals(exceptions.single().error, LiveEditUpdateException.Error.UNSUPPORTED_SRC_CHANGE_ENUM)
    assertTrue(exceptions.single().details.contains("modified enum initializer"))
  }

  @Test
  fun `modify enum order`() {
    val exceptions = testCase(
      """
      enum class Complex(x: String = "") {
        VALUE_A("Hello"),
        VALUE_B,
        VALUE_C("Goodbye"),
      }
      """.trimIndent(),
      """
      enum class Complex(x: String = "") {
        VALUE_A("Hello"),
        VALUE_C("Goodbye"),
        VALUE_B,
      }
      """.trimIndent())

    assertEquals(1, exceptions.size)
    assertEquals(exceptions.single().error, LiveEditUpdateException.Error.UNSUPPORTED_SRC_CHANGE_ENUM)
    assertTrue(exceptions.single().details.contains("modified order of enum"))
  }

  @Test
  fun `modify constructor`() {
    val exceptions = testCase(
      """
      class Foo(val a: Int, val b: Int) {
        constructor(a: String, b: String): this(0, 0)
        constructor(a: Double, b: Double): this(0, 0)
      }
      """.trimIndent(),
      """
      class Foo(val a: Int, val b: Int) {
        constructor(a: String, b: String): this(0, 0)
        constructor(a: Double, b: Double): this(1, 1)
      }
      """.trimIndent())
    assertEquals(1, exceptions.size)
    assertEquals(exceptions.single().error, LiveEditUpdateException.Error.UNSUPPORTED_SRC_CHANGE_CONSTRUCTOR)
    assertTrue(exceptions.single().details.contains("modified constructor"))
  }

  @Test
  fun `validator should ignore newlines and comments`() {
    val exceptions = testCase(
      """
      class Foo(val a: Int, val b: Int) {
        constructor(a: String, b: String): this(0, 0) {}
        val foo: Int by lazy {
          100
        }
      }
      """.trimIndent(),
      """
      class Foo(val a: Int, val b: Int) {
        constructor(a: String, b: String): this(0, 0) {
        
        
        }
        val foo: Int by lazy {
          // Comment
          100
        }
      }
      """.trimIndent())
    assertEquals(0, exceptions.size)
  }

  private fun testCase(@Language("kotlin") original: String, @Language("kotlin") next: String): List<LiveEditUpdateException> {
    val file = projectRule.createKtFile("A.kt", original)
    val original = ReadAction.compute<PsiState, Throwable> { getPsiValidationState(file) }
    projectRule.modifyKtFile(file, next)
    val next = ReadAction.compute<PsiState, Throwable> { getPsiValidationState(file) }
    return ReadAction.compute<List<LiveEditUpdateException>, Throwable> { validatePsiChanges(original, next) }
  }
}