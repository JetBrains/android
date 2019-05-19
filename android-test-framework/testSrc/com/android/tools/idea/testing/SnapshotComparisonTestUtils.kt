/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.testing

import com.android.testutils.TestUtils.runningFromBazel
import com.intellij.openapi.util.io.FileUtil.sanitizeFileName
import com.intellij.testFramework.UsefulTestCase
import org.jetbrains.android.AndroidTestBase
import java.io.File

/**
 * See implementing classes for usage examples.
 */
interface SnapshotComparisonTest {
  /**
   * The name of the property which should be set to activate "update snapshots" test execution mode.
   */
  val updateSnapshotsJvmProperty: String get() = "UPDATE_TEST_SNAPSHOTS"

  /**
   * A testData subdirectory name where to look for snapshots.
   */
  val snapshotDirectoryName: String

  /**
   * The list of file name suffixes applicale to the currently running test.
   */
  val snapshotSuffixes: List<String> get() = listOf("")

  /**
   * Assumed to be matched by [UsefulTestCase.getName].
   */
  fun getName(): String
}

fun SnapshotComparisonTest.assertIsEqualToSnapshot(text: String, snapshotTestSuffix: String = "") {
  val fullSnapshotName = sanitizeFileName(UsefulTestCase.getTestName(getName(), true)) + snapshotTestSuffix
  val expectedText = getExpectedTextFor(fullSnapshotName)

  if (System.getProperty(updateSnapshotsJvmProperty) != null) {
    updateSnapshotFile(fullSnapshotName, text)
  }

  if (runningFromBazel()) {
    // Produces diffs readable in logs.
    com.google.common.truth.Truth.assertThat(text).isEqualTo(expectedText)
  }
  else {
    // Produces diffs that can be visually inspected in IDE.
    org.junit.Assert.assertEquals(expectedText, text)
  }
}

private fun SnapshotComparisonTest.getCandidateSnapshotFiles(project: String): List<File> =
  snapshotSuffixes
    .map { File("${AndroidTestBase.getTestDataPath()}/$snapshotDirectoryName/${project.substringAfter("projects/")}$it.txt") }

private fun SnapshotComparisonTest.updateSnapshotFile(snapshotName: String, text: String) {
  getCandidateSnapshotFiles(snapshotName)
    .let { candidates -> candidates.firstOrNull { it.exists() } ?: candidates.last() }
    .run {
      println("Writing to: ${this.absolutePath}")
      writeText(text)
    }
}

private fun SnapshotComparisonTest.getExpectedTextFor(project: String): String =
  getCandidateSnapshotFiles(project)
    .let { candidateFiles ->
      candidateFiles
        .firstOrNull { it.exists() }
        ?.let {
          println("Comparing with: ${it.relativeTo(File(AndroidTestBase.getTestDataPath()))}")
          it.readText().trimIndent()
        }
      ?: candidateFiles
        .joinToString(separator = "\n", prefix = "No snapshot files found. Candidates considered:\n\n") {
          it.relativeTo(File(AndroidTestBase.getTestDataPath())).toString()
        }
    }

