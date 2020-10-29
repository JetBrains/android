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

import com.android.testutils.TestUtils
import com.android.tools.idea.appinspection.inspector.api.AppInspectorJar
import com.android.tools.idea.appinspection.inspector.api.launch.ArtifactCoordinate
import com.android.tools.idea.appinspection.inspector.api.service.DiskFileService
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppInspectorJarPathsTest {

  @Test
  fun findJars() {
    val testData = TestUtils.getWorkspaceFile("tools/adt/idea/app-inspection/ide/testData").toPath()
    val fileService = object : DiskFileService() {
      override val cacheRoot = testData.resolve("cache")
      override val tmpRoot = testData.resolve("tmp")
    }

    val jarPaths = AppInspectorJarPaths(fileService)
    val basePath = fileService.getOrCreateCacheDir(INSPECTOR_JARS_DIR)

    val artifact1 = ArtifactCoordinate("androidx.work", "work-runtime", "2.5.0-alpha01", ArtifactCoordinate.Type.JAR)
    val artifact1DirPath = basePath.resolve("androidx.work").resolve("work-runtime").resolve("2.5.0-alpha01").toString()
    val artifact2 = ArtifactCoordinate("androidx.sqlite", "sqlite", "2.1.0", ArtifactCoordinate.Type.JAR)
    val artifact2DirPath = basePath.resolve("androidx.sqlite").resolve("sqlite").resolve("2.1.0").toString()

    assertThat(jarPaths.getInspectorJar(artifact1)).isEqualTo(AppInspectorJar("inspector.jar", artifact1DirPath, artifact1DirPath))
    assertThat(jarPaths.getInspectorJar(artifact2)).isEqualTo(AppInspectorJar("inspector.jar", artifact2DirPath, artifact2DirPath))

    assertThat(jarPaths.getInspectorJar(ArtifactCoordinate("a", "b", "1.0.0", ArtifactCoordinate.Type.JAR))).isNull()
  }
}