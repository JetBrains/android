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
package com.android.tools.idea.gradle.project.sync.snapshots

import com.android.tools.idea.testing.TestProjectToSnapshotPaths
import com.google.common.truth.Truth.assertThat
import java.io.File

/**
 * Defines test projects used in [SyncedProjectTest].
 *
 * When adding a new entry to this file add a new test method to [SyncedProjectTest].
 */
enum class TestProject(
  val template: String,
  val pathToOpen: String = "",
  val testName: String? = null,
  val patch: (projectRoot: File) -> Unit = {}
) {
  SIMPLE_APPLICATION(TestProjectToSnapshotPaths.SIMPLE_APPLICATION),
  SIMPLE_APPLICATION_WITH_ADDITIONAL_GRADLE_SOURCE_SETS(
    TestProjectToSnapshotPaths.SIMPLE_APPLICATION, testName = "additionalGradleSourceSets", patch = { root ->
      val buildFile = root.resolve("app").resolve("build.gradle")
      buildFile.writeText(
        buildFile.readText() + """
          sourceSets {
            test.resources.srcDirs += 'src/test/resources'
          }
        """.trimIndent()
      )
    }),
  WITH_GRADLE_METADATA(TestProjectToSnapshotPaths.WITH_GRADLE_METADATA),
  BASIC_CMAKE_APP(TestProjectToSnapshotPaths.BASIC_CMAKE_APP),
  PSD_SAMPLE_GROOVY(TestProjectToSnapshotPaths.PSD_SAMPLE_GROOVY),
  COMPOSITE_BUILD(TestProjectToSnapshotPaths.COMPOSITE_BUILD, patch = { projectRoot ->
    truncateForV2(projectRoot.resolve("settings.gradle"))
  }),
  NON_STANDARD_SOURCE_SETS(TestProjectToSnapshotPaths.NON_STANDARD_SOURCE_SETS, "/application"),
  NON_STANDARD_SOURCE_SET_DEPENDENCIES(TestProjectToSnapshotPaths.NON_STANDARD_SOURCE_SET_DEPENDENCIES),
  LINKED(TestProjectToSnapshotPaths.LINKED, "/firstapp"),
  KOTLIN_KAPT(TestProjectToSnapshotPaths.KOTLIN_KAPT),
  LINT_CUSTOM_CHECKS(TestProjectToSnapshotPaths.LINT_CUSTOM_CHECKS),
  TEST_FIXTURES(TestProjectToSnapshotPaths.TEST_FIXTURES),
  TEST_ONLY_MODULE(TestProjectToSnapshotPaths.TEST_ONLY_MODULE),
  KOTLIN_MULTIPLATFORM(TestProjectToSnapshotPaths.KOTLIN_MULTIPLATFORM),
  MULTI_FLAVOR(TestProjectToSnapshotPaths.MULTI_FLAVOR),
  NAMESPACES(TestProjectToSnapshotPaths.NAMESPACES),
  INCLUDE_FROM_LIB(TestProjectToSnapshotPaths.INCLUDE_FROM_LIB),
  LOCAL_AARS_AS_MODULES(TestProjectToSnapshotPaths.LOCAL_AARS_AS_MODULES),
  BASIC(TestProjectToSnapshotPaths.BASIC);

  val projectName: String get() = "${template.removePrefix("projects/")}$pathToOpen${if (testName == null) "" else " - $testName"}"
}

private fun truncateForV2(settingsFile: File) {
  val patchedText = settingsFile.readLines().takeWhile { !it.contains("//-v2:truncate-from-here") }.joinToString("\n")
  assertThat(patchedText.trim()).isNotEqualTo(settingsFile.readText().trim())
  settingsFile.writeText(patchedText)
}
