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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

private fun getProjectSpecificResyncIssues(testProject: TestProject) = when(testProject) {
  // TODO(b/384022658): Symlink for the root project is not handled the same way by phased sync
  TestProject.SIMPLE_APPLICATION_VIA_SYMLINK -> setOf(
    "/RootProjectPath"
  )
  TestProject.BASIC_WITH_EMPTY_SETTINGS_FILE -> setOf(
    // TODO(b/384022658): We don't set up tasks in phased sync, although it shouldn't really affect a re-sync, it does for this project.
    "BUILD_TASKS",
    // TODO(b/384022658): Not sure why
    "MODULE (project.androidTest)/Classes"
  )
  TestProject.KOTLIN_KAPT,
  TestProject.NEW_SYNC_KOTLIN_TEST -> setOf(
    "</>kaptKotlin</>",
    "</>kapt</>"
  )
  TestProject.MAIN_IN_ROOT -> setOf(
    // This is incorrectly populated as a content root(!) in old sync
    "project</>app</>AndroidManifest.xml",
    // This is incorrectly missing from the old sync content roots
    "project</>app</>src</>debug"
  )
  else -> emptySet()
}

private fun getProjectSpecificIdeModelResyncIssues(testProject: TestProject) = when(testProject.template) {
  TestProjectToSnapshotPaths.KOTLIN_MULTIPLATFORM,
  TestProjectToSnapshotPaths.NON_STANDARD_SOURCE_SET_DEPENDENCIES -> setOf(
    // TODO(b/384022658): Dependencies to kotlin multiplatform modules can't be set up as module set up is not supported by phased sync
    "Classpath/module (<PROJECT>-:module2",
    "Classpath/module (<PROJECT>-:feature-b-MAIN)",
    "Classpath/module (<PROJECT>-:common-commonMain)"
  )

  else -> when (testProject) {
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
}


fun ModuleDumpWithType.filterOutProjectSpecificIssues(testProject: TestProject) = copy(
  projectStructure = projectStructure.filter { line ->
     getProjectSpecificResyncIssues(testProject).none { line.contains(it) }
    },
  ideModels = ideModels.filter { line ->
    getProjectSpecificIdeModelResyncIssues(testProject).none { line.contains(it) }
  }
)

@RunWith(Parameterized::class)
class PhasedSyncResyncTests(val testProject: TestProject) : PhasedSyncSnapshotTestBase() {
  @get:Rule val projectRule: IntegrationTestEnvironmentRule = AndroidProjectRule.withIntegrationTestEnvironment()

  @Test
  fun testResync() {
    setupPhasedSyncIntermediateStateCollector(projectRule.testRootDisposable)

    val preparedProject = projectRule.prepareTestProject(testProject)
    preparedProject.open({ it.copy(expectedSyncIssues = testProject.expectedSyncIssues) }) { project: Project ->
      val firstFullSync = project.dumpModules(knownAndroidPaths)
      project.requestSyncAndWait(ignoreSyncIssues = testProject.expectedSyncIssues, waitForIndexes = false)
      val secondFullSync = project.dumpModules(knownAndroidPaths)
      val secondIntermediateSync = intermediateDump.copy()
      aggregateAndThrowIfAny {
        runCatchingAndRecord {
          Truth.assertWithMessage("Comparing full project structures")
            .that(secondFullSync.projectStructure())
            .isEqualTo(firstFullSync.projectStructure())
        }
        runCatchingAndRecord {
          Truth.assertWithMessage("Comparing full ide models")
            .that(secondFullSync.ideModels())
            .isEqualTo(firstFullSync.ideModels())
        }
        runCatchingAndRecord {
          Truth.assertWithMessage("Comparing resync intermediate sync project structure to full state")
            .that(secondIntermediateSync.filterOutProjectSpecificIssues(testProject).projectStructure())
            .isEqualTo(secondFullSync.filterOutProjectSpecificIssues(testProject).projectStructure())
        }
        runCatchingAndRecord {
          Truth.assertWithMessage("Comparing resync intermediate sync ide models to full state")
            .that(secondIntermediateSync.filterOutProjectSpecificIssues(testProject).ideModels())
            .isEqualTo(secondFullSync.filterOutProjectSpecificIssues(testProject).ideModels())
        }
      }
    }
  }


  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun testParameters(): Collection<*>  = phasedSyncTestProjects.filterNot {
      setOf(
        // TODO(b/384022658): Excluded for now as dependency resolution is disabled for this project
        TestProject.PRIVACY_SANDBOX_SDK,
        // TODO(b/384022658): There is an issue regarding the full sync regarding this project, it seems to create duplicate
        //  library dependencies for some modules. Probably has to do with module libraries.
        TestProject.KOTLIN_MULTIPLATFORM_WITHJS,
        // TODO(b/384022658): Handle spaces in the root project name (settings.gradle) correctly
        TestProject.TWO_JARS,
        TestProject.ANDROID_KOTLIN_MULTIPLATFORM,
      ).contains(it)
    }
  }
}
