/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.projectsystem.gradle

import com.android.tools.idea.gradle.project.sync.snapshots.SyncedProjectTest
import com.android.tools.idea.gradle.project.sync.snapshots.TestProject
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor
import com.android.tools.idea.testing.findAppModule
import com.android.tools.idea.testing.findModule
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.TruthJUnit
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import java.io.File

data class GradleModuleHierarchyProviderTest(
  override val name: String,
  override val testProject: TestProject,
  override val agpVersion: AgpVersionSoftwareEnvironmentDescriptor = AgpVersionSoftwareEnvironmentDescriptor.AGP_CURRENT,
  val test: (Project) -> Unit
) : SyncedProjectTest.TestDef {

  companion object {
    val tests: List<GradleModuleHierarchyProviderTest> = listOf(
      GradleModuleHierarchyProviderTest(
        name = "testCompositeStructure",
        TestProject.COMPOSITE_BUILD,
      ) { project ->
        val provider = GradleModuleHierarchyProvider(project)
        val mainProject = project.findModule("project")
        val project1 = project.findModule("TestCompositeLib1")
        val project2 = project.findModule("composite2")
        val project3 = project.findModule("TestCompositeLib3")
        val project4 = project.findModule("composite4")
        val project5 = project.findModule("compositeNest")
        val project6 = project.findModule("com.test.compositeNest3.compositeNest") // The name given by IntelliJ.
        assertThat(provider.forProject.submodules).containsExactly(mainProject, project1, project2, project3, project4, project5, project6)
      },

      GradleModuleHierarchyProviderTest(
        name = "testUsualStructure",
        TestProject.SIMPLE_APPLICATION,
      ) { project ->
        val provider = GradleModuleHierarchyProvider(project)
        val app = project.findAppModule()
        assertThat(provider.forProject.submodules).containsExactly(app)
      },

      GradleModuleHierarchyProviderTest(
        name = "testFirstSyncFailedStructure",
        TestProject.SIMPLE_APPLICATION_SYNC_FAILED
      ) {project ->
        TruthJUnit.assume().that(ModuleManager.getInstance(project).modules).asList().hasSize(1)
        val provider = GradleModuleHierarchyProvider(project)
        // This case is handled by the AndroidViewProjectNode directly.
        assertThat(provider.forProject.submodules).isEmpty()

      },
    )
  }

  override fun withAgpVersion(agpVersion: AgpVersionSoftwareEnvironmentDescriptor): SyncedProjectTest.TestDef {
    return copy(agpVersion = agpVersion)
  }

  override fun runTest(root: File, project: Project) {
    test(project)
  }
}