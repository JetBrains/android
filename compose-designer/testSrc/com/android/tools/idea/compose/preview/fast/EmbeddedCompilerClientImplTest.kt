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
package com.android.tools.idea.compose.preview.fast

import com.android.tools.idea.compose.preview.toFileNameSet
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.util.io.delete
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.nio.file.Files

internal class EmbeddedCompilerClientImplTest {
  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()
  private lateinit var compiler: EmbeddedCompilerClientImpl

  @Before
  fun setUp() {
    compiler = EmbeddedCompilerClientImpl(project = projectRule.project,
                                          log = Logger.getInstance(EmbeddedCompilerClientImplTest::class.java))
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
      assertTrue(result.toString(), result is CompilationResult.RequestException)
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
  fun `test retry`() {
    val attempts = 20
    var executedRetries = 0
    val result = retry(attempts) {
      executedRetries++
      // Throw in all but the last one
      if (executedRetries < attempts - 1) throw IllegalStateException()
      230L
    }
    assertEquals(230L, result)
    assertEquals(attempts - 1, executedRetries)

    // Check that ProcessCanceledException is not retried
    executedRetries = 0
    try {
      retry(attempts) {
        executedRetries++
        // Throw in all but the last one
        if (executedRetries < attempts - 1) throw ProcessCanceledException()
        230L
      }
    }
    catch (_: ProcessCanceledException) {
    }
    assertEquals(1, executedRetries)
  }
}