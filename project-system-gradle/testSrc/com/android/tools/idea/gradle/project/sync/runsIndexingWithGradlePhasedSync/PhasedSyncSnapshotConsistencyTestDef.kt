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
import com.android.tools.idea.testing.TestProjectToSnapshotPaths
import com.google.common.truth.Truth
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import java.io.File

private val PROPERTIES_WITH_KNOWN_CONSISTENCY_ISSUES = setOf(

  // TODO(b/384022658): Facet related
  "/FACET (Android)",
  "/FACET (Android-Gradle)",
  "/FACET (Kotlin)",

  // TODO(b/384022658): Set up test fixtures modules in phased sync as well
  "/LINKED_ANDROID_MODULE_GROUP/testFixtures", // TODO(b/384022658)

  // Individual issues
  "/TEST_MODULE_PROPERTIES", // TODO(b/384022658)
  "/EXCLUDE_FOLDER", // TODO(b/384022658)
  "/Classes" // TODO(b/384022658)
)


// Additional issues with java/kmp modules, as we only operate on Android modules
private val PROPERTIES_WITH_KNOWN_CONSISTENCY_ISSUES_FOR_NON_ANDROID_MODULES =
  PROPERTIES_WITH_KNOWN_CONSISTENCY_ISSUES +
  // TODO(b/384022658): There are dependency related issues with non-android modules
  DEPENDENCY_RELATED_PROPERTIES + setOf(
    // TODO(b/384022658): Content root watching related
    "/WATCHED_SOURCE_FOLDER",
    "/WATCHED_RESOURCE_FOLDER",
    "/WATCHED_TEST_SOURCE_FOLDER",
    "/WATCHED_TEST_RESOURCE_FOLDER",

    // TODO(b/384022658): JDK related
    "/JDK",
    // This should be nested under JDK, but isn't by mistake I think, so need to add it here explicitly
    "/*isInherited",

    // Individual issues
    "/COMPILER_MODULE_EXTENSION",
  )

fun getProjectSpecificIssues(testProject: TestProject) = when(testProject.template) {
  TestProjectToSnapshotPaths.KOTLIN_MULTIPLATFORM,
  TestProjectToSnapshotPaths.NON_STANDARD_SOURCE_SET_DEPENDENCIES -> setOf(
    // TODO(b/384022658): Linked android module group is still set for KMP holder modules by full sync, but not phased sync
    "LINKED_ANDROID_MODULE_GROUP",
    // TODO(b/384022658): KMP projects are currently ignored by phased sync, except for when there is no Android target configured.
    "</>src</>jvmMain",
    "</>src</>jvmTest"
  ) else -> when(testProject) {
    // TODO(b/384022658): Info from KaptGradleModel is missing for phased sync entities for now
    TestProject.KOTLIN_KAPT,
    TestProject.NEW_SYNC_KOTLIN_TEST -> setOf(
      "</>kaptKotlin</>",
      "</>kapt</>"
    )

    TestProject.MULTI_FLAVOR_SWITCH_VARIANT -> setOf(
      // TODO(b/384022658): When switching from debug to release, the orphaned androidTest module isn't removed as in full sync
      "MODULE (MultiFlavor.app.androidTest)",
    )
    TestProject.MAIN_IN_ROOT -> setOf(
      // This is incorrectly populated as a content root(!) in old sync
      "project</>app</>AndroidManifest.xml",
      // This is incorrectly missing from the old sync content roots
      "project</>app</>src</>debug"
    )
    // TODO:(b/384022658): When syncing an already existing project with iml,
    //  1. Some external options metadata is different
    //  2. Holder modules have their directory as a content root (which should be incorrect)
    TestProject.COMPATIBILITY_TESTS_AS_36 -> setOf(
      // 1
      "/ExternalModuleGroup",
      "/ExternalModuleVersion",
      // 2
      "MODULE (AS36.features)/CONENT_ENTRY",
      "MODULE (AS36.libs)/CONENT_ENTRY",
      "MODULE (AS36.libs.java_lib)/CONENT_ENTRY",
      "MODULE (AS36.app)/CONENT_ENTRY",
      "MODULE (AS36.features.dynamicfeature)/CONENT_ENTRY",
      "MODULE (AS36.features.dynamicfeature2)/CONENT_ENTRY",
      "MODULE (AS36.libs.android_library)/CONENT_ENTRY"
    )
    else -> emptySet()
  }
}

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
      .that(intermediateDump.filterOutExpectedInconsistencies().filterOutKnownConsistencyIssues(testProject).filterOutRootModule().join())
      .isEqualTo(fullDump.filterOutExpectedInconsistencies().filterOutKnownConsistencyIssues(testProject).filterOutRootModule().filterToPhasedSyncModules().join())
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
      // TODO(b/384022658): Handle spaces in the root project name (settings.gradle) correctly
      TestProject.TWO_JARS,
      TestProject.ANDROID_KOTLIN_MULTIPLATFORM,
      ).contains(it)
    }.map { PhasedSyncSnapshotConsistencyTestDef(it) }
  }
}

