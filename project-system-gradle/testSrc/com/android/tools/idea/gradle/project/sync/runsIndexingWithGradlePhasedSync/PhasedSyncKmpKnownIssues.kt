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
import com.android.tools.idea.testing.TestProjectToSnapshotPaths

internal object KmpConsistencyIssues {
  val filteredProjects =
    setOf(
      // TODO(b/384022658): New KMP not supported, we could list out the individual project issues instead
      // of filtering the entire test, but there are too many inconsistencies as of right now, so it's not very practical/useful
      TestProject.ANDROID_KOTLIN_MULTIPLATFORM
    )

  internal fun projectStructure(testProject: TestProject): Set<String> =
    when (testProject.template) {
      TestProjectToSnapshotPaths.KOTLIN_MULTIPLATFORM,
      TestProjectToSnapshotPaths.NON_STANDARD_SOURCE_SET_DEPENDENCIES,
      TestProjectToSnapshotPaths.ANDROID_KOTLIN_MULTIPLATFORM ->
        setOf(
          // TODO(b/384022658): Linked android module group is still set for KMP holder modules by full sync, but not phased sync
          "LINKED_ANDROID_MODULE_GROUP",
          // TODO(b/384022658): KMP projects are currently ignored by phased sync, except for when there is no Android target configured.
          "</>src</>jvmMain",
          "</>src</>jvmTest",
          // TODO(b/384022658): Dependencies to kotlin multiplatform modules can't be set up as module set up is not supported by phased
          // sync
          "/ORDER_ENTRY (kotlinMultiPlatform.module2", // Close paranthesis left out deliberately to include all sub-modules
          "/ORDER_ENTRY (NonStandardSourceSetDependencies.common", // Close paranthesis left out deliberately to include all sub-modules
          "/ORDER_ENTRY (NonStandardSourceSetDependencies.feature-b",
          "/FACET (Android)", // These are still present in the KMP holder modules
        )
      else -> emptySet()
    }

  internal fun ideModels(testProject: TestProject): Set<String> =
    when (testProject.template) {
      TestProjectToSnapshotPaths.KOTLIN_MULTIPLATFORM,
      TestProjectToSnapshotPaths.NON_STANDARD_SOURCE_SET_DEPENDENCIES,
      TestProjectToSnapshotPaths.ANDROID_KOTLIN_MULTIPLATFORM ->
        setOf(
          // TODO(b/384022658): Dependencies to kotlin multiplatform modules can't be set up as module set up is not supported by phased
          // sync
          "Classpath/module (<PROJECT>-:module2",
          "Classpath/module (<PROJECT>-:feature-b-MAIN)",
          "Classpath/module (<PROJECT>-:common-commonMain)",
          "Classpath/module (<PROJECT>-:kmpFirstLib",
          "Classpath/module (<PROJECT>-:kmpSecondLib",
          "Classpath/module (<PROJECT>-:kmpJvmOnly",
        )

      else -> emptySet()
    }
}

internal object KmpResyncIssues {
  internal fun projectStructure(testProject: TestProject): Set<String> =
    when (testProject.template) {
      TestProjectToSnapshotPaths.KOTLIN_MULTIPLATFORM,
      TestProjectToSnapshotPaths.NON_STANDARD_SOURCE_SET_DEPENDENCIES,
      TestProjectToSnapshotPaths.ANDROID_KOTLIN_MULTIPLATFORM ->
        setOf(
          // Kmp is not properly set up as expected.
          "kmp-java.sample.jvmMain)/CONTENT_ENTRY",
          "kmp-java.sample.jvmTest)/CONTENT_ENTRY",
          "kmp-java.sample.main)/CONTENT_ENTRY",
          "kmp-java.sample.test)/CONTENT_ENTRY",
          "MODULE (kotlinMultiPlatform.module2)/COMPILER_MODULE_EXTENSION",
          "MODULE (NonStandardSourceSetDependencies.feature-b)/COMPILER_MODULE_EXTENSION",
          "/Classes",
        )
      else -> emptySet()
    }
}
