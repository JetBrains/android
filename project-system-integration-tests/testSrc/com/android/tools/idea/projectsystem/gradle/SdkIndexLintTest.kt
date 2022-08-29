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
package com.android.tools.idea.projectsystem.gradle

import org.junit.Test
import java.nio.file.Path
import kotlin.io.path.name

class SdkIndexLintTest : SdkIndexTestBase() {
  @Test
  fun snapshotUsedByLintTest() {
    verifySdkIndexIsInitializedAndUsedWhen(
      showFunction = { studio, project ->
        // Open build.gradle file in editor
        val projectName = project.targetProject.name
        val buildFilePath: Path = project.targetProject.resolve("build.gradle")
        studio.openFile(projectName, buildFilePath.toString())
      },
      beforeClose = null,
      expectedIssues = setOf(
        "com.mopub:mopub-sdk version 4.16.0 has been marked as outdated by its author",
        "com.stripe:stripe-android version 9.3.2 has policy issues that will block publishing of your app to Play Console",
        "com.startapp:inapp-sdk version 3.9.1 has been reported as problematic by its author and will block publishing of your app to Play Console"
      )
    )
  }
}
