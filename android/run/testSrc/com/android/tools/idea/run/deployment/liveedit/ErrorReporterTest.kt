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

import com.android.tools.idea.run.deployment.liveedit.LiveEditUpdateException.Companion.analysisError
import com.android.tools.idea.run.deployment.liveedit.LiveEditUpdateException.Companion.desugarFailure
import com.android.tools.idea.run.deployment.liveedit.LiveEditUpdateException.Companion.internalErrorCompileCommandException
import com.android.tools.idea.run.deployment.liveedit.LiveEditUpdateException.Companion.internalErrorNoCompilerOutput
import com.android.tools.idea.run.deployment.liveedit.LiveEditUpdateException.Companion.kotlinEap
import com.android.tools.idea.run.deployment.liveedit.LiveEditUpdateException.Companion.nonKotlin
import com.android.tools.idea.run.deployment.liveedit.LiveEditUpdateException.Companion.unsupportedSourceModificationAddedMethod
import com.android.tools.idea.run.deployment.liveedit.LiveEditUpdateException.Companion.unsupportedSourceModificationRemovedMethod
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.psi.PsiFile
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(JUnit4::class)
class ErrorReporterTest {
  @get:Rule
  var projectRule = AndroidProjectRule.inMemory().withKotlin()
  lateinit var file1: PsiFile
  lateinit var file2: PsiFile

  @Before
  fun setUp() {
    setUpComposeInProjectFixture(projectRule)
    file1= projectRule.fixture.configureByText("fileOne.java", "")
    file2= projectRule.fixture.configureByText("fileTwo.java", "")
  }

  @Test
  fun `Global Error`() {
    eq(kotlinEap(),
      "Compilation Error.\nLive Edit does not support running with this Kotlin Plugin version and will only work with the bundled Kotlin Plugin.")
  }

  @Test
  fun `Error with file name`() {
    eq(nonKotlin(file1), "Modifying a non-Kotlin file is not supported in fileOne.java.")
  }

  @Test
  fun `Syntax Errors`() {
    val msg = "Cannot Resolve Symbol A"
    val fakeException = RuntimeException("This exception is fake")
    eq(analysisError(msg), "Resolution Analysis Error.\nCannot Resolve Symbol A.")
    eq(analysisError(msg, file2), "Resolution Analysis Error in fileTwo.java.\nCannot Resolve Symbol A.")
    eq(analysisError(msg, file2, fakeException), "Resolution Analysis Error in fileTwo.java.\nCannot Resolve Symbol A.\n"+
                                                 "${fakeException.stackTraceToString()}")
  }

  @Test
  fun `ClassDiffer Exceptions`() {
    // These type of exceptions might not have a reference to the PsiFile
    eq(unsupportedSourceModificationAddedMethod("a.class", "Currently not support adding methods"),
       "Unsupported change in a.class.\nCurrently not support adding methods.")
    eq(unsupportedSourceModificationRemovedMethod("a.class", "Currently not support removing methods"),
       "Unsupported change in a.class.\nCurrently not support removing methods.")
  }

  @Test
  fun `Desugaring Errors`() {
    eq(desugarFailure("Failed to Desugar"), "Live Edit post-processing failure.\n" +
                                            "Failed to Desugar.")
    eq(desugarFailure("Failed to Desugar", "output.jar"), "Live Edit post-processing failure in output.jar.\n" +
                                                          "Failed to Desugar.")
    val fakeException = RuntimeException("This exception is fake")
    eq(desugarFailure("Failed to Desugar", "output.jar", fakeException),
       "Live Edit post-processing failure in output.jar.\nFailed to Desugar.\n${fakeException.stackTraceToString()}")
  }

  @Test
  fun `Internal Errors`() {
    val fakeException = RuntimeException("This exception is fake")
    eq(internalErrorNoCompilerOutput(file2), "Internal Error in fileTwo.java.\nNo compiler output.")
    eq(internalErrorCompileCommandException(file2, fakeException), "Internal Error in fileTwo.java.\n" +
                                                                   "Unexpected error during compilation command.\n" +
                                                                   "${fakeException.stackTraceToString()}")

  }

  private fun eq(exception: LiveEditUpdateException, expected: String) {
    var msg = errorMessage(exception)
    assertEquals(expected, msg)
  }

  private fun match(exception: LiveEditUpdateException, pattern: String) {
    var msg = errorMessage(exception)
    assertTrue("${msg}") {
      Regex(pattern).containsMatchIn(msg)
    }
  }
}