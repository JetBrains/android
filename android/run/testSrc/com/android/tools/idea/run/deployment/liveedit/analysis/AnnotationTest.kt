/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.run.deployment.liveedit.analysis

import com.android.tools.idea.run.deployment.liveedit.setUpComposeInProjectFixture
import com.android.tools.idea.testing.AndroidProjectRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AnnotationTest {
  @get:Rule
  var projectRule = AndroidProjectRule.inMemory()

  @Before
  fun setUp() {
    setUpComposeInProjectFixture(projectRule)
  }

  @Test
  fun testAnnotationWithEnumParams() {
    val original = projectRule.compileIr("""
      enum class MyEnum { A, B }
      annotation class Q(val param: MyEnum)
      class A {
        @Q(MyEnum.A)
        val field: Int = 0
      }""", "A.kt", "A")

    val new = projectRule.compileIr("""
      enum class MyEnum { A, B }
      annotation class Q(val param: MyEnum)
      class A {
        @Q(MyEnum.B)
        val field: Int = 0
      }""", "A.kt", "A")

    assertNull(diff(original, original))
    assertNull(diff(new, new))

    val diff = diff(original, new)
    assertNotNull(diff)

    val inv = diff(new, original)
    assertNotNull(inv)
  }
}
