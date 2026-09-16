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
import com.android.tools.idea.gradle.project.sync.runsIndexingWithGradlePhasedSync.AndroidConsistencyIssues.maybeFilterToPhasedSyncModules
import com.android.tools.idea.gradle.project.sync.snapshots.SyncedProjectTestDef
import com.android.tools.idea.gradle.project.sync.snapshots.TestProject
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.Companion.AGP_CURRENT
import com.android.tools.idea.testing.aggregateAndThrowIfAny
import com.android.tools.idea.testing.runCatchingAndRecord
import com.google.common.truth.Truth
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import java.io.File

fun ModuleDumpWithType.filterOutKnownConsistencyIssues(testProject: TestProject): ModuleDumpWithType {
  val (androidProjectStructure, restProjectStructure) =
    projectStructure.partition { line -> androidModuleNames.any { line.contains("MODULE ($it)") } }
  val (androidIdeModelsAndLibraryTable, restIdeModels) =
    ideModels.partition { line -> androidModuleNames.any { line.contains("MODULE ($it)") || line.contains("LIBRARY_TABLE") } }
  return copy(
    projectStructure =
      androidProjectStructure
        .filter { line ->
          (AndroidConsistencyIssues.projectStructure(testProject) + KmpConsistencyIssues.projectStructure(testProject)).none {
            line.contains(it)
          }
        }
        .asSequence() +
        restProjectStructure.filter { line ->
          (JavaConsistencyIssues.projectStructure + KmpConsistencyIssues.projectStructure(testProject)).none { line.contains(it) }
        },
    ideModels =
      androidIdeModelsAndLibraryTable
        .filter { line ->
          (AndroidConsistencyIssues.ideModels(testProject) + KmpConsistencyIssues.ideModels(testProject)).none { line.contains(it) }
        }
        .asSequence() + restIdeModels.filter { line -> KmpConsistencyIssues.ideModels(testProject).none { line.contains(it) } },
  )
}

data class PhasedSyncSnapshotConsistencyTestDef(
  override val testProject: TestProject,
  override val agpVersion: AgpVersionSoftwareEnvironmentDescriptor = AGP_CURRENT,
) : SyncedProjectTestDef, PhasedSyncSnapshotTestBase(checkObjectIdentity = true) {

  override fun setup(testRootDisposable: Disposable) {
    setupPhasedSyncIntermediateStateCollector(testRootDisposable)
  }

  override fun runTest(root: File, project: Project) {
    if (!StudioFlags.PHASED_SYNC_ENABLED.get()) return
    Truth.assertThat(knownAndroidPaths).isNotNull()
    Truth.assertThat(intermediateDump).isNotNull()

    val filteredFullDump =
      project
        .dumpModules(knownAndroidPaths, checkObjectIdentity = true)
        .filterOutKnownConsistencyIssues(testProject)
        .filterToPhasedSyncModules()

    val filteredIntermediateDump = intermediateDump.filterOutKnownConsistencyIssues(testProject).maybeFilterToPhasedSyncModules(testProject)

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
      if (testProject in GradleProjectPathConsistencyIssues.filteredProjects) return@aggregateAndThrowIfAny
      runCatchingAndRecord {
        Truth.assertWithMessage("Comparing Workspace and DataNodes based GradleProjectPaths failed")
          .that(project.dumpGradleProjectPaths(FetchMode.Workspace))
          .isEqualTo(project.dumpGradleProjectPaths(FetchMode.DataNodes))
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
    // projects excluded from Gradle Project Path tests
    val tests =
      phasedSyncTestProjects
        .filterNot { KmpConsistencyIssues.filteredProjects.contains(it) }
        .map { PhasedSyncSnapshotConsistencyTestDef(it) }
  }
}
