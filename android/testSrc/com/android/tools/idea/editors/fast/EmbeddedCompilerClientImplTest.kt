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
package com.android.tools.idea.editors.fast

import com.android.tools.idea.run.deployment.liveedit.LiveEditUpdateException
import com.android.tools.idea.run.deployment.liveedit.loadComposeRuntimeInClassPath
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.util.io.delete
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.nio.file.Files

internal class EmbeddedCompilerClientImplTest {
  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()
  private val compiler: EmbeddedCompilerClientImpl by lazy {
    EmbeddedCompilerClientImpl(project = projectRule.project,
                               log = Logger.getInstance(EmbeddedCompilerClientImplTest::class.java))
  }

  @Before
  fun setUp() {
    projectRule.module.loadComposeRuntimeInClassPath()
  }

  @Test
  fun `simple compilation request`() {
    val file = projectRule.fixture.addFileToProject(
      "src/com/test/Source.kt",
      """
        fun testMethod() {
        }

        fun testMethodB() {
          testMethod()
        }
      """.trimIndent())
    val outputDirectory = Files.createTempDirectory("out")
    runBlocking {
      val result = compiler.compileRequest(listOf(file), projectRule.module, outputDirectory, EmptyProgressIndicator())
      assertTrue(result.toString(), result is CompilationResult.Success)
      assertEquals("""
        SourceKt.class
        light_idea_test_case.kotlin_module
      """.trimIndent(), outputDirectory.toFileNameSet().sorted().joinToString("\n"))
    }
  }

  @Test
  fun `syntax error compilation request`() {
    val file = projectRule.fixture.addFileToProject(
      "src/com/test/Source.kt",
      """
        fun testMethod(
        }
      """.trimIndent())
    val outputDirectory = Files.createTempDirectory("out")
    runBlocking {
      val result = compiler.compileRequest(listOf(file), projectRule.module, outputDirectory, EmptyProgressIndicator())
      assertTrue(result.toString(), result is CompilationResult.CompilationError)
      assertTrue(outputDirectory.toFileNameSet().isEmpty())
    }
  }

  @Test
  fun `parallel requests`() {
    val file = projectRule.fixture.addFileToProject(
      "src/com/test/Source.kt",
      """
        fun testMethod() {
        }
      """.trimIndent())

    val outputDirectories = (1..200).map { Files.createTempDirectory("out") }.toList()
    try {
      runBlocking {
        outputDirectories.forEach { outputDirectory ->
          launch {
            val result = compiler.compileRequest(listOf(file), projectRule.module, outputDirectory, EmptyProgressIndicator())
            assertTrue(result.toString(), result is CompilationResult.Success)
            assertEquals("""
              SourceKt.class
              light_idea_test_case.kotlin_module
            """.trimIndent(), outputDirectory.toFileNameSet().sorted().joinToString("\n"))
          }
        }
      }
    }
    finally {
      outputDirectories.forEach { it.delete(true) }
    }
  }

  @Test
  fun `inline test`() {
    projectRule.fixture.addFileToProject(
      "src/com/test/Inline.kt",
      """
        inline fun inlineMethod() {
        }
      """.trimIndent())
    val file = projectRule.fixture.addFileToProject(
      "src/com/test/Source.kt",
      """
        fun testMethod() {
          inlineMethod()
        }
      """.trimIndent())

    // Test with inline analysis enabled. This should fail when using inline methods in other files.
    run {
      val compiler = EmbeddedCompilerClientImpl(project = projectRule.project,
                                                log = Logger.getInstance(EmbeddedCompilerClientImplTest::class.java), false)
      val outputDirectory = Files.createTempDirectory("out")
      runBlocking {
        val result = compiler.compileRequest(listOf(file), projectRule.module, outputDirectory, EmptyProgressIndicator())
        assertTrue(result.toString(), result is CompilationResult.RequestException)
        assertEquals(
          "Unable to update function that references an inline function from another source file: public final fun inlineMethod (): kotlin.Unit [inline] declared in <root>.InlineKt",
          (result as CompilationResult.RequestException).e?.message?.trim())
      }
    }

    // Test with inline analysis disabled. This should pass when using inline methods in other files.
    run {
      val compiler = EmbeddedCompilerClientImpl(project = projectRule.project,
                                                log = Logger.getInstance(EmbeddedCompilerClientImplTest::class.java), true)
      val outputDirectory = Files.createTempDirectory("out")
      runBlocking {
        val result = compiler.compileRequest(listOf(file), projectRule.module, outputDirectory, EmptyProgressIndicator())
        assertTrue(result.toString(), result is CompilationResult.Success)
        assertEquals("""
              InlineKt.class
              SourceKt.class
              light_idea_test_case.kotlin_module
            """.trimIndent(), outputDirectory.toFileNameSet().sorted().joinToString("\n"))
      }
    }
  }

  @Test
  fun `test retry`() {
    val attempts = 20
    var executedRetries = 0
    val result = retryInNonBlockingReadAction(attempts) {
      executedRetries++
      // Throw in all but the last one
      if (executedRetries < attempts - 1) throw IllegalStateException()
      230L
    }
    assertEquals(230L, result)
    assertEquals(attempts - 1, executedRetries)

    class TestException: Exception()
    executedRetries = 0
    try {
      retryInNonBlockingReadAction(attempts) {
        executedRetries++
        // Do not execute all attempts, just throw a NonRetryableException after 5 attempts
        // to avoid more retries.
        if (executedRetries == 5) throw NonRetriableException(TestException())
        throw IllegalStateException()
      }
    }
    catch (t: TestException) {
      assertEquals(5, executedRetries)
    }
    catch (t: Throwable) {
      fail("Unexpected exception ${t.message}")
    }
  }

  /**
   * Verifies that the compileRequest fails correctly when passing a qualifier call with no receiver like `Test.`.
   * This is a regression test to verify that compileRequest does not break and handles that case correctly.
   */
  @Test
  fun `check dot qualifier error`() {
    val file = projectRule.fixture.addFileToProject(
      "src/com/test/Source.kt",
      """
        object Test {
          fun method() {}
        }

        fun testMethod() {
          Test.
        }
      """.trimIndent())
    val outputDirectory = Files.createTempDirectory("out")
    runBlocking {
      val result = compiler.compileRequest(listOf(file), projectRule.module, outputDirectory, EmptyProgressIndicator())
      assertTrue((result as CompilationResult.CompilationError).e is LiveEditUpdateException)
    }
  }
}