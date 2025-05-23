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
package com.android.tools.idea.gradle.project.sync.runsIndexingWithGradlePhasedSync

import com.android.tools.idea.gradle.project.sync.snapshots.SyncedProjectTestDef
import com.android.tools.idea.gradle.project.sync.snapshots.TestProject
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.Companion.AGP_CURRENT
import com.google.common.truth.Truth
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import java.io.File

private val PROPERTIES_WITH_KNOWN_CONSISTENCY_ISSUES = setOf(

  // TODO(b/384022658): Facet related
  "/FACET (Android)",
  "/FACET (Android-Gradle)",
  "/FACET (Kotlin)",

  // TODO(b/384022658): JDK related
  "/JDK",
  // This should be nested under JDK, but isn't by mistake I think, so need to add it here explicitly
  "/*isInherited",

  // TODO(b/384022658): External module options related
  "/ExternalModuleGroup",
  "/ExternalModuleVersion",
  "/LinkedProjectId",

  // TODO(b/384022658): These are missing from full sync, should they?
  "</>data_binding_base_class_source_out</>",

  // TODO(b/384022658): Set up test fixtures modules in phased sync as well
  "/LINKED_ANDROID_MODULE_GROUP/testFixtures", // TODO(b/384022658)

  // Individual issues
  "/COMPILER_MODULE_EXTENSION", // TODO(b/384022658)
  "/TEST_MODULE_PROPERTIES", // TODO(b/384022658)
  "/EXCLUDE_FOLDER", // TODO(b/384022658)
  "/Classes" // TODO(b/384022658)
)


private val PROPERTIES_WITH_KNOWN_CONSISTENCY_ISSUES_FOR_NON_ANDROID_MODULES =
  PROPERTIES_WITH_KNOWN_CONSISTENCY_ISSUES +
  // TODO(b/384022658): There are dependency related issues with non-android modules
  DEPENDENCY_RELATED_PROPERTIES + setOf(
    // TODO(b/384022658): Content root watching related, these are not set up properly for java/kmp modules yet
    "/WATCHED_SOURCE_FOLDER",
    "/WATCHED_RESOURCE_FOLDER",
    "/WATCHED_TEST_SOURCE_FOLDER",
    "/WATCHED_TEST_RESOURCE_FOLDER",
  )

fun ModuleDumpWithType.filterOutKnownConsistencyIssues(testProject: TestProject): ModuleDumpWithType {
  val (androidEntries, rest) = entries.partition { line ->
    androidModuleNames.any { line.contains("MODULE ($it)") }
  }
  val projectSpecificIssues = getProjectSpecificIssues(testProject)
  return copy(
    entries = androidEntries.filter { line ->
      (PROPERTIES_WITH_KNOWN_CONSISTENCY_ISSUES +
       projectSpecificIssues).none { line.contains(it) }
    }.asSequence() + rest.filter { line ->
      (PROPERTIES_WITH_KNOWN_CONSISTENCY_ISSUES_FOR_NON_ANDROID_MODULES +
       projectSpecificIssues).none { line.contains(it) }
    }
  )
}




data class PhasedSyncSnapshotConsistencyTestDef(
  override val testProject: TestProject,
  override val agpVersion: AgpVersionSoftwareEnvironmentDescriptor = AGP_CURRENT,
) : SyncedProjectTestDef, PhasedSyncSnapshotTestBase() {

  override fun setup(testRootDisposable: Disposable) {
    setupPhasedSyncIntermediateStateCollector(testRootDisposable)
  }

  override fun runTest(root: File, project: Project) {
    Truth.assertThat(isAndroidByPath).isNotNull()
    Truth.assertThat(intermediateDump).isNotNull()

    val fullDump = project.dumpModules(isAndroidByPath)

    Truth.assertWithMessage("Comparing intermediate phased sync state to full sync without dependencies")
      .that(intermediateDump.filterOutDependencies().filterOutKnownConsistencyIssues(testProject).filterOutRootModule().join())
      .isEqualTo(fullDump.filterOutDependencies().filterOutKnownConsistencyIssues(testProject).filterOutRootModule().filterToPhasedSyncModules().join())
  }



  override val name: String = testProject.projectName

  override fun toString(): String = testProject.projectName

  override fun withAgpVersion(agpVersion: AgpVersionSoftwareEnvironmentDescriptor): SyncedProjectTestDef {
    return copy(agpVersion = agpVersion)
  }

  override fun isCompatible(): Boolean {
    return agpVersion == AGP_CURRENT
  }

  companion object {
    val tests = phasedSyncTestProjects.filterNot {
      setOf(
      // It's currently expected that phased sync's intermediate state is inconsistent with the full sync for this compatibility test
      TestProject.COMPATIBILITY_TESTS_AS_36,
      // TODO(b/384022658): Handle spaces in the root project name (settings.gradle) correctly
      TestProject.TWO_JARS,
      TestProject.ANDROID_KOTLIN_MULTIPLATFORM,
      ).contains(it)
    }.map { PhasedSyncSnapshotConsistencyTestDef(it) }
  }
}

