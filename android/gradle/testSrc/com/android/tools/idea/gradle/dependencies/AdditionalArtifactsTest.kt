/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.gradle.dependencies

import com.android.tools.idea.gradle.LibraryFilePaths
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths
import com.google.common.truth.Truth.assertThat
import java.io.File

class AdditionalArtifactsTest : AndroidGradleTestCase() {

  // regression test for b/329877056
  fun testSourceDownloadedWithoutGradleMetadata() {

    val projectRoot = prepareProjectForImport(TestProjectPaths.KOTLIN_LIB)
    File(projectRoot, "mylibrary/build.gradle.kts").appendText(
      """
        
        dependencies {
            implementation("org.robolectric:robolectric:4.8.2")
        }
      """.trimIndent()
    )
    requestSyncAndWait()

    val libraryFilePaths = LibraryFilePaths.getInstance(myFixture.project)
    val artifactPaths = libraryFilePaths.getCachedPathsForArtifact("org.robolectric:robolectric:4.8.2")
    assertThat(artifactPaths).isNotNull()
    assertThat(artifactPaths!!.sources.map { it.name }).containsExactly(
      "robolectric-4.8.2-sources.jar"
    )
  }
}