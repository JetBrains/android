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
import shark.ApplicationLeak
import shark.HeapAnalysisFailure
import shark.HeapAnalysisSuccess

/** Tests for [SharkHostAnalyzer]. */
class SharkHostAnalyzerTest {

  @get:Rule val tempFolder = TemporaryFolder()
  private val analyzer = SharkHostAnalyzer()

  /** Helper function to load test files using the project's standard [com.android.testutils.TestUtils]. */
  private fun getTestFile(pathFromWorkspaceRoot: String): File {
    val workspacePath = TestUtils.resolveWorkspacePath(pathFromWorkspaceRoot)
    val file = workspacePath.toFile()
    if (!file.exists()) {
      throw FileNotFoundException(
        "Test file not found: ${file.path}. Make sure '$pathFromWorkspaceRoot' is a valid path from the workspace root."
      )
    }
    return file
  }

  /**
   * Verifies that the Shark analyzer correctly identifies a single application leak. This test loads a heap dump specifically crafted with
   * one Fragment leak. It checks the resulting HeapAnalysisSuccess object for the presence of the leak, verifies the class name of the
   * leaking object, and ensures retained size is computed.
   */
  @Test
  fun `analyze successfully identifies single application leak`() {
    val hprofFile = getTestFile("tools/adt/idea/profilers/testData/hprofs/single_leak.hprof")
    val result = analyzer.analyze(hprofFile) {}

    Truth.assertThat(result).isInstanceOf(HeapAnalysisSuccess::class.java)
    val successResult = result as HeapAnalysisSuccess

    Truth.assertThat(successResult.applicationLeaks).hasSize(1)
    val leak = successResult.applicationLeaks.first() as ApplicationLeak

    val leakingClassName = leak.leakTraces.first().leakingObject.className
    Truth.assertThat(leakingClassName).isEqualTo("io.github.pastthepixels.freepaint.MainActivity\$ToolsBottomSheet")
    Truth.assertThat(leak.leakTraces).isNotEmpty()

    // Check computed retained size: verifies that `computeRetainedHeapSize = true` works.
    val trace = leak.leakTraces.first()
    Truth.assertThat(trace.retainedHeapByteSize).isGreaterThan(0)
  }

  /**
   * Verifies that the Shark analyzer correctly identifies multiple distinct application leaks in a single heap dump. The test uses an hprof
   * file that contains 10 application leaks (various Fragments and Views). It checks that the analyzer successfully categorizes and lists
   * all of them.
   */
  @Test
  fun `analyze successfully identifies multiple distinct leaks`() {
    val hprofFile = getTestFile("tools/adt/idea/profilers/testData/hprofs/multi_leak.hprof")
    val result = analyzer.analyze(hprofFile) {}

    Truth.assertThat(result).isInstanceOf(HeapAnalysisSuccess::class.java)
    val successResult = result as HeapAnalysisSuccess

    // There are exactly 10 application leaks in this multi_leak.hprof file
    Truth.assertThat(successResult.applicationLeaks).hasSize(10)

    // Extract all class names from the leaking objects across all leaks
    val classNames = successResult.applicationLeaks.map { (it as ApplicationLeak).leakTraces.first().leakingObject.className }

    // Assert that the expected classes are present in the list of leaks
    Truth.assertThat(classNames).contains("com.example.memoryleaksample.LeakingFragment1")
    Truth.assertThat(classNames).contains("android.view.View")
  }

  /**
   * Tests the resilience of the Shark analyzer when fed a heap dump that does not contain the KeyedWeakReference class (meaning
   * LeakCanary's ObjectWatcher was not tracking anything or not installed). It should successfully parse the graph but find 0 leaks.
   */
  @Test
  fun `analyze gracefully handles heap dumps without KeyedWeakReference`() {
    val hprofFile = getTestFile("tools/adt/idea/profilers/testData/hprofs/displayingbitmaps_leakedActivity.hprof")
    val result = analyzer.analyze(hprofFile) {}

    Truth.assertThat(result).isInstanceOf(HeapAnalysisSuccess::class.java)
    val successResult = result as HeapAnalysisSuccess

    // Shark should not crash, it should simply find no tracked leaking objects.
    Truth.assertThat(successResult.applicationLeaks).isEmpty()
  }

  /**
   * Tests that the analyzer can parse a very large and complex heap graph without throwing an exception. Uses a known valid, large hprof
   * test file.
   */
  @Test
  fun `analyze handles very large graph gracefully`() {
    val hprofFile = getTestFile("tools/adt/idea/profilers/testData/hprofs/valid_leak_test.hprof")
    val result = analyzer.analyze(hprofFile) {}

    Truth.assertThat(result).isInstanceOf(HeapAnalysisSuccess::class.java)
  }

  /**
   * Verifies that passing a corrupted file correctly returns a HeapAnalysisFailure instead of throwing an unhandled exception to the
   * caller.
   */
  @Test
  fun `analyze returns failure for corrupt hprof file`() {
    val corruptFile = tempFolder.newFile("corrupt.hprof")
    corruptFile.writeText("This is not a real heap dump.")

    val result = analyzer.analyze(corruptFile) {}

    // The analyzer should catch the file format error and wrap it in a failure object.
    Truth.assertThat(result).isInstanceOf(HeapAnalysisFailure::class.java)
    corruptFile.delete()
  }

  /** Verifies that providing a non-existent file path results in a HeapAnalysisFailure. */
  @Test
  fun `analyze returns failure for missing hprof file`() {
    val missingFile = File(tempFolder.root, "non_existent_file.hprof")

    val result = analyzer.analyze(missingFile) {}

    // The analyzer should catch the FileNotFoundException and wrap it.
    Truth.assertThat(result).isInstanceOf(HeapAnalysisFailure::class.java)
  }

  /** Verifies that passing a valid text file (but not an HPROF format) gracefully fails and returns a HeapAnalysisFailure. */
  @Test
  fun `analyze returns failure for valid file of non-hprof format`() {
    // Create a simple text file, which is a valid file but not an HPROF.
    val textFile = tempFolder.newFile("test.txt")
    textFile.writeText("This is a simple text file.")

    val result = analyzer.analyze(textFile) {}

    // The analyzer should gracefully fail when it tries to parse a non-HPROF file.
    Truth.assertThat(result).isInstanceOf(HeapAnalysisFailure::class.java)
    textFile.delete()
  }

  /**
   * Verifies that the analyzer reports progress incrementally from 0 to 100 via the provided callback function during the heap graph
   * parsing process.
   */
  @Test
  fun `analysis progress is reported`() {
    val validHprofFile = getTestFile("tools/adt/idea/profilers/testData/hprofs/single_leak.hprof")
    val progressUpdates = mutableListOf<Int>()

    analyzer.analyze(validHprofFile) { progress -> progressUpdates.add(progress) }

    // Progress updates should have been fired
    Truth.assertThat(progressUpdates).isNotEmpty()

    // Progress should be monotonically increasing
    Truth.assertThat(progressUpdates).isOrdered()

    Truth.assertThat(progressUpdates.last()).isAtLeast(90)
  }
}
