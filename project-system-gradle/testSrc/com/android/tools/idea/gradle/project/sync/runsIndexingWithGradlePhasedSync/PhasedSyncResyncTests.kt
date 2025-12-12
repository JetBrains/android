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
import com.android.tools.idea.gradle.project.sync.snapshots.TestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.android.tools.idea.testing.TestProjectToSnapshotPaths
import com.android.tools.idea.testing.aggregateAndThrowIfAny
import com.android.tools.idea.testing.requestSyncAndWait
import com.android.tools.idea.testing.runCatchingAndRecord
import com.google.common.truth.Truth
import com.intellij.openapi.project.Project
import kotlin.collections.none
import kotlin.collections.plus
import kotlin.text.contains
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

private fun getProjectSpecificResyncIssues(testProject: TestProject) =
  when(testProject.template) {
    TestProjectToSnapshotPaths.NON_STANDARD_SOURCE_SET_DEPENDENCIES -> setOf(
      // Kmp is not properly set up as expected.
      "kmp-java.sample.jvmMain)/CONENT_ENTRY",
      "kmp-java.sample.jvmTest)/CONENT_ENTRY",
      "kmp-java.sample.main)/CONENT_ENTRY",
      "kmp-java.sample.test)/CONENT_ENTRY",
    )
    else -> when(testProject) {
      // This is incorrectly missing from the old sync content roots
      TestProject.MAIN_IN_ROOT -> setOf("project</>app</>src</>debug")
      TestProject.TEST_SUITES -> setOf(
        // TODO(b/445376814): Understand why they are different
        "MODULE (project.app.second)/CONENT_ENTRY"
      )
      // Phased sync creates these as top level libraries whereas in data services we have them as
      // module level (or vice versa)
      TestProject.NEW_SYNC_KOTLIN_TEST,
      TestProject.KOTLIN_KAPT -> setOf(
        "/LIBRARY (Gradle: org.jetbrains:annotations:13.0 [=])"
      )
      else -> emptySet()
    }
  }


private fun getProjectSpecificIdeModelResyncIssues(testProject: TestProject) = when(testProject.template) {
  TestProjectToSnapshotPaths.ANDROID_KOTLIN_MULTIPLATFORM,
  TestProjectToSnapshotPaths.KOTLIN_MULTIPLATFORM,
  TestProjectToSnapshotPaths.NON_STANDARD_SOURCE_SET_DEPENDENCIES -> setOf(
    // TODO(b/384022658): Dependencies to kotlin multiplatform modules can't be set up as module set up is not supported by phased sync
    "Classpath/module (<PROJECT>-:module2",
    "Classpath/module (<PROJECT>-:feature-b-MAIN)",
    "Classpath/module (<PROJECT>-:common-commonMain)",
    "Classpath/module (<PROJECT>-:kmpFirstLib",
    "Classpath/module (<PROJECT>-:kmpSecondLib",
    "Classpath/module (<PROJECT>-:kmpJvmOnly"
  )

  else -> when (testProject) {
    TestProject.COMPATIBILITY_TESTS_AS_36,
    TestProject.COMPATIBILITY_TESTS_AS_36_NO_IML -> setOf(
      // TODO(b/384022658): Manifest index affects these values so they fail to populate correctly in some cases
      "/CurrentVariantReportedVersions"
    )
    // The models for phased sync is fetched too early and are missing this, but it's fine as we have the rest of the tasks for variants
    TestProject.PSD_SAMPLE_GROOVY -> setOf("/taskNames (testDebugUnitTest)")
    TestProject.TWO_JARS -> setOf("/taskNames (test)")
    else -> emptySet()
  }
}


fun ModuleDumpWithType.filterOutKnownResyncIssues(testProject: TestProject): ModuleDumpWithType {
  val (androidEntries, rest) = projectStructure.partition { line ->
    androidModuleNames.any { line.contains("MODULE ($it)") }
  }
  val projectSpecificIssues = getProjectSpecificResyncIssues(testProject) + fullResyncIssues(testProject)
  return copy(
    projectStructure = androidEntries.filter { line ->
      (projectSpecificIssues).none { line.contains(it) }
    }.asSequence() + rest.filter { line ->
      (projectSpecificIssues + setOf(
        "/TEST_MODULE_PROPERTIES",
        "/Classes",
      )).none { line.contains(it) }
    },
    ideModels = ideModels.filter { line ->
      getProjectSpecificIdeModelResyncIssues(testProject).none { line.contains(it) }
    }
  )
}

private fun fullResyncIssues(testProject: TestProject) = when(testProject) {
  // TODO(b/384022658): There are consistency issues around how the JDK for the root project is set up.
  TestProject.SIMPLE_APPLICATION_NOT_AT_ROOT -> setOf(
    "MODULE (gradle_project)/*isInherited"
  )
  TestProject.SIMPLE_APPLICATION_MULTIPLE_ROOTS -> setOf(
    "MODULE (gradle_project_name)/*isInherited",
    "MODULE (gradle_project_1)/*isInherited"
  )
  else -> emptySet()
}

private fun ModuleDumpWithType.filterOutFullResyncIssues(testProject: TestProject) =
  copy(
    projectStructure = projectStructure.filter { line ->
      (fullResyncIssues(testProject)).none { line.contains(it) }
    }
  )

@RunWith(Parameterized::class)
class PhasedSyncResyncTests(val testProject: TestProject) : PhasedSyncSnapshotTestBase(checkObjectIdentity = true) {
  @get:Rule val projectRule: IntegrationTestEnvironmentRule = AndroidProjectRule.withIntegrationTestEnvironment()

  @Test
  fun testResync() {
    if (!StudioFlags.PHASED_SYNC_ENABLED.get()) return
    setupPhasedSyncIntermediateStateCollector(projectRule.testRootDisposable)

    val preparedProject = projectRule.prepareTestProject(testProject)
    preparedProject.open({ it.copy(expectedSyncIssues = testProject.expectedSyncIssues) }) { project: Project ->
      val firstFullSync = project.dumpModules(knownAndroidPaths, checkObjectIdentity = true)
      project.requestSyncAndWait(ignoreSyncIssues = testProject.expectedSyncIssues, waitForIndexes = false)
      val secondFullSync = project.dumpModules(knownAndroidPaths, checkObjectIdentity = true)
      val secondIntermediateSync = intermediateDump.copy()
      aggregateAndThrowIfAny {
        runCatchingAndRecord {
          Truth.assertWithMessage("Comparing full project structures")
            .that(secondFullSync.filterOutFullResyncIssues(testProject).projectStructure())
            .isEqualTo(firstFullSync.filterOutFullResyncIssues(testProject).projectStructure())
        }
        runCatchingAndRecord {
          Truth.assertWithMessage("Comparing full ide models")
            .that(secondFullSync.ideModels())
            .isEqualTo(firstFullSync.ideModels())
        }
        runCatchingAndRecord {
          Truth.assertWithMessage("Comparing resync intermediate sync project structure to full state")
            .that(secondIntermediateSync.filterOutKnownResyncIssues(testProject).projectStructure())
            .isEqualTo(secondFullSync.filterOutKnownResyncIssues(testProject).projectStructure())
        }
        runCatchingAndRecord {
          Truth.assertWithMessage("Comparing resync intermediate sync ide models to full state")
            .that(secondIntermediateSync.filterOutKnownResyncIssues(testProject).ideModels())
            .isEqualTo(secondFullSync.filterOutKnownResyncIssues(testProject).ideModels())
        }
      }
    }
  }


  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun testParameters(): Collection<*>  = phasedSyncTestProjects.filterNot {
      setOf(
        // TODO(b/384022658): There is an issue regarding the full sync regarding this project, it seems to create duplicate
        //  library dependencies for some modules. Probably has to do with module libraries.
        TestProject.KOTLIN_MULTIPLATFORM_WITHJS,
      ).contains(it)
    }
  }
}
