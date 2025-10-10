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
package com.android.tools.idea.logcat.gradle

import com.android.tools.idea.gradle.project.sync.snapshots.replaceContent
import com.android.tools.idea.logcat.LogcatR8MappingsToken
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectToSnapshotPaths
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Rule
import org.junit.Test

class LogcatGradleTokenIntegrationTest() {

  @get:Rule
  val projectRule =
    AndroidGradleProjectRule(
      workspaceRelativeTestDataPath = "tools/adt/idea/android/testData/snapshots"
    )

  @Test
  fun testGetR8MappingForApkFile() {
    projectRule.loadProject(TestProjectToSnapshotPaths.SIMPLE_APPLICATION) { rootFolder ->
      File(rootFolder, "gradle.properties").appendText("\nandroid.r8.gradual.support=true")
      updateMinSdk(rootFolder)
      File(rootFolder, "app/build.gradle")
        .appendText("\nandroid.buildTypes.release.optimization.enable = true")
    }
    val project = projectRule.project
    projectRule.invokeTasks(":app:assembleRelease")
    val mappingsTextFiles = LogcatR8MappingsToken.getR8TextMappings(project)
    checkListPath(mappingsTextFiles, 1, "mapping.txt")

    val mappingsPartitionFiles = LogcatR8MappingsToken.getR8PartitionMappings(project)
    checkListPath(mappingsPartitionFiles, 1, "mapping.prt")
  }

  @Test
  fun testGetMultipleR8MappingForApkFile() {
    projectRule.loadProject(TestProjectToSnapshotPaths.SIMPLE_APPLICATION) { rootFolder ->
      File(rootFolder, "gradle.properties").appendText("\nandroid.r8.gradual.support=true")
      updateMinSdk(rootFolder)
      File(rootFolder, "app/build.gradle")
        .appendText(
          "\nandroid.buildTypes.release.optimization.enable = true" +
            "\nandroid.buildTypes.debug.optimization.enable = true"
        )
    }
    val project = projectRule.project
    projectRule.invokeTasks(":app:assembleRelease")
    val mappingsTextFiles = LogcatR8MappingsToken.getR8TextMappings(project)
    checkListPath(mappingsTextFiles, 2, "mapping.txt")

    val mappingsPartitionFiles = LogcatR8MappingsToken.getR8PartitionMappings(project)
    checkListPath(mappingsPartitionFiles, 2, "mapping.prt")
  }

  private fun updateMinSdk(rootFolder: File) {
    File(rootFolder, "app/build.gradle").replaceContent {
      // make sure minSdk is >=21 as Gradual R8 Required
      val firstMatch: MatchResult? = ("minSdkVersion \\d+".toRegex().find(it))
      assertThat(firstMatch).isNotNull()
      it.replace(firstMatch!!.value, "minSdkVersion 21")
    }
  }

  private fun checkListPath(paths: List<Path>, size: Int, name: String) {
    assertThat(paths).isNotNull()
    assertThat(paths).hasSize(size)
    var existsCounter = 0
    paths.forEach {
      assertThat(it.fileName.toString()).isEqualTo(name)
      if (Files.exists(it)) existsCounter += 1
    }
    assertWithMessage("Only one mapping file should exist").that(existsCounter).isEqualTo(1)
  }
}
