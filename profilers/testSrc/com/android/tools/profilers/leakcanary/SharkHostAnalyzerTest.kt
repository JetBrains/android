/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.profilers.leakcanary

import com.android.testutils.TestUtils
import com.google.common.truth.Truth
import java.io.File
import java.io.FileNotFoundException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import shark.HeapAnalysisFailure
import shark.HeapAnalysisSuccess

/**
 * Tests for [SharkHostAnalyzer].
 */
class SharkHostAnalyzerTest {

  @get:Rule
  val tempFolder = TemporaryFolder()
  private val analyzer = SharkHostAnalyzer()

  /**
   * Helper function to load test files using the project's standard [com.android.testutils.TestUtils].
   */
  private fun getTestFile(pathFromWorkspaceRoot: String): File {
    val workspacePath = TestUtils.resolveWorkspacePath(pathFromWorkspaceRoot)
    val file = workspacePath.toFile()
    if (!file.exists()) {
      throw FileNotFoundException(
        "Test file not found: ${file.path}. " +
        "Make sure '$pathFromWorkspaceRoot' is a valid path from the workspace root."
      )
    }
    return file
  }

  @Test
  fun `analyze returns success and leaks for valid hprof file`() {

    val validHprofFile = getTestFile("tools/adt/idea/profilers/testData/hprofs/valid_leak_test.hprof")
    val result = analyzer.analyze(validHprofFile) { }

    Truth.assertThat(result).isInstanceOf(HeapAnalysisSuccess::class.java)
    val successResult = result as HeapAnalysisSuccess

    // Verify it found the leak(s) in the test file
    Truth.assertThat(successResult.applicationLeaks).isNotEmpty()
  }

  @Test
  fun `analyze returns failure for corrupt hprof file`() {
    val corruptFile = tempFolder.newFile("corrupt.hprof")
    corruptFile.writeText("This is not a real heap dump.")

    val result = analyzer.analyze(corruptFile) { }

    // The analyzer should catch the error and return a failure object.
    Truth.assertThat(result).isInstanceOf(HeapAnalysisFailure::class.java)
    corruptFile.delete()
  }

  @Test
  fun `analyze returns failure for missing hprof file`() {
    val missingFile = File(tempFolder.root, "non_existent_file.hprof")

    val result = analyzer.analyze(missingFile) { }

    // The analyzer should catch the file-not-found error and return a failure object.
    Truth.assertThat(result).isInstanceOf(HeapAnalysisFailure::class.java)
  }

  @Test
  fun `analyze returns failure for valid file of non-hprof format`() {
    // Create a simple text file, which is a valid file but not an HPROF.
    val textFile = tempFolder.newFile("test.txt")
    textFile.writeText("This is a simple text file.")

    val result = analyzer.analyze(textFile) { }

    // The analyzer should gracefully fail when it tries to parse a non-HPROF file.
    Truth.assertThat(result).isInstanceOf(HeapAnalysisFailure::class.java)
    textFile.delete()
  }

  @Test
  fun `analysis progress is reported`() {
    val validHprofFile = getTestFile("tools/adt/idea/profilers/testData/hprofs/valid_leak_test.hprof")
    val progressUpdates = mutableListOf<Int>()

    analyzer.analyze(validHprofFile) { progress ->
      progressUpdates.add(progress)
    }

    Truth.assertThat(progressUpdates).isNotEmpty()
    Truth.assertThat(progressUpdates).isOrdered()
    Truth.assertThat(progressUpdates.first()).isAtLeast(0)
    Truth.assertThat(progressUpdates.last()).isEqualTo(90)
  }
}