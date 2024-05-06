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
package com.android.tools.idea.run.deployment.liveedit

import com.android.tools.idea.run.deployment.liveedit.analysis.createKtFile
import com.android.tools.idea.run.deployment.liveedit.analysis.disableLiveEdit
import com.android.tools.idea.run.deployment.liveedit.analysis.initialCache
import com.android.tools.idea.run.deployment.liveedit.analysis.modifyKtFile
import com.android.tools.idea.testing.AndroidProjectRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.test.assertNotNull


@RunWith(JUnit4::class)
class AccessorCompileTest {
  @get:Rule
  var projectRule = AndroidProjectRule.inMemory()

  @Before
  fun setUp() {
    setUpComposeInProjectFixture(projectRule)
    disableLiveEdit()
  }

  @Test
  fun test() {
    val file = projectRule.createKtFile("Test.kt", """
      class Test {
        private var field = 0
        fun test() {
          val x = {
          }
        }
        private fun doThing() {}
      }
    """)
    val cache = projectRule.initialCache(listOf(file))
    projectRule.modifyKtFile(file, """
    class Test {
        private var field = 0
        fun test() {
          val x = {
            doThing()
            field = 1
          }
        }
        private fun doThing() {}
      }
    """.trimIndent())

    // Ensure this does not throw an incompatible change exception due to added method
    val output = compile(file, cache)
    val irClasses = output.irClasses.associateBy { it.name }
    assertNotNull(irClasses["Test"]?.methods?.find { it.name.startsWith("access\$doThing") })
    assertNotNull(irClasses["Test"]?.methods?.find { it.name.startsWith("access\$setField") })
  }
}

