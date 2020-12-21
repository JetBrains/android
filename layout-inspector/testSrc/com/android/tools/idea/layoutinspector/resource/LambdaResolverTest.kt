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

import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth
import com.intellij.psi.PsiElement
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.intellij.lang.annotations.Language
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

class LambdaResolverTest {
  private val projectRule = AndroidProjectRule.inMemory()

  @get:Rule
  val rules: RuleChain = RuleChain.outerRule(projectRule).around(EdtRule())

  @RunsInEdt
  @Test
  fun testFindLambdaLocation() {
    @Language("kotlin")
    val kotlinFile = """
      package com.company.app

      val l1: (Int) -> Int = { it }
      val l2: (Int) -> Int = {
        number -> number * number
      }

      class c1 {
        init {
          f2({1}, {2}, { f2({3}, {4}) })
        }
      }

      fun f1() {
        f2({1}, {2}, { f2({3}, {4}) })
        f2(l1, l2, l1)
      }

      fun f2(a1: (Int) -> Int, a2: (Int) -> Int, a3: (Int) -> Int = {-1}): Int {
        return if (a2(1) + a1(2) > 8) a1(1) else a3(2)
      }
    """.trimIndent()
    projectRule.fixture.addFileToProject("src/com/company/app/MainActivity.kt", kotlinFile)
    val resourceLookup = ResourceLookup(projectRule.project)
    assertLocation(resourceLookup.findLambdaLocation("com.company.app", "MainActivity.kt", "f1$1", 14, 14), "MainActivity.kt:15", "{1}")
    assertLocation(resourceLookup.findLambdaLocation("com.company.app", "MainActivity.kt", "f1$2", 14, 14), "MainActivity.kt:15", "{2}")
    assertLocation(resourceLookup.findLambdaLocation("com.company.app", "MainActivity.kt", "f1$3", 14, 14),
                   "MainActivity.kt:15", "{ f2({3}, {4}) }")
    assertLocation(resourceLookup.findLambdaLocation("com.company.app", "MainActivity.kt", "f1$3$1", 14, 14), "MainActivity.kt:15", "{3}")
    assertLocation(resourceLookup.findLambdaLocation("com.company.app", "MainActivity.kt", "f1$3$2", 14, 14), "MainActivity.kt:15", "{4}")
    assertLocation(resourceLookup.findLambdaLocation("com.company.app", "MainActivity.kt", "f2$1", 18, 18), "MainActivity.kt:19", "{-1}")
    assertLocation(resourceLookup.findLambdaLocation("com.company.app", "MainActivity.kt", "$1", 9, 9), "MainActivity.kt:10", "{1}")
    assertLocation(resourceLookup.findLambdaLocation("com.company.app", "MainActivity.kt", "$2", 9, 9), "MainActivity.kt:10", "{2}")
    assertLocation(resourceLookup.findLambdaLocation("com.company.app", "MainActivity.kt", "$3", 9, 9),
                   "MainActivity.kt:10", "{ f2({3}, {4}) }")
    assertLocation(resourceLookup.findLambdaLocation("com.company.app", "MainActivity.kt", "$3$1", 9, 9), "MainActivity.kt:10", "{3}")
    assertLocation(resourceLookup.findLambdaLocation("com.company.app", "MainActivity.kt", "$3$2", 9, 9), "MainActivity.kt:10", "{4}")
    assertLocation(resourceLookup.findLambdaLocation("com.company.app", "MainActivity.kt", "l1$1", 2, 2), "MainActivity.kt:3", "{ it }")
    assertLocation(resourceLookup.findLambdaLocation("com.company.app", "MainActivity.kt", "l2$1", 4, 5), "MainActivity.kt:4",
                   """
                   {
                     number -> number * number
                   }
                   """.trimIndent())
  }

  private fun assertLocation(location: SourceLocation?, source: String, text: String) {
    Truth.assertThat(location).isNotNull()
    Truth.assertThat(location?.source).isEqualTo(source)
    Truth.assertThat((location?.navigatable as? PsiElement)?.text).isEqualTo(text)
  }
}
