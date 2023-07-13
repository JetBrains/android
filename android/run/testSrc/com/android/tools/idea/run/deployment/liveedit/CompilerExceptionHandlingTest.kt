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

import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.progress.ProcessCanceledException
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito

class CompilerExceptionHandlingTest {

  @get:Rule
  var projectRule = AndroidProjectRule.inMemory()

  @Before
  fun setUp() {
    setUpComposeInProjectFixture(projectRule)
  }

  @Test
  fun notDropProcessCancelException() {
    var file = projectRule.fixture.configureByText("A.kt", "fun foo() = 1")
    var input = Mockito.spy(LiveEditCompilerInput(file, findFunction(file, "foo")))
    Mockito.`when`(input.element).thenThrow(ProcessCanceledException())
    var inputs = listOf(input)
    var output = LiveEditCompiler(inputs.first().file.project).compile(inputs)
    assert(output.isEmpty)
  }

  @Test
  fun syntaxError() {
    var file = projectRule.fixture.configureByText("A.kt", "fun foo() = 1")
    var input = Mockito.spy(LiveEditCompilerInput(file, findFunction(file, "foo")))
    Mockito.`when`(input.element).thenThrow(LiveEditUpdateException.compilationError("some syntax error in file A.kt"))
    var inputs = listOf(input)

    try {
      LiveEditCompiler(inputs.first().file.project).compile(inputs)
      Assert.fail("Expecting LiveEditUpdateException")
    } catch (e : LiveEditUpdateException) {

    }
  }

  class ExceptionUnknownToStudio : RuntimeException()

  @Test
  fun unknownException() {
    var file = projectRule.fixture.configureByText("A.kt", "fun foo() = 1")
    var input = Mockito.spy(LiveEditCompilerInput(file, findFunction(file, "foo")))
    Mockito.`when`(input.element).thenThrow(ExceptionUnknownToStudio())
    var inputs = listOf(input)

    try {
      LiveEditCompiler(inputs.first().file.project).compile(inputs)
      Assert.fail("Expecting LiveEditUpdateException")
    } catch (e : LiveEditUpdateException) {

    }
  }
}