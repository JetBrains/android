/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.appinspection.ide.resolver

import com.android.test.testutils.TestUtils.resolveWorkspacePath
import com.android.tools.idea.appinspection.inspector.api.launch.RunningArtifactCoordinate
import com.android.tools.idea.appinspection.test.mockMinimumArtifactCoordinate
import com.android.tools.idea.io.DiskFileService
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppInspectorArtifactPathsTest {

  @Test
  fun findJars() {
    val testData = resolveWorkspacePath("tools/adt/idea/app-inspection/ide/testData")
    val fileService =
      object : DiskFileService() {
        override val cacheRoot = testData.resolve("cache")
        override val tmpRoot = testData.resolve("tmp")
      }

    val jarPaths = AppInspectorArtifactPaths(fileService)
    val basePath = fileService.getOrCreateCacheDir(INSPECTOR_JARS_DIR)

    val artifact1 = mockMinimumArtifactCoordinate("androidx.work", "work-runtime", "2.5.0-alpha01")
    val artifact1Path =
      basePath
        .resolve("androidx.work")
        .resolve("work-runtime")
        .resolve("2.5.0-alpha01")
        .resolve("androidx.work-work-runtime-2.5.0-alpha01-inspector.jar")
    val artifact2 = mockMinimumArtifactCoordinate("androidx.sqlite", "sqlite", "2.1.0")
    val artifact2Path =
      basePath
        .resolve("androidx.sqlite")
        .resolve("sqlite")
        .resolve("2.1.0")
        .resolve("androidx.sqlite-sqlite-2.1.0-inspector.jar")

    assertThat(jarPaths.getInspectorArchive(RunningArtifactCoordinate(artifact1, "2.5.0-alpha01")))
      .isEqualTo(artifact1Path)
    assertThat(jarPaths.getInspectorArchive(RunningArtifactCoordinate(artifact2, "2.1.0")))
      .isEqualTo(artifact2Path)

    assertThat(
        jarPaths.getInspectorArchive(
          RunningArtifactCoordinate(mockMinimumArtifactCoordinate("a", "b", "1.0.0"), "1.0.0")
        )
      )
      .isNull()
  }
}
