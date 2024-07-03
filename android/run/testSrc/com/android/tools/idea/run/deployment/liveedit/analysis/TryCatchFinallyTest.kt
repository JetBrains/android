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

class TryCatchFinallyTest {
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
  fun testLineNumberChange() {
    val file = projectRule.createKtFile("A.kt", """
      class A {
        fun method(): Int {
          return 0
        }
      }""".trimIndent())
    val original = projectRule.directApiCompileIr(file)["A"]!!

    projectRule.modifyKtFile(file, """
      class A {
        fun method(): Int {
          // This is a comment
          return 0
        }
      }""".trimIndent())
    val new = projectRule.directApiCompileIr(file)["A"]!!

    assertNoChanges(original, new)
  }

  @Test
  fun testTryBlock() {
    val file = projectRule.createKtFile("A.kt", """
      class A {
        fun method(): Int {
          try {
            return 0
          } catch (e: Exception) {
            return 1
          }
        }
      }""")
    val original = projectRule.directApiCompileIr(file)["A"]!!

    projectRule.modifyKtFile(file, """
      class A {
        fun method(): Int {
          try {
            var a = 1
            return 0
          } catch (e: Exception) {
            return 1
          }
        }
      }""")
    val new = projectRule.directApiCompileIr(file)["A"]!!

    assertChanges(original, new)
  }

  @Test
  fun testCatch() {
    val file =  projectRule.createKtFile("A.kt", """
      class A {
        fun method(): Int {
          try {
            return 0
          } catch (e: Exception) {
            return 1
          }
        }
      }""")
      val original = projectRule.directApiCompileIr(file)["A"]!!

      projectRule.modifyKtFile(file,"""
        class A {
          fun method(): Int {
            try {
              return 0
            } catch (e: IllegalArgumentException) {
              return 1
            }
          }
        }""")
      val new = projectRule.directApiCompileIr(file)["A"]!!

      assertChanges(original, new)
  }

  @Test
  fun testCatchBlock() {
    val file = projectRule.createKtFile("A.kt", """
      class A {
        fun method(): Int {
          try {
            return 0
          } catch (e: Exception) {
            return 1
          }
        }
      }""")
    val original = projectRule.directApiCompileIr(file)["A"]!!

    projectRule.modifyKtFile(file, """
      class A {
        fun method(): Int {
          try {
            return 0
          } catch (e: Exception) {
            var a = 1
            return 1
          }
        }
      }""")
    val new = projectRule.directApiCompileIr(file)["A"]!!

    assertChanges(original, new)
  }

  @Test
  fun testFinallyBlock() {
    val file = projectRule.createKtFile("A.kt", """
      class A {
        fun method(): Int {
          try {
            return 0
          } finally {
            return 1
          }
        }
      }""")
    val original = projectRule.directApiCompileIr(file)["A"]!!

    projectRule.modifyKtFile(file, """
      class A {
        fun method(): Int {
          try {
            return 0
          } finally {
            val a = 0
            return 1
          }
        }
      }""")
    val new = projectRule.directApiCompileIr(file)["A"]!!

    assertChanges(original, new)
  }
}