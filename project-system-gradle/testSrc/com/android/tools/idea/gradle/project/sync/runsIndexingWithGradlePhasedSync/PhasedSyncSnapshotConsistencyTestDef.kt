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
  // TODO(b/384022658): Content root related
  // Variant specific paths and info from AndroidProject model is missing.
  "CONENT_ENTRY", // Yes, typo

  // TODO(b/384022658): Content root watching related
  "WATCHED_SOURCE_FOLDER",
  "WATCHED_RESOURCE_FOLDER",
  "WATCHED_TEST_SOURCE_FOLDER",
  "WATCHED_TEST_RESOURCE_FOLDER",

  // TODO(b/384022658): Facet related
  "FACET (Android)",
  "FACET (Android-Gradle)",
  "FACET (Kotlin)",

  // TODO(b/384022658): JDK related
  "JDK",
  // This should be nested under JDK, but isn't by mistake I think, so need to add it here explicitly
  "*isInherited",

  // TODO(b/384022658): External module options related
  "ExternalModuleGroup",
  "ExternalModuleVersion",
  "LinkedProjectId",

  // Individual issues
  "COMPILER_MODULE_EXTENSION", // TODO(b/384022658)
  "LINKED_ANDROID_MODULE_GROUP", // TODO(b/384022658)
  "TEST_MODULE_PROPERTIES", // TODO(b/384022658)
  "EXCLUDE_FOLDER", // TODO(b/384022658)
  "Classes" // TODO(b/384022658)
)

fun ModuleDumpWithType.filterOutKnownConsistencyIssues(): ModuleDumpWithType {
  val (androidEntries, rest) = entries.partition { line ->
    androidModuleNames.any { line.contains("MODULE ($it)") }
  }
  return copy(
    entries = androidEntries.filter { line ->
      PROPERTIES_WITH_KNOWN_CONSISTENCY_ISSUES.none { line.contains("/$it") }
    }.asSequence() + rest.filter { line ->
      PROPERTIES_WITH_KNOWN_CONSISTENCY_ISSUES.none { line.contains("/$it") }
      // for non-android modules also filter out dependency related stuff as known issues
      && DEPENDENCY_RELATED_PROPERTIES.none { line.contains("/$it") }
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

    val before = project.dumpModules(isAndroidByPath)

    Truth.assertWithMessage("Comparing intermediate phased sync state to full sync without dependencies (before)")
      .that(intermediateDump.filterOutDependencies().filterOutKnownConsistencyIssues().filterOutRootModule().join())
      .isEqualTo(before.filterOutDependencies().filterOutKnownConsistencyIssues().filterOutRootModule().filterToPhasedSyncModules().join())
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

