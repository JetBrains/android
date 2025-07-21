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

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.sync.snapshots.SyncedProjectTestDef
import com.android.tools.idea.gradle.project.sync.snapshots.TestProject
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.Companion.AGP_CURRENT
import com.android.tools.idea.testing.TestProjectToSnapshotPaths
import com.android.tools.idea.testing.aggregateAndThrowIfAny
import com.android.tools.idea.testing.runCatchingAndRecord
import com.google.common.truth.Truth
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import java.io.File

private val PROPERTIES_WITH_KNOWN_CONSISTENCY_ISSUES = setOf(
  // TODO(b/384022658): Facet related
  "/FACET (Kotlin)",

  // Individual issues
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
    "/TEST_MODULE_PROPERTIES", // TODO(b/384022658)

    // TODO(b/384022658): Facet related
    // Apparently these are currently set up even for Java libraries (and aar wrapper modules)!.
    // KMP modules are also not setup but that's expected.
    "/FACET (Android-Gradle)",
    // These are still present in the KMP holder modules, and not set up by phased sync, so we need to filter them out here
    "/FACET (Android)",
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
      // This is stored in the facet but does actually change correctly when switching, so we need to ignore it here.
      "/SelectedBuildVariant"
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

private fun getProjectSpecificIdeModelIssues(testProject: TestProject) = when(testProject) {
  TestProject.PRIVACY_SANDBOX_SDK,
  TestProject.COMPATIBILITY_TESTS_AS_36,
  TestProject.COMPATIBILITY_TESTS_AS_36_NO_IML -> setOf(
    // TODO(b/384022658): Manifest index affects these values so they fail to populate correctly in some cases
    "/CurrentVariantReportedVersions"
  )
  // TODO(b/384022658): Info from KaptGradleModel is missing for phased sync entities for now
  TestProject.KOTLIN_KAPT,
  TestProject.NEW_SYNC_KOTLIN_TEST -> setOf(
    "generated/source/kaptKotlin",
  )
  // TODO(b/428221750) BytecodeTransforms is missing for phased sync entities
  TestProject.BASIC_WITH_EMPTY_SETTINGS_FILE -> setOf(
    "/BytecodeTransforms",
  )
  else -> emptySet()
}

fun ModuleDumpWithType.filterOutKnownConsistencyIssues(testProject: TestProject): ModuleDumpWithType {
  val (androidEntries, rest) = projectStructure.partition { line ->
    androidModuleNames.any { line.contains("MODULE ($it)") }
  }
  val projectSpecificIssues = getProjectSpecificIssues(testProject)
  return copy(
    projectStructure = androidEntries.filter { line ->
      (PROPERTIES_WITH_KNOWN_CONSISTENCY_ISSUES +
       projectSpecificIssues).none { line.contains(it) }
    }.asSequence() + rest.filter { line ->
      (PROPERTIES_WITH_KNOWN_CONSISTENCY_ISSUES_FOR_NON_ANDROID_MODULES +
       projectSpecificIssues).none { line.contains(it) }
    },
    ideModels = ideModels.filter { line ->
        getProjectSpecificIdeModelIssues(testProject).none { line.contains(it) }
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
    if (!StudioFlags.PHASED_SYNC_ENABLED.get()) return
    Truth.assertThat(knownAndroidPaths).isNotNull()
    Truth.assertThat(intermediateDump).isNotNull()

    val fullDump = project.dumpModules(knownAndroidPaths)
    val filteredIntermediateDump = intermediateDump.filterOutExpectedInconsistencies().filterOutKnownConsistencyIssues(testProject).filterOutRootModule()
    val filteredFullDump = fullDump.filterOutExpectedInconsistencies().filterOutKnownConsistencyIssues(testProject).filterOutRootModule().filterToPhasedSyncModules()

    aggregateAndThrowIfAny {
      runCatchingAndRecord {
        Truth.assertWithMessage("Comparing intermediate phased sync project structure to full sync without dependencies")
          .that(filteredIntermediateDump.projectStructure())
          .isEqualTo(filteredFullDump.projectStructure())
      }
      runCatchingAndRecord {
        Truth.assertWithMessage("Comparing intermediate phased sync ide models to full sync without dependencies")
          .that(filteredIntermediateDump.ideModels())
          // We only need to inspect android modules when comparing IDE models
          .isEqualTo(filteredFullDump.filterToAndroidModules().ideModels())
      }
    }
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

