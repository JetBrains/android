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

import com.android.tools.idea.gradle.project.sync.snapshots.SyncedProjectTestDef
import com.android.tools.idea.gradle.project.sync.snapshots.TestProject
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor
import com.android.tools.idea.testing.findAppModule
import com.android.tools.idea.testing.findModule
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.TruthJUnit
import com.intellij.gradle.toolingExtension.util.GradleVersionUtil
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gradle.settings.GradleSettings
import java.io.File

data class GradleModuleHierarchyProviderTest(
  override val name: String,
  override val testProject: TestProject,
  override val agpVersion: AgpVersionSoftwareEnvironmentDescriptor = AgpVersionSoftwareEnvironmentDescriptor.AGP_CURRENT,
  val test: (Project) -> Unit
) : SyncedProjectTestDef {

  companion object {
    val tests: List<GradleModuleHierarchyProviderTest> = listOf(
      GradleModuleHierarchyProviderTest(
        name = "testCompositeStructure",
        TestProject.COMPOSITE_BUILD,
      ) { project ->

        val expected = mutableListOf(
          project.findModule("project.app"),
          project.findModule("project.lib"),
          project.findModule("TestCompositeLib1"),
          project.findModule("composite2"),
          project.findModule("TestCompositeLib3"),
          project.findModule("composite4"),
        )

        val projectGradleVersion = GradleSettings.getInstance(project).linkedProjectsSettings.single().resolveGradleVersion()
        val additionalTopLevel = if (GradleVersionUtil.isGradleOlderThan(projectGradleVersion, "8.0")) {
          // With Gradle 7.x (and below), the names of the included builds had to be unique (even for nested ones), and the identity
          // path is just the build name. In Gradle 8.0+ names don't need to be unique so Gradle identity path includes full parent chain.
          // This means that resulting hierarchy is different.
          listOf(project.findModule("compositeNest"), project.findModule("com.test.compositeNest3.compositeNest"))
        } else {
          emptyList()
        }

        val provider = GradleModuleHierarchyProvider(project)
        assertThat(provider.forProject.submodules).containsExactlyElementsIn(expected + additionalTopLevel)
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

  override fun withAgpVersion(agpVersion: AgpVersionSoftwareEnvironmentDescriptor): SyncedProjectTestDef {
    return copy(agpVersion = agpVersion)
  }

  override fun runTest(root: File, project: Project) {
    test(project)
  }
}