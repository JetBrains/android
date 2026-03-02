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

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.sync.snapshots.TestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.android.tools.idea.testing.aggregateAndThrowIfAny
import com.android.tools.idea.testing.requestSyncAndWait
import com.android.tools.idea.testing.runCatchingAndRecord
import com.google.common.truth.Truth
import com.intellij.openapi.project.Project
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

fun ModuleDumpWithType.filterOutKnownResyncIssues(testProject: TestProject): ModuleDumpWithType {
  val (androidEntries, rest) = projectStructure.partition { line -> androidModuleNames.any { line.contains("MODULE ($it)") } }
  return copy(
    projectStructure =
      androidEntries.filter { line -> AndroidResyncIssues.projectStructure(testProject).none { line.contains(it) } }.asSequence() +
        rest.filter { line ->
          (JavaResyncIssues.projectStructure(testProject) + KmpResyncIssues.projectStructure(testProject)).none { line.contains(it) }
        },
    ideModels = ideModels.filter { line -> AndroidResyncIssues.ideModels(testProject).none { line.contains(it) } },
  )
}

private fun ModuleDumpWithType.filterOutFullResyncIssues(testProject: TestProject) =
  copy(
    projectStructure =
      projectStructure.filter { line -> (MiscResyncIssues.fullResyncProjectStructure(testProject)).none { line.contains(it) } }
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
      val firstGpp = project.dumpGradleProjectPaths()
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
          Truth.assertWithMessage("Comparing full ide models").that(secondFullSync.ideModels()).isEqualTo(firstFullSync.ideModels())
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
        runCatchingAndRecord {
          Truth.assertWithMessage("Comparing GradleProjectPaths between resync failed")
            .that(project.dumpGradleProjectPaths())
            .isEqualTo(firstGpp)
        }
      }
    }
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun testParameters(): Collection<*> = phasedSyncTestProjects.filterNot { MiscResyncIssues.filteredProjects.contains(it) }
  }
}
