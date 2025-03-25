/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.hyperlink

import com.android.tools.idea.gradle.dsl.utils.FN_GRADLE_PROPERTIES
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.intellij.openapi.project.Project
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever
import java.io.File

class SuppressUnsupportedSdkVersionHyperlinkTest: AndroidGradleTestCase() {

  fun `test when project is disposed`() {
    val gradlePropertiesPath = File(projectFolderPath, FN_GRADLE_PROPERTIES)
    deleteGradlePropertiesFile(gradlePropertiesPath)

    val mockProject = mock(Project::class.java)
    whenever(mockProject.isDisposed).thenReturn(true)
    SuppressUnsupportedSdkVersionHyperlink("abc=x").execute(mockProject)
    assertFalse(gradlePropertiesPath.exists())
  }

  private fun deleteGradlePropertiesFile(path: File) {
    if (path.exists()) {
      assertTrue(path.delete())
    }
  }
}