/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.google.android.tools.debugger.test.lib

import com.google.android.tools.debugger.test.lib.ExpectedResult.ERROR
import com.google.android.tools.debugger.test.lib.ExpectedResult.FAIL
import com.google.android.tools.debugger.test.lib.ExpectedResult.FLAKY
import com.google.android.tools.debugger.test.lib.ExpectedResult.PASS
import com.intellij.tests.TestExecutionResultInterceptor
import java.nio.file.Path
import kotlin.io.path.notExists
import kotlin.io.path.readLines
import kotlin.jvm.optionals.getOrNull
import org.junit.platform.engine.TestExecutionResult
import org.junit.platform.engine.TestExecutionResult.Status.ABORTED
import org.junit.platform.engine.TestExecutionResult.Status.SUCCESSFUL
import org.junit.platform.engine.support.descriptor.MethodSource
import org.junit.platform.launcher.TestIdentifier

class ExpectedFailuresInterceptor : TestExecutionResultInterceptor {
  private val expectedResults = loadExpectedResults().withDefault { PASS }

  init {
    println()
  }

  @Suppress("unused") // Used by reflection
  override fun intercept(identifier: TestIdentifier, result: TestExecutionResult): TestExecutionResult {
    val expectedResult = identifier.expectedResult()

    return when (expectedResult) {
      PASS -> result
      FLAKY -> TestExecutionResult.successful()
      FAIL -> result.expectedFail()
      ERROR -> result.expectedError()
    }
  }

  private fun TestIdentifier.expectedResult(): ExpectedResult {
    val source = source.getOrNull() as? MethodSource ?: return PASS
    return expectedResults.getValue("${source.className}.${source.methodName}")
  }
}

private fun TestExecutionResult.expectedFail(): TestExecutionResult {
  return when {
    status == SUCCESSFUL -> TestExecutionResult.failed(AssertionError("Expected to fail but passed"))
    status == ABORTED -> this
    throwable.getOrNull() is AssertionError -> TestExecutionResult.successful()
    else -> TestExecutionResult.failed(AssertionError("Expected to fail but errored"))
  }
}

private fun TestExecutionResult.expectedError(): TestExecutionResult {
  return when {
    status == SUCCESSFUL -> TestExecutionResult.failed(AssertionError("Expected to error but passed"))
    status == ABORTED -> this
    throwable.getOrNull() is AssertionError -> TestExecutionResult.failed(AssertionError("Expected to error but failed"))
    else -> TestExecutionResult.successful()
  }
}

private enum class ExpectedResult {
  PASS,
  FAIL,
  ERROR,
  FLAKY,
}

private fun loadExpectedResults(): Map<String, ExpectedResult> {
  val file = System.getenv("EXPECTED_RESULTS_FILE") ?: return emptyMap()
  val path = Path.of(file)
  if (path.notExists()) {
    return emptyMap()
  }
  var current = FAIL

  return buildMap {
    val badLines = mutableListOf<Int>()
    val lines = path.readLines().map { it.trim() }
    lines.checkDuplicates()
    lines.forEachIndexed lines@{ lineNum, line ->
      when {
        line.startsWith("# Failure") -> current = FAIL
        line.startsWith("# Error") -> current = ERROR
        line.startsWith("# Flaky") -> current = FLAKY
        line.isEmpty() -> return@lines
        line.startsWith('#') -> return@lines
        line.isValidTestMethodFormat() -> put(line, current)
        else -> badLines.add(lineNum)
      }
    }
    if (badLines.isNotEmpty()) {
      throw IllegalStateException(
        "File ${path.fileName} has invalid entries:\n${badLines.joinToString("\n") { "  ${it + 1}: ${lines[it]}" }}"
      )
    }
  }
}

fun List<String>.checkDuplicates() {
  val dups =
    asSequence()
      .withIndex()
      .filter { it.value.isNotEmpty() && !it.value.startsWith('#') }
      .groupBy({ it.value }, { it.index + 1 })
      .filter { it.value.size > 1 }
      .map { (text, indices) -> "Line duplicated at $indices: $text" }
      .toList()
  if (dups.isNotEmpty()) {
    throw IllegalStateException("Duplicate lines:\n  ${dups.joinToString("  \n")}")
  }
}

fun String.isValidTestMethodFormat(): Boolean {
  // Updated Regex Breakdown:
  // ^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)* -> Standard package structure
  // \.[A-Z][a-zA-Z0-9_]* -> The top-level class name
  // (\$[a-zA-Z0-9_]+)* -> Optional: $ followed by inner class names
  // .                                     -> The method delimiter
  // [a-zA-Z0-9_]+                         -> The test method name
  // $

  val pattern = Regex("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)*\\.[A-Z][a-zA-Z0-9_]*(\\$[a-zA-Z0-9_]+)*\\.[a-zA-Z0-9_]+$")
  return pattern.matches(this)
}
