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

internal object AndroidConsistencyIssues {
  fun projectStructure(testProject: TestProject) =
    UnactionableConsistencyIssues.projectStructure(testProject) +
      MiscConsistencyIssues.projectStructure(testProject) +
      setOf("/FACET (Kotlin)", "/BUILD_TASKS")

  fun ideModels(testProject: TestProject) = UnactionableConsistencyIssues.ideModels(testProject)

  fun ModuleDumpWithType.maybeFilterToPhasedSyncModules(testProject: TestProject) = let {
    // These test project contains modules represented as iml files (or otherwise as JPS entities), so filtering them out.
    if (
      testProject in
        setOf(
          TestProject.COMPATIBILITY_TESTS_AS_36,
          TestProject.SIMPLE_APPLICATION_NOT_AT_ROOT,
          TestProject.SIMPLE_APPLICATION_MULTIPLE_ROOTS,
        )
    )
      filterToPhasedSyncModules()
    else this
  }
}

internal object AndroidResyncIssues {
  fun projectStructure(testProject: TestProject) =
    MiscResyncIssues.projectStructure(testProject) + UnactionableResyncIssues.projectStructure(testProject)

  fun ideModels(testProject: TestProject) = UnactionableConsistencyIssues.ideModels(testProject)
}
