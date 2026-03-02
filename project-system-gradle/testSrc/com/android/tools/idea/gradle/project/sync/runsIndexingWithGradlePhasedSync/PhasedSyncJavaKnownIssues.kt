/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.runsIndexingWithGradlePhasedSync

import com.android.tools.idea.gradle.project.sync.snapshots.TestProject

internal object JavaConsistencyIssues {
  internal val projectStructure: Set<String> =
    setOf(
      "/WATCHED_", // Content root watching related
      "/JDK",
      "/*isInherited", // JDK related
      "/FACET (Android-Gradle)", // These are currently set up even for Java/KMP libraries (and aar wrapper modules!)
      "/FACET (Kotlin)", // Unsupported by both Android and Java
      "</>kapt</>",
      "</>kaptKotlin</>", // Kapt model is not handled correctly for non-Android
      "/ORDER_ENTRY",
      "/LIBRARY", // Java modules don't support dependency setup yet.
      "/COMPILER_MODULE_EXTENSION",
      "/TEST_MODULE_PROPERTIES",
      "/Classes",
      "/EXCLUDE_FOLDER",
      "/BUILD_TASKS",
    )
}

internal object JavaResyncIssues {
  internal fun projectStructure(testProject: TestProject): Set<String> =
    when (testProject) {
      TestProject.SIMPLE_APPLICATION_MULTIPLE_ROOTS,
      TestProject.SIMPLE_APPLICATION_NOT_AT_ROOT ->
        setOf(
          "/JDK",
          "/*isInherited", // JDK related
        )
      else -> emptySet()
    } + setOf("/TEST_MODULE_PROPERTIES")
}
