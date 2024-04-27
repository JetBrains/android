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
import kotlin.test.assertEquals

class CompilerExceptionHandlingTest {

  @get:Rule
  var projectRule = AndroidProjectRule.inMemory()

  @Before
  fun setUp() {
    setUpComposeInProjectFixture(projectRule)
  }

  @Test
  fun notDropProcessCancelException() {
    val file = projectRule.fixture.configureByText("A.kt", "fun foo() = 1")
    val input = LiveEditCompilerInput(file, findFunction(file, "foo"))
    val cache = Mockito.spy(MutableIrClassCache())
    Mockito.`when`(cache["AKt"]).thenThrow(ProcessCanceledException())
    val output = LiveEditCompiler(file.project, cache).compile(listOf(input))
    assert(output.isEmpty)
  }

  @Test
  fun syntaxError() {
    val file = projectRule.fixture.configureByText("A.kt", "fun foo() = 1")
    val input = LiveEditCompilerInput(file, findFunction(file, "foo"))
    val cache = Mockito.spy(MutableIrClassCache())
    Mockito.`when`(cache["AKt"]).thenThrow(LiveEditUpdateException.compilationError("some syntax error in file A.kt"))

    try {
      LiveEditCompiler(file.project, cache).compile(listOf(input))
      Assert.fail("Expecting LiveEditUpdateException")
    } catch (e : LiveEditUpdateException) {
      assertEquals(LiveEditUpdateException.Error.COMPILATION_ERROR, e.error)
    }
  }

  class ExceptionUnknownToStudio : RuntimeException()

  @Test
  fun unknownException() {
    val file = projectRule.fixture.configureByText("A.kt", "fun foo() = 1")
    val input = LiveEditCompilerInput(file, findFunction(file, "foo"))
    val cache = Mockito.spy(MutableIrClassCache())
    Mockito.`when`(cache["AKt"]).thenThrow(ExceptionUnknownToStudio())

    try {
      LiveEditCompiler(file.project, cache).compile(listOf(input))
      Assert.fail("Expecting LiveEditUpdateException")
    } catch (e : LiveEditUpdateException) {

    }
  }
}