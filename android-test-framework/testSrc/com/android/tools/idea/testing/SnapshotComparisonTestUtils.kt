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

import com.android.testutils.TestUtils
import com.android.testutils.TestUtils.resolveWorkspacePath
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil.sanitizeFileName
import com.intellij.testFramework.UsefulTestCase
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.writeText

/**
 * See implementing classes for usage examples.
 *
 * NOTE: It you made changes to sync or the test projects which make Snapshot tests fail in an expected way, you can re-run the tests:
 *       (1) from the IDE with -DUPDATE_TEST_SNAPSHOTS to update the files; or
 *       (2) from the command-line using Bazel with:

```
bazel test [target]  \
   --jvmopt="-DUPDATE_TEST_SNAPSHOTS=$(bazel info workspace)" \
   --sandbox_writable_path=$(bazel info workspace) \
   --test_strategy=standalone \
   --nocache_test_results \
   --test_timeout=6000
```

 *
 */
interface SnapshotComparisonTest {
  /**
   * The name of the property which should be set to activate "update snapshots" test execution mode.
   */
  @JvmDefault
  val updateSnapshotsJvmProperty: String get() = "UPDATE_TEST_SNAPSHOTS"

  /**
   * A testData subdirectory name where to look for snapshots.
   */
  val snapshotDirectoryWorkspaceRelativePath: String

  /**
   * The list of file name suffixes applicable to the currently running test.
   */
  @JvmDefault
  val snapshotSuffixes: List<String> get() = listOf("")

  /**
   * Assumed to be matched by [UsefulTestCase.getName].
   */
  fun getName(): String
}

private val testLogsPath: String?
  get() = System.getenv("TEST_TARGET")?.takeIf { it.isNotEmpty() }?.removePrefix("//")?.replace(":", "/")?.let { targetPath ->
    targetPath + (System.getenv("TEST_SHARD_INDEX")?.takeIf { it.isNotEmpty() }?.let { shard -> "/shard_${shard}_of_${System.getenv("TEST_TOTAL_SHARDS")}" } ?: "")
  }

private fun SnapshotComparisonTest.assertInSnapshotTextContext() = Truth.assert_().withMessage("""
  There has been a change to the contents of snapshot test ${getName()}.

  If that change is intentional, update the expectation files.

  To update the files from a presubmit failure, download and unzip the outputs.zip for this target:
    unzip -d $(bazel info workspace) -o outputs.zip

  For a local bazel invocation, outputs.zip will be in bazel-testlogs:
    unzip -d $(bazel info workspace) -o \\
      $(bazel info bazel-testlogs)/${testLogsPath ?: "<bazel test target for ${getName()}>"}/test.outputs/outputs.zip

  Or, to re-run the test and update the expectations in place, either add -DUPDATE_TEST_SNAPSHOTS
  to the jvm options in the idea test configuration and re-run the test, or from bazel, run:
    bazel test ${System.getenv("TEST_TARGET")?.takeIf { it.isNotEmpty() } ?: "<bazel test target for ${getName()}>"} \\
      --nocache_test_results \\
      --sandbox_writable_path=${'$'}(bazel info workspace) \
      --strategy=TestRunner=standalone \\
      --jvmopt=\"-DUPDATE_TEST_SNAPSHOTS=$(bazel info workspace)\" \\
      --test_timeout=6000 \\
      --test_output=streamed

  NB: All the commands above assume 'tools/base/bazel' is on your path.
  """.trimIndent()
)

fun SnapshotComparisonTest.assertIsEqualToSnapshot(text: String, snapshotTestSuffix: String = "") {
  val (fullSnapshotName, expectedText) = getAndMaybeUpdateSnapshot(snapshotTestSuffix, text)
  assertInSnapshotTextContext()
    .that(text)
    .named("Snapshot comparison for $fullSnapshotName")
    .isEqualTo(expectedText)
}

fun SnapshotComparisonTest.assertAreEqualToSnapshots(vararg checks: Pair<String, String>) {
  val (actual, expected) =
    checks
      .map { (actual, suffix) ->
        val (fullName, expected) = getAndMaybeUpdateSnapshot(suffix, actual)
        val header = "\n####################### ${fullName} #######################\n"
        header + actual to header + expected
      }
      .unzip()
      .let {
        it.first.joinToString(separator = "\n") to it.second.joinToString(separator = "\n")
      }

  assertInSnapshotTextContext()
    .that(actual)
    .named("Snapshot comparisons for ${checks.joinToString(", ") { it.second }}")
    .isEqualTo(expected)
}

fun SnapshotComparisonTest.getAndMaybeUpdateSnapshot(
  snapshotTestSuffix: String,
  text: String,
  doNotUpdate: Boolean = false
): Pair<String, String> {
  val fullSnapshotName = sanitizeFileName(UsefulTestCase.getTestName(getName(), true)) + snapshotTestSuffix
  val expectedText = getExpectedTextFor(fullSnapshotName)

  if (!doNotUpdate && System.getProperty(updateSnapshotsJvmProperty) != null) {
    getSnapshotFileToUpdate(fullSnapshotName).run {
      println("Writing to: ${this.absolutePath}")
      writeText(text)
    }
  } else if (TestUtils.runningFromBazel()) {
    // Populate additional test output if the file needs updating
    val snapshotFileToUpdate = getSnapshotFileToUpdate(fullSnapshotName)
    if (!snapshotFileToUpdate.isFile || expectedText != text) {
      val workspaceRelativePath = TestUtils.getWorkspaceRoot().relativize(snapshotFileToUpdate.toPath())
      println("Writing updated snapshot file to bazel additional test output.\n" +
              "    $workspaceRelativePath")
      TestUtils.getTestOutputDir().resolve(workspaceRelativePath).run {
        Files.createDirectories(parent)
        writeText(text)
      }
    }

  }
  return fullSnapshotName to expectedText
}

private fun SnapshotComparisonTest.getCandidateSnapshotFiles(project: String): List<File> {
  val configuredWorkspace =
    System.getProperty(updateSnapshotsJvmProperty)?.takeUnless { it.isEmpty() }
      ?.let { Paths.get(it).resolve(snapshotDirectoryWorkspaceRelativePath) }
    ?: resolveWorkspacePath(snapshotDirectoryWorkspaceRelativePath)
  return snapshotSuffixes
    .map { configuredWorkspace.resolve("${project.substringAfter("projects/")}$it.txt").toFile() }
}

private fun SnapshotComparisonTest.getSnapshotFileToUpdate(snapshotName: String): File {
  return getCandidateSnapshotFiles(snapshotName)
    .let { candidates -> candidates.firstOrNull { it.exists() } ?: candidates.last() }
}

private fun SnapshotComparisonTest.getExpectedTextFor(project: String): String =
  getCandidateSnapshotFiles(project)
    .let { candidateFiles ->
      candidateFiles
        .firstOrNull { it.exists() }
        ?.let {
          println("Comparing with: ${it.relativeTo(resolveWorkspacePath("").toFile())}")
          it.readText().trimIndent()
        }
      ?: candidateFiles
        .joinToString(separator = "\n", prefix = "No snapshot files found. Candidates considered:\n\n") {
          it.relativeTo(resolveWorkspacePath("").toFile()).toString()
        }
    }

data class ProjectViewSettings(
  val hideEmptyPackages: Boolean = true,
  val flattenPackages: Boolean = false
)

fun Project.dumpAndroidProjectView(): String = dumpAndroidProjectView(initialState = Unit) { _, _ -> Unit }

fun nameProperties(snapshotLines: Sequence<String>): Sequence<Pair<String, String>> = sequence {
  val context = mutableListOf<Pair<Int, String>>()
  var previousIndentation = -1
  for (line in snapshotLines) {
    val propertyName = line.trimStart().removePrefix("- ").substringBefore(' ', line).trim()
    val indentation = line.indexOfFirst { !it.isWhitespace() }.let { if (it == -1) line.length else it }
    when {
      indentation > previousIndentation -> context.add(indentation to propertyName)
      indentation == previousIndentation -> context[context.size - 1] = indentation to propertyName
      else -> {
        while (context.size > 1 && context[context.size - 1].first > indentation) {
          context.removeLast()
        }
        context[context.size - 1] = indentation to propertyName
      }
    }
    previousIndentation = indentation
    yield(context.map { it.second }.joinToString(separator = "/") to line)
  }
}

