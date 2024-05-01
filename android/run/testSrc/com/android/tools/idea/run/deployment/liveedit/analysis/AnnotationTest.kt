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

import com.android.ddmlib.internal.FakeAdbTestRule
import com.android.tools.idea.run.deployment.liveedit.setUpComposeInProjectFixture
import com.android.tools.idea.testing.AndroidProjectRule
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AnnotationTest {
  private var projectRule = AndroidProjectRule.inMemory().withKotlin()
  private val fakeAdb: FakeAdbTestRule = FakeAdbTestRule("30")
  @get:Rule
  val chain = RuleChain.outerRule(projectRule).around(fakeAdb)

  @Before
  fun setUp() {
    setUpComposeInProjectFixture(projectRule)
    disableLiveEdit()
  }

  @After
  fun tearDown() {
    enableLiveEdit()
  }

  @Test
  fun testAnnotationWithEnumParams() {
    val file = projectRule.createKtFile("A.kt", """
      enum class MyEnum { A, B }
      annotation class Q(val param: MyEnum)
      class A {
        @Q(MyEnum.A)
        val field: Int = 0
      }""")

    val original = projectRule.directApiCompileIr(file)["A"]!!

    projectRule.modifyKtFile(file, """
      enum class MyEnum { A, B }
      annotation class Q(val param: MyEnum)
      class A {
        @Q(MyEnum.B)
        val field: Int = 0
      }""")

    val new = projectRule.directApiCompileIr(file)["A"]!!

    assertNull(diff(original, original))
    assertNull(diff(new, new))

    val diff = diff(original, new)
    assertNotNull(diff)

    val inv = diff(new, original)
    assertNotNull(inv)
  }
}
