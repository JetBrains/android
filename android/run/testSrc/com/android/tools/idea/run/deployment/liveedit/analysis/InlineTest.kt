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
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class InlineTest {
  @get:Rule
  var projectRule = AndroidProjectRule.inMemory().withKotlin()

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
  fun testInline() {
    val file = projectRule.createKtFile("A.kt", """
      class A {
        fun inlineMethod(): Int {
          return 0
        }
        fun method() {
          inlineMethod()
        }
      }""")
    val original = projectRule.directApiCompileIr(file)["A"]!!
    assertNotNull(original)

    projectRule.modifyKtFile(file, """
      class A {
        inline fun inlineMethod(): Int {
          return 0
        }
        fun method() {
          inlineMethod()
        }
      }""")
    val new = projectRule.directApiCompileIr(file)["A"]!!
    assertNotNull(new)

    assertChanges(original, new)

    val oldMethod = original.methods.first { it.name == "inlineMethod" }
    assertFalse(oldMethod.isInline())

    val newMethod = new.methods.first { it.name == "inlineMethod" }
    assertTrue(newMethod.isInline())
  }

  @Test
  fun testNoInline() {
    val file = projectRule.createKtFile("A.kt", """
      class A {
        inline fun inlineMethod(first: () -> Int, second: () -> Int): Int {
          first()
          second()
          return 0
        }
        fun method() {
          inlineMethod({0}, {1})
        }
      }""")
    val original = projectRule.directApiCompileIr(file)["A"]!!

   projectRule.modifyKtFile(file, """
      class A {
        inline fun inlineMethod(first: () -> Int, noinline second: () -> Int): Int {
          first()
          second()
          return 0
        }
        fun method() {
          inlineMethod({0}, {1})
        }
      }""")
    val new = projectRule.directApiCompileIr(file)["A"]!!

    assertChanges(original, new)
  }
}