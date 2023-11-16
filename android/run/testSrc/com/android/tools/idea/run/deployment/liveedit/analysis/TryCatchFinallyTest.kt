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

class TryCatchFinallyTest {
  @get:Rule
  var projectRule = AndroidProjectRule.inMemory()

  @Before
  fun setUp() {
    setUpComposeInProjectFixture(projectRule)
  }

  @Test
  fun testLineNumberChange() {
    val original = projectRule.compileIr("""
      class A {
        fun method(): Int {
          return 0
        }
      }""", "A.kt", "A")

    val new = projectRule.compileIr("""
      class A {
        fun method(): Int {
          // This is a comment
          return 0
        }
      }""", "A.kt", "A")

    assertNoChanges(original, new)
  }

  @Test
  fun testTryBlock() {
    val original = projectRule.compileIr("""
      class A {
        fun method(): Int {
          try {
            return 0
          } catch (e: Exception) {
            return 1
          }
        }
      }""", "A.kt", "A")

    val new = projectRule.compileIr("""
      class A {
        fun method(): Int {
          try {
            var a = 1
            return 0
          } catch (e: Exception) {
            return 1
          }
        }
      }""", "A.kt", "A")

    assertChanges(original, new)
  }

  @Test
  fun testCatch() {
    val original = projectRule.compileIr("""
      class A {
        fun method(): Int {
          try {
            return 0
          } catch (e: Exception) {
            return 1
          }
        }
      }""", "A.kt", "A")

    val new = projectRule.compileIr("""
      class A {
        fun method(): Int {
          try {
            return 0
          } catch (e: IllegalArgumentException) {
            return 1
          }
        }
      }""", "A.kt", "A")

    assertChanges(original, new)
  }

  @Test
  fun testCatchBlock() {
    val original = projectRule.compileIr("""
      class A {
        fun method(): Int {
          try {
            return 0
          } catch (e: Exception) {
            return 1
          }
        }
      }""", "A.kt", "A")

    val new = projectRule.compileIr("""
      class A {
        fun method(): Int {
          try {
            return 0
          } catch (e: Exception) {
            var a = 1
            return 1
          }
        }
      }""", "A.kt", "A")

    assertChanges(original, new)
  }

  @Test
  fun testFinallyBlock() {
    val original = projectRule.compileIr("""
      class A {
        fun method(): Int {
          try {
            return 0
          } finally {
            return 1
          }
        }
      }""", "A.kt", "A")

    val new = projectRule.compileIr("""
      class A {
        fun method(): Int {
          try {
            return 0
          } finally {
            val a = 0
            return 1
          }
        }
      }""", "A.kt", "A")

    assertChanges(original, new)
  }
}