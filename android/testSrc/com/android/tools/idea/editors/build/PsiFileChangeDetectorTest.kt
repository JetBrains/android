/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.editors.build

import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.executeAndSave
import com.android.tools.idea.testing.replaceText
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.psi.KtProperty
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class PsiFileChangeDetectorTest {
  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  private val hashPsiFileChangeDetector = HashPsiFileChangeDetector()
  lateinit var file: PsiFile

  @Before
  fun setup() {
    file = projectRule.fixture.configureByText(
      "Test.kt",
      //language=kotlin
      """
      //Placeholder

      // A comment
      class ClassA {
        /**
        A multiline
        comment
        */
        private val classProperty = 123L

        fun MethodInClassA() { // comment
          val aMethod = "This is a test"
        }
      }

      fun TestMethod1() {
        val bMethod = "This is a test" // comment
        println("Hello world")
        println("Hi")
      }

      fun TestMethod2() {
      }
    """.trimIndent())
    hashPsiFileChangeDetector.markFileAsUpToDate(file)
  }

  private fun PsiFile.assertChanged(detector: PsiFileChangeDetector = hashPsiFileChangeDetector) =
    assertTrue(detector.hasFileChanged(this))

  private fun PsiFile.assertNoChanges(detector: PsiFileChangeDetector = hashPsiFileChangeDetector) {
    if (detector.hasFileChanged(this)) {
      fail("No changes were expected")
    }
  }

  @Test
  fun `method name change is detected`() {
    projectRule.fixture.editor.executeAndSave {
      replaceText("TestMethod1", "TestMethod3")
    }
    file.assertChanged()
  }

  @Test
  fun `undone change should report the file as not changed`() {
    projectRule.fixture.editor.executeAndSave {
      replaceText("TestMethod1", "TestMethod3")
    }
    file.assertChanged()
    projectRule.fixture.editor.executeAndSave {
      replaceText("TestMethod3", "TestMethod1")
    }
    file.assertNoChanges()
  }

  @Test
  fun `remove property is detected`() {
    projectRule.fixture.editor.executeAndSave {
      replaceText("private val classProperty = 123L", "")
    }
    file.assertChanged()
  }

  @Test
  fun `adding a new file level property is detected`() {
    projectRule.fixture.editor.executeAndSave {
      replaceText("//Placeholder", "val b = 123.0")
    }
    file.assertChanged()
  }

  @Test
  fun `expensive hashing is not used when the file has no changes`() {
    var hasBeenInvoked = false
    val detectorWithFilter = HashPsiFileChangeDetector.forTest {
      hasBeenInvoked = true
      true
    }
    detectorWithFilter.markFileAsUpToDate(file)
    assertTrue("the hash calculation is expected to be invoked the first time", hasBeenInvoked)
    hasBeenInvoked = false
    projectRule.fixture.editor.executeAndSave {
      replaceText("TestMethod1", "TestMethod3")
    }
    detectorWithFilter.hasFileChanged(file, true)
    assertTrue(hasBeenInvoked)
    hasBeenInvoked = false
    detectorWithFilter.hasFileChanged(file, true)
    assertFalse("hash calculation invoked when the file had no changes", hasBeenInvoked)
  }
}